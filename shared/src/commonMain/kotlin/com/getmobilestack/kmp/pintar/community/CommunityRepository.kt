package com.getmobilestack.kmp.pintar.community

import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.catalog.CatalogMeta
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable data class CommunityUser(val id: String, val name: String)
@Serializable data class CommunityGroup(val id: String, val name: String, val description: String, val access: String, val membership: String, @SerialName("member_count") val memberCount: Int, @SerialName("image_url") val imageUrl: String? = null, val owner: CommunityUser)
@Serializable data class CommunityAttachment(val name: String, @SerialName("download_url") val downloadUrl: String)
@Serializable data class CommunityProduct(val id: String, val type: String, val title: String)
@Serializable data class CommunityReply(val id: String, val author: CommunityUser, val content: String, @SerialName("created_at") val createdAt: String)
@Serializable data class CommunityThread(val id: String, val author: CommunityUser, val content: String = "", @SerialName("like_count") val likes: Int = 0, val liked: Boolean = false, @SerialName("reply_count") val replyCount: Int = 0, val replies: List<CommunityReply> = emptyList(), val attachment: CommunityAttachment? = null, val item: CommunityProduct? = null, @SerialName("created_at") val createdAt: String)
@Serializable data class CommunityGroupPage(val data: List<CommunityGroup>, val meta: CatalogMeta)
@Serializable data class CommunityThreadPage(val data: List<CommunityThread>, val meta: CatalogMeta)
@Serializable data class CommunityRequest(val user: CommunityUser)
@Serializable private data class ThreadResponse(val data: CommunityThread)
@Serializable private data class GroupResponse(val data: CommunityGroup)
@Serializable private data class RequestResponse(val data: List<CommunityRequest>)
@Serializable private data class ThreadBody(@SerialName("group_id") val groupId: String, val content: String)
@Serializable private data class ReplyBody(val content: String)
@Serializable private data class GroupBody(val name: String, val description: String?, val access: String, @SerialName("image_asset_id") val imageAssetId: String)

interface CommunityRepository {
    suspend fun groups(token: String?, page: Int, search: String, joined: Boolean): CommunityGroupPage
    suspend fun group(token: String?, id: String): CommunityGroup
    suspend fun createGroup(token: String, name: String, description: String, access: String, imageAssetId: String): CommunityGroup
    suspend fun join(token: String, id: String)
    suspend fun leave(token: String, id: String)
    suspend fun requests(token: String, id: String): List<CommunityRequest>
    suspend fun decide(token: String, groupId: String, userId: String, accept: Boolean)
    suspend fun threads(token: String?, groupId: String?, page: Int): CommunityThreadPage
    suspend fun thread(token: String?, id: String): CommunityThread
    suspend fun createThread(token: String, groupId: String, content: String)
    suspend fun reply(token: String, threadId: String, content: String)
    suspend fun like(token: String, threadId: String, liked: Boolean)
    suspend fun deleteThread(token: String, threadId: String)
}

class ApiCommunityRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : CommunityRepository {
    private fun api(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"
    private fun id(value: String): String { require(value.isNotBlank()); return value.encodeURLPathPart() }
    override suspend fun groups(token: String?, page: Int, search: String, joined: Boolean): CommunityGroupPage {
        require(page > 0 && search.length <= 100 && (!joined || token != null))
        return json.decodeFromString(client.get(api("community-groups")) { token?.let { header(HttpHeaders.Authorization, "Bearer $it") }; parameter("page", page); parameter("limit", 20); if (joined) parameter("joined", true); search.trim().takeIf(String::isNotEmpty)?.let { parameter("search", it) } }.checked())
    }
    override suspend fun group(token: String?, id: String) = json.decodeFromString<GroupResponse>(client.get(api("community-groups/${id(id)}")) { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }.checked()).data
    override suspend fun createGroup(token: String, name: String, description: String, access: String, imageAssetId: String): CommunityGroup {
        require(name.isNotBlank() && name.trim().length <= 80 && description.length <= 500 && access in setOf("public", "request") && imageAssetId.isNotBlank())
        return json.decodeFromString<GroupResponse>(client.post(api("community-groups")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(GroupBody(name.trim(), description.trim().ifBlank { null }, access, imageAssetId))) }.checked()).data
    }
    override suspend fun join(token: String, id: String) { client.post(api("community-groups/${id(id)}/join")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked() }
    override suspend fun leave(token: String, id: String) { client.delete(api("community-groups/${id(id)}/membership")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked() }
    override suspend fun requests(token: String, id: String) = json.decodeFromString<RequestResponse>(client.get(api("community-groups/${id(id)}/requests")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked()).data
    override suspend fun decide(token: String, groupId: String, userId: String, accept: Boolean) { client.post(api("community-groups/${id(groupId)}/requests/${id(userId)}/${if (accept) "accept" else "reject"}")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked() }
    override suspend fun threads(token: String?, groupId: String?, page: Int): CommunityThreadPage {
        require(page > 0)
        return json.decodeFromString(client.get(api("community-threads")) { token?.let { header(HttpHeaders.Authorization, "Bearer $it") }; groupId?.let { parameter("group_id", it) }; parameter("page", page); parameter("limit", 20) }.checked())
    }
    override suspend fun thread(token: String?, id: String) = json.decodeFromString<ThreadResponse>(client.get(api("community-threads/${id(id)}")) { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }.checked()).data
    override suspend fun createThread(token: String, groupId: String, content: String) {
        require(groupId.isNotBlank() && content.isNotBlank() && content.length <= 5000)
        client.post(api("community-threads")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(ThreadBody(groupId, content.trim()))) }.checked()
    }
    override suspend fun reply(token: String, threadId: String, content: String) {
        require(content.isNotBlank() && content.length <= 5000)
        client.post(api("community-threads/${id(threadId)}/replies")) { header(HttpHeaders.Authorization, "Bearer $token"); contentType(ContentType.Application.Json); setBody(json.encodeToString(ReplyBody(content.trim()))) }.checked()
    }
    override suspend fun like(token: String, threadId: String, liked: Boolean) {
        val url = api("community-threads/${id(threadId)}/like")
        (if (liked) client.put(url) { header(HttpHeaders.Authorization, "Bearer $token") } else client.delete(url) { header(HttpHeaders.Authorization, "Bearer $token") }).checked()
    }
    override suspend fun deleteThread(token: String, threadId: String) { client.delete(api("community-threads/${id(threadId)}")) { header(HttpHeaders.Authorization, "Bearer $token") }.checked() }
    private suspend fun HttpResponse.checked(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            val message = runCatching { (json.parseToJsonElement(body).jsonObject["responseMessage"] as? JsonPrimitive)?.contentOrNull }.getOrNull()
            throw AccountApiException(status.value, message ?: "Komunitas belum dapat diproses.")
        }
        return body
    }
}
