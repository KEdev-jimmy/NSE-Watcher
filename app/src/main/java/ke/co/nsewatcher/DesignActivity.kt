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
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.flow.first
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


data class Stock(val symbol:String,val name:String,val price:Double,val change:Double,val history:List<Double>,val logoUrl:String?=null,val sector:String="Other",val volume:Long=0L,val changeAvailable:Boolean=true,val volumeAvailable:Boolean=true,val source:String="",val observedAt:String="",val freshnessMode:String="UNKNOWN",val dataOrigin:String="unknown",val averageVolume:Long=0L,val averageVolumeAvailable:Boolean=false,val previousClose:Double?=null,val delayMinutes:Int?=null)

data class NewsItem(
    val id:String, val title:String, val summary:String, val body:String, val source:String,
    val publishedAt:String, val category:String, val symbol:String, val companyName:String,
    val imageUrl:String, val url:String, val dividendAmount:String, val exDate:String, val paymentDate:String,
    val intelligenceRelevance:String = "unknown", val intelligenceRelevanceReason:String = "", val freshnessMode:String = "UNKNOWN"
)
private val liveStocks = mutableStateOf(emptyList<Stock>())
private val stocks: List<Stock> get() = liveStocks.value

private enum class Page { HOME, MARKET, NEWS, COMPANIES, PAPER, MORE, COMPANY, WATCHLIST, COMPARE, NEWS_DETAIL, PROFILE, SETTINGS, ACCOUNT, AUTH_CREATE, AUTH_SIGNIN, THEME, NOTIFICATIONS, LIVE_DATA, CHARTS, ALERTS, LANGUAGE, SECURITY, PRIVACY, DISPLAY, HELP, ABOUT }

class DesignActivity : ComponentActivity() {
    private var alertDestination by mutableStateOf<AlertDestination?>(null)
    private var practiceDestination by mutableStateOf<PracticeNotificationDestination?>(null)
    private var avatarRevision by mutableIntStateOf(0)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("avatar_uri", uri.toString()).apply()
        avatarRevision++
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
                avatarRevision = avatarRevision,
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
    avatarRevision: Int,
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
    var offlineStartupSources by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) {
        val snapshotStore = StartupSnapshotStore(context)
        val offline = snapshotStore.loadFallback()

        val startup = StartupDataLoader(
            stocks = MarketData::stocks,
            news = { MarketData.newsFeed() },
            companies = MarketData::companies,
            status = MarketData::status
        ).load()

        val unavailable = startup.failedSources + startup.timedOutSources
        offlineStartupSources = StartupSnapshotPolicy.usedSources(startup, offline)

        liveStocks.value = when {
            startup.stocks.isNotEmpty() -> startup.stocks
            offline.stocks.isNotEmpty() -> offline.stocks
            else -> liveStocks.value
        }

        if ("news" !in unavailable) {
            newsFeed = startup.news
        } else if (offline.news.isNotEmpty()) {
            newsFeed = offline.news
        }

        companyCatalog = when {
            startup.companies.isNotEmpty() -> startup.companies
            offline.companies.isNotEmpty() -> offline.companies
            else -> companyCatalog
        }

        // Persisted status is deliberately never restored. UNKNOWN is safer than
        // authorizing alerts or Practice fills from an old OPEN/CLOSED state.
        if (startup.marketStatus.isKnown) {
            startupMarketStatus = startup.marketStatus
        }

        snapshotStore.saveSuccessfulSources(startup)

        startupComplete = startup.completed
        startupReady = true

        if (startup.marketStatus.isKnown) {
            val indices = withTimeoutOrNull(4_000L) {
                runCatching {
                    MarketData.indices(startup.marketStatus.isOpen)
                }.getOrDefault(emptyList())
            }.orEmpty()
            if (indices.isNotEmpty()) {
                marketIndices = indices
            }
        }
    }

    var defaultView by rememberSaveable { mutableStateOf(prefs.getString("default_view", "Home") ?: "Home") }
    val initialPage = remember {
        when (prefs.getString("default_view", "Home") ?: "Home") {
            "Market" -> Page.MARKET
            "News" -> Page.NEWS
            "Companies" -> Page.COMPANIES
            "Practice" -> Page.PAPER
            else -> Page.HOME
        }
    }
    var page by remember { mutableStateOf(initialPage) }
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
    val avatarUri = remember(avatarRevision) { prefs.getString("avatar_uri", null) }
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
            MarketData.companies().takeIf { it.isNotEmpty() }?.let {
                companyCatalog = it
                offlineStartupSources = offlineStartupSources - "companies"
            }
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

            if (refreshedStatus.isKnown) {
                startupMarketStatus = refreshedStatus
            }

            val quoteRefreshDue = MarketRefreshController.shouldRefreshQuotes(stocks.isNotEmpty())
            val openingRefresh = becameOpen && !MarketRefreshController.state.value.refreshInProgress

            if (refreshedStatus.isKnown && (marketStateChanged || quoteRefreshDue)) {
                MarketData.indices(refreshedStatus.isOpen)
                    .takeIf { it.isNotEmpty() }
                    ?.let { marketIndices = it }
            }

            if (openingRefresh || quoteRefreshDue) {
                MarketData.stocks().takeIf { it.isNotEmpty() }?.let { refreshed ->
                    liveStocks.value = refreshed
                    offlineStartupSources = offlineStartupSources - "stocks"
                    // Use the latest selected company without restarting the status timer
                    // when navigation changes the selection.
                    if (latestSelected.symbol.isNotBlank()) {
                        refreshed.firstOrNull { it.symbol == latestSelected.symbol }?.let { selected = it }
                    }
                }
            }

            if (refreshedStatus.isKnown) {
                previousMarketKnown = true
                previousMarketOpen = refreshedStatus.isOpen
            }
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
            Page.HOME,Page.MARKET,Page.NEWS,Page.COMPANIES,Page.PAPER -> Scaffold(topBar={if(page!=Page.PAPER && page!=Page.HOME && page!=Page.MARKET && page!=Page.NEWS && page!=Page.COMPANIES) TopBar(name,::go)},bottomBar={BottomNav(when(page){Page.HOME->0;Page.MARKET->1;Page.NEWS->2;Page.COMPANIES->3;else->4}){tab=it;if(it==3)directorySector="All";history=emptyList();page=when(it){0->Page.HOME;1->Page.MARKET;2->Page.NEWS;3->Page.COMPANIES;else->Page.PAPER}}}){pad->Box(Modifier.fillMaxSize().padding(pad)){when(page){Page.HOME->HomeDashboard(stocks,{selected=it;go(Page.COMPANY)},{selectedNews=it;go(Page.NEWS_DETAIL)},{go(Page.MARKET)},{directorySector="All";go(Page.COMPANIES)},{go(Page.WATCHLIST)},newsFeed,marketIndices,startupMarketStatus,startupComplete,offlineSnapshotSources=offlineStartupSources,name=name,initialCatalog=companyCatalog,practiceEnabled=PaperPortfolioStore.isEnabled(context),practiceCash=PaperPortfolioStore.cash(context),darkTheme=dark,openAllNews={go(Page.NEWS)},openPractice={launchPractice()},openPracticeReview={launchPracticeReview(it)},openProfile={go(Page.PROFILE)},openAlertSettings={go(Page.NOTIFICATIONS)},onQuotesLoaded={liveStocks.value=it;offlineStartupSources=offlineStartupSources-"stocks"},onNewsLoaded={newsFeed=it;offlineStartupSources=offlineStartupSources-"news"},onIndicesLoaded={marketIndices=it},onMarketStatusLoaded={startupMarketStatus=it});Page.MARKET->directoryState.SaveableStateProvider("market"){MarketDashboard(stocks,companyCatalog,startupMarketStatus,marketIndices,newsFeed,openCompany={selected=it;go(Page.COMPANY)},openCompanies={directorySector=it;go(Page.COMPANIES)},openNews={selectedNews=it;go(Page.NEWS_DETAIL)},openSearch={directorySector="All";go(Page.COMPANIES)},openAlerts={go(Page.NOTIFICATIONS)},onQuotesLoaded={liveStocks.value=it;offlineStartupSources=offlineStartupSources-"stocks"},onCatalogLoaded={companyCatalog=it;offlineStartupSources=offlineStartupSources-"companies"},onIndicesLoaded={marketIndices=it},onMarketStatusLoaded={startupMarketStatus=it})};Page.NEWS->directoryState.SaveableStateProvider("news"){NewsDashboard(newsFeed=newsFeed,catalog=companyCatalog,quotes=stocks,offlineSnapshot=offlineStartupSources.contains("news"),onNewsLoaded={newsFeed=it;offlineStartupSources=offlineStartupSources-"news"},openAlerts={go(Page.NOTIFICATIONS)}){selectedNews=it;go(Page.NEWS_DETAIL)}};Page.COMPANIES->directoryState.SaveableStateProvider("companies:$directorySector"){CompaniesDirectory(catalog=companyCatalog,quotes=stocks,name=name,newsFeed=newsFeed,offlineCatalog=offlineStartupSources.contains("companies"),offlineQuotes=offlineStartupSources.contains("stocks"),initialSector=directorySector,openCompany={selected=it;go(Page.COMPANY)},openWatchlist={go(Page.WATCHLIST)},openCompare={comparisonSymbols=it;go(Page.COMPARE)},openNews={selectedNews=it;go(Page.NEWS_DETAIL)},openProfile={go(Page.PROFILE)},onCatalogLoaded={companyCatalog=it;offlineStartupSources=offlineStartupSources-"companies"},onQuotesLoaded={liveStocks.value=it;offlineStartupSources=offlineStartupSources-"stocks"},onNewsLoaded={newsFeed=it;offlineStartupSources=offlineStartupSources-"news"})};Page.PAPER->directoryState.SaveableStateProvider("practice"){PracticePortfolioScreen(quoteFeed=stocks,catalog=companyCatalog,initialMarket=startupMarketStatus,news=newsFeed,initialSymbol=practiceSymbol,initialReviewOrderId=practiceReviewOrderId,launchRevision=practiceLaunchRevision,launchSource=practiceLaunchSource,onQuotes={liveStocks.value=it;offlineStartupSources=offlineStartupSources-"stocks"},onCatalog={companyCatalog=it;offlineStartupSources=offlineStartupSources-"companies"},openCompany={selected=it;go(Page.COMPANY)},openNews={selectedNews=it;go(Page.NEWS_DETAIL)},back=::back)};else->MoreHubScreen(name=name,username=username,email=email,back=::back,openProfile={go(Page.PROFILE)},openPractice={launchPractice()},openWatchlist={go(Page.WATCHLIST)},openAlerts={go(Page.ALERTS)},openCompare={comparisonSymbols=emptyList();go(Page.COMPARE)},openSettings={go(Page.SETTINGS)},openNotifications={go(Page.NOTIFICATIONS)},openMarketData={go(Page.LIVE_DATA)},openAppearance={go(Page.DISPLAY)},openHelp={go(Page.HELP)},openAbout={go(Page.ABOUT)})}}}
            Page.COMPANY->Company(
                s=selected,
                sharedNews=newsFeed,
                marketStatus=startupMarketStatus,
                onNewsLoaded={newsFeed=it;offlineStartupSources=offlineStartupSources-"news"},
                onMarketStatusLoaded={startupMarketStatus=it},
                back=::back,
                openPractice={launchPractice(selected.symbol,"Company Intelligence")}
            ){selectedNews=it;go(Page.NEWS_DETAIL)}
            Page.WATCHLIST->WatchlistDashboard(quoteStocks=stocks, initialCatalog=companyCatalog, initialMarket=startupMarketStatus, sharedNews=newsFeed, onQuotesLoaded={liveStocks.value=it;offlineStartupSources=offlineStartupSources-"stocks"}, onNewsLoaded={newsFeed=it;offlineStartupSources=offlineStartupSources-"news"}, openCompany={selected=it;go(Page.COMPANY)}, openNews={selectedNews=it;go(Page.NEWS_DETAIL)}, openPreferences={go(Page.NOTIFICATIONS)}, back=::back)
            Page.COMPARE->CompanyComparison(CompaniesPresentation.companies(companyCatalog, stocks),::back,comparisonSymbols)
            Page.NEWS_DETAIL->key(alertNavigationRevision) { selectedNews?.let { NewsArticleScreen(it,companyCatalog,stocks,::back){company->selected=company;go(Page.COMPANY)} } }
            Page.PROFILE->DesignedProfileScreen(
                name=name,
                username=username,
                email=email,
                avatarUri=avatarUri,
                onName={name=it;put("profile_name",it)},
                onUsername={username=it;put("username",it)},
                onEmail={email=it;put("email",it)},
                pickAvatar=pickAvatar,
                back=::back,
                openAccount={go(Page.ACCOUNT)},
                openPractice={launchPractice()},
                openAlerts={go(Page.ALERTS)},
                openWatchlist={go(Page.WATCHLIST)},
                openSettings={go(Page.SETTINGS)},
                openHelp={go(Page.HELP)}
            )
            Page.MORE->MoreHubScreen(name=name,username=username,email=email,back=::back,openProfile={go(Page.PROFILE)},openPractice={launchPractice()},openWatchlist={go(Page.WATCHLIST)},openAlerts={go(Page.ALERTS)},openCompare={comparisonSymbols=emptyList();go(Page.COMPARE)},openSettings={go(Page.SETTINGS)},openNotifications={go(Page.NOTIFICATIONS)},openMarketData={go(Page.LIVE_DATA)},openAppearance={go(Page.DISPLAY)},openHelp={go(Page.HELP)},openAbout={go(Page.ABOUT)})
            Page.SETTINGS->DesignedSettingsScreen(
                back=::back,
                openProfile={go(Page.PROFILE)},
                openAccount={go(Page.ACCOUNT)},
                priceAlerts=priceAlerts,
                marketAlerts=marketAlerts,
                newsAndCompanyAlerts=watchlistNewsAlerts && watchlistCorporateAlerts,
                onPriceAlerts={priceAlerts=it;put("price_alerts",it)},
                onMarketAlerts={marketAlerts=it;put("market_alerts",it)},
                onNewsAndCompanyAlerts={enabled->
                    watchlistNewsAlerts=enabled
                    watchlistCorporateAlerts=enabled
                    prefs.edit()
                        .putBoolean("watchlist_news_alerts",enabled)
                        .putBoolean("watchlist_corporate_alerts",enabled)
                        .apply()
                    if(enabled){
                        val now=System.currentTimeMillis()
                        prefs.edit()
                            .putLong("watchlist_news_enabled_at",now)
                            .putLong("watchlist_corporate_enabled_at",now)
                            .apply()
                    }
                },
                darkTheme=dark,
                defaultView=defaultView,
                onDefaultView={defaultView=it;put("default_view",it)},
                openAppearance={go(Page.DISPLAY)},
                openMarketData={go(Page.LIVE_DATA)},
                openPrivacy={go(Page.PRIVACY)},
                openAbout={go(Page.ABOUT)}
            )
            Page.ACCOUNT->AuthLandingScreen(
                back=::back,
                openCreateAccount={go(Page.AUTH_CREATE)},
                openSignIn={go(Page.AUTH_SIGNIN)},
                continueAsGuest=::back
            )
            Page.AUTH_CREATE->CreateAccountScreen(name,::back){go(Page.AUTH_SIGNIN)}
            Page.AUTH_SIGNIN->SignInScreen(::back){go(Page.AUTH_CREATE)}
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
                openAlertRules={go(Page.ALERTS)},
                openAlertEvent={event->
                    val target=AlertDestination.from(event)
                    val article=target.article()
                    if(article!=null){
                        alertNavigationRevision++
                        selectedNews=article
                        go(Page.NEWS_DETAIL)
                    }else{
                        selected=target.company(CompaniesPresentation.companies(companyCatalog,stocks))
                        go(Page.COMPANY)
                    }
                },
                back=::back
            )
            Page.LIVE_DATA->MarketDataSettingsScreen(autoRefresh,{autoRefresh=it;put("auto_refresh",it)},::back)
            Page.CHARTS->ChartSettingsScreen(chartDefaultRange,chartShowGrid,{chartDefaultRange=it;put("chart_default_range",it)},{chartShowGrid=it;put("chart_show_grid",it)},::back)
            Page.ALERTS->PremiumAlertRulesScreen(
                catalog=companyCatalog,
                quotes=stocks,
                back=::back
            )
            Page.LANGUAGE->LanguageRegionScreen(::back)
            Page.SECURITY->AuthLandingScreen(::back,{go(Page.AUTH_CREATE)},{go(Page.AUTH_SIGNIN)},::back)
            Page.PRIVACY->PrivacyDataScreen(::back)
            Page.DISPLAY->DisplayAppearanceScreen(dark,fontSizeSetting,{dark=it;put("dark_mode",it)},{fontSizeSetting=it;put("font_size",it)},::back)
            Page.HELP->HelpSupportExperienceScreen(::back)
            Page.ABOUT->AboutNseWatcherExperienceScreen(::back)
            else->{page=Page.HOME}
        }
    }}
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
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Home" to Icons.Default.Home,
        "Market" to Icons.Default.CandlestickChart,
        "News" to Icons.Default.Article,
        "Companies" to Icons.Default.Business,
        "Practice" to Icons.Default.School
    )
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = premiumHomePalette(dark)

    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        val compact = shouldCompactBottomNav(
            widthDp = maxWidth.value,
            fontScale = LocalDensity.current.fontScale,
            itemCount = items.size
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = palette.nav,
            border = BorderStroke(1.dp, palette.border),
            tonalElevation = 0.dp,
            shadowElevation = 7.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 64.dp else 68.dp)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = selected == index
                    val selectedModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(23.dp))
                        .background(
                            if (isSelected) {
                                palette.primary.copy(alpha = if (dark) 0.13f else 0.10f)
                            } else {
                                Color.Transparent
                            }
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
                        if (!compact || isSelected) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.first,
                                color = if (isSelected) palette.primary else palette.muted,
                                fontSize = if (compact) 9.sp else 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected && !compact) {
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
}
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
                val alertStore = AlertStore(context)
                if (watched) {
                    val paused = alertStore.alerts.first().filter {
                        it.symbol.equals(s.symbol, true) && it.enabled
                    }
                    paused.forEach { alertStore.setEnabled(it.id, false) }
                    watchlistStore.remove(s.symbol)
                } else {
                    watchlistStore.add(s.symbol)
                }
            }
        },
        openPractice = openPractice
    )
}

private object PaperPortfolioStore {
    private fun prefs(context: Context) = context.getSharedPreferences("nse_watcher_paper_portfolio", Context.MODE_PRIVATE)
    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun cash(context: Context): Double {
        val raw = prefs(context).getString("practice_v2", null)
        return if (raw == null) prefs(context).getFloat("cash", 0f).toDouble()
            else runCatching { JSONObject(raw).getDouble("cash") }.getOrDefault(0.0)
    }
}
