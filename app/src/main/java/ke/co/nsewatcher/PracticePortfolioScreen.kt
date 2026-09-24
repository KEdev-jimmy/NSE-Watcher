package ke.co.nsewatcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ke.co.nsewatcher.data.CompanyChangeStore
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.MarketData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import java.time.Instant
import java.util.UUID

internal object PracticeLaunch {
    fun symbol(raw: String): String = WatchlistPresentation.symbol(raw)
    fun shouldOpenOrder(enabled: Boolean, initialSymbol: String, launchRevision: Int, handledRevision: Int): Boolean =
        enabled && symbol(initialSymbol).isNotBlank() && launchRevision > handledRevision

    fun shouldOpenReview(enabled: Boolean, orderId: String, launchRevision: Int, handledRevision: Int): Boolean =
        enabled && orderId.isNotBlank() && launchRevision > handledRevision
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticePortfolioScreen(quoteFeed: List<Stock>, catalog: List<Stock>, initialMarket: MyStocksCache.MarketStatus,
    news: List<NewsItem>, initialSymbol: String = "", initialReviewOrderId: String = "",
    launchRevision: Int = 0, launchSource: String = "",
    onQuotes: (List<Stock>) -> Unit, onCatalog: (List<Stock>) -> Unit,
    openCompany: (Stock) -> Unit, openNews: (NewsItem) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PracticeStore(context) }
    val companyChangeStore = remember { CompanyChangeStore(context) }
    val scope = rememberCoroutineScope()
    var companyChangeError by remember { mutableStateOf(false) }
    val companyChangeFlow = remember(companyChangeStore) {
        companyChangeStore.events.catch { companyChangeError = true }
    }
    val companyDataEvents by companyChangeFlow.collectAsState(initial = emptyList())
    var state by remember { mutableStateOf<PracticeState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var market by remember { mutableStateOf(initialMarket) }
    var statusChecked by remember { mutableLongStateOf(0L) }
    var page by rememberSaveable { mutableStateOf("MAIN") }
    var tab by rememberSaveable { mutableStateOf("Overview") }
    var activityTab by rememberSaveable { mutableStateOf("Orders") }
    var symbol by rememberSaveable { mutableStateOf(PracticeLaunch.symbol(initialSymbol)) }
    var handledLaunchRevision by rememberSaveable { mutableIntStateOf(-1) }
    var orderLaunchSource by rememberSaveable { mutableStateOf("") }
    var side by rememberSaveable { mutableStateOf("BUY") }
    var editId by rememberSaveable { mutableStateOf("") }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resumed by remember { mutableStateOf(true) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun operation(block: () -> PracticeState, complete: () -> Unit = {}) {
        if (working) return
        working = true; error = null
        scope.launch {
            try { state = withContext(Dispatchers.IO) { block() }; complete() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message ?: "Could not save your practice account." }
            finally { working = false }
        }
    }
    LaunchedEffect(Unit) {
        try { state = withContext(Dispatchers.IO) { store.read() } }
        catch (e: Exception) { error = "Could not read the saved portfolio: ${e.message}. Your stored account has not been replaced." }
        if (catalog.isEmpty()) { val rows = MarketData.companies(); if (rows.isNotEmpty()) onCatalog(rows) }
    }
    LaunchedEffect(state?.enabled, initialReviewOrderId, launchRevision) {
        if (PracticeLaunch.shouldOpenReview(state?.enabled == true, initialReviewOrderId, launchRevision, handledLaunchRevision)) {
            val order = state?.orders?.firstOrNull { it.id == initialReviewOrderId }
            if (order != null) {
                symbol = order.symbol
                editId = order.id
                page = "MAIN"
                orderLaunchSource = ""
                sheet = "Decision review"
            } else {
                notice = "That saved practice decision is no longer available."
            }
            handledLaunchRevision = launchRevision
        }
    }
    LaunchedEffect(state?.enabled, initialSymbol, initialReviewOrderId, launchRevision) {
        if (initialReviewOrderId.isBlank() &&
            PracticeLaunch.shouldOpenOrder(state?.enabled == true, initialSymbol, launchRevision, handledLaunchRevision)
        ) {
            symbol = PracticeLaunch.symbol(initialSymbol)
            side = "BUY"
            editId = ""
            sheet = null
            orderLaunchSource = launchSource.trim()
            page = "ORDER"
            handledLaunchRevision = launchRevision
        }
    }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        while (isActive) {
            market = MarketData.status(); statusChecked = System.currentTimeMillis()
            if (MarketRefreshController.shouldRefreshQuotes(quoteFeed.isNotEmpty())) {
                val quotes = MarketData.stocks(); if (quotes.isNotEmpty()) onQuotes(quotes)
            }
            delay(30_000L)
        }
    }
    val currentOrders = state?.orders
    LaunchedEffect(quoteFeed, statusChecked, currentOrders, resumed) {
        if (!resumed || state?.enabled != true || statusChecked == 0L) return@LaunchedEffect
        try {
            val previousFilled = state!!.orders.count { it.status == "FILLED" }
            val next = withContext(Dispatchers.IO) { store.update { s ->
                val now = System.currentTimeMillis()
                PracticeOrderProcessor.process(
                    initial = s,
                    quotes = quoteFeed,
                    marketOpen = market.isOpen,
                    marketKnown = market.isKnown && now - statusChecked < 60_000,
                    now = now
                ).state
            } }
            state = next
            if (next.orders.count { it.status == "FILLED" } > previousFilled) notice = "Practice order filled. View Activity for the observed price and simulated costs."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (e: Exception) { error = e.message ?: "Could not update practice orders." }
    }
    val companies = remember(catalog, quoteFeed) { CompaniesPresentation.companies(catalog, quoteFeed) }
    val s = state
    val selected = companies.firstOrNull { it.symbol == symbol } ?: s?.quotes?.firstOrNull { it.symbol == symbol }?.let { Stock(it.symbol, it.name.ifBlank { it.symbol }, it.price, 0.0, emptyList(), sector = it.sector, observedAt = it.at, changeAvailable = false) }
        ?: Stock(symbol, symbol, Double.NaN, 0.0, emptyList(), changeAvailable = false, volumeAvailable = false)
    fun trade(stock: Stock, direction: String, id: String = "") {
        symbol = stock.symbol
        side = direction
        editId = id
        orderLaunchSource = ""
        page = "ORDER"
    }
    fun goBack() {
        if (page != "MAIN") {
            page = "MAIN"
            orderLaunchSource = ""
        } else back()
    }
    BackHandler(page != "MAIN" && sheet == null) {
        page = "MAIN"
        orderLaunchSource = ""
    }
    MaterialTheme(colorScheme = CompanyResearchColors) {
        Column(Modifier.fillMaxSize().background(ResearchBackground)) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::goBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = ResearchText) }
                Text(when (page) { "HOLDING" -> "Your holding"; "ORDER" -> "Practice order"; "DIVIDENDS" -> "Dividends"; else -> "NSE Watcher" }, Modifier.weight(1f), color = ResearchText, fontWeight = FontWeight.SemiBold)
                if (page == "MAIN") IconButton(onClick = { sheet = "Settings" }) { Icon(Icons.Outlined.Settings, "Practice settings", tint = ResearchText) } else PracticeBadge()
            }
            if (page == "MAIN") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { ResearchTitle("Practice Portfolio"); PracticeBadge() }
                MarketChoiceRow(listOf("Overview", "Holdings", "Activity"), tab) { tab = it }
            }
            if (error != null) Surface(color = ResearchRaised, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) { Text(error.orEmpty(), color = PracticeAmber, fontSize = 13.sp); if (s == null) TextButton(onClick = { operation({ store.read() }) }) { Text("Retry saved account") } }
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ResearchGreen)
            if (s == null) { Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ResearchCaption(if (error == null) "Loading your practice account…" else "Your saved account is unavailable.") } }
            else if (page == "ORDER") {
                PracticeOrderTicket(
                    s = s,
                    stock = selected,
                    side = side,
                    editId = editId,
                    market = market,
                    working = working,
                    launchSource = orderLaunchSource,
                    captureDecision = { confirmedAt ->
                        PracticeReviewPresentation.captureDecision(
                            stock = selected,
                            news = news,
                            companyEvents = companyDataEvents,
                            capturedAt = confirmedAt
                        )
                    },
                    onSubmit = { order ->
                        operation({ store.update { PracticeEngine.submit(it, order) } }) {
                            page = "MAIN"
                            tab = "Activity"
                            activityTab = "Orders"
                            orderLaunchSource = ""
                            notice = "Practice order queued. Cash or shares are reserved; no shares have traded yet."
                        }
                    },
                    onSide = { side = it }
                )
            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    page == "DIVIDENDS" -> item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { PracticeDividends() } }
                    page == "HOLDING" -> item {
                        PracticeHoldingDetail(s, selected, onTrade = { trade(selected, it) }, onResearch = { openCompany(selected) }, onNews = { sheet = "Related news" }, onNotes = { sheet = "Holding note" })
                    }
                    !s.enabled -> item {
                        ResearchPanel {
                            PracticeIcon(Icons.Outlined.School); ResearchTitle("Your first investment starts here")
                            ResearchBody("Learn with virtual money and actual market observations.")
                            ResearchCaption("Start with KSh 1,000,000 or choose a smaller practice balance. Nothing is sent to a broker.")
                            Button(onClick = { sheet = "Create portfolio" }, modifier = Modifier.fillMaxWidth()) { Text("Create practice portfolio") }
                        }
                    }
                    tab == "Overview" -> {
                        val positions = PracticeEngine.positions(s)
                        val value = PracticeEngine.value(s)
                        val provisional = positions.any { it.estimated }
                        item {
                            ResearchCaption(when { !market.isKnown -> "Market status unavailable"; market.isOpen -> "Market open • Delayed observations"; else -> "Market closed • Latest available prices" })
                            val latest = s.quotes.filter { q -> s.holdings.any { it.symbol == q.symbol } }.maxByOrNull { CompanyResearchPresentation.timestamp(it.at) ?: Instant.MIN }
                            ResearchCaption(latest?.let { "Latest holding quote: ${CompanyResearchPresentation.date(it.at)}" } ?: "Quote dates appear on your holdings.")
                        }
                        item { ResearchPanel {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { PracticeIcon(Icons.Outlined.BarChart); Column(Modifier.weight(1f)) {
                                ResearchCaption(if (provisional) "Estimated portfolio value" else "Portfolio value")
                                Text(practiceMoney(value), color = ResearchText, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                                Text("${practiceGain(value - s.contributed)} since start", color = researchChangeColor(value-s.contributed), fontWeight = FontWeight.Bold)
                            } }
                            ResearchCaption("After recorded practice costs • Added cash excluded from profit")
                            if (provisional) ResearchCaption("Some holdings have no dated quote. Their cost is shown as an estimate; this gain is provisional.")
                        } }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { PracticeMetric("Cash balance", practiceMoney(s.cash), Modifier.weight(1f)); PracticeMetric("Shares value", practiceMoney(value - s.cash), Modifier.weight(1f)) } }
                        item { ResearchCaption("${practiceMoney(PracticeEngine.reserved(s))} reserved for orders • ${practiceMoney(PracticeEngine.available(s))} available") }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Button(onClick = { sheet = "Choose company" }, modifier = Modifier.weight(1f)) { Text("Buy shares") }
                            OutlinedButton(onClick = { sheet = "Add virtual cash" }, modifier = Modifier.weight(1f)) { Text("Add virtual cash") }
                        } }
                        item { PracticeJourney(s) }
                        val pending = s.orders.count { it.status == "PENDING" }
                        if (pending > 0) item { ResearchPanel {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { PracticeIcon(Icons.Outlined.Schedule, true); Column(Modifier.weight(1f)) { Text("$pending order${if (pending == 1) "" else "s"} waiting", color = PracticeAmber, fontWeight = FontWeight.Bold); ResearchCaption("View the reason for each order") } }
                            TextButton(onClick = { tab = "Activity"; activityTab = "Orders" }) { Text("View orders →") }
                        } }
                        item { ResearchPanel { PracticeIcon(Icons.Outlined.PieChart); ResearchTitle("Understand your exposure"); PracticeAllocation(s, companies); TextButton(onClick = { tab = "Holdings" }) { Text("Explore your holdings →") } } }
                        item { ResearchPanel { PracticeLink(Icons.Outlined.AccountBalanceWallet, "Dividends and eligibility") { page = "DIVIDENDS" }; PracticeLink(Icons.Outlined.MenuBook, "How practice trading works") { sheet = "Practice rules" } } }
                    }
                    tab == "Holdings" -> item {
                        PracticeHoldings(s, companies, onHolding = { symbol = it.symbol; page = "HOLDING" }, onTrade = { trade(it, "BUY") }, onResearch = openCompany, onBrowse = { sheet = "Choose company" }, onRules = { sheet = "Valuation" })
                    }
                    tab == "Activity" -> item {
                        PracticeActivity(s, activityTab, onTab = { activityTab = it }, onCancel = { id -> operation({ store.update { PracticeEngine.cancel(it, id) } }) },
                            onEdit = { o -> trade(companies.firstOrNull { it.symbol == o.symbol } ?: Stock(o.symbol, o.symbol, Double.NaN, 0.0, emptyList()), o.side, o.id) },
                            onDetails = { id -> editId = id; sheet = "Order details" },
                            onReview = { o -> editId = o.id; symbol = o.symbol; sheet = "Decision review" },
                            onNote = { symbol = ""; sheet = "Trade journal" }, onRules = { sheet = "Practice rules" }, working = working)
                    }
                }
                item { ResearchCaption("Practice only • Pending orders are checked here while active and periodically in the background when Android can run connected work. Delayed quotes, fees and fills are simulations; real queue position and liquidity are not reproduced.") }
            }
        }
        if (notice != null) AlertDialog(onDismissRequest = { notice = null }, icon = { Icon(Icons.Outlined.CheckCircle, null, tint = ResearchGreen) }, title = { Text("Practice account updated") }, text = { Text(notice.orEmpty()) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("Done") } })
        sheet?.let { title ->
            ModalBottomSheet(onDismissRequest = { if (!working) sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
                LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.88f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { ResearchTitle(title) }
                    when (title) {
                        "Create portfolio", "Add virtual cash" -> item {
                            var amount by rememberSaveable(title) { mutableStateOf(if (title == "Create portfolio") "1000000" else "10000") }
                            ResearchBody("Virtual capital is not investment profit.")
                            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (KSh)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { operation({ if (title == "Create portfolio") store.create(amount.toDoubleOrNull() ?: Double.NaN) else store.addCash(amount.toDoubleOrNull() ?: Double.NaN) }) { sheet = null } }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (title == "Create portfolio") "Start practising" else "Add virtual cash") }
                        }
                        "Choose company" -> item {
                            var query by rememberSaveable { mutableStateOf("") }
                            OutlinedTextField(query, { query = it }, placeholder = { Text("Search companies or symbols") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            val rows = companies.filter { it.name.contains(query, true) || it.symbol.contains(query, true) }
                            if (rows.isEmpty()) ResearchCaption("No matching companies. Market data may be unavailable.")
                            rows.forEach { stock -> MarketStockRow(stock, stock.price.takeIf { it.isFinite() && it > 0 }?.let(::practiceMoney) ?: "Unavailable", null, open = { sheet = null; trade(it, "BUY") }) }
                        }
                        "Trade journal", "Holding note" -> item {
                            var note by rememberSaveable(title, symbol) { mutableStateOf("") }
                            ResearchCaption(if (symbol.isBlank()) "Record why you made a decision and what would change your mind." else "$symbol • Record why you bought and what changed.")
                            OutlinedTextField(note, { note = it.take(2000) }, label = { Text("Your note") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                            Button(enabled = note.isNotBlank() && !working, onClick = { operation({ store.update { it.copy(entries = it.entries + PracticeEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), "NOTE", note.trim(), symbol = symbol)) } }) { sheet = null } }) { Text("Save note") }
                            s?.entries?.filter { (it.kind == "NOTE" || it.kind == "REVIEW") && (symbol.isBlank() || it.symbol == symbol) }?.reversed()?.forEach {
                                ResearchPanel {
                                    ResearchCaption(if (it.kind == "REVIEW") "Decision review • ${practiceTime(it.time)}" else practiceTime(it.time))
                                    ResearchBody(it.text)
                                }
                            }
                            s?.orders?.filter { it.note.isNotBlank() && (symbol.isBlank() || it.symbol == symbol) }?.reversed()?.forEach { ResearchPanel { ResearchCaption("${it.symbol} • ${practiceTime(it.created)}"); ResearchBody(it.note) } }
                        }
                        "Related news" -> {
                            val linked = WatchlistPresentation.linkedNews(news, listOf(selected))
                            if (linked.isEmpty()) item { ResearchCaption("No related news is available in the loaded feed.") }
                            items(linked, key = { it.id }) { story -> ResearchPanel { ResearchBody(story.title); TextButton(onClick = { sheet = null; openNews(story) }) { Text("Read news →") } } }
                        }
                        "Settings" -> item {
                            ResearchCaption("Your practice account is saved on this device. Clearing app data removes it.")
                            PracticeLink(Icons.Outlined.MenuBook, "Practice rules and costs") { sheet = "Practice rules" }
                            PracticeLink(Icons.Outlined.AccountBalanceWallet, "Dividends") { sheet = null; page = "DIVIDENDS" }
                            PracticeLink(Icons.Outlined.RestartAlt, "Reset practice portfolio") { sheet = "Reset portfolio" }
                        }
                        "Reset portfolio" -> item {
                            var confirmation by rememberSaveable { mutableStateOf("") }
                            ResearchBody("This removes all practice cash, holdings, orders, history and notes on this device. Type RESET to continue.")
                            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type RESET") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { operation({ store.reset() }) { sheet = null; page = "MAIN"; tab = "Overview" } }, enabled = confirmation == "RESET" && !working, colors = ButtonDefaults.buttonColors(containerColor = ResearchRed)) { Text("Reset practice portfolio") }
                        }
                        "Decision review" -> item {
                            val order = s?.orders?.firstOrNull { it.id == editId }
                            if (s == null || order == null) {
                                ResearchCaption("This saved order could not be found.")
                            } else {
                                val reviewStock = companies.firstOrNull { it.symbol == order.symbol }
                                    ?: s.quotes.firstOrNull { it.symbol == order.symbol }?.let {
                                        Stock(
                                            it.symbol,
                                            it.name.ifBlank { it.symbol },
                                            it.price,
                                            0.0,
                                            emptyList(),
                                            sector = it.sector,
                                            observedAt = it.at,
                                            changeAvailable = false
                                        )
                                    }
                                    ?: Stock(order.symbol, order.symbol, Double.NaN, 0.0, emptyList(), changeAvailable = false, volumeAvailable = false)
                                PracticeDecisionReviewPanel(
                                    state = s,
                                    order = order,
                                    stock = reviewStock,
                                    news = news,
                                    companyEvents = companyDataEvents,
                                    companyChangesUnavailable = companyChangeError,
                                    working = working,
                                    onNews = { story -> sheet = null; openNews(story) },
                                    onResearch = { sheet = null; openCompany(reviewStock) },
                                    onSaveReview = { reflection ->
                                        operation({
                                            store.update { current ->
                                                current.copy(
                                                    entries = current.entries + PracticeEntry(
                                                        id = "review:${order.id}:${UUID.randomUUID()}",
                                                        time = System.currentTimeMillis(),
                                                        kind = "REVIEW",
                                                        text = reflection,
                                                        symbol = order.symbol
                                                    )
                                                )
                                            }
                                        }) {
                                            sheet = null
                                            notice = "Decision review saved. You can revisit it in Activity → Notes."
                                        }
                                    }
                                )
                            }
                        }
                        "Order details" -> item { s?.orders?.firstOrNull { it.id == editId }?.let { PracticeOrderReceipt(it) } }
                        "Valuation" -> item {
                            ResearchBody("Portfolio value = cash balance + shares valued at their latest saved dated quotes. Reserved cash remains part of your cash balance.")
                            ResearchCaption("Missing quotes never remove holdings. A last-known quote keeps its original date; without one we show cost as an estimate and label portfolio gain provisional. New buy costs include the 2% practice fee. Migrated holdings retain their older cost basis, which excluded fees.")
                        }
                        else -> item {
                            ResearchBody("Practice limit orders")
                            ResearchCaption("Buy at the limit or lower; sell at the limit or higher. Whole shares only. Cash or shares are reserved until filled or cancelled. Orders remain pending until you cancel them; editing restarts their observation eligibility time.")
                            ResearchBody("When an order fills")
                            ResearchCaption("A known open market and a timed same-day continuous-session quote are required. The quote must be no older than 30 minutes and observed after submission. Android also checks pending orders periodically in the background when connected; timing may be delayed by the operating system. Missed historical prices are never replayed as fills.")
                            ResearchBody("Costs and settlement")
                            ResearchCaption("A 2% simulated fee applies to each buy and sell. This is an educational assumption, not a broker tariff. Fills use eligible observed prices, not a real order book. Exchange-specific price bands, ticks, partial fills and settlement delays are not simulated. Sale proceeds become available immediately in this practice account.")
                            ResearchBody("Market closure and data")
                            ResearchCaption("New fills pause when closed or status is unknown. Delayed closing observations can still change valuation. No synthetic price movement, dividends or corporate actions are generated. Splits and other actions may require manual portfolio review.")
                        }
                    }
                    if (error != null) item { Text(error.orEmpty(), color = PracticeAmber) }
                }
            }
        }
    }
}
