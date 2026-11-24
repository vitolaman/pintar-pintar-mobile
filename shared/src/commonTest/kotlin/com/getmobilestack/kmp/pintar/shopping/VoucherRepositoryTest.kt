package com.getmobilestack.kmp.pintar.shopping

import com.getmobilestack.kmp.pintar.account.AccountApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VoucherRepositoryTest {
    @Test fun `deduplicates public vouchers and claims with bearer`() = runBlocking {
        val voucher = """{"id":"v","name":"Hemat","code":"SAVE","merchant_name":"Toko","discount_type":"percentage","discount_value":10}"""
        val client = HttpClient(MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> { assertEquals("/api/v1/promo/vouchers", request.url.encodedPath); respond("""{"data":{"featured":[$voucher],"vouchers":[$voucher]}}""") }
                else -> { assertEquals("/api/v1/vouchers/v/claim", request.url.encodedPath); assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization]); respond("{}") }
            }
        })
        try {
            val repository = ApiVoucherRepository(client, "https://example.com")
            assertEquals("SAVE", repository.list().single().code)
            repository.claim("secret", "v")
        } finally { client.close() }
    }

    @Test fun `claim failure preserves status`() = runBlocking {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.Unauthorized) })
        try { assertEquals(401, assertFailsWith<AccountApiException> { ApiVoucherRepository(client, "https://example.com").claim("bad", "v") }.statusCode) }
        finally { client.close() }
    }

    @Test fun `cart selection excludes unavailable entries and reconciles removal`() {
        val entries = listOf(
            ShoppingEntry("entry1", ShoppingProduct("p1", "kelas", "A", 10.0)),
            ShoppingEntry("entry2", ShoppingProduct("p2", "digital", "B", 20.0, isAvailable = false)),
            ShoppingEntry("entry3", ShoppingProduct("p3", "bundle", "C", 30.0)),
        )
        assertEquals(listOf("p1", "p3"), selectedCartEntries(entries, setOf("entry1", "entry2", "entry3")).map { it.item.id })
        assertEquals(setOf("entry3"), reconcileCartSelection(entries.drop(1), setOf("entry1", "entry2", "entry3")))
        assertEquals(emptyList(), selectedCartEntries(entries, emptySet()))
    }
}
