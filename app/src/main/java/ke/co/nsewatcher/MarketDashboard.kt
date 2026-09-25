package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.MarketObservationStore
import ke.co.nsewatcher.data.MovementIntelligenceCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketDashboard(
    stockFeed: List<Stock>,
    catalog: List<Stock>,
    initialStatus: MyStocksCache.MarketStatus,
    marketIndices: List<MyStocksCache.MarketIndex>,
    newsFeed: List<NewsItem>,
    openCompany: (Stock) -> Unit,
    openCompanies: (String) -> Unit,
    openNews: (NewsItem) -> Unit,
    openSearch: () -> Unit,
    openAlerts: () -> Unit,
    onQuotesLoaded: (List<Stock>) -> Unit,
    onCatalogLoaded: (List<Stock>) -> Unit,
    onIndicesLoaded: (List<MyStocksCache.MarketIndex>) -> Unit,
    onMarketStatusLoaded: (MyStocksCache.MarketStatus) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf("Overview") }
    var status by remember { mutableStateOf(initialStatus) }
    val indices = marketIndices
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var movementSymbol by rememberSaveable { mutableStateOf("") }
    var movementResult by remember { mutableStateOf(MovementIntelligenceCache.Result()) }
    var movementLoading by remember { mutableStateOf(false) }
    var movementRevision by remember { mutableIntStateOf(0) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(Instant.now()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val observationStore = remember { MarketObservationStore(context) }
    var observationError by remember { mutableStateOf(false) }
    val observationsFlow = remember { observationStore.observations.catch { observationError = true } }
    val observations by observationsFlow.collectAsState(initial = emptyList())
    val companies = remember(catalog, stockFeed) { CompaniesPresentation.companies(catalog, stockFeed) }
    val movementStock = remember(companies, movementSymbol) {
        companies.firstOrNull { it.symbol.equals(movementSymbol, ignoreCase = true) }
    }
    val breadth = remember(companies) { MarketPresentation.breadth(companies) }
    val sectors = remember(companies) { MarketPresentation.sectors(companies) }
    val attention = remember(companies, newsFeed, now) {
        MarketAttentionEngine.rank(companies, newsFeed, now)
    }
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
        var quoteFailed = false
        var statusFailed = false
        var catalogFailed = false
        try {
            coroutineScope {
                launch {
                    try {
                        val refreshedStatus = MarketData.status()
                        status = refreshedStatus
                        if (refreshedStatus.isKnown || !initialStatus.isKnown) {
                            onMarketStatusLoaded(refreshedStatus)
                        }
                        val refreshedIndices = MarketData.indices(
                            refreshedStatus.isKnown && refreshedStatus.isOpen
                        )
                        if (refreshedIndices.isNotEmpty()) {
                            onIndicesLoaded(refreshedIndices)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        statusFailed = true
                    }
                }
                launch {
                    if (catalog.isEmpty() || force) {
                        try {
                            val data = MarketData.companies()
                            if (data.isNotEmpty()) onCatalogLoaded(data)
                            else if (catalog.isEmpty()) catalogFailed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (catalog.isEmpty()) catalogFailed = true
                        }
                    }
                }
                launch {
                    if (
                        MarketRefreshController.shouldRefreshQuotes(stockFeed.isNotEmpty()) &&
                        (stockFeed.isEmpty() || force)
                    ) {
                        try {
                            val data = MarketData.stocks()
                            if (data.isNotEmpty()) onQuotesLoaded(data) else quoteFailed = true
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            quoteFailed = true
                        }
                    }
                }
            }
            error = when {
                quoteFailed -> "Quotes could not be refreshed. Available observations keep their original dates."
                statusFailed -> "Market status could not be refreshed. Other available market data was updated."
                catalogFailed -> "The company catalogue could not be refreshed."
                else -> null
            }
            if (force) historyRevision++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            busy = false
        }
    }
    LaunchedEffect(Unit) { refreshData(false) }

    LaunchedEffect(movementStock?.symbol, movementRevision) {
        val selected = movementStock ?: return@LaunchedEffect
        movementLoading = true
        movementResult = MovementIntelligenceCache.Result()
        try {
            movementResult = MovementIntelligenceCache.load(selected.symbol)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            movementResult = MovementIntelligenceCache.Result(
                error = "Movement intelligence is unavailable right now."
            )
        } finally {
            movementLoading = false
        }
    }

    fun explainMovement(stock: Stock) {
        movementSymbol = stock.symbol
        movementResult = MovementIntelligenceCache.Result()
        movementLoading = true
        movementRevision++
        sheet = "Movement"
    }

    MaterialTheme(colorScheme = CompanyResearchColors) {
        PremiumMarketExperience(
            tab = tab,
            onTab = { tab = it },
            status = status,
            companies = companies,
            newsFeed = newsFeed,
            breadth = breadth,
            sectors = sectors,
            attention = attention,
            latest = latest,
            historyRevision = historyRevision,
            now = now,
            busy = busy,
            error = error,
            onRefresh = { if (!busy) scope.launch { refreshData(true) } },
            openCompany = openCompany,
            openCompanies = openCompanies,
            openNews = openNews,
            openSearch = openSearch,
            openAlerts = openAlerts,
            showSheet = { sheet = it }
        )
        sheet?.let { title ->
            ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
                LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        when {
                            title == "Movement" && movementStock != null ->
                                ResearchTitle("Why is ${movementStock!!.symbol} moving?")
                            title.startsWith("Sector:") -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MarketSectorIcon(title.substringAfter(':'))
                                Column(Modifier.weight(1f)) { ResearchTitle(title.substringAfter(':')) }
                            }
                            else -> ResearchTitle(title.substringAfter(':'))
                        }
                    }
                    when {
                        title == "Movement" -> {
                            val selected = movementStock
                            if (selected == null) {
                                item { ResearchCaption("Choose a company movement to investigate.") }
                            } else {
                                item {
                                    ResearchPanel {
                                        ResearchBody("${selected.symbol} · ${selected.name}")
                                        Text(
                                            CompanyResearchPresentation.percent(selected.change),
                                            color = researchChangeColor(selected.change),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        ResearchCaption("Latest market observation: ${CompanyResearchPresentation.date(selected.observedAt)}")
                                        ResearchCaption("Quote source: ${selected.source.ifBlank { "Unavailable" }}")
                                        ResearchCaption(
                                            "The mover figure above comes from the latest available quote. " +
                                                "The evidence window below uses dated historical observations, so the percentages can differ."
                                        )
                                    }
                                }
                                item {
                                    ResearchMovementContext(
                                        CompanyAnalysisPresentation.movementContext(selected, companies)
                                    )
                                }
                                item {
                                    ResearchMovement(
                                        result = movementResult,
                                        loading = movementLoading,
                                        retry = {
                                            movementLoading = true
                                            movementRevision++
                                        }
                                    )
                                }
                                item {
                                    Button(
                                        onClick = {
                                            sheet = null
                                            openCompany(selected)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Open Company Intelligence →")
                                    }
                                    ResearchCaption(
                                        "Company Intelligence combines this movement evidence with financials, company news, " +
                                            "broader peer context and the optional evidence explanation."
                                    )
                                }
                            }
                        }
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
                                    MarketStockRow(
                                        stock,
                                        if (title == "Movers:By volume") "${String.format(Locale.US, "%,d", stock.volume)} shares"
                                        else if (MarketPresentation.movementQuestionAvailable(stock)) CompanyResearchPresentation.percent(stock.change)
                                        else "Unavailable",
                                        if (title != "Movers:By volume" && MarketPresentation.movementQuestionAvailable(stock)) stock.change else null,
                                        open = { sheet = null; openCompany(it) }
                                    )
                                    ResearchCaption("Source: ${stock.source.ifBlank { "Unavailable" }}")
                                    if (MarketPresentation.movementQuestionAvailable(stock)) {
                                        MarketWhyMovingAction(stock) { explainMovement(it) }
                                    }
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
private fun MarketWhyMovingAction(stock: Stock, explain: (Stock) -> Unit) {
    TextButton(
        onClick = { explain(stock) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Outlined.Insights, null, tint = ResearchGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Why is ${stock.symbol} moving?", color = ResearchGreen, fontSize = 12.sp)
    }
}

@Composable
internal fun MarketChoiceRow(options: List<String>, selected: String, select: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scroll = shouldScrollTabs(
            widthDp = maxWidth.value,
            fontScale = LocalDensity.current.fontScale,
            optionCount = options.size,
            minimumOptionWidthDp = 72f
        )
        if (scroll) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                options.forEach { option ->
                    Column(Modifier.widthIn(min = 78.dp)) {
                        TextButton(
                            onClick = { select(option) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                option,
                                color = if (option == selected) ResearchGreen else ResearchMuted,
                                fontSize = 12.sp,
                                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                        if (option == selected) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(ResearchGreen))
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    Column(Modifier.weight(1f)) {
                        TextButton(
                            onClick = { select(option) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Text(
                                option,
                                color = if (option == selected) ResearchGreen else ResearchMuted,
                                fontSize = 12.sp,
                                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                        if (option == selected) {
                            Box(Modifier.fillMaxWidth().height(2.dp).background(ResearchGreen))
                        }
                    }
                }
            }
        }
    }
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


// Sector identity stays mint regardless of daily return; red/green values carry performance.
@Composable
private fun MarketSectorIcon(sector: String) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(ResearchGreen.copy(alpha = 0.18f), ResearchGreen.copy(alpha = 0.04f)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(marketSectorSymbol(sector), contentDescription = null,
                tint = ResearchGreen.copy(alpha = 0.75f), modifier = Modifier.size(27.dp))
        }
    }
}

private fun marketSectorSymbol(sector: String): ImageVector {
    val name = sector.trim().lowercase(Locale.US)
    return when {
        name.contains("bank") -> Icons.Outlined.AccountBalance
        name.contains("telecom") -> Icons.Outlined.CellTower
        name.contains("agric") || name.contains("farm") -> Icons.Outlined.Eco
        name.contains("energy") || name.contains("petroleum") || name.contains("oil") || name.contains("gas") -> Icons.Outlined.Bolt
        name.contains("insurance") -> Icons.Outlined.Shield
        name.contains("construct") || name.contains("allied") -> Icons.Outlined.Construction
        name.contains("manufactur") -> Icons.Outlined.Factory
        name.contains("automobil") || name.contains("transport") -> Icons.Outlined.DirectionsCar
        name.contains("real estate") || name.contains("reit") || name.contains("property") -> Icons.Outlined.Apartment
        name.contains("invest") || name.contains("exchange traded") || name.contains("etf") -> Icons.Outlined.PieChart
        name.contains("commercial") || name.contains("service") -> Icons.Outlined.Storefront
        else -> Icons.Outlined.Category
    }
}
