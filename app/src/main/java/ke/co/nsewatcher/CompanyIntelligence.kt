package ke.co.nsewatcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.CompanyIntelligenceEngine
import ke.co.nsewatcher.data.AnalystCache
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

private val IntelligenceGreen = Color(0xFF00A859)
private val IntelligenceLight = Color(0xFFE9F8F0)
private val IntelligenceDark = Color(0xFF083C27)
private val IntelligenceText = Color(0xFF12231B)
private val IntelligenceMuted = Color(0xFF6C7A72)
private val IntelligenceBorder = Color(0xFFE1EAE5)
private val IntelligenceRed = Color(0xFFE04444)

@Composable
private fun CompanyOverviewCard(
    stock: Stock,
    marketStatus: MyStocksCache.MarketStatus,
    watched: Boolean,
    onBack: () -> Unit,
    onWatchToggle: (() -> Unit)?
) {
    val hasPrice = stock.observedAt.isNotBlank() && stock.dataOrigin == "backend" && stock.price.isFinite()
    val hasChange = stock.changeAvailable && stock.change.isFinite() && stock.dataOrigin == "backend"
    val change = stock.change
    val changeColor = if (hasChange) {
        if (change >= 0.0) IntelligenceGreen else IntelligenceRed
    } else IntelligenceMuted

    IntelligenceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = IntelligenceText)
            }
            CompanyLogo(stock.symbol, 42, stock.logoUrl)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stock.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceText, maxLines = 2)
                Text(stock.symbol + " • NSE", fontSize = 9.sp, color = IntelligenceMuted)
            }
            if (onWatchToggle != null) {
                IconButton(onClick = onWatchToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (watched) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (watched) "Remove from watchlist" else "Add to watchlist",
                        tint = if (watched) IntelligenceGreen else IntelligenceMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = IntelligenceBorder)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("LATEST NSE OBSERVATION", color = IntelligenceMuted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (hasPrice) String.format(Locale.US, "KSh %.2f", stock.price) else "Price unavailable",
                    color = if (hasPrice) IntelligenceText else IntelligenceMuted,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (hasChange) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format(Locale.US, "%+.2f%%", change), color = changeColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("vs previous close", color = IntelligenceMuted, fontSize = 8.sp)
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.weight(1f)) {
                MiniFact("STATUS", when {
                    !marketStatus.isKnown -> "Unknown"
                    marketStatus.isOpen -> "Market open"
                    else -> "Market closed"
                })
            }
            Box(Modifier.weight(1f)) {
                MiniFact(
                    "OBSERVATION",
                    if (stock.observedAt.isNotBlank()) formatCompactChartTimestamp(stock.observedAt) + " EAT" else "Unavailable"
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (hasPrice) marketObservationLabel(stock) else "Latest NSE observation unavailable from the market feed",
            color = IntelligenceMuted,
            fontSize = 8.sp,
            lineHeight = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        Text("Exchange-supplied NSE data • analysis only • no real trading", color = IntelligenceMuted, fontSize = 7.sp)
    }
}

@Composable
private fun CompanyNewsSection(
    news: List<NewsItem>,
    loading: Boolean
) {
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxWidth()) {
        SectionTitle(
            "Company news",
            "Recent announcements, market coverage and corporate events",
            Icons.Default.Newspaper
        )

        when {
            loading -> IntelligenceCard {
                IntelligenceLoader("Loading company news", "Checking recent company announcements…")
            }

            news.isEmpty() -> IntelligenceCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(Modifier.size(42.dp), RoundedCornerShape(12.dp), color = Color(0xFF10283D)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Newspaper, null, tint = IntelligenceGreen)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("No recent company news", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "No articles or corporate events were returned by the current feed.",
                            color = Color(0xFFA9BCD0),
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            else -> {
                val featured = news.first()
                val secondary = news.drop(1).take(4)

                IntelligenceCard {
                    Text(
                        "LATEST",
                        color = IntelligenceGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(7.dp))

                    if (featured.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = featured.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Surface(
                            Modifier.fillMaxWidth().height(74.dp),
                            RoundedCornerShape(14.dp),
                            color = Color(0xFF10283D)
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Newspaper, null, tint = IntelligenceGreen, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Company intelligence update", color = Color(0xFFF4F7FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            featured.category.ifBlank { "Market" }.uppercase(Locale.US),
                            color = IntelligenceGreen,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(formatCompanyNewsDate(featured.publishedAt), color = Color(0xFFA9BCD0), fontSize = 8.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        featured.title,
                        color = Color(0xFFF4F7FA),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 4
                    )
                    if (featured.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            featured.summary,
                            color = Color(0xFFA9BCD0),
                            fontSize = 9.sp,
                            lineHeight = 14.sp,
                            maxLines = 3
                        )
                    }
                    if (featured.url.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri(featured.url) } },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Read full story  →", color = IntelligenceGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                secondary.forEach { article ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = {
                            if (article.url.isNotBlank()) runCatching { uriHandler.openUri(article.url) }
                        },
                        enabled = article.url.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0A1F32),
                        border = BorderStroke(1.dp, Color(0xFF17364F))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.Top) {
                            if (article.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = article.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(Modifier.size(72.dp), RoundedCornerShape(10.dp), color = Color(0xFF10283D)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Article, null, tint = IntelligenceGreen)
                                    }
                                }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        article.category.ifBlank { "Market" }.uppercase(Locale.US),
                                        color = IntelligenceGreen,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(formatCompanyNewsDate(article.publishedAt), color = Color(0xFFA9BCD0), fontSize = 7.sp, maxLines = 1)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    article.title,
                                    color = Color(0xFFF4F7FA),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3
                                )
                                if (article.source.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(article.source, color = Color(0xFFA9BCD0), fontSize = 7.sp, maxLines = 1)
                                }
                            }
                            if (article.url.isNotBlank()) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open article", tint = Color(0xFFA9BCD0), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }

                if (news.size > 5) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "+\${news.size - 5} more stories available in the company news feed",
                        color = Color(0xFFA9BCD0),
                        fontSize = 8.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun formatCompanyNewsDate(raw: String): String {
    if (raw.isBlank()) return "Latest"
    return runCatching {
        Instant.parse(raw)
            .atZone(ZoneId.of("Africa/Nairobi"))
            .format(DateTimeFormatter.ofPattern("dd MMM yy • h:mm a", Locale.US))
    }.getOrElse { raw.take(10) }
}


private fun marketObservationLabel(stock: Stock): String {
    val delay = stock.delayMinutes ?: 15
    val observed = runCatching {
        Instant.parse(stock.observedAt)
            .atZone(ZoneId.of("Africa/Nairobi"))
            .format(DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.US))
    }.getOrDefault(stock.observedAt.replace("T", " ").removeSuffix("Z").take(16))
    return "Latest NSE observation • $observed EAT • $delay-min delayed"
}


private enum class CompanyIntelligenceSection(val label: String) {
    ABOUT("About"), INTELLIGENCE("Intelligence"), PERFORMANCE("Performance"),
    FINANCIALS("Financials"), VALUATION("Valuation"), DIVIDENDS("Dividends"),
    NEWS("News"), ANALYSIS("Analysis"), EVIDENCE("Evidence")
}

@Composable
private fun CompanySectionNavigation(
    labels: List<CompanyIntelligenceSection>,
    selected: CompanyIntelligenceSection,
    onSelected: (CompanyIntelligenceSection) -> Unit
) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF071B2F), RoundedCornerShape(12.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEach { section ->
            val active = section == selected
            Surface(
                onClick = { onSelected(section) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                color = if (active) IntelligenceGreen else Color.Transparent,
                contentColor = if (active) Color.White else Color(0xFFA9B7C6),
                shape = RoundedCornerShape(8.dp),
                border = if (active) null else BorderStroke(1.dp, Color(0xFF294057))
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(section.label, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}


@Composable
private fun ApprovedCompanyOverview(
    stock: Stock,
    back: () -> Unit,
    sector: String,
    previousClose: Double?,
    open: Double?,
    dayHigh: Double?,
    dayLow: Double?,
    latest: Double?,
    observedAt: String,
    dailyChange: Double?,
    sinceOpen: Double?,
    points: List<MyStocksCache.HistoryPoint>,
    loading: Boolean
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF061625))
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                ApprovedCompanyHeader(
                    stock = stock,
                    price = latest,
                    change = dailyChange,
                    sector = sector,
                    back = back
                )
            }

            item {
                ApprovedCompanyTabs()
            }

            item {
                ApprovedGlanceCard(
                    previousClose, open, dayHigh, dayLow, latest,
                    observedAt, dailyChange, sinceOpen
                )
            }

            item {
                ApprovedChartCard(points, previousClose, latest, loading)
            }

            item {
                Surface(
                    onClick = { },
                    modifier = Modifier.padding(horizontal = 36.dp).fillMaxWidth(),
                    color = Color(0xFF0A1F32),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF17364F))
                ) {
                    Row(
                        Modifier.padding(horizontal = 26.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            Modifier.size(54.dp),
                            RoundedCornerShape(50),
                            color = Color(0xFF102D28),
                            border = BorderStroke(1.dp, Color(0xFF1E5B45))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Psychology, null, tint = Color(0xFF00D084), modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Company Intelligence", color = Color(0xFFF4F7FA), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "AI-powered insights, financials and key information about " + stock.name + ".",
                                color = Color(0xFFA9BCD0), fontSize = 15.sp, lineHeight = 21.sp
                            )
                        }
                        Text("›", color = Color(0xFFA9BCD0), fontSize = 42.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovedCompanyHeader(
    stock: Stock,
    price: Double?,
    change: Double?,
    sector: String,
    back: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF071B2D)).padding(start = 36.dp, end = 36.dp, top = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFF4F7FA), modifier = Modifier.size(30.dp))
            }
            Text("Company Intelligence", color = Color(0xFFF4F7FA), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Box {
                Icon(Icons.Default.NotificationsNone, "Notifications", tint = Color(0xFFF4F7FA), modifier = Modifier.size(30.dp))
                Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFF4650)))
            }
            Spacer(Modifier.width(18.dp))
            Icon(Icons.Default.MoreVert, "More", tint = Color(0xFFF4F7FA), modifier = Modifier.size(29.dp))
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CompanyLogo(stock.symbol, 105, stock.logoUrl)
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stock.name, color = Color(0xFFF4F7FA), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Spacer(Modifier.height(7.dp))
                Text(stock.symbol + "  •  NSE", color = Color(0xFFA9BCD0), fontSize = 18.sp)
                Spacer(Modifier.height(7.dp))
                Text(sector, color = Color(0xFFA9BCD0), fontSize = 17.sp, maxLines = 2)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(price?.let(::currencyLabel) ?: "Unavailable", color = Color(0xFFF4F7FA), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                if (change != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (change >= 0.0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = if (change >= 0.0) Color(0xFF00D084) else Color(0xFFFF4D55), modifier = Modifier.size(21.dp))
                        Text(String.format(Locale.US, "%+.2f%%", change), color = if (change >= 0.0) Color(0xFF00D084) else Color(0xFFFF4D55), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text("vs previous close", color = Color(0xFFA9BCD0), fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(25.dp))
    }
}

@Composable
private fun ApprovedCompanyTabs() {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFF071B2D)).padding(horizontal = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        listOf("Overview", "Financials", "News", "Analysis", "About").forEachIndexed { index, label ->
            Surface(
                onClick = { },
                color = if (index == 0) Color(0xFF43E51B) else Color.Transparent,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.height(54.dp)
            ) {
                Box(Modifier.padding(horizontal = if (index == 0) 27.dp else 4.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (index == 0) Color(0xFF061B10) else Color(0xFFA9BCD0), fontSize = 18.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
    }
    HorizontalDivider(color = Color(0xFF12283B))
}

@Composable
private fun ApprovedGlanceCard(
    previousClose: Double?,
    open: Double?,
    dayHigh: Double?,
    dayLow: Double?,
    latest: Double?,
    observedAt: String,
    dailyChange: Double?,
    sinceOpen: Double?
) {
    Surface(
        Modifier.padding(horizontal = 36.dp).fillMaxWidth(),
        color = Color(0xFF0A1F32),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, Color(0xFF17364F))
    ) {
        Column(Modifier.padding(horizontal = 30.dp, vertical = 25.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShowChart, null, tint = Color(0xFF19E7D0), modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(17.dp))
                Text("Today at a glance", color = Color(0xFFF4F7FA), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(27.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    ApprovedMetric(Icons.Default.ShowChart, "Previous close", previousClose?.let(::currencyLabel) ?: "Unavailable")
                    ApprovedMetric(Icons.Default.ArrowUpward, "Day high", dayHigh?.let(::currencyLabel) ?: "Unavailable")
                    ApprovedMetric(Icons.Default.AccessTime, "Today's close", latest?.let(::currencyLabel) ?: "Unavailable")
                }
                VerticalDivider(Modifier.padding(horizontal = 25.dp).height(265.dp), color = Color(0xFF17364F))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    ApprovedMetric(Icons.Default.ShowChart, "Today's open", open?.let(::currencyLabel) ?: "Unavailable")
                    ApprovedMetric(Icons.Default.ArrowDownward, "Day low", dayLow?.let(::currencyLabel) ?: "Unavailable")
                    ApprovedMetric(Icons.Default.AccessTime, "Observed at", approvedObservedTime(observedAt))
                }
                VerticalDivider(Modifier.padding(horizontal = 25.dp).height(265.dp), color = Color(0xFF17364F))
                Column(Modifier.weight(1f)) {
                    ApprovedMovement(dailyChange?.let { it >= 0.0 }, "Today's change", dailyChange?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "Unavailable", previousClose?.let { "vs previous close (" + currencyLabel(it) + ")" } ?: "vs previous close")
                    Spacer(Modifier.height(42.dp))
                    ApprovedMovement(sinceOpen?.let { it >= 0.0 }, "Since open today", sinceOpen?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "Unavailable", if (open != null && latest != null) "(" + currencyLabel(open) + " → " + currencyLabel(latest) + ")" else "Open-to-latest movement unavailable")
                }
            }
        }
    }
}

@Composable
private fun ApprovedMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    phone: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFFAFC2F0), modifier = Modifier.size(if (phone) 18.dp else 27.dp))
        Spacer(Modifier.width(if (phone) 8.dp else 20.dp))
        Column {
            Text(label, color = Color(0xFFA9BCD0), fontSize = if (phone) 12.sp else 17.sp)
            Spacer(Modifier.height(if (phone) 3.dp else 8.dp))
            Text(value, color = Color(0xFFF4F7FA), fontSize = if (phone) 12.sp else 22.sp, fontWeight = if (phone) FontWeight.Medium else FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ApprovedMovement(
    positive: Boolean?,
    label: String,
    value: String,
    detail: String,
    phone: Boolean = false
) {
    val color = when (positive) {
        true -> Color(0xFF00D084)
        false -> Color(0xFFFF4D55)
        null -> Color(0xFFA9BCD0)
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            when (positive) {
                true -> Icons.Default.ArrowUpward
                false -> Icons.Default.ArrowDownward
                null -> Icons.Default.Remove
            },
            null,
            tint = color,
            modifier = Modifier.size(if (phone) 22.dp else 34.dp)
        )
        Spacer(Modifier.width(if (phone) 8.dp else 17.dp))
        Column {
            Text(label, color = color, fontSize = if (phone) 12.sp else 17.sp)
            Spacer(Modifier.height(if (phone) 3.dp else 7.dp))
            Text(value, color = color, fontSize = if (phone) 14.sp else 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(if (phone) 3.dp else 7.dp))
            Text(detail, color = Color(0xFFA9BCD0), fontSize = if (phone) 10.sp else 14.sp, lineHeight = if (phone) 14.sp else 19.sp)
        }
    }
}

@Composable
private fun ApprovedChartCard(
    points: List<MyStocksCache.HistoryPoint>,
    previousClose: Double?,
    latest: Double?,
    loading: Boolean
) {
    Surface(
        Modifier.padding(horizontal = 36.dp).fillMaxWidth(),
        color = Color(0xFF0A1F32),
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, Color(0xFF17364F))
    ) {
        Column(Modifier.padding(horizontal = 30.dp, vertical = 25.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShowChart, null, tint = Color(0xFF19E7D0), modifier = Modifier.size(31.dp))
                    Spacer(Modifier.width(15.dp))
                    Text("1D Intraday Chart", color = Color(0xFFF4F7FA), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(13.dp).clip(RoundedCornerShape(50)).background(Color(0xFF00D084)))
                    Spacer(Modifier.width(9.dp))
                    Text("NSE session 09:30 – 15:00 EAT", color = Color(0xFFA9BCD0), fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(17.dp))
            if (loading && points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00D084), strokeWidth = 2.dp)
                }
            } else {
                ApprovedIntradayCanvas(points, previousClose, latest, Modifier.fillMaxWidth().height(330.dp))
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ApprovedLegend(false, "Today's observations")
                ApprovedLegend(true, "Previous close (" + (previousClose?.let(::currencyLabel) ?: "unavailable") + ")")
            }
            Spacer(Modifier.height(14.dp))
            Surface(Modifier.fillMaxWidth(), color = Color(0xFF10283D), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0xFF17364F))) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFAFC2F0), modifier = Modifier.size(27.dp))
                    Spacer(Modifier.width(15.dp))
                    Text(
                        "The chart starts from the previous trading close, then follows today's actual NSE observations to the latest available price. No synthetic intraday prices are added.",
                        color = Color(0xFFA9BCD0), fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.Fullscreen, "Expand chart", tint = Color(0xFFAFC2F0), modifier = Modifier.size(25.dp))
                }
            }
        }
    }
}

@Composable
private fun ApprovedLegend(dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(38.dp).height(12.dp)) {
            val y = size.height / 2f
            if (dashed) {
                var x = 0f
                while (x < size.width) {
                    drawLine(Color(0xFF637FF0), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(min(x + 9f, size.width), y), strokeWidth = 3f)
                    x += 14f
                }
            } else {
                drawLine(Color(0xFF00D084), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(label, color = Color(0xFFA9BCD0), fontSize = 12.sp)
    }
}

@Composable
private fun ApprovedIntradayCanvas(
    points: List<MyStocksCache.HistoryPoint>,
    previousClose: Double?,
    latest: Double?,
    modifier: Modifier
) {
    var zoom by remember(points.size) { mutableFloatStateOf(1f) }
    var viewportStartMinutes by remember(points.size) { mutableFloatStateOf(0f) }

    val sessionStartMinutes = 9 * 60 + 30
    val sessionEndMinutes = 15 * 60
    val sessionSpanMinutes = (sessionEndMinutes - sessionStartMinutes).toFloat()

    fun clampViewport(nextZoom: Float, nextStart: Float): Float {
        val span = sessionSpanMinutes / nextZoom.coerceIn(1f, 4f)
        return nextStart.coerceIn(0f, max(0f, sessionSpanMinutes - span))
    }

    val gestureModifier = modifier
        .clipToBounds()
        .pointerInput(points.size) {
            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                val oldZoom = zoom
                val newZoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                val width = size.width.toFloat().coerceAtLeast(1f)
                val currentSpan = sessionSpanMinutes / oldZoom
                val anchorMinutes = viewportStartMinutes +
                    ((centroid.x / width).coerceIn(0f, 1f) * currentSpan)
                val newSpan = sessionSpanMinutes / newZoom
                val desiredStart = anchorMinutes -
                    ((centroid.x / width).coerceIn(0f, 1f) * newSpan) -
                    (pan.x / width) * newSpan
                zoom = newZoom
                viewportStartMinutes = clampViewport(newZoom, desiredStart)
            }
        }

    Canvas(gestureModifier) {
        val left = 50f
        val right = 104f
        val top = 18f
        val bottom = 50f
        val plotWidth = max(1f, size.width - left - right)
        val plotHeight = max(1f, size.height - top - bottom)
        val visibleSpanMinutes = sessionSpanMinutes / zoom
        val visibleStart = viewportStartMinutes
        val visibleEnd = visibleStart + visibleSpanMinutes

        val visiblePoints = points.mapIndexedNotNull { index, point ->
            if (!point.close.isFinite() || point.close <= 0.0) null
            else {
                val rawMinute = approvedChartMinutes(point.date)?.toFloat()
                    ?: (sessionStartMinutes + sessionSpanMinutes * index / max(1, points.lastIndex))
                // Keep provider timestamps that fall just outside the regular
                // NSE session visible at the chart boundary. Some feeds stamp
                // the final observation slightly beyond 15:00 EAT, but it is
                // still the session's closing observation.
                val minute = (rawMinute - sessionStartMinutes).coerceIn(0f, sessionSpanMinutes)
                Triple(index, point, minute)
            }
        }

        val values = (visiblePoints.map { it.second.close } + listOfNotNull(previousClose, latest))
            .filter { it.isFinite() && it > 0.0 }
        if (values.isEmpty()) return@Canvas

        val minValue = values.minOrNull() ?: 0.0
        val maxValue = values.maxOrNull() ?: 1.0
        val pad = max(0.05, (maxValue - minValue) * 0.08)
        val yMin = minValue - pad
        val yMax = maxValue + pad
        val range = max(0.0001, yMax - yMin)

        fun xAtMinute(minute: Float): Float =
            left + ((minute - visibleStart) / visibleSpanMinutes).coerceIn(0f, 1f) * plotWidth

        fun yAt(value: Double): Float =
            top + plotHeight - (((value - yMin) / range).toFloat() * plotHeight)

        repeat(6) { i ->
            val y = top + plotHeight * i / 5f
            drawLine(
                Color(0xFF17364F),
                androidx.compose.ui.geometry.Offset(left, y),
                androidx.compose.ui.geometry.Offset(left + plotWidth, y),
                strokeWidth = 1f
            )
        }

        val tickStep = when {
            zoom >= 3f -> 15f
            zoom >= 1.8f -> 30f
            else -> 60f
        }
        var tick = kotlin.math.ceil(visibleStart / tickStep) * tickStep
        while (tick <= visibleEnd + 0.1f) {
            val x = xAtMinute(tick)
            drawLine(
                Color(0xFF102B40),
                androidx.compose.ui.geometry.Offset(x, top),
                androidx.compose.ui.geometry.Offset(x, top + plotHeight),
                strokeWidth = 1f
            )
            tick += tickStep
        }

        previousClose?.takeIf { it.isFinite() && it > 0.0 }?.let {
            val y = yAt(it)
            var x = left
            while (x < left + plotWidth) {
                drawLine(
                    Color(0xFF8B78FF),
                    androidx.compose.ui.geometry.Offset(x, y),
                    androidx.compose.ui.geometry.Offset(min(x + 10f, left + plotWidth), y),
                    strokeWidth = 3f
                )
                x += 16f
            }

            val bubbleWidth = 82f
            val bubbleHeight = 46f
            val bubbleLeft = left + plotWidth + 8f
            val bubbleTop = (y - bubbleHeight / 2f).coerceIn(top, top + plotHeight - bubbleHeight)
            drawRoundRect(
                Color(0xFF8B78FF),
                androidx.compose.ui.geometry.Offset(bubbleLeft, bubbleTop),
                androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                androidx.compose.ui.geometry.CornerRadius(9f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.2f", it),
                bubbleLeft + bubbleWidth / 2f,
                bubbleTop + 19f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            drawContext.canvas.nativeCanvas.drawText(
                "PREV CLOSE",
                bubbleLeft + bubbleWidth / 2f,
                bubbleTop + 36f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    textSize = 9f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // Draw each movement segment independently. Green means the next
        // observation is at or above the previous one; red means it moved down.
        // This keeps the intraday line readable when the price changes direction
        // several times during the session.
        val plottedPoints = visiblePoints.filter { it.third >= visibleStart - 1f && it.third <= visibleEnd + 1f }
        if (plottedPoints.isNotEmpty()) {
            // Anchor the session chart to the actual previous trading close.
            // The previous close is a reference observation, not a fabricated
            // intraday candle. The segment to the first NSE observation makes
            // the opening move visible instead of making the chart appear to
            // start only at today's opening price.
            val firstPoint = plottedPoints.firstOrNull()
            val previousClosePoint = previousClose?.takeIf { it.isFinite() && it > 0.0 }?.let {
                Triple(-1, MyStocksCache.HistoryPoint(it, ""), 0f)
            }
            val chartPoints = if (previousClosePoint != null && firstPoint != null && firstPoint.third > 0f) {
                listOf(previousClosePoint) + plottedPoints
            } else {
                plottedPoints
            }

            for (i in 1 until chartPoints.size) {
                val previous = chartPoints[i - 1]
                val current = chartPoints[i]
                val x1 = xAtMinute(previous.third)
                val y1 = yAt(previous.second.close)
                val x2 = xAtMinute(current.third)
                val y2 = yAt(current.second.close)
                val movementColor = when {
                    current.second.close > previous.second.close -> Color(0xFF00D084)
                    current.second.close < previous.second.close -> Color(0xFFFF5C5C)
                    else -> Color(0xFF9FB3C8)
                }
                drawLine(
                    movementColor,
                    androidx.compose.ui.geometry.Offset(x1, y1),
                    androidx.compose.ui.geometry.Offset(x2, y2),
                    strokeWidth = 4.2f,
                    cap = StrokeCap.Round
                )
            }

            // Subtle directional fill follows the same movement colors.
            for (i in 1 until plottedPoints.size) {
                val previous = plottedPoints[i - 1]
                val current = plottedPoints[i]
                val movementColor = when {
                    current.second.close > previous.second.close -> Color(0xFF00D084)
                    current.second.close < previous.second.close -> Color(0xFFFF5C5C)
                    else -> Color(0xFF9FB3C8)
                }
                val fill = Path().apply {
                    moveTo(xAtMinute(previous.third), top + plotHeight)
                    lineTo(xAtMinute(previous.third), yAt(previous.second.close))
                    lineTo(xAtMinute(current.third), yAt(current.second.close))
                    lineTo(xAtMinute(current.third), top + plotHeight)
                    close()
                }
                drawPath(fill, movementColor.copy(alpha = 0.07f))
            }
        }

        fun drawMarker(point: MyStocksCache.HistoryPoint?, label: String, tint: Color, alignLeft: Boolean) {
            if (point == null || !point.close.isFinite() || point.close <= 0.0) return
            val rawMinute = approvedChartMinutes(point.date)?.toFloat() ?: return
            // At the normal 1D view, keep the OPEN/CLOSE marker attached to
            // the chart even when the provider timestamp sits just outside
            // the 09:30–15:00 session window.
            val minute = (rawMinute - sessionStartMinutes).coerceIn(0f, sessionSpanMinutes)
            if (minute < visibleStart || minute > visibleEnd) return
            val x = xAtMinute(minute)
            val y = yAt(point.close)
            drawCircle(tint, 6.5f, androidx.compose.ui.geometry.Offset(x, y))
            val bubbleWidth = if (label == "LATEST") 78f else 76f
            val bubbleHeight = 44f
            val desiredLeft = if (alignLeft) x - bubbleWidth - 8f else x + 8f
            val bubbleLeft = desiredLeft.coerceIn(left, left + plotWidth - bubbleWidth)
            val bubbleTop = (y - bubbleHeight / 2f).coerceIn(top, top + plotHeight - bubbleHeight)
            drawRoundRect(
                tint,
                androidx.compose.ui.geometry.Offset(bubbleLeft, bubbleTop),
                androidx.compose.ui.geometry.Size(bubbleWidth, bubbleHeight),
                androidx.compose.ui.geometry.CornerRadius(9f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.2f", point.close),
                bubbleLeft + bubbleWidth / 2f,
                bubbleTop + 19f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.rgb(4,35,24)
                    textSize = 13f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            drawContext.canvas.nativeCanvas.drawText(
                label,
                bubbleLeft + bubbleWidth / 2f,
                bubbleTop + 35f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.rgb(4,35,24)
                    textSize = 9f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // The right-hand endpoint is the latest available actual observation.
        drawMarker(points.lastOrNull(), "LATEST", Color(0xFF00D084), alignLeft = true)

        val scale = (0 until 6).map { i -> yMax - (yMax - yMin) * i / 5 }
        scale.forEachIndexed { i, value ->
            val y = top + plotHeight * i / 5f
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.0f", value),
                left - 10f,
                y + 5f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.rgb(169,188,208)
                    textSize = 14f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        var labelTick = kotlin.math.ceil(visibleStart / tickStep) * tickStep
        while (labelTick <= visibleEnd + 0.1f) {
            val x = xAtMinute(labelTick)
            drawContext.canvas.nativeCanvas.drawText(
                approvedMinuteLabel(sessionStartMinutes + labelTick),
                x,
                size.height - 12f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.rgb(169,188,208)
                    textSize = 12f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
            labelTick += tickStep
        }

        drawContext.canvas.nativeCanvas.drawText(
            if (zoom > 1.05f) "Zoom " + String.format(Locale.US, "%.1fx", zoom) + " • pinch / drag to explore"
            else "Pinch to zoom • drag to explore the session",
            left + plotWidth / 2f,
            12f,
            android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.rgb(169,188,208)
                textSize = 10f
                textAlign = android.graphics.Paint.Align.CENTER
            }
        )
    }
}

private fun approvedChartMinutes(raw: String): Int? {
    if (raw.isBlank()) return null
    val local = runCatching {
        Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi")).toLocalTime()
    }.getOrNull() ?: return null
    return local.hour * 60 + local.minute
}

private fun approvedMinuteLabel(totalMinutes: Float): String {
    val rounded = totalMinutes.toInt().coerceIn(0, 23 * 60 + 59)
    val hour = rounded / 60
    val minute = rounded % 60
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

private fun approvedObservedTime(raw: String): String {
    if (raw.isBlank()) return "Unavailable"
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi"))
            .format(DateTimeFormatter.ofPattern("h:mm a 'EAT'", Locale.US))
    }.getOrElse { raw.replace("T", " ").removeSuffix("Z").take(16) }
}

private fun approvedChartTime(raw: String): String {
    if (raw.isBlank()) return "—"
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi"))
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    }.getOrElse { raw.substringAfter("T", raw).take(5) }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompanyIntelligence(
    s: Stock,
    back: () -> Unit,
    watched: Boolean = false,
    onWatchToggle: (() -> Unit)? = null
) {
    var historyResult by remember(s.symbol) { mutableStateOf(MyStocksCache.HistoryResult()) }
    var chartResult by remember(s.symbol) { mutableStateOf(MyStocksCache.HistoryResult()) }
    var chartLoading by remember(s.symbol) { mutableStateOf(true) }
    var selectedChartRange by rememberSaveable(s.symbol) { mutableStateOf("1D") }
    var rangeResults by remember(s.symbol) { mutableStateOf<Map<String, MyStocksCache.HistoryResult>>(emptyMap()) }
    var marketStatus by remember(s.symbol) { mutableStateOf(MyStocksCache.MarketStatus()) }
    var intelligence by remember(s.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var companyNews by remember(s.symbol) { mutableStateOf<List<NewsItem>>(emptyList()) }
    var newsLoading by remember(s.symbol) { mutableStateOf(false) }
    var historyLoading by remember(s.symbol) { mutableStateOf(true) }
    val lastMarketRefreshMs = MarketRefreshController.state.value.lastSuccessfulRefreshMs

    LaunchedEffect(s.symbol) {
        intelligence = CompanyIntelligenceCache.load(s.symbol)
    }
    LaunchedEffect(s.symbol) {
        newsLoading = true
        companyNews = NewsCache.loadCompanyNews(s.symbol).items
        newsLoading = false
    }

    LaunchedEffect(s.symbol) {
        while (true) {
            marketStatus = MyStocksCache.loadMarketStatus()
            kotlinx.coroutines.delay(60_000L)
        }
    }

    LaunchedEffect(s.symbol, lastMarketRefreshMs) {
        historyLoading = true
        historyResult = MyStocksCache.loadHistoryDetails(s.symbol, "1D")
        historyLoading = false
    }

    LaunchedEffect(s.symbol, lastMarketRefreshMs) {
        val ranges = listOf("1D", "3D", "1W", "1M", "3M", "6M", "1Y", "3Y", "5Y")
        chartLoading = true
        val loaded = kotlinx.coroutines.coroutineScope {
            ranges.map { range ->
                async {
                    range to if (range == "1D") {
                        MyStocksCache.loadHistoryDetails(s.symbol, "1D")
                    } else {
                        MyStocksCache.loadHistoryDetails(s.symbol, range.lowercase())
                    }
                }
            }.mapNotNull { deferred -> runCatching { deferred.await() }.getOrNull() }.toMap()
        }
        rangeResults = loaded
        chartResult = loaded[selectedChartRange] ?: MyStocksCache.HistoryResult()
        chartLoading = false
    }

    LaunchedEffect(selectedChartRange, rangeResults) {
        rangeResults[selectedChartRange]?.let { chartResult = it }
    }

    val profile = intelligence.profile
    val points = historyResult.points.filter { it.close.isFinite() && it.close > 0.0 }
    // Use the actual previous trading session close from the chart API first.
    // The quote feed can mirror today's close in its previousClose field, so it
    // must not be trusted as the UI's historical previous-close reference.
    val previousClose = historyResult.previousSessionClose?.takeIf { it.isFinite() && it > 0.0 }
        ?: s.previousClose?.takeIf { it.isFinite() && it > 0.0 }
    val latest = historyResult.sessionClose?.takeIf { it.isFinite() && it > 0.0 }
        ?: points.lastOrNull()?.close
        ?: s.price.takeIf { it.isFinite() && it > 0.0 }
    val open = historyResult.sessionOpen?.takeIf { it.isFinite() && it > 0.0 }
        ?: points.firstOrNull()?.close
    val dayHigh = points.maxOfOrNull { it.close }
    val dayLow = points.minOfOrNull { it.close }
    val observedAt = historyResult.sessionCloseAt.ifBlank {
        historyResult.observedAt
    }.ifBlank {
        points.lastOrNull()?.date.orEmpty()
    }

    // Today's change is authoritative provider data already normalized by
    // MyStocksCache from the API's changePct field. Do not recalculate it
    // from prices in the UI. If the API does not provide it, show it as unavailable.
    val dailyChange = historyResult.dailyChangePct?.takeIf { it.isFinite() }
        ?: s.change.takeIf { it.isFinite() }

    // "Since open today" is intentionally kept separate: it is a different
    // metric from the provider's close-to-close daily change.
    val sinceOpen = if (open != null && latest != null && open > 0.0) {
        ((latest - open) / open) * 100.0
    } else null

    // The approved reference is an overview layout. Keep the existing company
    // data path, but do not reintroduce the previous dense intelligence dashboard.
    // The other existing intelligence sections remain available in the codebase
    // for the next targeted redesign pass.
    MobileCompanyIntelligenceLayout(
        stock = s,
        back = back,
        profile = intelligence.profile,
        intelligence = intelligence,
        points = points,
        previousClose = previousClose,
        open = open,
        dayHigh = dayHigh,
        dayLow = dayLow,
        latest = latest,
        observedAt = observedAt,
        dailyChange = dailyChange,
        sinceOpen = sinceOpen,
        historyLoading = historyLoading,
        news = companyNews,
        newsLoading = newsLoading,
        chartPoints = chartResult.points.filter { it.close.isFinite() && it.close > 0.0 },
        chartPreviousClose = if (selectedChartRange == "1D") chartResult.previousSessionClose else null,
        chartLatest = chartResult.sessionClose?.takeIf { it.isFinite() && it > 0.0 }
            ?: chartResult.points.lastOrNull()?.close,
        chartLoading = chartLoading,
        selectedChartRange = selectedChartRange,
        onChartRangeSelected = { selectedChartRange = it },
        rangeReturns = rangeResults.mapValues { (range, result) ->
            periodReturnPct(range, result, if (range == "1D") dailyChange else null)
        }
    )
}


@Composable
private fun MobileCompanyIntelligenceLayout(
    stock: Stock,
    back: () -> Unit,
    profile: CompanyIntelligenceCache.Profile,
    intelligence: CompanyIntelligenceCache.Result,
    points: List<MyStocksCache.HistoryPoint>,
    previousClose: Double?,
    open: Double?,
    dayHigh: Double?,
    dayLow: Double?,
    latest: Double?,
    observedAt: String,
    dailyChange: Double?,
    sinceOpen: Double?,
    historyLoading: Boolean,
    news: List<NewsItem>,
    newsLoading: Boolean,
    chartPoints: List<MyStocksCache.HistoryPoint>,
    chartPreviousClose: Double?,
    chartLatest: Double?,
    chartLoading: Boolean,
    selectedChartRange: String,
    onChartRangeSelected: (String) -> Unit,
    rangeReturns: Map<String, Double?>
) {
    var selected by rememberSaveable(stock.symbol) { mutableStateOf("Overview") }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF061625))) {
        val phone = maxWidth < 600.dp
        val edge = if (phone) 12.dp else 28.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (phone) 12.dp else 18.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF071B2D))
                        .padding(horizontal = if (phone) 14.dp else 28.dp, vertical = if (phone) 8.dp else 16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = back, modifier = Modifier.size(42.dp)) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                        Text("Company Intelligence", Modifier.weight(1f), color = Color.White,
                            fontSize = if (phone) 20.sp else 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Box(Modifier.size(28.dp)) {
                            Icon(Icons.Default.NotificationsNone, "Notifications", tint = Color.White, modifier = Modifier.size(25.dp))
                            Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFF4650)))
                        }
                        Icon(Icons.Default.MoreVert, "More", tint = Color.White, modifier = Modifier.size(25.dp))
                    }
                    Spacer(Modifier.height(if (phone) 10.dp else 18.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CompanyLogo(stock.symbol, if (phone) 62 else 92, stock.logoUrl)
                        Spacer(Modifier.width(if (phone) 12.dp else 20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stock.name, color = Color.White, fontSize = if (phone) 20.sp else 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                            Text(stock.symbol + "  •  NSE", color = Color(0xFFA9BCD0), fontSize = if (phone) 12.sp else 16.sp)
                            Text(profile.sector.ifBlank { "Banking and Financial Services" }, color = Color(0xFFA9BCD0), fontSize = if (phone) 11.sp else 15.sp, maxLines = 2)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(latest?.let(::currencyLabel) ?: "Unavailable", color = Color.White, fontSize = if (phone) 19.sp else 25.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            dailyChange?.let {
                                Text(String.format(Locale.US, "%+.2f%%", it), color = if (it >= 0) Color(0xFF00D084) else Color(0xFFFF4D55), fontSize = if (phone) 16.sp else 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Text("vs previous close", color = Color(0xFFA9BCD0), fontSize = if (phone) 9.sp else 12.sp)
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .background(Color(0xFF071B2D))
                        .padding(horizontal = edge, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (phone) 7.dp else 16.dp)
                ) {
                    listOf("Overview", "Financials", "News", "Analysis", "About").forEach { tab ->
                        val active = selected == tab
                        Surface(
                            onClick = { selected = tab },
                            color = if (active) Color(0xFF43E51B) else Color.Transparent,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.height(if (phone) 40.dp else 48.dp)
                        ) {
                            Box(Modifier.padding(horizontal = if (active) 16.dp else 5.dp), contentAlignment = Alignment.Center) {
                                Text(tab, color = if (active) Color(0xFF061B10) else Color(0xFFA9BCD0),
                                    fontSize = if (phone) 13.sp else 16.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF12283B))
            }

            when (selected) {
                "Overview" -> {
                    item { MobileGlanceCard(previousClose, open, dayHigh, dayLow, latest, observedAt, dailyChange, sinceOpen, edge, phone) }
                    item {
                        MobileChartCard(
                            chartPoints = chartPoints,
                            previousClose = chartPreviousClose,
                            latest = chartLatest,
                            loading = chartLoading,
                            edge = edge,
                            phone = phone,
                            selectedRange = selectedChartRange,
                            onRangeSelected = onChartRangeSelected,
                            rangeReturns = rangeReturns
                        )
                    }
                    item {
                        Surface(
                            onClick = { },
                            modifier = Modifier.padding(horizontal = edge).fillMaxWidth(),
                            color = Color(0xFF0A1F32), shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color(0xFF17364F))
                        ) {
                            Row(Modifier.padding(if (phone) 16.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(if (phone) 44.dp else 54.dp), RoundedCornerShape(50), color = Color(0xFF102D28), border = BorderStroke(1.dp, Color(0xFF1E5B45))) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Psychology, null, tint = Color(0xFF00D084), modifier = Modifier.size(if (phone) 27.dp else 32.dp)) }
                                }
                                Spacer(Modifier.width(if (phone) 13.dp else 20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Company Intelligence", color = Color.White, fontSize = if (phone) 18.sp else 23.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("Financials, sourced evidence and key information about " + stock.name + ".", color = Color(0xFFA9BCD0), fontSize = if (phone) 11.sp else 14.sp, lineHeight = 17.sp)
                                }
                                Text("›", color = Color(0xFFA9BCD0), fontSize = 32.sp)
                            }
                        }
                    }
                }
                "Financials" -> item {
                    Surface(Modifier.padding(horizontal = edge).fillMaxWidth(), color = Color(0xFF0A1F32), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF17364F))) {
                        Column(Modifier.padding(if (phone) 15.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Financials", color = Color.White, fontSize = if (phone) 21.sp else 26.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Sourced financial information, ratios and dividends.", color = Color(0xFFA9BCD0), fontSize = 12.sp)
                            MetricGrid(
                                listOf(
                                    "Revenue" to profile.revenue.ifBlank { "Unavailable" },
                                    "Profit" to profile.profit.ifBlank { "Unavailable" },
                                    "EPS (Earnings Per Share)" to profile.eps.ifBlank { "Unavailable" },
                                    "ROE (Return on Equity)" to profile.roe.ifBlank { "Unavailable" },
                                    "Debt / Equity" to profile.debtToEquity.ifBlank { "Unavailable" },
                                    "Net margin" to profile.margin.ifBlank { "Unavailable" },
                                    "P/E (Price-to-Earnings)" to profile.pe.ifBlank { "Unavailable" },
                                    "P/B (Price-to-Book)" to profile.pb.ifBlank { "Unavailable" },
                                    "Dividend yield" to profile.dividendYield.ifBlank { "Unavailable" },
                                    "Market cap" to profile.marketCap.ifBlank { "Unavailable" }
                                ), intelligence.fieldSources, intelligence.fieldQuality
                            )
                            if (intelligence.dividends.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFF17364F))
                                Text("Dividends", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                intelligence.dividends.take(6).forEach { d ->
                                    Text(d.amount.ifBlank { "Amount unavailable" } + " • " + d.status.ifBlank { "Status unavailable" } + " • Ex-date " + d.exDate.ifBlank { "Unavailable" }, color = Color(0xFFA9BCD0), fontSize = 11.sp, lineHeight = 16.sp)
                                }
                            }
                            if (intelligence.financialHistory.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFF17364F))
                                Text("Financial history", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                intelligence.financialHistory.takeLast(6).forEach { f ->
                                    Text(f.period + " • Revenue " + f.revenue + " • Profit " + f.profit + " • EPS " + f.eps, color = Color(0xFFA9BCD0), fontSize = 10.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }
                }
                "News" -> item { CompanyNewsSection(news, newsLoading) }
                "Analysis" -> item {
                    Surface(Modifier.padding(horizontal = edge).fillMaxWidth(), color = Color(0xFF0A1F32), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF17364F))) {
                        Column(Modifier.padding(if (phone) 15.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Analysis", color = Color.White, fontSize = if (phone) 21.sp else 26.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Data-backed observations from the currently available company and market fields.", color = Color(0xFFA9BCD0), fontSize = 12.sp, lineHeight = 17.sp)
                            EvidenceRow("Latest", latest?.let(::currencyLabel) ?: "Unavailable")
                            EvidenceRow("Previous close", previousClose?.let(::currencyLabel) ?: "Unavailable")
                            EvidenceRow("Today's change", dailyChange?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "Unavailable")
                            EvidenceRow("Since open", sinceOpen?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "Unavailable")
                            EvidenceRow("P/E", profile.pe.ifBlank { "Unavailable" })
                            EvidenceRow("ROE", profile.roe.ifBlank { "Unavailable" })
                            EvidenceRow("Net margin", profile.margin.ifBlank { "Unavailable" })
                            HorizontalDivider(color = Color(0xFF17364F))
                            Text("Evidence", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (intelligence.evidence.isEmpty()) {
                                Text("No additional evidence records are available.", color = Color(0xFFA9BCD0), fontSize = 11.sp)
                            } else {
                                intelligence.evidence.take(8).forEach { e ->
                                    Text(e.claim.ifBlank { "Sourced claim" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text(e.value.ifBlank { "Value unavailable" } + " • " + e.source.ifBlank { "Source unavailable" }, color = Color(0xFFA9BCD0), fontSize = 10.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }
                }
                "About" -> item {
                    Surface(Modifier.padding(horizontal = edge).fillMaxWidth(), color = Color(0xFF0A1F32), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF17364F))) {
                        Column(Modifier.padding(if (phone) 15.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Text("About", color = Color.White, fontSize = if (phone) 21.sp else 26.sp, fontWeight = FontWeight.ExtraBold)
                            Text(stock.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(profile.description.ifBlank { "Company description is not available from the current source." }, color = Color(0xFFA9BCD0), fontSize = 12.sp, lineHeight = 18.sp)
                            EvidenceRow("Sector", profile.sector.ifBlank { "Unavailable" })
                            EvidenceRow("Headquarters", profile.headquarters.ifBlank { "Unavailable" })
                            EvidenceRow("Website", profile.website.ifBlank { "Unavailable" })
                            EvidenceRow("Financial period", profile.financialPeriod.ifBlank { "Unavailable" })
                            EvidenceRow("Source", intelligence.source)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileGlanceCard(
    previousClose: Double?, open: Double?, high: Double?, low: Double?, latest: Double?,
    observedAt: String, dailyChange: Double?, sinceOpen: Double?, edge: androidx.compose.ui.unit.Dp, phone: Boolean
) {
    Surface(Modifier.padding(horizontal = edge).fillMaxWidth(), color = Color(0xFF0A1F32), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF17364F))) {
        Column(Modifier.padding(if (phone) 15.dp else 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShowChart, null, tint = Color(0xFF19E7D0), modifier = Modifier.size(if (phone) 27.dp else 33.dp))
                Spacer(Modifier.width(10.dp))
                Text("Today at a glance", color = Color.White, fontSize = if (phone) 18.sp else 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(if (phone) 18.dp else 25.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (phone) 8.dp else 22.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (phone) 17.dp else 25.dp)) {
                    ApprovedMetric(Icons.Default.ShowChart, "Previous close", previousClose?.let(::currencyLabel) ?: "Unavailable", phone = phone)
                    ApprovedMetric(Icons.Default.ArrowUpward, "Day high", high?.let(::currencyLabel) ?: "Unavailable", phone = phone)
                    ApprovedMetric(Icons.Default.AccessTime, "Today's close", latest?.let(::currencyLabel) ?: "Unavailable", phone = phone)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (phone) 17.dp else 25.dp)) {
                    ApprovedMetric(Icons.Default.ShowChart, "Today's open", open?.let(::currencyLabel) ?: "Unavailable", phone = phone)
                    ApprovedMetric(Icons.Default.ArrowDownward, "Day low", low?.let(::currencyLabel) ?: "Unavailable", phone = phone)
                    ApprovedMetric(Icons.Default.AccessTime, "Observed at", approvedObservedTime(observedAt), phone = phone)
                }
            }
            Spacer(Modifier.height(if (phone) 15.dp else 22.dp))
            HorizontalDivider(color = Color(0xFF17364F))
            Spacer(Modifier.height(if (phone) 15.dp else 22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    ApprovedMovement(dailyChange?.let { it >= 0 }, "Today's change", dailyChange?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "Unavailable",
                        previousClose?.let { "vs previous close (" + currencyLabel(it) + ")" } ?: "vs previous close", phone = phone)
                }
                Box(Modifier.weight(1f)) {
                    ApprovedMovement(sinceOpen?.let { it >= 0 }, "Since open today", sinceOpen?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "Unavailable",
                        if (open != null && latest != null) "(" + currencyLabel(open) + " → " + currencyLabel(latest) + ")" else "Open-to-latest unavailable", phone = phone)
                }
            }
        }
    }
}

@Composable
private fun MobileChartCard(
    chartPoints: List<MyStocksCache.HistoryPoint>,
    previousClose: Double?,
    latest: Double?,
    loading: Boolean,
    edge: androidx.compose.ui.unit.Dp,
    phone: Boolean,
    selectedRange: String,
    onRangeSelected: (String) -> Unit,
    rangeReturns: Map<String, Double?>
) {
    Surface(
        Modifier.padding(horizontal = edge).fillMaxWidth(),
        color = Color(0xFF0A1F32),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF17364F))
    ) {
        Column(Modifier.padding(if (phone) 15.dp else 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShowChart, null, tint = Color(0xFF19E7D0), modifier = Modifier.size(if (phone) 27.dp else 31.dp))
                Spacer(Modifier.width(9.dp))
                Text("1D Intraday Chart", Modifier.weight(1f), color = Color.White, fontSize = if (phone) 19.sp else 25.sp, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(Color(0xFF00D084)))
            }
            Text(
                if (selectedRange == "1D") "NSE session 09:30 – 15:00 EAT"
                else "Historical NSE price movement • available exchange observations",
                color = Color(0xFFA9BCD0),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 36.dp)
            )
            Spacer(Modifier.height(10.dp))
            if (loading && chartPoints.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(if (phone) 220.dp else 300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00D084), strokeWidth = 2.dp)
                }
            } else if (chartPoints.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(if (phone) 220.dp else 300.dp), contentAlignment = Alignment.Center) {
                    Text("Chart data unavailable for this period.", color = Color(0xFFA9BCD0), fontSize = 12.sp)
                }
            } else if (selectedRange == "1D") {
                ApprovedIntradayCanvas(chartPoints, previousClose, latest, Modifier.fillMaxWidth().height(if (phone) 230.dp else 320.dp))
            } else {
                HistoricalRangeCanvas(chartPoints, Modifier.fillMaxWidth().height(if (phone) 230.dp else 320.dp))
            }

            Spacer(Modifier.height(10.dp))
            RangePerformanceStrip(rangeReturns, selectedRange, onRangeSelected, phone)

            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf("1D", "3D", "1W", "1M", "3M", "6M", "1Y", "3Y", "5Y").forEach { range ->
                    val active = selectedRange == range
                    Surface(
                        onClick = { onRangeSelected(range) },
                        color = if (active) Color(0xFF00D084) else Color(0xFF10283D),
                        contentColor = if (active) Color(0xFF062018) else Color(0xFFA9BCD0),
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, if (active) Color(0xFF00D084) else Color(0xFF17364F))
                    ) {
                        Text(
                            range,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                Modifier.fillMaxWidth(),
                color = Color(0xFF10283D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF17364F))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFAFC2F0), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (selectedRange == "1D")
                            "Today's chart uses actual NSE observations. Previous close is a reference line, not a fabricated intraday observation."
                        else
                            "Historical charts use the actual candles returned for the selected period. Missing observations are not interpolated.",
                        color = Color(0xFFA9BCD0),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}


private fun periodReturnPct(
    range: String,
    result: MyStocksCache.HistoryResult,
    oneDayFallback: Double?
): Double? {
    if (range == "1D") {
        return result.dailyChangePct?.takeIf { it.isFinite() } ?: oneDayFallback
    }
    val first = result.points.firstOrNull()?.close
    val last = result.points.lastOrNull()?.close
    if (first == null || last == null || !first.isFinite() || !last.isFinite() || first <= 0.0) return null
    return ((last - first) / first) * 100.0
}

@Composable
private fun RangePerformanceStrip(
    rangeReturns: Map<String, Double?>,
    selectedRange: String,
    onRangeSelected: (String) -> Unit,
    phone: Boolean
) {
    val ranges = listOf("1D", "3D", "1W", "1M", "3M", "6M", "1Y", "3Y", "5Y")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (phone) 14.dp else 18.dp)
    ) {
        ranges.forEach { range ->
            val value = rangeReturns[range]
            val tint = when {
                value == null -> Color(0xFF8FA4B8)
                value >= 0.0 -> Color(0xFF00D084)
                else -> Color(0xFFFF5C5C)
            }
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedRange == range) Color(0xFF10283D) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .clickable { onRangeSelected(range) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    value?.let { String.format(Locale.US, "%+.2f%%", it) } ?: "—",
                    color = tint,
                    fontSize = if (phone) 12.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(range, color = Color(0xFFA9BCD0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoricalRangeCanvas(
    points: List<MyStocksCache.HistoryPoint>,
    modifier: Modifier
) {
    Canvas(modifier) {
        val left = 46f
        val right = 12f
        val top = 18f
        val bottom = 32f
        val plotWidth = max(1f, size.width - left - right)
        val plotHeight = max(1f, size.height - top - bottom)
        val values = points.map { it.close }.filter { it.isFinite() && it > 0.0 }
        if (values.size < 2) return@Canvas

        val minValue = values.minOrNull() ?: return@Canvas
        val maxValue = values.maxOrNull() ?: return@Canvas
        val pad = max(0.05, (maxValue - minValue) * 0.08)
        val yMin = minValue - pad
        val yMax = maxValue + pad
        val range = max(0.0001, yMax - yMin)

        fun xAt(index: Int): Float =
            left + (index.toFloat() / max(1, points.lastIndex)) * plotWidth

        fun yAt(value: Double): Float =
            top + plotHeight - (((value - yMin) / range).toFloat() * plotHeight)

        repeat(5) { i ->
            val y = top + plotHeight * i / 4f
            drawLine(
                Color(0xFF17364F),
                androidx.compose.ui.geometry.Offset(left, y),
                androidx.compose.ui.geometry.Offset(left + plotWidth, y),
                strokeWidth = 1f
            )
        }

        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val current = points[i]
            val movementColor = when {
                current.close > previous.close -> Color(0xFF00D084)
                current.close < previous.close -> Color(0xFFFF5C5C)
                else -> Color(0xFF9FB3C8)
            }
            drawLine(
                movementColor,
                androidx.compose.ui.geometry.Offset(xAt(i - 1), yAt(previous.close)),
                androidx.compose.ui.geometry.Offset(xAt(i), yAt(current.close)),
                strokeWidth = 3.6f,
                cap = StrokeCap.Round
            )
        }

        val scale = (0 until 5).map { i -> yMax - (yMax - yMin) * i / 4 }
        scale.forEachIndexed { i, value ->
            val y = top + plotHeight * i / 4f
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.0f", value),
                left - 7f,
                y + 4f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.rgb(169,188,208)
                    textSize = 11f
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        val labelIndexes = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
        labelIndexes.forEach { index ->
            val raw = points[index].date
            val label = runCatching {
                Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi"))
                    .format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))
            }.getOrElse { raw.take(10) }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                xAt(index),
                size.height - 8f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.rgb(169,188,208)
                    textSize = 10f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }
    }
}

@Composable
private fun SignalGroup(title: String, tint: Color, items: List<String>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(title, color = tint, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        if (items.isEmpty()) Text("No signal from the currently available fields.", color = IntelligenceMuted, fontSize = 9.sp)
        items.take(4).forEach { Text("• $it", color = IntelligenceText, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun EvidencePerspective(title: String, tint: Color, items: List<String>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = tint, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        if (items.isEmpty()) Text("No sourced point available yet.", color = IntelligenceMuted, fontSize = 9.sp)
        items.take(4).forEach { Text("• $it", color = IntelligenceText, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun IntelligenceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F32)), border = BorderStroke(1.dp, Color(0xFF17364F))) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), Color(0xFF10283D), border = BorderStroke(1.dp, Color(0xFF17364F))) { Icon(icon, null, tint = IntelligenceGreen, modifier = Modifier.padding(7.dp)) }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = IntelligenceText)
            Text(subtitle, color = IntelligenceMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MetricGrid(
    metrics: List<Pair<String, String>>,
    fieldSources: Map<String, List<String>> = emptyMap(),
    fieldQuality: Map<String, String> = emptyMap()
) {
    val sourceKeys = mapOf(
        "Revenue" to "revenue",
        "Revenue growth (YoY)" to "revenueGrowth",
        "Profit" to "profit",
        "Profit growth (YoY)" to "profitGrowth",
        "EPS (Earnings Per Share)" to "eps",
        "EPS growth (YoY)" to "epsGrowth",
        "ROE (Return on Equity)" to "roe",
        "Debt / Equity" to "debtToEquity",
        "Net margin" to "margin",
        "P/E (Price-to-Earnings)" to "pe",
        "P/B (Price-to-Book)" to "pb",
        "Dividend yield" to "dividendYield",
        "Market cap" to "marketCap"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    val key = sourceKeys[label].orEmpty()
                    val sources = fieldSources[key].orEmpty()
                    val quality = fieldQuality[key].orEmpty()
                    val provenance = when {
                        quality == "CONFLICT" -> "Sources differ"
                        sources.size > 1 -> "2 sources"
                        sources.size == 1 -> sources.first().removePrefix("StockAnalysis / ").removeSuffix(" Market Intelligence")
                        else -> "Source unavailable"
                    }
                    Surface(Modifier.weight(1f), RoundedCornerShape(13.dp), color = Color(0xFF10283D), border = BorderStroke(1.dp, Color(0xFF17364F))) {
                        Column(Modifier.padding(10.dp)) {
                            Text(label, color = Color(0xFFA9BCD0), fontSize = 8.sp)
                            Text(value, color = Color(0xFFF4F7FA), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF17364F)
                            ) {
                                Text(
                                    provenance,
                                    color = if (quality == "CONFLICT") IntelligenceRed else Color(0xFFA9BCD0),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniFact(label: String, value: String) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), color = Color(0xFF10283D), border = BorderStroke(1.dp, Color(0xFF17364F))) {
        Column(Modifier.padding(9.dp)) { Text(label, color = IntelligenceMuted, fontSize = 8.sp); Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
    }
}

@Composable
private fun Watchpoint(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFD58A00), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 10.sp, lineHeight = 15.sp, color = IntelligenceText)
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(92.dp), color = IntelligenceMuted, fontSize = 9.sp)
        Text(value, Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = IntelligenceText)
    }
}

@Composable
private fun IntelligenceLoader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(40.dp), RoundedCornerShape(12.dp), IntelligenceLight) { Icon(Icons.Default.AutoGraph, null, tint = IntelligenceGreen, modifier = Modifier.padding(9.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(subtitle, color = IntelligenceMuted, fontSize = 9.sp)
        }
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = IntelligenceGreen)
    }
}

@Composable
private fun IntelligenceChart(
    points: List<MyStocksCache.HistoryPoint>,
    period: String,
    tint: Color,
    previousClose: Double? = null
) {
    // Plot only actual exchange observations. Previous close is a reference level,
    // not a synthetic price observation at today's open.
    val valid = points.filter { it.close.isFinite() && it.close > 0.0 }
    if (valid.size < 2) return

    val referenceClose = previousClose?.takeIf { it.isFinite() && it > 0.0 }
    val minPrice = listOfNotNull(valid.minOfOrNull { it.close }, referenceClose).minOrNull() ?: return
    val maxPrice = listOfNotNull(valid.maxOfOrNull { it.close }, referenceClose).maxOrNull() ?: return
    val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: (maxPrice * 0.01).coerceAtLeast(1.0)
    val top = maxPrice + range * 0.08
    val bottom = (minPrice - range * 0.08).coerceAtLeast(0.0)
    val chartRange = (top - bottom).coerceAtLeast(0.0001)
    val mid = (top + bottom) / 2.0

    var zoomX by remember(valid, period) { mutableFloatStateOf(1f) }
    var panX by remember(valid, period) { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val plotWidthPx = with(density) { (maxWidth - 42.dp).coerceAtLeast(0.dp).toPx() }

        LaunchedEffect(zoomX, plotWidthPx) {
            val maxPan = (plotWidthPx * (zoomX - 1f)).coerceAtLeast(0f)
            panX = panX.coerceIn(-maxPan, 0f)
        }

        val labels = chartAxisLabels(valid, period, zoomX)

        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(218.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.width(42.dp).fillMaxHeight().padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(priceAxis(top), color = IntelligenceMuted, fontSize = 8.sp, maxLines = 1)
                    Text(priceAxis(mid), color = IntelligenceMuted, fontSize = 8.sp, maxLines = 1)
                    Text(priceAxis(bottom), color = IntelligenceMuted, fontSize = 8.sp, maxLines = 1)
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .pointerInput(valid, period) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldZoom = zoomX
                                val newZoom = (oldZoom * zoom).coerceIn(1f, 6f)
                                val scaleRatio = newZoom / oldZoom

                                // Keep the data point under the pinch centre anchored while zooming.
                                val anchoredPan = centroid.x - (centroid.x - panX) * scaleRatio
                                val candidatePan = anchoredPan + pan.x
                                val maxPan = (plotWidthPx * (newZoom - 1f)).coerceAtLeast(0f)

                                zoomX = newZoom
                                panX = candidatePan.coerceIn(-maxPan, 0f)
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val contentWidth = size.width * zoomX

                        // Keep the chart horizontally interactive while the price axis stays fixed.
                        clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                            val line = Path()
                            val area = Path()

                            valid.forEachIndexed { index, point ->
                                val x = panX + contentWidth * index / valid.lastIndex.coerceAtLeast(1)
                                val y = size.height - (((point.close - bottom) / chartRange).toFloat() * size.height)

                                if (index == 0) {
                                    line.moveTo(x, y)
                                    area.moveTo(x, size.height)
                                    area.lineTo(x, y)
                                } else {
                                    line.lineTo(x, y)
                                    area.lineTo(x, y)
                                }
                            }

                            area.lineTo(panX + contentWidth, size.height)
                            area.close()

                            drawPath(
                                area,
                                Brush.verticalGradient(
                                    listOf(tint.copy(alpha = 0.34f), tint.copy(alpha = 0.03f)),
                                    startY = 0f,
                                    endY = size.height
                                )
                            )
                            drawPath(line, tint, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

                            referenceClose?.let { close ->
                                val y = size.height - (((close - bottom) / chartRange).toFloat() * size.height)
                                drawLine(
                                    color = IntelligenceMuted.copy(alpha = 0.65f),
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1.5f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                )
                            }
                        }
                    }
                }
            }

            // The X-axis uses the same transformed positions as the line, so labels stay
            // attached to their actual observations while the user pans/zooms.
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(42.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clipToBounds()
                ) {
                    labels.forEach { label ->
                        val xPx = panX + plotWidthPx * zoomX * label.index / valid.lastIndex.coerceAtLeast(1)
                        val labelWidthPx = with(density) {
                            (label.text.length.coerceAtLeast(3) * 4.5f).dp.toPx()
                        }
                        val clampedX = when {
                            label.index == 0 -> xPx.coerceAtLeast(labelWidthPx / 2f)
                            label.index == valid.lastIndex -> xPx.coerceAtMost(plotWidthPx - labelWidthPx / 2f)
                            else -> xPx
                        }
                        Text(
                            label.text,
                            color = IntelligenceMuted,
                            fontSize = 8.sp,
                            maxLines = 1,
                            modifier = Modifier.offset {
                                androidx.compose.ui.unit.IntOffset(
                                    (clampedX - labelWidthPx / 2f).toInt(),
                                    0
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(42.dp))
                Box(Modifier.size(18.dp, 1.dp).background(IntelligenceMuted))
                Spacer(Modifier.width(5.dp))
                Text("Previous close", color = IntelligenceMuted, fontSize = 7.sp)
                Spacer(Modifier.width(10.dp))
                Text("• Actual NSE observations", color = IntelligenceMuted, fontSize = 7.sp)
            }

            Spacer(Modifier.height(3.dp))
            Text(
                chartAxisDescription(period),
                color = IntelligenceMuted,
                fontSize = 8.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            if (zoomX > 1.01f) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "Pinch to zoom • drag horizontally to inspect the timeline",
                    color = IntelligenceMuted,
                    fontSize = 7.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatHeaderUnavailable(
    marketStatus: MyStocksCache.MarketStatus,
    historyResult: MyStocksCache.HistoryResult
): String {
    val timestamp = historyResult.sessionCloseAt
        .ifBlank { historyResult.lastDate }
        .ifBlank { historyResult.observedAt }
        .takeIf { it.isNotBlank() }
        ?.let(::formatCompactChartTimestamp)

    val state = when {
        !marketStatus.isKnown -> "STATUS UNKNOWN"
        marketStatus.isOpen -> "LATEST"
        else -> "CLOSE"
    }

    return buildString {
        append("Change unavailable")
        timestamp?.let {
            append(" • ")
            append(it)
            append(" EAT")
        }
        append(" • ")
        append(state)
    }
}

private fun formatHeaderChange(
    change: Double,
    marketStatus: MyStocksCache.MarketStatus,
    historyResult: MyStocksCache.HistoryResult
): String {
    if (marketStatus.isKnown && !marketStatus.isOpen) {
        val closedAt = historyResult.sessionCloseAt
            .ifBlank { historyResult.lastDate }
            .ifBlank { historyResult.observedAt }
            .takeIf { it.isNotBlank() }
            ?.let(::formatCompactChartTimestamp)
        return if (closedAt != null) {
            "MARKET CLOSED • Latest available observation ${closedAt} EAT"
        } else {
            "MARKET CLOSED"
        }
    }

    val timestamp = historyResult.sessionCloseAt
        .ifBlank { historyResult.lastDate }
        .ifBlank { historyResult.observedAt }
        .takeIf { it.isNotBlank() }
        ?.let(::formatCompactChartTimestamp)

    val state = if (!marketStatus.isKnown) "STATUS UNKNOWN" else "LATEST"

    return buildString {
        append(String.format(Locale.US, "%+.2f%%", change))
        timestamp?.let {
            append(" • ")
            append(it)
            append(" EAT")
        }
        append(" • ")
        append(state)
    }
}

@Composable
private fun TodayAtAGlance(
    stock: Stock,
    historyResult: MyStocksCache.HistoryResult,
    marketStatus: MyStocksCache.MarketStatus
) {
    val open = historyResult.sessionOpen
    val latest = historyResult.sessionClose
    val actualPoints = historyResult.points.filter { it.close.isFinite() && it.close > 0.0 }
    val dayHigh = actualPoints.maxOfOrNull { it.close }
    val dayLow = actualPoints.minOfOrNull { it.close }
    val hasSession = latest != null && latest > 0.0
    val observedRaw = historyResult.sessionCloseAt
        .takeIf { it.isNotBlank() }
        ?: historyResult.observedAt.takeIf { it.isNotBlank() }
    val observed = observedRaw?.let(::formatChartTimestamp)
    val sessionDate = observedRaw?.let(::formatChartTimestampDate)
    val nextOpen = marketStatus.nextOpen.takeIf { it.isNotBlank() }?.let(::formatChartTimestamp)
    val known = marketStatus.isKnown
    val openSession = known && marketStatus.isOpen

    SectionTitle(
        "Today at a glance",
        when {
            openSession -> "NSE session • latest available data"
            known -> "NSE session • latest available observation"
            else -> "NSE session status is currently unavailable"
        },
        Icons.Default.Schedule
    )

    IntelligenceCard {
        when {
            !known -> {
                Text(
                    "Market status is currently unavailable. No current-session state is inferred.",
                    color = IntelligenceMuted,
                    fontSize = 10.sp
                )
            }
            !hasSession -> {
                Text(
                    "Today's intraday price summary is not available from the current market feed.",
                    color = IntelligenceMuted,
                    fontSize = 10.sp
                )
                nextOpen?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Next regular session: " + it, color = IntelligenceMuted, fontSize = 8.sp)
                }
            }
            else -> {
                val intradayMove = if (open != null && open > 0.0 && latest != null) {
                    ((latest - open) / open) * 100.0
                } else null

                val dailyMove = stock.change.takeIf {
                    stock.changeAvailable && it.isFinite() && stock.dataOrigin == "backend"
                } ?: stock.previousClose?.takeIf { it > 0.0 }?.let { previous ->
                    latest?.takeIf { it > 0.0 }?.let { current ->
                        ((current - previous) / previous) * 100.0
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stock.previousClose?.takeIf { it > 0.0 }?.let {
                        Box(Modifier.weight(1f)) { MiniFact("PREVIOUS CLOSE", currencyLabel(it)) }
                    }
                    open?.takeIf { it > 0.0 }?.let {
                        Box(Modifier.weight(1f)) { MiniFact("TODAY'S OPEN", currencyLabel(it)) }
                    }
                    dayHigh?.let {
                        Box(Modifier.weight(1f)) { MiniFact("DAY HIGH", currencyLabel(it)) }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dayLow?.let {
                        Box(Modifier.weight(1f)) { MiniFact("DAY LOW", currencyLabel(it)) }
                    }
                    latest?.takeIf { it > 0.0 }?.let {
                        Box(Modifier.weight(1f)) { MiniFact("LATEST OBSERVATION", currencyLabel(it)) }
                    }
                    observed?.let {
                        Box(Modifier.weight(1f)) { MiniFact("OBSERVED AT", it.removeSuffix(" EAT")) }
                    }
                }

                Spacer(Modifier.height(9.dp))

                if (!openSession) {
                    Text(
                        "MARKET CLOSED",
                        color = IntelligenceText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    sessionDate?.let {
                        Text("Today's NSE session • " + it, color = IntelligenceMuted, fontSize = 8.sp)
                    }
                }

                dailyMove?.let {
                    Text(
                        "Today's change " + String.format(Locale.US, "%+.2f%%", it) + " vs previous close",
                        color = if (it >= 0.0) IntelligenceGreen else IntelligenceRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                intradayMove?.let {
                    Text(
                        "Since open " + String.format(Locale.US, "%+.2f%%", it),
                        color = if (it >= 0.0) IntelligenceGreen else IntelligenceRed,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                observed?.let {
                    Text(
                        "Latest observation: " + it + " • exchange-supplied • 15 min delayed",
                        color = IntelligenceMuted,
                        fontSize = 8.sp
                    )
                }

                if (!openSession) {
                    Text(
                        "The feed is showing the latest available observation; it is not labelled as a final close unless the source confirms one.",
                        color = IntelligenceMuted,
                        fontSize = 8.sp,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatChartTimestamp(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("dd MMM yy • h:mm a", Locale.US)) + " EAT"
}.getOrElse { raw.take(19) }

private fun formatChartTimestampDate(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("EEE, dd MMM yy", Locale.US))
}.getOrElse { raw.take(10) }

private fun formatCompactChartTimestamp(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("dd MMM yy • h:mm a", Locale.US))
}.getOrElse { raw.take(19) }

private data class ChartLabel(val index: Int, val text: String)

private fun chartAxisLabels(
    points: List<MyStocksCache.HistoryPoint>,
    period: String,
    zoomX: Float = 1f
): List<ChartLabel> {
    val dated = points.mapIndexedNotNull { index, point ->
        parseChartDate(point.date)?.let { ChartLabel(index, formatChartDate(it, period)) }
    }
    if (dated.size < 2) return fallbackChartLabels(period).mapIndexed { index, text -> ChartLabel(index, text) }

    if (period == "1D") {
        // Use the provider's real observation timestamps. The description below
        // supplies the NSE session window (09:30–15:00 EAT) without inventing
        // observations at the session boundaries.
        val uniqueTimes = dated.distinctBy { it.text }
        if (uniqueTimes.size >= 2) {
            return evenlySpacedLabels(uniqueTimes, uniqueTimes.size.coerceAtMost(6))
        }
    }

    val baseCount = when (period) {
        "1D" -> 5
        "1W" -> 5
        "1M" -> 5
        "3M", "6M" -> 4
        "1Y" -> 6
        "3Y", "5Y" -> 4
        else -> 5
    }

    val unique = dated.distinctBy { it.text }
    if (unique.size < 2) {
        return listOf(
            dated.firstOrNull() ?: ChartLabel(0, fallbackChartLabels(period).first()),
            dated.lastOrNull() ?: ChartLabel(points.lastIndex, fallbackChartLabels(period).last())
        ).distinctBy { it.index }
    }
    val maxLabelCount = unique.size.coerceAtMost(12)
    val targetCount = (baseCount * zoomX).toInt().coerceIn(2, maxLabelCount)
    return evenlySpacedLabels(unique, targetCount)
}

private fun evenlySpacedLabels(labels: List<ChartLabel>, count: Int): List<ChartLabel> {
    if (labels.size <= 1) return labels
    if (count >= labels.size) return labels
    return (0 until count).map { i ->
        val index = kotlin.math.round(
            i * (labels.lastIndex.toDouble() / (count - 1).coerceAtLeast(1))
        ).toInt()
        labels[index]
    }.distinctBy { it.text }
}

private fun parseChartDate(raw: String): java.time.LocalDateTime? {
    val value = raw.trim()
    if (value.isBlank()) return null

    return runCatching {
        Instant.parse(value).atZone(ZoneId.of("Africa/Nairobi")).toLocalDateTime()
    }.getOrElse {
        runCatching { LocalDate.parse(value).atStartOfDay() }.getOrNull()
    }
}

private fun formatChartDate(
    dateTime: java.time.LocalDateTime,
    period: String
): String = when (period) {
    "1D" -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    "1W" -> dateTime.format(DateTimeFormatter.ofPattern("EEE dd", Locale.US))
    "1M" -> dateTime.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))
    "3M", "6M", "1Y" -> dateTime.format(DateTimeFormatter.ofPattern("MMM", Locale.US))
    "3Y", "5Y" -> dateTime.format(DateTimeFormatter.ofPattern("yyyy", Locale.US))
    else -> dateTime.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US))
}

private fun chartAxisDescription(period: String): String = when (period) {
    "1D" -> "Nairobi NSE session • 09:30–15:00 EAT • plotted points are exchange observations"
    "1W" -> "Trading days"
    "1M" -> "Trading dates"
    "3M", "6M" -> "Months across the selected period"
    "1Y" -> "Months across the selected year"
    "3Y", "5Y" -> "Years across the selected period"
    else -> "Time"
}

private fun fallbackChartLabels(period: String): List<String> = when (period) {
    "1D" -> listOf("Open", "Mid", "Latest")
    "1W" -> listOf("Start", "Mid", "Now")
    "1M" -> listOf("Start", "Mid", "Now")
    "3M", "6M" -> listOf("Start", "Mid", "Now")
    "1Y" -> listOf("Start", "Mid", "Now")
    "3Y", "5Y" -> listOf("Start", "Mid", "Now")
    else -> listOf("Start", "Now")
}

@Composable
private fun CompanyLogo(symbol: String, size: Int, logoUrl: String? = null) {
    val model = logoUrl?.takeIf { it.isNotBlank() } ?: "https://mystocks.africa/logos/${symbol.lowercase(Locale.US)}-ke.svg"
    Surface(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)), RoundedCornerShape(12.dp), IntelligenceLight) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            AsyncImage(model = model, contentDescription = symbol, modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            Text(symbol.take(3), color = IntelligenceGreen, fontWeight = FontWeight.ExtraBold, fontSize = 7.sp)
        }
    }
}

@Composable
private fun BeginnerMetricGuide(
    title: String,
    items: List<Pair<String, String>>
) {
    Surface(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(12.dp),
        color = IntelligenceLight,
        border = BorderStroke(1.dp, IntelligenceBorder)
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = IntelligenceGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(title, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceText)
            }
            Spacer(Modifier.height(5.dp))
            items.forEachIndexed { index, (term, explanation) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(term, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IntelligenceGreen)
                    Text(explanation, fontSize = 8.sp, lineHeight = 12.sp, color = IntelligenceText)
                }
                if (index < items.lastIndex) HorizontalDivider(color = IntelligenceBorder)
            }
        }
    }
}

private fun currencyLabel(value: Double): String = String.format(Locale.US, "KSh %.2f", value)

private fun priceAxis(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun valueOrMissing(value: String): String = value.trim().takeIf { it.isNotBlank() } ?: "Not available"

private fun formatFinancialValue(value: String, unit: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "Not available"
    if (!unit.equals("Millions KES", ignoreCase = true)) {
        return clean
    }
    val millions = clean.replace(",", "").toDoubleOrNull() ?: return clean
    return if (millions >= 1000.0) {
        String.format(Locale.US, "KSh %.2fB", millions / 1000.0)
    } else {
        String.format(Locale.US, "KSh %,.2fM", millions)
    }
}

private fun formatMetricValue(label: String, value: String): String {
    val clean = value.trim()
    if (clean.isBlank() || clean == "-" || clean.equals("n/a", ignoreCase = true)) return "Not available"
    val numeric = clean.removeSuffix("%").replace(",", "").toDoubleOrNull() ?: return clean
    return when (label) {
        "EPS", "EPS (Earnings Per Share)" -> String.format(Locale.US, "KSh %.2f / share", numeric)
        "Revenue growth", "Profit growth", "EPS growth" ->
            String.format(Locale.US, "%+.2f%%", numeric)
        "ROE", "Net margin", "Dividend yield" ->
            String.format(Locale.US, "%.2f%%", numeric)
        "P/E", "P/B", "Debt / Equity" -> String.format(Locale.US, "%.2f×", numeric)
        else -> clean
    }
}

private fun formatMarketCap(value: String): String {
    val clean = value.trim()
    if (clean.isBlank()) return "Not available"
    val absoluteKsh = clean.replace(",", "").toDoubleOrNull() ?: return clean
    return when {
        absoluteKsh >= 1_000_000_000_000.0 -> String.format(Locale.US, "KSh %.2fT", absoluteKsh / 1_000_000_000_000.0)
        absoluteKsh >= 1_000_000_000.0 -> String.format(Locale.US, "KSh %.2fB", absoluteKsh / 1_000_000_000.0)
        absoluteKsh >= 1_000_000.0 -> String.format(Locale.US, "KSh %.2fM", absoluteKsh / 1_000_000.0)
        else -> String.format(Locale.US, "KSh %,.0f", absoluteKsh)
    }
}

@Composable
private fun SourceDateLine(label: String, providerUpdatedAt: String, pageCheckedAt: String) {
    if (providerUpdatedAt.isBlank() && pageCheckedAt.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        providerUpdatedAt.takeIf { it.isNotBlank() }?.let {
            Text("$label: ${formatSourceDate(it)}", color = IntelligenceMuted, fontSize = 8.sp)
        }
        pageCheckedAt.takeIf { it.isNotBlank() }?.let {
            Text("Source page checked: ${formatSourceDate(it)}", color = IntelligenceMuted, fontSize = 8.sp)
        }
    }
}

private fun formatSourceDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
}.getOrElse { raw }

private fun financialPeriodLabel(period: String): String {
    val clean = period.trim()
    if (clean.isBlank()) return "Annual figures • Latest reported period"
    if (clean.contains("FY ", ignoreCase = true)) {
        val parts = clean.split(" • ", limit = 2)
        return if (parts.size == 2) {
            "Annual figures • ${parts[0]} • year ended ${parts[1]}"
        } else {
            "Annual figures • ${parts[0]}"
        }
    }
    return "Annual figures • $clean"
}

private fun periodDescription(period: String): String = when (period) {
    "1D" -> "Today's Nairobi trading session"
    "1W" -> "Past 1 week"
    "1M" -> "Past 1 month"
    "3M" -> "Past 3 months"
    "6M" -> "Past 6 months"
    "1Y" -> "Past 1 year"
    "3Y" -> "Past 3 years"
    "5Y" -> "Past 5 years"
    "NOW" -> "Latest available intraday data"
    else -> period
}

private fun formatPeriodReturn(period: String, value: Double): String {
    val label = when (period) {
        "1D" -> "session"
        "1W" -> "1 week"
        "1M" -> "1 month"
        "3M" -> "3 months"
        "6M" -> "6 months"
        "1Y" -> "1 year"
        "3Y" -> "3 years"
        "5Y" -> "5 years"
        "NOW" -> "latest session"
        else -> period
    }
    return String.format(Locale.US, "%+.2f%% $label", value)
}

private fun percentReturn(values: List<Double>): Double? {
    val valid = values.filter { it.isFinite() && it > 0.0 }
    if (valid.size < 2) return null
    val first = valid.first()
    return ((valid.last() - first) / first) * 100.0
}

private fun formatSigned(value: Double): String = String.format(Locale.US, "%+.1f%%", value)