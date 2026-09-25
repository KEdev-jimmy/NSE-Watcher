package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private data class ComparisonMetric(
    val key: String,
    val label: String,
    val left: String,
    val right: String
)

private data class ComparisonSection(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val metrics: List<ComparisonMetric>
)

@Composable
fun CompanyComparison(
    stocks: List<Stock>,
    back: () -> Unit,
    initialSymbols: List<String> = emptyList()
) {
    val available = remember(stocks) {
        stocks.distinctBy { it.symbol.uppercase() }
            .filter { it.symbol.isNotBlank() }
            .sortedBy { it.symbol }
    }

    if (available.size < 2) {
        PremiumCompareScaffold {
            PremiumCompareHeader(back)
            CompareInfoCard(
                title = "Two companies are needed",
                body = "Add or load at least two NSE companies before starting a side-by-side comparison.",
                accent = ResearchGreen
            )
        }
        return
    }

    var leftSymbol by rememberSaveable(initialSymbols) {
        mutableStateOf(
            initialSymbols.firstOrNull { symbol ->
                available.any { it.symbol.equals(symbol, true) }
            } ?: available.first().symbol
        )
    }
    var rightSymbol by rememberSaveable(initialSymbols) {
        mutableStateOf(
            initialSymbols.firstOrNull { symbol ->
                !symbol.equals(leftSymbol, true) &&
                    available.any { it.symbol.equals(symbol, true) }
            } ?: available.first { !it.symbol.equals(leftSymbol, true) }.symbol
        )
    }

    val leftStock = available.firstOrNull { it.symbol.equals(leftSymbol, true) } ?: available.first()
    val rightStock = available.firstOrNull {
        it.symbol.equals(rightSymbol, true) && !it.symbol.equals(leftStock.symbol, true)
    } ?: available.first { !it.symbol.equals(leftStock.symbol, true) }

    var leftResult by remember(leftStock.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var rightResult by remember(rightStock.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var loading by remember(leftStock.symbol, rightStock.symbol) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(leftStock.symbol, rightStock.symbol, retry) {
        loading = true
        try {
            coroutineScope {
                val forceRefresh = retry > 0
                val results = listOf(
                    async {
                        CompanyIntelligenceCache.load(
                            symbol = leftStock.symbol,
                            forceRefresh = forceRefresh
                        )
                    },
                    async {
                        CompanyIntelligenceCache.load(
                            symbol = rightStock.symbol,
                            forceRefresh = forceRefresh
                        )
                    }
                ).awaitAll()
                leftResult = results[0]
                rightResult = results[1]
            }
            loadError = leftResult.error != null || rightResult.error != null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadError = true
        } finally {
            loading = false
        }
    }

    val sections = remember(leftStock, rightStock, leftResult, rightResult) {
        comparisonSections(leftStock, rightStock, leftResult, rightResult)
    }

    PremiumCompareScaffold {
        PremiumCompareHeader(back)

        PremiumCompanySelectors(
            left = leftStock,
            right = rightStock,
            companies = available,
            onLeft = { symbol ->
                if (!symbol.equals(rightStock.symbol, true)) leftSymbol = symbol
            },
            onRight = { symbol ->
                if (!symbol.equals(leftStock.symbol, true)) rightSymbol = symbol
            }
        )

        if (loading) {
            ResearchPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = ResearchGreen,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Loading sourced company research…",
                            color = ResearchText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Quote values and company research can have different observation dates.",
                            color = ResearchMuted,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
        } else {
            if (loadError) {
                val unavailable = buildList {
                    if (leftResult.error != null) add(leftStock.symbol)
                    if (rightResult.error != null) add(rightStock.symbol)
                }
                CompareInfoCard(
                    title = "Some company research is unavailable",
                    body = "Research could not be refreshed for " +
                        unavailable.joinToString(" and ") +
                        ". Quote values remain separate and missing research fields are not inferred.",
                    accent = ResearchRed
                )
            }

            if (
                leftResult.cacheState == "STALE_FALLBACK" ||
                rightResult.cacheState == "STALE_FALLBACK"
            ) {
                val cached = buildList {
                    if (leftResult.cacheState == "STALE_FALLBACK") add(leftStock.symbol)
                    if (rightResult.cacheState == "STALE_FALLBACK") add(rightStock.symbol)
                }
                CompareInfoCard(
                    title = "Using cached company research",
                    body = "Live company research could not be refreshed for " +
                        cached.joinToString(" and ") +
                        ". Their most recent cached research is shown and clearly kept separate from live quotes.",
                    accent = Color(0xFFF0B531)
                )
            }

            if (
                leftStock.sector.isNotBlank() &&
                rightStock.sector.isNotBlank() &&
                !leftStock.sector.equals(rightStock.sector, true)
            ) {
                CompareInfoCard(
                    title = "Different sectors",
                    body = "${leftStock.symbol} is classified as ${leftStock.sector}, while " +
                        "${rightStock.symbol} is classified as ${rightStock.sector}. " +
                        "Ratios and margins can mean different things across sectors.",
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }

            val leftPeriod = leftResult.profile.financialPeriod
            val rightPeriod = rightResult.profile.financialPeriod
            if (
                leftPeriod.isNotBlank() &&
                rightPeriod.isNotBlank() &&
                !leftPeriod.equals(rightPeriod, true)
            ) {
                CompareInfoCard(
                    title = "Reporting periods differ",
                    body = "${leftStock.symbol}: $leftPeriod · ${rightStock.symbol}: $rightPeriod. " +
                        "Use caution when comparing figures from different reporting periods.",
                    accent = Color(0xFFF0B531)
                )
            }

            sections.forEach { section ->
                PremiumComparisonSection(
                    section = section,
                    left = leftStock,
                    right = rightStock,
                    leftResult = leftResult,
                    rightResult = rightResult
                )
            }

            PremiumCompareLearningCard()

            PremiumCompareSources(
                left = leftStock,
                right = rightStock,
                leftResult = leftResult,
                rightResult = rightResult
            )

            OutlinedButton(
                onClick = { retry++ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, ResearchGreen)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Refresh company research", color = ResearchGreen)
            }

            Text(
                "This comparison presents available provider-backed facts side by side. " +
                    "It does not rank either company or provide a BUY/SELL recommendation.",
                color = ResearchMuted,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun PremiumCompareScaffold(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ResearchBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PremiumCompareHeader(back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) {
            Icon(Icons.Default.ArrowBack, "Back", tint = ResearchText)
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Compare Companies",
                color = ResearchText,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Understand key differences side by side.",
                color = ResearchMuted,
                fontSize = 11.sp
            )
        }
        Box(
            Modifier.size(38.dp).background(
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CompareArrows,
                null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PremiumCompanySelectors(
    left: Stock,
    right: Stock,
    companies: List<Stock>,
    onLeft: (String) -> Unit,
    onRight: (String) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.12f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumCompanySelector("Company 1", left, companies, onLeft)
                PremiumCompanySelector("Company 2", right, companies, onRight)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumCompanySelector("Company 1", left, companies, onLeft, Modifier.weight(1f))
                PremiumCompanySelector("Company 2", right, companies, onRight, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PremiumCompanySelector(
    label: String,
    selected: Stock,
    companies: List<Stock>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(selected.symbol) { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = ResearchCard,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = ResearchMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Box {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(role = Role.Button) { expanded = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(34.dp).background(
                            ResearchGreen.copy(alpha = 0.10f),
                            RoundedCornerShape(9.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            selected.symbol.take(4),
                            color = ResearchGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            selected.symbol,
                            color = ResearchText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            selected.name,
                            color = ResearchMuted,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        null,
                        tint = ResearchMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    companies.forEach { company ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        company.symbol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        company.name,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onSelected(company.symbol)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumComparisonSection(
    section: ComparisonSection,
    left: Stock,
    right: Stock,
    leftResult: CompanyIntelligenceCache.Result,
    rightResult: CompanyIntelligenceCache.Result
) {
    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    section.icon,
                    null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    section.title,
                    color = ResearchText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    section.subtitle,
                    color = ResearchMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }
        }

        section.metrics.forEachIndexed { index, metric ->
            MetricComparisonCard(
                metric = metric,
                left = left,
                right = right,
                leftConflict = metric.key.isNotBlank() &&
                    leftResult.fieldQuality[metric.key] == "CONFLICT",
                rightConflict = metric.key.isNotBlank() &&
                    rightResult.fieldQuality[metric.key] == "CONFLICT"
            )
            if (index < section.metrics.lastIndex) {
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun MetricComparisonCard(
    metric: ComparisonMetric,
    left: Stock,
    right: Stock,
    leftConflict: Boolean,
    rightConflict: Boolean
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder.copy(alpha = 0.75f))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                metric.label,
                color = ResearchMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComparisonValueCell(
                    symbol = left.symbol,
                    value = metric.left,
                    conflict = leftConflict,
                    modifier = Modifier.weight(1f)
                )
                ComparisonValueCell(
                    symbol = right.symbol,
                    value = metric.right,
                    conflict = rightConflict,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ComparisonValueCell(
    symbol: String,
    value: String,
    conflict: Boolean,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                symbol,
                color = ResearchGreen,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (conflict) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.WarningAmber,
                    "Sources differ",
                    tint = Color(0xFFF0B531),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            value,
            color = if (value == "Unavailable") ResearchMuted else ResearchText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (conflict) {
            Text(
                "Sources differ",
                color = Color(0xFFF0B531),
                fontSize = 7.5.sp
            )
        }
    }
}

@Composable
private fun CompareInfoCard(
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            Modifier.padding(11.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    color = ResearchText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    body,
                    color = ResearchMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PremiumCompareLearningCard() {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ResearchGreen.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.42f))
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).background(
                    ResearchGreen.copy(alpha = 0.10f),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.School,
                    null,
                    tint = ResearchGreen,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "How to read this comparison",
                    color = ResearchText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "A lower share price does not mean a company is cheaper. Revenue and profit describe business scale, growth compares reported periods, and valuation ratios need sector context. Use the source dates below before drawing conclusions.",
                    color = ResearchMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun PremiumCompareSources(
    left: Stock,
    right: Stock,
    leftResult: CompanyIntelligenceCache.Result,
    rightResult: CompanyIntelligenceCache.Result
) {
    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.VerifiedUser,
                null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Sources & dates",
                color = ResearchText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        SourceStatusCard(left, leftResult)
        SourceStatusCard(right, rightResult)

        Text(
            "Prices use each company's latest available NSE observation. Company financials can have different provider update dates and reporting periods.",
            color = ResearchMuted,
            fontSize = 8.5.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun SourceStatusCard(
    stock: Stock,
    result: CompanyIntelligenceCache.Result
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${stock.symbol} · ${stock.name}",
                    color = ResearchText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Quote: ${observationTime(stock)}",
                    color = ResearchMuted,
                    fontSize = 8.5.sp
                )
                val period = result.profile.financialPeriod.ifBlank { "period unavailable" }
                Text(
                    "Financials: $period",
                    color = ResearchMuted,
                    fontSize = 8.5.sp
                )
                val sourceDate = buildString {
                    if (result.profile.financialProviderUpdatedAt.isNotBlank()) {
                        append("Updated " + result.profile.financialProviderUpdatedAt)
                    }
                    if (result.profile.financialPageCheckedAt.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append("Checked " + result.profile.financialPageCheckedAt)
                    }
                    if (isEmpty()) append("Provider dates unavailable")
                }
                Text(sourceDate, color = ResearchMuted, fontSize = 8.5.sp)
            }
            val status = when {
                result.error != null -> "Unavailable"
                result.cacheState == "STALE_FALLBACK" -> "Cached"
                else -> "Available"
            }
            val accent = when (status) {
                "Unavailable" -> ResearchRed
                "Cached" -> Color(0xFFF0B531)
                else -> ResearchGreen
            }
            Surface(
                color = accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.42f))
            ) {
                Text(
                    status,
                    color = accent,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private fun comparisonSections(
    left: Stock,
    right: Stock,
    leftResult: CompanyIntelligenceCache.Result,
    rightResult: CompanyIntelligenceCache.Result
): List<ComparisonSection> {
    val lp = leftResult.profile
    val rp = rightResult.profile

    return listOf(
        ComparisonSection(
            title = "Quick snapshot",
            subtitle = "Current market observation and company scale",
            icon = Icons.Default.Insights,
            metrics = listOf(
                ComparisonMetric(
                    key = "",
                    label = "Latest price",
                    left = CompanyResearchPresentation.money(left.price),
                    right = CompanyResearchPresentation.money(right.price)
                ),
                ComparisonMetric(
                    key = "",
                    label = "Daily change",
                    left = dailyChange(left),
                    right = dailyChange(right)
                ),
                ComparisonMetric(
                    key = "marketCap",
                    label = "Market value",
                    left = financial(lp.marketCap, ""),
                    right = financial(rp.marketCap, "")
                ),
                ComparisonMetric(
                    key = "",
                    label = "Sector",
                    left = left.sector.ifBlank { lp.sector.ifBlank { "Unavailable" } },
                    right = right.sector.ifBlank { rp.sector.ifBlank { "Unavailable" } }
                )
            )
        ),
        ComparisonSection(
            title = "Business performance",
            subtitle = "Latest returned financial figures and period changes",
            icon = Icons.Default.BarChart,
            metrics = listOf(
                ComparisonMetric(
                    "revenue",
                    "Revenue",
                    financial(lp.revenue, lp.financialUnit),
                    financial(rp.revenue, rp.financialUnit)
                ),
                ComparisonMetric(
                    "profit",
                    "Net profit",
                    financial(lp.profit, lp.financialUnit),
                    financial(rp.profit, rp.financialUnit)
                ),
                ComparisonMetric(
                    "eps",
                    "Earnings per share (EPS)",
                    value(lp.eps),
                    value(rp.eps)
                ),
                ComparisonMetric(
                    "revenueGrowth",
                    "Revenue growth",
                    percent(lp.revenueGrowth),
                    percent(rp.revenueGrowth)
                ),
                ComparisonMetric(
                    "profitGrowth",
                    "Profit growth",
                    percent(lp.profitGrowth),
                    percent(rp.profitGrowth)
                )
            )
        ),
        ComparisonSection(
            title = "Ratios & shareholder context",
            subtitle = "Valuation, profitability, leverage and dividend fields",
            icon = Icons.Default.QueryStats,
            metrics = listOf(
                ComparisonMetric("pe", "P/E ratio", value(lp.pe), value(rp.pe)),
                ComparisonMetric("pb", "P/B ratio", value(lp.pb), value(rp.pb)),
                ComparisonMetric("roe", "Return on equity (ROE)", percent(lp.roe), percent(rp.roe)),
                ComparisonMetric(
                    "debtToEquity",
                    "Debt / Equity",
                    value(lp.debtToEquity),
                    value(rp.debtToEquity)
                ),
                ComparisonMetric(
                    "dividendYield",
                    "Dividend yield",
                    percent(lp.dividendYield),
                    percent(rp.dividendYield)
                )
            )
        )
    )
}

private fun dailyChange(stock: Stock): String =
    if (stock.changeAvailable && stock.change.isFinite()) {
        CompanyResearchPresentation.percent(stock.change)
    } else {
        "Unavailable"
    }

private fun observationTime(stock: Stock): String {
    if (stock.observedAt.isBlank()) return "time unavailable"
    return runCatching {
        java.time.Instant.parse(stock.observedAt)
            .atZone(java.time.ZoneId.of("Africa/Nairobi"))
            .format(
                java.time.format.DateTimeFormatter.ofPattern(
                    "dd MMM yyyy, HH:mm 'EAT'",
                    java.util.Locale.US
                )
            )
    }.getOrDefault(stock.observedAt.replace("T", " ").removeSuffix("Z").take(16))
}

private fun value(raw: String): String =
    raw.trim().ifBlank { "Unavailable" }

private fun percent(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return "Unavailable"
    return if (value.contains("%")) value else "$value%"
}

private fun financial(raw: String, unit: String): String {
    val value = raw.trim()
    if (value.isBlank()) return "Unavailable"
    return if (unit.isBlank()) value else "$value $unit"
}
