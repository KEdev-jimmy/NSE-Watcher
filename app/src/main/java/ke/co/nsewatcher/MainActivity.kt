@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ke.co.nsewatcher

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ke.co.nsewatcher.data.DemoMarketRepository
import ke.co.nsewatcher.data.MarketRepository
import ke.co.nsewatcher.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val Green = Color(0xFF00A859)
private val LightGreen = Color(0xFFE8F8EF)
private val SoftGreen = Color(0xFFD9F3E5)
private val Red = Color(0xFFE53935)
private val SoftRed = Color(0xFFFFE8E7)
private val TextDark = Color(0xFF102018)
private val Muted = Color(0xFF68766F)
private val AppColors = lightColorScheme(
    primary = Green, onPrimary = Color.White, secondary = Green, tertiary = Green,
    background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFF4F7F5),
    onBackground = TextDark, onSurface = TextDark, onSurfaceVariant = Muted, error = Red
)

data class AppState(val snapshot: MarketSnapshot? = null, val news: List<NewsItem> = emptyList(), val loading: Boolean = true, val error: String? = null)
data class Holding(val symbol: String, val shares: Int, val averagePrice: Double)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NSEWatcherApp(MarketViewModel(application)) }
    }
}

class MarketViewModel(application: Application, private val repository: MarketRepository = DemoMarketRepository(application)) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        repository.snapshot(true).onSuccess { snapshot ->
            repository.news().onSuccess { news -> _state.value = AppState(snapshot, news, false) }
        }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load market data") }
    }
    fun add(symbol: String) = viewModelScope.launch { repository.addSymbol(symbol); refresh() }
    fun remove(symbol: String) = viewModelScope.launch { repository.removeSymbol(symbol); refresh() }
}

@Composable
fun NSEWatcherApp(vm: MarketViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Quote?>(null) }
    var holdings by remember { mutableStateOf(listOf(Holding("SCOM.KE", 500, 21.50), Holding("KCB.KE", 200, 38.00))) }
    var alerts by remember { mutableStateOf(listOf("SCOM.KE above KSh 30.00", "KCB.KE daily gain above 5%")) }

    MaterialTheme(colorScheme = AppColors) {
        Surface(color = Color.White) {
            if (selected != null) {
                DetailScreen(selected!!) { selected = null }
            } else {
                Scaffold(
                    containerColor = Color.White,
                    topBar = { TopBar(vm::refresh) },
                    bottomBar = {
                        NavigationBar(containerColor = Color.White) {
                            val nav = listOf("Dashboard" to Icons.Default.Home, "Watchlist" to Icons.Default.Star, "Portfolio" to Icons.Default.AccountBalanceWallet, "Alerts" to Icons.Default.Notifications, "News" to Icons.Default.Article)
                            nav.forEachIndexed { i, item ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) },
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = LightGreen, unselectedIconColor = Muted, unselectedTextColor = Muted)
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Green)
                            state.error != null -> ErrorState(state.error!!, vm::refresh)
                            tab == 0 -> Dashboard(state) { selected = it }
                            tab == 1 -> Watchlist(state.snapshot?.quotes.orEmpty(), vm::add, vm::remove) { selected = it }
                            tab == 2 -> PortfolioScreen(holdings) { holdings = it }
                            tab == 3 -> AlertsScreen(alerts) { alerts = it }
                            else -> NewsScreen(state.news)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(refresh: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
        title = { Column { Text("NSE Watcher", fontWeight = FontWeight.ExtraBold); Text("Track • Analyze • Grow", style = MaterialTheme.typography.labelSmall, color = Muted) } },
        actions = {
            IconButton(onClick = refresh) { Icon(Icons.Default.Refresh, "Refresh", tint = Green) }
            Surface(shape = RoundedCornerShape(50), color = LightGreen) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Green)); Spacer(Modifier.width(5.dp)); Text("DEMO", color = Green, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
        }
    )
}

@Composable
private fun Dashboard(state: AppState, open: (Quote) -> Unit) {
    val quotes = state.snapshot?.quotes.orEmpty()
    val gainers = quotes.sortedByDescending { it.dailyChange }
    val losers = quotes.sortedBy { it.dailyChange }
    val volume = quotes.sumOf { it.volume }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { MarketHero(quotes) }
        item { OverviewCards(quotes, volume) }
        item {
            SectionTitle("🔥 Market Movers", "Top gainers and losers")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactMover("Top Gainer", gainers.firstOrNull(), true, Modifier.weight(1f), open)
                CompactMover("Top Loser", losers.firstOrNull(), false, Modifier.weight(1f), open)
            }
        }
        item { SectionTitle("⭐ Your Watchlist", "${quotes.size} tracked") }
        items(quotes.take(4), key = { it.symbol }) { StockCard(it) { open(it) } }
        item { InsightCard(quotes) }
        item { SectionTitle("📰 Latest News", "From your tracked stocks") }
        items(state.news.take(2)) { NewsCard(it) }
        item { DemoBanner() }
    }
}

@Composable
private fun MarketHero(quotes: List<Quote>) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Green)); Spacer(Modifier.width(7.dp)); Text("Market Status", fontWeight = FontWeight.SemiBold) }
                Text("DEMO MARKET", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Green)
                Text("Sample data • not live prices", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) { Text("NSE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text("Nairobi Securities Exchange", style = MaterialTheme.typography.labelSmall, color = Muted) }
        }
    }
}

@Composable
private fun OverviewCards(quotes: List<Quote>, volume: Double) {
    Text("Market Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniCard("Gainers", quotes.count { it.dailyChange > 0 }.toString(), Icons.Default.TrendingUp, Green, Modifier.weight(1f))
        MiniCard("Losers", quotes.count { it.dailyChange < 0 }.toString(), Icons.Default.TrendingDown, Red, Modifier.weight(1f))
        MiniCard("Volume", fmt(volume), Icons.Default.BarChart, Green, Modifier.weight(1f))
    }
}

@Composable
private fun MiniCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0xFFE0EAE4)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Surface(shape = RoundedCornerShape(50), color = if (tint == Red) SoftRed else SoftGreen) { Icon(icon, null, tint, Modifier.padding(6.dp).size(17.dp)) }
            Spacer(Modifier.height(6.dp)); Text(label, style = MaterialTheme.typography.labelSmall, color = Muted); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CompactMover(title: String, q: Quote?, positive: Boolean, modifier: Modifier, open: (Quote) -> Unit) {
    Card(modifier.clickable { if (q != null) open(q) }, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFE0EAE4)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(q?.symbol ?: "—", fontWeight = FontWeight.ExtraBold)
            Text(q?.companyName ?: "No data", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(q?.let { "%+.1f%%".format(it.dailyChange) } ?: "—", color = if (positive) Green else Red, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable
private fun InsightCard(quotes: List<Quote>) {
    val best = quotes.maxByOrNull { it.dailyChange }
    val worst = quotes.minByOrNull { it.dailyChange }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8))) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Lightbulb, null, tint = Green, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
            Column { Text("Demo insight", fontWeight = FontWeight.Bold); Text("${best?.symbol ?: "—"} has the strongest daily move while ${worst?.symbol ?: "—"} is the weakest in this sample.", color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun StockCard(q: Quote, open: () -> Unit) {
    val up = q.dailyChange >= 0; val tint = if (up) Green else Red
    Card(Modifier.fillMaxWidth().clickable(onClick = open), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFE0EAE4)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(q.companyName, fontWeight = FontWeight.ExtraBold); Text(q.symbol, color = Muted, style = MaterialTheme.typography.bodySmall) }
                Column(horizontalAlignment = Alignment.End) { Text("KSh %.2f".format(q.price), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text("%+.1f%%".format(q.dailyChange), color = tint, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Stat("1W", q.weeklyChange, Modifier.weight(1f)); Stat("1M", q.monthlyChange, Modifier.weight(1f)); Column(Modifier.weight(1.2f)) { Text("Volume", color = Muted, style = MaterialTheme.typography.labelSmall); Text(fmt(q.volume), fontWeight = FontWeight.Bold) }; Sparkline(q.history, Modifier.width(82.dp).height(40.dp), tint)
            }
            Spacer(Modifier.height(9.dp)); Surface(shape = RoundedCornerShape(9.dp), color = if (q.signal == Signal.STRONG) Green else LightGreen) { Text("${q.signal.name}  •  Tap for details", color = if (q.signal == Signal.STRONG) Color.White else Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) }
        }
    }
}

@Composable private fun Stat(label: String, value: Double, modifier: Modifier) { Column(modifier) { Text(label, color = Muted, style = MaterialTheme.typography.labelSmall); Text("%+.1f%%".format(value), color = if (value >= 0) Green else Red, fontWeight = FontWeight.Bold) } }

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier, tint: Color) {
    Canvas(modifier) {
        if (values.size > 1) {
            val min = values.minOrNull() ?: 0.0; val max = values.maxOrNull() ?: 1.0; val range = (max - min).takeIf { it > 0 } ?: 1.0
            val path = Path()
            values.forEachIndexed { i, v -> val x = size.width * i / (values.lastIndex); val y = size.height - ((v - min) / range).toFloat() * size.height; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        }
    }
}

@Composable
private fun Watchlist(quotes: List<Quote>, add: (String) -> Unit, remove: (String) -> Unit, open: (Quote) -> Unit) {
    var search by remember { mutableStateOf("") }; var expanded by remember { mutableStateOf(false) }
    val catalog = listOf("SCOM.KE", "EQTY.KE", "KCB.KE", "ABSA.KE", "COOP.KE", "EABL.KE", "KPLC.KE")
    val filtered = quotes.filter { it.companyName.contains(search, true) || it.symbol.contains(search, true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, Modifier.weight(1f), singleLine = true, label = { Text("Search stocks") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.width(8.dp)); Box { Button(onClick = { expanded = true }) { Icon(Icons.Default.Add, null); Text("Add") }; DropdownMenu(expanded, { expanded = false }) { catalog.forEach { sym -> DropdownMenuItem(text = { Text(sym) }, onClick = { add(sym); expanded = false }) } } }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(filtered, key = { it.symbol }) { q -> StockCard(q) { open(q) }; TextButton(onClick = { remove(q.symbol) }) { Icon(Icons.Default.DeleteOutline, null); Text("Remove from watchlist") } } }
    }
}

@Composable
private fun PortfolioScreen(holdings: List<Holding>, update: (List<Holding>) -> Unit) {
    val quotes = remember { mutableStateOf<List<Quote>>(emptyList()) }
    // Portfolio uses the same demo quote set when available through the current UI state; sample values keep this screen useful during demo stage.
    val prices = mapOf("SCOM.KE" to 27.45, "KCB.KE" to 42.10, "EQTY.KE" to 58.20, "ABSA.KE" to 18.90)
    val invested = holdings.sumOf { it.shares * it.averagePrice }; val current = holdings.sumOf { it.shares * (prices[it.symbol] ?: it.averagePrice) }; val pnl = current - invested
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Column(Modifier.padding(18.dp)) { Text("Portfolio Value", color = Muted); Text("KSh %,.2f".format(current), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text("%+.2f  (%+.1f%%)".format(pnl, if (invested == 0.0) 0.0 else pnl / invested * 100), color = if (pnl >= 0) Green else Red, fontWeight = FontWeight.Bold) } }
        }
        item { SectionTitle("Holdings", "Track your demo investments") }
        items(holdings, key = { it.symbol }) { h ->
            val price = prices[h.symbol] ?: h.averagePrice; val value = h.shares * price; val gain = value - h.shares * h.averagePrice
            Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFE0EAE4)), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(h.symbol, fontWeight = FontWeight.ExtraBold); Text("${h.shares} shares • Avg KSh %.2f".format(h.averagePrice), color = Muted, style = MaterialTheme.typography.bodySmall) }; Column(horizontalAlignment = Alignment.End) { Text("KSh %,.2f".format(value), fontWeight = FontWeight.ExtraBold); Text("%+.2f".format(gain), color = if (gain >= 0) Green else Red, fontWeight = FontWeight.Bold) } } }
        }
        item { Button(onClick = { showAdd = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add demo holding") } }
        item { Text("Portfolio values are demo calculations for now. Live portfolio syncing can be added later.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
    if (showAdd) {
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Add demo holding") }, text = { Text("A full holding editor will be connected to live prices later. For now, this demo portfolio is preloaded with Safaricom and KCB.") }, confirmButton = { TextButton(onClick = { showAdd = false }) { Text("OK") } })
    }
}

@Composable
private fun AlertsScreen(alerts: List<String>, update: (List<String>) -> Unit) {
    var show by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("🔔 Price Alerts", "Never miss a move") }
        items(alerts) { alert -> Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE0EAE4))) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(50), color = LightGreen) { Icon(Icons.Default.NotificationsActive, null, tint = Green, modifier = Modifier.padding(8.dp)) }; Spacer(Modifier.width(10.dp)); Text(alert, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); IconButton(onClick = { update(alerts - alert) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Muted) } } } }
        item { Button(onClick = { show = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AddAlert, null); Spacer(Modifier.width(6.dp)); Text("Create alert") } }
        item { Text("Demo alerts are stored only while this demo session is open. Live notifications will be connected later.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("Create alert") }, text = { Text("Choose a stock and condition in the live version. Example: SCOM above KSh 30 or daily gain above 5%.") }, confirmButton = { TextButton(onClick = { update(alerts + "SCOM.KE above KSh 30.00"); show = false }) { Text("Add demo alert") } })
}

@Composable
private fun NewsScreen(news: List<NewsItem>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SectionTitle("📰 NSE News", "News linked to your watchlist") }; items(news) { NewsCard(it) }; item { Text("In the live version, news will be linked to affected stocks and corporate actions.", color = Muted, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun NewsCard(item: NewsItem) { Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFE0EAE4)), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(15.dp)) { Text(item.category, color = Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall); Text(item.headline, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("${item.source} • ${item.publishedAt}", color = Muted, style = MaterialTheme.typography.bodySmall) } } }

@Composable
private fun DetailScreen(q: Quote, back: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }; Column { Text(q.companyName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge); Text(q.symbol, color = Muted) } } }
        item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Column(Modifier.padding(18.dp)) { Text("KSh %.2f".format(q.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text("%+.1f%% today".format(q.dailyChange), color = if (q.dailyChange >= 0) Green else Red, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Sparkline(q.history, Modifier.fillMaxWidth().height(150.dp), if (q.dailyChange >= 0) Green else Red) } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { DetailStat("Day high", q.dayHigh); DetailStat("Day low", q.dayLow); DetailStat("Volume", q.volume.toDouble()) } }
        item { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE0EAE4))) { Column(Modifier.padding(16.dp)) { Text("Signal: ${q.signal.name}", color = Green, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(5.dp)); Text(q.signalExplanation, color = Muted) } } }
        item { Text("Demo chart and indicators. Live historical data will replace the sample values.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun DetailStat(label: String, value: Double) { Column(Modifier.weight(1f)) { Text(label, color = Muted, style = MaterialTheme.typography.labelSmall); Text("%.2f".format(value), fontWeight = FontWeight.Bold) } }

@Composable
private fun ErrorState(message: String, retry: () -> Unit) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Something went wrong", fontWeight = FontWeight.Bold); Text(message, color = Muted); Spacer(Modifier.height(8.dp)); Button(onClick = retry) { Text("Retry") } } }

@Composable private fun DemoBanner() { Surface(color = Color(0xFFF7FAF8), shape = RoundedCornerShape(14.dp)) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("DEMO MODE — prices, signals and portfolio values are sample data.", color = Muted, style = MaterialTheme.typography.bodySmall) } } }

private fun fmt(value: Double): String = when { value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000); value >= 1_000_000 -> "%.1fM".format(value / 1_000_000); value >= 1_000 -> "%.1fK".format(value / 1_000); else -> "%.0f".format(value) }
