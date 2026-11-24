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
import com.getmobilestack.kmp.pintar.learning.ClassWorkspaceRepository
import com.getmobilestack.kmp.pintar.learning.Grades
import kotlinx.coroutines.CancellationException

@Composable
fun GradesScreen(classId: String, token: String, repository: ClassWorkspaceRepository, onBack: () -> Unit) {
    var data by remember(classId, token) { mutableStateOf<Grades?>(null) }
    var loading by remember(classId, token) { mutableStateOf(true) }
    var error by remember(classId, token) { mutableStateOf<String?>(null) }
    var retry by remember(classId, token) { mutableIntStateOf(0) }
    LaunchedEffect(classId, token, retry) {
        loading = true; error = null
        try { data = repository.grades(token, classId) }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Nilai belum dapat dimuat." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali") }; Text("Nilai dan Partisipasi", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        if (loading) item { CircularProgressIndicator() }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        data?.let { grades ->
            item { Text("Rata-rata: ${grades.averageScore?.toString() ?: "—"}"); Text("Partisipasi: ${grades.participation?.let { "$it%" } ?: "—"}") }
            if (grades.assignments.isEmpty()) item { Text("Belum ada nilai tugas.") }
            items(grades.assignments) { grade ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(grade.title, fontWeight = FontWeight.SemiBold)
                    Text("Status: ${grade.status}")
                    Text("Nilai: ${grade.score?.toString() ?: "Menunggu penilaian"}")
                    grade.feedback?.let { Text("Umpan balik: $it") }
                } }
            }
        }
    }
}
