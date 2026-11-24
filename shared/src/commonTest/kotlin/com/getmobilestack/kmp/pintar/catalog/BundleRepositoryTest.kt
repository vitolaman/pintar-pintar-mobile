package com.getmobilestack.kmp.pintar.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class BundleRepositoryTest {
    @Test fun `public bundles use pagination and merchant filter`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/bundles", request.url.encodedPath)
            assertEquals("merchant-1", request.url.parameters["merchant_id"])
            assertEquals("2", request.url.parameters["page"])
            respond("""{"data":[{"id":"b","title":"Paket","description":"Belajar","merchant":{"id":"merchant-1","name":"Toko"},"items":[{"id":"c","type":"kelas","title":"Kelas","price":100,"is_available":true}],"price":80,"original_price":100,"cover_url":null,"discount_percent":20}],"meta":{"page":2,"total_page":3}}""")
        })
        try {
            val result = ApiBundleRepository(client, "https://example.com").list(2, "merchant-1")
            assertEquals(3, result.meta.totalPages)
            assertEquals("kelas", result.data.single().items.single().type)
            assertEquals(20, result.data.single().discountPercent)
        } finally { client.close() }
    }
}
