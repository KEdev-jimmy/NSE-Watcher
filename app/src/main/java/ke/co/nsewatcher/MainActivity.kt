package ke.co.nsewatcher

import android.os.Bundle
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { NSEWatcherApp(MarketViewModel(application)) } } }
data class AppState(val snapshot: MarketSnapshot? = null, val news: List<NewsItem> = emptyList(), val loading: Boolean = true, val error: String? = null)
class MarketViewModel(application: Application, private val repository: MarketRepository = DemoMarketRepository(application)) : ViewModel() {
    private val _state = MutableStateFlow(AppState()); val state: StateFlow<AppState> = _state
    init { refresh() }
    fun refresh() = viewModelScope.launch { _state.value = _state.value.copy(loading = true, error = null); repository.snapshot(true).onSuccess { snap -> repository.news().onSuccess { _state.value = AppState(snap, it, false) } }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "Unable to load market data") } }
    fun add(symbol: String) = viewModelScope.launch { repository.addSymbol(symbol); refresh() }
    fun remove(symbol: String) = viewModelScope.launch { repository.removeSymbol(symbol); refresh() }
}

@Composable fun NSEWatcherApp(vm: MarketViewModel) {
    val state by vm.state.collectAsStateWithLifecycle(); var tab by remember { mutableIntStateOf(0) }; var selected by remember { mutableStateOf<Quote?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF54D6A5), secondary = Color(0xFF9DB8FF), surface = Color(0xFF111827))) {
        Surface { if (selected != null) DetailScreen(selected!!, { selected = null }) else Scaffold(
            topBar = { TopAppBar(title = { Column { Text("NSE Watcher", fontWeight = FontWeight.Bold); Text("DEMO / SAMPLE DATA — not live prices", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) } }, actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh demo market data") } }) },
            bottomBar = { NavigationBar { listOf("Dashboard" to Icons.Default.Home, "Watchlist" to Icons.Default.Star, "Alerts" to Icons.Default.Notifications, "News" to Icons.Default.Article).forEachIndexed { i, item -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) }) } } }
        ) { padding -> Box(Modifier.padding(padding)) { when { state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center)); state.error != null -> ErrorState(state.error, vm::refresh); else -> when(tab) { 0 -> Dashboard(state, { selected = it }); 1 -> Watchlist(state.snapshot?.quotes.orEmpty(), vm::add, vm::remove, { selected = it }); 2 -> AlertsScreen(); else -> NewsScreen(state.news) } } } } }
    }
}

@Composable private fun Dashboard(state: AppState, open: (Quote) -> Unit) { val snapshot = state.snapshot ?: return; LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { StatusCard(snapshot) }; item { Text("Watchlist overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; item { Summary(snapshot.quotes) }; item { Text("Tracked equities", style = MaterialTheme.typography.titleMedium) }; items(snapshot.quotes, key = { it.symbol }) { StockCard(it, { open(it) }) }; item { Text("Recent news", style = MaterialTheme.typography.titleMedium) }; items(state.news.take(2)) { NewsCard(it) } } }
@Composable private fun StatusCard(s: MarketSnapshot) { val stamp = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(s.updatedAt); Card { Column(Modifier.padding(16.dp)) { AssistChip(onClick = {}, label = { Text("Market status: ${s.status}") }, leadingIcon = { Icon(Icons.Default.Info, null) }); Spacer(Modifier.height(8.dp)); Text("Last updated: $stamp (demo refresh)", style = MaterialTheme.typography.bodyMedium); Text("Sample values are illustrative and may be delayed or unavailable. They are not live NSE quotes.", style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun Summary(quotes: List<Quote>) { val best = quotes.maxByOrNull { it.dailyChange }; val worst = quotes.minByOrNull { it.dailyChange }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Positive", quotes.count { it.dailyChange > 0 }.toString(), Modifier.weight(1f)); Metric("Strongest", best?.symbol ?: "—", Modifier.weight(1f)); Metric("Weakest", worst?.symbol ?: "—", Modifier.weight(1f)) } }
@Composable private fun Metric(label: String, value: String, mod: Modifier) { Card(mod) { Column(Modifier.padding(12.dp)) { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun StockCard(q: Quote, open: () -> Unit) { val good = q.dailyChange >= 0; Card(Modifier.fillMaxWidth().clickable(onClick = open)) { Column(Modifier.padding(16.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(q.companyName, fontWeight = FontWeight.Bold); Text(q.symbol, style = MaterialTheme.typography.labelMedium) }; Column(horizontalAlignment = Alignment.End) { Text("KES %.2f".format(q.price), fontWeight = FontWeight.Bold); Text("%+.1f%% today".format(q.dailyChange), color = if(good) Color(0xFF54D6A5) else Color(0xFFFF8A80)) } }; Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text("1W %+.1f%%".format(q.weeklyChange)); Text("1M %+.1f%%".format(q.monthlyChange)); Text("Vol ${q.volume / 1000}k") }; AssistChip(onClick = open, label = { Text(q.signal.name) }) } } }
@Composable private fun Watchlist(quotes: List<Quote>, add: (String) -> Unit, remove: (String) -> Unit, open: (Quote) -> Unit) { var expanded by remember { mutableStateOf(false) }; val available = listOf("SCOM.KE", "EQTY.KE", "KCB.KE", "ABSA.KE") - quotes.map { it.symbol }.toSet(); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("My watchlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Box { FilledTonalButton(onClick = { expanded = true }) { Icon(Icons.Default.Add, null); Text(" Add") }; DropdownMenu(expanded, { expanded = false }) { available.forEach { symbol -> DropdownMenuItem(text = { Text("Add $symbol") }, onClick = { add(symbol); expanded = false }) } } } } }; if (quotes.isEmpty()) item { Text("No stocks tracked. Add an NSE symbol to start monitoring.") }; items(quotes, key = { it.symbol }) { q -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { StockCard(q) { open(q) } }; IconButton(onClick = { remove(q.symbol) }) { Icon(Icons.Default.Delete, "Remove ${q.symbol}") } } } } }
@Composable private fun DetailScreen(q: Quote, back: () -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { TextButton(back) { Icon(Icons.Default.ArrowBack, null); Text("Back") } }; item { Text(q.companyName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("${q.symbol} • DEMO / SAMPLE DATA") }; item { Card { Column(Modifier.padding(18.dp)) { Text("KES %.2f".format(q.price), style = MaterialTheme.typography.displaySmall); Text("%+.1f%% today".format(q.dailyChange), color = if(q.dailyChange >= 0) Color(0xFF54D6A5) else Color(0xFFFF8A80)); Spacer(Modifier.height(8.dp)); Text("Day high KES %.2f  •  Day low KES %.2f".format(q.dayHigh, q.dayLow)); Text("Volume %,d  •  Average %,d".format(q.volume, q.averageVolume)) } } }; item { Text("Historical price trend", style = MaterialTheme.typography.titleMedium) }; item { Sparkline(q.history) }; item { Card { Column(Modifier.padding(16.dp)) { Text("${q.signal} signal", fontWeight = FontWeight.Bold); Text(q.signalExplanation); Spacer(Modifier.height(8.dp)); Text("1 week %+.1f%%  •  1 month %+.1f%%".format(q.weeklyChange, q.monthlyChange)) } } }; item { Text("Relevant news & corporate actions", style = MaterialTheme.typography.titleMedium); Text("Demo mode has no confirmed company announcements. A secure backend can supply provider news, dividends, and corporate actions here.") } } }
@Composable private fun Sparkline(values: List<Double>) { Card(Modifier.fillMaxWidth()) { Canvas(Modifier.fillMaxWidth().height(130.dp).padding(16.dp)) { if(values.size > 1) { val min = values.min(); val max = values.max(); val range = (max - min).takeIf { it > 0 } ?: 1.0; val path = Path(); values.forEachIndexed { i, value -> val x = size.width * i / (values.size - 1); val y = size.height - ((value - min) / range).toFloat() * size.height; if(i == 0) path.moveTo(x,y) else path.lineTo(x,y) }; drawPath(path, Color(0xFF54D6A5), style = androidx.compose.ui.graphics.drawscope.Stroke(5f)) } } } }
@Composable private fun AlertsScreen() { val alerts = remember { listOf(PriceAlert("scom-move", "SCOM.KE", AlertType.DAILY_GAIN, 5.0, true), PriceAlert("kcb-volume", "KCB.KE", AlertType.HIGH_VOLUME, null, false)) }; var enabled by remember { mutableStateOf(alerts.associate { it.id to it.enabled }) }; LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Notifications are prepared for a future backend. Demo mode does not send market alerts.") }; items(alerts) { alert -> Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(alert.symbol, fontWeight = FontWeight.Bold); Text("${alert.type.name.replace('_',' ')}${alert.threshold?.let { ": $it%" } ?: ""}") }; Switch(enabled[alert.id] == true, { enabled = enabled + (alert.id to it) }) } } }; item { Text("Alert types supported: price thresholds, daily movement, unusual volume, breakouts, news, dividends and corporate actions.", style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun NewsScreen(news: List<NewsItem>) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Market news", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Demo feed — connect your backend for real, attributable news.") }; items(news) { NewsCard(it) } } }
@Composable private fun NewsCard(n: NewsItem) { Card { Column(Modifier.padding(16.dp)) { Text(n.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); Text(n.headline, fontWeight = FontWeight.Bold); Text("${n.source} • ${n.publishedAt}", style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun ErrorState(message: String, retry: () -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.Error, null); Text("Market data unavailable", style = MaterialTheme.typography.titleLarge); Text(message); Button(retry) { Text("Try again") } } }
