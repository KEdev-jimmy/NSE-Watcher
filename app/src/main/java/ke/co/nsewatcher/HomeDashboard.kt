package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.data.CompanyChangeStore
import ke.co.nsewatcher.data.HomeChangeState
import ke.co.nsewatcher.data.HomeChangeStore
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.WatchlistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    currentStocks: List<Stock>, openCompany: (Stock) -> Unit, openNews: (NewsItem) -> Unit,
    openMarket: () -> Unit, openWatchlist: () -> Unit, newsFeed: List<NewsItem>,
    initialMarketStatus: MyStocksCache.MarketStatus, startupDataLoaded: Boolean,
    name: String, initialCatalog: List<Stock>, practiceEnabled: Boolean, practiceCash: Double,
    openAllNews: () -> Unit, openPractice: () -> Unit, openProfile: () -> Unit,
    openAlertSettings: () -> Unit, onQuotesLoaded: (List<Stock>) -> Unit,
    onNewsLoaded: (List<NewsItem>) -> Unit,
    onMarketStatusLoaded: (MyStocksCache.MarketStatus) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watchlist = remember { WatchlistStore(context) }
    val alertStore = remember { AlertStore(context) }
    val companyChangeStore = remember { CompanyChangeStore(context) }
    val changeStore = remember { HomeChangeStore(context) }
    var changeState by remember { mutableStateOf<HomeChangeState?>(null) }
    var changeStateError by remember { mutableStateOf(false) }
    var watchlistError by remember { mutableStateOf(false) }
    var alertError by remember { mutableStateOf(false) }
    var companyChangeError by remember { mutableStateOf(false) }
    val savedFlow = remember(watchlist) { watchlist.symbols.catch { watchlistError = true } }
    val saved by savedFlow.collectAsState<List<String>, List<String>?>(initial = null)
    val eventFlow = remember(alertStore) { alertStore.events.catch { alertError = true } }
    val events by eventFlow.collectAsState(initial = emptyList())
    val companyEventFlow = remember(companyChangeStore) {
        companyChangeStore.events.catch { companyChangeError = true }
    }
    val companyDataEvents by companyEventFlow.collectAsState(initial = emptyList())
    var newsLoading by remember { mutableStateOf(newsFeed.isEmpty()) }
    var newsError by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf(initialCatalog) }
    var market by remember { mutableStateOf(initialMarketStatus) }
    var showAlerts by rememberSaveable { mutableStateOf(false) }
    var gainersSelected by rememberSaveable { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var histories by remember { mutableStateOf<Map<String, List<MyStocksCache.HistoryPoint>>>(emptyMap()) }
    val avatar = context.getSharedPreferences("nse_watcher_preferences", 0).getString("avatar_uri", null)
    LaunchedEffect(initialMarketStatus) { market = initialMarketStatus }
    LaunchedEffect(initialCatalog) { if (initialCatalog.isNotEmpty()) catalog = initialCatalog }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(60_000L) } }

    suspend fun refreshNews(force: Boolean = false) {
        newsLoading = true
        try {
            val result = NewsCache.loadFeedResult(forceRefresh = force)
            newsError = result.error != null
            if (!newsError) onNewsLoaded(result.items)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { newsError = true }
        finally { newsLoading = false }
    }
    LaunchedEffect(Unit) {
        refreshNews()
        if (catalog.isEmpty()) catalog = MyStocksCache.loadCompanies()
        if (!startupDataLoaded && MarketRefreshController.shouldRefreshQuotes(currentStocks.isNotEmpty())) {
            MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let(onQuotesLoaded)
            val refreshedStatus = MyStocksCache.loadMarketStatus()
            val preferredStatus = SharedMarketStatus.preferred(market, refreshedStatus)
            market = preferredStatus
            onMarketStatusLoaded(preferredStatus)
        }
        while (true) { delay(MarketRefreshController.REFRESH_INTERVAL_MS); refreshNews(); historyRevision++ }
    }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                val refreshedStatus = MyStocksCache.loadMarketStatus()
                val preferredStatus = SharedMarketStatus.preferred(market, refreshedStatus)
                market = preferredStatus
                onMarketStatusLoaded(preferredStatus)
                // All foreground screens share the same provider-aware quote cadence.
                if (MarketRefreshController.shouldRefreshQuotes(currentStocks.isNotEmpty())) {
                    val quotes = MyStocksCache.loadStocks()
                    if (quotes.isNotEmpty()) { onQuotesLoaded(quotes); refreshError = null }
                    else refreshError = "Quotes could not be updated. Available observations are still shown."
                } else refreshError = null
                refreshNews(force = true)
                if (catalog.isEmpty()) catalog = MyStocksCache.loadCompanies()
                historyRevision++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { refreshError = "Could not refresh. Please try again." }
            finally { refreshing = false }
        }
    }
    val watched = remember(saved, catalog, currentStocks) { WatchlistPresentation.companies(saved.orEmpty(), catalog, currentStocks) }
    val watchedSymbols = remember(watched) { watched.map { WatchlistPresentation.symbol(it.symbol) }.toSet() }
    val changes = remember(watched, newsFeed, events, companyDataEvents, now) {
        HomePresentation.changes(
            watched = watched,
            news = newsFeed,
            events = events,
            now = now,
            companyDataEvents = companyDataEvents
        )
    }
    LaunchedEffect(saved, watchedSymbols, changes.map { it.id }) {
        if (saved == null) return@LaunchedEffect
        try {
            changeState = changeStore.reconcile(watchedSymbols, changes, Instant.now())
            changeStateError = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            changeStateError = true
        }
    }
    val unreviewedChanges = remember(changes, changeState) {
        val reviewed = changeState?.reviewedIds ?: emptySet()
        if (changeState == null) emptyList() else changes.filter { it.id !in reviewed }
    }
    val brief = unreviewedChanges.take(3)
    fun markReviewed(ids: Set<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            try {
                changeState = changeStore.markReviewed(ids, Instant.now())
                changeStateError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                changeStateError = true
            }
        }
    }
    fun reviewAndOpen(item: HomeBriefItem) {
        scope.launch {
            try {
                changeState = changeStore.markReviewed(setOf(item.id), Instant.now())
                changeStateError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                changeStateError = true
            } finally {
                when {
                    item.story != null -> openNews(item.story)
                    item.stock != null -> openCompany(item.stock)
                    else -> showAlerts = true
                }
            }
        }
    }
    val preview = watched.take(3)
    LaunchedEffect(preview.map { it.symbol }, historyRevision) {
        preview.forEach { stock ->
            try { histories = histories + (stock.symbol to WatchlistPresentation.trend(MyStocksCache.loadHistoryDetails(stock.symbol, "1m").points)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { histories = histories + (stock.symbol to emptyList()) }
        }
    }
    val intelligence = remember(currentStocks, newsFeed) {
        HomeIntelligenceEngine.build(currentStocks.filter { it.price.isFinite() && it.price > 0.0 }, newsFeed)
    }
    val relevantNews = remember(newsFeed, watched) { HomePresentation.companyNews(newsFeed, watched) }
    val displayedNews = if (watched.isEmpty()) newsFeed.distinctBy { it.id }.sortedByDescending { CompanyResearchPresentation.timestamp(it.publishedAt) } else relevantNews

    MaterialTheme(colorScheme = CompanyResearchColors) {
        Column(Modifier.fillMaxSize().background(ResearchBackground)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShowChart, null, tint = ResearchGreen, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(9.dp))
                Text("NSE Watcher", Modifier.weight(1f), color = ResearchText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showAlerts = true }) { Icon(Icons.Default.NotificationsNone, "Open recorded alerts", tint = ResearchText) }
                IconButton(onClick = openProfile, modifier = Modifier.semantics { contentDescription = "Open profile" }) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(ResearchRaised), contentAlignment = Alignment.Center) {
                        Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = ResearchText, fontWeight = FontWeight.Bold)
                        if (avatar != null) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item {
                    Text(HomePresentation.greeting(name, now), color = ResearchText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp)); ResearchBody("Here’s what matters to you.")
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(Modifier.size(8.dp).background(if (market.isKnown && market.isOpen) ResearchGreen else ResearchMuted, CircleShape))
                                Text(when { !market.isKnown -> "Market status unavailable"; market.isOpen -> "Market open"; else -> "Market closed" }, color = if (market.isKnown && market.isOpen) ResearchGreen else ResearchMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ResearchCaption(HomePresentation.freshness(currentStocks, now))
                            val latest = currentStocks.filter { it.price.isFinite() && it.price > 0 }.mapNotNull { CompanyResearchPresentation.timestamp(it.observedAt) }.maxOrNull()
                            if (latest != null) ResearchCaption("Latest observation · ${CompanyResearchPresentation.date(latest.toString())}")
                        }
                        IconButton(onClick = ::refresh, enabled = !refreshing) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = ResearchGreen, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "Refresh Home", tint = ResearchMuted)
                        }
                    }
                    refreshError?.let { ResearchCaption(it) }
                }
                item {
                    ResearchPanel {
                        Text("YOUR DAILY BRIEF", color = ResearchGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("What needs your attention?", Modifier.weight(1f), color = ResearchText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            if (unreviewedChanges.isNotEmpty()) Surface(color = ResearchGreen.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.45f))) {
                                Text("${unreviewedChanges.size} new", Modifier.padding(7.dp), color = ResearchText, fontSize = 11.sp)
                            }
                        }
                        when {
                            watchlistError -> ResearchCaption("Saved companies could not be read. Reopen Home to retry.")
                            saved == null -> ResearchLoading("Loading your companies…")
                            watched.isEmpty() -> {
                                ResearchBody("Make this brief yours")
                                ResearchCaption("Follow companies to establish a baseline. Existing items become known; later developments can then appear here as new.")
                                TextButton(onClick = openWatchlist) { Text("Choose your companies →", color = ResearchGreen) }
                            }
                            changeState == null -> ResearchLoading("Establishing your change baseline…")
                            brief.isEmpty() && newsLoading -> ResearchLoading("Checking for new company developments…")
                            brief.isEmpty() -> {
                                ResearchBody(when {
                                    newsError || alertError || companyChangeError || changeStateError -> "Some change tracking is unavailable"
                                    changes.isNotEmpty() -> "You’re caught up"
                                    else -> "No new changes detected"
                                })
                                ResearchCaption(if (changes.isNotEmpty())
                                    "No unreviewed changes remain in the available 7-day company news, recorded alerts and detected company-data updates."
                                else "NSE Watcher is tracking later published updates, recorded alerts and observed company-data changes for your followed companies.")
                                TextButton(onClick = openWatchlist) { Text("Review your watchlist →", color = ResearchGreen) }
                            }
                        }
                        brief.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = ResearchBorder)
                            HomeBriefRow(item) { reviewAndOpen(item) }
                        }
                        if (unreviewedChanges.isNotEmpty()) {
                            TextButton(onClick = { markReviewed(unreviewedChanges.map { it.id }.toSet()) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Mark all reviewed", color = ResearchGreen, fontSize = 12.sp)
                            }
                        }
                        if (newsError) ResearchCaption("News refresh failed; available stories retain their publication dates.")
                        if (alertError) ResearchCaption("Recorded alerts could not be read.")
                        if (companyChangeError) ResearchCaption("Detected company-data changes could not be read.")
                        if (changeStateError) ResearchCaption("Review state could not be saved; items may reappear until storage succeeds.")
                    }
                }
                item {
                    HomeHeading("Your watchlist", "View all", openWatchlist)
                    Spacer(Modifier.height(8.dp))
                    ResearchPanel {
                        when {
                            watchlistError -> ResearchCaption("Your saved companies are temporarily unavailable.")
                            saved == null -> ResearchLoading("Loading watchlist…")
                            preview.isEmpty() -> {
                                ResearchBody("Keep the companies you care about close.")
                                TextButton(onClick = openWatchlist) { Text("Add your first company →", color = ResearchGreen) }
                            }
                            else -> preview.forEachIndexed { index, stock ->
                                if (index > 0) HorizontalDivider(color = ResearchBorder)
                                HomeStockRow(stock, histories[stock.symbol].orEmpty(), true) { openCompany(stock) }
                            }
                        }
                        if (preview.isNotEmpty()) ResearchCaption("1M trends · Provider daily changes · Quotes may have different observation times")
                    }
                }
                item {
                    HomeHeading("Understand today’s market")
                    Spacer(Modifier.height(8.dp))
                    ResearchPanel {
                        val breadth = intelligence.breadth
                        val total = breadth.advancing + breadth.declining + breadth.unchanged
                        ResearchBody(HomePresentation.marketSummary(breadth))
                        if (total > 0) {
                            ResearchCaption("Among $total companies with available daily changes. Based on the latest available observations.")
                            Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(8.dp))) {
                                if (breadth.advancing > 0) Box(Modifier.weight(breadth.advancing.toFloat()).fillMaxHeight().background(ResearchGreen))
                                if (breadth.unchanged > 0) Box(Modifier.weight(breadth.unchanged.toFloat()).fillMaxHeight().background(ResearchMuted))
                                if (breadth.declining > 0) Box(Modifier.weight(breadth.declining.toFloat()).fillMaxHeight().background(ResearchRed))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${breadth.advancing} rising", color = ResearchGreen, fontSize = 12.sp)
                                Text("${breadth.unchanged} unchanged", color = ResearchMuted, fontSize = 12.sp)
                                Text("${breadth.declining} falling", color = ResearchRed, fontSize = 12.sp)
                            }
                        } else ResearchCaption("Missing data is not counted as unchanged. Refresh when quotes become available.")
                        val sources = currentStocks.map { it.source.trim() }.filter { it.isNotEmpty() }.distinct()
                        ResearchCaption("Source: ${sources.joinToString().ifBlank { "Unavailable" }}")
                    }
                }
                val marketContext = HomeMarketContextPresentation.items(intelligence)
                if (marketContext.isNotEmpty()) item {
                    HomeHeading("Market context")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        marketContext.forEach { insight ->
                            ResearchPanel {
                                Text("OBSERVED CONTEXT", color = ResearchGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                                ResearchBody(insight.fact)
                                if (insight.calculation.isNotBlank()) ResearchCaption(insight.calculation)
                                if (insight.interpretation.isNotBlank()) {
                                    Text("HOW TO READ IT", color = ResearchMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    ResearchCaption(insight.interpretation)
                                }
                                val evidence = insight.evidence.firstOrNull()
                                if (evidence != null) {
                                    val evidenceDate = evidence.date.takeIf(String::isNotBlank)
                                        ?.let(CompanyResearchPresentation::date)
                                    ResearchCaption(
                                        listOfNotNull(
                                            "Evidence",
                                            evidence.source.takeIf(String::isNotBlank),
                                            evidenceDate
                                        ).joinToString(" · ")
                                    )
                                } else if (insight.source.isNotBlank()) {
                                    ResearchCaption("Evidence · ${insight.source}")
                                }
                                TextButton(onClick = openMarket, contentPadding = PaddingValues(0.dp)) {
                                    Text("Inspect market evidence →", color = ResearchGreen, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                item {
                    HomeHeading("Market movers")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(true to "Gainers", false to "Losers").forEach { (selected, label) ->
                            FilterChip(selected = gainersSelected == selected, onClick = { gainersSelected = selected }, label = { Text(label) },
                                shape = RoundedCornerShape(10.dp), modifier = Modifier.heightIn(min = 44.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ResearchGreen.copy(alpha = 0.12f), selectedLabelColor = ResearchGreen, containerColor = ResearchCard, labelColor = ResearchMuted),
                                border = BorderStroke(1.dp, if (gainersSelected == selected) ResearchGreen else ResearchBorder))
                        }
                    }
                    ResearchPanel {
                        val movers = (if (gainersSelected) intelligence.gainers else intelligence.losers).take(3)
                        if (movers.isEmpty()) ResearchCaption(if (currentStocks.isEmpty()) "Market quotes are unavailable." else "No ${if (gainersSelected) "gainers" else "losers"} in the available daily changes.")
                        movers.forEachIndexed { index, stock ->
                            if (index > 0) HorizontalDivider(color = ResearchBorder)
                            HomeStockRow(stock, emptyList(), false) { openCompany(stock) }
                        }
                        TextButton(onClick = openMarket) { Text("Explore market →", color = ResearchGreen) }
                    }
                }
                item {
                    HomeHeading(if (watched.isEmpty()) "Latest market news" else "News for your companies", "View all", openAllNews)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        displayedNews.take(2).forEach { story -> HomeNewsRow(story) { openNews(story) } }
                        if (newsLoading && displayedNews.isEmpty()) ResearchLoading("Loading news…")
                        if (!newsLoading && displayedNews.isEmpty()) ResearchPanel {
                            ResearchCaption(if (newsError) "The news service is unavailable. Try refreshing." else if (watched.isEmpty()) "No articles were returned by the feed." else "No articles linked to your saved companies were returned by this feed.")
                            TextButton(onClick = openAllNews) { Text("Explore all news →", color = ResearchGreen) }
                        }
                        if (newsError && displayedNews.isNotEmpty()) ResearchCaption("Could not refresh news. Previously loaded articles are shown.")
                    }
                }
                item {
                    ResearchPanel {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = ResearchMuted, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("PRACTICE PORTFOLIO", color = ResearchGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                ResearchBody("Build confidence with virtual money")
                                Text(if (practiceEnabled) practiceCash.takeIf { it.isFinite() && it >= 0 }?.let { String.format(Locale.US, "KSh %,.2f", it) } ?: "Unavailable" else "KSh 1,000,000",
                                    color = ResearchText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                ResearchCaption(if (practiceEnabled) "Available virtual cash" else "Suggested virtual starting balance")
                            }
                        }
                        ResearchCaption(if (practiceEnabled) "Continue learning with your saved practice portfolio." else "Choose your starting balance and try a practice investment.")
                        Button(onClick = openPractice, shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.End)) {
                            Text(if (practiceEnabled) "Open practice portfolio →" else "Start practising →")
                        }
                    }
                }
            }
        }
        if (showAlerts) ModalBottomSheet(onDismissRequest = { showAlerts = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    ResearchTitle("Your recorded alerts")
                    ResearchCaption("Conditions detected by background checks. Detection does not confirm Android notification delivery.")
                    Row {
                        TextButton(onClick = { showAlerts = false; openWatchlist() }) { Text("Manage rules →", color = ResearchGreen) }
                        TextButton(onClick = { showAlerts = false; openAlertSettings() }) { Text("Preferences", color = ResearchGreen) }
                    }
                    if (alertError) ResearchCaption("Alert history could not be read. Reopen Home to retry.")
                    else if (events.isEmpty()) ResearchCaption("No alerts have been recorded yet. History starts with this app update; past notifications are not reconstructed.")
                }
                items(events, key = { it.id }) { event ->
                    ResearchPanel {
                        ResearchBody("${event.symbol} · ${event.title}")
                        ResearchBody(event.message)
                        ResearchCaption("Detected · ${CompanyResearchPresentation.date(event.recordedAt)}")
                        if (event.observedAt.isNotBlank()) ResearchCaption("Quote observed · ${CompanyResearchPresentation.date(event.observedAt)}")
                        val company = WatchlistPresentation.companies(listOf(event.symbol), catalog, currentStocks).firstOrNull()
                        if (company != null) TextButton(onClick = { showAlerts = false; openCompany(company) }) { Text("Research company →", color = ResearchGreen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = ResearchText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onAction) { Text("$action →", color = ResearchGreen, fontSize = 12.sp) }
    }
}

@Composable
private fun HomeBriefRow(item: HomeBriefItem, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(ResearchRaised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (item.alert != null) Icons.Default.NotificationsNone else Icons.Default.Description, null, tint = ResearchMuted, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("WHAT CHANGED", color = ResearchGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
            Text(item.title, color = ResearchText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(item.detail, color = ResearchMuted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text("WHY IT MAY MATTER", color = ResearchMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(item.whyItMayMatter, color = ResearchText, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            ResearchCaption("Evidence · ${item.source} · ${CompanyResearchPresentation.date(item.time)}")
            Text("UNCERTAINTY", color = ResearchMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(item.uncertainty, color = ResearchMuted, fontSize = 12.sp, lineHeight = 18.sp)
            TextButton(onClick = open, contentPadding = PaddingValues(0.dp)) { Text("${item.action} →", color = ResearchGreen, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun HomeStockRow(stock: Stock, history: List<MyStocksCache.HistoryPoint>, trend: Boolean, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Research ${stock.name}", onClick = open).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).background(ResearchRaised, CircleShape), contentAlignment = Alignment.Center) {
                Text(stock.symbol.take(1), color = ResearchGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stock.name, color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ResearchCaption(stock.symbol)
            }
            if (trend) WatchlistSparkline(history, Modifier.width(46.dp).height(24.dp))
            Column(Modifier.widthIn(max = 105.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (trend) Text(CompanyResearchPresentation.money(stock.price), color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val change = stock.change.takeIf { stock.changeAvailable && it.isFinite() }
                Text(change?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable", color = researchChangeColor(change), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(if (stock.observedAt.isBlank()) "Observation time unavailable" else "As of ${CompanyResearchPresentation.date(stock.observedAt)}", color = ResearchMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 40.dp, top = 5.dp))
    }
}

@Composable
private fun HomeNewsRow(story: NewsItem, open: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = open), color = ResearchCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ResearchBorder)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(story.title, color = ResearchText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                ResearchCaption("${story.source.ifBlank { "Source unavailable" }} · ${CompanyResearchPresentation.date(story.publishedAt)}")
                if (story.symbol.isNotBlank()) Surface(color = ResearchRaised, shape = RoundedCornerShape(5.dp)) {
                    Text(story.symbol, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = ResearchMuted, fontSize = 10.sp)
                }
            }
            if (story.imageUrl.isNotBlank()) AsyncImage(story.imageUrl, null, Modifier.size(68.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        }
    }
}
