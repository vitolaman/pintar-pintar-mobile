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
import com.getmobilestack.kmp.pintar.shopping.VoucherRepository
import kotlinx.coroutines.CancellationException

@Composable
fun PromotionScreen(repository: PromotionRepository, vouchers: VoucherRepository, token: String?, onSignIn: () -> Unit, onProduct: (CatalogItem) -> Unit, onBack: () -> Unit) {
    var products by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var type by remember { mutableStateOf("kelas,bootcamp") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var showVouchers by remember { mutableStateOf(false) }
    LaunchedEffect(type, retry) {
        loading = true; error = false
        try { products = repository.items(type) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    if (showVouchers && token != null) { VoucherScreen(vouchers, token, onSelect = { showVouchers = false }, onBack = { showVouchers = false }); return }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹  Kembali") }
            Text("Promosi", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Temukan produk dan voucher pilihan.")
            OutlinedButton(onClick = { if (token == null) onSignIn() else showVouchers = true }) { Text("Lihat Voucher") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "kelas,bootcamp", onClick = { type = "kelas,bootcamp" }, label = { Text("Kelas & Bootcamp") })
                FilterChip(selected = type == "digital", onClick = { type = "digital" }, label = { Text("Produk Digital") })
            }
        }
        if (loading) item { CircularProgressIndicator() }
        if (error) item { Text("Promosi belum dapat dimuat."); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } }
        if (!loading && !error && products.isEmpty()) item { Text("Belum ada promosi.") }
        items(products, key = { "${it.type}-${it.id}" }) { product -> Card(Modifier.fillMaxWidth().clickable { onProduct(product) }) { Column(Modifier.padding(16.dp)) {
            Text(product.title, fontWeight = FontWeight.SemiBold); Text(product.merchant.name); Text(formatRupiah(product.price), color = MaterialTheme.colorScheme.primary)
        } } }
    }
}
