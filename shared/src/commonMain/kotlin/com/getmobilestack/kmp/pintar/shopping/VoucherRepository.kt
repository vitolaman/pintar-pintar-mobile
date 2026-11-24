package com.getmobilestack.kmp.pintar.shopping

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class PublicVoucher(
    val id: String, val name: String, val code: String, val description: String? = null,
    @SerialName("merchant_name") val merchantName: String,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: Double,
    @SerialName("minimum_purchase") val minimumPurchase: Double? = null,
    @SerialName("ends_at") val endsAt: String? = null,
)
@Serializable private data class VoucherContent(val featured: List<PublicVoucher> = emptyList(), val vouchers: List<PublicVoucher> = emptyList())
@Serializable private data class VoucherResponse(val data: VoucherContent)

interface VoucherRepository {
    suspend fun list(): List<PublicVoucher>
    suspend fun claim(token: String, id: String)
}

class ApiVoucherRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : VoucherRepository {
    override suspend fun list(): List<PublicVoucher> {
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/promo/vouchers")
        check(response.status.isSuccess()) { "Vouchers request failed (${response.status.value})" }
        val data = json.decodeFromString<VoucherResponse>(response.bodyAsText()).data
        return (data.featured + data.vouchers).distinctBy { it.id }
    }
    override suspend fun claim(token: String, id: String) {
        require(id.isNotBlank())
        val response = client.post("${baseUrl.trimEnd('/')}/api/v1/vouchers/${id.encodeURLPathPart()}/claim") { header(HttpHeaders.Authorization, "Bearer $token") }
        if (!response.status.isSuccess()) throw AccountApiException(response.status.value, "Voucher belum dapat diklaim.")
    }
}
