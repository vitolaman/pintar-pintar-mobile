package com.getmobilestack.kmp.pintar.detail

import com.getmobilestack.kmp.pintar.catalog.Merchant
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.getmobilestack.kmp.pintar.config.ApiConfig

@Serializable
data class ProductDetail(
    val id: String,
    val type: String,
    val title: String,
    val category: String? = null,
    @SerialName("skill_category") val skillCategory: String? = null,
    val level: String? = null,
    val price: Double = 0.0,
    @SerialName("original_price") val originalPrice: Double? = null,
    val rating: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("students_count") val studentsCount: Int = 0,
    val merchant: Merchant = Merchant(),
    @SerialName("image_url") val imageUrl: String? = null,
    val description: String? = null,
    val duration: String? = null,
    val requirements: List<String> = emptyList(),
    @SerialName("learning_outcomes") val learningOutcomes: List<String> = emptyList(),
    val mentors: List<DetailMentor> = emptyList(),
    val chapters: List<DetailChapter> = emptyList(),
    val meetings: List<DetailMeeting> = emptyList(),
    val files: List<DetailFile> = emptyList(),
    val faqs: List<DetailFaq> = emptyList(),
    @SerialName("is_owned") val isOwned: Boolean = false,
    @SerialName("in_cart") val inCart: Boolean = false,
    @SerialName("in_wishlist") val inWishlist: Boolean = false,
    @SerialName("has_reviewed") val hasReviewed: Boolean = false,
)

@Serializable data class DetailMentor(val name: String, val headline: String? = null)
@Serializable data class DetailVideo(val title: String, val duration: String? = null)
@Serializable data class DetailFile(val name: String, val format: String? = null, val type: String? = null)
@Serializable data class DetailChapter(val title: String, val description: String? = null, val videos: List<DetailVideo> = emptyList(), val files: List<DetailFile> = emptyList())
@Serializable data class DetailMeeting(val title: String, val date: String? = null, val time: String? = null)
@Serializable data class DetailFaq(val question: String, val answer: String)
@Serializable private data class DetailResponse(val data: ProductDetail)

enum class ProductKind(val apiPath: String, val sitePath: String) {
    CLASS("classes", "kelas"), BOOTCAMP("classes", "bootcamp"), DIGITAL("digital-products", "produk-digital");

    companion object {
        fun fromApiType(type: String): ProductKind? = when (type) {
            "kelas", "class" -> CLASS
            "bootcamp" -> BOOTCAMP
            "digital" -> DIGITAL
            else -> null
        }
    }
}

data class ProductReference(val id: String, val kind: ProductKind) {
    fun websiteUrl(): String = "https://pintarpintar.id/${kind.sitePath}/${id.encodeURLPathPart()}"
}

interface ProductDetailRepository {
    suspend fun getDetail(reference: ProductReference, token: String? = null): ProductDetail
}

class ApiProductDetailRepository(
    private val client: HttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ProductDetailRepository {
    override suspend fun getDetail(reference: ProductReference, token: String?): ProductDetail {
        require(reference.id.isNotBlank())
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/catalog/${reference.kind.apiPath}/${reference.id.encodeURLPathPart()}") {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        check(response.status.isSuccess()) { "Detail request failed (${response.status.value})" }
        return json.decodeFromString<DetailResponse>(response.bodyAsText()).data
    }
}
