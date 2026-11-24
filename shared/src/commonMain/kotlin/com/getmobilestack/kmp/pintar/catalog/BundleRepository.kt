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

@Serializable data class BundleItem(val id: String, val type: String, val title: String, val price: Double, @SerialName("is_available") val available: Boolean = true)
@Serializable data class Bundle(
    val id: String, val title: String, val description: String, val merchant: Merchant,
    val items: List<BundleItem>, val price: Double, @SerialName("original_price") val originalPrice: Double,
    @SerialName("cover_url") val coverUrl: String? = null, @SerialName("discount_percent") val discountPercent: Int = 0,
)
@Serializable data class BundlePage(val data: List<Bundle>, val meta: CatalogMeta = CatalogMeta())

interface BundleRepository { suspend fun list(page: Int, merchantId: String? = null): BundlePage }

class ApiBundleRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : BundleRepository {
    override suspend fun list(page: Int, merchantId: String?): BundlePage {
        require(page > 0)
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/bundles") {
            parameter("page", page); parameter("limit", 20)
            merchantId?.takeIf(String::isNotBlank)?.let { parameter("merchant_id", it) }
        }
        check(response.status.isSuccess()) { "Bundles request failed (${response.status.value})" }
        return json.decodeFromString(response.bodyAsText())
    }
}
