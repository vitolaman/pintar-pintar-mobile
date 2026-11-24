package com.getmobilestack.kmp.pintar.learning

import com.getmobilestack.kmp.pintar.account.AccountApiException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AssignmentRepositoryTest {
    @Test fun `validates file type and size`() {
        assertNull(assignmentFileError("work.PDF", 1))
        assertNull(assignmentFileError("work.dwg", MAX_ASSIGNMENT_BYTES.toLong()))
        assertNotNull(assignmentFileError("work.exe", 1))
        assertNotNull(assignmentFileError("work.pdf", 0))
        assertNotNull(assignmentFileError("work.zip", MAX_ASSIGNMENT_BYTES.toLong() + 1))
    }

    @Test fun `lists assignments and submits quiz answer text`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/learning/classes/c/assignments" -> respond("""{"data":[{"id":"q","title":"Quiz","type":"quiz","my_submission":null}]}""")
                "/api/v1/learning/assignments/q/quiz" -> respond("""{"data":{"id":"q","title":"Quiz","questions":[{"id":"x","question_text":"Pilih","type":"multiple_choice","options":["A","B"],"score_weight":1}]}}""")
                "/api/v1/learning/assignments/q/submit-quiz" -> {
                    assertEquals("""{"answers":[{"question_id":"x","answer":"B"}]}""", (request.body as TextContent).text)
                    respond("""{"data":{"id":"q","title":"Quiz","type":"quiz","my_submission":{"status":"graded","score":100}}}""")
                }
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiAssignmentRepository(client, "https://example.com")
            assertEquals("q", repository.list("token", "c").single().id)
            assertEquals(listOf("A", "B"), repository.quiz("token", "q").questions.single().options)
            assertEquals(100.0, repository.submitQuiz("token", "q", listOf(QuizAnswer("x", "B"))).submission?.score)
        } finally { client.close() }
    }

    @Test fun `quiz rejects duplicate and missing answers before sending`() = runBlocking {
        val client = HttpClient(MockEngine { error("No request should be made") })
        try {
            val repository = ApiAssignmentRepository(client, "https://example.com")
            assertFailsWith<IllegalArgumentException> { repository.submitQuiz("t", "q", listOf(QuizAnswer("x", "A"), QuizAnswer("x", "B"))) }
            assertFailsWith<IllegalArgumentException> { repository.submitQuiz("t", "q", listOf(QuizAnswer("x", " "))) }
            Unit
        } finally { client.close() }
    }

    @Test fun `upload uses multipart signing and submits resulting asset`() = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.host == "storage.example.com") {
                assertNull(request.headers[HttpHeaders.Authorization])
                respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"etag\""))
            } else {
                assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
                when (request.url.encodedPath) {
                    "/api/v1/upload/initiate" -> respond("""{"uploadId":"upload","key":"key"}""")
                    "/api/v1/upload/presigned-urls" -> respond("""[{"partNumber":1,"url":"https://storage.example.com/part"}]""")
                    "/api/v1/upload/complete" -> {
                        assertEquals("""{"uploadId":"upload","key":"key","parts":[{"PartNumber":1,"ETag":"\"etag\""}]}""", (request.body as TextContent).text)
                        respond("""{"asset_id":"asset"}""")
                    }
                    "/api/v1/learning/assignments/a/submit" -> {
                        assertEquals("""{"file_asset_id":"asset"}""", (request.body as TextContent).text)
                        respond("""{"data":{"id":"a","title":"Tugas","type":"file_upload","my_submission":{"status":"submitted"}}}""")
                    }
                    else -> error("Unexpected request")
                }
            }
        })
        try {
            val repository = ApiAssignmentRepository(client, "https://example.com")
            val asset = repository.uploadFile("token", "work.pdf", byteArrayOf(1, 2))
            val result = repository.submitFile("token", "a", asset)
            assertEquals("submitted", result.submission?.status)
            assertEquals(5, paths.size)
        } finally { client.close() }
    }

    @Test fun `does not submit after signed upload fails`() = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/v1/upload/initiate" -> respond("""{"uploadId":"u","key":"k"}""")
                "/api/v1/upload/presigned-urls" -> respond("""[{"partNumber":1,"url":"https://storage.example.com/part"}]""")
                else -> respond("", HttpStatusCode.InternalServerError)
            }
        })
        try {
            assertFailsWith<IllegalStateException> { ApiAssignmentRepository(client, "https://example.com").uploadFile("t", "x.zip", byteArrayOf(1)) }
            assertEquals(listOf("/api/v1/upload/initiate", "/api/v1/upload/presigned-urls", "/part"), paths)
        } finally { client.close() }
    }

    @Test fun `quiz errors preserve backend status`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"responseMessage":"Deadline passed"}""", HttpStatusCode.Forbidden) })
        try { assertEquals(403, assertFailsWith<AccountApiException> { ApiAssignmentRepository(client, "https://example.com").quiz("t", "q") }.statusCode) }
        finally { client.close() }
    }

    @Test fun `failed submission can retry using the uploaded asset without reupload`() = runBlocking {
        var submits = 0
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/v1/upload/initiate" -> respond("""{"uploadId":"u","key":"k"}""")
                "/api/v1/upload/presigned-urls" -> respond("""[{"partNumber":1,"url":"https://storage.example.com/part"}]""")
                "/part" -> respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "etag"))
                "/api/v1/upload/complete" -> respond("""{"asset_id":"asset"}""")
                "/api/v1/learning/assignments/a/submit" -> {
                    submits++
                    assertEquals("""{"file_asset_id":"asset"}""", (request.body as TextContent).text)
                    if (submits == 1) respond("{}", HttpStatusCode.ServiceUnavailable)
                    else respond("""{"data":{"id":"a","title":"Tugas","type":"file_upload","my_submission":{"status":"submitted"}}}""")
                }
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiAssignmentRepository(client, "https://example.com")
            val asset = repository.uploadFile("token", "work.pdf", byteArrayOf(1))
            assertFailsWith<AccountApiException> { repository.submitFile("token", "a", asset) }
            assertEquals("submitted", repository.submitFile("token", "a", asset).submission?.status)
            assertEquals(1, paths.count { it == "/api/v1/upload/initiate" })
        } finally { client.close() }
    }

    @Test fun `multipart upload sends every part before completion`() = runBlocking {
        var uploads = 0
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/upload/initiate" -> respond("""{"uploadId":"u","key":"k"}""")
                "/api/v1/upload/presigned-urls" -> {
                    assertEquals("""{"uploadId":"u","key":"k","partsCount":2}""", (request.body as TextContent).text)
                    respond("""[{"partNumber":2,"url":"https://storage.example.com/2"},{"partNumber":1,"url":"https://storage.example.com/1"}]""")
                }
                "/1", "/2" -> { uploads++; respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "etag$uploads")) }
                "/api/v1/upload/complete" -> {
                    assertEquals("""{"uploadId":"u","key":"k","parts":[{"PartNumber":1,"ETag":"etag1"},{"PartNumber":2,"ETag":"etag2"}]}""", (request.body as TextContent).text)
                    respond("""{"asset_id":"asset"}""")
                }
                else -> error("Unexpected request")
            }
        })
        try { assertEquals("asset", ApiAssignmentRepository(client, "https://example.com").uploadFile("token", "work.zip", ByteArray(5 * 1024 * 1024 + 1))) }
        finally { client.close() }
    }
}
