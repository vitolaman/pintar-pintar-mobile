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
import com.getmobilestack.kmp.pintar.dashboard.*
import com.getmobilestack.kmp.pintar.learning.safeLearningUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ManageClassContentScreen(classId: String, token: String, repository: MerchantManagementRepository, onBack: () -> Unit) {
    var chapters by remember(classId) { mutableStateOf<List<ManagedChapter>>(emptyList()) }
    var loading by remember(classId) { mutableStateOf(true) }; var error by remember(classId) { mutableStateOf<String?>(null) }; var retry by remember(classId) { mutableIntStateOf(0) }
    var chapterTitle by remember { mutableStateOf("") }; var chapterDescription by remember { mutableStateOf("") }
    var videoChapter by remember { mutableStateOf<String?>(null) }; var videoTitle by remember { mutableStateOf("") }; var videoUrl by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(classId, retry) {
        loading = true; error = null
        try { chapters = repository.chapters(token, classId) }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Materi belum dapat dimuat." }
        finally { loading = false }
    }
    fun mutate(action: suspend () -> Unit) {
        loading = true; error = null
        scope.launch {
            try { action(); retry++ }
            catch (e: CancellationException) { throw e }
            catch (e: AccountApiException) { error = e.message; loading = false }
            catch (_: Exception) { error = "Materi belum dapat disimpan."; loading = false }
        }
    }
    deleteTarget?.let { (chapter, video) -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text(if (video == null) "Hapus bab?" else "Hapus video?") }, text = { Text(if (video == null) "Materi dalam bab ini juga dapat terhapus." else "Video akan dihapus dari bab.") }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } }, confirmButton = { TextButton(onClick = { deleteTarget = null; mutate { if (video == null) repository.deleteChapter(token, classId, chapter) else repository.deleteVideo(token, classId, chapter, video) } }) { Text("Hapus") } }) }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("‹  Kembali") }; Text("Materi Kelas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } }
            OutlinedTextField(chapterTitle, { chapterTitle = it.take(255) }, label = { Text("Judul Bab") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(chapterDescription, { chapterDescription = it.take(10000) }, label = { Text("Deskripsi Bab") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            Button(enabled = !loading && chapterTitle.isNotBlank(), onClick = { mutate { repository.addChapter(token, classId, chapterTitle, chapterDescription); chapterTitle = ""; chapterDescription = "" } }) { Text("Tambah Bab") }
        }
        if (!loading && error == null && chapters.isEmpty()) item { Text("Belum ada bab.") }
        items(chapters, key = { it.id }) { chapter -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(chapter.title, fontWeight = FontWeight.Bold); chapter.description?.let { Text(it) }
            chapter.videos.forEach { video -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(video.title, modifier = Modifier.weight(1f)); TextButton(enabled = !loading, onClick = { deleteTarget = chapter.id to video.id }) { Text("Hapus") } } }
            Row { TextButton(enabled = !loading, onClick = { videoChapter = if (videoChapter == chapter.id) null else chapter.id }) { Text("Tambah Video Link") }; TextButton(enabled = !loading, onClick = { deleteTarget = chapter.id to null }) { Text("Hapus Bab") } }
            if (videoChapter == chapter.id) {
                OutlinedTextField(videoTitle, { videoTitle = it.take(255) }, label = { Text("Judul Video") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(videoUrl, { videoUrl = it.take(2048) }, label = { Text("URL Video HTTPS") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
                Button(enabled = !loading && videoTitle.isNotBlank(), onClick = {
                    if (safeLearningUrl(videoUrl)?.startsWith("https://", true) != true) error = "Masukkan URL HTTPS yang valid."
                    else mutate { repository.addVideoLink(token, classId, chapter.id, videoTitle, videoUrl); videoTitle = ""; videoUrl = ""; videoChapter = null }
                }) { Text("Simpan Video") }
            }
        } } }
    }
}
