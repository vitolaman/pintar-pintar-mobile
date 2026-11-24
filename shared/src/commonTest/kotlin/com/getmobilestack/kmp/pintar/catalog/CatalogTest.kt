package com.getmobilestack.kmp.pintar.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond

class CatalogTest {
    private val item = CatalogItem("1", "kelas", "Kelas", price = 99000.0)

    @Test
    fun `decodes the public catalog response and ignores extra fields`() {
        val page = Json { ignoreUnknownKeys = true }.decodeFromString<CatalogPage>(
            """{"data":[{"id":"1","type":"kelas","title":"Kelas","price":99000,"merchant":{"name":"Media Sipil","id":"m"},"image_url":null}],"meta":{"page":1,"total_page":2},"responseMessage":"ok"}"""
        )
        assertEquals("Media Sipil", page.data.single().merchant.name)
        assertEquals(2, page.meta.totalPages)
    }

    @Test
    fun `formats Indonesian prices`() {
        assertEquals("Rp99.000", formatRupiah(99000.0))
        assertEquals("Rp0", formatRupiah(0.0))
        assertEquals("Rp1.234.567", formatRupiah(1234567.0))
    }

    @Test
    fun `loads first page and appends subsequent pages`() = runBlocking {
        val calls = mutableListOf<Int>()
        val loader = CatalogLoader(object : CatalogRepository {
            override suspend fun getCategories() = emptyList<CatalogCategory>()
            override suspend fun getItems(type: CatalogType, search: String, page: Int, category: String?, sort: CatalogSort, subcategory: String?, level: String?, fileFormat: String?, sortOrder: String?, merchantId: String?): CatalogPage {
                calls.add(page)
                return CatalogPage(listOf(item.copy(id = page.toString())), CatalogMeta(page, 2))
            }
        })
        val first = loader.load(CatalogState())
        assertTrue(first.hasMore)
        val second = loader.load(first, append = true)
        assertEquals(listOf(1, 2), calls)
        assertEquals(listOf("1", "2"), second.items.map { it.id })
        assertFalse(second.hasMore)
    }

    @Test fun `overlapping pages do not create duplicate catalog keys`() = runBlocking {
        val loader = CatalogLoader(object : CatalogRepository {
            override suspend fun getCategories() = emptyList<CatalogCategory>()
            override suspend fun getItems(type: CatalogType, search: String, page: Int, category: String?, sort: CatalogSort, subcategory: String?, level: String?, fileFormat: String?, sortOrder: String?, merchantId: String?) = CatalogPage(listOf(item, item.copy(type = "digital")), CatalogMeta(page, 2))
        })
        val result = loader.load(loader.load(CatalogState()), append = true)
        assertEquals(listOf("kelas", "digital"), result.items.map { it.type })
    }

    @Test
    fun `failed loading preserves existing items for retry`() = runBlocking {
        val loader = CatalogLoader(object : CatalogRepository {
            override suspend fun getCategories() = emptyList<CatalogCategory>()
            override suspend fun getItems(type: CatalogType, search: String, page: Int, category: String?, sort: CatalogSort, subcategory: String?, level: String?, fileFormat: String?, sortOrder: String?, merchantId: String?): CatalogPage = error("offline")
        })
        val result = loader.load(CatalogState(items = listOf(item), loading = true), append = true)
        assertTrue(result.error)
        assertFalse(result.loading)
        assertEquals(listOf(item), result.items)
    }

    @Test
    fun `cancellation is propagated`() = runBlocking {
        val loader = CatalogLoader(object : CatalogRepository {
            override suspend fun getCategories() = emptyList<CatalogCategory>()
            override suspend fun getItems(type: CatalogType, search: String, page: Int, category: String?, sort: CatalogSort, subcategory: String?, level: String?, fileFormat: String?, sortOrder: String?, merchantId: String?): CatalogPage = throw CancellationException()
        })
        assertFailsWith<CancellationException> { loader.load(CatalogState()) }
        Unit
    }

    @Test
    fun `catalog sends selected category and sort to backend`() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("desain", request.url.parameters["category"])
            assertEquals("rating", request.url.parameters["sort_by"])
            assertEquals("bootcamp", request.url.parameters["type"])
            assertEquals("grafis", request.url.parameters["sub"])
            assertEquals("Pemula", request.url.parameters["level"])
            assertEquals("pdf", request.url.parameters["file_format"])
            assertEquals("asc", request.url.parameters["sort_order"])
            respond("""{"data":[],"meta":{"page":1,"total_page":0}}""")
        })
        try { ApiCatalogRepository(client, "https://example.com").getItems(CatalogType.BOOTCAMP, "", 1, "desain", CatalogSort.RATING, "grafis", "Pemula", "pdf", "asc") }
        finally { client.close() }
        Unit
    }

    @Test
    fun `categories decode hierarchy`() = runBlocking {
        val client = HttpClient(MockEngine { respond("""{"data":[{"id":"1","name":"Desain","slug":"desain","children":[{"id":"2","name":"Grafis","slug":"grafis","children":[]}]}]}""") })
        try { assertEquals("grafis", ApiCatalogRepository(client, "https://example.com").getCategories().single().children.single().slug) }
        finally { client.close() }
    }
}
