package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.getmobilestack.kmp.pintar.catalog.formatRupiah
import com.getmobilestack.kmp.pintar.detail.ProductDetail
import com.getmobilestack.kmp.pintar.detail.ProductDetailRepository
import com.getmobilestack.kmp.pintar.detail.ProductReference
import com.getmobilestack.kmp.pintar.shopping.ShoppingRepository
import com.getmobilestack.kmp.pintar.account.AccountApiException
import com.getmobilestack.kmp.pintar.review.ReviewRepository
import com.getmobilestack.kmp.pintar.review.Review
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface DetailState {
    data object Loading : DetailState
    data object Error : DetailState
    data class Loaded(val product: ProductDetail) : DetailState
}

@Composable
fun ProductDetailScreen(reference: ProductReference, repository: ProductDetailRepository, shopping: ShoppingRepository, reviewsRepository: ReviewRepository, token: String?, onSignIn: () -> Unit, onCart: () -> Unit, onMerchant: (String) -> Unit, onBack: () -> Unit) {
    var state by remember(reference) { mutableStateOf<DetailState>(DetailState.Loading) }
    var attempt by remember(reference) { mutableIntStateOf(0) }
    var actionLoading by remember(reference) { mutableStateOf(false) }
    var actionMessage by remember(reference) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var reviews by remember(reference) { mutableStateOf<List<Review>>(emptyList()) }
    var reviewPage by remember(reference) { mutableIntStateOf(0) }
    var reviewHasMore by remember(reference) { mutableStateOf(false) }
    var reviewLoading by remember(reference) { mutableStateOf(true) }
    var reviewError by remember(reference) { mutableStateOf(false) }
    var reviewRetry by remember(reference) { mutableIntStateOf(0) }
    var canReply by remember(reference, token) { mutableStateOf(false) }
    var rating by remember(reference) { mutableIntStateOf(5) }
    var reviewText by remember(reference) { mutableStateOf("") }
    var reviewMessage by remember(reference) { mutableStateOf<String?>(null) }
    var reviewActionLoading by remember(reference) { mutableStateOf(false) }
    var replyTo by remember(reference) { mutableStateOf<String?>(null) }
    var replyText by remember(reference) { mutableStateOf("") }

    LaunchedEffect(reference, token, reviewRetry) {
        reviewLoading = true; reviewError = false
        try {
            val result = reviewsRepository.getReviews(reference, 1, token)
            reviews = result.data.reviews; reviewPage = result.meta.page; reviewHasMore = reviewPage < result.meta.totalPages
            canReply = result.data.viewerCanReply
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { reviewError = true }
        finally { reviewLoading = false }
    }

    LaunchedEffect(reference, token, attempt) {
        state = DetailState.Loading
        state = try {
            DetailState.Loaded(repository.getDetail(reference, token))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            DetailState.Error
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("‹  Kembali ke katalog") } }
        when (val current = state) {
            DetailState.Loading -> item { LoadingState("Memuat detail produk...") }
            DetailState.Error -> item {
                StatusMessage("Detail belum dapat dimuat. Silakan coba lagi.", isError = true)
                TextButton(onClick = { attempt++ }) { Text("Coba Lagi") }
            }
            is DetailState.Loaded -> {
                val product = current.product
                if (product.imageUrl != null) item {
                    AsyncImage(product.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(210.dp))
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(product.category ?: product.skillCategory ?: reference.kind.sitePath, color = MaterialTheme.colorScheme.primary)
                        Text(product.title, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        Text("Oleh ${product.merchant.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        (product.merchant.slug ?: product.merchant.id)?.let { merchant -> TextButton(onClick = { onMerchant(merchant) }) { Text("Lihat Merchant") } }
                        Text("★ ${product.rating}  ·  ${product.reviewCount} ulasan  ·  ${product.studentsCount} peserta")
                        product.level?.let { Text("Level: $it") }
                        product.duration?.let { Text("Durasi: $it") }
                        Text(formatRupiah(product.price), fontSize = 22.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (product.isOwned) Text("Anda sudah memiliki produk ini.", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = {
                            if (token == null) onSignIn() else {
                                actionLoading = true; actionMessage = null
                                scope.launch {
                                    try { shopping.addToCart(token, reference.id); actionMessage = "Produk ditambahkan ke keranjang."; attempt++ }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: AccountApiException) { actionMessage = e.message }
                                    catch (_: Exception) { actionMessage = "Produk belum dapat ditambahkan." }
                                    finally { actionLoading = false }
                                }
                            }
                        }, enabled = !actionLoading && !product.isOwned && !product.inCart, modifier = Modifier.fillMaxWidth()) { Text(if (product.inCart) "Sudah di Keranjang" else "Tambah ke Keranjang") }
                        OutlinedButton(onClick = {
                            if (token == null) onSignIn() else {
                                actionLoading = true; actionMessage = null
                                scope.launch {
                                    try { shopping.addToWishlist(token, reference.id); actionMessage = "Produk ditambahkan ke wishlist."; attempt++ }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: AccountApiException) { actionMessage = e.message }
                                    catch (_: Exception) { actionMessage = "Produk belum dapat ditambahkan." }
                                    finally { actionLoading = false }
                                }
                            }
                        }, enabled = !actionLoading && !product.inWishlist, modifier = Modifier.fillMaxWidth()) { Text(if (product.inWishlist) "Sudah di Wishlist" else "Simpan ke Wishlist") }
                        TextButton(onClick = onCart) { Text("Lihat Keranjang") }
                        actionMessage?.let { StatusMessage(it) }
                    }
                }
                product.description?.takeIf { it.isNotBlank() }?.let { description ->
                    item { DetailSection("Deskripsi", description) }
                }
                if (product.learningOutcomes.isNotEmpty()) item { DetailList("Hasil pembelajaran", product.learningOutcomes) }
                if (product.requirements.isNotEmpty()) item { DetailList("Persyaratan", product.requirements) }
                if (product.mentors.isNotEmpty()) item {
                    DetailList("Mentor", product.mentors.map { listOfNotNull(it.name, it.headline).joinToString(" — ") })
                }
                if (product.chapters.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Materi kelas")
                        product.chapters.forEach { chapter ->
                            Text(chapter.title, fontWeight = FontWeight.SemiBold)
                            chapter.description?.let { Text(it) }
                            chapter.videos.forEach { Text("• ${it.title}${it.duration?.let { duration -> " ($duration)" } ?: ""}") }
                            chapter.files.forEach { Text("• ${it.name}") }
                        }
                    }
                }
                if (product.meetings.isNotEmpty()) item {
                    DetailList("Jadwal", product.meetings.map { listOfNotNull(it.title, it.date, it.time).joinToString(" · ") })
                }
                if (product.files.isNotEmpty()) item { DetailList("File produk", product.files.map { it.name }) }
                if (product.faqs.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Pertanyaan umum")
                        product.faqs.forEach { faq ->
                            Text(faq.question, fontWeight = FontWeight.SemiBold)
                            Text(faq.answer)
                        }
                    }
                }
                item { SectionTitle("Ulasan") }
                if (token != null && product.isOwned && !product.hasReviewed) item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tulis Ulasan", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().selectableGroup()) { (1..5).forEach { value -> TextButton(modifier = Modifier.weight(1f).semantics { contentDescription = "$value dari 5 bintang"; selected = rating == value; role = Role.RadioButton }, contentPadding = PaddingValues(0.dp), onClick = { rating = value }, enabled = !reviewActionLoading) { Text(if (value <= rating) "★" else "☆", fontSize = 22.sp) } } }
                        OutlinedTextField(reviewText, { reviewText = it.take(2000) }, label = { Text("Ulasan (opsional)") }, enabled = !reviewActionLoading, modifier = Modifier.fillMaxWidth())
                        Button(enabled = !reviewActionLoading, onClick = {
                            reviewActionLoading = true; reviewMessage = null
                            scope.launch {
                                try { reviewsRepository.createReview(reference, token, rating, reviewText); reviewText = ""; reviewMessage = "Ulasan terkirim."; reviewRetry++; attempt++ }
                                catch (e: CancellationException) { throw e }
                                catch (e: AccountApiException) { reviewMessage = e.message }
                                catch (_: Exception) { reviewMessage = "Ulasan belum dapat dikirim." }
                                finally { reviewActionLoading = false }
                            }
                        }) { Text("Kirim Ulasan") }
                    }
                }
                reviewMessage?.let { message -> item { Text(message) } }
                if (reviewLoading && reviews.isEmpty()) item { LoadingState("Memuat ulasan...") }
                if (reviewError) item { Text("Ulasan belum dapat dimuat."); TextButton(onClick = { reviewRetry++ }) { Text("Coba Lagi") } }
                if (!reviewLoading && !reviewError && reviews.isEmpty()) item { Text("Belum ada ulasan.") }
                reviews.forEach { review -> item(key = "review-${review.id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${review.reviewerName}  ·  ★ ${review.rating}", fontWeight = FontWeight.SemiBold)
                        review.comment?.let { Text(it) }
                        review.replies.forEach { reply -> Text("${reply.authorName}: ${reply.comment}", modifier = Modifier.padding(start = 12.dp)) }
                        TextButton(enabled = !reviewActionLoading, onClick = {
                            if (token == null) onSignIn() else {
                                reviewActionLoading = true; reviewMessage = null
                                scope.launch {
                                    try { reviewsRepository.setHelpful(review.id, token, !review.viewerHasVoted); reviewRetry++ }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: AccountApiException) { reviewMessage = e.message }
                                    catch (_: Exception) { reviewMessage = "Pilihan belum dapat disimpan." }
                                    finally { reviewActionLoading = false }
                                }
                            }
                        }) { Text("${if (review.viewerHasVoted) "Batalkan membantu" else "Membantu"} (${review.helpfulCount})") }
                        if (canReply && token != null) {
                            TextButton(enabled = !reviewActionLoading, onClick = { replyTo = if (replyTo == review.id) null else review.id; replyText = "" }) { Text("Balas") }
                            if (replyTo == review.id) {
                                OutlinedTextField(replyText, { replyText = it.take(2000) }, label = { Text("Balasan") }, enabled = !reviewActionLoading, modifier = Modifier.fillMaxWidth())
                                Button(enabled = !reviewActionLoading && replyText.isNotBlank(), onClick = {
                                    reviewActionLoading = true; reviewMessage = null
                                    scope.launch {
                                        try { reviewsRepository.reply(review.id, token, replyText); replyTo = null; replyText = ""; reviewRetry++ }
                                        catch (e: CancellationException) { throw e }
                                        catch (e: AccountApiException) { reviewMessage = e.message }
                                        catch (_: Exception) { reviewMessage = "Balasan belum dapat dikirim." }
                                        finally { reviewActionLoading = false }
                                    }
                                }) { Text("Kirim Balasan") }
                            }
                        }
                    }
                } }
                if (reviewHasMore && !reviewError) item {
                    OutlinedButton(enabled = !reviewLoading, onClick = {
                        reviewLoading = true
                        scope.launch {
                            try {
                                val result = reviewsRepository.getReviews(reference, reviewPage + 1, token)
                                reviews = reviews + result.data.reviews; reviewPage = result.meta.page; reviewHasMore = reviewPage < result.meta.totalPages
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { reviewError = true }
                            finally { reviewLoading = false }
                        }
                    }) { Text("Muat ulasan lainnya") }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String) { SectionHeading(title) }
@Composable private fun DetailSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SectionTitle(title); Text(text) }
}
@Composable private fun DetailList(title: String, entries: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        entries.forEach { Text("• $it") }
    }
}
