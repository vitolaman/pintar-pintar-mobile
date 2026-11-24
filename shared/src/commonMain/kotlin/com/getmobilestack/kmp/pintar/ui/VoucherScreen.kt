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
import com.getmobilestack.kmp.pintar.shopping.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun VoucherScreen(repository: VoucherRepository, token: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    var vouchers by remember { mutableStateOf<List<PublicVoucher>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var claimError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var claimed by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, retry) {
        loading = true; error = null
        try { vouchers = repository.list() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Voucher belum dapat dimuat." }
        finally { loading = false }
    }
    val filtered = vouchers.filter { it.name.contains(query, true) || it.code.contains(query, true) || it.merchantName.contains(query, true) }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹  Kembali") }
            Text("Voucher", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Klaim voucher lalu periksa kelayakannya pada ringkasan checkout.")
            OutlinedTextField(query, { query = it.take(100) }, label = { Text("Cari voucher atau merchant") }, modifier = Modifier.fillMaxWidth())
        }
        if (loading) item { CircularProgressIndicator() }
        error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        claimError?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
        if (!loading && error == null && filtered.isEmpty()) item { Text("Tidak ada voucher ditemukan.") }
        items(filtered, key = { it.id }) { voucher ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(voucher.name, fontWeight = FontWeight.SemiBold)
                Text("${voucher.merchantName} · ${voucher.code}")
                Text(if (voucher.discountType == "percentage") "Diskon ${voucher.discountValue}%" else "Diskon ${formatRupiah(voucher.discountValue)}")
                voucher.description?.let { Text(it) }
                voucher.minimumPurchase?.let { Text("Minimum ${formatRupiah(it)}") }
                voucher.endsAt?.let { Text("Berlaku sampai $it") }
                if (voucher.id in claimed) Button(onClick = { onSelect(voucher.code) }) { Text("Gunakan Kode") }
                else OutlinedButton(enabled = !loading, onClick = {
                    loading = true; claimError = null
                    scope.launch {
                        try { repository.claim(token, voucher.id); claimed = claimed + voucher.id }
                        catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { claimError = e.message }
                        catch (_: Exception) { claimError = "Voucher belum dapat diklaim." }
                        finally { loading = false }
                    }
                }) { Text("Klaim Voucher") }
            } }
        }
    }
}
