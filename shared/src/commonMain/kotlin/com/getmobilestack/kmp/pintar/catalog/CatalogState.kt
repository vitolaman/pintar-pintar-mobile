package com.getmobilestack.kmp.pintar.catalog

import kotlinx.coroutines.CancellationException

data class CatalogState(
    val type: CatalogType = CatalogType.ALL,
    val search: String = "",
    val category: String? = null,
    val subcategory: String? = null,
    val level: String? = null,
    val fileFormat: String? = null,
    val sortOrder: String? = null,
    val sort: CatalogSort = CatalogSort.NEWEST,
    val items: List<CatalogItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
)

/** Pure state transitions keep loading behavior independent from Compose and easy to test. */
class CatalogLoader(private val repository: CatalogRepository) {
    suspend fun load(state: CatalogState, append: Boolean = false): CatalogState {
        val page = if (append) state.page + 1 else 1
        return try {
            val result = repository.getItems(state.type, state.search, page, state.category, state.sort, state.subcategory, state.level, state.fileFormat, state.sortOrder)
            state.copy(
                items = (if (append) state.items + result.data else result.data).distinctBy { it.type to it.id },
                page = result.meta.page,
                hasMore = result.meta.page < result.meta.totalPages,
                loading = false,
                error = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            state.copy(loading = false, error = true)
        }
    }
}
