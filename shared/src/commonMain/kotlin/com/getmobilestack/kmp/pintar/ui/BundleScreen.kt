package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.catalog.*
import com.getmobilestack.kmp.pintar.detail.ProductKind
import com.getmobilestack.kmp.pintar.detail.ProductReference
import com.getmobilestack.kmp.pintar.shopping.ShoppingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BundleScreen(repository: BundleRepository, shopping: ShoppingRepository, token: String?, merchantId: String?, onSignIn: () -> Unit, onProduct: (ProductReference) -> Unit, onCart: () -> Unit, onBack: () -> Unit) {
    var bundles by remember(merchantId) { mutableStateOf<List<Bundle>>(emptyList()) }
    var selected by remember(merchantId) { mutableStateOf<Bundle?>(null) }
    var page by remember(merchantId) { mutableIntStateOf(1) }
    var hasMore by remember(merchantId) { mutableStateOf(false) }
    var loading by remember(merchantId) { mutableStateOf(true) }
    var error by remember(merchantId) { mutableStateOf<String?>(null) }
    var message by remember(merchantId) { mutableStateOf<String?>(null) }
    var retry by remember(merchantId) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, merchantId, retry) {
        loading = true; error = null
        try { val result = repository.list(1, merchantId); bundles = result.data; page = result.meta.page; hasMore = page < result.meta.totalPages }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Bundling belum dapat dimuat." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = { if (selected == null) onBack() else { selected = null; message = null } }) { Text("‹  Kembali") }
            Text(selected?.title ?: "Paket Bundling", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (loading) item { CircularProgressIndicator() }
        error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        val bundle = selected
        if (bundle == null) {
            if (!loading && error == null && bundles.isEmpty()) item { Text("Belum ada bundling tersedia.") }
            items(bundles, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth().clickable { selected = entry }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        entry.coverUrl?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(150.dp)) }
                        Text(entry.title, fontWeight = FontWeight.SemiBold); Text(entry.merchant.name)
                        Text("${entry.items.size} produk · ${formatRupiah(entry.price)}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (hasMore && error == null) item {
                OutlinedButton(enabled = !loading, onClick = {
                    loading = true
                    scope.launch {
                        try { val result = repository.list(page + 1, merchantId); bundles = (bundles + result.data).distinctBy { it.id }; page = result.meta.page; hasMore = page < result.meta.totalPages }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "Bundling belum dapat dimuat." }
                        finally { loading = false }
                    }
                }) { Text("Muat lebih banyak") }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bundle.coverUrl?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp)) }
                    Text("Oleh ${bundle.merchant.name}"); Text(bundle.description)
                    Text("Harga normal: ${formatRupiah(bundle.originalPrice)}")
                    Text("Harga bundling: ${formatRupiah(bundle.price)}", fontWeight = FontWeight.Bold)
                    if (bundle.discountPercent > 0) Text("Hemat ${bundle.discountPercent}%")
                    Button(enabled = !loading, onClick = {
                        if (token == null) onSignIn() else {
                            loading = true; message = null
                            scope.launch {
                                try { shopping.addToCart(token, bundle.id); message = "Bundling ditambahkan ke keranjang." }
                                catch (e: CancellationException) { throw e }
                                catch (e: AccountApiException) { message = e.message }
                                catch (_: Exception) { message = "Bundling belum dapat ditambahkan." }
                                finally { loading = false }
                            }
                        }
                    }) { Text("Tambah ke Keranjang") }
                    TextButton(onClick = onCart) { Text("Lihat Keranjang") }
                    message?.let { Text(it) }
                    Text("Produk dalam Paket", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(bundle.items, key = { "${it.type}-${it.id}" }) { entry ->
                val kind = ProductKind.fromApiType(entry.type)
                Card(Modifier.fillMaxWidth().clickable(enabled = kind != null && entry.available) { kind?.let { onProduct(ProductReference(entry.id, it)) } }) {
                    Column(Modifier.padding(16.dp)) { Text(entry.title); Text("${entry.type} · ${formatRupiah(entry.price)}"); if (!entry.available) Text("Tidak tersedia") }
                }
            }
        }
    }
}
