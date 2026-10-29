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
        Spacer(Modifier.height(2.dp))
        Text(
            "Here's what's happening on the Nairobi Securities Exchange.",
            color = palette.muted,
            fontSize = 11.sp,
            lineHeight = 16.sp
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
        border = BorderStroke(1.dp, palette.secondary.copy(alpha = 0.55f))
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    if (dark) listOf(Color(0xFF05213D), Color(0xFF09233F), Color(0xFF2E1747))
                    else listOf(palette.surface, palette.raised, palette.surface)
                )
            )
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "MY PRACTICE PORTFOLIO",
                        color = palette.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    HomeSmallTag("Virtual money", palette.primary, palette)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "Simulation only · Not a connected brokerage account",
                    color = palette.muted,
                    fontSize = 9.sp
                )

                Spacer(Modifier.height(14.dp))
                when {
                    loading -> HomeCardMessage("Loading your saved practice portfolio…", palette)
                    error -> HomeCardMessage("Practice portfolio could not be loaded. Open Practice to retry.", palette)
                    summary == null -> {
                        Text("Start with virtual money", color = palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        HomeCardMessage("Try buying and following NSE shares without using real funds.", palette)
                        Spacer(Modifier.height(11.dp))
                        Text("Create practice portfolio  →", color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    else -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Total invested", color = palette.muted, fontSize = 10.sp)
                                Text(
                                    practiceMoney(summary.invested),
                                    color = palette.text,
                                    fontSize = 23.sp,
                                    lineHeight = 27.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Current shares value", color = palette.muted, fontSize = 10.sp)
                                Text(
                                    practiceMoney(summary.marketValue),
                                    color = palette.text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(Modifier.width(1.dp).height(97.dp).background(palette.border))
                            Column(Modifier.weight(1f)) {
                                Text("Net gain / loss", color = palette.muted, fontSize = 10.sp)
                                val gainColor = when {
                                    summary.provisional -> palette.muted
                                    summary.gain < 0.0 -> palette.danger
                                    else -> palette.primary
                                }
                                Text(
                                    if (summary.provisional) "Estimate only"
                                    else (if (summary.gain < 0) "−" else "+") + practiceMoney(abs(summary.gain)),
                                    color = gainColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = if (summary.provisional) 16.sp else 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    summary.gainPercent?.let { String.format(Locale.US, "%+.1f%%", it) }
                                        ?: "Return unavailable",
                                    color = gainColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                if (summary.balanceHistory.size >= 2) {
                                    PremiumSparkline(
                                        values = summary.balanceHistory,
                                        tint = gainColor,
                                        modifier = Modifier.fillMaxWidth().height(23.dp)
                                    )
                                    Text("Account balance history incl. cash", color = palette.muted, fontSize = 7.5.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = palette.border.copy(alpha = 0.8f))
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your holdings", color = palette.text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            if (summary.symbols.isEmpty()) {
                                Text("No shares purchased yet", color = palette.muted, fontSize = 10.sp)
                            } else {
                                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    summary.symbols.forEach { symbol -> HomeSmallTag(symbol, palette.secondary, palette) }
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(16.dp))
                        }
                        if (summary.provisional) {
                            Spacer(Modifier.height(7.dp))
                            Text("Some holding prices are missing or unconfirmed; values include cost estimates.", color = palette.amber, fontSize = 9.sp)
                        }
                    }
                }
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
            Text("Unusual market move", color = palette.text, fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Info, contentDescription = "Why this was flagged", tint = palette.muted, modifier = Modifier.size(17.dp))
            Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (attention == null) {
            Text("No unusual move confirmed", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            HomeCardMessage("An eligible daily change or peer comparison will appear here when evidence is available.", palette)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumCompanyLogo(attention.stock, 39)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(attention.stock.name.ifBlank { attention.stock.symbol }, color = palette.text,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(attention.stock.symbol, color = palette.muted, fontSize = 9.sp)
                }
                Text(CompanyResearchPresentation.percent(attention.stock.change),
                    color = if (attention.stock.change < 0) palette.danger else palette.primary,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Flagged: " + (attention.reasons.firstOrNull()?.detail
                    ?: "A recent observed price move warrants an evidence review."),
                color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Not a buy or sell signal.", color = palette.muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text("Review evidence  ›", color = palette.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
            Text("Trading & liquidity", color = palette.text, fontSize = 15.sp,
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
                        Column(Modifier.weight(1.2f)) {
                            Text(stock.symbol, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(stock.name, color = palette.muted, fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        PremiumSparkline(
                            values = histories[stock.symbol].orEmpty().map { it.close },
                            tint = if (stock.changeAvailable && stock.change < 0.0) palette.danger else palette.primary,
                            modifier = Modifier.weight(0.9f).height(28.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (stock.freshnessMode.equals("STALE", true)) "Stale quote"
                            else if (stock.changeAvailable && stock.change.isFinite()) CompanyResearchPresentation.percent(stock.change)
                            else "Data pending",
                            color = if (stock.freshnessMode.equals("STALE", true) || !stock.changeAvailable) palette.muted
                                else if (stock.change < 0.0) palette.danger else palette.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 54.dp),
                            maxLines = 1
                        )
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
        Spacer(Modifier.height(7.dp))
        when {
            news.isEmpty() -> {
                HomeCardMessage(
                    when {
                        loading -> "Loading verified market headlines…"
                        error -> "News is temporarily unavailable. Open News to retry."
                        else -> "Verified market headlines will appear here when available."
                    }, palette
                )
                TextButton(onClick = openAllNews) { Text("Open News", color = palette.primary) }
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
                            Text(item.title, color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                            Text(item.source + if (item.freshnessMode.equals("STALE", true)) " · Saved copy" else "",
                                color = palette.muted, fontSize = 9.sp)
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
        Text(title, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(action, color = palette.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, null, tint = palette.primary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun HomeCardMessage(message: String, palette: PremiumHomePalette) {
    Text(message, color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp)
}
