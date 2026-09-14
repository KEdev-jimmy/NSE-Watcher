@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ke.co.nsewatcher

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private val Green = Color(0xFF00A859)
private val LightGreen = Color(0xFFE8F8EF)
private val SoftGreen = Color(0xFFD9F3E5)
private val Red = Color(0xFFE53935)
private val SoftRed = Color(0xFFFFE8E7)
private val TextDark = Color(0xFF102018)
private val Muted = Color(0xFF68766F)

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NSEWatcherApp(MarketViewModel(application)) }
    }
}

data class AppState(
    val snapshot: MarketSnapshot? = null,
    val news: List<NewsItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

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
                    _state.value = AppState(snapshot, news, false)
                }
            }
            .onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "Unable to load market data"
                )
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
fun NSEWatcherApp(vm: MarketViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Quote?>(null) }

    MaterialTheme(colorScheme = AppColors) {
        Surface(color = Color.White) {
            if (selected != null) {
                DetailScreen(selected!!) { selected = null }
            } else {
                Scaffold(
                    containerColor = Color.White,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                            title = {
                                Column {
                                    Text("NSE Watcher", fontWeight = FontWeight.ExtraBold)
                                    Text(
                                        "Track • Analyze • Grow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Muted
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = vm::refresh) {
                                    Icon(Icons.Default.Refresh, "Refresh", tint = Green)
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = LightGreen
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(9.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Green)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            "Demo Market",
                                            color = Green,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color.White) {
                            listOf(
                                "Dashboard" to Icons.Default.Home,
                                "Watchlist" to Icons.Default.Star,
                                "Alerts" to Icons.Default.Notifications,
                                "News" to Icons.Default.Article
                            ).forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(item.second, item.first) },
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
                    Box(Modifier.padding(padding)) {
                        when {
                            state.loading -> CircularProgressIndicator(
                                Modifier.align(Alignment.Center),
                                color = Green
                            )
                            state.error != null -> ErrorState(state.error!!, vm::refresh)
                            else -> when (tab) {
                                0 -> Dashboard(state) { quote -> selected = quote }
                                1 -> Watchlist(
                                    state.snapshot?.quotes.orEmpty(),
                                    vm::add,
                                    vm::remove
                                ) { quote -> selected = quote }
                                2 -> AlertsScreen()
                                else -> NewsScreen(state.news)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(state: AppState, open: (Quote) -> Unit) {
    val snapshot = state.snapshot ?: return
    val quotes = snapshot.quotes
    val best = quotes.maxByOrNull { it.dailyChange }
    val worst = quotes.minByOrNull { it.dailyChange }
    val volume = quotes.sumOf { it.volume }
    val stamp = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(snapshot.updatedAt)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = LightGreen)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Green)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("Market Status", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            snapshot.status.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Green
                        )
                        Text(
                            "Last updated: $stamp • Demo refresh",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "NSE",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Nairobi Securities Exchange",
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Market Overview",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                MiniCard(
                    "Top Gainers",
                    quotes.count { it.dailyChange > 0 }.toString(),
                    "stocks",
                    Icons.Default.TrendingUp,
                    Green,
                    Modifier.weight(1f)
                )
                MiniCard(
                    "Top Losers",
                    quotes.count { it.dailyChange < 0 }.toString(),
                    "stocks",
                    Icons.Default.TrendingDown,
                    Red,
                    Modifier.weight(1f)
                )
                MiniCard(
                    "Total Volume",
                    fmt(volume),
                    "shares",
                    Icons.Default.BarChart,
                    Green,
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tracked Equities",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "${quotes.size} tracked",
                    color = Green,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        items(quotes, key = { it.symbol }) { quote ->
            StockCard(quote) { open(quote) }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Demo insight", fontWeight = FontWeight.Bold)
                    Text(
                        "${best?.symbol ?: "—"} is strongest and ${worst?.symbol ?: "—"} is weakest in this sample dataset.",
                        color = Muted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sample values are illustrative and are not live NSE quotes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
            }
        }

        item {
            Text(
                "Recent News",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
        items(state.news.take(2)) { NewsCard(it) }
    }
}

@Composable
private fun MiniCard(
    label: String,
    value: String,
    suffix: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFE0EAE4)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(11.dp)) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (tint == Red) SoftRed else SoftGreen
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(6.dp).size(18.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                suffix,
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun StockCard(q: Quote, open: () -> Unit) {
    val good = q.dailyChange >= 0
    val movementColor = if (good) Green else Red
    val movementBackground = if (good) SoftGreen else SoftRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = open),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFFE0EAE4)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        q.companyName,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        q.symbol,
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "KES %.2f".format(q.price),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = movementBackground
                    ) {
                        Text(
                            "%+.1f%% today".format(q.dailyChange),
                            color = movementColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(11.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("1W", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(
                        "%+.1f%%".format(q.weeklyChange),
                        fontWeight = FontWeight.Bold,
                        color = if (q.weeklyChange >= 0) Green else Red
                    )
                }
                Column {
                    Text("1M", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(
                        "%+.1f%%".format(q.monthlyChange),
                        fontWeight = FontWeight.Bold,
                        color = if (q.monthlyChange >= 0) Green else Red
                    )
                }
                Column {
                    Text("Volume", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(fmt(q.volume), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Sparkline(
                    q.history,
                    Modifier.width(95.dp).height(48.dp),
                    movementColor
                )
            }

            Spacer(Modifier.height(10.dp))

            Surface(shape = RoundedCornerShape(10.dp), color = Green) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (q.signal == Signal.STRONG) Icons.Default.Star else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        q.signal.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    tint: Color = Green
) {
    Canvas(modifier) {
        if (values.size > 1) {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: 1.0
            val range = (max - min).takeIf { it > 0 } ?: 1.0
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1)
                val y = size.height - ((value - min) / range).toFloat() * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                tint,
                style = androidx.compose.ui.graphics.drawscope.Stroke(4f)
            )
        }
    }
}

private fun fmt(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.0fk".format(value / 1_000.0)
    else -> value.toString()
}

@Composable
private fun Watchlist(
    quotes: List<Quote>,
    add: (String) -> Unit,
    remove: (String) -> Unit,
    open: (Quote) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val available = listOf("SCOM.KE", "EQTY.KE", "KCB.KE", "ABSA.KE") -
        quotes.map { it.symbol }.toSet()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "My Watchlist",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("Track your selected NSE equities", color = Muted)
                }
                Box {
                    FilledTonalButton(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = LightGreen,
                            contentColor = Green
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Add")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        available.forEach { symbol ->
                            DropdownMenuItem(
                                text = { Text("Add $symbol") },
                                onClick = {
                                    expanded = false
                                    add(symbol)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (quotes.isEmpty()) {
            item {
                Text(
                    "No stocks tracked. Add an NSE symbol to start monitoring.",
                    color = Muted
                )
            }
        }

        items(quotes, key = { it.symbol }) { quote ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    StockCard(quote) { open(quote) }
                }
                IconButton(onClick = { remove(quote.symbol) }) {
                    Icon(
                        Icons.Default.Delete,
                        "Remove ${quote.symbol}",
                        tint = Red
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(q: Quote, back: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(
                onClick = back,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Green)
                Spacer(Modifier.width(4.dp))
                Text("Back", color = Green)
            }
        }
        item {
            Text(
                q.companyName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text("${q.symbol} • DEMO / SAMPLE DATA", color = Muted)
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = LightGreen)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "KES %.2f".format(q.price),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "%+.1f%% today".format(q.dailyChange),
                        color = if (q.dailyChange >= 0) Green else Red,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Day high KES %.2f • Day low KES %.2f".format(q.dayHigh, q.dayLow)
                    )
                    Text(
                        "Volume %,d • Average %,d".format(q.volume, q.averageVolume),
                        color = Muted
                    )
                }
            }
        }
        item {
            Text(
                "Historical Price Trend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Sparkline(
                    q.history,
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(16.dp)
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("${q.signal} signal", fontWeight = FontWeight.Bold)
                    Text(q.signalExplanation, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1 week %+.1f%% • 1 month %+.1f%%".format(
                            q.weeklyChange,
                            q.monthlyChange
                        )
                    )
                }
            }
        }
        item {
            Text(
                "Relevant News & Corporate Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Demo mode has no confirmed company announcements. A secure backend can supply provider news, dividends, and corporate actions here.",
                color = Muted
            )
        }
    }
}

@Composable
private fun AlertsScreen() {
    val alerts = remember {
        listOf(
            PriceAlert("scom-move", "SCOM.KE", AlertType.DAILY_GAIN, 5.0, true),
            PriceAlert("kcb-volume", "KCB.KE", AlertType.HIGH_VOLUME, null, false)
        )
    }
    var enabled by remember { mutableStateOf(alerts.associate { it.id to it.enabled }) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Alerts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Demo alert rules — notifications will connect to the backend later.",
                color = Muted
            )
        }
        items(alerts) { alert ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(alert.symbol, fontWeight = FontWeight.Bold)
                        Text(
                            "${alert.type.name.replace('_', ' ')}${alert.threshold?.let { ": $it%" } ?: ""}",
                            color = Muted
                        )
                    }
                    Switch(
                        checked = enabled[alert.id] == true,
                        onCheckedChange = { checked ->
                            enabled = enabled + (alert.id to checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Green
                        )
                    )
                }
            }
        }
        item {
            Text(
                "Supported alert ideas: price thresholds, daily movement, unusual volume, breakouts, news, dividends and corporate actions.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun NewsScreen(news: List<NewsItem>) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Market News",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Demo feed — connect the backend for real, attributable news.",
                color = Muted
            )
        }
        items(news) { NewsCard(it) }
    }
}

@Composable
private fun NewsCard(n: NewsItem) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                n.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Green,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(n.headline, fontWeight = FontWeight.Bold)
            Text(
                "${n.source} • ${n.publishedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, retry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = Red)
        Text(
            "Market data unavailable",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(message, color = Muted)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = retry,
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) {
            Text("Try again")
        }
    }
}
