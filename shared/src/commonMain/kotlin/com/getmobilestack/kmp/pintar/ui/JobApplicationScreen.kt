package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.account.AccountSession
import com.getmobilestack.kmp.pintar.account.isValidEmail
import com.getmobilestack.kmp.pintar.jobs.*
import com.getmobilestack.kmp.pintar.learning.safeLearningUrl
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun JobApplicationScreen(job: Job, session: AccountSession, repository: JobApplicationRepository, onSaved: () -> Unit, onBack: () -> Unit) {
    var name by remember(job.id) { mutableStateOf(session.user.name) }
    var email by remember(job.id) { mutableStateOf(session.user.email) }
    var phone by remember(job.id) { mutableStateOf("") }
    var linkedin by remember(job.id) { mutableStateOf("") }
    var note by remember(job.id) { mutableStateOf("") }
    var file by remember(job.id) { mutableStateOf<PlatformFile?>(null) }
    var asset by remember(job.id) { mutableStateOf<String?>(null) }
    var loading by remember(job.id) { mutableStateOf(false) }
    var success by remember(job.id) { mutableStateOf(false) }
    var error by remember(job.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = PickerType.File(extensions = listOf("pdf", "doc", "docx"))) { selected ->
        if (selected != null) try {
            val problem = cvFileError(selected.name, selected.getSize() ?: 1L)
            if (problem == null) { file = selected; asset = null }
            error = problem
        } catch (_: Exception) { error = "File belum dapat dibaca." }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !loading, onClick = onBack) { Text("‹  Kembali") }
        Text("Lamar ${job.title}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(job.merchant.name)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (success) {
            Text("Lamaran berhasil dikirim.", color = MaterialTheme.colorScheme.primary)
            Button(onClick = onSaved) { Text("Lihat Lowongan") }
        } else {
            OutlinedTextField(name, { name = it.take(150) }, label = { Text("Nama") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it.take(255) }, label = { Text("Email") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.take(32) }, label = { Text("WhatsApp") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(linkedin, { linkedin = it.take(500) }, label = { Text("LinkedIn (opsional)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it.take(2000) }, label = { Text("Surat pengantar (opsional)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = !loading, onClick = { picker.launch() }) { Text("Pilih CV (PDF, DOC, DOCX · maks. 10 MB)") }
            file?.let { Text(it.name) }
            Button(enabled = !loading, onClick = {
                val selected = file
                if (name.isBlank() || !isValidEmail(email) || phone.isBlank() || selected == null) error = "Lengkapi nama, email, WhatsApp, dan CV."
                else {
                    loading = true; error = null
                    scope.launch {
                        try {
                            val size = selected.getSize()
                            if (size != null) require(cvFileError(selected.name, size) == null) { "CV tidak valid atau terlalu besar." }
                            val uploaded = asset ?: run {
                                val bytes = selected.readBytes()
                                require(cvFileError(selected.name, bytes.size.toLong()) == null) { "CV tidak valid atau terlalu besar." }
                                repository.uploadCv(session.token, selected.name, bytes).also { asset = it }
                            }
                            repository.apply(session.token, job.id, ApplyJob(name.trim(), email.trim(), phone.trim(), linkedin.trim().ifBlank { null }, uploaded, note.trim().ifBlank { null }))
                            success = true
                        } catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { error = e.message }
                        catch (e: IllegalArgumentException) { error = e.message }
                        catch (_: Exception) { error = "Lamaran belum dapat dikirim." }
                        finally { loading = false }
                    }
                }
            }) { Text(if (loading) "Mengirim..." else "Kirim Lamaran") }
        }
    }
}

@Composable
fun MyApplicationsScreen(token: String, repository: JobApplicationRepository, onBack: () -> Unit) {
    var applications by remember(token) { mutableStateOf<List<JobApplication>>(emptyList()) }
    var page by remember(token) { mutableIntStateOf(1) }
    var more by remember(token) { mutableStateOf(false) }
    var loading by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var retry by remember(token) { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, retry) {
        loading = true; error = null
        try { val result = repository.applications(token, 1); applications = result.data.applications; page = result.meta.page; more = page < result.meta.totalPages }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Lamaran belum dapat dimuat." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali") }; Text("Lamaran Saya", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        if (loading) item { CircularProgressIndicator() }
        error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        if (!loading && error == null && applications.isEmpty()) item { Text("Belum ada lamaran.") }
        items(applications, key = { it.id }) { application -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(application.job.title, fontWeight = FontWeight.SemiBold); Text(application.merchant.name); Text("Status: ${application.status}"); Text("Dikirim: ${application.createdAt}")
            application.interviewAt?.let { Text("Wawancara: $it") }
            application.decidedAt?.let { Text("Keputusan: $it") }
            safeLearningUrl(application.interviewUrl)?.let { link -> TextButton(onClick = { uri.openUri(link) }) { Text("Buka Wawancara") } }
        } } }
        if (more && error == null) item { OutlinedButton(enabled = !loading, onClick = {
            loading = true
            scope.launch {
                try { val result = repository.applications(token, page + 1); applications = (applications + result.data.applications).distinctBy { it.id }; page = result.meta.page; more = page < result.meta.totalPages }
                catch (e: CancellationException) { throw e }
                catch (e: AccountApiException) { error = e.message }
                catch (_: Exception) { error = "Lamaran belum dapat dimuat." }
                finally { loading = false }
            }
        }) { Text("Muat lebih banyak") } }
    }
}
