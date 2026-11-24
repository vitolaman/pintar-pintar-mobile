package com.getmobilestack.kmp.pintar.community

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityRepositoryTest {
    @Test fun `groups and feed are paginated and join reply like use correct contracts`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/community-groups" -> { assertEquals("true", request.url.parameters["joined"]); respond("""{"data":[{"id":"g","name":"Grup","description":"Topik","access":"public","membership":"member","member_count":1,"owner":{"id":"u","name":"Ana"}}],"meta":{"page":1,"total_page":1}}""") }
                "/api/v1/community-threads" -> {
                    if (request.method == HttpMethod.Get) { assertEquals("g", request.url.parameters["group_id"]); respond("""{"data":[{"id":"t","author":{"id":"u","name":"Ana"},"content":"Halo","created_at":"date"}],"meta":{"page":1,"total_page":1}}""") }
                    else { assertEquals("""{"group_id":"g","content":"Halo"}""", (request.body as TextContent).text); respond("{}") }
                }
                "/api/v1/community-groups/g/join" -> respond("{}")
                "/api/v1/community-threads/t/replies" -> { assertEquals("""{"content":"Jawab"}""", (request.body as TextContent).text); respond("{}") }
                "/api/v1/community-threads/t/like" -> { assertEquals(HttpMethod.Put, request.method); respond("{}") }
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiCommunityRepository(client, "https://example.com")
            assertEquals("Grup", repository.groups("token", 1, "", true).data.single().name)
            assertEquals("Halo", repository.threads("token", "g", 1).data.single().content)
            repository.join("token", "g"); repository.createThread("token", "g", " Halo "); repository.reply("token", "t", " Jawab "); repository.like("token", "t", true)
        } finally { client.close() }
    }
}
