package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallet
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
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun HomeBriefingGreeting(
    palette: PremiumHomePalette,
    name: String,
    now: Instant,
    market: MyStocksCache.MarketStatus
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                HomePresentation.greeting(name, now),
                color = palette.text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            val statusColor = when {
                !market.isKnown -> palette.muted
                market.isOpen -> palette.primary
                else -> palette.danger
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = statusColor.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
            ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(statusColor, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        HomeBriefingPresentation.marketStatus(market.isKnown, market.isOpen),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "Your 10-minute market check",
            color = palette.muted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
internal fun HomePracticePortfolioHero(
    palette: PremiumHomePalette,
    dark: Boolean,
    loading: Boolean,
    error: Boolean,
    summary: HomePortfolioSummary?,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.35f))
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF0B2B42), Color(0xFF075044), Color(0xFF083D4F))
                    else listOf(Color(0xFF06484E), Color(0xFF00836C), Color(0xFF036458))
                )
            )
        ) {
            val heroText = Color.White
            val heroMuted = Color(0xFFD1E7E4)
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "My practice portfolio",
                        color = heroText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.55f))
                    ) {
                        Text(
                            "VIRTUAL MONEY", color = Color(0xFF8FF8DD),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(17.dp))
                when {
                    loading -> Text("Loading your saved practice portfolio…", color = heroMuted, fontSize = 13.sp)
                    error -> Text("Practice portfolio could not be loaded. Open Practice to retry.", color = heroMuted, fontSize = 13.sp)
                    summary == null -> {
                        Text("Start with virtual money", color = heroText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text("Try buying and following NSE shares without using real funds.", color = heroMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(18.dp))
                        Text("Create practice portfolio  →", color = Color(0xFF8FF8DD), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    else -> {
                        Text("Current value", color = heroMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            practiceMoney(summary.marketValue), color = heroText,
                            fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Invested", color = heroMuted, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                practiceMoney(summary.invested), color = heroText,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f), maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val gainColor = when {
                                summary.provisional -> Color(0xFFFFDE9C)
                                summary.gain < 0.0 -> Color(0xFFFFA8B6)
                                else -> Color(0xFF8FF8DD)
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = gainColor.copy(alpha = 0.13f),
                                border = BorderStroke(1.dp, gainColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    if (summary.provisional) "Estimate only"
                                    else (if (summary.gain < 0) "−" else "+") + practiceMoney(abs(summary.gain)) +
                                        (summary.gainPercent?.let { String.format(Locale.US, " (%+.1f%%)", it) } ?: ""),
                                    color = gainColor, fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp, maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (summary.balanceHistory.size >= 2) {
                            PremiumSparkline(
                                values = summary.balanceHistory,
                                tint = Color(0xFF83EFCF),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            )
                            Text("Practice account balance history (includes cash)", color = heroMuted, fontSize = 9.sp)
                        } else {
                            Text("Portfolio history will appear after practice activity.", color = heroMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.20f))
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your holdings", color = heroText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            if (summary.symbols.isEmpty()) {
                                Text("No shares purchased yet", color = heroMuted, fontSize = 10.sp)
                            } else {
                                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    summary.symbols.forEach { symbol -> HomeSmallTag(symbol, palette.secondary, palette) }
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = heroText, modifier = Modifier.size(18.dp))
                        }
                        if (summary.provisional) {
                            Spacer(Modifier.height(7.dp))
                            Text("Some holding prices are unconfirmed; values include estimates.", color = Color(0xFFFFDE9C), fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text("Simulation only · Not a brokerage account", color = heroMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun HomeSmallTag(text: String, accent: Color, palette: PremiumHomePalette) {
    Surface(
        color = accent.copy(alpha = 0.09f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
    ) {
        Text(text, color = if (accent == palette.primary) palette.primary else palette.text,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
internal fun HomePortfolioStatPair(
    palette: PremiumHomePalette,
    summary: HomePortfolioSummary?,
    onOpen: () -> Unit
) {
    if (summary == null) return
    PremiumResponsivePair(
        first = {
            HomeSmallMetric(
                palette, "Portfolio value", practiceMoney(summary.marketValue),
                palette.secondary, onOpen, Icons.Default.Wallet
            )
        },
        second = {
            val top = summary.topPerformer
            HomeSmallMetric(
                palette,
                "Top performer",
                top?.let { String.format(Locale.US, "%s %+.1f%%", it.symbol, it.gainPercent) }
                    ?: "Not available",
                if (top == null) palette.muted else if (top.gainPercent < 0) palette.danger else palette.primary,
                onOpen,
                Icons.Default.TrendingUp
            )
        }
    )
}

@Composable
private fun HomeSmallMetric(
    palette: PremiumHomePalette,
    label: String,
    value: String,
    accent: Color,
    onOpen: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(17.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = palette.muted, fontSize = 9.sp, maxLines = 1)
                Text(value, color = if (label == "Top performer") accent else palette.text,
                    fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(13.dp))
        }
    }
}

@Composable
internal fun HomeMarketMinuteCard(
    palette: PremiumHomePalette,
    summary: HomeMinuteSummary,
    openMarket: () -> Unit,
    openWatchlist: () -> Unit
) {
    PremiumCardSurface(palette, Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = openMarket),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Market in a minute", color = palette.text,
                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeMinuteMetric(
                modifier = Modifier.weight(1f), palette = palette,
                value = if (summary.verified == 0) "—" else summary.advancing.toString(),
                label = "Advancers", tint = palette.primary,
                icon = Icons.Default.TrendingUp, onClick = openMarket
            )
            HomeMinuteMetric(
                modifier = Modifier.weight(1f), palette = palette,
                value = if (summary.verified == 0) "—" else summary.declining.toString(),
                label = "Decliners", tint = palette.danger,
                icon = Icons.Default.TrendingDown, onClick = openMarket
            )
            HomeMinuteMetric(
                modifier = Modifier.weight(1f), palette = palette,
                value = if (summary.verified == 0) "—" else summary.watched.toString(),
                label = "Tracked quotes", tint = palette.secondary,
                icon = Icons.Default.Visibility, onClick = openWatchlist
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            if (summary.verified == 0) "Verified price changes are not available yet."
            else "Based on ${summary.verified} recent observed quotes, not the entire NSE.",
            color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp
        )
    }
}

@Composable
private fun HomeMinuteMetric(
    modifier: Modifier,
    palette: PremiumHomePalette,
    value: String,
    label: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 97.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = palette.raised
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 11.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(5.dp))
            Text(value, color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = palette.muted, fontSize = 10.sp, maxLines = 2, lineHeight = 13.sp)
        }
    }
}

@Composable
internal fun HomeUnusualMove(
    palette: PremiumHomePalette,
    attention: MarketAttentionItem?,
    openMarket: () -> Unit,
    openCompany: (Stock) -> Unit
) {
    PremiumCardSurface(palette, Modifier.fillMaxWidth(), onClick = {
        attention?.stock?.let(openCompany) ?: openMarket()
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Worth investigating", color = palette.text, fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Info, contentDescription = "Why this was flagged", tint = palette.muted, modifier = Modifier.size(17.dp))
            Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (attention == null) {
            Text("Nothing unusual confirmed yet", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            HomeCardMessage("This section highlights unusually large movements when recent, verifiable evidence is available.", palette)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumCompanyLogo(attention.stock, 39)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(attention.stock.name.ifBlank { attention.stock.symbol }, color = palette.text,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Large price movement · ${attention.stock.symbol}", color = palette.amber, fontSize = 10.sp)
                }
                Text(CompanyResearchPresentation.percent(attention.stock.change),
                    color = if (attention.stock.change < 0) palette.danger else palette.primary,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                (attention.reasons.firstOrNull()?.detail
                    ?: "A recent observed price move warrants an evidence review."),
                color = palette.muted, fontSize = 11.sp, lineHeight = 16.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not a buy or sell signal.", color = palette.muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text("Explore why  →", color = palette.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun HomeTradingActivityCard(
    palette: PremiumHomePalette,
    activity: List<HomeTradingActivity>,
    openMarket: () -> Unit,
    openCompany: (Stock) -> Unit
) {
    PremiumCardSurface(palette, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = openMarket), verticalAlignment = Alignment.CenterVertically) {
            Text("Trading volume", color = palette.text, fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Info, contentDescription = "Volume is not proof of liquidity", tint = palette.muted, modifier = Modifier.size(17.dp))
            Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(3.dp))
        HomeCardMessage("Recent reported trading volume; not a measure of order-book depth.", palette)
        Spacer(Modifier.height(8.dp))
        if (activity.isEmpty()) {
            HomeCardMessage("Verified daily volume data is pending. No liquidity ranking is available.", palette)
        } else {
            activity.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = { openCompany(item.stock) }),
                    shape = RoundedCornerShape(12.dp),
                    color = palette.raised,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.65f))
                ) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        PremiumCompanyLogo(item.stock, 34)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.stock.symbol, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(item.stock.name, color = palette.muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatHomeVolume(item.stock.volume), color = palette.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                item.vsAverage?.let { String.format(Locale.US, "%.1f× supplied average", it) }
                                    ?: "Baseline unavailable",
                                color = palette.muted, fontSize = 8.5.sp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("Price movement or volume alone does not prove that a share is easy to trade.",
            color = palette.muted, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

@Composable
internal fun HomeCompactWatchlist(
    palette: PremiumHomePalette,
    stocks: List<Stock>,
    histories: Map<String, List<MyStocksCache.HistoryPoint>>,
    loading: Boolean,
    error: Boolean,
    openWatchlist: () -> Unit,
    openCompany: (Stock) -> Unit
) {
    PremiumCardSurface(palette, Modifier.fillMaxWidth()) {
        HomeLinkedHeader("Your watchlist", palette, "See all", openWatchlist)
        Spacer(Modifier.height(7.dp))
        when {
            error -> HomeCardMessage("Your watchlist is temporarily unavailable.", palette)
            loading -> HomeCardMessage("Loading your watchlist…", palette)
            stocks.isEmpty() -> {
                HomeCardMessage("No watched companies yet. Add some to track their changes.", palette)
                TextButton(onClick = openWatchlist) { Text("Add companies", color = palette.primary) }
            }
            else -> stocks.take(2).forEachIndexed { index, stock ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = { openCompany(stock) }),
                    shape = RoundedCornerShape(12.dp), color = palette.raised,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.65f))
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PremiumCompanyLogo(stock, 36)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1.1f)) {
                            Text(stock.symbol, color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(stock.name, color = palette.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        PremiumSparkline(
                            values = histories[stock.symbol].orEmpty().map { it.close },
                            tint = if (stock.changeAvailable && stock.change < 0.0) palette.danger else palette.primary,
                            modifier = Modifier.weight(0.7f).height(32.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        val unavailable = stock.freshnessMode.equals("STALE", true) ||
                            !stock.price.isFinite() || stock.price <= 0.0
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 67.dp)) {
                            Text(
                                if (unavailable) "—" else CompanyResearchPresentation.money(stock.price),
                                color = palette.text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (stock.freshnessMode.equals("STALE", true)) "Stale"
                                else if (stock.changeAvailable && stock.change.isFinite()) CompanyResearchPresentation.percent(stock.change)
                                else "Pending",
                                color = if (unavailable || !stock.changeAvailable) palette.muted
                                    else if (stock.change < 0.0) palette.danger else palette.primary,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeLatestMarketNews(
    palette: PremiumHomePalette,
    news: List<NewsItem>,
    loading: Boolean,
    error: Boolean,
    openAllNews: () -> Unit,
    openNews: (NewsItem) -> Unit
) {
    PremiumCardSurface(palette, Modifier.fillMaxWidth()) {
        HomeLinkedHeader("Latest market news", palette, "See all", openAllNews)
        Text("Company announcements and verified NSE updates", color = palette.muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        when {
            news.isEmpty() -> {
                Text("No verified headlines yet", color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                HomeCardMessage(
                    when {
                        loading -> "Loading verified market headlines…"
                        error -> "News is temporarily unavailable. Open News to retry."
                        else -> "Check back later for the latest company announcements and NSE updates."
                    }, palette
                )
                TextButton(onClick = openAllNews) { Text("Open news  →", color = palette.primary) }
            }
            else -> news.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = { openNews(item) }),
                    shape = RoundedCornerShape(12.dp), color = palette.raised,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.65f))
                ) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                .background(palette.secondary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Newspaper, null, tint = palette.secondary, modifier = Modifier.size(21.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                            Text(item.source + if (item.freshnessMode.equals("STALE", true)) " · Saved copy" else "",
                                color = palette.muted, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLinkedHeader(title: String, palette: PremiumHomePalette, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = palette.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(action, color = palette.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, null, tint = palette.primary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun HomeCardMessage(message: String, palette: PremiumHomePalette) {
    Text(message, color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp)
}
