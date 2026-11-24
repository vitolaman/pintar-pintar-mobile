package com.getmobilestack.kmp.pintar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.getmobilestack.kmp.pintar.account.AccountSession
import com.getmobilestack.kmp.pintar.account.ApiAccountRepository
import com.getmobilestack.kmp.pintar.account.ApiAvatarUploader
import com.getmobilestack.kmp.pintar.account.ApiAccountSettingsRepository
import com.getmobilestack.kmp.pintar.account.SessionStore
import com.getmobilestack.kmp.pintar.catalog.*
import com.getmobilestack.kmp.pintar.detail.*
import com.getmobilestack.kmp.pintar.shopping.ApiShoppingRepository
import com.getmobilestack.kmp.pintar.shopping.ApiVoucherRepository
import com.getmobilestack.kmp.pintar.learning.ApiLearningRepository
import com.getmobilestack.kmp.pintar.learning.ApiAssignmentRepository
import com.getmobilestack.kmp.pintar.learning.ApiClassWorkspaceRepository
import com.getmobilestack.kmp.pintar.review.ApiReviewRepository
import com.getmobilestack.kmp.pintar.jobs.ApiJobsRepository
import com.getmobilestack.kmp.pintar.jobs.ApiJobApplicationRepository
import com.getmobilestack.kmp.pintar.dashboard.ApiDashboardRepository
import com.getmobilestack.kmp.pintar.dashboard.ApiMerchantManagementRepository
import com.getmobilestack.kmp.pintar.help.ApiHelpRepository
import com.getmobilestack.kmp.pintar.community.ApiCommunityRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Blue = Color(0xFF155BC4)

@Composable
fun PintarApp(sessionStore: SessionStore) {
    val client = remember { createApiClient() }
    DisposableEffect(client) { onDispose { client.close() } }
    val catalogRepository = remember(client) { ApiCatalogRepository(client) }
    val homeRepository = remember(client) { ApiHomeRepository(client) }
    val merchantRepository = remember(client) { ApiMerchantRepository(client) }
    val loader = remember(catalogRepository) { CatalogLoader(catalogRepository) }
    val detailRepository = remember(client) { ApiProductDetailRepository(client) }
    val accountRepository = remember(client) { ApiAccountRepository(client) }
    val avatarUploader = remember(client) { ApiAvatarUploader(client) }
    val settingsRepository = remember(client) { ApiAccountSettingsRepository(client) }
    val shoppingRepository = remember(client) { ApiShoppingRepository(client) }
    val voucherRepository = remember(client) { ApiVoucherRepository(client) }
    val bundleRepository = remember(client) { ApiBundleRepository(client) }
    val promotionRepository = remember(client) { ApiPromotionRepository(client) }
    val learningRepository = remember(client) { ApiLearningRepository(client) }
    val assignmentsRepository = remember(client) { ApiAssignmentRepository(client) }
    val workspaceRepository = remember(client) { ApiClassWorkspaceRepository(client) }
    val reviewRepository = remember(client) { ApiReviewRepository(client) }
    val jobsRepository = remember(client) { ApiJobsRepository(client) }
    val jobApplicationRepository = remember(client) { ApiJobApplicationRepository(client) }
    val dashboardRepository = remember(client) { ApiDashboardRepository(client) }
    val merchantManagement = remember(client) { ApiMerchantManagementRepository(client) }
    val helpRepository = remember(client) { ApiHelpRepository(client) }
    val communityRepository = remember(client) { ApiCommunityRepository(client) }
    var state by remember { mutableStateOf(CatalogState()) }
    var query by remember { mutableStateOf("") }
    var retryVersion by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var loadMoreJob by remember { mutableStateOf<Job?>(null) }
    var selectedProduct by remember { mutableStateOf<ProductReference?>(null) }
    var selectedMerchant by remember { mutableStateOf<String?>(null) }
    var previousProduct by remember { mutableStateOf<ProductReference?>(null) }
    var showAccount by remember { mutableStateOf(false) }
    var showJobs by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showPromotion by remember { mutableStateOf(false) }
    var showCommunity by remember { mutableStateOf(false) }
    var productFromPromotion by remember { mutableStateOf(false) }
    var showBundles by remember { mutableStateOf(false) }
    var bundleMerchantId by remember { mutableStateOf<String?>(null) }
    var shoppingSection by remember { mutableStateOf<ShoppingSection?>(null) }
    var productFromShopping by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<AccountSession?>(null) }
    var sessionLoading by remember { mutableStateOf(true) }
    var sessionStorageError by remember { mutableStateOf(false) }
    var storageRetry by remember { mutableIntStateOf(0) }
    var categories by remember { mutableStateOf<List<CatalogCategory>>(emptyList()) }
    var categoriesError by remember { mutableStateOf(false) }
    var categoriesRetry by remember { mutableIntStateOf(0) }
    val catalogListState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(catalogRepository, categoriesRetry) {
        try { categories = catalogRepository.getCategories(); categoriesError = false }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { categoriesError = true }
    }

    LaunchedEffect(sessionStore, storageRetry) {
        try {
            session = sessionStore.load()
            sessionStorageError = false
        } catch (_: Exception) {
            sessionStorageError = true
        } finally {
            sessionLoading = false
        }
    }

    LaunchedEffect(state.type, state.search, state.category, state.subcategory, state.level, state.fileFormat, state.sort, state.sortOrder, retryVersion) {
        loadMoreJob?.cancel()
        val request = state.copy(items = emptyList(), page = 1, hasMore = false, loading = true, error = false)
        state = request
        state = loader.load(request)
    }

    PintarTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val selected = selectedProduct
            if (sessionLoading) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) { LoadingState("Memuat sesi...") }
            } else if (sessionStorageError) {
                Column(Modifier.safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Penyimpanan sesi aman tidak dapat diakses. Silakan coba lagi.")
                    Button(onClick = { sessionLoading = true; storageRetry++ }) { Text("Coba Lagi") }
                }
            } else if (showAccount) {
                AccountScreen(session, accountRepository, learningRepository, assignmentsRepository, workspaceRepository, dashboardRepository, avatarUploader, settingsRepository, merchantManagement, jobApplicationRepository, onSession = { next ->
                    if (next == null) sessionStore.clear() else sessionStore.save(next)
                    session = next
                }, onBack = { showAccount = false })
            } else if (showJobs) {
                JobsScreen(jobsRepository, jobApplicationRepository, session, onSignIn = { showAccount = true }, onBack = { showJobs = false })
            } else if (showHelp) {
                HelpScreen(helpRepository, session?.token, onSignIn = { showAccount = true }, onBack = { showHelp = false })
            } else if (shoppingSection != null && !productFromShopping) {
                ShoppingScreen(shoppingSection!!, session?.token, shoppingRepository, voucherRepository, onProduct = { productFromShopping = true; selectedProduct = it }, onBack = { shoppingSection = null }, onSignIn = { showAccount = true })
            } else if (selected != null) {
                ProductDetailScreen(selected, detailRepository, shoppingRepository, reviewRepository, session?.token, onSignIn = { showAccount = true }, onCart = { productFromShopping = false; shoppingSection = ShoppingSection.CART }, onMerchant = { merchant -> if (selectedMerchant == null) previousProduct = selected; selectedProduct = null; selectedMerchant = merchant; shoppingSection = null; productFromShopping = false; showPromotion = false; productFromPromotion = false }, onBack = { selectedProduct = null; productFromShopping = false; productFromPromotion = false })
            } else if (showPromotion && !productFromPromotion) {
                PromotionScreen(promotionRepository, voucherRepository, session?.token, onSignIn = { showAccount = true }, onProduct = { product -> ProductKind.fromApiType(product.type)?.let { productFromPromotion = true; selectedProduct = ProductReference(product.id, it) } }, onBack = { showPromotion = false })
            } else if (showCommunity) {
                CommunityScreen(communityRepository, avatarUploader, session, onSignIn = { showAccount = true }, onProduct = { selectedProduct = it }, onBack = { showCommunity = false })
            } else if (showBundles) {
                BundleScreen(bundleRepository, shoppingRepository, session?.token, bundleMerchantId, onSignIn = { showAccount = true }, onProduct = { selectedProduct = it }, onCart = { shoppingSection = ShoppingSection.CART }, onBack = { showBundles = false; bundleMerchantId = null })
            } else if (selectedMerchant != null) {
                MerchantScreen(selectedMerchant!!, merchantRepository, catalogRepository, onBundles = { bundleMerchantId = it; showBundles = true }, onProduct = { product -> ProductKind.fromApiType(product.type)?.let { selectedProduct = ProductReference(product.id, it) } }, onBack = { selectedMerchant = null; selectedProduct = previousProduct; previousProduct = null })
            } else CatalogScreen(
                state = state,
                listState = catalogListState,
                homeRepository = homeRepository,
                query = query,
                onQueryChange = { query = it.take(100) },
                onSearch = { state = state.copy(search = query.trim()) },
                onTypeChange = { state = state.copy(type = it) },
                onFeaturedViewAll = { type ->
                    state = state.copy(type = type)
                    scope.launch { catalogListState.animateScrollToItem(2 + FeaturedSection.entries.size) }
                },
                categories = categories,
                categoriesError = categoriesError,
                onCategoriesRetry = { categoriesRetry++ },
                onCategoryChange = { state = state.copy(category = it, subcategory = null) },
                onSubcategoryChange = { state = state.copy(subcategory = it) },
                onLevelChange = { state = state.copy(level = it) },
                onFormatChange = { state = state.copy(fileFormat = it) },
                onSortOrderChange = { state = state.copy(sortOrder = it) },
                onSortChange = { state = state.copy(sort = it) },
                signedIn = session != null,
                onAccountClick = { showAccount = true },
                onCartClick = { shoppingSection = ShoppingSection.CART },
                onWishlistClick = { shoppingSection = ShoppingSection.WISHLIST },
                onJobsClick = { showJobs = true },
                onHelpClick = { showHelp = true },
                onBundlesClick = { bundleMerchantId = null; showBundles = true },
                onPromotionClick = { showPromotion = true },
                onCommunityClick = { showCommunity = true },
                onProductClick = { item ->
                    ProductKind.fromApiType(item.type)?.let { selectedProduct = ProductReference(item.id, it) }
                },
                onLoad = { append ->
                    if (!state.loading) {
                        if (!append) {
                            retryVersion++
                        } else {
                            val request = state.copy(loading = true, error = false)
                            state = request
                            loadMoreJob = scope.launch { state = loader.load(request, append = true) }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CatalogScreen(
    state: CatalogState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    homeRepository: HomeRepository,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTypeChange: (CatalogType) -> Unit,
    onFeaturedViewAll: (CatalogType) -> Unit,
    categories: List<CatalogCategory>,
    categoriesError: Boolean,
    onCategoriesRetry: () -> Unit,
    onCategoryChange: (String?) -> Unit,
    onSubcategoryChange: (String?) -> Unit,
    onLevelChange: (String?) -> Unit,
    onFormatChange: (String?) -> Unit,
    onSortOrderChange: (String?) -> Unit,
    onSortChange: (CatalogSort) -> Unit,
    signedIn: Boolean,
    onAccountClick: () -> Unit,
    onCartClick: () -> Unit,
    onWishlistClick: () -> Unit,
    onJobsClick: () -> Unit,
    onHelpClick: () -> Unit,
    onBundlesClick: () -> Unit,
    onPromotionClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onProductClick: (CatalogItem) -> Unit,
    onLoad: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("pintar pintar", color = Blue, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onAccountClick) { Text(if (signedIn) "Profil" else "Masuk") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCartClick) { Text("Keranjang") }
                OutlinedButton(onClick = onWishlistClick) { Text("Wishlist") }
                OutlinedButton(onClick = onBundlesClick) { Text("Bundling") }
                OutlinedButton(onClick = onPromotionClick) { Text("Promosi") }
                OutlinedButton(onClick = onCommunityClick) { Text("Komunitas") }
                OutlinedButton(onClick = onJobsClick) { Text("Karier") }
                OutlinedButton(onClick = onHelpClick) { Text("Bantuan") }
            }
            Spacer(Modifier.height(20.dp))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BELAJAR BERSAMA PINTAR PINTAR", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Belajar, Berkarya, dan Dapatkan Penghasilan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.semantics { heading() })
                    Text("Temukan kelas, bootcamp, dan produk digital untuk mengembangkan keahlianmu.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item { HomeStatisticsSection(homeRepository) }
        FeaturedSection.entries.forEach { section ->
            item(key = "featured-${section.path}") { FeaturedCatalogSection(homeRepository, section, onProductClick, onFeaturedViewAll) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Cari kelas atau produk") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }))
                Button(onClick = onSearch, modifier = Modifier.padding(top = 8.dp)) { Text("Cari") }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogType.entries.forEach { type ->
                    FilterChip(selected = type == state.type, onClick = { onTypeChange(type) }, label = { Text(type.label) })
                }
            }
        }
        item {
            var filtersExpanded by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { filtersExpanded = !filtersExpanded }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (filtersExpanded) "Terbuka" else "Tertutup" }) {
                    val activeCount = listOf(state.category, state.subcategory, state.level, state.fileFormat, state.sortOrder).count { it != null } + if (state.sort != CatalogSort.NEWEST) 1 else 0
                    Text(if (filtersExpanded) "Sembunyikan Filter" else if (activeCount > 0) "Filter & Urutkan ($activeCount aktif)" else "Filter & Urutkan")
                }
                if (filtersExpanded) {
                Text("Kategori", fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                if (categoriesError) TextButton(onClick = onCategoriesRetry) { Text("Kategori tidak tersedia. Coba lagi") }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.category == null, onClick = { onCategoryChange(null) }, label = { Text("Semua") })
                    categories.forEach { category ->
                        FilterChip(selected = state.category == category.slug, onClick = { onCategoryChange(category.slug) }, label = { Text(category.name) })
                    }
                }
                val selectedCategory = categories.firstOrNull { it.slug == state.category }
                if (selectedCategory?.children?.isNotEmpty() == true) {
                    Text("Subkategori", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.subcategory == null, onClick = { onSubcategoryChange(null) }, label = { Text("Semua") })
                        fun flatten(nodes: List<CatalogCategory>): List<CatalogCategory> = nodes.flatMap { listOf(it) + flatten(it.children) }
                        flatten(selectedCategory.children).forEach { category ->
                            FilterChip(selected = state.subcategory == category.slug, onClick = { onSubcategoryChange(category.slug) }, label = { Text(category.name) })
                        }
                    }
                }
                Text("Level", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, "Pemula", "Menengah", "Mahir").forEach { level ->
                        FilterChip(selected = state.level == level, onClick = { onLevelChange(level) }, label = { Text(level ?: "Semua") })
                    }
                }
                Text("Format file", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null, "pdf", "dwg", "excel", "powerpoint", "word", "zip").forEach { format ->
                        FilterChip(selected = state.fileFormat == format, onClick = { onFormatChange(format) }, label = { Text(format?.uppercase() ?: "Semua") })
                    }
                }
                Text("Urutkan", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CatalogSort.entries.forEach { sort ->
                        FilterChip(selected = sort == state.sort, onClick = { onSortChange(sort) }, label = { Text(sort.label) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Urutan default", "asc" to "Naik", "desc" to "Turun").forEach { (order, label) ->
                        FilterChip(selected = state.sortOrder == order, onClick = { onSortOrderChange(order) }, label = { Text(label) })
                    }
                }
                }
            }
        }
        item { SectionHeading("Jelajahi ${state.type.label}") }
        if (state.loading && state.items.isEmpty()) item { LoadingState("Memuat katalog...") }
        if (state.error) item {
            Column {
                StatusMessage("Data belum dapat dimuat. Silakan coba lagi.", isError = true)
                TextButton(onClick = { onLoad(state.items.isNotEmpty()) }) { Text("Coba Lagi") }
            }
        }
        if (!state.loading && !state.error && state.items.isEmpty()) item { Text("Belum ada data tersedia.") }
        items(state.items, key = { "${it.type}-${it.id}" }) { item ->
            CatalogCard(item) { onProductClick(item) }
        }
        if (state.hasMore && !state.error) item {
            OutlinedButton(onClick = { onLoad(true) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.loading) "Memuat..." else "Muat lebih banyak")
            }
        }
        item { HomeTestimonialsSection(homeRepository) }
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Lihat ${item.title}", onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.imageUrl != null) AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.category ?: item.skillCategory ?: item.type.replaceFirstChar { it.uppercase() }, color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(item.merchant.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatRupiah(item.price), color = Blue, fontWeight = FontWeight.Bold)
                    Text("★ ${item.rating}", color = Color(0xFF9A6700))
                }
            }
        }
    }
}
