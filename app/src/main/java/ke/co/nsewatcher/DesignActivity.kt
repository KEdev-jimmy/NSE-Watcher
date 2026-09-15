package ke.co.nsewatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val NSEGreen = Color(0xFF00A859)
private val NSELight = Color(0xFFE9F8F0)
private val NSEDark = Color(0xFF083C27)
private val NSEText = Color(0xFF12231B)
private val NSEMuted = Color(0xFF6C7A72)
private val NSEBorder = Color(0xFFE1EAE5)
private val NSERed = Color(0xFFE04444)

private data class DesignStock(val symbol: String, val name: String, val price: Double, val change: Double, val history: List<Double>)
private val designStocks = listOf(
    DesignStock("SCOM", "Safaricom", 18.50, 5.24, listOf(15.2, 15.5, 15.3, 16.1, 16.8, 16.5, 17.2, 17.9, 18.5)),
    DesignStock("KCB", "KCB Group", 42.30, 3.26, listOf(38.0, 38.8, 39.2, 40.1, 39.7, 40.8, 41.5, 41.9, 42.3)),
    DesignStock("EQTY", "Equity Group", 46.75, 2.98, listOf(43.2, 43.8, 44.0, 44.9, 44.5, 45.1, 45.8, 46.1, 46.75)),
    DesignStock("COOP", "Co-operative Bank", 21.10, 2.41, listOf(19.5, 19.7, 20.0, 19.9, 20.3, 20.5, 20.8, 20.9, 21.1)),
    DesignStock("ABSA", "Absa Bank Kenya", 14.30, -2.17, listOf(15.5, 15.2, 15.0, 14.8, 14.9, 14.6, 14.7, 14.5, 14.3)),
    DesignStock("EABL", "East African Breweries", 155.00, -1.81, listOf(161.0, 160.5, 159.8, 158.7, 159.2, 157.8, 157.0, 156.2, 155.0)),
    DesignStock("KPLC", "Kenya Power", 4.82, -1.22, listOf(5.2, 5.1, 5.0, 5.05, 4.9, 4.95, 4.88, 4.86, 4.82))
)

class DesignActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignApp() }
    }
}

@Composable
private fun DesignApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<DesignStock?>(null) }
    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = NSEGreen, background = Color.White, surface = Color.White, onBackground = NSEText, onSurface = NSEText, onSurfaceVariant = NSEMuted)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            if (selected != null) {
                CompanyScreen(selected!!) { selected = null }
            } else {
                Scaffold(
                    topBar = { DesignTopBar() },
                    bottomBar = { DesignNavigation(tab) { tab = it } }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            0 -> HomeScreen { selected = it }
                            1 -> MarketScreen()
                            2 -> CompaniesScreen { selected = it }
                            3 -> PaperInvestScreen()
                            else -> MoreScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignTopBar() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(42.dp), RoundedCornerShape(12.dp), color = NSELight) { Icon(Icons.Default.ShowChart, null, Modifier.padding(7.dp), tint = NSEGreen) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("NSE Watcher", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("Analyse • Understand • Invest Smarter", fontSize = 10.sp, color = NSEMuted)
        }
        IconButton(onClick = {}) { Icon(Icons.Default.Search, "Search", tint = NSEGreen) }
        IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Settings", tint = NSEGreen) }
    }
}

@Composable
private fun DesignNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("Home" to Icons.Default.Home, "Market" to Icons.Default.CandlestickChart, "Companies" to Icons.Default.Business, "Paper Invest" to Icons.Default.AccountBalanceWallet, "More" to Icons.Default.Article)
    NavigationBar(containerColor = Color.White) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(selected = selected == index, onClick = { onSelect(index) }, icon = { Icon(item.second, item.first, Modifier.size(22.dp)) }, label = { Text(item.first, fontSize = 9.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = NSEGreen, selectedTextColor = NSEGreen, indicatorColor = NSELight, unselectedIconColor = NSEMuted, unselectedTextColor = NSEMuted))
        }
    }
}

@Composable
private fun HomeScreen(open: (DesignStock) -> Unit) {
    val gainers = designStocks.filter { it.change > 0 }.sortedByDescending { it.change }
    val losers = designStocks.filter { it.change < 0 }.sortedBy { it.change }
    LazyColumn(contentPadding = PaddingValues(16.dp, 5.dp, 16.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MarketStatusCard() }
        item { IndexMiniRow() }
        item { TrendCard() }
        item { SectionHeader("Market Snapshot", "A quick view before you invest") }
        item { SnapshotActions() }
        item { MoversCard(gainers.take(4), losers.take(3), open) }
        item { SectionHeader("Top Companies", "Stocks moving the NSE today") }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) { items(gainers.take(4)) { StockMiniCard(it) { open(it) } } } }
        item { SectionHeader("Latest News", "Market events and company updates") }
        item { NewsPreview() }
        item { PaperBanner() }
    }
}

@Composable
private fun MarketStatusCard() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(NSEGreen)); Spacer(Modifier.width(7.dp)); Text("NSE MARKET OPEN", color = NSEDark, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("10:24 AM EAT", color = NSEMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { Text("Market overview", color = NSEMuted, fontSize = 11.sp); Text("Clear picture of the NSE", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold); Text("before you make an investment decision.", fontSize = 11.sp, color = NSEMuted) }
                Surface(RoundedCornerShape(10.dp), color = Color.White) { Text("LIVE", color = NSEGreen, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) }
            }
        }
    }
}

@Composable
private fun IndexMiniRow() {
    val indexes = listOf("NSE 20" to "1,843.56", "NASI" to "112.48", "NSE 25" to "3,642.17")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(indexes) { (name, value) -> Card(Modifier.width(145.dp), RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(11.dp)) { Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("▲ +1.34%", color = NSEGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } } }
}

@Composable
private fun TrendCard() {
    var period by rememberSaveable { mutableStateOf("1D") }
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("NSE 20 — Market Trend", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text("See how the market has been performing", color = NSEMuted, fontSize = 10.sp) }; Text("+1.34%", color = NSEGreen, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("1D", "1W", "1M", "3M", "6M", "1Y", "5Y").forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p, fontSize = 10.sp) }) } }
            Spacer(Modifier.height(5.dp)); TrendChart(); Text("Historical performance will use sourced NSE market data in live mode.", color = NSEMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun TrendChart() {
    Canvas(Modifier.fillMaxWidth().height(95.dp).padding(vertical = 8.dp)) {
        val values = listOf(28f, 38f, 34f, 47f, 44f, 58f, 52f, 67f, 61f, 74f, 69f, 83f)
        val path = Path()
        values.forEachIndexed { i, value -> val x = size.width * i / (values.size - 1); val y = size.height - (value / 100f * size.height); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, NSEGreen, style = Stroke(4f, cap = StrokeCap.Round))
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) { Column { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text(subtitle, color = NSEMuted, fontSize = 10.sp) } }

@Composable
private fun SnapshotActions() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ActionTile(Icons.Default.AutoGraph, "Sectors", "Performance")
        ActionTile(Icons.Default.ShowChart, "Top Movers", "Gainers & Losers")
        ActionTile(Icons.Default.Business, "Market Analysis", "Trends & Outlook")
        ActionTile(Icons.Default.Article, "News & Events", "Latest Updates")
    }
}

@Composable
private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) { Card(Modifier.weight(1f), RoundedCornerShape(13.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = NSEGreen, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(4.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center); Text(subtitle, color = NSEMuted, fontSize = 7.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } } }

@Composable
private fun MoversCard(gainers: List<DesignStock>, losers: List<DesignStock>, open: (DesignStock) -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) {
        Row(Modifier.padding(vertical = 12.dp)) { MoverList("Top Gainers", gainers, NSEGreen, open, Modifier.weight(1f)); Box(Modifier.width(1.dp).height(165.dp).background(NSEBorder)); MoverList("Top Losers", losers, NSERed, open, Modifier.weight(1f)) }
    }
}

@Composable
private fun MoverList(title: String, stocks: List<DesignStock>, tint: Color, open: (DesignStock) -> Unit, modifier: Modifier) { Column(modifier.padding(horizontal = 11.dp)) { Text(title, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); Spacer(Modifier.height(7.dp)); stocks.forEach { stock -> Row(Modifier.fillMaxWidth().clickable { open(stock) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { StockLogo(stock.symbol, 27); Spacer(Modifier.width(6.dp)); Text(stock.symbol, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(String.format(Locale.US, "%+.2f%%", stock.change), color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp) } } } }

@Composable
private fun StockLogo(symbol: String, size: Int) { val bg = when (symbol) { "SCOM" -> Color(0xFF0B8F4D); "KCB" -> Color(0xFF1B4D9B); "EQTY" -> Color(0xFF137A45); "ABSA" -> Color(0xFFC6283D); "COOP" -> Color(0xFF1769AA); "EABL" -> Color(0xFFB8A23A); "KPLC" -> Color(0xFF285C8C); else -> NSEGreen }; Surface(Modifier.size(size.dp), RoundedCornerShape(8.dp), color = bg) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(symbol.take(3), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 8.sp) } } }

@Composable
private fun StockMiniCard(stock: DesignStock, open: () -> Unit) { Card(Modifier.width(145.dp).clickable(onClick = open), RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { StockLogo(stock.symbol, 27); Spacer(Modifier.width(6.dp)); Text(stock.symbol, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) }; Text(String.format(Locale.US, "KSh %.2f", stock.price), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) NSEGreen else NSERed, fontWeight = FontWeight.Bold, fontSize = 10.sp); MiniSpark(stock.history, if (stock.change >= 0) NSEGreen else NSERed) } } }

@Composable
private fun MiniSpark(values: List<Double>, tint: Color) { Canvas(Modifier.fillMaxWidth().height(30.dp)) { val min = values.minOrNull() ?: 0.0; val max = values.maxOrNull() ?: 1.0; val range = (max - min).takeIf { it > 0 } ?: 1.0; val path = Path(); values.forEachIndexed { i, v -> val x = size.width * i / (values.size - 1).toFloat(); val y = size.height - ((v - min) / range).toFloat() * size.height; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; drawPath(path, tint, style = Stroke(3f, cap = StrokeCap.Round)) } }

@Composable
private fun NewsPreview() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(58.dp), RoundedCornerShape(10.dp), color = NSELight) { Icon(Icons.Default.Article, null, tint = NSEGreen, modifier = Modifier.padding(17.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("NSE market maintaining bullish trend as investors regain confidence", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("Business Daily • 2h ago", color = NSEMuted, fontSize = 9.sp); Text("Market movement and company news", color = NSEMuted, fontSize = 9.sp) }; Icon(Icons.Default.ChevronRight, null, tint = NSEMuted) } } }

@Composable
private fun PaperBanner() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = NSEDark)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(42.dp), CircleShape, color = NSEGreen) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.padding(9.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Paper Investing", color = Color.White, fontWeight = FontWeight.ExtraBold); Text("Real market data. Virtual money. Real learning.", color = Color(0xFFB9D8C8), fontSize = 10.sp) }; Text("Try it", color = Color.White, fontWeight = FontWeight.Bold) } } }

@Composable
private fun MarketScreen() { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SectionHeader("Market Analysis", "Understand the big picture") }; item { TrendCard() }; item { SectorCard() }; item { BreadthCard() } } }
@Composable
private fun SectorCard() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) { Text("Sector Performance", fontWeight = FontWeight.ExtraBold); listOf("Banking" to 2.48, "Telecom" to 1.87, "Manufacturing" to 0.62, "Energy" to -1.21, "Retail" to 0.34).forEach { (name, change) -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(name, fontSize = 11.sp, modifier = Modifier.width(100.dp)); Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(5.dp)).background(NSEBorder)) { Box(Modifier.fillMaxWidth((kotlin.math.abs(change) / 3.0).toFloat().coerceIn(0f, 1f)).height(7.dp).clip(RoundedCornerShape(5.dp)).background(if (change >= 0) NSEGreen else NSERed)) }; Spacer(Modifier.width(7.dp)); Text(String.format(Locale.US, "%+.2f%%", change), color = if (change >= 0) NSEGreen else NSERed, fontSize = 9.sp, fontWeight = FontWeight.Bold) } } } } }
@Composable
private fun BreadthCard() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) { Text("Market Breadth", fontWeight = FontWeight.ExtraBold); Text("Advancers vs decliners", color = NSEMuted, fontSize = 10.sp); Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp))) { Box(Modifier.weight(78f).fillMaxSize().background(NSEGreen)); Box(Modifier.weight(42f).fillMaxSize().background(NSERed)) }; Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("78 Advancing", color = NSEGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("42 Declining", color = NSERed, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } } }

@Composable
private fun CompaniesScreen(open: (DesignStock) -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { SectionHeader("Company Intelligence", "Understand each company before you invest") }; items(designStocks) { stock -> Card(Modifier.fillMaxWidth().clickable { open(stock) }, RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { StockLogo(stock.symbol, 40); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(stock.name, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp); Text(stock.symbol, color = NSEMuted, fontSize = 10.sp); Text(String.format(Locale.US, "KSh %.2f", stock.price), fontWeight = FontWeight.Bold, fontSize = 12.sp) }; Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) NSEGreen else NSERed, fontWeight = FontWeight.ExtraBold) } } } } }

@Composable
private fun CompanyScreen(stock: DesignStock, back: () -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }; StockLogo(stock.symbol, 42); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(stock.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp); Text("${stock.symbol} • NSE", color = NSEMuted, fontSize = 10.sp) } } }; item { CompanyPrice(stock) }; item { CompanyChart(stock) }; item { ScoreCard(stock) }; item { Fundamentals() }; item { Text("Data shown in this design is illustrative until the licensed live NSE feed is connected.", color = NSEMuted, fontSize = 9.sp) } } }
@Composable private fun CompanyPrice(stock: DesignStock) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Column(Modifier.padding(16.dp)) { Text("LIVE NSE PRICE", color = NSEMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "KSh %.2f", stock.price), fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text(String.format(Locale.US, "%+.2f%% today", stock.change), color = if (stock.change >= 0) NSEGreen else NSERed, fontWeight = FontWeight.ExtraBold); Text("Real market price • analysis only • no real trading", color = NSEMuted, fontSize = 9.sp) } } }
@Composable private fun CompanyChart(stock: DesignStock) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) { Text("Price History", fontWeight = FontWeight.ExtraBold); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("1D", "1W", "1M", "3M", "6M", "1Y", "5Y").forEach { FilterChip(selected = it == "1Y", onClick = {}, label = { Text(it, fontSize = 10.sp) }) } }; Spacer(Modifier.height(8.dp)); MiniSpark(stock.history, if (stock.change >= 0) NSEGreen else NSERed) } } }
@Composable private fun ScoreCard(stock: DesignStock) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = NSEDark)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("NSE Watcher Score", color = Color.White, fontWeight = FontWeight.ExtraBold); Text("Explainable analysis, not a BUY/SELL signal", color = Color(0xFFB9D8C8), fontSize = 9.sp); Text(if (stock.change >= 0) "Positive momentum" else "Weak short-term momentum", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)) }; Text(if (stock.change >= 0) "8.2/10" else "5.9/10", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) } } }
@Composable private fun Fundamentals() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) { Text("Key Fundamentals", fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Market Cap", "KSh 750B"); Metric("P/E", "12.4"); Metric("P/B", "2.8") }; Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("Dividend", "4.2%"); Metric("ROE", "22.1%"); Metric("Debt/Equity", "0.32") } } } }
@Composable private fun Metric(label: String, value: String) { Column(Modifier.width(90.dp)) { Text(label, color = NSEMuted, fontSize = 9.sp); Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) } }

@Composable
private fun PaperInvestScreen() { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Column(Modifier.padding(16.dp)) { Text("Virtual Balance", color = NSEMuted, fontSize = 10.sp); Row(verticalAlignment = Alignment.CenterVertically) { Text("KSh 1,000,000", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text("CREATE", color = NSEGreen, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) }; Text("Use real NSE prices to practise with virtual money.", color = NSEMuted, fontSize = 10.sp) } } }; item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) { Text("My Paper Portfolio", fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(8.dp)); listOf(designStocks[0], designStocks[1], designStocks[2], designStocks[3]).forEach { stock -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { StockLogo(stock.symbol, 31); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(stock.symbol, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text("Demo holding •  real market", color = NSEMuted, fontSize = 8.sp) }; Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) NSEGreen else NSERed, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp) } } } } }; item { Text("Real market data + virtual money = practice without risking real cash.", color = NSEMuted, fontSize = 10.sp) } } }

@Composable
private fun MoreScreen() { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SectionHeader("More", "Everything else in NSE Watcher") }; item { MoreRow(Icons.Default.NotificationsNone, "Alerts", "Price, volume and news alerts") }; item { MoreRow(Icons.Default.StarBorder, "Watchlist", "Track companies you care about") }; item { MoreRow(Icons.Default.Settings, "Settings", "Account, appearance and preferences") }; item { MoreRow(Icons.Default.Article, "Learning", "Understand NSE and investing terms") } } }
@Composable private fun MoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, NSEBorder)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(38.dp), RoundedCornerShape(10.dp), color = NSELight) { Icon(icon, null, tint = NSEGreen, modifier = Modifier.padding(8.dp)) }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp); Text(subtitle, color = NSEMuted, fontSize = 9.sp) }; Icon(Icons.Default.ChevronRight, null, tint = NSEMuted) } } }
