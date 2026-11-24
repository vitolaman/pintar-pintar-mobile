package com.getmobilestack.kmp.pintar.help

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HelpRepositoryTest {
    @Test fun `loads FAQ and submits feedback`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/faqs" -> respond("""{"data":{"categories":[{"id":"c","name":"Umum","faqs":[{"id":"1","question":"Apa?","answer":"Jawaban"}]}]}}""")
                "/api/v1/help-tickets" -> {
                    assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
                    assertEquals("""{"ticket_type":"saran","message":"Halo"}""", (request.body as TextContent).text)
                    respond("{}")
                }
                else -> error("Unexpected path")
            }
        })
        try {
            val repository = ApiHelpRepository(client, "https://example.com")
            assertEquals("Jawaban", repository.faqs().single().faqs.single().answer)
            repository.sendFeedback("secret", "saran", " Halo ")
        } finally { client.close() }
    }
}
