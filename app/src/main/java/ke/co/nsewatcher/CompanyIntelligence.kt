package ke.co.nsewatcher

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import ke.co.nsewatcher.data.AnalystCache
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.CompanyIntelligenceEngine
import ke.co.nsewatcher.data.MovementIntelligenceCache
import ke.co.nsewatcher.data.MarketHistoryCache
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

internal object CompanyHistoryLoadingPolicy {
    private val overviewRanges = setOf("1D", "1M", "3M", "1Y", "3Y")

    fun initial(defaultRange: String): Set<String> =
        (overviewRanges + defaultRange)
            .filter { it in CompanyResearchPresentation.ranges }
            .toSet()

    fun request(current: Set<String>, range: String): Set<String> =
        if (range in CompanyResearchPresentation.ranges) current + range else current
}

@Composable
fun CompanyIntelligence(
    s: Stock,
    back: () -> Unit,
    watched: Boolean = false,
    onWatchToggle: (() -> Unit)? = null,
    marketStocks: List<Stock>,
    sharedNews: List<NewsItem>,
    marketStatus: MyStocksCache.MarketStatus,
    onNewsLoaded: (List<NewsItem>) -> Unit,
    onMarketStatusLoaded: (MyStocksCache.MarketStatus) -> Unit,
    openPractice: () -> Unit,
    openNews: (NewsItem) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("nse_watcher_preferences", android.content.Context.MODE_PRIVATE) }
    val configuredDefaultRange = preferences.getString("chart_default_range", "1D")
        ?.takeIf { it in CompanyResearchPresentation.ranges } ?: "1D"
    val showChartGrid = preferences.getBoolean("chart_show_grid", true)
    var refresh by remember(s.symbol) { mutableIntStateOf(0) }
    var selectedRange by rememberSaveable(s.symbol) { mutableStateOf(configuredDefaultRange) }
    var ranges by remember(s.symbol) { mutableStateOf<Map<String, MyStocksCache.HistoryResult>>(emptyMap()) }
    var requestedRanges by remember(s.symbol) {
        mutableStateOf(CompanyHistoryLoadingPolicy.initial(configuredDefaultRange))
    }
    var loadingRanges by remember(s.symbol) { mutableStateOf(emptySet<String>()) }
    var intelligence by remember(s.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var fundamentalsLoading by remember(s.symbol) { mutableStateOf(true) }
    var newsLoading by remember(s.symbol) { mutableStateOf(sharedNews.isEmpty()) }
    var newsError by remember(s.symbol) { mutableStateOf<String?>(null) }
    var movement by remember(s.symbol) { mutableStateOf(MovementIntelligenceCache.Result()) }
    var movementLoading by remember(s.symbol) { mutableStateOf(false) }
    var analyst by remember(s.symbol) { mutableStateOf(AnalystCache.Result()) }
    var analystLoading by remember(s.symbol) { mutableStateOf(false) }
    var movementRequested by remember(s.symbol) { mutableStateOf(false) }
    var analystRequested by remember(s.symbol) { mutableStateOf(false) }
    val lastMarketRefreshMs = MarketRefreshController.state.value.lastSuccessfulRefreshMs

    LaunchedEffect(s.symbol, refresh) {
        fundamentalsLoading = true
        try {
            intelligence = CompanyIntelligenceCache.load(s.symbol)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            intelligence = CompanyIntelligenceCache.Result(error = "Company information is temporarily unavailable.")
        } finally { fundamentalsLoading = false }
    }
    val news = remember(s, sharedNews) { CompanySharedData.companyNews(s, sharedNews) }

    // The first company render consumes the app-owned news feed. If startup did
    // not manage to load that feed, recover it here once and publish it back to
    // the shared app state instead of creating a private company-news cache.
    LaunchedEffect(s.symbol, sharedNews.isEmpty()) {
        if (sharedNews.isNotEmpty()) {
            newsLoading = false
            return@LaunchedEffect
        }
        newsLoading = true
        try {
            val result = MarketData.newsFeed()
            newsError = result.error
            if (result.error == null) onNewsLoaded(result.items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            newsError = "Company news is temporarily unavailable."
        } finally {
            newsLoading = false
        }
    }

    // A manual Company Intelligence refresh updates the same news/status values
    // used elsewhere in the app. This keeps Home, News and Company Intelligence
    // on one observation instead of allowing screen-local copies to drift.
    LaunchedEffect(refresh) {
        if (refresh == 0) return@LaunchedEffect
        newsLoading = true
        try {
            val result = MarketData.newsFeed(forceRefresh = true)
            newsError = result.error
            if (result.error == null) onNewsLoaded(result.items)

            val refreshedStatus = MarketData.status()
            if (refreshedStatus.isKnown || !marketStatus.isKnown) {
                onMarketStatusLoaded(refreshedStatus)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            newsError = "Shared market data could not be refreshed."
        } finally {
            newsLoading = false
        }
    }
    // History is demand-driven. Overview preloads only the ranges it visibly
    // uses; other chart periods are requested when the user selects them.
    // MarketHistoryCache still handles freshness, in-flight deduplication and TTL.
    CompanyResearchPresentation.ranges.forEach { range ->
        val requested = range in requestedRanges
        LaunchedEffect(s.symbol, range, requested, lastMarketRefreshMs, refresh) {
            if (!requested) return@LaunchedEffect
            loadingRanges = loadingRanges + range
            try {
                val result = MarketHistoryCache.load(
                    symbol = s.symbol,
                    period = range,
                    forceRefresh = refresh > 0
                )
                ranges = ranges + (range to result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ranges = ranges + (range to MyStocksCache.HistoryResult())
            } finally {
                loadingRanges = loadingRanges - range
            }
        }
    }
    LaunchedEffect(s.symbol, movementRequested, refresh) {
        if (!movementRequested) return@LaunchedEffect
        movementLoading = true
        try {
            movement = MovementIntelligenceCache.load(s.symbol)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            movement = MovementIntelligenceCache.Result(error = "Movement evidence is temporarily unavailable.")
        } finally { movementLoading = false }
    }
    LaunchedEffect(s.symbol, analystRequested, refresh) {
        if (!analystRequested) return@LaunchedEffect
        analystLoading = true
        try {
            analyst = AnalystCache.ask(
                s.symbol,
                "Using only the supplied evidence, explain what changed, what may matter, and what remains uncertain. Do not give investment instructions."
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            analyst = AnalystCache.Result(error = "AI explanation is temporarily unavailable.")
        } finally { analystLoading = false }
    }

    val day = ranges["1D"] ?: MyStocksCache.HistoryResult()
    val session = remember(s, day) { CompanyResearchPresentation.session(s, day) }
    val returns = remember(ranges, session.dailyChange) {
        ranges.mapValues { (range, result) ->
            CompanyChartAccuracy.periodReturn(range, result, if (range == "1D") session.dailyChange else null)
        } + ("1D" to session.dailyChange)
    }
    val deterministic = remember(s, intelligence, ranges["1M"], news) {
        CompanyIntelligenceEngine.build(
            stock = s,
            source = intelligence,
            priceHistory = ranges["1M"]?.prices.orEmpty(),
            news = news
        )
    }
    val movementContext = remember(s, marketStocks) {
        CompanyAnalysisPresentation.movementContext(s, marketStocks)
    }
    CompanyResearchScreen(
        stock = s, session = session, market = marketStatus, intelligence = intelligence,
        fundamentalsLoading = fundamentalsLoading, news = news, newsLoading = newsLoading,
        newsError = newsError, movement = movement, movementLoading = movementLoading,
        deterministic = deterministic, movementContext = movementContext,
        analyst = analyst, analystLoading = analystLoading,
        onAnalysis = { analysisRequested = true }, watched = watched, onWatchToggle = onWatchToggle,
        back = back, openNews = openNews, openPractice = openPractice,
        onRefresh = { refresh++ },
        refreshing = fundamentalsLoading || newsLoading || loadingRanges.isNotEmpty() || movementLoading || analystLoading,
        selectedRange = selectedRange, onRange = { selectedRange = it },
        chart = ranges[selectedRange] ?: MyStocksCache.HistoryResult(),
        chartLoading = selectedRange in loadingRanges,
        oneYearChart = ranges["1Y"] ?: MyStocksCache.HistoryResult(),
        rangeReturns = returns,
        sessionLoading = "1D" in loadingRanges,
        showChartGrid = showChartGrid
    )
}
