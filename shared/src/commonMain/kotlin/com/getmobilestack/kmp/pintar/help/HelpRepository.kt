package com.getmobilestack.kmp.pintar.help

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class Faq(val id: String, val question: String, val answer: String)
@Serializable data class FaqCategory(val id: String, val name: String, val faqs: List<Faq>)
@Serializable private data class FaqContent(val categories: List<FaqCategory>)
@Serializable private data class FaqResponse(val data: FaqContent)
@Serializable private data class FeedbackRequest(@SerialName("ticket_type") val type: String, val message: String)

interface HelpRepository {
    suspend fun faqs(): List<FaqCategory>
    suspend fun sendFeedback(token: String, type: String, message: String)
}

class ApiHelpRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : HelpRepository {
    override suspend fun faqs(): List<FaqCategory> {
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/faqs")
        check(response.status.isSuccess()) { "FAQ request failed (${response.status.value})" }
        return json.decodeFromString<FaqResponse>(response.bodyAsText()).data.categories
    }
    override suspend fun sendFeedback(token: String, type: String, message: String) {
        require(type in setOf("kritik", "saran", "pujian") && message.isNotBlank() && message.length <= 2000)
        val response = client.post("${baseUrl.trimEnd('/')}/api/v1/help-tickets") {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(FeedbackRequest(type, message.trim())))
        }
        if (!response.status.isSuccess()) throw AccountApiException(response.status.value, "Pesan belum dapat dikirim.")
    }
}
