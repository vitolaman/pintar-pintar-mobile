package com.getmobilestack.kmp.pintar.catalog

import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class MerchantStorefront(
    val id: String,
    @SerialName("store_name") val name: String,
    @SerialName("store_description") val description: String? = null,
    val tagline: String? = null,
    val city: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("average_rating") val rating: Double = 0.0,
    @SerialName("total_students") val students: Int = 0,
    val skills: List<String> = emptyList(),
)
@Serializable private data class MerchantResponse(val data: MerchantStorefront)
interface MerchantRepository { suspend fun storefront(id: String): MerchantStorefront }
class ApiMerchantRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : MerchantRepository {
    override suspend fun storefront(id: String): MerchantStorefront {
        require(id.isNotBlank())
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/merchants/${id.encodeURLPathPart()}")
        check(response.status.isSuccess()) { "Merchant request failed (${response.status.value})" }
        return json.decodeFromString<MerchantResponse>(response.bodyAsText()).data
    }
}
