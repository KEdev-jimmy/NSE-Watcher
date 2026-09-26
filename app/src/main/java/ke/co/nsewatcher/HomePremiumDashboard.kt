package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun HomePremiumDashboard(
    darkTheme: Boolean,
    market: MyStocksCache.MarketStatus,
    stocks: List<Stock>,
    now: Instant,
    refreshing: Boolean,
    hasAttention: Boolean,
    watchlistPreview: List<Stock>,
    watchlistHistories: Map<String, List<MyStocksCache.HistoryPoint>>,
    watchlistLoading: Boolean,
    watchlistError: Boolean,
    briefItems: List<HomeBriefItem>,
    briefHasError: Boolean,
    intelligence: HomeIntelligenceSnapshot,
    marketAttention: List<MarketAttentionItem>,
    practiceInsights: PracticeLearningInsights,
    practiceEnabled: Boolean,
    practiceCash: Double,
    onRefresh: () -> Unit,
    openAlerts: () -> Unit,
    openWatchlist: () -> Unit,
    openPractice: () -> Unit,
    openMarket: () -> Unit,
    openCompanies: () -> Unit,
    openCompany: (Stock) -> Unit,
    openBriefItem: (HomeBriefItem) -> Unit
) {
    val palette = premiumHomePalette(darkTheme)
    val hero = HomePremiumPresentation.hero(intelligence, briefItems)
    val spotlight = HomePremiumPresentation.indexSpotlight(intelligence.marketIndices)
    val sentiment = HomePremiumPresentation.sentiment(intelligence.breadth, stocks.size)

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PremiumHomeHeader(
                    dark = darkTheme,
                    palette = palette,
                    hasAttention = hasAttention,
                    openCompanies = openCompanies,
                    openAlerts = openAlerts
                )
            }
            if (stocks.any { it.dataOrigin == "offline_snapshot" }) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = palette.amber.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, palette.amber.copy(alpha = 0.42f))
                    ) {
                        Text(
                            "Offline snapshot • Showing saved market observations while live data reconnects. " +
                                "Saved prices are marked stale and cannot trigger alerts or Practice fills.",
                            color = palette.muted,
                            fontSize = 9.5.sp,
                            lineHeight = 13.5.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)
                        )
                    }
                }
            }
            item {
                PremiumHeroCard(
                    palette = palette,
                    dark = darkTheme,
                    market = market,
                    stocks = stocks,
                    now = now,
                    hero = hero,
                    spotlight = spotlight,
                    topMover = (intelligence.gainers + intelligence.losers)
                        .maxByOrNull { abs(it.change) },
                    refreshing = refreshing,
                    onRefresh = onRefresh,
                    onHeroAction = {
                        val item = hero.briefItem
                        if (item != null) openBriefItem(item) else openMarket()
                    }
                )
            }
            item {
                PremiumMarketOverview(
                    palette = palette,
                    breadth = intelligence.breadth,
                    sentiment = sentiment
                )
            }
            item {
                PremiumWatchlistStrip(
                    palette = palette,
                    stocks = watchlistPreview,
                    histories = watchlistHistories,
                    loading = watchlistLoading,
                    error = watchlistError,
                    openWatchlist = openWatchlist,
                    openCompany = openCompany
                )
            }
            item {
                PremiumWhyMovingCard(
                    palette = palette,
                    dark = darkTheme,
                    attention = marketAttention.firstOrNull(),
                    openMarket = openMarket,
                    openCompany = openCompany
                )
            }
            item {
                PremiumPracticeOverview(
                    palette = palette,
                    enabled = practiceEnabled,
                    cash = practiceCash,
                    insights = practiceInsights,
                    openPractice = openPractice
                )
            }
            if (briefHasError || watchlistError) {
                item {
                    Text(
                        "Some sources are temporarily unavailable. Only verified observations and saved evidence are shown.",
                        color = palette.muted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumHomeHeader(
    dark: Boolean,
    palette: PremiumHomePalette,
    hasAttention: Boolean,
    openCompanies: () -> Unit,
    openAlerts: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NseWatcherBrandLockup(
            modifier = Modifier.weight(1f),
            dark = dark
        )
        PremiumCircleButton(
            palette = palette,
            onClick = openCompanies,
            contentDescription = "Search companies"
        ) {
            Icon(Icons.Default.Search, null, tint = palette.text, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(7.dp))
        Box {
            PremiumCircleButton(
                palette = palette,
                onClick = openAlerts,
                contentDescription = "Open alerts"
            ) {
                Icon(Icons.Default.NotificationsNone, null, tint = palette.text, modifier = Modifier.size(24.dp))
            }
            if (hasAttention) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.primary)
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumCircleButton(
    palette: PremiumHomePalette,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = palette.raised,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun PremiumHeroCard(
    palette: PremiumHomePalette,
    dark: Boolean,
    market: MyStocksCache.MarketStatus,
    stocks: List<Stock>,
    now: Instant,
    hero: HomePremiumHero,
    spotlight: HomePremiumIndexSpotlight?,
    topMover: Stock?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onHeroAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 282.dp)
        ) {
            PremiumNairobiBackdrop(
                dark = dark,
                palette = palette,
                modifier = Modifier.matchParentSize()
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            if (dark) {
                                listOf(
                                    Color(0xFF06182F).copy(alpha = 0.98f),
                                    Color(0xFF0A1C36).copy(alpha = 0.82f),
                                    Color.Transparent
                                )
                            } else {
                                listOf(
                                    Color(0xFFFFFEF8).copy(alpha = 0.98f),
                                    Color(0xFFFFFEF8).copy(alpha = 0.80f),
                                    Color.Transparent
                                )
                            }
                        )
                    )
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "What matters today",
                        color = palette.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            homePremiumDate(now),
                            color = palette.muted,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        if (market.isKnown && market.isOpen) palette.primary else palette.muted,
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                when {
                                    refreshing -> "Refreshing"
                                    !market.isKnown -> "Status unavailable"
                                    market.isOpen -> "NSE open"
                                    else -> "NSE closed"
                                },
                                color = palette.muted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(17.dp))
                Text(
                    hero.title,
                    color = palette.text,
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.72f)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    hero.body,
                    color = palette.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.70f)
                )

                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Button(
                        onClick = onHeroAction,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primary,
                            contentColor = if (dark) Color(0xFF032319) else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            hero.action + "  →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PremiumMarketSpotlight(
                        palette = palette,
                        spotlight = spotlight,
                        topMover = topMover,
                        stocks = stocks,
                        onRefresh = onRefresh
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumMarketSpotlight(
    palette: PremiumHomePalette,
    spotlight: HomePremiumIndexSpotlight?,
    topMover: Stock?,
    stocks: List<Stock>,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 132.dp, max = 154.dp)
            .clickable(role = Role.Button, onClick = onRefresh),
        shape = RoundedCornerShape(18.dp),
        color = palette.surface.copy(alpha = 0.91f),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            when {
                spotlight != null -> {
                    Text(
                        spotlight.label,
                        color = palette.muted,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        spotlight.value,
                        color = palette.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    spotlight.change?.let {
                        Text(
                            it,
                            color = if (spotlight.positive == false) palette.danger else palette.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                topMover != null -> {
                    Text(topMover.symbol, color = palette.muted, fontSize = 9.5.sp)
                    Text(
                        topMover.price.takeIf { it.isFinite() && it > 0.0 }
                            ?.let(CompanyResearchPresentation::money) ?: "Price unavailable",
                        color = palette.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        CompanyResearchPresentation.percent(topMover.change),
                        color = if (topMover.change < 0) palette.danger else palette.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text("Market data", color = palette.muted, fontSize = 9.5.sp)
                    Text(
                        if (stocks.isEmpty()) "Unavailable" else "Latest quotes",
                        color = palette.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

internal fun homePremiumDate(now: Instant): String =
    now.atZone(ZoneId.of("Africa/Nairobi"))
        .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US))
