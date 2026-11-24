package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.account.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val learnerPreferences = listOf("email_order_paid" to "Konfirmasi pembayaran", "email_learner_announcement" to "Pengumuman pembelajaran", "email_application_submitted" to "Konfirmasi lamaran mentor", "email_merchant_registered" to "Pendaftaran merchant", "email_pro_paid" to "Pendaftaran Pro")
private val mentorPreferences = listOf("email_interview_scheduled" to "Jadwal wawancara", "email_application_result" to "Hasil lamaran", "email_mentor_announcement" to "Pengumuman merchant")
private val merchantPreferences = listOf("email_new_sale" to "Transaksi baru", "email_new_applicant" to "Lamaran baru", "email_merchant_announcement" to "Pengumuman mentor", "email_cashflow_success" to "Pembaruan saldo")

@Composable
fun AccountSettingsScreen(session: AccountSession, profile: AccountProfile, repository: AccountSettingsRepository, onSession: (AccountSession?) -> Unit, onBack: () -> Unit) {
    var values by remember(session.user.id) { mutableStateOf<Map<String, Boolean>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var deleteConfirmation by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var pendingSession by remember(session.user.id) { mutableStateOf<AccountSession?>(null) }
    var deleted by remember { mutableStateOf(false) }
    var tokenInvalidated by remember(session.token) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.token, retry) {
        loading = true
        try { values = repository.notificationPreferences(session.token) }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) { error = e.message }
        catch (_: Exception) { error = "Preferensi belum dapat dimuat." }
        finally { loading = false }
    }
    confirmAction?.let { action -> AlertDialog(
        onDismissRequest = { if (!saving) confirmAction = null },
        title = { Text(if (action == "delete") "Hapus akun?" else "Akhiri sesi lain?") },
        text = { Text(if (action == "delete") "Akun dan merchant terkait akan dinonaktifkan. Tindakan ini tidak dapat dibatalkan." else "Semua sesi di perangkat lain akan diakhiri.") },
        dismissButton = { TextButton(enabled = !saving, onClick = { confirmAction = null }) { Text("Batal") } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            saving = true; error = null; message = null
            scope.launch {
                try {
                    if (action == "delete") {
                        if (!deleted) { repository.deleteAccount(session.token); deleted = true }
                        onSession(null)
                    } else {
                        val token = repository.endOtherSessions(session.token)
                        tokenInvalidated = true
                        val replacement = session.copy(token = token)
                        pendingSession = replacement
                        onSession(replacement); pendingSession = null; tokenInvalidated = false; message = "Sesi perangkat lain diakhiri."
                    }
                    confirmAction = null
                } catch (e: CancellationException) { throw e }
                catch (_: ReplacementSessionException) { tokenInvalidated = true; error = "Sesi pengganti tidak tersedia."; confirmAction = null }
                catch (e: AccountApiException) { error = e.message; confirmAction = null }
                catch (_: Exception) { error = if (deleted) "Akun telah dihapus, tetapi sesi lokal belum dapat dihapus. Coba lagi." else "Pengaturan belum dapat disimpan. Jika sesi berakhir, masuk kembali."; confirmAction = null }
                finally { saving = false }
            }
        }) { Text(if (action == "delete") "Hapus" else "Akhiri") } },
    ) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !saving && pendingSession == null && !deleted && !tokenInvalidated, onClick = onBack) { Text("‹  Kembali") }
        ScreenHeading("Pengaturan Akun", "Kelola notifikasi dan keamanan akun Anda.")
        error?.let { StatusMessage(it, isError = true) }
        message?.let { StatusMessage(it) }
        pendingSession?.let { pending ->
            Text("Token sesi baru belum tersimpan. Simpan sebelum melanjutkan.", color = MaterialTheme.colorScheme.error)
            Button(enabled = !saving, onClick = {
                try { onSession(pending); pendingSession = null; tokenInvalidated = false; currentPassword = ""; newPassword = ""; confirmation = ""; error = null; message = "Sesi tersimpan dengan aman." }
                catch (_: Exception) { error = "Sesi belum dapat disimpan dengan aman." }
            }) { Text("Coba Simpan Sesi") }
        }
        if (tokenInvalidated && pendingSession == null) {
            Text("Operasi berhasil tetapi sesi pengganti tidak tersedia. Hapus sesi lokal lalu masuk kembali.", color = MaterialTheme.colorScheme.error)
            Button(onClick = { try { onSession(null) } catch (_: Exception) { error = "Sesi lokal belum dapat dihapus." } }) { Text("Masuk Kembali") }
        }
        if (pendingSession == null && !deleted && !tokenInvalidated) {
        SectionHeading("Notifikasi Email")
        if (loading) LoadingState("Memuat preferensi...")
        if (!loading && values == null) TextButton(onClick = { retry++ }) { Text("Coba Lagi") }
        values?.let { current ->
            val visible = learnerPreferences + (if (profile.isMentor) mentorPreferences else emptyList()) + (if (profile.isMerchant) merchantPreferences else emptyList())
            visible.forEach { (key, label) -> AccessibleSwitchRow(label, checked = current[key] ?: false, enabled = !saving, onCheckedChange = { values = current + (key to it) }) }
            Button(enabled = !saving, onClick = {
                saving = true; error = null; message = null
                scope.launch {
                    try { repository.updateNotificationPreferences(session.token, current); values = repository.notificationPreferences(session.token); message = "Preferensi disimpan." }
                    catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Preferensi belum dapat disimpan." }
                    finally { saving = false }
                }
            }) { Text("Simpan Notifikasi") }
        }
        HorizontalDivider()
        SectionHeading("Keamanan")
        OutlinedTextField(currentPassword, { currentPassword = it.take(128) }, label = { Text("Kata sandi saat ini") }, visualTransformation = PasswordVisualTransformation(), enabled = !saving, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(newPassword, { newPassword = it.take(128) }, label = { Text("Kata sandi baru") }, visualTransformation = PasswordVisualTransformation(), enabled = !saving, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirmation, { confirmation = it.take(128) }, label = { Text("Konfirmasi kata sandi") }, visualTransformation = PasswordVisualTransformation(), enabled = !saving, modifier = Modifier.fillMaxWidth())
        Button(enabled = !saving, onClick = {
            if (currentPassword.isBlank() || newPassword.length !in 8..128 || newPassword.none(Char::isDigit) || newPassword != confirmation) error = "Periksa kata sandi saat ini dan konfirmasi. Kata sandi baru harus 8–128 karakter dan mengandung angka."
            else {
                saving = true; error = null; message = null
                scope.launch {
                    try {
                        val token = repository.changePassword(session.token, currentPassword, newPassword)
                        tokenInvalidated = true
                        val replacement = session.copy(token = token)
                        pendingSession = replacement
                        onSession(replacement); pendingSession = null; tokenInvalidated = false; currentPassword = ""; newPassword = ""; confirmation = ""; message = "Kata sandi diperbarui."
                    } catch (e: CancellationException) { throw e }
                    catch (_: ReplacementSessionException) { tokenInvalidated = true; error = "Sesi pengganti tidak tersedia." }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Kata sandi atau sesi belum dapat disimpan. Jika sesi berakhir, masuk kembali." }
                    finally { saving = false }
                }
            }
        }) { Text("Ubah Kata Sandi") }
        OutlinedButton(enabled = !saving, onClick = { confirmAction = "sessions" }) { Text("Akhiri Sesi Perangkat Lain") }
        }
        HorizontalDivider()
        if (pendingSession == null && !tokenInvalidated) {
        SectionHeading("Hapus Akun")
        Text("Penghapusan menonaktifkan akun dan merchant Anda. Selesaikan urusan pesanan dan saldo terlebih dahulu.")
        OutlinedTextField(deleteConfirmation, { deleteConfirmation = it }, enabled = !saving && !deleted, label = { Text("Ketik HAPUS AKUN") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !saving && (deleted || deleteConfirmation == "HAPUS AKUN"), onClick = { confirmAction = "delete" }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (deleted) "Hapus Sesi Lokal" else "Hapus Akun Saya") }
        }
    }
}
