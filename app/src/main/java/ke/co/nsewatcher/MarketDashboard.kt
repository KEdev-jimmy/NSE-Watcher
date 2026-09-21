package ke.co.nsewatcher

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.MyStocksCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

private val MarketBg = Color(0xFF061326)
private val MarketCard = Color(0xFF0D1D32)
private val MarketCard2 = Color(0xFF10233A)
private val MarketGreen = Color(0xFF12E889)
private val MarketBlue = Color(0xFF9DB6E0)
private val MarketWhite = Color(0xFFF7FAFF)
private val MarketBorder = Color(0xFF1B3654)


private val MarketLogoAliases = mapOf(
    "SCOM" to "scom", "KCB" to "kcb", "EQTY" to "eqty", "ABSA" to "absa", "BOC" to "boc",
    "KEGN" to "kengen", "COOP" to "coop", "EABL" to "eabl", "NCBA" to "ncba", "KPLC" to "kplc",
    "BRIT" to "brit", "KQ" to "kq", "BAT" to "bat", "JUB" to "jub", "DTK" to "dtk"
)

private data class PeriodPerformance(val stock: Stock, val gainPct: Double)

private val MarketPeriods = listOf(
    "3D" to "3d",
    "1W" to "1w",
    "1M" to "1m",
    "3M" to "3m",
    "6M" to "6m",
    "1Y" to "1y",
    "3Y" to "3y"
)

@Composable
fun MarketDashboard(stockFeed: List<Stock>) {
    var periodLabel by rememberSaveable { mutableStateOf("1W") }
    val period = MarketPeriods.firstOrNull { it.first == periodLabel } ?: MarketPeriods[1]
    var performances by remember { mutableStateOf<List<PeriodPerformance>>(emptyList()) }
    var loading by remember(period.second, stockFeed) { mutableStateOf(true) }

    LaunchedEffect(period.second, stockFeed) {
        loading = true
        performances = emptyList()
        val stocks = stockFeed.filter { it.price.isFinite() && it.price > 0.0 }
        performances = coroutineScope {
            stocks.map { stock ->
                async {
                    val history = MyStocksCache.loadHistory(stock.symbol, period.second)
                    // A performance window needs two real historical observations.
                    // Do not substitute today's quote when history is incomplete:
                    // that would turn an unavailable period into a misleading gain.
                    val first = history.firstOrNull()
                    val latest = history.lastOrNull()
                    val gain = if (history.size >= 2 && first != null && first > 0.0 && latest != null && latest > 0.0) {
                        ((latest - first) / first) * 100.0
                    } else null
                    gain?.let { PeriodPerformance(stock, it) }
                }
            }.awaitAll()
                .filterNotNull()
                .sortedByDescending { it.gainPct }
                .take(5)
        }
        loading = false
    }

    val top = performances

    Box(Modifier.fillMaxSize().background(MarketBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    AsyncImage(
                        painter = painterResource(id = R.drawable.nairobi_city_county_skyline),
                        contentDescription = "Nairobi skyline",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color(0x6613263A)))
                    Box(Modifier.fillMaxSize().background(Color(0x2500A859)))
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ShowChart, null, tint = MarketGreen, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Market", color = MarketWhite, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text(
                            "Top performing companies on the NSE",
                            color = MarketBlue,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            item {
                MarketFreshnessStrip(stockFeed)
            }

            item {
                Surface(
                    Modifier.fillMaxWidth().height(55.dp), RoundedCornerShape(28.dp),
                    color = Color(0xFF0B1930), border = BorderStroke(1.dp, MarketBorder)
                ) {
                    Row(Modifier.fillMaxSize()) {
                        MarketPeriods.forEach { (label, _) ->
                            val selected = periodLabel == label
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).clickable { periodLabel = label },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) Box(Modifier.fillMaxSize().padding(1.dp).clip(RoundedCornerShape(28.dp)).background(MarketGreen))
                                Text(label, color = if (selected) MarketWhite else MarketBlue, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            item {
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = Color(0xFF0A2630), border = BorderStroke(1.dp, Color(0xFF08705E))) {
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(56.dp), CircleShape, Color(0xFF087451)) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(31.dp)) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Top Performers (This $periodLabel)", color = MarketWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Companies with the highest price gains", color = MarketBlue, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#", color = MarketBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                    Text("Company", color = MarketBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Price (KES)", color = MarketBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(92.dp))
                    Text("Gain", color = MarketBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
                }
            }

            if (loading) {
                item { MarketHistoricalLoader(periodLabel) }
            } else {
                itemsIndexed(top) { index, item -> MarketPerformerRow(index + 1, item.stock, item.gainPct) }
                if (top.isEmpty()) {
                    item {
                        Text("Historical performance is unavailable for this period right now.", color = MarketBlue, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            item {
                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), color = Color(0xFF0A2630), border = BorderStroke(1.dp, Color(0xFF08705E))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(60.dp), CircleShape, Color(0xFF087451)) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.ShowChart, null, tint = MarketGreen, modifier = Modifier.size(34.dp)) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Track what's moving the market", color = MarketWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("Historical performance from the connected market-data feed.", color = MarketBlue, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MarketBlue, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketFreshnessStrip(stocks: List<Stock>) {
    val valid = stocks.filter { it.price.isFinite() && it.price > 0.0 }
    val source = valid.map { it.source.trim() }.firstOrNull { it.isNotBlank() } ?: "Market source unavailable"
    val freshness = when {
        valid.any { it.freshnessMode == "CURRENT_SESSION" } -> "Current session"
        valid.any { it.freshnessMode == "END_OF_DAY" } -> "End-of-day observation"
        valid.any { it.freshnessMode == "STALE" } -> "Previous session"
        else -> "Freshness unknown"
    }
    Surface(
        Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
        color = MarketCard,
        border = BorderStroke(1.dp, MarketBorder)
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = MarketGreen, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Data freshness", color = MarketWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text("• $freshness", color = MarketBlue, fontSize = 8.sp)
                Spacer(Modifier.weight(1f))
                Text(valid.size.toString() + " valid quotes", color = MarketBlue, fontSize = 8.sp)
            }
            Text(
                "Source: $source • Historical rankings use the connected market-data feed.",
                color = MarketBlue,
                fontSize = 7.sp,
                modifier = Modifier.padding(start = 21.dp, top = 3.dp)
            )
        }
    }
}

@Composable
private fun MarketHistoricalLoader(periodLabel: String) {
    val transition = rememberInfiniteTransition(label = "marketLoader")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val dotOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot"
    )

    Surface(
        Modifier.fillMaxWidth().height(190.dp),
        RoundedCornerShape(20.dp),
        color = MarketCard,
        border = BorderStroke(1.dp, MarketBorder)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                Surface(Modifier.size(72.dp), CircleShape, MarketGreen.copy(alpha = 0.10f)) {}
                Surface(Modifier.size(48.dp), CircleShape, MarketGreen.copy(alpha = pulse * 0.22f)) {}
                Icon(Icons.Default.ShowChart, null, tint = MarketGreen.copy(alpha = pulse), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Checking ${periodLabel} market performance", color = MarketWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Loading historical NSE data", color = MarketBlue, fontSize = 12.sp)
                Spacer(Modifier.width(3.dp))
                repeat(3) { index ->
                    Text("•", color = MarketGreen.copy(alpha = if (dotOffset > index * 2.5f) 1f else 0.35f), fontSize = 15.sp, modifier = Modifier.offset(y = (-dotOffset / 2).dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Please wait a moment…", color = MarketBlue.copy(alpha = 0.75f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MarketPerformerRow(rank: Int, stock: Stock, gainPct: Double) {
    val logo = stock.logoUrl?.takeIf { it.isNotBlank() }
        ?: "https://mystocks.africa/logos/${MarketLogoAliases[stock.symbol.uppercase(Locale.US)] ?: stock.symbol.lowercase(Locale.US)}-ke.svg"
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), color = MarketCard, border = BorderStroke(1.dp, MarketBorder)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), CircleShape, MarketCard2) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(rank.toString(), color = MarketWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium) } }
            Spacer(Modifier.width(10.dp))
            Surface(Modifier.size(62.dp), RoundedCornerShape(14.dp), Color.White) { AsyncImage(model = logo, contentDescription = stock.name, modifier = Modifier.fillMaxSize().padding(5.dp)) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(stock.name, color = MarketWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(stock.symbol, color = MarketBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                Text(stock.sector.ifBlank { "Other" }, color = MarketBlue, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
            }
            Text("KES ${String.format(Locale.US, "%.2f", stock.price)}", color = MarketWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(88.dp))
            Text("▲ ${String.format(Locale.US, "%+.2f%%", gainPct)}", color = MarketGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(61.dp))
        }
    }
}
