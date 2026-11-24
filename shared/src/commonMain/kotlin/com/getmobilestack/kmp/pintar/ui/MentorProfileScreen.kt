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
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.dashboard.*
import com.getmobilestack.kmp.pintar.learning.safeLearningUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MentorProfileScreen(token: String, repository: DashboardRepository, onBack: () -> Unit) {
    var profile by remember(token) { mutableStateOf<MentorProfile?>(null) }
    var phone by remember { mutableStateOf("") }; var headline by remember { mutableStateOf("") }; var bio by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }; var experience by remember { mutableStateOf("") }; var education by remember { mutableStateOf("") }; var portfolio by remember { mutableStateOf("") }; var linkedin by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var message by remember { mutableStateOf<String?>(null) }; var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token, retry) {
        loading = true; error = null
        try {
            val value = repository.mentorProfile(token); profile = value; phone = value.phone; headline = value.headline.orEmpty(); bio = value.bio.orEmpty(); skills = value.skills.joinToString(", "); experience = value.experienceYears.toString(); education = value.education; portfolio = value.portfolioUrl.orEmpty(); linkedin = value.linkedinUrl
        } catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Profil mentor belum dapat dimuat." }
        finally { loading = false }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !loading, onClick = onBack) { Text("‹  Kembali") }
        Text("Pengaturan Mentor", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); if (profile == null) TextButton(onClick = { retry++ }) { Text("Coba Lagi") } }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (profile != null) {
            OutlinedTextField(phone, { phone = it.take(32) }, label = { Text("WhatsApp") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(headline, { headline = it.take(160) }, label = { Text("Headline") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(2000) }, label = { Text("Bio") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(skills, { skills = it.take(200) }, label = { Text("Keahlian, pisahkan dengan koma") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(experience, { experience = it.filter(Char::isDigit).take(2) }, label = { Text("Pengalaman (tahun)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(education, { education = it.take(255) }, label = { Text("Pendidikan") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(portfolio, { portfolio = it.take(2048) }, label = { Text("URL Portofolio (opsional)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(linkedin, { linkedin = it.take(2048) }, label = { Text("URL LinkedIn") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            Button(enabled = !loading, onClick = {
                val entries = skills.split(',').map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }
                val years = experience.toIntOrNull()
                if (phone.isBlank() || entries.isEmpty() || entries.size > 20 || entries.any { it.length > 60 } || entries.joinToString(", ").length > 160 || years == null || years !in 0..80 || education.isBlank() || safeLearningUrl(linkedin.trim()) == null || (portfolio.isNotBlank() && safeLearningUrl(portfolio.trim()) == null)) error = "Periksa WhatsApp, keahlian, pengalaman, pendidikan, dan URL."
                else {
                    loading = true; error = null; message = null
                    scope.launch {
                        try { profile = repository.updateMentorProfile(token, MentorProfileUpdate(phone.trim(), headline.trim().ifBlank { null }, bio.trim().ifBlank { null }, entries, years, education.trim(), portfolio.trim().ifBlank { null }, linkedin.trim())); message = "Profil mentor disimpan." }
                        catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { error = e.message }
                        catch (_: Exception) { error = "Profil mentor belum dapat disimpan." }
                        finally { loading = false }
                    }
                }
            }) { Text("Simpan Profil") }
        }
    }
}
