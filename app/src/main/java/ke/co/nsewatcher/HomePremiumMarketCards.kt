package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import java.util.Locale

@Composable
internal fun PremiumMarketOverview(
    palette: PremiumHomePalette,
    breadth: HomeMarketBreadth,
    sentiment: HomePremiumSentiment
) {
    PremiumResponsivePair(
        forceHorizontal = true,
        first = {
            PremiumBreadthCard(
                palette = palette,
                breadth = breadth,
                modifier = Modifier.fillMaxWidth()
            )
        },
        second = {
            PremiumSentimentCard(
                palette = palette,
                sentiment = sentiment,
                reportedVolume = breadth.reportedVolume,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun PremiumBreadthCard(
    palette: PremiumHomePalette,
    breadth: HomeMarketBreadth,
    modifier: Modifier = Modifier
) {
    val total = breadth.advancing + breadth.declining + breadth.unchanged
    PremiumCardSurface(palette, modifier.heightIn(min = 196.dp)) {
        PremiumSectionHeader("Market breadth", palette)
        Spacer(Modifier.height(8.dp))
        PremiumBreadthGauge(
            palette = palette,
            breadth = breadth,
            modifier = Modifier.fillMaxWidth().height(95.dp)
        )
        Spacer(Modifier.height(8.dp))
        val denominator = total.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            Text(
                if (total == 0) "—" else ((breadth.declining * 100 / denominator).toString() + "%"),
                color = palette.danger,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (total == 0) "Coverage unavailable"
                else "Neutral " + (breadth.unchanged * 100 / denominator) + "%",
                color = palette.muted,
                fontSize = 9.5.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (total == 0) "—" else ((breadth.advancing * 100 / denominator).toString() + "%"),
                color = palette.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PremiumBreadthGauge(
    palette: PremiumHomePalette,
    breadth: HomeMarketBreadth,
    modifier: Modifier = Modifier
) {
    val total = breadth.advancing + breadth.declining + breadth.unchanged
    Box(modifier) {
        Canvas(Modifier.matchParentSize().padding(horizontal = 18.dp, vertical = 4.dp)) {
            val stroke = 14.dp.toPx()
            val arcSize = Size(size.width * 0.64f, size.height * 1.28f)
            val topLeft = Offset((size.width - arcSize.width) / 2f, size.height * 0.11f)
            drawArc(
                color = palette.border,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (total > 0) {
                val declineSweep = 180f * breadth.declining / total.toFloat()
                val unchangedSweep = 180f * breadth.unchanged / total.toFloat()
                val advanceSweep = 180f * breadth.advancing / total.toFloat()
                drawArc(
                    color = palette.danger,
                    startAngle = 180f,
                    sweepAngle = declineSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = palette.muted.copy(alpha = 0.45f),
                    startAngle = 180f + declineSweep,
                    sweepAngle = unchangedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = palette.primary,
                    startAngle = 180f + declineSweep + unchangedSweep,
                    sweepAngle = advanceSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                total.toString(),
                color = palette.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text("Total", color = palette.muted, fontSize = 10.sp)
        }
        Column(
            Modifier.align(Alignment.CenterStart),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                breadth.declining.toString(),
                color = palette.danger,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Decliners", color = palette.danger, fontSize = 8.5.sp)
        }
        Column(
            Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                breadth.advancing.toString(),
                color = palette.primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Gainers", color = palette.primary, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun PremiumSentimentCard(
    palette: PremiumHomePalette,
    sentiment: HomePremiumSentiment,
    reportedVolume: Long,
    modifier: Modifier = Modifier
) {
    val accent = when (sentiment.label) {
        "Positive" -> palette.primary
        "Cautious" -> palette.danger
        "Mixed" -> palette.amber
        else -> palette.muted
    }
    PremiumCardSurface(palette, modifier.heightIn(min = 196.dp)) {
        PremiumSectionHeader("Market sentiment", palette)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShowChart, null, tint = accent, modifier = Modifier.size(31.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sentiment.label,
                    color = accent,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    sentiment.detail,
                    color = palette.muted,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.raised,
            border = BorderStroke(1.dp, palette.border)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reported volume", color = palette.muted, fontSize = 8.5.sp)
                    Text(
                        if (reportedVolume > 0) formatHomeVolume(reportedVolume) else "Unavailable",
                        color = palette.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Box(Modifier.width(1.dp).height(32.dp).background(palette.border))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Breadth net", color = palette.muted, fontSize = 8.5.sp)
                    Text(
                        String.format(Locale.US, "%+d", sentiment.breadthNet),
                        color = when {
                            sentiment.breadthNet < 0 -> palette.danger
                            sentiment.breadthNet > 0 -> palette.primary
                            else -> palette.muted
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun PremiumWatchlistStrip(
    palette: PremiumHomePalette,
    stocks: List<Stock>,
    histories: Map<String, List<MyStocksCache.HistoryPoint>>,
    loading: Boolean,
    error: Boolean,
    openWatchlist: () -> Unit,
    openCompany: (Stock) -> Unit
) {
    PremiumCardSurface(palette, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumSectionHeader(
                title = "My watchlist",
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = openWatchlist, contentPadding = PaddingValues(0.dp)) {
                Text(
                    "Edit",
                    color = palette.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        when {
            error -> PremiumMessage("Your saved companies are temporarily unavailable.", palette)
            loading -> PremiumMessage("Loading your watchlist…", palette)
            stocks.isEmpty() -> {
                PremiumMessage("Add companies to build your watchlist.", palette)
                TextButton(onClick = openWatchlist, contentPadding = PaddingValues(0.dp)) {
                    Text("Choose companies →", color = palette.primary, fontSize = 11.sp)
                }
            }
            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 2.dp)
                ) {
                    items(stocks, key = { it.symbol }) { stock ->
                        PremiumWatchlistTile(
                            palette = palette,
                            stock = stock,
                            history = histories[stock.symbol].orEmpty(),
                            open = { openCompany(stock) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumWatchlistTile(
    palette: PremiumHomePalette,
    stock: Stock,
    history: List<MyStocksCache.HistoryPoint>,
    open: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(108.dp)
            .height(178.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = open),
        shape = RoundedCornerShape(16.dp),
        color = palette.raised,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            PremiumCompanyLogo(stock, 40)
            Spacer(Modifier.height(7.dp))
            Text(
                stock.name.ifBlank { stock.symbol },
                color = palette.text,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(stock.symbol, color = palette.muted, fontSize = 8.5.sp)
            Spacer(Modifier.height(5.dp))
            PremiumSparkline(
                values = history.map { it.close },
                tint = if (stock.change < 0) palette.danger else palette.primary,
                modifier = Modifier.fillMaxWidth().height(35.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                stock.price.takeIf { it.isFinite() && it > 0.0 }
                    ?.let(CompanyResearchPresentation::money) ?: "Price unavailable",
                color = palette.text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stock.change.takeIf { stock.changeAvailable && it.isFinite() }
                    ?.let(CompanyResearchPresentation::percent) ?: "Change unavailable",
                color = if (!stock.changeAvailable) palette.muted
                    else if (stock.change < 0) palette.danger
                    else palette.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
