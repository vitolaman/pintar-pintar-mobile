package com.getmobilestack.kmp.pintar.dashboard

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.config.ApiConfig
import com.getmobilestack.kmp.pintar.catalog.CatalogMeta
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable data class Metric(val current: Double = 0.0)
@Serializable data class DashboardActivity(@SerialName("actor_name") val actorName: String, @SerialName("item_title") val itemTitle: String, val type: String)
@Serializable data class MerchantDashboard(
    val revenue: Metric, val transactions: Metric, val students: Metric,
    @SerialName("catalog_total") val catalogTotal: Int,
    @SerialName("rating_average") val rating: Double? = null,
    @SerialName("review_count") val reviewCount: Int = 0,
    val activities: List<DashboardActivity> = emptyList(),
)
@Serializable data class MentorStats(@SerialName("active_classes") val activeClasses: Int, @SerialName("total_students") val totalStudents: Int, @SerialName("upcoming_sessions") val upcomingSessions: Int, val rating: Double, @SerialName("review_count") val reviewCount: Int)
@Serializable data class UpcomingSession(val id: String, val title: String, val date: String, val time: String? = null, @SerialName("class_title") val classTitle: String)
@Serializable data class MentorClassProgress(@SerialName("class_id") val classId: String, val title: String, @SerialName("enrolled_count") val enrolledCount: Int, @SerialName("average_progress") val averageProgress: Double)
@Serializable data class MentorMessage(val id: String, val content: String, @SerialName("user_name") val userName: String, @SerialName("class_title") val classTitle: String)
@Serializable data class MentorDashboard(val stats: MentorStats, @SerialName("upcoming_sessions") val upcomingSessions: List<UpcomingSession> = emptyList(), @SerialName("class_progress") val classProgress: List<MentorClassProgress> = emptyList(), @SerialName("recent_messages") val recentMessages: List<MentorMessage> = emptyList())
@Serializable data class DashboardClass(val id: String, val title: String, val status: String, val type: String? = null)
@Serializable data class DashboardProduct(val id: String, val title: String, val status: String, val price: Double, val downloads: Int = 0)
@Serializable data class DashboardCustomer(val id: String, val name: String, val email: String, @SerialName("classes_enrolled") val classesEnrolled: Int = 0, @SerialName("completion_rate") val completionRate: Double = 0.0)
@Serializable data class MerchantSale(val id: String, @SerialName("ordered_at") val orderedAt: String, @SerialName("buyer_name") val buyerName: String, @SerialName("item_title") val itemTitle: String, @SerialName("net_amount") val netAmount: Double, val status: String)
@Serializable data class MerchantSalesPage(val data: List<MerchantSale>, val meta: CatalogMeta)
@Serializable data class AnalyticsMetric(val value: Double? = null, @SerialName("change_percent") val changePercent: Double? = null)
@Serializable data class MerchantAnalytics(@SerialName("conversion_rate") val conversionRate: AnalyticsMetric, @SerialName("retention_rate") val retentionRate: AnalyticsMetric, @SerialName("average_order_value") val averageOrderValue: AnalyticsMetric)
@Serializable data class MentorProfile(val id: String, val name: String, val phone: String, val headline: String? = null, val bio: String? = null, @SerialName("expertise_list") val skills: List<String> = emptyList(), @SerialName("experience_years") val experienceYears: Int, val education: String, @SerialName("portfolio_url") val portfolioUrl: String? = null, @SerialName("linkedin_url") val linkedinUrl: String)
@Serializable data class MentorProfileUpdate(val phone: String, val headline: String?, val bio: String?, @SerialName("expertise_list") val skills: List<String>, @SerialName("experience_years") val experienceYears: Int, val education: String, @SerialName("portfolio_url") val portfolioUrl: String?, @SerialName("linkedin_url") val linkedinUrl: String)
@Serializable data class MentorRegistration(val phone: String, val headline: String?, val bio: String?, val expertise: String, @SerialName("experience_years") val experienceYears: Int, val education: String, @SerialName("portfolio_url") val portfolioUrl: String?, @SerialName("linkedin_url") val linkedinUrl: String, @SerialName("cv_asset_id") val cvAssetId: String, @SerialName("skill_certificate_asset_id") val certificateAssetId: String)
@Serializable private data class AnalyticsResponse(val data: MerchantAnalytics)
@Serializable private data class MentorProfileResponse(val data: MentorProfile)
@Serializable private data class MerchantDashboardResponse(val data: MerchantDashboard)
@Serializable private data class MentorDashboardResponse(val data: MentorDashboard)
@Serializable data class DashboardClassPage(val data: List<DashboardClass>, val meta: CatalogMeta)
@Serializable data class DashboardProductPage(val data: List<DashboardProduct>, val meta: CatalogMeta)
@Serializable data class DashboardCustomerPage(val data: List<DashboardCustomer>, val meta: CatalogMeta)
@Serializable private data class ClassListResponse(val data: List<DashboardClass>)

interface DashboardRepository {
    suspend fun merchantDashboard(token: String, periodDays: Int): MerchantDashboard
    suspend fun merchantClasses(token: String, page: Int): DashboardClassPage
    suspend fun mentorDashboard(token: String): MentorDashboard
    suspend fun mentorClasses(token: String): List<DashboardClass>
    suspend fun merchantProducts(token: String, page: Int): DashboardProductPage
    suspend fun merchantCustomers(token: String, page: Int): DashboardCustomerPage
    suspend fun merchantSales(token: String, page: Int, search: String): MerchantSalesPage
    suspend fun merchantAnalytics(token: String, period: String): MerchantAnalytics
    suspend fun mentorProfile(token: String): MentorProfile
    suspend fun updateMentorProfile(token: String, update: MentorProfileUpdate): MentorProfile
    suspend fun registerMentor(token: String, registration: MentorRegistration)
}

class ApiDashboardRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : DashboardRepository {
    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"
    override suspend fun merchantDashboard(token: String, periodDays: Int): MerchantDashboard {
        require(periodDays in setOf(7, 30, 90))
        return json.decodeFromString<MerchantDashboardResponse>(client.get(url("merchant/dashboard")) { header(HttpHeaders.Authorization, "Bearer $token"); parameter("period_days", periodDays) }.checked()).data
    }
    override suspend fun merchantClasses(token: String, page: Int): DashboardClassPage {
        require(page > 0)
        return json.decodeFromString(client.get(url("merchant/classes")) { header(HttpHeaders.Authorization, "Bearer $token"); parameter("page", page); parameter("limit", 20) }.checked())
    }
    override suspend fun mentorDashboard(token: String): MentorDashboard = json.decodeFromString<MentorDashboardResponse>(client.get(url("mentor/dashboard")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked()).data
    override suspend fun mentorClasses(token: String): List<DashboardClass> = json.decodeFromString<ClassListResponse>(client.get(url("mentor/classes")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked()).data
    override suspend fun merchantProducts(token: String, page: Int): DashboardProductPage {
        require(page > 0)
        return json.decodeFromString(client.get(url("merchant/digital-products")) { header(HttpHeaders.Authorization, "Bearer $token"); parameter("page", page); parameter("limit", 20) }.checked())
    }
    override suspend fun merchantCustomers(token: String, page: Int): DashboardCustomerPage {
        require(page > 0)
        return json.decodeFromString(client.get(url("merchant/customers")) { header(HttpHeaders.Authorization, "Bearer $token"); parameter("page", page); parameter("limit", 20) }.checked())
    }
    override suspend fun merchantSales(token: String, page: Int, search: String): MerchantSalesPage {
        require(page > 0 && search.length <= 100)
        return json.decodeFromString(client.get(url("merchant/sales")) { header(HttpHeaders.Authorization, "Bearer $token"); parameter("page", page); parameter("limit", 20); search.trim().takeIf(String::isNotEmpty)?.let { parameter("search", it) } }.checked())
    }
    override suspend fun merchantAnalytics(token: String, period: String): MerchantAnalytics {
        require(period in setOf("today", "month", "year"))
        return json.decodeFromString<AnalyticsResponse>(client.get(url("merchant/analytics/summary")) { header(HttpHeaders.Authorization, "Bearer $token"); parameter("period", period) }.checked()).data
    }
    override suspend fun mentorProfile(token: String): MentorProfile = json.decodeFromString<MentorProfileResponse>(client.get(url("mentor/profile")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked()).data
    override suspend fun updateMentorProfile(token: String, update: MentorProfileUpdate): MentorProfile {
        require(update.phone.isNotBlank() && update.phone.length <= 32 && update.skills.isNotEmpty() && update.skills.size <= 20 && update.skills.all { it.isNotBlank() && it.length <= 60 } && update.skills.joinToString(", ").length <= 160 && update.experienceYears in 0..80 && update.education.isNotBlank() && update.education.length <= 255 && update.linkedinUrl.length <= 2048 && com.getmobilestack.kmp.pintar.learning.safeLearningUrl(update.linkedinUrl) != null && (update.portfolioUrl == null || (update.portfolioUrl.length <= 2048 && com.getmobilestack.kmp.pintar.learning.safeLearningUrl(update.portfolioUrl) != null)))
        return json.decodeFromString<MentorProfileResponse>(client.patch(url("mentor/profile")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(io.ktor.http.ContentType.Application.Json); setBody(json.encodeToString(MentorProfileUpdate.serializer(), update)) }.checked()).data
    }
    override suspend fun registerMentor(token: String, registration: MentorRegistration) {
        require(registration.phone.isNotBlank() && registration.phone.length <= 32 && registration.expertise.isNotBlank() && registration.expertise.length <= 160 && registration.experienceYears in 0..80 && registration.education.isNotBlank() && registration.education.length <= 255 && registration.cvAssetId.isNotBlank() && registration.certificateAssetId.isNotBlank() && com.getmobilestack.kmp.pintar.learning.safeLearningUrl(registration.linkedinUrl) != null && registration.linkedinUrl.length <= 2048 && (registration.portfolioUrl == null || (registration.portfolioUrl.length <= 2048 && com.getmobilestack.kmp.pintar.learning.safeLearningUrl(registration.portfolioUrl) != null)))
        client.post(url("mentor/register")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(io.ktor.http.ContentType.Application.Json); setBody(json.encodeToString(MentorRegistration.serializer(), registration)) }.checked()
    }

    private suspend fun HttpResponse.checked(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = try { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull } catch (_: Exception) { null }
            throw AccountApiException(status.value, message ?: "Dashboard belum dapat dimuat.")
        }
        return body
    }
}
