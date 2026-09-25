package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.MarketHistoryCache
import ke.co.nsewatcher.data.MyStocksCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private data class MarketCalendarItem(
    val id: String,
    val date: LocalDate,
    val company: String,
    val label: String,
    val kind: String
)

@Composable
internal fun PremiumMarketExperience(
    stockFeed: List<Stock>,
    catalog: List<Stock>,
    initialStatus: MyStocksCache.MarketStatus,
    marketIndices: List<MyStocksCache.MarketIndex>,
    newsFeed: List<NewsItem>,
    openCompany: (Stock) -> Unit,
    openCompanies: (String) -> Unit,
    openAlerts: () -> Unit,
    onQuotesLoaded: (List<Stock>) -> Unit,
    onCatalogLoaded: (List<Stock>) -> Unit,
    onIndicesLoaded: (List<MyStocksCache.MarketIndex>) -> Unit,
    onMarketStatusLoaded: (MyStocksCache.MarketStatus) -> Unit
) {
    var tab by rememberSaveable { mutableStateOf("Overview") }
    var status by remember { mutableStateOf(initialStatus) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(Instant.now()) }
    var histories by remember { mutableStateOf<Map<String, List<Double>>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = premiumHomePalette(dark)
    val companies = remember(catalog, stockFeed) { CompaniesPresentation.companies(catalog, stockFeed) }
    val breadth = remember(companies) { MarketPresentation.breadth(companies) }
    val sectors = remember(companies) { MarketPresentation.sectors(companies) }
    val gainers = remember(companies) { premiumMovers(companies, "Gainers") }
    val losers = remember(companies) { premiumMovers(companies, "Losers") }
    val active = remember(companies) { premiumMovers(companies, "Active") }
    val calendar = remember(newsFeed, now) {
        premiumCalendar(newsFeed, now.atZone(CompanyResearchPresentation.zone).toLocalDate())
    }
    val historyTargets = remember(gainers, losers, active) {
        (gainers.take(3) + losers.take(3) + active.take(3)).distinctBy { it.symbol }
    }

    suspend fun refresh(force: Boolean) {
        if (busy) return
        busy = true
        try {
            val nextStatus = MarketData.status()
            status = nextStatus
            if (nextStatus.isKnown || !initialStatus.isKnown) onMarketStatusLoaded(nextStatus)

            val nextIndices = MarketData.indices(nextStatus.isKnown && nextStatus.isOpen)
            if (nextIndices.isNotEmpty()) onIndicesLoaded(nextIndices)

            if (catalog.isEmpty() || force) {
                MarketData.companies().takeIf { it.isNotEmpty() }?.let(onCatalogLoaded)
            }
            if (MarketRefreshController.shouldRefreshQuotes(stockFeed.isNotEmpty()) && (stockFeed.isEmpty() || force)) {
                val quotes = MarketData.stocks()
                if (quotes.isNotEmpty()) {
                    onQuotesLoaded(quotes)
                    error = null
                } else error = "Quotes could not be refreshed. Available observations remain visible."
            }
            if (force) revision++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Market refresh failed. Please try again."
        } finally {
            busy = false
        }
    }

    LaunchedEffect(initialStatus) { status = initialStatus }
    LaunchedEffect(Unit) {
        refresh(false)
        while (true) {
            now = Instant.now()
            delay(60_000L)
        }
    }
    LaunchedEffect(historyTargets.map { it.symbol }, revision) {
        if (historyTargets.isEmpty()) return@LaunchedEffect
        val limiter = Semaphore(4)
        coroutineScope {
            historyTargets.forEach { stock ->
                launch {
                    limiter.withPermit {
                        val result = try {
                            MarketHistoryCache.load(stock.symbol, "1m", forceRefresh = revision > 0)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            MyStocksCache.HistoryResult()
                        }
                        ensureActive()
                        histories = histories + (stock.symbol to result.points.map { it.close })
                    }
                }
            }
        }
    }

    val subtitle = when (tab) {
        "Movers" -> "See the shares drawing attention today."
        "Sectors" -> "Track sectors and upcoming market events."
        "Calendar" -> "Follow dated company events from the available feed."
        else -> "Understand what is moving the market today."
    }

    Column(Modifier.fillMaxSize().background(palette.background)) {
        MarketPremiumHeader(
            palette = palette,
            subtitle = subtitle,
            busy = busy,
            search = { openCompanies("All") },
            alerts = openAlerts
        )
        MarketPremiumTabs(palette, tab) { tab = it }
        if (error != null) {
            Text(
                error.orEmpty(),
                color = palette.amber,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        when (tab) {
            "Movers" -> MarketMoversPage(palette, gainers, losers, active, histories, openCompany)
            "Sectors" -> MarketSectorsPage(palette, sectors, calendar, openCompanies)
            "Calendar" -> MarketCalendarPage(palette, calendar)
            else -> MarketOverviewPage(
                palette, status, companies, breadth, sectors, gainers, losers, active,
                histories, now, { scope.launch { refresh(true) } }, openCompany, openCompanies
            )
        }
    }
}

@Composable
private fun MarketPremiumHeader(
    palette: PremiumHomePalette,
    subtitle: String,
    busy: Boolean,
    search: () -> Unit,
    alerts: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NseWatcherBrandLockup(Modifier.weight(1f), compact = true)
            MarketHeaderButton(Icons.Default.Search, "Search", palette, search)
            Spacer(Modifier.width(6.dp))
            Box {
                MarketHeaderButton(Icons.Default.NotificationsNone, "Notifications", palette, alerts)
                Box(
                    Modifier.size(7.dp).align(Alignment.TopEnd).offset(x = (-5).dp, y = 5.dp)
                        .clip(CircleShape).background(palette.primary)
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Market", color = palette.text, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = palette.muted, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2)
            }
            Row(Modifier.widthIn(max = 150.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(42.dp).background(palette.primary))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Nairobi Securities Exchange", color = palette.text, fontSize = 9.2.sp, maxLines = 1)
                    Text("REAL DATA. REAL INSIGHTS.", color = palette.muted, fontSize = 7.2.sp, letterSpacing = 1.sp, maxLines = 1)
                }
            }
        }
        if (busy) LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = palette.primary,
            trackColor = palette.border
        )
    }
}

@Composable
private fun MarketHeaderButton(icon: ImageVector, desc: String, palette: PremiumHomePalette, click: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(role = Role.Button, onClick = click),
        shape = CircleShape,
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = palette.text, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun MarketPremiumTabs(palette: PremiumHomePalette, selected: String, select: (String) -> Unit) {
    val tabs = listOf(
        "Overview" to Icons.Outlined.BarChart,
        "Movers" to Icons.Outlined.TrendingUp,
        "Sectors" to Icons.Outlined.PieChart,
        "Calendar" to Icons.Outlined.CalendarMonth
    )
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(tabs, key = { it.first }) { (label, icon) ->
            val active = selected == label
            Surface(
                modifier = Modifier.widthIn(min = 84.dp).height(48.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable(role = Role.Tab) { select(label) },
                shape = RoundedCornerShape(13.dp),
                color = if (active) palette.primary.copy(alpha = .12f) else palette.surface,
                border = BorderStroke(1.dp, if (active) palette.primary else palette.border)
            ) {
                Row(
                    Modifier.padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(icon, null, tint = if (active) palette.primary else palette.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        label,
                        color = if (active) palette.text else palette.muted,
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketOverviewPage(
    palette: PremiumHomePalette,
    status: MyStocksCache.MarketStatus,
    companies: List<Stock>,
    breadth: MarketBreadth,
    sectors: List<MarketSector>,
    gainers: List<Stock>,
    losers: List<Stock>,
    active: List<Stock>,
    histories: Map<String, List<Double>>,
    now: Instant,
    refresh: () -> Unit,
    openCompany: (Stock) -> Unit,
    openCompanies: (String) -> Unit
) {
    val topGainer = gainers.firstOrNull()
    val topLoser = losers.firstOrNull()
    val topActive = active.firstOrNull()
    val latest = companies.mapNotNull { CompanyResearchPresentation.timestamp(it.observedAt) }.maxOrNull()
    val changed = marketChangedLines(breadth, sectors, topActive)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item { MarketPulseCard(palette, status, breadth, sectors, latest, companies, refresh) }
        item { MarketChangedCard(palette, changed) }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cards = listOf(
                    Triple("Top Gainer", topGainer, palette.primary),
                    Triple("Top Loser", topLoser, palette.danger),
                    Triple("Most Active", topActive, palette.secondary)
                )
                if (maxWidth >= 350.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEach { (title, stock, tint) ->
                            MarketOverviewMoverCard(
                                title, stock, tint, stock?.let { histories[it.symbol] }.orEmpty(),
                                palette, Modifier.weight(1f), openCompany
                            )
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(cards) { (title, stock, tint) ->
                            MarketOverviewMoverCard(
                                title, stock, tint, stock?.let { histories[it.symbol] }.orEmpty(),
                                palette, Modifier.width(112.dp), openCompany
                            )
                        }
                    }
                }
            }
        }
        item { MarketSectorSnapshot(palette, sectors.take(6)) { openCompanies("All") } }
        item {
            MarketLearningCard(
                palette, "Why this matters", "A quick note for new investors.",
                "Breadth shows how widely a move is shared across the market. Sector averages help you see where activity is concentrated without treating one company as the whole market."
            )
        }
    }
}

@Composable
private fun MarketPulseCard(
    palette: PremiumHomePalette,
    status: MyStocksCache.MarketStatus,
    breadth: MarketBreadth,
    sectors: List<MarketSector>,
    latest: Instant?,
    companies: List<Stock>,
    refresh: () -> Unit
) {
    PremiumCardSurface(palette) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketRoundIcon(Icons.Outlined.MonitorHeart, palette.secondary, palette)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Market Pulse", color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("A quick view of today’s market activity.", color = palette.muted, fontSize = 10.sp)
            }
            TextButton(onClick = refresh, contentPadding = PaddingValues(3.dp)) {
                Text("15 min delay", color = palette.muted, fontSize = 8.8.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Refresh, "Refresh", tint = palette.muted, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.Stretch, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                Modifier.width(105.dp),
                RoundedCornerShape(14.dp),
                palette.primary.copy(alpha = .08f),
                BorderStroke(1.dp, palette.primary.copy(alpha = .55f))
            ) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape)
                                .background(if (status.isKnown && status.isOpen) palette.primary else palette.muted)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when { !status.isKnown -> "Unknown"; status.isOpen -> "Open"; else -> "Closed" },
                            color = palette.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text("NSE Market", color = palette.primary, fontSize = 9.2.sp)
                    Text(
                        latest?.let { CompanyResearchPresentation.date(it.toString()) } ?: "Time unavailable",
                        color = palette.muted,
                        fontSize = 8.2.sp,
                        lineHeight = 11.sp
                    )
                }
            }
            Surface(
                Modifier.weight(1f),
                RoundedCornerShape(14.dp),
                palette.raised,
                BorderStroke(1.dp, palette.border)
            ) {
                Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    PulseValue(breadth.rising, "Advancers", palette.primary, Modifier.weight(1f))
                    PulseLine(palette)
                    PulseValue(breadth.falling, "Decliners", palette.danger, Modifier.weight(1f))
                    PulseLine(palette)
                    PulseValue(breadth.flat, "Unchanged", palette.muted, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = palette.secondary.copy(alpha = .07f),
            border = BorderStroke(1.dp, palette.secondary.copy(alpha = .35f))
        ) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BarChart, null, tint = palette.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(9.dp))
                Text(marketPulseText(breadth, sectors), color = palette.text, fontSize = 10.2.sp, lineHeight = 15.sp)
            }
        }
        if (companies.isEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text("Market observations are unavailable. Refresh to try again.", color = palette.muted, fontSize = 9.5.sp)
        }
    }
}

@Composable
private fun PulseValue(value: Int, label: String, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = tint, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 7.8.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(.72f).height(3.dp).clip(CircleShape).background(tint.copy(alpha = .65f)))
    }
}

@Composable
private fun PulseLine(palette: PremiumHomePalette) {
    Box(Modifier.width(1.dp).height(55.dp).background(palette.border))
}

@Composable
private fun MarketChangedCard(palette: PremiumHomePalette, lines: List<String>) {
    PremiumCardSurface(palette) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketRoundIcon(Icons.Outlined.Lightbulb, palette.amber, palette)
            Spacer(Modifier.width(9.dp))
            Column {
                Text("What changed today?", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("Key market observations in simple terms.", color = palette.muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (lines.isEmpty()) Text("Not enough current observations are available yet.", color = palette.muted, fontSize = 10.sp)
        lines.take(3).forEach { line ->
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(palette.primary))
                Spacer(Modifier.width(8.dp))
                Text(line, color = palette.text, fontSize = 10.2.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun MarketOverviewMoverCard(
    title: String,
    stock: Stock?,
    tint: Color,
    history: List<Double>,
    palette: PremiumHomePalette,
    modifier: Modifier,
    openCompany: (Stock) -> Unit
) {
    Surface(
        modifier = modifier.height(142.dp).clip(RoundedCornerShape(17.dp))
            .then(if (stock != null) Modifier.clickable { openCompany(stock) } else Modifier),
        shape = RoundedCornerShape(17.dp),
        color = tint.copy(alpha = .08f),
        border = BorderStroke(1.dp, tint.copy(alpha = .45f))
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (title == "Top Loser") Icons.Outlined.SouthEast
                    else if (title == "Most Active") Icons.Outlined.Equalizer
                    else Icons.Outlined.NorthEast,
                    null, tint = tint, modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(title, color = palette.text, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(7.dp))
            if (stock == null) Text("Unavailable", color = palette.muted, fontSize = 9.5.sp)
            else {
                Text(stock.symbol, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    stock.price.takeIf { it.isFinite() && it > 0 }?.let(CompanyResearchPresentation::money) ?: "Price unavailable",
                    color = palette.text, fontSize = 10.sp, maxLines = 1
                )
                Text(
                    if (title == "Most Active") {
                        if (stock.volumeAvailable) formatHomeVolume(stock.volume) else "Volume unavailable"
                    } else stock.change.takeIf { stock.changeAvailable }?.let(CompanyResearchPresentation::percent) ?: "Change unavailable",
                    color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                PremiumSparkline(history, tint, Modifier.fillMaxWidth().height(27.dp))
            }
        }
    }
}

@Composable
private fun MarketSectorSnapshot(palette: PremiumHomePalette, sectors: List<MarketSector>, seeAll: () -> Unit) {
    PremiumCardSurface(palette) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketRoundIcon(Icons.Outlined.PieChart, palette.secondary, palette)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Sector Snapshot", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("How key sectors performed today.", color = palette.muted, fontSize = 10.sp)
            }
            TextButton(onClick = seeAll, contentPadding = PaddingValues(3.dp)) {
                Text("See all sectors", color = palette.secondary, fontSize = 9.sp)
                Icon(Icons.Default.ChevronRight, null, tint = palette.secondary, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (sectors.isEmpty()) Text("Sector observations are unavailable.", color = palette.muted, fontSize = 10.sp)
        sectors.chunked(3).forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { sector -> SectorSmallTile(sector, palette, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectorSmallTile(sector: MarketSector, palette: PremiumHomePalette, modifier: Modifier) {
    val tint = changeTint(sector.average, palette)
    Surface(modifier.height(72.dp), RoundedCornerShape(12.dp), palette.raised, BorderStroke(1.dp, palette.border)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sectorIcon(sector.name), null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    sectorName(sector.name), color = palette.text, fontSize = 8.4.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                sector.average?.let(CompanyResearchPresentation::percent) ?: "Unavailable",
                color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold
            )
            Text("${sector.breadth.covered}/${sector.breadth.total} with data", color = palette.muted, fontSize = 7.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MarketMoversPage(
    palette: PremiumHomePalette,
    gainers: List<Stock>,
    losers: List<Stock>,
    active: List<Stock>,
    histories: Map<String, List<Double>>,
    openCompany: (Stock) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            PremiumCardSurface(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarketRoundIcon(Icons.Outlined.LocalFireDepartment, palette.danger, palette)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Today’s attention board", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "See which shares are gaining, losing or trading the most, based on today’s market observations.",
                            color = palette.muted, fontSize = 10.2.sp, lineHeight = 15.sp
                        )
                    }
                    Icon(Icons.Outlined.Equalizer, null, tint = palette.primary, modifier = Modifier.size(29.dp))
                }
            }
        }
        item { MoversCard("Top Gainers", "Shares with the biggest available price increases today.", Icons.Outlined.NorthEast, palette.primary, gainers.take(3), histories, false, palette, openCompany) }
        item { MoversCard("Top Losers", "Shares with the biggest available price drops today.", Icons.Outlined.SouthEast, palette.danger, losers.take(3), histories, false, palette, openCompany) }
        item { MoversCard("Most Active", "Shares with the highest reported trading volume.", Icons.Outlined.Equalizer, palette.secondary, active.take(3), histories, true, palette, openCompany) }
        item {
            MarketLearningCard(
                palette, "How to read movers", "",
                "Gainers have increased in price, losers have decreased, and most active ranks available share volume. A large move or high volume is a reason to investigate, not a recommendation."
            )
        }
    }
}

@Composable
private fun MoversCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    stocks: List<Stock>,
    histories: Map<String, List<Double>>,
    volume: Boolean,
    palette: PremiumHomePalette,
    openCompany: (Stock) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(19.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, tint.copy(alpha = .42f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarketRoundIcon(icon, tint, palette)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(title, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = palette.muted, fontSize = 9.3.sp)
                }
            }
            Spacer(Modifier.height(7.dp))
            if (stocks.isEmpty()) Text("No matching observations are available.", color = palette.muted, fontSize = 10.sp)
            stocks.forEachIndexed { index, stock ->
                if (index > 0) HorizontalDivider(color = palette.border)
                Row(
                    Modifier.fillMaxWidth().clickable { openCompany(stock) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Surface(Modifier.size(32.dp), RoundedCornerShape(9.dp), tint.copy(alpha = .11f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = tint, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stock.symbol, color = palette.text, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stock.name, color = palette.muted, fontSize = 8.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PremiumSparkline(histories[stock.symbol].orEmpty(), tint, Modifier.width(66.dp).height(29.dp))
                    Column(Modifier.widthIn(min = 82.dp, max = 96.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            stock.price.takeIf { it.isFinite() && it > 0 }?.let(CompanyResearchPresentation::money) ?: "Unavailable",
                            color = palette.text, fontSize = 9.7.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                        Text(
                            if (volume) {
                                if (stock.volumeAvailable) formatHomeVolume(stock.volume) else "Volume unavailable"
                            } else stock.change.takeIf { stock.changeAvailable }?.let(CompanyResearchPresentation::percent) ?: "Change unavailable",
                            color = tint, fontSize = 8.8.sp, fontWeight = FontWeight.Bold, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketSectorsPage(
    palette: PremiumHomePalette,
    sectors: List<MarketSector>,
    calendar: List<MarketCalendarItem>,
    openCompanies: (String) -> Unit
) {
    val ranked = sectors.filter { it.average != null }.sortedByDescending { it.average ?: Double.NEGATIVE_INFINITY }
    val today = LocalDate.now(CompanyResearchPresentation.zone)
    val upcoming = calendar.filter { !it.date.isBefore(today) }.take(4)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            PremiumCardSurface(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarketRoundIcon(Icons.Outlined.PieChart, palette.secondary, palette)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sector Heatmap", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("See how different sectors are performing today.", color = palette.muted, fontSize = 10.sp)
                    }
                    Text("Today", color = palette.secondary, fontSize = 9.sp)
                }
                Spacer(Modifier.height(8.dp))
                if (sectors.isEmpty()) Text("Sector observations are unavailable.", color = palette.muted, fontSize = 10.sp)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 350.dp) 4 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        sectors.take(8).chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                row.forEach { sector ->
                                    SectorHeatTile(sector, palette, Modifier.weight(1f)) { openCompanies(sector.name) }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
        item {
            PremiumCardSurface(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarketRoundIcon(Icons.Outlined.Lightbulb, palette.amber, palette)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Sector leaders", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Key observations from today’s sector averages.", color = palette.muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(7.dp))
                val lines = sectorLeaderLines(ranked)
                if (lines.isEmpty()) Text("Not enough sector data is available.", color = palette.muted, fontSize = 10.sp)
                lines.forEachIndexed { index, line ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Surface(Modifier.size(23.dp), CircleShape, palette.primary.copy(alpha = .1f), BorderStroke(1.dp, palette.primary)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", color = palette.primary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(line, color = palette.text, fontSize = 10.2.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
        item { UpcomingCalendarCard(palette, upcoming) }
        item {
            PremiumCardSurface(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarketRoundIcon(Icons.Outlined.School, palette.amber, palette)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("What to watch next", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Simple prompts to help a new investor.", color = palette.muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                WatchPrompt(Icons.Outlined.BarChart, ranked.firstOrNull()?.let { "${it.name} currently has the strongest available sector average. Open the sector to see the companies behind it." } ?: "Compare sectors only when enough companies have current daily changes.", palette)
                WatchPrompt(Icons.Outlined.Description, "Use company results and announcements to investigate unusual price moves rather than guessing the cause.", palette)
                WatchPrompt(Icons.Outlined.CalendarMonth, upcoming.firstOrNull()?.let { "The next dated event in the feed is ${it.company}: ${it.label} on ${eventDate(it.date)}." } ?: "Check the calendar for dividend dates, results or AGMs when the feed supplies them.", palette)
            }
        }
    }
}

@Composable
private fun SectorHeatTile(sector: MarketSector, palette: PremiumHomePalette, modifier: Modifier, click: () -> Unit) {
    val tint = changeTint(sector.average, palette)
    Surface(
        modifier.height(82.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = click),
        RoundedCornerShape(12.dp),
        tint.copy(alpha = .08f),
        BorderStroke(1.dp, tint.copy(alpha = .38f))
    ) {
        Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sectorIcon(sector.name), null, tint = tint, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(sectorName(sector.name), color = palette.text, fontSize = 8.1.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(sector.average?.let(CompanyResearchPresentation::percent) ?: "Unavailable", color = tint, fontSize = 10.2.sp, fontWeight = FontWeight.ExtraBold)
            Text("${sector.breadth.covered} companies", color = palette.muted, fontSize = 7.sp, maxLines = 1)
        }
    }
}

@Composable
private fun UpcomingCalendarCard(palette: PremiumHomePalette, events: List<MarketCalendarItem>) {
    PremiumCardSurface(palette) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketRoundIcon(Icons.Outlined.CalendarMonth, palette.secondary, palette)
            Spacer(Modifier.width(9.dp))
            Column {
                Text("Upcoming market calendar", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("Dated events returned by the current news feed.", color = palette.muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        if (events.isEmpty()) Text("No upcoming dated events are currently available from the connected feed.", color = palette.muted, fontSize = 10.sp, lineHeight = 15.sp)
        events.forEachIndexed { index, event ->
            if (index > 0) HorizontalDivider(color = palette.border)
            CalendarRow(event, palette)
        }
    }
}

@Composable
private fun MarketCalendarPage(palette: PremiumHomePalette, calendar: List<MarketCalendarItem>) {
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var selectedDateRaw by rememberSaveable { mutableStateOf("") }
    val today = LocalDate.now(CompanyResearchPresentation.zone)
    val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val selectedDate = selectedDateRaw.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val visible = calendar.filter { YearMonth.from(it.date) == month }
        .filter { filter == "All" || it.kind == filter }
        .filter { selectedDate == null || it.date == selectedDate }
        .sortedBy { it.date }
    val strip = calendarStrip(month, today)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        item {
            PremiumCardSurface(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthOffset--; selectedDateRaw = "" }) {
                        Icon(Icons.Default.ChevronLeft, "Previous month", tint = palette.text)
                    }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { monthOffset++; selectedDateRaw = "" }) {
                        Icon(Icons.Default.ChevronRight, "Next month", tint = palette.text)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    strip.forEach { date ->
                        val selected = selectedDate == date
                        val hasEvent = calendar.any { it.date == date }
                        Surface(
                            Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).clickable {
                                selectedDateRaw = if (selected) "" else date.toString()
                            },
                            RoundedCornerShape(11.dp),
                            if (selected) palette.primary.copy(alpha = .13f) else palette.raised,
                            BorderStroke(1.dp, if (selected) palette.primary else palette.border)
                        ) {
                            Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase(Locale.US) }, color = palette.muted, fontSize = 7.5.sp)
                                Text(date.dayOfMonth.toString(), color = if (selected) palette.primary else palette.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                Box(Modifier.size(4.dp).clip(CircleShape).background(if (hasEvent) palette.secondary else Color.Transparent))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Results", "Dividends", "AGM", "Other").forEach { option ->
                        val active = filter == option
                        Surface(
                            Modifier.clip(RoundedCornerShape(20.dp)).clickable { filter = option; selectedDateRaw = "" },
                            RoundedCornerShape(20.dp),
                            if (active) palette.primary.copy(alpha = .12f) else palette.raised,
                            BorderStroke(1.dp, if (active) palette.primary else palette.border)
                        ) {
                            Text(option, color = if (active) palette.primary else palette.muted, fontSize = 9.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            PremiumCardSurface(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MarketRoundIcon(Icons.Outlined.EventNote, palette.secondary, palette)
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text("Market events", color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (selectedDate != null) "Events on ${eventDate(selectedDate)}" else "Upcoming and recent dated events in this month.", color = palette.muted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(7.dp))
                if (visible.isEmpty()) Text("No matching dated events are available from the connected news feed.", color = palette.muted, fontSize = 10.sp, lineHeight = 15.sp)
                visible.forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider(color = palette.border)
                    CalendarRow(event, palette)
                }
            }
        }
        item {
            MarketLearningCard(
                palette, "How to use the calendar",
                "Dates are source-supplied or explicitly stated in sourced stories.",
                "Use the calendar to prepare questions before results, dividends or meetings. An event can matter without implying that a share price will rise or fall."
            )
        }
    }
}

@Composable
private fun CalendarRow(event: MarketCalendarItem, palette: PremiumHomePalette) {
    val tint = when (event.kind) {
        "Dividends" -> palette.amber
        "AGM" -> Color(0xFFB07CFF)
        "Results" -> palette.secondary
        else -> palette.primary
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.width(49.dp), RoundedCornerShape(10.dp), palette.raised, BorderStroke(1.dp, palette.border)) {
            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(event.date.dayOfMonth.toString().padStart(2, '0'), color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(event.date.month.name.take(3), color = palette.muted, fontSize = 8.sp)
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(event.company.ifBlank { "Market event" }, color = palette.text, fontSize = 10.8.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(event.label, color = palette.muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Surface(RoundedCornerShape(20.dp), tint.copy(alpha = .1f), BorderStroke(1.dp, tint.copy(alpha = .65f))) {
            Text(event.kind, color = tint, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun MarketLearningCard(palette: PremiumHomePalette, title: String, subtitle: String, body: String) {
    PremiumCardSurface(palette) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketRoundIcon(Icons.Outlined.School, palette.amber, palette)
            Spacer(Modifier.width(9.dp))
            Column {
                Text(title, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                if (subtitle.isNotBlank()) Text(subtitle, color = palette.muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Surface(RoundedCornerShape(13.dp), palette.primary.copy(alpha = .07f), BorderStroke(1.dp, palette.primary.copy(alpha = .32f))) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MenuBook, null, tint = palette.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(9.dp))
                Text(body, color = palette.text, fontSize = 10.2.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun MarketRoundIcon(icon: ImageVector, tint: Color, palette: PremiumHomePalette) {
    Surface(Modifier.size(39.dp), CircleShape, tint.copy(alpha = .10f), BorderStroke(1.dp, palette.border)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun WatchPrompt(icon: ImageVector, text: String, palette: PremiumHomePalette) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = palette.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = palette.text, fontSize = 9.8.sp, lineHeight = 14.sp)
    }
}

private fun premiumMovers(companies: List<Stock>, type: String): List<Stock> = when (type) {
    "Losers" -> companies.filter { MarketPresentation.validChange(it) && it.change < 0 }.sortedBy { it.change }
    "Active" -> companies.filter { it.volumeAvailable && it.volume > 0 }.sortedByDescending { it.volume }
    else -> companies.filter { MarketPresentation.validChange(it) && it.change > 0 }.sortedByDescending { it.change }
}

private fun marketPulseText(breadth: MarketBreadth, sectors: List<MarketSector>): String {
    val base = when {
        breadth.covered == 0 -> "Daily market breadth is unavailable from the current observations."
        breadth.rising > breadth.falling -> "More shares are rising than falling in the available daily changes."
        breadth.falling > breadth.rising -> "More shares are falling than rising in the available daily changes."
        else -> "Rising and falling shares are balanced in the available daily changes."
    }
    val leader = sectors.firstOrNull { it.average != null }
    return if (leader?.average != null) "$base ${leader.name} has the strongest available sector average at ${CompanyResearchPresentation.percent(leader.average)}." else base
}

private fun marketChangedLines(breadth: MarketBreadth, sectors: List<MarketSector>, active: Stock?): List<String> {
    val lines = mutableListOf<String>()
    if (breadth.covered > 0) lines += "${breadth.rising} companies are rising, ${breadth.falling} are falling and ${breadth.flat} are unchanged among ${breadth.covered} with daily changes."
    sectors.firstOrNull { it.average != null }?.let { lines += "${it.name} has the strongest available sector average at ${CompanyResearchPresentation.percent(it.average!!)}." }
    active?.takeIf { it.volumeAvailable && it.volume > 0 }?.let { lines += "${it.symbol} has the highest reported share volume in the loaded observations (${formatHomeVolume(it.volume)})." }
    return lines
}

private fun sectorLeaderLines(sectors: List<MarketSector>): List<String> {
    val positive = sectors.filter { (it.average ?: 0.0) > 0 }.take(3)
    val negative = sectors.lastOrNull { (it.average ?: 0.0) < 0 }
    val lines = positive.map { "${it.name} is averaging ${CompanyResearchPresentation.percent(it.average!!)} across ${it.breadth.covered} companies with daily changes." }.toMutableList()
    if (negative != null && lines.size < 4) lines += "${negative.name} is lower on average at ${CompanyResearchPresentation.percent(negative.average!!)} across its available daily changes."
    return lines.take(4)
}

private fun changeTint(change: Double?, palette: PremiumHomePalette): Color = when {
    change == null -> palette.muted
    change < 0 -> palette.danger
    change > 0 -> palette.primary
    else -> palette.muted
}

private fun sectorName(raw: String): String = when {
    raw.contains("Telecommunication", true) -> "Telecom"
    raw.contains("Real Estate", true) -> "REITs"
    raw.contains("Commercial", true) -> "Services"
    raw.contains("Automobile", true) -> "Auto"
    else -> raw
}

private fun sectorIcon(raw: String): ImageVector {
    val value = raw.lowercase(Locale.US)
    return when {
        "bank" in value -> Icons.Outlined.AccountBalance
        "telecom" in value -> Icons.Outlined.CellTower
        "manufact" in value -> Icons.Outlined.Factory
        "insurance" in value -> Icons.Outlined.Shield
        "energy" in value || "petroleum" in value || "oil" in value -> Icons.Outlined.Bolt
        "agric" in value -> Icons.Outlined.Eco
        "real estate" in value || "reit" in value -> Icons.Outlined.Apartment
        "invest" in value -> Icons.Outlined.PieChart
        "consumer" in value || "commercial" in value || "service" in value -> Icons.Outlined.Storefront
        "construct" in value -> Icons.Outlined.Construction
        else -> Icons.Outlined.Category
    }
}

private fun premiumCalendar(news: List<NewsItem>, today: LocalDate): List<MarketCalendarItem> {
    val rows = mutableListOf<MarketCalendarItem>()
    news.forEach { item ->
        parseMarketDate(item.exDate)?.let { date ->
            rows += MarketCalendarItem("${item.id}:ex", date, item.companyName.ifBlank { item.symbol }, if (item.dividendAmount.isNotBlank()) "Dividend ex-date · ${item.dividendAmount}" else "Dividend ex-date", "Dividends")
        }
        parseMarketDate(item.paymentDate)?.let { date ->
            rows += MarketCalendarItem("${item.id}:pay", date, item.companyName.ifBlank { item.symbol }, if (item.dividendAmount.isNotBlank()) "Dividend payment · ${item.dividendAmount}" else "Dividend payment", "Dividends")
        }
        val text = listOf(item.title, item.summary).filter { it.isNotBlank() }.joinToString(" ")
        val kind = eventKind(item.category, text)
        if (kind != null) {
            extractMarketDate(text)?.let { date ->
                rows += MarketCalendarItem("${item.id}:story:${date}", date, item.companyName.ifBlank { item.symbol }, item.title, kind)
            }
        }
    }
    return rows.filter { it.date >= today.minusMonths(2) && it.date <= today.plusYears(1) }
        .distinctBy { Triple(it.date, it.kind, it.label.lowercase(Locale.US)) }
        .sortedBy { it.date }
}

private fun eventKind(category: String, text: String): String? {
    val value = "$category $text".lowercase(Locale.US)
    return when {
        "dividend" in value || "book closure" in value || "ex-date" in value -> "Dividends"
        Regex("\\bagm\\b").containsMatchIn(value) || "annual general meeting" in value -> "AGM"
        "results" in value || "earnings" in value || "financial statements" in value -> "Results"
        "rights issue" in value || "corporate action" in value || "share split" in value -> "Other"
        else -> null
    }
}

private fun parseMarketDate(raw: String): LocalDate? {
    val value = raw.trim()
    if (value.isBlank()) return null
    runCatching { return LocalDate.parse(value.take(10)) }
    runCatching { return Instant.parse(value).atZone(CompanyResearchPresentation.zone).toLocalDate() }
    val clean = value.replace(Regex("(\\d)(st|nd|rd|th)", RegexOption.IGNORE_CASE), "$1").replace(Regex("\\s+"), " ").trim()
    for (pattern in listOf("d MMM uuuu", "d MMMM uuuu", "MMM d, uuuu", "MMMM d, uuuu", "d-MMM-uuuu", "dd/MM/uuuu")) {
        try { return LocalDate.parse(clean, DateTimeFormatter.ofPattern(pattern, Locale.US)) }
        catch (_: DateTimeParseException) {}
    }
    return null
}

private fun extractMarketDate(text: String): LocalDate? {
    val clean = text.replace(Regex("(\\d)(st|nd|rd|th)", RegexOption.IGNORE_CASE), "$1").replace(Regex("\\s+"), " ")
    val month = "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
    for (regex in listOf(
        Regex("\\b\\d{1,2}\\s+$month\\s+\\d{4}\\b", RegexOption.IGNORE_CASE),
        Regex("\\b$month\\s+\\d{1,2},?\\s+\\d{4}\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
    )) {
        regex.find(clean)?.value?.let { parseMarketDate(it)?.let { date -> return date } }
    }
    return null
}

private fun calendarStrip(month: YearMonth, today: LocalDate): List<LocalDate> {
    val anchor = if (YearMonth.from(today) == month) today else month.atDay(1)
    var start = anchor.minusDays(2)
    if (YearMonth.from(start) != month) start = month.atDay(1)
    val result = mutableListOf<LocalDate>()
    var cursor = start
    while (result.size < 5 && !cursor.isAfter(month.atEndOfMonth())) {
        result += cursor
        cursor = cursor.plusDays(1)
    }
    while (result.size < 5 && result.isNotEmpty()) {
        val previous = result.first().minusDays(1)
        if (YearMonth.from(previous) != month) break
        result.add(0, previous)
    }
    return result
}

private fun eventDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
