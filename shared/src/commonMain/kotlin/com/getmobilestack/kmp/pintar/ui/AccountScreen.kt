package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import com.getmobilestack.kmp.pintar.account.*
import com.getmobilestack.kmp.pintar.learning.LearningRepository
import com.getmobilestack.kmp.pintar.learning.AssignmentRepository
import com.getmobilestack.kmp.pintar.learning.ClassWorkspaceRepository
import com.getmobilestack.kmp.pintar.dashboard.DashboardRepository
import com.getmobilestack.kmp.pintar.dashboard.MerchantManagementRepository
import com.getmobilestack.kmp.pintar.jobs.JobApplicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP, RESET }

@Composable
fun AccountScreen(
    session: AccountSession?,
    repository: AccountRepository,
    learningRepository: LearningRepository,
    assignmentsRepository: AssignmentRepository,
    workspaceRepository: ClassWorkspaceRepository,
    dashboardRepository: DashboardRepository,
    avatarUploader: AvatarUploader,
    settingsRepository: AccountSettingsRepository,
    merchantManagement: MerchantManagementRepository,
    documentUploads: JobApplicationRepository,
    onSession: (AccountSession?) -> Unit,
    onBack: () -> Unit,
) {
    if (session == null) AuthForm(repository, onSession, onBack)
    else ProfileScreen(session, repository, learningRepository, assignmentsRepository, workspaceRepository, dashboardRepository, avatarUploader, settingsRepository, merchantManagement, documentUploads, onSession, onBack)
}

@Composable
private fun AuthForm(repository: AccountRepository, onSession: (AccountSession?) -> Unit, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun switchMode(next: AuthMode) {
        mode = next; password = ""; confirm = ""; passwordVisible = false; confirmVisible = false; error = null; message = null
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, enabled = !loading) { Text("‹  Kembali ke katalog") }
        ScreenHeading(when (mode) { AuthMode.SIGN_IN -> "Masuk"; AuthMode.SIGN_UP -> "Buat Akun"; AuthMode.RESET -> "Lupa Kata Sandi" }, when (mode) {
            AuthMode.SIGN_IN -> "Masukkan email dan kata sandi untuk mengakses akun Anda."
            AuthMode.SIGN_UP -> "Daftar untuk mulai belajar di Pintar Pintar."
            AuthMode.RESET -> "Kami akan mengirimkan tautan reset jika akun Anda terdaftar."
        })
        error?.let { StatusMessage(it, isError = true) }
        message?.let { StatusMessage(it) }
        if (mode == AuthMode.SIGN_UP) OutlinedTextField(name, { name = it.take(120) }, label = { Text("Nama Lengkap") }, singleLine = true, enabled = !loading, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it.take(255) }, label = { Text("Email") }, singleLine = true, enabled = !loading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        if (mode != AuthMode.RESET) OutlinedTextField(password, { password = it }, label = { Text("Kata Sandi") }, singleLine = true, enabled = !loading, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.semantics { contentDescription = if (passwordVisible) "Sembunyikan kata sandi" else "Tampilkan kata sandi" }) { Text(if (passwordVisible) "Sembunyi" else "Lihat") } }, modifier = Modifier.fillMaxWidth())
        if (mode == AuthMode.SIGN_UP) {
            OutlinedTextField(confirm, { confirm = it }, label = { Text("Konfirmasi Kata Sandi") }, singleLine = true, enabled = !loading, visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { TextButton(onClick = { confirmVisible = !confirmVisible }, modifier = Modifier.semantics { contentDescription = if (confirmVisible) "Sembunyikan konfirmasi kata sandi" else "Tampilkan konfirmasi kata sandi" }) { Text(if (confirmVisible) "Sembunyi" else "Lihat") } }, modifier = Modifier.fillMaxWidth())
            AccessibleCheckboxRow("Saya menyetujui syarat dan ketentuan", checked = agreed, enabled = !loading, onCheckedChange = { agreed = it })
            TextButton(onClick = { uriHandler.openUri("https://pintarpintar.id/syarat") }) { Text("Baca syarat dan ketentuan") }
        }
        Button(onClick = {
            error = when (mode) {
                AuthMode.SIGN_IN -> validateSignIn(email, password)
                AuthMode.SIGN_UP -> validateSignUp(name, email, password, confirm, agreed)
                AuthMode.RESET -> if (isValidEmail(email)) null else "Masukkan alamat email yang valid."
            }
            if (error == null) {
                val requestedMode = mode
                loading = true; message = null
                scope.launch {
                    try {
                        when (requestedMode) {
                            AuthMode.SIGN_IN -> onSession(repository.signIn(email, password))
                            AuthMode.SIGN_UP -> onSession(repository.signUp(name, email, password))
                            AuthMode.RESET -> {
                                repository.requestPasswordReset(email)
                                message = "Jika akun terdaftar, tautan reset akan dikirim ke email Anda."
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: IllegalStateException) { error = "Sesi tidak dapat disimpan dengan aman. Silakan coba lagi." }
                    catch (_: Exception) { error = "Tidak dapat terhubung. Silakan coba lagi." }
                    finally { loading = false }
                }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (loading) "Memproses..." else when (mode) { AuthMode.SIGN_IN -> "Masuk"; AuthMode.SIGN_UP -> "Daftar"; AuthMode.RESET -> "Kirim Tautan" })
        }
        if (mode == AuthMode.SIGN_IN) TextButton(onClick = { switchMode(AuthMode.RESET) }, enabled = !loading) { Text("Lupa kata sandi?") }
        TextButton(onClick = { switchMode(if (mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN) }, enabled = !loading) {
            Text(if (mode == AuthMode.SIGN_IN) "Belum punya akun? Daftar" else "Sudah punya akun? Masuk")
        }
        Text("Sesi disimpan dengan aman di perangkat ini.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileScreen(session: AccountSession, repository: AccountRepository, learningRepository: LearningRepository, assignmentsRepository: AssignmentRepository, workspaceRepository: ClassWorkspaceRepository, dashboardRepository: DashboardRepository, avatarUploader: AvatarUploader, settingsRepository: AccountSettingsRepository, merchantManagement: MerchantManagementRepository, documentUploads: JobApplicationRepository, onSession: (AccountSession?) -> Unit, onBack: () -> Unit) {
    var profile by remember(session.user.id) { mutableStateOf<AccountProfile?>(null) }
    var learning by remember(session) { mutableStateOf<List<LearningItem>?>(null) }
    var loading by remember(session) { mutableStateOf(true) }
    var learningLoading by remember(session) { mutableStateOf(true) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var learningError by remember(session) { mutableStateOf(false) }
    var attempt by remember(session) { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf(false) }
    var selectedLearning by remember { mutableStateOf<LearningItem?>(null) }
    var showOnboarding by remember(session) { mutableStateOf(false) }
    var onboardingChecked by remember(session.user.id) { mutableStateOf(false) }
    var dashboardRole by remember(session) { mutableStateOf<DashboardRole?>(null) }
    var showSettings by remember(session.user.id) { mutableStateOf(false) }
    var showMerchantRegistration by remember(session.user.id) { mutableStateOf(false) }
    var showMentorRegistration by remember(session.user.id) { mutableStateOf(false) }
    var merchantRegistered by remember(session.user.id) { mutableStateOf(false) }
    var mentorRegistered by remember(session.user.id) { mutableStateOf(false) }

    LaunchedEffect(session, attempt) {
        loading = true; learningLoading = true; error = null; learningError = false
        try {
            val loaded = repository.getProfile(session.token)
            profile = loaded
            if (!onboardingChecked) {
                onboardingChecked = true
                showOnboarding = loaded.onboarding.completedAt == null
            }
        }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) {
            if (e.statusCode == 401) {
                try { onSession(null) } catch (_: Exception) { error = "Sesi kedaluwarsa tetapi tidak dapat dihapus. Silakan coba lagi." }
                learningLoading = false
                return@LaunchedEffect
            }
            error = e.message
        }
        catch (_: Exception) { error = "Profil belum dapat dimuat." }
        finally { loading = false }
        try { learning = repository.getLearning(session.token) }
        catch (e: CancellationException) { throw e }
        catch (e: AccountApiException) {
            if (e.statusCode == 401) {
                try { onSession(null) } catch (_: Exception) { error = "Sesi kedaluwarsa tetapi tidak dapat dihapus. Silakan coba lagi." }
            } else learningError = true
        }
        catch (_: Exception) { learningError = true }
        finally { learningLoading = false }
    }

    val current = profile
    if (showMentorRegistration) {
        MentorRegistrationScreen(session.token, dashboardRepository, documentUploads, onRegistered = { mentorRegistered = true; showMentorRegistration = false; attempt++ }, onBack = { showMentorRegistration = false; attempt++ })
        return
    }
    if (showMerchantRegistration) {
        MerchantRegistrationScreen(session.token, merchantManagement, onRegistered = { merchantRegistered = true; showMerchantRegistration = false; attempt++ }, onBack = { showMerchantRegistration = false; attempt++ })
        return
    }
    if (showSettings && current != null) {
        AccountSettingsScreen(session, current, settingsRepository, onSession, onBack = { showSettings = false })
        return
    }
    dashboardRole?.let { role ->
        DashboardScreen(role, session.token, dashboardRepository, merchantManagement, onBack = { dashboardRole = null })
        return
    }
    if (showOnboarding && current != null) {
        OnboardingScreen(current.onboarding, session.token, repository, onSaved = { showOnboarding = false; attempt++ }, onUnauthorized = { onSession(null) }, onBack = { showOnboarding = false })
        return
    }
    selectedLearning?.let {
        LearningScreen(it, session.token, learningRepository, assignmentsRepository, workspaceRepository, onBack = { selectedLearning = null; attempt++ })
        return
    }
    if (editing && current != null) {
        EditProfileScreen(current, session.token, repository, avatarUploader, onSaved = { profile = it; editing = false }, onUnauthorized = { onSession(null) }, onBack = { editing = false })
        return
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("‹  Kembali ke katalog") }
        ScreenHeading("Profil Saya")
        if (loading) LoadingState("Memuat profil...")
        error?.let { StatusMessage(it, isError = true); TextButton(onClick = { attempt++ }) { Text("Coba Lagi") } }
        current?.let {
            it.avatarUrl?.let { url -> AsyncImage(url, contentDescription = "Foto profil", contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp).clip(CircleShape)) }
            Text(it.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(it.email)
            it.headline?.takeIf(String::isNotBlank)?.let { headline -> Text(headline) }
            it.phone?.takeIf(String::isNotBlank)?.let { phone -> Text(phone) }
            it.bio?.takeIf(String::isNotBlank)?.let { bio -> Text(bio) }
            if (it.expertise.isNotEmpty()) Text("Keahlian: ${it.expertise.joinToString(", ")}")
            it.memberSince?.let { date -> Text("Bergabung: $date", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { editing = true }) { Text("Edit Profil") }
            OutlinedButton(onClick = { showSettings = true }) { Text("Pengaturan Akun") }
            OutlinedButton(onClick = { showOnboarding = true }) { Text(if (it.onboarding.completedAt == null) "Lengkapi Profil" else "Peran dan Keahlian") }
            if (it.isMerchant) OutlinedButton(onClick = { dashboardRole = DashboardRole.MERCHANT }) { Text("Dashboard Merchant") }
            else if (!merchantRegistered) OutlinedButton(onClick = { showMerchantRegistration = true }) { Text("Daftar Merchant") }
            else Text("Pendaftaran merchant tersimpan. Perbarui profil untuk melihat dashboard.")
            if (it.isMentor) OutlinedButton(onClick = { dashboardRole = DashboardRole.MENTOR }) { Text("Dashboard Mentor") }
            if (!it.isMentor && !mentorRegistered) OutlinedButton(onClick = { showMentorRegistration = true }) { Text("Daftar Mentor") }
            else if (!it.isMentor && mentorRegistered) Text("Pendaftaran mentor tersimpan. Perbarui profil untuk melihat dashboard.")
        }
        HorizontalDivider()
        SectionHeading("Pembelajaran Saya")
        if (learningLoading) LoadingState("Memuat pembelajaran...")
        if (learningError) { Text("Pembelajaran belum dapat dimuat."); TextButton(onClick = { attempt++ }) { Text("Coba Lagi") } }
        if (!learningLoading && !learningError && learning.isNullOrEmpty()) Text("Belum ada pembelajaran.")
        learning?.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text("Progres: ${item.progress.coerceIn(0, 100)}%")
                    TextButton(onClick = { selectedLearning = item }) { Text("Buka Pembelajaran") }
                }
            }
        }
        if (signOutError) Text("Sesi tidak dapat dihapus. Silakan coba lagi.", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = {
            try { onSession(null) } catch (_: Exception) { signOutError = true }
        }, modifier = Modifier.fillMaxWidth()) { Text("Keluar") }
        Text("Keluar menghapus sesi di aplikasi ini.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EditProfileScreen(profile: AccountProfile, token: String, repository: AccountRepository, avatarUploader: AvatarUploader, onSaved: (AccountProfile) -> Unit, onUnauthorized: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var phone by remember { mutableStateOf(profile.phone.orEmpty()) }
    var headline by remember { mutableStateOf(profile.headline.orEmpty()) }
    var bio by remember { mutableStateOf(profile.bio.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var avatarFile by remember { mutableStateOf<PlatformFile?>(null) }
    var removeAvatar by remember { mutableStateOf(false) }
    var uploadedAvatar by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = PickerType.File(extensions = listOf("png", "jpg", "jpeg", "webp"))) { selected ->
        if (selected != null) {
            val problem = avatarFileError(selected.name, selected.getSize() ?: 1L)
            if (problem == null) { avatarFile = selected; removeAvatar = false; uploadedAvatar = null }
            error = problem
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !loading) { Text("‹  Kembali") }
        ScreenHeading("Edit Profil")
        error?.let { StatusMessage(it, isError = true) }
        if (!removeAvatar && avatarFile == null) profile.avatarUrl?.let { AsyncImage(it, contentDescription = "Foto profil", contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp).clip(CircleShape)) }
        avatarFile?.let { Text("Foto dipilih: ${it.name}") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !loading, onClick = { picker.launch() }) { Text("Pilih Foto") }
            if (profile.avatarAssetId != null || avatarFile != null) TextButton(enabled = !loading, onClick = { avatarFile = null; uploadedAvatar = null; removeAvatar = true }) { Text("Hapus Foto") }
        }
        if (removeAvatar) Text("Foto akan dihapus saat disimpan.")
        OutlinedTextField(name, { name = it.take(120) }, label = { Text("Nama Lengkap") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it.take(32) }, label = { Text("Nomor Telepon") }, enabled = !loading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(headline, { headline = it.take(160) }, label = { Text("Headline") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bio, { bio = it.take(2000) }, label = { Text("Bio") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            if (name.isBlank()) error = "Nama tidak boleh kosong."
            else {
                loading = true; error = null
                scope.launch {
                    try {
                        val selected = avatarFile
                        val avatar = if (removeAvatar) null else if (selected != null) {
                            uploadedAvatar ?: run {
                                val size = selected.getSize()
                                if (size != null) require(avatarFileError(selected.name, size) == null) { "Ukuran gambar maksimal 2 MB." }
                                val bytes = selected.readBytes()
                                require(avatarFileError(selected.name, bytes.size.toLong()) == null) { "Gambar tidak valid atau terlalu besar." }
                                avatarUploader.upload(token, selected.name, bytes).also { uploadedAvatar = it }
                            }
                        } else profile.avatarAssetId
                        onSaved(repository.updateProfile(token, ProfileUpdate(name.trim(), phone.trim().ifBlank { null }, headline.trim().ifBlank { null }, bio.trim().ifBlank { null }, avatar)))
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) {
                        if (e.statusCode == 401) {
                            try { onUnauthorized() } catch (_: Exception) { error = "Sesi kedaluwarsa tetapi tidak dapat dihapus. Silakan coba lagi." }
                        } else error = e.message
                    }
                    catch (e: IllegalArgumentException) { error = e.message ?: "Gambar tidak valid." }
                    catch (_: Exception) { error = "Profil belum dapat disimpan." }
                    finally { loading = false }
                }
            }
        }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Menyimpan..." else "Simpan") }
    }
}
