package ke.co.nsewatcher

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MovementIntelligenceCache
import ke.co.nsewatcher.domain.EvidenceAdapters
import ke.co.nsewatcher.data.MyStocksCache
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val MovementGreen = Color(0xFF00A859)
private val MovementLight = Color(0xFFE9F8F0)
private val MovementText = Color(0xFF12231B)
private val MovementMuted = Color(0xFF6C7A72)
private val MovementBorder = Color(0xFFE1EAE5)
private val MovementRed = Color(0xFFE04444)
private val MovementAmber = Color(0xFFB7791F)

@Composable
fun WhyStockMovingSection(symbol: String) {
    var result by remember(symbol) { mutableStateOf(MovementIntelligenceCache.Result()) }
    var loading by remember(symbol) { mutableStateOf(true) }
    var day by remember(symbol) { mutableStateOf(MyStocksCache.HistoryResult()) }
    var market by remember(symbol) { mutableStateOf(MyStocksCache.MarketStatus()) }
    var dayLoading by remember(symbol) { mutableStateOf(true) }

    LaunchedEffect(symbol) {
        loading = true
        result = MovementIntelligenceCache.load(symbol)
        loading = false
    }

    LaunchedEffect(symbol) {
        dayLoading = true
        day = MyStocksCache.loadHistoryDetails(symbol, "1D")
        market = MyStocksCache.loadMarketStatus()
        dayLoading = false
    }

    DailyCompanyOverview(symbol, day, market, dayLoading)
    Spacer(Modifier.height(12.dp))

    Text("Why is this stock moving?", color = MovementText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    Text("Evidence around the latest price movement", color = MovementMuted, fontSize = 9.sp)
    Spacer(Modifier.height(7.dp))

    Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MovementGreen)
                    Spacer(Modifier.size(9.dp))
                    Text("Checking price movement and dated company evidence…", color = MovementMuted, fontSize = 10.sp)
                }
                result.error != null -> Text(result.error.orEmpty(), color = MovementMuted, fontSize = 10.sp)
                else -> {
                    val move = result.move
                    if (move == null) {
                        Text("A reliable movement window is not available yet.", color = MovementMuted, fontSize = 10.sp)
                    } else {
                        Text(move.change.ifBlank { "Movement available" }, color = if (move.change.startsWith("-")) MovementRed else MovementGreen, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(movementWindow(move), color = MovementMuted, fontSize = 9.sp)
                        Spacer(Modifier.height(9.dp))
                        Text(result.summary.ifBlank { "No movement explanation is available from the current evidence." }, color = MovementText, fontSize = 11.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(12.dp))

                        val evidence = result.evidence.take(5)
                        val normalizedEvidence = EvidenceAdapters.fromMovementResult(result).evidence.take(5)
                        if (evidence.isEmpty()) {
                            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MovementLight) {
                                Text("No dated company event was found close enough to the movement to link it as evidence.", Modifier.padding(11.dp), color = MovementText, fontSize = 10.sp)
                            }
                        } else {
                            Text("What we found", color = MovementText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(5.dp))
                            evidence.forEachIndexed { index, item ->
                                MovementEvidenceRow(item, normalizedEvidence.getOrNull(index)?.sourceUrl)
                                if (index < evidence.lastIndex) HorizontalDivider(color = MovementBorder)
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("Correlation is not proof of causation. Market-wide and sector-wide drivers are not yet attributed in this first evidence pass.", color = MovementMuted, fontSize = 8.sp, lineHeight = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCompanyOverview(symbol: String, day: MyStocksCache.HistoryResult, market: MyStocksCache.MarketStatus, loading: Boolean) {
    val statusLabel = if (market.isOpen) "NSE OPEN" else "NSE CLOSED"
    val statusColor = if (market.isOpen) MovementGreen else MovementMuted
    val regularNextOpen = nextRegularOpenLabel()
    val sessionChange = day.sessionChangePct
    val hasSession = day.sessionOpen != null && day.sessionClose != null

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today at a glance", color = MovementText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("A simple summary of this company's trading day", color = MovementMuted, fontSize = 9.sp)
            }
            Surface(shape = RoundedCornerShape(50), color = if (market.isOpen) MovementLight else Color(0xFFF1F3F2)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(7.dp), shape = RoundedCornerShape(50), color = statusColor) {}
                    Spacer(Modifier.size(5.dp))
                    Text(statusLabel, color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(7.dp))

        Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp)) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = MovementGreen)
                        Spacer(Modifier.size(8.dp))
                        Text("Preparing today's session summary…", color = MovementMuted, fontSize = 10.sp)
                    }
                } else if (hasSession) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        DayMetric("OPEN", "KSh ${money(day.sessionOpen!!)}", Modifier.weight(1f))
                        DayMetric("LATEST / CLOSE", "KSh ${money(day.sessionClose!!)}", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = if ((sessionChange ?: 0.0) >= 0) MovementLight else Color(0xFFFFF0F0)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                if (sessionChange != null) {
                                    "${if (sessionChange >= 0) "Up" else "Down"} ${signedPercent(sessionChange)} from today's open"
                                } else "Today's open-to-close change is unavailable",
                                color = if ((sessionChange ?: 0.0) >= 0) MovementGreen else MovementRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "This is different from the daily change vs yesterday's close shown above.",
                                color = MovementMuted,
                                fontSize = 8.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null, tint = MovementMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(5.dp))
                        Text(
                            if (market.isOpen) "Market is trading • prices are 15 min delayed" else "Market closed • last available observation is shown",
                            color = MovementMuted,
                            fontSize = 8.sp
                        )
                    }
                    if (day.observedAt.isNotBlank()) {
                        Text("Observed ${formatEAT(day.observedAt)}", color = MovementMuted, fontSize = 8.sp, modifier = Modifier.padding(start = 19.dp, top = 2.dp))
                    }
                    Text(
                        if (market.isOpen) "Regular session: Mon–Fri • closes around 3:00 PM EAT" else "Next regular session: $regularNextOpen",
                        color = MovementMuted,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(start = 19.dp, top = 2.dp)
                    )
                } else {
                    Text("Today's session summary is not available from the current market feed.", color = MovementMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun DayMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MovementLight) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = MovementMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = MovementText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
private fun signedPercent(value: Double): String = String.format(Locale.US, "%+.2f%%", value)

private fun formatEAT(iso: String): String = runCatching {
    val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val output = SimpleDateFormat("EEE, d MMM • h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("Africa/Nairobi") }
    output.format(input.parse(iso) ?: Date()) + " EAT"
}.getOrElse { iso.take(16).replace('T', ' ') + " EAT" }

private fun nextRegularOpenLabel(): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Africa/Nairobi"))
    when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.FRIDAY -> cal.add(Calendar.DAY_OF_MONTH, 3)
        Calendar.SATURDAY -> cal.add(Calendar.DAY_OF_MONTH, 2)
        Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_MONTH, 1)
        else -> cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return SimpleDateFormat("EEE, d MMM • h:mm a", Locale.US).format(cal.time) + " EAT"
}

@Composable
private fun MovementEvidenceRow(evidence: MovementIntelligenceCache.Evidence, sourceUrl: String?) {
    val context = LocalContext.current
    val openSource = sourceUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url ->
        { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    val relationshipLabel = when (evidence.relationship.lowercase()) { "related" -> "RELATED"; "possible" -> "POSSIBLE"; else -> "NOT ESTABLISHED" }
    val relationshipColor = when (evidence.relationship.lowercase()) { "related" -> MovementGreen; "possible" -> MovementAmber; else -> MovementMuted }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).then(if (openSource != null) Modifier.clickable { openSource() } else Modifier), verticalAlignment = Alignment.Top) {
        Surface(Modifier.size(32.dp), RoundedCornerShape(9.dp), MovementLight) {
            Icon(if (evidence.eventType == "financial-results" || evidence.eventType == "dividend") Icons.Default.Insights else Icons.Default.Newspaper, null, tint = MovementGreen, modifier = Modifier.padding(7.dp))
        }
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(evidence.title, color = MovementText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 3)
            Spacer(Modifier.height(2.dp))
            Text(listOf(evidence.date, evidence.source).filter(String::isNotBlank).joinToString(" • "), color = MovementMuted, fontSize = 8.sp)
            if (evidence.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp)); Text(evidence.description, color = MovementMuted, fontSize = 8.sp, maxLines = 2, lineHeight = 12.sp)
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(relationshipLabel, color = relationshipColor, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun movementWindow(move: MovementIntelligenceCache.Move): String {
    val label = when (move.periodDays) { 1 -> "Latest 1-day movement"; 7 -> "Latest 1-week movement"; 30 -> "Latest 1-month movement"; 90 -> "Latest 3-month movement"; else -> "Latest movement" }
    return if (move.from.isNotBlank() && move.to.isNotBlank()) "$label • ${move.from} to ${move.to}" else label
}
