package com.getmobilestack.kmp.pintar.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PromotionRepositoryTest {
    @Test fun `loads digital promotions from public endpoint`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/promo/items", request.url.encodedPath)
            assertEquals("digital", request.url.parameters["type"])
            assertEquals("6", request.url.parameters["limit"])
            respond("""{"data":[{"id":"p","type":"digital","title":"Template","price":5000,"merchant":{"name":"Toko"}}]}""")
        })
        try { assertEquals("Template", ApiPromotionRepository(client, "https://example.com").items("digital").single().title) }
        finally { client.close() }
    }
}
