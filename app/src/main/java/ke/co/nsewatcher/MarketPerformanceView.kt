package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MarketHistoryCache
import ke.co.nsewatcher.data.MyStocksCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

private val performanceRanges = listOf("1M", "3M", "6M", "1Y", "3Y", "5Y")

private data class PerformanceSector(
    val name: String,
    val change: Double,
    val members: List<Stock>,
    val series: List<Double>
)

private data class DistributionBucket(
    val label: String,
    val rule: String,
    val count: Int,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketPerformanceView(
    companies: List<Stock>,
    revision: Int,
    now: Instant,
    openCompany: (Stock) -> Unit,
    openCompanies: (String) -> Unit,
    onTab: (String) -> Unit,
    busy: Boolean,
    onRefresh: () -> Unit,
    openSearch: () -> Unit,
    openAlerts: () -> Unit
) {
    var range by rememberSaveable { mutableStateOf("3M") }
    var explain by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var processed by remember { mutableIntStateOf(0) }
    var handledRevision by remember { mutableIntStateOf(0) }
    var histories by remember { mutableStateOf(emptyMap<String, MyStocksCache.HistoryResult>()) }

    val end = now.atZone(CompanyResearchPresentation.zone).toLocalDate()
    val symbols = remember(companies) { companies.map { it.symbol } }

    LaunchedEffect(range, symbols, revision, end) {
        histories = emptyMap()
        processed = 0
        if (symbols.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val forceHistoryRefresh = revision > handledRevision
        try {
            val limiter = Semaphore(4)
            coroutineScope {
                symbols.forEach { symbol ->
                    launch {
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
                    }
                }
            }
            if (forceHistoryRefresh) handledRevision = revision
        } finally {
            loading = false
        }
    }

    val evaluated = remember(companies, histories, range, end) {
        companies.map { stock ->
            stock to MarketPresentation.eligible(
                stock,
                range,
                histories[stock.symbol] ?: MyStocksCache.HistoryResult(),
                end
            )
        }
    }
    val eligible = remember(evaluated) { evaluated.mapNotNull { it.second.performance } }
    val excluded = remember(evaluated) { evaluated.filter { it.second.performance == null } }
    val higher = eligible.count { it.change > 0 }
    val lower = eligible.count { it.change < 0 }
    val flat = eligible.count { it.change == 0.0 }

    val strongest = eligible.maxByOrNull { it.change }
    val weakest = eligible.minByOrNull { it.change }
    val mostConsistent = remember(eligible, histories, range, end) {
        eligible.filter { it.change > 0 }
            .mapNotNull { performance ->
                consistencyScore(histories[performance.stock.symbol], range, end)
                    ?.let { performance to it }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    val sectorPerformance = remember(eligible, histories, range, end) {
        eligible.groupBy { CompaniesPresentation.sector(it.stock.sector) }
            .mapNotNull { (name, rows) ->
                val values = rows.map { it.change }
                if (values.isEmpty()) null
                else {
                    val members = rows.map { it.stock }
                    PerformanceSector(
                        name = name,
                        change = values.average(),
                        members = members,
                        series = sectorSeries(members, histories, range, end)
                    )
                }
            }
            .sortedByDescending { abs(it.change) }
    }

    val directional = eligible.filter { it.change != 0.0 }
    val strongGain = directional.count { it.change > 10.0 }
    val mildGain = directional.count { it.change > 0.0 && it.change <= 10.0 }
    val mildLoss = directional.count { it.change < 0.0 && it.change >= -10.0 }
    val strongLoss = directional.count { it.change < -10.0 }
    val asOf = eligible.mapNotNull { MarketPresentation.date(it.last) }.maxOrNull() ?: end

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ResearchBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PremiumMarketHeader(
                subtitle = "See how shares and sectors have performed over time.",
                busy = busy,
                onRefresh = onRefresh,
                openSearch = openSearch,
                openAlerts = openAlerts
            )
        }
        item { PremiumMarketTabs("Performance", onTab) }

        item {
            PerformanceSummaryCard(
                range = range,
                onRange = { range = it },
                asOf = asOf,
                loading = loading,
                processed = processed,
                total = companies.size,
                higher = higher,
                lower = lower,
                flat = flat,
                unavailable = excluded.size,
                sectors = sectorPerformance,
                onInfo = { explain = true }
            )
        }

        if (!loading) {
            item {
                PerformanceLeadersRow(
                    strongest = strongest,
                    weakest = weakest,
                    mostConsistent = mostConsistent,
                    histories = histories,
                    range = range,
                    end = end,
                    openCompany = openCompany
                )
            }

            item {
                SectorPerformanceCard(
                    sectors = sectorPerformance.take(6),
                    range = range,
                    openCompanies = openCompanies
                )
            }

            item {
                PerformanceDistributionCard(
                    range = range,
                    directionalTotal = directional.size,
                    flatCount = flat,
                    buckets = listOf(
                        DistributionBucket("Strong gain", "(> +10%)", strongGain, ResearchGreen),
                        DistributionBucket("Mild gain", "(0% to +10%)", mildGain, Color(0xFF7BE77A)),
                        DistributionBucket("Mild loss", "(0% to -10%)", mildLoss, Color(0xFFFF8294)),
                        DistributionBucket("Strong loss", "(< -10%)", strongLoss, ResearchRed)
                    ),
                    onInfo = { explain = true }
                )
            }

            item { WhyPerformanceMattersCard(range) }
        }
    }

    if (explain) {
        ModalBottomSheet(
            onDismissRequest = { explain = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ResearchBackground
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    ResearchTitle("$range performance coverage")
                    ResearchBody(
                        "Performance uses the first and last valid dated closing prices available for the selected period. " +
                            "No current quote is substituted when historical data is missing."
                    )
                    ResearchCaption(
                        "Price change excludes dividends. The provider does not expose an adjustment-status flag here, " +
                            "so corporate actions can affect historical comparisons."
                    )
                    ResearchCaption(
                        "Sector performance is an equal-weight average of eligible company price changes, not an official NSE sector index."
                    )
                    ResearchCaption(
                        "Most consistent identifies a positive performer whose dated closing-price path most closely follows a steady upward trend. " +
                            "It is descriptive, not an investment-quality score."
                    )
                    if (flat > 0) ResearchCaption("$flat eligible ${if (flat == 1) "share was" else "shares were"} unchanged over the selected period.")
                    ResearchTitle("Unavailable (${excluded.size})")
                }
                if (excluded.isEmpty()) {
                    item { ResearchCaption("Every loaded company met the history coverage rules for this period.") }
                } else {
                    items(excluded, key = { it.first.symbol }) { (stock, result) ->
                        MarketSectionCard {
                            ResearchBody("${stock.symbol} · ${stock.name}")
                            ResearchCaption(result.reason)
                            TextButton(onClick = { explain = false; openCompany(stock) }) {
                                Text("Open Company Intelligence →", color = ResearchGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceSummaryCard(
    range: String,
    onRange: (String) -> Unit,
    asOf: LocalDate,
    loading: Boolean,
    processed: Int,
    total: Int,
    higher: Int,
    lower: Int,
    flat: Int,
    unavailable: Int,
    sectors: List<PerformanceSector>,
    onInfo: () -> Unit
) {
    MarketSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketIconBubble(Icons.Outlined.TrendingUp, MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Performance Summary", color = ResearchText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("A snapshot of how the market has performed over time.", color = ResearchMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "As of " + asOf.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)),
                    color = ResearchMuted,
                    fontSize = 9.5.sp
                )
                IconButton(onClick = onInfo, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Info, "Performance methodology", tint = ResearchMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PerformanceRangeSelector(range, onRange)
        Spacer(Modifier.height(12.dp))

        if (loading) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = ResearchRaised,
                border = BorderStroke(1.dp, ResearchBorder)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Checking historical coverage…", color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else processed.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                        color = ResearchGreen,
                        trackColor = ResearchBorder
                    )
                    Text("$processed of $total companies checked", color = ResearchMuted, fontSize = 10.sp)
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = ResearchGreen.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.45f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BarChart, null, tint = ResearchGreen, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            performanceSummaryHeadline(range, higher, lower),
                            color = ResearchText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            performanceSummaryDetail(sectors, flat),
                            color = ResearchMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                PerformanceSummaryStat("Higher", higher, ResearchGreen, Modifier.weight(1f))
                VerticalDivider(Modifier.height(58.dp), color = ResearchBorder)
                PerformanceSummaryStat("Lower", lower, ResearchRed, Modifier.weight(1f))
                VerticalDivider(Modifier.height(58.dp), color = ResearchBorder)
                PerformanceSummaryStat("Unavailable", unavailable, ResearchMuted, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PerformanceRangeSelector(selected: String, onSelected: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 330.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                performanceRanges.forEach { option ->
                    val active = selected == option
                    Surface(
                        modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp))
                            .clickable(role = Role.Tab) { onSelected(option) },
                        shape = RoundedCornerShape(11.dp),
                        color = if (active) ResearchGreen.copy(alpha = 0.12f) else ResearchRaised,
                        border = BorderStroke(1.dp, if (active) ResearchGreen else ResearchBorder)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                option,
                                color = if (active) ResearchText else ResearchMuted,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                performanceRanges.forEach { option ->
                    val active = selected == option
                    Surface(
                        modifier = Modifier.width(58.dp).height(42.dp).clip(RoundedCornerShape(11.dp))
                            .clickable(role = Role.Tab) { onSelected(option) },
                        shape = RoundedCornerShape(11.dp),
                        color = if (active) ResearchGreen.copy(alpha = 0.12f) else ResearchRaised,
                        border = BorderStroke(1.dp, if (active) ResearchGreen else ResearchBorder)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(option, color = if (active) ResearchText else ResearchMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceSummaryStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), color = color, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = ResearchMuted, fontSize = 10.sp, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(4.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(if (value > 0) 0.72f else 0.12f).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun PerformanceLeadersRow(
    strongest: MarketPerformance?,
    weakest: MarketPerformance?,
    mostConsistent: MarketPerformance?,
    histories: Map<String, MyStocksCache.HistoryResult>,
    range: String,
    end: LocalDate,
    openCompany: (Stock) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.12f
        if (stack) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    PerformanceLeaderCard(
                        "Strongest Performer", Icons.Outlined.TrendingUp, strongest, ResearchGreen,
                        strongest?.stock?.symbol?.let { histories[it] }, range, end, openCompany, Modifier.width(158.dp)
                    )
                }
                item {
                    PerformanceLeaderCard(
                        "Weakest Performer", Icons.Outlined.TrendingDown, weakest, ResearchRed,
                        weakest?.stock?.symbol?.let { histories[it] }, range, end, openCompany, Modifier.width(158.dp)
                    )
                }
                item {
                    PerformanceLeaderCard(
                        "Most Consistent", Icons.Outlined.Shield, mostConsistent, MaterialTheme.colorScheme.tertiary,
                        mostConsistent?.stock?.symbol?.let { histories[it] }, range, end, openCompany, Modifier.width(158.dp)
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerformanceLeaderCard(
                    "Strongest Performer", Icons.Outlined.TrendingUp, strongest, ResearchGreen,
                    strongest?.stock?.symbol?.let { histories[it] }, range, end, openCompany, Modifier.weight(1f)
                )
                PerformanceLeaderCard(
                    "Weakest Performer", Icons.Outlined.TrendingDown, weakest, ResearchRed,
                    weakest?.stock?.symbol?.let { histories[it] }, range, end, openCompany, Modifier.weight(1f)
                )
                PerformanceLeaderCard(
                    "Most Consistent", Icons.Outlined.Shield, mostConsistent, MaterialTheme.colorScheme.tertiary,
                    mostConsistent?.stock?.symbol?.let { histories[it] }, range, end, openCompany, Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PerformanceLeaderCard(
    title: String,
    icon: ImageVector,
    performance: MarketPerformance?,
    accent: Color,
    history: MyStocksCache.HistoryResult?,
    range: String,
    end: LocalDate,
    openCompany: (Stock) -> Unit,
    modifier: Modifier = Modifier
) {
    val series = remember(history, range, end) { performanceSeries(history, range, end) }
    Surface(
        modifier = modifier.heightIn(min = 148.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = performance != null, role = Role.Button) { performance?.stock?.let(openCompany) },
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.62f))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(title, color = ResearchText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Outlined.ChevronRight, null, tint = accent, modifier = Modifier.size(14.dp))
            }
            Text(performance?.stock?.symbol ?: "—", color = ResearchText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                performance?.stock?.name ?: "Not enough history",
                color = ResearchMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                performance?.let { CompanyResearchPresentation.percent(it.change) } ?: "Unavailable",
                color = if (performance == null) ResearchMuted else accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text("over $range", color = ResearchMuted, fontSize = 9.sp)
            if (series.size >= 2) PerformanceSparkline(series, accent, Modifier.fillMaxWidth().height(26.dp))
        }
    }
}

@Composable
private fun SectorPerformanceCard(
    sectors: List<PerformanceSector>,
    range: String,
    openCompanies: (String) -> Unit
) {
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.PieChart,
            MaterialTheme.colorScheme.tertiary,
            "Sector Performance",
            "How key sectors have performed over the selected period.",
            if (sectors.isNotEmpty()) "See all sectors" else null,
            { openCompanies("All") }
        )
        Spacer(Modifier.height(10.dp))
        if (sectors.isEmpty()) {
            Text("No sector has enough comparable history for $range.", color = ResearchMuted, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sectors.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { sector ->
                            SectorPerformanceCell(sector, Modifier.weight(1f)) { openCompanies(sector.name) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Equal-weight averages from eligible company price histories; these are not official NSE sector indices.",
                color = ResearchMuted,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun SectorPerformanceCell(
    sector: PerformanceSector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = performanceChangeColor(sector.change)
    Surface(
        modifier = modifier.heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(premiumSectorSymbol(sector.name), null, tint = accent, modifier = Modifier.size(23.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(sector.name, color = ResearchText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    CompanyResearchPresentation.percent(sector.change),
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (sector.series.size >= 2) {
                PerformanceSparkline(sector.series, accent, Modifier.width(52.dp).height(28.dp))
            }
        }
    }
}

@Composable
private fun PerformanceDistributionCard(
    range: String,
    directionalTotal: Int,
    flatCount: Int,
    buckets: List<DistributionBucket>,
    onInfo: () -> Unit
) {
    MarketSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketIconBubble(Icons.Outlined.BarChart, ResearchGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("How shares finished", color = ResearchText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Distribution of share performance over the last ${performanceRangeLabel(range)}.", color = ResearchMuted, fontSize = 10.5.sp)
            }
            IconButton(onClick = onInfo, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Info, "Distribution methodology", tint = ResearchMuted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (directionalTotal == 0) {
            Text("There are no directional historical returns available for this period.", color = ResearchMuted, fontSize = 12.sp)
        } else {
            Row(
                Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                buckets.filter { it.count > 0 }.forEach { bucket ->
                    Box(Modifier.weight(bucket.count.toFloat()).fillMaxHeight().background(bucket.color))
                }
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stack = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.12f
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        buckets.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { bucket ->
                                    DistributionLegendItem(bucket, directionalTotal, Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        buckets.forEachIndexed { index, bucket ->
                            DistributionLegendItem(bucket, directionalTotal, Modifier.weight(1f))
                            if (index < buckets.lastIndex) VerticalDivider(Modifier.height(72.dp), color = ResearchBorder)
                        }
                    }
                }
            }
            if (flatCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text("$flatCount unchanged ${if (flatCount == 1) "share is" else "shares are"} outside the four directional buckets.", color = ResearchMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun DistributionLegendItem(bucket: DistributionBucket, total: Int, modifier: Modifier = Modifier) {
    val pct = if (total == 0) 0 else ((bucket.count * 100.0) / total).toInt()
    Column(modifier.padding(horizontal = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(bucket.color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text("$pct%", color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(bucket.label, color = ResearchText, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        Text(bucket.rule, color = ResearchMuted, fontSize = 8.5.sp)
        Text("${bucket.count} ${if (bucket.count == 1) "share" else "shares"}", color = ResearchMuted, fontSize = 8.5.sp)
    }
}

@Composable
private fun WhyPerformanceMattersCard(range: String) {
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.School,
            Color(0xFFF6C65B),
            "Why performance matters",
            "A quick note for new investors."
        )
        Spacer(Modifier.height(9.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ResearchGreen.copy(alpha = 0.07f),
            border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.45f))
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MenuBook, null, tint = ResearchGreen, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "A share’s price today and its longer-term performance can be different. " +
                        "A stock may be up today but still down over ${performanceRangeLabel(range)}, or vice versa. " +
                        "Looking at both short-term moves and longer-term trends helps put today’s move in context.",
                    color = ResearchText,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun PerformanceSparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val xStep = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { index, value ->
            Offset(
                x = index * xStep,
                y = size.height - (((value - min) / span).toFloat() * size.height)
            )
        }
        points.zipWithNext().forEach { (a, b) ->
            drawLine(color = color, start = a, end = b, strokeWidth = 2.dp.toPx())
        }
    }
}

private fun performanceSummaryHeadline(range: String, higher: Int, lower: Int): String = when {
    higher > lower -> "Most tracked shares with sufficient history finished the last ${performanceRangeLabel(range)} higher."
    lower > higher -> "More tracked shares with sufficient history finished the last ${performanceRangeLabel(range)} lower."
    higher + lower == 0 -> "There is not enough historical coverage yet to summarize this period."
    else -> "Tracked shares were broadly balanced over the last ${performanceRangeLabel(range)}."
}

private fun performanceSummaryDetail(sectors: List<PerformanceSector>, flat: Int): String {
    val leaders = sectors.filter { it.change > 0 }.sortedByDescending { it.change }.take(2)
    val sectorText = when (leaders.size) {
        0 -> "No positive sector average is available for this period."
        1 -> "A stronger available sector average is ${leaders[0].name}."
        else -> "Stronger available sector averages include ${leaders[0].name} and ${leaders[1].name}."
    }
    return if (flat > 0) "$sectorText $flat ${if (flat == 1) "share finished" else "shares finished"} unchanged." else sectorText
}

private fun performanceRangeLabel(range: String): String = when (range) {
    "1M" -> "1 month"
    "3M" -> "3 months"
    "6M" -> "6 months"
    "1Y" -> "1 year"
    "3Y" -> "3 years"
    "5Y" -> "5 years"
    else -> range
}

@Composable
private fun performanceChangeColor(value: Double?): Color = when {
    value == null || !value.isFinite() || value == 0.0 -> ResearchMuted
    value > 0 -> ResearchGreen
    else -> ResearchRed
}

private fun performancePoints(
    result: MyStocksCache.HistoryResult?,
    range: String,
    end: LocalDate
): List<MyStocksCache.HistoryPoint> {
    if (result == null) return emptyList()
    val start = MarketPresentation.start(range, end)
    return WatchlistPresentation.trend(result.points).filter { point ->
        val date = MarketPresentation.date(point.date)
        date != null && !date.isBefore(start) && !date.isAfter(end)
    }
}

private fun performanceSeries(
    result: MyStocksCache.HistoryResult?,
    range: String,
    end: LocalDate
): List<Double> = performancePoints(result, range, end).map { it.close }

private fun consistencyScore(
    result: MyStocksCache.HistoryResult?,
    range: String,
    end: LocalDate
): Double? {
    val points = performancePoints(result, range, end)
    if (points.size < 4) return null
    val dated = points.mapNotNull { point ->
        MarketPresentation.date(point.date)?.let { it.toEpochDay().toDouble() to ln(point.close) }
    }
    if (dated.size < 4) return null
    val meanX = dated.map { it.first }.average()
    val meanY = dated.map { it.second }.average()
    var covariance = 0.0
    var varianceX = 0.0
    var varianceY = 0.0
    dated.forEach { (x, y) ->
        val dx = x - meanX
        val dy = y - meanY
        covariance += dx * dy
        varianceX += dx * dx
        varianceY += dy * dy
    }
    if (varianceX <= 0.0 || varianceY <= 0.0) return null
    val slope = covariance / varianceX
    if (slope <= 0.0) return null
    return (covariance * covariance) / (varianceX * varianceY)
}

private fun sectorSeries(
    members: List<Stock>,
    histories: Map<String, MyStocksCache.HistoryResult>,
    range: String,
    end: LocalDate
): List<Double> {
    val byDate = sortedMapOf<LocalDate, MutableList<Double>>()
    members.forEach { stock ->
        val points = performancePoints(histories[stock.symbol], range, end)
        val baseline = points.firstOrNull()?.close?.takeIf { it > 0.0 } ?: return@forEach
        points.forEach pointLoop@{ point ->
            val date = MarketPresentation.date(point.date) ?: return@pointLoop
            byDate.getOrPut(date) { mutableListOf() }.add(((point.close / baseline) - 1.0) * 100.0)
        }
    }
    return byDate.values.mapNotNull { values -> values.takeIf { it.isNotEmpty() }?.average() }
}
