package com.getmobilestack.kmp.pintar.dashboard

import com.getmobilestack.kmp.pintar.account.AccountApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DashboardRepositoryTest {
    @Test fun `merchant summary and paginated classes use bearer and period`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/merchant/dashboard" -> {
                    assertEquals("30", request.url.parameters["period_days"])
                    respond("""{"data":{"revenue":{"current":1000},"transactions":{"current":2},"students":{"current":1},"catalog_total":3,"rating_average":null,"activities":[]}}""")
                }
                "/api/v1/merchant/classes" -> {
                    assertEquals("2", request.url.parameters["page"])
                    respond("""{"data":[{"id":"1","title":"Kelas","status":"published","type":"kelas"}],"meta":{"page":2,"total_page":3}}""")
                }
                else -> error("Unexpected path")
            }
        })
        try {
            val repository = ApiDashboardRepository(client, "https://example.com")
            assertEquals(1000.0, repository.merchantDashboard("secret", 30).revenue.current)
            assertEquals(3, repository.merchantClasses("secret", 2).meta.totalPages)
        } finally { client.close() }
    }

    @Test fun `mentor dashboard decodes summary and teaching classes`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/mentor/dashboard" -> respond("""{"data":{"stats":{"active_classes":2,"total_students":10,"upcoming_sessions":1,"rating":4.5,"review_count":3},"upcoming_sessions":[],"recent_messages":[],"class_progress":[]}}""")
                "/api/v1/mentor/classes" -> respond("""{"data":[{"id":"c","title":"Kelas","status":"aktif","merchant":{"name":"Toko"}}]}""")
                else -> error("Unexpected path")
            }
        })
        try {
            val repository = ApiDashboardRepository(client, "https://example.com")
            assertEquals(10, repository.mentorDashboard("secret").stats.totalStudents)
            assertEquals("Kelas", repository.mentorClasses("secret").single().title)
        } finally { client.close() }
    }

    @Test fun `role access error preserves backend status`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"responseMessage":"Forbidden"}""", HttpStatusCode.Forbidden) })
        try { assertEquals(403, assertFailsWith<AccountApiException> { ApiDashboardRepository(client, "https://example.com").merchantDashboard("secret", 30) }.statusCode) }
        finally { client.close() }
    }

    @Test fun `merchant product and customer lists use paginated endpoints`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            assertEquals("2", request.url.parameters["page"])
            when (request.url.encodedPath) {
                "/api/v1/merchant/digital-products" -> respond("""{"data":[{"id":"p","title":"Template","status":"published","price":10000,"downloads":3}],"meta":{"page":2,"total_page":3}}""")
                "/api/v1/merchant/customers" -> respond("""{"data":[{"id":"c","name":"Ana","email":"a@example.com","classes_enrolled":1,"completion_rate":50}],"meta":{"page":2,"total_page":2}}""")
                else -> error("Unexpected path")
            }
        })
        try {
            val repository = ApiDashboardRepository(client, "https://example.com")
            assertEquals(10000.0, repository.merchantProducts("secret", 2).data.single().price)
            assertEquals("Ana", repository.merchantCustomers("secret", 2).data.single().name)
        } finally { client.close() }
    }

    @Test fun `merchant sales and analytics use role authenticated endpoints`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/merchant/sales" -> { assertEquals("Ana", request.url.parameters["search"]); respond("""{"data":[{"id":"s","ordered_at":"date","buyer_name":"Ana","item_title":"Kelas","net_amount":1000,"status":"paid"}],"meta":{"page":1,"total_page":1}}""") }
                "/api/v1/merchant/analytics/summary" -> { assertEquals("month", request.url.parameters["period"]); respond("""{"data":{"conversion_rate":{"value":10},"retention_rate":{"value":null},"average_order_value":{"value":1000,"change_percent":5}}}""") }
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiDashboardRepository(client, "https://example.com")
            assertEquals("Ana", repository.merchantSales("secret", 1, "Ana").data.single().buyerName)
            assertEquals(1000.0, repository.merchantAnalytics("secret", "month").averageOrderValue.value)
        } finally { client.close() }
    }

    @Test fun `mentor registration and profile updates use backend contracts`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v1/mentor/register" -> { assertEquals(io.ktor.http.HttpMethod.Post, request.method); respond("{}") }
                "/api/v1/mentor/profile" -> respond("""{"data":{"id":"m","name":"Ana","phone":"123","expertise_list":["Desain"],"experience_years":2,"education":"S1","linkedin_url":"https://example.com"}}""")
                else -> error("Unexpected request")
            }
        })
        try {
            val repository = ApiDashboardRepository(client, "https://example.com")
            repository.registerMentor("secret", MentorRegistration("123", null, null, "Desain", 2, "S1", null, "https://example.com", "cv", "certificate"))
            assertEquals("Ana", repository.mentorProfile("secret").name)
            assertEquals(listOf("Desain"), repository.updateMentorProfile("secret", MentorProfileUpdate("123", null, null, listOf("Desain"), 2, "S1", null, "https://example.com")).skills)
        } finally { client.close() }
    }
}
