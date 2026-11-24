package com.getmobilestack.kmp.pintar.jobs

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.encodedPath
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class JobsRepositoryTest {
    @Test fun `search requests job board and decodes paginated jobs`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/job-postings", request.url.encodedPath)
            assertEquals("desain", request.url.parameters["search"])
            assertEquals("2", request.url.parameters["page"])
            respond("""{"data":{"jobs":[{"id":"1","title":"Desainer","location":"Jakarta","salary":"Negotiable","requirements":"Pengalaman","skills":["UI"],"merchant":{"name":"Toko"},"contract_type":"full_time","work_type":"remote"}],"active_jobs":1},"meta":{"page":2,"total_page":3}}""")
        })
        try {
            val result = ApiJobsRepository(client, "https://example.com").search(" desain ", 2)
            assertEquals("Toko", result.data.jobs.single().merchant.name)
            assertEquals(3, result.meta.totalPages)
        } finally { client.close() }
    }

    @Test fun `saved jobs and save toggle require bearer token`() = runBlocking {
        val methods = mutableListOf<HttpMethod>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            methods += request.method
            if (request.url.encodedPath.endsWith("/saved")) respond("""{"data":[{"id":"1","title":"Guru","location":"Jakarta","salary":"-","requirements":"-","merchant":{"name":"Toko"},"contract_type":"full_time","work_type":"remote","is_saved":true,"is_open":false}],"meta":{"page":1,"total_page":1}}""") else respond("{}")
        })
        try {
            val repository = ApiJobsRepository(client, "https://example.com")
            assertEquals(false, repository.saved("secret", 1).data.single().isOpen)
            repository.setSaved("secret", "1", true)
            repository.setSaved("secret", "1", false)
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Put, HttpMethod.Delete), methods)
        } finally { client.close() }
    }
}
