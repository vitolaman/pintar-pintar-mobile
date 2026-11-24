package com.getmobilestack.kmp.pintar.learning

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable data class LearningVideo(
    val id: String,
    val title: String,
    val duration: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    @SerialName("youtube_id") val youtubeId: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("is_completed") val completed: Boolean = false,
    val description: String? = null,
    val source: String? = null,
)
@Serializable data class LearningResource(
    val id: String,
    val name: String,
    val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val description: String? = null,
    val type: String? = null,
)
@Serializable data class LearningChapter(val id: String, val title: String, val videos: List<LearningVideo> = emptyList(), val files: List<LearningResource> = emptyList(), val description: String? = null)
@Serializable data class LearningMeeting(val id: String, val title: String, val date: String? = null, val time: String? = null, @SerialName("live_url") val liveUrl: String? = null, val content: String? = null, val status: String? = null, @SerialName("duration_minutes") val durationMinutes: Int? = null, @SerialName("my_attendance_status") val attendanceStatus: String? = null)
@Serializable data class NextVideo(val id: String, val title: String, @SerialName("chapter_id") val chapterId: String)
@Serializable data class LearningMentor(val id: String, val name: String)
@Serializable data class LearningFaq(val question: String, val answer: String)
@Serializable data class LearningClass(
    val id: String,
    val title: String,
    val description: String? = null,
    val progress: Int = 0,
    val chapters: List<LearningChapter> = emptyList(),
    val meetings: List<LearningMeeting> = emptyList(),
    val certificate: CertificateStatus? = null,
    @SerialName("next_video") val nextVideo: NextVideo? = null,
    @SerialName("post_purchase_instructions") val instructions: String? = null,
    val mentors: List<LearningMentor> = emptyList(),
    val faqs: List<LearningFaq> = emptyList(),
)
@Serializable data class OwnedFile(val name: String, @SerialName("download_url") val downloadUrl: String? = null, val format: String? = null, val size: Long? = null)
@Serializable data class OwnedProduct(val id: String, val title: String, val description: String? = null, val file: OwnedFile? = null, @SerialName("post_purchase_instructions") val instructions: String? = null, @SerialName("cover_url") val coverUrl: String? = null)
@Serializable private data class ClassResponse(val data: LearningClass)
@Serializable private data class ProductResponse(val data: OwnedProduct)

interface LearningRepository {
    suspend fun getClass(token: String, id: String): LearningClass
    suspend fun getProduct(token: String, id: String): OwnedProduct
    suspend fun completeVideo(token: String, id: String)
}

class ApiLearningRepository(
    private val client: HttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : LearningRepository {
    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1/learning/$path"

    override suspend fun getClass(token: String, id: String): LearningClass = json.decodeFromString<ClassResponse>(
        client.get(url("classes/${id.encodeURLPathPart()}")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    ).data

    override suspend fun getProduct(token: String, id: String): OwnedProduct = json.decodeFromString<ProductResponse>(
        client.get(url("digital-products/${id.encodeURLPathPart()}")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    ).data

    override suspend fun completeVideo(token: String, id: String) {
        client.post(url("videos/${id.encodeURLPathPart()}/complete")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    }

    private suspend fun HttpResponse.checkedBody(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = try { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull } catch (_: Exception) { null }
            throw AccountApiException(status.value, message ?: "Data pembelajaran belum dapat dimuat.")
        }
        return body
    }
}

/** Only open HTTP(S) links supplied by the backend, never arbitrary URI schemes. */
fun safeLearningUrl(value: String?): String? = value?.trim()?.takeIf { candidate ->
    candidate.none(Char::isWhitespace) && runCatching {
        val parsed = Url(candidate)
        parsed.protocol.name in setOf("http", "https") && parsed.host.isNotBlank() && parsed.user.isNullOrEmpty() && parsed.password.isNullOrEmpty()
    }.getOrDefault(false)
}

fun LearningVideo.playbackUrl(): String? = safeLearningUrl(videoUrl) ?: safeLearningUrl(youtubeUrl)
    ?: youtubeId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/watch?v=$it" }

sealed interface VideoPlayback {
    data class File(val url: String) : VideoPlayback
    data class YouTube(val id: String) : VideoPlayback
    data class External(val url: String) : VideoPlayback
}

/** Only YouTube IDs are embedded; arbitrary links are not loaded into a privileged WebView. */
fun LearningVideo.playback(): VideoPlayback? {
    if (source == "file" || (source == null && videoUrl != null)) return safeLearningUrl(videoUrl)?.let(VideoPlayback::File)
    val id = youtubeId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    if (id != null) return VideoPlayback.YouTube(id)
    return safeLearningUrl(youtubeUrl ?: videoUrl)?.let(VideoPlayback::External)
}
