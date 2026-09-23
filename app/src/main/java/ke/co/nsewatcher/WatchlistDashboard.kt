package ke.co.nsewatcher

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.WatchlistStore
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistDashboard(
    quoteStocks: List<Stock>, initialCatalog: List<Stock>, initialMarket: MyStocksCache.MarketStatus,
    onQuotesLoaded: (List<Stock>) -> Unit, openCompany: (Stock) -> Unit,
    openNews: (NewsItem) -> Unit, openPreferences: () -> Unit, back: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { WatchlistStore(context) }
    val alertStore = remember { AlertStore(context) }
    val alerts by alertStore.alerts.collectAsState(initial = emptyList())
    var storageError by remember { mutableStateOf(false) }
    val saved by produceState<List<String>?>(null, store) {
        try { store.symbols.collect { value = it; storageError = false } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { storageError = true }
    }
    var catalog by remember { mutableStateOf(initialCatalog) }
    var market by remember { mutableStateOf(initialMarket) }
    var query by rememberSaveable { mutableStateOf("") }
    var alertsOnly by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showAlerts by rememberSaveable { mutableStateOf(false) }
    var alertSymbol by rememberSaveable { mutableStateOf<String?>(null) }
    var showNews by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var mutating by remember { mutableStateOf(emptySet<String>()) }
    var histories by remember { mutableStateOf<Map<String, List<MyStocksCache.HistoryPoint>>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = remember { context.getSharedPreferences("nse_watcher_preferences", 0) }
    var notificationsEnabled by remember { mutableStateOf(false) }
    var preferencesRevision by remember { mutableIntStateOf(0) }

    fun checkNotifications() {
        val manager = context.getSystemService(NotificationManager::class.java)
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel("market_alerts")?.importance != NotificationManager.IMPORTANCE_NONE
        preferencesRevision++
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkNotifications() }
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    DisposableEffect(lifecycle) {
        checkNotifications()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checkNotifications() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val priceEnabled = remember(preferencesRevision) { prefs.getBoolean("price_alerts", true) }
    val marketEnabled = remember(preferencesRevision) { prefs.getBoolean("market_alerts", true) }
    val newsEnabled = remember(preferencesRevision) { prefs.getBoolean("news_alerts", true) }

    LaunchedEffect(initialCatalog) { if (initialCatalog.isNotEmpty()) catalog = initialCatalog }
    LaunchedEffect(initialMarket) { market = initialMarket }
    fun refreshData() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                coroutineScope {
                    val quoteRequest = async { MyStocksCache.loadStocks() }
                    val catalogRequest = async { MyStocksCache.loadCompanies() }
                    val marketRequest = async { MyStocksCache.loadMarketStatus() }
                    val quotes = quoteRequest.await()
                    if (quotes.isNotEmpty()) { onQuotesLoaded(quotes); refreshError = null }
                    else refreshError = "Quotes could not be updated. Each company shows its available observation time."
                    catalogRequest.await().takeIf { it.isNotEmpty() }?.let { catalog = it }
                    market = marketRequest.await()
                }
                refreshTick++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { refreshError = "Market data could not be refreshed. Please try again." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refreshData() }
    val companies = remember(saved, catalog, quoteStocks) { WatchlistPresentation.companies(saved.orEmpty(), catalog, quoteStocks) }
    val visible = remember(companies, query, alertsOnly, alerts) { WatchlistPresentation.filter(companies, query, alertsOnly, alerts) }
    val keys = companies.map { it.symbol }
    LaunchedEffect(keys, refreshTick) {
        val limiter = Semaphore(4)
        coroutineScope {
            keys.forEach { symbol -> launch {
                limiter.withPermit {
                    val result = MyStocksCache.loadHistoryDetails(symbol, "1m")
                    histories = histories + (symbol to WatchlistPresentation.trend(result.points))
                }
            } }
        }
    }
    fun statusFor(rule: PriceAlert): String {
        val stock = companies.firstOrNull { it.symbol == WatchlistPresentation.symbol(rule.symbol) }
        val preference = when (rule.type) {
            AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> priceEnabled
            AlertType.NEWS, AlertType.CORPORATE_ACTION -> newsEnabled
            else -> marketEnabled
        }
        return when {
            !rule.enabled -> "Paused"
            rule.type !in WatchlistPresentation.supportedTypes -> "Unsupported"
            WatchlistPresentation.needsThreshold(rule.type) && (rule.threshold == null || !rule.threshold.isFinite() || rule.threshold <= 0.0) -> "Edit threshold"
            !preference -> "Off in settings"
            !notificationsEnabled -> "Notifications off"
            !market.isKnown -> "Awaiting market status"
            !market.isOpen -> "Market closed"
            stock == null || !stock.price.isFinite() || stock.price <= 0.0 -> "Awaiting quote"
            rule.type in setOf(AlertType.DAILY_GAIN, AlertType.DAILY_LOSS) && (!stock.changeAvailable || !stock.change.isFinite()) -> "Awaiting change"
            rule.type == AlertType.HIGH_VOLUME && (!stock.volumeAvailable || !stock.averageVolumeAvailable || stock.averageVolume <= 0L) -> "Awaiting volume"
            else -> "Watching"
        }
    }
    fun manage(symbol: String? = null) { alertSymbol = symbol; showAlerts = true }
    fun remove(stock: Stock) {
        if (stock.symbol in mutating) return
        mutating = mutating + stock.symbol
        scope.launch {
            try {
                val enabledRules = alerts.filter { WatchlistPresentation.symbol(it.symbol) == stock.symbol && it.enabled }
                enabledRules.forEach { alertStore.setEnabled(it.id, false) }
                store.remove(stock.symbol)
                mutating = mutating - stock.symbol
                snackbar.currentSnackbarData?.dismiss()
                val message = if (enabledRules.isEmpty()) "${stock.symbol} removed" else "${stock.symbol} removed; its alerts are paused"
                if (snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    store.add(stock.symbol)
                    enabledRules.forEach { alertStore.setEnabled(it.id, true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar("Could not finish updating this company. Please try again.") }
            finally { mutating = mutating - stock.symbol }
        }
    }
    fun phoneSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            .onFailure { scope.launch { snackbar.showSnackbar("Open Android Settings → Apps → NSE Watcher → Notifications.") } }
    }

    MaterialTheme(colorScheme = CompanyResearchColors) {
        Scaffold(containerColor = ResearchBackground, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back", tint = ResearchText) }
                        Text("My Watchlist", Modifier.weight(1f), color = ResearchText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAdd = true }, enabled = saved != null) {
                            Box(Modifier.size(36.dp).background(ResearchGreen, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, "Add a company", tint = ResearchBackground)
                            }
                        }
                    }
                    ResearchCaption("The companies you choose to follow")
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val state = when { !market.isKnown -> "Market status unavailable"; market.isOpen -> "Market open"; else -> "Market closed" }
                            ResearchCaption("$state · Quotes may be delayed")
                            val latest = companies.mapNotNull { CompanyResearchPresentation.timestamp(it.observedAt) }.maxOrNull()
                            ResearchCaption(latest?.let { "Latest observation: ${CompanyResearchPresentation.date(it.toString())}" } ?: "Observation time unavailable")
                        }
                        IconButton(onClick = ::refreshData, enabled = !busy) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = ResearchGreen, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "Refresh watchlist", tint = ResearchMuted)
                        }
                    }
                }
                if (refreshError != null) item { ResearchCaption(refreshError.orEmpty()) }
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Find a saved company", fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                        shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ResearchCard, unfocusedContainerColor = ResearchCard,
                            focusedBorderColor = ResearchGreen, unfocusedBorderColor = ResearchBorder
                        )
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(false to "All", true to "Price alerts").forEach { (filter, label) ->
                            FilterChip(selected = alertsOnly == filter, onClick = { alertsOnly = filter }, label = { Text(label, fontSize = 13.sp) },
                                modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(24.dp),
                                colors = FilterChipDefaults.filterChipColors(containerColor = ResearchCard, labelColor = ResearchMuted, selectedContainerColor = ResearchGreen, selectedLabelColor = ResearchBackground),
                                border = BorderStroke(1.dp, if (alertsOnly == filter) ResearchGreen else ResearchBorder))
                        }
                    }
                }
                when {
                    storageError -> item { ResearchPanel { ResearchTitle("Watchlist could not be read"); ResearchCaption("Your saved companies have not been replaced. Close and reopen this screen to try again.") } }
                    saved == null -> item { ResearchLoading("Loading your saved companies…") }
                    companies.isEmpty() -> item {
                        ResearchPanel {
                            Icon(Icons.Default.StarBorder, null, tint = ResearchGreen, modifier = Modifier.size(36.dp))
                            ResearchTitle("Build your market radar")
                            ResearchCaption("Add companies you want to research. Your watchlist stays saved on this device.")
                            Button(onClick = { showAdd = true }) { Text("Add your first company") }
                        }
                    }
                    visible.isEmpty() -> item {
                        ResearchPanel {
                            ResearchTitle(if (alertsOnly) "No matching price alerts" else "No matching companies")
                            ResearchCaption(if (alertsOnly) "Tap a company’s bell to create a price rule. Paused price rules also appear in this filter." else "Try a different company name or ticker.")
                            TextButton(onClick = { query = ""; alertsOnly = false }) { Text("Show all saved companies", color = ResearchGreen) }
                        }
                    }
                    else -> items(visible, key = { it.symbol }) { stock ->
                        WatchlistCompanyCard(stock, histories[stock.symbol].orEmpty(), alerts.any { it.enabled && WatchlistPresentation.symbol(it.symbol) == stock.symbol }, stock.symbol in mutating,
                            { openCompany(stock) }, { manage(stock.symbol) }, { remove(stock) })
                    }
                }
                item {
                    ResearchPanel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.NotificationsNone, null, tint = ResearchMuted, modifier = Modifier.size(28.dp))
                            ResearchTitle("Your alerts")
                        }
                        val watchedRules = alerts.filter { WatchlistPresentation.symbol(it.symbol) in keys }.sortedByDescending { it.enabled }
                        if (watchedRules.isEmpty()) ResearchCaption("No alerts for your saved companies yet. Tap a bell to add a rule.")
                        watchedRules.take(2).forEach { rule ->
                            ResearchBody(WatchlistPresentation.ruleLabel(rule))
                            Surface(color = ResearchRaised, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, ResearchBorder)) {
                                Text(statusFor(rule), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = if (statusFor(rule) == "Watching") ResearchGreen else ResearchMuted, fontSize = 12.sp)
                            }
                        }
                        ResearchCaption("Checks are scheduled about every 15 minutes; Android may delay them. News checks continue after hours with a 7-day catch-up window. Price rules require recent, timed quotes while the market is open.")
                        TextButton(onClick = { manage() }) { Text("Manage alerts →", color = ResearchGreen) }
                        if (!notificationsEnabled && alerts.any { it.enabled }) {
                            ResearchCaption("Android notifications are off. Rules are saved, but notifications cannot be shown.")
                            TextButton(onClick = ::phoneSettings) { Text("Open notification settings", color = ResearchGreen) }
                        }
                    }
                }
                item {
                    ResearchPanel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Newspaper, null, tint = ResearchMuted, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f)) { ResearchTitle("News from your watchlist"); ResearchCaption("Company announcements in one place") }
                        }
                        TextButton(onClick = { showNews = true }, enabled = companies.isNotEmpty()) { Text("Explore company news →", color = if (companies.isEmpty()) ResearchMuted else ResearchGreen) }
                    }
                }
                item {
                    OutlinedButton(onClick = { showAdd = true }, enabled = saved != null, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, ResearchGreen)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a company", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (showAdd) WatchlistAddSheet(
            catalog = (catalog + quoteStocks).distinctBy { WatchlistPresentation.symbol(it.symbol) }, saved = saved.orEmpty(), busy = busy,
            retry = ::refreshData, dismiss = { showAdd = false }, add = { store.add(it) }
        )
        if (showAlerts) WatchlistAlertsSheet(
            store = alertStore, alerts = alerts, companies = companies, initialSymbol = alertSymbol,
            statusFor = ::statusFor, dismiss = { showAlerts = false },
            onSaved = {
                AlertWorker.schedule(context)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else checkNotifications()
            },
            preferences = { showAlerts = false; openPreferences() },
            phoneSettings = ::phoneSettings, notificationsEnabled = notificationsEnabled
        )
        if (showNews) WatchlistNewsSheet(companies, dismiss = { showNews = false }, open = { showNews = false; openNews(it) })
    }
}

@Composable
private fun WatchlistCompanyCard(stock: Stock, history: List<MyStocksCache.HistoryPoint>, hasAlert: Boolean, mutating: Boolean, open: () -> Unit, alert: () -> Unit, remove: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = "Research ${stock.name}", onClick = open),
        color = ResearchCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ResearchBorder)) {
        Column(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).background(ResearchRaised, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    Text(stock.symbol.take(3), color = ResearchGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stock.name, color = ResearchText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ResearchCaption(stock.symbol)
                }
                Column(Modifier.widthIn(max = 120.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(CompanyResearchPresentation.money(stock.price), color = ResearchText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val change = stock.change.takeIf { stock.changeAvailable && it.isFinite() }
                    Text(change?.let { CompanyResearchPresentation.percent(it) } ?: "Change unavailable", color = researchChangeColor(change), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchlistSparkline(history, Modifier.width(64.dp).height(24.dp))
                Text("1M\n" + if (stock.observedAt.isBlank()) "Time unavailable" else CompanyResearchPresentation.date(stock.observedAt),
                    color = ResearchMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = alert) { Icon(if (hasAlert) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone, "Manage alerts for ${stock.symbol}", tint = if (hasAlert) ResearchGreen else ResearchMuted) }
                IconButton(onClick = remove, enabled = !mutating) { Icon(Icons.Default.Star, "Remove ${stock.symbol} from watchlist", tint = ResearchGreen) }
            }
        }
    }
}

@Composable
internal fun WatchlistSparkline(points: List<MyStocksCache.HistoryPoint>, modifier: Modifier) {
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No data", color = ResearchMuted, fontSize = 10.sp) }
        return
    }
    val color = researchChangeColor(points.last().close - points.first().close)
    Canvas(modifier.semantics { contentDescription = "One-month available price observations; open company for dated chart." }) {
        val low = points.minOf { it.close }
        val high = points.maxOf { it.close }
        val span = (high - low).coerceAtLeast(0.0001)
        val first = CompanyResearchPresentation.timestamp(points.first().date)!!.toEpochMilli()
        val timeSpan = (CompanyResearchPresentation.timestamp(points.last().date)!!.toEpochMilli() - first).coerceAtLeast(1L)
        val path = Path()
        points.forEachIndexed { index, p ->
            val x = ((CompanyResearchPresentation.timestamp(p.date)!!.toEpochMilli() - first).toDouble() / timeSpan * size.width).toFloat()
            val y = if (high == low) size.height / 2 else (size.height - 4.dp.toPx()) * (1 - (p.close - low) / span).toFloat() + 2.dp.toPx()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchlistAddSheet(catalog: List<Stock>, saved: List<String>, busy: Boolean, retry: () -> Unit, dismiss: () -> Unit, add: suspend (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val matches = catalog.filter { it.name.contains(query.trim(), true) || it.symbol.contains(query.trim(), true) }.sortedBy { it.name }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ResearchTitle("Add a company")
            ResearchCaption("Choose NSE companies to follow. Your existing selections stay saved.")
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search companies") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            if (error != null) ResearchCaption(error.orEmpty())
            if (busy && catalog.isEmpty()) ResearchLoading("Loading company catalogue…")
            else if (matches.isEmpty()) {
                ResearchCaption(if (catalog.isEmpty()) "The company catalogue is unavailable." else "No companies match your search.")
                TextButton(onClick = retry, enabled = !busy) { Text("Refresh catalogue", color = ResearchGreen) }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(matches, key = { it.symbol }) { stock ->
                    val selected = WatchlistPresentation.symbol(stock.symbol) in saved.map(WatchlistPresentation::symbol)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { ResearchBody(stock.name); ResearchCaption(stock.symbol) }
                        IconButton(onClick = {
                            saving = stock.symbol
                            scope.launch {
                                try { add(stock.symbol); error = null }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { error = "Could not save this company. Please try again." }
                                finally { saving = null }
                            }
                        }, enabled = !selected && saving == null) {
                            Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline, if (selected) "${stock.symbol} already saved" else "Add ${stock.symbol}", tint = if (selected) ResearchGreen else ResearchMuted)
                        }
                    }
                    HorizontalDivider(color = ResearchBorder)
                }
            }
            TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("Done", color = ResearchGreen) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchlistNewsSheet(companies: List<Stock>, dismiss: () -> Unit, open: (NewsItem) -> Unit) {
    var feed by remember { mutableStateOf(emptyList<NewsItem>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        try { val result = NewsCache.loadFeedResult(forceRefresh = refresh > 0); error = result.error != null; if (!error) feed = result.items }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    val stories = remember(feed, companies) { WatchlistPresentation.linkedNews(feed, companies) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.85f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("News from your watchlist", Modifier.weight(1f), color = ResearchText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh watchlist news", tint = ResearchGreen) }
                }
            }
            if (loading) item { ResearchLoading("Loading company stories…") }
            if (error) item { ResearchCaption("News could not be updated. Try refreshing again.") }
            if (!loading && !error && stories.isEmpty()) item { ResearchCaption("No stories linked to your saved companies were returned by the current feed.") }
            items(stories, key = { it.id }) { story ->
                Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { open(story) }, color = ResearchCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ResearchBorder)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ResearchCaption(story.companyName.ifBlank { story.symbol })
                        ResearchBody(story.title)
                        ResearchCaption("${story.source.ifBlank { "Source unavailable" }} · ${CompanyResearchPresentation.date(story.publishedAt)}")
                        Text("Read article →", color = ResearchGreen, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}


