package com.getmobilestack.kmp.pintar.jobs

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.account.isValidEmail
import com.getmobilestack.kmp.pintar.catalog.CatalogMeta
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

const val MAX_CV_BYTES = 10 * 1024 * 1024
fun cvFileError(name: String, size: Long): String? = when {
    name.substringAfterLast('.', "").lowercase() !in setOf("pdf", "doc", "docx") -> "CV harus PDF, DOC, atau DOCX."
    size <= 0 || size > MAX_CV_BYTES -> "Ukuran CV harus lebih dari 0 dan maksimal 10 MB."
    else -> null
}

@Serializable data class ApplicationJob(val id: String, val title: String)
@Serializable data class JobApplication(val id: String, val status: String, val job: ApplicationJob, val merchant: JobMerchant, @SerialName("created_at") val createdAt: String, @SerialName("interview_at") val interviewAt: String? = null, @SerialName("interview_url") val interviewUrl: String? = null, @SerialName("decided_at") val decidedAt: String? = null)
@Serializable data class ApplicationPage(val data: ApplicationContentWrapper, val meta: CatalogMeta)
@Serializable data class ApplicationContentWrapper(val applications: List<JobApplication>)
@Serializable private data class ApplicationResponse(val data: JobApplication)
@Serializable data class ApplyJob(val name: String, val email: String, val phone: String, @SerialName("linkedin_url") val linkedinUrl: String? = null, @SerialName("cv_asset_id") val cvAssetId: String, val note: String? = null)
@Serializable private data class UploadStart(val fileName: String, val contentType: String)
@Serializable private data class UploadInfo(val uploadId: String, val key: String)
@Serializable private data class UploadUrlsRequest(val uploadId: String, val key: String, val partsCount: Int)
@Serializable private data class UploadUrl(val partNumber: Int, val url: String)
@Serializable private data class UploadPart(@SerialName("PartNumber") val number: Int, @SerialName("ETag") val etag: String)
@Serializable private data class UploadComplete(val uploadId: String, val key: String, val parts: List<UploadPart>)
@Serializable private data class UploadAsset(@SerialName("asset_id") val id: String)

interface JobApplicationRepository {
    suspend fun applications(token: String, page: Int): ApplicationPage
    suspend fun uploadCv(token: String, name: String, bytes: ByteArray): String
    suspend fun uploadMentorCertificate(token: String, name: String, bytes: ByteArray): String
    suspend fun apply(token: String, jobId: String, application: ApplyJob): JobApplication
}

class ApiJobApplicationRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : JobApplicationRepository {
    private fun api(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"
    override suspend fun applications(token: String, page: Int): ApplicationPage {
        require(page > 0)
        return json.decodeFromString(client.get(api("job-applications")) { header(HttpHeaders.Authorization, "Bearer $token"); parameter("page", page); parameter("limit", 20) }.checked())
    }
    override suspend fun apply(token: String, jobId: String, application: ApplyJob): JobApplication {
        require(jobId.isNotBlank() && application.name.isNotBlank() && application.name.length <= 150 && isValidEmail(application.email) && application.phone.isNotBlank() && application.phone.length <= 32 && (application.linkedinUrl?.length ?: 0) <= 500 && application.cvAssetId.isNotBlank() && (application.note?.length ?: 0) <= 2000)
        return json.decodeFromString<ApplicationResponse>(client.post(api("job-postings/${jobId.encodeURLPathPart()}/apply")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(application))
        }.checked()).data
    }
    override suspend fun uploadCv(token: String, name: String, bytes: ByteArray): String {
        require(cvFileError(name, bytes.size.toLong()) == null)
        return uploadDocument(token, name, bytes)
    }
    override suspend fun uploadMentorCertificate(token: String, name: String, bytes: ByteArray): String {
        require(mentorCertificateFileError(name, bytes.size.toLong()) == null)
        return uploadDocument(token, name, bytes)
    }
    private suspend fun uploadDocument(token: String, name: String, bytes: ByteArray): String {
        val type = when (name.substringAfterLast('.').lowercase()) { "pdf" -> "application/pdf"; "doc" -> "application/msword"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; else -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document" }
        suspend fun post(path: String, body: String) = client.post(api("upload/$path")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(body) }.checked()
        val info = json.decodeFromString<UploadInfo>(post("initiate", json.encodeToString(UploadStart(name, type))))
        val chunk = 5 * 1024 * 1024
        val count = (bytes.size + chunk - 1) / chunk
        val urls = json.decodeFromString<List<UploadUrl>>(post("presigned-urls", json.encodeToString(UploadUrlsRequest(info.uploadId, info.key, count))))
        require(urls.size == count && urls.map { it.partNumber }.toSet() == (1..count).toSet())
        val parts = (1..count).map { number ->
            val part = urls.single { it.partNumber == number }
            require(runCatching { Url(part.url).protocol.name == "https" }.getOrDefault(false))
            val response = client.put(part.url) { setBody(bytes.copyOfRange((number - 1) * chunk, minOf(number * chunk, bytes.size))) }
            check(response.status.isSuccess()) { "CV upload failed" }
            UploadPart(number, response.headers[HttpHeaders.ETag] ?: error("Upload ETag missing"))
        }
        return json.decodeFromString<UploadAsset>(post("complete", json.encodeToString(UploadComplete(info.uploadId, info.key, parts)))).id.also { check(it.isNotBlank()) }
    }
    private suspend fun HttpResponse.checked(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = runCatching { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull }.getOrNull()
            throw AccountApiException(status.value, message ?: "Lamaran belum dapat diproses.")
        }
        return body
    }
}

fun mentorCertificateFileError(name: String, size: Long): String? = when {
    name.substringAfterLast('.', "").lowercase() !in setOf("pdf", "png", "jpg", "jpeg") -> "Sertifikat harus PDF, PNG, JPG, atau JPEG."
    size <= 0 || size > MAX_CV_BYTES -> "Ukuran sertifikat harus lebih dari 0 dan maksimal 10 MB."
    else -> null
}
