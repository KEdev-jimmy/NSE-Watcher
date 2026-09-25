package ke.co.nsewatcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.WatchlistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class CompanyDirectorySignals(
    val hasResults: Boolean = false,
    val hasDividend: Boolean = false,
    val hasCompanyNews: Boolean = false,
    val hasAgm: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaniesDirectory(
    catalog: List<Stock>,
    quotes: List<Stock>,
    name: String,
    newsFeed: List<NewsItem>,
    initialSector: String = "All",
    openCompany: (Stock) -> Unit,
    openWatchlist: () -> Unit,
    openCompare: (List<String>) -> Unit,
    openNews: (NewsItem) -> Unit,
    openProfile: () -> Unit,
    onCatalogLoaded: (List<Stock>) -> Unit,
    onQuotesLoaded: (List<Stock>) -> Unit,
    onNewsLoaded: (List<NewsItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WatchlistStore(context) }
    val alertStore = remember { AlertStore(context) }
    var storageError by remember { mutableStateOf(false) }
    val savedFlow = remember(store) { store.symbols.catch { storageError = true } }
    val saved by savedFlow.collectAsState<List<String>, List<String>?>(initial = null)
    val savedSymbols = saved.orEmpty().map(WatchlistPresentation::symbol).toSet()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedSector by rememberSaveable(initialSector) { mutableStateOf(initialSector) }
    var spotlight by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(CompanySort.NAME.name) }
    var sortMenu by remember { mutableStateOf(false) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var sectorSheet by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(emptySet<String>()) }
    val snackbar = remember { SnackbarHostState() }

    val companies = remember(catalog, quotes) { CompaniesPresentation.companies(catalog, quotes) }
    val baseVisible = remember(companies, query, selectedSector, sortName) {
        CompaniesPresentation.visible(companies, query, selectedSector, CompanySort.valueOf(sortName))
    }
    val sectors = remember(companies) { companies.map { it.sector }.distinct().sorted() }
    val avatar = context.getSharedPreferences("nse_watcher_preferences", 0).getString("avatar_uri", null)

    val linkedStories = remember(newsFeed, companies) {
        companies.associate { stock ->
            stock.symbol to WatchlistPresentation.linkedNews(newsFeed, listOf(stock))
                .sortedByDescending { CompanyResearchPresentation.timestamp(it.publishedAt) ?: java.time.Instant.MIN }
        }
    }
    val signals = remember(linkedStories) {
        linkedStories.mapValues { (_, stories) ->
            CompanyDirectorySignals(
                hasResults = stories.any(::premiumIsResults),
                hasDividend = stories.any(::premiumIsDividend),
                hasCompanyNews = stories.isNotEmpty(),
                hasAgm = stories.any(::premiumIsAgm)
            )
        }
    }

    val today = remember { LocalDate.now(CompanyResearchPresentation.zone) }
    val recentResultSymbols = remember(newsFeed, today) {
        newsFeed.filter(::premiumIsResults).mapNotNull { story ->
            val published = CompanyResearchPresentation.timestamp(story.publishedAt)
                ?.atZone(CompanyResearchPresentation.zone)?.toLocalDate()
            story.symbol.trim().uppercase(Locale.US).takeIf {
                it.isNotBlank() && published != null &&
                    ChronoUnit.DAYS.between(published, today) in 0..45
            }
        }.distinct()
    }
    val upcomingDividendSymbols = remember(newsFeed, today) {
        newsFeed.filter(::premiumIsDividend).mapNotNull { story ->
            val raw = story.exDate.ifBlank { story.paymentDate }
            val date = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
            story.symbol.trim().uppercase(Locale.US).takeIf {
                it.isNotBlank() && date != null &&
                    ChronoUnit.DAYS.between(today, date) in 0..90
            }
        }.distinct()
    }

    val visible = remember(baseVisible, spotlight, recentResultSymbols, upcomingDividendSymbols) {
        when (spotlight) {
            "Results" -> baseVisible.filter { it.symbol in recentResultSymbols }
            "Dividends" -> baseVisible.filter { it.symbol in upcomingDividendSymbols }
            else -> baseVisible
        }
    }

    suspend fun load(force: Boolean) {
        busy = true
        var catalogFailed = false
        var quoteFailed = false
        var newsFailed = false
        try {
            coroutineScope {
                launch {
                    if (catalog.isEmpty() || force) {
                        try {
                            val result = MarketData.companies()
                            if (result.isNotEmpty()) onCatalogLoaded(result)
                            else catalogFailed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            catalogFailed = true
                        }
                    }
                }
                launch {
                    if (
                        MarketRefreshController.shouldRefreshQuotes(quotes.isNotEmpty()) &&
                        (quotes.isEmpty() || force)
                    ) {
                        try {
                            val result = MarketData.stocks()
                            if (result.isNotEmpty()) onQuotesLoaded(result)
                            else quoteFailed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            quoteFailed = true
                        }
                    }
                }
                launch {
                    if (newsFeed.isEmpty() || force) {
                        try {
                            val feed = MarketData.newsFeed(forceRefresh = force)
                            if (feed.error == null) onNewsLoaded(feed.items)
                            else newsFailed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            newsFailed = true
                        }
                    }
                }
            }
            loadError = when {
                catalogFailed && catalog.isEmpty() ->
                    "Company catalogue could not be updated. Try again when data is available."
                quoteFailed ->
                    "Quotes could not be updated. Companies remain available for research."
                newsFailed ->
                    "Company news signals could not be refreshed. The current shared feed remains visible."
                catalogFailed ->
                    "Company catalogue could not be refreshed. Available companies remain visible."
                else -> null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) { load(false) }

    fun refresh() {
        if (!busy) scope.launch { load(true) }
    }

    fun toggleSaved(stock: Stock) {
        if (stock.symbol in pending || saved == null || storageError) return
        pending = pending + stock.symbol
        val wasSaved = stock.symbol in savedSymbols
        scope.launch {
            try {
                val paused = if (wasSaved) {
                    alertStore.alerts.first().filter {
                        it.symbol.equals(stock.symbol, true) && it.enabled
                    }
                } else emptyList()
                paused.forEach { alertStore.setEnabled(it.id, false) }
                if (wasSaved) store.remove(stock.symbol) else store.add(stock.symbol)
                snackbar.currentSnackbarData?.dismiss()
                val message = if (wasSaved) {
                    "${stock.symbol} removed${if (paused.isNotEmpty()) "; its alerts are paused" else ""}"
                } else {
                    "${stock.symbol} added to your watchlist"
                }
                if (snackbar.showSnackbar(message, "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                    if (wasSaved) store.add(stock.symbol) else store.remove(stock.symbol)
                    paused.forEach { alertStore.setEnabled(it.id, true) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar("Could not update your watchlist. Please try again.")
            } finally {
                pending = pending - stock.symbol
            }
        }
    }

    fun select(symbol: String) {
        if (selected.size == 2 && symbol !in selected) {
            scope.launch { snackbar.showSnackbar("Remove a selection before choosing another company.") }
        } else {
            selected = CompaniesPresentation.toggleSelection(selected, symbol)
        }
    }

    BackHandler(comparing) {
        comparing = false
        selected = emptyList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NseWatcherBrandLockup(modifier = Modifier.weight(1f), compact = true)
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Search, "Clear and focus company search", tint = ResearchText)
                }
                IconButton(onClick = openProfile) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(ResearchRaised),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = ResearchText, fontWeight = FontWeight.Bold)
                        if (avatar != null) {
                            AsyncImage(
                                avatar,
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Companies", color = ResearchText, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(3.dp))
                    Text("Explore all NSE listed companies.", color = ResearchMuted, fontSize = 12.5.sp)
                }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search company or ticker (e.g. KCB, SCOM)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ResearchText,
                            unfocusedTextColor = ResearchText,
                            focusedContainerColor = ResearchCard,
                            unfocusedContainerColor = ResearchCard,
                            focusedBorderColor = ResearchGreen,
                            unfocusedBorderColor = ResearchBorder,
                            focusedPlaceholderColor = ResearchMuted,
                            unfocusedPlaceholderColor = ResearchMuted,
                            cursorColor = ResearchGreen
                        )
                    )
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Banking", "Telecom", "Agriculture", "Energy").forEach { sector ->
                            PremiumDirectoryChip(sector, selectedSector == sector) { selectedSector = sector }
                        }
                        PremiumDirectoryChip(
                            if (selectedSector !in listOf("All", "Banking", "Telecom", "Agriculture", "Energy")) selectedSector else "More",
                            selectedSector !in listOf("All", "Banking", "Telecom", "Agriculture", "Energy")
                        ) { sectorSheet = true }
                    }
                }

                item {
                    CompanyDirectorySummaryRow(
                        watchlistCount = savedSymbols.size,
                        resultCount = recentResultSymbols.size,
                        dividendCount = upcomingDividendSymbols.size,
                        openWatchlist = openWatchlist,
                        onResults = {
                            query = ""
                            selectedSector = "All"
                            spotlight = if (spotlight == "Results") "" else "Results"
                        },
                        onDividends = {
                            query = ""
                            selectedSector = "All"
                            spotlight = if (spotlight == "Dividends") "" else "Dividends"
                        }
                    )
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    spotlight == "Results" -> "New Results"
                                    spotlight == "Dividends" -> "Dividend Soon"
                                    selectedSector == "All" -> "All Companies"
                                    else -> "$selectedSector Companies"
                                },
                                color = ResearchText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "${visible.size} ${if (visible.size == 1) "company" else "companies"}",
                                color = ResearchMuted,
                                fontSize = 10.sp
                            )
                        }
                        if (spotlight.isNotBlank()) {
                            TextButton(onClick = { spotlight = "" }) {
                                Text("Show all", color = ResearchGreen, fontSize = 9.5.sp)
                            }
                        }
                        if (!comparing) {
                            OutlinedButton(
                                onClick = { comparing = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Compare", fontSize = 10.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Box {
                            OutlinedButton(
                                onClick = { sortMenu = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    "Sort: " + when (CompanySort.valueOf(sortName)) {
                                        CompanySort.NAME -> "A-Z"
                                        CompanySort.NAME_DESC -> "Z-A"
                                        CompanySort.GAIN -> "Change ↓"
                                        CompanySort.LOSS -> "Change ↑"
                                        CompanySort.PRICE -> "Price ↑"
                                    },
                                    fontSize = 9.5.sp
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(15.dp))
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                CompanySort.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            sortName = option.name
                                            sortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (comparing) {
                    item {
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                            color = ResearchRaised,
                            border = BorderStroke(1.dp, ResearchBorder)
                        ) {
                            Row(
                                Modifier.padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CompareArrows, null, tint = ResearchGreen)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Select two companies to compare sourced facts side by side.",
                                    Modifier.weight(1f),
                                    color = ResearchMuted,
                                    fontSize = 10.sp
                                )
                                TextButton(onClick = {
                                    comparing = false
                                    selected = emptyList()
                                }) { Text("Cancel", color = ResearchGreen, fontSize = 10.sp) }
                            }
                        }
                    }
                }

                loadError?.let { message ->
                    item { ResearchCaption(message) }
                }
                if (storageError) {
                    item { ResearchCaption("Saved companies could not be read. Reopen this screen to retry.") }
                }

                when {
                    companies.isEmpty() && busy -> item { ResearchLoading("Loading company directory...") }
                    visible.isEmpty() -> item {
                        ResearchPanel {
                            ResearchTitle(if (companies.isEmpty()) "Directory unavailable" else "No matching companies")
                            ResearchCaption(
                                if (companies.isEmpty()) "Refresh to try loading the company catalogue again."
                                else "Try another name, ticker, or sector."
                            )
                            TextButton(onClick = {
                                if (companies.isEmpty()) refresh()
                                else {
                                    query = ""
                                    selectedSector = "All"
                                }
                            }) {
                                Text(if (companies.isEmpty()) "Retry" else "Clear filters", color = ResearchGreen)
                            }
                        }
                    }
                    else -> items(visible, key = { it.symbol }) { stock ->
                        PremiumCompanyDirectoryCard(
                            stock = stock,
                            comparing = comparing,
                            selected = stock.symbol in selected,
                            saved = stock.symbol in savedSymbols,
                            canSave = saved != null && !storageError && stock.symbol !in pending,
                            signals = signals[stock.symbol] ?: CompanyDirectorySignals(),
                            stories = linkedStories[stock.symbol].orEmpty(),
                            onOpen = {
                                if (comparing) select(stock.symbol)
                                else openCompany(stock)
                            },
                            onWatch = { toggleSaved(stock) },
                            onStory = openNews
                        )
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Quotes use the latest available provider observation and may be delayed.",
                            Modifier.weight(1f),
                            color = ResearchMuted,
                            fontSize = 9.sp
                        )
                        IconButton(onClick = ::refresh, enabled = !busy) {
                            if (busy) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    color = ResearchGreen,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, "Refresh companies", tint = ResearchGreen)
                            }
                        }
                    }
                }
            }

            if (comparing) {
                Surface(
                    color = ResearchCard,
                    border = BorderStroke(1.dp, ResearchBorder)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${selected.size} selected",
                                color = ResearchText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            selected.forEach { symbol ->
                                InputChip(
                                    selected = true,
                                    onClick = { selected = selected - symbol },
                                    label = { Text(symbol) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            "Remove $symbol",
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Button(
                            onClick = { openCompare(selected) },
                            enabled = selected.size == 2,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Compare companies →")
                        }
                    }
                }
            }
        }

        if (sectorSheet) {
            ModalBottomSheet(
                onDismissRequest = { sectorSheet = false },
                containerColor = ResearchBackground
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item { ResearchTitle("Browse by sector") }
                    items(listOf("All") + sectors) { sector ->
                        TextButton(
                            onClick = {
                                selectedSector = sector
                                sectorSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                sector,
                                Modifier.weight(1f),
                                color = if (selectedSector == sector) ResearchGreen else ResearchText
                            )
                            if (selectedSector == sector) {
                                Icon(Icons.Default.Check, null, tint = ResearchGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyDirectorySummaryRow(
    watchlistCount: Int,
    resultCount: Int,
    dividendCount: Int,
    openWatchlist: () -> Unit,
    onResults: () -> Unit,
    onDividends: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.12f
        if (stack) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    DirectorySummaryCard(
                        "My Watchlist", watchlistCount, "companies",
                        Icons.Default.Star, ResearchGreen, Modifier.width(132.dp), openWatchlist
                    )
                }
                item {
                    DirectorySummaryCard(
                        "New Results", resultCount, "companies",
                        Icons.Default.Description, Color(0xFFFF6EA8), Modifier.width(132.dp), onResults
                    )
                }
                item {
                    DirectorySummaryCard(
                        "Dividend Soon", dividendCount, "companies",
                        Icons.Default.CalendarMonth, Color(0xFFF0B531), Modifier.width(132.dp), onDividends
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectorySummaryCard(
                    "My Watchlist", watchlistCount, "companies",
                    Icons.Default.Star, ResearchGreen, Modifier.weight(1f), openWatchlist
                )
                DirectorySummaryCard(
                    "New Results", resultCount, "companies",
                    Icons.Default.Description, Color(0xFFFF6EA8), Modifier.weight(1f), onResults
                )
                DirectorySummaryCard(
                    "Dividend Soon", dividendCount, "companies",
                    Icons.Default.CalendarMonth, Color(0xFFF0B531), Modifier.weight(1f), onDividends
                )
            }
        }
    }
}

@Composable
private fun DirectorySummaryCard(
    title: String,
    count: Int,
    suffix: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 88.dp).clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.58f))
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f),
                    color = ResearchText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Text(count.toString(), color = accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(suffix, color = ResearchMuted, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun PremiumDirectoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ResearchCard,
            labelColor = ResearchMuted,
            selectedContainerColor = ResearchGreen,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = BorderStroke(1.dp, if (selected) ResearchGreen else ResearchBorder)
    )
}

@Composable
private fun PremiumCompanyDirectoryCard(
    stock: Stock,
    comparing: Boolean,
    selected: Boolean,
    saved: Boolean,
    canSave: Boolean,
    signals: CompanyDirectorySignals,
    stories: List<NewsItem>,
    onOpen: () -> Unit,
    onWatch: () -> Unit,
    onStory: (NewsItem) -> Unit
) {
    val interaction = if (comparing) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onOpen() })
    } else {
        Modifier.clickable(role = Role.Button, onClickLabel = "Research ${stock.name}", onClick = onOpen)
    }
    val accent = researchChangeColor(stock.change.takeIf { stock.changeAvailable })
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).then(interaction),
        color = if (selected && comparing) ResearchGreen.copy(alpha = 0.09f) else ResearchCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected && comparing) ResearchGreen else ResearchBorder)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comparing) {
                    Checkbox(selected, null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(5.dp))
                }
                CompanyLogo(stock, 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stock.symbol,
                            color = ResearchText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (saved) {
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Default.Star, null, tint = Color(0xFFF6C65B), modifier = Modifier.size(15.dp))
                        }
                    }
                    Text(
                        stock.name,
                        color = ResearchText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(stock.sector, color = ResearchMuted, fontSize = 9.5.sp)
                }
                if (stock.history.size >= 2) {
                    DirectorySparkline(
                        stock.history,
                        accent,
                        Modifier.width(58.dp).height(30.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        CompanyResearchPresentation.money(stock.price),
                        color = if (stock.price.isFinite() && stock.price > 0) ResearchText else ResearchMuted,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (stock.changeAvailable && stock.change.isFinite()) CompanyResearchPresentation.percent(stock.change) else "Change unavailable",
                        color = accent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!comparing) {
                    IconButton(onClick = onWatch, enabled = canSave, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (saved) Icons.Default.Star else Icons.Default.StarBorder,
                            if (saved) "Remove from watchlist" else "Add to watchlist",
                            tint = if (saved) Color(0xFFF6C65B) else ResearchMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            val tags = buildList {
                if (signals.hasDividend) add("Dividend" to Color(0xFFF0B531))
                if (signals.hasResults) add("Results" to MaterialTheme.colorScheme.tertiary)
                if (signals.hasAgm) add("AGM" to Color(0xFF9B6BFF))
                if (signals.hasCompanyNews && none { it.first == "Results" }) {
                    add("Company News" to MaterialTheme.colorScheme.tertiary)
                }
                if (stock.averageVolumeAvailable && stock.averageVolume > 0L && stock.volumeAvailable &&
                    stock.volume > (stock.averageVolume * 1.5)
                ) {
                    add("High Volume" to ResearchGreen)
                }
                if (saved && size < 3) add("Watchlist" to ResearchGreen)
            }.take(3)

            if (tags.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { (label, color) ->
                        Surface(
                            color = color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, color.copy(alpha = 0.48f))
                        ) {
                            Text(
                                label,
                                color = color,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            val latest = stories.firstOrNull()
            if (latest != null && !comparing) {
                TextButton(
                    onClick = { onStory(latest) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Article, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        latest.title,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanyLogo(stock: Stock, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(ResearchRaised),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stock.symbol.take(3),
            color = ResearchGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
        val url = stock.logoUrl?.takeIf { it.isNotBlank() }
            ?: "https://mystocks.africa/logos/${stock.symbol.lowercase(Locale.US)}-ke.svg"
        var loaded by remember(url) { mutableStateOf(false) }
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(if (loaded) 1f else 0f).padding(5.dp),
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { loaded = false }
        )
    }
}

@Composable
private fun DirectorySparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    val valid = remember(values) { values.filter { it.isFinite() && it > 0.0 }.takeLast(24) }
    Canvas(modifier) {
        if (valid.size < 2) return@Canvas
        val min = valid.minOrNull() ?: return@Canvas
        val max = valid.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val x = size.width / (valid.size - 1)
        valid.zipWithNext().forEachIndexed { index, pair ->
            val y1 = size.height - ((pair.first - min) / span).toFloat() * size.height
            val y2 = size.height - ((pair.second - min) / span).toFloat() * size.height
            drawLine(
                color = color,
                start = Offset(index * x, y1),
                end = Offset((index + 1) * x, y2),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
