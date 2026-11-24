package com.getmobilestack.kmp.pintar.learning

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.catalog.CatalogMeta
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable data class DiscussionAuthor(val name: String, val role: String = "")
@Serializable data class DiscussionComment(val id: String, val content: String, val author: DiscussionAuthor, @SerialName("created_at") val createdAt: String? = null)
@Serializable data class DiscussionThread(val id: String, val title: String, val content: String, val author: DiscussionAuthor, @SerialName("comment_count") val commentCount: Int = 0, val comments: List<DiscussionComment> = emptyList(), val badge: String? = null, @SerialName("created_at") val createdAt: String? = null)
@Serializable data class DiscussionPage(val data: List<DiscussionThread>, val meta: CatalogMeta)
@Serializable data class Grade(val title: String, val status: String, val score: Double? = null, val feedback: String? = null)
@Serializable data class Grades(val assignments: List<Grade>, val participation: Double? = null, @SerialName("average_score") val averageScore: Double? = null)
@Serializable data class MeetingMentor(val name: String)
@Serializable data class MeetingClass(val id: String, val title: String)
@Serializable data class MeetingDetails(val id: String, val title: String, val date: String? = null, val time: String? = null, val content: String? = null, @SerialName("has_started") val hasStarted: Boolean, val status: String, @SerialName("live_url") val liveUrl: String? = null, val mentor: MeetingMentor? = null)
@Serializable data class Attendance(val status: String, @SerialName("check_in_time") val checkInTime: String? = null, val feedback: String? = null)
@Serializable data class MeetingAttendance(val meeting: MeetingDetails, @SerialName("my_attendance") val myAttendance: Attendance? = null, @SerialName("class") val course: MeetingClass? = null, @SerialName("mentor_names") val mentorNames: List<String> = emptyList())
@Serializable data class CertificateRequirements(@SerialName("min_attendance_percent") val minAttendancePercent: Double, @SerialName("min_score") val minScore: Double, @SerialName("auto_issue") val autoIssue: Boolean = false)
@Serializable data class CertificateStatus(val status: String, val progress: Double = 0.0, @SerialName("cert_no") val certificateNumber: String? = null, @SerialName("file_download_url") val downloadUrl: String? = null, @SerialName("attendance_percent") val attendancePercent: Double? = null, @SerialName("average_score") val averageScore: Double? = null, val requirements: CertificateRequirements? = null)

fun certificateDownloadUrl(certificate: CertificateStatus?): String? =
    certificate?.takeIf { it.status == "issued" }?.downloadUrl?.let(::safeLearningUrl)

@Serializable private data class ThreadRequest(@SerialName("class_id") val classId: String, val title: String, val content: String)
@Serializable private data class CommentRequest(@SerialName("thread_id") val threadId: String, val content: String)
@Serializable private data class CheckInRequest(val feedback: String?)
@Serializable private data class GradesResponse(val data: Grades)
@Serializable private data class MeetingResponse(val data: MeetingAttendance)

interface ClassWorkspaceRepository {
    suspend fun threads(token: String, classId: String, page: Int): DiscussionPage
    suspend fun createThread(token: String, classId: String, title: String, content: String)
    suspend fun addComment(token: String, threadId: String, content: String)
    suspend fun grades(token: String, classId: String): Grades
    suspend fun meeting(token: String, meetingId: String): MeetingAttendance
    suspend fun checkIn(token: String, meetingId: String, feedback: String?): MeetingAttendance
}

class ApiClassWorkspaceRepository(
    private val client: HttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ClassWorkspaceRepository {
    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"

    override suspend fun threads(token: String, classId: String, page: Int): DiscussionPage {
        require(page > 0 && classId.isNotBlank())
        return json.decodeFromString(client.get(url("discussions/threads")) {
            header(HttpHeaders.Authorization, "Bearer $token"); parameter("class_id", classId); parameter("page", page); parameter("limit", 20)
        }.checkedBody())
    }

    override suspend fun createThread(token: String, classId: String, title: String, content: String) {
        require(classId.isNotBlank() && title.isNotBlank() && title.length <= 160 && content.isNotBlank() && content.length <= 5000)
        client.post(url("discussions/threads")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ThreadRequest(classId, title.trim(), content.trim())))
        }.checkedBody()
    }

    override suspend fun addComment(token: String, threadId: String, content: String) {
        require(threadId.isNotBlank() && content.isNotBlank() && content.length <= 2000)
        client.post(url("discussions/comments")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CommentRequest(threadId, content.trim())))
        }.checkedBody()
    }

    override suspend fun grades(token: String, classId: String): Grades = json.decodeFromString<GradesResponse>(
        client.get(url("learning/classes/${classId.encodeURLPathPart()}/grades")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    ).data

    override suspend fun meeting(token: String, meetingId: String): MeetingAttendance = json.decodeFromString<MeetingResponse>(
        client.get(url("learning/meetings/${meetingId.encodeURLPathPart()}")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    ).data

    override suspend fun checkIn(token: String, meetingId: String, feedback: String?): MeetingAttendance {
        require(feedback == null || feedback.length <= 2000)
        return json.decodeFromString<MeetingResponse>(client.post(url("learning/meetings/${meetingId.encodeURLPathPart()}/check-in")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CheckInRequest(feedback?.trim()?.ifEmpty { null })))
        }.checkedBody()).data
    }

    private suspend fun HttpResponse.checkedBody(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = try {
                when (val value = json.parseToJsonElement(body).jsonObject["responseMessage"]) {
                    is JsonPrimitive -> value.contentOrNull
                    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ").ifBlank { null }
                    else -> null
                }
            } catch (_: Exception) { null }
            throw AccountApiException(status.value, message ?: "Permintaan belum dapat diproses.")
        }
        return body
    }
}
