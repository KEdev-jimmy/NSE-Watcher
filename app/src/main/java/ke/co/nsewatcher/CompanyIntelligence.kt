package ke.co.nsewatcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.CompanyIntelligenceEngine
import ke.co.nsewatcher.data.AnalystCache
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val IntelligenceGreen = Color(0xFF00A859)
private val IntelligenceLight = Color(0xFFE9F8F0)
private val IntelligenceDark = Color(0xFF083C27)
private val IntelligenceText = Color(0xFF12231B)
private val IntelligenceMuted = Color(0xFF6C7A72)
private val IntelligenceBorder = Color(0xFFE1EAE5)
private val IntelligenceRed = Color(0xFFE04444)


private enum class CompanyIntelligenceSection(val label: String) {
    ABOUT("About"), INTELLIGENCE("Intelligence"), PERFORMANCE("Performance"),
    FINANCIALS("Financials"), VALUATION("Valuation"), DIVIDENDS("Dividends"),
    NEWS("News"), ANALYSIS("Analysis"), EVIDENCE("Evidence")
}

@Composable
private fun CompanySectionNavigation(
    labels: List<CompanyIntelligenceSection>,
    selected: CompanyIntelligenceSection,
    onSelected: (CompanyIntelligenceSection) -> Unit
) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF071B2F), RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEach { section ->
            val active = section == selected
            Surface(
                onClick = { onSelected(section) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                color = if (active) IntelligenceGreen else Color.Transparent,
                contentColor = if (active) Color.White else Color(0xFFA9B7C6),
                shape = RoundedCornerShape(8.dp),
                border = if (active) null else BorderStroke(1.dp, Color(0xFF294057))
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(section.label, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompanyIntelligence(s: Stock, back: () -> Unit, watched: Boolean = false, onWatchToggle: (() -> Unit)? = null) {
    val periods = listOf("1D", "1W", "1M", "3M", "6M", "1Y", "3Y", "5Y", "NOW")
    var period by rememberSaveable(s.symbol) { mutableStateOf("1D") }
    var history by remember(s.symbol) {
        mutableStateOf(s.history.map { MyStocksCache.HistoryPoint(it) })
    }
    var historyResult by remember(s.symbol) { mutableStateOf(MyStocksCache.HistoryResult()) }
    var marketStatus by remember(s.symbol) { mutableStateOf(MyStocksCache.MarketStatus()) }
    var historyLoading by remember(s.symbol) { mutableStateOf(false) }
    var monthHistory by remember(s.symbol) { mutableStateOf(emptyList<Double>()) }
    var intelligence by remember(s.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var intelligenceLoading by remember(s.symbol) { mutableStateOf(true) }
    var news by remember(s.symbol) { mutableStateOf(emptyList<NewsItem>()) }
    var newsLoading by remember(s.symbol) { mutableStateOf(true) }
    val analystScope = rememberCoroutineScope()
    var analystQuestion by rememberSaveable(s.symbol) { mutableStateOf("Explain the latest company performance using only the available evidence.") }
    var analystResult by remember(s.symbol) { mutableStateOf(AnalystCache.Result()) }
    var analystLoading by remember(s.symbol) { mutableStateOf(false) }

    LaunchedEffect(s.symbol) {
        intelligenceLoading = true
        intelligence = CompanyIntelligenceCache.load(s.symbol)
        intelligenceLoading = false
    }

    LaunchedEffect(s.symbol) {
        newsLoading = true
        news = NewsCache.loadCompanyNews(s.symbol).items
        newsLoading = false
    }

    LaunchedEffect(s.symbol) {
        monthHistory = MyStocksCache.loadHistory(s.symbol, "1m")
    }

    LaunchedEffect(s.symbol) {
        while (true) {
            marketStatus = MyStocksCache.loadMarketStatus()
            kotlinx.coroutines.delay(60_000L)
        }
    }

    val lastMarketRefreshMs = MarketRefreshController.state.value.lastSuccessfulRefreshMs
    LaunchedEffect(s.symbol, period, lastMarketRefreshMs) {
        historyLoading = true
        // NOW is a view of the latest available intraday session data.
        // It intentionally reuses the verified 1D endpoint; no live price is fabricated.
        // The existing 15-minute refresh timestamp also forces a reload when a newer
        // observation arrives, even if the price itself has not changed.
        val requestedPeriod = if (period == "NOW") "1D" else period
        val live = MyStocksCache.loadHistoryDetails(s.symbol, requestedPeriod)
        historyResult = live
        history = live.points
        historyLoading = false
    }

    val profile = intelligence.profile
    val selectedPeriodReturn = if (period == "1D" || period == "NOW") {
        historyResult.sessionChangePct
            ?: historyResult.points.takeIf { it.size >= 2 }?.let { percentReturn(it.map { point -> point.close }) }
    } else {
        history.takeIf { it.size >= 2 }?.let { percentReturn(it.map { point -> point.close }) }
    }
    val latestFinancialPeriod = intelligence.financialHistory.lastOrNull()?.period.orEmpty()
    val intelligenceView = CompanyIntelligenceEngine.build(s, intelligence, monthHistory, news)
    val hasSessionNavigationItem = period == "1D" || period == "NOW"
    val listState = rememberLazyListState()
    var primarySection by rememberSaveable(s.symbol) { mutableStateOf(CompanyIntelligenceSection.INTELLIGENCE) }
    var metricsSection by rememberSaveable(s.symbol) { mutableStateOf(CompanyIntelligenceSection.PERFORMANCE) }
    var researchSection by rememberSaveable(s.symbol) { mutableStateOf(CompanyIntelligenceSection.NEWS) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                CompanyLogo(s.symbol, 46, s.logoUrl)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceText)
                    Text("${s.symbol} • NSE", fontSize = 10.sp, color = IntelligenceMuted)
                }
                if (onWatchToggle != null) {
                    OutlinedButton(
                        onClick = onWatchToggle,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            if (watched) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (watched) "Remove from watchlist" else "Add to watchlist",
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (watched) "Watching" else "Watch", fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = IntelligenceLight)
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text("NSE DATA • 15 MIN DELAYED", color = IntelligenceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        String.format(Locale.US, "KSh %.2f", s.price),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = IntelligenceText
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (s.changeAvailable && s.change.isFinite()) {
                            formatHeaderChange(s.change, marketStatus, historyResult)
                        } else {
                            formatHeaderUnavailable(marketStatus, historyResult)
                        },
                        color = if (s.changeAvailable && s.change.isFinite()) {
                            if (s.change >= 0) IntelligenceGreen else IntelligenceRed
                        } else {
                            IntelligenceMuted
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Exchange-supplied NSE data • analysis only • no real trading",
                        color = IntelligenceMuted,
                        fontSize = 8.sp
                    )
                }
            }
        }

        item {
            CompanySectionNavigation(
                labels = listOf(CompanyIntelligenceSection.ABOUT, CompanyIntelligenceSection.INTELLIGENCE),
                selected = primarySection,
                onSelected = { primarySection = it }
            )
        }

        if (primarySection == CompanyIntelligenceSection.ABOUT) {
            item { SectionTitle("Business", "What does this company actually do?", Icons.Default.Business) }
            item {
                IntelligenceCard {
                    if (profile.description.isNotBlank()) Text(profile.description, fontSize = 12.sp, lineHeight = 18.sp, color = IntelligenceText)
                    else Text("Business description is not available from the current company-data response.", color = IntelligenceMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { MiniFact("Sector", profile.sector.ifBlank { "Not available" }) }
                        Box(Modifier.weight(1f)) { MiniFact("HQ", profile.headquarters.ifBlank { "Not available" }) }
                    }
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = IntelligenceDark)) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(38.dp), RoundedCornerShape(12.dp), Color.White.copy(alpha = .12f)) {
                                Icon(Icons.Default.Psychology, null, tint = Color(0xFF8BE0B3), modifier = Modifier.padding(8.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Intelligence", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Evidence first • no BUY/SELL instruction", color = Color(0xFFBFE8D0), fontSize = 9.sp)
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                        if (intelligenceLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF8BE0B3))
                                Spacer(Modifier.width(9.dp))
                                Text("Building the company evidence view…", color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                            }
                        } else {
                            Text(intelligenceView.summary, color = Color.White, fontSize = 11.sp, lineHeight = 17.sp)
                        }
                    }
                }
            }
        }

        if (primarySection == CompanyIntelligenceSection.INTELLIGENCE) {
        item {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = IntelligenceDark)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(34.dp), RoundedCornerShape(11.dp), Color.White.copy(alpha = .12f)) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF8BE0B3), modifier = Modifier.padding(7.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("NSE WATCHER ANALYST", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Ask the evidence, not the market", color = Color(0xFFBFE8D0), fontSize = 9.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Uses the company evidence available to NSE Watcher. It explains the data; it does not give BUY/SELL instructions.", color = Color.White.copy(alpha = .78f), fontSize = 8.sp, lineHeight = 12.sp)
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = analystQuestion,
                        onValueChange = { analystQuestion = it.take(1200) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF8BE0B3), unfocusedBorderColor = Color.White.copy(alpha = .25f), cursorColor = Color(0xFF8BE0B3)),
                        placeholder = { Text("Ask about performance, growth, valuation or evidence…", color = Color.White.copy(alpha = .45f), fontSize = 9.sp) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            analystScope.launch {
                                analystLoading = true
                                analystResult = AnalystCache.ask(s.symbol, analystQuestion)
                                analystLoading = false
                            }
                        },
                        enabled = !analystLoading && analystQuestion.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = IntelligenceGreen),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        if (analystLoading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Analysing…", fontSize = 10.sp)
                        } else {
                            Icon(Icons.Default.Psychology, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Ask Analyst", fontSize = 10.sp)
                        }
                    }
                    if (analystResult.message.isNotBlank() || analystResult.answer.isNotBlank() || analystResult.error.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        when {
                            analystResult.error.isNotBlank() -> Text("Analyst unavailable: " + analystResult.error, color = Color(0xFFFFC4C4), fontSize = 9.sp)
                            analystResult.message.isNotBlank() && analystResult.answer.isBlank() -> Text(analystResult.message, color = Color.White.copy(alpha = .82f), fontSize = 9.sp, lineHeight = 14.sp)
                            else -> {
                                Text(analystResult.answer, color = Color.White, fontSize = 10.sp, lineHeight = 15.sp)
                                if (analystResult.evidence.isNotEmpty()) {
                                    Spacer(Modifier.height(7.dp))
                                    Text("Evidence used", color = Color(0xFFBFE8D0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    analystResult.evidence.take(6).forEach { evidence ->
                                        Text(evidence.id + ": " + evidence.claim, color = Color.White.copy(alpha = .75f), fontSize = 8.sp, lineHeight = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        }



        item {
            CompanySectionNavigation(
                labels = listOf(
                    CompanyIntelligenceSection.PERFORMANCE,
                    CompanyIntelligenceSection.FINANCIALS,
                    CompanyIntelligenceSection.VALUATION,
                    CompanyIntelligenceSection.DIVIDENDS
                ),
                selected = metricsSection,
                onSelected = { metricsSection = it }
            )
        }

        if (metricsSection == CompanyIntelligenceSection.FINANCIALS) {
        item {
            IntelligenceCard {
                Text(
                    financialPeriodLabel(profile.financialPeriod.ifBlank { latestFinancialPeriod }),
                    color = IntelligenceMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
                SourceDateLine("Provider data updated", profile.financialProviderUpdatedAt, profile.financialPageCheckedAt)
                Spacer(Modifier.height(8.dp))
                MetricGrid(
                    metrics = listOf(
                        "Revenue" to formatFinancialValue(profile.revenue, profile.financialUnit),
                        "Profit" to formatFinancialValue(profile.profit, profile.financialUnit),
                        "EPS (Earnings Per Share)" to formatMetricValue("EPS", profile.eps),
                        "Net margin" to formatMetricValue("Net margin", profile.margin)
                    ),
                    fieldSources = intelligence.fieldSources,
                    fieldQuality = intelligence.fieldQuality
                )
            }
        }

        }

        if (metricsSection == CompanyIntelligenceSection.PERFORMANCE) {
        item {
            IntelligenceCard {
                MetricGrid(
                    metrics = listOf(
                        "Revenue growth (YoY)" to formatMetricValue("Revenue growth", profile.revenueGrowth),
                        "Profit growth (YoY)" to formatMetricValue("Profit growth", profile.profitGrowth),
                        "EPS growth (YoY)" to formatMetricValue("EPS growth", profile.epsGrowth)
                    ),
                    fieldSources = intelligence.fieldSources,
                    fieldQuality = intelligence.fieldQuality
                )
                Spacer(Modifier.height(8.dp))
                Text("Growth compares the latest reported annual figures with the previous comparable annual period.", color = IntelligenceMuted, fontSize = 9.sp)
            }
        }

        }

        if (metricsSection == CompanyIntelligenceSection.VALUATION) {
        item {
            IntelligenceCard {
                if (profile.ratioBasis.equals("Current", ignoreCase = true)) {
                    val basis = profile.ratioPeriod.takeIf { it.isNotBlank() }?.let { "Current ratios • period ending $it" }
                        ?: "Current ratios"
                    Text(basis, color = IntelligenceMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    SourceDateLine("Provider data updated", profile.ratioProviderUpdatedAt, profile.ratioPageCheckedAt)
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text("Current ratio data is unavailable from the structured ratio response.", color = IntelligenceMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                }
                MetricGrid(
                    metrics = listOf(
                        "P/E (Price-to-Earnings)" to formatMetricValue("P/E", profile.pe),
                        "P/B (Price-to-Book)" to formatMetricValue("P/B", profile.pb),
                        "ROE (Return on Equity)" to formatMetricValue("ROE", profile.roe),
                        "Debt / Equity" to formatMetricValue("Debt / Equity", profile.debtToEquity),
                        "Dividend yield" to formatMetricValue("Dividend yield", profile.dividendYield),
                        "Market cap" to formatMarketCap(profile.marketCap)
                    ),
                    fieldSources = intelligence.fieldSources,
                    fieldQuality = intelligence.fieldQuality
                )
                Spacer(Modifier.height(8.dp))
                Text("These ratios are the source's current snapshot; they are not FY 2025 historical ratio values.", color = IntelligenceMuted, fontSize = 9.sp)
            }
        }

        }

        if (metricsSection == CompanyIntelligenceSection.DIVIDENDS) {
        item {
            IntelligenceCard {
                if (intelligence.dividends.isEmpty()) {
                    Text("No dividend history was returned by the current provider response.", color = IntelligenceMuted, fontSize = 11.sp)
                } else {
                    intelligence.dividends.take(5).forEachIndexed { index, dividend ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), IntelligenceLight) { Icon(Icons.Default.Payments, null, tint = IntelligenceGreen, modifier = Modifier.padding(8.dp)) }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (dividend.amount.isNotBlank()) "KSh ${dividend.amount}" else "Dividend amount not supplied", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(listOf(dividend.exDate, dividend.paymentDate).filter(String::isNotBlank).joinToString(" • ").ifBlank { "Date not supplied" }, color = IntelligenceMuted, fontSize = 9.sp)
                            }
                            if (dividend.status.isNotBlank()) Text(dividend.status, color = IntelligenceGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        if (index < intelligence.dividends.take(5).lastIndex) HorizontalDivider(color = IntelligenceBorder)
                    }
                }
            }
        }

        }

        if (metricsSection == CompanyIntelligenceSection.PERFORMANCE) {
                item {
            IntelligenceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("${currencyLabel(s.price)}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceText)
                        Text("NSE • 15 min delayed", color = IntelligenceMuted, fontSize = 9.sp)
                    }
                    if ((period == "1D" || period == "NOW") && marketStatus.isKnown && !marketStatus.isOpen) {
                        Text(
                            "MARKET CLOSED",
                            color = IntelligenceMuted,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            textAlign = TextAlign.End
                        )
                    } else {
                        selectedPeriodReturn?.let { periodReturn ->
                            Text(
                                formatPeriodReturn(period, periodReturn),
                                color = if (periodReturn >= 0) IntelligenceGreen else IntelligenceRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    periods.forEach { value ->
                        FilterChip(
                            selected = period == value,
                            onClick = { period = value },
                            label = { Text(value, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IntelligenceGreen,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = period == value,
                                borderColor = IntelligenceMuted.copy(alpha = 0.65f),
                                selectedBorderColor = IntelligenceGreen
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    periodDescription(period),
                    color = IntelligenceText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                if (period == "NOW") {
                    val latestTimestamp = historyResult.sessionCloseAt
                        .ifBlank { historyResult.lastDate }
                        .ifBlank { historyResult.observedAt }
                    Text(
                        if (latestTimestamp.isNotBlank()) {
                            "Latest observation • " + formatChartTimestamp(latestTimestamp) + " • 15 min delayed"
                        } else {
                            "Latest available intraday observation • 15 min delayed"
                        },
                        color = IntelligenceMuted,
                        fontSize = 8.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (historyLoading) {
                    IntelligenceLoader("Loading $period market history", "Checking historical NSE data…")
                } else if (history.size >= 2) {
                    IntelligenceChart(
                        history,
                        if (period == "NOW") "1D" else period,
                        if ((selectedPeriodReturn ?: s.change) >= 0) IntelligenceGreen else IntelligenceRed
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${history.size} data points", color = IntelligenceMuted, fontSize = 8.sp)
                        if ((period == "1D" || period == "NOW") && marketStatus.isKnown && !marketStatus.isOpen) {
                            Text("Market closed", color = IntelligenceMuted, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                        } else {
                            selectedPeriodReturn?.let { Text(formatPeriodReturn(period, it), color = if (it >= 0) IntelligenceGreen else IntelligenceRed, fontWeight = FontWeight.Bold, fontSize = 8.sp) }
                        }
                    }
                } else {
                    Text("Historical market data is not available for this period.", color = IntelligenceMuted, fontSize = 10.sp)
                }
            }
        }

        if (period == "1D" || period == "NOW") {
            item {
                SessionAtGlance(
                    historyResult = historyResult,
                    marketStatus = marketStatus
                )
            }
        }

        item { WhyStockMovingSection(s.symbol) }

        }

        item {
            CompanySectionNavigation(
                labels = listOf(
                    CompanyIntelligenceSection.NEWS,
                    CompanyIntelligenceSection.EVIDENCE,
                    CompanyIntelligenceSection.ANALYSIS
                ),
                selected = researchSection,
                onSelected = { researchSection = it }
            )
        }

        if (researchSection == CompanyIntelligenceSection.NEWS) {
        item {
            IntelligenceCard {
                when {
                    newsLoading -> IntelligenceLoader("Loading company intelligence", "Checking recent announcements and news…")
                    news.isEmpty() -> Text("No recent company news or corporate actions were returned.", color = IntelligenceMuted, fontSize = 11.sp)
                    else -> news.take(5).forEachIndexed { index, item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.category.ifBlank { "Market" }, color = IntelligenceGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(7.dp))
                                Text(item.publishedAt.take(10).ifBlank { "Latest" }, color = IntelligenceMuted, fontSize = 8.sp)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                            if (item.summary.isNotBlank()) Text(item.summary, color = IntelligenceMuted, fontSize = 9.sp, maxLines = 2)
                        }
                        if (index < news.take(5).lastIndex) HorizontalDivider(color = IntelligenceBorder)
                    }
                }
            }
        }

        }

        if (researchSection == CompanyIntelligenceSection.EVIDENCE) {
                item {
            IntelligenceCard {
                if (intelligenceView.evidenceRecords.isEmpty()) {
                    Text("No normalized evidence records are available from the current response.", color = IntelligenceMuted, fontSize = 10.sp)
                } else {
                    intelligenceView.evidenceRecords.take(8).forEachIndexed { index, evidence ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(evidence.type.name.replace('_', ' '), color = IntelligenceGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(7.dp))
                                Text(evidence.source, color = IntelligenceMuted, fontSize = 8.sp, maxLines = 1)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(evidence.claim, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                            evidence.value?.takeIf { it.isNotBlank() }?.let { Text(it, color = IntelligenceMuted, fontSize = 9.sp, maxLines = 2) }
                            (evidence.publishedAt ?: evidence.observedAt)?.takeIf { it.isNotBlank() }?.let { Text(it.take(19), color = IntelligenceMuted, fontSize = 8.sp) }
                            evidence.sourceUrl?.takeIf { it.isNotBlank() }?.let { Text("Source available", color = IntelligenceGreen, fontSize = 8.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        if (index < intelligenceView.evidenceRecords.take(8).lastIndex) HorizontalDivider(color = IntelligenceBorder)
                    }
                    if (intelligenceView.evidenceRecords.size > 8) {
                        Spacer(Modifier.height(4.dp))
                        Text("+${intelligenceView.evidenceRecords.size - 8} more evidence records", color = IntelligenceMuted, fontSize = 8.sp)
                    }
                }
            }
        }

        }

        if (researchSection == CompanyIntelligenceSection.ANALYSIS) {
        item { SectionTitle("Risks to investigate", "Questions raised by the available evidence", Icons.Default.Warning) }
        item {
            IntelligenceCard {
                if (intelligenceView.risks.isEmpty()) {
                    Text("No automatic watchpoint was generated from the currently available evidence. This does not mean the company has no risks.", color = IntelligenceMuted, fontSize = 10.sp)
                } else {
                    intelligenceView.risks.forEach { Watchpoint(it) }
                }
            }
        }

        item { SectionTitle("Intelligence signals", "Deterministic evidence signals — no invented conclusions", Icons.Default.Insights) }
        item {
            IntelligenceCard {
                Text("Evidence coverage: ${intelligenceView.evidenceCoverage}", color = IntelligenceGreen, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Coverage: ${intelligenceView.quality.state} • ${intelligenceView.quality.availableCount}/5 evidence areas", color = IntelligenceMuted, fontSize = 9.sp)
                Spacer(Modifier.height(10.dp))
                SignalGroup("SUPPORTING", IntelligenceGreen, intelligenceView.signals.filter { it.type == CompanyIntelligenceEngine.SignalType.SUPPORTING }.map { it.text })
                SignalGroup("CAUTION", IntelligenceRed, intelligenceView.signals.filter { it.type == CompanyIntelligenceEngine.SignalType.CAUTION }.map { it.text })
                SignalGroup("UNKNOWN / NEEDS EVIDENCE", IntelligenceMuted, intelligenceView.unknowns)
            }
        }

        item { SectionTitle("Bull / Bear / Unknown", "A balanced view of the available evidence", Icons.Default.CompareArrows) }
        item {
            IntelligenceCard {
                EvidencePerspective("Supporting case", IntelligenceGreen, buildList {
                    if (profile.revenueGrowth.isNotBlank()) add("Revenue growth: ${profile.revenueGrowth}")
                    if (profile.profitGrowth.isNotBlank()) add("Profit growth: ${profile.profitGrowth}")
                    if (profile.roe.isNotBlank()) add("ROE: ${profile.roe}")
                    if (intelligence.dividends.isNotEmpty()) add("Dividend history is available for review")
                })
                EvidencePerspective("Counter-evidence", IntelligenceRed, buildList {
                    if (s.change < 0) add("Today's price change: ${String.format(Locale.US, "%+.2f%%", s.change)}")
                    if (profile.revenueGrowth.toDoubleOrNull()?.let { it < 0 } == true) add("Revenue growth is negative")
                    if (profile.profitGrowth.toDoubleOrNull()?.let { it < 0 } == true) add("Profit growth is negative")
                    if (profile.eps.toDoubleOrNull()?.let { it < 0 } == true) add("EPS is negative")
                })
                EvidencePerspective("Unknown / investigate", IntelligenceMuted, buildList {
                    if (profile.pe.isBlank()) add("P/E not available")
                    if (profile.pb.isBlank()) add("P/B not available")
                    if (profile.debtToEquity.isBlank()) add("Debt/equity not available")
                    if (news.isEmpty()) add("Recent events need verification from issuer/NSE sources")
                })
            }
        }

        item { SectionTitle("Company timeline", "Recent intelligence events in context", Icons.Default.Timeline) }
        item {
            IntelligenceCard {
                if (news.isEmpty()) {
                    Text("The timeline will populate when dated company intelligence is available.", color = IntelligenceMuted, fontSize = 10.sp)
                } else {
                    news.take(8).forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(Modifier.size(10.dp), RoundedCornerShape(50), color = IntelligenceGreen) {}
                                if (index < news.take(8).lastIndex) Box(Modifier.width(1.dp).height(42.dp).background(IntelligenceBorder))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
                                Text(item.publishedAt.take(10).ifBlank { "Recent" }, color = IntelligenceMuted, fontSize = 8.sp)
                                Text(item.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Ask NSE Watcher", "Questions to investigate before making your own decision", Icons.Default.Psychology) }
        item {
            IntelligenceCard {
                Text("Use these prompts as an analyst checklist. AI answers will be connected to the sourced evidence layer after the intelligence data pipeline is complete.", color = IntelligenceMuted, fontSize = 10.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(9.dp))
                listOf(
                    "Why did ${s.symbol} move recently?",
                    "Explain ${s.name} like I'm a beginner.",
                    "What changed in the latest company information?",
                    "What are the biggest risks I should investigate?",
                    "What evidence supports the current picture?"
                ).forEach { prompt ->
                    Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp), RoundedCornerShape(12.dp), color = IntelligenceLight) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QuestionMark, null, tint = IntelligenceGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(prompt, fontSize = 10.sp, color = IntelligenceText)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Beginner guide", "Understand the numbers before interpreting them", Icons.Default.School) }
        item {
            IntelligenceCard {
                listOf(
                    "P/E" to "Price compared with earnings per share. Compare it with the company's history and sector, not in isolation.",
                    "ROE" to "Return on equity. It describes how efficiently reported profit is generated from shareholders' equity.",
                    "EPS" to "Earnings per share. It shows the portion of reported earnings attributable to each share.",
                    "Dividend yield" to "Dividend relative to the share price. A higher yield is not automatically a better investment.",
                    "Debt / equity" to "A leverage measure comparing debt with shareholders' equity. Compare it over time and with peers."
                ).forEach { (term, explanation) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(term, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceGreen)
                        Text(explanation, fontSize = 9.sp, lineHeight = 14.sp, color = IntelligenceText)
                    }
                    HorizontalDivider(color = IntelligenceBorder)
                }
            }
        }

        item { SectionTitle("What to investigate next", "A practical research checklist", Icons.Default.Checklist) }
        item {
            IntelligenceCard {
                listOf(
                    "Read the latest results and compare revenue, profit and EPS with prior periods.",
                    "Check the latest issuer and NSE announcements for material events.",
                    "Compare valuation measures with the company's own history and relevant peers.",
                    "Review dividend consistency, payout dates and sustainability.",
                    "Look at the price chart alongside company events instead of treating price movement as an explanation."
                ).forEach { Watchpoint(it) }
            }
        }

        }

        if (researchSection == CompanyIntelligenceSection.EVIDENCE) {
        item {
            IntelligenceCard {
                EvidenceRow("Coverage", "${intelligenceView.quality.state} • ${intelligenceView.quality.availableCount}/5 areas")
                EvidenceRow("Evidence", "${intelligenceView.quality.evidenceCount} sourced claims returned")
                EvidenceRow("Evidence coverage", intelligenceView.evidenceCoverage)
            }
        }

        item {
            IntelligenceCard {
                EvidenceRow("Market price", "${s.source.ifBlank { "Market source unavailable" }} • ${when (s.freshnessMode) { "STALE" -> "stale observation"; "CURRENT_SESSION" -> "current session"; "END_OF_DAY" -> "end-of-day observation"; else -> "freshness unknown" }}")
                EvidenceRow("Company profile", if (intelligenceLoading) "Loading source data…" else "Field-level sources shown below")
                EvidenceRow("Dividends", if (intelligence.dividends.isNotEmpty()) "Source recorded per dividend event" else "Not available")
                EvidenceRow("News & actions", "MyStocks Africa company intelligence feed")
                if (intelligence.fetchedAt.isNotBlank()) EvidenceRow("App fetch time", intelligence.fetchedAt)
                Spacer(Modifier.height(6.dp))
                Text("NSE Watcher separates sourced facts from interpretation. Verify material announcements against the issuer or NSE before acting.", color = IntelligenceMuted, fontSize = 9.sp)
            }
        }

        item {
            IntelligenceCard {
                Text("Data quality & sources", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceText)
                Spacer(Modifier.height(6.dp))
                val materialFields = listOf(
                    "revenue" to "Revenue", "profit" to "Profit", "eps" to "EPS",
                    "roe" to "ROE", "margin" to "Net margin", "debtToEquity" to "Debt / equity",
                    "pe" to "P/E", "pb" to "P/B", "dividendYield" to "Dividend yield",
                    "marketCap" to "Market capitalization"
                )
                materialFields.forEach { (key, label) ->
                    val quality = intelligence.fieldQuality[key] ?: "UNKNOWN"
                    val sources = intelligence.fieldSources[key].orEmpty().joinToString(" + ")
                    EvidenceRow(label, "$quality${if (sources.isNotBlank()) " • $sources" else ""}")
                }
                if (intelligence.conflicts.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Conflicting source values", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IntelligenceRed)
                    intelligence.conflicts.entries.forEach { (field, values) ->
                        val label = materialFields.firstOrNull { it.first == field }?.second ?: field
                        EvidenceRow(label, values.joinToString(" vs ") { "${it.first}: ${it.second}" })
                    }
                }
                Text("CONFLICT means two available providers returned different values. NSE Watcher does not silently treat one as verified.", color = IntelligenceMuted, fontSize = 9.sp)
            }
        }

        item {
            Text("NSE Watcher is an analysis and education product. It does not execute real trades or guarantee returns.", color = IntelligenceMuted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
    }
}
}


@Composable
private fun SignalGroup(title: String, tint: Color, items: List<String>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title, color = tint, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        if (items.isEmpty()) Text("No signal from the currently available fields.", color = IntelligenceMuted, fontSize = 9.sp)
        items.take(4).forEach { Text("• $it", color = IntelligenceText, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun EvidencePerspective(title: String, tint: Color, items: List<String>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = tint, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        if (items.isEmpty()) Text("No sourced point available yet.", color = IntelligenceMuted, fontSize = 9.sp)
        items.take(4).forEach { Text("• $it", color = IntelligenceText, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun IntelligenceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = BorderStroke(1.dp, IntelligenceBorder)) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), IntelligenceLight) { Icon(icon, null, tint = IntelligenceGreen, modifier = Modifier.padding(7.dp)) }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = IntelligenceText)
            Text(subtitle, color = IntelligenceMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MetricGrid(
    metrics: List<Pair<String, String>>,
    fieldSources: Map<String, List<String>> = emptyMap(),
    fieldQuality: Map<String, String> = emptyMap()
) {
    val sourceKeys = mapOf(
        "Revenue" to "revenue",
        "Revenue growth (YoY)" to "revenueGrowth",
        "Profit" to "profit",
        "Profit growth (YoY)" to "profitGrowth",
        "EPS (Earnings Per Share)" to "eps",
        "EPS growth (YoY)" to "epsGrowth",
        "ROE (Return on Equity)" to "roe",
        "Debt / Equity" to "debtToEquity",
        "Net margin" to "margin",
        "P/E (Price-to-Earnings)" to "pe",
        "P/B (Price-to-Book)" to "pb",
        "Dividend yield" to "dividendYield",
        "Market cap" to "marketCap"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    val key = sourceKeys[label].orEmpty()
                    val sources = fieldSources[key].orEmpty()
                    val quality = fieldQuality[key].orEmpty()
                    val provenance = when {
                        quality == "CONFLICT" -> "Sources differ"
                        sources.size > 1 -> "2 sources"
                        sources.size == 1 -> sources.first().removePrefix("StockAnalysis / ").removeSuffix(" Market Intelligence")
                        else -> "Source unavailable"
                    }
                    Surface(Modifier.weight(1f), RoundedCornerShape(13.dp), color = IntelligenceLight) {
                        Column(Modifier.padding(10.dp)) {
                            Text(label, color = IntelligenceMuted, fontSize = 8.sp)
                            Text(value, color = IntelligenceText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IntelligenceLight.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    provenance,
                                    color = if (quality == "CONFLICT") IntelligenceRed else IntelligenceMuted,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniFact(label: String, value: String) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = IntelligenceLight) {
        Column(Modifier.padding(9.dp)) { Text(label, color = IntelligenceMuted, fontSize = 8.sp); Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
    }
}

@Composable
private fun Watchpoint(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFD58A00), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 10.sp, lineHeight = 15.sp, color = IntelligenceText)
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(92.dp), color = IntelligenceMuted, fontSize = 9.sp)
        Text(value, Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = IntelligenceText)
    }
}

@Composable
private fun IntelligenceLoader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(40.dp), RoundedCornerShape(12.dp), IntelligenceLight) { Icon(Icons.Default.AutoGraph, null, tint = IntelligenceGreen, modifier = Modifier.padding(9.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(subtitle, color = IntelligenceMuted, fontSize = 9.sp)
        }
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = IntelligenceGreen)
    }
}

@Composable
private fun IntelligenceChart(
    points: List<MyStocksCache.HistoryPoint>,
    period: String,
    tint: Color
) {
    val valid = points.filter { it.close.isFinite() && it.close > 0.0 }
    if (valid.size < 2) return

    val min = valid.minOf { it.close }
    val max = valid.maxOf { it.close }
    val range = (max - min).takeIf { it > 0.0 } ?: (max * 0.01).coerceAtLeast(1.0)
    val top = max + range * 0.08
    val bottom = (min - range * 0.08).coerceAtLeast(0.0)
    val chartRange = (top - bottom).coerceAtLeast(0.0001)
    val mid = (top + bottom) / 2.0

    var zoomX by remember(valid, period) { mutableFloatStateOf(1f) }
    var panX by remember(valid, period) { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val plotWidthPx = with(density) { (maxWidth - 42.dp).coerceAtLeast(0.dp).toPx() }

        LaunchedEffect(zoomX, plotWidthPx) {
            val maxPan = (plotWidthPx * (zoomX - 1f)).coerceAtLeast(0f)
            panX = panX.coerceIn(-maxPan, 0f)
        }

        val labels = chartAxisLabels(valid, period, zoomX)

        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(218.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(42.dp).fillMaxHeight().padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(priceAxis(top), color = IntelligenceMuted, fontSize = 8.sp, maxLines = 1)
                    Text(priceAxis(mid), color = IntelligenceMuted, fontSize = 8.sp, maxLines = 1)
                    Text(priceAxis(bottom), color = IntelligenceMuted, fontSize = 8.sp, maxLines = 1)
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .pointerInput(valid, period) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldZoom = zoomX
                                val newZoom = (oldZoom * zoom).coerceIn(1f, 6f)
                                val scaleRatio = newZoom / oldZoom

                                // Keep the data point under the pinch centre anchored while zooming.
                                val anchoredPan = centroid.x - (centroid.x - panX) * scaleRatio
                                val candidatePan = anchoredPan + pan.x
                                val maxPan = (plotWidthPx * (newZoom - 1f)).coerceAtLeast(0f)

                                zoomX = newZoom
                                panX = candidatePan.coerceIn(-maxPan, 0f)
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val contentWidth = size.width * zoomX

                        // Keep the chart horizontally interactive while the price axis stays fixed.
                        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                            val line = Path()
                            val area = Path()

                            valid.forEachIndexed { index, point ->
                                val x = panX + contentWidth * index / valid.lastIndex.coerceAtLeast(1)
                                val y = size.height - (((point.close - bottom) / chartRange).toFloat() * size.height)

                                if (index == 0) {
                                    line.moveTo(x, y)
                                    area.moveTo(x, size.height)
                                    area.lineTo(x, y)
                                } else {
                                    line.lineTo(x, y)
                                    area.lineTo(x, y)
                                }
                            }

                            area.lineTo(panX + contentWidth, size.height)
                            area.close()

                            drawPath(
                                area,
                                Brush.verticalGradient(
                                    listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.03f)),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )
                            drawPath(line, tint, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                        }
                    }
                }
            }

            // The X-axis uses the same transformed positions as the line, so labels stay
            // attached to their actual observations while the user pans/zooms.
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(42.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clipToBounds()
                ) {
                    labels.forEach { label ->
                        val xPx = panX + plotWidthPx * zoomX * label.index / valid.lastIndex.coerceAtLeast(1)
                        val labelWidthPx = with(density) {
                            (label.text.length.coerceAtLeast(3) * 4.5f).dp.toPx()
                        }
                        val clampedX = when {
                            label.index == 0 -> xPx.coerceAtLeast(labelWidthPx / 2f)
                            label.index == valid.lastIndex -> xPx.coerceAtMost(plotWidthPx - labelWidthPx / 2f)
                            else -> xPx
                        }
                        Text(
                            label.text,
                            color = IntelligenceMuted,
                            fontSize = 8.sp,
                            maxLines = 1,
                            modifier = Modifier.offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (clampedX - labelWidthPx / 2f).toInt(),
                                    0
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                chartAxisDescription(period),
                color = IntelligenceMuted,
                fontSize = 8.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            if (zoomX > 1.01f) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "Pinch to zoom • drag horizontally to inspect the timeline",
                    color = IntelligenceMuted,
                    fontSize = 7.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SessionAtGlance(
    historyResult: MyStocksCache.HistoryResult,
    marketStatus: MyStocksCache.MarketStatus
) {
    val open = historyResult.sessionOpen
    val latest = historyResult.sessionClose
    val hasSession = latest != null && latest > 0.0
    val sessionDate = historyResult.sessionCloseAt.takeIf { it.isNotBlank() }?.let(::formatChartTimestampDate)
    val observed = historyResult.sessionCloseAt
        .takeIf { it.isNotBlank() }
        ?.let(::formatChartTimestamp)
        ?: historyResult.observedAt.takeIf { it.isNotBlank() }?.let(::formatChartTimestamp)
    val nextOpen = marketStatus.nextOpen.takeIf { it.isNotBlank() }?.let(::formatChartTimestamp)
    val known = marketStatus.isKnown
    val openSession = known && marketStatus.isOpen

    SectionTitle(
        "Market status",
        when {
            openSession -> "NSE session • 15 min delayed"
            known -> "Market is closed"
            else -> "Session status is currently unavailable"
        },
        Icons.Default.Schedule
    )

    IntelligenceCard {
        when {
            !known -> {
                Text(
                    "Market status is currently unavailable. No current-session state is inferred.",
                    color = IntelligenceMuted,
                    fontSize = 10.sp
                )
            }
            !hasSession -> {
                Text(
                    if (openSession) {
                        "The latest NSE session observation is not available."
                    } else {
                        "The last completed NSE session close is not available."
                    },
                    color = IntelligenceMuted,
                    fontSize = 10.sp
                )
                nextOpen?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Next regular session: $it", color = IntelligenceMuted, fontSize = 8.sp)
                }
            }
            openSession -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    open?.takeIf { it > 0.0 }?.let {
                        Box(Modifier.weight(1f)) {
                            MiniFact("OPEN", String.format(Locale.US, "KSh %.2f", it))
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        MiniFact("LATEST", String.format(Locale.US, "KSh %.2f", latest))
                    }
                }
                Spacer(Modifier.height(9.dp))
                observed?.let {
                    Text("Latest observation: $it", color = IntelligenceMuted, fontSize = 8.sp)
                }
                Text(
                    "Market open • price data is exchange-supplied and 15 min delayed.",
                    color = IntelligenceMuted,
                    fontSize = 8.sp
                )
            }
            else -> {
                Text(
                    "MARKET CLOSED",
                    color = IntelligenceText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                sessionDate?.let {
                    Text("Closed: $it", color = IntelligenceMuted, fontSize = 9.sp)
                }
                observed?.let {
                    Text("Close observation: $it", color = IntelligenceMuted, fontSize = 8.sp)
                }
                Text(
                    "Closed at ${String.format(Locale.US, "KSh %.2f", latest)}",
                    color = IntelligenceText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                nextOpen?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Next regular session: $it", color = IntelligenceMuted, fontSize = 8.sp)
                }
            }
        }
    }
}
private fun formatHeaderUnavailable(
    marketStatus: MyStocksCache.MarketStatus,
    historyResult: MyStocksCache.HistoryResult
): String {
    val timestamp = historyResult.sessionCloseAt
        .ifBlank { historyResult.lastDate }
        .ifBlank { historyResult.observedAt }
        .takeIf { it.isNotBlank() }
        ?.let(::formatCompactChartTimestamp)

    val state = when {
        !marketStatus.isKnown -> "STATUS UNKNOWN"
        marketStatus.isOpen -> "LATEST"
        else -> "CLOSE"
    }

    return buildString {
        append("Change unavailable")
        timestamp?.let {
            append(" • ")
            append(it)
            append(" EAT")
        }
        append(" • ")
        append(state)
    }
}

private fun formatHeaderChange(
    change: Double,
    marketStatus: MyStocksCache.MarketStatus,
    historyResult: MyStocksCache.HistoryResult
): String {
    if (marketStatus.isKnown && !marketStatus.isOpen) {
        val closedAt = historyResult.sessionCloseAt
            .ifBlank { historyResult.lastDate }
            .ifBlank { historyResult.observedAt }
            .takeIf { it.isNotBlank() }
            ?.let(::formatCompactChartTimestamp)
        return if (closedAt != null) {
            "MARKET CLOSED • Closed ${closedAt} EAT"
        } else {
            "MARKET CLOSED"
        }
    }

    val timestamp = historyResult.sessionCloseAt
        .ifBlank { historyResult.lastDate }
        .ifBlank { historyResult.observedAt }
        .takeIf { it.isNotBlank() }
        ?.let(::formatCompactChartTimestamp)

    val state = if (!marketStatus.isKnown) "STATUS UNKNOWN" else "LATEST"

    return buildString {
        append(String.format(Locale.US, "%+.2f%%", change))
        timestamp?.let {
            append(" • ")
            append(it)
            append(" EAT")
        }
        append(" • ")
        append(state)
    }
}

private fun formatChartTimestamp(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("dd MMM yy • h:mm a", Locale.US)) + " EAT"
}.getOrElse { raw.take(19) }

private fun formatCompactChartTimestamp(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("dd MMM yy • h:mm a", Locale.US))
}.getOrElse { raw.take(19) }

private fun formatChartTimestampDate(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("EEE, dd MMM yy", Locale.US))
}.getOrElse { raw.take(10) }

private data class ChartLabel(val index: Int, val text: String)

private fun chartAxisLabels(
    points: List<MyStocksCache.HistoryPoint>,
    period: String,
    zoomX: Float = 1f
): List<ChartLabel> {
    val dated = points.mapIndexedNotNull { index, point ->
        parseChartDate(point.date)?.let { ChartLabel(index, formatChartDate(it, period)) }
    }
    if (dated.size < 2) return fallbackChartLabels(period).mapIndexed { index, text -> ChartLabel(index, text) }

    // At normal scale keep the chart calm. As the user zooms in, reveal more
    // real observations rather than fabricating intermediate points.
    val baseCount = when (period) {
        "1D" -> 5
        "1W" -> 5
        "1M" -> 5
        "3M", "6M" -> 4
        "1Y" -> 6
        "3Y", "5Y" -> 4
        else -> 5
    }

    val unique = dated.distinctBy { it.text }
    if (unique.size < 2) {
        return listOf(
            dated.firstOrNull() ?: ChartLabel(0, fallbackChartLabels(period).first()),
            dated.lastOrNull() ?: ChartLabel(points.lastIndex, fallbackChartLabels(period).last())
        ).distinctBy { it.index }
    }
    val maxLabelCount = unique.size.coerceAtMost(12)
    val targetCount = (baseCount * zoomX).toInt().coerceIn(2, maxLabelCount)
    return evenlySpacedLabels(unique, targetCount)
}

private fun evenlySpacedLabels(labels: List<ChartLabel>, count: Int): List<ChartLabel> {
    if (labels.size <= 1) return labels
    if (count >= labels.size) return labels
    return (0 until count).map { i ->
        val index = kotlin.math.round(
            i * (labels.lastIndex.toDouble() / (count - 1).coerceAtLeast(1))
        ).toInt()
        labels[index]
    }.distinctBy { it.text }
}

private fun parseChartDate(raw: String): java.time.LocalDateTime? {
    val value = raw.trim()
    if (value.isBlank()) return null

    return runCatching {
        Instant.parse(value).atZone(ZoneId.of("Africa/Nairobi")).toLocalDateTime()
    }.getOrElse {
        runCatching { LocalDate.parse(value).atStartOfDay() }.getOrNull()
    }
}

private fun formatChartDate(
    dateTime: java.time.LocalDateTime,
    period: String
): String = when (period) {
    "1D" -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    "1W" -> dateTime.format(DateTimeFormatter.ofPattern("EEE dd", Locale.US))
    "1M" -> dateTime.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))
    "3M", "6M", "1Y" -> dateTime.format(DateTimeFormatter.ofPattern("MMM", Locale.US))
    "3Y", "5Y" -> dateTime.format(DateTimeFormatter.ofPattern("yyyy", Locale.US))
    else -> dateTime.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))
}

private fun chartAxisDescription(period: String): String = when (period) {
    "1D" -> "Time • Nairobi trading session"
    "1W" -> "Trading days"
    "1M" -> "Trading dates"
    "3M", "6M" -> "Months across the selected period"
    "1Y" -> "Months across the selected year"
    "3Y", "5Y" -> "Years across the selected period"
    else -> "Time"
}

private fun fallbackChartLabels(period: String): List<String> = when (period) {
    "1D" -> listOf("Start", "Mid", "Now")
    "1W" -> listOf("Start", "Mid", "Now")
    "1M" -> listOf("Start", "Mid", "Now")
    "3M", "6M" -> listOf("Start", "Mid", "Now")
    "1Y" -> listOf("Start", "Mid", "Now")
    "3Y", "5Y" -> listOf("Start", "Mid", "Now")
    else -> listOf("Start", "Now")
}

@Composable
private fun CompanyLogo(symbol: String, size: Int, logoUrl: String? = null) {
    val model = logoUrl?.takeIf { it.isNotBlank() } ?: "https://mystocks.africa/logos/${symbol.lowercase(Locale.US)}-ke.svg"
    Surface(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)), RoundedCornerShape(12.dp), IntelligenceLight) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            AsyncImage(model = model, contentDescription = symbol, modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            Text(symbol.take(3), color = IntelligenceGreen, fontWeight = FontWeight.ExtraBold, fontSize = 7.sp)
        }
    }
}

private fun currencyLabel(value: Double): String = String.format(Locale.US, "KSh %.2f", value)

private fun priceAxis(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun valueOrMissing(value: String): String = value.trim().takeIf { it.isNotBlank() } ?: "Not available"

private fun formatFinancialValue(value: String, unit: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "Not available"
    if (!unit.equals("Millions KES", ignoreCase = true)) {
        return clean
    }
    val millions = clean.replace(",", "").toDoubleOrNull() ?: return clean
    return if (millions >= 1000.0) {
        String.format(Locale.US, "KSh %.2fB", millions / 1000.0)
    } else {
        String.format(Locale.US, "KSh %,.2fM", millions)
    }
}

private fun formatMetricValue(label: String, value: String): String {
    val clean = value.trim()
    if (clean.isBlank() || clean == "-" || clean.equals("n/a", ignoreCase = true)) return "Not available"
    val numeric = clean.removeSuffix("%").replace(",", "").toDoubleOrNull() ?: return clean
    return when (label) {
        "EPS", "EPS (Earnings Per Share)" -> String.format(Locale.US, "KSh %.2f / share", numeric)
        "Revenue growth", "Profit growth", "EPS growth" ->
            String.format(Locale.US, "%+.2f%%", numeric)
        "ROE", "Net margin", "Dividend yield" ->
            String.format(Locale.US, "%.2f%%", numeric)
        "P/E", "P/B", "Debt / Equity" -> String.format(Locale.US, "%.2f×", numeric)
        else -> clean
    }
}

private fun formatMarketCap(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "Not available"
    val absoluteKsh = clean.replace(",", "").toDoubleOrNull() ?: return clean
    return when {
        absoluteKsh >= 1_000_000_000_000.0 -> String.format(Locale.US, "KSh %.2fT", absoluteKsh / 1_000_000_000_000.0)
        absoluteKsh >= 1_000_000_000.0 -> String.format(Locale.US, "KSh %.2fB", absoluteKsh / 1_000_000_000.0)
        absoluteKsh >= 1_000_000.0 -> String.format(Locale.US, "KSh %.2fM", absoluteKsh / 1_000_000.0)
        else -> String.format(Locale.US, "KSh %,.0f", absoluteKsh)
    }
}

@Composable
private fun SourceDateLine(label: String, providerUpdatedAt: String, pageCheckedAt: String) {
    if (providerUpdatedAt.isBlank() && pageCheckedAt.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        providerUpdatedAt.takeIf { it.isNotBlank() }?.let {
            Text("$label: ${formatSourceDate(it)}", color = IntelligenceMuted, fontSize = 8.sp)
        }
        pageCheckedAt.takeIf { it.isNotBlank() }?.let {
            Text("Source page checked: ${formatSourceDate(it)}", color = IntelligenceMuted, fontSize = 8.sp)
        }
    }
}

private fun formatSourceDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
}.getOrElse { raw }

private fun financialPeriodLabel(period: String): String {
    val clean = period.trim()
    if (clean.isBlank()) return "Annual figures • Latest reported period"
    if (clean.contains("FY ", ignoreCase = true)) {
        val parts = clean.split(" • ", limit = 2)
        return if (parts.size == 2) {
            "Annual figures • ${parts[0]} • year ended ${parts[1]}"
        } else {
            "Annual figures • ${parts[0]}"
        }
    }
    return "Annual figures • $clean"
}

private fun periodDescription(period: String): String = when (period) {
    "1D" -> "Current trading session"
    "1W" -> "Past 1 week"
    "1M" -> "Past 1 month"
    "3M" -> "Past 3 months"
    "6M" -> "Past 6 months"
    "1Y" -> "Past 1 year"
    "3Y" -> "Past 3 years"
    "5Y" -> "Past 5 years"
    "NOW" -> "Latest available intraday data • 15 min delayed"
    else -> period
}

private fun formatPeriodReturn(period: String, value: Double): String {
    val label = when (period) {
        "1D" -> "session"
        "1W" -> "1 week"
        "1M" -> "1 month"
        "3M" -> "3 months"
        "6M" -> "6 months"
        "1Y" -> "1 year"
        "3Y" -> "3 years"
        "5Y" -> "5 years"
        "NOW" -> "latest session"
        else -> period
    }
    return String.format(Locale.US, "%+.2f%% $label", value)
}

private fun percentReturn(values: List<Double>): Double? {
    val valid = values.filter { it.isFinite() && it > 0.0 }
    if (valid.size < 2) return null
    val first = valid.first()
    return ((valid.last() - first) / first) * 100.0
}

private fun formatSigned(value: Double): String = String.format(Locale.US, "%+.1f%%", value)