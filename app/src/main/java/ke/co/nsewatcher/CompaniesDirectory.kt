package ke.co.nsewatcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.WatchlistStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompaniesDirectory(
    catalog: List<Stock>, quotes: List<Stock>, name: String,
    openCompany: (Stock) -> Unit, openWatchlist: () -> Unit, openCompare: (List<String>) -> Unit,
    openNews: (NewsItem) -> Unit, openProfile: () -> Unit,
    onCatalogLoaded: (List<Stock>) -> Unit, onQuotesLoaded: (List<Stock>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WatchlistStore(context) }
    val alertStore = remember { AlertStore(context) }
    var storageError by remember { mutableStateOf(false) }
    val savedFlow = remember(store) { store.symbols.catch { storageError = true } }
    val saved by savedFlow.collectAsState<List<String>, List<String>?>(initial = null)
    val savedSymbols = saved.orEmpty().map(WatchlistPresentation::symbol).toSet()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSector by rememberSaveable { mutableStateOf("All") }
    var sortName by rememberSaveable { mutableStateOf(CompanySort.NAME.name) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var sectorSheet by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(emptySet<String>()) }
    var news by remember { mutableStateOf(emptyList<NewsItem>()) }
    var newsError by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val companies = remember(catalog, quotes) { CompaniesPresentation.companies(catalog, quotes) }
    val visible = remember(companies, query, selectedSector, sortName) { CompaniesPresentation.visible(companies, query, selectedSector, CompanySort.valueOf(sortName)) }
    val sectors = remember(companies) { companies.map { it.sector }.distinct().sorted() }
    val companyNews = remember(news, companies) { CompaniesPresentation.newsByCompany(news, companies) }
    val avatar = context.getSharedPreferences("nse_watcher_preferences", 0).getString("avatar_uri", null)

    suspend fun load(force: Boolean) {
        busy = true
        try {
            if (catalog.isEmpty() || force) {
                val result = MyStocksCache.loadCompanies()
                if (result.isNotEmpty()) { onCatalogLoaded(result); loadError = null }
                else loadError = "Company catalogue could not be updated. Available companies are still shown."
            }
            val state = MarketRefreshController.state.value
            val due = state.lastSuccessfulRefreshMs?.let { System.currentTimeMillis() - it >= MarketRefreshController.REFRESH_INTERVAL_MS } ?: true
            if (!state.refreshInProgress && (quotes.isEmpty() || (force && due))) {
                val result = MyStocksCache.loadStocks()
                if (result.isNotEmpty()) onQuotesLoaded(result)
                else loadError = "Quotes could not be updated. Companies remain available for research."
            }
            val feed = NewsCache.loadFeedResult(forceRefresh = force)
            newsError = feed.error != null
            if (!newsError) news = feed.items
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = "Could not refresh this directory. Please try again." }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { load(false) }
    fun refresh() { if (!busy) scope.launch { load(true) } }
    fun toggleSaved(stock: Stock) {
        if (stock.symbol in pending || saved == null || storageError) return
        pending = pending + stock.symbol
        val wasSaved = stock.symbol in savedSymbols
        scope.launch {
            try {
                val paused = if (wasSaved) alertStore.alerts.first().filter { it.symbol.equals(stock.symbol, true) && it.enabled } else emptyList()
                paused.forEach { alertStore.setEnabled(it.id, false) }
                if (wasSaved) store.remove(stock.symbol) else store.add(stock.symbol)
                snackbar.currentSnackbarData?.dismiss()
                val message = if (wasSaved) "${stock.symbol} removed${if (paused.isNotEmpty()) "; its alerts are paused" else ""}" else "${stock.symbol} added to your watchlist"
                if (snackbar.showSnackbar(message, "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                    if (wasSaved) store.add(stock.symbol) else store.remove(stock.symbol)
                    paused.forEach { alertStore.setEnabled(it.id, true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbar.showSnackbar("Could not update your watchlist. Please try again.") }
            finally { pending = pending - stock.symbol }
        }
    }
    fun select(symbol: String) {
        if (selected.size == 2 && symbol !in selected) {
            scope.launch { snackbar.showSnackbar("Remove a selection before choosing another company.") }
        } else selected = CompaniesPresentation.toggleSelection(selected, symbol)
    }
    BackHandler(comparing) { comparing = false; selected = emptyList() }

    MaterialTheme(colorScheme = CompanyResearchColors) {
        Scaffold(containerColor = ResearchBackground, snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShowChart, null, tint = ResearchGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp)); Text("NSE Watcher", Modifier.weight(1f), color = ResearchText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = openProfile, modifier = Modifier.semantics { contentDescription = "Open profile" }) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(ResearchRaised), contentAlignment = Alignment.Center) {
                            Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = ResearchText)
                            if (avatar != null) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("Companies", color = ResearchText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        ResearchBody(if (comparing) "Compare businesses in the same sector." else "Find your next company to understand.")
                        Spacer(Modifier.height(8.dp))
                        ResearchCaption("Delayed quotes · Observation times shown")
                    }
                    item {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("Search name or ticker", fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                            shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = ResearchCard, focusedContainerColor = ResearchCard, unfocusedBorderColor = ResearchBorder, focusedBorderColor = ResearchGreen))
                    }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Banking", "Telecom").forEach { sector ->
                                DirectoryChip(sector, selectedSector == sector) { selectedSector = sector }
                            }
                            DirectoryChip(if (selectedSector !in listOf("All", "Banking", "Telecom")) "$selectedSector ▾" else "More ▾", selectedSector !in listOf("All", "Banking", "Telecom")) { sectorSheet = true }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, null, tint = ResearchMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (selectedSector == "All") "Company directory" else "$selectedSector companies", Modifier.weight(1f), color = ResearchText, fontSize = 14.sp)
                            Box {
                                TextButton(onClick = { sortMenu = true }) { Text(when (CompanySort.valueOf(sortName)) { CompanySort.NAME -> "A–Z ⇅"; CompanySort.NAME_DESC -> "Z–A ⇅"; CompanySort.GAIN -> "Change ↓"; CompanySort.LOSS -> "Change ↑"; CompanySort.PRICE -> "Price ↑" }, color = ResearchMuted) }
                                DropdownMenu(sortMenu, { sortMenu = false }) {
                                    CompanySort.values().forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { sortName = option.name; sortMenu = false }) }
                                }
                            }
                            IconButton(onClick = ::refresh, enabled = !busy) {
                                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = ResearchGreen, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, "Refresh companies", tint = ResearchMuted, modifier = Modifier.size(20.dp))
                            }
                        }
                        HorizontalDivider(color = ResearchBorder)
                        if (comparing) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CompareArrows, null, tint = ResearchMuted)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { ResearchBody("Select 2 companies"); ResearchCaption("Compare available facts, not recommendations.") }
                                TextButton(onClick = { comparing = false; selected = emptyList() }) { Text("Cancel", color = ResearchGreen) }
                            }
                        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { comparing = true }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Compare")
                            }
                            Spacer(Modifier.weight(1f)); TextButton(onClick = openWatchlist) { Text("My watchlist →", color = ResearchMuted) }
                        }
                    }
                    loadError?.let { item { ResearchCaption(it) } }
                    if (storageError) item { ResearchCaption("Saved companies could not be read. Reopen this screen to retry.") }
                    if (newsError) item { ResearchCaption("Company news could not be refreshed. Available links may be older.") }
                    if (companies.isEmpty() && busy) item { ResearchLoading("Loading company directory…") }
                    else if (visible.isEmpty()) item {
                        ResearchPanel {
                            ResearchTitle(if (companies.isEmpty()) "Directory unavailable" else "No matching companies")
                            ResearchCaption(if (companies.isEmpty()) "Refresh to try loading the company catalogue again." else "Try another name, ticker, or sector.")
                            TextButton(onClick = { if (companies.isEmpty()) refresh() else { query = ""; selectedSector = "All" } }) { Text(if (companies.isEmpty()) "Retry" else "Clear filters", color = ResearchGreen) }
                        }
                    }
                    items(visible, key = { it.symbol }) { stock ->
                        DirectoryCompanyCard(stock, comparing, stock.symbol in selected, stock.symbol in savedSymbols,
                            saved != null && !storageError && stock.symbol !in pending, companyNews[stock.symbol],
                            { if (comparing) select(stock.symbol) else openCompany(stock) }, { toggleSaved(stock) }, openNews)
                    }
                    item {
                        if (comparing) ResearchPanel {
                            ResearchBody("Start with the business")
                            ResearchCaption("Compare sector, financial results and source dates. A lower share price alone does not mean better value.")
                        } else ResearchCaption("Tap a company for its intelligence. ☆ Save to watchlist.")
                    }
                }
                if (comparing) Surface(color = ResearchCard, border = BorderStroke(1.dp, ResearchBorder)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${selected.size} selected", color = ResearchText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            selected.forEach { symbol -> InputChip(selected = true, onClick = { selected = selected - symbol }, label = { Text(symbol) }, trailingIcon = { Icon(Icons.Default.Close, "Remove $symbol from comparison", modifier = Modifier.size(16.dp)) }) }
                        }
                        if (selected.size == 2 && companies.filter { it.symbol in selected }.map { it.sector }.distinct().size > 1) ResearchCaption("Different sectors: financial ratios may not be directly comparable.")
                        Button(onClick = { openCompare(selected) }, enabled = selected.size == 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Compare companies →") }
                    }
                }
            }
            if (sectorSheet) ModalBottomSheet(onDismissRequest = { sectorSheet = false }, containerColor = ResearchBackground) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { ResearchTitle("Browse by sector") }
                    items(listOf("All") + sectors) { sector ->
                        TextButton(onClick = { selectedSector = sector; sectorSheet = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(sector, Modifier.weight(1f), color = if (selectedSector == sector) ResearchGreen else ResearchText)
                            if (selectedSector == sector) Icon(Icons.Default.Check, null, tint = ResearchGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryChip(label: String, selected: Boolean, action: () -> Unit) {
    FilterChip(selected, action, label = { Text(label, fontSize = 13.sp) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.heightIn(min = 44.dp),
        colors = FilterChipDefaults.filterChipColors(containerColor = ResearchCard, labelColor = ResearchMuted, selectedContainerColor = ResearchGreen, selectedLabelColor = ResearchBackground), border = BorderStroke(1.dp, if (selected) ResearchGreen else ResearchBorder))
}

@Composable
private fun DirectoryCompanyCard(stock: Stock, comparing: Boolean, selected: Boolean, saved: Boolean, canSave: Boolean,
    story: NewsItem?, open: () -> Unit, toggleSaved: () -> Unit, openNews: (NewsItem) -> Unit) {
    val interaction = if (comparing) Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { open() })
        else Modifier.clickable(role = Role.Button, onClickLabel = "Research ${stock.name}", onClick = open)
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(interaction), color = if (selected && comparing) ResearchGreen.copy(alpha = 0.10f) else ResearchCard,
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (selected && comparing) ResearchGreen else ResearchBorder)) {
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (comparing) Checkbox(selected, null, modifier = Modifier.size(32.dp))
                else Box(Modifier.size(34.dp).background(ResearchRaised, CircleShape), contentAlignment = Alignment.Center) {
                    Text(stock.symbol.take(1), color = ResearchGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    stock.logoUrl?.takeIf { it.isNotBlank() }?.let { AsyncImage(it, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Fit) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stock.name, color = ResearchText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${stock.symbol} · ${stock.sector}", color = ResearchMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.widthIn(max = 105.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(CompanyResearchPresentation.money(stock.price), color = if (stock.price.isFinite() && stock.price > 0) ResearchText else ResearchMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val change = stock.change.takeIf { stock.changeAvailable && it.isFinite() && stock.price.isFinite() && stock.price > 0 }
                    Text(change?.let { CompanyResearchPresentation.percent(it) } ?: "—", color = researchChangeColor(change), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                if (!comparing) IconButton(onClick = toggleSaved, enabled = canSave) {
                    Icon(if (saved) Icons.Default.Star else Icons.Default.StarBorder, if (saved) "Remove ${stock.symbol} from watchlist" else "Save ${stock.symbol} to watchlist", tint = if (saved) ResearchGreen else ResearchMuted)
                } else Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.padding(start = 42.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (!stock.price.isFinite() || stock.price <= 0) "Quote not returned" else if (stock.observedAt.isBlank()) "Observation time unavailable" else "As of ${CompanyResearchPresentation.date(stock.observedAt)}", color = ResearchMuted, fontSize = 10.sp)
                if (story != null && !comparing) TextButton(onClick = { openNews(story) }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Company news ↗", fontSize = 11.sp)
                }
            }
        }
    }
}
