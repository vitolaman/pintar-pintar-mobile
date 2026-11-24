package com.getmobilestack.kmp.pintar.dashboard

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

@Serializable data class MerchantRegistration(@SerialName("store_name") val storeName: String, @SerialName("store_description") val description: String, @SerialName("terms_accepted") val termsAccepted: Boolean, @SerialName("business_type") val businessType: String, @SerialName("category_label") val category: String? = null, val city: String, @SerialName("public_phone") val phone: String, @SerialName("product_types") val productTypes: List<String>)
@Serializable data class ClassDraft(val title: String, val description: String? = null, val type: String, val status: String = "draft", @SerialName("original_price") val originalPrice: Double = 0.0, @SerialName("discount_price") val discountPrice: Double? = null)
@Serializable data class ClassDetails(val id: String, val title: String, val description: String? = null, val type: String, val status: String, @SerialName("original_price") val originalPrice: Double? = null, @SerialName("discount_price") val discountPrice: Double? = null)
@Serializable private data class ClassResponse(val data: ClassDetails)
@Serializable data class ManagedVideo(val id: String, val title: String, @SerialName("youtube_url") val youtubeUrl: String? = null, val source: String)
@Serializable data class ManagedChapter(val id: String, val title: String, val description: String? = null, val videos: List<ManagedVideo> = emptyList())
@Serializable private data class ChaptersResponse(val data: List<ManagedChapter>)
@Serializable private data class ChapterRequest(val title: String, val description: String? = null)
@Serializable private data class VideoRequest(val title: String, @SerialName("youtube_url") val youtubeUrl: String)

interface MerchantManagementRepository {
    suspend fun register(token: String, registration: MerchantRegistration)
    suspend fun classDetails(token: String, id: String): ClassDetails
    suspend fun createClass(token: String, draft: ClassDraft): ClassDetails
    suspend fun updateClass(token: String, id: String, draft: ClassDraft): ClassDetails
    suspend fun chapters(token: String, classId: String): List<ManagedChapter>
    suspend fun addChapter(token: String, classId: String, title: String, description: String)
    suspend fun addVideoLink(token: String, classId: String, chapterId: String, title: String, url: String)
    suspend fun deleteChapter(token: String, classId: String, chapterId: String)
    suspend fun deleteVideo(token: String, classId: String, chapterId: String, videoId: String)
}

class ApiMerchantManagementRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : MerchantManagementRepository {
    private fun api(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"
    override suspend fun register(token: String, registration: MerchantRegistration) {
        require(registration.storeName.isNotBlank() && registration.storeName.length <= 160 && registration.description.isNotBlank() && registration.description.length <= 2000 && registration.termsAccepted && registration.businessType in setOf("individual", "institution", "company") && registration.city.isNotBlank() && registration.city.length <= 120 && registration.phone.isNotBlank() && registration.phone.length <= 32 && registration.productTypes.isNotEmpty() && registration.productTypes.all { it in setOf("kelas", "bootcamp", "digital") })
        require(registration.category == null || registration.category in setOf("Teknik & Arsitektur", "Pemrograman & IT", "Desain & Kreatif", "Bisnis & Manajemen"))
        val response = client.post(api("merchant/register")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(registration)) }
        checked(response.status.value, response.status.isSuccess(), response.bodyAsText())
    }
    override suspend fun classDetails(token: String, id: String): ClassDetails {
        require(id.isNotBlank())
        val response = client.getClassDetails(api("classes/${id.encodeURLPathPart()}"), token)
        return json.decodeFromString<ClassResponse>(checked(response.status.value, response.status.isSuccess(), response.bodyAsText())).data
    }
    override suspend fun createClass(token: String, draft: ClassDraft): ClassDetails = save(token, null, draft)
    override suspend fun updateClass(token: String, id: String, draft: ClassDraft): ClassDetails { require(id.isNotBlank()); return save(token, id, draft) }
    override suspend fun chapters(token: String, classId: String): List<ManagedChapter> {
        require(classId.isNotBlank())
        val response = client.getClassDetails(api("classes/${classId.encodeURLPathPart()}/chapters"), token)
        return json.decodeFromString<ChaptersResponse>(checked(response.status.value, response.status.isSuccess(), response.bodyAsText())).data
    }
    override suspend fun addChapter(token: String, classId: String, title: String, description: String) {
        require(classId.isNotBlank() && title.isNotBlank() && title.length <= 255 && description.length <= 10000)
        val response = client.post(api("classes/${classId.encodeURLPathPart()}/chapters")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(ChapterRequest(title.trim(), description.trim().ifBlank { null }))) }
        checked(response.status.value, response.status.isSuccess(), response.bodyAsText())
    }
    override suspend fun addVideoLink(token: String, classId: String, chapterId: String, title: String, url: String) {
        require(classId.isNotBlank() && chapterId.isNotBlank() && title.isNotBlank() && title.length <= 255 && url.length <= 2048 && com.getmobilestack.kmp.pintar.learning.safeLearningUrl(url)?.startsWith("https://", true) == true)
        val response = client.post(api("classes/${classId.encodeURLPathPart()}/chapters/${chapterId.encodeURLPathPart()}/videos")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(VideoRequest(title.trim(), url.trim()))) }
        checked(response.status.value, response.status.isSuccess(), response.bodyAsText())
    }
    override suspend fun deleteChapter(token: String, classId: String, chapterId: String) {
        require(classId.isNotBlank() && chapterId.isNotBlank())
        val response = client.delete(api("classes/${classId.encodeURLPathPart()}/chapters/${chapterId.encodeURLPathPart()}")) { header(HttpHeaders.Authorization, "Bearer $token") }
        checked(response.status.value, response.status.isSuccess(), response.bodyAsText())
    }
    override suspend fun deleteVideo(token: String, classId: String, chapterId: String, videoId: String) {
        require(classId.isNotBlank() && chapterId.isNotBlank() && videoId.isNotBlank())
        val response = client.delete(api("classes/${classId.encodeURLPathPart()}/chapters/${chapterId.encodeURLPathPart()}/videos/${videoId.encodeURLPathPart()}")) { header(HttpHeaders.Authorization, "Bearer $token") }
        checked(response.status.value, response.status.isSuccess(), response.bodyAsText())
    }
    private suspend fun save(token: String, id: String?, draft: ClassDraft): ClassDetails {
        require(draft.title.isNotBlank() && draft.title.length <= 255 && (draft.description?.length ?: 0) <= 10000 && draft.type in setOf("kelas", "bootcamp") && draft.status in setOf("draft", "published", "archived") && draft.originalPrice >= 0 && (draft.discountPrice == null || draft.discountPrice in 0.0..draft.originalPrice))
        val body = json.encodeToString(draft)
        val response = if (id == null) client.post(api("merchant/classes")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(body) }
        else client.patch(api("classes/${id.encodeURLPathPart()}")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(body) }
        return json.decodeFromString<ClassResponse>(checked(response.status.value, response.status.isSuccess(), response.bodyAsText())).data
    }
    private fun checked(status: Int, success: Boolean, body: String): String {
        if (!success) {
            val message = runCatching { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull }.getOrNull()
            throw AccountApiException(status, message ?: "Permintaan belum dapat diproses.")
        }
        return body
    }
}

private suspend fun HttpClient.getClassDetails(url: String, token: String) = get(url) { header(HttpHeaders.Authorization, "Bearer $token") }
