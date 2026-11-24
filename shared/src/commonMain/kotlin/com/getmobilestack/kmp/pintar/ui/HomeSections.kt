package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmobilestack.kmp.pintar.catalog.*
import kotlinx.coroutines.CancellationException

@Composable
fun HomeStatisticsSection(repository: HomeRepository) {
    var data by remember { mutableStateOf<HomeStatistics?>(null) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository, retry) {
        try { data = repository.statistics(); error = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    data?.let { Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) { Text("${it.activeStudents} siswa · ${it.learningProducts} kelas · ${it.digitalProducts} produk · Rating ${it.platformRating}", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
    if (error) TextButton(onClick = { retry++ }) { Text("Statistik belum tersedia. Coba lagi") }
}

@Composable
fun FeaturedCatalogSection(repository: HomeRepository, section: FeaturedSection, onProduct: (CatalogItem) -> Unit, onViewAll: (CatalogType) -> Unit) {
    var items by remember(section) { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember(section) { mutableStateOf(true) }
    var error by remember(section) { mutableStateOf(false) }
    var retry by remember(section) { mutableIntStateOf(0) }
    LaunchedEffect(repository, section, retry) {
        loading = true
        try { items = repository.featured(section); error = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionHeading(section.title, modifier = Modifier.weight(1f))
            TextButton(onClick = { onViewAll(section.type) }) { Text("Lihat semua") }
        }
        if (loading) LoadingState("Memuat ${section.title.lowercase()}...")
        if (error) TextButton(onClick = { retry++ }) { Text("Data belum tersedia. Coba lagi") }
        if (!loading && !error && items.isEmpty()) Text("Belum ada produk tersedia.")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { "${it.type}-${it.id}" }) { product ->
                Card(Modifier.width(220.dp).clickable(role = Role.Button, onClickLabel = "Lihat ${product.title}") { onProduct(product) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(product.title, fontWeight = FontWeight.SemiBold, maxLines = 3)
                        Text(product.merchant.name, style = MaterialTheme.typography.bodySmall)
                        Text(formatRupiah(product.price), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTestimonialsSection(repository: HomeRepository) {
    var items by remember { mutableStateOf<List<HomeTestimonial>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository, retry) {
        try { items = repository.testimonials(); error = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading("Apa Kata Mereka?")
        if (error) TextButton(onClick = { retry++ }) { Text("Ulasan belum tersedia. Coba lagi") }
        items.forEach { testimonial ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${testimonial.userName} · ★ ${testimonial.rating}", fontWeight = FontWeight.SemiBold)
                Text(testimonial.classTitle, style = MaterialTheme.typography.bodySmall)
                Text(testimonial.comment)
            } }
        }
    }
}
