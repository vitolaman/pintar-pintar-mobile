package com.getmobilestack.kmp.pintar.learning

import com.getmobilestack.kmp.pintar.account.AccountApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LearningRepositoryTest {
    @Test fun `loads course with bearer token`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/learning/classes/class-1", request.url.encodedPath)
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            respond("""{"data":{"id":"class-1","title":"Kelas","progress":25,"chapters":[{"id":"c","title":"Bab","videos":[{"id":"v","title":"Video","is_completed":false}],"files":[]}],"meetings":[]}}""")
        })
        try { assertEquals("Video", ApiLearningRepository(client, "https://example.com").getClass("secret", "class-1").chapters.single().videos.single().title) }
        finally { client.close() }
    }

    @Test fun `loads owned product and completes video`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            if (request.method == HttpMethod.Post) {
                assertEquals("/api/v1/learning/videos/v/complete", request.url.encodedPath)
                respond("{}")
            } else {
                assertEquals("/api/v1/learning/digital-products/p", request.url.encodedPath)
                respond("""{"data":{"id":"p","title":"Produk","file":{"name":"file.zip","download_url":"https://example.com/file"}}}""")
            }
        })
        try {
            val repository = ApiLearningRepository(client, "https://example.com")
            assertEquals("file.zip", repository.getProduct("secret", "p").file?.name)
            repository.completeVideo("secret", "v")
        } finally { client.close() }
    }

    @Test fun `rejects non web links and preserves API error status`() = runBlocking {
        assertNull(safeLearningUrl("javascript:alert(1)"))
        assertNull(safeLearningUrl("https://example.com/ bad"))
        assertEquals("https://example.com/video", safeLearningUrl(" https://example.com/video "))
        val client = HttpClient(MockEngine { respond("""{"responseMessage":"Unauthorized"}""", HttpStatusCode.Unauthorized) })
        try {
            assertEquals(401, assertFailsWith<AccountApiException> { ApiLearningRepository(client, "https://example.com").getClass("bad", "id") }.statusCode)
        } finally { client.close() }
    }

    @Test fun `playback link falls back to validated youtube id`() {
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", LearningVideo("1", "Video", youtubeId = "abcdefghijk").playbackUrl())
        assertNull(LearningVideo("1", "Video", youtubeId = "bad/id").playbackUrl())
        assertEquals("https://cdn.example/video", LearningVideo("1", "Video", videoUrl = "https://cdn.example/video", youtubeId = "abcdefghijk").playbackUrl())
    }

    @Test fun `selects native file youtube or safe external playback`() {
        assertEquals(VideoPlayback.File("https://cdn.example/video"), LearningVideo("v", "Video", videoUrl = "https://cdn.example/video", source = "file").playback())
        assertEquals(VideoPlayback.YouTube("abcdefghijk"), LearningVideo("v", "Video", youtubeId = "abcdefghijk", source = "link").playback())
        assertEquals(VideoPlayback.External("https://example.com/embed"), LearningVideo("v", "Video", youtubeUrl = "https://example.com/embed", source = "link").playback())
        assertNull(LearningVideo("v", "Video", youtubeId = "bad/id", youtubeUrl = "javascript:alert(1)", source = "link").playback())
        assertNull(LearningVideo("v", "Video", videoUrl = "https://user:password@example.com/file", source = "file").playback())
    }

    @Test fun `certificate links require issued status and a web URL`() {
        assertEquals("https://cdn.example/cert.pdf", certificateDownloadUrl(CertificateStatus("issued", downloadUrl = "https://cdn.example/cert.pdf")))
        assertNull(certificateDownloadUrl(CertificateStatus("pending", downloadUrl = "https://cdn.example/cert.pdf")))
        assertNull(certificateDownloadUrl(CertificateStatus("issued", downloadUrl = "javascript:alert(1)")))
        assertNull(certificateDownloadUrl(null))
    }

    @Test fun `learning response exposes chapter details attendance mentors and FAQ`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"data":{"id":"class","title":"Kelas","progress":50,"mentors":[{"id":"m","name":"Mentor"}],"faqs":[{"question":"Apa?","answer":"Jawaban"}],"certificate":{"status":"pending","progress":50,"requirements":{"auto_issue":true,"min_attendance_percent":80,"min_score":75}},"chapters":[{"id":"c","title":"Bab","description":"Pengantar","videos":[{"id":"v","title":"Video","description":"Materi"}],"files":[{"id":"f","name":"File","description":"Latihan"}]}],"meetings":[{"id":"m","title":"Live","status":"upcoming","duration_minutes":60,"my_attendance_status":"present"}]}}""") })
        try {
            val course = ApiLearningRepository(client, "https://example.com").getClass("token", "class")
            assertEquals("Pengantar", course.chapters.single().description)
            assertEquals("Materi", course.chapters.single().videos.single().description)
            assertEquals("Latihan", course.chapters.single().files.single().description)
            assertEquals("present", course.meetings.single().attendanceStatus)
            assertEquals("Mentor", course.mentors.single().name)
            assertEquals("Jawaban", course.faqs.single().answer)
            assertEquals(80.0, course.certificate?.requirements?.minAttendancePercent)
        } finally { client.close() }
    }
}
