package com.getmobilestack.kmp.pintar.shopping

import com.getmobilestack.kmp.pintar.account.AccountApiException
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

class ShoppingRepositoryTest {
    private val cart = """{"data":{"items":[{"id":"entry-1","item":{"id":"product-1","type":"kelas","title":"Kelas","price":12000,"is_available":true}}],"subtotal":12000,"item_count":1}}"""

    @Test
    fun `loads cart with bearer token`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/cart", request.url.encodedPath)
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            respond(cart)
        })
        try {
            val result = ApiShoppingRepository(client).getCart("secret")
            assertEquals("entry-1", result.items.single().id)
            assertEquals(12000.0, result.subtotal)
        } finally { client.close() }
    }

    @Test
    fun `add sends product id and remove uses entry id`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                assertEquals("/api/v1/cart/items", request.url.encodedPath)
                assertEquals("""{"id":"product-1"}""", (request.body as TextContent).text)
            } else {
                assertEquals(HttpMethod.Delete, request.method)
                assertEquals("/api/v1/cart/items/entry-1", request.url.encodedPath)
            }
            respond(cart)
        })
        try {
            val repository = ApiShoppingRepository(client)
            repository.addToCart("secret", "product-1")
            repository.removeFromCart("secret", "entry-1")
        } finally { client.close() }
        Unit
    }

    @Test
    fun `wishlist uses pagination and backend entry IDs`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            when (request.method) {
                HttpMethod.Get -> {
                    assertEquals("2", request.url.parameters["page"])
                    respond("""{"data":[],"meta":{"page":2,"total_page":3}}""")
                }
                HttpMethod.Post -> {
                    assertEquals("""{"id":"product-1"}""", (request.body as TextContent).text)
                    respond("{}", HttpStatusCode.Created)
                }
                else -> {
                    assertEquals("/api/v1/wishlist/items/entry-1", request.url.encodedPath)
                    respond("{}")
                }
            }
        })
        try {
            val repository = ApiShoppingRepository(client)
            assertEquals(3, repository.getWishlist("secret", 2).meta.totalPages)
            repository.addToWishlist("secret", "product-1")
            repository.removeFromWishlist("secret", "entry-1")
        } finally { client.close() }
    }

    @Test
    fun `reports API errors without pretending mutation succeeded`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"responseMessage":"Not available"}""", HttpStatusCode.Conflict) })
        try {
            val error = assertFailsWith<AccountApiException> { ApiShoppingRepository(client).addToCart("secret", "1") }
            assertEquals(409, error.statusCode)
            assertEquals("Not available", error.message)
        } finally { client.close() }
    }

    @Test
    fun `checkout preview sends products and codes without creating an order`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/orders/preview", request.url.encodedPath)
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertEquals("""{"items":[{"id":"product-1"}],"codes":["PROMO"]}""", (request.body as TextContent).text)
            respond("""{"data":{"items":[],"codes":[],"rejected_codes":[],"subtotal":10000,"discount_amount":1000,"total_amount":9000}}""")
        })
        try { assertEquals(9000.0, ApiShoppingRepository(client).previewCheckout("secret", listOf("product-1"), listOf("PROMO")).totalAmount) }
        finally { client.close() }
    }

    @Test fun `preview decodes selected items applied codes and rejections`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("""{"items":[{"id":"p2"}],"codes":["SAVE"]}""", (request.body as TextContent).text)
            respond("""{"data":{"items":[{"item_id":"p2","title":"Kelas","price":10000,"discount_amount":1000}],"codes":[{"code":"SAVE","merchant_name":"Toko","discount_amount":1000}],"rejected_codes":[{"code":"OLD","reason":"expired"}],"subtotal":10000,"discount_amount":1000,"total_amount":9000}}""")
        })
        try {
            val preview = ApiShoppingRepository(client, "https://example.com").previewCheckout("secret", listOf("p2"), listOf("SAVE"))
            assertEquals("p2", preview.items.single().itemId)
            assertEquals("Toko", preview.codes.single().merchantName)
            assertEquals("expired", preview.rejectedCodes.single().reason)
        } finally { client.close() }
    }
}
