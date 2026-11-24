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
import com.getmobilestack.kmp.pintar.learning.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DiscussionsScreen(classId: String, token: String, repository: ClassWorkspaceRepository, onBack: () -> Unit) {
    var threads by remember(classId, token) { mutableStateOf<List<DiscussionThread>>(emptyList()) }
    var page by remember(classId, token) { mutableIntStateOf(1) }
    var hasMore by remember(classId, token) { mutableStateOf(false) }
    var loading by remember(classId, token) { mutableStateOf(true) }
    var error by remember(classId, token) { mutableStateOf<String?>(null) }
    var attempt by remember(classId, token) { mutableIntStateOf(0) }
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var replyThread by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    var reloadAllPages by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(classId, token, attempt) {
        loading = true; error = null
        try {
            val result = repository.threads(token, classId, 1)
            var loaded = result.data
            var loadedPage = result.meta.page
            var totalPages = result.meta.totalPages
            val target = if (reloadAllPages) page else 1
            while (loadedPage < target && loadedPage < totalPages) {
                val next = repository.threads(token, classId, loadedPage + 1)
                if (next.meta.page <= loadedPage) break
                loaded = (loaded + next.data).distinctBy { it.id }
                loadedPage = next.meta.page; totalPages = next.meta.totalPages
            }
            threads = loaded; page = loadedPage; hasMore = page < totalPages; reloadAllPages = false
        } catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Diskusi belum dapat dimuat." }
        finally { loading = false }
    }

    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹  Kembali") }
            Text("Diskusi Kelas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showForm = !showForm }, enabled = !loading) { Text(if (showForm) "Tutup" else "Buat Diskusi") }
        }
        if (showForm) item {
            OutlinedTextField(title, { title = it.take(160) }, label = { Text("Judul") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { content = it.take(5000) }, label = { Text("Pertanyaan atau diskusi") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            Button(enabled = !loading && title.isNotBlank() && content.isNotBlank(), onClick = {
                loading = true; error = null
                scope.launch {
                    try { repository.createThread(token, classId, title, content); title = ""; content = ""; showForm = false; reloadAllPages = true; attempt++ }
                    catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message; loading = false }
                    catch (_: Exception) { error = "Diskusi belum dapat dibuat."; loading = false }
                }
            }) { Text("Kirim Diskusi") }
        }
        if (loading) item { CircularProgressIndicator() }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(enabled = !loading, onClick = { attempt++ }) { Text("Muat Ulang") } } }
        if (!loading && error == null && threads.isEmpty()) item { Text("Belum ada diskusi.") }
        items(threads, key = { it.id }) { thread ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(thread.title, fontWeight = FontWeight.Bold)
                    thread.badge?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Text("${thread.author.name} · ${thread.author.role}", style = MaterialTheme.typography.bodySmall)
                    thread.createdAt?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(thread.content)
                    Text("${thread.commentCount} komentar", style = MaterialTheme.typography.bodySmall)
                    thread.comments.forEach { comment ->
                        Column(Modifier.padding(start = 12.dp)) { Text(comment.author.name, fontWeight = FontWeight.SemiBold); comment.createdAt?.let { Text(it, style = MaterialTheme.typography.bodySmall) }; Text(comment.content) }
                    }
                    TextButton(enabled = !loading, onClick = { replyThread = if (replyThread == thread.id) null else thread.id; reply = "" }) { Text("Balas") }
                    if (replyThread == thread.id) {
                        OutlinedTextField(reply, { reply = it.take(2000) }, label = { Text("Komentar") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !loading && reply.isNotBlank(), onClick = {
                            loading = true; error = null
                            scope.launch {
                                try { repository.addComment(token, thread.id, reply); reply = ""; replyThread = null; reloadAllPages = true; attempt++ }
                                catch (e: CancellationException) { throw e }
                                catch (e: AccountApiException) { error = e.message; loading = false }
                                catch (_: Exception) { error = "Komentar belum dapat dikirim."; loading = false }
                            }
                        }) { Text("Kirim Balasan") }
                    }
                }
            }
        }
        if (hasMore && error == null) item {
            OutlinedButton(enabled = !loading, onClick = {
                loading = true
                scope.launch {
                    try {
                        val result = repository.threads(token, classId, page + 1)
                        threads = (threads + result.data).distinctBy { it.id }; page = result.meta.page; hasMore = page < result.meta.totalPages
                    } catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Diskusi belum dapat dimuat." }
                    finally { loading = false }
                }
            }) { Text("Muat lebih banyak") }
        }
    }
}
