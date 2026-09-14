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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
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
import ke.co.nsewatcher.domain.*
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

private val AppColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = Green,
    tertiary = Green,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF4F7F5),
    onBackground = TextDark,
    onSurface = TextDark,
    onSurfaceVariant = Muted,
    error = Red
)

private val DarkAppColors = darkColorScheme(
    primary = Color(0xFF35C979),
    onPrimary = Color(0xFF00391C),
    secondary = Color(0xFF35C979),
    background = Color(0xFF0E1511),
    surface = Color(0xFF16201A),
    surfaceVariant = Color(0xFF223027),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB8C6BD),
    error = Color(0xFFFF6B64)
)

data class AppState(
    val snapshot: MarketSnapshot? = null,
    val news: List<NewsItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

data class Holding(val symbol: String, val shares: Int, val averagePrice: Double)

data class IndexDemo(val name: String, val value: Double, val change: Double, val description: String)

data class FundamentalDemo(
    val marketCap: String,
    val pe: String,
    val pb: String,
    val eps: String,
    val dividendYield: String,
    val roe: String,
    val revenueGrowth: String,
    val profitGrowth: String,
    val debtEquity: String
)

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
        setContent {
            NSEWatcherApp(
                MarketViewModel(application),
                getSharedPreferences("nse_watcher_user", Context.MODE_PRIVATE)
            )
        }
    }
}

class MarketViewModel(
    application: Application,
    private val repository: MarketRepository = DemoMarketRepository(application)
) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        repository.snapshot(true)
            .onSuccess { snapshot ->
                repository.news().onSuccess { news ->
                    _state.value = AppState(snapshot, news, false, null)
                }.onFailure { e ->
                    _state.value = AppState(snapshot, emptyList(), false, e.message)
                }
            }
            .onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Unable to load market data")
            }
    }

    fun add(symbol: String) = viewModelScope.launch {
        repository.addSymbol(symbol)
        refresh()
    }

    fun remove(symbol: String) = viewModelScope.launch {
        repository.removeSymbol(symbol)
        refresh()
    }
}

@Composable
fun NSEWatcherApp(vm: MarketViewModel, prefs: SharedPreferences) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Quote?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf(false) }
    var appearance by rememberSaveable { mutableStateOf(false) }
    var infoDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var darkMode by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var username by rememberSaveable { mutableStateOf(prefs.getString("username", "jimmymwangi") ?: "jimmymwangi") }
    var email by rememberSaveable { mutableStateOf(prefs.getString("email", "jimmy.mwangi@email.com") ?: "jimmy.mwangi@email.com") }
    var password by rememberSaveable { mutableStateOf(prefs.getString("password", "password123") ?: "password123") }
    var description by rememberSaveable { mutableStateOf(prefs.getString("description", "Building wealth, one stock at a time.") ?: "Building wealth, one stock at a time.") }
    var avatarUri by rememberSaveable { mutableStateOf(prefs.getString("avatar_uri", null)) }
    var holdings by remember { mutableStateOf(listOf(Holding("SCOM.KE", 500, 21.50), Holding("KCB.KE", 200, 38.00))) }
    var alerts by remember { mutableStateOf(listOf("SCOM.KE above KSh 30.00", "KCB.KE daily gain above 5%", "EABL.KE volume above average")) }

    fun savePrefs() {
        prefs.edit()
            .putBoolean("dark_mode", darkMode)
            .putString("username", username)
            .putString("email", email)
            .putString("password", password)
            .putString("description", description)
            .putString("avatar_uri", avatarUri)
            .apply()
    }

    val internalBackEnabled = settings || account || appearance || selected != null || tab != 0 || searchOpen
    BackHandler(enabled = internalBackEnabled) {
        when {
            searchOpen -> searchOpen = false
            account -> account = false
            appearance -> appearance = false
            settings -> settings = false
            selected != null -> selected = null
            tab != 0 -> tab = 0
        }
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
            infoDialog?.let { message ->
                AlertDialog(
                    onDismissRequest = { infoDialog = null },
                    confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("OK") } },
                    title = { Text("NSE Watcher") },
                    text = { Text(message) }
                )
            }
        }
    }
}

@Composable
private fun MainShell(
    state: AppState,
    vm: MarketViewModel,
    tab: Int,
    setTab: (Int) -> Unit,
    open: (Quote) -> Unit,
    openSettings: () -> Unit,
    openSearch: () -> Unit,
    holdings: List<Holding>,
    setHoldings: (List<Holding>) -> Unit,
    alerts: List<String>,
    setAlerts: (List<String>) -> Unit,
    viewAllWatchlist: () -> Unit
) {
    Scaffold(
        topBar = { TopBar(openSettings, openSearch) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val nav = listOf(
                    "Home" to Icons.Default.Home,
                    "Watchlist" to Icons.Default.Star,
                    "Portfolio" to Icons.Default.AccountBalanceWallet,
                    "Alerts" to Icons.Default.Notifications,
                    "News" to Icons.Default.Article
                )
                nav.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { setTab(index) },
                        icon = { Icon(item.second, item.first, Modifier.size(24.dp)) },
                        label = { Text(item.first) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green,
                            selectedTextColor = Green,
                            indicatorColor = LightGreen,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
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
                    Icon(Icons.Default.ShowChart, null, tint = Green, modifier = Modifier.padding(7.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("NSE Watcher", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text("Track • Analyze • Grow", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        },
        actions = {
            IconButton(onClick = openSearch) { Icon(Icons.Default.Search, "Search", tint = Green, Modifier.size(27.dp)) }
            IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, "Settings", tint = Green, Modifier.size(28.dp)) }
        }
    )
}

@Composable
private fun Dashboard(state: AppState, open: (Quote) -> Unit, viewAll: () -> Unit) {
    val quotes = state.snapshot?.quotes.orEmpty()
    val gainers = quotes.sortedByDescending { it.dailyChange }
    val losers = quotes.sortedBy { it.dailyChange }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { MarketHero(state.snapshot?.updatedAt?.toString()?.replace('T', ' ')?.take(16) ?: "—") }
        item { MarketSummary(quotes) }
        item { SectionTitle("Market Indices", "NSE benchmark snapshot") }
        item { IndicesRow() }
        item { SectionTitleWithAction("🔥 Market Movers", "Top gainers and losers", "View All") {} }
        item { MarketMovers(gainers.take(4), losers.take(4), open) }
        item { SectionTitleWithAction("⭐ Your Watchlist", "Your tracked stocks", "View All", viewAll) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 1.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quotes.take(6), key = { it.symbol }) { quote -> HorizontalStockCard(quote) { open(quote) } }
            }
        }
        item { SectionTitle("🧠 NSE Watcher Intelligence", "Explainable demo analysis") }
        item { IntelligenceSummary(quotes) }
        item { SectionTitleWithAction("📰 Latest News", "From your tracked stocks", "View All") {} }
        items(state.news.take(3), key = { it.id }) { NewsCard(it) }
        item { DemoBanner() }
    }
}

@Composable
private fun MarketHero(updated: String) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Green))
                    Spacer(Modifier.width(7.dp))
                    Text("MARKET SESSION", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(3.dp))
                Text("DEMO MARKET", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Green)
                Text("Sample prices • not live data", style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("Updated $updated", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("NSE", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                Text("Nairobi Securities\nExchange", style = MaterialTheme.typography.labelSmall, color = Muted, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun MarketSummary(quotes: List<Quote>) {
    val gain = quotes.count { it.dailyChange > 0 }
    val loss = quotes.count { it.dailyChange < 0 }
    val flat = quotes.size - gain - loss
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(13.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SummaryStat("Stocks", quotes.size.toString(), Green, Modifier.weight(1f))
            SummaryDivider()
            SummaryStat("Gainers", gain.toString(), Green, Modifier.weight(1f))
            SummaryDivider()
            SummaryStat("Losers", loss.toString(), Red, Modifier.weight(1f))
            SummaryDivider()
            SummaryStat("Unchanged", flat.toString(), TextDark, Modifier.weight(1f))
        }
    }
}

@Composable private fun SummaryDivider() { Box(Modifier.width(1.dp).height(34.dp).background(BorderGreen)) }

@Composable
private fun SummaryStat(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionTitleWithAction(title: String, subtitle: String, action: String, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
            Text(action, color = Green, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, null, tint = Green, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IndicesRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(demoIndices) { index ->
            Card(Modifier.width(150.dp), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) {
                Column(Modifier.padding(11.dp)) {
                    Text(index.name, fontWeight = FontWeight.ExtraBold)
                    Text(String.format(Locale.US, "%.2f", index.value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.US, "%+.2f%%", index.change), color = if (index.change >= 0) Green else Red, fontWeight = FontWeight.Bold)
                    Text(index.description, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MarketMovers(gainers: List<Quote>, losers: List<Quote>, open: (Quote) -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            MoverColumn("Top Gainers", gainers, true, Modifier.weight(1f), open)
            Box(Modifier.width(1.dp).height(170.dp).background(BorderGreen))
            MoverColumn("Top Losers", losers, false, Modifier.weight(1f), open)
        }
    }
}

@Composable
private fun MoverColumn(title: String, quotes: List<Quote>, positive: Boolean, modifier: Modifier, open: (Quote) -> Unit) {
    Column(modifier.padding(horizontal = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (positive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = if (positive) Green else Red, Modifier.size(22.dp))
            Spacer(Modifier.width(5.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = if (positive) Green else Red)
        }
        Spacer(Modifier.height(8.dp))
        quotes.forEach { quote -> MoverRow(quote, positive) { open(quote) } }
    }
}

@Composable
private fun MoverRow(q: Quote, positive: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StockLogo(q.symbol, 30)
        Spacer(Modifier.width(7.dp))
        Text(q.symbol.removeSuffix(".KE"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(String.format(Locale.US, "%+.1f%%", q.dailyChange), fontWeight = FontWeight.ExtraBold, color = if (positive) Green else Red, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StockLogo(symbol: String, size: Int) {
    val short = symbol.removeSuffix(".KE")
    val background = when (short) {
        "SCOM" -> Color(0xFF0B8F4D)
        "KCB" -> Color(0xFF1B4D9B)
        "EQTY" -> Color(0xFF137A45)
        "ABSA" -> Color(0xFFC6283D)
        "COOP" -> Color(0xFF1769AA)
        "EABL" -> Color(0xFFB8A23A)
        "KPLC" -> Color(0xFF285C8C)
        "BAT" -> Color(0xFF243B75)
        else -> Green
    }
    Surface(Modifier.size(size.dp), RoundedCornerShape((size / 3).dp), color = background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(short.take(3), color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HorizontalStockCard(q: Quote, open: () -> Unit) {
    val tint = if (q.dailyChange >= 0) Green else Red
    Card(Modifier.width(188.dp).clickable(onClick = open), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Column(Modifier.padding(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                StockLogo(q.symbol, 30)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.symbol.removeSuffix(".KE"), fontWeight = FontWeight.ExtraBold)
                    Text(q.companyName, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
                }
                Text(String.format(Locale.US, "%+.1f%%", q.dailyChange), color = tint, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(7.dp))
            Text(String.format(Locale.US, "KSh %.2f", q.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Sparkline(q.history, tint)
            Spacer(Modifier.height(3.dp))
            Text(q.signal.name, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Sparkline(values: List<Double>, tint: Color) {
    Canvas(Modifier.fillMaxWidth().height(35.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 1.0
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1).toFloat()
            val y = size.height - ((value - min) / range).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, tint, strokeWidth = 4f)
    }
}

@Composable
private fun IntelligenceSummary(quotes: List<Quote>) {
    val best = quotes.maxByOrNull { watcherScore(it) }
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoGraph, null, tint = Green, Modifier.size(23.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("NSE Watcher Score", fontWeight = FontWeight.ExtraBold)
                    Text("Rule-based demo score, not investment advice", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                if (best != null) ScoreBadge(watcherScore(best))
            }
            Spacer(Modifier.height(9.dp))
            Text(best?.let { "Highest demo score: ${it.symbol.removeSuffix(".KE")} — ${it.signalExplanation}" } ?: "Add stocks to begin analysis.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun watcherScore(q: Quote): Int {
    val momentum = (q.dailyChange * 3 + q.weeklyChange * 1.5 + q.monthlyChange).coerceIn(-20.0, 40.0)
    val volume = if (q.averageVolume > 0) ((q.volume.toDouble() / q.averageVolume) * 10).coerceIn(0.0, 15.0) else 0.0
    return (45 + momentum + volume).coerceIn(0.0, 100.0).toInt()
}

@Composable
private fun ScoreBadge(score: Int) {
    Surface(shape = RoundedCornerShape(10.dp), color = LightGreen) {
        Text("$score/100", color = Green, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun DetailScreen(q: Quote, close: () -> Unit) {
    val score = watcherScore(q)
    val fund = fundamentals[q.symbol]
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.Default.ArrowBack, "Back") }
                StockLogo(q.symbol, 42)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.companyName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    Text(q.symbol, color = Muted)
                }
                ScoreBadge(score)
            }
        }
        item { PriceCard(q) }
        item { ChartCard(q) }
        item { ScoreCard(q, score) }
        item { FundamentalsCard(fund) }
        item { EventsCard(q) }
        item { DisclaimerCard() }
    }
}

@Composable
private fun PriceCard(q: Quote) {
    val tint = if (q.dailyChange >= 0) Green else Red
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Column(Modifier.padding(16.dp)) {
            Text("DEMO PRICE", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "KSh %.2f", q.price), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(String.format(Locale.US, "%+.2f%% today", q.dailyChange), color = tint, fontWeight = FontWeight.ExtraBold)
            Text("Day range: KSh ${String.format(Locale.US, "%.2f", q.dayLow)} – ${String.format(Locale.US, "%.2f", q.dayHigh)}", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChartCard(q: Quote) {
    var period by rememberSaveable { mutableStateOf("1M") }
    val periods = listOf("1D", "1W", "1M", "3M", "6M", "1Y")
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Price Trend", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text("$period", color = Green, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                periods.forEach { item ->
                    FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Sparkline(q.history, if (q.dailyChange >= 0) Green else Red)
            Text("Demo historical series • live chart will use sourced market data", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun ScoreCard(q: Quote, score: Int) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NSE Watcher Score", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                ScoreBadge(score)
            }
            Spacer(Modifier.height(10.dp))
            ScoreLine("Momentum", ((q.dailyChange + q.weeklyChange + q.monthlyChange) * 3.0 + 50).coerceIn(0.0, 100.0).toInt())
            ScoreLine("Volume", if (q.averageVolume > 0) ((q.volume.toDouble() / q.averageVolume) * 50).coerceIn(0.0, 100.0).toInt() else 0)
            ScoreLine("Trend", if (q.monthlyChange >= 0) 70 else 35)
            Text("Score is a transparent analytical indicator based on demo market factors. It is not a recommendation or prediction.", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun ScoreLine(label: String, value: Int) {
    Column(Modifier.padding(bottom = 7.dp)) {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium); Text("$value/100", fontWeight = FontWeight.Bold) }
        LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Green, trackColor = LightGreen)
    }
}

@Composable
private fun FundamentalsCard(fund: FundamentalDemo?) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Column(Modifier.padding(14.dp)) {
            Text("Fundamentals", fontWeight = FontWeight.ExtraBold)
            Text("Demo reference values — verify against official/company disclosures before use.", color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            if (fund == null) Text("No fundamental data available.", color = Muted)
            else {
                val rows = listOf("Market cap" to fund.marketCap, "P/E" to fund.pe, "P/B" to fund.pb, "EPS" to fund.eps, "Dividend yield" to fund.dividendYield, "ROE" to fund.roe, "Revenue growth" to fund.revenueGrowth, "Profit growth" to fund.profitGrowth, "Debt / equity" to fund.debtEquity)
                rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(label, Modifier.weight(1f), color = Muted)
                        Text(value, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventsCard(q: Quote) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Column(Modifier.padding(14.dp)) {
            Text("Corporate Actions & Events", fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(7.dp))
            Text("No live corporate actions connected in demo mode.", color = Muted)
            Text("Live phase will include dividends, results, AGMs, rights issues, splits and other sourced events.", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, tint = Green, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("NSE Watcher is currently an information and analysis demo. Scores and sample figures do not constitute investment advice, guarantees or predictions.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun Watchlist(quotes: List<Quote>, add: (String) -> Unit, remove: (String) -> Unit, open: (Quote) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SectionTitle("Your Watchlist", "Track the companies you care about") }
        items(quotes, key = { it.symbol }) { q ->
            StockListRow(q, { open(q) }, { remove(q.symbol) })
        }
        item {
            Spacer(Modifier.height(5.dp))
            Text("Demo catalog", fontWeight = FontWeight.Bold)
            val available = listOf("SCOM.KE", "EQTY.KE", "KCB.KE", "ABSA.KE", "COOP.KE", "EABL.KE", "KPLC.KE")
            available.filter { symbol -> quotes.none { it.symbol == symbol } }.forEach { symbol ->
                val name = mapOf("SCOM.KE" to "Safaricom", "EQTY.KE" to "Equity Group", "KCB.KE" to "KCB Group", "ABSA.KE" to "Absa Bank", "COOP.KE" to "Co-operative Bank", "EABL.KE" to "East African Breweries", "KPLC.KE" to "Kenya Power")[symbol] ?: symbol
                OutlinedButton(onClick = { add(symbol) }, modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) { Text("+ Add $name") }
            }
        }
    }
}

@Composable
private fun StockListRow(q: Quote, open: () -> Unit, remove: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = open), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StockLogo(q.symbol, 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(q.companyName, fontWeight = FontWeight.ExtraBold); Text(q.symbol, color = Muted, style = MaterialTheme.typography.labelSmall) }
            Column(horizontalAlignment = Alignment.End) { Text(String.format(Locale.US, "KSh %.2f", q.price), fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "%+.1f%%", q.dailyChange), color = if (q.dailyChange >= 0) Green else Red, fontWeight = FontWeight.Bold) }
            IconButton(onClick = remove) { Icon(Icons.Default.RemoveCircleOutline, "Remove", tint = Muted) }
        }
    }
}

@Composable
private fun PortfolioScreen(holdings: List<Holding>, setHoldings: (List<Holding>) -> Unit) {
    val quotes = remember { emptyMap<String, Quote>() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Portfolio", "Demo holdings and allocation") }
        item {
            val invested = holdings.sumOf { it.shares * it.averagePrice }
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                Column(Modifier.padding(15.dp)) {
                    Text("TOTAL INVESTED", color = Muted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.US, "KSh %,.2f", invested), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Live P/L and current valuation will be connected with live prices.", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
        items(holdings, key = { it.symbol }) { holding ->
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StockLogo(holding.symbol, 38)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) { Text(holding.symbol, fontWeight = FontWeight.ExtraBold); Text("${holding.shares} shares", color = Muted) }
                    Column(horizontalAlignment = Alignment.End) { Text(String.format(Locale.US, "KSh %.2f avg", holding.averagePrice), fontWeight = FontWeight.Bold); Text("Demo", color = Muted, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        item {
            OutlinedButton(onClick = { setHoldings(holdings + Holding("COOP.KE", 100, 15.50)) }, modifier = Modifier.fillMaxWidth()) { Text("Add demo holding") }
        }
    }
}

@Composable
private fun AlertsScreen(alerts: List<String>, setAlerts: (List<String>) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SectionTitle("Alerts", "Rules to monitor prices and market events") }
        item { AlertInfoCard() }
        items(alerts) { alert ->
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Green)
                    Spacer(Modifier.width(9.dp))
                    Text(alert, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { setAlerts(alerts - alert) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Muted) }
                }
            }
        }
        item { OutlinedButton(onClick = { setAlerts(alerts + "SCOM.KE daily loss below -5%") }, modifier = Modifier.fillMaxWidth()) { Text("Add demo alert") } }
    }
}

@Composable
private fun AlertInfoCard() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Column(Modifier.padding(13.dp)) {
            Text("Alert engine", fontWeight = FontWeight.ExtraBold)
            Text("Price, percentage, volume, breakout, news, results and corporate-action alerts are planned for the live backend.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NewsScreen(news: List<NewsItem>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SectionTitle("Latest News", "Market and company updates") }
        items(news, key = { it.id }) { NewsCard(it) }
        item { DemoBanner() }
    }
}

@Composable
private fun NewsCard(item: NewsItem) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NewsThumbnail(item.category)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.category.uppercase(), color = Green, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(item.headline, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text("${item.source} • ${item.publishedAt}", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun NewsThumbnail(category: String) {
    val icon = when (category) { "Company" -> Icons.Default.Business; "Market" -> Icons.Default.TrendingUp; else -> Icons.Default.Article }
    Surface(Modifier.size(72.dp), RoundedCornerShape(13.dp), color = LightGreen) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Green, Modifier.size(34.dp)) } }
}

@Composable
private fun DemoBanner() {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Science, null, tint = Green, Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Text("DEMO MODE • Prices, indices, fundamentals and news are sample data.", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.ErrorOutline, null, tint = Red, Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text("Unable to load data", fontWeight = FontWeight.ExtraBold)
        Text(message, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = retry) { Text("Retry") }
    }
}

@Composable
private fun SearchDialog(quotes: List<Quote>, onSelect: (Quote) -> Unit, close: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = quotes.filter { it.symbol.contains(query, true) || it.companyName.contains(query, true) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Search stocks") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Company or symbol") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Spacer(Modifier.height(8.dp))
                results.forEach { q ->
                    TextButton(onClick = { onSelect(q) }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) { StockLogo(q.symbol, 30); Spacer(Modifier.width(8.dp)); Text("${q.symbol.removeSuffix(".KE")} • ${q.companyName}", modifier = Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Close") } }
    )
}

@Composable
private fun SettingsScreen(username: String, description: String, avatarUri: String?, close: () -> Unit, account: () -> Unit, appearance: () -> Unit, notifications: () -> Unit, security: () -> Unit, help: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SettingsHeader("Settings", close) }
        item { ProfileCard(username, description, avatarUri) }
        item { SettingGroup("Account") }
        item { SettingRow("Account Settings", "Profile, email and password", Icons.Default.Person, account) }
        item { SettingRow("Appearance", "Light and dark mode", Icons.Default.DarkMode, appearance) }
        item { SettingGroup("Preferences") }
        item { SettingRow("Notifications", "Alerts and push delivery", Icons.Default.Notifications, notifications) }
        item { SettingRow("Security", "Authentication controls", Icons.Default.Lock, security) }
        item { SettingRow("Help & Support", "Before production release", Icons.Default.HelpOutline, help) }
    }
}

@Composable
private fun SettingsHeader(title: String, close: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = close) { Icon(Icons.Default.ArrowBack, "Back") }; Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun ProfileCard(username: String, description: String, avatarUri: String?) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatarUri, 64)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("@$username", color = Green, fontWeight = FontWeight.Bold)
                Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Kenya investor | NSE Watcher", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SettingGroup(text: String) { Text(text.uppercase(), color = Green, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 5.dp)) }

@Composable
private fun SettingRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Green, Modifier.size(24.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.ExtraBold); Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall) }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun AccountSettingsScreen(username: String, email: String, password: String, description: String, avatarUri: String?, close: () -> Unit, setUsername: (String) -> Unit, setEmail: (String) -> Unit, setPassword: (String) -> Unit, setDescription: (String) -> Unit, setAvatar: (String?) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) setAvatar(uri.toString()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SettingsHeader("Account Settings", close) }
        item {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                Column(Modifier.padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(avatarUri, 88)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { launcher.launch(arrayOf("image/*")) }) { Text("Change profile picture") }
                }
            }
        }
        item { AccountRow("Username", username) { editing = "username" } }
        item { AccountRow("Email", email) { editing = "email" } }
        item { AccountRow("Password", "••••••••") { editing = "password" } }
        item { AccountRow("Description", description) { editing = "description" } }
    }
    editing?.let { field ->
        val initial = when (field) { "username" -> username; "email" -> email; "password" -> password; else -> description }
        EditDialog(field.replaceFirstChar { it.uppercase() }, initial, field == "password", { editing = null }) { value ->
            when (field) { "username" -> setUsername(value); "email" -> setEmail(value); "password" -> setPassword(value); else -> setDescription(value) }
            editing = null
        }
    }
}

@Composable
private fun AccountRow(title: String, value: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, BorderGreen)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(value, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            Icon(Icons.Default.Edit, "Edit", tint = Green, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EditDialog(title: String, initial: String, secret: Boolean, close: () -> Unit, save: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Edit $title") },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None) },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        confirmButton = { Button(onClick = { save(value) }) { Text("Save") } }
    )
}

@Composable
private fun AppearanceScreen(darkMode: Boolean, close: () -> Unit, setDark: (Boolean) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SettingsHeader("Appearance", close) }
        item { ThemeOption("Light", !darkMode) { setDark(false) } }
        item { ThemeOption("Dark", darkMode) { setDark(true) } }
        item { Text("Your theme choice is saved on this device.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ThemeOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(15.dp), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Green else BorderGreen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (title == "Light") Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = Green)
            Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun Avatar(uri: String?, size: Int) {
    val bitmap: ImageBitmap? = remember(uri) {
        uri?.let { runCatching { BitmapFactory.decodeStream(android.content.ContentResolver::class.java.getDeclaredConstructor().let { null }) }.getOrNull() }
    }
    Surface(Modifier.size(size.dp), CircleShape, color = Green) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.White, Modifier.size((size * 0.55f).dp)) }
    }
}
