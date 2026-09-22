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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.MarketObservationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketDashboard(stockFeed: List<Stock>, catalog: List<Stock>, initialStatus: MyStocksCache.MarketStatus,
    openCompany: (Stock) -> Unit, openCompanies: (String) -> Unit,
    onQuotesLoaded: (List<Stock>) -> Unit, onCatalogLoaded: (List<Stock>) -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Overview") }
    var mover by rememberSaveable { mutableStateOf("Gainers") }
    var status by remember { mutableStateOf(initialStatus) }
    var indices by remember { mutableStateOf(emptyList<MyStocksCache.MarketIndex>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(Instant.now()) }
    val historyCache = remember { mutableMapOf<String, Pair<Long, MyStocksCache.HistoryResult>>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val observationStore = remember { MarketObservationStore(context) }
    var observationError by remember { mutableStateOf(false) }
    val observationsFlow = remember { observationStore.observations.catch { observationError = true } }
    val observations by observationsFlow.collectAsState(initial = emptyList())
    val companies = remember(catalog, stockFeed) { CompaniesPresentation.companies(catalog, stockFeed) }
    val breadth = remember(companies) { MarketPresentation.breadth(companies) }
    val sectors = remember(companies) { MarketPresentation.sectors(companies) }
    val volumeStocks = companies.filter { it.volumeAvailable && it.volume >= 0 }
    val volume = volumeStocks.sumOf { it.volume.toDouble() }
    val latest = companies.filter(MarketPresentation::validChange).mapNotNull { CompanyResearchPresentation.timestamp(it.observedAt) }.maxOrNull()
    val sources = companies.map { it.source.trim() }.filter(String::isNotBlank).distinct().joinToString().ifBlank { "Source unavailable" }
    LaunchedEffect(initialStatus) { status = initialStatus }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(60_000L) } }
    LaunchedEffect(companies) {
        if (breadth.covered > 0 && latest != null && !latest.isAfter(Instant.now())) {
            try { observationStore.record(MarketSavedObservation(latest.toString(), Instant.now().toString(), sources, breadth.rising, breadth.flat, breadth.falling, breadth.total)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { observationError = true }
        }
    }
    suspend fun refreshData(force: Boolean) {
        busy = true
        try {
            status = MyStocksCache.loadMarketStatus()
            indices = MyStocksCache.loadMarketIndices(status.isKnown && status.isOpen)
            if (catalog.isEmpty() || force) {
                val data = MyStocksCache.loadCompanies()
                if (data.isNotEmpty()) onCatalogLoaded(data)
            }
            val state = MarketRefreshController.state.value
            val due = state.lastSuccessfulRefreshMs?.let { System.currentTimeMillis() - it >= MarketRefreshController.REFRESH_INTERVAL_MS } ?: true
            if (!state.refreshInProgress && (stockFeed.isEmpty() || (force && due))) {
                val data = MyStocksCache.loadStocks()
                if (data.isNotEmpty()) { onQuotesLoaded(data); error = null }
                else error = "Quotes could not be refreshed. Available observations keep their original dates."
            }
            if (force) { historyCache.clear(); historyRevision++ }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Market refresh failed. Please try again." }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { refreshData(false) }

    MaterialTheme(colorScheme = CompanyResearchColors) {
        Column(Modifier.fillMaxSize().background(ResearchBackground)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, null, tint = ResearchGreen, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(7.dp)); Text("NSE Watcher", Modifier.weight(1f), color = ResearchGreen, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { if (!busy) scope.launch { refreshData(true) } }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = ResearchGreen, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Refresh market", tint = ResearchText)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Market", color = ResearchText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp)); ResearchCaption("Direction, participation and performance")
            }
            MarketChoiceRow(listOf("Overview", "Sectors", "Performance"), tab) { tab = it }
            HorizontalDivider(color = ResearchBorder)
            if (tab == "Performance") {
                Box(Modifier.weight(1f)) { MarketPerformanceView(companies, historyRevision, now, historyCache, openCompany) }
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (error != null) item { ResearchCaption(error.orEmpty()) }
                if (busy && companies.isEmpty()) item { ResearchLoading("Loading market observations…") }
                if (tab == "Overview") {
                    item {
                        ResearchPanel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(when { !status.isKnown -> "Market status unavailable"; status.isOpen -> "● Market open"; else -> "Market closed" }, color = if (status.isKnown && status.isOpen) ResearchGreen else ResearchMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    ResearchCaption(HomePresentation.freshness(companies, now))
                                    ResearchCaption(latest?.let { CompanyResearchPresentation.date(it.toString()) } ?: "Observation time unavailable")
                                }
                                TextButton(onClick = { sheet = "Data coverage" }) { Text("Data coverage ↗", color = ResearchGreen, fontSize = 11.sp) }
                            }
                            ResearchCaption("Source: $sources")
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("^NASI" to "NASI", "^N20I" to "NSE 20", "^N25I" to "NSE 25").forEach { (symbol, title) ->
                                val index = indices.firstOrNull { it.symbol == symbol && it.value.isFinite() && it.value > 0 }
                                Surface(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { sheet = "Index observations" }, color = ResearchCard, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ResearchBorder)) {
                                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(title, color = ResearchText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(index?.let { String.format(Locale.US, "%,.2f", it.value) } ?: "Unavailable", color = ResearchText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(index?.changePct?.let { CompanyResearchPresentation.percent(it) } ?: "No change data", color = researchChangeColor(index?.changePct), fontSize = 11.sp)
                                        if (index != null) Text(CompanyResearchPresentation.date(index.asOf), color = ResearchMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        ResearchPanel {
                            Text("MARKET PARTICIPATION", color = ResearchMuted, fontSize = 11.sp, letterSpacing = 0.7.sp)
                            ResearchTitle(MarketPresentation.summary(breadth))
                            ResearchCaption("${breadth.rising} rising versus ${breadth.falling} falling in the available daily changes.")
                            MarketBreadthBar(breadth)
                            ResearchCaption("${breadth.covered} daily changes · ${breadth.total - breadth.covered} unavailable")
                            TextButton(onClick = { sheet = "Market participation" }) { Text("What does this mean? ⓘ", color = ResearchGreen, fontSize = 12.sp) }
                        }
                    }
                    item {
                        ResearchTitle("Today’s movers")
                        MarketChoiceRow(listOf("Gainers", "Losers", "By volume"), mover) { mover = it }
                        ResearchPanel {
                            val movers = marketMovers(companies, mover)
                            if (movers.isEmpty()) ResearchCaption("No matching observations are available. Missing daily changes are not counted as zero.")
                            movers.take(3).forEach { stock -> MarketStockRow(stock, if (mover == "By volume") "${String.format(Locale.US, "%,d", stock.volume)} shares" else CompanyResearchPresentation.percent(stock.change), if (mover == "By volume") null else stock.change, openCompany) }
                            TextButton(onClick = { sheet = "Movers:$mover" }) { Text("View all movers →", color = ResearchGreen) }
                        }
                    }
                    item {
                        ResearchPanel {
                            ResearchBody("Trading activity")
                            Text(if (volumeStocks.isEmpty()) "Unavailable" else if (volume >= 1_000_000) String.format(Locale.US, "%.2fM shares", volume / 1_000_000) else String.format(Locale.US, "%,.0f shares", volume), color = ResearchText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            ResearchCaption("Reported volume across ${volumeStocks.size} companies")
                            ResearchCaption("Share volume, not shilling turnover. Observations may have different times.")
                        }
                    }
                } else {
                    item { ResearchCaption("Daily changes · Latest available observations"); Spacer(Modifier.height(8.dp)); ResearchTitle("Where is strength appearing?"); ResearchCaption("Compare participation within each sector.") }
                    if (sectors.isEmpty()) item { ResearchPanel { ResearchCaption("Sector observations are unavailable. Refresh to try again.") } }
                    items(sectors, key = { it.name }) { sector ->
                        Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { sheet = "Sector:${sector.name}" }, color = ResearchCard, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ResearchBorder)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { ResearchBody(sector.name); Text(sector.average?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable", color = researchChangeColor(sector.average), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                                    ResearchCaption("${sector.breadth.covered} of ${sector.breadth.total} with data")
                                    Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted)
                                }
                                MarketBreadthBar(sector.breadth)
                            }
                        }
                    }
                    item {
                        ResearchPanel {
                            ResearchTitle("How this is calculated")
                            ResearchBody("Equal-weight average of available daily price changes. This is not an official sector index.")
                            ResearchCaption("Small samples can be misleading. Check coverage and observation dates before comparing.")
                            TextButton(onClick = { sheet = "Data coverage" }) { Text("See source observations →", color = ResearchGreen) }
                        }
                    }
                    item { OutlinedButton(onClick = { openCompanies("All") }, modifier = Modifier.fillMaxWidth()) { Text("Browse companies by sector →") } }
                }
            }
        }
        sheet?.let { title ->
            ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
                LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { ResearchTitle(title.substringAfter(':')) }
                    when {
                        title == "Market participation" -> item {
                            ResearchBody("Each company with a finite provider-supplied daily change and a valid price counts once. Rising and falling counts describe participation, not the change in an NSE index.")
                            ResearchCaption("Missing data is excluded, not treated as unchanged. Dates can differ across companies.")
                            TextButton(onClick = { sheet = "Data coverage" }) { Text("Inspect the observations →", color = ResearchGreen) }
                        }
                        title == "Index observations" -> {
                            item { ResearchCaption("Only verified index observations can appear here. The current backend has no verified official NSE index source configured; no market average is substituted.") }
                            items(indices) { index -> ResearchPanel { ResearchBody(index.name); ResearchBody(String.format(Locale.US, "%,.2f", index.value)); ResearchCaption(CompanyResearchPresentation.date(index.asOf)) } }
                        }
                        title == "Saved observations" -> {
                            item {
                                ResearchCaption("Up to 90 observed dates saved on this device, starting when you use this version. These are summaries of available quotes, not official closing-market records. No past dates are reconstructed.")
                                if (observationError) ResearchCaption("Saved observations could not be read or updated.")
                                else if (observations.isEmpty()) ResearchCaption("No dated market summaries have been saved yet.")
                            }
                            items(observations, key = { it.observedAt }) { row -> ResearchPanel {
                                ResearchBody(CompanyResearchPresentation.date(row.observedAt))
                                ResearchCaption("${row.rising} rising · ${row.flat} unchanged · ${row.falling} falling")
                                ResearchCaption("${row.rising + row.flat + row.falling} of ${row.total} with daily changes · ${row.source}")
                                ResearchCaption("Collected: ${CompanyResearchPresentation.date(row.checkedAt)}")
                            } }
                        }
                        else -> {
                            val rows = when {
                                title.startsWith("Sector:") -> companies.filter { it.sector == title.substringAfter(':') }
                                title.startsWith("Movers:") -> marketMovers(companies, title.substringAfter(':'))
                                else -> companies
                            }
                            item {
                                if (title == "Data coverage") {
                                    ResearchBody("${breadth.covered} of ${breadth.total} companies have daily changes; ${volumeStocks.size} have volume observations.")
                                    ResearchCaption("Coverage is based on the loaded company catalogue and quote feed, not a claim that every listed security is covered.")
                                    TextButton(onClick = { sheet = "Saved observations" }) { Text("Saved observations on this device →", color = ResearchGreen) }
                                }
                                if (title.startsWith("Sector:")) TextButton(onClick = { sheet = null; openCompanies(title.substringAfter(':')) }) { Text("Browse this sector →", color = ResearchGreen) }
                                if (rows.isEmpty()) ResearchCaption("No matching observations are available.")
                            }
                            items(rows, key = { it.symbol }) { stock ->
                                ResearchPanel {
                                    MarketStockRow(stock, if (title == "Movers:By volume") "${String.format(Locale.US, "%,d", stock.volume)} shares" else if (MarketPresentation.validChange(stock)) CompanyResearchPresentation.percent(stock.change) else "Unavailable", if (title != "Movers:By volume" && MarketPresentation.validChange(stock)) stock.change else null, open = { sheet = null; openCompany(it) })
                                    ResearchCaption("Source: ${stock.source.ifBlank { "Unavailable" }}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun marketMovers(companies: List<Stock>, type: String): List<Stock> = when (type) {
    "By volume" -> companies.filter { it.volumeAvailable && it.volume > 0 }.sortedByDescending { it.volume }
    "Losers" -> companies.filter { MarketPresentation.validChange(it) && it.change < 0 }.sortedBy { it.change }
    else -> companies.filter { MarketPresentation.validChange(it) && it.change > 0 }.sortedByDescending { it.change }
}

@Composable
internal fun MarketChoiceRow(options: List<String>, selected: String, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) { options.forEach { option ->
        Column(Modifier.weight(1f)) {
            TextButton(onClick = { select(option) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)) { Text(option, color = if (option == selected) ResearchGreen else ResearchMuted, fontSize = 12.sp, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal) }
            if (option == selected) Box(Modifier.fillMaxWidth().height(2.dp).background(ResearchGreen))
        }
    } }
}

@Composable
internal fun MarketBreadthBar(breadth: MarketBreadth) {
    if (breadth.covered > 0) Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp))) {
        if (breadth.rising > 0) Box(Modifier.weight(breadth.rising.toFloat()).fillMaxHeight().background(ResearchGreen))
        if (breadth.flat > 0) Box(Modifier.weight(breadth.flat.toFloat()).fillMaxHeight().background(ResearchMuted))
        if (breadth.falling > 0) Box(Modifier.weight(breadth.falling.toFloat()).fillMaxHeight().background(ResearchRed))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${breadth.rising} rising", color = ResearchGreen, fontSize = 11.sp)
        Text("${breadth.flat} flat", color = ResearchMuted, fontSize = 11.sp)
        Text("${breadth.falling} falling", color = ResearchRed, fontSize = 11.sp)
    }
}

@Composable
internal fun MarketStockRow(stock: Stock, value: String, change: Double?, open: (Stock) -> Unit, dates: String? = null) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Research ${stock.name}") { open(stock) }.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).background(ResearchRaised, CircleShape), contentAlignment = Alignment.Center) { Text(stock.symbol.take(1), color = ResearchGreen, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f)) { Text(stock.name, color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); ResearchCaption(stock.symbol) }
            Text(value, color = researchChangeColor(change), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(max = 125.dp))
            Icon(Icons.Default.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(16.dp))
        }
        Text(dates ?: "Observed: ${CompanyResearchPresentation.date(stock.observedAt)}", Modifier.padding(start = 40.dp, top = 4.dp), color = ResearchMuted, fontSize = 10.sp)
    }
}
