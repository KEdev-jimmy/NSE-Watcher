package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.util.Locale
import ke.co.nsewatcher.data.MyStocksCache

internal val PracticeAmber = Color(0xFFFFCE71)
internal fun practiceMoney(value: Double) = if (value.isFinite()) String.format(Locale.US, "KSh %,.2f", value) else "Unavailable"
internal fun practiceTime(time: Long) = CompanyResearchPresentation.date(Instant.ofEpochMilli(time).toString())
internal fun practiceGain(value: Double) = (if (value > 0) "+" else if (value < 0) "−" else "") + practiceMoney(kotlin.math.abs(value))

@Composable internal fun PracticeIcon(icon: ImageVector, amber: Boolean = false) {
    val tint = if (amber) PracticeAmber else Color(0xFF8AE8B9)
    Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = Color.Transparent, border = BorderStroke(1.dp, tint.copy(alpha = 0.22f))) {
        Box(Modifier.background(Brush.linearGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.03f)))), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint)
        }
    }
}
@Composable internal fun PracticeBadge() {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent, border = BorderStroke(1.dp, ResearchGreen)) {
        Text("VIRTUAL MONEY", color = ResearchGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
    }
}
@Composable internal fun PracticeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = ResearchCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ResearchBorder)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { ResearchCaption(label); Text(value, color = ResearchText, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
}
@Composable internal fun PracticeLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = ResearchMuted, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(value, color = ResearchText, modifier = Modifier.widthIn(max = 170.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable internal fun PracticeLink(icon: ImageVector, title: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        Icon(icon, null, tint = ResearchGreen, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f), color = ResearchText); Icon(Icons.Outlined.ChevronRight, null, tint = ResearchMuted)
    }
}
@Composable
internal fun PracticeAdaptivePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.12f
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun PracticePortfolioHero(
    value: Double,
    gain: Double,
    provisional: Boolean,
    marketLabel: String,
    observationLabel: String
) {
    ResearchPanel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (provisional) "Last-known portfolio value" else "Practice portfolio value",
                        color = ResearchMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(7.dp))
                    PracticeBadge()
                }
                Text(
                    practiceMoney(value),
                    color = ResearchText,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    practiceGain(gain) + " since start",
                    color = if (provisional) PracticeAmber else researchChangeColor(gain),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ResearchRaised,
                border = BorderStroke(1.dp, ResearchBorder)
            ) {
                Text(
                    marketLabel,
                    color = if (marketLabel.startsWith("Market open")) ResearchGreen else ResearchMuted,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
        ResearchCaption("After recorded practice costs • Added virtual cash is excluded from gain.")
        if (provisional) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = PracticeAmber.copy(alpha = 0.07f),
                border = BorderStroke(1.dp, PracticeAmber.copy(alpha = 0.38f))
            ) {
                Row(
                    Modifier.padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        tint = PracticeAmber,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        "Some holdings are using saved observations that the current loaded market feed has not yet confirmed. Treat the displayed gain as provisional.",
                        color = ResearchMuted,
                        fontSize = 8.8.sp,
                        lineHeight = 12.5.sp
                    )
                }
            }
        }
        ResearchCaption(observationLabel)
    }
}

@Composable
internal fun PracticeAttentionCard(
    summary: PracticeAttentionSummary,
    onOrders: () -> Unit,
    onLearn: () -> Unit,
    onBrowse: () -> Unit
) {
    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PracticeIcon(
                if (summary.total == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Notifications,
                amber = summary.total > 0
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                ResearchTitle(if (summary.total == 0) "You're caught up" else "What needs your attention?")
                ResearchCaption(
                    if (summary.total == 0) {
                        "No pending orders or saved decisions currently need review."
                    } else {
                        "Use this queue to continue the decisions you already started."
                    }
                )
            }
        }

        if (summary.total == 0) {
            TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text("Explore another company →", color = ResearchGreen)
            }
        } else {
            if (summary.pendingOrders > 0) {
                PracticeAttentionRow(
                    icon = Icons.Outlined.Schedule,
                    title = "${summary.pendingOrders} pending order${if (summary.pendingOrders == 1) "" else "s"}",
                    detail = "See why each order is still waiting.",
                    action = onOrders
                )
            }
            if (summary.needsFirstReview > 0) {
                PracticeAttentionRow(
                    icon = Icons.Outlined.RateReview,
                    title = "${summary.needsFirstReview} decision${if (summary.needsFirstReview == 1) "" else "s"} need a first review",
                    detail = "Compare what you expected with what happened afterward.",
                    action = onLearn
                )
            }
            if (summary.newEvidence > 0) {
                PracticeAttentionRow(
                    icon = Icons.Outlined.NewReleases,
                    title = "New evidence for ${summary.newEvidence} reviewed decision${if (summary.newEvidence == 1) "" else "s"}",
                    detail = "Revisit the evidence without treating timing as causation.",
                    action = onLearn
                )
            }
        }
    }
}

@Composable
private fun PracticeAttentionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    action: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ResearchRaised,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = PracticeAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = ResearchText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = ResearchMuted, fontSize = 8.7.sp, lineHeight = 12.sp)
            }
            IconButton(onClick = action, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.ChevronRight, "Open", tint = ResearchGreen)
            }
        }
    }
}

@Composable internal fun PracticeJourney(state: PracticeState) {
    var range by rememberSaveable { mutableStateOf("All") }
    var metric by rememberSaveable { mutableStateOf("Value") }
    val cutoff = when (range) { "1W" -> 7L; "1M" -> 30L; "3M" -> 90L; else -> null }?.let { System.currentTimeMillis() - it * 86_400_000L }
    val rows = state.snapshots.filter { cutoff == null || it.time >= cutoff }
    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) { PracticeIcon(Icons.Outlined.ShowChart); ResearchTitle("Your journey") }
        MarketChoiceRow(listOf("1W", "1M", "3M", "All"), range) { range = it }
        MarketChoiceRow(listOf("Value", "Gain / loss"), metric) { metric = it }
        // Contributions stay in the value chart; profit uses contribution-adjusted snapshots.
        if (metric == "Value") {
            CompanyResearchChart(rows.map { MyStocksCache.HistoryPoint(it.value, Instant.ofEpochMilli(it.time).toString()) }, range, false, {}, title = "Portfolio value · KSh", allowZero = true)
            ResearchCaption("Account value includes added virtual cash. Snapshots begin when this version records a complete valuation.")
        } else {
            if (rows.isEmpty()) ResearchCaption("No complete valuations in this period yet.")
            rows.takeLast(6).reversed().forEach { PracticeLine(practiceTime(it.time), practiceGain(it.value - it.contributed)) }
            ResearchCaption("Gain = account value minus contributed cash. Added cash is not profit. This is not an annualised or time-weighted return.")
        }
        ResearchCaption("Total virtual contributions: ${practiceMoney(state.contributed)}")
    }
}
@Composable internal fun PracticeDividends() {
    ResearchTitle("Learn how dividends work")
    ResearchCaption("Track announcements before counting income.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(Icons.Outlined.Description to "Announced", Icons.Outlined.People to "Eligible", Icons.Outlined.AccountBalanceWallet to "Credited").forEach { (icon, text) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { PracticeIcon(icon); ResearchCaption(text) }
        }
    }
    ResearchPanel {
        ResearchBody("Virtual dividends credited"); Text("KSh 0.00", color = ResearchText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        ResearchCaption("Dividend simulation is not active. No dividend cash has been credited by this simulator.")
    }
    var explain by rememberSaveable { mutableStateOf(false) }
    ResearchPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { PracticeIcon(Icons.Outlined.Info, true); Text("Dividend data not connected", color = PracticeAmber, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) }
        ResearchBody("We cannot confirm upcoming payments or your eligibility yet.")
        OutlinedButton(onClick = { explain = !explain }, modifier = Modifier.fillMaxWidth()) { Text("How eligibility works") }
        if (explain) ResearchCaption("A verified announcement must identify the amount, eligibility dates, payment date and applicable taxes. Qualifying holdings and settlement must be established before a virtual payment is credited. An announcement alone does not make every holder eligible.")
    }
    ResearchTitle("Steps to understand dividends")
    listOf(Triple(Icons.Outlined.Description, "1. Check the announcement", "Amount per share and key dates."), Triple(Icons.Outlined.People, "2. Check eligibility", "Your qualifying holding and settlement matter."), Triple(Icons.Outlined.AccountBalanceWallet, "3. Wait for payment", "A future dividend simulator must use the verified payment date.")).forEach { (icon, title, body) ->
        ResearchPanel { Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { PracticeIcon(icon); Column(Modifier.weight(1f)) { ResearchBody(title); ResearchCaption(body) } } }
    }
    ResearchPanel { ResearchBody("A dividend is not free extra profit"); ResearchCaption("Share prices can adjust when a stock trades ex-dividend. Amounts, taxes and dates must be verified.") }
}

@Composable internal fun PracticeCompanyIcon(stock: Stock) {
    var failed by remember(stock.logoUrl) { mutableStateOf(false) }
    if (stock.logoUrl.isNullOrBlank() || failed) PracticeIcon(Icons.Outlined.Business)
    else Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = ResearchRaised) {
        AsyncImage(model = stock.logoUrl, contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.padding(5.dp), onError = { failed = true })
    }
}
