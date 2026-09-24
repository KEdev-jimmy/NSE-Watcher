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
import androidx.compose.ui.graphics.luminance
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
import ke.co.nsewatcher.data.MarketData
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

private val Green: Color
    @Composable get() = MaterialTheme.colorScheme.primary
private val LightGreen: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val DarkGreen: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
private val TextDark: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground
private val Muted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Border: Color
    @Composable get() = MaterialTheme.colorScheme.outline
private val Red: Color
    @Composable get() = MaterialTheme.colorScheme.error
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

private enum class Page { HOME, MARKET, NEWS, COMPANIES, PAPER, MORE, COMPANY, WATCHLIST, COMPARE, NEWS_DETAIL, PROFILE, SETTINGS, ACCOUNT, THEME, NOTIFICATIONS, LIVE_DATA, CHARTS, ALERTS, LANGUAGE, SECURITY, PRIVACY, DISPLAY, HELP, ABOUT }

class DesignActivity : ComponentActivity() {
    private var alertDestination by mutableStateOf<AlertDestination?>(null)
    private var practiceDestination by mutableStateOf<PracticeNotificationDestination?>(null)
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
        practiceDestination = PracticeNotifications.destination(intent)
        setContent {
            App(
                alertDestination = alertDestination,
                consumeAlert = { alertDestination = null; intent.action = null },
                practiceDestination = practiceDestination,
                consumePractice = { practiceDestination = null; intent.action = null; intent.data = null },
                pickAvatar = { picker.launch(arrayOf("image/*")) }
            )
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alertDestination = AlertNotifications.destination(intent)
        practiceDestination = PracticeNotifications.destination(intent)
    }
}

@Composable
private fun App(
    alertDestination: AlertDestination?,
    consumeAlert: () -> Unit,
    practiceDestination: PracticeNotificationDestination?,
    consumePractice: () -> Unit,
    pickAvatar: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val onboardingCompleted = prefs.getBoolean("onboarding_complete", false)
    val firstLaunchOnboarding = remember(packageInfo, onboardingCompleted) {
        shouldShowFirstLaunchOnboarding(
            completed = onboardingCompleted,
            firstInstallTime = packageInfo?.firstInstallTime ?: 0L,
            lastUpdateTime = packageInfo?.lastUpdateTime ?: Long.MAX_VALUE
        )
    }
    var showOnboarding by rememberSaveable { mutableStateOf(firstLaunchOnboarding) }
    LaunchedEffect(Unit) {
        if (!onboardingCompleted && !firstLaunchOnboarding) {
            // Existing installations should not be forced through first-launch education
            // after upgrading to the redesigned launch experience.
            prefs.edit().putBoolean("onboarding_complete", true).apply()
        }
    }
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
                    runCatching { MarketData.stocks() }.getOrDefault(emptyList())
                }
                val newsDeferred = async {
                    runCatching { MarketData.newsFeed().items }.getOrDefault(emptyList())
                }
                val companiesDeferred = async {
                    runCatching { MarketData.companies() }.getOrDefault(emptyList())
                }
                val statusDeferred = async {
                    runCatching { MarketData.status() }.getOrDefault(MyStocksCache.MarketStatus())
                }
                val indicesDeferred = async {
                    val status = statusDeferred.await()
                    runCatching {
                        MarketData.indices(status.isKnown && status.isOpen)
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
    var practiceReviewOrderId by rememberSaveable { mutableStateOf("") }
    var practiceLaunchRevision by rememberSaveable { mutableIntStateOf(0) }
    var practiceLaunchSource by rememberSaveable { mutableStateOf("") }
    var alertNavigationRevision by remember { mutableIntStateOf(0) }
    val directoryState = rememberSaveableStateHolder()
    var directorySector by rememberSaveable { mutableStateOf("All") }
    var comparisonSymbols by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var dark by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var name by rememberSaveable { mutableStateOf(prefs.getString("profile_name", ProfileDefaults.displayName) ?: ProfileDefaults.displayName) }
    var username by rememberSaveable { mutableStateOf(prefs.getString("username", ProfileDefaults.username) ?: ProfileDefaults.username) }
    var email by rememberSaveable { mutableStateOf(prefs.getString("email", ProfileDefaults.email) ?: ProfileDefaults.email) }
    var description by rememberSaveable { mutableStateOf(prefs.getString("description", ProfileDefaults.description) ?: ProfileDefaults.description) }
    var marketAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("market_alerts", true)) }
    var priceAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("price_alerts", true)) }
    var watchlistNewsAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("watchlist_news_alerts", false)) }
    var watchlistCorporateAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("watchlist_corporate_alerts", false)) }
    var practiceAlerts by rememberSaveable {
        mutableStateOf(if (prefs.contains("practice_alerts")) prefs.getBoolean("practice_alerts", true) else prefs.getBoolean("app_alerts", true))
    }
    var notificationSound by rememberSaveable { mutableStateOf(prefs.getString("notification_sound", "Default") ?: "Default") }
    var autoRefresh by rememberSaveable { mutableStateOf(prefs.getBoolean("auto_refresh", true)) }
    var fontSizeSetting by rememberSaveable { mutableStateOf(prefs.getString("font_size", "Medium") ?: "Medium") }
    var chartDefaultRange by rememberSaveable { mutableStateOf(prefs.getString("chart_default_range", "1D") ?: "1D") }
    var chartShowGrid by rememberSaveable { mutableStateOf(prefs.getBoolean("chart_show_grid", true)) }
    val latestSelected by rememberUpdatedState(selected)

    if (showSplash) {
        NSEWatcherOpeningScreen(
            ready = startupReady,
            onFinished = { showSplash = false }
        )
        return
    }
    if (showOnboarding) {
        NSEWatcherOnboardingScreen {
            prefs.edit().putBoolean("onboarding_complete", true).apply()
            showOnboarding = false
        }
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
        val target = practiceDestination ?: return@LaunchedEffect
        history = listOf(Page.HOME)
        practiceSymbol = ""
        practiceReviewOrderId = target.orderId
        practiceLaunchSource = if (target.orderId.isBlank()) "" else "Practice fill notification"
        practiceLaunchRevision++
        page = Page.PAPER
        consumePractice()
    }
    LaunchedEffect(page) {
        if (page == Page.COMPANIES && companyCatalog.isEmpty()) {
            MarketData.companies().takeIf { it.isNotEmpty() }?.let { companyCatalog = it }
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
            val refreshedStatus = MarketData.status()
            val marketStateChanged = refreshedStatus.isKnown &&
                (!previousMarketKnown || refreshedStatus.isOpen != previousMarketOpen)
            val becameOpen = refreshedStatus.isKnown && refreshedStatus.isOpen && !previousMarketOpen
            startupMarketStatus = refreshedStatus

            val quoteRefreshDue = MarketRefreshController.shouldRefreshQuotes(stocks.isNotEmpty())
            val openingRefresh = becameOpen && !MarketRefreshController.state.value.refreshInProgress

            if (marketStateChanged || quoteRefreshDue) {
                MarketData.indices(refreshedStatus.isKnown && refreshedStatus.isOpen)
                    .takeIf { it.isNotEmpty() }
                    ?.let { marketIndices = it }
            }

            if (openingRefresh || quoteRefreshDue) {
                MarketData.stocks().takeIf { it.isNotEmpty() }?.let { refreshed ->
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
    fun put(k:String,v:Boolean){prefs.edit().putBoolean(k,v).apply()}
    fun put(k:String,v:String){prefs.edit().putString(k,v).apply()}
    fun go(to:Page){if(to!=page){history=history+page;page=to}}
    fun launchPractice(symbol:String="", source:String=""){
        practiceSymbol=PracticeLaunch.symbol(symbol)
        practiceReviewOrderId=""
        practiceLaunchSource=source.trim()
        practiceLaunchRevision++
        go(Page.PAPER)
    }
    fun launchPracticeReview(orderId:String){
        practiceSymbol=""
        practiceReviewOrderId=orderId.trim()
        practiceLaunchSource="Home follow-up"
        practiceLaunchRevision++
        go(Page.PAPER)
    }
    fun back(){if(history.isNotEmpty()){page=history.last();history=history.dropLast(1)}else page=Page.HOME}
    BackHandler(enabled=page!=Page.HOME){back()}
    val fontScaleMultiplier = when (fontSizeSetting) {
        "Small" -> 0.90f
        "Large" -> 1.12f
        else -> 1f
    }
    NseWatcherTheme(darkTheme = dark, fontScaleMultiplier = fontScaleMultiplier) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
        when(page){
            Page.HOME,Page.MARKET,Page.NEWS,Page.COMPANIES,Page.PAPER,Page.MORE -> Scaffold(topBar={if(page!=Page.PAPER && page!=Page.HOME && page!=Page.MARKET && page!=Page.NEWS && page!=Page.COMPANIES) TopBar(name,::go)},bottomBar={BottomNav(when(page){Page.HOME->0;Page.MARKET->1;Page.NEWS->2;Page.COMPANIES->3;else->4}, newsStyle=page==Page.NEWS, homeStyle=page==Page.PAPER || page==Page.MARKET || page==Page.HOME || page==Page.COMPANIES){tab=it;if(it==3)directorySector="All";history=emptyList();page=when(it){0->Page.HOME;1->Page.MARKET;2->Page.NEWS;3->Page.COMPANIES;else->Page.MORE}}}){pad->Box(Modifier.fillMaxSize().padding(pad)){when(page){Page.HOME->HomeDashboard(stocks,{selected=it;go(Page.COMPANY)},{selectedNews=it;go(Page.NEWS_DETAIL)},{go(Page.MARKET)},{directorySector="All";go(Page.COMPANIES)},{go(Page.WATCHLIST)},newsFeed,marketIndices,startupMarketStatus,startupComplete,name=name,initialCatalog=companyCatalog,practiceEnabled=PaperPortfolioStore.isEnabled(context),practiceCash=PaperPortfolioStore.cash(context),darkTheme=dark,openAllNews={go(Page.NEWS)},openPractice={launchPractice()},openPracticeReview={launchPracticeReview(it)},openProfile={go(Page.PROFILE)},openAlertSettings={go(Page.NOTIFICATIONS)},onQuotesLoaded={liveStocks.value=it},onNewsLoaded={newsFeed=it},onIndicesLoaded={marketIndices=it},onMarketStatusLoaded={startupMarketStatus=it});Page.MARKET->directoryState.SaveableStateProvider("market"){MarketDashboard(stocks,companyCatalog,startupMarketStatus,marketIndices,newsFeed,openCompany={selected=it;go(Page.COMPANY)},openCompanies={directorySector=it;go(Page.COMPANIES)},onQuotesLoaded={liveStocks.value=it},onCatalogLoaded={companyCatalog=it},onIndicesLoaded={marketIndices=it},onMarketStatusLoaded={startupMarketStatus=it})};Page.NEWS->directoryState.SaveableStateProvider("news"){NewsDashboard(newsFeed,onNewsLoaded={newsFeed=it}){selectedNews=it;go(Page.NEWS_DETAIL)}};Page.COMPANIES->directoryState.SaveableStateProvider("companies:$directorySector"){CompaniesDirectory(catalog=companyCatalog,quotes=stocks,name=name,initialSector=directorySector,openCompany={selected=it;go(Page.COMPANY)},openWatchlist={go(Page.WATCHLIST)},openCompare={comparisonSymbols=it;go(Page.COMPARE)},openNews={selectedNews=it;go(Page.NEWS_DETAIL)},openProfile={go(Page.PROFILE)},onCatalogLoaded={companyCatalog=it},onQuotesLoaded={liveStocks.value=it})};Page.PAPER->directoryState.SaveableStateProvider("practice"){PracticePortfolioScreen(quoteFeed=stocks,catalog=companyCatalog,initialMarket=startupMarketStatus,news=newsFeed,initialSymbol=practiceSymbol,initialReviewOrderId=practiceReviewOrderId,launchRevision=practiceLaunchRevision,launchSource=practiceLaunchSource,onQuotes={liveStocks.value=it},onCatalog={companyCatalog=it},openCompany={selected=it;go(Page.COMPANY)},openNews={selectedNews=it;go(Page.NEWS_DETAIL)},back=::back)};else->MoreHubScreen(name=name,username=username,email=email,openProfile={go(Page.PROFILE)},openPractice={launchPractice()},openWatchlist={go(Page.WATCHLIST)},openAlerts={go(Page.ALERTS)},openCompare={comparisonSymbols=emptyList();go(Page.COMPARE)},openSettings={go(Page.SETTINGS)},openNotifications={go(Page.NOTIFICATIONS)},openMarketData={go(Page.LIVE_DATA)},openAppearance={go(Page.DISPLAY)},openHelp={go(Page.HELP)},openAbout={go(Page.ABOUT)})}}}
            Page.COMPANY->Company(
                s=selected,
                sharedNews=newsFeed,
                marketStatus=startupMarketStatus,
                onNewsLoaded={newsFeed=it},
                onMarketStatusLoaded={startupMarketStatus=it},
                back=::back,
                openPractice={launchPractice(selected.symbol,"Company Intelligence")}
            ){selectedNews=it;go(Page.NEWS_DETAIL)}
            Page.WATCHLIST->WatchlistDashboard(quoteStocks=stocks, initialCatalog=companyCatalog, initialMarket=startupMarketStatus, onQuotesLoaded={liveStocks.value=it}, openCompany={selected=it;go(Page.COMPANY)}, openNews={selectedNews=it;go(Page.NEWS_DETAIL)}, openPreferences={go(Page.NOTIFICATIONS)}, back=::back)
            Page.COMPARE->CompanyComparison(CompaniesPresentation.companies(companyCatalog, stocks),::back,comparisonSymbols)
            Page.NEWS_DETAIL->key(alertNavigationRevision) { selectedNews?.let { NewsArticleScreen(it,companyCatalog,stocks,::back){company->selected=company;go(Page.COMPANY)} } }
            Page.PROFILE->ProfileHubScreen(name,username,email,description,{name=it;put("profile_name",it)},{username=it;put("username",it)},{email=it;put("email",it)},{description=it;put("description",it)},pickAvatar,::back,{go(Page.ACCOUNT)},{go(Page.WATCHLIST)},{launchPractice()})
            Page.SETTINGS->SettingsOverviewScreen(
                back=::back,openAccount={go(Page.ACCOUNT)},openNotifications={go(Page.NOTIFICATIONS)},
                openMarketData={go(Page.LIVE_DATA)},openAppearance={go(Page.DISPLAY)},openCharts={go(Page.CHARTS)},
                openLanguage={go(Page.LANGUAGE)},openPrivacy={go(Page.PRIVACY)},openHelp={go(Page.HELP)},openAbout={go(Page.ABOUT)}
            )
            Page.ACCOUNT->AccountSignInScreen(name,username,::back){go(Page.PROFILE)}
            Page.THEME->DisplayAppearanceScreen(dark,fontSizeSetting,{dark=it;put("dark_mode",it)},{fontSizeSetting=it;put("font_size",it)},::back)
            Page.NOTIFICATIONS->NotificationCenterScreen(
                marketAlerts=marketAlerts,priceAlerts=priceAlerts,newsAlerts=watchlistNewsAlerts,corporateAlerts=watchlistCorporateAlerts,
                practiceAlerts=practiceAlerts,soundMode=notificationSound,
                onMarketAlerts={marketAlerts=it;put("market_alerts",it)},
                onPriceAlerts={priceAlerts=it;put("price_alerts",it)},
                onNewsAlerts={enabled->
                    watchlistNewsAlerts=enabled
                    prefs.edit().putBoolean("watchlist_news_alerts",enabled).apply()
                    if(enabled) prefs.edit().putLong("watchlist_news_enabled_at",System.currentTimeMillis()).apply()
                },
                onCorporateAlerts={enabled->
                    watchlistCorporateAlerts=enabled
                    prefs.edit().putBoolean("watchlist_corporate_alerts",enabled).apply()
                    if(enabled) prefs.edit().putLong("watchlist_corporate_enabled_at",System.currentTimeMillis()).apply()
                },
                onPracticeAlerts={practiceAlerts=it;put("practice_alerts",it)},
                onSoundMode={notificationSound=it;put("notification_sound",it)},
                openAlertRules={go(Page.ALERTS)},back=::back
            )
            Page.LIVE_DATA->MarketDataSettingsScreen(autoRefresh,{autoRefresh=it;put("auto_refresh",it)},::back)
            Page.CHARTS->ChartSettingsScreen(chartDefaultRange,chartShowGrid,{chartDefaultRange=it;put("chart_default_range",it)},{chartShowGrid=it;put("chart_show_grid",it)},::back)
            Page.ALERTS->AlertPage(::back)
            Page.LANGUAGE->LanguageRegionScreen(::back)
            Page.SECURITY->AccountSignInScreen(name,username,::back){go(Page.PROFILE)}
            Page.PRIVACY->PrivacyDataScreen(::back)
            Page.DISPLAY->DisplayAppearanceScreen(dark,fontSizeSetting,{dark=it;put("dark_mode",it)},{fontSizeSetting=it;put("font_size",it)},::back)
            Page.HELP->HelpSupportExperienceScreen(::back)
            Page.ABOUT->AboutNseWatcherExperienceScreen(::back)
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
@Composable private fun TopBar(name:String,go:(Page)->Unit){
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        NseWatcherBrandLockup(
            modifier=Modifier.weight(1f),
            dark=dark,
            compact=true
        )
        Avatar(name){go(Page.PROFILE)}
    }
}
@Composable private fun Avatar(name:String,onClick:()->Unit){val c=androidx.compose.ui.platform.LocalContext.current;val u=c.getSharedPreferences(PREFS,0).getString("avatar_uri",null);val b by produceState<Bitmap?>(null,u){value=try{u?.let{c.contentResolver.openInputStream(Uri.parse(it))?.use{stream->BitmapFactory.decodeStream(stream)}}}catch(_:Exception){null}};Surface(Modifier.size(38.dp).clip(CircleShape).clickable(onClick=onClick),CircleShape,Color(0xFFDDEFE6)){if(b!=null)Image(b!!.asImageBitmap(),"Profile",Modifier.fillMaxSize())else Box(Modifier.fillMaxSize(),Alignment.Center){Text(name.take(1).uppercase(),color=Green,fontWeight=FontWeight.Bold)}}}
@Composable
private fun BottomNav(selected: Int, newsStyle: Boolean = false, homeStyle: Boolean = false, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Market" to Icons.Default.CandlestickChart,
        "News" to Icons.Default.Article,
        "Companies" to Icons.Default.Business,
        "More" to Icons.Default.MoreHoriz
    )
    if (!homeStyle) {
        val accent = if (newsStyle) NewsColorScheme.primary else Green
        NavigationBar(
            containerColor = if (newsStyle) NewsColorScheme.background else NavigationBarDefaults.containerColor
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    icon = { Icon(item.second, item.first) },
                    label = { Text(item.first, fontSize = if (newsStyle) 11.sp else 9.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent,
                        selectedTextColor = accent,
                        indicatorColor = if (newsStyle) NewsColorScheme.surfaceVariant else LightGreen,
                        unselectedIconColor = if (newsStyle) NewsColorScheme.onSurfaceVariant else Muted,
                        unselectedTextColor = if (newsStyle) NewsColorScheme.onSurfaceVariant else Muted
                    )
                )
            }
        }
        return
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = premiumHomePalette(dark)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        shape = RoundedCornerShape(28.dp),
        color = palette.nav,
        border = BorderStroke(1.dp, palette.border),
        tonalElevation = 0.dp,
        shadowElevation = 7.dp
    ) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selected == index
                val selectedModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        if (isSelected) palette.primary.copy(alpha = if (dark) 0.13f else 0.10f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                Column(
                    selectedModifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        item.second,
                        contentDescription = item.first,
                        tint = if (isSelected) palette.primary else palette.muted,
                        modifier = Modifier.size(if (isSelected) 25.dp else 23.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.first,
                        color = if (isSelected) palette.primary else palette.muted,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isSelected) {
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                        )
                    }
                }
            }
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
    LaunchedEffect(Unit){ loading=true; items=MarketData.newsFeed().items; loading=false }
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

@Composable private fun Profile(name:String,username:String,email:String,description:String,onName:(String)->Unit,onUsername:(String)->Unit,onEmail:(String)->Unit,onDescription:(String)->Unit,pick:()->Unit,back:()->Unit,go:(Page)->Unit){var editing by rememberSaveable{mutableStateOf(false)};var n by rememberSaveable(name){mutableStateOf(name)};var u by rememberSaveable(username){mutableStateOf(username)};var e by rememberSaveable(email){mutableStateOf(email)};var d by rememberSaveable(description){mutableStateOf(description)};LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("Profile","Your NSE Watcher account",back)};item{ProfileHero(name,username,pick)};item{if(editing){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileField("Full name",n,{n=it},Icons.Default.Person);ProfileField("Username",u,{u=it},Icons.Default.AccountCircle);ProfileField("Email",e,{e=it},Icons.Default.Email);ProfileField("Description",d,{d=it},Icons.Default.Info);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({editing=false}){Text("Cancel")};Button({onName(n);onUsername(u);onEmail(e);onDescription(d);editing=false},colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Save")}}}}}else{Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileInfo(Icons.Default.AccountCircle,"Username",username.trim().takeIf { it.isNotEmpty() }?.let { "@$it" } ?: "Not set");ProfileInfo(Icons.Default.Email,"Email",email.trim().ifBlank { "Not set" });ProfileInfo(Icons.Default.Info,"Description",description.trim().ifBlank { "Not set" });Spacer(Modifier.height(5.dp));Button({editing=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(7.dp));Text("Edit Profile")}}}}};item{RowItem(Icons.Default.PhotoCamera,"Profile Picture","Change your profile photo",pick)};item{RowItem(Icons.Default.AccountBalanceWallet,"Practice Portfolio","Practice with virtual money"){go(Page.PAPER)}};item{RowItem(Icons.Default.Settings,"Settings","Theme, notifications, data and privacy"){go(Page.SETTINGS)}};item{RowItem(Icons.Default.AccountCircle,"Account & Sign in","Profile, sign-in and future sync"){go(Page.ACCOUNT)}};item{RowItem(Icons.Default.PrivacyTip,"Privacy","Review your privacy settings"){go(Page.PRIVACY)}};item{Note("NSE Watcher provides market information and analysis. It does not execute trades or guarantee returns.")}}}
@Composable private fun ProfileHero(name:String,username:String,pick:()->Unit){val c=androidx.compose.ui.platform.LocalContext.current;val u=c.getSharedPreferences(PREFS,0).getString("avatar_uri",null);val b by produceState<Bitmap?>(null,u){value=try{u?.let{c.contentResolver.openInputStream(Uri.parse(it))?.use{stream->BitmapFactory.decodeStream(stream)}}}catch(_:Exception){null}};Card(Modifier.fillMaxWidth(),RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(100.dp)){Surface(Modifier.fillMaxSize(),CircleShape,Color.White){if(b!=null)Image(b!!.asImageBitmap(),"Profile picture",Modifier.fillMaxSize())else Icon(Icons.Default.Person,null,tint=Green,modifier=Modifier.padding(25.dp))};Surface(Modifier.size(32.dp).align(Alignment.BottomEnd).clickable(onClick=pick),CircleShape,Green){Icon(Icons.Default.PhotoCamera,"Change profile picture",modifier=Modifier.padding(7.dp),tint=Color.White)}};Spacer(Modifier.height(10.dp));Text(name.trim().ifBlank { ProfileDefaults.displayName },fontSize=21.sp,fontWeight=FontWeight.ExtraBold);Text(username.trim().takeIf { it.isNotEmpty() }?.let { "@$it" } ?: "Complete your profile",fontSize=11.sp,color=Muted)}}}
@Composable private fun ProfileField(label:String,value:String,onValue:(String)->Unit,i:ImageVector){OutlinedTextField(value,onValue,Modifier.fillMaxWidth().padding(bottom=8.dp),label={Text(label)},leadingIcon={Icon(i,null,tint=Green)},singleLine=label!="Description")}
@Composable private fun ProfileInfo(i:ImageVector,label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(38.dp),CircleShape,LightGreen){Icon(i,null,tint=Green,modifier=Modifier.padding(9.dp))};Spacer(Modifier.width(11.dp));Column{Text(label,fontSize=10.sp,color=Muted);Text(value,fontSize=13.sp,fontWeight=FontWeight.Bold)}}}

@Composable private fun SettingsCard(title:String,icon:ImageVector,content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(34.dp),CircleShape,LightGreen){Icon(icon,null,tint=Green,modifier=Modifier.padding(8.dp))};Spacer(Modifier.width(9.dp));Text(title,fontWeight=FontWeight.ExtraBold,fontSize=15.sp)};Spacer(Modifier.height(4.dp));content()}}}
@Composable private fun ToggleRow(icon:ImageVector,title:String,sub:String,checked:Boolean,onChecked:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(21.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Switch(checked,onChecked)}}
@Composable private fun RowItem(icon:ImageVector,title:String,sub:String,onClick:()->Unit={}){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(22.dp));Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(19.dp))}}
@Composable private fun NotificationsPage(m:Boolean,p:Boolean,n:Boolean,a:Boolean,sm:(Boolean)->Unit,sp:(Boolean)->Unit,sn:(Boolean)->Unit,sa:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Notifications","Choose what you want to hear about",back)};item{SettingsCard("Notification Types",Icons.Default.Notifications){ToggleRow(Icons.Default.ShowChart,"Market updates","NSE-wide movements and daily briefs",m,sm);ToggleRow(Icons.Default.PriceChange,"Price alerts","Your watchlist thresholds",p,sp);ToggleRow(Icons.Default.Article,"News alerts","Company and market news",n,sn);ToggleRow(Icons.Default.Apps,"App notifications","Product updates",a,sa)}};item{Note("Price, news, and corporate-action alerts are monitored from provider-backed market data. Background checks are scheduled about every 15 minutes; Android may delay them. News checks run outside market hours and catch up on available stories from the last 7 days. Price alerts require an open market and quotes no more than 30 minutes old. Daily gain, loss and volume alerts notify once per rule per Nairobi day.")}}}
@Composable
private fun AlertPage(back:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val store=remember{AlertStore(context)}
    val watchlistStore=remember{WatchlistStore(context)}
    val alerts by store.alerts.collectAsState(initial=emptyList())
    val watchedSymbols by watchlistStore.symbols.collectAsState(initial=emptyList())
    LaunchedEffect(Unit) {
        if (MarketRefreshController.shouldRefreshQuotes(stocks.isNotEmpty())) {
            MarketData.stocks().takeIf { it.isNotEmpty() }?.let { liveStocks.value = it }
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

@Composable private fun HelpPage(back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Help & Support","Get help using NSE Watcher",back)};item{SettingsCard("Support",Icons.Default.HelpOutline){RowItem(Icons.Default.MenuBook,"Getting started","Learn how to read the market dashboard");RowItem(Icons.Default.QuestionMark,"Frequently asked questions","Common NSE Watcher questions");RowItem(Icons.Default.ReportProblem,"Report a problem","Tell us about an issue")}}}}
@Composable private fun AboutPage(back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("About NSE Watcher","Market intelligence for the NSE",back)};item{SettingsCard("NSE Watcher",Icons.Default.Info){Text("Version 0.1.0",fontWeight=FontWeight.Bold);Text("Trading apps help you buy. NSE Watcher helps you understand what you're buying.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=7.dp));Spacer(Modifier.height(9.dp));Text("NSE Watcher does not execute real trades and does not guarantee investment returns.",fontSize=10.sp,color=Muted)
            Spacer(Modifier.height(9.dp))
            Text("Nairobi skyline photo: Antony Trivet • Creative Commons BY-SA 4.0",fontSize=9.sp,color=Muted)
            Text("Source: commons.wikimedia.org/wiki/File:Nairobi_City_County_Skyline.jpg",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
            Text("License: creativecommons.org/licenses/by-sa/4.0/ • Image bundled with the app; crop/overlay applied.",fontSize=8.sp,color=Muted,modifier=Modifier.padding(top=2.dp))
        }}}}
@Composable private fun Note(text:String){Card(Modifier.fillMaxWidth(),RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null,tint=Green);Spacer(Modifier.width(9.dp));Text(text,fontSize=9.sp,color=Muted)}}}






