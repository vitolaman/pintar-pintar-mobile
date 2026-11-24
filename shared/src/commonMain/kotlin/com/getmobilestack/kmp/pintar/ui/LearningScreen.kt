package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.AsyncImage
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.account.LearningItem
import com.getmobilestack.kmp.pintar.learning.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LearningScreen(item: LearningItem, token: String, repository: LearningRepository, assignmentsRepository: AssignmentRepository, workspaceRepository: ClassWorkspaceRepository, onBack: () -> Unit) {
    var course by remember(item) { mutableStateOf<LearningClass?>(null) }
    var product by remember(item) { mutableStateOf<OwnedProduct?>(null) }
    var error by remember(item) { mutableStateOf<String?>(null) }
    var loading by remember(item) { mutableStateOf(true) }
    var attempt by remember(item) { mutableIntStateOf(0) }
    var showAssignments by remember(item) { mutableStateOf(false) }
    var showDiscussions by remember(item) { mutableStateOf(false) }
    var showGrades by remember(item) { mutableStateOf(false) }
    var attendanceMeeting by remember(item) { mutableStateOf<String?>(null) }
    var selectedVideoId by remember(item, token) { mutableStateOf<String?>(null) }
    var savingVideos by remember(item) { mutableStateOf<Set<String>>(emptySet()) }
    var certificateLoading by remember(item) { mutableStateOf(false) }
    var certificateMessage by remember(item) { mutableStateOf<String?>(null) }
    var refreshingMedia by remember(item) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()

    fun revealVideo(videoId: String, refreshed: LearningClass) {
        selectedVideoId = videoId
        val chapterIndex = refreshed.chapters.indexOfFirst { chapter -> chapter.videos.any { it.id == videoId } }
        if (chapterIndex >= 0) scope.launch {
            val headerCount = 2 + (if (loading) 1 else 0) + (if (error != null) 1 else 0)
            listState.animateScrollToItem(headerCount + chapterIndex)
        }
    }

    fun completeVideo(video: LearningVideo) {
        if (video.completed || video.id in savingVideos) return
        savingVideos = savingVideos + video.id; error = null
        scope.launch {
            try {
                repository.completeVideo(token, video.id)
                course = course?.let { current -> current.copy(chapters = current.chapters.map { chapter -> chapter.copy(videos = chapter.videos.map { if (it.id == video.id) it.copy(completed = true) else it }) }) }
                try {
                    val fresh = repository.getClass(token, item.itemId)
                    val completedIds = course?.chapters.orEmpty().flatMap { it.videos }.filter { it.completed }.map { it.id }.toSet() + video.id
                    course = fresh.copy(chapters = fresh.chapters.map { chapter -> chapter.copy(videos = chapter.videos.map { currentVideo ->
                        if (currentVideo.id in completedIds) currentVideo.copy(completed = true) else currentVideo
                    }) })
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "Video selesai, tetapi progres belum dapat diperbarui. Coba lagi." }
            } catch (e: CancellationException) { throw e }
            catch (e: AccountApiException) { error = e.message }
            catch (_: Exception) { error = "Progres belum dapat disimpan." }
            finally { savingVideos = savingVideos - video.id }
        }
    }

    fun openVideo(video: LearningVideo) {
        if (selectedVideoId == video.id) { selectedVideoId = null; return }
        if (video.playback() !is VideoPlayback.File) { course?.let { revealVideo(video.id, it) }; return }
        refreshingMedia = true; error = null
        scope.launch {
            try {
            val refreshed = repository.getClass(token, item.itemId)
            course = refreshed
            if (refreshed.chapters.any { chapter -> chapter.videos.any { it.id == video.id && it.playback() != null } }) { error = null; revealVideo(video.id, refreshed) }
                else error = "Video belum tersedia."
            } catch (e: CancellationException) { throw e }
            catch (e: AccountApiException) { error = e.message }
            catch (_: Exception) { error = "Video belum dapat dimuat." }
            finally { refreshingMedia = false }
        }
    }

    fun openResource(chapterId: String, file: LearningResource) {
        if (file.downloadUrl == null) {
            safeLearningUrl(file.url)?.let { uriHandler.openUri(it) }
            return
        }
        refreshingMedia = true; error = null
        scope.launch {
            try {
                val fresh = repository.getClass(token, item.itemId)
                course = fresh
                val updated = fresh.chapters.firstOrNull { it.id == chapterId }?.files?.firstOrNull { it.id == file.id }
                val link = safeLearningUrl(updated?.downloadUrl ?: updated?.url)
                if (link != null) uriHandler.openUri(link) else error = "Materi belum tersedia."
            } catch (e: CancellationException) { throw e }
            catch (e: AccountApiException) { error = e.message }
            catch (_: Exception) { error = "Materi belum dapat dimuat." }
            finally { refreshingMedia = false }
        }
    }

    LaunchedEffect(item, token, attempt) {
        loading = true; error = null
        try {
            if (item.itemType == "digital") product = repository.getProduct(token, item.itemId)
            else course = repository.getClass(token, item.itemId)
        } catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Pembelajaran belum dapat dimuat." }
        finally { loading = false }
    }
    if (showAssignments) {
        AssignmentsScreen(item.itemId, token, assignmentsRepository, onBack = { showAssignments = false; attempt++ })
        return
    }
    if (showDiscussions) { DiscussionsScreen(item.itemId, token, workspaceRepository, onBack = { showDiscussions = false }); return }
    if (showGrades) { GradesScreen(item.itemId, token, workspaceRepository, onBack = { showGrades = false }); return }
    attendanceMeeting?.let { meetingId ->
        MeetingAttendanceScreen(meetingId, token, workspaceRepository, onBack = { attendanceMeeting = null; attempt++ })
        return
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali ke profil") }; ScreenHeading(item.title) }
        if (loading) item { LoadingState("Memuat pembelajaran...") }
        error?.let { message -> item { StatusMessage(message, isError = true); TextButton(onClick = { attempt++ }) { Text("Coba Lagi") } } }
        course?.let { current ->
            item {
                Text("Progres: ${current.progress.coerceIn(0, 100)}%", fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { current.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
                current.nextVideo?.let { next ->
                    val video = current.chapters.flatMap { it.videos }.firstOrNull { it.id == next.id }
                    video?.let { nextVideo -> Button(enabled = !refreshingMedia, onClick = { if (selectedVideoId != nextVideo.id) openVideo(nextVideo) }) { Text("Lanjutkan: ${next.title}") } }
                }
                OutlinedButton(onClick = { showAssignments = true }) { Text("Tugas dan Quiz") }
                OutlinedButton(onClick = { showDiscussions = true }) { Text("Diskusi Kelas") }
                OutlinedButton(onClick = { showGrades = true }) { Text("Nilai dan Partisipasi") }
                current.instructions?.takeIf { it.isNotBlank() }?.let { Text(it) }
                current.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                if (current.mentors.isNotEmpty()) Text("Mentor: ${current.mentors.joinToString { it.name }}")
            }
            current.chapters.forEach { chapter ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading(chapter.title)
                        chapter.description?.takeIf(String::isNotBlank)?.let { Text(it) }
                        chapter.videos.forEach { video ->
                            Text("${if (video.completed) "✓ " else ""}${video.title}")
                            video.duration?.let { Text("Durasi: $it") }
                            video.description?.takeIf(String::isNotBlank)?.let { Text(it) }
                            val playback = video.playback()
                            if (playback != null) OutlinedButton(modifier = Modifier.semantics { contentDescription = "${if (selectedVideoId == video.id) "Tutup" else "Tonton"} video ${video.title}" }, enabled = !refreshingMedia, onClick = { openVideo(video) }) { Text(if (selectedVideoId == video.id) "Tutup Video" else if (refreshingMedia) "Memuat..." else "Tonton Video") }
                            if (selectedVideoId == video.id) {
                                when (playback) {
                                    is VideoPlayback.File, is VideoPlayback.YouTube -> LearningVideoPlayer(playback, Modifier.fillMaxWidth().aspectRatio(16f / 9f), onEnded = { completeVideo(video) })
                                    is VideoPlayback.External -> TextButton(onClick = { uriHandler.openUri(playback.url) }) { Text("Buka video pembelajaran") }
                                    null -> Text("Video belum tersedia.")
                                }
                                if (!video.completed) TextButton(modifier = Modifier.semantics { contentDescription = "Tandai ${video.title} selesai" }, enabled = video.id !in savingVideos, onClick = { completeVideo(video) }) { Text(if (video.id in savingVideos) "Menyimpan..." else "Tandai video selesai") }
                                else Text("Video selesai")
                            }
                        }
                        chapter.files.forEach { file ->
                            Text(file.name)
                            file.description?.takeIf(String::isNotBlank)?.let { Text(it) }
                            if (safeLearningUrl(file.downloadUrl ?: file.url) != null) TextButton(modifier = Modifier.semantics { contentDescription = "Buka materi ${file.name}" }, enabled = !refreshingMedia, onClick = { openResource(chapter.id, file) }) { Text("Buka Materi") }
                        }
                    }
                }
            }
            if (current.meetings.isNotEmpty()) item {
                SectionHeading("Jadwal")
                current.meetings.forEach { meeting ->
                    Text(listOfNotNull(meeting.title, meeting.date, meeting.time).joinToString(" · "))
                    meeting.content?.takeIf(String::isNotBlank)?.let { Text(it) }
                    meeting.status?.let { Text("Status: $it") }
                    meeting.durationMinutes?.let { Text("Durasi: $it menit") }
                    meeting.attendanceStatus?.let { Text("Kehadiran: $it") }
                    safeLearningUrl(meeting.liveUrl)?.let { link -> TextButton(onClick = { uriHandler.openUri(link) }) { Text("Buka Pertemuan") } }
                    TextButton(modifier = Modifier.semantics { contentDescription = "Kehadiran ${meeting.title}" }, onClick = { attendanceMeeting = meeting.id }) { Text("Kehadiran") }
                }
            }
            item {
                SectionHeading("Sertifikat")
                certificateMessage?.let { StatusMessage(it, isError = true) }
                val certificate = current.certificate
                if (certificate == null) Text("Sertifikat belum tersedia.") else {
                    Text("Status: ${certificate.status} · Progres: ${certificate.progress}%")
                    certificate.certificateNumber?.let { Text("Nomor: $it") }
                    certificate.attendancePercent?.let { Text("Kehadiran: $it%") }
                    certificate.averageScore?.let { Text("Rata-rata: $it") }
                    certificate.requirements?.let { Text("Syarat: kehadiran ${it.minAttendancePercent}% · nilai ${it.minScore}") }
                }
                TextButton(enabled = !certificateLoading, onClick = {
                    certificateLoading = true; certificateMessage = null
                    scope.launch {
                        try {
                            val refreshed = repository.getClass(token, item.itemId)
                            course = refreshed
                            val latest = refreshed.certificate
                            val link = certificateDownloadUrl(latest)
                            if (link != null) uriHandler.openUri(link) else certificateMessage = when (latest?.status) {
                                "ineligible" -> "Syarat sertifikat belum terpenuhi."
                                "pending" -> "Sertifikat masih menunggu penerbitan."
                                "issued" -> "File sertifikat belum tersedia."
                                else -> "Sertifikat belum tersedia."
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { certificateMessage = e.message }
                        catch (_: Exception) { certificateMessage = "Sertifikat belum dapat diperiksa." }
                        finally { certificateLoading = false }
                    }
                }) { Text(if (certificateLoading) "Memeriksa..." else "Periksa dan Unduh Sertifikat") }
            }
            if (current.faqs.isNotEmpty()) item {
                Text("Pertanyaan Umum", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                current.faqs.forEach { faq -> Text(faq.question, fontWeight = FontWeight.SemiBold); Text(faq.answer) }
            }
        }
        product?.let { current -> item {
            current.coverUrl?.let { AsyncImage(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp)) }
            current.description?.let { Text(it) }
            current.instructions?.let { Text(it) }
            current.file?.let { file ->
                Text(file.name, fontWeight = FontWeight.SemiBold)
                file.format?.let { Text("Format: $it") }
                safeLearningUrl(file.downloadUrl)?.let { link -> Button(onClick = { uriHandler.openUri(link) }) { Text("Unduh Produk") } }
            }
        } }
    }
}
