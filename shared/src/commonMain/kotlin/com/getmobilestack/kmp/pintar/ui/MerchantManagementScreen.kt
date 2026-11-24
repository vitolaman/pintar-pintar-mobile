package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.dashboard.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MerchantRegistrationScreen(token: String, repository: MerchantManagementRepository, onRegistered: () -> Unit, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var business by remember { mutableStateOf("individual") }; var category by remember { mutableStateOf<String?>(null) }; var types by remember { mutableStateOf<Set<String>>(emptySet()) }; var agreed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !loading, onClick = { if (success) onRegistered() else onBack() }) { Text("‹  Kembali") }
        ScreenHeading("Daftar Merchant")
        error?.let { StatusMessage(it, isError = true) }
        if (success) { Text("Pendaftaran berhasil disimpan."); Button(onClick = onRegistered) { Text("Perbarui Profil") } }
        else {
            OutlinedTextField(name, { name = it.take(160) }, label = { Text("Nama Toko") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it.take(2000) }, label = { Text("Deskripsi Toko") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it.take(120) }, label = { Text("Kota Operasional") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.take(32) }, label = { Text("WhatsApp Bisnis") }, modifier = Modifier.fillMaxWidth())
            Text("Jenis Merchant")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) { listOf("individual" to "Individu", "institution" to "Lembaga", "company" to "Perusahaan").forEach { (value, label) -> AccessibleRadioRow(label, selected = business == value, enabled = !loading, onClick = { business = value }) } }
            Text("Bidang Utama")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) { listOf(null to "Lainnya", "Teknik & Arsitektur" to "Teknik & Arsitektur", "Pemrograman & IT" to "Pemrograman & IT", "Desain & Kreatif" to "Desain & Kreatif", "Bisnis & Manajemen" to "Bisnis & Manajemen").forEach { (value, label) -> AccessibleRadioRow(label, selected = category == value, enabled = !loading, onClick = { category = value }) } }
            Text("Produk yang dijual")
            listOf("kelas" to "Kelas Video", "bootcamp" to "Bootcamp", "digital" to "Produk Digital").forEach { (value, label) -> AccessibleCheckboxRow(label, checked = value in types, enabled = !loading, onCheckedChange = { types = if (it) types + value else types - value }) }
            AccessibleCheckboxRow("Saya menyetujui syarat dan ketentuan", checked = agreed, enabled = !loading, onCheckedChange = { agreed = it })
            TextButton(onClick = { uriHandler.openUri("https://pintarpintar.id/syarat") }) { Text("Baca syarat dan ketentuan") }
            Button(enabled = !loading && agreed && name.isNotBlank() && description.isNotBlank() && city.isNotBlank() && phone.isNotBlank() && types.isNotEmpty(), onClick = {
                loading = true; error = null
                scope.launch {
                    try { repository.register(token, MerchantRegistration(name.trim(), description.trim(), true, business, category, city.trim(), phone.trim(), types.toList())); success = true }
                    catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Pendaftaran belum dapat disimpan." }
                    finally { loading = false }
                }
            }) { Text(if (loading) "Mendaftar..." else "Daftar Merchant") }
        }
    }
}

@Composable
fun MerchantClassEditor(classId: String?, token: String, repository: MerchantManagementRepository, onSaved: () -> Unit, onBack: () -> Unit) {
    var title by remember(classId) { mutableStateOf("") }; var description by remember(classId) { mutableStateOf("") }
    var type by remember(classId) { mutableStateOf("kelas") }; var status by remember(classId) { mutableStateOf("draft") }
    var price by remember(classId) { mutableStateOf("0") }; var discount by remember(classId) { mutableStateOf("") }
    var loading by remember(classId) { mutableStateOf(classId != null) }; var error by remember(classId) { mutableStateOf<String?>(null) }; var retry by remember { mutableIntStateOf(0) }
    var loaded by remember(classId) { mutableStateOf(classId == null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(classId, retry) {
        if (classId != null) {
            loading = true; error = null
            try { val data = repository.classDetails(token, classId); title = data.title; description = data.description.orEmpty(); type = data.type; status = data.status; price = (data.originalPrice ?: 0.0).toLong().toString(); discount = data.discountPrice?.toLong()?.toString().orEmpty(); loaded = true }
            catch (e: CancellationException) { throw e }
            catch (e: AccountApiException) { error = e.message }
            catch (_: Exception) { error = "Kelas belum dapat dimuat." }
            finally { loading = false }
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(enabled = !loading, onClick = onBack) { Text("‹  Kembali") }
        Text(if (classId == null) "Buat Kelas" else "Edit Kelas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); if (classId != null) TextButton(onClick = { retry++ }) { Text("Muat ulang") } }
        if (loading) CircularProgressIndicator()
        OutlinedTextField(title, { title = it.take(255) }, label = { Text("Judul") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it.take(10000) }, label = { Text("Deskripsi") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        Row { listOf("kelas" to "Kelas Video", "bootcamp" to "Bootcamp").forEach { (value, label) -> FilterChip(selected = type == value, enabled = !loading, onClick = { type = value }, label = { Text(label) }) } }
        Row { listOf("draft", "published", "archived").forEach { value -> FilterChip(selected = status == value, enabled = !loading, onClick = { status = value }, label = { Text(value) }) } }
        OutlinedTextField(price, { price = it.filter(Char::isDigit).take(12) }, label = { Text("Harga Asli") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(discount, { discount = it.filter(Char::isDigit).take(12) }, label = { Text("Harga Jual (opsional)") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
        Button(enabled = !loading && loaded, onClick = {
            val original = price.toDoubleOrNull(); val selling = discount.takeIf(String::isNotBlank)?.toDoubleOrNull()
            if (title.isBlank() || original == null || (selling != null && selling > original)) error = "Periksa judul dan harga."
            else {
                loading = true; error = null
                scope.launch {
                    try { val draft = ClassDraft(title.trim(), description.trim().ifBlank { null }, type, status, original, selling); if (classId == null) repository.createClass(token, draft) else repository.updateClass(token, classId, draft); onSaved() }
                    catch (e: CancellationException) { throw e }
                    catch (e: AccountApiException) { error = e.message }
                    catch (_: Exception) { error = "Kelas belum dapat disimpan." }
                    finally { loading = false }
                }
            }
        }) { Text(if (loading) "Menyimpan..." else "Simpan") }
    }
}
