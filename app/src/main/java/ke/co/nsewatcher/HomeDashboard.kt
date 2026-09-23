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
import androidx.compose.ui.graphics.Color
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
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.MarketHistoryCache
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.WatchlistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    currentStocks: List<Stock>, openCompany: (Stock) -> Unit, openNews: (NewsItem) -> Unit,
    openMarket: () -> Unit, openWatchlist: () -> Unit, newsFeed: List<NewsItem>,
    marketIndices: List<MyStocksCache.MarketIndex>,
    initialMarketStatus: MyStocksCache.MarketStatus, startupDataLoaded: Boolean,
    name: String, initialCatalog: List<Stock>, practiceEnabled: Boolean, practiceCash: Double,
    openAllNews: () -> Unit, openPractice: () -> Unit, openProfile: () -> Unit,
    openAlertSettings: () -> Unit, onQuotesLoaded: (List<Stock>) -> Unit,
    onNewsLoaded: (List<NewsItem>) -> Unit,
    onIndicesLoaded: (List<MyStocksCache.MarketIndex>) -> Unit,
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
    var showChanges by rememberSaveable { mutableStateOf(false) }
    var gainersSelected by rememberSaveable { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var handledHistoryRevision by remember { mutableIntStateOf(0) }
    var histories by remember { mutableStateOf<Map<String, List<MyStocksCache.HistoryPoint>>>(emptyMap()) }
    val avatar = context.getSharedPreferences("nse_watcher_preferences", 0).getString("avatar_uri", null)
    LaunchedEffect(initialMarketStatus) { market = initialMarketStatus }
    LaunchedEffect(initialCatalog) { if (initialCatalog.isNotEmpty()) catalog = initialCatalog }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(60_000L) } }

    suspend fun refreshNews(force: Boolean = false) {
        newsLoading = true
        try {
            val result = MarketData.newsFeed(forceRefresh = force)
            newsError = result.error != null
            if (!newsError) onNewsLoaded(result.items)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { newsError = true }
        finally { newsLoading = false }
    }
    LaunchedEffect(Unit) {
        refreshNews()
        if (catalog.isEmpty()) catalog = MarketData.companies()
        if (!startupDataLoaded && MarketRefreshController.shouldRefreshQuotes(currentStocks.isNotEmpty())) {
            MarketData.stocks().takeIf { it.isNotEmpty() }?.let(onQuotesLoaded)
            val recoveredStatus = MarketData.status()
            market = recoveredStatus
            if (recoveredStatus.isKnown || !initialMarketStatus.isKnown) {
                onMarketStatusLoaded(recoveredStatus)
            }
            MarketData.indices(recoveredStatus.isKnown && recoveredStatus.isOpen)
                .takeIf { it.isNotEmpty() }
                ?.let(onIndicesLoaded)
        }
        while (true) { delay(MarketRefreshController.REFRESH_INTERVAL_MS); refreshNews(); historyRevision++ }
    }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                val refreshedStatus = MarketData.status()
                market = refreshedStatus
                if (refreshedStatus.isKnown || !initialMarketStatus.isKnown) {
                    onMarketStatusLoaded(refreshedStatus)
                }
                MarketData.indices(refreshedStatus.isKnown && refreshedStatus.isOpen)
                    .takeIf { it.isNotEmpty() }
                    ?.let(onIndicesLoaded)
                // All foreground screens share the same provider-aware quote cadence.
                if (MarketRefreshController.shouldRefreshQuotes(currentStocks.isNotEmpty())) {
                    val quotes = MarketData.stocks()
                    if (quotes.isNotEmpty()) { onQuotesLoaded(quotes); refreshError = null }
                    else refreshError = "Quotes could not be updated. Available observations are still shown."
                } else refreshError = null
                refreshNews(force = true)
                if (catalog.isEmpty()) catalog = MarketData.companies()
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
    val attentionDigest = remember(unreviewedChanges) {
        HomePresentation.attentionDigest(unreviewedChanges)
    }
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
        val forceHistoryRefresh = historyRevision > handledHistoryRevision
        preview.forEach { stock ->
            try {
                val result = MarketHistoryCache.load(stock.symbol, "1M", forceRefresh = forceHistoryRefresh)
                histories = histories + (stock.symbol to WatchlistPresentation.trend(result.points))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                histories = histories + (stock.symbol to emptyList())
            }
        }
        if (forceHistoryRefresh) handledHistoryRevision = historyRevision
    }
    val homeIndices = remember(marketIndices) { HomeMarketIndexPresentation.fromProvider(marketIndices) }
    val intelligence = remember(currentStocks, newsFeed, homeIndices) {
        HomeIntelligenceEngine.build(
            currentStocks.filter { it.price.isFinite() && it.price > 0.0 },
            newsFeed,
            homeIndices
        )
    }
    val relevantNews = remember(newsFeed, watched) { HomePresentation.companyNews(newsFeed, watched) }
    val displayedNews = if (watched.isEmpty()) newsFeed.distinctBy { it.id }.sortedByDescending { CompanyResearchPresentation.timestamp(it.publishedAt) } else relevantNews

    val unreviewedNewsCompanies = remember(unreviewedChanges) {
        unreviewedChanges.filter { it.story != null }.map { WatchlistPresentation.symbol(it.symbol) }.filter { it.isNotBlank() }.distinct().size
    }
    val unreviewedIds = remember(unreviewedChanges) { unreviewedChanges.map { it.id }.toSet() }
    val unreviewedDividendUpdates = remember(unreviewedChanges, companyDataEvents, unreviewedIds) {
        companyDataEvents.count { it.kind == ke.co.nsewatcher.data.CompanyDataChangeKind.DIVIDEND && it.id in unreviewedIds } +
            unreviewedChanges.count { item ->
                val story = item.story ?: return@count false
                story.category.contains("dividend", true) || story.dividendAmount.isNotBlank() || story.exDate.isNotBlank()
            }
    }
    val unreviewedAlertCount = remember(unreviewedChanges) { unreviewedChanges.count { it.alert != null } }
    val breadth = intelligence.breadth
    val totalBreadth = breadth.advancing + breadth.declining + breadth.unchanged
    val strongestSector = intelligence.sectors.maxByOrNull { it.averageChangePct }
    val topMover = (intelligence.gainers + intelligence.losers).maxByOrNull { abs(it.change) }

    MaterialTheme(colorScheme = CompanyResearchColors) {
        Column(Modifier.fillMaxSize().background(ResearchBackground)) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    HomeH3Header(
                        name = name,
                        avatar = avatar,
                        market = market,
                        stocks = currentStocks,
                        now = now,
                        refreshing = refreshing,
                        hasAttention = unreviewedAlertCount > 0 || events.isNotEmpty(),
                        onRefresh = ::refresh,
                        openAlerts = { showAlerts = true },
                        openProfile = openProfile
                    )
                    refreshError?.let {
                        Text(it, Modifier.padding(horizontal = 16.dp, vertical = 3.dp), color = ResearchMuted, fontSize = 9.5.sp)
                    }
                }
                item {
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        HomeH3BriefCard(
                            newsCompanies = unreviewedNewsCompanies,
                            dividendUpdates = unreviewedDividendUpdates,
                            alertCount = unreviewedAlertCount,
                            loading = saved == null || (watched.isNotEmpty() && changeState == null) || (newsLoading && changes.isEmpty()),
                            hasError = watchlistError || newsError || alertError || companyChangeError || changeStateError,
                            review = { if (unreviewedChanges.isEmpty()) openWatchlist() else showChanges = true },
                            manageWatchlist = openWatchlist,
                            seeAll = { if (unreviewedChanges.isEmpty()) openWatchlist() else showChanges = true }
                        )
                    }
                }
                item {
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        HomeH3QuickActions(
                            openWatchlist = openWatchlist,
                            openAlerts = { showAlerts = true },
                            openPractice = openPractice
                        )
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        HomeH3SectionHeader("Your watchlist", "View all", openWatchlist)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = ResearchCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ResearchBorder)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                when {
                                    watchlistError -> HomeH3Message("Your saved companies are temporarily unavailable.")
                                    saved == null -> HomeH3Message("Loading your watchlist…")
                                    preview.isEmpty() -> Row(
                                        Modifier.fillMaxWidth().clickable(onClick = openWatchlist).padding(vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, null, tint = ResearchGreen)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add your first company", Modifier.weight(1f), color = ResearchText, fontWeight = FontWeight.SemiBold)
                                        Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted)
                                    }
                                    else -> preview.forEachIndexed { index, stock ->
                                        if (index > 0) HorizontalDivider(color = ResearchBorder.copy(alpha = 0.7f))
                                        HomeH3WatchlistRow(stock, histories[stock.symbol].orEmpty()) { openCompany(stock) }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        HomeH3SectionHeader("News for your companies", "View all", openAllNews)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = ResearchCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ResearchBorder)
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when {
                                    watched.isEmpty() -> HomeH3Message("Follow companies to get a personalised company-news feed here.")
                                    newsLoading && relevantNews.isEmpty() -> HomeH3Message("Loading company news…")
                                    newsError && relevantNews.isEmpty() -> HomeH3Message("Company news is temporarily unavailable.")
                                    relevantNews.isEmpty() -> HomeH3Message("No recent stories were found for your followed companies.")
                                    else -> relevantNews.take(2).forEach { story ->
                                        HomeH3NewsRow(story) { openNews(story) }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        HomeH3MarketSnapshot(
                            breadth = breadth,
                            total = totalBreadth,
                            sector = strongestSector,
                            topMover = topMover,
                            openMarket = openMarket,
                            openMover = openCompany
                        )
                    }
                }
                item {
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        HomeH3MoversCard(
                            selectedGainers = gainersSelected,
                            movers = (if (gainersSelected) intelligence.gainers else intelligence.losers).take(3),
                            quotesAvailable = currentStocks.isNotEmpty(),
                            select = { gainersSelected = it },
                            openMarket = openMarket,
                            openCompany = openCompany
                        )
                    }
                }
                item {
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        HomeH3PracticeCard(practiceEnabled, practiceCash, openPractice)
                    }
                }
            }
        }
        if (showChanges) ModalBottomSheet(
            onDismissRequest = { showChanges = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ResearchBackground
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(0.78f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ResearchTitle("What changed for you")
                    ResearchCaption("${unreviewedChanges.size} ${if (unreviewedChanges.size == 1) "development" else "developments"} ready to review")
                }
                items(unreviewedChanges, key = { it.id }) { item ->
                    ResearchPanel {
                        Text(item.title, color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ResearchCaption(item.detail)
                        TextButton(onClick = { showChanges = false; reviewAndOpen(item) }, contentPadding = PaddingValues(0.dp)) {
                            Text("${item.action} →", color = ResearchGreen, fontSize = 11.sp)
                        }
                    }
                }
                if (unreviewedChanges.isEmpty()) item { HomeH3Message("You're caught up.") }
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

@Composable
private fun HomeH3Header(
    name: String,
    avatar: String?,
    market: MyStocksCache.MarketStatus,
    stocks: List<Stock>,
    now: Instant,
    refreshing: Boolean,
    hasAttention: Boolean,
    onRefresh: () -> Unit,
    openAlerts: () -> Unit,
    openProfile: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ShowChart, null, tint = ResearchGreen, modifier = Modifier.size(27.dp))
            Spacer(Modifier.width(8.dp))
            Text("NSE Watcher", Modifier.weight(1f), color = ResearchText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Box {
                IconButton(onClick = openAlerts) {
                    Icon(Icons.Default.NotificationsNone, "Open recorded alerts", tint = ResearchText)
                }
                if (hasAttention) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape).background(ResearchGreen)
                            .align(Alignment.TopEnd).offset(x = (-7).dp, y = 7.dp)
                    )
                }
            }
            IconButton(onClick = openProfile) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(ResearchRaised), contentAlignment = Alignment.Center) {
                    Text(name.trim().take(1).uppercase().ifBlank { "I" }, color = ResearchText, fontWeight = FontWeight.Bold)
                    if (avatar != null) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            HomePresentation.greeting(name, now),
            color = ResearchText,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("Here's what changed in your market today.", color = ResearchMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        val latest = stocks.filter { it.price.isFinite() && it.price > 0.0 }
            .mapNotNull { CompanyResearchPresentation.timestamp(it.observedAt) }
            .maxOrNull()
        HomeH3StatusStrip(
            status = when {
                !market.isKnown -> "Market status unavailable"
                market.isOpen -> "Market open"
                else -> "Market closed"
            },
            freshness = HomePresentation.freshness(stocks, now),
            latest = latest?.let { CompanyResearchPresentation.date(it.toString()) } ?: "Observation time unavailable",
            known = market.isKnown,
            refreshing = refreshing,
            onRefresh = onRefresh
        )
    }
}

@Composable
private fun HomeH3StatusStrip(
    status: String,
    freshness: String,
    latest: String,
    known: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xE00A2032),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF23465F))
    ) {
        Row(
            Modifier.heightIn(min = 45.dp).padding(start = 11.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(if (known) ResearchGreen else ResearchMuted, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(status, color = ResearchText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            HomeH3VerticalDivider()
            Text(freshness, Modifier.weight(0.78f), color = ResearchMuted, fontSize = 9.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HomeH3VerticalDivider()
            Text(latest, Modifier.weight(1f), color = ResearchMuted, fontSize = 9.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(38.dp)) {
                if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), color = ResearchGreen, strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, "Refresh Home", tint = ResearchMuted, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun HomeH3VerticalDivider() {
    Box(Modifier.padding(horizontal = 7.dp).width(1.dp).height(20.dp).background(ResearchBorder))
}

@Composable
private fun HomeH3BriefCard(
    newsCompanies: Int,
    dividendUpdates: Int,
    alertCount: Int,
    loading: Boolean,
    hasError: Boolean,
    review: () -> Unit,
    manageWatchlist: () -> Unit,
    seeAll: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ResearchCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "WHAT CHANGED FOR YOU",
                    Modifier.weight(1f),
                    color = ResearchGreen,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.55.sp
                )
                TextButton(onClick = seeAll, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("See all →", color = ResearchGreen, fontSize = 10.5.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HomeH3Metric(Icons.Default.Article, Color(0xFF2EA7F5), if (loading) "-" else newsCompanies.toString(), "watched\ncompanies\nin the news", Modifier.weight(1f))
                HomeH3Metric(Icons.Default.EventAvailable, Color(0xFFB55CF6), if (loading) "-" else dividendUpdates.toString(), "dividend\nupdate", Modifier.weight(1f))
                HomeH3Metric(Icons.Default.Notifications, Color(0xFFFFC857), if (loading) "-" else alertCount.toString(), "alerts ready\nto review", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(
                    onClick = review,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ResearchGreen, contentColor = Color(0xFF061625))
                ) {
                    Text("Review changes →", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = manageWatchlist,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ResearchBorder)
                ) {
                    Text("Manage watchlist →", color = ResearchText, fontSize = 10.sp, maxLines = 1)
                }
            }
            if (hasError) {
                Text("Some Home sources are temporarily unavailable; available observations remain visible.", color = ResearchMuted, fontSize = 8.8.sp)
            }
        }
    }
}

@Composable
private fun HomeH3Metric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    value: String,
    label: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 91.dp),
        color = ResearchRaised.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(value, color = ResearchText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(label, color = ResearchMuted, fontSize = 9.2.sp, lineHeight = 11.5.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(13.dp).align(Alignment.Bottom))
        }
    }
}

@Composable
private fun HomeH3SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = ResearchText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("${action} →", color = ResearchGreen, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun HomeH3Message(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        color = ResearchMuted,
        fontSize = 10.2.sp,
        lineHeight = 14.sp
    )
}

@Composable
private fun HomeH3WatchlistRow(
    stock: Stock,
    history: List<MyStocksCache.HistoryPoint>,
    open: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Research ${stock.name}", onClick = open)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(93.dp)) {
            Text(stock.symbol, color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stock.name,
                color = ResearchMuted,
                fontSize = 9.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        WatchlistSparkline(history, Modifier.weight(1f).height(25.dp).padding(horizontal = 6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stock.price.takeIf { it.isFinite() && it > 0.0 }?.let(CompanyResearchPresentation::money) ?: "Unavailable",
                color = ResearchText,
                fontSize = 11.2.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            HomeH3ChangeBadge(stock.change.takeIf { stock.changeAvailable && it.isFinite() })
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun HomeH3ChangeBadge(change: Double?) {
    val color = researchChangeColor(change)
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp)) {
        Text(
            change?.let(CompanyResearchPresentation::percent) ?: "-",
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontSize = 9.3.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HomeH3QuickActions(
    openWatchlist: () -> Unit,
    openAlerts: () -> Unit,
    openPractice: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeH3QuickAction("Watchlist", Icons.Default.Bookmark, ResearchGreen, openWatchlist, Modifier.weight(1f))
        HomeH3QuickAction("Alerts", Icons.Default.NotificationsActive, Color(0xFFFFC857), openAlerts, Modifier.weight(1f))
        HomeH3QuickAction("Practice", Icons.Default.AccountBalanceWallet, MaterialTheme.colorScheme.tertiary, openPractice, Modifier.weight(1f))
    }
}

@Composable
private fun HomeH3QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    action: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.height(68.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = action),
        color = ResearchCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(label, color = ResearchText, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun HomeH3NewsRow(story: NewsItem, open: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = open)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                story.title,
                color = ResearchText,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${story.source.ifBlank { "Source unavailable" }} · ${CompanyResearchPresentation.date(story.publishedAt)}",
                color = ResearchMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (story.symbol.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(story.symbol, color = ResearchGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (story.imageUrl.isNotBlank()) {
            AsyncImage(story.imageUrl, null, Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun HomeH3MarketSnapshot(
    breadth: HomeMarketBreadth,
    total: Int,
    sector: HomeSectorPulse?,
    topMover: Stock?,
    openMarket: () -> Unit,
    openMover: (Stock) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ResearchCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeH3SectionHeader("Market snapshot", "See more", openMarket)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HomeH3BreadthStat(breadth.advancing, "rising", ResearchGreen, Modifier.weight(1f))
                HomeH3VerticalDivider()
                HomeH3BreadthStat(breadth.unchanged, "unchanged", ResearchMuted, Modifier.weight(1f))
                HomeH3VerticalDivider()
                HomeH3BreadthStat(breadth.declining, "falling", ResearchRed, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(7.dp))) {
                if (total <= 0) {
                    Box(Modifier.fillMaxSize().background(ResearchBorder))
                } else {
                    if (breadth.advancing > 0) Box(Modifier.weight(breadth.advancing.toFloat()).fillMaxHeight().background(ResearchGreen))
                    if (breadth.unchanged > 0) Box(Modifier.weight(breadth.unchanged.toFloat()).fillMaxHeight().background(Color(0xFFB3C6DA)))
                    if (breadth.declining > 0) Box(Modifier.weight(breadth.declining.toFloat()).fillMaxHeight().background(ResearchRed))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BarChart, null, tint = ResearchGreen, modifier = Modifier.size(29.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Strongest sector", color = ResearchMuted, fontSize = 9.2.sp)
                        Text(
                            sector?.sector?.let(::homeH3SectorName) ?: "Unavailable",
                            color = ResearchText,
                            fontSize = 12.3.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            sector?.let { "${it.memberCount} counters ${CompanyResearchPresentation.percent(it.averageChangePct)}" }
                                ?: "No sector calculation",
                            color = ResearchMuted,
                            fontSize = 9.2.sp,
                            maxLines = 1
                        )
                    }
                }
                Box(Modifier.padding(horizontal = 9.dp).width(1.dp).height(50.dp).background(ResearchBorder))
                Row(
                    Modifier.weight(1f).then(if (topMover != null) Modifier.clickable { openMover(topMover) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.TrendingUp, null, tint = researchChangeColor(topMover?.change), modifier = Modifier.size(29.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Top mover", color = ResearchMuted, fontSize = 9.2.sp)
                        Text(
                            topMover?.name ?: "Unavailable",
                            color = ResearchText,
                            fontSize = 12.3.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            topMover?.let { "${CompanyResearchPresentation.money(it.price)}  ${CompanyResearchPresentation.percent(it.change)}" }
                                ?: "No daily mover",
                            color = researchChangeColor(topMover?.change),
                            fontSize = 9.2.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeH3BreadthStat(value: Int, label: String, color: Color, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(value.toString(), color = color, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(5.dp))
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 9.2.sp)
    }
}

@Composable
private fun HomeH3MoversCard(
    selectedGainers: Boolean,
    movers: List<Stock>,
    quotesAvailable: Boolean,
    select: (Boolean) -> Unit,
    openMarket: () -> Unit,
    openCompany: (Stock) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ResearchCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Market movers", Modifier.weight(1f), color = ResearchText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                HomeH3MoverChip("Gainers", selectedGainers) { select(true) }
                Spacer(Modifier.width(4.dp))
                HomeH3MoverChip("Losers", !selectedGainers) { select(false) }
                TextButton(onClick = openMarket, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text("View all →", color = ResearchGreen, fontSize = 9.2.sp)
                }
            }
            if (movers.isEmpty()) {
                HomeH3Message(
                    if (!quotesAvailable) "Market quotes are unavailable."
                    else "No ${if (selectedGainers) "gainers" else "losers"} in the available daily changes."
                )
            } else {
                movers.forEachIndexed { index, stock ->
                    if (index > 0) HorizontalDivider(color = ResearchBorder.copy(alpha = 0.7f))
                    Row(
                        Modifier.fillMaxWidth().clickable { openCompany(stock) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text((index + 1).toString(), color = ResearchMuted, fontSize = 10.2.sp, modifier = Modifier.width(24.dp))
                        Text(stock.symbol, color = ResearchText, fontSize = 11.7.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(50.dp))
                        Text(
                            stock.name,
                            Modifier.weight(1f),
                            color = ResearchMuted,
                            fontSize = 9.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(CompanyResearchPresentation.money(stock.price), color = ResearchText, fontSize = 10.2.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(7.dp))
                        HomeH3ChangeBadge(stock.change.takeIf { stock.changeAvailable && it.isFinite() })
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeH3MoverChip(label: String, selected: Boolean, click: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = click),
        color = if (selected) ResearchGreen.copy(alpha = 0.08f) else ResearchCard,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, if (selected) ResearchGreen else ResearchBorder)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (selected) ResearchGreen else ResearchMuted,
            fontSize = 9.2.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HomeH3PracticeCard(enabled: Boolean, cash: Double, openPractice: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ResearchCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val stack = maxWidth < 320.dp
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeH3PracticeIdentity(enabled, cash)
                    Button(
                        onClick = openPractice,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResearchGreen, contentColor = Color(0xFF061625))
                    ) {
                        Text(if (enabled) "Open portfolio →" else "Start practising →", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HomeH3PracticeIdentity(enabled, cash, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = openPractice,
                        modifier = Modifier.widthIn(min = 126.dp).height(43.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResearchGreen, contentColor = Color(0xFF061625))
                    ) {
                        Text(if (enabled) "Open portfolio →" else "Start practising →", fontSize = 10.2.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeH3PracticeIdentity(enabled: Boolean, cash: Double, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFB6C9DC).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountBalanceWallet, null, tint = Color(0xFFB6C9DC), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("PRACTICE PORTFOLIO", color = ResearchGreen, fontSize = 9.2.sp, fontWeight = FontWeight.Bold)
            Text("Build confidence with virtual money", color = ResearchText, fontSize = 10.2.sp, maxLines = 1)
            Text(
                if (enabled && cash.isFinite() && cash >= 0) String.format(Locale.US, "KSh %,.0f", cash) else "KSh 1,000,000",
                color = ResearchText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun homeH3SectorName(sector: String): String = when (sector.lowercase(Locale.US)) {
    "banks" -> "Banking"
    "telecommunication", "telecommunications" -> "Telecom"
    "oil & gas", "oil and gas" -> "Energy"
    else -> sector
}
