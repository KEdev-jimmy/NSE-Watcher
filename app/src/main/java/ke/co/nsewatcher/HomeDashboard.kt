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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.Locale
import ke.co.nsewatcher.data.NewsCache

private val HomeGreen = Color(0xFF00A859)
private val HomeLightGreen = Color(0xFFE9F8F0)
private val HomeDarkGreen = Color(0xFF083C27)
private val HomeTextDark = Color(0xFF12231B)
private val HomeMuted = Color(0xFF6C7A72)
private val HomeBorder = Color(0xFFE1EAE5)
private val HomeRed = Color(0xFFE04444)

private fun homeFormatShares(value: Long): String = when {
    value >= 1_000_000_000L -> String.format(Locale.US, "%.2fB", value / 1_000_000_000.0)
    value >= 1_000_000L -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> String.format(Locale.US, "%,d", value)
}

@Composable
fun HomeDashboard(currentStocks: List<Stock>, openCompany: (Stock) -> Unit, openNews: (NewsItem) -> Unit) {
    val gainers = currentStocks.filter { it.change.isFinite() && it.change > 0 }.sortedByDescending { it.change }
    val losers = currentStocks.filter { it.change.isFinite() && it.change < 0 }.sortedBy { it.change }
    val advancing = gainers.size
    val declining = losers.size
    val unchanged = currentStocks.count { it.change.isFinite() && it.change == 0.0 }
    val validCount = advancing + declining + unchanged
    val reportedVolume = currentStocks.sumOf { it.volume.coerceAtLeast(0L) }

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
    val topGainer = gainers.firstOrNull()
    val strongestSector = currentStocks
        .filter { it.sector.isNotBlank() && it.sector != "Other" && it.change.isFinite() }
        .groupBy { it.sector.trim() }
        .mapValues { (_, members) -> members.map { it.change }.average() }
        .maxByOrNull { it.value }

    LazyColumn(contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HomeGreeting() }
        item { MarketSnapshotHome(advancing, declining, unchanged, validCount, reportedVolume) }
        item { IntelligenceCard(advancing, declining, strongestSector?.key, strongestSector?.value, topGainer) }

        if (gainers.isNotEmpty() || losers.isNotEmpty()) {
            item { HomeSectionHeader("Market Movers", "See what's moving", Icons.Default.Whatshot) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items((gainers.take(4) + losers.take(2)).distinctBy { it.symbol }) { stock -> MoverCard(stock) { openCompany(stock) } }
                }
            }
        }

        val sectors = currentStocks
            .filter { it.sector.isNotBlank() && it.sector != "Other" && it.change.isFinite() }
            .groupBy { it.sector.trim() }
            .map { (sector, members) -> sector to members.map { it.change }.average() }
            .sortedByDescending { kotlin.math.abs(it.second) }
            .take(5)
        if (sectors.isNotEmpty()) {
            item { HomeSectionHeader("Sector Pulse", "Average move of available NSE constituents", Icons.Default.Insights) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) { items(sectors) { (sector, change) -> SectorPulseCard(sector, change) } } }
        }

        if (corporateActions.isNotEmpty()) {
            item { HomeSectionHeader("Corporate Actions", "Dividends & important company events", Icons.Default.Event) }
            item { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) { items(corporateActions) { item -> CorporateActionCard(item) { openNews(item) } } } }
        } else if (!newsLoading && newsError == null && news.isNotEmpty()) {
            item { EmptyHomeCard("No recent corporate actions found", "We won't invent announcements when the feed has none.") }
        }

        if (companyNews.isNotEmpty()) {
            item { HomeSectionHeader("Market Intelligence", "Company-linked news that may matter", Icons.Default.Lightbulb) }
            item { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) { items(companyNews) { item -> IntelligenceNewsCard(item) { openNews(item) } } } }
        } else if (newsLoading) {
            item { LoadingHomeCard() }
        } else if (newsError != null) {
            item { EmptyHomeCard("Market intelligence temporarily unavailable", "Please try again later.") }
        }

        item { DailyBriefCard(validCount, advancing, declining, corporateActions.size, companyNews.size) }
        item { Text("Market data is exchange-supplied through MyStocks Africa and approximately 15 minutes delayed. Intelligence is informational; verify material announcements with the issuer or NSE.", color = HomeMuted, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 3.dp)) }
    }
}

@Composable private fun HomeGreeting() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = HomeDarkGreen)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NSE WATCHER", color = Color(0xFF9FE5C2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Good morning", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Here's what matters in the NSE today", color = Color(0xFFD5E9DF), fontSize = 11.sp)
            }
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF0B6B46))) { Icon(Icons.Default.ShowChart, "Market", tint = Color(0xFF8BE0B3), modifier = Modifier.padding(13.dp)) }
        }
    }
}

@Composable private fun MarketSnapshotHome(advancing: Int, declining: Int, unchanged: Int, validCount: Int, reportedVolume: Long) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = HomeDarkGreen)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0B6B46))) { Icon(Icons.Default.BarChart, null, tint = Color(0xFF8BE0B3), modifier = Modifier.padding(7.dp)) }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("NSE MARKET SNAPSHOT", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("Current stock feed", color = Color(0xFFB9D8C8), fontSize = 9.sp)
                }
                Text("15 MIN DELAYED", color = Color(0xFF9FE5C2), fontWeight = FontWeight.Bold, fontSize = 8.sp)
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BreadthValue("Advancing", advancing, HomeGreen)
                BreadthValue("Declining", declining, HomeRed)
                BreadthValue("Unchanged", unchanged, Color(0xFFBFCAC5))
            }
            Spacer(Modifier.height(12.dp)); HorizontalDivider(color = Color.White.copy(alpha = .15f)); Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricValue("Stocks covered", validCount.toString())
                MetricValue("Reported volume", homeFormatShares(reportedVolume))
            }
        }
    }
}

@Composable private fun BreadthValue(label: String, value: Int, color: Color) {
    Column { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp)); Text(label, color = Color(0xFFD5E9DF), fontSize = 9.sp) }; Text(value.toString(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable private fun MetricValue(label: String, value: String) { Column { Text(label, color = Color(0xFFB9D8C8), fontSize = 8.sp); Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }

@Composable private fun IntelligenceCard(advancing: Int, declining: Int, strongestSector: String?, strongestSectorChange: Double?, topGainer: Stock?) {
    val breadthText = when { advancing > declining -> "More stocks are advancing than declining today."; declining > advancing -> "More stocks are declining than advancing today."; else -> "Advancing and declining stocks are currently balanced." }
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1FBF6)), border = BorderStroke(1.dp, Color(0xFFD7EEE1))) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).clip(CircleShape).background(HomeDarkGreen)) { Icon(Icons.Default.Psychology, null, tint = Color(0xFF8BE0B3), modifier = Modifier.padding(9.dp)) }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) { Text("TODAY'S INTELLIGENCE", color = HomeDarkGreen, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp); Text("Evidence from the current market feed", color = HomeMuted, fontSize = 9.sp) }
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFD8F4E5))) { Text("DATA", color = HomeGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
            }
            Spacer(Modifier.height(11.dp)); Text(breadthText, color = HomeTextDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(5.dp))
            val detail = when {
                strongestSector != null && strongestSectorChange != null -> "$strongestSector is the strongest available sector at ${String.format(Locale.US, "%+.2f%%", strongestSectorChange)} average move."
                topGainer != null -> "${topGainer.symbol} is the strongest gainer in the current stock feed at ${String.format(Locale.US, "%+.2f%%", topGainer.change)}."
                else -> "There is not enough valid market data to identify a stronger market signal right now."
            }
            Text(detail, color = HomeMuted, fontSize = 11.sp); Spacer(Modifier.height(11.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { EvidenceChip(Icons.Default.BarChart, "Market breadth"); if (strongestSector != null) EvidenceChip(Icons.Default.Category, "Sector move"); if (topGainer != null) EvidenceChip(Icons.Default.TrendingUp, "Top mover") }
            Spacer(Modifier.height(8.dp)); Text("Evidence is descriptive, not a buy/sell signal.", color = HomeMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun EvidenceChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)) { Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = HomeGreen, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text(text, color = HomeTextDark, fontSize = 8.sp, fontWeight = FontWeight.SemiBold) } }
}

@Composable private fun HomeSectionHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(31.dp).clip(RoundedCornerShape(9.dp)).background(HomeLightGreen)) { Icon(icon, null, tint = HomeGreen, modifier = Modifier.padding(7.dp)) }
        Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = HomeTextDark); Text(subtitle, color = HomeMuted, fontSize = 8.sp) }
    }
}

@Composable private fun MoverCard(stock: Stock, onClick: () -> Unit) {
    Card(Modifier.width(142.dp).clickable(onClick = onClick), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Column(Modifier.padding(11.dp)) { HomeLogo(stock.symbol, stock.logoUrl, 31); Spacer(Modifier.height(7.dp)); Text(stock.symbol, color = HomeMuted, fontSize = 9.sp); Text(String.format(Locale.US, "KSh %.2f", stock.price), color = HomeTextDark, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold); Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) HomeGreen else HomeRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun SectorPulseCard(sector: String, change: Double) {
    Card(Modifier.width(137.dp), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Column(Modifier.padding(10.dp)) { Text(sector, color = HomeTextDark, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1); Text(String.format(Locale.US, "%+.2f%%", change), color = if (change >= 0) HomeGreen else HomeRed, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(7.dp)); Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(HomeBorder)) { Box(Modifier.fillMaxWidth(.68f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (change >= 0) HomeGreen else HomeRed)) } }
    }
}

@Composable private fun CorporateActionCard(item: NewsItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { HomeLogo(item.symbol, null, 42); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.companyName.ifBlank { item.symbol.ifBlank { "NSE company" } }, color = HomeMuted, fontSize = 9.sp); Text(item.title, color = HomeTextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2); val event = listOf(item.dividendAmount, item.exDate, item.paymentDate).firstOrNull { it.isNotBlank() }; if (event != null) Text(event, color = HomeGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold) else Text(item.category.ifBlank { "Corporate action" }, color = HomeMuted, fontSize = 8.sp) }; Icon(Icons.Default.ChevronRight, null, tint = HomeMuted) }
    }
}

@Composable private fun IntelligenceNewsCard(item: NewsItem, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.imageUrl.isNotBlank()) AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else HomeLogo(item.symbol, null, 58)
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.title, color = HomeTextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2); Text(listOf(item.companyName, item.category).filter { it.isNotBlank() }.joinToString(" • "), color = HomeMuted, fontSize = 8.sp, maxLines = 1); if (item.summary.isNotBlank()) Text(item.summary, color = HomeMuted, fontSize = 8.sp, maxLines = 2) }; Icon(Icons.Default.ChevronRight, null, tint = HomeMuted)
        }
    }
}

@Composable private fun HomeLogo(symbol: String, logoUrl: String?, size: Int) {
    val resolved = logoUrl?.takeIf { it.isNotBlank() } ?: symbol.takeIf { it.isNotBlank() }?.let { "https://mystocks.africa/logos/${it.lowercase(Locale.US)}-ke.svg" }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(9.dp)).background(HomeLightGreen)) { if (resolved != null) AsyncImage(model = resolved, contentDescription = symbol, modifier = Modifier.fillMaxSize().padding(5.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) else Icon(Icons.Default.Article, null, tint = HomeGreen, modifier = Modifier.padding((size / 4).dp)) }
}

@Composable private fun DailyBriefCard(validCount: Int, advancing: Int, declining: Int, corporateActionCount: Int, intelligenceCount: Int) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = HomeLightGreen)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(CircleShape).background(HomeGreen)) { Icon(Icons.Default.Lightbulb, null, tint = Color.White, modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Today's NSE Brief", color = HomeDarkGreen, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp); Text("$validCount stocks covered • $advancing advancing • $declining declining", color = HomeMuted, fontSize = 9.sp); Text("$intelligenceCount company intelligence items • $corporateActionCount corporate actions", color = HomeMuted, fontSize = 9.sp) } }
    }
}

@Composable private fun LoadingHomeCard() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = HomeGreen); Spacer(Modifier.width(10.dp)); Text("Loading market intelligence…", color = HomeMuted, fontSize = 10.sp) } }
}

@Composable private fun EmptyHomeCard(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, HomeBorder)) { Column(Modifier.padding(14.dp)) { Text(title, color = HomeTextDark, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(subtitle, color = HomeMuted, fontSize = 9.sp) } }
}
