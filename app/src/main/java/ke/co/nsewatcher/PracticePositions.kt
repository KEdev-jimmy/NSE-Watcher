package ke.co.nsewatcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.MarketHistoryCache
import java.util.Locale

@Composable internal fun PracticeAllocation(s: PracticeState, companies: List<Stock>) {
    val positions = PracticeEngine.positions(s)
    val groups = positions.groupBy { p -> CompaniesPresentation.sector(companies.firstOrNull { it.symbol == p.holding.symbol }?.sector ?: p.quote?.sector.orEmpty()) }
        .mapValues { (_, members) -> members.sumOf { it.value } }.entries.sortedByDescending { it.value }
    val total = groups.sumOf { it.value }
    val colors = listOf(ResearchGreen, Color(0xFF33CBD5), PracticeAmber, Color(0xFFA9A5FF), ResearchMuted)
    if (total <= 0) { ResearchCaption("Your sector mix appears after your first practice fill."); return }
    Row(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp))) { groups.forEachIndexed { i, g -> if (g.value > 0) Box(Modifier.weight((g.value / total).toFloat().coerceAtLeast(0.00001f)).fillMaxHeight().background(colors[i % colors.size])) } }
    groups.forEachIndexed { i, g -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(colors[i % colors.size], RoundedCornerShape(4.dp))); Spacer(Modifier.width(7.dp))
        Text(g.key, Modifier.weight(1f), color = ResearchMuted, fontSize = 12.sp)
        Text(String.format(Locale.US, "%.1f%%", g.value / total * 100), color = ResearchText, fontSize = 12.sp)
    } }
    ResearchCaption("Allocation of shares value; cash excluded.${if (positions.any { it.estimated }) " Includes cost estimates for missing quotes." else ""}")
}

@Composable internal fun PracticeHoldings(
    s: PracticeState,
    companies: List<Stock>,
    currentQuotes: List<Stock>,
    onHolding: (Stock) -> Unit,
    onTrade: (Stock) -> Unit,
    onResearch: (Stock) -> Unit,
    onBrowse: () -> Unit,
    onRules: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Value") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ResearchTitle("Your holdings"); ResearchCaption("${s.holdings.size} companies • Values include gains and losses")
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search your holdings") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) })
        MarketChoiceRow(listOf("Value", "Gain / loss"), sort) { sort = it }
        val rows = PracticeEngine.positions(s).filter { p -> p.holding.symbol.contains(query, true) || (companies.firstOrNull { it.symbol == p.holding.symbol }?.name ?: p.quote?.name.orEmpty()).contains(query, true) }
            .sortedByDescending { if (sort == "Value") it.value else it.gain }
        if (rows.isEmpty()) ResearchPanel { ResearchBody(if (s.holdings.isEmpty()) "No positions yet" else "No matching holdings"); TextButton(onClick = onBrowse) { Text("Explore companies →") } }
        rows.forEach { p ->
            val stock = companies.firstOrNull { it.symbol == p.holding.symbol } ?: Stock(p.holding.symbol, p.quote?.name?.ifBlank { p.holding.symbol } ?: p.holding.symbol, p.quote?.price ?: Double.NaN, 0.0, emptyList(), changeAvailable = false)
            val quoteConfirmed = PracticePortfolioPresentation.quoteConfirmedByCurrentFeed(
                p.holding.symbol,
                s,
                currentQuotes
            )
            val provisional = p.estimated || !quoteConfirmed
            ResearchPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PracticeCompanyIcon(stock)
                    Column(Modifier.weight(1f)) { ResearchBody(stock.name); ResearchCaption(stock.symbol) }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(practiceMoney(p.value), color = ResearchText, fontWeight = FontWeight.Bold)
                        Text(
                            if (provisional) "Last-known value" else practiceGain(p.gain),
                            color = if (provisional) PracticeAmber else researchChangeColor(p.gain),
                            fontSize = 13.sp
                        )
                    }
                }
                ResearchCaption("Average cost ${practiceMoney(p.holding.cost / p.holding.shares)} • ${p.holding.shares} shares")
                ResearchCaption(
                    p.quote?.let {
                        val prefix = if (quoteConfirmed) "Observed" else "Saved observation"
                        "$prefix ${CompanyResearchPresentation.date(it.at)}"
                    } ?: "No dated quote. Holding retained at estimated cost."
                )
                if (!p.estimated && !quoteConfirmed) {
                    ResearchCaption("The current loaded market feed has not confirmed this saved observation yet, so the displayed gain is provisional.")
                }
                if (p.holding.legacy) ResearchCaption("Legacy cost basis excludes original buy fees.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onResearch(stock) }) { Text("Research →") }
                    OutlinedButton(onClick = { onTrade(stock) }) { Text("Trade") }
                }
                TextButton(onClick = { onHolding(stock) }) { Text("View holding details →") }
            }
        }
        ResearchCaption("Gains on shares you still hold can change.")
        ResearchPanel { ResearchTitle("Portfolio mix"); PracticeAllocation(s, companies); TextButton(onClick = onRules) { Text("How values are calculated ⓘ") } }
        ResearchPanel { ResearchBody("If a quote is missing"); ResearchCaption("We retain your holding and flag its last known value. Its original observation date stays visible.") }
    }
}

@Composable internal fun PracticeHoldingDetail(
    s: PracticeState,
    stock: Stock,
    currentQuotes: List<Stock>,
    onTrade: (String) -> Unit,
    onResearch: () -> Unit,
    onNews: () -> Unit,
    onNotes: () -> Unit
) {
    val position = PracticeEngine.positions(s).firstOrNull { it.holding.symbol == stock.symbol }
    val quoteConfirmed = PracticePortfolioPresentation.quoteConfirmedByCurrentFeed(
        stock.symbol,
        s,
        currentQuotes
    )
    var range by rememberSaveable(stock.symbol) { mutableStateOf("1M") }
    var history by remember(stock.symbol) { mutableStateOf(MyStocksCache.HistoryResult()) }
    var loading by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var handledRetry by remember(stock.symbol) { mutableIntStateOf(0) }
    LaunchedEffect(stock.symbol, range, retry) {
        loading = true
        val forceRefresh = retry > handledRetry
        try {
            history = MarketHistoryCache.load(stock.symbol, range, forceRefresh = forceRefresh)
            if (forceRefresh) handledRetry = retry
        } finally {
            loading = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { PracticeCompanyIcon(stock); Column { ResearchTitle(stock.name); ResearchCaption("${stock.symbol} • ${stock.sector}") } }
        if (position == null) { ResearchBody("You no longer hold shares in this company."); Button(onClick = { onTrade("BUY") }) { Text("Buy shares") }; return }
        val h = position.holding
        val provisional = position.estimated || !quoteConfirmed
        ResearchPanel {
            ResearchCaption(
                when {
                    position.estimated -> "Estimated holding value"
                    !quoteConfirmed -> "Last-known holding value"
                    else -> "Holding value"
                }
            )
            Text(practiceMoney(position.value), color = ResearchText, fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    position.estimated -> "Gain unavailable without a quote"
                    !quoteConfirmed -> "${practiceGain(position.gain)} provisional"
                    else -> "${practiceGain(position.gain)} unrealised"
                },
                color = if (provisional) PracticeAmber else researchChangeColor(position.gain)
            )
            ResearchCaption(
                if (provisional) {
                    "A current loaded market observation has not confirmed this valuation yet."
                } else {
                    "Gain on shares you still hold"
                }
            )
        }
        PracticeAdaptivePair(
            first = { modifier -> PracticeMetric("Shares owned", h.shares.toString(), modifier) },
            second = { modifier -> PracticeMetric("Average cost", practiceMoney(h.cost / h.shares), modifier) }
        )
        ResearchCaption(if (h.legacy) "Legacy cost basis excludes original buy fees." else "Average cost includes simulated buy costs.")
        ResearchPanel {
            ResearchTitle("Your position"); PracticeLine("Cost basis", practiceMoney(h.cost))
            PracticeLine("Latest observed price", position.quote?.price?.let(::practiceMoney) ?: "Unavailable")
            PracticeLine("Portfolio share", if (PracticeEngine.value(s) > 0) String.format(Locale.US, "%.1f%%", position.value / PracticeEngine.value(s) * 100) else "Unavailable")
            PracticeLine("Shares reserved for sale", PracticeEngine.reservedShares(s, stock.symbol).toString())
            ResearchCaption(position.quote?.let { "Observed ${CompanyResearchPresentation.date(it.at)}" } ?: "Cost estimate used until a valid dated quote is received.")
        }
        ResearchPanel {
            ResearchTitle("Price history")
            MarketChoiceRow(listOf("1W", "1M", "3M"), range) { range = it }
            CompanyResearchChart(history.points, range, loading, { retry++ }, purchaseMarkers = s.orders.filter { it.symbol == stock.symbol && it.side == "BUY" && it.status == "FILLED" }.mapNotNull { order -> CompanyResearchPresentation.timestamp(order.quoteAt)?.let { it.toEpochMilli() to order.price } })
            ResearchCaption("White-ring markers show your simulated buy observations when within this chart window.")
            val purchases = s.orders.filter { it.symbol == stock.symbol && it.side == "BUY" && it.status == "FILLED" }
            purchases.takeLast(3).forEach { ResearchCaption("Your purchase: ${it.shares} @ ${practiceMoney(it.price)} • ${practiceTime(it.filledAt)}") }
            if (purchases.isEmpty() && h.legacy) ResearchCaption("Exact purchase observations were not stored for this legacy holding.")
        }
        ResearchPanel {
            ResearchTitle("Before your next decision")
            PracticeLink(Icons.Outlined.MenuBook, "Company intelligence", onResearch)
            PracticeLink(Icons.Outlined.Article, "Related news", onNews)
            PracticeLink(Icons.Outlined.EditNote, "Your trade notes", onNotes)
        }
        PracticeAdaptivePair(
            first = { modifier ->
                Button(onClick = { onTrade("BUY") }, modifier = modifier) { Text("Buy more") }
            },
            second = { modifier ->
                OutlinedButton(onClick = { onTrade("SELL") }, modifier = modifier) { Text("Sell shares") }
            }
        )
        ResearchCaption("Selling realises a gain or loss after costs. Practice sale proceeds are available immediately; real settlement delays are not simulated.")
    }
}
