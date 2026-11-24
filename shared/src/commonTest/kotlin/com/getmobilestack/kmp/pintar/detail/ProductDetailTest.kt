package com.getmobilestack.kmp.pintar.detail

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProductDetailTest {
    @Test
    fun `class and bootcamp both use the class API but different website routes`() {
        assertEquals("classes", ProductKind.CLASS.apiPath)
        assertEquals("classes", ProductKind.BOOTCAMP.apiPath)
        assertEquals("https://pintarpintar.id/bootcamp/123", ProductReference("123", ProductKind.BOOTCAMP).websiteUrl())
        assertEquals(ProductKind.DIGITAL, ProductKind.fromApiType("digital"))
        assertNull(ProductKind.fromApiType("unknown"))
    }

    @Test
    fun `fetches and decodes digital product details`() = runBlocking {
        var requestedPath = ""
        val client = HttpClient(MockEngine { request ->
            requestedPath = request.url.encodedPath
            respond("""{"data":{"id":"123","type":"digital","title":"Template","price":10000,"merchant":{"name":"Toko"},"description":"Contoh","files":[{"name":"file.zip","format":"zip"}],"unknown":true}}""")
        })
        try {
            val product = ApiProductDetailRepository(client, "https://example.com").getDetail(ProductReference("123", ProductKind.DIGITAL))
            assertEquals("/api/v1/catalog/digital-products/123", requestedPath)
            assertEquals("Template", product.title)
            assertEquals("file.zip", product.files.single().name)
            assertEquals("Toko", product.merchant.name)
        } finally {
            client.close()
        }
    }

    @Test
    fun `rejects failed requests`() = runBlocking {
        val client = HttpClient(MockEngine { respond("not found", HttpStatusCode.NotFound) })
        try {
            assertFailsWith<IllegalStateException> {
                ApiProductDetailRepository(client).getDetail(ProductReference("missing", ProductKind.CLASS))
            }
        } finally {
            client.close()
        }
        Unit
    }
}
