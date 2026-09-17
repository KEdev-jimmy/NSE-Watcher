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

    val corporateActions = news.filter { item ->
        val category = item.category.lowercase(Locale.US)
        category.contains("dividend") || category.contains("corporate") ||
            category.contains("rights") || category.contains("bonus") || category.contains("action")
    }.take(3)
    val companyNews = news.filter { it.symbol.isNotBlank() || it.companyName.isNotBlank() }.take(3)
    val strongestSector = validStocks
        .filter { it.sector.isNotBlank() && it.sector != "Other" }
        .groupBy { it.sector.trim() }
        .mapValues { (_, members) -> members.map { it.change }.average() }
        .maxByOrNull { it.value }

    val sectors = validStocks
        .filter { it.sector.isNotBlank() && it.sector != "Other" }
        .groupBy { it.sector.trim() }
        .map { (sector, members) -> sector to members.map { it.change }.average() }
        .sortedByDescending { kotlin.math.abs(it.second) }
        .take(5)

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
            TodaysIntelligence(
                strongestSector = strongestSector?.key,
                strongestSectorChange = strongestSector?.value,
                topGainer = gainers.firstOrNull(),
                openMarket = openMarket
            )
        }

        if (gainers.isNotEmpty()) {
            item {
                Spacer(Modifier.height(17.dp))
                HomeSectionHeader("Market Movers", "", Icons.Default.Whatshot, openMarket)
            }
            item {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(gainers.take(4)) { stock -> MoverCard(stock) { openCompany(stock) } }
                }
            }
        }

        if (sectors.isNotEmpty()) {
            item {
                Spacer(Modifier.height(17.dp))
                HomeSectionHeader("Sector Pulse", "", Icons.Default.Insights, openMarket)
            }
            item {
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sectors) { (sector, change) -> SectorPulseCard(sector, change) }
                }
            }
        }

        item {
            Spacer(Modifier.height(17.dp))
            HomeSectionHeader("Corporate Actions", "", Icons.Default.Event, openMarket)
        }
        item {
            Spacer(Modifier.height(7.dp))
            CorporateActionTabs()
        }
        item {
            Spacer(Modifier.height(8.dp))
            if (corporateActions.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    corporateActions.take(1).forEach { action -> CorporateActionCard(action) { openNews(action) } }
                }
            } else if (newsLoading) {
                LoadingHomeCard()
            } else {
                EmptyHomeCard("No upcoming corporate actions found", "We won't invent announcements when the feed has none.")
            }
        }

        item {
            Spacer(Modifier.height(17.dp))
            HomeSectionHeader("Market Intelligence", "Company-linked news that may matter", Icons.Default.Lightbulb, openMarket)
        }
        item {
            Spacer(Modifier.height(8.dp))
            if (companyNews.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    companyNews.forEach { intelligence -> IntelligenceNewsCard(intelligence) { openNews(intelligence) } }
                }
            } else if (newsLoading) {
                LoadingHomeCard()
            } else if (newsError != null) {
                EmptyHomeCard("Market intelligence temporarily unavailable", "Please try again later.")
            } else {
                EmptyHomeCard("No company-linked intelligence found", "We won't invent stories when the feed has none.")
            }
        }

        item {
            Spacer(Modifier.height(14.dp))
            DailyBriefCard(openMarket)
            Spacer(Modifier.height(10.dp))
            Text(
                "Market data is exchange-supplied through MyStocks Africa and approximately 15 minutes delayed. Intelligence is informational; verify material announcements with the issuer or NSE.",
                color = HomeMuted,
                fontSize = 8.sp,
                modifier = Modifier.padding(horizontal = 17.dp)
            )
        }
    }
}

@Composable
private fun HomeHero(advancing: Int, declining: Int, unchanged: Int, reportedVolume: Long) {
    Box(Modifier.fillMaxWidth().height(278.dp)) {
        Box(Modifier.fillMaxWidth().height(202.dp)) {
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
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(HomeGreen)) {
                    Icon(Icons.Default.ShowChart, null, tint = Color.White, modifier = Modifier.padding(9.dp).fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NSE Watcher", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Analyse • Understand • Invest Smarter", color = Color(0xFFD7F2E4), fontSize = 11.sp)
                }
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(29.dp))
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF4D5A)).align(Alignment.TopEnd))
                }
            }

            Column(Modifier.padding(start = 30.dp, top = 8.dp, end = 24.dp)) {
                Text("Good morning, James", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Here's what's happening in the NSE today", color = Color(0xFFE0F2EA), fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))
            NasiPulseCard()
            Spacer(Modifier.height(7.dp))
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
            BreadthLine("Advancing", advancing, HomeGreen)
            Spacer(Modifier.width(9.dp))
            BreadthLine("Declining", declining, HomeRed)
            Spacer(Modifier.width(9.dp))
            BreadthLine("Unchanged", unchanged, Color(0xFFD4DFDB))
            Spacer(Modifier.width(12.dp))
            Text("Vol ${formatShares(reportedVolume)}", color = Color(0xFFD7EAE1), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BreadthLine(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color(0xFFD7EAE1), fontSize = 8.sp)
        Spacer(Modifier.width(4.dp))
        Text(value.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TodaysIntelligence(strongestSector: String?, strongestSectorChange: Double?, topGainer: Stock?, openMarket: () -> Unit) {
    val headline = when {
        strongestSector != null -> "$strongestSector stocks are leading today's market movement"
        topGainer != null -> "${topGainer.symbol} is leading today's market movement"
        else -> "Market movement is developing today"
    }
    val detail = when {
        strongestSector != null && strongestSectorChange != null -> "$strongestSector counters are averaging ${String.format(Locale.US, "%+.2f%%", strongestSectorChange)} in the current feed."
        topGainer != null -> "${topGainer.symbol} is up ${String.format(Locale.US, "%+.2f%%", topGainer.change)} in the current stock feed."
        else -> "There is not enough evidence in the current feed to describe a stronger market theme."
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1FBF6)),
        border = BorderStroke(1.dp, Color(0xFFD6EEE2))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(HomeDarkGreen)) {
                    Icon(Icons.Default.Psychology, null, tint = Color(0xFF8BE0B3), modifier = Modifier.padding(6.dp))
                }
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Today's Intelligence", color = HomeTextDark, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(7.dp))
                        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFD8F4E5))) {
                            Text("Market", color = HomeGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(headline, color = HomeTextDark, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 18.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(detail, color = HomeMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
                Spacer(Modifier.width(7.dp))
                Column(Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3F6EC)).padding(9.dp)) {
                    Text("Confidence", color = HomeTextDark, fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.clip(RoundedCornerShape(15.dp)).background(Color(0xFFFFD36A))) {
                        Text("Moderate", color = Color(0xFF6B5100), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.height(11.dp))
                    Text("View evidence  →", color = HomeGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = openMarket))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                EvidenceChip(Icons.Default.BarChart, "Sector performance")
                EvidenceChip(Icons.Default.Equalizer, "Higher volume")
                EvidenceChip(Icons.Default.Assessment, "Recent results")
            }
        }
    }
}

@Composable
private fun EvidenceChip(icon: ImageVector, text: String) {
    Box(Modifier.clip(RoundedCornerShape(11.dp)).background(Color.White).border(BorderStroke(1.dp, HomeBorder), RoundedCornerShape(11.dp))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = HomeGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, subtitle: String, icon: ImageVector, onViewAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(HomeLightGreen)) {
            Icon(icon, null, tint = HomeDarkGreen, modifier = Modifier.padding(7.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HomeTextDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = HomeMuted, fontSize = 8.sp)
        }
        Text("View all  →", color = HomeDarkGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onViewAll))
    }
}

@Composable
private fun MoverCard(stock: Stock, onClick: () -> Unit) {
    Card(Modifier.width(88.dp).clickable(onClick = onClick), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, HomeBorder), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(8.dp)) {
            HomeLogo(stock.symbol, stock.logoUrl, 29)
            Spacer(Modifier.height(5.dp))
            Text(stock.symbol, color = HomeMuted, fontSize = 8.sp)
            Text(String.format(Locale.US, "KSh %.2f", stock.price), color = HomeTextDark, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text("▲ ${String.format(Locale.US, "%.2f%%", stock.change)}", color = HomeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectorPulseCard(sector: String, change: Double) {
    val (icon, iconBg) = sectorVisual(sector)
    Card(Modifier.width(128.dp), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(iconBg)) { Icon(icon, null, tint = HomeDarkGreen, modifier = Modifier.padding(5.dp)) }
                Spacer(Modifier.width(6.dp))
                Text(displaySector(sector), color = HomeTextDark, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(5.dp))
            Text(String.format(Locale.US, "%+.1f%%", change), color = if (change >= 0) HomeGreen else HomeRed, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(HomeBorder)) {
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
private fun CorporateActionTabs() {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ActionTab("Upcoming", true)
        ActionTab("Dividends", false)
        ActionTab("Announcements", false)
        ActionTab("Rights Issues", false)
        ActionTab("Bonus Issues", false)
    }
}

@Composable
private fun ActionTab(text: String, selected: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) Color(0xFFD6F5E5) else Color(0xFFF1F4F3))) {
        Text(text, color = if (selected) HomeGreen else HomeMuted, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun CorporateActionCard(item: NewsItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeLogo(item.symbol, null, 46)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.companyName.ifBlank { item.symbol.ifBlank { "NSE company" } }, color = HomeMuted, fontSize = 9.sp)
                Text(item.category.ifBlank { "Corporate action" }, color = HomeTextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(item.title, color = HomeTextDark, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val event = listOf(item.dividendAmount, item.exDate, item.paymentDate).firstOrNull { it.isNotBlank() }
                if (event != null) Text(event, color = HomeMuted, fontSize = 8.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = HomeMuted)
        }
    }
}

@Composable
private fun IntelligenceNewsCard(item: NewsItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            } else HomeLogo(item.symbol, null, 60)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = HomeTextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (item.summary.isNotBlank()) Text(item.summary, color = HomeMuted, fontSize = 8.sp, maxLines = 2)
                Text(listOf(item.companyName.ifBlank { item.symbol }, timeAgo(item.publishedAt)).filter { it.isNotBlank() }.joinToString("  •  "), color = HomeMuted, fontSize = 8.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = HomeMuted)
        }
    }
}

@Composable
private fun DailyBriefCard(openMarket: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable(onClick = openMarket), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF9F2)), border = BorderStroke(1.dp, Color(0xFFD2EEE0))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(41.dp).clip(CircleShape).background(HomeGreen)) { Icon(Icons.Default.Article, null, tint = Color.White, modifier = Modifier.padding(10.dp)) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Today's NSE Brief", color = HomeDarkGreen, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("5 key things to know about the market today", color = HomeMuted, fontSize = 8.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(HomeDarkGreen)) {
                Text("Read full brief  →", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
    }
}

@Composable
private fun LoadingHomeCard() {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = HomeGreen)
            Spacer(Modifier.width(10.dp))
            Text("Loading market intelligence…", color = HomeMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun EmptyHomeCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = HomeTextDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(subtitle, color = HomeMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun HomeLogo(symbol: String, logoUrl: String?, size: Int) {
    val resolved = logoUrl?.takeIf { it.isNotBlank() } ?: symbol.takeIf { it.isNotBlank() }?.let { "https://mystocks.africa/logos/${it.lowercase(Locale.US)}-ke.svg" }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(9.dp)).background(HomeLightGreen)) {
        if (resolved != null) AsyncImage(model = resolved, contentDescription = symbol, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        else Icon(Icons.Default.Article, null, tint = HomeGreen, modifier = Modifier.padding((size / 4).dp))
    }
}

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
