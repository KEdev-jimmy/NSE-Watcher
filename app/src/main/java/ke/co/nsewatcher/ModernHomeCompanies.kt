package ke.co.nsewatcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Approved UI redesign: Home and Companies only. Existing data and navigation are preserved.

@Composable
fun ModernHome(data: List<Stock>, open: (Stock) -> Unit) {
    val gainers = data.filter { it.change >= 0 }.sortedByDescending { it.change }
    val losers = data.filter { it.change < 0 }.sortedBy { it.change }
    val breadth = if (data.isEmpty()) 0 else ((gainers.size.toFloat() / data.size) * 100).toInt()
    val avgChange = if (data.isEmpty()) 0.0 else data.map { it.change }.average()

    LazyColumn(contentPadding = PaddingValues(16.dp, 6.dp, 16.dp, 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Good morning", color = Muted, fontSize = 11.sp); Text("NSE at a glance", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold) }
                Surface(Modifier.size(42.dp), CircleShape, LightGreen) { Icon(Icons.Default.ShowChart, "Market", tint = Green, modifier = Modifier.padding(9.dp)) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = DarkGreen)) {
                Column(Modifier.padding(19.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("MARKET OVERVIEW", color = Color.White.copy(alpha = .72f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
                        Surface(RoundedCornerShape(10.dp), Color.White.copy(alpha = .12f)) { Text("NSE DATA • 15 MIN DELAYED", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) }
                    }
                    Spacer(Modifier.height(12.dp)); Text("Market pulse", color = Color.White.copy(alpha = .72f), fontSize = 11.sp)
                    Text(String.format(Locale.US, "%+.2f%%", avgChange), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (avgChange >= 0) "Broadly positive movement across tracked stocks" else "Mixed movement across tracked stocks", color = Color.White.copy(alpha = .78f), fontSize = 10.sp)
                    Spacer(Modifier.height(12.dp)); MiniChart(data.flatMap { it.history }.takeLast(18), Color(0xFF72E6B0))
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) { HomeMetric("Tracked", data.size.toString(), "companies", Modifier.weight(1f)); HomeMetric("Positive", "$breadth%", "breadth", Modifier.weight(1f)); HomeMetric("Leaders", gainers.take(2).joinToString(" • ") { it.symbol }.ifBlank { "—" }, "today", Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Market activity", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("What deserves attention today", color = Muted, fontSize = 10.sp) }; Text("VIEW ALL", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold) } }
        item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { Column(Modifier.padding(13.dp)) { if (gainers.isNotEmpty()) ActivityRow(gainers.first(), true, open); if (losers.isNotEmpty()) ActivityRow(losers.first(), false, open); if (gainers.size > 1) ActivityRow(gainers[1], true, open) } } }
        item { Column { Text("Companies to watch", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Tap any company for deeper analysis", color = Muted, fontSize = 10.sp) } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(data.take(5), key = { it.symbol }) { stock -> HomeCompanyCard(stock) { open(stock) } } } }
        item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(36.dp), CircleShape, Color.White) { Icon(Icons.Default.Info, null, tint = Green, modifier = Modifier.padding(8.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Understand before you invest", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); Text("Use price, trend, volume and company information together. NSE Watcher does not execute trades.", color = Muted, fontSize = 9.sp) } } } }
    }
}

@Composable private fun HomeMetric(title: String, value: String, sub: String, modifier: Modifier) { Card(modifier, RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { Column(Modifier.padding(11.dp)) { Text(title, color = Muted, fontSize = 9.sp); Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1); Text(sub, color = Muted, fontSize = 8.sp) } } }

@Composable private fun ActivityRow(stock: Stock, positive: Boolean, open: (Stock) -> Unit) { Row(Modifier.fillMaxWidth().clickable { open(stock) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), if (positive) LightGreen else Color(0xFFFFEEEE)) { Icon(if (positive) Icons.Default.TrendingUp else Icons.Default.TrendingDown, null, tint = if (positive) Green else Red, modifier = Modifier.padding(8.dp)) }; Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(stock.symbol, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(stock.name, color = Muted, fontSize = 9.sp, maxLines = 1) }; Column(horizontalAlignment = Alignment.End) { Text(String.format(Locale.US, "KSh %.2f", stock.price), fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (positive) Green else Red, fontWeight = FontWeight.Bold, fontSize = 9.sp) } } }

@Composable private fun HomeCompanyCard(stock: Stock, open: () -> Unit) { Card(Modifier.width(158.dp).clickable(onClick = open), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border)) { Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(30.dp), RoundedCornerShape(9.dp), LightGreen) { Icon(Icons.Default.Business, null, tint = Green, modifier = Modifier.padding(7.dp)) }; Spacer(Modifier.width(7.dp)); Column { Text(stock.symbol, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp); Text("NSE", color = Muted, fontSize = 8.sp) } }; Spacer(Modifier.height(10.dp)); Text(String.format(Locale.US, "KSh %.2f", stock.price), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text(String.format(Locale.US, "%+.2f%% today", stock.change), color = if (stock.change >= 0) Green else Red, fontWeight = FontWeight.Bold, fontSize = 9.sp); MiniChart(stock.history, if (stock.change >= 0) Green else Red) } } }

@Composable private fun MiniChart(values: List<Double>, tint: Color) { val valid = values.filter { it.isFinite() }; Canvas(Modifier.fillMaxWidth().height(48.dp).padding(vertical = 5.dp)) { if (valid.size < 2) return@Canvas; val min = valid.minOrNull() ?: return@Canvas; val max = valid.maxOrNull() ?: return@Canvas; val range = (max - min).takeIf { it > 0.0 } ?: 1.0; val path = Path(); valid.forEachIndexed { index, value -> val x = size.width * index / (valid.lastIndex.coerceAtLeast(1)); val y = size.height - (((value - min) / range).toFloat() * size.height); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }; drawPath(path, tint, style = Stroke(width = 3f, cap = StrokeCap.Round)) } }

@Composable
fun ModernCompanies(data: List<Stock>, open: (Stock) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = data.filter { query.isBlank() || it.symbol.contains(query, true) || it.name.contains(query, true) }
    val featured = data.sortedByDescending { it.change }.take(4)
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(285.dp)) {
                androidx.compose.foundation.Image(painter = painterResource(id = R.drawable.companies_city_background), contentDescription = "Nairobi city skyline", modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(0xB5082E2A)))
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Bottom) {
                    Text("NSE COMPANIES", color = Color.White.copy(alpha = .75f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp))
                    Text("Discover great\ncompanies", color = Color.White, fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(7.dp))
                    Text("Explore Kenyan listed companies and understand what moves them.", color = Color.White.copy(alpha = .82f), fontSize = 10.sp); Spacer(Modifier.height(15.dp))
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search company or symbol", color = Color.White.copy(alpha = .72f), fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear", tint = Color.White) } }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = .65f), focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White, focusedContainerColor = Color.Black.copy(alpha = .18f), unfocusedContainerColor = Color.Black.copy(alpha = .18f)), shape = RoundedCornerShape(15.dp))
                }
            }
        }
        item { Column(Modifier.padding(horizontal = 16.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Featured today", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text("Companies with notable price movement", color = Muted, fontSize = 10.sp) }; Surface(RoundedCornerShape(10.dp), LightGreen) { Text("NSE DATA", color = Green, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) } } } }
        item { LazyRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(featured, key = { it.symbol }) { stock -> FeaturedCompany(stock) { open(stock) } } } }
        item { Column(Modifier.padding(horizontal = 16.dp)) { Text("All companies", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text(if (query.isBlank()) "Listed companies available in your NSE dataset" else "Search results for \"$query\"", color = Muted, fontSize = 10.sp) } }
        if (filtered.isEmpty()) item { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), RoundedCornerShape(18.dp)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.SearchOff, null, tint = Muted, modifier = Modifier.size(34.dp)); Text("No company found", fontWeight = FontWeight.Bold); Text("Try another company name or symbol.", color = Muted, fontSize = 10.sp) } } } else items(filtered, key = { it.symbol }) { stock -> CompanyListRow(stock) { open(stock) } }
        item { Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Green); Spacer(Modifier.width(9.dp)); Text("NSE Watcher provides market intelligence, not trade execution or guaranteed returns.", color = Muted, fontSize = 9.sp) } } }
    }
}

@Composable private fun FeaturedCompany(stock: Stock, open: () -> Unit) { Card(Modifier.width(205.dp).clickable(onClick = open), RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = DarkGreen)) { Column(Modifier.padding(15.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(38.dp), CircleShape, Color.White.copy(alpha = .12f)) { Icon(Icons.Default.Business, null, tint = Color.White, modifier = Modifier.padding(9.dp)) }; Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(stock.symbol, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); Text(stock.name, color = Color.White.copy(alpha = .65f), fontSize = 9.sp, maxLines = 1) } }; Spacer(Modifier.height(16.dp)); Text(String.format(Locale.US, "KSh %.2f", stock.price), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold); Text(String.format(Locale.US, "%+.2f%% today", stock.change), color = if (stock.change >= 0) Color(0xFF72E6B0) else Color(0xFFFF8B8B), fontSize = 10.sp, fontWeight = FontWeight.Bold); MiniChart(stock.history, Color(0xFF72E6B0)); Text("View company →", color = Color.White.copy(alpha = .7f), fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) } } }

@Composable private fun CompanyListRow(stock: Stock, open: () -> Unit) { Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(onClick = open).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(44.dp), RoundedCornerShape(13.dp), LightGreen) { Icon(Icons.Default.Business, null, tint = Green, modifier = Modifier.padding(10.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(stock.name, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(stock.symbol, color = Muted, fontSize = 9.sp) }; Column(horizontalAlignment = Alignment.End) { Text(String.format(Locale.US, "KSh %.2f", stock.price), fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) Green else Red, fontWeight = FontWeight.Bold, fontSize = 9.sp) }; Spacer(Modifier.width(5.dp)); Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp)) } }
