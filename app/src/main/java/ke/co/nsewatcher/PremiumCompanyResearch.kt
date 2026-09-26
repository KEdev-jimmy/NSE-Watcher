package ke.co.nsewatcher

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.domain.TechnicalStrengthComponent
import ke.co.nsewatcher.domain.TechnicalStrengthLabel
import ke.co.nsewatcher.domain.TechnicalStrengthResult

@Composable
internal fun PremiumCompanyTopBar(
    stock: Stock,
    watched: Boolean,
    onWatchToggle: (() -> Unit)?,
    back: () -> Unit
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) {
            Icon(Icons.Default.ArrowBack, "Back", tint = ResearchText)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onWatchToggle?.invoke() }, enabled = onWatchToggle != null) {
            Icon(
                if (watched) Icons.Default.Star else Icons.Default.StarBorder,
                if (watched) "Remove from watchlist" else "Add to watchlist",
                tint = if (watched) Color(0xFFF6C65B) else ResearchText
            )
        }
        IconButton(onClick = {
            val text = "${stock.name} (${stock.symbol}) on NSE Watcher"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Share company"))
            }
        }) {
            Icon(Icons.Default.Share, "Share company", tint = ResearchText)
        }
    }
}

@Composable
internal fun PremiumCompanyIdentity(
    stock: Stock,
    profile: CompanyIntelligenceCache.Profile,
    watched: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).background(ResearchRaised),
            contentAlignment = Alignment.Center
        ) {
            Text(stock.symbol.take(4), color = ResearchGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            val url = stock.logoUrl?.takeIf { it.isNotBlank() }
                ?: "https://mystocks.africa/logos/${stock.symbol.lowercase(Locale.US)}-ke.svg"
            var loaded by remember(url) { mutableStateOf(false) }
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(5.dp),
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false }
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stock.name,
                color = ResearchText,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(stock.symbol, color = ResearchText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (watched) {
                    PremiumCompanyPill("★ Watchlisted", ResearchGreen)
                }
                val sector = profile.sector.ifBlank { stock.sector.takeUnless { it.isBlank() || it == "Other" } ?: "Sector unavailable" }
                PremiumCompanyPill(sector, MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
private fun PremiumCompanyPill(text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Text(
            text,
            color = accent,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun PremiumCompanyTabs(selected: String, onSelected: (String) -> Unit) {
    val tabs = listOf("Overview", "Financials", "News", "Analysis", "About")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scroll = maxWidth < 355.dp || LocalDensity.current.fontScale > 1.12f
        if (scroll) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEach { label ->
                    PremiumCompanyTab(label, selected == label, Modifier.widthIn(min = 67.dp)) {
                        onSelected(label)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tabs.forEach { label ->
                    PremiumCompanyTab(label, selected == label, Modifier.weight(1f)) {
                        onSelected(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumCompanyTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(39.dp).clip(RoundedCornerShape(9.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) ResearchGreen.copy(alpha = 0.13f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) ResearchGreen else ResearchBorder.copy(alpha = 0.55f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) ResearchGreen else ResearchText,
                fontSize = if (label == "Financials") 8.5.sp else 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun PremiumCompanyOverview(
    stock: Stock,
    session: CompanyResearchPresentation.Session,
    market: MyStocksCache.MarketStatus,
    intelligence: CompanyIntelligenceCache.Result,
    news: List<NewsItem>,
    technicalHistory: MyStocksCache.HistoryResult,
    technicalStrength: TechnicalStrengthResult?,
    technicalLoading: Boolean,
    technicalError: String?,
    selectedRange: String,
    onRange: (String) -> Unit,
    chart: MyStocksCache.HistoryResult,
    chartLoading: Boolean,
    oneYearChart: MyStocksCache.HistoryResult,
    rangeReturns: Map<String, Double?>,
    showChartGrid: Boolean,
    onRefresh: () -> Unit,
    openPractice: () -> Unit,
    openAnalysis: () -> Unit
) {
    PremiumPriceHistoryCard(
        stock = stock,
        session = session,
        market = market,
        selectedRange = selectedRange,
        onRange = onRange,
        chart = chart,
        chartLoading = chartLoading,
        showChartGrid = showChartGrid,
        onRefresh = onRefresh
    )

    PremiumTodayGlanceCard(session)

    PremiumTechnicalStrengthCard(
        result = technicalStrength,
        history = technicalHistory,
        loading = technicalLoading,
        error = technicalError,
        onRefresh = onRefresh
    )

    PremiumKeyStatisticsCard(
        intelligence = intelligence,
        oneYearChart = oneYearChart
    )

    PremiumReturnRow(rangeReturns)

    PremiumWhatShouldIKnow(
        stock = stock,
        session = session,
        intelligence = intelligence,
        news = news,
        onAnalysis = openAnalysis
    )

    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.School, null, tint = ResearchGreen)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("Practice this company", color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Test an idea with virtual money after reviewing the evidence.",
                    color = ResearchMuted,
                    fontSize = 9.5.sp
                )
            }
            TextButton(onClick = openPractice) {
                Text("Practice →", color = ResearchGreen, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PremiumPriceHistoryCard(
    stock: Stock,
    session: CompanyResearchPresentation.Session,
    market: MyStocksCache.MarketStatus,
    selectedRange: String,
    onRange: (String) -> Unit,
    chart: MyStocksCache.HistoryResult,
    chartLoading: Boolean,
    showChartGrid: Boolean,
    onRefresh: () -> Unit
) {
    ResearchPanel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    CompanyResearchPresentation.money(session.latest),
                    color = ResearchText,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    session.dailyChange?.let { CompanyResearchPresentation.percent(it) }
                        ?: "Daily change unavailable",
                    color = researchChangeColor(session.dailyChange),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "As of ${CompanyResearchPresentation.date(session.observedAt)}",
                    color = ResearchMuted,
                    fontSize = 9.5.sp
                )
                Text(
                    stock.delayMinutes?.takeIf { it > 0 }?.let { "Delayed by $it minutes" }
                        ?: "Provider delay unavailable",
                    color = ResearchMuted,
                    fontSize = 9.sp
                )
            }
            val marketLabel = when {
                !market.isKnown -> "Status unavailable"
                market.isOpen -> "Market Open"
                else -> "Market Closed"
            }
            Surface(
                color = (if (market.isOpen) ResearchGreen else ResearchMuted).copy(alpha = 0.10f),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(
                    1.dp,
                    (if (market.isOpen) ResearchGreen else ResearchMuted).copy(alpha = 0.45f)
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(7.dp).background(
                            if (market.isOpen) ResearchGreen else ResearchMuted,
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        marketLabel,
                        color = if (market.isOpen) ResearchGreen else ResearchMuted,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        CompanyResearchChart(
            points = chart.points,
            range = selectedRange,
            loading = chartLoading,
            retry = onRefresh,
            showGrid = showChartGrid
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            CompanyResearchPresentation.ranges.forEach { range ->
                val active = selectedRange == range
                Surface(
                    modifier = Modifier.widthIn(min = 38.dp).height(34.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Tab) { onRange(range) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) ResearchGreen else ResearchRaised,
                    border = BorderStroke(1.dp, if (active) ResearchGreen else Color.Transparent)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            range,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else ResearchText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumTodayGlanceCard(session: CompanyResearchPresentation.Session) {
    PremiumSectionCard(
        icon = Icons.Default.Insights,
        accent = MaterialTheme.colorScheme.tertiary,
        title = "Today's at a glance"
    ) {
        PremiumTwoByTwo(
            listOf(
                "Previous close" to CompanyResearchPresentation.money(session.previousClose),
                "Day high" to CompanyResearchPresentation.money(session.high),
                "Today's change" to (session.dailyChange?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable"),
                "Day low" to CompanyResearchPresentation.money(session.low)
            ),
            changeIndex = 2
        )
    }
}

@Composable
private fun PremiumTechnicalStrengthCard(
    result: TechnicalStrengthResult?,
    history: MyStocksCache.HistoryResult,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    var expanded by remember(result?.score, result?.confidenceScore, history.observedAt) {
        mutableStateOf(false)
    }
    val label = result?.label
    val accent = when (label) {
        TechnicalStrengthLabel.STRONG_BULLISH,
        TechnicalStrengthLabel.BULLISH -> ResearchGreen
        TechnicalStrengthLabel.STRONG_BEARISH,
        TechnicalStrengthLabel.BEARISH -> ResearchRed
        TechnicalStrengthLabel.NEUTRAL -> MaterialTheme.colorScheme.tertiary
        else -> ResearchMuted
    }

    PremiumSectionCard(
        icon = Icons.Default.QueryStats,
        accent = accent,
        title = "Technical strength",
        subtitle = "Trend, momentum and trading participation from verified daily observations"
    ) {
        when {
            loading && result == null -> {
                ResearchLoading("Calculating technical strength…")
            }

            result == null -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ResearchRaised,
                    border = BorderStroke(1.dp, ResearchBorder.copy(alpha = 0.75f))
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Technical strength unavailable",
                            color = ResearchText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            error ?: "Verified daily history was not returned, so NSE Watcher will not estimate a score.",
                            color = ResearchMuted,
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
                TextButton(onClick = onRefresh) {
                    Text("Try again", color = ResearchGreen, fontSize = 10.sp)
                }
            }

            else -> {
                PremiumTechnicalStrengthGauge(
                    score = result.score,
                    label = result.label.displayName,
                    confidenceLabel = result.confidenceLabel.displayName,
                    confidenceScore = result.confidenceScore
                )

                Text(
                    result.summary,
                    color = ResearchText,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )

                PremiumTechnicalComponentGrid(
                    trend = result.trend,
                    momentum = result.momentum,
                    participation = result.participation
                )

                val observedAt = history.observedAt.ifBlank {
                    history.dataQuality.lastObservationAt
                }
                val source = history.source.ifBlank { "MyStocks Africa" }
                val delay = history.delayMinutes?.takeIf { it >= 0 }
                    ?.let { " · ${it}-min delayed" }
                    .orEmpty()
                Text(
                    "Daily observations · $source$delay" +
                        if (observedAt.isNotBlank()) "\nAs of ${CompanyResearchPresentation.date(observedAt)}" else "",
                    color = ResearchMuted,
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp
                )

                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (expanded) "Hide why ↑" else "Why this rating? ↓",
                        color = ResearchGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (expanded) {
                    HorizontalDivider(color = ResearchBorder.copy(alpha = 0.75f))
                    Text(
                        "Why",
                        color = ResearchText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    result.reasons.forEach { reason ->
                        PremiumTechnicalBullet(reason)
                    }

                    val ohlcCoverage = history.dataQuality.ohlcCoveragePct
                    val volumeCoverage = history.dataQuality.volumeCoveragePct
                    if (
                        history.dataQuality.candleCount > 0 ||
                        ohlcCoverage > 0.0 ||
                        volumeCoverage > 0.0
                    ) {
                        Text(
                            "Data coverage",
                            color = ResearchText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "OHLC ${String.format(Locale.US, "%.1f", ohlcCoverage)}% · " +
                                "Volume ${String.format(Locale.US, "%.1f", volumeCoverage)}% · " +
                                "${history.dataQuality.candleCount} daily observations",
                            color = ResearchMuted,
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
                    }

                    if (result.cautions.isNotEmpty()) {
                        Text(
                            "Keep in mind",
                            color = ResearchText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        result.cautions.forEach { caution ->
                            PremiumTechnicalBullet(caution, caution = true)
                        }
                    }
                }

                Text(
                    "Technical conditions only — not a buy/sell instruction.",
                    color = ResearchMuted,
                    fontSize = 8.5.sp,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PremiumTechnicalStrengthGauge(
    score: Int?,
    label: String,
    confidenceLabel: String,
    confidenceScore: Int
) {
    val animatedScore = remember { Animatable(50f) }
    LaunchedEffect(score) {
        val target = score?.coerceIn(0, 100)?.toFloat() ?: 50f
        if (score != null) {
            animatedScore.snapTo(50f)
            animatedScore.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 650)
            )
        } else {
            animatedScore.snapTo(50f)
        }
    }

    val safeScore = score?.coerceIn(0, 100)
    val labelColor = when {
        safeScore == null -> ResearchMuted
        safeScore >= 60 -> ResearchGreen
        safeScore < 40 -> ResearchRed
        else -> MaterialTheme.colorScheme.tertiary
    }
    val zoneColors = listOf(
        ResearchRed,
        ResearchRed.copy(alpha = 0.58f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.78f),
        ResearchGreen.copy(alpha = 0.62f),
        ResearchGreen
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 178.dp)
            ) {
                val density = LocalDensity.current
                val arcStrokePx = with(density) { 13.dp.toPx() }
                val needleStrokePx = with(density) { 2.5.dp.toPx() }

                Canvas(
                    modifier = Modifier.fillMaxWidth().height(126.dp).align(Alignment.TopCenter)
                ) {
                    val center = Offset(size.width / 2f, size.height - 7f)
                    val radius = minOf(
                        (size.width / 2f) - arcStrokePx,
                        size.height - arcStrokePx - 9f
                    ).coerceAtLeast(1f)
                    val arcSize = Size(radius * 2f, radius * 2f)
                    val arcTopLeft = Offset(center.x - radius, center.y - radius)

                    val zoneSweep = 36f
                    zoneColors.forEachIndexed { index, zoneColor ->
                        drawArc(
                            color = zoneColor,
                            startAngle = 180f + (index * zoneSweep),
                            sweepAngle = zoneSweep - 2.4f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = arcStrokePx, cap = StrokeCap.Round)
                        )
                    }

                    if (safeScore != null) {
                        val angle = 180f + ((animatedScore.value / 100f) * 180f)
                        val radians = Math.toRadians(angle.toDouble())
                        val needleLength = radius - (arcStrokePx * 0.9f)
                        val end = Offset(
                            x = center.x + (cos(radians) * needleLength).toFloat(),
                            y = center.y + (sin(radians) * needleLength).toFloat()
                        )

                        drawLine(
                            color = labelColor,
                            start = center,
                            end = end,
                            strokeWidth = needleStrokePx,
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = ResearchCard,
                            radius = arcStrokePx * 0.56f,
                            center = center
                        )
                        drawCircle(
                            color = labelColor,
                            radius = arcStrokePx * 0.34f,
                            center = center
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        safeScore?.toString() ?: "—",
                        color = labelColor,
                        fontSize = 29.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (safeScore == null) "Score unavailable" else label,
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Data confidence: $confidenceLabel · $confidenceScore/100",
                        color = ResearchMuted,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Strong\nBearish",
                    color = ResearchRed,
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp
                )
                Text(
                    "Neutral",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 7.5.sp
                )
                Text(
                    "Strong\nBullish",
                    color = ResearchGreen,
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun PremiumTechnicalComponentGrid(
    trend: TechnicalStrengthComponent,
    momentum: TechnicalStrengthComponent,
    participation: TechnicalStrengthComponent
) {
    val items = listOf(
        "Trend" to trend,
        "Momentum" to momentum,
        "Participation" to participation
    )

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.10f
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items.forEach { (label, component) ->
                    PremiumTechnicalComponentTile(
                        label = label,
                        component = component,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items.forEach { (label, component) ->
                    PremiumTechnicalComponentTile(
                        label = label,
                        component = component,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumTechnicalComponentTile(
    label: String,
    component: TechnicalStrengthComponent,
    modifier: Modifier
) {
    val score = component.score
    val accent = when {
        score == null -> ResearchMuted
        score >= 60 -> ResearchGreen
        score < 40 -> ResearchRed
        else -> MaterialTheme.colorScheme.tertiary
    }
    val state = when {
        score == null -> "Unavailable"
        score >= 80 -> "Strong"
        score >= 60 -> "Positive"
        score >= 40 -> "Mixed"
        score >= 20 -> "Negative"
        else -> "Weak"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder.copy(alpha = 0.75f))
    ) {
        Column(
            Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, color = ResearchMuted, fontSize = 8.5.sp, maxLines = 1)
            Text(
                score?.let { "$it/100" } ?: "—",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                state,
                color = ResearchMuted,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PremiumTechnicalBullet(text: String, caution: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (caution) Icons.Default.Info else Icons.Default.CheckCircle,
            null,
            tint = if (caution) MaterialTheme.colorScheme.tertiary else ResearchGreen,
            modifier = Modifier.size(14.dp).padding(top = 1.dp)
        )
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = ResearchMuted,
            fontSize = 9.sp,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun PremiumKeyStatisticsCard(
    intelligence: CompanyIntelligenceCache.Result,
    oneYearChart: MyStocksCache.HistoryResult
) {
    val yearPrices = remember(oneYearChart.points) {
        oneYearChart.points.map { it.close }.filter { it.isFinite() && it > 0.0 }
    }
    PremiumSectionCard(
        icon = Icons.Default.BusinessCenter,
        accent = MaterialTheme.colorScheme.tertiary,
        title = "Key statistics"
    ) {
        PremiumTwoByTwo(
            listOf(
                "Market value" to intelligence.profile.marketCap.ifBlank { "Unavailable" },
                "P/E ratio" to intelligence.profile.pe.ifBlank { "Unavailable" },
                "52-week high" to (yearPrices.maxOrNull()?.let { CompanyResearchPresentation.money(it) } ?: "Unavailable"),
                "52-week low" to (yearPrices.minOrNull()?.let { CompanyResearchPresentation.money(it) } ?: "Unavailable")
            )
        )
        if (yearPrices.isEmpty()) {
            Text(
                "52-week high/low require sufficient dated one-year history.",
                color = ResearchMuted,
                fontSize = 8.5.sp
            )
        }
    }
}

@Composable
private fun PremiumTwoByTwo(
    facts: List<Pair<String, String>>,
    changeIndex: Int = -1
) {
    facts.chunked(2).forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { itemIndex, (label, value) ->
                val index = rowIndex * 2 + itemIndex
                Surface(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = ResearchRaised,
                    border = BorderStroke(1.dp, ResearchBorder.copy(alpha = 0.75f))
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, color = ResearchMuted, fontSize = 9.sp)
                        Text(
                            value,
                            color = if (index == changeIndex) {
                                value.removeSuffix("%").toDoubleOrNull()?.let { researchChangeColor(it) } ?: ResearchText
                            } else ResearchText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        if (rowIndex < 1) Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PremiumReturnRow(rangeReturns: Map<String, Double?>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labels = listOf("1M", "3M", "1Y", "3Y")
        val stack = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.12f
        if (stack) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(labels.size) { index ->
                    PremiumReturnTile(labels[index], rangeReturns[labels[index]], Modifier.width(78.dp))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                labels.forEach { label ->
                    PremiumReturnTile(label, rangeReturns[label], Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PremiumReturnTile(label: String, value: Double?, modifier: Modifier) {
    val accent = researchChangeColor(value)
    Surface(
        modifier,
        shape = RoundedCornerShape(11.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.padding(vertical = 9.dp, horizontal = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value?.let { CompanyResearchPresentation.percent(it) } ?: "—",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(label, color = ResearchMuted, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun PremiumWhatShouldIKnow(
    stock: Stock,
    session: CompanyResearchPresentation.Session,
    intelligence: CompanyIntelligenceCache.Result,
    news: List<NewsItem>,
    onAnalysis: () -> Unit
) {
    val dividend = remember(intelligence.dividends) { latestDividend(intelligence.dividends) }
    val resultStory = remember(news) { news.firstOrNull(::premiumIsResults) }
    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFF6C65B))
            Spacer(Modifier.width(8.dp))
            Text("What should I know?", Modifier.weight(1f), color = ResearchText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onAnalysis) {
                Text("See all", color = MaterialTheme.colorScheme.tertiary, fontSize = 9.5.sp)
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scroll = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.12f
            if (scroll) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumInsightMiniCard(
                        "Price movement",
                        Icons.Default.TrendingUp,
                        ResearchGreen,
                        CompanyResearchPresentation.meaning(session.dailyChange),
                        Modifier.width(145.dp)
                    )
                    PremiumInsightMiniCard(
                        "Latest results",
                        Icons.Default.Description,
                        MaterialTheme.colorScheme.tertiary,
                        latestResultText(intelligence, resultStory),
                        Modifier.width(145.dp)
                    )
                    PremiumInsightMiniCard(
                        "Dividend",
                        Icons.Default.CalendarMonth,
                        Color(0xFFF0B531),
                        dividendText(dividend),
                        Modifier.width(145.dp)
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PremiumInsightMiniCard(
                        "Price movement",
                        Icons.Default.TrendingUp,
                        ResearchGreen,
                        CompanyResearchPresentation.meaning(session.dailyChange),
                        Modifier.weight(1f)
                    )
                    PremiumInsightMiniCard(
                        "Latest results",
                        Icons.Default.Description,
                        MaterialTheme.colorScheme.tertiary,
                        latestResultText(intelligence, resultStory),
                        Modifier.weight(1f)
                    )
                    PremiumInsightMiniCard(
                        "Dividend",
                        Icons.Default.CalendarMonth,
                        Color(0xFFF0B531),
                        dividendText(dividend),
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumInsightMiniCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    text: String,
    modifier: Modifier
) {
    Surface(
        modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(11.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(title, color = ResearchText, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text,
                color = ResearchMuted,
                fontSize = 8.5.sp,
                lineHeight = 12.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun PremiumCompanyFinancials(
    result: CompanyIntelligenceCache.Result,
    loading: Boolean,
    onRefresh: () -> Unit,
    onMetric: (ResearchMetric) -> Unit
) {
    when {
        loading -> ResearchPanel { ResearchLoading("Loading financial reports...") }
        result.error != null -> ResearchPanel {
            ResearchTitle("Company information unavailable")
            ResearchCaption("The company service did not return financial information. Market observations remain separate.")
            TextButton(onClick = onRefresh) { Text("Try again", color = ResearchGreen) }
        }
        else -> {
            PremiumKeyFinancialNumbers(result, onMetric)
            PremiumFinancialTrend(result)
            PremiumFinancialChange(result)
            PremiumKeyRatios(result, onMetric)
            if (result.partial) {
                ResearchCaption("Partial coverage · Some company fields are unavailable.")
            }
            ResearchPanel {
                Text(
                    "Provider units: ${result.profile.financialUnit.ifBlank { "Not supplied" }}",
                    color = ResearchMuted,
                    fontSize = 9.5.sp
                )
                Text(
                    "Reported period: ${result.profile.financialPeriod.ifBlank { "Unavailable" }}",
                    color = ResearchMuted,
                    fontSize = 9.5.sp
                )
                if (result.profile.financialProviderUpdatedAt.isNotBlank()) {
                    ResearchCaption("Provider updated: ${CompanyResearchPresentation.date(result.profile.financialProviderUpdatedAt)}")
                }
                if (result.profile.financialPageCheckedAt.isNotBlank()) {
                    ResearchCaption("Source checked: ${CompanyResearchPresentation.date(result.profile.financialPageCheckedAt)}")
                }
                Text(
                    "Tap a financial figure or ratio for its definition, reporting period, source and any source conflict.",
                    color = ResearchMuted,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun PremiumKeyFinancialNumbers(
    result: CompanyIntelligenceCache.Result,
    onMetric: (ResearchMetric) -> Unit
) {
    val metrics = remember(result.profile) { researchMetrics(result.profile).associateBy { it.key } }
    val dividend = remember(result.dividends) { latestDividend(result.dividends) }

    PremiumSectionCard(
        icon = Icons.Default.AccountBalance,
        accent = ResearchGreen,
        title = "Key financial numbers",
        subtitle = "Latest reported period: ${result.profile.financialPeriod.ifBlank { "Unavailable" }}"
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumFinancialNumberTile(
                "Revenue",
                metrics["revenue"]?.value ?: "Unavailable",
                growthText(result.profile.revenueGrowth),
                Icons.Default.BarChart,
                ResearchGreen,
                Modifier.weight(1f)
            ) { metrics["revenue"]?.let(onMetric) }
            PremiumFinancialNumberTile(
                "Net profit",
                metrics["profit"]?.value ?: "Unavailable",
                growthText(result.profile.profitGrowth),
                Icons.Default.Paid,
                ResearchGreen,
                Modifier.weight(1f)
            ) { metrics["profit"]?.let(onMetric) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumFinancialNumberTile(
                "Earnings per share (EPS)",
                metrics["eps"]?.value ?: "Unavailable",
                growthText(result.profile.epsGrowth),
                Icons.Default.ShowChart,
                ResearchGreen,
                Modifier.weight(1f)
            ) { metrics["eps"]?.let(onMetric) }
            PremiumFinancialNumberTile(
                "Dividend (recent)",
                dividend?.amount?.ifBlank { "Amount unavailable" } ?: "Unavailable",
                dividend?.let { it.type.ifBlank { it.status.ifBlank { "Dividend record" } } } ?: "No record returned",
                Icons.Default.CalendarMonth,
                Color(0xFFF0B531),
                Modifier.weight(1f),
                onClick = null
            )
        }
    }
}

@Composable
private fun PremiumFinancialNumberTile(
    title: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = if (onClick != null) modifier.clip(RoundedCornerShape(11.dp)).clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(11.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f))
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    color = ResearchText,
                    fontSize = 8.8.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
            }
            Text(
                value,
                color = ResearchText,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2
            )
            Text(
                detail,
                color = if (detail.contains("+") || detail.startsWith("Up", true)) ResearchGreen else ResearchMuted,
                fontSize = 8.7.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PremiumFinancialTrend(result: CompanyIntelligenceCache.Result) {
    PremiumSectionCard(
        icon = Icons.Default.StackedBarChart,
        accent = MaterialTheme.colorScheme.tertiary,
        title = "Financial performance trend",
        subtitle = "Reported revenue and net profit by returned period"
    ) {
        if (result.financialHistory.size < 2) {
            ResearchCaption("Comparable historical statements were not returned.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text("Revenue", color = ResearchMuted, fontSize = 8.5.sp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(8.dp).background(ResearchGreen, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text("Net profit", color = ResearchMuted, fontSize = 8.5.sp)
            }
            FinancialTrendBars(
                points = result.financialHistory.takeLast(5),
                modifier = Modifier.fillMaxWidth().height(170.dp)
            )
            Text(
                "Bars use provider-returned figures and are scaled within this chart. Compare like-for-like periods and units.",
                color = ResearchMuted,
                fontSize = 8.5.sp,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun FinancialTrendBars(
    points: List<CompanyIntelligenceCache.FinancialPoint>,
    modifier: Modifier
) {
    val parsed = remember(points) {
        points.map { point ->
            Triple(point, parseFinancialNumber(point.revenue), parseFinancialNumber(point.profit))
        }
    }
    val maximum = parsed.flatMap { listOfNotNull(it.second, it.third) }
        .maxOfOrNull { abs(it) }?.takeIf { it > 0.0 }

    if (maximum == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            ResearchCaption("Numeric revenue/profit history is unavailable.")
        }
        return
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        parsed.forEach { (point, revenue, profit) ->
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    FinancialHistoryBar(revenue, maximum, MaterialTheme.colorScheme.tertiary)
                    FinancialHistoryBar(profit, maximum, ResearchGreen)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    point.period.ifBlank { "—" }.take(9),
                    color = ResearchMuted,
                    fontSize = 7.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RowScope.FinancialHistoryBar(value: Double?, maximum: Double, positiveColor: Color) {
    if (value == null) {
        Spacer(Modifier.width(11.dp))
        return
    }
    val fraction = (abs(value) / maximum).toFloat().coerceIn(0.03f, 1f)
    Box(
        Modifier.width(11.dp)
            .fillMaxHeight(fraction)
            .background(
                if (value < 0.0) ResearchRed else positiveColor,
                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
            )
    )
}

@Composable
private fun PremiumFinancialChange(result: CompanyIntelligenceCache.Result) {
    val history = result.financialHistory
    val previous = history.getOrNull(history.lastIndex - 1)
    val latest = history.lastOrNull()
    val revenueChange = financialDelta(previous?.revenue, latest?.revenue) ?: parsePercentage(result.profile.revenueGrowth)
    val profitChange = financialDelta(previous?.profit, latest?.profit) ?: parsePercentage(result.profile.profitGrowth)
    val epsChange = financialDelta(previous?.eps, latest?.eps) ?: parsePercentage(result.profile.epsGrowth)

    ResearchPanel {
        Text(
            "Since the previous reported period",
            color = ResearchText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FinancialDeltaTile("Revenue", revenueChange, Modifier.weight(1f))
            FinancialDeltaTile("Net profit", profitChange, Modifier.weight(1f))
            FinancialDeltaTile("EPS", epsChange, Modifier.weight(1f))
        }
        Text(
            financialChangeSummary(revenueChange, profitChange, epsChange),
            color = ResearchMuted,
            fontSize = 9.5.sp,
            lineHeight = 14.sp
        )
        if (previous != null && latest != null) {
            Text(
                "Comparing ${previous.period.ifBlank { "previous period" }} → ${latest.period.ifBlank { "latest period" }}",
                color = ResearchMuted,
                fontSize = 8.5.sp
            )
        }
    }
}

@Composable
private fun FinancialDeltaTile(label: String, value: Double?, modifier: Modifier) {
    val color = researchChangeColor(value)
    Surface(
        modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            Modifier.padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                when {
                    value == null -> Icons.Default.Remove
                    value > 0 -> Icons.Default.ArrowUpward
                    value < 0 -> Icons.Default.ArrowDownward
                    else -> Icons.Default.Remove
                },
                null,
                tint = color,
                modifier = Modifier.size(17.dp)
            )
            Text(label, color = ResearchMuted, fontSize = 8.5.sp, maxLines = 1)
            Text(
                value?.let { CompanyResearchPresentation.percent(it) } ?: "Unavailable",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PremiumKeyRatios(
    result: CompanyIntelligenceCache.Result,
    onMetric: (ResearchMetric) -> Unit
) {
    val metrics = remember(result.profile) { researchMetrics(result.profile).associateBy { it.key } }
    val primaryKeys = listOf(
        Triple("roe", "ROE", Icons.Default.TrendingUp),
        Triple("pe", "P/E ratio", Icons.Default.QueryStats),
        Triple("pb", "P/B ratio", Icons.Default.Layers),
        Triple("debtToEquity", "Debt/Equity", Icons.Default.Balance)
    )
    PremiumSectionCard(
        icon = Icons.Default.Tune,
        accent = MaterialTheme.colorScheme.tertiary,
        title = "Key ratios (latest)"
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.12f
            if (twoColumns) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    primaryKeys.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (key, label, icon) ->
                                RatioTile(label, metrics[key], icon, Modifier.weight(1f), onMetric)
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    primaryKeys.forEach { (key, label, icon) ->
                        RatioTile(label, metrics[key], icon, Modifier.weight(1f), onMetric)
                    }
                }
            }
        }

        val extras = listOfNotNull(metrics["margin"], metrics["dividendYield"], metrics["marketCap"])
        if (extras.isNotEmpty()) {
            HorizontalDivider(color = ResearchBorder)
            Text("More financial metrics", color = ResearchMuted, fontSize = 9.sp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                extras.forEach { metric ->
                    AssistChip(
                        onClick = { onMetric(metric) },
                        label = { Text("${metric.label}: ${metric.value}", fontSize = 8.5.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = ResearchRaised,
                            labelColor = ResearchText
                        ),
                        border = BorderStroke(1.dp, ResearchBorder)
                    )
                }
            }
        }
    }
}

@Composable
private fun RatioTile(
    label: String,
    metric: ResearchMetric?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onMetric: (ResearchMetric) -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 90.dp).clip(RoundedCornerShape(11.dp))
            .clickable(enabled = metric != null) { metric?.let(onMetric) },
        shape = RoundedCornerShape(11.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(19.dp))
            Text(label, color = ResearchMuted, fontSize = 8.3.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(
                metric?.value ?: "Unavailable",
                color = ResearchText,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun PremiumSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(accent.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ResearchText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, color = ResearchMuted, fontSize = 9.sp, lineHeight = 12.sp)
                }
            }
        }
        content()
    }
}

private fun latestDividend(items: List<CompanyIntelligenceCache.Dividend>): CompanyIntelligenceCache.Dividend? =
    items.maxByOrNull { dividend ->
        listOf(dividend.exDate, dividend.paymentDate, dividend.declaredDate)
            .mapNotNull { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            .maxOrNull() ?: LocalDate.MIN
    }

private fun dividendText(dividend: CompanyIntelligenceCache.Dividend?): String = when {
    dividend == null -> "No dividend record was returned by the current company source."
    dividend.amount.isNotBlank() -> "Recent dividend: ${dividend.amount}. ${dividend.status.ifBlank { "Check the supplied dates." }}"
    else -> "A dividend record is available, but its amount was not supplied."
}

private fun latestResultText(
    result: CompanyIntelligenceCache.Result,
    resultStory: NewsItem?
): String {
    if (resultStory != null) return resultStory.title
    val period = result.profile.financialPeriod
    val revenue = growthText(result.profile.revenueGrowth)
    val profit = growthText(result.profile.profitGrowth)
    return when {
        period.isNotBlank() && (revenue != "Change unavailable" || profit != "Change unavailable") ->
            "$period: revenue $revenue; profit $profit."
        period.isNotBlank() -> "Latest returned financial period: $period."
        else -> "No recent results period was returned by the current source."
    }
}

private fun growthText(raw: String): String {
    val value = parsePercentage(raw) ?: return "Change unavailable"
    return CompanyResearchPresentation.percent(value)
}

private fun parseFinancialNumber(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    return Regex("[-+]?\\d+(?:[,.]\\d+)*")
        .find(raw.replace(" ", ""))?.value
        ?.replace(",", "")
        ?.toDoubleOrNull()
}

private fun parsePercentage(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    return Regex("[-+]?\\d+(?:\\.\\d+)?").find(raw)?.value?.toDoubleOrNull()
}

private fun financialDelta(previousRaw: String?, latestRaw: String?): Double? {
    val previous = parseFinancialNumber(previousRaw) ?: return null
    val latest = parseFinancialNumber(latestRaw) ?: return null
    if (previous == 0.0) return null
    return ((latest - previous) / abs(previous)) * 100.0
}

private fun financialChangeSummary(revenue: Double?, profit: Double?, eps: Double?): String {
    val known = listOf("Revenue" to revenue, "profit" to profit, "EPS" to eps).filter { it.second != null }
    if (known.isEmpty()) return "Comparable numeric values were not returned for the latest two periods."
    val up = known.filter { (it.second ?: 0.0) > 0 }.map { it.first }
    val down = known.filter { (it.second ?: 0.0) < 0 }.map { it.first }
    return buildString {
        if (up.isNotEmpty()) append(up.joinToString(", ") + " increased")
        if (down.isNotEmpty()) {
            if (isNotEmpty()) append(", while ")
            append(down.joinToString(", ") + " decreased")
        }
        if (isEmpty()) append("The available comparable figures were unchanged")
        append(" versus the preceding returned period.")
    }
}
