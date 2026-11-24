package com.getmobilestack.kmp.pintar.catalog

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRepositoryTest {
    @Test fun `home sections use live endpoints and decode responses`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/home/statistics" -> {
                    assertEquals(null, request.url.parameters["limit"])
                    respond("""{"data":{"active_students":10,"learning_products":2,"digital_products":3,"platform_rating":4.5}}""")
                }
                "/api/v1/home/bootcamps" -> {
                    assertEquals("10", request.url.parameters["limit"])
                    respond("""{"data":[{"id":"1","type":"bootcamp","title":"Kelas","price":1000,"merchant":{"name":"Toko"}}]}""")
                }
                "/api/v1/home/testimonials" -> respond("""{"data":[{"id":"t","rating":5,"comment":"Bagus","user_name":"Ana","class_title":"Kelas"}]}""")
                else -> error("Unexpected path")
            }
        })
        try {
            val repository = ApiHomeRepository(client, "https://example.com")
            assertEquals(10, repository.statistics().activeStudents)
            assertEquals("Kelas", repository.featured(FeaturedSection.BOOTCAMPS).single().title)
            assertEquals("Ana", repository.testimonials().single().userName)
        } finally { client.close() }
    }
}
