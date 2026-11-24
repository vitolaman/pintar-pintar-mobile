package com.getmobilestack.kmp.pintar.catalog

import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable private data class PromotionResponse(val data: List<CatalogItem>)
interface PromotionRepository { suspend fun items(type: String): List<CatalogItem> }
class ApiPromotionRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : PromotionRepository {
    override suspend fun items(type: String): List<CatalogItem> {
        require(type in setOf("kelas,bootcamp", "digital"))
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/promo/items") { parameter("type", type); parameter("limit", 6) }
        check(response.status.isSuccess()) { "Promotion request failed (${response.status.value})" }
        return json.decodeFromString<PromotionResponse>(response.bodyAsText()).data
    }
}
