@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ke.co.nsewatcher

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ke.co.nsewatcher.data.DemoMarketRepository
import ke.co.nsewatcher.data.MarketRepository
import ke.co.nsewatcher.domain.MarketSnapshot
import ke.co.nsewatcher.domain.NewsItem
import ke.co.nsewatcher.domain.Quote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private val Green = Color(0xFF00A859)
private val LightGreen = Color(0xFFE8F8EF)
private val Red = Color(0xFFE53935)
private val TextDark = Color(0xFF102018)
private val Muted = Color(0xFF68766F)
private val BorderGreen = Color(0xFFE0EAE4)

private val AppColors = androidx.compose.material3.lightColorScheme(
    primary = Green, onPrimary = Color.White, secondary = Green,
    background = Color.White, surface = Color.White,
    surfaceVariant = Color(0xFFF4F7F5), onBackground = TextDark,
    onSurface = TextDark, onSurfaceVariant = Muted, error = Red
)

private val DarkAppColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF35C979), onPrimary = Color(0xFF00391C), secondary = Color(0xFF35C979),
    background = Color(0xFF0E1511), surface = Color(0xFF16201A),
    surfaceVariant = Color(0xFF223027), onBackground = Color.White,
    onSurface = Color.White, onSurfaceVariant = Color(0xFFB8C6BD), error = Color(0xFFFF6B64)
)

data class AppState(val snapshot: MarketSnapshot? = null, val news: List<NewsItem> = emptyList(), val loading: Boolean = true, val error: String? = null)
data class Holding(val symbol: String, val shares: Int, val averagePrice: Double)
data class IndexDemo(val name: String, val value: Double, val change: Double, val description: String)
data class FundamentalDemo(val marketCap: String, val pe: String, val pb: String, val eps: String, val dividendYield: String, val roe: String, val revenueGrowth: String, val profitGrowth: String, val debtEquity: String)

private val demoIndices = listOf(
    IndexDemo("NASI", 183.42, 0.84, "Broad market"),
    IndexDemo("NSE 20", 1945.60, 0.51, "Blue chips"),
    IndexDemo("NSE 25", 3712.18, 0.73, "Large companies")
)

private val fundamentals = mapOf(
    "SCOM.KE" to FundamentalDemo("KSh 750B", "12.4", "2.8", "1.50", "4.2%", "22.1%", "8.4%", "11.2%", "0.32"),
    "EQTY.KE" to FundamentalDemo("KSh 178B", "5.9", "0.9", "8.00", "5.8%", "18.4%", "12.1%", "10.6%", "0.74"),
    "KCB.KE" to FundamentalDemo("KSh 121B", "4.8", "0.7", "8.30", "7.1%", "16.9%", "9.2%", "8.7%", "0.81"),
    "ABSA.KE" to FundamentalDemo("KSh 70B", "6.2", "0.8", "2.16", "6.3%", "14.7%", "7.1%", "6.8%", "0.77"),
    "COOP.KE" to FundamentalDemo("KSh 94B", "5.5", "0.9", "2.87", "7.0%", "17.2%", "10.4%", "9.5%", "0.69"),
    "EABL.KE" to FundamentalDemo("KSh 307B", "15.8", "4.1", "2.44", "3.1%", "25.3%", "6.9%", "5.8%", "1.12"),
    "KPLC.KE" to FundamentalDemo("Demo", "—", "—", "—", "—", "—", "—", "—", "—")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NSEWatcherApp(MarketViewModel(application), getSharedPreferences("nse_watcher_user", Context.MODE_PRIVATE)) }
    }
}

class MarketViewModel(application: Application, private val repository: MarketRepository = DemoMarketRepository(application)) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    init { refresh() }
    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        repository.snapshot(true).onSuccess { snapshot ->
            repository.news().onSuccess { news -> _state.value = AppState(snapshot, news, false, null) }
                .onFailure { e -> _state.value = AppState(snapshot, emptyList(), false, e.message) }
        }.onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load market data") }
    }
    fun add(symbol: String) = viewModelScope.launch { repository.addSymbol(symbol); refresh() }
    fun remove(symbol: String) = viewModelScope.launch { repository.removeSymbol(symbol); refresh() }
}

@Composable
fun NSEWatcherApp(vm: MarketViewModel, prefs: SharedPreferences) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Quote?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var infoDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var darkMode by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var username by rememberSaveable { mutableStateOf(prefs.getString("username", "jimmymwangi") ?: "jimmymwangi") }
    var email by rememberSaveable { mutableStateOf(prefs.getString("email", "jimmy.mwangi@email.com") ?: "jimmy.mwangi@email.com") }
    var password by rememberSaveable { mutableStateOf(prefs.getString("password", "password123") ?: "password123") }
    var description by rememberSaveable { mutableStateOf(prefs.getString("description", "Building wealth, one stock at a time.") ?: "Building wealth, one stock at a time.") }
    var avatarUri by rememberSaveable { mutableStateOf(prefs.getString("avatar_uri", null)) }
    var holdings by remember { mutableStateOf(listOf(Holding("SCOM.KE", 500, 21.50), Holding("KCB.KE", 200, 38.00))) }
    var alerts by remember { mutableStateOf(listOf("SCOM.KE above KSh 30.00", "KCB.KE daily gain above 5%", "EABL.KE volume above average")) }
    fun savePrefs() { prefs.edit().putBoolean("dark_mode", darkMode).putString("username", username).putString("email", email).putString("password", password).putString("description", description).putString("avatar_uri", avatarUri).apply() }
    val canGoBack = settings || account || appearance || selected != null || tab != 0 || searchOpen
    BackHandler(enabled = canGoBack) {
        when { searchOpen -> searchOpen = false; account -> account = false; appearance -> appearance = false; settings -> settings = false; selected != null -> selected = null; tab != 0 -> tab = 0 }
    }
    MaterialTheme(colorScheme = if (darkMode) DarkAppColors else AppColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                account -> AccountSettingsScreen(username, email, password, description, avatarUri, { account = false }, { username = it; savePrefs() }, { email = it; savePrefs() }, { password = it; savePrefs() }, { description = it; savePrefs() }, { avatarUri = it; savePrefs() })
                appearance -> AppearanceScreen(darkMode, { appearance = false }) { darkMode = it; savePrefs() }
                settings -> SettingsScreen(username, description, avatarUri, { settings = false }, { account = true }, { appearance = true }, { infoDialog = "Notifications will connect to alert preferences and push delivery in the live phase." }, { infoDialog = "Security controls will be enabled when account authentication is connected." }, { infoDialog = "Help and support will be connected before production release." })
                selected != null -> DetailScreen(selected!!) { selected = null }
                else -> MainShell(state, vm, tab, { tab = it }, { selected = it }, { settings = true }, { searchOpen = true }, holdings, { holdings = it }, alerts, { alerts = it }, { tab = 1 })
            }
            if (searchOpen) SearchDialog(state.snapshot?.quotes.orEmpty(), { searchOpen = false; selected = it }, { searchOpen = false })
            infoDialog?.let { message -> AlertDialog(onDismissRequest = { infoDialog = null }, confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("OK") } }, title = { Text("NSE Watcher") }, text = { Text(message) }) }
        }
    }
}

@Composable
private fun MainShell(state: AppState, vm: MarketViewModel, tab: Int, setTab: (Int) -> Unit, open: (Quote) -> Unit, openSettings: () -> Unit, openSearch: () -> Unit, holdings: List<Holding>, setHoldings: (List<Holding>) -> Unit, alerts: List<String>, setAlerts: (List<String>) -> Unit, viewAllWatchlist: () -> Unit) {
    Scaffold(topBar = { TopBar(openSettings, openSearch) }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            val nav = listOf("Home" to Icons.Default.Home, "Watchlist" to Icons.Default.Star, "Portfolio" to Icons.Default.AccountBalanceWallet, "Alerts" to Icons.Default.Notifications, "News" to Icons.Default.Article)
            nav.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { setTab(index) }, icon = { Icon(imageVector = item.second, contentDescription = item.first, modifier = Modifier.size(24.dp)) }, label = { Text(item.first) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = LightGreen, unselectedIconColor = Muted, unselectedTextColor = Muted)) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Green)
                state.error != null && state.snapshot == null -> ErrorState(state.error, vm::refresh)
                tab == 0 -> Dashboard(state, open, viewAllWatchlist)
                tab == 1 -> Watchlist(state.snapshot?.quotes.orEmpty(), vm::add, vm::remove, open)
                tab == 2 -> PortfolioScreen(holdings, setHoldings)
                tab == 3 -> AlertsScreen(alerts, setAlerts)
                else -> NewsScreen(state.news)
            }
        }
    }
}

@Composable
private fun TopBar(openSettings: () -> Unit, openSearch: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp), RoundedCornerShape(11.dp), color = LightGreen) {
                    Icon(imageVector = Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.padding(7.dp), tint = Green)
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("NSE Watcher", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Track • Analyze • Grow", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        },
        actions = {
            IconButton(onClick = openSearch) { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Green, modifier = Modifier.size(27.dp)) }
            IconButton(onClick = openSettings) { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Green, modifier = Modifier.size(28.dp)) }
        }
    )
}

@Composable
private fun Dashboard(state: AppState, open: (Quote) -> Unit, viewAll: () -> Unit) {
    val quotes = state.snapshot?.quotes.orEmpty()
    val gainers = quotes.sortedByDescending { it.dailyChange }
    val losers = quotes.sortedBy { it.dailyChange }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MarketHero(state.snapshot?.updatedAt?.toString()?.replace('T', ' ')?.take(16) ?: "—") }
        item { MarketSummary(quotes) }
        item { SectionTitle("Market Indices", "NSE benchmark snapshot") }
        item { IndicesRow() }
        item { SectionTitleWithAction("🔥 Market Movers", "Top gainers and losers", "View All") }
        item { MarketMovers(gainers.take(4), losers.take(4), open) }
        item { SectionTitleWithAction("⭐ Your Watchlist", "Your tracked stocks", "View All", viewAll) }
        item { LazyRow(contentPadding = PaddingValues(horizontal = 1.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(quotes.take(6), key = { it.symbol }) { quote -> HorizontalStockCard(quote) { open(quote) } } } }
        item { SectionTitle("🧠 NSE Watcher Intelligence", "Explainable demo analysis") }
        item { IntelligenceSummary(quotes) }
        item { SectionTitleWithAction("📰 Latest News", "From your tracked stocks", "View All") }
        items(state.news.take(3), key = { it.id }) { NewsCard(it) }
        item { DemoBanner() }
    }
}

@Composable private fun MarketHero(updated: String) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(Green)); Spacer(Modifier.width(7.dp)); Text("MARKET SESSION", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.height(3.dp)); Text("DEMO MARKET", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Green)
                Text("Sample prices • not live data", style = MaterialTheme.typography.bodySmall, color = Muted); Text("Updated $updated", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) { Text("NSE", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge); Text("Nairobi Securities\nExchange", style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.End) }
        }
    }
}

@Composable private fun MarketSummary(quotes: List<Quote>) {
    val gain = quotes.count { it.dailyChange > 0 }; val loss = quotes.count { it.dailyChange < 0 }; val flat = quotes.size - gain - loss
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(13.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryStat("Stocks", quotes.size.toString(), Green, Modifier.weight(1f)); SummaryDivider(); SummaryStat("Gainers", gain.toString(), Green, Modifier.weight(1f)); SummaryDivider(); SummaryStat("Losers", loss.toString(), Red, Modifier.weight(1f)); SummaryDivider(); SummaryStat("Unchanged", flat.toString(), TextDark, Modifier.weight(1f))
        }
    }
}
@Composable private fun SummaryDivider() { Box(Modifier.width(1.dp).height(34.dp).background(BorderGreen)) }
@Composable private fun SummaryStat(label: String, value: String, tint: Color, modifier: Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = tint); Text(label, style = MaterialTheme.typography.labelSmall, color = Muted) } }
@Composable private fun SectionTitle(title: String, subtitle: String) { Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun SectionTitleWithAction(title: String, subtitle: String, action: String = "", onAction: () -> Unit = {}) { Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall) }; if (action.isNotEmpty()) { TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) { Text(action, color = Green, fontWeight = FontWeight.Bold); Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp)) } } } }

@Composable private fun IndicesRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) { items(demoIndices) { index ->
        Card(Modifier.width(150.dp), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) { Column(Modifier.padding(11.dp)) { Text(index.name, fontWeight = FontWeight.ExtraBold); Text(String.format(Locale.US, "%.2f", index.value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "%+.2f%%", index.change), color = if (index.change >= 0) Green else Red, fontWeight = FontWeight.Bold); Text(index.description, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1) } }
    } }
}

@Composable private fun MarketMovers(gainers: List<Quote>, losers: List<Quote>, open: (Quote) -> Unit) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = BorderStroke(1.dp, BorderGreen)) { Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) { MoverColumn("Top Gainers", gainers, true, Modifier.weight(1f), open); Box(Modifier.width(1.dp).height(170.dp).background(BorderGreen)); MoverColumn("Top Losers", losers, false, Modifier.weight(1f), open) } } }
@Composable private fun MoverColumn(title: String, quotes: List<Quote>, positive: Boolean, modifier: Modifier, open: (Quote) -> Unit) { Column(modifier.padding(horizontal = 13.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = if (positive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, tint = if (positive) Green else Red, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(5.dp)); Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = if (positive) Green else Red) }; Spacer(Modifier.height(8.dp)); quotes.forEach { quote -> MoverRow(quote, positive) { open(quote) } } } }
@Composable private fun MoverRow(q: Quote, positive: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { StockLogo(q.symbol, 30); Spacer(Modifier.width(7.dp)); Text(q.symbol.removeSuffix(".KE"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text(String.format(Locale.US, "%+.1f%%", q.dailyChange), fontWeight = FontWeight.ExtraBold, color = if (positive) Green else Red, style = MaterialTheme.typography.bodyMedium) } }

@Composable private fun StockLogo(symbol: String, size: Int) {
    val short = symbol.removeSuffix(".KE")
    val background = when (short) { "SCOM" -> Color(0xFF0B8F4D); "KCB" -> Color(0xFF1B4D9B); "EQTY" -> Color(0xFF137A45); "ABSA" -> Color(0xFFC6283D); "COOP" -> Color(0xFF1769AA); "EABL" -> Color(0xFFB8A23A); "KPLC" -> Color(0xFF285C8C); "BAT" -> Color(0xFF243B75); else -> Green }
    Surface(Modifier.size(size.dp), RoundedCornerShape((size / 3).dp), color = background) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(short.take(3), color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun HorizontalStockCard(q: Quote, open: () -> Unit) {
    val tint = if (q.dailyChange >= 0) Green else Red
    Card(Modifier.width(188.dp).clickable(onClick = open), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, BorderGreen)) { Column(Modifier.padding(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { StockLogo(q.symbol, 30); Spacer(Modifier.width(7.dp)); Column(Modifier.weight(1f)) { Text(q.symbol.removeSuffix(".KE"), fontWeight = FontWeight.ExtraBold); Text(q.companyName, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1) }; Text(String.format(Locale.US, "%+.1f%%", q.dailyChange), color = tint, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.height(7.dp)); Text(String.format(Locale.US, "KSh %.2f", q.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(4.dp)); Sparkline(q.history, tint); Text(q.signal.name, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    } }
}

@Composable private fun Sparkline(values: List<Double>, tint: Color) {
    Canvas(Modifier.fillMaxWidth().height(35.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: 0.0; val max = values.maxOrNull() ?: 1.0; val range = (max - min).takeIf { it > 0.0 } ?: 1.0; val path = Path()
        values.forEachIndexed { index, value -> val x = size.width * index / (values.size - 1).toFloat(); val y = size.height - ((value - min) / range).toFloat() * size.height; if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path = path, color = tint, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

@Composable private fun IntelligenceSummary(quotes: List<Quote>) {
    val best = quotes.maxByOrNull { watcherScore(it) }
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) { Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = Icons.Default.AutoGraph, contentDescription = null, tint = Green, modifier = Modifier.size(23.dp)); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text("NSE Watcher Score", fontWeight = FontWeight.ExtraBold); Text("Rule-based demo score, not investment advice", style = MaterialTheme.typography.labelSmall, color = Muted) }; if (best != null) ScoreBadge(watcherScore(best)) }
        Spacer(Modifier.height(9.dp)); Text(best?.let { "Highest demo score: ${it.symbol.removeSuffix(".KE")} — ${it.signalExplanation}" } ?: "Add stocks to begin analysis.", style = MaterialTheme.typography.bodySmall)
    } }
}
private fun watcherScore(q: Quote): Int { val momentum = (q.dailyChange * 3 + q.weeklyChange * 1.5 + q.monthlyChange).coerceIn(-20.0, 40.0); val volume = if (q.averageVolume > 0) ((q.volume.toDouble() / q.averageVolume) * 10).coerceIn(0.0, 15.0) else 0.0; return (45 + momentum + volume).coerceIn(0.0, 100.0).toInt() }
@Composable private fun ScoreBadge(score: Int) { Surface(shape = RoundedCornerShape(10.dp), color = LightGreen) { Text("$score/100", color = Green, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) } }

@Composable private fun DetailScreen(q: Quote, close: () -> Unit) {
    val score = watcherScore(q)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back") }; StockLogo(q.symbol, 42); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(q.companyName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge); Text(q.symbol, color = Muted) }; ScoreBadge(score) } }
        item { PriceCard(q) }; item { ChartCard(q) }; item { ScoreCard(q, score) }; item { FundamentalsCard(fundamentals[q.symbol]) }; item { DisclaimerCard() }
    }
}
@Composable private fun PriceCard(q: Quote) { val tint = if (q.dailyChange >= 0) Green else Red; Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Column(Modifier.padding(16.dp)) { Text("DEMO PRICE", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "KSh %.2f", q.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text(String.format(Locale.US, "%+.2f%% today", q.dailyChange), color = tint, fontWeight = FontWeight.ExtraBold); Text("Day range: KSh ${String.format(Locale.US, "%.2f", q.dayLow)} – ${String.format(Locale.US, "%.2f", q.dayHigh)}", color = Muted, style = MaterialTheme.typography.labelSmall) } } }
@Composable private fun ChartCard(q: Quote) { var period by rememberSaveable { mutableStateOf("1M") }; val periods = listOf("1D", "1W", "1M", "3M", "6M", "1Y"); Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Price Trend", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text(period, color = Green, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(8.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { periods.forEach { item -> FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item) }) } }; Spacer(Modifier.height(8.dp)); Sparkline(q.history, if (q.dailyChange >= 0) Green else Red); Text("Demo historical series • live chart will use sourced market data", style = MaterialTheme.typography.labelSmall, color = Muted) } } }
@Composable private fun ScoreCard(q: Quote, score: Int) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("NSE Watcher Score", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); ScoreBadge(score) }; Spacer(Modifier.height(10.dp)); ScoreLine("Momentum", ((q.dailyChange + q.weeklyChange + q.monthlyChange) * 3.0 + 50).coerceIn(0.0, 100.0).toInt()); ScoreLine("Volume", if (q.averageVolume > 0) ((q.volume.toDouble() / q.averageVolume) * 50).coerceIn(0.0, 100.0).toInt() else 0); ScoreLine("Trend", if (q.monthlyChange >= 0) 70 else 35); Text("Score is a transparent analytical indicator based on demo market factors. It is not a recommendation or prediction.", style = MaterialTheme.typography.labelSmall, color = Muted) } } }
@Composable private fun ScoreLine(label: String, value: Int) { Column(Modifier.fillMaxWidth().padding(bottom = 7.dp)) { Row { Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); Text("$value/100", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }; Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(BorderGreen)) { Box(Modifier.fillMaxWidth(value.coerceIn(0, 100) / 100f).height(6.dp).background(Green)) } } }
@Composable private fun FundamentalsCard(f: FundamentalDemo?) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) { Column(Modifier.padding(14.dp)) { Text("Fundamentals", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); if (f == null) Text("No demo fundamentals available.", color = Muted) else { FundamentalRow("Market cap", f.marketCap); FundamentalRow("P/E", f.pe); FundamentalRow("P/B", f.pb); FundamentalRow("EPS", f.eps); FundamentalRow("Dividend yield", f.dividendYield); FundamentalRow("ROE", f.roe); FundamentalRow("Revenue growth", f.revenueGrowth); FundamentalRow("Profit growth", f.profitGrowth); FundamentalRow("Debt / equity", f.debtEquity) } } } }
@Composable private fun FundamentalRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, color = Muted, modifier = Modifier.weight(1f)); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun DisclaimerCard() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Text("DEMO ONLY • Prices, scores and fundamentals are sample data. NSE Watcher does not provide investment advice.", Modifier.padding(14.dp), style = MaterialTheme.typography.labelSmall, color = Muted) } }
@Composable private fun ErrorState(message: String, retry: () -> Unit) { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Unable to load demo data", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text(message, color = Muted, textAlign = TextAlign.Center); TextButton(onClick = retry) { Text("Retry", color = Green) } } }
@Composable private fun DemoBanner() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Text("DEMO MODE • Sample market data only. Not affiliated with or endorsed by the Nairobi Securities Exchange.", Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall, color = Muted) } }

@Composable private fun NewsCard(item: NewsItem) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) { Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(58.dp), RoundedCornerShape(10.dp), color = LightGreen) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Default.Article, contentDescription = null, tint = Green, modifier = Modifier.size(28.dp)) } }; Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(item.headline, fontWeight = FontWeight.Bold, maxLines = 2); Text("${item.source} • ${item.category}", color = Muted, style = MaterialTheme.typography.labelSmall) }; Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Muted) } } }

@Composable private fun SearchDialog(quotes: List<Quote>, open: (Quote) -> Unit, close: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = quotes.filter { query.isBlank() || it.symbol.contains(query, true) || it.companyName.contains(query, true) }
    AlertDialog(onDismissRequest = close, confirmButton = { TextButton(onClick = close) { Text("Close") } }, title = { Text("Search stocks") }, text = { Column { TextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Symbol or company") }); Spacer(Modifier.height(8.dp)); results.take(6).forEach { q -> Row(Modifier.fillMaxWidth().clickable { open(q) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { StockLogo(q.symbol, 30); Spacer(Modifier.width(8.dp)); Column { Text(q.symbol.removeSuffix(".KE"), fontWeight = FontWeight.Bold); Text(q.companyName, color = Muted, style = MaterialTheme.typography.labelSmall) } } } } })
}

@Composable private fun Watchlist(quotes: List<Quote>, add: (String) -> Unit, remove: (String) -> Unit, open: (Quote) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { SectionTitle("Your Watchlist", "Track stocks you want to follow") }; items(quotes, key = { it.symbol }) { q -> Card(Modifier.fillMaxWidth().clickable { open(q) }, RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { StockLogo(q.symbol, 38); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(q.companyName, fontWeight = FontWeight.Bold); Text(q.symbol, color = Muted, style = MaterialTheme.typography.labelSmall) }; Text(String.format(Locale.US, "KSh %.2f", q.price), fontWeight = FontWeight.Bold) } } }; item { Text("Demo catalog: SCOM, EQTY, KCB, ABSA, COOP, EABL, KPLC", color = Muted, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun PortfolioScreen(holdings: List<Holding>, setHoldings: (List<Holding>) -> Unit) {
    val total = holdings.sumOf { it.shares * it.averagePrice }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { SectionTitle("Portfolio", "Demo holdings and cost basis") }; item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Column(Modifier.padding(16.dp)) { Text("Demo invested value", color = Muted); Text(String.format(Locale.US, "KSh %.2f", total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) } } }; items(holdings) { h -> Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { StockLogo(h.symbol, 36); Spacer(Modifier.width(9.dp)); Column(Modifier.weight(1f)) { Text(h.symbol.removeSuffix(".KE"), fontWeight = FontWeight.Bold); Text("${h.shares} shares", color = Muted) }; Text(String.format(Locale.US, "KSh %.2f", h.averagePrice), fontWeight = FontWeight.Bold) } } }; item { TextButton(onClick = { setHoldings(holdings + Holding("EQTY.KE", 100, 35.00)) }) { Text("Add demo holding") } } }
}

@Composable private fun AlertsScreen(alerts: List<String>, setAlerts: (List<String>) -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { SectionTitle("Alerts", "Demo alert rules") }; items(alerts) { alert -> Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = Green); Spacer(Modifier.width(9.dp)); Text(alert, Modifier.weight(1f)) } } }; item { TextButton(onClick = { setAlerts(alerts + "New demo alert") }) { Text("Add demo alert") } } } }
@Composable private fun NewsScreen(news: List<NewsItem>) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { SectionTitle("Latest News", "Demo market headlines") }; items(news, key = { it.id }) { NewsCard(it) } } }

@Composable private fun SettingsScreen(username: String, description: String, avatarUri: String?, close: () -> Unit, account: () -> Unit, appearance: () -> Unit, notifications: () -> Unit, security: () -> Unit, help: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { SettingsHeader("Settings", close) }; item { ProfileCard(username, description, avatarUri, account) }; item { SettingsRow("Account Settings", "Username, email, password and profile", Icons.Default.Settings, account) }; item { SettingsRow("Appearance", "Light or dark mode", Icons.Default.DarkMode, appearance) }; item { SettingsRow("Notifications", "Alert and push preferences", Icons.Default.Notifications, notifications) }; item { SettingsRow("Security", "Account security controls", Icons.Default.Settings, security) }; item { SettingsRow("Help & Support", "Demo support centre", Icons.Default.Article, help) }; item { DemoBanner() } }
}
@Composable private fun SettingsHeader(title: String, back: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back") }; Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) } }
@Composable private fun ProfileCard(username: String, description: String, avatarUri: String?, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(avatarUri, 58); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text("@$username", color = Muted, style = MaterialTheme.typography.labelSmall); Text(description, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2); Text("Kenya investor | NSE Watcher", color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Green) } } }
@Composable private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = icon, contentDescription = null, tint = Green, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall) }; Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Muted) } } }

@Composable private fun AccountSettingsScreen(username: String, email: String, password: String, description: String, avatarUri: String?, close: () -> Unit, setUsername: (String) -> Unit, setEmail: (String) -> Unit, setPassword: (String) -> Unit, setDescription: (String) -> Unit, setAvatar: (String) -> Unit) {
    var field by rememberSaveable { mutableStateOf<String?>(null) }
    var value by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { setAvatar(it.toString()) } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SettingsHeader("Account Settings", close) }
        item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Avatar(avatarUri, 88); TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("Change profile picture", color = Green) } } } }
        item { EditableRow("Username", username) { field = "Username"; value = username } }
        item { EditableRow("Email", email) { field = "Email"; value = email } }
        item { EditableRow("Password", "••••••••") { field = "Password"; value = password } }
        item { EditableRow("Description", description) { field = "Description"; value = description } }
    }
    field?.let { current -> AlertDialog(onDismissRequest = { field = null }, confirmButton = { TextButton(onClick = { when (current) { "Username" -> setUsername(value); "Email" -> setEmail(value); "Password" -> setPassword(value); "Description" -> setDescription(value) }; field = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { field = null }) { Text("Cancel") } }, title = { Text("Edit $current") }, text = { TextField(value = value, onValueChange = { value = it }, visualTransformation = if (current == "Password") PasswordVisualTransformation() else VisualTransformation.None) }) }
}
@Composable private fun EditableRow(title: String, value: String, onClick: () -> Unit) { SettingsRow(title, value, Icons.Default.Settings, onClick) }
@Composable private fun AppearanceScreen(darkMode: Boolean, close: () -> Unit, setDark: (Boolean) -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { SettingsHeader("Appearance", close) }; item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = null, tint = Green); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Dark mode", fontWeight = FontWeight.Bold); Text(if (darkMode) "Dark theme enabled" else "Light theme enabled", color = Muted, style = MaterialTheme.typography.labelSmall) }; Switch(checked = darkMode, onCheckedChange = setDark) } } } } }

@Composable private fun Avatar(uriString: String?, size: Int) {
    val context = LocalContext.current
    val bitmap = remember(uriString) { uriString?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it))?.use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() } }.getOrNull() } }
    Surface(Modifier.size(size.dp), CircleShape, color = LightGreen) {
        if (bitmap != null) Image(bitmap = bitmap, contentDescription = "Profile picture", modifier = Modifier.fillMaxSize().clip(CircleShape))
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("JW", color = Green, fontWeight = FontWeight.ExtraBold) }
    }
}
