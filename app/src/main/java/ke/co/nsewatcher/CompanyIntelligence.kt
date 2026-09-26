package ke.co.nsewatcher

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import ke.co.nsewatcher.data.AnalystCache
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.CompanyIntelligenceEngine
import ke.co.nsewatcher.data.MovementIntelligenceCache
import ke.co.nsewatcher.data.MarketHistoryCache
import ke.co.nsewatcher.data.TechnicalHistoryCache
import ke.co.nsewatcher.data.TechnicalStrengths
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import ke.co.nsewatcher.domain.TechnicalStrengthResult

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
    var analystRequestRevision by remember(s.symbol) { mutableIntStateOf(0) }
    var technicalHistory by remember(s.symbol) { mutableStateOf(MyStocksCache.HistoryResult()) }
    var technicalStrength by remember(s.symbol) { mutableStateOf<TechnicalStrengthResult?>(null) }
    var technicalLoading by remember(s.symbol) { mutableStateOf(true) }
    var technicalError by remember(s.symbol) { mutableStateOf<String?>(null) }
    val lastMarketRefreshMs = MarketRefreshController.state.value.lastSuccessfulRefreshMs

    LaunchedEffect(s.symbol, refresh) {
        fundamentalsLoading = true
        try {
            intelligence = CompanyIntelligenceCache.load(
                symbol = s.symbol,
                forceRefresh = refresh > 0
            )
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
            coroutineScope {
                launch {
                    try {
                        val result = MarketData.newsFeed(forceRefresh = true)
                        newsError = result.error
                        if (result.error == null) onNewsLoaded(result.items)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        newsError = "Company news could not be refreshed."
                    }
                }
                launch {
                    try {
                        val refreshedStatus = MarketData.status()
                        if (refreshedStatus.isKnown) {
                            onMarketStatusLoaded(refreshedStatus)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the last known shared market status. Company research
                        // and news refresh independently from this status check.
                    }
                }
            }
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
            movement = MovementIntelligenceCache.load(
                symbol = s.symbol,
                forceRefresh = refresh > 0
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            movement = MovementIntelligenceCache.Result(error = "Movement evidence is temporarily unavailable.")
        } finally { movementLoading = false }
    }
    LaunchedEffect(s.symbol, analystRequestRevision) {
        if (analystRequestRevision == 0) return@LaunchedEffect
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
    LaunchedEffect(s.symbol, lastMarketRefreshMs, refresh) {
        technicalLoading = true
        technicalError = null
        try {
            val history = TechnicalHistoryCache.load(
                symbol = s.symbol,
                lookbackDays = 400,
                forceRefresh = refresh > 0
            )
            technicalHistory = history
            if (history.points.isEmpty()) {
                technicalStrength = null
                technicalError = "Verified daily technical history is temporarily unavailable."
            } else {
                technicalStrength = TechnicalStrengths.calculate(history)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            technicalHistory = MyStocksCache.HistoryResult()
            technicalStrength = null
            technicalError = "Technical strength could not be calculated right now."
        } finally {
            technicalLoading = false
        }
    }

    val day = ranges["1D"] ?: MyStocksCache.HistoryResult()
    val session = remember(s, day) { CompanyResearchPresentation.session(s, day) }
    val coverageDate = LocalDate.now(CompanyResearchPresentation.zone)
    val returns = remember(ranges, session.dailyChange, coverageDate) {
        ranges.mapValues { (range, result) ->
            CompanyChartAccuracy.periodReturn(
                range,
                result,
                if (range == "1D") session.dailyChange else null,
                coverageDate
            )
        } + ("1D" to session.dailyChange)
    }

    val oneMonthResult = ranges["1M"] ?: MyStocksCache.HistoryResult()
    val oneMonthCoverage = remember(oneMonthResult, coverageDate) {
        MarketPresentation.historicalCoverage("1M", oneMonthResult, coverageDate)
    }
    val oneMonthPrices = remember(oneMonthResult, oneMonthCoverage, coverageDate) {
        if (oneMonthCoverage.change == null) {
            emptyList()
        } else {
            val start = MarketPresentation.start("1M", coverageDate)
            WatchlistPresentation.trend(oneMonthResult.points)
                .filter { point ->
                    val date = MarketPresentation.date(point.date)
                    date != null && !date.isBefore(start) && !date.isAfter(coverageDate)
                }
                .map { it.close }
        }
    }

    val oneYearResult = ranges["1Y"] ?: MyStocksCache.HistoryResult()
    val oneYearCoverage = remember(oneYearResult, coverageDate) {
        MarketPresentation.historicalCoverage("1Y", oneYearResult, coverageDate)
    }
    val validatedOneYear = remember(oneYearResult, oneYearCoverage) {
        if (oneYearCoverage.change != null) oneYearResult else MyStocksCache.HistoryResult()
    }

    val deterministic = remember(s, intelligence, oneMonthPrices, news) {
        CompanyIntelligenceEngine.build(
            stock = s,
            source = intelligence,
            priceHistory = oneMonthPrices,
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
        analystRequested = analystRequestRevision > 0,
        technicalHistory = technicalHistory,
        technicalStrength = technicalStrength,
        technicalLoading = technicalLoading,
        technicalError = technicalError,
        onAnalysis = { movementRequested = true },
        onAiExplain = { analystRequestRevision++ },
        watched = watched, onWatchToggle = onWatchToggle,
        back = back, openNews = openNews, openPractice = openPractice,
        onRefresh = {
            analyst = AnalystCache.Result()
            analystRequestRevision = 0
            refresh++
        },
        refreshing = fundamentalsLoading || newsLoading || loadingRanges.isNotEmpty() || movementLoading || analystLoading || technicalLoading,
        selectedRange = selectedRange, onRange = { range ->
            selectedRange = range
            requestedRanges = CompanyHistoryLoadingPolicy.request(requestedRanges, range)
        },
        chart = ranges[selectedRange] ?: MyStocksCache.HistoryResult(),
        chartLoading = selectedRange in loadingRanges,
        oneYearChart = validatedOneYear,
        rangeReturns = returns,
        sessionLoading = "1D" in loadingRanges,
        showChartGrid = showChartGrid
    )
}
