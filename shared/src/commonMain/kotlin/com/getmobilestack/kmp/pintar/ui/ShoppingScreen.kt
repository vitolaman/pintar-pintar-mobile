package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.catalog.formatRupiah
import com.getmobilestack.kmp.pintar.detail.ProductKind
import com.getmobilestack.kmp.pintar.detail.ProductReference
import com.getmobilestack.kmp.pintar.shopping.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class ShoppingSection { CART, WISHLIST }

@Composable
fun ShoppingScreen(section: ShoppingSection, token: String?, repository: ShoppingRepository, vouchers: VoucherRepository, onProduct: (ProductReference) -> Unit, onBack: () -> Unit, onSignIn: () -> Unit) {
    var cart by remember(section, token) { mutableStateOf<Cart?>(null) }
    var wishlist by remember(section, token) { mutableStateOf<List<ShoppingEntry>>(emptyList()) }
    var page by remember(section, token) { mutableIntStateOf(1) }
    var hasMore by remember(section, token) { mutableStateOf(false) }
    var loading by remember(section, token) { mutableStateOf(token != null) }
    var error by remember(section, token) { mutableStateOf<String?>(null) }
    var message by remember(section, token) { mutableStateOf<String?>(null) }
    var retry by remember(section, token) { mutableIntStateOf(0) }
    var checkout by remember(section, token) { mutableStateOf(false) }
    var preview by remember(section, token) { mutableStateOf<CheckoutPreview?>(null) }
    var voucher by remember(section, token) { mutableStateOf("") }
    var discount by remember(section, token) { mutableStateOf("") }
    var selectedIds by remember(section, token) { mutableStateOf<Set<String>>(emptySet()) }
    var initializedSelection by remember(section, token) { mutableStateOf(false) }
    var showVouchers by remember(section, token) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(section, token, retry) {
        if (token != null) {
            loading = true; error = null
            try {
                if (section == ShoppingSection.CART) {
                    val loaded = repository.getCart(token)
                    selectedIds = if (!initializedSelection) loaded.items.filter { it.item.isAvailable }.map { it.id }.toSet() else reconcileCartSelection(loaded.items, selectedIds)
                    initializedSelection = true; cart = loaded
                }
                else {
                    val result = repository.getWishlist(token, 1)
                    wishlist = result.data; page = result.meta.page; hasMore = page < result.meta.totalPages
                }
            } catch (e: CancellationException) { throw e }
            catch (e: AccountApiException) { error = e.message }
            catch (_: Exception) { error = "Data belum dapat dimuat." }
            finally { loading = false }
        }
    }

    if (showVouchers && token != null) {
        VoucherScreen(vouchers, token, onSelect = { voucher = it; preview = null; showVouchers = false }, onBack = { showVouchers = false })
        return
    }

    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = { if (checkout) { checkout = false; error = null; preview = null; retry++ } else onBack() }) { Text("‹  Kembali") }
            ScreenHeading(if (checkout) "Ringkasan Checkout" else if (section == ShoppingSection.CART) "Keranjang" else "Wishlist", if (checkout) "Periksa ringkasan dan kode promo. Pesanan belum dibuat; pembayaran tidak termasuk." else null)
        }
        if (token == null) item {
            Text("Masuk untuk mengakses ${if (section == ShoppingSection.CART) "keranjang" else "wishlist"} Anda.")
            Button(onClick = onSignIn) { Text("Masuk") }
        } else {
            if (loading) item { LoadingState("Memuat keranjang...") }
            error?.let { message -> item {
                StatusMessage(message, isError = true)
                if (!checkout) TextButton(onClick = { retry++ }) { Text("Coba Lagi") }
            } }
            message?.let { text -> item { StatusMessage(text) } }
            val allEntries = if (section == ShoppingSection.CART) cart?.items.orEmpty() else wishlist
            val selectedEntries = selectedCartEntries(allEntries, selectedIds)
            val entries = if (checkout) selectedEntries else allEntries
            if (!loading && error == null && entries.isEmpty()) item { Text("Belum ada produk.") }
            if (!checkout && section == ShoppingSection.CART && allEntries.isNotEmpty()) item {
                val availableIds = allEntries.filter { it.item.isAvailable }.map { it.id }.toSet()
                AccessibleCheckboxRow("Pilih semua produk tersedia", checked = availableIds.isNotEmpty() && selectedIds.containsAll(availableIds), enabled = !loading && availableIds.isNotEmpty(), onCheckedChange = { selectedIds = if (it) availableIds else emptySet(); preview = null })
            }
            items(entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.item.title, fontWeight = FontWeight.SemiBold)
                        entry.item.merchantName?.let { Text(it) }
                        Text(formatRupiah(entry.item.price), color = MaterialTheme.colorScheme.primary)
                        if (!entry.item.isAvailable) Text("Produk tidak tersedia", color = MaterialTheme.colorScheme.error)
                        ProductKind.fromApiType(entry.item.type)?.let { kind ->
                            if (!checkout && entry.item.isAvailable) TextButton(enabled = !loading, onClick = { onProduct(ProductReference(entry.item.id, kind)) }) { Text("Lihat Produk") }
                        }
                        if (!checkout && section == ShoppingSection.CART) {
                            AccessibleCheckboxRow("Pilih ${entry.item.title}", checked = entry.id in selectedIds, enabled = !loading && entry.item.isAvailable, onCheckedChange = {
                                selectedIds = if (it) selectedIds + entry.id else selectedIds - entry.id; preview = null
                            })
                        }
                        if (!checkout) TextButton(modifier = Modifier.semantics { contentDescription = "Hapus ${entry.item.title} dari ${if (section == ShoppingSection.CART) "keranjang" else "wishlist"}" }, enabled = !loading, onClick = {
                            loading = true; error = null
                            scope.launch {
                                try {
                                    if (section == ShoppingSection.CART) {
                                        val updated = repository.removeFromCart(token, entry.id)
                                        selectedIds = reconcileCartSelection(updated.items, selectedIds); cart = updated; preview = null
                                    }
                                    else { repository.removeFromWishlist(token, entry.id); wishlist = wishlist.filterNot { it.id == entry.id } }
                                } catch (e: CancellationException) { throw e }
                                catch (e: AccountApiException) { error = e.message }
                                catch (_: Exception) { error = "Produk belum dapat dihapus." }
                                finally { loading = false }
                            }
                        }) { Text("Hapus") }
                        if (!checkout && section == ShoppingSection.WISHLIST && entry.item.isAvailable) TextButton(enabled = !loading, onClick = {
                            loading = true; error = null; message = null
                            scope.launch {
                                try { repository.addToCart(token, entry.item.id); message = "Produk ditambahkan ke keranjang." }
                                catch (e: CancellationException) { throw e }
                                catch (e: AccountApiException) { error = e.message }
                                catch (_: Exception) { error = "Produk belum dapat ditambahkan ke keranjang." }
                                finally { loading = false }
                            }
                        }) { Text("Tambah ke Keranjang") }
                    }
                }
            }
            if (section == ShoppingSection.WISHLIST && hasMore && !checkout) item {
                OutlinedButton(enabled = !loading, onClick = {
                    loading = true; error = null
                    scope.launch {
                        try {
                            val result = repository.getWishlist(token, page + 1)
                            wishlist = (wishlist + result.data).distinctBy { it.id }; page = result.meta.page; hasMore = page < result.meta.totalPages
                        } catch (e: CancellationException) { throw e }
                        catch (e: AccountApiException) { error = e.message }
                        catch (_: Exception) { error = "Data belum dapat dimuat." }
                        finally { loading = false }
                    }
                }) { Text("Muat lebih banyak") }
            }
            if (section == ShoppingSection.CART && cart != null && entries.isNotEmpty()) item {
                if (checkout) {
                    OutlinedTextField(voucher, { voucher = it.take(32); preview = null }, label = { Text("Kode Voucher") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = !loading, onClick = { showVouchers = true }) { Text("Cari Voucher") }
                    OutlinedTextField(discount, { discount = it.take(32); preview = null }, label = { Text("Kode Diskon") }, enabled = !loading, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !loading, onClick = {
                        loading = true; error = null; preview = null
                        scope.launch {
                            try { preview = repository.previewCheckout(token, selectedEntries.map { it.item.id }, listOf(voucher, discount).map(String::trim).filter(String::isNotEmpty)) }
                            catch (e: CancellationException) { throw e }
                            catch (e: AccountApiException) { error = e.message }
                            catch (_: Exception) { error = "Ringkasan belum dapat dimuat." }
                            finally { loading = false }
                        }
                    }) { Text("Periksa Kode") }
                    preview?.let {
                        it.items.forEach { product -> Text("${product.title}: ${formatRupiah(product.price)} · Diskon ${formatRupiah(product.discountAmount)}") }
                        it.codes.forEach { code -> Text("Kode ${code.code}${code.merchantName?.let { name -> " · $name" }.orEmpty()}: −${formatRupiah(code.discountAmount)}") }
                        Text("Subtotal: ${formatRupiah(it.subtotal)}")
                        Text("Diskon: ${formatRupiah(it.discountAmount)}")
                        Text("Total: ${formatRupiah(it.totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        it.rejectedCodes.forEach { rejected -> Text("Kode ${rejected.code}: ${rejected.reason}", color = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    Text("Subtotal dipilih: ${formatRupiah(selectedEntries.sumOf { it.item.price })}", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Button(onClick = {
                        checkout = true; loading = true; error = null; preview = null
                        scope.launch {
                            try { preview = repository.previewCheckout(token, selectedEntries.map { it.item.id }, emptyList()) }
                            catch (e: CancellationException) { throw e }
                            catch (e: AccountApiException) { error = e.message }
                            catch (_: Exception) { error = "Ringkasan belum dapat dimuat." }
                            finally { loading = false }
                        }
                    }, enabled = !loading && selectedEntries.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Lanjutkan ke Checkout") }
                }
            }
        }
    }
}
