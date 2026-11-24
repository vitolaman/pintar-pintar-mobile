package com.getmobilestack.kmp.pintar.catalog

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.getmobilestack.kmp.pintar.config.ApiConfig

enum class CatalogType(val label: String, val apiValue: String?) {
    ALL("Semua", null), CLASS("Kelas Video", "kelas"),
    BOOTCAMP("Bootcamp", "bootcamp"), DIGITAL("Produk Digital", "digital")
}

enum class CatalogSort(val label: String, val apiValue: String?) {
    NEWEST("Terbaru", null), POPULAR("Terpopuler", "popularity"),
    PRICE("Harga", "price"), RATING("Rating", "rating"), TITLE("Nama", "title")
}

@Serializable data class CatalogCategory(val id: String, val name: String, val slug: String, val children: List<CatalogCategory> = emptyList())
@Serializable private data class CategoriesResponse(val data: List<CatalogCategory>)

@Serializable
data class Merchant(val name: String = "Merchant", val id: String? = null, val slug: String? = null)

@Serializable
data class CatalogItem(
    val id: String,
    val type: String,
    val title: String,
    val category: String? = null,
    @SerialName("skill_category") val skillCategory: String? = null,
    val price: Double = 0.0,
    val rating: Double = 0.0,
    val merchant: Merchant = Merchant(),
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class CatalogMeta(
    val page: Int = 1,
    @SerialName("total_page") val totalPages: Int = 1,
)

@Serializable
data class CatalogPage(val data: List<CatalogItem>, val meta: CatalogMeta = CatalogMeta())

interface CatalogRepository {
    suspend fun getItems(type: CatalogType, search: String, page: Int, category: String? = null, sort: CatalogSort = CatalogSort.NEWEST, subcategory: String? = null, level: String? = null, fileFormat: String? = null, sortOrder: String? = null, merchantId: String? = null): CatalogPage
    suspend fun getCategories(): List<CatalogCategory>
}

/** The backend URL is injectable so tests and staging environments need no UI changes. */
class ApiCatalogRepository(
    private val client: HttpClient,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CatalogRepository {
    override suspend fun getItems(type: CatalogType, search: String, page: Int, category: String?, sort: CatalogSort, subcategory: String?, level: String?, fileFormat: String?, sortOrder: String?, merchantId: String?): CatalogPage {
        require(page > 0)
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/catalog/items") {
            type.apiValue?.let { parameter("type", it) }
            search.trim().takeIf { it.isNotEmpty() }?.let { parameter("search", it.take(100)) }
            category?.takeIf { it.isNotBlank() }?.let { parameter("category", it) }
            subcategory?.takeIf { it.isNotBlank() }?.let { parameter("sub", it) }
            level?.takeIf { it.isNotBlank() }?.let { parameter("level", it) }
            fileFormat?.takeIf { it.isNotBlank() }?.let { parameter("file_format", it) }
            merchantId?.takeIf { it.isNotBlank() }?.let { parameter("merchant_id", it) }
            sortOrder?.takeIf { it in setOf("asc", "desc") }?.let { parameter("sort_order", it) }
            sort.apiValue?.let { parameter("sort_by", it) }
            parameter("page", page)
            parameter("limit", 20)
        }
        check(response.status.isSuccess()) { "Catalog request failed (${response.status.value})" }
        return json.decodeFromString<CatalogPage>(response.bodyAsText())
    }

    override suspend fun getCategories(): List<CatalogCategory> {
        val response = client.get("${baseUrl.trimEnd('/')}/api/v1/catalog/categories")
        check(response.status.isSuccess()) { "Categories request failed (${response.status.value})" }
        return json.decodeFromString<CategoriesResponse>(response.bodyAsText()).data
    }
}

fun formatRupiah(price: Double): String {
    val digits = price.toLong().coerceAtLeast(0).toString()
    return "Rp" + digits.reversed().chunked(3).joinToString(".").reversed()
}
