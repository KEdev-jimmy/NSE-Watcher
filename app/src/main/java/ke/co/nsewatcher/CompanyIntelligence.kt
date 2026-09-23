package ke.co.nsewatcher

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.MovementIntelligenceCache
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CompanyIntelligence(
    s: Stock,
    back: () -> Unit,
    watched: Boolean = false,
    onWatchToggle: (() -> Unit)? = null,
    openPractice: () -> Unit,
    openNews: (NewsItem) -> Unit
) {
    var refresh by remember(s.symbol) { mutableIntStateOf(0) }
    var selectedRange by rememberSaveable(s.symbol) { mutableStateOf("1D") }
    var ranges by remember(s.symbol) { mutableStateOf<Map<String, MyStocksCache.HistoryResult>>(emptyMap()) }
    var loadingRanges by remember(s.symbol) { mutableStateOf(CompanyResearchPresentation.ranges.toSet()) }
    var status by remember(s.symbol) { mutableStateOf(MyStocksCache.MarketStatus()) }
    var intelligence by remember(s.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var fundamentalsLoading by remember(s.symbol) { mutableStateOf(true) }
    var news by remember(s.symbol) { mutableStateOf(emptyList<NewsItem>()) }
    var newsLoading by remember(s.symbol) { mutableStateOf(true) }
    var newsError by remember(s.symbol) { mutableStateOf<String?>(null) }
    var movement by remember(s.symbol) { mutableStateOf(MovementIntelligenceCache.Result()) }
    var movementLoading by remember(s.symbol) { mutableStateOf(false) }
    var analysisRequested by remember(s.symbol) { mutableStateOf(false) }
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
    LaunchedEffect(s.symbol, refresh) {
        newsLoading = true
        try {
            val result = NewsCache.loadCompanyNews(s.symbol)
            news = result.items.distinctBy { it.id }.sortedByDescending { it.publishedAt }
            newsError = result.error
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            newsError = "Company news is temporarily unavailable."
        } finally { newsLoading = false }
    }
    LaunchedEffect(s.symbol, refresh) {
        while (true) {
            status = MyStocksCache.loadMarketStatus()
            delay(60_000L)
        }
    }
    // Each range becomes usable as soon as it arrives; a slow five-year response
    // no longer blocks the session header or one-day chart. No duplicate 1D fetch.
    LaunchedEffect(s.symbol, lastMarketRefreshMs, refresh) {
        loadingRanges = CompanyResearchPresentation.ranges.toSet()
        coroutineScope {
            CompanyResearchPresentation.ranges.forEach { range ->
                launch {
                    try {
                        val result = MyStocksCache.loadHistoryDetails(s.symbol, if (range == "1D") range else range.lowercase())
                        ranges = ranges + (range to result)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        ranges = ranges + (range to MyStocksCache.HistoryResult())
                    } finally { loadingRanges = loadingRanges - range }
                }
            }
        }
    }
    LaunchedEffect(s.symbol, analysisRequested, refresh) {
        if (!analysisRequested) return@LaunchedEffect
        movementLoading = true
        try {
            movement = MovementIntelligenceCache.load(s.symbol)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            movement = MovementIntelligenceCache.Result(error = "Movement evidence is temporarily unavailable.")
        } finally { movementLoading = false }
    }

    val day = ranges["1D"] ?: MyStocksCache.HistoryResult()
    val session = remember(s, day) { CompanyResearchPresentation.session(s, day) }
    val returns = remember(ranges, session.dailyChange) {
        ranges.mapValues { (range, result) ->
            CompanyChartAccuracy.periodReturn(range, result, if (range == "1D") session.dailyChange else null)
        } + ("1D" to session.dailyChange)
    }
    CompanyResearchScreen(
        stock = s, session = session, market = status, intelligence = intelligence,
        fundamentalsLoading = fundamentalsLoading, news = news, newsLoading = newsLoading,
        newsError = newsError, movement = movement, movementLoading = movementLoading,
        onAnalysis = { analysisRequested = true }, watched = watched, onWatchToggle = onWatchToggle,
        back = back, openNews = openNews, openPractice = openPractice,
        onRefresh = { refresh++ },
        refreshing = fundamentalsLoading || newsLoading || loadingRanges.isNotEmpty() || movementLoading,
        selectedRange = selectedRange, onRange = { selectedRange = it },
        chart = ranges[selectedRange] ?: MyStocksCache.HistoryResult(),
        chartLoading = selectedRange in loadingRanges, rangeReturns = returns,
        sessionLoading = "1D" in loadingRanges
    )
}
