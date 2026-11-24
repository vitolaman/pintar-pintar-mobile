package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.help.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun HelpScreen(repository: HelpRepository, token: String?, onSignIn: () -> Unit, onBack: () -> Unit) {
    var categories by remember { mutableStateOf<List<FaqCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var type by remember { mutableStateOf("saran") }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, retry) {
        loading = true; error = null
        try { categories = repository.faqs() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Pertanyaan umum belum dapat dimuat." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali") }; Text("Bantuan", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        if (loading) item { CircularProgressIndicator() }
        error?.let { text -> item { Text(text); TextButton(onClick = { retry++ }) { Text("Coba Lagi") } } }
        categories.forEach { category -> item(key = category.id) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(category.name, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                category.faqs.forEach { faq -> Text(faq.question, fontWeight = FontWeight.SemiBold); Text(faq.answer) }
            }
        } }
        item {
            Text("Kirim Masukan", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("kritik", "saran", "pujian").forEach { value -> FilterChip(selected = type == value, onClick = { type = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) }) }
            }
            OutlinedTextField(message, { message = it.take(2000) }, label = { Text("Pesan") }, enabled = !sending, modifier = Modifier.fillMaxWidth())
            result?.let { Text(it) }
            Button(enabled = !sending && message.isNotBlank(), onClick = {
                if (token == null) onSignIn() else {
                    sending = true; result = null
                    scope.launch {
                        try { repository.sendFeedback(token, type, message); message = ""; result = "Masukan berhasil dikirim." }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { result = "Masukan belum dapat dikirim." }
                        finally { sending = false }
                    }
                }
            }) { Text(if (sending) "Mengirim..." else "Kirim") }
        }
    }
}
