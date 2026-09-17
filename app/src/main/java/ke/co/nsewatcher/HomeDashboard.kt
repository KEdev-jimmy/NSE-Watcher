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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.Locale
import ke.co.nsewatcher.data.NewsCache

private val HomeGreen = Color(0xFF00A859)
private val HomeLightGreen = Color(0xFFE9F8F0)
private val HomeDarkGreen = Color(0xFF063D2A)
private val HomeTextDark = Color(0xFF12352A)
private val HomeMuted = Color(0xFF64756D)
private val HomeBorder = Color(0xFFDDE9E3)
private val HomeRed = Color(0xFFE94A4A)
private const val NairobiSkyline = "https://upload.wikimedia.org/wikipedia/commons/8/80/Nairobi_Skyline_from_West.jpg"

@Composable
fun HomeDashboard(
    currentStocks: List<Stock>,
    openCompany: (Stock) -> Unit,
    openNews: (NewsItem) -> Unit,
    openMarket: () -> Unit = {}
) {
    val validStocks = currentStocks.filter { it.change.isFinite() }
    val gainers = validStocks.filter { it.change > 0 }.sortedByDescending { it.change }
    val losers = validStocks.filter { it.change < 0 }.sortedBy { it.change }
    val advancing = gainers.size
    val declining = losers.size
    val unchanged = validStocks.count { it.change == 0.0 }
    val reportedVolume = validStocks.sumOf { it.volume.coerceAtLeast(0L) }

    var news by remember { mutableStateOf(emptyList<NewsItem>()) }
    var newsLoading by remember { mutableStateOf(true) }
    var newsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = NewsCache.loadFeedResult()
        news = result.items
        newsError = result.error
        newsLoading = false
    }

    val corporateActions = news.filter { it.isCorporateAction() }
    val companyNews = news.filter { (it.symbol.isNotBlank() || it.companyName.isNotBlank()) && !it.isCorporateAction() }

    val sectorChanges = validStocks
        .filter { it.sector.isNotBlank() && !it.sector.equals("Other", ignoreCase = true) }
        .groupBy { it.sector.trim() }
        .map { (sector, members) -> sector to members.map { it.change }.average() }
        .sortedByDescending { kotlin.math.abs(it.second) }

    val strongestSector = sectorChanges.maxByOrNull { it.second }
    val weakestSector = sectorChanges.minByOrNull { it.second }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            HomeHero(
                advancing = advancing,
                declining = declining,
                unchanged = unchanged,
                reportedVolume = reportedVolume
            )
        }

        item {
            Spacer(Modifier.height(14.dp))
            SectionLabel("Today's Intelligence", "Evidence from the current market and news feed", Icons.Default.Psychology)
        }

        item {
            Spacer(Modifier.height(7.dp))
            TodaysIntelligence(
                strongestSector = strongestSector,
                topGainer = gainers.firstOrNull(),
                latestNews = companyNews.firstOrNull(),
                openCompany = openCompany,
                openNews = openNews,
                openMarket = openMarket
            )
        }

        item {
            Spacer(Modifier.height(17.dp))
            SectionLabel("What Changed?", "Observable changes — no invented causes", Icons.Default.ChangeCircle)
        }
        item {
            Spacer(Modifier.height(7.dp))
            WhatChanged(
                advancing = advancing,
                declining = declining,
                unchanged = unchanged,
                strongestSector = strongestSector,
                weakestSector = weakestSector,
                topGainer = gainers.firstOrNull(),
                topLoser = losers.firstOrNull { true },
                latestNews = news.firstOrNull(),
                openCompany = openCompany,
                openNews = openNews
            )
        }

        if (gainers.isNotEmpty() || losers.isNotEmpty()) {
            item {
                Spacer(Modifier.height(17.dp))
                SectionLabel("Market Movers", "Current session movement", Icons.Default.Whatshot, openMarket)
            }
            item {
                Spacer(Modifier.height(7.dp))
                MarketMovers(gainers, losers, openCompany)
            }
        }

        if (sectorChanges.isNotEmpty()) {
            item {
                Spacer(Modifier.height(17.dp))
                SectionLabel("Sector Pulse", "Average movement by sector", Icons.Default.Insights, openMarket)
            }
            item {
                Spacer(Modifier.height(7.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sectorChanges.take(5)) { (sector, change) -> SectorPulseCard(sector, change) }
                }
            }
        }

        if (corporateActions.isNotEmpty() || newsLoading) {
            item {
                Spacer(Modifier.height(17.dp))
                SectionLabel("Corporate Actions", "Dividends, rights, bonuses and announcements", Icons.Default.Event, openMarket)
            }
            item {
                Spacer(Modifier.height(7.dp))
                if (corporateActions.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        corporateActions.take(2).forEach { action ->
                            CorporateActionCard(action) { openNews(action) }
                        }
                    }
                } else {
                    LoadingHomeCard("Loading corporate actions…")
                }
            }
        }

        if (companyNews.isNotEmpty() || newsLoading || newsError != null) {
            item {
                Spacer(Modifier.height(17.dp))
                SectionLabel("Important News", "Company-linked information that may matter", Icons.Default.Lightbulb, openMarket)
            }
            item {
                Spacer(Modifier.height(7.dp))
                when {
                    companyNews.isNotEmpty() -> {
                        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            companyNews.take(3).forEach { story ->
                                IntelligenceNewsCard(story) { openNews(story) }
                            }
                        }
                    }
                    newsLoading -> LoadingHomeCard("Loading market news…")
                    newsError != null -> EmptyHomeCard(
                        "Market intelligence temporarily unavailable",
                        "The news feed returned an error. No stories are being fabricated."
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(17.dp))
            QuickActions(openMarket, if (news.isNotEmpty()) { { openNews(news.first()) } } else null)
            Spacer(Modifier.height(11.dp))
            Text(
                "Market data is supplied through MyStocks Africa and is approximately 15 minutes delayed. Intelligence is informational; verify material announcements with the issuer or NSE.",
                color = HomeMuted,
                fontSize = 8.sp,
                lineHeight = 11.sp,
                modifier = Modifier.padding(horizontal = 17.dp)
            )
        }
    }
}

@Composable
private fun HomeHero(advancing: Int, declining: Int, unchanged: Int, reportedVolume: Long) {
    Box(Modifier.fillMaxWidth().height(278.dp)) {
        Box(Modifier.fillMaxWidth().height(194.dp)) {
            AsyncImage(
                model = NairobiSkyline,
                contentDescription = "Nairobi skyline",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color(0xCC00523B)))
            Box(Modifier.fillMaxSize().background(Color(0x66002018)))
        }

        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(start = 24.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(HomeGreen)) {
                    Icon(Icons.Default.ShowChart, null, tint = Color.White, modifier = Modifier.padding(8.dp).fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("NSE Watcher", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Analyse • Understand • Invest Smarter", color = Color(0xFFD7F2E4), fontSize = 10.sp)
                }
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(25.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF4D5A)).align(Alignment.TopEnd))
                }
            }

            Column(Modifier.padding(start = 30.dp, top = 7.dp, end = 24.dp)) {
                Text("Good morning, James", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("Here's what's happening in the NSE today", color = Color(0xFFE0F2EA), fontSize = 11.sp)
            }

            Spacer(Modifier.height(5.dp))
            NasiPulseCard()
            Spacer(Modifier.height(6.dp))
            CompactBreadthCard(advancing, declining, unchanged, reportedVolume)
        }
    }
}

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
private fun SectionLabel(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onViewAll: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(HomeLightGreen)) {
            Icon(icon, null, tint = HomeDarkGreen, modifier = Modifier.padding(6.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HomeTextDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = HomeMuted, fontSize = 8.sp)
        }
        if (onViewAll != null) {
            Text("View all →", color = HomeDarkGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onViewAll))
        }
    }
}

@Composable
private fun TodaysIntelligence(
    strongestSector: Pair<String, Double>?,
    topGainer: Stock?,
    latestNews: NewsItem?,
    openCompany: (Stock) -> Unit,
    openNews: (NewsItem) -> Unit,
    openMarket: () -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (strongestSector != null) {
            IntelligenceItem(
                type = "CALCULATION",
                title = "${displaySector(strongestSector.first)} has the strongest sector average",
                detail = "The sector's counters are averaging ${String.format(Locale.US, "%+.2f%%", strongestSector.second)} in the current stock feed.",
                source = "MyStocks Africa • current feed",
                accent = if (strongestSector.second >= 0) HomeGreen else HomeRed,
                actionLabel = "Source",
                onAction = openMarket
            )
        }
        if (topGainer != null) {
            IntelligenceItem(
                type = "FACT",
                title = "${topGainer.symbol} is the largest current gainer",
                detail = "${topGainer.symbol} is ${String.format(Locale.US, "%+.2f%%", topGainer.change)} in the current stock feed at ${formatPrice(topGainer.price)}.",
                source = "MyStocks Africa • current feed",
                accent = HomeGreen,
                actionLabel = "Explain",
                onAction = { openCompany(topGainer) }
            )
        }
        if (latestNews != null) {
            IntelligenceItem(
                type = "NEWS",
                title = latestNews.title,
                detail = latestNews.summary.ifBlank { "A company-linked story is available in the current news feed." },
                source = listOf(latestNews.companyName.ifBlank { latestNews.symbol }, latestNews.source).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "News feed" },
                accent = HomeDarkGreen,
                actionLabel = "Source",
                onAction = { openNews(latestNews) }
            )
        }
        if (strongestSector == null && topGainer == null && latestNews == null) {
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
    accent: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, HomeBorder)
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                    Icon(if (type == "NEWS") Icons.Default.Article else Icons.Default.Insights, null, tint = accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(type, color = accent, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    Text(title, color = HomeTextDark, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(detail, color = HomeMuted, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 3)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source, color = HomeMuted, fontSize = 7.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(actionLabel, color = HomeGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
            }
        }
    }
}

@Composable
private fun WhatChanged(
    advancing: Int,
    declining: Int,
    unchanged: Int,
    strongestSector: Pair<String, Double>?,
    weakestSector: Pair<String, Double>?,
    topGainer: Stock?,
    topLoser: Stock?,
    latestNews: NewsItem?,
    openCompany: (Stock) -> Unit,
    openNews: (NewsItem) -> Unit
) {
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ChangeRow(
            label = "Market breadth",
            detail = "$advancing advancing • $declining declining • $unchanged unchanged",
            value = if (advancing + declining + unchanged > 0) "${advancing - declining}" else "—",
            valueColor = if (advancing >= declining) HomeGreen else HomeRed
        )
        strongestSector?.let {
            ChangeRow("Strongest sector", displaySector(it.first), String.format(Locale.US, "%+.2f%%", it.second), if (it.second >= 0) HomeGreen else HomeRed)
        }
        weakestSector?.takeIf { strongestSector?.first != it.first }?.let {
            ChangeRow("Weakest sector", displaySector(it.first), String.format(Locale.US, "%+.2f%%", it.second), if (it.second >= 0) HomeGreen else HomeRed)
        }
        topGainer?.let {
            ChangeRow("Largest gainer", it.symbol, String.format(Locale.US, "+%.2f%%", it.change), HomeGreen, { openCompany(it) })
        }
        topLoser?.let {
            ChangeRow("Largest loser", it.symbol, String.format(Locale.US, "%.2f%%", it.change), HomeRed, { openCompany(it) })
        }
        latestNews?.let {
            ChangeRow("Latest company news", it.companyName.ifBlank { it.symbol }.ifBlank { "News" }, "Open source", HomeDarkGreen) { openNews(it) }
        }
    }
}

@Composable
private fun ChangeRow(
    label: String,
    detail: String,
    value: String,
    valueColor: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, HomeBorder, RoundedCornerShape(13.dp)).clickable(enabled = onClick != null, onClick = { onClick?.invoke() }).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = HomeTextDark, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, HomeBorder, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
private fun QuickActions(openMarket: () -> Unit, openNews: (() -> Unit)?) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        Text("Quick Actions", color = HomeTextDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QuickAction("Market", Icons.Default.ShowChart, openMarket, Modifier.weight(1f))
            QuickAction("News", Icons.Default.Article, { openNews?.invoke() }, Modifier.weight(1f), enabled = openNews != null)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) HomeLightGreen else Color(0xFFF3F5F4)),
        border = BorderStroke(1.dp, HomeBorder)
    ) {
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

private fun NewsItem.isCorporateAction(): Boolean {
    val category = category.lowercase(Locale.US)
    return category.contains("dividend") || category.contains("corporate") ||
        category.contains("rights") || category.contains("bonus") || category.contains("action")
}

@Composable
private fun HomeLogo(symbol: String, logoUrl: String?, size: Int) {
    val resolved = logoUrl?.takeIf { it.isNotBlank() } ?: symbol.takeIf { it.isNotBlank() }?.let { "https://mystocks.africa/logos/${it.lowercase(Locale.US)}-ke.svg" }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(HomeLightGreen)) {
        if (resolved != null) AsyncImage(model = resolved, contentDescription = symbol, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        else Icon(Icons.Default.Article, null, tint = HomeGreen, modifier = Modifier.padding((size / 4).dp))
    }
}

private fun formatPrice(value: Double): String = if (value.isFinite()) String.format(Locale.US, "KSh %.2f", value) else "Price unavailable"

private fun formatShares(value: Long): String = when {
    value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> String.format(Locale.US, "%,d", value)
}

private fun timeAgo(value: String): String {
    if (value.isBlank()) return ""
    return value.replace("T", " ").take(16)
}
