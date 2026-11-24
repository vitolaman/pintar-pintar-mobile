package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.learning.ClassWorkspaceRepository
import com.getmobilestack.kmp.pintar.learning.MeetingAttendance
import com.getmobilestack.kmp.pintar.learning.safeLearningUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MeetingAttendanceScreen(meetingId: String, token: String, repository: ClassWorkspaceRepository, onBack: () -> Unit) {
    var data by remember(meetingId, token) { mutableStateOf<MeetingAttendance?>(null) }
    var feedback by remember(meetingId, token) { mutableStateOf("") }
    var loading by remember(meetingId, token) { mutableStateOf(true) }
    var error by remember(meetingId, token) { mutableStateOf<String?>(null) }
    var retry by remember(meetingId, token) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(meetingId, token, retry) {
        loading = true; error = null
        try { data = repository.meeting(token, meetingId) }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Pertemuan belum dapat dimuat." }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹  Kembali") }
        Text(data?.meeting?.title ?: "Kehadiran", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(enabled = !loading, onClick = { retry++ }) { Text("Perbarui Status") } }
        data?.let { current ->
            Text(listOfNotNull(current.meeting.date, current.meeting.time).joinToString(" · "))
            current.meeting.content?.let { Text(it) }
            current.course?.let { Text("Kelas: ${it.title}") }
            (current.meeting.mentor?.name ?: current.mentorNames.joinToString(", ").takeIf(String::isNotBlank))?.let { Text("Mentor: $it") }
            safeLearningUrl(current.meeting.liveUrl)?.let { link -> OutlinedButton(onClick = { uriHandler.openUri(link) }) { Text("Gabung Pertemuan") } }
            Text("Status kehadiran: ${current.myAttendance?.status ?: "Belum tercatat"}")
            current.myAttendance?.checkInTime?.let { Text("Waktu: $it WIB") }
            current.myAttendance?.feedback?.let { Text("Feedback: $it") }
            if (!current.meeting.hasStarted) Text("Absensi dibuka setelah pertemuan dimulai.")
            if (current.myAttendance?.status != "hadir") {
                OutlinedTextField(feedback, { feedback = it.take(2000) }, label = { Text("Feedback sesi (opsional)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
                Button(enabled = !loading && error == null && current.meeting.hasStarted, onClick = {
                    loading = true; error = null
                    scope.launch {
                        try { data = repository.checkIn(token, meetingId, feedback); feedback = "" }
                        catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { error = e.message }
                        catch (_: Exception) { error = "Kehadiran belum dapat disimpan." }
                        finally { loading = false }
                    }
                }) { Text("Catat Kehadiran Saya") }
            }
            TextButton(enabled = !loading, onClick = { retry++ }) { Text("Perbarui Status") }
        }
    }
}
