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

enum class DashboardRole { MERCHANT, MENTOR }

@Composable
fun DashboardScreen(role: DashboardRole, token: String, repository: DashboardRepository, management: MerchantManagementRepository, onBack: () -> Unit) {
    var merchant by remember(role, token) { mutableStateOf<MerchantDashboard?>(null) }
    var mentor by remember(role, token) { mutableStateOf<MentorDashboard?>(null) }
    var classes by remember(role, token) { mutableStateOf<List<DashboardClass>>(emptyList()) }
    var page by remember(role, token) { mutableIntStateOf(1) }
    var hasMore by remember(role, token) { mutableStateOf(false) }
    var period by remember(role, token) { mutableIntStateOf(30) }
    var loading by remember(role, token) { mutableStateOf(true) }
    var error by remember(role, token) { mutableStateOf<String?>(null) }
    var retry by remember(role, token) { mutableIntStateOf(0) }
    var merchantList by remember(role, token) { mutableStateOf<MerchantList?>(null) }
    var insight by remember(role, token) { mutableStateOf<MerchantInsight?>(null) }
    var editingClass by remember(role, token) { mutableStateOf<String?>(null) }
    var creatingClass by remember(role, token) { mutableStateOf(false) }
    var managingClass by remember(role, token) { mutableStateOf<String?>(null) }
    var showMentorProfile by remember(role, token) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(role, token, period, retry) {
        loading = true; error = null
        try {
            if (role == DashboardRole.MERCHANT) {
                merchant = repository.merchantDashboard(token, period)
                val result = repository.merchantClasses(token, 1)
                classes = result.data; page = result.meta.page; hasMore = page < result.meta.totalPages
            } else {
                mentor = repository.mentorDashboard(token)
                classes = repository.mentorClasses(token)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Dashboard belum dapat dimuat." }
        finally { loading = false }
    }
    merchantList?.let { section -> MerchantListScreen(section, token, repository, onBack = { merchantList = null }); return }
    insight?.let { section -> MerchantInsightsScreen(section, token, repository, onBack = { insight = null }); return }
    if (creatingClass || editingClass != null) {
        MerchantClassEditor(editingClass, token, management, onSaved = { editingClass = null; creatingClass = false; retry++ }, onBack = { editingClass = null; creatingClass = false })
        return
    }
    if (showMentorProfile) { MentorProfileScreen(token, repository, onBack = { showMentorProfile = false }); return }
    managingClass?.let { classId -> ManageClassContentScreen(classId, token, management, onBack = { managingClass = null; retry++ }); return }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali") }; Text(if (role == DashboardRole.MERCHANT) "Dashboard Merchant" else "Dashboard Mentor", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        if (role == DashboardRole.MERCHANT) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { days -> FilterChip(selected = period == days, onClick = { period = days }, label = { Text("$days hari") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { insight = MerchantInsight.SALES }) { Text("Penjualan") }
                OutlinedButton(onClick = { insight = MerchantInsight.ANALYTICS }) { Text("Analitik") }
            }
            OutlinedButton(onClick = { creatingClass = true }) { Text("Buat Kelas") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { merchantList = MerchantList.PRODUCTS }) { Text("Produk Digital") }
                OutlinedButton(onClick = { merchantList = MerchantList.CUSTOMERS }) { Text("Pelanggan") }
            }
        }
        if (role == DashboardRole.MENTOR) item { OutlinedButton(onClick = { showMentorProfile = true }) { Text("Pengaturan Mentor") } }
        if (loading) item { CircularProgressIndicator() }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        merchant?.takeIf { role == DashboardRole.MERCHANT }?.let { data -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pendapatan: ${formatRupiah(data.revenue.current)}")
                Text("Transaksi: ${data.transactions.current.toInt()} · Siswa: ${data.students.current.toInt()}")
                Text("Produk: ${data.catalogTotal} · Rating: ${data.rating?.toString() ?: "—"} (${data.reviewCount} ulasan)")
                if (data.activities.isNotEmpty()) {
                    Text("Aktivitas Terbaru", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    data.activities.forEach { Text("${it.actorName} · ${it.itemTitle}") }
                }
            }
        } }
        mentor?.takeIf { role == DashboardRole.MENTOR }?.let { data -> item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kelas aktif: ${data.stats.activeClasses} · Siswa: ${data.stats.totalStudents}")
                Text("Sesi mendatang: ${data.stats.upcomingSessions} · Rating: ${data.stats.rating} (${data.stats.reviewCount} ulasan)")
                if (data.upcomingSessions.isNotEmpty()) {
                    Text("Sesi Mendatang", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    data.upcomingSessions.forEach { Text("${it.title} · ${it.classTitle} · ${it.date} ${it.time.orEmpty()}") }
                }
                if (data.classProgress.isNotEmpty()) {
                    Text("Progres Kelas", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    data.classProgress.forEach { Text("${it.title} · ${it.enrolledCount} siswa · ${it.averageProgress}%") }
                }
                if (data.recentMessages.isNotEmpty()) {
                    Text("Pesan Terbaru", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    data.recentMessages.forEach { Text("${it.userName} · ${it.classTitle}: ${it.content}") }
                }
            }
        } }
        item { Text("Daftar Kelas", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        if (!loading && error == null && classes.isEmpty()) item { Text("Belum ada kelas.") }
        items(classes, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(entry.title, fontWeight = FontWeight.SemiBold); Text("Status: ${entry.status}"); if (role == DashboardRole.MERCHANT) { TextButton(onClick = { editingClass = entry.id }) { Text("Edit Kelas") }; TextButton(onClick = { managingClass = entry.id }) { Text("Kelola Materi") } } } }
        }
        if (role == DashboardRole.MERCHANT && hasMore && error == null) item {
            OutlinedButton(enabled = !loading, onClick = {
                loading = true
                scope.launch {
                    try {
                        val result = repository.merchantClasses(token, page + 1)
                        classes = classes + result.data; page = result.meta.page; hasMore = page < result.meta.totalPages
                    } catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Kelas belum dapat dimuat." }
                    finally { loading = false }
                }
            }) { Text("Muat lebih banyak") }
        }
        item { Text("Pengelolaan kelas dan beberapa fitur dashboard belum tersedia.", style = MaterialTheme.typography.bodySmall) }
    }
}
