package com.getmobilestack.kmp.pintar.account

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import com.getmobilestack.kmp.pintar.config.ApiConfig

@Serializable
data class AccountUser(val id: String, val name: String, val email: String)

@Serializable
data class AccountSession(val token: String, val user: AccountUser)

@Serializable
data class AccountProfile(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("is_mentor") val isMentor: Boolean = false,
    @SerialName("is_merchant") val isMerchant: Boolean = false,
    val phone: String? = null,
    val headline: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("avatar_asset_id") val avatarAssetId: String? = null,
    @SerialName("member_since") val memberSince: String? = null,
    @SerialName("expertise_list") val expertise: List<String> = emptyList(),
    val onboarding: Onboarding = Onboarding(),
)

@Serializable data class Onboarding(val role: String? = null, @SerialName("custom_role") val customRole: String? = null, val skills: List<String> = emptyList(), @SerialName("completed_at") val completedAt: String? = null)
@Serializable data class OnboardingUpdate(val role: String, @SerialName("custom_role") val customRole: String? = null, val skills: List<String>)

@Serializable
data class LearningItem(
    @SerialName("access_id") val accessId: String,
    @SerialName("item_id") val itemId: String,
    val title: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("completion_percentage") val progress: Int = 0,
    @SerialName("progress_status") val status: String = "",
)

@Serializable data class ProfileUpdate(val name: String, val phone: String?, val headline: String?, val bio: String?, @SerialName("avatar_asset_id") val avatarAssetId: String?)
@Serializable private data class SignInRequest(val email: String, val password: String)
@Serializable private data class SignUpRequest(val name: String, val email: String, val password: String)
@Serializable private data class ResetRequest(val email: String)
@Serializable private data class AuthResponse(val data: AccountSession)
@Serializable private data class ProfileResponse(val data: AccountProfile)
@Serializable private data class LearningResponse(val data: List<LearningItem>)

class AccountApiException(val statusCode: Int, message: String) : Exception(message)

interface AccountRepository {
    suspend fun signIn(email: String, password: String): AccountSession
    suspend fun signUp(name: String, email: String, password: String): AccountSession
    suspend fun requestPasswordReset(email: String)
    suspend fun getProfile(token: String): AccountProfile
    suspend fun updateProfile(token: String, update: ProfileUpdate): AccountProfile
    suspend fun getLearning(token: String): List<LearningItem>
    suspend fun updateOnboarding(token: String, update: OnboardingUpdate)
}

class ApiAccountRepository(
    private val client: HttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AccountRepository {
    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"

    override suspend fun signIn(email: String, password: String): AccountSession {
        val body = json.encodeToString(SignInRequest(email.trim(), password))
        return json.decodeFromString<AuthResponse>(client.post(url("auth/sign-in")) {
            contentType(ContentType.Application.Json); setBody(body)
        }.checkedBody()).data.requireToken()
    }

    override suspend fun signUp(name: String, email: String, password: String): AccountSession {
        val body = json.encodeToString(SignUpRequest(name.trim(), email.trim(), password))
        return json.decodeFromString<AuthResponse>(client.post(url("auth/sign-up")) {
            contentType(ContentType.Application.Json); setBody(body)
        }.checkedBody()).data.requireToken()
    }

    override suspend fun requestPasswordReset(email: String) {
        client.post(url("auth/password-reset")) {
            contentType(ContentType.Application.Json); setBody(json.encodeToString(ResetRequest(email.trim())))
        }.checkedBody()
    }

    override suspend fun getProfile(token: String): AccountProfile =
        json.decodeFromString<ProfileResponse>(client.get(url("profile")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.checkedBody()).data

    override suspend fun updateProfile(token: String, update: ProfileUpdate): AccountProfile =
        json.decodeFromString<ProfileResponse>(client.patch(url("profile")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(update))
        }.checkedBody()).data

    override suspend fun getLearning(token: String): List<LearningItem> =
        json.decodeFromString<LearningResponse>(client.get(url("profile/learning")) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.checkedBody()).data

    override suspend fun updateOnboarding(token: String, update: OnboardingUpdate) {
        client.put(url("profile/onboarding")) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(update))
        }.checkedBody()
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

    private fun AccountSession.requireToken(): AccountSession {
        check(token.isNotBlank()) { "Token sesi tidak diterima." }
        return this
    }
}

fun validateSignIn(email: String, password: String): String? = when {
    !isValidEmail(email) -> "Masukkan alamat email yang valid."
    password.isEmpty() -> "Masukkan kata sandi."
    else -> null
}

fun validateSignUp(name: String, email: String, password: String, confirmation: String, agreed: Boolean): String? = when {
    name.isBlank() || name.trim().length > 120 -> "Masukkan nama lengkap (maksimal 120 karakter)."
    !isValidEmail(email) -> "Masukkan alamat email yang valid."
    password.length !in 8..128 || password.none { it.isDigit() } -> "Kata sandi harus 8–128 karakter dan mengandung angka."
    password != confirmation -> "Konfirmasi kata sandi tidak sesuai."
    !agreed -> "Setujui syarat dan ketentuan untuk mendaftar."
    else -> null
}

fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    return trimmed.length <= 255 && trimmed.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
}
