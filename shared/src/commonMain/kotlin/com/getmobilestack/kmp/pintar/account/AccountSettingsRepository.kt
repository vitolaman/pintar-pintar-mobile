package com.getmobilestack.kmp.pintar.account

import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable private data class TokenData(val token: String)
@Serializable private data class TokenResponse(val data: TokenData)
@Serializable private data class PasswordRequest(@SerialName("current_password") val current: String, @SerialName("new_password") val new: String)
class ReplacementSessionException : Exception("Token sesi pengganti tidak diterima.")

interface AccountSettingsRepository {
    suspend fun notificationPreferences(token: String): Map<String, Boolean>
    suspend fun updateNotificationPreferences(token: String, values: Map<String, Boolean>)
    suspend fun changePassword(token: String, current: String, new: String): String
    suspend fun endOtherSessions(token: String): String
    suspend fun deleteAccount(token: String)
}

class ApiAccountSettingsRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : AccountSettingsRepository {
    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"
    override suspend fun notificationPreferences(token: String): Map<String, Boolean> {
        val body = client.get(url("profile/notification-preferences")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked()
        return json.parseToJsonElement(body).jsonObject.getValue("data").jsonObject.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.booleanOrNull?.let { key to it } }.toMap()
    }
    override suspend fun updateNotificationPreferences(token: String, values: Map<String, Boolean>) {
        require(values.isNotEmpty())
        client.patch(url("profile/notification-preferences")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json)
            setBody(JsonObject(values.mapValues { JsonPrimitive(it.value) }).toString())
        }.checked()
    }
    override suspend fun changePassword(token: String, current: String, new: String): String {
        require(current.isNotBlank() && current.length <= 128 && new.length in 8..128 && new.any(Char::isDigit))
        val response = client.patch(url("auth/password")) {
            header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(PasswordRequest(current, new)))
        }.checked()
        return replacementToken(response)
    }
    override suspend fun endOtherSessions(token: String): String = replacementToken(client.post(url("auth/end-other-sessions")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked())
    override suspend fun deleteAccount(token: String) { client.delete(url("users/me")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked() }
    private fun replacementToken(body: String): String = try {
        json.decodeFromString<TokenResponse>(body).data.token.takeIf(String::isNotBlank) ?: throw ReplacementSessionException()
    } catch (e: ReplacementSessionException) { throw e } catch (_: Exception) { throw ReplacementSessionException() }
    private suspend fun HttpResponse.checked(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = runCatching { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull }.getOrNull()
            throw AccountApiException(status.value, message ?: "Pengaturan belum dapat diproses.")
        }
        return body
    }
}
