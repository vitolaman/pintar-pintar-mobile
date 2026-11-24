package com.getmobilestack.kmp.pintar.shopping

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.catalog.CatalogMeta
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import com.getmobilestack.kmp.pintar.config.ApiConfig

@Serializable
data class ShoppingProduct(
    val id: String,
    val type: String,
    val title: String,
    val price: Double,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("merchant_name") val merchantName: String? = null,
    @SerialName("is_available") val isAvailable: Boolean = true,
)

@Serializable data class ShoppingEntry(val id: String, val item: ShoppingProduct)
@Serializable data class Cart(val items: List<ShoppingEntry>, val subtotal: Double, @SerialName("item_count") val itemCount: Int)
@Serializable data class WishlistPage(val data: List<ShoppingEntry>, val meta: CatalogMeta = CatalogMeta())
@Serializable private data class CartResponse(val data: Cart)
@Serializable private data class ProductRequest(val id: String)
@Serializable private data class PreviewRequest(val items: List<ProductRequest>, val codes: List<String>)
@Serializable data class RejectedCode(val code: String, val reason: String)
@Serializable data class PreviewItem(@SerialName("item_id") val itemId: String, val title: String, val price: Double, @SerialName("discount_amount") val discountAmount: Double = 0.0)
@Serializable data class AppliedCode(val code: String, @SerialName("merchant_name") val merchantName: String? = null, @SerialName("discount_amount") val discountAmount: Double)
@Serializable data class CheckoutPreview(
    val subtotal: Double,
    @SerialName("discount_amount") val discountAmount: Double,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("rejected_codes") val rejectedCodes: List<RejectedCode> = emptyList(),
    val items: List<PreviewItem> = emptyList(),
    val codes: List<AppliedCode> = emptyList(),
)
@Serializable private data class PreviewResponse(val data: CheckoutPreview)

interface ShoppingRepository {
    suspend fun getCart(token: String): Cart
    suspend fun addToCart(token: String, productId: String): Cart
    suspend fun removeFromCart(token: String, entryId: String): Cart
    suspend fun getWishlist(token: String, page: Int): WishlistPage
    suspend fun addToWishlist(token: String, productId: String)
    suspend fun removeFromWishlist(token: String, entryId: String)
    suspend fun previewCheckout(token: String, productIds: List<String>, codes: List<String>): CheckoutPreview
}

class ApiShoppingRepository(
    private val client: HttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ShoppingRepository {
    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"

    override suspend fun getCart(token: String): Cart = json.decodeFromString<CartResponse>(
        client.get(url("cart")) { header(HttpHeaders.Authorization, "Bearer $token") }.checkedBody()
    ).data

    override suspend fun addToCart(token: String, productId: String): Cart = json.decodeFromString<CartResponse>(
        client.post(url("cart/items")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ProductRequest(productId)))
        }.checkedBody()
    ).data

    override suspend fun removeFromCart(token: String, entryId: String): Cart = json.decodeFromString<CartResponse>(
        client.delete(url("cart/items/${entryId.encodeURLPathPart()}")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.checkedBody()
    ).data

    override suspend fun getWishlist(token: String, page: Int): WishlistPage {
        require(page > 0)
        return json.decodeFromString(client.get(url("wishlist")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("page", page)
            parameter("limit", 20)
        }.checkedBody())
    }

    override suspend fun addToWishlist(token: String, productId: String) {
        client.post(url("wishlist/items")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ProductRequest(productId)))
        }.checkedBody()
    }

    override suspend fun removeFromWishlist(token: String, entryId: String) {
        client.delete(url("wishlist/items/${entryId.encodeURLPathPart()}")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.checkedBody()
    }

    override suspend fun previewCheckout(token: String, productIds: List<String>, codes: List<String>): CheckoutPreview {
        require(productIds.isNotEmpty() && productIds.none { it.isBlank() })
        require(codes.size <= 2 && codes.all { it.length <= 32 })
        val request = PreviewRequest(productIds.map(::ProductRequest), codes)
        return json.decodeFromString<PreviewResponse>(client.post(url("orders/preview")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }.checkedBody()).data
    }

    private suspend fun HttpResponse.checkedBody(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = try {
                when (val value = json.parseToJsonElement(body).jsonObject["responseMessage"]) {
                    is JsonPrimitive -> value.contentOrNull
                    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ").ifBlank { null }
                    else -> null
                }
            } catch (_: Exception) { null }
            throw AccountApiException(status.value, message ?: "Permintaan gagal. Silakan coba lagi.")
        }
        return body
    }
}
