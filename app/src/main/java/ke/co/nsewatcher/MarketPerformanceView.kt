package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.MarketHistoryCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketPerformanceView(
    companies: List<Stock>,
    revision: Int,
    now: Instant,
    openCompany: (Stock) -> Unit
) {
    var range by rememberSaveable { mutableStateOf("1Y") }
    var filter by rememberSaveable { mutableStateOf("Gainers") }
    var all by rememberSaveable { mutableStateOf(false) }
    var explain by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var processed by remember { mutableIntStateOf(0) }
    var handledRevision by remember { mutableIntStateOf(0) }
    var histories by remember { mutableStateOf(emptyMap<String, MyStocksCache.HistoryResult>()) }
    val end = now.atZone(CompanyResearchPresentation.zone).toLocalDate()
    val symbols = companies.map { it.symbol }
    LaunchedEffect(range, symbols, revision, end) {
        histories = emptyMap()
        processed = 0
        if (range == "1D" || symbols.isEmpty()) { loading = false; return@LaunchedEffect }
        loading = true
        val forceHistoryRefresh = revision > handledRevision
        try {
            val limiter = Semaphore(4)
            coroutineScope {
                symbols.forEach { symbol -> launch {
                    limiter.withPermit {
                        val result = try {
                            MarketHistoryCache.load(
                                symbol = symbol,
                                period = MarketPresentation.historyPeriod(range),
                                forceRefresh = forceHistoryRefresh
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            MyStocksCache.HistoryResult()
                        }
                        ensureActive()
                        histories = histories + (symbol to result)
                        processed++
                    }
                } }
            }
            if (forceHistoryRefresh) handledRevision = revision
        } finally { loading = false }
    }
    val evaluated = remember(companies, histories, range, end) {
        companies.map { it to MarketPresentation.eligible(it, range, histories[it.symbol] ?: MyStocksCache.HistoryResult(), end) }
    }
    val eligible = evaluated.mapNotNull { it.second.performance }
    val excluded = evaluated.filter { it.second.performance == null }
    val ranked = MarketPresentation.ranked(eligible, filter)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ResearchTitle("Look beyond today") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MarketPresentation.ranges.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            OutlinedButton(onClick = { range = option; all = false }, modifier = Modifier.weight(1f).heightIn(min = 44.dp), contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, if (range == option) ResearchGreen else ResearchBorder),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (range == option) ResearchGreen else ResearchCard, contentColor = if (range == option) ResearchBackground else ResearchMuted)) { Text(option, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        item {
            ResearchCaption(if (range == "1D") "Provider-reported daily changes · Each quote retains its date" else "Requested: ${MarketPresentation.start(range, end)} → $end")
            if (range != "1D") ResearchCaption("Actual observation dates appear below each company.")
        }
        item {
            ResearchPanel {
                if (loading) {
                    ResearchBody("Checking history · $processed of ${companies.size}")
                    LinearProgressIndicator(progress = { if (companies.isEmpty()) 0f else processed.toFloat() / companies.size }, modifier = Modifier.fillMaxWidth(), color = ResearchGreen)
                    ResearchCaption("Rankings appear after the available companies have been checked. You can change the period while this loads.")
                } else {
                    ResearchBody("${eligible.size} of ${companies.size} companies eligible")
                    ResearchCaption("${excluded.size} excluded: missing dates, changes, or incomplete history")
                    TextButton(onClick = { explain = true }) { Text("Inspect coverage →", color = ResearchGreen) }
                }
            }
        }
        item { MarketChoiceRow(listOf("Gainers", "Losers", "All"), filter) { filter = it; all = false } }
        if (!loading) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { ResearchCaption("Company"); ResearchCaption("$range price change") }
                if (ranked.isEmpty()) ResearchCaption(if (companies.isEmpty()) "Company data is unavailable. Use the refresh button to retry." else "No eligible ${filter.lowercase()} for this period. Open coverage to see why.")
            }
            items(if (all) ranked else ranked.take(5), key = { it.stock.symbol }) { row ->
                Surface(color = ResearchCard, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ResearchBorder)) {
                    Box(Modifier.padding(horizontal = 10.dp)) {
                        MarketStockRow(row.stock, CompanyResearchPresentation.percent(row.change), row.change, openCompany,
                            if (range == "1D") "Observed: ${CompanyResearchPresentation.date(row.last)}" else "${CompanyResearchPresentation.date(row.first)} → ${CompanyResearchPresentation.date(row.last)}")
                    }
                }
            }
            if (!all && ranked.size > 5) item { TextButton(onClick = { all = true }) { Text("See all ${ranked.size} companies →", color = ResearchGreen) } }
        }
        item {
            ResearchPanel {
                ResearchTitle("Price change is not total return")
                ResearchBody("Dividends are excluded. The provider does not supply an adjustment-status flag here, so splits and other corporate actions may affect historical comparisons.")
                ResearchCaption("Open a company to inspect dated observations. These rankings do not measure investment quality.")
                TextButton(onClick = { explain = true }) { Text("Why is a period unavailable? ⓘ", color = ResearchGreen) }
            }
        }
    }
    if (explain) ModalBottomSheet(onDismissRequest = { explain = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                ResearchTitle("$range coverage and methodology")
                ResearchBody(if (range == "1D") "Daily changes come directly from the provider, with a valid positive price and a quote dated within the last four calendar days. This allows for non-trading days; inspect each observation date." else "Changes use the first and last valid dated closing prices returned for the requested window. No current quote is substituted for missing historical data.")
                if (range != "1D") {
                    ResearchCaption("Start and end coverage allow ${MarketPresentation.endpointTolerance(range)} calendar days for source sampling and non-trading days. Large interior gaps are excluded. Actual endpoints are displayed; different companies may have different dates.")
                    if (range == "YTD") ResearchCaption("YTD requires an observation on or before the previous year-end, no more than 10 days earlier. Missing year-end baselines are excluded.")
                }
                ResearchCaption("A newly listed company may not have enough history. An unavailable period is not a zero return. Use Refresh to retry; the provider may still have limited coverage.")
                if (loading) ResearchCaption("Checking $processed of ${companies.size}; exclusions below are not final yet.")
                else ResearchTitle("Excluded companies (${excluded.size})")
            }
            if (!loading) items(excluded, key = { it.first.symbol }) { (stock, result) -> ResearchPanel {
                ResearchBody("${stock.symbol} · ${stock.name}")
                ResearchCaption(result.reason)
                TextButton(onClick = { explain = false; openCompany(stock) }) { Text("Research company →", color = ResearchGreen) }
            } }
        }
    }
}
