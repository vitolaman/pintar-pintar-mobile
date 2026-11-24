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
import com.getmobilestack.kmp.pintar.jobs.*
import com.getmobilestack.kmp.pintar.learning.safeLearningUrl
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MentorRegistrationScreen(token: String, repository: DashboardRepository, uploads: JobApplicationRepository, onRegistered: () -> Unit, onBack: () -> Unit) {
    var phone by remember { mutableStateOf("") }; var headline by remember { mutableStateOf("") }; var bio by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("") }; var experience by remember { mutableStateOf("") }; var education by remember { mutableStateOf("") }; var portfolio by remember { mutableStateOf("") }; var linkedin by remember { mutableStateOf("") }
    var cv by remember { mutableStateOf<PlatformFile?>(null) }; var certificate by remember { mutableStateOf<PlatformFile?>(null) }
    var cvAsset by remember { mutableStateOf<String?>(null) }; var certificateAsset by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cvPicker = rememberFilePickerLauncher(type = PickerType.File(extensions = listOf("pdf", "doc", "docx"))) { file ->
        if (file != null) try { val issue = cvFileError(file.name, file.getSize() ?: 1L); if (issue == null) { cv = file; cvAsset = null }; error = issue } catch (_: Exception) { error = "CV belum dapat dibaca." }
    }
    val certificatePicker = rememberFilePickerLauncher(type = PickerType.File(extensions = listOf("pdf", "png", "jpg", "jpeg"))) { file ->
        if (file != null) try { val issue = mentorCertificateFileError(file.name, file.getSize() ?: 1L); if (issue == null) { certificate = file; certificateAsset = null }; error = issue } catch (_: Exception) { error = "Sertifikat belum dapat dibaca." }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !loading, onClick = { if (success) onRegistered() else onBack() }) { Text("‹  Kembali") }
        Text("Daftar Mentor", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (success) { Text("Pendaftaran mentor berhasil disimpan."); Button(onClick = onRegistered) { Text("Perbarui Profil") } }
        else {
            OutlinedTextField(phone, { phone = it.take(32) }, label = { Text("WhatsApp") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(headline, { headline = it.take(160) }, label = { Text("Headline") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bio, { bio = it.take(2000) }, label = { Text("Bio") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(skills, { skills = it.take(200) }, label = { Text("Keahlian, pisahkan dengan koma") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(experience, { experience = it.filter(Char::isDigit).take(2) }, label = { Text("Pengalaman (tahun)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(education, { education = it.take(255) }, label = { Text("Pendidikan") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(portfolio, { portfolio = it.take(2048) }, label = { Text("URL Portofolio (opsional)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(linkedin, { linkedin = it.take(2048) }, label = { Text("URL LinkedIn") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = !loading, onClick = { cvPicker.launch() }) { Text("Pilih CV · maks. 10 MB") }; cv?.let { Text(it.name) }
            OutlinedButton(enabled = !loading, onClick = { certificatePicker.launch() }) { Text("Pilih Sertifikat Keahlian · maks. 10 MB") }; certificate?.let { Text(it.name) }
            Button(enabled = !loading, onClick = {
                val entries = skills.split(',').map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }
                val years = experience.toIntOrNull(); val cvFile = cv; val certificateFile = certificate
                if (phone.isBlank() || entries.isEmpty() || entries.size > 20 || entries.any { it.length > 60 } || entries.joinToString(", ").length > 160 || years == null || years !in 0..80 || education.isBlank() || safeLearningUrl(linkedin.trim()) == null || (portfolio.isNotBlank() && safeLearningUrl(portfolio.trim()) == null) || cvFile == null || certificateFile == null) error = "Periksa data profil, URL, CV dan sertifikat."
                else {
                    loading = true; error = null
                    scope.launch {
                        try {
                            val cvId = cvAsset ?: run { val size = cvFile.getSize(); if (size != null) require(cvFileError(cvFile.name, size) == null); val bytes = cvFile.readBytes(); require(cvFileError(cvFile.name, bytes.size.toLong()) == null); uploads.uploadCv(token, cvFile.name, bytes).also { cvAsset = it } }
                            val certificateId = certificateAsset ?: run { val size = certificateFile.getSize(); if (size != null) require(mentorCertificateFileError(certificateFile.name, size) == null); val bytes = certificateFile.readBytes(); require(mentorCertificateFileError(certificateFile.name, bytes.size.toLong()) == null); uploads.uploadMentorCertificate(token, certificateFile.name, bytes).also { certificateAsset = it } }
                            repository.registerMentor(token, MentorRegistration(phone.trim(), headline.trim().ifBlank { null }, bio.trim().ifBlank { null }, entries.joinToString(", "), years, education.trim(), portfolio.trim().ifBlank { null }, linkedin.trim(), cvId, certificateId)); success = true
                        } catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { error = e.message }
                        catch (_: Exception) { error = "Pendaftaran belum dapat disimpan." }
                        finally { loading = false }
                    }
                }
            }) { Text(if (loading) "Mendaftar..." else "Daftar Mentor") }
        }
    }
}
