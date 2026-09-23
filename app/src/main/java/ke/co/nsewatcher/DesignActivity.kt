@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ke.co.nsewatcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ke.co.nsewatcher.data.WatchlistStore

private val Green = Color(0xFF00A859)
private val LightGreen = Color(0xFFE9F8F0)
private val DarkGreen = Color(0xFF083C27)
private val TextDark = Color(0xFF12231B)
private val Muted = Color(0xFF6C7A72)
private val Border = Color(0xFFE1EAE5)
private val Red = Color(0xFFE04444)
private const val PREFS = "nse_watcher_preferences"


private fun formatPrice(value: Double): String = "KSh " + String.format(Locale.US, "%,.2f", value)
private fun formatShares(value: Long): String = String.format(Locale.US, "%,d", value)
private fun marketObservationShort(stock: Stock): String {
    if (stock.observedAt.isBlank()) return "Latest NSE observation unavailable"
    val delay = stock.delayMinutes ?: 15
    val observed = runCatching {
        java.time.Instant.parse(stock.observedAt)
            .atZone(java.time.ZoneId.of("Africa/Nairobi"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.US))
    }.getOrDefault(stock.observedAt.replace("T", " ").removeSuffix("Z").take(16))
    return "As of $observed EAT • $delay-min delayed"
}

data class Stock(val symbol:String,val name:String,val price:Double,val change:Double,val history:List<Double>,val logoUrl:String?=null,val sector:String="Other",val volume:Long=0L,val changeAvailable:Boolean=true,val volumeAvailable:Boolean=true,val source:String="",val observedAt:String="",val freshnessMode:String="UNKNOWN",val dataOrigin:String="unknown",val averageVolume:Long=0L,val averageVolumeAvailable:Boolean=false,val previousClose:Double?=null,val delayMinutes:Int?=null)

data class NewsItem(
    val id:String, val title:String, val summary:String, val body:String, val source:String,
    val publishedAt:String, val category:String, val symbol:String, val companyName:String,
    val imageUrl:String, val url:String, val dividendAmount:String, val exDate:String, val paymentDate:String,
    val intelligenceRelevance:String = "unknown", val intelligenceRelevanceReason:String = "", val freshnessMode:String = "UNKNOWN"
)
private val liveStocks = mutableStateOf(emptyList<Stock>())
private val stocks: List<Stock> get() = liveStocks.value

private enum class Page { HOME, MARKET, NEWS, COMPANIES, PAPER, MORE, COMPANY, WATCHLIST, COMPARE, NEWS_DETAIL, PROFILE, SETTINGS, THEME, NOTIFICATIONS, LIVE_DATA, CHARTS, ALERTS, LANGUAGE, SECURITY, PRIVACY, DISPLAY, HELP, ABOUT }

class DesignActivity : ComponentActivity() {
    private var alertDestination by mutableStateOf<AlertDestination?>(null)
    private var practiceDestination by mutableStateOf(false)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("avatar_uri", uri.toString()).apply()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AlertWorker.schedule(this)
        alertDestination = AlertNotifications.destination(intent)
        practiceDestination = PracticeNotifications.opensPractice(intent)
        setContent {
            App(
                alertDestination = alertDestination,
                consumeAlert = { alertDestination = null; intent.action = null },
                practiceDestination = practiceDestination,
                consumePractice = { practiceDestination = false; intent.action = null },
                pickAvatar = { picker.launch(arrayOf("image/*")) }
            )
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alertDestination = AlertNotifications.destination(intent)
        practiceDestination = PracticeNotifications.opensPractice(intent)
    }
}

@Composable
private fun App(
    alertDestination: AlertDestination?,
    consumeAlert: () -> Unit,
    practiceDestination: Boolean,
    consumePractice: () -> Unit,
    pickAvatar: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var showOpeningScreen by rememberSaveable { mutableStateOf(true) }
    var startupReady by remember { mutableStateOf(false) }
    var startupComplete by remember { mutableStateOf(false) }
    var newsFeed by remember { mutableStateOf(emptyList<NewsItem>()) }
    var startupMarketStatus by remember { mutableStateOf(MyStocksCache.MarketStatus()) }
    var marketIndices by remember { mutableStateOf(emptyList<MyStocksCache.MarketIndex>()) }
    var companyCatalog by remember { mutableStateOf(emptyList<Stock>()) }

    LaunchedEffect(Unit) {
        val completed = withTimeoutOrNull(12_000L) {
            coroutineScope {
                val stocksDeferred = async {
                    runCatching { MyStocksCache.loadStocks() }.getOrDefault(emptyList())
                }
                val newsDeferred = async {
                    runCatching { NewsCache.loadFeed() }.getOrDefault(emptyList())
                }
                val companiesDeferred = async {
                    runCatching { MyStocksCache.loadCompanies() }.getOrDefault(emptyList())
                }
                val statusDeferred = async {
                    runCatching { MyStocksCache.loadMarketStatus() }.getOrDefault(MyStocksCache.MarketStatus())
                }
                val indicesDeferred = async {
                    val status = statusDeferred.await()
                    runCatching {
                        MyStocksCache.loadMarketIndices(status.isKnown && status.isOpen)
                    }.getOrDefault(emptyList())
                }

                val loadedStocks = stocksDeferred.await()
                if (loadedStocks.isNotEmpty()) {
                    liveStocks.value = loadedStocks
                }
                newsFeed = newsDeferred.await()
                companyCatalog = companiesDeferred.await()
                startupMarketStatus = statusDeferred.await()
                marketIndices = indicesDeferred.await()
            }
            true
        } ?: false

        // The welcome screen is intentionally the gate. If a provider is slow or unavailable,
        // the gate has a bounded fallback; Home can then retry any incomplete data request.
        startupComplete = completed
        startupReady = true
    }

    var page by remember { mutableStateOf(Page.HOME) }
    var history by remember { mutableStateOf(emptyList<Page>()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(Stock("", "", 0.0, 0.0, emptyList())) }
    var selectedNews by remember { mutableStateOf<NewsItem?>(null) }
    var practiceSymbol by rememberSaveable { mutableStateOf("") }
    var alertNavigationRevision by remember { mutableIntStateOf(0) }
    val directoryState = rememberSaveableStateHolder()
    var directorySector by rememberSaveable { mutableStateOf("All") }
    var comparisonSymbols by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var dark by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("profile_name", "James Waweru") ?: "James Waweru") }
    var username by rememberSaveable { mutableStateOf(prefs.getString("username", "jameswaweru") ?: "jameswaweru") }
    var email by rememberSaveable { mutableStateOf(prefs.getString("email", "jameswaweru@gmail.com") ?: "jameswaweru@gmail.com") }
    var description by rememberSaveable { mutableStateOf(prefs.getString("description", "IT Graduate | Investor | Learner") ?: "IT Graduate | Investor | Learner") }
    var marketAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("market_alerts", true)) }
    var priceAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("price_alerts", true)) }
    var newsAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("news_alerts", true)) }
    var appAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("app_alerts", true)) }
    var autoRefresh by rememberSaveable { mutableStateOf(prefs.getBoolean("auto_refresh", true)) }
    val latestSelected by rememberUpdatedState(selected)

    if (showOpeningScreen) {
        NSEWatcherOpeningScreen(
            ready = startupReady,
            onFinished = { showOpeningScreen = false }
        )
        return
    }
    LaunchedEffect(alertDestination) {
        val target = alertDestination ?: return@LaunchedEffect
        history = listOf(Page.HOME)
        val article = target.article()
        if (article != null) {
            alertNavigationRevision++
            selectedNews = article
            page = Page.NEWS_DETAIL
        } else {
            selected = target.company(CompaniesPresentation.companies(companyCatalog, stocks))
            page = Page.COMPANY
        }
        consumeAlert()
    }
    LaunchedEffect(practiceDestination) {
        if (!practiceDestination) return@LaunchedEffect
        history = listOf(Page.HOME)
        practiceSymbol = ""
        page = Page.PAPER
        consumePractice()
    }
    LaunchedEffect(page) {
        if (page == Page.COMPANIES && companyCatalog.isEmpty()) {
            MyStocksCache.loadCompanies().takeIf { it.isNotEmpty() }?.let { companyCatalog = it }
        }
    }
    LaunchedEffect(autoRefresh) {
        if (!autoRefresh) return@LaunchedEffect
        var previousMarketKnown = startupMarketStatus.isKnown
        var previousMarketOpen = startupMarketStatus.isKnown && startupMarketStatus.isOpen

        while (isActive) {
            delay(MarketRefreshController.STATUS_POLL_INTERVAL_MS)

            // Status is real-time and must not wait for the 15-minute quote cadence.
            // This lets an app that is already open recognize the Nairobi session
            // transition around 09:30 EAT even when the user is physically abroad.
            val refreshedStatus = MyStocksCache.loadMarketStatus()
            val marketStateChanged = refreshedStatus.isKnown &&
                (!previousMarketKnown || refreshedStatus.isOpen != previousMarketOpen)
            val becameOpen = refreshedStatus.isKnown && refreshedStatus.isOpen && !previousMarketOpen
            startupMarketStatus = refreshedStatus

            val quoteRefreshDue = MarketRefreshController.shouldRefreshQuotes(stocks.isNotEmpty())
            val openingRefresh = becameOpen && !MarketRefreshController.state.value.refreshInProgress

            if (marketStateChanged || quoteRefreshDue) {
                MyStocksCache.loadMarketIndices(refreshedStatus.isKnown && refreshedStatus.isOpen)
                    .takeIf { it.isNotEmpty() }
                    ?.let { marketIndices = it }
            }

            if (openingRefresh || quoteRefreshDue) {
                MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { refreshed ->
                    liveStocks.value = refreshed
                    // Use the latest selected company without restarting the status timer
                    // when navigation changes the selection.
                    if (latestSelected.symbol.isNotBlank()) {
                        refreshed.firstOrNull { it.symbol == latestSelected.symbol }?.let { selected = it }
                    }
                }
            }

            previousMarketKnown = refreshedStatus.isKnown
            previousMarketOpen = refreshedStatus.isKnown && refreshedStatus.isOpen
        }
    }
    var showVolume by rememberSaveable { mutableStateOf(prefs.getBoolean("show_volume", true)) }
    var showChanges by rememberSaveable { mutableStateOf(prefs.getBoolean("show_changes", true)) }
    fun put(k:String,v:Boolean){prefs.edit().putBoolean(k,v).apply()}
    fun put(k:String,v:String){prefs.edit().putString(k,v).apply()}
    fun go(to:Page){if(to!=page){history=history+page;page=to}}
    fun back(){if(history.isNotEmpty()){page=history.last();history=history.dropLast(1)}else page=Page.HOME}
    BackHandler(enabled=page!=Page.HOME){back()}
    val scheme=if(page==Page.PAPER || page==Page.MARKET || page==Page.NEWS_DETAIL || page==Page.HOME || page==Page.COMPANIES || page==Page.COMPARE || page==Page.COMPANY || page==Page.WATCHLIST) CompanyResearchColors else if(page==Page.NEWS) NewsColorScheme else if(dark) darkColorScheme(primary=Color(0xFF32D486),background=Color(0xFF0D1712),surface=Color(0xFF132019),onSurface=Color.White,onBackground=Color.White,onSurfaceVariant=Color(0xFFB7C7BE)) else lightColorScheme(primary=Green,background=Color.White,surface=Color.White,onSurface=TextDark,onBackground=TextDark,onSurfaceVariant=Muted)
    MaterialTheme(colorScheme=scheme){Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=scheme.background){
        when(page){
            Page.HOME,Page.MARKET,Page.NEWS,Page.COMPANIES,Page.PAPER,Page.MORE -> Scaffold(topBar={if(page!=Page.PAPER && page!=Page.HOME && page!=Page.MARKET && page!=Page.NEWS && page!=Page.COMPANIES) TopBar(name,::go)},bottomBar={BottomNav(when(page){Page.HOME->0;Page.MARKET->1;Page.NEWS->2;Page.COMPANIES->3;else->4}, newsStyle=page==Page.NEWS, homeStyle=page==Page.PAPER || page==Page.MARKET || page==Page.HOME || page==Page.COMPANIES){tab=it;if(it==3)directorySector="All";history=emptyList();page=when(it){0->Page.HOME;1->Page.MARKET;2->Page.NEWS;3->Page.COMPANIES;else->Page.MORE}}}){pad->Box(Modifier.fillMaxSize().padding(pad)){when(page){Page.HOME->HomeDashboard(stocks,{selected=it;go(Page.COMPANY)},{selectedNews=it;go(Page.NEWS_DETAIL)},{go(Page.MARKET)},{go(Page.WATCHLIST)},newsFeed,marketIndices,startupMarketStatus,startupComplete,name=name,initialCatalog=companyCatalog,practiceEnabled=PaperPortfolioStore.isEnabled(context),practiceCash=PaperPortfolioStore.cash(context),openAllNews={go(Page.NEWS)},openPractice={practiceSymbol="";go(Page.PAPER)},openProfile={go(Page.PROFILE)},openAlertSettings={go(Page.NOTIFICATIONS)},onQuotesLoaded={liveStocks.value=it},onNewsLoaded={newsFeed=it},onIndicesLoaded={marketIndices=it},onMarketStatusLoaded={startupMarketStatus=it});Page.MARKET->directoryState.SaveableStateProvider("market"){MarketDashboard(stocks,companyCatalog,startupMarketStatus,marketIndices,openCompany={selected=it;go(Page.COMPANY)},openCompanies={directorySector=it;go(Page.COMPANIES)},onQuotesLoaded={liveStocks.value=it},onCatalogLoaded={companyCatalog=it},onIndicesLoaded={marketIndices=it},onMarketStatusLoaded={startupMarketStatus=it})};Page.NEWS->directoryState.SaveableStateProvider("news"){NewsDashboard(newsFeed,onNewsLoaded={newsFeed=it}){selectedNews=it;go(Page.NEWS_DETAIL)}};Page.COMPANIES->directoryState.SaveableStateProvider("companies:$directorySector"){CompaniesDirectory(catalog=companyCatalog,quotes=stocks,name=name,initialSector=directorySector,openCompany={selected=it;go(Page.COMPANY)},openWatchlist={go(Page.WATCHLIST)},openCompare={comparisonSymbols=it;go(Page.COMPARE)},openNews={selectedNews=it;go(Page.NEWS_DETAIL)},openProfile={go(Page.PROFILE)},onCatalogLoaded={companyCatalog=it},onQuotesLoaded={liveStocks.value=it})};Page.PAPER->directoryState.SaveableStateProvider("practice"){PracticePortfolioScreen(quoteFeed=stocks,catalog=companyCatalog,initialMarket=startupMarketStatus,news=newsFeed,initialSymbol=practiceSymbol,onQuotes={liveStocks.value=it},onCatalog={companyCatalog=it},openCompany={selected=it;go(Page.COMPANY)},openNews={selectedNews=it;go(Page.NEWS_DETAIL)},back=::back)};else->More(::go)}}}
            Page.COMPANY->Company(
                s=selected,
                sharedNews=newsFeed,
                marketStatus=startupMarketStatus,
                onNewsLoaded={newsFeed=it},
                onMarketStatusLoaded={startupMarketStatus=it},
                back=::back,
                openPractice={practiceSymbol=selected.symbol;go(Page.PAPER)}
            ){selectedNews=it;go(Page.NEWS_DETAIL)}
            Page.WATCHLIST->WatchlistDashboard(quoteStocks=stocks, initialCatalog=companyCatalog, initialMarket=startupMarketStatus, onQuotesLoaded={liveStocks.value=it}, openCompany={selected=it;go(Page.COMPANY)}, openNews={selectedNews=it;go(Page.NEWS_DETAIL)}, openPreferences={go(Page.NOTIFICATIONS)}, back=::back)
            Page.COMPARE->CompanyComparison(CompaniesPresentation.companies(companyCatalog, stocks),::back,comparisonSymbols)
            Page.NEWS_DETAIL->key(alertNavigationRevision) { selectedNews?.let { NewsArticleScreen(it,companyCatalog,stocks,::back){company->selected=company;go(Page.COMPANY)} } }
            Page.PROFILE->Profile(name,username,email,description,{name=it;put("profile_name",it)},{username=it;put("username",it)},{email=it;put("email",it)},{description=it;put("description",it)},pickAvatar,::back,::go)
            Page.SETTINGS->Settings(dark,marketAlerts,priceAlerts,newsAlerts,appAlerts,autoRefresh,showVolume,showChanges,{dark=it;put("dark_mode",it)},{marketAlerts=it;put("market_alerts",it)},{priceAlerts=it;put("price_alerts",it)},{newsAlerts=it;put("news_alerts",it)},{appAlerts=it;put("app_alerts",it)},{autoRefresh=it;put("auto_refresh",it)},{showVolume=it;put("show_volume",it)},{showChanges=it;put("show_changes",it)},::back,::go)
            Page.THEME->ThemePage(dark,{dark=it;put("dark_mode",it)},::back)
            Page.NOTIFICATIONS->NotificationsPage(marketAlerts,priceAlerts,newsAlerts,appAlerts,{marketAlerts=it;put("market_alerts",it)},{priceAlerts=it;put("price_alerts",it)},{newsAlerts=it;put("news_alerts",it)},{appAlerts=it;put("app_alerts",it)},::back)
            Page.LIVE_DATA->LiveData(autoRefresh,showVolume,showChanges,{autoRefresh=it;put("auto_refresh",it)},{showVolume=it;put("show_volume",it)},{showChanges=it;put("show_changes",it)},::back)
            Page.CHARTS->SimplePage("Chart Settings",Icons.Default.ShowChart,listOf("Default timeframe" to "1D","Chart style" to "Line","Show grid" to "On","Indicators" to "On"),::back)
            Page.ALERTS->AlertPage(::back)
            Page.LANGUAGE->SimplePage("Language",Icons.Default.Language,listOf("App language" to "English","Currency" to "KSh (Kenyan Shillings)","Region" to "Kenya"),::back)
            Page.SECURITY->SimplePage("Account Security",Icons.Default.Lock,listOf("Password" to "Protected","Biometric unlock" to "Off","Active sessions" to "This device","Data permissions" to "Review"),::back)
            Page.PRIVACY->SimplePage("Privacy",Icons.Default.PrivacyTip,listOf("Personalisation" to "On device","Analytics" to "Optional","Data sharing" to "Not shared for trading"),::back)
            Page.DISPLAY->SimplePage("Font & Display",Icons.Default.Visibility,listOf("Font size" to "Medium","Compact cards" to "On","Animations" to "Standard"),::back)
            Page.HELP->HelpPage(::back)
            Page.ABOUT->AboutPage(::back)
            else->{page=Page.HOME}
        }
    }}
}

@Composable private fun Section(t:String,s:String){Column{Text(t,fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Text(s,color=Muted,fontSize=10.sp)}}
@Composable private fun Logo(symbol:String,size:Int,logoUrl:String?=null){
    val resolvedUrl = logoUrl?.takeIf{it.isNotBlank()} ?: "https://mystocks.africa/logos/${symbol.lowercase(Locale.US)}-ke.svg"
    Surface(Modifier.size(size.dp),RoundedCornerShape(8.dp),when(symbol){"SCOM"->Color(0xFF0B8F4D);"KCB"->Color(0xFF1B4D9B);"EQTY"->Color(0xFF137A45);"ABSA"->Color(0xFFC6283D);"COOP"->Color(0xFF1769AA);"EABL"->Color(0xFFB8A23A);else->Color(0xFF285C8C)}){
        Box(Modifier.fillMaxSize(),Alignment.Center){
            AsyncImage(model=resolvedUrl,contentDescription=symbol,modifier=Modifier.fillMaxSize().padding(5.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
            Text(symbol.take(3),color=Color.White.copy(alpha=.85f),fontWeight=FontWeight.ExtraBold,fontSize=8.sp)
        }
    }
}
@Composable private fun TopBar(name:String,go:(Page)->Unit){Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(42.dp),RoundedCornerShape(12.dp),LightGreen){Icon(Icons.Default.ShowChart,null,Modifier.padding(7.dp),Green)};Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text("NSE Watcher",fontSize=18.sp,fontWeight=FontWeight.ExtraBold);Text("Analyse • Understand • Invest Smarter",fontSize=10.sp,color=Muted)};Avatar(name){go(Page.PROFILE)}}}
@Composable private fun Avatar(name:String,onClick:()->Unit){val c=androidx.compose.ui.platform.LocalContext.current;val u=c.getSharedPreferences(PREFS,0).getString("avatar_uri",null);val b by produceState<Bitmap?>(null,u){value=try{u?.let{c.contentResolver.openInputStream(Uri.parse(it))?.use{stream->BitmapFactory.decodeStream(stream)}}}catch(_:Exception){null}};Surface(Modifier.size(38.dp).clip(CircleShape).clickable(onClick=onClick),CircleShape,Color(0xFFDDEFE6)){if(b!=null)Image(b!!.asImageBitmap(),"Profile",Modifier.fillMaxSize())else Box(Modifier.fillMaxSize(),Alignment.Center){Text(name.take(1).uppercase(),color=Green,fontWeight=FontWeight.Bold)}}}
@Composable
private fun BottomNav(selected: Int, newsStyle: Boolean = false, homeStyle: Boolean = false, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Home" to Icons.Default.Home, "Market" to Icons.Default.CandlestickChart,
        "News" to Icons.Default.Article, "Companies" to Icons.Default.Business,
        "More" to if (homeStyle) Icons.Default.MoreHoriz else Icons.Default.AutoGraph
    )
    val accent = if (homeStyle) ResearchGreen else if (newsStyle) NewsColorScheme.primary else Green
    NavigationBar(
        containerColor = if (homeStyle) ResearchBackground else if (newsStyle) NewsColorScheme.background else NavigationBarDefaults.containerColor
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(item.second, item.first) },
                label = { Text(item.first, fontSize = if (newsStyle || homeStyle) 11.sp else 9.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent, selectedTextColor = accent,
                    indicatorColor = if (homeStyle) ResearchBackground else if (newsStyle) NewsColorScheme.surfaceVariant else LightGreen,
                    unselectedIconColor = if (homeStyle) ResearchMuted else if (newsStyle) NewsColorScheme.onSurfaceVariant else Muted,
                    unselectedTextColor = if (homeStyle) ResearchMuted else if (newsStyle) NewsColorScheme.onSurfaceVariant else Muted
                )
            )
        }
    }
}
@Composable private fun Header(title:String,sub:String?=null,back:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)IconButton(back){Icon(Icons.Default.ArrowBack,"Back")};Column{Text(title,fontSize=20.sp,fontWeight=FontWeight.ExtraBold);if(sub!=null)Text(sub,fontSize=10.sp,color=Muted)}}}

@Composable
private fun Company(
    s: Stock,
    sharedNews: List<NewsItem>,
    marketStatus: MyStocksCache.MarketStatus,
    onNewsLoaded: (List<NewsItem>) -> Unit,
    onMarketStatusLoaded: (MyStocksCache.MarketStatus) -> Unit,
    back: () -> Unit,
    openPractice: () -> Unit,
    openNews: (NewsItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val watchlistStore = remember { WatchlistStore(context) }
    val watchedSymbols by watchlistStore.symbols.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val watched = watchedSymbols.contains(s.symbol.trim().uppercase())

    CompanyIntelligence(
        s = s,
        back = back,
        openNews = openNews,
        watched = watched,
        marketStocks = stocks,
        sharedNews = sharedNews,
        marketStatus = marketStatus,
        onNewsLoaded = onNewsLoaded,
        onMarketStatusLoaded = onMarketStatusLoaded,
        onWatchToggle = {
            scope.launch {
                if (watched) watchlistStore.remove(s.symbol)
                else watchlistStore.add(s.symbol)
            }
        },
        openPractice = openPractice
    )
}

@Composable
private fun CompanyNewsSection(
    symbol: String,
    items: List<NewsItem>,
    loading: Boolean,
    error: String?
) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Company Intelligence", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("News & corporate actions for $symbol", color = Muted, fontSize = 10.sp)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = LightGreen) {
                    Text("90 DAYS", color = Green, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                loading -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Green)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading company news…", color = Muted, fontSize = 10.sp)
                    }
                }
                error != null -> {
                    Text("Company news temporarily unavailable", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
                items.isEmpty() -> {
                    Text("No recent company news found", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 10.dp))
                }
                else -> {
                    items.take(5).forEachIndexed { index, news ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                            if (news.symbol.isNotBlank()) {
                                Logo(news.symbol, 36)
                            } else {
                                Surface(Modifier.size(36.dp), RoundedCornerShape(9.dp), LightGreen) {
                                    Icon(Icons.Default.Article, null, tint = Green, modifier = Modifier.padding(9.dp))
                                }
                            }
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NewsChip(news.category, false)
                                    Spacer(Modifier.width(6.dp))
                                    Text(newsDate(news.publishedAt), color = Muted, fontSize = 8.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(news.title, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 3)
                                if (news.summary.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(news.summary, color = Muted, fontSize = 9.sp, maxLines = 2)
                                }
                            }
                        }
                        if (index < items.take(5).lastIndex) HorizontalDivider(color = Border)
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("Sourced from MyStocks Africa • approximately 15 min delayed. Verify material announcements with the issuer or NSE.", color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun CompanyHistoryChart(values: List<Double>, tint: Color) {
    val valid = values.filter { it.isFinite() && it > 0.0 }
    if (valid.size < 2) return

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(vertical = 8.dp)
    ) {
        val min = valid.minOrNull() ?: return@Canvas
        val max = valid.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()

        valid.forEachIndexed { index, value ->
            val x = size.width * index / (valid.lastIndex.coerceAtLeast(1))
            val y = size.height - (((value - min) / range).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}

@Composable private fun InfoRow(i:ImageVector,a:String,b:String){Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Icon(i,null,tint=Green,modifier=Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Text(a,Modifier.weight(1f),fontSize=11.sp);Text(b,fontSize=11.sp,fontWeight=FontWeight.Bold)}}

@Composable
private fun News(open:(NewsItem)->Unit){
    var items by remember { mutableStateOf(emptyList<NewsItem>()) }
    var loading by remember { mutableStateOf(true) }
    var category by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(Unit){ loading=true; items=NewsCache.loadFeed(); loading=false }
    val categories=listOf("All","Company News","Dividends","Market","Analysis","Corporate Actions")
    val filtered=if(category=="All") items else items.filter{it.category.equals(category,true)}
    val top=filtered.firstOrNull()
    val trending=filtered.drop(1).take(3)
    val latest=filtered.drop(4)
    LazyColumn(contentPadding=PaddingValues(16.dp,8.dp,16.dp,20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("News","NSE companies, dividends & market intelligence")}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){categories.forEach{c->FilterChip(selected=category==c,onClick={category=c},label={Text(c,fontSize=10.sp)})}}}
        if(loading){item{Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Green)}}}
        else if(filtered.isEmpty()){
            item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Article,null,tint=Green,modifier=Modifier.size(36.dp));Spacer(Modifier.height(8.dp));Text("News unavailable",fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Text("The live news provider did not return any articles right now. No placeholder news is shown.",color=Muted,fontSize=10.sp,textAlign=TextAlign.Center)}}}
        } else {
            top?.let{item{Text("Top News",fontWeight=FontWeight.ExtraBold,fontSize=17.sp)};item{NewsFeatured(it,open)}}
            if(trending.isNotEmpty()){
                item{Text("Most Trending News",fontWeight=FontWeight.ExtraBold,fontSize=17.sp)}
                items(trending){item->NewsListCard(item,open)}
            }
            if(latest.isNotEmpty()){
                item{Text("Latest News",fontWeight=FontWeight.ExtraBold,fontSize=17.sp)}
                items(latest){item->NewsListCard(item,open)}
            }
        }
        item{Text("News and corporate-action information is sourced through the MyStocks market-intelligence feed. Market data may be delayed; always verify important announcements against the issuer or exchange source.",color=Muted,fontSize=9.sp)}
    }
}

private data class NewsDisplayMeta(val symbol:String,val company:String,val logoUrl:String,val label:String,val icon:ImageVector)

private fun newsDisplayMeta(item:NewsItem):NewsDisplayMeta{
    val raw=item.symbol.trim().uppercase(Locale.US)
    val normalized=raw.removeSuffix(".KE")
    val stock=stocks.firstOrNull{it.symbol.equals(normalized,true)}
    val company=item.companyName.trim().ifBlank{stock?.name.orEmpty()}
    val symbol=stock?.symbol?:normalized
    if(company.isNotBlank()||symbol.isNotBlank()){
        val logo=stock?.logoUrl?.takeIf{it.isNotBlank()} ?: "https://mystocks.africa/logos/${symbol.lowercase(Locale.US)}-ke.svg"
        return NewsDisplayMeta(symbol,company,logo,symbol.ifBlank{"COMPANY"},Icons.Default.Business)
    }
    val c=item.category.lowercase(Locale.US)
    return when{
        c.contains("dividend")->NewsDisplayMeta("","","","NSE DIVIDEND",Icons.Default.Payments)
        c.contains("corporate")||c.contains("action")->NewsDisplayMeta("","","","NSE ACTION",Icons.Default.Event)
        c.contains("sector")->NewsDisplayMeta("","","","SECTOR",Icons.Default.AccountTree)
        c.contains("market")->NewsDisplayMeta("","","","MARKET",Icons.Default.TrendingUp)
        else->NewsDisplayMeta("","","","MARKET INTELLIGENCE",Icons.Default.Article)
    }
}

@Composable private fun NewsMetaIcon(item:NewsItem,size:Int,dark:Boolean=false){
    val meta=newsDisplayMeta(item)
    if(meta.logoUrl.isNotBlank()&&meta.symbol.isNotBlank()){
        Surface(Modifier.size(size.dp),RoundedCornerShape(9.dp),if(dark) Color.White.copy(alpha=.13f) else LightGreen){
            Box(Modifier.fillMaxSize(),Alignment.Center){AsyncImage(model=meta.logoUrl,contentDescription=meta.symbol,modifier=Modifier.fillMaxSize().padding(5.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit);Text(meta.symbol.take(3),color=if(dark) Color.White.copy(alpha=.75f) else Green,fontWeight=FontWeight.ExtraBold,fontSize=7.sp)}
        }
    }else{
        Surface(Modifier.size(size.dp),RoundedCornerShape(9.dp),if(dark) Color.White.copy(alpha=.13f) else LightGreen){Icon(meta.icon,null,tint=if(dark) Color.White else Green,modifier=Modifier.padding((size/4).dp))}
    }
}

@Composable private fun NewsFeatured(item:NewsItem,open:(NewsItem)->Unit){
    Card(Modifier.fillMaxWidth().clickable{open(item)},RoundedCornerShape(19.dp),colors=CardDefaults.cardColors(containerColor=DarkGreen)){
        Box(Modifier.fillMaxWidth().height(240.dp)){
            Box(Modifier.fillMaxSize().background(Color(0x66083C27)))
            Column(Modifier.fillMaxSize().padding(15.dp),verticalArrangement=Arrangement.Bottom){
                Row(verticalAlignment=Alignment.CenterVertically){NewsMetaIcon(item,38,true);Spacer(Modifier.width(7.dp));Column(Modifier.weight(1f)){Text(newsDisplayMeta(item).label,color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=9.sp);if(newsDisplayMeta(item).company.isNotBlank())Text(newsDisplayMeta(item).company,color=Color.White.copy(alpha=.8f),fontSize=8.sp)};NewsChip(item.category,true)}
                Spacer(Modifier.height(6.dp));Text(item.title,color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=18.sp,maxLines=3);if(item.summary.isNotBlank()){Spacer(Modifier.height(4.dp));Text(item.summary,color=Color(0xFFD5E9DF),fontSize=10.sp,maxLines=2)}
                Spacer(Modifier.height(5.dp));Row(verticalAlignment=Alignment.CenterVertically){Text(newsDate(item.publishedAt),color=Color.White.copy(alpha=.75f),fontSize=8.sp);Spacer(Modifier.weight(1f));Text("Read full article →",color=Color.White,fontWeight=FontWeight.Bold,fontSize=10.sp)}
            }
        }
    }
}

@Composable private fun NewsListCard(item:NewsItem,open:(NewsItem)->Unit){Card(Modifier.fillMaxWidth().clickable{open(item)},RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Border)){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){NewsMetaIcon(item,42);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(if(newsDisplayMeta(item).company.isNotBlank()) newsDisplayMeta(item).company else newsDisplayMeta(item).label,fontSize=9.sp,color=Muted,modifier=Modifier.weight(1f));NewsChip(item.category,false)};Spacer(Modifier.height(3.dp));Text(item.title,fontWeight=FontWeight.Bold,fontSize=12.sp,maxLines=2);if(item.summary.isNotBlank())Text(item.summary,color=Muted,fontSize=9.sp,maxLines=2);Text(newsDate(item.publishedAt),color=Muted,fontSize=8.sp)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}

@Composable private fun NewsChip(label:String,dark:Boolean){Surface(shape=RoundedCornerShape(20.dp),color=if(dark) Color.White.copy(alpha=.16f) else LightGreen){Text(label,color=if(dark) Color.White else Green,fontWeight=FontWeight.Bold,fontSize=8.sp,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))}}

private fun newsDate(value:String):String=when{value.isBlank()->"Latest";value.length>=10->value.take(10);else->value}


private object PaperPortfolioStore {
    private fun prefs(context: Context) = context.getSharedPreferences("nse_watcher_paper_portfolio", Context.MODE_PRIVATE)
    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun cash(context: Context): Double {
        val raw = prefs(context).getString("practice_v2", null)
        return if (raw == null) prefs(context).getFloat("cash", 0f).toDouble()
            else runCatching { JSONObject(raw).getDouble("cash") }.getOrDefault(0.0)
    }
}

@Composable private fun More(go:(Page)->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Section("More","Your NSE Watcher tools")};item{RowItem(Icons.Default.AccountCircle,"Profile","Personal information and profile picture"){go(Page.PROFILE)}};item{RowItem(Icons.Default.AccountBalanceWallet,"Practice Portfolio","Practice with virtual money"){go(Page.PAPER)}};item{RowItem(Icons.Default.Settings,"Settings","Theme, notifications, data and privacy"){go(Page.SETTINGS)}};item{RowItem(Icons.Default.HelpOutline,"Help & Support","FAQs, contact and report issues"){go(Page.HELP)}};item{RowItem(Icons.Default.Info,"About NSE Watcher","Version and product information"){go(Page.ABOUT)}}}}

@Composable private fun Profile(name:String,username:String,email:String,description:String,onName:(String)->Unit,onUsername:(String)->Unit,onEmail:(String)->Unit,onDescription:(String)->Unit,pick:()->Unit,back:()->Unit,go:(Page)->Unit){var editing by rememberSaveable{mutableStateOf(false)};var n by rememberSaveable(name){mutableStateOf(name)};var u by rememberSaveable(username){mutableStateOf(username)};var e by rememberSaveable(email){mutableStateOf(email)};var d by rememberSaveable(description){mutableStateOf(description)};LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("Profile","Your NSE Watcher account",back)};item{ProfileHero(name,username,pick)};item{if(editing){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileField("Full name",n,{n=it},Icons.Default.Person);ProfileField("Username",u,{u=it},Icons.Default.AccountCircle);ProfileField("Email",e,{e=it},Icons.Default.Email);ProfileField("Description",d,{d=it},Icons.Default.Info);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({editing=false}){Text("Cancel")};Button({onName(n);onUsername(u);onEmail(e);onDescription(d);editing=false},colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Save")}}}}}else{Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileInfo(Icons.Default.AccountCircle,"Username","@$username");ProfileInfo(Icons.Default.Email,"Email",email);ProfileInfo(Icons.Default.Info,"Description",description);Spacer(Modifier.height(5.dp));Button({editing=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(7.dp));Text("Edit Profile")}}}}};item{RowItem(Icons.Default.PhotoCamera,"Profile Picture","Change your profile photo",pick)};item{RowItem(Icons.Default.AccountBalanceWallet,"Practice Portfolio","Practice with virtual money"){go(Page.PAPER)}};item{RowItem(Icons.Default.Settings,"Settings","Theme, notifications, data and privacy"){go(Page.SETTINGS)}};item{RowItem(Icons.Default.Lock,"Account Security","Password and device protection"){go(Page.SECURITY)}};item{RowItem(Icons.Default.PrivacyTip,"Privacy","Review your privacy settings"){go(Page.PRIVACY)}};item{Note("NSE Watcher provides market information and analysis. It does not execute trades or guarantee returns.")}}}
@Composable private fun ProfileHero(name:String,username:String,pick:()->Unit){val c=androidx.compose.ui.platform.LocalContext.current;val u=c.getSharedPreferences(PREFS,0).getString("avatar_uri",null);val b by produceState<Bitmap?>(null,u){value=try{u?.let{c.contentResolver.openInputStream(Uri.parse(it))?.use{stream->BitmapFactory.decodeStream(stream)}}}catch(_:Exception){null}};Card(Modifier.fillMaxWidth(),RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(100.dp)){Surface(Modifier.fillMaxSize(),CircleShape,Color.White){if(b!=null)Image(b!!.asImageBitmap(),"Profile picture",Modifier.fillMaxSize())else Icon(Icons.Default.Person,null,tint=Green,modifier=Modifier.padding(25.dp))};Surface(Modifier.size(32.dp).align(Alignment.BottomEnd).clickable(onClick=pick),CircleShape,Green){Icon(Icons.Default.PhotoCamera,"Change profile picture",modifier=Modifier.padding(7.dp),tint=Color.White)}};Spacer(Modifier.height(10.dp));Text(name,fontSize=21.sp,fontWeight=FontWeight.ExtraBold);Text("@$username",fontSize=11.sp,color=Muted)}}}
@Composable private fun ProfileField(label:String,value:String,onValue:(String)->Unit,i:ImageVector){OutlinedTextField(value,onValue,Modifier.fillMaxWidth().padding(bottom=8.dp),label={Text(label)},leadingIcon={Icon(i,null,tint=Green)},singleLine=label!="Description")}
@Composable private fun ProfileInfo(i:ImageVector,label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(38.dp),CircleShape,LightGreen){Icon(i,null,tint=Green,modifier=Modifier.padding(9.dp))};Spacer(Modifier.width(11.dp));Column{Text(label,fontSize=10.sp,color=Muted);Text(value,fontSize=13.sp,fontWeight=FontWeight.Bold)}}}

@Composable
private fun Settings(dark:Boolean,market:Boolean,price:Boolean,news:Boolean,app:Boolean,refresh:Boolean,volume:Boolean,changes:Boolean,onDark:(Boolean)->Unit,onMarket:(Boolean)->Unit,onPrice:(Boolean)->Unit,onNews:(Boolean)->Unit,onApp:(Boolean)->Unit,onRefresh:(Boolean)->Unit,onVolume:(Boolean)->Unit,onChanges:(Boolean)->Unit,back:()->Unit,go:(Page)->Unit) {
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Header("Settings","Customize NSE Watcher",back) }
        item { SettingsCard("Appearance",Icons.Default.Palette) { ToggleRow(Icons.Default.DarkMode,"Dark theme","Switch between light and dark mode",dark,onDark); RowItem(Icons.Default.Palette,"Theme","Light / Dark"){go(Page.THEME)}; RowItem(Icons.Default.Language,"Language","English • Kenya • KSh"){go(Page.LANGUAGE)}; RowItem(Icons.Default.Visibility,"Font & Display","Size, density and animations"){go(Page.DISPLAY)} } }
        item { SettingsCard("Notifications",Icons.Default.Notifications) { ToggleRow(Icons.Default.NotificationsActive,"Market alerts","Important NSE market updates",market,onMarket); ToggleRow(Icons.Default.PriceChange,"Price alerts","Watchlist and threshold alerts",price,onPrice); ToggleRow(Icons.Default.Article,"News alerts","Company and market news",news,onNews); ToggleRow(Icons.Default.Apps,"App notifications","Product and service updates",app,onApp); RowItem(Icons.Default.Notifications,"Notification settings","Detailed notification controls"){go(Page.NOTIFICATIONS)} } }
        item { SettingsCard("Market & Data",Icons.Default.ShowChart) { ToggleRow(Icons.Default.Sync,"Auto refresh","Refresh market information every 15 minutes while the app is open",refresh,onRefresh); ToggleRow(Icons.Default.BarChart,"Show volume","Display trading volume where available",volume,onVolume); ToggleRow(Icons.Default.TrendingUp,"Show price changes","Display daily gains and losses",changes,onChanges); RowItem(Icons.Default.ShowChart,"Live data settings","Refresh and market data preferences"){go(Page.LIVE_DATA)}; RowItem(Icons.Default.Tune,"Chart settings","Timeframes, style and indicators"){go(Page.CHARTS)}; RowItem(Icons.Default.NotificationsNone,"Price alerts","Manage alert rules"){go(Page.ALERTS)} } }
        item { SettingsCard("Account & Privacy",Icons.Default.AccountCircle) { RowItem(Icons.Default.Lock,"Account security","Password, sessions and protection"){go(Page.SECURITY)}; RowItem(Icons.Default.PrivacyTip,"Privacy","Personalisation and data controls"){go(Page.PRIVACY)} } }
        item { SettingsCard("Support",Icons.Default.HelpOutline) { RowItem(Icons.Default.HelpOutline,"Help & Support","FAQs, feedback and report issues"){go(Page.HELP)}; RowItem(Icons.Default.Info,"About NSE Watcher","Version and product information"){go(Page.ABOUT)} } }
        item { Note("NSE Watcher is an analysis and education product. Practice Portfolio uses virtual money only and does not place real trades.") }
    }
}
@Composable private fun SettingsCard(title:String,icon:ImageVector,content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(34.dp),CircleShape,LightGreen){Icon(icon,null,tint=Green,modifier=Modifier.padding(8.dp))};Spacer(Modifier.width(9.dp));Text(title,fontWeight=FontWeight.ExtraBold,fontSize=15.sp)};Spacer(Modifier.height(4.dp));content()}}}
@Composable private fun ToggleRow(icon:ImageVector,title:String,sub:String,checked:Boolean,onChecked:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(21.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Switch(checked,onChecked)}}
@Composable private fun RowItem(icon:ImageVector,title:String,sub:String,onClick:()->Unit={}){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(22.dp));Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(19.dp))}}
@Composable private fun ThemePage(dark:Boolean,set:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Theme","Choose how NSE Watcher looks",back)};item{ThemeChoice("Light","Bright white interface",!dark){set(false)}};item{ThemeChoice("Dark","Low-light interface",dark){set(true)}};}}
@Composable private fun ThemeChoice(title:String,sub:String,selected:Boolean,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),RoundedCornerShape(16.dp),border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Green else Border),colors=CardDefaults.cardColors(containerColor=if(selected)LightGreen else MaterialTheme.colorScheme.surface)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(title=="Light")Icons.Default.LightMode else if(title=="Dark")Icons.Default.DarkMode else Icons.Default.SettingsSystemDaydream,null,tint=Green);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(sub,fontSize=10.sp,color=Muted)};if(selected)Icon(Icons.Default.CheckCircle,null,tint=Green)}}}
@Composable private fun NotificationsPage(m:Boolean,p:Boolean,n:Boolean,a:Boolean,sm:(Boolean)->Unit,sp:(Boolean)->Unit,sn:(Boolean)->Unit,sa:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Notifications","Choose what you want to hear about",back)};item{SettingsCard("Notification Types",Icons.Default.Notifications){ToggleRow(Icons.Default.ShowChart,"Market updates","NSE-wide movements and daily briefs",m,sm);ToggleRow(Icons.Default.PriceChange,"Price alerts","Your watchlist thresholds",p,sp);ToggleRow(Icons.Default.Article,"News alerts","Company and market news",n,sn);ToggleRow(Icons.Default.Apps,"App notifications","Product updates",a,sa)}};item{Note("Price, news, and corporate-action alerts are monitored from provider-backed market data. Background checks are scheduled about every 15 minutes; Android may delay them. News checks run outside market hours and catch up on available stories from the last 7 days. Price alerts require an open market and quotes no more than 30 minutes old. Daily gain, loss and volume alerts notify once per rule per Nairobi day.")}}}
@Composable private fun LiveData(refresh:Boolean,volume:Boolean,changes:Boolean,sr:(Boolean)->Unit,sv:(Boolean)->Unit,sc:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Live Data","Market-data display preferences",back)};item{SettingsCard("Data Display",Icons.Default.ShowChart){ToggleRow(Icons.Default.Sync,"Auto refresh","Keep market information current",refresh,sr);ToggleRow(Icons.Default.BarChart,"Show volume","Display trading activity",volume,sv);ToggleRow(Icons.Default.TrendingUp,"Show price changes","Display daily percentage moves",changes,sc)}}}}
@Composable
private fun AlertPage(back:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val store=remember{AlertStore(context)}
    val watchlistStore=remember{WatchlistStore(context)}
    val alerts by store.alerts.collectAsState(initial=emptyList())
    val watchedSymbols by watchlistStore.symbols.collectAsState(initial=emptyList())
    LaunchedEffect(Unit) {
        if (MarketRefreshController.shouldRefreshQuotes(stocks.isNotEmpty())) {
            MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { liveStocks.value = it }
        }
    }
    val watched=stocks.filter{it.symbol.uppercase() in watchedSymbols.map(String::uppercase)}
    val scope=rememberCoroutineScope()
    val notificationPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    var selectedSymbol by rememberSaveable{mutableStateOf(watched.firstOrNull()?.symbol?:"")}
    var selectedTypeName by rememberSaveable{mutableStateOf(AlertType.PRICE_ABOVE.name)}
    var thresholdText by rememberSaveable{mutableStateOf("")}
    var editingId by rememberSaveable{mutableStateOf<String?>(null)}
    var symbolMenu by remember{mutableStateOf(false)}
    var typeMenu by remember{mutableStateOf(false)}
    val selectedType=runCatching{AlertType.valueOf(selectedTypeName)}.getOrDefault(AlertType.PRICE_ABOVE)
    val supportedTypes=listOf(AlertType.PRICE_ABOVE,AlertType.PRICE_BELOW,AlertType.DAILY_GAIN,AlertType.DAILY_LOSS,AlertType.HIGH_VOLUME,AlertType.NEWS,AlertType.CORPORATE_ACTION)

    fun resetForm(){
        editingId=null
        selectedSymbol=watched.firstOrNull()?.symbol?:""
        selectedTypeName=AlertType.PRICE_ABOVE.name
        thresholdText=""
    }

    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Header("Price Alerts","Real threshold monitoring for companies you follow",back)}
        item{
            if(watched.isEmpty()) Note("Add a company to your Watchlist first. Alert rules are user-owned and are not created for demo symbols.")
            else Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(if(editingId==null)"Create alert" else "Edit alert",fontWeight=FontWeight.ExtraBold,fontSize=16.sp)
                    Text("Supported rules use provider-supplied price, daily change, and volume data. Background checks run no more often than every 15 minutes.",fontSize=9.sp,color=Muted)
                    Box(Modifier.fillMaxWidth()){
                        OutlinedButton(onClick={symbolMenu=true},modifier=Modifier.fillMaxWidth()){
                            Text(if(selectedSymbol.isBlank())"Select company" else selectedSymbol,Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown,null)
                        }
                        DropdownMenu(expanded=symbolMenu,onDismissRequest={symbolMenu=false}){
                            watched.forEach{s->DropdownMenuItem(text={Text(s.symbol+" • "+s.name)},onClick={selectedSymbol=s.symbol;symbolMenu=false})}
                        }
                    }
                    Box(Modifier.fillMaxWidth()){
                        OutlinedButton(onClick={typeMenu=true},modifier=Modifier.fillMaxWidth()){
                            Text(selectedTypeLabel(selectedType),Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown,null)
                        }
                        DropdownMenu(expanded=typeMenu,onDismissRequest={typeMenu=false}){
                            supportedTypes.forEach{type->DropdownMenuItem(text={Text(selectedTypeLabel(type))},onClick={selectedTypeName=type.name;typeMenu=false})}
                        }
                    }
                    OutlinedTextField(
                        value=thresholdText,
                        onValueChange={thresholdText=it.filter{ch->ch.isDigit()||ch=='.'}},
                        modifier=Modifier.fillMaxWidth(),
                        singleLine=true,
                        label={Text(if(selectedType==AlertType.PRICE_ABOVE||selectedType==AlertType.PRICE_BELOW)"Threshold (KSh)" else if(selectedType==AlertType.DAILY_GAIN||selectedType==AlertType.DAILY_LOSS)"Threshold (%)" else if(selectedType==AlertType.HIGH_VOLUME)"Volume above average (%)" else "No threshold needed")},
                        placeholder={Text(if(selectedType==AlertType.DAILY_LOSS||selectedType==AlertType.HIGH_VOLUME)"Example: 50" else "Example: 30 or 5")}
                    )
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                        if(editingId!=null)TextButton({resetForm()}){Text("Cancel")}
                        Button(onClick={
                            val threshold=thresholdText.toDoubleOrNull()
                            val noThreshold=selectedType==AlertType.NEWS||selectedType==AlertType.CORPORATE_ACTION
                            if(selectedSymbol.isNotBlank()&&(noThreshold||(threshold!=null&&threshold>0.0))){
                                if(Build.VERSION.SDK_INT>=33) notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                                scope.launch{
                                    store.save(PriceAlert(editingId?:java.util.UUID.randomUUID().toString(),selectedSymbol,selectedType,threshold,true))
                                    resetForm()
                                }
                            }
                        },enabled=selectedSymbol.isNotBlank()&&(selectedType==AlertType.NEWS||selectedType==AlertType.CORPORATE_ACTION||thresholdText.toDoubleOrNull()?.let{it>0.0}==true),colors=ButtonDefaults.buttonColors(containerColor=Green)){
                            Icon(if(editingId==null)Icons.Default.Add else Icons.Default.Save,null)
                            Spacer(Modifier.width(6.dp));Text(if(editingId==null)"Add alert" else "Save changes")
                        }
                    }
                }
            }
        }
        item{Text("Your alert rules",fontWeight=FontWeight.ExtraBold,fontSize=16.sp)}
        if(alerts.isEmpty()) item{Note("No alert rules yet. The app will not invent or pre-fill alerts.")}
        items(alerts,key={it.id}){alert->
            Card(Modifier.fillMaxWidth(),RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Border)){
                Column(Modifier.padding(13.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(alert.symbol+" • "+selectedTypeLabel(alert.type),fontWeight=FontWeight.Bold,fontSize=12.sp)
                            Text(alert.threshold?.let{if(alert.type==AlertType.PRICE_ABOVE||alert.type==AlertType.PRICE_BELOW)"Threshold KSh %.2f".format(Locale.US,it) else "Threshold %.2f%%".format(Locale.US,it)}?:"No threshold",fontSize=10.sp,color=Muted)
                        }
                        Switch(checked=alert.enabled,onCheckedChange={enabled->scope.launch{store.setEnabled(alert.id,enabled)}})
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                        TextButton({selectedSymbol=alert.symbol;selectedTypeName=alert.type.name;thresholdText=alert.threshold?.toString()?:"";editingId=alert.id}){Text("Edit")}
                        TextButton({scope.launch{store.remove(alert.id);if(editingId==alert.id)resetForm()}}){Text("Delete",color=Red)}
                    }
                }
            }
        }
        item{Note("Price alerts trigger on a real threshold crossing. Daily gain/loss alerts use the provider's reported daily percentage and are limited to one notification per alert per Nairobi calendar day. Notifications require Android notification permission.") }
    }
}

private fun selectedTypeLabel(type:AlertType):String=when(type){
    AlertType.PRICE_ABOVE->"Price rises above"
    AlertType.PRICE_BELOW->"Price falls below"
    AlertType.DAILY_GAIN->"Daily gain reaches"
    AlertType.DAILY_LOSS->"Daily loss reaches"
    AlertType.HIGH_VOLUME->"Volume exceeds average by"
    AlertType.NEWS->"New company news"
    AlertType.CORPORATE_ACTION->"Corporate action"
    else->type.name.replace('_',' ')
}

@Composable private fun SimplePage(title:String,icon:ImageVector,rows:List<Pair<String,String>>,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header(title,null,back)};item{SettingsCard(title,icon){rows.forEach{RowItem(icon,it.first,it.second)}}}}}
@Composable private fun HelpPage(back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Help & Support","Get help using NSE Watcher",back)};item{SettingsCard("Support",Icons.Default.HelpOutline){RowItem(Icons.Default.MenuBook,"Getting started","Learn how to read the market dashboard");RowItem(Icons.Default.QuestionMark,"Frequently asked questions","Common NSE Watcher questions");RowItem(Icons.Default.ReportProblem,"Report a problem","Tell us about an issue")}}}}
@Composable private fun AboutPage(back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("About NSE Watcher","Market intelligence for the NSE",back)};item{SettingsCard("NSE Watcher",Icons.Default.Info){Text("Version 0.1.0",fontWeight=FontWeight.Bold);Text("Trading apps help you buy. NSE Watcher helps you understand what you're buying.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=7.dp));Spacer(Modifier.height(9.dp));Text("NSE Watcher does not execute real trades and does not guarantee investment returns.",fontSize=10.sp,color=Muted)
            Spacer(Modifier.height(9.dp))
            Text("Nairobi skyline photo: Antony Trivet • Creative Commons BY-SA 4.0",fontSize=9.sp,color=Muted)
            Text("Source: commons.wikimedia.org/wiki/File:Nairobi_City_County_Skyline.jpg",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
            Text("License: creativecommons.org/licenses/by-sa/4.0/ • Image bundled with the app; crop/overlay applied.",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
        }}}}
@Composable private fun Note(text:String){Card(Modifier.fillMaxWidth(),RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null,tint=Green);Spacer(Modifier.width(9.dp));Text(text,fontSize=9.sp,color=Muted)}}}






