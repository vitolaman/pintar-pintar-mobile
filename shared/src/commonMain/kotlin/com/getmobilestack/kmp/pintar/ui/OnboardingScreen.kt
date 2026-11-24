package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.account.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val roles = listOf("mahasiswa" to "Mahasiswa", "content_creator" to "Content Creator", "professional" to "Professional", "ibu_rumah_tangga" to "Ibu Rumah Tangga", "brand_bisnis" to "Brand & Bisnis", "custom" to "Lainnya")
private val suggestions = listOf("Fotografi", "Developer", "Desain Grafis", "Arsitektur", "Videografi", "Desain UI/UX", "Penulisan & Konten", "Pemasaran Digital", "Bisnis & Kewirausahaan", "Analisis Data", "Teknik & Rekayasa", "Manajemen Proyek")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(current: Onboarding, token: String, repository: AccountRepository, onSaved: () -> Unit, onUnauthorized: () -> Unit, onBack: () -> Unit) {
    var role by remember(token) { mutableStateOf(current.role) }
    var customRole by remember(token) { mutableStateOf(current.customRole.orEmpty()) }
    var skills by remember(token) { mutableStateOf(current.skills) }
    var newSkill by remember(token) { mutableStateOf("") }
    var step by remember(token) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = { if (step == 1) { step = 0; error = null } else onBack() }, enabled = !loading) { Text("‹  Kembali") }
        ScreenHeading(if (step == 0) "Tentang Anda" else "Keahlian Anda", if (step == 0) "Langkah 1 dari 2 · Pilih peran yang paling sesuai dengan Anda." else "Langkah 2 dari 2 · Pilih atau tambahkan keahlian Anda (opsional).")
        if (step == 0) {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                roles.forEach { (value, label) -> AccessibleRadioRow(label, selected = role == value, enabled = !loading, onClick = { role = value }) }
            }
            if (role == "custom") OutlinedTextField(customRole, { customRole = it.take(60) }, label = { Text("Peran Anda") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { skill ->
                    val selected = skills.any { it.equals(skill, ignoreCase = true) }
                    FilterChip(selected = selected, onClick = {
                        if (selected) skills = skills.filterNot { it.equals(skill, ignoreCase = true) }
                        else if (skills.size < 20) skills = skills + skill else error = "Maksimal 20 keahlian."
                    }, enabled = !loading, label = { Text(skill) })
                }
            }
            skills.filterNot { s -> suggestions.any { it.equals(s, true) } }.forEach { skill ->
                AssistChip(onClick = { skills = skills - skill }, enabled = !loading, modifier = Modifier.semantics { contentDescription = "Hapus keahlian $skill" }, label = { Text("$skill ×") })
            }
            OutlinedTextField(newSkill, { newSkill = it.take(50) }, label = { Text("Tambah keahlian") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !loading && newSkill.isNotBlank(), onClick = {
                val value = newSkill.trim()
                if (skills.none { it.equals(value, true) }) {
                    if (skills.size < 20) skills = skills + value else error = "Maksimal 20 keahlian."
                }
                newSkill = ""
            }) { Text("Tambah") }
        }
        error?.let { StatusMessage(it, isError = true) }
        Button(enabled = !loading, modifier = Modifier.fillMaxWidth(), onClick = {
            val payload = try { onboardingPayload(role, customRole, skills) } catch (e: IllegalArgumentException) { error = e.message; null }
            if (payload != null) {
                error = null
                if (step == 0) step = 1 else {
                    loading = true
                    scope.launch {
                        try { repository.updateOnboarding(token, payload); onSaved() }
                        catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) {
                            if (e.statusCode == 401) try { onUnauthorized() } catch (_: Exception) { error = "Sesi tidak dapat dihapus. Silakan coba lagi." }
                            else error = e.message
                        }
                        catch (_: Exception) { error = "Profil belum dapat disimpan." }
                        finally { loading = false }
                    }
                }
            }
        }) { Text(if (loading) "Menyimpan..." else if (step == 0) "Lanjut" else "Simpan") }
    }
}
