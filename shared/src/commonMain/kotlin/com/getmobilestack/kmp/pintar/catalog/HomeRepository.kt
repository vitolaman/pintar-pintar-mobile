package com.getmobilestack.kmp.pintar.catalog

import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class HomeStatistics(@SerialName("active_students") val activeStudents: Int, @SerialName("learning_products") val learningProducts: Int, @SerialName("digital_products") val digitalProducts: Int, @SerialName("platform_rating") val platformRating: Double)
@Serializable data class HomeTestimonial(val id: String, val rating: Int, val comment: String, @SerialName("user_name") val userName: String, @SerialName("class_title") val classTitle: String)
enum class FeaturedSection(val path: String, val title: String, val type: CatalogType) {
    BOOTCAMPS("bootcamps", "Bootcamp Unggulan", CatalogType.BOOTCAMP),
    CLASSES("video-classes", "Kelas Video Unggulan", CatalogType.CLASS),
    DIGITAL("digital-products", "Produk Digital Unggulan", CatalogType.DIGITAL),
}
@Serializable private data class StatisticsResponse(val data: HomeStatistics)
@Serializable private data class FeaturedResponse(val data: List<CatalogItem>)
@Serializable private data class TestimonialsResponse(val data: List<HomeTestimonial>)

interface HomeRepository {
    suspend fun statistics(): HomeStatistics
    suspend fun featured(section: FeaturedSection): List<CatalogItem>
    suspend fun testimonials(): List<HomeTestimonial>
}

class ApiHomeRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : HomeRepository {
    private suspend fun fetch(section: String, limit: Boolean): String {
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/home/$section") { if (limit) parameter("limit", 10) }
        check(response.status.isSuccess()) { "Home request failed (${response.status.value})" }
        return response.bodyAsText()
    }
    override suspend fun statistics() = json.decodeFromString<StatisticsResponse>(fetch("statistics", false)).data
    override suspend fun featured(section: FeaturedSection) = json.decodeFromString<FeaturedResponse>(fetch(section.path, true)).data
    override suspend fun testimonials() = json.decodeFromString<TestimonialsResponse>(fetch("testimonials", true)).data
}
