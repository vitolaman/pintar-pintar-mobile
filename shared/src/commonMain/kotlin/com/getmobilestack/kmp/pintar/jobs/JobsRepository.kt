package com.getmobilestack.kmp.pintar.jobs

import com.getmobilestack.kmp.pintar.catalog.CatalogMeta
import com.getmobilestack.kmp.pintar.config.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.http.HttpHeaders
import com.getmobilestack.kmp.pintar.account.AccountApiException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class JobMerchant(val name: String)
@Serializable data class Job(
    val id: String,
    val title: String,
    val location: String,
    val salary: String,
    val requirements: String,
    val skills: List<String> = emptyList(),
    val merchant: JobMerchant,
    @SerialName("contract_type") val contractType: String,
    @SerialName("work_type") val workType: String,
    @SerialName("is_saved") val isSaved: Boolean = false,
    @SerialName("is_open") val isOpen: Boolean = true,
    val status: String? = null,
)
fun Job.canApply(): Boolean = isOpen && (status == null || status == "active")
@Serializable data class JobBoard(val jobs: List<Job>, @SerialName("active_jobs") val activeJobs: Int = 0, @SerialName("recruiting_merchants") val recruitingMerchants: Int = 0)
@Serializable data class JobPage(val data: JobBoard, val meta: CatalogMeta)
@Serializable private data class JobResponse(val data: Job)
@Serializable data class SavedJobsPage(val data: List<Job>, val meta: CatalogMeta)

interface JobsRepository {
    suspend fun search(query: String, page: Int, token: String? = null): JobPage
    suspend fun detail(id: String, token: String? = null): Job
    suspend fun saved(token: String, page: Int): SavedJobsPage
    suspend fun setSaved(token: String, id: String, saved: Boolean)
}

class ApiJobsRepository(private val client: HttpClient, private val baseUrl: String = ApiConfig.BASE_URL, private val json: Json = Json { ignoreUnknownKeys = true }) : JobsRepository {
    override suspend fun search(query: String, page: Int, token: String?): JobPage {
        require(page > 0)
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/job-postings") {
            parameter("page", page); parameter("limit", 20)
            query.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it.take(100)) }
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        check(response.status.isSuccess()) { "Jobs request failed (${response.status.value})" }
        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun detail(id: String, token: String?): Job {
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/job-postings/${id.encodeURLPathPart()}") { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }
        check(response.status.isSuccess()) { "Job request failed (${response.status.value})" }
        return json.decodeFromString<JobResponse>(response.bodyAsText()).data
    }

    override suspend fun saved(token: String, page: Int): SavedJobsPage {
        require(page > 0)
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/job-postings/saved") { header(HttpHeaders.Authorization, "Bearer $token"); parameter("page", page); parameter("limit", 20) }
        if (!response.status.isSuccess()) throw AccountApiException(response.status.value, "Lowongan tersimpan belum dapat dimuat.")
        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun setSaved(token: String, id: String, saved: Boolean) {
        val url = "${baseUrl.trimEnd('/')}/api/v1/job-postings/${id.encodeURLPathPart()}/save"
        val response = if (saved) client.put(url) { header(HttpHeaders.Authorization, "Bearer $token") } else client.delete(url) { header(HttpHeaders.Authorization, "Bearer $token") }
        if (!response.status.isSuccess()) throw AccountApiException(response.status.value, "Lowongan belum dapat disimpan.")
    }
}
