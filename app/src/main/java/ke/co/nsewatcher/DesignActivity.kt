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
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("avatar_uri", uri.toString()).apply()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AlertWorker.schedule(this)
        setContent { App { picker.launch(arrayOf("image/*")) } }
    }
}

@Composable
private fun App(pickAvatar:()->Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var showOpeningScreen by rememberSaveable { mutableStateOf(true) }
    var startupReady by remember { mutableStateOf(false) }
    var startupComplete by remember { mutableStateOf(false) }
    var startupNews by remember { mutableStateOf(emptyList<NewsItem>()) }
    var startupMarketStatus by remember { mutableStateOf(MyStocksCache.MarketStatus()) }

    LaunchedEffect(Unit) {
        val completed = withTimeoutOrNull(12_000L) {
            coroutineScope {
                val stocksDeferred = async {
                    runCatching { MyStocksCache.loadStocks() }.getOrDefault(emptyList())
                }
                val newsDeferred = async {
                    runCatching { NewsCache.loadFeed() }.getOrDefault(emptyList())
                }
                val statusDeferred = async {
                    runCatching { MyStocksCache.loadMarketStatus() }.getOrDefault(MyStocksCache.MarketStatus())
                }

                val loadedStocks = stocksDeferred.await()
                if (loadedStocks.isNotEmpty()) {
                    liveStocks.value = loadedStocks
                }
                startupNews = newsDeferred.await()
                startupMarketStatus = statusDeferred.await()
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
    LaunchedEffect(autoRefresh) {
        if (!autoRefresh) return@LaunchedEffect
        var lastStockRefreshMs = System.currentTimeMillis()
        var previousMarketOpen = startupMarketStatus.isKnown && startupMarketStatus.isOpen

        while (isActive) {
            delay(MarketRefreshController.STATUS_POLL_INTERVAL_MS)

            // Status is real-time and must not wait for the 15-minute quote cadence.
            // This lets an app that is already open recognize the Nairobi session
            // transition around 09:30 EAT even when the user is physically abroad.
            val refreshedStatus = MyStocksCache.loadMarketStatus()
            val becameOpen = refreshedStatus.isKnown && refreshedStatus.isOpen && !previousMarketOpen
            startupMarketStatus = refreshedStatus

            val now = System.currentTimeMillis()
            val quoteRefreshDue =
                now - lastStockRefreshMs >= MarketRefreshController.REFRESH_INTERVAL_MS

            if (becameOpen || quoteRefreshDue) {
                MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { refreshed ->
                    liveStocks.value = refreshed
                    lastStockRefreshMs = now
                    // Use the latest selected company without restarting the status timer
                    // when navigation changes the selection.
                    if (latestSelected.symbol.isNotBlank()) {
                        refreshed.firstOrNull { it.symbol == latestSelected.symbol }?.let { selected = it }
                    }
                }
            }

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
    val scheme=if(dark) darkColorScheme(primary=Color(0xFF32D486),background=Color(0xFF0D1712),surface=Color(0xFF132019),onSurface=Color.White,onBackground=Color.White,onSurfaceVariant=Color(0xFFB7C7BE)) else lightColorScheme(primary=Green,background=Color.White,surface=Color.White,onSurface=TextDark,onBackground=TextDark,onSurfaceVariant=Muted)
    MaterialTheme(colorScheme=scheme){Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=scheme.background){
        when(page){
            Page.HOME,Page.MARKET,Page.NEWS,Page.COMPANIES,Page.PAPER,Page.MORE -> Scaffold(topBar={if(page!=Page.HOME && page!=Page.MARKET) TopBar(name,::go)},bottomBar={BottomNav(tab){tab=it;history=emptyList();page=when(it){0->Page.HOME;1->Page.MARKET;2->Page.NEWS;3->Page.COMPANIES;else->Page.MORE}}}){pad->Box(Modifier.fillMaxSize().padding(pad)){when(page){Page.HOME->HomeDashboard(stocks,{selected=it;go(Page.COMPANY)},{selectedNews=it;go(Page.NEWS_DETAIL)},{go(Page.MARKET)},{go(Page.WATCHLIST)},startupNews,startupMarketStatus,startupComplete);Page.MARKET->MarketDashboard(stocks);Page.NEWS->NewsDashboard{selectedNews=it;go(Page.NEWS_DETAIL)};Page.COMPANIES->Companies({selected=it;go(Page.COMPANY)},{go(Page.WATCHLIST)},{go(Page.COMPARE)});Page.PAPER->Paper();else->More(::go)}}}
            Page.COMPANY->Company(selected,::back)
            Page.WATCHLIST->Watchlist({selected=it;go(Page.COMPANY)},::back)
            Page.COMPARE->CompanyComparison(stocks,::back)
            Page.NEWS_DETAIL->selectedNews?.let { NewsDetail(it,::back) }
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
@Composable private fun BottomNav(selected:Int,onSelect:(Int)->Unit){val items=listOf("Home" to Icons.Default.Home,"Market" to Icons.Default.CandlestickChart,"News" to Icons.Default.Article,"Companies" to Icons.Default.Business,"More" to Icons.Default.AutoGraph);NavigationBar{items.forEachIndexed{i,x->NavigationBarItem(selected==i,{onSelect(i)},icon={Icon(x.second,x.first)},label={Text(x.first,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Green,selectedTextColor=Green,indicatorColor=LightGreen,unselectedIconColor=Muted,unselectedTextColor=Muted))}}}
@Composable private fun Header(title:String,sub:String?=null,back:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)IconButton(back){Icon(Icons.Default.ArrowBack,"Back")};Column{Text(title,fontSize=20.sp,fontWeight=FontWeight.ExtraBold);if(sub!=null)Text(sub,fontSize=10.sp,color=Muted)}}}

@Composable
private fun Companies(open:(Stock)->Unit, openWatchlist:()->Unit, openCompare:()->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    val filtered=stocks.filter{it.name.contains(query,true)||it.symbol.contains(query,true)}
    Box(Modifier.fillMaxSize().background(Color(0xFF062A23))){
        LazyColumn(contentPadding=PaddingValues(16.dp,0.dp,16.dp,20.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
            item{
                Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(bottomStart=26.dp,bottomEnd=26.dp))){
                    Box(Modifier.fillMaxSize().background(Color(0x55052B24)))
                    Column(Modifier.fillMaxSize().padding(10.dp,20.dp,10.dp,18.dp),verticalArrangement=Arrangement.Bottom){Text("Discover",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Text("Great Companies",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(5.dp));Text("Research. Analyze. Understand.\\nExplore sourced NSE company information.",color=Color.White,fontSize=11.sp)}
                }
            }
            item{CompanyDataCoverage(stocks)}
            item{OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search companies...",color=Muted)},leadingIcon={Icon(Icons.Default.Search,null,tint=Muted)},shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White,unfocusedBorderColor=Color.Transparent,focusedBorderColor=Green,unfocusedTextColor=TextDark,focusedTextColor=TextDark))}
            item{
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("Companies",color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=17.sp)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick=openCompare,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFF55E0A0)),contentPadding=PaddingValues(horizontal=11.dp,vertical=4.dp)){
                        Icon(Icons.Default.StarBorder,null,tint=Color(0xFF55E0A0),modifier=Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text("Compare",color=Color(0xFF55E0A0),fontSize=10.sp,fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick=openWatchlist,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFF55E0A0)),contentPadding=PaddingValues(horizontal=11.dp,vertical=4.dp)){
                        Icon(Icons.Default.StarBorder,null,tint=Color(0xFF55E0A0),modifier=Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text("Watchlist",color=Color(0xFF55E0A0),fontSize=10.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
            item{
                Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Column(Modifier.padding(horizontal=12.dp)){
                        filtered.forEachIndexed{index,s->
                            Row(Modifier.fillMaxWidth().clickable{open(s)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                                Logo(s.symbol,40,s.logoUrl);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.name,color=TextDark,fontWeight=FontWeight.ExtraBold,fontSize=12.sp);Text(s.symbol,color=Muted,fontSize=10.sp)};Column(horizontalAlignment=Alignment.End){Text(String.format(Locale.US,"KSh %.2f",s.price),color=TextDark,fontWeight=FontWeight.Bold,fontSize=11.sp);if(s.changeAvailable){Text(String.format(Locale.US,"%+.1f%%",s.change),color=if(s.change>=0)Green else Red,fontWeight=FontWeight.Bold,fontSize=10.sp)}else{Text("Daily change unavailable",color=Muted,fontWeight=FontWeight.Bold,fontSize=9.sp)}}
                            }
                            if(index<filtered.lastIndex)HorizontalDivider(color=Border)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun Watchlist(open: (Stock) -> Unit, back: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val watchlistStore = remember { WatchlistStore(context) }
    LaunchedEffect(Unit) {
        MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { liveStocks.value = it }
    }
    val watchedSymbols by watchlistStore.symbols.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header("Watchlist", "Companies you explicitly chose to follow", back) }
        item {
            Text("Prices and daily changes are from MyStocks Africa and may be delayed. Verify material announcements with the issuer or NSE.", color = Muted, fontSize = 9.sp)
        }
        if (watchedSymbols.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(Modifier.size(58.dp), CircleShape, LightGreen) {
                            Icon(Icons.Default.StarBorder, null, tint = Green, modifier = Modifier.padding(15.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Your watchlist is empty", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Open a company and tap Watch to add it here. Nothing is added automatically.", color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            item {
                Text("${watchedSymbols.size} ${if (watchedSymbols.size == 1) "company" else "companies"} watched", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            items(watchedSymbols) { symbol ->
                val stock = stocks.firstOrNull { it.symbol.trim().uppercase() == symbol.trim().uppercase() }
                if (stock != null) {
                    Card(Modifier.fillMaxWidth().clickable { open(stock) }, RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Logo(stock.symbol, 42, stock.logoUrl)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stock.name, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                Text(stock.symbol, color = Muted, fontSize = 9.sp)
                                Text(String.format(Locale.US, "KSh %.2f", stock.price), color = TextDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (stock.changeAvailable) {
                                    Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) Green else Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                } else {
                                    Text("Daily change unavailable", color = Muted, fontSize = 9.sp)
                                }
                                TextButton(onClick = { scope.launch { watchlistStore.remove(stock.symbol) } }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                    Text("Remove", color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(42.dp), CircleShape, LightGreen) {
                                Icon(Icons.Default.HelpOutline, null, tint = Muted, modifier = Modifier.padding(11.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(symbol, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                Text("Current market data unavailable", color = Muted, fontSize = 9.sp)
                            }
                            TextButton(onClick = { scope.launch { watchlistStore.remove(symbol) } }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("Remove", color = Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyDataCoverage(stocks: List<Stock>) {
    val valid = stocks.count { it.price.isFinite() && it.price > 0.0 }
    val sourced = stocks.count { it.source.isNotBlank() }
    val freshness = when {
        stocks.any { it.freshnessMode == "CURRENT_SESSION" } -> "Current session"
        stocks.any { it.freshnessMode == "END_OF_DAY" } -> "End-of-day"
        stocks.any { it.freshnessMode == "STALE" } -> "Previous session"
        else -> "Freshness unknown"
    }
    Card(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Verified, null, tint = Green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Data coverage", color = TextDark, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("$valid valid quotes", color = Muted, fontSize = 8.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "$sourced/${stocks.size} quotes have a recorded source • $freshness",
                color = Muted,
                fontSize = 8.sp
            )
            Text("Prices may be delayed. Missing values are not estimated.", color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun Company(s: Stock, back: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val watchlistStore = remember { WatchlistStore(context) }
    val watchedSymbols by watchlistStore.symbols.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val watched = watchedSymbols.contains(s.symbol.trim().uppercase())

    CompanyIntelligence(
        s = s,
        back = back,
        watched = watched,
        onWatchToggle = {
            scope.launch {
                if (watched) watchlistStore.remove(s.symbol)
                else watchlistStore.add(s.symbol)
            }
        }
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

@Composable private fun NewsDetail(item:NewsItem,back:()->Unit){
    var detail by remember(item.id){mutableStateOf(item)}
    var loading by remember(item.id){mutableStateOf(true)}
    LaunchedEffect(item.id){NewsCache.loadDetail(item.id)?.let{detail=it};loading=false}
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Header("News Details",detail.companyName.ifBlank{detail.source},back)}
        item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(19.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){if(detail.symbol.isNotBlank())Logo(detail.symbol,46);Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(if(detail.companyName.isNotBlank())detail.companyName else detail.source,fontWeight=FontWeight.Bold,fontSize=12.sp);Text(if(detail.symbol.isNotBlank())detail.symbol else "NSE",color=Muted,fontSize=9.sp)}};Spacer(Modifier.height(12.dp));NewsChip(detail.category,false);Spacer(Modifier.height(8.dp));Text(detail.title,fontWeight=FontWeight.ExtraBold,fontSize=21.sp);Spacer(Modifier.height(7.dp));Text("${newsDate(detail.publishedAt)} • ${detail.source}",color=Muted,fontSize=9.sp)}}}
        item{if(loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Green)}
        if(detail.summary.isNotBlank()) item{Text(detail.summary,fontWeight=FontWeight.SemiBold,fontSize=13.sp,color=TextDark)}
        if(detail.body.isNotBlank()) item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Text(detail.body,Modifier.padding(15.dp),fontSize=12.sp,lineHeight=18.sp)}}
        if(detail.dividendAmount.isNotBlank()||detail.exDate.isNotBlank()||detail.paymentDate.isNotBlank()) item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Column(Modifier.padding(14.dp)){Text("Dividend details",fontWeight=FontWeight.ExtraBold,fontSize=16.sp);if(detail.dividendAmount.isNotBlank())InfoRow(Icons.Default.Payments,"Dividend amount",detail.dividendAmount);if(detail.exDate.isNotBlank())InfoRow(Icons.Default.Event,"Book closure / ex-date",detail.exDate);if(detail.paymentDate.isNotBlank())InfoRow(Icons.Default.CalendarMonth,"Payment date",detail.paymentDate)}}}
        if(detail.url.isNotBlank()) item{val uriHandler=LocalUriHandler.current;val secureUrl=detail.url.startsWith("https://");Button({if(secureUrl) uriHandler.openUri(detail.url)},Modifier.fillMaxWidth(),enabled=secureUrl,colors=ButtonDefaults.buttonColors(containerColor=Green)){Icon(Icons.Default.Link,null);Spacer(Modifier.width(7.dp));Text("Source / Related Link")}}
        item{Text("NSE Watcher presents sourced information for analysis. News is not a recommendation to buy or sell a security. For material corporate actions, confirm the issuer's official announcement or exchange filing.",color=Muted,fontSize=9.sp)}
    }
}

private fun newsDate(value:String):String=when{value.isBlank()->"Latest";value.length>=10->value.take(10);else->value}


private data class PaperHolding(val symbol: String, val shares: Long, val averageCost: Double)

private object PaperPortfolioStore {
    private const val PREFS = "nse_watcher_paper_portfolio"
    private const val ENABLED = "enabled"
    private const val INITIAL = "initial"
    private const val CASH = "cash"
    private const val HOLDINGS = "holdings"
    private const val HISTORY = "history"
    private const val TRADES = "trades"
    private const val CONTRIBUTIONS = "contributions"
    const val PRACTICE_COST_RATE = 0.02

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(context: Context) = prefs(context).getBoolean(ENABLED, false)
    fun initial(context: Context) = prefs(context).getFloat(INITIAL, 0f).toDouble()
    fun cash(context: Context) = prefs(context).getFloat(CASH, 0f).toDouble()

    fun holdings(context: Context): List<PaperHolding> {
        return try {
            val a = JSONArray(prefs(context).getString(HOLDINGS, "[]") ?: "[]")
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(PaperHolding(o.getString("symbol"), o.getLong("shares"), o.getDouble("averageCost")))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun history(context: Context): List<Pair<Long, Double>> {
        return try {
            val a = JSONArray(prefs(context).getString(HISTORY, "[]") ?: "[]")
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(o.getLong("time") to o.getDouble("value"))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun contributions(context: Context): List<PaperContribution> {
        return try {
            val a = JSONArray(prefs(context).getString(CONTRIBUTIONS, "[]") ?: "[]")
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(PaperContribution(o.getLong("time"), o.getDouble("amount")))
                }
            }.let { stored ->
                if (stored.isNotEmpty()) stored.sortedBy { it.time }
                else {
                    val original = initial(context)
                    val firstTime = history(context).firstOrNull()?.first ?: System.currentTimeMillis()
                    if (original > 0.0) listOf(PaperContribution(firstTime, original)) else emptyList()
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun totalContributed(context: Context): Double {
        val stored = contributions(context).sumOf { it.amount }
        return if (stored > 0.0) stored else initial(context)
    }

    fun trades(context: Context): List<String> {
        return try {
            val a = JSONArray(prefs(context).getString(TRADES, "[]") ?: "[]")
            buildList { for (i in 0 until a.length()) add(a.getString(i)) }
        } catch (_: Exception) { emptyList() }
    }

    fun create(context: Context, amount: Double) {
        val safe = amount.coerceAtLeast(1000.0)
        val now = System.currentTimeMillis()
        val history = JSONArray().put(JSONObject().put("time", now).put("value", safe))
        val contributions = JSONArray().put(JSONObject().put("time", now).put("amount", safe))
        prefs(context).edit().putBoolean(ENABLED, true).putFloat(INITIAL, safe.toFloat())
            .putFloat(CASH, safe.toFloat()).putString(HOLDINGS, "[]")
            .putString(HISTORY, history.toString()).putString(CONTRIBUTIONS, contributions.toString())
            .putString(TRADES, "[]").apply()
    }

    fun addPracticeMoney(context: Context, amount: Double): Result<Unit> {
        if (!amount.isFinite() || amount < 1000.0) {
            return Result.failure(IllegalArgumentException("Enter at least KSh 1,000 of practice money."))
        }
        if (!isEnabled(context)) {
            return Result.failure(IllegalArgumentException("Create a practice portfolio first."))
        }
        val now = System.currentTimeMillis()
        val entries = contributions(context).toMutableList()
        entries.add(PaperContribution(now, amount))
        val a = JSONArray()
        entries.forEach { a.put(JSONObject().put("time", it.time).put("amount", it.amount)) }
        prefs(context).edit().putFloat(CASH, (cash(context) + amount).toFloat()).putString(CONTRIBUTIONS, a.toString()).apply()
        addTrade(context, "CAPITAL ADD • " + formatPrice(amount))
        return Result.success(Unit)
    }

    fun reset(context: Context) { prefs(context).edit().clear().apply() }

    fun recordSnapshot(context: Context, value: Double) {
        if (!isEnabled(context) || !value.isFinite()) return
        val entries = history(context).toMutableList()
        val now = System.currentTimeMillis()
        if (entries.isEmpty() || now - entries.last().first >= 15 * 60 * 1000L) entries.add(now to value)
        else entries[entries.lastIndex] = now to value
        val a = JSONArray()
        entries.takeLast(4000).forEach { a.put(JSONObject().put("time", it.first).put("value", it.second)) }
        prefs(context).edit().putString(HISTORY, a.toString()).apply()
    }

    fun buy(context: Context, stock: Stock, shares: Long, price: Double): Result<Unit> {
        val error = validate(stock, "BUY", shares, price)
        if (error != null) return Result.failure(IllegalArgumentException(error))
        val tradeValue = shares * price
        val fee = tradeValue * PRACTICE_COST_RATE
        val cash = cash(context)
        if (tradeValue + fee > cash + 0.0001) return Result.failure(IllegalArgumentException("Not enough practice cash."))
        val map = holdings(context).associateBy { it.symbol }.toMutableMap()
        val old = map[stock.symbol]
        val totalShares = (old?.shares ?: 0L) + shares
        val average = if (old == null) price else ((old.shares * old.averageCost) + tradeValue) / totalShares
        map[stock.symbol] = PaperHolding(stock.symbol, totalShares, average)
        saveHoldings(context, map.values.toList())
        prefs(context).edit().putFloat(CASH, (cash - tradeValue - fee).toFloat()).apply()
        addTrade(context, "BUY " + stock.symbol + " • " + formatShares(shares) + " @ " + formatPrice(price))
        return Result.success(Unit)
    }

    fun sell(context: Context, stock: Stock, shares: Long, price: Double): Result<Unit> {
        val error = validate(stock, "SELL", shares, price)
        if (error != null) return Result.failure(IllegalArgumentException(error))
        val map = holdings(context).associateBy { it.symbol }.toMutableMap()
        val old = map[stock.symbol] ?: return Result.failure(IllegalArgumentException("You do not hold " + stock.symbol + "."))
        if (shares > old.shares) return Result.failure(IllegalArgumentException("You do not have enough " + stock.symbol + " shares."))
        val tradeValue = shares * price
        val fee = tradeValue * PRACTICE_COST_RATE
        if (shares == old.shares) map.remove(stock.symbol) else map[stock.symbol] = old.copy(shares = old.shares - shares)
        saveHoldings(context, map.values.toList())
        prefs(context).edit().putFloat(CASH, (cash(context) + tradeValue - fee).toFloat()).apply()
        addTrade(context, "SELL " + stock.symbol + " • " + formatShares(shares) + " @ " + formatPrice(price))
        return Result.success(Unit)
    }

    private fun validate(stock: Stock, side: String, shares: Long, price: Double): String? {
        val marketPrice = stock.price
        if (!marketPrice.isFinite() || marketPrice <= 0.0) return "Current market price is unavailable."
        if (shares <= 0) return "Enter a positive share quantity."
        if (!price.isFinite() || price <= 0.0) return "Enter a valid limit price."
        val referencePrice = stock.previousClose?.takeIf { it.isFinite() && it > 0.0 }
            ?: return "This company's NSE reference price is unavailable in the current market feed. Refresh the market data before placing the practice order."
        val lowerBand = referencePrice * 0.90
        val upperBand = referencePrice * 1.10
        if (price < lowerBand - 0.000001 || price > upperBand + 0.000001) {
            return "Limit price must be within the current NSE price band: " + formatPrice(lowerBand) + " – " + formatPrice(upperBand) + "."
        }
        val tick = paperTickSize(price)
        val steps = price / tick
        if (kotlin.math.abs(steps - kotlin.math.round(steps)) > 0.000001) {
            return "Limit price must follow the NSE tick size of " + formatPrice(tick) + " at this price."
        }
        if (side == "BUY" && price + 0.000001 < marketPrice) {
            return "Practice buy limit must be at or above the latest observed price (" + formatPrice(marketPrice) + ") for an immediate simulated fill."
        }
        if (side == "SELL" && price - 0.000001 > marketPrice) {
            return "Practice sell limit must be at or below the latest observed price (" + formatPrice(marketPrice) + ") for an immediate simulated fill."
        }
        return null
    }

    private fun saveHoldings(context: Context, holdings: List<PaperHolding>) {
        val a = JSONArray()
        holdings.sortedBy { it.symbol }.forEach { h ->
            a.put(JSONObject().put("symbol", h.symbol).put("shares", h.shares).put("averageCost", h.averageCost))
        }
        prefs(context).edit().putString(HOLDINGS, a.toString()).apply()
    }

    private fun addTrade(context: Context, text: String) {
        val list = trades(context).toMutableList()
        list.add(0, java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()) + " • " + text)
        val a = JSONArray()
        list.take(50).forEach(a::put)
        prefs(context).edit().putString(TRADES, a.toString()).apply()
    }
}
private fun paperPriceBand(stock: Stock): Pair<Double, Double> {
    val market = stock.price
    val reference = stock.previousClose?.takeIf { it.isFinite() && it > 0.0 }
    if (!market.isFinite() || market <= 0.0 || reference == null) {
        return market to market
    }
    return reference * 0.90 to reference * 1.10
}

private fun paperTickSize(price: Double): Double = when {
    price < 5.0 -> 0.01
    price < 10.0 -> 0.02
    price < 50.0 -> 0.05
    price < 500.0 -> 0.25
    price < 1000.0 -> 1.00
    else -> 5.00
}

@Composable
private fun Paper() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(!PaperPortfolioStore.isEnabled(context)) }
    var showAddMoney by remember { mutableStateOf(false) }
    var startingAmount by rememberSaveable { mutableStateOf("100000") }
    var addedAmount by rememberSaveable { mutableStateOf("10000") }
    var addMoneyError by remember { mutableStateOf<String?>(null) }
    var selectedStock by remember { mutableStateOf<Stock?>(null) }
    var side by remember { mutableStateOf("BUY") }
    var resetConfirm by remember { mutableStateOf(false) }
    var pendingTradeSuccess by remember { mutableStateOf<PaperTradeSuccess?>(null) }
    var tradeSuccess by remember { mutableStateOf<PaperTradeSuccess?>(null) }
    var chartRange by rememberSaveable { mutableStateOf(PaperChartRange.ALL.name) }

    val enabled = remember(refresh) { PaperPortfolioStore.isEnabled(context) }
    val holdings = remember(refresh, stocks) { PaperPortfolioStore.holdings(context) }
    val cash = remember(refresh) { PaperPortfolioStore.cash(context) }
    val contributed = remember(refresh) { PaperPortfolioStore.totalContributed(context) }
    val value = cash + holdings.sumOf { h ->
        val stock = stocks.firstOrNull { it.symbol == h.symbol }
        (stock?.price ?: h.averageCost) * h.shares
    }
    val gain = value - contributed
    val returnPct = if (contributed > 0) gain / contributed * 100.0 else 0.0
    val history = remember(refresh, stocks) { PaperPortfolioStore.history(context) }
    val contributions = remember(refresh) { PaperPortfolioStore.contributions(context) }
    val activeRange = PaperChartRange.valueOf(chartRange)

    LaunchedEffect(refresh, stocks) {
        if (enabled) PaperPortfolioStore.recordSnapshot(context, value)
    }

    LazyColumn(contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Practice Portfolio", color = TextDark, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Learn by testing decisions with virtual money.", color = Muted, fontSize = 10.sp)
                }
                Surface(shape = RoundedCornerShape(14.dp), color = LightGreen) {
                    Icon(Icons.Default.School, "Practice", tint = Green, modifier = Modifier.padding(9.dp).size(20.dp))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = DarkGreen)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR PRACTICE ACCOUNT", color = Color(0xFFA9DEC5), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            Text(if (enabled) formatPrice(value) else "Not started", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            if (enabled) Text((if (gain >= 0) "+" else "") + formatPrice(gain) + "  •  " + (if (returnPct >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", returnPct), color = if (gain >= 0) Color(0xFF7CE6B3) else Color(0xFFFF9B9B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (enabled) FilledTonalButton(
                            onClick = { addMoneyError = null; showAddMoney = true },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0x337CE6B3), contentColor = Color(0xFFE5FFF1)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add money", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (enabled) {
                        Spacer(Modifier.height(12.dp))
                        PaperHistoryChart(history, contributions, activeRange) { range -> chartRange = range.name }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaperMetric("Available", formatPrice(cash), Modifier.weight(1f))
                            PaperMetric("Invested", formatPrice(value - cash), Modifier.weight(1f))
                            PaperMetric("Contributed", formatPrice(contributed), Modifier.weight(1f))
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text("Start with virtual money. No broker, CDS account or real trade is involved.", color = Color(0xFFD5E9DF), fontSize = 10.sp, lineHeight = 14.sp)
                        Spacer(Modifier.height(14.dp))
                        Button({ showCreate = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(14.dp)) { Text("Create practice portfolio", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (enabled) {
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Your holdings", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(holdings.size.toString() + " companies • latest available quote used for valuation", color = Muted, fontSize = 9.sp)
                            }
                            IconButton({ refresh++ }) { Icon(Icons.Default.Refresh, "Refresh", tint = Green) }
                        }
                        if (holdings.isEmpty()) {
                            Text("No positions yet", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Choose a company below to test a practice purchase.", color = Muted, fontSize = 9.sp)
                        } else holdings.forEach { h ->
                            val stock = stocks.firstOrNull { it.symbol == h.symbol }
                            if (stock != null) {
                                val marketValue = h.shares * stock.price
                                val pnl = marketValue - h.shares * h.averageCost
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Logo(stock.symbol, 38, stock.logoUrl)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stock.symbol, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                        Text(formatShares(h.shares) + " shares • avg " + formatPrice(h.averageCost), color = Muted, fontSize = 8.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatPrice(marketValue), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text((if (pnl >= 0) "+" else "") + formatPrice(pnl), color = if (pnl >= 0) Green else Red, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                    }
                                    TextButton({ selectedStock = stock; side = "SELL" }) { Text("SELL", color = Red, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp) }
                                }
                                HorizontalDivider(color = Border)
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Test a trade", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = DarkGreen)
                        Text("Choose a company, enter a limit price and quantity. The simulator validates the order and fills it immediately when the rules pass.", color = TextDark, fontSize = 9.sp, lineHeight = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        var companyQuery by rememberSaveable { mutableStateOf("") }
                        OutlinedTextField(value = companyQuery, onValueChange = { companyQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search companies…", fontSize = 10.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) })
                        Spacer(Modifier.height(7.dp))
                        stocks.filter { companyQuery.isBlank() || it.symbol.contains(companyQuery, true) || it.name.contains(companyQuery, true) }.take(8).forEach { stock ->
                            Row(Modifier.fillMaxWidth().clickable { selectedStock = stock; side = "BUY" }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Logo(stock.symbol, 36, stock.logoUrl); Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) { Text(stock.symbol, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp); Text(stock.name, color = Muted, fontSize = 8.sp, maxLines = 1) }
                                Column(horizontalAlignment = Alignment.End) { Text(formatPrice(stock.price), fontWeight = FontWeight.Bold, fontSize = 10.sp); Text((if (stock.change >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", stock.change), color = if (stock.change >= 0) Green else Red, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                            }
                            HorizontalDivider(color = Border)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Practice rules", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text("• Simulated locally — nothing is sent to a broker or the NSE ATS.", color = Muted, fontSize = 9.sp)
                        Text("• NSE equities use single-share trading; the simulator does not impose an artificial 100-share minimum.", color = Muted, fontSize = 9.sp)
                        Text("• Each company uses its own provider-supplied previous close as the reference input for the standard NSE ±10% price band; no blanket KSh limit is used.", color = Muted, fontSize = 9.sp)
                        Text("• The market feed is exchange-supplied and 15-minute delayed. If an exact exchange reference/limit field is unavailable, the app does not invent one.", color = Muted, fontSize = 9.sp)
                        Text("• Practice orders fill immediately only when the limit price could cross the latest observed market price. A real order book is not simulated.", color = Muted, fontSize = 9.sp)
                        Text("• A 2.0% practice transaction-cost assumption is applied to buys and sells; it is not a live brokerage quote.", color = Muted, fontSize = 9.sp)
                        Text("• Portfolio value is repriced from the latest available quote. Added practice money is capital, not investment profit.", color = Muted, fontSize = 9.sp)
                    }
                }
            }
            item {
                OutlinedButton({ resetConfirm = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD8A3A3))) {
                    Icon(Icons.Default.RestartAlt, null, tint = Red); Spacer(Modifier.width(6.dp)); Text("Reset practice portfolio", color = Red, fontWeight = FontWeight.Bold)
                }
            }
            val recent = PaperPortfolioStore.trades(context).take(5)
            if (recent.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Recent practice activity", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        recent.forEach { Text(it, color = Muted, fontSize = 8.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                }
            }
        }
        item { Text("Practice investing is educational only. It does not execute trades, guarantee returns or represent a brokerage account.", color = Muted, fontSize = 8.sp, lineHeight = 11.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
    }

    if (showCreate) AlertDialog(
        onDismissRequest = { if (enabled) showCreate = false },
        title = { Text("Create practice portfolio", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("Choose the virtual starting balance you want to test.", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(startingAmount, { startingAmount = it.filter(Char::isDigit) }, label = { Text("Starting balance (KSh)") }, singleLine = true)
                Spacer(Modifier.height(5.dp)); Text("No real money is involved.", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button({
                val amount = startingAmount.toDoubleOrNull()
                if (amount != null && amount >= 1000.0) { PaperPortfolioStore.create(context, amount); showCreate = false; refresh++ }
            }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Start") }
        },
        dismissButton = { if (enabled) TextButton({ showCreate = false }) { Text("Cancel") } }
    )

    if (showAddMoney) AlertDialog(
        onDismissRequest = { showAddMoney = false },
        title = { Text("Add practice money", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add virtual capital without changing your investment gain or return. Your account value increases by the added cash.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                OutlinedTextField(addedAmount, { addedAmount = it.filter(Char::isDigit) }, label = { Text("Amount (KSh)") }, singleLine = true)
                addMoneyError?.let { Text(it, color = Red, fontSize = 9.sp) }
            }
        },
        confirmButton = {
            Button({
                val result = PaperPortfolioStore.addPracticeMoney(context, addedAmount.toDoubleOrNull() ?: 0.0)
                result.fold(
                    { showAddMoney = false; addMoneyError = null; refresh++ },
                    { addMoneyError = it.message ?: "Could not add practice money." }
                )
            }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Add money") }
        },
        dismissButton = { TextButton({ showAddMoney = false }) { Text("Cancel") } }
    )

    selectedStock?.let { stock ->
        PaperOrderDialog(context, stock, side, cash, holdings.firstOrNull { it.symbol == stock.symbol }?.shares ?: 0L,
            { selectedStock = null }, { success -> selectedStock = null; refresh++; pendingTradeSuccess = success })
    }

    LaunchedEffect(selectedStock, pendingTradeSuccess) {
        if (selectedStock == null && pendingTradeSuccess != null) {
            kotlinx.coroutines.delay(150); tradeSuccess = pendingTradeSuccess; pendingTradeSuccess = null
        }
    }

    tradeSuccess?.let { success ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { tradeSuccess = null }) {
            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = LightGreen, modifier = Modifier.size(72.dp)) { Icon(Icons.Default.CheckCircle, "Practice trade approved", tint = Green, modifier = Modifier.padding(13.dp).fillMaxSize()) }
                    Text(if (success.side == "BUY") "Practice buy approved" else "Practice sell approved", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TextDark, textAlign = TextAlign.Center)
                    Text(if (success.side == "BUY") "Your virtual purchase was added to the practice portfolio." else "Your virtual sale was added to the practice portfolio.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = LightGreen)) {
                        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(formatShares(success.shares) + " shares of " + success.symbol, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("Execution price  •  " + formatPrice(success.price), color = Muted, fontSize = 10.sp)
                            Text((if (success.side == "BUY") "Estimated cost  •  " else "Estimated proceeds  •  ") + formatPrice(success.amount), color = TextDark, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    Button(onClick = { tradeSuccess = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Done") }
                }
            }
        }
    }

    if (resetConfirm) {
        var resetText by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("Reset practice portfolio?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("This permanently clears your virtual cash, holdings, history and practice activity. This cannot be undone.", color = Muted, fontSize = 10.sp); Text("Type RESET to confirm.", fontWeight = FontWeight.Bold, fontSize = 10.sp); OutlinedTextField(resetText, { resetText = it }, label = { Text("Confirmation") }, singleLine = true) } },
            confirmButton = { TextButton(enabled = resetText == "RESET", onClick = { PaperPortfolioStore.reset(context); resetConfirm = false; resetText = ""; refresh++ }) { Text("Reset", color = if (resetText == "RESET") Red else Muted) } },
            dismissButton = { TextButton({ resetConfirm = false }) { Text("Cancel") } }
        )
    }
}
@Composable
private fun PaperMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(14.dp), color = Color(0x331A5A45)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, color = Color(0xFFB8D5C9), fontSize = 7.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private data class PaperContribution(val time: Long, val amount: Double)

private enum class PaperChartRange(val label: String, val millis: Long?) {
    DAY("1D", 24L * 60L * 60L * 1000L),
    WEEK("1W", 7L * 24L * 60L * 60L * 1000L),
    MONTH("1M", 30L * 24L * 60L * 60L * 1000L),
    THREE_MONTHS("3M", 90L * 24L * 60L * 60L * 1000L),
    SIX_MONTHS("6M", 180L * 24L * 60L * 60L * 1000L),
    YEAR("1Y", 365L * 24L * 60L * 60L * 1000L),
    ALL("All", null)
}

@Composable
private fun PaperHistoryChart(
    history: List<Pair<Long, Double>>,
    contributions: List<PaperContribution>,
    selectedRange: PaperChartRange,
    onRangeSelected: (PaperChartRange) -> Unit
) {
    val now = System.currentTimeMillis()
    val cutoff = selectedRange.millis?.let { now - it }
    val filtered = history.filter { cutoff == null || it.first >= cutoff }.takeLast(240)
    val allPoints = if (filtered.isEmpty()) history.takeLast(2) else filtered
    var selectedIndex by remember(selectedRange, history) { mutableIntStateOf(-1) }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Investment performance", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Text("Market movement only • capital additions are excluded from gain", color = Color(0xFFB8D5C9), fontSize = 8.sp)
            }
            if (allPoints.size >= 2) {
                val latestPerformance = allPoints.last().second - cumulativeContribution(contributions, allPoints.last().first)
                Text(formatPrice(latestPerformance), color = Color(0xFF7CE6B3), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PaperChartRange.values().forEach { range ->
                val active = range == selectedRange
                Surface(
                    modifier = Modifier.clickable { onRangeSelected(range) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) Color(0xFF7CE6B3) else Color(0x331A5A45)
                ) {
                    Text(range.label, color = if (active) DarkGreen else Color(0xFFB8D5C9), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
        }
        if (allPoints.size >= 2) {
            val performance = allPoints.map { it.first to (it.second - cumulativeContribution(contributions, it.first)) }
            val min = performance.minOf { it.second }
            val max = performance.maxOf { it.second }
            val range = (max - min).takeIf { it > 0.0 } ?: 1.0
            val selected = performance.getOrNull(selectedIndex.coerceIn(-1, performance.lastIndex))
            Canvas(
                Modifier.fillMaxWidth().height(150.dp).pointerInput(selectedRange, performance) {
                    detectTapGestures { offset ->
                        val index = ((offset.x / size.width) * performance.lastIndex).toInt().coerceIn(0, performance.lastIndex)
                        selectedIndex = index
                    }
                }
            ) {
                repeat(4) { row ->
                    val y = size.height * row / 3f
                    drawLine(Color(0x334E8B70), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                }
                val path = Path()
                performance.forEachIndexed { i, p ->
                    val x = size.width * i / performance.lastIndex
                    val y = size.height - (((p.second - min) / range).toFloat() * (size.height - 18f) + 9f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, Color(0xFF7CE6B3), style = Stroke(width = 4f, cap = StrokeCap.Round))
                contributions.filter { it.time >= performance.first().first && it.time <= performance.last().first }.forEach { event ->
                    val x = performance.indexOfFirst { it.first >= event.time }
                    if (x >= 0) {
                        val px = size.width * x / performance.lastIndex
                        drawLine(Color(0x6687C8AC), androidx.compose.ui.geometry.Offset(px, 8f), androidx.compose.ui.geometry.Offset(px, size.height - 8f), 2f)
                    }
                }
                if (selected != null && selectedIndex in performance.indices) {
                    val px = size.width * selectedIndex / performance.lastIndex
                    val py = size.height - (((selected.second - min) / range).toFloat() * (size.height - 18f) + 9f)
                    drawCircle(Color(0xFFBFF5D7), 8f, androidx.compose.ui.geometry.Offset(px, py))
                    drawCircle(Green, 4f, androidx.compose.ui.geometry.Offset(px, py))
                }
            }
            selected?.let {
                Text(formatPrice(it.second) + " • " + java.text.SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(java.util.Date(it.first)), color = Color(0xFFB8D5C9), fontSize = 8.sp)
            }
        } else {
            Text("More observations will appear as the practice account is repriced from delayed market data.", color = Color(0xFFB8D5C9), fontSize = 8.sp)
        }
    }
}

private fun cumulativeContribution(contributions: List<PaperContribution>, time: Long): Double =
    contributions.filter { it.time <= time }.sumOf { it.amount }

private data class PaperTradeSuccess(
    val side: String,
    val symbol: String,
    val shares: Long,
    val price: Double,
    val amount: Double
)

@Composable
private fun PaperOrderDialog(
    context: Context,
    stock: Stock,
    side: String,
    cash: Double,
    ownedShares: Long,
    onDismiss: () -> Unit,
    onComplete: (PaperTradeSuccess) -> Unit
) {
    var sharesText by rememberSaveable(stock.symbol, side) { mutableStateOf("100") }
    var priceText by rememberSaveable(stock.symbol, side) { mutableStateOf(String.format(Locale.US, "%.2f", stock.price)) }
    var error by remember { mutableStateOf<String?>(null) }
    val marketPrice = stock.price
    val currentPrice = priceText.toDoubleOrNull() ?: 0.0
    val currentTick = paperTickSize(currentPrice.coerceAtLeast(0.01))
    val band = paperPriceBand(stock)
    val shares = sharesText.toLongOrNull() ?: 0L
    val price = priceText.toDoubleOrNull() ?: 0.0
    val gross = shares * price
    val fee = gross * PaperPortfolioStore.PRACTICE_COST_RATE
    val amount = if (side == "BUY") gross + fee else gross - fee
    val tick = paperTickSize(price.coerceAtLeast(0.01))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = if (side == "BUY") LightGreen else Color(0xFFFFEEEE)) {
                    Text(if (side == "BUY") "BUY" else "SELL", color = if (side == "BUY") Green else Red, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Text("Practice " + if (side == "BUY") "Buy" else "Sell", fontWeight = FontWeight.ExtraBold)
            }
            Text(stock.symbol + " • " + stock.name, color = Muted, fontSize = 9.sp)
        }},
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Latest observed price: " + formatPrice(stock.price) + " • 15 min delayed market data", color = Muted, fontSize = 9.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(sharesText, { sharesText = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Shares") }, singleLine = true)
                    Column(Modifier.weight(1.15f)) {
                        OutlinedTextField(priceText, { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.fillMaxWidth(), label = { Text("Limit price") }, singleLine = true)
                        TextButton(onClick = { priceText = String.format(Locale.US, "%.2f", marketPrice); error = null }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                            Text("Use market price", fontSize = 8.sp, color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val next = (currentPrice - currentTick).coerceAtLeast(band.first)
                        priceText = String.format(Locale.US, "%.2f", next); error = null
                    }, enabled = currentPrice > band.first + 0.000001, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = {
                        val next = (currentPrice + currentTick).coerceAtMost(band.second)
                        priceText = String.format(Locale.US, "%.2f", next); error = null
                    }, enabled = currentPrice < band.second - 0.000001, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Tick " + formatPrice(currentTick) + " • band " + formatPrice(band.first) + " – " + formatPrice(band.second), color = Muted, fontSize = 8.sp, modifier = Modifier.weight(1f))
                }
                Text("Reference: provider previous close • market data is 15 min delayed", color = Muted, fontSize = 8.sp)
                Text((if (side == "BUY") "Estimated cost: " else "Estimated proceeds: ") + formatPrice(amount), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(if (side == "BUY") "Available cash: " + formatPrice(cash) else "Available shares: " + formatShares(ownedShares), color = Muted, fontSize = 8.sp)
                error?.let { Text(it, color = Red, fontSize = 9.sp, lineHeight = 12.sp) }
            }
        },
        confirmButton = {
            Button({
                val result = if (side == "BUY") PaperPortfolioStore.buy(context, stock, shares, price) else PaperPortfolioStore.sell(context, stock, shares, price)
                result.fold(
                    {
                        error = null
                        onComplete(PaperTradeSuccess(side, stock.symbol, shares, price, amount))
                    },
                    { error = it.message ?: "Practice trade could not be completed" }
                )
            }, colors = ButtonDefaults.buttonColors(containerColor = if (side == "BUY") Green else Red)) {
                Text(if (side == "BUY") "Approve practice buy" else "Approve practice sell")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun More(go:(Page)->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Section("More","Your NSE Watcher tools")};item{RowItem(Icons.Default.AccountCircle,"Profile","Personal information and profile picture"){go(Page.PROFILE)}};item{RowItem(Icons.Default.AccountBalanceWallet,"Paper Investing","Practice with virtual money"){go(Page.PAPER)}};item{RowItem(Icons.Default.Settings,"Settings","Theme, notifications, data and privacy"){go(Page.SETTINGS)}};item{RowItem(Icons.Default.HelpOutline,"Help & Support","FAQs, contact and report issues"){go(Page.HELP)}};item{RowItem(Icons.Default.Info,"About NSE Watcher","Version and product information"){go(Page.ABOUT)}}}}

@Composable private fun Profile(name:String,username:String,email:String,description:String,onName:(String)->Unit,onUsername:(String)->Unit,onEmail:(String)->Unit,onDescription:(String)->Unit,pick:()->Unit,back:()->Unit,go:(Page)->Unit){var editing by rememberSaveable{mutableStateOf(false)};var n by rememberSaveable(name){mutableStateOf(name)};var u by rememberSaveable(username){mutableStateOf(username)};var e by rememberSaveable(email){mutableStateOf(email)};var d by rememberSaveable(description){mutableStateOf(description)};LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("Profile","Your NSE Watcher account",back)};item{ProfileHero(name,username,pick)};item{if(editing){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileField("Full name",n,{n=it},Icons.Default.Person);ProfileField("Username",u,{u=it},Icons.Default.AccountCircle);ProfileField("Email",e,{e=it},Icons.Default.Email);ProfileField("Description",d,{d=it},Icons.Default.Info);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({editing=false}){Text("Cancel")};Button({onName(n);onUsername(u);onEmail(e);onDescription(d);editing=false},colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Save")}}}}}else{Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileInfo(Icons.Default.AccountCircle,"Username","@$username");ProfileInfo(Icons.Default.Email,"Email",email);ProfileInfo(Icons.Default.Info,"Description",description);Spacer(Modifier.height(5.dp));Button({editing=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(7.dp));Text("Edit Profile")}}}}};item{RowItem(Icons.Default.PhotoCamera,"Profile Picture","Change your profile photo",pick)};item{RowItem(Icons.Default.AccountBalanceWallet,"Paper Investing","Practice with virtual money"){go(Page.PAPER)}};item{RowItem(Icons.Default.Settings,"Settings","Theme, notifications, data and privacy"){go(Page.SETTINGS)}};item{RowItem(Icons.Default.Lock,"Account Security","Password and device protection"){go(Page.SECURITY)}};item{RowItem(Icons.Default.PrivacyTip,"Privacy","Review your privacy settings"){go(Page.PRIVACY)}};item{Note("NSE Watcher provides market information and analysis. It does not execute trades or guarantee returns.")}}}
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
        item { Note("NSE Watcher is an analysis and education product. Paper Investing uses virtual money only and does not place real trades.") }
    }
}
@Composable private fun SettingsCard(title:String,icon:ImageVector,content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(34.dp),CircleShape,LightGreen){Icon(icon,null,tint=Green,modifier=Modifier.padding(8.dp))};Spacer(Modifier.width(9.dp));Text(title,fontWeight=FontWeight.ExtraBold,fontSize=15.sp)};Spacer(Modifier.height(4.dp));content()}}}
@Composable private fun ToggleRow(icon:ImageVector,title:String,sub:String,checked:Boolean,onChecked:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(21.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Switch(checked,onChecked)}}
@Composable private fun RowItem(icon:ImageVector,title:String,sub:String,onClick:()->Unit={}){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(22.dp));Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(19.dp))}}
@Composable private fun ThemePage(dark:Boolean,set:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Theme","Choose how NSE Watcher looks",back)};item{ThemeChoice("Light","Bright white interface",!dark){set(false)}};item{ThemeChoice("Dark","Low-light interface",dark){set(true)}};}}
@Composable private fun ThemeChoice(title:String,sub:String,selected:Boolean,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),RoundedCornerShape(16.dp),border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Green else Border),colors=CardDefaults.cardColors(containerColor=if(selected)LightGreen else MaterialTheme.colorScheme.surface)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(title=="Light")Icons.Default.LightMode else if(title=="Dark")Icons.Default.DarkMode else Icons.Default.SettingsSystemDaydream,null,tint=Green);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(sub,fontSize=10.sp,color=Muted)};if(selected)Icon(Icons.Default.CheckCircle,null,tint=Green)}}}
@Composable private fun NotificationsPage(m:Boolean,p:Boolean,n:Boolean,a:Boolean,sm:(Boolean)->Unit,sp:(Boolean)->Unit,sn:(Boolean)->Unit,sa:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Notifications","Choose what you want to hear about",back)};item{SettingsCard("Notification Types",Icons.Default.Notifications){ToggleRow(Icons.Default.ShowChart,"Market updates","NSE-wide movements and daily briefs",m,sm);ToggleRow(Icons.Default.PriceChange,"Price alerts","Your watchlist thresholds",p,sp);ToggleRow(Icons.Default.Article,"News alerts","Company and market news",n,sn);ToggleRow(Icons.Default.Apps,"App notifications","Product updates",a,sa)}};item{Note("Price, news, and corporate-action alerts are monitored from provider-backed market data. Background checks run no more often than every 15 minutes when the market is open.")}}}
@Composable private fun LiveData(refresh:Boolean,volume:Boolean,changes:Boolean,sr:(Boolean)->Unit,sv:(Boolean)->Unit,sc:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Live Data","Market-data display preferences",back)};item{SettingsCard("Data Display",Icons.Default.ShowChart){ToggleRow(Icons.Default.Sync,"Auto refresh","Keep market information current",refresh,sr);ToggleRow(Icons.Default.BarChart,"Show volume","Display trading activity",volume,sv);ToggleRow(Icons.Default.TrendingUp,"Show price changes","Display daily percentage moves",changes,sc)}}}}
@Composable
private fun AlertPage(back:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val store=remember{AlertStore(context)}
    val watchlistStore=remember{WatchlistStore(context)}
    val alerts by store.alerts.collectAsState(initial=emptyList())
    val watchedSymbols by watchlistStore.symbols.collectAsState(initial=emptyList())
    LaunchedEffect(Unit) {
        MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { liveStocks.value = it }
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