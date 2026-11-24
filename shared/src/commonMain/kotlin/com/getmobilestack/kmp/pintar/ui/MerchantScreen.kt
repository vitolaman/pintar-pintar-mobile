package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.catalog.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MerchantScreen(merchantId: String, merchants: MerchantRepository, catalog: CatalogRepository, onBundles: (String) -> Unit, onProduct: (CatalogItem) -> Unit, onBack: () -> Unit) {
    var store by remember(merchantId) { mutableStateOf<MerchantStorefront?>(null) }
    var products by remember(merchantId) { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var page by remember(merchantId) { mutableIntStateOf(1) }
    var hasMore by remember(merchantId) { mutableStateOf(false) }
    var loading by remember(merchantId) { mutableStateOf(true) }
    var error by remember(merchantId) { mutableStateOf(false) }
    var retry by remember(merchantId) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(merchantId, retry) {
        loading = true; error = false
        try {
            val loaded = merchants.storefront(merchantId)
            val result = catalog.getItems(CatalogType.ALL, "", 1, merchantId = loaded.id)
            store = loaded; products = result.data; page = result.meta.page; hasMore = page < result.meta.totalPages
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali") }; Text(store?.name ?: "Merchant", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        if (loading) item { CircularProgressIndicator() }
        if (error) item { Text("Merchant belum dapat dimuat."); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } }
        store?.let { merchant -> item {
            merchant.tagline?.let { Text(it) }
            merchant.description?.let { Text(it) }
            Text("★ ${merchant.rating} · ${merchant.students} siswa")
            merchant.city?.let { Text(it) }
            if (merchant.skills.isNotEmpty()) Text(merchant.skills.joinToString(" · "))
            OutlinedButton(onClick = { onBundles(merchant.id) }) { Text("Lihat Bundling") }
            Text("Produk Merchant", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        } }
        if (!loading && !error && products.isEmpty()) item { Text("Belum ada produk tersedia.") }
        items(products, key = { "${it.type}-${it.id}" }) { product ->
            Card(Modifier.fillMaxWidth().clickable { onProduct(product) }) {
                Column(Modifier.padding(16.dp)) { Text(product.title, fontWeight = FontWeight.SemiBold); Text(formatRupiah(product.price), color = MaterialTheme.colorScheme.primary) }
            }
        }
        if (hasMore && !error) item {
            OutlinedButton(enabled = !loading, onClick = {
                val id = store?.id ?: return@OutlinedButton
                loading = true
                scope.launch {
                    try {
                        val result = catalog.getItems(CatalogType.ALL, "", page + 1, merchantId = id)
                        products = products + result.data; page = result.meta.page; hasMore = page < result.meta.totalPages
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = true }
                    finally { loading = false }
                }
            }) { Text("Muat lebih banyak") }
        }
    }
}
