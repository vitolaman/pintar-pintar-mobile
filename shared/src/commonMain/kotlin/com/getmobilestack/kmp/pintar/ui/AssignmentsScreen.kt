package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.learning.*
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AssignmentsScreen(classId: String, token: String, repository: AssignmentRepository, onBack: () -> Unit) {
    var assignments by remember(classId) { mutableStateOf<List<Assignment>>(emptyList()) }
    var selected by remember(classId) { mutableStateOf<Assignment?>(null) }
    var quiz by remember(classId) { mutableStateOf<Quiz?>(null) }
    var answers by remember(classId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var file by remember(classId) { mutableStateOf<PlatformFile?>(null) }
    var fileError by remember(classId) { mutableStateOf<String?>(null) }
    var error by remember(classId) { mutableStateOf<String?>(null) }
    var loading by remember(classId) { mutableStateOf(true) }
    var refresh by remember(classId) { mutableIntStateOf(0) }
    var confirmSubmit by remember(classId) { mutableStateOf(false) }
    var confirmFileSubmit by remember(classId) { mutableStateOf(false) }
    var submitted by remember(classId) { mutableStateOf(false) }
    var uploadedAsset by remember(classId) { mutableStateOf<String?>(null) }
    var resourceLoading by remember(classId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val picker = rememberFilePickerLauncher(type = PickerType.File(extensions = listOf("pdf", "dwg", "zip"))) { selectedFile ->
        if (selectedFile != null) {
            try {
                file = selectedFile
                uploadedAsset = null
                fileError = assignmentFileError(selectedFile.name, selectedFile.getSize() ?: 1L)
            } catch (_: Exception) { file = null; uploadedAsset = null; fileError = "File belum dapat dibaca. Pilih kembali." }
        }
    }

    LaunchedEffect(classId, token, refresh) {
        loading = true; error = null
        try { assignments = repository.list(token, classId) }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Daftar tugas belum dapat dimuat." }
        finally { loading = false }
    }
    if (confirmSubmit) AlertDialog(
        onDismissRequest = { confirmSubmit = false },
        title = { Text("Kirim jawaban quiz?") },
        text = { Text("Pastikan semua jawaban sudah benar sebelum mengirim.") },
        dismissButton = { TextButton(onClick = { confirmSubmit = false }) { Text("Batal") } },
        confirmButton = { TextButton(onClick = {
            val current = quiz
            val entry = selected
            confirmSubmit = false
            if (current != null && entry != null) {
                loading = true; error = null
                scope.launch {
                    try {
                        val result = repository.submitQuiz(token, entry.id, current.questions.map { QuizAnswer(it.id, answers.getValue(it.id)) })
                        selected = result; quiz = current.copy(submission = result.submission); submitted = true
                        try { quiz = repository.quiz(token, entry.id) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* Submission succeeded; results can be refreshed later. */ }
                    } catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Jawaban belum dapat dikirim." }
                    finally { loading = false }
                }
            }
        }) { Text("Kirim") } },
    )
    if (confirmFileSubmit) AlertDialog(
        onDismissRequest = { confirmFileSubmit = false },
        title = { Text("Kirim tugas?") },
        text = { Text("File ${file?.name.orEmpty()} akan dikirim sebagai jawaban Anda.") },
        dismissButton = { TextButton(onClick = { confirmFileSubmit = false }) { Text("Batal") } },
        confirmButton = { TextButton(onClick = {
            confirmFileSubmit = false
            val selectedFile = file
            val entry = selected
            if (selectedFile != null && entry != null) {
                loading = true; error = null
                scope.launch {
                    try {
                        val size = selectedFile.getSize()
                        val sizeError = size?.let { assignmentFileError(selectedFile.name, it) }
                        if (sizeError != null) fileError = sizeError else {
                            val asset = uploadedAsset ?: run {
                                val bytes = selectedFile.readBytes()
                                val validation = assignmentFileError(selectedFile.name, bytes.size.toLong())
                                if (validation != null) { fileError = validation; null }
                                else repository.uploadFile(token, selectedFile.name, bytes).also { uploadedAsset = it }
                            }
                            if (asset != null) { selected = repository.submitFile(token, entry.id, asset); file = null; uploadedAsset = null; submitted = true }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Tugas belum dapat dikirim." }
                    finally { loading = false }
                }
            }
        }) { Text("Kirim") } },
    )
    val assignment = selected
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(enabled = !loading, onClick = { if (assignment == null) onBack() else { selected = null; quiz = null; answers = emptyMap(); file = null; uploadedAsset = null; fileError = null; error = null; refresh++ } }) { Text("‹  Kembali") }
            ScreenHeading(assignment?.title ?: "Tugas dan Quiz")
        }
        if (loading) item { LoadingState("Memuat tugas...") }
        error?.let { message -> item { StatusMessage(message, isError = true); if (assignment == null) TextButton(onClick = { refresh++ }) { Text("Coba Lagi") } } }
        if (assignment == null) {
            if (!loading && error == null && assignments.isEmpty()) item { Text("Belum ada tugas.") }
            items(assignments, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.title, fontWeight = FontWeight.Bold)
                        Text(if (entry.type == "quiz") "Quiz" else "Tugas file")
                        entry.due?.let { Text("Tenggat: $it") }
                        entry.submission?.let { Text("Terkirim · Nilai: ${it.score?.toString() ?: "Menunggu penilaian"}") }
                        TextButton(enabled = !loading, onClick = {
                            selected = entry; file = null; uploadedAsset = null; fileError = null; quiz = null; answers = emptyMap(); error = null; submitted = false
                            if (entry.type == "quiz") {
                                loading = true
                                scope.launch {
                                    try { quiz = repository.quiz(token, entry.id) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: AccountApiException) { error = e.message }
                                    catch (_: Exception) { error = "Quiz belum dapat dimuat." }
                                    finally { loading = false }
                                }
                            }
                        }) { Text("Lihat") }
                    }
                }
            }
        } else {
            item {
                assignment.description?.let { Text(it) }
                assignment.due?.let { Text("Tenggat: $it") }
                if (assignment.isPastDue) Text("Tenggat telah lewat.", color = MaterialTheme.colorScheme.error)
                (assignment.resources.ifEmpty { listOfNotNull(assignment.resource) }).forEach { resource ->
                    TextButton(enabled = !resourceLoading, onClick = {
                        resourceLoading = true; error = null
                        scope.launch {
                            try {
                                val fresh = repository.list(token, classId).firstOrNull { it.id == assignment.id }
                                val resources = fresh?.resources?.ifEmpty { listOfNotNull(fresh.resource) }.orEmpty()
                                val link = resources.firstOrNull { it.name == resource.name }?.downloadUrl?.let(::safeLearningUrl)
                                if (link != null) uriHandler.openUri(link) else error = "File tugas belum tersedia."
                            } catch (e: CancellationException) { throw e }
                            catch (e: AccountApiException) { error = e.message }
                            catch (_: Exception) { error = "File tugas belum dapat dimuat." }
                            finally { resourceLoading = false }
                        }
                    }) { Text(if (resourceLoading) "Memuat file..." else "Unduh ${resource.name}") }
                }
                if (submitted) Text("Jawaban berhasil dikirim.", color = MaterialTheme.colorScheme.primary)
                val submission = quiz?.submission ?: assignment.submission
                submission?.let {
                    Text("Status: ${it.status}")
                    Text("Nilai: ${it.score?.toString() ?: "Menunggu penilaian"}")
                    it.fileName?.let { name -> Text("File: $name") }
                    it.feedback?.let { feedback -> Text("Umpan balik: $feedback") }
                }
            }
            if (assignment.type == "quiz" && quiz == null && !loading) item {
                TextButton(onClick = {
                    loading = true; error = null
                    scope.launch {
                        try { quiz = repository.quiz(token, assignment.id) }
                        catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { error = e.message }
                        catch (_: Exception) { error = "Quiz belum dapat dimuat." }
                        finally { loading = false }
                    }
                }) { Text("Muat Quiz") }
            }
            quiz?.let { current ->
                item {
                    current.description?.takeIf(String::isNotBlank)?.let { Text(it) }
                    if (current.submission == null && assignment.submission == null && !submitted) {
                        Text("${current.questions.count { !answers[it.id].isNullOrBlank() }} dari ${current.questions.size} soal terjawab · ${current.questions.sumOf { it.weight }} poin")
                    }
                }
                if (current.submission == null && assignment.submission == null && !submitted) {
                    current.questions.forEachIndexed { index, question -> item(key = question.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${index + 1}. ${question.text}", fontWeight = FontWeight.SemiBold)
                            Text("${question.weight} poin")
                            if (question.type == "multiple_choice") Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                question.options.orEmpty().forEach { option -> AccessibleRadioRow(option, selected = answers[question.id] == option, enabled = !loading, onClick = { answers = answers + (question.id to option) }) }
                            } else if (question.type == "essay") OutlinedTextField(answers[question.id].orEmpty(), { answers = answers + (question.id to it.take(10000)) }, label = { Text("Jawaban soal ${index + 1}") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
                            else Text("Jenis soal belum didukung.", color = MaterialTheme.colorScheme.error)
                        }
                    } }
                    item {
                        Button(enabled = !loading && current.questions.isNotEmpty() && current.questions.all { it.type in setOf("multiple_choice", "essay") && !answers[it.id].isNullOrBlank() }, onClick = { confirmSubmit = true }, modifier = Modifier.fillMaxWidth()) { Text("Kirim Jawaban") }
                    }
                }
            }
            if (assignment.type == "file_upload" && !submitted) item {
                Text("PDF, DWG, atau ZIP; maksimal 20 MB.")
                if (assignment.submission != null) Text("Pilih file baru untuk mengirim ulang tugas.")
                OutlinedButton(enabled = !loading, onClick = { picker.launch() }) { Text("Pilih File") }
                file?.let { Text(it.name) }
                fileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = !loading && file != null && fileError == null, onClick = { confirmFileSubmit = true }, modifier = Modifier.fillMaxWidth()) { Text("Kirim Tugas") }
            }
        }
    }
}
