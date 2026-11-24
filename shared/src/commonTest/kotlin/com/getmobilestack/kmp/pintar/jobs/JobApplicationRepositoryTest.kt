package com.getmobilestack.kmp.pintar.jobs

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JobApplicationRepositoryTest {
    @Test fun `validates CV file type and size`() {
        assertNull(cvFileError("cv.DOCX", MAX_CV_BYTES.toLong()))
        assertNotNull(cvFileError("cv.zip", 1))
        assertNotNull(cvFileError("cv.pdf", 0))
        assertNotNull(cvFileError("cv.pdf", MAX_CV_BYTES.toLong() + 1))
        assertNull(mentorCertificateFileError("certificate.JPG", 1))
        assertNotNull(mentorCertificateFileError("certificate.docx", 1))
    }
    @Test fun `uploads CV then applies and lists applications`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "storage.example.com") {
                assertNull(request.headers[HttpHeaders.Authorization]); respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "etag"))
            } else {
                assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                when (request.url.encodedPath) {
                    "/api/v1/upload/initiate" -> { assertEquals("""{"fileName":"cv.pdf","contentType":"application/pdf"}""", (request.body as TextContent).text); respond("""{"uploadId":"u","key":"k"}""") }
                    "/api/v1/upload/presigned-urls" -> respond("""[{"partNumber":1,"url":"https://storage.example.com/part"}]""")
                    "/api/v1/upload/complete" -> respond("""{"asset_id":"cv"}""")
                    "/api/v1/job-postings/j/apply" -> { assertEquals("""{"name":"Ana","email":"ana@example.com","phone":"123","cv_asset_id":"cv"}""", (request.body as TextContent).text); respond("""{"data":{"id":"a","status":"applied","created_at":"date","job":{"id":"j","title":"Mentor"},"merchant":{"name":"Toko"}}}""") }
                    "/api/v1/job-applications" -> respond("""{"data":{"applications":[{"id":"a","status":"applied","created_at":"date","job":{"id":"j","title":"Mentor"},"merchant":{"name":"Toko"}}]},"meta":{"page":1,"total_page":1}}""")
                    else -> error("Unexpected path")
                }
            }
        })
        try {
            val repository = ApiJobApplicationRepository(client, "https://example.com")
            val asset = repository.uploadCv("token", "cv.pdf", byteArrayOf(1))
            assertEquals("a", repository.apply("token", "j", ApplyJob("Ana", "ana@example.com", "123", cvAssetId = asset)).id)
            assertEquals("Mentor", repository.applications("token", 1).data.applications.single().job.title)
        } finally { client.close() }
    }
}
