package com.getmobilestack.kmp.pintar.account

import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val MAX_AVATAR_BYTES = 2 * 1024 * 1024

fun avatarFileError(name: String, size: Long): String? = when {
    name.substringAfterLast('.', "").lowercase() !in setOf("png", "jpg", "jpeg", "webp") -> "Gunakan gambar PNG, JPG, JPEG, atau WEBP."
    size <= 0 -> "File kosong tidak dapat diunggah."
    size > MAX_AVATAR_BYTES -> "Ukuran gambar maksimal 2 MB."
    else -> null
}

@Serializable private data class StartUpload(val fileName: String, val contentType: String)
@Serializable private data class UploadInfo(val uploadId: String, val key: String)
@Serializable private data class UrlRequest(val uploadId: String, val key: String, val partsCount: Int)
@Serializable private data class UploadUrl(val partNumber: Int, val url: String)
@Serializable private data class Part(@SerialName("PartNumber") val number: Int, @SerialName("ETag") val etag: String)
@Serializable private data class CompleteUpload(val uploadId: String, val key: String, val parts: List<Part>)
@Serializable private data class Asset(@SerialName("asset_id") val id: String)

interface AvatarUploader { suspend fun upload(token: String, name: String, bytes: ByteArray): String }

class ApiAvatarUploader(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : AvatarUploader {
    override suspend fun upload(token: String, name: String, bytes: ByteArray): String {
        require(avatarFileError(name, bytes.size.toLong()) == null)
        val type = when (name.substringAfterLast('.').lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        suspend fun post(path: String, body: String): String {
            val response = client.post("${baseUrl.trimEnd('/')}/api/v1/upload/$path") {
                header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(body)
            }
            if (!response.status.isSuccess()) throw AccountApiException(response.status.value, "Foto belum dapat diunggah.")
            return response.bodyAsText()
        }
        val init = json.decodeFromString<UploadInfo>(post("initiate", json.encodeToString(StartUpload(name, type))))
        val urls = json.decodeFromString<List<UploadUrl>>(post("presigned-urls", json.encodeToString(UrlRequest(init.uploadId, init.key, 1))))
        require(urls.size == 1 && urls.single().partNumber == 1 && runCatching { Url(urls.single().url).protocol.name == "https" }.getOrDefault(false))
        val response = client.put(urls.single().url) { setBody(bytes) }
        check(response.status.isSuccess()) { "Upload failed" }
        val etag = response.headers[HttpHeaders.ETag] ?: error("Upload ETag missing")
        val asset = json.decodeFromString<Asset>(post("complete", json.encodeToString(CompleteUpload(init.uploadId, init.key, listOf(Part(1, etag))))))
        check(asset.id.isNotBlank())
        return asset.id
    }
}
