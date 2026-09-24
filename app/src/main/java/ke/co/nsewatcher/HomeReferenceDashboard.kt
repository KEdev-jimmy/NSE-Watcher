package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.MyStocksCache
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HomeReferenceColors = darkColorScheme(
    primary = Color(0xFF00E59B),
    onPrimary = Color(0xFF03141F),
    primaryContainer = Color(0xFF0D3B31),
    onPrimaryContainer = Color(0xFFD2FFEC),
    background = Color(0xFF041827),
    onBackground = Color(0xFFF5F8FC),
    surface = Color(0xFF08243A),
    onSurface = Color(0xFFF5F8FC),
    surfaceVariant = Color(0xFF0C2D46),
    onSurfaceVariant = Color(0xFFA9BDD0),
    outline = Color(0xFF123E5B),
    error = Color(0xFFFF5E72),
    onError = Color(0xFF3B0710),
    tertiary = Color(0xFF9A68F7),
    onTertiary = Color.White
)

@Composable
internal fun HomeReferenceDashboard(
    name: String,
    avatar: String?,
    market: MyStocksCache.MarketStatus,
    stocks: List<Stock>,
    now: Instant,
    refreshing: Boolean,
    hasAttention: Boolean,
    watched: List<Stock>,
    watchlistPreview: List<Stock>,
    watchlistLoading: Boolean,
    watchlistError: Boolean,
    relevantNews: List<NewsItem>,
    newsLoading: Boolean,
    newsError: Boolean,
    briefItems: List<HomeBriefItem>,
    briefLoading: Boolean,
    briefHasError: Boolean,
    intelligence: HomeIntelligenceSnapshot,
    gainersSelected: Boolean,
    onGainersSelected: (Boolean) -> Unit,
    practiceEnabled: Boolean,
    practiceCash: Double,
    onRefresh: () -> Unit,
    openAlerts: () -> Unit,
    openProfile: () -> Unit,
    openWatchlist: () -> Unit,
    openPractice: () -> Unit,
    openMarket: () -> Unit,
    openAllNews: () -> Unit,
    openNews: (NewsItem) -> Unit,
    openCompany: (Stock) -> Unit,
    openBriefItem: (HomeBriefItem) -> Unit,
    reviewBrief: () -> Unit
) {
    val strongestSector = intelligence.sectors.maxByOrNull { it.averageChangePct }
    val topMover = (intelligence.gainers + intelligence.losers).maxByOrNull { kotlin.math.abs(it.change) }
    val breadth = intelligence.breadth

    MaterialTheme(colorScheme = HomeReferenceColors) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ReferenceHomeHeader(
                        name = name,
                        avatar = avatar,
                        market = market,
                        stocks = stocks,
                        now = now,
                        refreshing = refreshing,
                        hasAttention = hasAttention,
                        onRefresh = onRefresh,
                        openAlerts = openAlerts,
                        openProfile = openProfile
                    )
                }

                item {
                    ReferenceDailyBrief(
                        sector = strongestSector,
                        topMover = topMover,
                        breadth = breadth,
                        briefItems = briefItems,
                        briefLoading = briefLoading,
                        briefHasError = briefHasError,
                        hasWatchlist = watched.isNotEmpty(),
                        now = now,
                        review = reviewBrief,
                        exploreMarket = openMarket,
                        openBriefItem = openBriefItem,
                        openCompany = openCompany
                    )
                }

                item {
                    ReferenceQuickActions(
                        openWatchlist = openWatchlist,
                        openAlerts = openAlerts,
                        openPractice = openPractice
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(286.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        ReferenceWatchlistCard(
                            stocks = watchlistPreview,
                            loading = watchlistLoading,
                            error = watchlistError,
                            openWatchlist = openWatchlist,
                            openCompany = openCompany,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        ReferenceNewsCard(
                            stories = relevantNews.take(2),
                            hasWatchlist = watched.isNotEmpty(),
                            loading = newsLoading,
                            error = newsError,
                            openAllNews = openAllNews,
                            openNews = openNews,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(278.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        ReferenceMoversCard(
                            selectedGainers = gainersSelected,
                            movers = if (gainersSelected) intelligence.gainers.take(3) else intelligence.losers.take(3),
                            quotesAvailable = stocks.isNotEmpty(),
                            select = onGainersSelected,
                            openMarket = openMarket,
                            openCompany = openCompany,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        ReferencePracticeCard(
                            enabled = practiceEnabled,
                            cash = practiceCash,
                            openPractice = openPractice,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceHomeHeader(
    name: String,
    avatar: String?,
    market: MyStocksCache.MarketStatus,
    stocks: List<Stock>,
    now: Instant,
    refreshing: Boolean,
    hasAttention: Boolean,
    onRefresh: () -> Unit,
    openAlerts: () -> Unit,
    openProfile: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ShowChart,
                contentDescription = null,
                tint = ResearchGreen,
                modifier = Modifier.size(31.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "NSE Watcher",
                modifier = Modifier.weight(1f),
                color = ResearchText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Box {
                IconButton(onClick = openAlerts, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = "Open alerts",
                        tint = ResearchText,
                        modifier = Modifier.size(27.dp)
                    )
                }
                if (hasAttention) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ResearchGreen)
                            .align(Alignment.TopEnd)
                            .offset(x = (-5).dp, y = 4.dp)
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
            Surface(
                modifier = Modifier
                    .size(45.dp)
                    .clip(CircleShape)
                    .clickable(onClick = openProfile),
                shape = CircleShape,
                color = Color(0xFF0B3553)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        name.trim().take(1).uppercase().ifBlank { "J" },
                        color = ResearchText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (avatar != null) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    HomePresentation.greeting(name, now),
                    color = ResearchText,
                    fontSize = 24.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Review changes, explore the market\nand keep learning.",
                    color = ResearchMuted,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.width(10.dp))
            val delayMinutes = stocks.mapNotNull { it.delayMinutes }.firstOrNull() ?: 15
            Surface(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !refreshing, onClick = onRefresh),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF082B45),
                border = BorderStroke(1.dp, ResearchBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        if (market.isKnown && market.isOpen) ResearchGreen else Color(0xFFBFD9EE),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    refreshing -> "Refreshing"
                                    !market.isKnown -> "Market status"
                                    market.isOpen -> "Market open"
                                    else -> "Market closed"
                                },
                                color = ResearchText,
                                fontSize = 10.2.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Quotes delayed " + delayMinutes + " min",
                            color = ResearchMuted,
                            fontSize = 8.8.sp,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = ResearchMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceDailyBrief(
    sector: HomeSectorPulse?,
    topMover: Stock?,
    breadth: HomeMarketBreadth,
    briefItems: List<HomeBriefItem>,
    briefLoading: Boolean,
    briefHasError: Boolean,
    hasWatchlist: Boolean,
    now: Instant,
    review: () -> Unit,
    exploreMarket: () -> Unit,
    openBriefItem: (HomeBriefItem) -> Unit,
    openCompany: (Stock) -> Unit
) {
    val title = HomePresentation.dailyBriefTitle(
        items = briefItems,
        loading = briefLoading,
        hasError = briefHasError,
        hasWatchlist = hasWatchlist
    )
    val primaryLabel = when {
        briefItems.isNotEmpty() -> "Review now  →"
        !hasWatchlist -> "Choose companies  →"
        else -> "Review watchlist  →"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF06263A),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ResearchGreen.copy(alpha = 0.50f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "DAILY BRIEF",
                    color = ResearchGreen,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    referenceHomeTimestamp(now),
                    color = ResearchMuted,
                    fontSize = 8.8.sp
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                title,
                color = ResearchText,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    when {
                        briefItems.isNotEmpty() -> briefItems.take(3).forEach { item ->
                            ReferenceLedgerBriefRow(item) { openBriefItem(item) }
                        }

                        briefLoading -> {
                            ReferenceBriefAttentionRow(
                                icon = Icons.Default.Bookmark,
                                accent = ResearchGreen,
                                title = "Checking your watchlist",
                                detail = "Looking for later company developments",
                                onClick = review
                            )
                            ReferenceBriefAttentionRow(
                                icon = Icons.Default.Notifications,
                                accent = Color(0xFFFFC857),
                                title = "Checking recorded alerts",
                                detail = "Only detected conditions become attention items",
                                onClick = review
                            )
                            ReferenceBriefAttentionRow(
                                icon = Icons.Default.Article,
                                accent = Color(0xFF58AFFF),
                                title = "Checking company updates",
                                detail = "Published evidence is matched to followed companies",
                                onClick = review
                            )
                        }

                        else -> {
                            ReferenceBriefAttentionRow(
                                icon = Icons.Default.AccountBalance,
                                accent = ResearchGreen,
                                title = if (sector != null) referenceSectorName(sector.sector) + " leading the market" else "Sector context unavailable",
                                detail = if (sector != null) {
                                    "Sector average " + CompanyResearchPresentation.percent(sector.averageChangePct) + " (" + sector.memberCount + " counters)"
                                } else {
                                    "Waiting for enough sector observations"
                                },
                                onClick = exploreMarket
                            )

                            ReferenceBriefAttentionRow(
                                icon = Icons.Default.Equalizer,
                                accent = Color(0xFF58AFFF),
                                title = HomePresentation.marketSummary(breadth),
                                detail = breadth.advancing.toString() + " rising · " + breadth.unchanged + " unchanged · " + breadth.declining + " falling",
                                onClick = exploreMarket
                            )

                            ReferenceBriefAttentionRow(
                                icon = if (topMover == null) Icons.Default.ShowChart else if (topMover.change >= 0.0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                accent = if (topMover == null) ResearchMuted else referenceChangeColor(topMover.change),
                                title = if (topMover != null) topMover.symbol + " is the strongest available mover" else "Top mover unavailable",
                                detail = if (topMover != null) {
                                    CompanyResearchPresentation.percent(topMover.change) + " · " + CompanyResearchPresentation.money(topMover.price)
                                } else {
                                    "No eligible daily mover in the available observations"
                                },
                                onClick = {
                                    if (topMover != null) openCompany(topMover) else exploreMarket()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                ReferenceMarketGraphic(
                    Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                )
            }

            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = review,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ResearchGreen,
                        contentColor = Color(0xFF041827)
                    )
                ) {
                    Text(
                        primaryLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = exploreMarket,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF205378))
                ) {
                    Text(
                        "Explore market  →",
                        color = ResearchText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (briefHasError && briefItems.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "Some change sources are temporarily unavailable; the items shown above are backed by the available change ledger.",
                    color = ResearchMuted,
                    fontSize = 7.8.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun ReferenceLedgerBriefRow(
    item: HomeBriefItem,
    onClick: () -> Unit
) {
    val icon = when {
        item.story != null -> Icons.Default.Article
        item.alert != null -> Icons.Default.Notifications
        else -> Icons.Default.AccountBalance
    }
    val accent = when {
        item.story != null -> Color(0xFF58AFFF)
        item.alert != null -> Color(0xFFFFC857)
        else -> Color(0xFF9A68F7)
    }
    val evidence = buildString {
        append(item.source.ifBlank { "Source unavailable" })
        if (item.time.isNotBlank()) {
            append(" · ")
            append(CompanyResearchPresentation.date(item.time))
        }
    }

    ReferenceBriefAttentionRow(
        icon = icon,
        accent = accent,
        title = item.title,
        detail = evidence,
        onClick = onClick
    )
}

@Composable
private fun ReferenceBriefAttentionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = ResearchText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                color = ResearchMuted,
                fontSize = 8.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ResearchMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ReferenceMarketGraphic(modifier: Modifier = Modifier) {
    val green = ResearchGreen
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val baseline = height * 0.87f
        val barWidth = width * 0.16f
        val gap = width * 0.055f
        val heights = listOf(0.28f, 0.43f, 0.58f, 0.78f)
        val startX = width * 0.08f

        heights.forEachIndexed { index, ratio ->
            val left = startX + index * (barWidth + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF20F3A7),
                        Color(0xFF009E70)
                    )
                ),
                topLeft = Offset(left, baseline - height * ratio),
                size = Size(barWidth, height * ratio),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            )
        }

        val line = Path().apply {
            moveTo(width * 0.04f, height * 0.70f)
            cubicTo(width * 0.23f, height * 0.63f, width * 0.28f, height * 0.48f, width * 0.42f, height * 0.51f)
            cubicTo(width * 0.58f, height * 0.55f, width * 0.63f, height * 0.30f, width * 0.76f, height * 0.34f)
            cubicTo(width * 0.88f, height * 0.37f, width * 0.90f, height * 0.17f, width * 0.97f, height * 0.16f)
        }

        drawPath(
            path = line,
            color = Color(0xFF67FFD1),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = green.copy(alpha = 0.35f),
            radius = 10.dp.toPx(),
            center = Offset(width * 0.97f, height * 0.16f)
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(width * 0.97f, height * 0.16f)
        )
    }
}

@Composable
private fun ReferenceQuickActions(
    openWatchlist: () -> Unit,
    openAlerts: () -> Unit,
    openPractice: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReferenceQuickAction(
            label = "Watchlist",
            subtitle = "Track your stocks",
            icon = Icons.Default.Star,
            accent = ResearchGreen,
            action = openWatchlist,
            modifier = Modifier.weight(1f)
        )
        ReferenceQuickAction(
            label = "Alerts",
            subtitle = "Stay informed",
            icon = Icons.Default.Notifications,
            accent = Color(0xFF58AFFF),
            action = openAlerts,
            modifier = Modifier.weight(1f)
        )
        ReferenceQuickAction(
            label = "Practice",
            subtitle = "Build confidence",
            icon = Icons.Default.AccountBalanceWallet,
            accent = Color(0xFF9A68F7),
            action = openPractice,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReferenceQuickAction(
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    action: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = action),
        color = ResearchCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = ResearchText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    subtitle,
                    color = ResearchMuted,
                    fontSize = 8.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ResearchMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ReferenceCardHeader(
    title: String,
    action: String,
    onAction: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = ResearchText,
            fontSize = 13.2.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(
            onClick = onAction,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                action + " →",
                color = ResearchGreen,
                fontSize = 8.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReferenceWatchlistCard(
    stocks: List<Stock>,
    loading: Boolean,
    error: Boolean,
    openWatchlist: () -> Unit,
    openCompany: (Stock) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = ResearchCard,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            ReferenceCardHeader("Your watchlist", "View all", openWatchlist)

            when {
                error -> ReferenceMessage("Your saved companies are temporarily unavailable.")
                loading -> ReferenceMessage("Loading watchlist…")
                stocks.isEmpty() -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add companies to see them here.",
                        color = ResearchMuted,
                        fontSize = 9.2.sp,
                        lineHeight = 13.sp
                    )
                    TextButton(onClick = openWatchlist, contentPadding = PaddingValues(0.dp)) {
                        Text("Add companies →", color = ResearchGreen, fontSize = 9.sp)
                    }
                }
                else -> stocks.take(3).forEachIndexed { index, stock ->
                    if (index > 0) {
                        HorizontalDivider(color = ResearchBorder.copy(alpha = 0.70f))
                    }
                    ReferenceWatchlistRow(stock) {
                        openCompany(stock)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceWatchlistRow(
    stock: Stock,
    open: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClick = open)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReferenceCompanyLogo(stock, 38)
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stock.symbol,
                color = ResearchText,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                stock.name,
                color = ResearchMuted,
                fontSize = 8.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                stock.price.takeIf { it.isFinite() && it > 0.0 }
                    ?.let(CompanyResearchPresentation::money)
                    ?: "—",
                color = ResearchText,
                fontSize = 9.4.sp,
                fontWeight = FontWeight.SemiBold
            )
            val changeValue = stock.change.takeIf { stock.changeAvailable && it.isFinite() }
            Text(
                changeValue?.let(CompanyResearchPresentation::percent) ?: "—",
                color = referenceChangeColor(changeValue),
                fontSize = 9.4.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReferenceNewsCard(
    stories: List<NewsItem>,
    hasWatchlist: Boolean,
    loading: Boolean,
    error: Boolean,
    openAllNews: () -> Unit,
    openNews: (NewsItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = ResearchCard,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            ReferenceCardHeader("News for your companies", "View all", openAllNews)

            when {
                !hasWatchlist -> ReferenceMessage("Follow companies to personalise this news card.")
                loading && stories.isEmpty() -> ReferenceMessage("Loading company news…")
                error && stories.isEmpty() -> ReferenceMessage("Company news is temporarily unavailable.")
                stories.isEmpty() -> ReferenceMessage("No recent stories for your followed companies.")
                else -> stories.take(2).forEachIndexed { index, story ->
                    if (index > 0) {
                        HorizontalDivider(color = ResearchBorder.copy(alpha = 0.70f))
                    }
                    ReferenceNewsRow(story) {
                        openNews(story)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceNewsRow(
    story: NewsItem,
    open: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 91.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = open)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (story.imageUrl.isNotBlank()) {
            AsyncImage(
                model = story.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ResearchRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Article,
                    contentDescription = null,
                    tint = Color(0xFF58AFFF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                story.title,
                color = ResearchText,
                fontSize = 9.3.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                story.source.ifBlank { "Source unavailable" },
                color = ResearchMuted,
                fontSize = 7.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                CompanyResearchPresentation.date(story.publishedAt),
                color = ResearchMuted,
                fontSize = 7.6.sp,
                maxLines = 1
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ResearchMuted,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ReferenceMoversCard(
    selectedGainers: Boolean,
    movers: List<Stock>,
    quotesAvailable: Boolean,
    select: (Boolean) -> Unit,
    openMarket: () -> Unit,
    openCompany: (Stock) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = ResearchCard,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            ReferenceCardHeader("Market movers", "View all", openMarket)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ReferenceMoverChip(
                    "Gainers",
                    selectedGainers,
                    { select(true) },
                    Modifier.weight(1f)
                )
                ReferenceMoverChip(
                    "Losers",
                    !selectedGainers,
                    { select(false) },
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(4.dp))
            if (movers.isEmpty()) {
                ReferenceMessage(
                    if (!quotesAvailable) {
                        "Market quotes are unavailable."
                    } else {
                        "No " + if (selectedGainers) "gainers" else "losers" + " in the available daily changes."
                    }
                )
            } else {
                movers.take(3).forEachIndexed { index, stock ->
                    if (index > 0) {
                        HorizontalDivider(color = ResearchBorder.copy(alpha = 0.70f))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 55.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { openCompany(stock) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReferenceCompanyLogo(stock, 34)
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stock.symbol,
                                color = ResearchText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                stock.name,
                                color = ResearchMuted,
                                fontSize = 7.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                CompanyResearchPresentation.money(stock.price),
                                color = ResearchText,
                                fontSize = 8.7.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                CompanyResearchPresentation.percent(stock.change),
                                color = referenceChangeColor(stock.change),
                                fontSize = 9.1.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceMoverChip(
    label: String,
    selected: Boolean,
    click: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = click),
        color = if (selected) ResearchGreen.copy(alpha = 0.09f) else ResearchCard,
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(
            1.dp,
            if (selected) ResearchGreen else ResearchBorder
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) ResearchGreen else ResearchMuted,
                fontSize = 8.9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ReferencePracticeCard(
    enabled: Boolean,
    cash: Double,
    openPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .clickable(role = Role.Button, onClick = openPractice),
        color = ResearchCard,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ResearchGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = ResearchGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    "Practice portfolio",
                    modifier = Modifier.weight(1f),
                    color = ResearchText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ResearchMuted,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(0.58f)) {
                    Text(
                        "Build confidence\nwith virtual money",
                        color = ResearchMuted,
                        fontSize = 9.2.sp,
                        lineHeight = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (enabled && cash.isFinite() && cash >= 0) {
                            String.format(Locale.US, "KSh %,.0f", cash)
                        } else {
                            "KSh 1,000,000"
                        },
                        color = ResearchText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (enabled) {
                            "Current virtual cash"
                        } else {
                            "Suggested virtual\nstarting balance"
                        },
                        color = ResearchMuted,
                        fontSize = 8.6.sp,
                        lineHeight = 11.sp
                    )
                }

                ReferencePracticeIllustration(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                )
            }

            Button(
                onClick = openPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ResearchGreen,
                    contentColor = Color(0xFF041827)
                )
            ) {
                Text(
                    if (enabled) "Open portfolio  →" else "Start practising  →",
                    fontSize = 10.2.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun ReferencePracticeIllustration(
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val coin = Color(0xFF58AFFF)
        val darkCoin = Color(0xFF1F669A)

        fun drawCoinStack(x: Float, baseY: Float, count: Int, stackWidth: Float) {
            repeat(count) { index ->
                val y = baseY - index * height * 0.055f
                drawOval(
                    color = if (index % 2 == 0) coin else darkCoin,
                    topLeft = Offset(x, y),
                    size = Size(stackWidth, height * 0.06f)
                )
            }
        }

        drawCoinStack(width * 0.47f, height * 0.77f, 4, width * 0.27f)
        drawCoinStack(width * 0.68f, height * 0.73f, 6, width * 0.29f)

        val arrow = Path().apply {
            moveTo(width * 0.10f, height * 0.61f)
            lineTo(width * 0.30f, height * 0.48f)
            lineTo(width * 0.43f, height * 0.52f)
            lineTo(width * 0.63f, height * 0.28f)
            lineTo(width * 0.80f, height * 0.31f)
            lineTo(width * 0.94f, height * 0.11f)
        }

        drawPath(
            path = arrow,
            color = Color(0xFF26E7A4),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawLine(
            color = Color(0xFF4DFFD0),
            start = Offset(width * 0.94f, height * 0.11f),
            end = Offset(width * 0.78f, height * 0.15f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF4DFFD0),
            start = Offset(width * 0.94f, height * 0.11f),
            end = Offset(width * 0.90f, height * 0.26f),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ReferenceCompanyLogo(
    stock: Stock,
    sizeDp: Int
) {
    val logoUrl = stock.logoUrl?.takeIf { it.isNotBlank() }
        ?: "https://mystocks.africa/logos/" + stock.symbol.lowercase(Locale.US) + "-ke.svg"

    Surface(
        modifier = Modifier.size(sizeDp.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF5F7FA)
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stock.symbol.take(3),
                color = Color(0xFF17364F),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
            AsyncImage(
                model = logoUrl,
                contentDescription = stock.symbol,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun ReferenceMessage(
    text: String
) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        color = ResearchMuted,
        fontSize = 9.2.sp,
        lineHeight = 13.sp
    )
}

@Composable
private fun referenceChangeColor(
    change: Double?
): Color = when {
    change == null || !change.isFinite() -> ResearchMuted
    change > 0.0 -> ResearchGreen
    change < 0.0 -> ResearchRed
    else -> ResearchMuted
}

private fun referenceHomeTimestamp(
    now: Instant
): String =
    DateTimeFormatter
        .ofPattern("dd MMM yyyy, HH:mm 'EAT'", Locale.US)
        .format(now.atZone(ZoneId.of("Africa/Nairobi")))

private fun referenceSectorName(
    sector: String
): String = when (sector.lowercase(Locale.US)) {
    "banks" -> "Financials"
    "banking" -> "Financials"
    "telecommunication" -> "Telecom"
    "telecommunications" -> "Telecom"
    "oil & gas" -> "Energy"
    "oil and gas" -> "Energy"
    else -> sector
}
