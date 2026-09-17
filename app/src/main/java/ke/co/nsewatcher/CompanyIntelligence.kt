package ke.co.nsewatcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import java.util.Locale

private val IntelligenceGreen = Color(0xFF00A859)
private val IntelligenceLight = Color(0xFFE9F8F0)
private val IntelligenceDark = Color(0xFF083C27)
private val IntelligenceText = Color(0xFF12231B)
private val IntelligenceMuted = Color(0xFF6C7A72)
private val IntelligenceBorder = Color(0xFFE1EAE5)
private val IntelligenceRed = Color(0xFFE04444)

@Composable
fun CompanyIntelligence(s: Stock, back: () -> Unit) {
    val periods = listOf("1W", "1M", "3M", "6M", "1Y", "3Y")
    var period by rememberSaveable(s.symbol) { mutableStateOf("1Y") }
    var history by remember(s.symbol) { mutableStateOf(s.history) }
    var historyLoading by remember(s.symbol) { mutableStateOf(false) }
    var monthHistory by remember(s.symbol) { mutableStateOf(emptyList<Double>()) }
    var intelligence by remember(s.symbol) { mutableStateOf(CompanyIntelligenceCache.Result()) }
    var intelligenceLoading by remember(s.symbol) { mutableStateOf(true) }
    var news by remember(s.symbol) { mutableStateOf(emptyList<NewsItem>()) }
    var newsLoading by remember(s.symbol) { mutableStateOf(true) }

    LaunchedEffect(s.symbol) {
        intelligenceLoading = true
        intelligence = CompanyIntelligenceCache.load(s.symbol)
        intelligenceLoading = false
    }

    LaunchedEffect(s.symbol) {
        newsLoading = true
        news = NewsCache.loadCompanyNews(s.symbol).items
        newsLoading = false
    }

    LaunchedEffect(s.symbol) {
        monthHistory = MyStocksCache.loadHistory(s.symbol, "1m")
    }

    LaunchedEffect(s.symbol, period) {
        historyLoading = true
        val live = MyStocksCache.loadHistory(s.symbol, period)
        if (live.size >= 2) history = live
        historyLoading = false
    }

    val profile = intelligence.profile
    val monthlyReturn = percentReturn(monthHistory)
    val intelligenceLine = when {
        monthlyReturn != null && news.isNotEmpty() -> "${s.name} has moved ${formatSigned(monthlyReturn)} over the last month. ${news.size} recent company intelligence item${if (news.size == 1) " is" else "s are"} available below."
        monthlyReturn != null -> "${s.name} has moved ${formatSigned(monthlyReturn)} over the last month. Historical market context is shown below."
        news.isNotEmpty() -> "Recent company intelligence is available below. Historical monthly performance is not currently available from the data source."
        else -> "Current NSE data is available. More company evidence will appear as the provider returns financial, dividend and news data."
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                CompanyLogo(s.symbol, 46, s.logoUrl)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceText)
                    Text("${s.symbol} • NSE", fontSize = 10.sp, color = IntelligenceMuted)
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = IntelligenceLight)
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text("NSE DATA • 15 MIN DELAYED", color = IntelligenceMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(String.format(Locale.US, "KSh %.2f", s.price), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = IntelligenceText)
                        Spacer(Modifier.width(9.dp))
                        Text(String.format(Locale.US, "%+.2f%% today", s.change), color = if (s.change >= 0) IntelligenceGreen else IntelligenceRed, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Exchange-supplied NSE data • analysis only • no real trading", color = IntelligenceMuted, fontSize = 9.sp)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = IntelligenceDark)) {
                Column(Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(38.dp), RoundedCornerShape(12.dp), Color.White.copy(alpha = .12f)) {
                            Icon(Icons.Default.Psychology, null, tint = Color(0xFF8BE0B3), modifier = Modifier.padding(8.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Intelligence", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Evidence first • no BUY/SELL instruction", color = Color(0xFFBFE8D0), fontSize = 9.sp)
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    if (intelligenceLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF8BE0B3))
                            Spacer(Modifier.width(9.dp))
                            Text("Building the company evidence view…", color = Color.White.copy(alpha = .82f), fontSize = 10.sp)
                        }
                    } else {
                        Text(intelligenceLine, color = Color.White, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
            }
        }

        item { SectionTitle("Business", "What does this company actually do?", Icons.Default.Business) }
        item {
            IntelligenceCard {
                if (profile.description.isNotBlank()) Text(profile.description, fontSize = 12.sp, lineHeight = 18.sp, color = IntelligenceText)
                else Text("Business description is not available from the current company-data response.", color = IntelligenceMuted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniFact("Sector", profile.sector.ifBlank { "Not available" })
                    MiniFact("HQ", profile.headquarters.ifBlank { "Not available" })
                }
            }
        }

        item { SectionTitle("Financial health", "Latest company financial evidence", Icons.Default.Assessment) }
        item {
            IntelligenceCard {
                MetricGrid(listOf(
                    "Revenue" to valueOrMissing(profile.revenue),
                    "Profit" to valueOrMissing(profile.profit),
                    "EPS" to valueOrMissing(profile.eps),
                    "ROE" to valueOrMissing(profile.roe),
                    "Debt / Equity" to valueOrMissing(profile.debtToEquity),
                    "Net margin" to valueOrMissing(profile.margin)
                ))
            }
        }

        item { SectionTitle("Growth", "Latest reported growth signals", Icons.Default.TrendingUp) }
        item {
            IntelligenceCard {
                MetricGrid(listOf(
                    "Revenue trend" to valueOrMissing(profile.revenueGrowth),
                    "Profit trend" to valueOrMissing(profile.profitGrowth),
                    "EPS" to valueOrMissing(profile.eps)
                ))
                Spacer(Modifier.height(8.dp))
                Text("Historical financial trend will only be shown when the source provides a comparable series; no figures are invented here.", color = IntelligenceMuted, fontSize = 9.sp)
            }
        }

        item { SectionTitle("Valuation", "How the current price relates to reported metrics", Icons.Default.Calculate) }
        item {
            IntelligenceCard {
                MetricGrid(listOf(
                    "P/E" to valueOrMissing(profile.pe),
                    "P/B" to valueOrMissing(profile.pb),
                    "Dividend yield" to valueOrMissing(profile.dividendYield),
                    "Market cap" to valueOrMissing(profile.marketCap)
                ))
                Spacer(Modifier.height(8.dp))
                Text("Historical valuation context is not displayed until a sourced valuation history is available.", color = IntelligenceMuted, fontSize = 9.sp)
            }
        }

        item { SectionTitle("Dividends", "Declared and historical distributions", Icons.Default.Payments) }
        item {
            IntelligenceCard {
                if (intelligence.dividends.isEmpty()) {
                    Text("No dividend history was returned by the current provider response.", color = IntelligenceMuted, fontSize = 11.sp)
                } else {
                    intelligence.dividends.take(5).forEachIndexed { index, dividend ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), IntelligenceLight) { Icon(Icons.Default.Payments, null, tint = IntelligenceGreen, modifier = Modifier.padding(8.dp)) }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (dividend.amount.isNotBlank()) "KSh ${dividend.amount}" else "Dividend amount not supplied", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(listOf(dividend.exDate, dividend.paymentDate).filter(String::isNotBlank).joinToString(" • ").ifBlank { "Date not supplied" }, color = IntelligenceMuted, fontSize = 9.sp)
                            }
                            if (dividend.status.isNotBlank()) Text(dividend.status, color = IntelligenceGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        if (index < intelligence.dividends.take(5).lastIndex) HorizontalDivider(color = IntelligenceBorder)
                    }
                }
            }
        }

        item { SectionTitle("Market behaviour", "Price movement across time", Icons.Default.ShowChart) }
        item {
            IntelligenceCard {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    periods.forEach { value ->
                        FilterChip(selected = period == value, onClick = { period = value }, label = { Text(value, fontSize = 9.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (historyLoading) {
                    IntelligenceLoader("Loading $period market history", "Checking historical NSE data…")
                } else if (history.size >= 2) {
                    IntelligenceChart(history, if (s.change >= 0) IntelligenceGreen else IntelligenceRed)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${history.size} data points", color = IntelligenceMuted, fontSize = 8.sp)
                        percentReturn(history)?.let { Text("${formatSigned(it)} $period", color = if (it >= 0) IntelligenceGreen else IntelligenceRed, fontWeight = FontWeight.Bold, fontSize = 8.sp) }
                    }
                } else {
                    Text("Historical market data is not available for this period.", color = IntelligenceMuted, fontSize = 10.sp)
                }
            }
        }

        item { SectionTitle("What changed?", "Recent company events and intelligence", Icons.Default.Newspaper) }
        item {
            IntelligenceCard {
                when {
                    newsLoading -> IntelligenceLoader("Loading company intelligence", "Checking recent announcements and news…")
                    news.isEmpty() -> Text("No recent company news or corporate actions were returned.", color = IntelligenceMuted, fontSize = 11.sp)
                    else -> news.take(5).forEachIndexed { index, item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.category.ifBlank { "Market" }, color = IntelligenceGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(7.dp))
                                Text(item.publishedAt.take(10).ifBlank { "Latest" }, color = IntelligenceMuted, fontSize = 8.sp)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                            if (item.summary.isNotBlank()) Text(item.summary, color = IntelligenceMuted, fontSize = 9.sp, maxLines = 2)
                        }
                        if (index < news.take(5).lastIndex) HorizontalDivider(color = IntelligenceBorder)
                    }
                }
            }
        }

        item { SectionTitle("Risks to investigate", "Questions raised by the available evidence", Icons.Default.Warning) }
        item {
            IntelligenceCard {
                val risks = buildList {
                    if (kotlin.math.abs(s.change) >= 5.0) add("The share moved ${String.format(Locale.US, "%+.2f%%", s.change)} today. Investigate the announcement or market event behind the move.")
                    profile.debtToEquity.toDoubleOrNull()?.let { add("Debt / equity is ${profile.debtToEquity}. Compare it with the company's history and sector peers.") }
                    profile.eps.toDoubleOrNull()?.takeIf { it < 0 }?.let { add("Latest EPS is negative. Review the latest reported results and management outlook.") }
                    if (profile.revenue.isBlank() || profile.profit.isBlank()) add("Some financial fields are unavailable. Missing data should not be treated as a positive signal.")
                    if (news.isEmpty()) add("No recent company intelligence was returned. Check official issuer and NSE announcements for material events.")
                }
                if (risks.isEmpty()) Text("No automatic watchpoint was generated from the currently available fields. This is not a statement that the company has no risks.", color = IntelligenceMuted, fontSize = 10.sp)
                risks.forEach { text -> Watchpoint(text) }
            }
        }

        item { SectionTitle("Evidence", "Where the important information came from", Icons.Default.Verified) }
        item {
            IntelligenceCard {
                EvidenceRow("Market price", "MyStocks Africa • NSE exchange-supplied • ~15 min delayed")
                EvidenceRow("Company profile", if (intelligenceLoading) "Loading source data…" else "MyStocks Africa company profile")
                EvidenceRow("Dividends", "MyStocks Africa dividend history")
                EvidenceRow("News & actions", "MyStocks Africa company intelligence feed")
                if (intelligence.fetchedAt.isNotBlank()) EvidenceRow("Fetched", intelligence.fetchedAt)
                Spacer(Modifier.height(6.dp))
                Text("NSE Watcher separates sourced facts from interpretation. Verify material announcements against the issuer or NSE before acting.", color = IntelligenceMuted, fontSize = 9.sp)
            }
        }

        item {
            Text("NSE Watcher is an analysis and education product. It does not execute real trades or guarantee returns.", color = IntelligenceMuted, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun IntelligenceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = BorderStroke(1.dp, IntelligenceBorder)) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), IntelligenceLight) { Icon(icon, null, tint = IntelligenceGreen, modifier = Modifier.padding(7.dp)) }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = IntelligenceText)
            Text(subtitle, color = IntelligenceMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MetricGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Surface(Modifier.weight(1f), RoundedCornerShape(13.dp), color = IntelligenceLight) {
                        Column(Modifier.padding(10.dp)) {
                            Text(label, color = IntelligenceMuted, fontSize = 8.sp)
                            Text(value, color = IntelligenceText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
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
    Surface(Modifier.weight(1f), RoundedCornerShape(12.dp), color = IntelligenceLight) {
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
private fun IntelligenceChart(values: List<Double>, tint: Color) {
    val valid = values.filter { it.isFinite() && it > 0.0 }
    if (valid.size < 2) return
    Canvas(Modifier.fillMaxWidth().height(155.dp).padding(vertical = 8.dp)) {
        val min = valid.minOrNull() ?: return@Canvas
        val max = valid.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        valid.forEachIndexed { index, value ->
            val x = size.width * index / valid.lastIndex.coerceAtLeast(1)
            val y = size.height - (((value - min) / range).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, tint, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
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

private fun valueOrMissing(value: String): String = value.trim().takeIf { it.isNotBlank() } ?: "Not available"

private fun percentReturn(values: List<Double>): Double? {
    val valid = values.filter { it.isFinite() && it > 0.0 }
    if (valid.size < 2) return null
    val first = valid.first()
    return ((valid.last() - first) / first) * 100.0
}

private fun formatSigned(value: Double): String = String.format(Locale.US, "%+.1f%%", value)
