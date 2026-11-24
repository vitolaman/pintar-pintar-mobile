package com.getmobilestack.kmp.pintar.review

import com.getmobilestack.kmp.pintar.catalog.CatalogMeta
import com.getmobilestack.kmp.pintar.config.ApiConfig
import com.getmobilestack.kmp.pintar.detail.ProductKind
import com.getmobilestack.kmp.pintar.detail.ProductReference
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import io.ktor.http.contentType
import com.getmobilestack.kmp.pintar.account.AccountApiException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable data class ReviewReply(val id: String, val comment: String, @SerialName("author_name") val authorName: String)
@Serializable data class Review(val id: String, val rating: Int, val comment: String? = null, @SerialName("reviewer_name") val reviewerName: String, @SerialName("created_at") val createdAt: String = "", @SerialName("helpful_count") val helpfulCount: Int = 0, @SerialName("viewer_has_voted") val viewerHasVoted: Boolean = false, val replies: List<ReviewReply> = emptyList())
@Serializable data class ReviewSummary(@SerialName("average_rating") val averageRating: Double, @SerialName("review_count") val count: Int, val reviews: List<Review>, @SerialName("viewer_can_reply") val viewerCanReply: Boolean = false)
@Serializable data class ReviewPage(val data: ReviewSummary, val meta: CatalogMeta)
@Serializable private data class CreateReviewRequest(@SerialName("class_id") val classId: String? = null, @SerialName("product_id") val productId: String? = null, val rating: Int, val comment: String? = null)
@Serializable private data class ReplyRequest(val comment: String)

interface ReviewRepository {
    suspend fun getReviews(reference: ProductReference, page: Int, token: String? = null): ReviewPage
    suspend fun createReview(reference: ProductReference, token: String, rating: Int, comment: String)
    suspend fun setHelpful(reviewId: String, token: String, helpful: Boolean)
    suspend fun reply(reviewId: String, token: String, comment: String)
}

class ApiReviewRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : ReviewRepository {
    override suspend fun getReviews(reference: ProductReference, page: Int, token: String?): ReviewPage {
        require(page > 0)
        val family = if (reference.kind == ProductKind.DIGITAL) "digital-products" else "classes"
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/reviews/$family/${reference.id.encodeURLPathPart()}") { parameter("page", page); parameter("limit", 10); token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }
        check(response.status.isSuccess()) { "Review request failed (${response.status.value})" }
        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun createReview(reference: ProductReference, token: String, rating: Int, comment: String) {
        require(rating in 1..5 && comment.length <= 2000)
        val request = if (reference.kind == ProductKind.DIGITAL) CreateReviewRequest(productId = reference.id, rating = rating, comment = comment.trim().ifBlank { null }) else CreateReviewRequest(classId = reference.id, rating = rating, comment = comment.trim().ifBlank { null })
        client.post("${baseUrl.trimEnd('/')}/api/v1/reviews") {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(request))
        }.checked()
    }

    override suspend fun setHelpful(reviewId: String, token: String, helpful: Boolean) {
        val url = "${baseUrl.trimEnd('/')}/api/v1/reviews/${reviewId.encodeURLPathPart()}/helpful"
        val response = if (helpful) client.put(url) { header(HttpHeaders.Authorization, "Bearer $token") } else client.delete(url) { header(HttpHeaders.Authorization, "Bearer $token") }
        response.checked()
    }

    override suspend fun reply(reviewId: String, token: String, comment: String) {
        require(comment.isNotBlank() && comment.length <= 2000)
        client.post("${baseUrl.trimEnd('/')}/api/v1/reviews/${reviewId.encodeURLPathPart()}/replies") {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(ReplyRequest(comment.trim())))
        }.checked()
    }

    private suspend fun io.ktor.client.statement.HttpResponse.checked() {
        if (!status.isSuccess()) {
            val body = bodyAsText()
            val message = try { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull } catch (_: Exception) { null }
            throw AccountApiException(status.value, message ?: "Ulasan belum dapat disimpan.")
        }
    }
}
