package ke.co.nsewatcher

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.SavedNewsStore
import ke.co.nsewatcher.data.WatchlistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal val PremiumNewsTabLabels = listOf("For You", "Market", "Company", "Results", "Dividends")

internal fun premiumIsDividend(item: NewsItem): Boolean =
    item.category.equals("Dividends", true) || item.dividendAmount.isNotBlank() ||
        item.exDate.isNotBlank() || item.paymentDate.isNotBlank() ||
        Regex("\\bdividends?\\b", RegexOption.IGNORE_CASE).containsMatchIn(item.title)

internal fun premiumIsResults(item: NewsItem): Boolean =
    item.category.equals("Results", true) || item.category.equals("Financial Results", true) ||
        Regex("\\b(earnings|financial results|annual results|interim results|half.year results|full.year results|quarterly results|q[1-4].{0,8}results)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(item.title + " " + item.summary)

internal fun premiumIsAgm(item: NewsItem): Boolean =
    Regex("\\b(agm|annual general meeting)\\b", RegexOption.IGNORE_CASE)
        .containsMatchIn(item.title + " " + item.summary)

internal fun premiumIsCorporateAction(item: NewsItem): Boolean =
    item.category.equals("Corporate Actions", true) ||
        Regex("\\b(corporate action|book closure|rights issue|bonus issue|share split|takeover|merger|acquisition)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(item.title + " " + item.summary)

internal fun premiumIsCompany(item: NewsItem): Boolean =
    item.symbol.isNotBlank() ||
        (item.companyName.isNotBlank() && !item.companyName.equals("MyStocks Africa", true))

internal fun premiumIsMarketWide(item: NewsItem): Boolean {
    if (premiumIsDividend(item) || premiumIsResults(item) || premiumIsAgm(item) || premiumIsCorporateAction(item)) return false
    val text = (item.title + " " + item.summary).lowercase(Locale.US)
    val explicitMarket = listOf(
        "nse", "nairobi securities exchange", "stock market", "share market", "equities",
        "listed shares", "market turnover", "market breadth", "capital markets authority",
        "cma kenya", "kenya shilling", "central bank of kenya", "cbk"
    ).any { text.contains(it) }
    return item.category.equals("Market", true) ||
        item.category.equals("Analysis", true) ||
        (!premiumIsCompany(item) && item.intelligenceRelevance.equals("market", true)) ||
        explicitMarket
}

internal fun premiumIsStrictRelevant(item: NewsItem): Boolean {
    val relevance = item.intelligenceRelevance.lowercase(Locale.US)
    if (relevance == "general") return false
    if (relevance == "market" || relevance == "company") return true
    if (premiumIsCompany(item) || premiumIsDividend(item) || premiumIsResults(item) ||
        premiumIsAgm(item) || premiumIsCorporateAction(item)
    ) return true
    val text = (item.title + " " + item.summary).lowercase(Locale.US)
    return listOf(
        "nse", "nairobi securities exchange", "listed shares", "listed company",
        "capital markets authority", "share price", "stock market", "equities"
    ).any { text.contains(it) }
}

private fun premiumQuarter(item: NewsItem): String? {
    val match = Regex("\\bQ([1-4])\\s*[- ]?(20\\d{2})\\b", RegexOption.IGNORE_CASE)
        .find(item.title + " " + item.summary)
    return match?.let { "Q${it.groupValues[1]} ${it.groupValues[2]}" }
}

private fun premiumStoryScore(item: NewsItem, watched: Set<String>): Int {
    var score = 0
    if (item.symbol.uppercase(Locale.US) in watched) score += 30
    if (premiumIsResults(item)) score += 12
    if (premiumIsDividend(item) || premiumIsAgm(item) || premiumIsCorporateAction(item)) score += 10
    if (item.intelligenceRelevance.equals("market", true)) score += 8
    if (premiumIsCompany(item)) score += 6
    if (item.freshnessMode.contains("CURRENT", true) || item.freshnessMode.contains("WITHIN", true)) score += 3
    return score
}

internal fun premiumNewsSearchMatch(item: NewsItem, query: String): Boolean {
    val term = query.trim()
    if (term.isBlank()) return true
    return listOf(item.title, item.summary, item.companyName, item.symbol, item.source, item.category)
        .any { it.contains(term, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PremiumNewsDashboard(
    newsFeed: List<NewsItem>,
    catalog: List<Stock>,
    quotes: List<Stock>,
    onNewsLoaded: (List<NewsItem>) -> Unit,
    openAlerts: () -> Unit,
    open: (NewsItem) -> Unit
) {
    val context = LocalContext.current
    val savedStore = remember { SavedNewsStore(context) }
    val watchlistStore = remember { WatchlistStore(context) }

    var savedError by remember { mutableStateOf(false) }
    val savedFlow = remember { savedStore.articles.catch { savedError = true } }
    val saved by savedFlow.collectAsState<List<NewsItem>, List<NewsItem>?>(null)
    val watchedSymbols by produceState(initialValue = emptyList<String>(), watchlistStore) {
        try {
            watchlistStore.symbols.collect { value = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = emptyList()
        }
    }

    var loading by remember { mutableStateOf(newsFeed.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var requestActive by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf("For You") }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var companyFilter by rememberSaveable { mutableStateOf("") }
    var resultPeriod by rememberSaveable { mutableStateOf("All") }
    var dividendFilter by rememberSaveable { mutableStateOf("All") }
    var showSaved by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()

    fun refreshNews(force: Boolean) {
        if (requestActive) return
        requestActive = true
        if (force) refreshing = true else loading = true
        scope.launch {
            try {
                val result = MarketData.newsFeed(forceRefresh = force)
                if (result.error == null) {
                    onNewsLoaded(
                        result.items.filter(::premiumIsStrictRelevant)
                            .distinctBy { it.id }
                            .sortedByDescending { it.publishedAt }
                    )
                }
                error = result.error
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Unable to refresh NSE news."
            } finally {
                loading = false
                refreshing = false
                requestActive = false
            }
        }
    }

    fun selectTab(value: String) {
        tab = value
        query = ""
        searching = false
        companyFilter = ""
        resultPeriod = "All"
        dividendFilter = "All"
        scope.launch { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(Unit) {
        if (newsFeed.isEmpty()) refreshNews(false) else loading = false
    }

    val relevantFeed = remember(newsFeed) {
        newsFeed.filter(::premiumIsStrictRelevant).distinctBy { it.id }.sortedByDescending { it.publishedAt }
    }
    val watched = remember(watchedSymbols) {
        watchedSymbols.map { it.trim().uppercase(Locale.US) }.filter { it.isNotBlank() }.toSet()
    }
    val companies = remember(catalog, quotes) { CompaniesPresentation.companies(catalog, quotes) }
    val companyBySymbol = remember(companies) { companies.associateBy { it.symbol.uppercase(Locale.US) } }
    val watchedCompanies = remember(watched, companyBySymbol) { watched.mapNotNull(companyBySymbol::get).take(8) }

    val rankedForYou = remember(relevantFeed, watched) {
        relevantFeed.sortedWith(
            compareByDescending<NewsItem> { premiumStoryScore(it, watched) }
                .thenByDescending { it.publishedAt }
        )
    }
    val marketStories = remember(relevantFeed) { relevantFeed.filter(::premiumIsMarketWide) }
    val companyStories = remember(relevantFeed, companyFilter, query) {
        val term = query.trim()
        relevantFeed.filter(::premiumIsCompany).filter { item ->
            (companyFilter.isBlank() || item.symbol.equals(companyFilter, true)) &&
                (term.isBlank() || listOf(item.title, item.summary, item.companyName, item.symbol, item.source)
                    .any { it.contains(term, true) })
        }
    }
    val resultStories = remember(relevantFeed, resultPeriod) {
        relevantFeed.filter(::premiumIsResults).filter { item ->
            resultPeriod == "All" || premiumQuarter(item) == resultPeriod
        }
    }
    val resultPeriods = remember(relevantFeed) {
        listOf("All") + relevantFeed.filter(::premiumIsResults).mapNotNull(::premiumQuarter).distinct().take(4)
    }
    val dividendStories = remember(relevantFeed, dividendFilter) {
        relevantFeed.filter {
            premiumIsDividend(it) || premiumIsAgm(it) || premiumIsCorporateAction(it)
        }.filter { item ->
            when (dividendFilter) {
                "Dividends" -> premiumIsDividend(item)
                "AGMs" -> premiumIsAgm(item)
                "Corporate Actions" -> premiumIsCorporateAction(item) &&
                    !premiumIsDividend(item) && !premiumIsAgm(item)
                else -> true
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshNews(true) },
        state = refreshState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PremiumNewsHeader(
                    searching = searching,
                    onSearch = {
                        searching = !searching
                        if (!searching) query = ""
                    },
                    openAlerts = openAlerts
                )
            }

            if (searching) {
                item {
                    PremiumNewsSearch(
                        value = query,
                        onValue = { query = it },
                        placeholder = if (tab == "Company") "Search company news..." else "Search NSE news..."
                    )
                }
            }

            item { PremiumNewsTabs(selected = tab, onSelected = ::selectTab) }

            if (error != null && relevantFeed.isNotEmpty()) {
                item {
                    PremiumNewsMessage(
                        "Refresh unavailable",
                        "Showing the last successfully loaded NSE-relevant stories.",
                        "Retry"
                    ) { refreshNews(true) }
                }
            }

            when {
                loading && relevantFeed.isEmpty() -> item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Loading NSE news...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }

                relevantFeed.isEmpty() -> item {
                    PremiumNewsMessage(
                        if (error != null) "NSE news temporarily unavailable" else "No NSE-relevant stories yet",
                        if (error != null) "The news service could not be refreshed. Try again."
                        else "Stories will appear here when they can be tied to the NSE, a listed company, results, dividends or a market event.",
                        "Refresh"
                    ) { refreshNews(true) }
                }

                tab == "For You" -> premiumForYouItems(
                    ranked = rankedForYou.filter { query.isBlank() || premiumNewsSearchMatch(it, query) },
                    savedCount = saved?.size ?: 0,
                    watchedCount = watched.size,
                    openSaved = { showSaved = true },
                    open = open
                )

                tab == "Market" -> premiumMarketNewsItems(
                    stories = marketStories.filter { query.isBlank() || premiumNewsSearchMatch(it, query) },
                    open = open
                )

                tab == "Company" -> premiumCompanyNewsItems(
                    stories = companyStories,
                    watchedCompanies = watchedCompanies,
                    companyFilter = companyFilter,
                    setCompanyFilter = { companyFilter = it },
                    open = open
                )

                tab == "Results" -> premiumResultsNewsItems(
                    stories = resultStories.filter { query.isBlank() || premiumNewsSearchMatch(it, query) },
                    periods = resultPeriods,
                    selectedPeriod = resultPeriod,
                    onPeriod = { resultPeriod = it },
                    open = open
                )

                else -> premiumDividendNewsItems(
                    stories = dividendStories.filter { query.isBlank() || premiumNewsSearchMatch(it, query) },
                    selected = dividendFilter,
                    onFilter = { dividendFilter = it },
                    open = open
                )
            }

            item {
                Text(
                    "Only stories tied to the NSE, listed companies or market-relevant corporate events are shown here. " +
                        "Dates and sources remain as supplied; confirm material announcements with the issuer or exchange.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }

    if (showSaved) {
        ModalBottomSheet(
            onDismissRequest = { showSaved = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(0.82f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Saved news",
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 21.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text("${saved?.size ?: 0}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                when {
                    savedError -> item {
                        Text("Saved articles could not be read.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    saved == null -> item { ResearchLoading("Loading saved articles...") }
                    saved!!.isEmpty() -> item {
                        PremiumNewsMessage(
                            "No saved stories yet",
                            "Bookmark an article from the reader to keep it here.",
                            "Close"
                        ) { showSaved = false }
                    }
                    else -> items(saved!!, key = { "saved-${it.id}" }) { item ->
                        PremiumNewsListRow(item) {
                            showSaved = false
                            open(item)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumForYouItems(
    ranked: List<NewsItem>,
    savedCount: Int,
    watchedCount: Int,
    openSaved: () -> Unit,
    open: (NewsItem) -> Unit
) {
    if (ranked.isEmpty()) {
        item { PremiumNewsMessage("No matching stories", "Try a different search term.", "Okay") {} }
        return
    }
    item { PremiumNewsFeaturedCard(ranked.first(), open) }
    val updates = ranked.drop(1).take(4)
    if (updates.isNotEmpty()) {
        item {
            PremiumNewsSectionHeading(
                title = "Today's key updates",
                action = if (savedCount > 0) "Saved $savedCount" else null,
                onAction = openSaved
            )
        }
        items(updates, key = { "foryou-${it.id}" }) { PremiumNewsListRow(it, open) }
    }
    item {
        PremiumNewsExplainer(
            icon = Icons.Default.Lightbulb,
            title = "What this means",
            body = premiumForYouExplanation(ranked, watchedCount)
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumMarketNewsItems(
    stories: List<NewsItem>,
    open: (NewsItem) -> Unit
) {
    item { PremiumMarketNewsPulse(stories) }
    if (stories.isEmpty()) {
        item {
            PremiumNewsMessage(
                "No market-wide stories in the current feed",
                "Company-specific stories may still be available in Company, Results or Dividends.",
                "Okay"
            ) {}
        }
        return
    }
    item { PremiumNewsSectionHeading("Latest market news", null) {} }
    items(stories, key = { "market-${it.id}" }) { PremiumNewsListRow(it, open) }
    item {
        PremiumNewsExplainer(
            icon = Icons.Default.School,
            title = "How to use market news",
            body = "Market-wide stories can show what deserves investigation, but they do not prove why an individual share moved. Open the company and compare dated evidence before drawing a conclusion."
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumCompanyNewsItems(
    stories: List<NewsItem>,
    watchedCompanies: List<Stock>,
    companyFilter: String,
    setCompanyFilter: (String) -> Unit,
    open: (NewsItem) -> Unit
) {
    if (watchedCompanies.isNotEmpty()) {
        item {
            PremiumNewsSectionHeading(
                title = "Followed companies",
                action = if (companyFilter.isNotBlank()) "Show all" else null,
                onAction = { setCompanyFilter("") }
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items(watchedCompanies, key = { it.symbol }) { stock ->
                    PremiumCompanyNewsChip(
                        stock = stock,
                        selected = companyFilter.equals(stock.symbol, true),
                        onClick = {
                            setCompanyFilter(
                                if (companyFilter.equals(stock.symbol, true)) "" else stock.symbol
                            )
                        }
                    )
                }
            }
        }
    }
    item {
        PremiumNewsSectionHeading(
            if (companyFilter.isBlank()) "Recent company news" else "$companyFilter news",
            null
        ) {}
    }
    if (stories.isEmpty()) {
        item {
            PremiumNewsMessage(
                "No matching company stories",
                if (companyFilter.isBlank()) "No listed-company stories matched the current feed."
                else "No current stories matched $companyFilter.",
                "Show all"
            ) { setCompanyFilter("") }
        }
    } else {
        items(stories, key = { "company-${it.id}" }) { PremiumNewsListRow(it, open) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumResultsNewsItems(
    stories: List<NewsItem>,
    periods: List<String>,
    selectedPeriod: String,
    onPeriod: (String) -> Unit,
    open: (NewsItem) -> Unit
) {
    item {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            periods.forEach { period ->
                PremiumNewsFilterChip(period, selectedPeriod == period) { onPeriod(period) }
            }
        }
    }
    if (stories.isEmpty()) {
        item {
            PremiumNewsMessage(
                "No results stories found",
                "No financial-results stories matched this selection.",
                "Show all"
            ) { onPeriod("All") }
        }
    } else {
        items(stories, key = { "results-${it.id}" }) { PremiumResultRow(it, open) }
    }
    item {
        PremiumNewsExplainer(
            icon = Icons.Default.Info,
            title = "How to read results",
            body = "Start with revenue, profit and earnings-per-share changes, then check whether one-off items or accounting changes affected the comparison. A stronger result does not automatically mean the share price will rise."
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumDividendNewsItems(
    stories: List<NewsItem>,
    selected: String,
    onFilter: (String) -> Unit,
    open: (NewsItem) -> Unit
) {
    item {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Dividends", "AGMs", "Corporate Actions").forEach { label ->
                PremiumNewsFilterChip(label, selected == label) { onFilter(label) }
            }
        }
    }
    if (stories.isEmpty()) {
        item {
            PremiumNewsMessage(
                "No matching corporate events",
                "No dividend, AGM or corporate-action stories matched this filter.",
                "Show all"
            ) { onFilter("All") }
        }
    } else {
        items(stories, key = { "event-${it.id}" }) { PremiumCorporateEventRow(it, open) }
    }
    item {
        PremiumNewsExplainer(
            icon = Icons.Default.MenuBook,
            title = "Why this matters",
            body = "Dividends, book closures, AGMs and other corporate actions can affect shareholder rights, cash payments and how a share trades. Always confirm important dates in the original issuer or exchange notice."
        )
    }
}

private fun premiumForYouExplanation(ranked: List<NewsItem>, watchedCount: Int): String {
    val results = ranked.count(::premiumIsResults)
    val corporate = ranked.count {
        premiumIsDividend(it) || premiumIsAgm(it) || premiumIsCorporateAction(it)
    }
    return when {
        results > 0 && corporate > 0 ->
            "The current relevant feed includes both company results and shareholder events. Results explain business performance; dividend and corporate-action notices may contain dates or rights that shareholders need to verify."
        results > 0 ->
            "Several current stories are financial results. Compare the reported period with the prior period and open Company Intelligence for the wider trend."
        corporate > 0 ->
            "Several current stories involve dividends, AGMs or corporate actions. Check the original notice for dates and eligibility before relying on a headline."
        watchedCount > 0 ->
            "Stories linked to companies you follow are prioritised here, followed by wider NSE-relevant updates."
        else ->
            "This feed prioritises stories that can be tied to the NSE, a listed company or a market-relevant event rather than general news."
    }
}
