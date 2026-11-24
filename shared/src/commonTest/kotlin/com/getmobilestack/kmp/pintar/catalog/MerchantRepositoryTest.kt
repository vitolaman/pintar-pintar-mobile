package com.getmobilestack.kmp.pintar.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MerchantRepositoryTest {
    @Test fun `loads storefront and merchant catalog`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/merchants/toko" -> respond("""{"data":{"id":"m1","store_name":"Toko","average_rating":4.5,"skills":["Desain"]}}""")
                "/api/v1/catalog/items" -> {
                    assertEquals("m1", request.url.parameters["merchant_id"])
                    respond("""{"data":[],"meta":{"page":1,"total_page":0}}""")
                }
                else -> error("Unexpected path")
            }
        })
        try {
            val merchant = ApiMerchantRepository(client, "https://example.com").storefront("toko")
            assertEquals("Toko", merchant.name)
            assertEquals(emptyList(), ApiCatalogRepository(client, "https://example.com").getItems(CatalogType.ALL, "", 1, merchantId = merchant.id).data)
        } finally { client.close() }
    }
}
