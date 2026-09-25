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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val marketZone: ZoneId = ZoneId.of("Africa/Nairobi")

private data class MarketCalendarEvent(
    val id: String,
    val date: LocalDate,
    val type: String,
    val company: String,
    val detail: String,
    val source: NewsItem?
)

@Composable
internal fun PremiumMarketExperience(
    tab: String,
    onTab: (String) -> Unit,
    status: MyStocksCache.MarketStatus,
    companies: List<Stock>,
    newsFeed: List<NewsItem>,
    breadth: MarketBreadth,
    sectors: List<MarketSector>,
    attention: List<MarketAttentionItem>,
    latest: Instant?,
    historyRevision: Int,
    now: Instant,
    busy: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    openCompany: (Stock) -> Unit,
    openCompanies: (String) -> Unit,
    openNews: (NewsItem) -> Unit,
    openSearch: () -> Unit,
    openAlerts: () -> Unit,
    showSheet: (String) -> Unit
) {
    val calendarEvents = remember(newsFeed) { buildMarketCalendarEvents(newsFeed) }

    if (tab == "Performance") {
        MarketPerformanceView(
            companies = companies,
            revision = historyRevision,
            now = now,
            openCompany = openCompany,
            openCompanies = openCompanies,
            onTab = onTab,
            busy = busy,
            onRefresh = onRefresh,
            openSearch = openSearch,
            openAlerts = openAlerts
        )
        return
    }

    val subtitle = when (tab) {
        "Movers" -> "See the shares drawing attention today."
        "Sectors" -> "Track sectors and upcoming market events."
        "Calendar" -> "Keep an eye on dated company events."
        else -> "Understand what is moving the market today."
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ResearchBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PremiumMarketHeader(subtitle, busy, onRefresh, openSearch, openAlerts)
        }
        item {
            PremiumMarketTabs(tab, onTab)
        }
        if (error != null) {
            item {
                MarketMessageCard(
                    icon = Icons.Outlined.Info,
                    title = "Some market data could not refresh",
                    detail = error
                )
            }
        }
        if (busy && companies.isEmpty()) {
            item {
                MarketMessageCard(
                    icon = Icons.Outlined.Refresh,
                    title = "Loading market observations…",
                    detail = "NSE Watcher is checking the latest available provider data."
                )
            }
        }
        when (tab) {
            "Movers" -> premiumMoversItems(companies, openCompany, showSheet)
            "Sectors" -> premiumSectorItems(sectors, calendarEvents, openCompanies, openNews) { onTab("Calendar") }
            "Calendar" -> premiumCalendarItems(calendarEvents, openNews)
            else -> premiumOverviewItems(
                status, companies, breadth, sectors, attention, latest,
                openCompany, openCompanies,
                onMovers = { onTab("Movers") },
                onSectors = { onTab("Sectors") },
                showSheet = showSheet
            )
        }
    }
}

@Composable
internal fun PremiumMarketHeader(
    subtitle: String,
    busy: Boolean,
    onRefresh: () -> Unit,
    openSearch: () -> Unit,
    openAlerts: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NseWatcherBrandLockup(modifier = Modifier.weight(1f), compact = true)
            MarketHeaderAction(Icons.Outlined.Search, "Search companies", openSearch)
            Spacer(Modifier.width(7.dp))
            MarketHeaderAction(Icons.Outlined.NotificationsNone, "Alerts", openAlerts)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 350.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Market", color = ResearchText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(subtitle, color = ResearchMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Row(Modifier.widthIn(max = 152.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(42.dp).background(ResearchGreen, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("Nairobi Securities Exchange", color = ResearchText, fontSize = 10.5.sp, lineHeight = 14.sp)
                            Text(
                                "REAL DATA. REAL INSIGHTS.",
                                color = ResearchMuted,
                                fontSize = 7.5.sp,
                                letterSpacing = 1.1.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Market", color = ResearchText, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = ResearchMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    Text("Nairobi Securities Exchange · Real data. Real insights.", color = ResearchMuted, fontSize = 10.sp)
                }
            }
        }
        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = ResearchGreen,
                trackColor = ResearchRaised
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp)) {
                    Icon(Icons.Outlined.Refresh, null, tint = ResearchMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", color = ResearchMuted, fontSize = 9.5.sp)
                }
            }
        }
    }
}

@Composable
private fun MarketHeaderAction(icon: ImageVector, description: String, action: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(role = Role.Button, onClick = action),
        shape = CircleShape,
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = ResearchText, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
internal fun PremiumMarketTabs(selected: String, onSelected: (String) -> Unit) {
    val options = listOf(
        "Overview" to Icons.Outlined.BarChart,
        "Movers" to Icons.Outlined.TrendingUp,
        "Sectors" to Icons.Outlined.PieChart,
        "Calendar" to Icons.Outlined.CalendarMonth,
        "Performance" to Icons.Outlined.TrendingUp
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scroll = shouldScrollTabs(
            widthDp = maxWidth.value,
            fontScale = LocalDensity.current.fontScale,
            optionCount = options.size,
            minimumOptionWidthDp = 70f
        )
        if (scroll) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                options.forEach { pair ->
                    PremiumMarketTab(
                        pair = pair,
                        active = selected == pair.first,
                        modifier = Modifier.widthIn(min = 82.dp),
                        onSelected = onSelected
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                options.forEach { pair ->
                    PremiumMarketTab(
                        pair = pair,
                        active = selected == pair.first,
                        modifier = Modifier.weight(1f),
                        onSelected = onSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumMarketTab(
    pair: Pair<String, ImageVector>,
    active: Boolean,
    modifier: Modifier,
    onSelected: (String) -> Unit
) {
    val label = pair.first
    Surface(
        modifier = modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Tab) { onSelected(label) },
        shape = RoundedCornerShape(13.dp),
        color = if (active) ResearchGreen.copy(alpha = 0.12f) else ResearchCard,
        border = BorderStroke(1.dp, if (active) ResearchGreen else ResearchBorder)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                pair.second,
                label,
                tint = if (active) ResearchGreen else ResearchMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                label,
                color = if (active) ResearchGreen else ResearchMuted,
                fontSize = if (label == "Performance") 8.5.sp else 9.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumOverviewItems(
    status: MyStocksCache.MarketStatus,
    companies: List<Stock>,
    breadth: MarketBreadth,
    sectors: List<MarketSector>,
    attention: List<MarketAttentionItem>,
    latest: Instant?,
    openCompany: (Stock) -> Unit,
    openCompanies: (String) -> Unit,
    onMovers: () -> Unit,
    onSectors: () -> Unit,
    showSheet: (String) -> Unit
) {
    item { MarketPulseCard(status, breadth, sectors, latest, showSheet) }
    item { WhatChangedTodayCard(attention, sectors, breadth, onMovers) }
    item { MarketHighlightsRow(companies, openCompany) }
    item { SectorSnapshotCard(sectors, openCompanies, onSectors) }
    item { WhyThisMattersCard(breadth, sectors) }
}

@Composable
private fun MarketPulseCard(
    status: MyStocksCache.MarketStatus,
    breadth: MarketBreadth,
    sectors: List<MarketSector>,
    latest: Instant?,
    showSheet: (String) -> Unit
) {
    MarketSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarketIconBubble(Icons.Outlined.MonitorHeart, ResearchGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Market Pulse", color = ResearchText, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("A quick view of today’s market activity.", color = ResearchMuted, fontSize = 12.sp)
            }
            Text(MarketRefreshController.PROVIDER_DELAY_MINUTES.toString() + " min delay", color = ResearchMuted, fontSize = 10.sp)
            IconButton(onClick = { showSheet("Data coverage") }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Info, "Data coverage", tint = ResearchMuted, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.widthIn(min = 105.dp).weight(0.88f),
                shape = RoundedCornerShape(14.dp),
                color = if (status.isKnown && status.isOpen) ResearchGreen.copy(alpha = 0.10f) else ResearchRaised,
                border = BorderStroke(1.dp, if (status.isKnown && status.isOpen) ResearchGreen else ResearchBorder)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(11.dp).background(
                                if (status.isKnown && status.isOpen) ResearchGreen else ResearchMuted,
                                CircleShape
                            )
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            when { !status.isKnown -> "Unknown"; status.isOpen -> "Open"; else -> "Closed" },
                            color = ResearchText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text("NSE Market", color = if (status.isKnown && status.isOpen) ResearchGreen else ResearchMuted, fontSize = 11.sp)
                    val observed = latest?.atZone(marketZone)
                    Text(
                        observed?.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)) ?: "Date unavailable",
                        color = ResearchMuted,
                        fontSize = 9.5.sp
                    )
                    Text(
                        observed?.format(DateTimeFormatter.ofPattern("HH:mm 'EAT'", Locale.US)) ?: "Time unavailable",
                        color = ResearchMuted,
                        fontSize = 9.5.sp
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1.7f),
                shape = RoundedCornerShape(14.dp),
                color = ResearchRaised,
                border = BorderStroke(1.dp, ResearchBorder)
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    MarketBreadthStat("Advancers", breadth.rising, ResearchGreen, Modifier.weight(1f))
                    MarketBreadthStat("Decliners", breadth.falling, ResearchRed, Modifier.weight(1f))
                    MarketBreadthStat("Unchanged", breadth.flat, ResearchMuted, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = ResearchGreen.copy(alpha = 0.07f),
            border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.45f))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BarChart, null, tint = ResearchGreen, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(marketPulseNarrative(breadth, sectors), color = ResearchText, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun MarketBreadthStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = ResearchMuted, fontSize = 9.5.sp, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(4.dp).background(color.copy(alpha = 0.22f), RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(if (value > 0) 0.68f else 0.12f).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun WhatChangedTodayCard(
    attention: List<MarketAttentionItem>,
    sectors: List<MarketSector>,
    breadth: MarketBreadth,
    onMovers: () -> Unit
) {
    val bullets = remember(attention, sectors, breadth) { marketChangeBullets(attention, sectors, breadth) }
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.Lightbulb,
            Color(0xFFF6C65B),
            "What changed today?",
            "Key market insights in simple terms.",
            "See more",
            onMovers
        )
        Spacer(Modifier.height(10.dp))
        bullets.forEach { bullet ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(7.dp).background(ResearchGreen, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text(bullet, color = ResearchText, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun MarketHighlightsRow(companies: List<Stock>, openCompany: (Stock) -> Unit) {
    val gainer = remember(companies) { marketMoversForPremium(companies, "Gainers").firstOrNull() }
    val loser = remember(companies) { marketMoversForPremium(companies, "Losers").firstOrNull() }
    val active = remember(companies) { marketMoversForPremium(companies, "By volume").firstOrNull() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 350.dp
        if (compact) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 2.dp)) {
                item { MarketHighlightCard("Top Gainer", Icons.Outlined.TrendingUp, gainer, ResearchGreen, gainer?.let { CompanyResearchPresentation.percent(it.change) } ?: "Unavailable", openCompany, Modifier.width(146.dp)) }
                item { MarketHighlightCard("Top Loser", Icons.Outlined.TrendingDown, loser, ResearchRed, loser?.let { CompanyResearchPresentation.percent(it.change) } ?: "Unavailable", openCompany, Modifier.width(146.dp)) }
                item { MarketHighlightCard("Most Active", Icons.Outlined.BarChart, active, MaterialTheme.colorScheme.tertiary, active?.let { formatMarketVolume(it.volume) } ?: "Unavailable", openCompany, Modifier.width(146.dp)) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarketHighlightCard("Top Gainer", Icons.Outlined.TrendingUp, gainer, ResearchGreen, gainer?.let { CompanyResearchPresentation.percent(it.change) } ?: "Unavailable", openCompany, Modifier.weight(1f))
                MarketHighlightCard("Top Loser", Icons.Outlined.TrendingDown, loser, ResearchRed, loser?.let { CompanyResearchPresentation.percent(it.change) } ?: "Unavailable", openCompany, Modifier.weight(1f))
                MarketHighlightCard("Most Active", Icons.Outlined.BarChart, active, MaterialTheme.colorScheme.tertiary, active?.let { formatMarketVolume(it.volume) } ?: "Unavailable", openCompany, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarketHighlightCard(
    title: String,
    icon: ImageVector,
    stock: Stock?,
    accent: Color,
    value: String,
    openCompany: (Stock) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 128.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = stock != null, role = Role.Button) { stock?.let(openCompany) },
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(title, color = ResearchText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Outlined.ChevronRight, null, tint = accent, modifier = Modifier.size(14.dp))
            }
            Text(stock?.symbol ?: "—", color = ResearchText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stock?.price?.takeIf { it.isFinite() && it > 0 }?.let { "KSh " + String.format(Locale.US, "%,.2f", it) } ?: "Price unavailable",
                color = ResearchText,
                fontSize = 10.5.sp,
                maxLines = 1
            )
            Text(value, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun SectorSnapshotCard(
    sectors: List<MarketSector>,
    openCompanies: (String) -> Unit,
    onSectors: () -> Unit
) {
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.PieChart,
            MaterialTheme.colorScheme.tertiary,
            "Sector Snapshot",
            "How key sectors performed today.",
            "See all sectors",
            onSectors
        )
        Spacer(Modifier.height(12.dp))
        if (sectors.isEmpty()) {
            Text("Sector observations are unavailable.", color = ResearchMuted, fontSize = 12.sp)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 340.dp) 2 else 3
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    sectors.take(6).chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            row.forEach { sector ->
                                CompactSectorCard(sector, Modifier.weight(1f)) { openCompanies(sector.name) }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSectorCard(sector: MarketSector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = marketChangeColor(sector.average)
    Surface(
        modifier = modifier.heightIn(min = 76.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(premiumSectorSymbol(sector.name), null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    sector.name,
                    color = ResearchText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                sector.average?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(sector.breadth.covered.toString() + "/" + sector.breadth.total + " with data", color = ResearchMuted, fontSize = 8.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun WhyThisMattersCard(breadth: MarketBreadth, sectors: List<MarketSector>) {
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.School,
            Color(0xFFF6C65B),
            "Why this matters",
            "A quick note for new investors."
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ResearchGreen.copy(alpha = 0.07f),
            border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.45f))
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MenuBook, null, tint = ResearchGreen, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(beginnerMarketMeaning(breadth, sectors), color = ResearchText, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumMoversItems(
    companies: List<Stock>,
    openCompany: (Stock) -> Unit,
    showSheet: (String) -> Unit
) {
    item {
        MarketMessageCard(
            icon = Icons.Outlined.LocalFireDepartment,
            iconTint = ResearchRed,
            title = "Today’s attention board",
            detail = "See which shares are gaining, losing or trading the most, based on the latest available market observations."
        )
    }
    item {
        MoversListCard(
            "Top Gainers",
            "Shares with the biggest available daily price increases.",
            Icons.Outlined.TrendingUp,
            ResearchGreen,
            marketMoversForPremium(companies, "Gainers").take(3),
            "Gainers",
            { showSheet("Movers:Gainers") },
            openCompany
        )
    }
    item {
        MoversListCard(
            "Top Losers",
            "Shares with the biggest available daily price drops.",
            Icons.Outlined.TrendingDown,
            ResearchRed,
            marketMoversForPremium(companies, "Losers").take(3),
            "Losers",
            { showSheet("Movers:Losers") },
            openCompany
        )
    }
    item {
        MoversListCard(
            "Most Active",
            "Shares with the highest reported trading volume.",
            Icons.Outlined.BarChart,
            MaterialTheme.colorScheme.tertiary,
            marketMoversForPremium(companies, "By volume").take(3),
            "By volume",
            { showSheet("Movers:By volume") },
            openCompany
        )
    }
    item {
        MarketMessageCard(
            icon = Icons.Outlined.School,
            iconTint = Color(0xFFF6C65B),
            title = "How to read movers",
            detail = "Gainers rose in the latest daily change, losers fell, and most active ranks the available reported share volume. Activity can draw attention, but it does not by itself explain why a price moved."
        )
    }
}

@Composable
private fun MoversListCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    rows: List<Stock>,
    mode: String,
    onSeeAll: () -> Unit,
    openCompany: (Stock) -> Unit
) {
    MarketSectionCard {
        MarketSectionHeader(icon, accent, title, subtitle, "See all", onSeeAll)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text("No matching observations are available.", color = ResearchMuted, fontSize = 12.sp)
        } else {
            rows.forEachIndexed { index, stock ->
                if (index > 0) HorizontalDivider(color = ResearchBorder.copy(alpha = 0.65f))
                PremiumMoverRow(index + 1, stock, mode, accent, openCompany)
            }
        }
    }
}

@Composable
private fun PremiumMoverRow(rank: Int, stock: Stock, mode: String, accent: Color, openCompany: (Stock) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button) { openCompany(stock) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(34.dp), shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.14f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(rank.toString(), color = accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stock.symbol, color = ResearchText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(stock.name, color = ResearchMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(40.dp).height(30.dp).background(accent.copy(alpha = 0.07f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(
                when {
                    mode == "By volume" -> Icons.Outlined.BarChart
                    stock.change < 0 -> Icons.Outlined.TrendingDown
                    else -> Icons.Outlined.TrendingUp
                },
                null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 88.dp)) {
            Text(
                stock.price.takeIf { it.isFinite() && it > 0 }?.let { "KSh " + String.format(Locale.US, "%,.2f", it) } ?: "Price unavailable",
                color = ResearchText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                if (mode == "By volume") formatMarketVolume(stock.volume) else CompanyResearchPresentation.percent(stock.change),
                color = accent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Outlined.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(17.dp))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumSectorItems(
    sectors: List<MarketSector>,
    calendarEvents: List<MarketCalendarEvent>,
    openCompanies: (String) -> Unit,
    openNews: (NewsItem) -> Unit,
    onCalendar: () -> Unit
) {
    item {
        MarketSectionCard {
            MarketSectionHeader(
                Icons.Outlined.PieChart,
                MaterialTheme.colorScheme.tertiary,
                "Sector Heatmap",
                "See how different sectors are performing in the latest available daily changes."
            )
            Spacer(Modifier.height(12.dp))
            if (sectors.isEmpty()) {
                Text("Sector observations are unavailable.", color = ResearchMuted, fontSize = 12.sp)
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val largeText = LocalDensity.current.fontScale > 1.12f
                    val columns = when {
                        largeText -> 2
                        maxWidth >= 360.dp -> 4
                        else -> 2
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        sectors.take(8).chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                row.forEach { sector ->
                                    HeatmapSectorCard(sector, Modifier.weight(1f)) { openCompanies(sector.name) }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Sector figures are equal-weight averages of available company daily changes, not official NSE sector indices.",
                color = ResearchMuted,
                fontSize = 9.5.sp,
                lineHeight = 14.sp
            )
        }
    }
    item { SectorLeadersCard(sectors, openCompanies) }
    item { UpcomingMarketCalendarPreview(calendarEvents, openNews, onCalendar) }
    item { WhatToWatchNextCard() }
}

@Composable
private fun HeatmapSectorCard(sector: MarketSector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = marketChangeColor(sector.average)
    Surface(
        modifier = modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(13.dp)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(premiumSectorSymbol(sector.name), null, tint = accent, modifier = Modifier.size(21.dp))
            Text(sector.name, color = ResearchText, fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                sector.average?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable",
                color = accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectorLeadersCard(sectors: List<MarketSector>, openCompanies: (String) -> Unit) {
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.Lightbulb,
            Color(0xFFF6C65B),
            "Sector leaders",
            "Key insights from today’s sector performance.",
            if (sectors.isNotEmpty()) "See all sectors" else null,
            { openCompanies("All") }
        )
        Spacer(Modifier.height(8.dp))
        val rows = sectors.filter { it.average != null }.take(4)
        if (rows.isEmpty()) {
            Text("No comparable sector changes are available.", color = ResearchMuted, fontSize = 12.sp)
        } else {
            rows.forEachIndexed { index, sector ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { openCompanies(sector.name) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(modifier = Modifier.size(30.dp), shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, ResearchGreen)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text((index + 1).toString(), color = ResearchGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(sectorLeaderSentence(sector), color = ResearchText, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun UpcomingMarketCalendarPreview(
    events: List<MarketCalendarEvent>,
    openNews: (NewsItem) -> Unit,
    onCalendar: () -> Unit
) {
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.CalendarMonth,
            MaterialTheme.colorScheme.tertiary,
            "Upcoming market calendar",
            "Dated company events and recent market announcements.",
            "See full calendar",
            onCalendar
        )
        Spacer(Modifier.height(8.dp))
        val preview = events.take(4)
        if (preview.isEmpty()) {
            Text(
                "No dated dividend events or recent results/announcements are available in the current feed.",
                color = ResearchMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        } else {
            preview.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = ResearchBorder.copy(alpha = 0.65f))
                CalendarEventRow(event, openNews)
            }
        }
    }
}

@Composable
private fun WhatToWatchNextCard() {
    MarketSectionCard {
        MarketSectionHeader(
            Icons.Outlined.School,
            Color(0xFFF6C65B),
            "What to watch next",
            "Simple prompts to help a new investor investigate further."
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stack = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.12f
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WatchPrompt(Icons.Outlined.BarChart, "Compare sector breadth with the companies driving it.")
                    WatchPrompt(Icons.Outlined.Description, "Check company results for evidence behind a large move.")
                    WatchPrompt(Icons.Outlined.CalendarMonth, "Check supplied dividend dates and recent corporate announcements.")
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WatchPrompt(Icons.Outlined.BarChart, "Compare sector breadth with the companies driving it.", Modifier.weight(1f))
                    WatchPrompt(Icons.Outlined.Description, "Check company results for evidence behind a large move.", Modifier.weight(1f))
                    WatchPrompt(Icons.Outlined.CalendarMonth, "Check supplied dividend dates and recent corporate announcements.", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WatchPrompt(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = ResearchText, fontSize = 10.5.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.premiumCalendarItems(
    events: List<MarketCalendarEvent>,
    openNews: (NewsItem) -> Unit
) {
    item { PremiumCalendarView(events, openNews) }
}

@Composable
private fun PremiumCalendarView(events: List<MarketCalendarEvent>, openNews: (NewsItem) -> Unit) {
    val today = remember { LocalDate.now(marketZone) }
    var anchorText by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedText by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val anchor = remember(anchorText) { runCatching { LocalDate.parse(anchorText) }.getOrDefault(today) }
    val selected = remember(selectedText) {
        selectedText.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    val week = remember(anchor) { (-2L..2L).map { anchor.plusDays(it) } }
    val filtered = remember(events, filter, selected) {
        events.filter { event ->
            (filter == "All" || event.type == filter || (filter == "Other" && event.type == "Corporate Action")) &&
                (selected == null || event.date == selected)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MarketSectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { anchorText = anchor.minusDays(7).toString(); selectedText = "" }) {
                    Icon(Icons.Outlined.ChevronLeft, "Previous week", tint = ResearchMuted)
                }
                Text(
                    anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                    color = ResearchText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { anchorText = anchor.plusDays(7).toString(); selectedText = "" }) {
                    Icon(Icons.Outlined.ChevronRight, "Next week", tint = ResearchMuted)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                week.forEach { date ->
                    val active = selected == date
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { selectedText = if (active) "" else date.toString() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) ResearchGreen.copy(alpha = 0.13f) else ResearchRaised,
                        border = BorderStroke(1.dp, if (active) ResearchGreen else ResearchBorder)
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(date.format(DateTimeFormatter.ofPattern("EEE", Locale.US)).take(3), color = ResearchMuted, fontSize = 8.5.sp)
                            Text(
                                date.dayOfMonth.toString(),
                                color = if (active) ResearchGreen else ResearchText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("All", "Results", "Dividends", "AGM", "Other").forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ResearchRaised,
                            labelColor = ResearchMuted,
                            selectedContainerColor = ResearchGreen.copy(alpha = 0.16f),
                            selectedLabelColor = ResearchGreen
                        ),
                        border = BorderStroke(1.dp, if (filter == option) ResearchGreen else ResearchBorder)
                    )
                }
            }
        }

        MarketSectionCard {
            MarketSectionHeader(
                Icons.Outlined.Event,
                MaterialTheme.colorScheme.tertiary,
                if (selected == null) "Upcoming & recent market events" else selected.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)),
                "Dates are shown only when supplied or safely derived from a dated published item."
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    if (selected == null) "No dated market events are available in the current feed."
                    else "No matching events are available for this date.",
                    color = ResearchMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            } else {
                filtered.take(12).forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider(color = ResearchBorder.copy(alpha = 0.65f))
                    CalendarEventRow(event, openNews)
                }
            }
        }

        MarketMessageCard(
            icon = Icons.Outlined.Info,
            title = "How Calendar dates work",
            detail = "Dividend ex-dates and payment dates come from supplied event fields. Results and announcements use the article’s published date unless the feed supplies a separate event date. Confirm important corporate dates with the issuer or exchange."
        )
    }
}

@Composable
private fun CalendarEventRow(event: MarketCalendarEvent, openNews: (NewsItem) -> Unit) {
    val accent = calendarAccent(event.type)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable(enabled = event.source != null, role = Role.Button) { event.source?.let(openNews) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.width(47.dp).height(52.dp),
            shape = RoundedCornerShape(10.dp),
            color = ResearchRaised,
            border = BorderStroke(1.dp, ResearchBorder)
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(event.date.dayOfMonth.toString().padStart(2, '0'), color = ResearchText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(event.date.format(DateTimeFormatter.ofPattern("MMM", Locale.US)).uppercase(Locale.US), color = ResearchMuted, fontSize = 8.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        MarketIconBubble(calendarIcon(event.type), accent, 34.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(event.company, color = ResearchText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(event.detail, color = ResearchMuted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Surface(shape = RoundedCornerShape(18.dp), color = accent.copy(alpha = 0.12f), border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))) {
            Text(event.type, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = accent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
        }
        if (event.source != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ChevronRight, null, tint = ResearchMuted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
internal fun MarketSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ResearchCard,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
internal fun MarketSectionHeader(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MarketIconBubble(icon, iconTint)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ResearchText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = ResearchMuted, fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        if (action != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)) {
                Text(action, color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun MarketMessageCard(icon: ImageVector, title: String, detail: String, iconTint: Color = ResearchGreen) {
    MarketSectionCard {
        Row(verticalAlignment = Alignment.Top) {
            MarketIconBubble(icon, iconTint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ResearchText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(3.dp))
                Text(detail, color = ResearchMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
internal fun MarketIconBubble(icon: ImageVector, tint: Color, size: Dp = 42.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = tint.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.50f))
        }
    }
}

private fun marketPulseNarrative(breadth: MarketBreadth, sectors: List<MarketSector>): String {
    val base = when {
        breadth.covered == 0 -> "Daily direction is not available for enough companies yet."
        breadth.rising > breadth.falling -> "More shares are rising than falling in the latest available daily changes."
        breadth.falling > breadth.rising -> "More shares are falling than rising in the latest available daily changes."
        else -> "Rising and falling shares are broadly balanced in the latest available daily changes."
    }
    val leader = sectors.firstOrNull { it.average != null }
    return if (leader?.average != null) {
        base + " " + leader.name + " is among the stronger available sector averages at " +
            CompanyResearchPresentation.percent(leader.average) + "."
    } else base
}

private fun marketChangeBullets(
    attention: List<MarketAttentionItem>,
    sectors: List<MarketSector>,
    breadth: MarketBreadth
): List<String> {
    val bullets = mutableListOf<String>()
    if (breadth.covered > 0) {
        bullets += breadth.rising.toString() + " shares are rising, " + breadth.falling +
            " are falling and " + breadth.flat + " are unchanged in the available daily changes."
    }
    attention.take(2).forEach { item ->
        val reason = item.reasons.firstOrNull()?.title?.lowercase(Locale.US) ?: "notable movement"
        bullets += item.stock.symbol + " is drawing attention with " +
            CompanyResearchPresentation.percent(item.stock.change) + " and " + reason + "."
    }
    if (bullets.size < 3) {
        sectors.firstOrNull { it.average != null }?.let { sector ->
            bullets += sector.name + " has an available equal-weight sector average of " +
                CompanyResearchPresentation.percent(sector.average!!) + "."
        }
    }
    if (bullets.isEmpty()) bullets += "There is not enough comparable market data to produce a daily summary yet."
    return bullets.take(3)
}

private fun beginnerMarketMeaning(breadth: MarketBreadth, sectors: List<MarketSector>): String {
    val breadthText = when {
        breadth.covered == 0 -> "Market breadth tells you how widely a move is shared across companies."
        breadth.rising > breadth.falling -> "When more shares rise than fall, the positive move is spread across more of the available companies."
        breadth.falling > breadth.rising -> "When more shares fall than rise, weakness is spread across more of the available companies."
        else -> "When rising and falling counts are close, market direction is mixed."
    }
    val sectorText = if (sectors.any { it.average != null }) {
        " Sector averages can then show where that strength or weakness is concentrated."
    } else ""
    return breadthText + sectorText
}

private fun sectorLeaderSentence(sector: MarketSector): String {
    val average = sector.average?.let { CompanyResearchPresentation.percent(it) } ?: "unavailable"
    val direction = when {
        (sector.average ?: 0.0) > 0 -> "higher"
        (sector.average ?: 0.0) < 0 -> "lower"
        else -> "flat"
    }
    return sector.name + " is " + direction + " at an equal-weight average of " + average +
        " across " + sector.breadth.covered + " available company changes."
}

private fun marketMoversForPremium(companies: List<Stock>, type: String): List<Stock> = when (type) {
    "By volume" -> companies.filter { it.volumeAvailable && it.volume > 0 }.sortedByDescending { it.volume }
    "Losers" -> companies.filter { MarketPresentation.validChange(it) && it.change < 0 }.sortedBy { it.change }
    else -> companies.filter { MarketPresentation.validChange(it) && it.change > 0 }.sortedByDescending { it.change }
}

private fun formatMarketVolume(value: Long): String = when {
    value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB shares", value / 1_000_000_000.0)
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM shares", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fK shares", value / 1_000.0)
    else -> String.format(Locale.US, "%,d shares", value)
}

@Composable
private fun marketChangeColor(value: Double?): Color = when {
    value == null || !value.isFinite() || value == 0.0 -> ResearchMuted
    value > 0 -> ResearchGreen
    else -> ResearchRed
}

internal fun premiumSectorSymbol(sector: String): ImageVector {
    val name = sector.trim().lowercase(Locale.US)
    return when {
        name.contains("bank") -> Icons.Outlined.AccountBalance
        name.contains("telecom") -> Icons.Outlined.CellTower
        name.contains("agric") || name.contains("farm") -> Icons.Outlined.Eco
        name.contains("energy") || name.contains("petroleum") || name.contains("oil") || name.contains("gas") -> Icons.Outlined.Bolt
        name.contains("insurance") -> Icons.Outlined.Shield
        name.contains("construct") || name.contains("allied") -> Icons.Outlined.Construction
        name.contains("manufactur") -> Icons.Outlined.Factory
        name.contains("real estate") || name.contains("reit") || name.contains("property") -> Icons.Outlined.Apartment
        name.contains("invest") || name.contains("exchange traded") || name.contains("etf") -> Icons.Outlined.PieChart
        name.contains("consumer") || name.contains("commercial") || name.contains("service") -> Icons.Outlined.ShoppingCart
        else -> Icons.Outlined.Category
    }
}

private fun buildMarketCalendarEvents(news: List<NewsItem>): List<MarketCalendarEvent> {
    val today = LocalDate.now(marketZone)
    val events = mutableListOf<MarketCalendarEvent>()
    news.forEach { item ->
        val company = item.companyName.ifBlank { item.symbol.ifBlank { item.source.ifBlank { "Market update" } } }
        parseMarketDate(item.exDate)?.let { date ->
            if (date >= today.minusDays(14) && date <= today.plusMonths(6)) {
                events += MarketCalendarEvent(
                    item.id + ":ex:" + date,
                    date,
                    "Dividends",
                    company,
                    "Ex-dividend date" + item.dividendAmount.takeIf { it.isNotBlank() }?.let { " · " + it }.orEmpty(),
                    item
                )
            }
        }
        parseMarketDate(item.paymentDate)?.let { date ->
            if (date >= today.minusDays(14) && date <= today.plusMonths(6)) {
                events += MarketCalendarEvent(
                    item.id + ":pay:" + date,
                    date,
                    "Dividends",
                    company,
                    "Dividend payment date" + item.dividendAmount.takeIf { it.isNotBlank() }?.let { " · " + it }.orEmpty(),
                    item
                )
            }
        }
        val published = parseMarketDate(item.publishedAt)
        if (published != null && published >= today.minusDays(30) && published <= today.plusDays(1)) {
            when {
                isMarketResultsItem(item) -> events += MarketCalendarEvent(
                    item.id + ":results:" + published,
                    published,
                    "Results",
                    company,
                    "Results coverage published · " + item.source.ifBlank { "Source unavailable" },
                    item
                )
                isMarketAgmItem(item) -> events += MarketCalendarEvent(
                    item.id + ":agm:" + published,
                    published,
                    "AGM",
                    company,
                    "AGM-related announcement published · " + item.source.ifBlank { "Source unavailable" },
                    item
                )
                isMarketAnnouncementItem(item) -> events += MarketCalendarEvent(
                    item.id + ":announcement:" + published,
                    published,
                    "Corporate Action",
                    company,
                    "Corporate announcement published · " + item.source.ifBlank { "Source unavailable" },
                    item
                )
            }
        }
    }
    return events.distinctBy { it.id }.sortedWith(
        compareBy<MarketCalendarEvent> { if (it.date >= today) 0 else 1 }
            .thenBy { if (it.date >= today) it.date.toEpochDay() else -it.date.toEpochDay() }
            .thenBy { it.company }
    )
}

private fun isMarketResultsItem(item: NewsItem): Boolean =
    item.category.equals("Results", true) ||
        item.category.equals("Financial Results", true) ||
        Regex("\\b(earnings|financial results|annual results|interim results|half.year results|full.year results|quarterly results)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(item.title)

private fun isMarketAgmItem(item: NewsItem): Boolean =
    Regex("\\b(agm|annual general meeting)\\b", RegexOption.IGNORE_CASE)
        .containsMatchIn(item.title)

private fun isMarketAnnouncementItem(item: NewsItem): Boolean =
    item.category.equals("Corporate Actions", true) ||
        item.category.equals("Announcements", true) ||
        Regex("\\b(corporate action|book closure|rights issue|bonus issue)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(item.title)

private fun parseMarketDate(raw: String): LocalDate? {
    val value = raw.trim()
    if (value.isBlank()) return null
    CompanyResearchPresentation.timestamp(value)?.let { return it.atZone(marketZone).toLocalDate() }
    runCatching { LocalDate.parse(value.take(10)) }.getOrNull()?.let { return it }
    val formats = listOf(
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    )
    formats.forEach { formatter ->
        runCatching { LocalDate.parse(value, formatter) }.getOrNull()?.let { return it }
    }
    return null
}

@Composable
private fun calendarAccent(type: String): Color = when (type) {
    "Dividends" -> Color(0xFFF0B531)
    "Results" -> MaterialTheme.colorScheme.tertiary
    "AGM" -> Color(0xFF9B6BFF)
    else -> ResearchGreen
}

private fun calendarIcon(type: String): ImageVector = when (type) {
    "Dividends" -> Icons.Outlined.Payments
    "Results" -> Icons.Outlined.Description
    "AGM" -> Icons.Outlined.AccountBalance
    else -> Icons.Outlined.Campaign
}
