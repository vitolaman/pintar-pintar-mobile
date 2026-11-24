package com.getmobilestack.kmp.pintar.learning

import com.getmobilestack.kmp.pintar.account.AccountApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClassWorkspaceRepositoryTest {
    @Test fun `loads paginated discussion threads with class id and bearer token`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/discussions/threads", request.url.encodedPath)
            assertEquals("class-1", request.url.parameters["class_id"])
            assertEquals("2", request.url.parameters["page"])
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            respond("""{"data":[{"id":"t","title":"Question","content":"Help","author":{"name":"Ana","role":"learner"},"comment_count":1,"comments":[{"id":"c","content":"Reply","author":{"name":"Mentor"}}]}],"meta":{"page":2,"total_page":3}}""")
        })
        try {
            val result = ApiClassWorkspaceRepository(client, "https://example.com").threads("secret", "class-1", 2)
            assertEquals("Mentor", result.data.single().comments.single().author.name)
            assertEquals(3, result.meta.totalPages)
        } finally { client.close() }
    }

    @Test fun `posts threads and comments with validated payloads`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/discussions/threads" -> assertEquals("""{"class_id":"c","title":"Title","content":"Question"}""", (request.body as TextContent).text)
                "/api/v1/discussions/comments" -> assertEquals("""{"thread_id":"t","content":"Reply"}""", (request.body as TextContent).text)
                else -> error("Unexpected request")
            }
            respond("{}", HttpStatusCode.Created)
        })
        try {
            val repository = ApiClassWorkspaceRepository(client, "https://example.com")
            repository.createThread("secret", "c", " Title ", " Question ")
            repository.addComment("secret", "t", " Reply ")
            assertFailsWith<IllegalArgumentException> { repository.addComment("secret", "t", " ") }
        } finally { client.close() }
        Unit
    }

    @Test fun `loads grades and meeting and checks in using only feedback`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/learning/classes/c/grades" -> respond("""{"data":{"assignments":[{"title":"Quiz","status":"graded","score":90}],"participation":80,"average_score":90}}""")
                "/api/v1/learning/meetings/m" -> respond("""{"data":{"meeting":{"id":"m","title":"Live","has_started":true,"status":"upcoming","mentor":{"name":"Ana"},"live_url":"https://example.com/live"},"class":{"id":"c","title":"Kelas"},"mentor_names":["Ana"],"my_attendance":null}}""")
                "/api/v1/learning/meetings/m/check-in" -> {
                    assertEquals("""{"feedback":"Great"}""", (request.body as TextContent).text)
                    respond("""{"data":{"meeting":{"id":"m","title":"Live","has_started":true,"status":"upcoming"},"my_attendance":{"status":"hadir"}}}""")
                }
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiClassWorkspaceRepository(client, "https://example.com")
            assertEquals(90.0, repository.grades("secret", "c").assignments.single().score)
            assertTrue(repository.meeting("secret", "m").meeting.hasStarted)
            assertEquals("Kelas", repository.meeting("secret", "m").course?.title)
            assertEquals("Ana", repository.meeting("secret", "m").meeting.mentor?.name)
            assertEquals("hadir", repository.checkIn("secret", "m", " Great ").myAttendance?.status)
        } finally { client.close() }
    }

    @Test fun `preserves backend errors`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"responseMessage":"Not enrolled"}""", HttpStatusCode.Forbidden) })
        try {
            val error = assertFailsWith<AccountApiException> { ApiClassWorkspaceRepository(client, "https://example.com").grades("secret", "c") }
            assertEquals(403, error.statusCode)
            assertEquals("Not enrolled", error.message)
        } finally { client.close() }
    }
}
