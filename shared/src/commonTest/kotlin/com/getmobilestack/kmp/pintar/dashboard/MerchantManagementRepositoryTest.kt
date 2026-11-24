package com.getmobilestack.kmp.pintar.dashboard

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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MerchantManagementRepositoryTest {
    @Test fun `registers merchant and creates and updates classes`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/merchant/register" -> { assertTrue((request.body as TextContent).text.contains("\"terms_accepted\":true")); respond("{}") }
                "/api/v1/merchant/classes", "/api/v1/classes/c" -> {
                    if (request.method != HttpMethod.Get) assertTrue((request.body as TextContent).text.contains("\"title\":\"Kelas\""))
                    respond("""{"data":{"id":"c","title":"Kelas","description":null,"type":"kelas","status":"draft","original_price":1000,"discount_price":null}}""")
                }
                else -> error("Unexpected path")
            }
        })
        try {
            val repository = ApiMerchantManagementRepository(client, "https://example.com")
            repository.register("token", MerchantRegistration("Toko", "Deskripsi", true, "individual", city = "Jakarta", phone = "123", productTypes = listOf("kelas")))
            val draft = ClassDraft("Kelas", type = "kelas", originalPrice = 1000.0)
            assertEquals("c", repository.createClass("token", draft).id)
            assertEquals("Kelas", repository.classDetails("token", "c").title)
            assertEquals("draft", repository.updateClass("token", "c", draft).status)
            assertFailsWith<IllegalArgumentException> { repository.createClass("token", draft.copy(discountPrice = 2000.0)) }
            Unit
        } finally { client.close() }
    }

    @Test fun `chapter and video management uses class workspace endpoints`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/classes/c/chapters" -> if (request.method == HttpMethod.Get) respond("""{"data":[{"id":"ch","title":"Bab","videos":[{"id":"v","title":"Video","source":"link"}]}]}""") else { assertEquals("""{"title":"Bab","description":"Deskripsi"}""", (request.body as TextContent).text); respond("{}") }
                "/api/v1/classes/c/chapters/ch/videos" -> { assertEquals("""{"title":"Video","youtube_url":"https://example.com/video"}""", (request.body as TextContent).text); respond("{}") }
                "/api/v1/classes/c/chapters/ch/videos/v", "/api/v1/classes/c/chapters/ch" -> { assertEquals(HttpMethod.Delete, request.method); respond("{}") }
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiMerchantManagementRepository(client, "https://example.com")
            assertEquals("Video", repository.chapters("token", "c").single().videos.single().title)
            repository.addChapter("token", "c", "Bab", "Deskripsi")
            repository.addVideoLink("token", "c", "ch", "Video", "https://example.com/video")
            repository.deleteVideo("token", "c", "ch", "v")
            repository.deleteChapter("token", "c", "ch")
        } finally { client.close() }
    }
}
