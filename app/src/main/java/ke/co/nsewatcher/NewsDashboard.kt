package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import ke.co.nsewatcher.data.SavedNewsStore
import kotlinx.coroutines.flow.catch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.MarketData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val NewsBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
private val NewsCard: Color
    @Composable get() = MaterialTheme.colorScheme.surface
private val NewsRaised: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val NewsGreen: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val NewsText: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground
private val NewsMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val NewsBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outline
private val NewsLink: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

internal val NewsColorScheme: ColorScheme
    @Composable get() = MaterialTheme.colorScheme

private val NewsCategories = listOf("All", "Saved", "Companies", "Dividends", "Market", "Results", "Announcements", "Analysis")

private fun isDividendNews(item: NewsItem): Boolean =
    item.category.equals("Dividends", true) || item.dividendAmount.isNotBlank() ||
        item.exDate.isNotBlank() || item.paymentDate.isNotBlank() ||
        Regex("\\bdividends?\\b", RegexOption.IGNORE_CASE).containsMatchIn(item.title)

private fun isResultsNews(item: NewsItem): Boolean =
    item.category.equals("Results", true) || item.category.equals("Financial Results", true) ||
        Regex("\\b(earnings|financial results|annual results|interim results|half.year results|full.year results|quarterly results)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(item.title)

private fun matchesNewsCategory(item: NewsItem, category: String): Boolean = when (category) {
    "All" -> true
    "Companies" -> item.category.equals("Company News", true) || item.symbol.isNotBlank() ||
        (item.companyName.isNotBlank() && !item.companyName.equals("MyStocks Africa", true))
    "Dividends" -> isDividendNews(item)
    "Results" -> isResultsNews(item)
    "Announcements" -> item.category.equals("Corporate Actions", true) ||
        isDividendNews(item) || isResultsNews(item)
    else -> item.category.equals(category, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDashboard(
    newsFeed: List<NewsItem>,
    catalog: List<Stock>,
    quotes: List<Stock>,
    onNewsLoaded: (List<NewsItem>) -> Unit,
    openAlerts: () -> Unit,
    open: (NewsItem) -> Unit
) {
    PremiumNewsDashboard(
        newsFeed = newsFeed,
        catalog = catalog,
        quotes = quotes,
        onNewsLoaded = onNewsLoaded,
        openAlerts = openAlerts,
        open = open
    )
}

@Composable
private fun NewsDashboardHeader(searching: Boolean, refreshing: Boolean, onSearch: () -> Unit, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NseWatcherBrandLockup(compact = true)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("News & insights", Modifier.weight(1f), color = NewsText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(36.dp).background(NewsRaised, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (searching) Icons.Default.Close else Icons.Default.Search, if (searching) "Close search" else "Search news", tint = NewsMuted, modifier = Modifier.size(21.dp))
                }
            }
            IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(36.dp).background(NewsRaised, CircleShape), contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(19.dp), color = NewsGreen, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Refresh news", tint = NewsMuted, modifier = Modifier.size(21.dp))
                }
            }
        }
        Text("Understand the stories behind the market", color = NewsMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun NewsSectionHeading(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = NewsText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onAction) {
            Text(action, color = NewsLink, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowForward, null, tint = NewsLink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NewsArticleImage(item: NewsItem, modifier: Modifier = Modifier) {
    var imageLoaded by remember(item.imageUrl) { mutableStateOf(false) }
    Box(modifier.background(Brush.linearGradient(listOf(NewsRaised, MaterialTheme.colorScheme.tertiaryContainer, NewsCard))), contentAlignment = Alignment.Center) {
        if (!imageLoaded) Icon(Icons.Default.Newspaper, null, tint = NewsMuted.copy(alpha = 0.45f), modifier = Modifier.size(48.dp))
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(
                model = item.imageUrl, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(if (imageLoaded) 1f else 0f),
                onSuccess = { imageLoaded = true }, onError = { imageLoaded = false }
            )
        }
    }
}

@Composable
private fun NewsFeaturedCard(item: NewsItem, open: (NewsItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = "Read featured article") { open(item) },
        shape = RoundedCornerShape(18.dp), color = NewsCard, border = BorderStroke(1.dp, NewsBorder)
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 278.dp)) {
            NewsArticleImage(item, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, NewsBackground.copy(alpha = 0.82f), NewsBackground))))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 110.dp, bottom = 18.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                    Text(item.category.ifBlank { "Latest story" }.uppercase(Locale.US), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(item.title, color = NewsText, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                NewsArticleMeta(item)
            }
        }
    }
}

@Composable
private fun NewsStoryCard(item: NewsItem, open: (NewsItem) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = "Read article") { open(item) },
        shape = RoundedCornerShape(14.dp), color = NewsCard, border = BorderStroke(1.dp, NewsBorder)
    ) {
        Column {
            NewsArticleImage(item, Modifier.fillMaxWidth().height(100.dp))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(item.category.ifBlank { "News" }, color = NewsMuted, fontSize = 11.sp)
                Text(item.title, color = NewsText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, minLines = 3, maxLines = 4, overflow = TextOverflow.Ellipsis)
                NewsArticleMeta(item)
            }
        }
    }
}

@Composable
private fun NewsListCard(item: NewsItem, open: (NewsItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = "Read article") { open(item) },
        shape = RoundedCornerShape(14.dp), color = NewsCard, border = BorderStroke(1.dp, NewsBorder)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NewsArticleImage(item, Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, color = NewsText, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                NewsArticleMeta(item)
                if (item.exDate.isNotBlank()) Text("Ex-dividend: ${newsDate(item.exDate)}", color = NewsGreen, fontSize = 12.sp)
                if (item.paymentDate.isNotBlank()) Text("Payment: ${newsDate(item.paymentDate)}", color = NewsMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NewsArticleMeta(item: NewsItem) {
    Text(
        item.source.ifBlank { "Source unavailable" } + " • " + newsDate(item.publishedAt),
        color = NewsMuted, fontSize = 12.sp, lineHeight = 17.sp,
        maxLines = 3, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun NewsCorporateCalendar(selectCategory: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = NewsCard, border = BorderStroke(1.dp, NewsBorder)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Corporate calendar", color = NewsText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            NewsCalendarRow(Icons.Default.CalendarMonth, "Dividend dates", "Check dividend announcements and available dates.") { selectCategory("Dividends") }
            HorizontalDivider(color = NewsBorder)
            NewsCalendarRow(Icons.Default.Description, "Company results", "Explore published company results and earnings coverage.") { selectCategory("Results") }
            HorizontalDivider(color = NewsBorder)
            TextButton(onClick = { selectCategory("Announcements") }, modifier = Modifier.align(Alignment.End)) {
                Text("Explore announcements", color = NewsLink, fontSize = 13.sp)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Default.ArrowForward, null, tint = NewsLink, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NewsCalendarRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).background(NewsRaised, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = NewsMuted, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = NewsText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = NewsMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = NewsMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun NewsMessage(title: String, detail: String, action: String, onAction: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = NewsCard, border = BorderStroke(1.dp, NewsBorder)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Newspaper, null, tint = NewsGreen, modifier = Modifier.size(28.dp))
            Text(title, color = NewsText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = NewsMuted, fontSize = 14.sp, lineHeight = 20.sp)
            TextButton(onClick = onAction) { Text(action, color = NewsGreen) }
        }
    }
}

private fun newsDate(value: String): String {
    if (value.isBlank()) return "Date unavailable"
    return runCatching {
        LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
    }.getOrDefault(value.take(10))
}

