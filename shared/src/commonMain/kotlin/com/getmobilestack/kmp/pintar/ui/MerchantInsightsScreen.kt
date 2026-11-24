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

enum class MerchantInsight { SALES, ANALYTICS }

@Composable
fun MerchantInsightsScreen(section: MerchantInsight, token: String, repository: DashboardRepository, onBack: () -> Unit) {
    var sales by remember(section, token) { mutableStateOf<List<MerchantSale>>(emptyList()) }
    var analytics by remember(section, token) { mutableStateOf<MerchantAnalytics?>(null) }
    var query by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("month") }
    var page by remember { mutableIntStateOf(1) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(section, token, search, period, retry) {
        loading = true; error = null
        try {
            if (section == MerchantInsight.SALES) { val result = repository.merchantSales(token, 1, search); sales = result.data; page = result.meta.page; more = page < result.meta.totalPages }
            else analytics = repository.merchantAnalytics(token, period)
        } catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Data belum dapat dimuat." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹  Kembali") }
            Text(if (section == MerchantInsight.SALES) "Penjualan" else "Analitik", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (section == MerchantInsight.SALES) Row { OutlinedTextField(query, { query = it.take(100) }, label = { Text("Cari pembeli atau produk") }, modifier = Modifier.weight(1f)); TextButton(onClick = { search = query.trim() }) { Text("Cari") } }
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("today" to "Hari ini", "month" to "Bulan ini", "year" to "Tahun ini").forEach { (value, label) -> FilterChip(selected = period == value, onClick = { period = value }, label = { Text(label) }) }
            }
        }
        if (loading) item { CircularProgressIndicator() }
        error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        if (section == MerchantInsight.ANALYTICS) analytics?.let { data -> item {
            Text("Konversi: ${data.conversionRate.value?.let { "$it%" } ?: "—"}")
            Text("Retensi: ${data.retentionRate.value?.let { "$it%" } ?: "—"}")
            Text("Nilai pesanan rata-rata: ${data.averageOrderValue.value?.let(::formatRupiah) ?: "—"}")
            Text("Perubahan dibanding periode sebelumnya: ${data.averageOrderValue.changePercent?.let { "$it%" } ?: "belum ada pembanding"}")
        } }
        if (section == MerchantInsight.SALES) {
            if (!loading && error == null && sales.isEmpty()) item { Text("Belum ada penjualan.") }
            items(sales, key = { it.id }) { sale -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(sale.itemTitle, fontWeight = FontWeight.SemiBold); Text(sale.buyerName); Text("${sale.orderedAt} · ${sale.status}"); Text(formatRupiah(sale.netAmount))
            } } }
            if (more && error == null) item { OutlinedButton(enabled = !loading, onClick = {
                loading = true
                scope.launch {
                    try { val result = repository.merchantSales(token, page + 1, search); sales = (sales + result.data).distinctBy { it.id }; page = result.meta.page; more = page < result.meta.totalPages }
                    catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Data belum dapat dimuat." }
                    finally { loading = false }
                }
            }) { Text("Muat lebih banyak") } }
        }
    }
}
