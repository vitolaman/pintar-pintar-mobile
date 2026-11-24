package com.getmobilestack.kmp.pintar.review

import com.getmobilestack.kmp.pintar.detail.ProductKind
import com.getmobilestack.kmp.pintar.detail.ProductReference
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.encodedPath
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewRepositoryTest {
    @Test fun `loads paginated digital reviews`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/reviews/digital-products/product", request.url.encodedPath)
            assertEquals("2", request.url.parameters["page"])
            respond("""{"data":{"average_rating":4.5,"review_count":1,"reviews":[{"id":"1","rating":5,"comment":"Bagus","reviewer_name":"Ana"}]},"meta":{"page":2,"total_page":3}}""")
        })
        try {
            val result = ApiReviewRepository(client, "https://example.com").getReviews(ProductReference("product", ProductKind.DIGITAL), 2)
            assertEquals("Ana", result.data.reviews.single().reviewerName)
            assertEquals(3, result.meta.totalPages)
        } finally { client.close() }
    }

    @Test fun `authenticated review actions use correct payloads`() = runBlocking {
        val requests = mutableListOf<Pair<String, HttpMethod>>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
            requests += request.url.encodedPath to request.method
            when (request.url.encodedPath) {
                "/api/v1/reviews" -> assertEquals("""{"product_id":"p","rating":5,"comment":"Bagus"}""", (request.body as TextContent).text)
                "/api/v1/reviews/r/replies" -> assertEquals("""{"comment":"Terima kasih"}""", (request.body as TextContent).text)
            }
            respond("{}")
        })
        try {
            val repository = ApiReviewRepository(client, "https://example.com")
            repository.createReview(ProductReference("p", ProductKind.DIGITAL), "token", 5, "Bagus")
            repository.setHelpful("r", "token", true)
            repository.setHelpful("r", "token", false)
            repository.reply("r", "token", "Terima kasih")
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete, HttpMethod.Post), requests.map { it.second })
        } finally { client.close() }
    }
}
