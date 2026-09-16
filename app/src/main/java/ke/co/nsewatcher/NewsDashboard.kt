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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.Locale
import ke.co.nsewatcher.data.NewsCache

private val NewsGreen = Color(0xFF00A859)
private val NewsLight = Color(0xFFE9F8F0)
private val NewsDark = Color(0xFF083C27)
private val NewsText = Color(0xFF12231B)
private val NewsMuted = Color(0xFF6C7A72)
private val NewsBorder = Color(0xFFE1EAE5)
private const val NewsSkyline = "https://upload.wikimedia.org/wikipedia/commons/8/80/Nairobi_Skyline_from_West.jpg"

private data class NewsMeta(val symbol: String, val company: String, val logo: String, val label: String)

private val KnownNewsCompanies = listOf(
    "SCOM" to "Safaricom", "KCB" to "KCB Group", "EQTY" to "Equity Group", "COOP" to "Co-operative Bank",
    "ABSA" to "Absa Bank Kenya", "EABL" to "East African Breweries", "KPLC" to "Kenya Power",
    "KEGN" to "KenGen", "BRIT" to "Britam Holdings", "KNRE" to "Kenya Re", "BAT" to "BAT Kenya",
    "NCBA" to "NCBA Group", "KQ" to "Kenya Airways", "JUB" to "Jubilee Holdings", "DTK" to "Diamond Trust Bank",
    "SBIC" to "Stanbic Holdings", "I&M" to "I&M Group", "CTUM" to "Centum Investment", "TOTL" to "TotalEnergies Marketing Kenya",
    "CARB" to "Carbacid Investments", "BOC" to "BOC Kenya", "UNGA" to "Unga Group", "LONG" to "Longhorn Publishers"
)

private fun dashboardNewsMeta(item: NewsItem): NewsMeta {
    val directSymbol = item.symbol.trim().uppercase(Locale.US).removeSuffix(".KE")
    val text = "${item.companyName} ${item.title}".lowercase(Locale.US)
    val resolved = KnownNewsCompanies.firstOrNull { (symbol, company) ->
        directSymbol == symbol || text.contains(company.lowercase(Locale.US)) || text.contains(symbol.lowercase(Locale.US))
    }
    val symbol = if (directSymbol.isNotBlank()) directSymbol else resolved?.first.orEmpty()
    val company = item.companyName.takeIf { it.isNotBlank() && !it.equals("MyStocks Africa", true) }
        ?: resolved?.second.orEmpty()
    val logo = symbol.takeIf { it.isNotBlank() }?.let {
        "https://mystocks.africa/logos/${it.lowercase(Locale.US)}-ke.svg"
    }.orEmpty()
    val label = symbol.ifBlank { item.source.ifBlank { "NSE" } }
    return NewsMeta(symbol, company, logo, label)
}

@Composable
private fun NewsDashboardHeader(title: String, sub: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            if (sub != null) Text(sub, fontSize = 10.sp, color = NewsMuted)
        }
    }
}

@Composable
fun NewsDashboard(open: (NewsItem) -> Unit) {
    var items by remember { mutableStateOf(emptyList<NewsItem>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        val result = NewsCache.loadFeedResult()
        items = result.items.sortedByDescending { it.publishedAt }
        error = result.error
        loading = false
    }

    val categories = listOf("All", "Company News", "Dividends", "Market", "Corporate Actions", "Analysis")
    val filtered = if (category == "All") items else items.filter { it.category.equals(category, true) }
    val top = filtered.firstOrNull()
    val companyNews = filtered.filter { it.symbol.isNotBlank() || it.companyName.isNotBlank() || dashboardNewsMeta(it).symbol.isNotBlank() }
    val trending = companyNews.drop(if (top != null && companyNews.firstOrNull()?.id == top.id) 1 else 0).take(3)
    val latest = filtered.filter { it.id != top?.id && trending.none { t -> t.id == it.id } }.take(3)

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { NewsDashboardHeader("News", "NSE companies, dividends & market intelligence") }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                categories.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c, fontSize = 10.sp, maxLines = 1) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NewsGreen)
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = BorderStroke(1.dp, NewsBorder)) {
                    Column(
                        Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Article, null, tint = NewsGreen, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("News unavailable", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            error ?: "The live news provider returned no articles for this category.",
                            color = NewsMuted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            top?.let { article ->
                item { Text("Top News", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = NewsText) }
                item { NewsDashboardFeatured(article, open) }
            }

            if (trending.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Whatshot, null, tint = NewsGreen, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Most Trending News", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = NewsText)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = NewsLight) {
                            Text("View all trending →", color = NewsGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 1.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(trending) { article -> NewsTrendingCard(article, open) }
                    }
                }
            }

            if (latest.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Article, null, tint = NewsGreen, modifier = Modifier.size(21.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Latest News", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = NewsText)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = NewsLight) {
                            Text("View all news →", color = NewsGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                items(latest) { article -> NewsLatestCard(article, open) }
            }
        }

        item {
            Text(
                "News and corporate-action information is sourced through the MyStocks market-intelligence feed. Verify important announcements against the issuer or exchange source.",
                color = NewsMuted,
                fontSize = 8.sp,
                modifier = Modifier.padding(horizontal = 3.dp)
            )
        }
    }
}

@Composable
private fun NewsDashboardFeatured(item: NewsItem, open: (NewsItem) -> Unit) {
    val meta = dashboardNewsMeta(item)
    Card(Modifier.fillMaxWidth().height(270.dp).clickable { open(item) }, RoundedCornerShape(19.dp)) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = NewsSkyline,
                contentDescription = "Nairobi skyline",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color(0xA9083C27)))
            Column(
                Modifier.fillMaxSize().padding(13.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = NewsGreen) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Whatshot, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Top News", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashboardLogo(item, 42, true)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (meta.company.isNotBlank()) meta.company else if (item.source.isNotBlank()) item.source else "NSE",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Text(
                            if (meta.symbol.isNotBlank()) "(${meta.symbol})" else item.source,
                            color = Color.White.copy(alpha = .8f),
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    item.title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    maxLines = 3
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(dashboardDate(item.publishedAt), color = Color.White, fontSize = 8.sp)
                    Spacer(Modifier.width(7.dp))
                    Text("|", color = Color.White.copy(alpha = .7f), fontSize = 8.sp)
                    Spacer(Modifier.width(7.dp))
                    DashboardChip(item.category, true)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x3300A859),
                        border = BorderStroke(1.dp, NewsGreen)
                    ) {
                        Text(
                            "Read full article  →",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsTrendingCard(item: NewsItem, open: (NewsItem) -> Unit) {
    val meta = dashboardNewsMeta(item)
    Card(Modifier.width(190.dp).height(185.dp).clickable { open(item) }, RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NewsBorder)) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DashboardLogo(item, 40, false)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (meta.company.isNotBlank()) meta.company else meta.label,
                    color = NewsText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (meta.symbol.isNotBlank()) DashboardSymbol(meta.symbol)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.title, color = Color(0xFF183B6B), fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = NewsMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(dashboardDate(item.publishedAt), color = NewsMuted, fontSize = 8.sp)
            }
            Spacer(Modifier.height(5.dp))
            DashboardChip("Trending", false, Icons.Default.Whatshot)
        }
    }
}

@Composable
private fun NewsLatestCard(item: NewsItem, open: (NewsItem) -> Unit) {
    val meta = dashboardNewsMeta(item)
    Card(Modifier.fillMaxWidth().clickable { open(item) }, RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NewsBorder)) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(82.dp, 68.dp), RoundedCornerShape(12.dp), NewsLight) {
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { DashboardLogo(item, 50, false) }
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (meta.company.isNotBlank()) meta.company else meta.label,
                        color = NewsText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (meta.symbol.isNotBlank()) DashboardSymbol(meta.symbol)
                }
                Spacer(Modifier.height(3.dp))
                Text(item.title, color = Color(0xFF183B6B), fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, null, tint = NewsMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(dashboardDate(item.publishedAt), color = NewsMuted, fontSize = 8.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("|", color = NewsMuted, fontSize = 8.sp)
                    Spacer(Modifier.width(6.dp))
                    DashboardChip(item.category, false)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = NewsMuted, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun DashboardLogo(item: NewsItem, size: Int, dark: Boolean) {
    val meta = dashboardNewsMeta(item)
    Surface(Modifier.size(size.dp), RoundedCornerShape(10.dp), if (dark) Color.White.copy(alpha = .14f) else NewsLight) {
        if (meta.logo.isNotBlank()) {
            AsyncImage(
                model = meta.logo,
                contentDescription = meta.symbol,
                modifier = Modifier.fillMaxSize().padding(5.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.Business, null, tint = if (dark) Color.White else NewsGreen, modifier = Modifier.size((size / 2).dp))
            }
        }
    }
}

@Composable
private fun DashboardSymbol(symbol: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = NewsLight) {
        Text(symbol, color = NewsGreen, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun DashboardChip(label: String, dark: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (dark) Color.White.copy(alpha = .16f) else NewsLight) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (dark) Color.White else NewsGreen, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(label, color = if (dark) Color.White else NewsGreen, fontWeight = FontWeight.Bold, fontSize = 8.sp)
        }
    }
}

private fun dashboardDate(value: String): String = when {
    value.isBlank() -> "Latest"
    value.length >= 10 -> value.take(10)
    else -> value
}
