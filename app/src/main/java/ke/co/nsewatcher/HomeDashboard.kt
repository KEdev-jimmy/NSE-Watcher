package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.MyStocksCache

private val HomeGreen = Color(0xFF00A859)
private val HomeLightGreen = Color(0xFFE9F8F0)
private val HomeDarkGreen = Color(0xFF063D2A)
private val HomeTextDark = Color(0xFF12352A)
private val HomeMuted = Color(0xFF64756D)
private val HomeBorder = Color(0xFFDDE9E3)
private val HomeRed = Color(0xFFE94A4A)


@Composable
fun HomeDashboard(
    currentStocks: List<Stock>,
    openCompany: (Stock) -> Unit,
    openNews: (NewsItem) -> Unit,
    openMarket: () -> Unit = {},
    openWatchlist: () -> Unit = {},
    initialNews: List<NewsItem> = emptyList(),
    initialMarketStatus: MyStocksCache.MarketStatus = MyStocksCache.MarketStatus(),
    startupDataLoaded: Boolean = false
) {
    var news by remember(initialNews) { mutableStateOf(initialNews) }
    var newsLoading by remember(startupDataLoaded) { mutableStateOf(!startupDataLoaded) }
    var newsError by remember { mutableStateOf<String?>(null) }
    var marketStatus by remember(initialMarketStatus) { mutableStateOf(initialMarketStatus) }

    LaunchedEffect(currentStocks, startupDataLoaded) {
        if (startupDataLoaded) return@LaunchedEffect

        marketStatus = MyStocksCache.loadMarketStatus()
        val result = NewsCache.loadFeedResult()
        news = result.items
        newsError = result.error
        newsLoading = false
    }

    val intelligence = remember(currentStocks, news, marketStatus.isOpen) { HomeIntelligenceEngine.build(currentStocks, news) }
    val breadth = intelligence.breadth
    val gainers = intelligence.gainers
    val losers = intelligence.losers
    val sectorChanges = intelligence.sectors.map { it.sector to it.averageChangePct }
    val corporateActions = intelligence.corporateActions
    val companyNews = intelligence.companyNews

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item { HomeHero(breadth.advancing, breadth.declining, breadth.unchanged, breadth.reportedVolume, marketStatus) }
        item { Spacer(Modifier.height(16.dp)); HomeSectionHeader("YOUR MARKET RADAR", "What is happening?", "A simple view of the latest available NSE evidence.") }
        item { Spacer(Modifier.height(8.dp)); TodaysIntelligence(intelligence.intelligence, currentStocks, news, openCompany, openNews, openMarket) }
        item { Spacer(Modifier.height(18.dp)); HomeSectionHeader("MARKET MOVEMENT", "What's moving", "The strongest and weakest observed price changes.") }
        item { Spacer(Modifier.height(8.dp)); if (gainers.isNotEmpty() || losers.isNotEmpty()) MarketMovers(gainers, losers, openCompany) else EmptyHomeCard("Movement unavailable", "Verified daily change data is not available for the current feed.") }
        item { Spacer(Modifier.height(18.dp)); HomeSectionHeader("EXPLAINED", "What changed?", "Observable changes, with evidence attached.") }
        item { Spacer(Modifier.height(8.dp)); WhatChanged(intelligence.changes, openCompany, currentStocks) }
        if (sectorChanges.isNotEmpty()) {
            item { Spacer(Modifier.height(18.dp)); HomeSectionHeader("MARKET STRUCTURE", "Sector pulse", "Calculated from available counters — not an official sector index.") }
            item { Spacer(Modifier.height(8.dp)); LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(sectorChanges.take(5)) { (sector, change) -> SectorPulseCard(sector, change) } } }
        }
        if (companyNews.isNotEmpty() || newsLoading || newsError != null) {
            item { Spacer(Modifier.height(18.dp)); HomeSectionHeader("FROM THE NEWS FEED", "Worth knowing", "Company-linked information surfaced from the available feed.", if (news.isNotEmpty()) "All news" else null, if (news.isNotEmpty()) { { openNews(news.first()) } } else null) }
            item { Spacer(Modifier.height(8.dp)); when { companyNews.isNotEmpty() -> Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { companyNews.take(3).forEach { story -> IntelligenceNewsCard(story) { openNews(story) } } }; newsLoading -> LoadingHomeCard("Preparing the latest market news…"); else -> EmptyHomeCard("News temporarily unavailable", "The feed returned an error. NSE Watcher will not invent a story.") } }
        }
        if (corporateActions.isNotEmpty()) {
            item { Spacer(Modifier.height(18.dp)); HomeSectionHeader("CORPORATE EVENTS", "Corporate actions", "Dividends, rights, bonuses and announcements.", "View market", openMarket) }
            item { Spacer(Modifier.height(8.dp)); Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { corporateActions.take(2).forEach { action -> CorporateActionCard(action) { openNews(action) } } } }
        }
        item { Spacer(Modifier.height(18.dp)); WatchlistQuickAccess(openWatchlist); Spacer(Modifier.height(14.dp)); HomeQuickActions(openMarket, if (news.isNotEmpty()) { { openNews(news.first()) } } else null); Spacer(Modifier.height(12.dp)); Text(marketDataFooter(currentStocks), color = HomeMuted, fontSize = 8.sp, lineHeight = 11.sp, modifier = Modifier.padding(horizontal = 17.dp)) }
    }
}

@Composable
private fun HomeHero(advancing: Int, declining: Int, unchanged: Int, reportedVolume: Long, marketStatus: MyStocksCache.MarketStatus) {
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))) {
        Box(Modifier.fillMaxSize().background(Color(0x55061A13)))
        Box(Modifier.fillMaxSize().background(Color(0x2500A859)))
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(HomeGreen), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShowChart, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("NSE WATCHER", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp); Text("Understand the market. Follow the evidence.", color = Color(0xFFD6EDE3), fontSize = 10.sp, maxLines = 1) }
                Icon(Icons.Default.NotificationsNone, "Notifications", tint = Color.White, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.height(22.dp)); Text(greetingForNairobi(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(3.dp)); Text("Here is your latest NSE market snapshot.", color = Color(0xFFD7E9E2), fontSize = 11.sp); Spacer(Modifier.height(18.dp))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xE61A3C31), border = BorderStroke(1.dp, Color(0x5539D995))) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("MARKET BREADTH", color = Color(0xFF9ED9BF), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.7.sp); Text(when { advancing > declining -> "More stocks are advancing"; declining > advancing -> "More stocks are declining"; else -> "Advancers and decliners are balanced" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                        Surface(shape = RoundedCornerShape(20.dp), color = if (marketStatus.isKnown && marketStatus.isOpen) Color(0x3327C982) else Color(0x33FFFFFF)) { Text(if (!marketStatus.isKnown) "STATUS UNKNOWN" else if (marketStatus.isOpen) "MARKET OPEN" else "MARKET CLOSED", color = if (marketStatus.isKnown && marketStatus.isOpen) Color(0xFF7CE6B3) else Color(0xFFD5E1DC), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
                    }
                    Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { HeroMetric("Advancing", advancing.toString(), HomeGreen, Modifier.weight(1f)); HeroMetric("Declining", declining.toString(), HomeRed, Modifier.weight(1f)); HeroMetric("Unchanged", unchanged.toString(), Color(0xFFD4DFDB), Modifier.weight(1f)) }
                    Spacer(Modifier.height(9.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.BarChart, null, tint = Color(0xFF9ED9BF), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text("Reported volume  " + formatShares(reportedVolume), color = Color(0xFFD5E5DE), fontSize = 9.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Text("Latest available", color = Color(0xFF9ED9BF), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, accent: Color, modifier: Modifier) { Surface(modifier = modifier, shape = RoundedCornerShape(13.dp), color = Color(0x331A5A45)) { Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) { Text(label, color = Color(0xFFB8D5C9), fontSize = 7.sp, maxLines = 1); Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Box(Modifier.padding(top = 3.dp).width(20.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(accent)) } } }
@Composable
private fun CompactBreadthCard(advancing: Int, declining: Int, unchanged: Int, reportedVolume: Long) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE604543C)),
        border = BorderStroke(1.dp, Color(0x5539D995))
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            BreadthLine("Adv", advancing, HomeGreen)
            Spacer(Modifier.width(10.dp))
            BreadthLine("Dec", declining, HomeRed)
            Spacer(Modifier.width(10.dp))
            BreadthLine("Flat", unchanged, Color(0xFFD4DFDB))
            Spacer(Modifier.width(12.dp))
            Text("Vol ${formatShares(reportedVolume)}", color = Color(0xFFD7EAE1), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BreadthLine(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFD7EAE1), fontSize = 8.sp)
        Spacer(Modifier.width(4.dp))
        Text(value.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MarketIndexPulse(indices: List<MyStocksCache.MarketIndex>, marketStatus: MyStocksCache.MarketStatus) {
    val shown = indices.filter { it.symbol in setOf("^NASI", "^N20I", "^N25I") }.take(3)
    if (shown.isEmpty()) return
    Column(Modifier.padding(horizontal = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            shown.forEach { index ->
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HomeLightGreen)
                        .border(1.dp, HomeBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    Text(indexLabel(index.symbol), color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(String.format(Locale.US, "%.2f", index.value), color = HomeTextDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    index.changePct?.let {
                        Text(
                            String.format(Locale.US, "%+.2f%%", it),
                            color = if (it >= 0) HomeGreen else HomeRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (index.asOf.isNotBlank()) {
                        Text(freshnessLabel(index.asOf, index.freshnessMode), color = HomeMuted, fontSize = 7.sp, maxLines = 1)
                    }
                }
            }
        }
        Text(
            "NSE index data • MyStocks Africa • freshness shown from observation time",
            color = HomeMuted,
            fontSize = 7.sp,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
        )
    }
}

private fun freshnessMode(mode: String): HomeMarketDataMode = when (mode) {
    "CURRENT_SESSION" -> HomeMarketDataMode.CURRENT_SESSION
    "END_OF_DAY" -> HomeMarketDataMode.END_OF_DAY
    "STALE" -> HomeMarketDataMode.STALE
    else -> HomeMarketDataMode.UNKNOWN
}

private fun freshnessLabel(asOf: String, freshnessMode: String): String = when (freshnessMode(freshnessMode)) {
    HomeMarketDataMode.CURRENT_SESSION -> {
        val display = runCatching {
            Instant.parse(asOf).atZone(ZoneId.of("Africa/Nairobi")).toLocalTime().toString().take(5)
        }.getOrDefault(asOf.replace("T", " ").take(16))
        "Current observation • $display EAT"
    }
    HomeMarketDataMode.END_OF_DAY -> "End of day • " + asOf.take(10)
    HomeMarketDataMode.STALE -> "Previous session • " + asOf.take(10)
    HomeMarketDataMode.UNKNOWN -> "As of " + asOf.replace("T", " ").removeSuffix("Z").take(16)
}

private fun indexLabel(symbol: String): String = when (symbol) {
    "^NASI" -> "NASI"
    "^N20I" -> "NSE 20"
    "^N25I" -> "NSE 25"
    else -> symbol.removePrefix("^")
}

@Composable
private fun MarketFreshnessStrip(stocks: List<Stock>, marketStatus: MyStocksCache.MarketStatus) {
    val controllerState by MarketRefreshController.state
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val available = stocks.filter { it.price.isFinite() && it.price > 0.0 }
    val source = available.map { it.source.trim() }.firstOrNull { it.isNotBlank() } ?: "Market source unavailable"
    val newestObservedAt = available.mapNotNull { it.observedAt.takeIf(String::isNotBlank)?.let(::parseObservationTime) }.maxOrNull()
    val ageMinutes = newestObservedAt?.let { ((System.currentTimeMillis() - it.toEpochMilli()).coerceAtLeast(0L) / 60_000L) }
    val freshness = when {
        available.isEmpty() -> "Data unavailable"
        !marketStatus.isKnown -> "Freshness unknown"
        !marketStatus.isOpen -> "Previous session"
        newestObservedAt == null -> "Freshness unknown"
        ageMinutes != null && ageMinutes > 30 -> "Stale • ${ageMinutes}m old"
        ageMinutes != null -> "Delayed • ${ageMinutes}m old"
        else -> "Freshness unknown"
    }
    val coverage = if (available.isNotEmpty()) available.size.toString() + " valid quotes" else "No valid quotes"
    val refreshStatus = when {
        controllerState.refreshInProgress -> "Refreshing market data…"
        controllerState.lastRefreshFailed -> "Last refresh failed"
        controllerState.lastSuccessfulRefreshMs == null -> "Waiting for first refresh"
        !marketStatus.isKnown -> controllerState.lastSuccessfulRefreshMs?.let { "Market status unavailable • Last checked " + formatLocalTime(it) } ?: "Market status unavailable"
        !marketStatus.isOpen -> controllerState.lastSuccessfulRefreshMs?.let { closedMarketStatus(marketStatus, it) } ?: "Market closed"
        else -> { val next = MarketRefreshController.formatCountdown(MarketRefreshController.secondsUntilNextCheck(nowMs)); if (available.isEmpty()) "Market open • data unavailable" else "Market open • delayed feed • next check $next" }
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        RoundedCornerShape(10.dp),
        color = HomeLightGreen,
        border = BorderStroke(1.dp, HomeBorder)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = HomeDarkGreen, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Market data", color = HomeTextDark, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text("• $freshness", color = HomeMuted, fontSize = 8.sp)
                Spacer(Modifier.weight(1f))
                Text(coverage, color = HomeMuted, fontSize = 8.sp)
            }
            Text(refreshStatus, color = HomeDarkGreen, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 21.dp, top = 3.dp))
            Text(
                if (marketStatus.isOpen) {
                    "Source: $source • ${if (ageMinutes != null) "Latest observation ${ageMinutes}m ago." else "Observation time unavailable."} The feed is exchange-supplied and delayed; unchanged prices can be legitimate."
                } else if (!marketStatus.isKnown) {
                    "Source: $source • Market status is unavailable, so no open/closed state is inferred."
                } else {
                    "Source: $source • Market is closed, so no countdown is shown."
                },
                color = HomeMuted, fontSize = 7.sp, modifier = Modifier.padding(start = 21.dp, top = 2.dp)
            )
        }
    }
}

private fun closedMarketStatus(status: MyStocksCache.MarketStatus, lastSuccessfulRefreshMs: Long): String {
    val nextOpen = status.nextOpen?.let(::formatNairobiTime)
    return if (nextOpen != null) {
        "Market closed • Next open $nextOpen"
    } else {
        "Market closed • Last checked " + formatLocalTime(lastSuccessfulRefreshMs)
    }
}

private fun formatNairobiTime(value: String): String? = runCatching {
    Instant.parse(value).atZone(ZoneId.of("Africa/Nairobi")).toLocalTime().toString().take(5) + " EAT"
}.getOrNull()

private fun parseObservationTime(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

private fun formatLocalTime(valueMs: Long): String =
    Instant.ofEpochMilli(valueMs).atZone(ZoneId.of("Africa/Nairobi")).toLocalTime().toString().take(5) + " EAT"

@Composable
private fun HomeSectionHeader(eyebrow: String, title: String, subtitle: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) { Text(eyebrow, color = HomeGreen, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp); Spacer(Modifier.height(2.dp)); Text(title, color = HomeTextDark, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = HomeMuted, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 2) }
        if (action != null && onAction != null) Text(action, color = HomeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction).padding(start = 8.dp, bottom = 2.dp))
    }
}
@Composable
private fun TodaysIntelligence(
    items: List<HomeIntelligenceItem>,
    currentStocks: List<Stock>,
    news: List<NewsItem>,
    openCompany: (Stock) -> Unit,
    openNews: (NewsItem) -> Unit,
    openMarket: () -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        items.take(3).forEach { item ->
            val accent = when (item.type) {
                HomeIntelligenceType.NEWS -> HomeDarkGreen
                HomeIntelligenceType.CALCULATION -> if (item.calculation?.contains("+") == true) HomeGreen else HomeRed
                HomeIntelligenceType.FACT -> HomeGreen
            }
            val targetStock = item.symbol.takeIf { it.isNotBlank() }?.let { symbol ->
                currentStocks.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
            }
            val targetNews = if (item.type == HomeIntelligenceType.NEWS) {
                news.firstOrNull { it.id.toString() == item.evidence.firstOrNull()?.id?.removePrefix("NEWS-") || (item.fact.isNotBlank() && it.title == item.fact) }
            } else null

            IntelligenceItem(
                type = item.type.name,
                title = item.fact.ifBlank { item.interpretation },
                detail = listOf(item.calculation, item.interpretation)
                    .filter { !it.isNullOrBlank() && it != item.fact }
                    .joinToString(" "),
                source = item.source.ifBlank { item.evidence.firstOrNull()?.source ?: "Source unavailable" },
                evidence = item.evidence,
                accent = accent,
                actionLabel = when {
                    targetNews != null -> "Source"
                    targetStock != null -> "Open"
                    else -> "Source"
                },
                onAction = when {
                    targetNews != null -> { { openNews(targetNews) } }
                    targetStock != null -> { { openCompany(targetStock) } }
                    else -> openMarket
                }
            )
        }
        if (items.isEmpty()) {
            EmptyHomeCard("Not enough current evidence", "The Home feed will stay factual until market or news data is available.")
        }
    }
}

@Composable
private fun IntelligenceItem(
    type: String,
    title: String,
    detail: String,
    source: String,
    evidence: List<HomeEvidenceReference>,
    accent: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val primaryEvidence = evidence.firstOrNull()
    val evidenceSource = primaryEvidence?.source?.takeIf { it.isNotBlank() } ?: source
    val evidenceDate = primaryEvidence?.date?.takeIf { it.isNotBlank() }?.let(::compactEvidenceDate) ?: ""
    val sourceUrl = primaryEvidence?.sourceUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    Card(Modifier.fillMaxWidth(), RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, HomeBorder)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                    Icon(if (type == "NEWS") Icons.Default.Article else Icons.Default.Insights, null, tint = accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(type, color = accent, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    Text(title, color = HomeTextDark, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(detail.ifBlank { "No additional interpretation is available from the current evidence." }, color = HomeMuted, fontSize = 8.sp, lineHeight = 12.sp, maxLines = 3)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val freshness = primaryEvidence?.freshness?.takeIf { it.isNotBlank() }
                val provenanceText = listOfNotNull(
                    evidenceSource.takeIf { it.isNotBlank() },
                    evidenceDate.takeIf { it.isNotBlank() },
                    freshness?.let(::homeFreshnessLabel)
                ).joinToString(" • ")

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = HomeLightGreen
                ) {
                    Text(
                        text = provenanceText.ifBlank { "Evidence source unavailable" },
                        color = HomeDarkGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(5.dp))
                if (sourceUrl != null) {
                    Text(
                        "Source ↗",
                        color = HomeGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { uriHandler.openUri(sourceUrl) }.padding(4.dp)
                    )
                } else {
                    Text(
                        actionLabel,
                        color = HomeGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onAction).padding(4.dp)
                    )
                }
            }
        }
    }
}

private fun compactEvidenceDate(value: String): String =
    value.replace("T", " ").removeSuffix("Z").take(16)

private fun homeFreshnessLabel(value: String): String = when (value) {
    "CURRENT_SESSION" -> "Current session"
    "CURRENT_DAY" -> "Current day"
    "END_OF_DAY" -> "End of day"
    "STALE" -> "Stale"
    "UNKNOWN" -> "Freshness unknown"
    else -> value.replace("_", " ").lowercase(Locale.US)
        .replaceFirstChar { it.uppercase(Locale.US) }
}


@Composable
private fun WhatChanged(changes: List<HomeChangeItem>, openCompany: (Stock) -> Unit, currentStocks: List<Stock>) {
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        changes.take(6).forEach { change ->
            val stock = change.symbol.takeIf { it.isNotBlank() }?.let { symbol -> currentStocks.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } }
            ChangeRow(
                label = change.label,
                detail = change.detail,
                value = change.value,
                valueColor = when {
                    change.value.startsWith("-") -> HomeRed
                    change.type == HomeIntelligenceType.CALCULATION -> HomeDarkGreen
                    else -> HomeGreen
                },
                source = change.source?.source.orEmpty(),
                onClick = stock?.let { { openCompany(it) } }
            )
        }
        if (changes.isEmpty()) {
            EmptyHomeCard("No observable changes yet", "The app will not fill this section with invented market activity.")
        }
    }
}

@Composable
private fun ChangeRow(label: String, detail: String, value: String, valueColor: Color, source: String = "", onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, HomeBorder, RoundedCornerShape(13.dp)).clickable(enabled = onClick != null, onClick = { onClick?.invoke() }).padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = HomeTextDark, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (source.isNotBlank()) Text("Source: $source", color = HomeMuted, fontSize = 7.sp, maxLines = 1)
        }
        Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        if (onClick != null) {
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Default.ChevronRight, null, tint = HomeMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MarketMovers(gainers: List<Stock>, losers: List<Stock>, openCompany: (Stock) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val selected = if (selectedTab == 0) gainers else losers
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MoverTab("Gainers", selectedTab == 0) { selectedTab = 0 }
            MoverTab("Losers", selectedTab == 1) { selectedTab = 1 }
        }
        Spacer(Modifier.height(7.dp))
        if (selected.isEmpty()) {
            EmptyHomeCard(if (selectedTab == 0) "No gainers in the current feed" else "No losers in the current feed", "The app will not substitute invented market values.")
        } else {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                selected.take(5).forEach { stock -> CompactMoverRow(stock) { openCompany(stock) } }
            }
        }
    }
}

@Composable
private fun MoverTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) HomeDarkGreen else Color(0xFFF1F4F3)).clickable(onClick = onClick)) {
        Text(text, color = if (selected) Color.White else HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
    }
}

@Composable
private fun CompactMoverRow(stock: Stock, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, HomeBorder, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeLogo(stock.symbol, stock.logoUrl, 31)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(stock.symbol, color = HomeTextDark, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            Text(formatPrice(stock.price), color = HomeMuted, fontSize = 8.sp)
        }
        Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) HomeGreen else HomeRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(3.dp))
        Icon(Icons.Default.ChevronRight, null, tint = HomeMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SectorPulseCard(sector: String, change: Double) {
    val (icon, iconBg) = sectorVisual(sector)
    Card(Modifier.width(128.dp), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HomeBorder), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(27.dp).clip(RoundedCornerShape(8.dp)).background(iconBg)) { Icon(icon, null, tint = HomeDarkGreen, modifier = Modifier.padding(5.dp)) }
                Spacer(Modifier.width(6.dp))
                Text(displaySector(sector), color = HomeTextDark, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(5.dp))
            Text(String.format(Locale.US, "%+.1f%%", change), color = if (change >= 0) HomeGreen else HomeRed, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(HomeBorder)) {
                Box(Modifier.fillMaxWidth((kotlin.math.abs(change).coerceAtMost(3.0) / 3.0).coerceIn(.18, 1.0).toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (change >= 0) HomeGreen else HomeRed))
            }
        }
    }
}

private fun displaySector(sector: String): String = when (sector.lowercase(Locale.US)) {
    "telecommunication", "telecommunications" -> "Telecom"
    "manufacturing" -> "Manufacturing"
    "insurance" -> "Insurance"
    "banking", "banks" -> "Banking"
    "energy", "oil & gas", "oil and gas" -> "Energy"
    else -> sector
}

private fun sectorVisual(sector: String): Pair<ImageVector, Color> = when (sector.lowercase(Locale.US)) {
    "banking", "banks" -> Icons.Default.AccountBalance to Color(0xFFFFF3C4)
    "telecommunication", "telecommunications" -> Icons.Default.CellTower to Color(0xFFDDF3FF)
    "insurance" -> Icons.Default.Security to Color(0xFFDDF7EC)
    "energy", "oil & gas", "oil and gas" -> Icons.Default.WaterDrop to Color(0xFFE2F3FA)
    "manufacturing" -> Icons.Default.Factory to Color(0xFFFFE1E8)
    else -> Icons.Default.BusinessCenter to Color(0xFFE9F8F0)
}

@Composable
private fun CorporateActionCard(item: NewsItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HomeBorder), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeLogo(item.symbol, null, 40)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(item.companyName.ifBlank { item.symbol.ifBlank { "NSE company" } }, color = HomeMuted, fontSize = 8.sp)
                Text(item.category.ifBlank { "Corporate action" }, color = HomeTextDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.title, color = HomeTextDark, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val event = listOf(item.dividendAmount, item.exDate, item.paymentDate).firstOrNull { it.isNotBlank() }
                if (event != null) Text(event, color = HomeMuted, fontSize = 7.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = HomeMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IntelligenceNewsCard(item: NewsItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HomeBorder), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
            } else HomeLogo(item.symbol, null, 54)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = HomeTextDark, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (item.summary.isNotBlank()) Text(item.summary, color = HomeMuted, fontSize = 8.sp, maxLines = 2)
                Text(listOf(item.companyName.ifBlank { item.symbol }, timeAgo(item.publishedAt)).filter { it.isNotBlank() }.joinToString("  •  "), color = HomeMuted, fontSize = 7.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = HomeMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WatchlistQuickAccess(openWatchlist: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        Text("Quick Access", color = HomeTextDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Card(
            Modifier.fillMaxWidth().clickable(onClick = openWatchlist),
            RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = HomeLightGreen),
            border = BorderStroke(1.dp, HomeBorder)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.StarBorder, null, tint = HomeDarkGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("My Watchlist", color = HomeTextDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Your selected companies", color = HomeMuted, fontSize = 8.sp)
                }
                Text("View →", color = HomeDarkGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeQuickActions(openMarket: () -> Unit, openNews: (() -> Unit)?) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        Text("Explore", color = HomeTextDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QuickAction("Market", Icons.Default.ShowChart, openMarket, Modifier.weight(1f))
            QuickAction("News", Icons.Default.Article, { openNews?.invoke() }, Modifier.weight(1f), enabled = openNews != null)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Card(modifier = modifier.clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (enabled) HomeLightGreen else Color(0xFFF3F5F4)), border = BorderStroke(1.dp, HomeBorder)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (enabled) HomeDarkGreen else HomeMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = if (enabled) HomeDarkGreen else HomeMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingHomeCard(message: String = "Loading market intelligence…") {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = HomeGreen)
            Spacer(Modifier.width(9.dp))
            Text(message, color = HomeMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun EmptyHomeCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = HomeTextDark, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(subtitle, color = HomeMuted, fontSize = 8.sp, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun HomeLogo(symbol: String, logoUrl: String?, size: Int) {
    val resolved = logoUrl?.takeIf { it.isNotBlank() } ?: symbol.takeIf { it.isNotBlank() }?.let { "https://mystocks.africa/logos/${it.lowercase(Locale.US)}-ke.svg" }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(HomeLightGreen)) {
        if (resolved != null) AsyncImage(model = resolved, contentDescription = symbol, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        else Icon(Icons.Default.Article, null, tint = HomeGreen, modifier = Modifier.padding((size / 4).dp))
    }
}

private fun greetingForNairobi(): String {
    val hour = java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Nairobi")).hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    return "$greeting, James"
}

private fun formatPrice(value: Double): String = if (value.isFinite()) String.format(Locale.US, "KSh %.2f", value) else "Price unavailable"

private fun formatShares(value: Long): String = when {
    value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> String.format(Locale.US, "%,d", value)
}

private fun marketDataFooter(stocks: List<Stock>): String {
    val sources = stocks.map { it.source.trim() }.filter { it.isNotBlank() }.distinct()
    val fallbackOnly = stocks.isNotEmpty() && stocks.all { it.dataOrigin == "fallback" }
    return when {
        fallbackOnly -> "Market data: NSE Watcher fallback catalogue • not live provider data. Intelligence is informational; verify material announcements with the issuer or NSE."
        sources.size == 1 -> {
            val source = sources.first()
            val delay = if (stocks.all { it.dataOrigin == "backend" && it.source.equals("MyStocks Africa", ignoreCase = true) }) " • approximately 15 minutes delayed" else ""
            "Market data: $source$delay. Intelligence is informational; verify material announcements with the issuer or NSE."
        }
        sources.size > 1 -> "Market data: multiple sources • delays may vary. Intelligence is informational; verify material announcements with the issuer or NSE."
        else -> "Market data source unavailable. Intelligence is informational; verify material announcements with the issuer or NSE."
    }
}

private fun timeAgo(value: String): String {
    if (value.isBlank()) return ""
    return value.replace("T", " ").take(16)
}
