package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.catalog.formatRupiah
import com.getmobilestack.kmp.pintar.dashboard.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class MerchantList { PRODUCTS, CUSTOMERS }

@Composable
fun MerchantListScreen(section: MerchantList, token: String, repository: DashboardRepository, onBack: () -> Unit) {
    var products by remember(section, token) { mutableStateOf<List<DashboardProduct>>(emptyList()) }
    var customers by remember(section, token) { mutableStateOf<List<DashboardCustomer>>(emptyList()) }
    var page by remember(section, token) { mutableIntStateOf(1) }
    var hasMore by remember(section, token) { mutableStateOf(false) }
    var loading by remember(section, token) { mutableStateOf(true) }
    var error by remember(section, token) { mutableStateOf<String?>(null) }
    var retry by remember(section, token) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load(next: Int) {
        if (section == MerchantList.PRODUCTS) {
            val result = repository.merchantProducts(token, next)
            products = if (next == 1) result.data else products + result.data
            page = result.meta.page; hasMore = page < result.meta.totalPages
        } else {
            val result = repository.merchantCustomers(token, next)
            customers = if (next == 1) result.data else customers + result.data
            page = result.meta.page; hasMore = page < result.meta.totalPages
        }
    }

    LaunchedEffect(section, token, retry) {
        loading = true; error = null
        try { load(1) }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Data belum dapat dimuat." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali") }; Text(if (section == MerchantList.PRODUCTS) "Produk Digital" else "Pelanggan", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        if (loading) item { CircularProgressIndicator() }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        if (!loading && error == null && products.isEmpty() && customers.isEmpty()) item { Text("Belum ada data.") }
        items(products, key = { it.id }) { product -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(product.title, fontWeight = FontWeight.SemiBold); Text("${product.status} · ${formatRupiah(product.price)} · ${product.downloads} unduhan") } } }
        items(customers, key = { it.id }) { customer -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(customer.name, fontWeight = FontWeight.SemiBold); Text(customer.email); Text("${customer.classesEnrolled} kelas · ${customer.completionRate}% progres") } } }
        if (hasMore && error == null) item { OutlinedButton(enabled = !loading, onClick = {
            loading = true
            scope.launch {
                try { load(page + 1) }
                catch (e: CancellationException) { throw e }
                catch (e: AccountApiException) { error = e.message }
                catch (_: Exception) { error = "Data belum dapat dimuat." }
                finally { loading = false }
            }
        }) { Text("Muat lebih banyak") } }
        item { Text("Daftar hanya-baca; pengelolaan belum tersedia.", style = MaterialTheme.typography.bodySmall) }
    }
}
