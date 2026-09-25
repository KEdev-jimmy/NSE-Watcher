package ke.co.nsewatcher

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.AnalystCache
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.CompanyIntelligenceEngine
import ke.co.nsewatcher.data.MovementIntelligenceCache
import ke.co.nsewatcher.data.MyStocksCache
import kotlinx.coroutines.launch
import java.util.Locale

internal val ResearchBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
internal val ResearchCard: Color
    @Composable get() = MaterialTheme.colorScheme.surface
internal val ResearchRaised: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
internal val ResearchBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline
internal val ResearchGreen: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val ResearchText: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground
internal val ResearchMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val ResearchRed: Color
    @Composable get() = MaterialTheme.colorScheme.error
private val ResearchLinkBlue: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
internal val CompanyResearchColors: ColorScheme
    @Composable get() = MaterialTheme.colorScheme

@Composable
internal fun CompanyResearchScreen(
    stock: Stock, session: CompanyResearchPresentation.Session,
    market: MyStocksCache.MarketStatus, intelligence: CompanyIntelligenceCache.Result,
    fundamentalsLoading: Boolean, news: List<NewsItem>, newsLoading: Boolean, newsError: String?,
    movement: MovementIntelligenceCache.Result, movementLoading: Boolean,
    deterministic: CompanyIntelligenceEngine.Result, movementContext: CompanyMovementContext,
    analyst: AnalystCache.Result, analystLoading: Boolean, onAnalysis: () -> Unit,
    watched: Boolean, onWatchToggle: (() -> Unit)?, back: () -> Unit, openNews: (NewsItem) -> Unit,
    openPractice: () -> Unit, onRefresh: () -> Unit, refreshing: Boolean, selectedRange: String, onRange: (String) -> Unit,
    chart: MyStocksCache.HistoryResult, chartLoading: Boolean, oneYearChart: MyStocksCache.HistoryResult,
    rangeReturns: Map<String, Double?>, sessionLoading: Boolean, showChartGrid: Boolean
) {
    var tab by rememberSaveable(stock.symbol) { mutableStateOf("Overview") }
    var selectedMetric by remember(stock.symbol) { mutableStateOf<ResearchMetric?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun selectTab(value: String) {
        tab = value
        if (value == "Analysis") onAnalysis()
        scope.launch { listState.animateScrollToItem(0) }
    }

    MaterialTheme(colorScheme = CompanyResearchColors) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(ResearchBackground),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                PremiumCompanyTopBar(
                    stock = stock,
                    watched = watched,
                    onWatchToggle = onWatchToggle,
                    back = back
                )
            }
            item {
                PremiumCompanyIdentity(
                    stock = stock,
                    profile = intelligence.profile,
                    watched = watched
                )
            }
            item {
                PremiumCompanyTabs(selected = tab, onSelected = ::selectTab)
            }

            when (tab) {
                "Overview" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        PremiumCompanyOverview(
                            stock = stock,
                            session = session,
                            market = market,
                            intelligence = intelligence,
                            news = news,
                            selectedRange = selectedRange,
                            onRange = onRange,
                            chart = chart,
                            chartLoading = chartLoading,
                            oneYearChart = oneYearChart,
                            rangeReturns = rangeReturns,
                            showChartGrid = showChartGrid,
                            onRefresh = onRefresh,
                            openPractice = openPractice,
                            openAnalysis = { selectTab("Analysis") }
                        )
                    }
                }

                "Financials" -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        PremiumCompanyFinancials(
                            result = intelligence,
                            loading = fundamentalsLoading,
                            onRefresh = onRefresh,
                            onMetric = { selectedMetric = it }
                        )
                    }
                }

                "News" -> {
                    when {
                        newsLoading -> item { ResearchLoading("Loading company news…") }
                        newsError != null -> item {
                            ResearchNotice(
                                "Company news unavailable",
                                "We couldn’t load the latest company stories.",
                                onRefresh
                            )
                        }
                        news.isEmpty() -> item {
                            ResearchNotice(
                                "No company news returned",
                                "No company stories are available in the current feed.",
                                onRefresh
                            )
                        }
                        else -> items(news, key = { "company-news-${it.id}" }) { story ->
                            ResearchNewsCard(story) { openNews(story) }
                        }
                    }
                }

                "Analysis" -> {
                    item {
                        ResearchPanel {
                            ResearchTitle("Observed market movement")
                            ResearchBody(CompanyResearchPresentation.meaning(session.dailyChange))
                            ResearchCaption("Quote source: ${session.source} • ${CompanyResearchPresentation.date(session.observedAt)}")
                            rangeReturns["1M"]?.let {
                                ResearchBody("The available one-month price return is ${CompanyResearchPresentation.percent(it)}. This excludes dividends.")
                            }
                            session.sinceOpen?.let {
                                ResearchCaption("Since observed open: ${CompanyResearchPresentation.percent(it)}. This is different from change versus the previous close.")
                            }
                            ResearchCaption("These observations describe available data; they do not establish fair value or predict the next price move.")
                        }
                    }
                    item { ResearchDeterministicAnalysis(deterministic) }
                    item { ResearchMovementContext(movementContext) }
                    item { ResearchMovement(movement, movementLoading, onRefresh) }
                    item { ResearchAnalyst(analyst, analystLoading) }
                    item {
                        ResearchPanel {
                            ResearchTitle("Sources & evidence")
                            ResearchCaption("Review the source, reporting period and update date behind each claim.")
                            if (fundamentalsLoading) {
                                ResearchLoading("Loading company evidence…")
                            } else if (intelligence.evidence.isEmpty()) {
                                ResearchCaption("No additional company evidence was returned.")
                            }
                        }
                    }
                    items(intelligence.evidence) { record -> ResearchEvidence(record) }
                }

                "About" -> item {
                    ResearchAbout(stock, intelligence, fundamentalsLoading, onRefresh)
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ResearchCaption(
                        "Research with evidence. Prices and reports may cover different dates.",
                        Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = ResearchGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh company data", tint = ResearchGreen)
                        }
                    }
                }
            }
        }

        selectedMetric?.let { metric ->
            AlertDialog(
                onDismissRequest = { selectedMetric = null },
                containerColor = ResearchCard,
                title = { Text(metric.label, color = ResearchText) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ResearchBody(metric.value)
                        ResearchBody(metric.explanation)
                        ResearchCaption("Period: ${metric.period.ifBlank { "Not supplied" }}")
                        ResearchCaption(
                            "Source: " + intelligence.fieldSources[metric.key].orEmpty()
                                .joinToString(" • ")
                                .ifBlank { "Not supplied" }
                        )
                        intelligence.conflicts[metric.key].orEmpty().forEach { (source, value) ->
                            ResearchBody("$source: $value")
                        }
                        if (intelligence.fieldQuality[metric.key] == "CONFLICT") {
                            ResearchCaption("Sources differ. Check the reporting period and source before comparing this value.")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedMetric = null
                        selectTab("Analysis")
                    }) {
                        Text("View evidence", color = ResearchGreen)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedMetric = null }) {
                        Text("Close", color = ResearchMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun ResearchCompanyHeader(stock: Stock, profile: CompanyIntelligenceCache.Profile, session: CompanyResearchPresentation.Session, market: MyStocksCache.MarketStatus, loading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(ResearchRaised), contentAlignment = Alignment.Center) {
                Text(stock.symbol.take(4), color = ResearchGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val url = stock.logoUrl?.takeIf { it.isNotBlank() } ?: "https://mystocks.africa/logos/${stock.symbol.lowercase(Locale.US)}-ke.svg"
                var loaded by remember(url) { mutableStateOf(false) }
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize().background(if (loaded) ResearchRaised else Color.Transparent).padding(6.dp), contentScale = ContentScale.Fit, onSuccess = { loaded = true }, onError = { loaded = false })
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stock.name, color = ResearchText, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                ResearchCaption(stock.symbol + " · " + profile.sector.ifBlank { stock.sector.takeUnless { it.isBlank() || it == "Other" } ?: "Sector unavailable" })
            }
        }
        Text(if (loading && session.latest == null) "Loading price…" else CompanyResearchPresentation.money(session.latest), color = ResearchText, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Text(session.dailyChange?.let { CompanyResearchPresentation.percent(it) + " vs previous close" } ?: "Daily change unavailable", color = researchChangeColor(session.dailyChange), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        val state = when { !market.isKnown -> "Market status unavailable"; market.isOpen -> "Market open"; else -> "Market closed" }
        val delay = stock.delayMinutes?.takeIf { it > 0 } ?: if (session.source.contains("MyStocks", true)) 15 else null
        val freshness = when {
            session.latest == null -> "Price unavailable"
            CompanyResearchPresentation.timestamp(session.observedAt) == null -> "Observation time unavailable"
            delay != null -> "$delay-min delayed"
            else -> "Delay unavailable"
        }
        ResearchCaption("$state · $freshness\nAs of ${CompanyResearchPresentation.date(session.observedAt)}")
    }
}

@Composable
internal fun researchChangeColor(value: Double?): Color = when { value == null -> ResearchMuted; value < 0 -> ResearchRed; value > 0 -> ResearchGreen; else -> ResearchMuted }

@Composable
private fun ResearchReturn(label: String, value: Double?, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value?.let { CompanyResearchPresentation.percent(it) } ?: "—", color = researchChangeColor(value), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        ResearchCaption(label)
    }
}

@Composable
internal fun ResearchPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = ResearchCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, ResearchBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun ResearchTitle(text: String) { Text(text, color = ResearchText, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
@Composable internal fun ResearchBody(text: String) { Text(text, color = ResearchText, fontSize = 14.sp, lineHeight = 21.sp) }
@Composable internal fun ResearchCaption(text: String, modifier: Modifier = Modifier) { Text(text, modifier, color = ResearchMuted, fontSize = 12.sp, lineHeight = 18.sp) }

@Composable
private fun ResearchFactGrid(facts: List<Pair<String, String>>) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    facts.chunked(if (largeText) 1 else 2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { (label, value) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ResearchCaption(label)
                    Text(value, color = ResearchText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable internal fun ResearchLoading(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = ResearchGreen, strokeWidth = 2.dp)
        ResearchCaption(label, Modifier.weight(1f))
    }
}

@Composable
private fun ResearchNotice(title: String, detail: String, retry: () -> Unit) {
    ResearchPanel {
        ResearchTitle(title); ResearchCaption(detail)
        TextButton(onClick = retry) { Text("Try again", color = ResearchGreen) }
    }
}

@Composable
private fun ResearchDataStatus(loading: Boolean, error: String?, partial: Boolean, retry: () -> Unit) {
    when {
        loading -> ResearchLoading("Loading financial reports…")
        error != null -> ResearchNotice("Company information unavailable", "The service did not return company information. Market observations are shown separately.", retry)
        partial -> ResearchCaption("Partial coverage · Some company fields are unavailable.")
    }
}

internal data class ResearchMetric(val key: String, val label: String, val value: String, val period: String, val explanation: String)

internal fun researchMetrics(p: CompanyIntelligenceCache.Profile): List<ResearchMetric> {
    fun value(raw: String) = raw.trim().takeUnless { it.isBlank() || it == "-" || it.equals("n/a", true) } ?: "Unavailable"
    fun metric(key: String, label: String, raw: String, explanation: String, ratios: Boolean = false) =
        ResearchMetric(key, label, value(raw), if (ratios) p.ratioPeriod else p.financialPeriod, explanation)
    return listOf(
        metric("revenue", "Revenue", CompanyResearchPresentation.financialValue(p.revenue, p.financialUnit), "Income from the company’s main activities. Compare equivalent reporting periods and units."),
        metric("profit", "Net profit", CompanyResearchPresentation.financialValue(p.profit, p.financialUnit), "Profit after expenses and applicable taxes. A negative value represents a loss."),
        metric("eps", "EPS", p.eps, "Earnings per share: the profit attributable to each ordinary share. Check the report’s currency and share basis."),
        metric("roe", "Return on equity", p.roe, "Profit relative to shareholders’ equity. Compare with the company’s own history and sector."),
        metric("margin", "Net margin", p.margin, "The share of revenue remaining as profit. Accounting and business models affect comparisons."),
        metric("debtToEquity", "Debt / equity", p.debtToEquity, "Debt compared with shareholders’ equity. Banks and non-financial companies should not be judged using the same threshold."),
        metric("pe", "P/E", p.pe, "Share price relative to earnings per share. A low ratio alone does not make a share good value.", true),
        metric("pb", "P/B", p.pb, "Share price relative to book value per share. Asset quality and the sector matter.", true),
        metric("dividendYield", "Dividend yield", p.dividendYield, "Reported dividend relative to share price. Historical dividends are not a promise of future payments.", true),
        metric("marketCap", "Market value", p.marketCap, "Total market value of the company’s shares. Check the supplied currency and units.", true)
    )
}

@Composable
private fun ResearchFinancials(result: CompanyIntelligenceCache.Result, onMetric: (ResearchMetric) -> Unit) {
    ResearchPanel {
        ResearchTitle("Financials")
        ResearchCaption("Reported period: ${result.profile.financialPeriod.ifBlank { "Unavailable" }}")
        ResearchCaption("Provider units: ${result.profile.financialUnit.ifBlank { "Not supplied" }} · Tap a figure for context and sources.")
        val metrics = researchMetrics(result.profile)
        val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
        metrics.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { metric ->
                    Surface(
                        onClick = { onMetric(metric) }, modifier = Modifier.weight(1f),
                        color = ResearchRaised, shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ResearchCaption(metric.label)
                            Text(metric.value, color = ResearchText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            val conflict = result.fieldQuality[metric.key] == "CONFLICT"
                            Text(if (conflict) "Sources differ" else result.fieldSources[metric.key].orEmpty().joinToString(" • ").ifBlank { "Source unavailable" },
                                color = if (conflict) Color(0xFFFFC66D) else ResearchMuted, fontSize = 10.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (result.profile.ratioPeriod.isNotBlank()) ResearchCaption("Valuation period: ${result.profile.ratioPeriod}")
        if (result.profile.ratioBasis.isNotBlank()) ResearchCaption("Valuation basis: ${result.profile.ratioBasis}")
        ResearchDates(result.profile.financialProviderUpdatedAt, result.profile.financialPageCheckedAt)
    }
}

@Composable
private fun ResearchFinancialHistory(result: CompanyIntelligenceCache.Result) {
    ResearchPanel {
        ResearchTitle("Reported financial history")
        if (result.financialHistory.isEmpty()) ResearchCaption("Comparable historical statements were not returned.")
        else {
            ResearchCaption("Values are shown in their reported periods. Compare like-for-like periods and units.")
            result.financialHistory.reversed().forEach { point ->
                HorizontalDivider(color = ResearchBorder)
                Text(point.period.ifBlank { "Period unavailable" }, color = ResearchGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                ResearchFactGrid(listOf(
                    "Revenue" to CompanyResearchPresentation.financialValue(point.revenue, result.profile.financialUnit),
                    "Net profit" to CompanyResearchPresentation.financialValue(point.profit, result.profile.financialUnit),
                    "EPS" to point.eps.ifBlank { "Unavailable" }
                ))
                ResearchCaption("Source: ${point.source.ifBlank { "Unavailable" }}")
                ResearchDates(point.providerUpdatedAt, point.pageCheckedAt)
            }
        }
    }
}

@Composable
private fun ResearchDividends(result: CompanyIntelligenceCache.Result) {
    ResearchPanel {
        ResearchTitle("Dividends")
        if (result.dividends.isEmpty()) ResearchCaption("No dividend records were returned. This does not mean the company has never paid dividends.")
        result.dividends.forEach { d ->
            HorizontalDivider(color = ResearchBorder)
            ResearchBody(listOf(d.type, d.amount).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Dividend record" })
            ResearchCaption("Status: ${d.status.ifBlank { "Not supplied" }}")
            ResearchFactGrid(listOf("Ex-dividend" to CompanyResearchPresentation.date(d.exDate), "Payment" to CompanyResearchPresentation.date(d.paymentDate)))
            if (d.declaredDate.isNotBlank()) ResearchCaption("Declared: ${CompanyResearchPresentation.date(d.declaredDate)}")
        }
    }
}

@Composable
private fun ResearchFundamentalContext(result: CompanyIntelligenceCache.Result, loading: Boolean) {
    ResearchPanel {
        ResearchTitle("Business performance")
        if (loading) ResearchLoading("Loading reported figures…")
        else {
            ResearchCaption("Reported period: ${result.profile.financialPeriod.ifBlank { "Unavailable" }}")
            val growth = listOf("Revenue growth" to result.profile.revenueGrowth, "Profit growth" to result.profile.profitGrowth, "EPS growth" to result.profile.epsGrowth)
            ResearchFactGrid(growth.map { it.first to it.second.ifBlank { "Unavailable" } })
            ResearchCaption("Growth figures are provider-reported. A rising share price and improving profits are different observations; neither proves the other.")
            if (result.conflicts.isNotEmpty()) ResearchCaption("Some sources report different values. Open the financial metrics and source records to inspect the differences.")
        }
    }
}

@Composable
private fun ResearchDeterministicAnalysis(result: CompanyIntelligenceEngine.Result) {
    ResearchPanel {
        ResearchTitle("NSE Watcher evidence assessment")
        ResearchCaption("${result.quality.state} • ${result.evidenceCoverage}")
        ResearchBody(result.summary)

        if (result.signals.isNotEmpty()) {
            HorizontalDivider(color = ResearchBorder)
            ResearchCaption("Observed signals")
            result.signals.take(6).forEach { signal ->
                Text(signal.type.name.replace('_', ' '), color = ResearchGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                ResearchBody(signal.text)
                if (signal.evidence.isNotBlank()) ResearchCaption("Evidence: ${signal.evidence}")
            }
        }
        if (result.risks.isNotEmpty()) {
            HorizontalDivider(color = ResearchBorder)
            ResearchCaption("Watchpoints")
            result.risks.take(4).forEach { ResearchBody("• $it") }
        }
        if (result.unknowns.isNotEmpty()) {
            HorizontalDivider(color = ResearchBorder)
            ResearchCaption("Still unknown")
            result.unknowns.take(4).forEach { ResearchCaption("• $it") }
        }
        ResearchCaption("This assessment is generated by deterministic NSE Watcher rules from sourced data. It is not an AI prediction or an investment instruction.")
    }
}

@Composable
internal fun ResearchMovementContext(context: CompanyMovementContext) {
    ResearchPanel {
        ResearchTitle("Broader movement context")
        if (context.companyChange == null) {
            ResearchCaption(context.interpretation)
        } else {
            ResearchFactGrid(
                listOf(
                    "Company" to CompanyResearchPresentation.percent(context.companyChange),
                    "Sector peers" to (context.sectorAverage?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable"),
                    "Market peers" to (context.marketAverage?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable")
                )
            )
            if (context.observationDate.isNotBlank()) ResearchCaption("Same-date observations: ${context.observationDate}")
            if (context.sectorCount > 0) ResearchCaption("${context.sectorCount} same-sector peer observation${if (context.sectorCount == 1) "" else "s"} used.")
            if (context.marketCount > 0) ResearchCaption("${context.marketCount} broader market peer observation${if (context.marketCount == 1) "" else "s"} used.")
            ResearchBody(context.interpretation)
        }
        if (context.limitation.isNotBlank()) ResearchCaption(context.limitation)
    }
}

@Composable
private fun ResearchAnalyst(result: AnalystCache.Result, loading: Boolean) {
    ResearchPanel {
        ResearchTitle("AI evidence explanation")
        ResearchCaption("Optional explanation layer. The deterministic assessment above remains the primary analysis.")
        when {
            loading -> ResearchLoading("Explaining the supplied evidence…")
            result.error.isNotBlank() -> ResearchCaption("AI explanation is temporarily unavailable. ${result.error}")
            result.analysis == null && result.message.isNotBlank() -> ResearchCaption(result.message)
            result.analysis == null -> ResearchCaption("No AI explanation was requested or returned.")
            else -> {
                val verified = CompanyAnalysisPresentation.verifiedAnalyst(result)
                if (verified == null) {
                    ResearchCaption("The AI response was withheld because its evidence references could not be verified against the returned evidence packet.")
                } else {
                    val analysis = verified.analysis
                    if (analysis.headline.isNotBlank()) ResearchBody(analysis.headline)
                    if (analysis.summary.isNotBlank()) ResearchBody(analysis.summary)
                    analysis.signals.take(4).forEach { signal ->
                        HorizontalDivider(color = ResearchBorder)
                        ResearchCaption(signal.type.ifBlank { "EVIDENCE" })
                        ResearchBody(signal.title)
                        ResearchCaption(signal.detail)
                        signal.evidenceIds.distinct().take(2).forEach { id ->
                            verified.evidenceById[id]?.let { evidence ->
                                ResearchCaption("Evidence: ${evidence.claim.ifBlank { id }} • ${evidence.source.ifBlank { "Source unavailable" }}")
                                if (evidence.period.isNotBlank()) ResearchCaption("Period: ${evidence.period}")
                                ResearchSourceLink("Open evidence source", evidence.url)
                            }
                        }
                    }
                    if (analysis.interpretation.isNotBlank()) {
                        HorizontalDivider(color = ResearchBorder)
                        ResearchCaption("Interpretation")
                        ResearchBody(analysis.interpretation)
                    }
                    if (analysis.unknowns.isNotEmpty()) {
                        ResearchCaption("Still unknown")
                        analysis.unknowns.take(4).forEach { ResearchCaption("• $it") }
                    }
                    ResearchCaption("AI may summarize or interpret only the supplied evidence. It does not add verified facts, establish causation, or provide BUY/SELL/HOLD instructions.")
                }
            }
        }
    }
}

@Composable
internal fun ResearchMovement(result: MovementIntelligenceCache.Result, loading: Boolean, retry: () -> Unit) {
    ResearchPanel {
        ResearchTitle("Company-specific movement evidence")
        when {
            loading -> ResearchLoading("Checking dated company evidence…")
            result.error != null -> {
                ResearchCaption("Movement evidence is temporarily unavailable.")
                TextButton(onClick = retry) { Text("Retry", color = ResearchGreen) }
            }
            result.move == null -> ResearchCaption("There is not enough price history to establish a reliable movement window.")
            else -> {
                val move = result.move
                ResearchBody("${move.change.ifBlank { "Change unavailable" }} · ${move.periodDays}-day evidence window")
                ResearchCaption("${CompanyResearchPresentation.date(move.from)} → ${CompanyResearchPresentation.date(move.to)}")
                ResearchBody(result.summary.ifBlank { "No supported explanation was returned." })
                result.evidence.forEach { evidence ->
                    HorizontalDivider(color = ResearchBorder)
                    ResearchBody(evidence.title)
                    ResearchCaption("${evidence.source.ifBlank { "Source unavailable" }} · ${CompanyResearchPresentation.date(evidence.date)}")
                    if (evidence.description.isNotBlank()) ResearchCaption(evidence.description)
                    ResearchCaption("Timing relationship: ${CompanyAnalysisPresentation.movementRelationshipLabel(evidence.relationship)}")
                    ResearchSourceLink("Open source", evidence.sourceUrl)
                }
                if (result.evidence.isEmpty()) ResearchCaption("No dated company event was returned for this movement window.")
                result.limitations.forEach { ResearchCaption(it) }
                ResearchCaption("Events near a price movement are context, not proof of its cause.")
            }
        }
    }
}

@Composable
private fun ResearchEvidence(record: CompanyIntelligenceCache.Evidence) {
    ResearchPanel {
        ResearchBody(record.claim.ifBlank { "Company evidence" })
        if (record.value.isNotBlank()) ResearchBody(record.value)
        ResearchCaption("Source: ${record.source.ifBlank { "Unavailable" }}")
        if (record.period.isNotBlank()) ResearchCaption("Period: ${record.period}")
        ResearchDates(record.providerUpdatedAt, record.providerCheckedAt)
        if (record.fetchedAt.isNotBlank()) ResearchCaption("Retrieved: ${CompanyResearchPresentation.date(record.fetchedAt)}")
        ResearchSourceLink("Open source", record.endpoint)
    }
}

@Composable
private fun ResearchAbout(stock: Stock, result: CompanyIntelligenceCache.Result, loading: Boolean, retry: () -> Unit) {
    ResearchPanel {
        ResearchTitle("About ${stock.name}")
        if (loading) ResearchLoading("Loading company profile…")
        else {
            ResearchBody(result.profile.description.ifBlank { "A company description was not returned by the current source." })
            ResearchFactGrid(listOf("Sector" to result.profile.sector.ifBlank { stock.sector.ifBlank { "Unavailable" } }, "Headquarters" to result.profile.headquarters.ifBlank { "Unavailable" }))
            ResearchSourceLink("Company website", result.profile.website)
            ResearchCaption("Source: ${result.source.ifBlank { "Unavailable" }}")
            if (result.fetchedAt.isNotBlank()) ResearchCaption("Retrieved: ${CompanyResearchPresentation.date(result.fetchedAt)}")
            if (result.error != null) TextButton(onClick = retry) { Text("Retry profile", color = ResearchGreen) }
        }
    }
}

@Composable
private fun ResearchNewsCard(item: NewsItem, open: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClickLabel = "Read company article", onClick = open),
        color = ResearchCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, ResearchBorder)) {
        Column {
            if (item.imageUrl.isNotBlank()) AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResearchCaption(item.category.ifBlank { "Company news" })
                ResearchBody(item.title)
                if (item.summary.isNotBlank()) Text(item.summary, color = ResearchMuted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                ResearchCaption("${item.source.ifBlank { "Source unavailable" }} · ${CompanyResearchPresentation.date(item.publishedAt)}")
                Text("Read article →", color = ResearchLinkBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ResearchDates(updated: String, checked: String) {
    if (updated.isNotBlank()) ResearchCaption("Provider updated: ${CompanyResearchPresentation.date(updated)}")
    if (checked.isNotBlank()) ResearchCaption("Source checked: ${CompanyResearchPresentation.date(checked)}")
}

@Composable
private fun ResearchSourceLink(label: String, raw: String) {
    val uri = CompanyResearchPresentation.sourceUrl(raw)
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    if (uri == null) ResearchCaption("$label: link unavailable")
    else TextButton(onClick = {
        runCatching { handler.openUri(uri) }.onFailure { Toast.makeText(context, "No app could open this link.", Toast.LENGTH_SHORT).show() }
    }) {
        Text(label, color = ResearchLinkBlue, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp)); Icon(Icons.Default.OpenInNew, null, tint = ResearchLinkBlue, modifier = Modifier.size(16.dp))
    }
}
