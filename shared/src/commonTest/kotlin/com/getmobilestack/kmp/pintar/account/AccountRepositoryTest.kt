package com.getmobilestack.kmp.pintar.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountRepositoryTest {
    @Test
    fun `validates sign up fields and terms`() {
        assertNotNull(validateSignUp("", "user@example.com", "password1", "password1", true))
        assertNotNull(validateSignUp("User", "wrong", "password1", "password1", true))
        assertNotNull(validateSignUp("User", "user@example.com", "password", "password", true))
        assertNotNull(validateSignUp("User", "user@example.com", "password1", "other", true))
        assertNotNull(validateSignUp("User", "user@example.com", "password1", "password1", false))
        assertNull(validateSignUp("User", "user@example.com", "password1", "password1", true))
    }

    @Test
    fun `sign in uses backend endpoint and decodes token`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/auth/sign-in", request.url.encodedPath)
            respond("""{"data":{"token":"token-123","user":{"id":"1","name":"User","email":"user@example.com","is_merchant":false}}}""")
        })
        try {
            val session = ApiAccountRepository(client).signIn(" user@example.com ", "password1")
            assertEquals("token-123", session.token)
            assertEquals("User", session.user.name)
        } finally { client.close() }
    }

    @Test
    fun `profile requests send bearer token`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertEquals("/api/v1/profile", request.url.encodedPath)
            respond("""{"data":{"id":"1","name":"User","email":"user@example.com","phone":null,"onboarding":{}}}""")
        })
        try { assertEquals("User", ApiAccountRepository(client).getProfile("secret").name) }
        finally { client.close() }
    }

    @Test
    fun `backend errors retain status and message`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"responseMessage":"Invalid credentials"}""", HttpStatusCode.Unauthorized) })
        try {
            val error = assertFailsWith<AccountApiException> { ApiAccountRepository(client).signIn("a@b.com", "wrong") }
            assertEquals(401, error.statusCode)
            assertEquals("Invalid credentials", error.message)
        } finally { client.close() }
    }

    @Test
    fun `learning returns owned items`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/profile/learning", request.url.encodedPath)
            respond("""{"data":[{"access_id":"a","item_id":"i","title":"Kelas","item_type":"kelas","completion_percentage":25,"progress_status":"in_progress"}]}""")
        })
        try { assertEquals(25, ApiAccountRepository(client).getLearning("secret").single().progress) }
        finally { client.close() }
    }

    @Test
    fun `sign up sends expected registration fields`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/auth/sign-up", request.url.encodedPath)
            assertEquals("""{"name":"User","email":"user@example.com","password":"password1"}""", (request.body as TextContent).text)
            respond("""{"data":{"token":"token","user":{"id":"1","name":"User","email":"user@example.com"}}}""")
        })
        try { assertEquals("token", ApiAccountRepository(client).signUp(" User ", " user@example.com ", "password1").token) }
        finally { client.close() }
    }

    @Test
    fun `reset request sends trimmed email`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/auth/password-reset", request.url.encodedPath)
            assertEquals("""{"email":"user@example.com"}""", (request.body as TextContent).text)
            respond("{}")
        })
        try { ApiAccountRepository(client).requestPasswordReset(" user@example.com ") }
        finally { client.close() }
    }

    @Test
    fun `profile update sends null to clear optional fields`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertEquals("""{"name":"User","phone":null,"headline":null,"bio":null,"avatar_asset_id":null}""", (request.body as TextContent).text)
            respond("""{"data":{"id":"1","name":"User","email":"user@example.com"}}""")
        })
        try { assertEquals("User", ApiAccountRepository(client).updateProfile("secret", ProfileUpdate("User", null, null, null, null)).name) }
        finally { client.close() }
    }

    @Test
    fun `onboarding sends role and skills`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/v1/profile/onboarding", request.url.encodedPath)
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertEquals("""{"role":"custom","custom_role":"Guru","skills":["Desain"]}""", (request.body as TextContent).text)
            respond("{}")
        })
        try { ApiAccountRepository(client).updateOnboarding("secret", OnboardingUpdate("custom", "Guru", listOf("Desain"))) }
        finally { client.close() }
    }

    @Test fun `profile update preserves or replaces avatar asset`() = runBlocking {
        val bodies = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            bodies += (request.body as TextContent).text
            respond("""{"data":{"id":"1","name":"User","email":"user@example.com"}}""")
        })
        try {
            val repository = ApiAccountRepository(client)
            repository.updateProfile("secret", ProfileUpdate("User", null, null, null, "existing"))
            repository.updateProfile("secret", ProfileUpdate("User", null, null, null, "replacement"))
            assertEquals(true, bodies[0].contains("\"avatar_asset_id\":\"existing\""))
            assertEquals(true, bodies[1].contains("\"avatar_asset_id\":\"replacement\""))
        } finally { client.close() }
    }

    @Test fun `onboarding validates and deduplicates answers`() {
        assertFailsWith<IllegalArgumentException> { onboardingPayload(null, "", emptyList()) }
        assertFailsWith<IllegalArgumentException> { onboardingPayload("custom", " ", emptyList()) }
        assertFailsWith<IllegalArgumentException> { onboardingPayload("custom", "x".repeat(61), emptyList()) }
        assertFailsWith<IllegalArgumentException> { onboardingPayload("mahasiswa", "", listOf("x".repeat(51))) }
        assertFailsWith<IllegalArgumentException> { onboardingPayload("mahasiswa", "", (1..21).map { "skill$it" }) }
        assertEquals(OnboardingUpdate("custom", "Guru", listOf("Desain", "UI")), onboardingPayload("custom", " Guru ", listOf(" Desain ", "desain", "", "UI")))
        assertEquals(null, onboardingPayload("mahasiswa", "unused", emptyList()).customRole)
    }

    @Test fun `avatar validates file extensions and size`() {
        assertNull(avatarFileError("foto.JPEG", MAX_AVATAR_BYTES.toLong()))
        assertNotNull(avatarFileError("foto.gif", 1))
        assertNotNull(avatarFileError("foto.png", 0))
        assertNotNull(avatarFileError("foto.webp", MAX_AVATAR_BYTES.toLong() + 1))
    }
}
