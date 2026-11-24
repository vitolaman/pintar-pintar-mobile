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
import com.getmobilestack.kmp.pintar.jobs.*
import com.getmobilestack.kmp.pintar.account.AccountSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun JobsScreen(repository: JobsRepository, applicationRepository: JobApplicationRepository, session: AccountSession?, onSignIn: () -> Unit, onBack: () -> Unit) {
    val token = session?.token
    var query by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var selected by remember { mutableStateOf<Job?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var savedOnly by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var applyJob by remember { mutableStateOf<Job?>(null) }
    var showApplications by remember { mutableStateOf(false) }
    var appliedJobIds by remember(token) { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(search, retry, savedOnly, token) {
        loading = true; error = false
        try {
            if (savedOnly && token != null) {
                val result = repository.saved(token, 1)
                jobs = result.data; page = result.meta.page; hasMore = page < result.meta.totalPages
            } else {
                val result = repository.search(search, 1, token)
                jobs = result.data.jobs; page = result.meta.page; hasMore = page < result.meta.totalPages
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    LaunchedEffect(token, showApplications) {
        if (token != null && !showApplications) {
            try {
                var pageNumber = 1
                val found = mutableSetOf<String>()
                do {
                    val result = applicationRepository.applications(token, pageNumber)
                    found.addAll(result.data.applications.map { it.job.id })
                    val morePages = result.meta.page < result.meta.totalPages && result.meta.page >= pageNumber
                    pageNumber = result.meta.page + 1
                } while (morePages)
                appliedJobIds = found
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Application status is rechecked by the server on submission. */ }
        }
    }
    if (showApplications && token != null) { MyApplicationsScreen(token, applicationRepository, onBack = { showApplications = false }); return }
    val applicationJob = applyJob
    if (applicationJob != null && session != null) { JobApplicationScreen(applicationJob, session, applicationRepository, onSaved = { applyJob = null; showApplications = true }, onBack = { applyJob = null }); return }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = { if (selected == null) onBack() else selected = null }) { Text("‹  Kembali") }
            Text(selected?.title ?: "Job Board", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        val current = selected
        if (current != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.merchant.name, fontWeight = FontWeight.SemiBold)
                Text("${current.location} · ${current.contractType} · ${current.workType}")
                Text(current.salary)
                Text("Persyaratan", fontWeight = FontWeight.Bold)
                Text(current.requirements)
                if (current.skills.isNotEmpty()) Text("Keahlian: ${current.skills.joinToString(", ")}")
                if (!current.canApply()) Text("Lowongan tidak lagi aktif.", color = MaterialTheme.colorScheme.error)
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(enabled = !loading, onClick = {
                    if (token == null) onSignIn() else {
                        loading = true; actionError = null
                        scope.launch {
                            try {
                                repository.setSaved(token, current.id, !current.isSaved)
                                selected = current.copy(isSaved = !current.isSaved)
                                jobs = if (savedOnly) jobs.filterNot { it.id == current.id } else jobs.map { if (it.id == current.id) it.copy(isSaved = !current.isSaved) else it }
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { actionError = "Lowongan belum dapat diperbarui." }
                            finally { loading = false }
                        }
                    }
                }) { Text(if (current.isSaved) "Hapus dari Tersimpan" else "Simpan Lowongan") }
                if (current.id in appliedJobIds) Button(onClick = { showApplications = true }) { Text("Lihat Progres Lamaran") }
                else Button(enabled = !loading && current.canApply(), onClick = { if (session == null) onSignIn() else applyJob = current }) { Text("Lamar") }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !savedOnly, onClick = { savedOnly = false }, label = { Text("Semua") })
                    FilterChip(selected = savedOnly, onClick = { if (token == null) onSignIn() else savedOnly = true }, label = { Text("Tersimpan") })
                    TextButton(onClick = { if (token == null) onSignIn() else showApplications = true }) { Text("Lamaran Saya") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it.take(100) }, modifier = Modifier.weight(1f), label = { Text("Cari lowongan") }, singleLine = true)
                    Button(onClick = { search = query.trim() }, modifier = Modifier.padding(top = 8.dp)) { Text("Cari") }
                }
            }
            if (loading) item { CircularProgressIndicator() }
            if (error) item { Text("Lowongan belum dapat dimuat."); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } }
            if (!loading && !error && jobs.isEmpty()) item { Text("Belum ada lowongan.") }
            items(jobs, key = { it.id }) { job ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !loading) {
                    loading = true; actionError = null
                    scope.launch {
                        try { selected = repository.detail(job.id, token) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = true }
                        finally { loading = false }
                    }
                }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(job.title, fontWeight = FontWeight.Bold)
                        Text(job.merchant.name)
                        Text("${job.location} · ${job.workType}")
                        Text(job.salary, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (hasMore && !error) item {
                OutlinedButton(enabled = !loading, onClick = {
                    loading = true
                    scope.launch {
                        try {
                            if (savedOnly && token != null) {
                                val result = repository.saved(token, page + 1)
                                jobs = jobs + result.data; page = result.meta.page; hasMore = page < result.meta.totalPages
                            } else {
                                val result = repository.search(search, page + 1, token)
                                jobs = jobs + result.data.jobs; page = result.meta.page; hasMore = page < result.meta.totalPages
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = true }
                        finally { loading = false }
                    }
                }) { Text("Muat lebih banyak") }
            }
        }
    }
}
