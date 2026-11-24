package com.getmobilestack.kmp.pintar.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountSettingsRepositoryTest {
    @Test fun `notification preferences retain unknown fields on update`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertEquals("/api/v1/profile/notification-preferences", request.url.encodedPath)
            if (request.method == HttpMethod.Get) respond("""{"data":{"email_learner_announcement":true,"future_setting":false}}""")
            else { assertEquals("""{"email_learner_announcement":false,"future_setting":false}""", (request.body as TextContent).text); respond("{}") }
        })
        try {
            val repository = ApiAccountSettingsRepository(client, "https://example.com")
            val values = repository.notificationPreferences("secret")
            repository.updateNotificationPreferences("secret", values + ("email_learner_announcement" to false))
        } finally { client.close() }
    }
    @Test fun `security changes return replacement tokens and deletion uses delete`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/auth/password" -> { assertEquals(HttpMethod.Patch, request.method); assertEquals("""{"current_password":"old","new_password":"newpass1"}""", (request.body as TextContent).text); respond("""{"data":{"token":"replacement"}}""") }
                "/api/v1/auth/end-other-sessions" -> respond("""{"data":{"token":"other"}}""")
                "/api/v1/users/me" -> { assertEquals(HttpMethod.Delete, request.method); respond("{}") }
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiAccountSettingsRepository(client, "https://example.com")
            assertEquals("replacement", repository.changePassword("secret", "old", "newpass1"))
            assertEquals("other", repository.endOtherSessions("secret"))
            repository.deleteAccount("secret")
        } finally { client.close() }
    }
    @Test fun `security failure preserves status`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"responseMessage":"Forbidden"}""", HttpStatusCode.Forbidden) })
        try { assertEquals(403, assertFailsWith<AccountApiException> { ApiAccountSettingsRepository(client, "https://example.com").endOtherSessions("secret") }.statusCode) }
        finally { client.close() }
    }
    @Test fun `successful security change rejects missing replacement token`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"data":{}}""") })
        try { assertFailsWith<ReplacementSessionException> { ApiAccountSettingsRepository(client, "https://example.com").endOtherSessions("secret") }; Unit }
        finally { client.close() }
    }
}
