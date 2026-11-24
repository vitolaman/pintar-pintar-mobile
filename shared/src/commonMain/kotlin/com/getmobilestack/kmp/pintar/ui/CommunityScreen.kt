package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.getmobilestack.kmp.pintar.account.*
import com.getmobilestack.kmp.pintar.community.*
import com.getmobilestack.kmp.pintar.detail.ProductKind
import com.getmobilestack.kmp.pintar.detail.ProductReference
import com.getmobilestack.kmp.pintar.learning.safeLearningUrl
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(repository: CommunityRepository, imageUploader: AvatarUploader, session: AccountSession?, onSignIn: () -> Unit, onProduct: (ProductReference) -> Unit, onBack: () -> Unit) {
    val token = session?.token
    var groups by remember(token) { mutableStateOf<List<CommunityGroup>>(emptyList()) }
    var group by remember(token) { mutableStateOf<CommunityGroup?>(null) }
    var threads by remember(token) { mutableStateOf<List<CommunityThread>>(emptyList()) }
    var requests by remember(token) { mutableStateOf<List<CommunityRequest>>(emptyList()) }
    var detail by remember(token) { mutableStateOf<CommunityThread?>(null) }
    var search by remember { mutableStateOf("") }
    var appliedSearch by remember { mutableStateOf("") }
    var joined by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var more by remember { mutableStateOf(false) }
    var groupPage by remember { mutableIntStateOf(1) }
    var moreGroups by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var access by remember { mutableStateOf("public") }
    var image by remember { mutableStateOf<PlatformFile?>(null) }
    var imageAsset by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val picker = rememberFilePickerLauncher(type = PickerType.File(extensions = listOf("png", "jpg", "jpeg", "webp"))) { selected ->
        if (selected != null) try {
            val issue = avatarFileError(selected.name, selected.getSize() ?: 1L)
            if (issue == null) { image = selected; imageAsset = null }
            error = issue
        } catch (_: Exception) { error = "Gambar belum dapat dibaca." }
    }
    LaunchedEffect(token, appliedSearch, joined, retry) {
        loading = true; error = null
        try {
            val result = repository.groups(token, 1, appliedSearch, joined && token != null)
            groups = result.data; groupPage = result.meta.page; moreGroups = groupPage < result.meta.totalPages
            group?.let { existing -> group = repository.group(token, existing.id) }
            val current = group
            if (current == null || current.access == "public" || current.membership in setOf("member", "owner")) {
                val feed = repository.threads(token, current?.id, 1)
                threads = feed.data; page = feed.meta.page; more = page < feed.meta.totalPages
            } else { threads = emptyList(); more = false }
            requests = if (token != null && current != null && current.owner.id == session?.user?.id) repository.requests(token, current.id) else emptyList()
            detail?.let { detail = repository.thread(token, it.id) }
        } catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Komunitas belum dapat dimuat." }
        finally { loading = false }
    }
    fun mutate(action: suspend () -> Unit) {
        loading = true; error = null
        scope.launch {
            try { action(); retry++ }
            catch (e: CancellationException) { throw e }
            catch (e: AccountApiException) { error = e.message; loading = false }
            catch (_: Exception) { error = "Aksi belum dapat diproses."; loading = false }
        }
    }
    confirmDelete?.let { id -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Hapus thread?") }, text = { Text("Thread akan dihapus.") }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Batal") } }, confirmButton = { TextButton(onClick = { confirmDelete = null; if (token != null) mutate { repository.deleteThread(token, id); detail = null } }) { Text("Hapus") } }) }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = { when { creating -> creating = false; detail != null -> detail = null; group != null -> { group = null; retry++ }; else -> onBack() } }) { Text("‹  Kembali") }
            Text(if (creating) "Buat Grup" else group?.name ?: "Komunitas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } }
            if (loading) CircularProgressIndicator()
        }
        if (creating) item {
            OutlinedTextField(groupName, { groupName = it.take(80) }, label = { Text("Nama Grup") }, modifier = Modifier.fillMaxWidth(), enabled = !loading)
            OutlinedTextField(description, { description = it.take(500) }, label = { Text("Deskripsi") }, modifier = Modifier.fillMaxWidth(), enabled = !loading)
            Row { listOf("public" to "Publik", "request" to "Persetujuan").forEach { (value, label) -> FilterChip(selected = access == value, onClick = { access = value }, label = { Text(label) }) } }
            OutlinedButton(enabled = !loading, onClick = { picker.launch() }) { Text("Pilih Gambar Grup") }
            image?.let { Text(it.name) }
            Button(enabled = !loading && groupName.isNotBlank() && image != null, onClick = {
                val file = image ?: return@Button
                if (token == null) onSignIn() else mutate {
                    val asset = imageAsset ?: run {
                        val size = file.getSize(); if (size != null) require(avatarFileError(file.name, size) == null)
                        val bytes = file.readBytes(); require(avatarFileError(file.name, bytes.size.toLong()) == null)
                        imageUploader.upload(token, file.name, bytes).also { imageAsset = it }
                    }
                    group = repository.createGroup(token, groupName, description, access, asset)
                    creating = false; groupName = ""; description = ""; image = null; imageAsset = null
                }
            }) { Text("Buat Grup") }
        } else if (detail != null) {
            val thread = detail!!
            item {
                Text(thread.author.name, fontWeight = FontWeight.Bold); Text(thread.content); Text(thread.createdAt)
                thread.attachment?.let { attachment -> safeLearningUrl(attachment.downloadUrl)?.let { link -> TextButton(onClick = { uri.openUri(link) }) { Text("Buka ${attachment.name}") } } }
                thread.item?.let { product -> ProductKind.fromApiType(product.type)?.let { kind -> TextButton(onClick = { onProduct(ProductReference(product.id, kind)) }) { Text(product.title) } } }
                Text("${thread.replies.size} balasan", fontWeight = FontWeight.SemiBold)
            }
            items(thread.replies, key = { it.id }) { entry -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(entry.author.name, fontWeight = FontWeight.SemiBold); Text(entry.content); Text(entry.createdAt, style = MaterialTheme.typography.bodySmall) } } }
            item {
                OutlinedTextField(reply, { reply = it.take(5000) }, label = { Text("Balasan") }, modifier = Modifier.fillMaxWidth(), enabled = !loading)
                Button(enabled = !loading && reply.isNotBlank(), onClick = { if (token == null) onSignIn() else mutate { repository.reply(token, thread.id, reply); reply = "" } }) { Text("Kirim Balasan") }
            }
        } else {
            if (group == null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !joined, onClick = { joined = false }, label = { Text("Jelajahi") })
                        FilterChip(selected = joined, onClick = { if (token == null) onSignIn() else joined = true }, label = { Text("Grup Diikuti") })
                        TextButton(onClick = { if (token == null) onSignIn() else creating = true }) { Text("Buat Grup") }
                    }
                    Row { OutlinedTextField(search, { search = it.take(100) }, label = { Text("Cari grup") }, modifier = Modifier.weight(1f)); TextButton(onClick = { appliedSearch = search.trim() }) { Text("Cari") } }
                }
                items(groups, key = { "group-${it.id}" }) { entry -> Card(Modifier.fillMaxWidth().clickable { group = entry; retry++ }) { Row(Modifier.padding(12.dp)) {
                    entry.imageUrl?.let { AsyncImage(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                    Column(Modifier.padding(start = 8.dp)) { Text(entry.name, fontWeight = FontWeight.SemiBold); Text("${entry.memberCount} anggota · ${entry.membership}"); Text(entry.description) }
                } } }
                if (moreGroups) item { OutlinedButton(enabled = !loading, onClick = {
                    loading = true
                    scope.launch {
                        try { val result = repository.groups(token, groupPage + 1, appliedSearch, joined && token != null); groups = (groups + result.data).distinctBy { it.id }; groupPage = result.meta.page; moreGroups = groupPage < result.meta.totalPages }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "Grup belum dapat dimuat." }
                        finally { loading = false }
                    }
                }) { Text("Muat grup lainnya") } }
                item { Text("Thread Komunitas", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            } else {
                val selected = group!!
                item {
                    Text(selected.description); Text("${selected.memberCount} anggota · ${selected.membership}")
                    when (selected.membership) {
                        "member" -> OutlinedButton(enabled = !loading, onClick = { if (token != null) mutate { repository.leave(token, selected.id); group = null } }) { Text("Keluar Grup") }
                        "none" -> Button(enabled = !loading, onClick = { if (token == null) onSignIn() else mutate { repository.join(token, selected.id) } }) { Text(if (selected.access == "request") "Ajukan Bergabung" else "Gabung Grup") }
                        "pending" -> Text("Menunggu persetujuan.")
                    }
                    if (selected.access == "request" && selected.membership !in setOf("owner", "member")) Text("Diskusi hanya tersedia bagi anggota yang disetujui.")
                    if (selected.membership in setOf("owner", "member")) {
                        OutlinedTextField(content, { content = it.take(5000) }, label = { Text("Mulai thread") }, modifier = Modifier.fillMaxWidth(), enabled = !loading)
                        Button(enabled = !loading && content.isNotBlank(), onClick = { if (token != null) mutate { repository.createThread(token, selected.id, content); content = "" } }) { Text("Posting") }
                    }
                }
                if (requests.isNotEmpty()) {
                    item { Text("Permintaan Bergabung", fontWeight = FontWeight.Bold) }
                    items(requests, key = { "request-${it.user.id}" }) { request -> Row {
                        Text(request.user.name, modifier = Modifier.weight(1f))
                        TextButton(enabled = !loading, onClick = { if (token != null) mutate { repository.decide(token, selected.id, request.user.id, true) } }) { Text("Terima") }
                        TextButton(enabled = !loading, onClick = { if (token != null) mutate { repository.decide(token, selected.id, request.user.id, false) } }) { Text("Tolak") }
                    } }
                }
            }
            if (!loading && error == null && threads.isEmpty()) item { Text("Belum ada thread.") }
            items(threads, key = { "thread-${it.id}" }) { thread -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(thread.author.name, fontWeight = FontWeight.SemiBold); Text(thread.content); thread.item?.let { Text(it.title) }; Text("${thread.likes} suka · ${thread.replyCount} balasan")
                Row {
                    TextButton(enabled = !loading, onClick = { if (token == null) onSignIn() else mutate { repository.like(token, thread.id, !thread.liked) } }) { Text(if (thread.liked) "Batal Suka" else "Suka") }
                    TextButton(enabled = !loading, onClick = { mutate { detail = repository.thread(token, thread.id) } }) { Text("Lihat Balasan") }
                    if (session?.user?.id == thread.author.id) TextButton(enabled = !loading, onClick = { confirmDelete = thread.id }) { Text("Hapus") }
                }
            } } }
            if (more && error == null) item { OutlinedButton(enabled = !loading, onClick = {
                loading = true
                scope.launch {
                    try { val result = repository.threads(token, group?.id, page + 1); threads = (threads + result.data).distinctBy { it.id }; page = result.meta.page; more = page < result.meta.totalPages }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Thread belum dapat dimuat." }
                    finally { loading = false }
                }
            }) { Text("Muat thread lainnya") } }
        }
    }
}
