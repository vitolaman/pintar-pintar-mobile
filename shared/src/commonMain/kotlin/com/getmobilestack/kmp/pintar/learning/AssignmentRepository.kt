package com.getmobilestack.kmp.pintar.learning

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable data class AssignmentResource(val name: String, @SerialName("download_url") val downloadUrl: String, val size: Long? = null)
@Serializable data class Submission(val status: String, val score: Double? = null, val feedback: String? = null, @SerialName("file_name") val fileName: String? = null, @SerialName("is_late") val isLate: Boolean = false)
@Serializable data class Assignment(
    val id: String, val title: String, val type: String, val description: String? = null, val due: String? = null,
    @SerialName("is_past_due") val isPastDue: Boolean = false,
    val resource: AssignmentResource? = null,
    @SerialName("my_submission") val submission: Submission? = null,
    val resources: List<AssignmentResource> = emptyList(),
    @SerialName("question_count") val questionCount: Int = 0,
    @SerialName("total_weight") val totalWeight: Double = 0.0,
)
@Serializable data class Question(val id: String, @SerialName("question_text") val text: String, val type: String, val options: List<String>? = null, @SerialName("score_weight") val weight: Double = 0.0)
@Serializable data class Quiz(val id: String, val title: String, val questions: List<Question>, @SerialName("my_submission") val submission: Submission? = null, val description: String? = null, val due: String? = null, @SerialName("total_weight") val totalWeight: Double = 0.0)
@Serializable data class QuizAnswer(@SerialName("question_id") val questionId: String, val answer: String)
@Serializable private data class QuizAnswers(val answers: List<QuizAnswer>)
@Serializable private data class AssignmentFile(@SerialName("file_asset_id") val fileAssetId: String)
@Serializable private data class AssignmentListResponse(val data: List<Assignment>)
@Serializable private data class AssignmentResponse(val data: Assignment)
@Serializable private data class QuizResponse(val data: Quiz)
@Serializable private data class InitiateRequest(val fileName: String, val contentType: String)
@Serializable private data class InitiateResponse(val uploadId: String, val key: String)
@Serializable private data class PresignedRequest(val uploadId: String, val key: String, val partsCount: Int)
@Serializable private data class PresignedPart(val partNumber: Int, val url: String)
@Serializable private data class UploadedPart(@SerialName("PartNumber") val number: Int, @SerialName("ETag") val etag: String)
@Serializable private data class CompleteRequest(val uploadId: String, val key: String, val parts: List<UploadedPart>)
@Serializable private data class CompleteResponse(@SerialName("asset_id") val assetId: String)

interface AssignmentRepository {
    suspend fun list(token: String, classId: String): List<Assignment>
    suspend fun quiz(token: String, assignmentId: String): Quiz
    suspend fun submitQuiz(token: String, assignmentId: String, answers: List<QuizAnswer>): Assignment
    suspend fun uploadFile(token: String, name: String, bytes: ByteArray): String
    suspend fun submitFile(token: String, assignmentId: String, assetId: String): Assignment
}

const val MAX_ASSIGNMENT_BYTES = 20 * 1024 * 1024
fun assignmentFileError(name: String, size: Long): String? = when {
    name.substringAfterLast('.', "").lowercase() !in setOf("pdf", "dwg", "zip") -> "Gunakan file PDF, DWG, atau ZIP."
    size <= 0 -> "File kosong tidak dapat diunggah."
    size > MAX_ASSIGNMENT_BYTES -> "Ukuran file maksimal 20 MB."
    else -> null
}

class ApiAssignmentRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : AssignmentRepository {
    private fun api(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"

    override suspend fun list(token: String, classId: String): List<Assignment> = json.decodeFromString<AssignmentListResponse>(
        client.get(api("learning/classes/${classId.encodeURLPathPart()}/assignments")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    ).data

    override suspend fun quiz(token: String, assignmentId: String): Quiz = json.decodeFromString<QuizResponse>(
        client.get(api("learning/assignments/${assignmentId.encodeURLPathPart()}/quiz")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    ).data

    override suspend fun submitQuiz(token: String, assignmentId: String, answers: List<QuizAnswer>): Assignment {
        require(assignmentId.isNotBlank() && answers.isNotEmpty() && answers.all { it.questionId.isNotBlank() && it.answer.isNotBlank() && it.answer.length <= 10000 } && answers.map { it.questionId }.distinct().size == answers.size)
        return json.decodeFromString<AssignmentResponse>(client.post(api("learning/assignments/${assignmentId.encodeURLPathPart()}/submit-quiz")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(QuizAnswers(answers)))
        }.checkedBody()).data
    }

    override suspend fun uploadFile(token: String, name: String, bytes: ByteArray): String {
        require(assignmentFileError(name, bytes.size.toLong()) == null)
        val type = when (name.substringAfterLast('.').lowercase()) { "pdf" -> "application/pdf"; "zip" -> "application/zip"; else -> "application/octet-stream" }
        val init = json.decodeFromString<InitiateResponse>(client.post(api("upload/initiate")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(InitiateRequest(name, type)))
        }.checkedBody())
        val chunkSize = 5 * 1024 * 1024
        val count = (bytes.size + chunkSize - 1) / chunkSize
        val urls = json.decodeFromString<List<PresignedPart>>(client.post(api("upload/presigned-urls")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(PresignedRequest(init.uploadId, init.key, count)))
        }.checkedBody())
        require(urls.size == count && urls.map { it.partNumber }.toSet() == (1..count).toSet()) { "Invalid upload URLs" }
        val parts = (1..count).map { number ->
            val part = urls.single { it.partNumber == number }
            require(runCatching { Url(part.url).protocol.name == "https" }.getOrDefault(false)) { "Invalid upload URL" }
            val response = client.put(part.url) { setBody(bytes.copyOfRange((number - 1) * chunkSize, minOf(number * chunkSize, bytes.size))) }
            check(response.status.isSuccess()) { "Upload failed (${response.status.value})" }
            UploadedPart(number, response.headers[HttpHeaders.ETag] ?: error("Upload ETag missing"))
        }
        val asset = json.decodeFromString<CompleteResponse>(client.post(api("upload/complete")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(CompleteRequest(init.uploadId, init.key, parts)))
        }.checkedBody())
        require(asset.assetId.isNotBlank())
        return asset.assetId
    }

    override suspend fun submitFile(token: String, assignmentId: String, assetId: String): Assignment {
        require(assignmentId.isNotBlank() && assetId.isNotBlank())
        return json.decodeFromString<AssignmentResponse>(client.post(api("learning/assignments/${assignmentId.encodeURLPathPart()}/submit")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(AssignmentFile(assetId)))
        }.checkedBody()).data
    }

    private suspend fun HttpResponse.checkedBody(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = try { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull } catch (_: Exception) { null }
            throw AccountApiException(status.value, message ?: "Permintaan belum dapat diproses.")
        }
        return body
    }
}
