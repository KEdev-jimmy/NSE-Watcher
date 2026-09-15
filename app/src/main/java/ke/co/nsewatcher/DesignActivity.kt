@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ke.co.nsewatcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import ke.co.nsewatcher.data.MyStocksCache

private val Green = Color(0xFF00A859)
private val LightGreen = Color(0xFFE9F8F0)
private val DarkGreen = Color(0xFF083C27)
private val TextDark = Color(0xFF12231B)
private val Muted = Color(0xFF6C7A72)
private val Border = Color(0xFFE1EAE5)
private val Red = Color(0xFFE04444)
private const val PREFS = "nse_watcher_preferences"

data class Stock(val symbol:String,val name:String,val price:Double,val change:Double,val history:List<Double>)
private val fallbackStocks = listOf(
    Stock("SCOM","Safaricom",18.50,5.24,listOf(15.2,15.5,15.3,16.1,16.8,16.5,17.2,17.9,18.5)),
    Stock("KCB","KCB Group",42.30,3.26,listOf(38.0,38.8,39.2,40.1,39.7,40.8,41.5,41.9,42.3)),
    Stock("EQTY","Equity Group",46.75,2.98,listOf(43.2,43.8,44.0,44.9,44.5,45.1,45.8,46.1,46.75)),
    Stock("COOP","Co-operative Bank",21.10,2.41,listOf(19.5,19.7,20.0,19.9,20.3,20.5,20.8,20.9,21.1)),
    Stock("ABSA","Absa Bank Kenya",14.30,-2.17,listOf(15.5,15.2,15.0,14.8,14.9,14.6,14.7,14.5,14.3)),
    Stock("EABL","East African Breweries",155.00,-1.81,listOf(161.0,160.5,159.8,158.7,159.2,157.8,157.0,156.2,155.0)),
    Stock("KPLC","Kenya Power",4.82,-1.22,listOf(5.2,5.1,5.0,5.05,4.9,4.95,4.88,4.86,4.82))
)
private val liveStocks = mutableStateOf(fallbackStocks)
private val stocks: List<Stock> get() = liveStocks.value
private enum class Page { HOME, MARKET, COMPANIES, PAPER, MORE, COMPANY, PROFILE, SETTINGS, THEME, NOTIFICATIONS, LIVE_DATA, CHARTS, ALERTS, LANGUAGE, SECURITY, PRIVACY, DISPLAY, HELP, ABOUT }

class DesignActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("avatar_uri", uri.toString()).apply()
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App { picker.launch(arrayOf("image/*")) } } }
}

@Composable private fun App(pickAvatar:()->Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    LaunchedEffect(Unit) { MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { liveStocks.value = it } }
    var page by remember { mutableStateOf(Page.HOME) }
    var history by remember { mutableStateOf(emptyList<Page>()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(stocks.first()) }
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
    var showVolume by rememberSaveable { mutableStateOf(prefs.getBoolean("show_volume", true)) }
    var showChanges by rememberSaveable { mutableStateOf(prefs.getBoolean("show_changes", true)) }
    fun put(k:String,v:Boolean){prefs.edit().putBoolean(k,v).apply()}; fun put(k:String,v:String){prefs.edit().putString(k,v).apply()}
    fun go(to:Page){if(to!=page){history=history+page;page=to}}; fun back(){if(history.isNotEmpty()){page=history.last();history=history.dropLast(1)}else page=Page.HOME}
    BackHandler(enabled=page!=Page.HOME){back()}
    val scheme=if(dark) darkColorScheme(primary=Color(0xFF32D486),background=Color(0xFF0D1712),surface=Color(0xFF132019),onSurface=Color.White,onBackground=Color.White,onSurfaceVariant=Color(0xFFB7C7BE)) else lightColorScheme(primary=Green,background=Color.White,surface=Color.White,onSurface=TextDark,onBackground=TextDark,onSurfaceVariant=Muted)
    MaterialTheme(colorScheme=scheme){Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),color=scheme.background){
        when(page){
            Page.HOME,Page.MARKET,Page.COMPANIES,Page.PAPER,Page.MORE -> Scaffold(topBar={TopBar(name,::go)},bottomBar={BottomNav(tab){tab=it;history=emptyList();page=when(it){0->Page.HOME;1->Page.MARKET;2->Page.COMPANIES;3->Page.PAPER;else->Page.MORE}}}){pad->Box(Modifier.fillMaxSize().padding(pad)){when(page){Page.HOME->Home{selected=it;go(Page.COMPANY)};Page.MARKET->Market();Page.COMPANIES->Companies{selected=it;go(Page.COMPANY)};Page.PAPER->Paper();else->More(::go)}}}
            Page.COMPANY->Company(selected,::back)
            Page.PROFILE->Profile(name,username,email,description,{name=it;put("profile_name",it)},{username=it;put("username",it)},{email=it;put("email",it)},{description=it;put("description",it)},pickAvatar,::back,::go)
            Page.SETTINGS->Settings(dark,marketAlerts,priceAlerts,newsAlerts,appAlerts,autoRefresh,showVolume,showChanges,{dark=it;put("dark_mode",it)},{marketAlerts=it;put("market_alerts",it)},{priceAlerts=it;put("price_alerts",it)},{newsAlerts=it;put("news_alerts",it)},{appAlerts=it;put("app_alerts",it)},{autoRefresh=it;put("auto_refresh",it)},{showVolume=it;put("show_volume",it)},{showChanges=it;put("show_changes",it)},::back,::go)
            Page.THEME->ThemePage(dark,{dark=it;put("dark_mode",it)},::back)
            Page.NOTIFICATIONS->NotificationsPage(marketAlerts,priceAlerts,newsAlerts,appAlerts,{marketAlerts=it;put("market_alerts",it)},{priceAlerts=it;put("price_alerts",it)},{newsAlerts=it;put("news_alerts",it)},{appAlerts=it;put("app_alerts",it)},::back)
            Page.LIVE_DATA->LiveData(autoRefresh,showVolume,showChanges,{autoRefresh=it;put("auto_refresh",it)},{showVolume=it;put("show_volume",it)},{showChanges=it;put("show_changes",it)},::back)
            Page.CHARTS->SimplePage("Chart Settings",Icons.Default.ShowChart,listOf("Default timeframe" to "1D","Chart style" to "Line","Show grid" to "On","Indicators" to "On"),::back)
            Page.ALERTS->SimplePage("Price Alerts",Icons.Default.Notifications,listOf("Price alerts" to if(priceAlerts) "Enabled" else "Disabled","Daily gain / loss" to "Enabled","High volume" to "Enabled","Corporate actions" to "Enabled"),::back)
            Page.LANGUAGE->SimplePage("Language",Icons.Default.Language,listOf("App language" to "English","Currency" to "KSh (Kenyan Shillings)","Region" to "Kenya"),::back)
            Page.SECURITY->SimplePage("Account Security",Icons.Default.Lock,listOf("Password" to "Protected","Biometric unlock" to "Off","Active sessions" to "This device","Data permissions" to "Review"),::back)
            Page.PRIVACY->SimplePage("Privacy",Icons.Default.PrivacyTip,listOf("Personalisation" to "On device","Analytics" to "Optional","Data sharing" to "Not shared for trading"),::back)
            Page.DISPLAY->SimplePage("Font & Display",Icons.Default.Visibility,listOf("Font size" to "Medium","Compact cards" to "On","Animations" to "Standard"),::back)
            Page.HELP->HelpPage(::back); Page.ABOUT->AboutPage(::back); else->{page=Page.HOME}
        }
    }}
}

@Composable private fun TopBar(name:String,go:(Page)->Unit){Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(42.dp),RoundedCornerShape(12.dp),LightGreen){Icon(Icons.Default.ShowChart,null,Modifier.padding(7.dp),Green)};Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text("NSE Watcher",fontSize=18.sp,fontWeight=FontWeight.ExtraBold);Text("Analyse • Understand • Invest Smarter",fontSize=10.sp,color=Muted)};IconButton({}){Icon(Icons.Default.Search,"Search",tint=Green)};Avatar(name){go(Page.PROFILE)};IconButton({go(Page.SETTINGS)}){Icon(Icons.Default.Settings,"Settings",tint=Green)}}}
@Composable private fun Avatar(name:String,onClick:()->Unit){val c=androidx.compose.ui.platform.LocalContext.current;val u=c.getSharedPreferences(PREFS,0).getString("avatar_uri",null);val b by produceState<Bitmap?>(null,u){value=try{u?.let{c.contentResolver.openInputStream(Uri.parse(it))?.use{stream->BitmapFactory.decodeStream(stream)}}}catch(_:Exception){null}};Surface(Modifier.size(38.dp).clip(CircleShape).clickable(onClick=onClick),CircleShape,Color(0xFFDDEFE6)){if(b!=null)Image(b!!.asImageBitmap(),"Profile",Modifier.fillMaxSize())else Box(Modifier.fillMaxSize(),Alignment.Center){Text(name.take(1).uppercase(),color=Green,fontWeight=FontWeight.Bold)}}}
@Composable private fun BottomNav(selected:Int,onSelect:(Int)->Unit){val items=listOf("Home" to Icons.Default.Home,"Market" to Icons.Default.CandlestickChart,"Companies" to Icons.Default.Business,"Paper Invest" to Icons.Default.AccountBalanceWallet,"More" to Icons.Default.AutoGraph);NavigationBar{items.forEachIndexed{i,x->NavigationBarItem(selected==i,{onSelect(i)},icon={Icon(x.second,x.first)},label={Text(x.first,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Green,selectedTextColor=Green,indicatorColor=LightGreen,unselectedIconColor=Muted,unselectedTextColor=Muted))}}}
@Composable private fun Header(title:String,sub:String?=null,back:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)IconButton(back){Icon(Icons.Default.ArrowBack,"Back")};Column{Text(title,fontSize=20.sp,fontWeight=FontWeight.ExtraBold);if(sub!=null)Text(sub,fontSize=10.sp,color=Muted)}}}

@Composable private fun Home(open:(Stock)->Unit){val gainers=stocks.filter{it.change>0}.sortedByDescending{it.change};val losers=stocks.filter{it.change<0}.sortedBy{it.change};LazyColumn(contentPadding=PaddingValues(16.dp,5.dp,16.dp,20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{MarketStatusCard()};item{IndexRow()};item{TrendCard()};item{Section("Market Snapshot","A quick view before you invest")};item{Movers(gainers.take(4),losers.take(4),open)};item{Section("Top Companies","Stocks moving the NSE today")};item{LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp)){items(gainers.take(4)){StockMini(it){open(it)}}}};item{Section("Latest News","Market events and company updates")};item{NewsPreview()};item{PaperBanner()}}}
@Composable private fun MarketStatusCard(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Column(Modifier.padding(15.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).clip(CircleShape).background(Green));Spacer(Modifier.width(7.dp));Text("NSE MARKET",color=DarkGreen,fontWeight=FontWeight.ExtraBold,fontSize=12.sp);Spacer(Modifier.weight(1f));Text("DATA FEED",color=Muted,fontSize=10.sp)};Spacer(Modifier.height(10.dp));Row(verticalAlignment=Alignment.Bottom){Column(Modifier.weight(1f)){Text("Market overview",color=Muted,fontSize=11.sp);Text("Clear picture of the NSE",fontSize=19.sp,fontWeight=FontWeight.ExtraBold);Text("before you make an investment decision.",fontSize=11.sp,color=Muted)};Surface(shape=RoundedCornerShape(10.dp),color=Color.White){Text("15 MIN DELAYED",color=Green,fontWeight=FontWeight.ExtraBold,fontSize=11.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp))}}}}}
@Composable private fun IndexRow(){val x=listOf("NSE 20" to "1,843.56","NASI" to "112.48","NSE 25" to "3,642.17");LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(x){(n,v)->Card(Modifier.width(145.dp),RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(11.dp)){Text(n,fontSize=11.sp,fontWeight=FontWeight.Bold);Text(v,fontSize=17.sp,fontWeight=FontWeight.ExtraBold);Text("▲ +1.34%",color=Green,fontSize=10.sp,fontWeight=FontWeight.Bold)}}}}}
@Composable private fun TrendCard(){var p by rememberSaveable{mutableStateOf("1D")};Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("NSE 20 — Market Trend",fontWeight=FontWeight.ExtraBold,fontSize=15.sp);Text("See how the market has been performing",color=Muted,fontSize=10.sp)};Text("+1.34%",color=Green,fontWeight=FontWeight.ExtraBold)};Spacer(Modifier.height(8.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("1D","1W","1M","3M","6M","1Y","5Y").forEach{x->FilterChip(selected=p==x,onClick={p=x},label={Text(x,fontSize=10.sp)})}};Spacer(Modifier.height(5.dp));Chart(listOf(28f,38f,34f,47f,44f,58f,52f,67f,61f,74f,69f,83f),Green);Text("Historical performance will use sourced NSE market data in live mode.",color=Muted,fontSize=9.sp)}}}
@Composable private fun Chart(values:List<Float>,tint:Color){Canvas(Modifier.fillMaxWidth().height(95.dp).padding(vertical=8.dp)){val path=Path();values.forEachIndexed{i,v->{val x=size.width*i/(values.size-1);val y=size.height-(v/100f*size.height);if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}};drawPath(path=path,color=tint,style=Stroke(width=4f,cap=StrokeCap.Round))}}
@Composable private fun Section(t:String,s:String){Column{Text(t,fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Text(s,color=Muted,fontSize=10.sp)}}
@Composable private fun Movers(g:List<Stock>,l:List<Stock>,open:(Stock)->Unit){Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Row(Modifier.padding(vertical=12.dp)){MoverList("Top Gainers",g,Green,open,Modifier.weight(1f));Box(Modifier.width(1.dp).height(165.dp).background(Border));MoverList("Top Losers",l,Red,open,Modifier.weight(1f))}}}
@Composable private fun MoverList(title:String,list:List<Stock>,tint:Color,open:(Stock)->Unit,m:Modifier){Column(m.padding(horizontal=11.dp)){Text(title,color=tint,fontWeight=FontWeight.ExtraBold,fontSize=12.sp);Spacer(Modifier.height(7.dp));list.forEach{s->Row(Modifier.fillMaxWidth().clickable{open(s)}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Logo(s.symbol,27);Spacer(Modifier.width(6.dp));Text(s.symbol,fontWeight=FontWeight.Bold,fontSize=11.sp,modifier=Modifier.weight(1f));Text(String.format(Locale.US,"%+.2f%%",s.change),color=tint,fontWeight=FontWeight.ExtraBold,fontSize=10.sp)}}}}
@Composable private fun Logo(symbol:String,size:Int){Surface(Modifier.size(size.dp),RoundedCornerShape(8.dp),Color(0xFF285C8C)){Box(Modifier.fillMaxSize(),Alignment.Center){Text(symbol.take(3),color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=8.sp)}}}
@Composable private fun StockMini(s:Stock,open:()->Unit){Card(Modifier.width(145.dp).clickable(onClick=open),RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(10.dp)){Row(verticalAlignment=Alignment.CenterVertically){Logo(s.symbol,27);Spacer(Modifier.width(6.dp));Text(s.symbol,fontWeight=FontWeight.ExtraBold,fontSize=11.sp)};Text(String.format(Locale.US,"KSh %.2f",s.price),fontWeight=FontWeight.ExtraBold,fontSize=15.sp);Text(String.format(Locale.US,"%+.2f%%",s.change),color=if(s.change>=0)Green else Red,fontWeight=FontWeight.Bold,fontSize=10.sp);Chart(s.history.map{it.toFloat()},if(s.change>=0)Green else Red)}}}
@Composable private fun NewsPreview(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(58.dp),RoundedCornerShape(10.dp),LightGreen){Icon(Icons.Default.Article,null,tint=Green,modifier=Modifier.padding(17.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("NSE market update",fontWeight=FontWeight.Bold,fontSize=12.sp);Text("Market events and company updates",color=Muted,fontSize=9.sp)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}
@Composable private fun PaperBanner(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=DarkGreen)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(42.dp),CircleShape,Green){Icon(Icons.Default.AccountBalanceWallet,null,tint=Color.White,modifier=Modifier.padding(9.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Paper Investing",color=Color.White,fontWeight=FontWeight.ExtraBold);Text("Real market data. Virtual money. Real learning.",color=Color(0xFFB9D8C8),fontSize=10.sp)}}}}

@Composable private fun Market(){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Section("Market Analysis","Understand the big picture")};item{TrendCard()};item{SectorCard()};item{BreadthCard()};item{MarketSnapshotDetail()}}}
@Composable private fun SectorCard(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Text("Sector Performance",fontWeight=FontWeight.ExtraBold);listOf("Banking" to 2.48,"Telecom" to 1.87,"Manufacturing" to .62,"Energy" to -1.21,"Retail" to .34).forEach{(n,c)->Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text(n,fontSize=11.sp,modifier=Modifier.width(100.dp));Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(5.dp)).background(Border)){Box(Modifier.fillMaxWidth((kotlin.math.abs(c)/3.0).toFloat().coerceIn(0f,1f)).height(7.dp).clip(RoundedCornerShape(5.dp)).background(if(c>=0)Green else Red))};Spacer(Modifier.width(7.dp));Text(String.format(Locale.US,"%+.2f%%",c),color=if(c>=0)Green else Red,fontSize=9.sp,fontWeight=FontWeight.Bold)}}}}}
@Composable private fun BreadthCard(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Text("Market Breadth",fontWeight=FontWeight.ExtraBold);Text("Advancers vs decliners",color=Muted,fontSize=10.sp);Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp))){Box(Modifier.weight(78f).fillMaxSize().background(Green));Box(Modifier.weight(42f).fillMaxSize().background(Red))};Row(Modifier.fillMaxWidth().padding(top=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("78 Advancing",color=Green,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("42 Declining",color=Red,fontSize=10.sp,fontWeight=FontWeight.Bold)}}}}
@Composable private fun MarketSnapshotDetail(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Text("Market Snapshot",fontWeight=FontWeight.ExtraBold);Text("Breadth, activity and momentum",color=Muted,fontSize=10.sp);Spacer(Modifier.height(8.dp));listOf("Market volume" to "12.8M shares","Advancing value" to "KSh 418.6M","Declining value" to "KSh 176.2M","Market momentum" to "Positive").forEach{(a,b)->Row(Modifier.fillMaxWidth().padding(vertical=7.dp)){Text(a,Modifier.weight(1f),fontSize=11.sp);Text(b,fontSize=11.sp,fontWeight=FontWeight.Bold,color=if(a=="Market momentum")Green else TextDark)}}}}}

@Composable private fun Companies(open:(Stock)->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Section("Company Intelligence","Understand each company before you invest")};items(stocks){s->Card(Modifier.fillMaxWidth().clickable{open(s)},RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Logo(s.symbol,40);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.ExtraBold,fontSize=13.sp);Text(s.symbol,color=Muted,fontSize=10.sp);Text(String.format(Locale.US,"KSh %.2f",s.price),fontWeight=FontWeight.Bold,fontSize=12.sp)};Text(String.format(Locale.US,"%+.2f%%",s.change),color=if(s.change>=0)Green else Red,fontWeight=FontWeight.ExtraBold)}}}}}
@Composable private fun Company(s: Stock, back: () -> Unit) { val periods=listOf("1D","1W","1M","3M","6M","1Y","5Y"); var period by rememberSaveable(s.symbol){mutableStateOf("1Y")}; var history by remember(s.symbol){mutableStateOf(s.history)}; var loading by remember(s.symbol){mutableStateOf(false)}; LaunchedEffect(s.symbol,period){loading=true;val live=MyStocksCache.loadHistory(s.symbol,period);if(live.size>=2)history=live;loading=false}; LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header(s.name,"${s.symbol} • NSE",back)};item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Column(Modifier.padding(16.dp)){Text("NSE DATA • 15 MIN DELAYED",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(String.format(Locale.US,"KSh %.2f",s.price),fontSize=29.sp,fontWeight=FontWeight.ExtraBold);Text(String.format(Locale.US,"%+.2f%% today",s.change),color=if(s.change>=0)Green else Red,fontWeight=FontWeight.ExtraBold);Text("Exchange-supplied NSE data • analysis only • no real trading",color=Muted,fontSize=9.sp)}}};item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Price History",fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Text("Historical closing prices",color=Muted,fontSize=10.sp)};if(loading)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Green)};Spacer(Modifier.height(9.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){periods.forEach{value->FilterChip(selected=period==value,onClick={period=value},label={Text(value,fontSize=10.sp)})}};Spacer(Modifier.height(8.dp));if(history.size>=2){CompanyHistoryChart(history,if(s.change>=0)Green else Red);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${history.size} data points",color=Muted,fontSize=9.sp);Text(period,color=Green,fontSize=9.sp,fontWeight=FontWeight.Bold)}}else Box(Modifier.fillMaxWidth().height(130.dp),contentAlignment=Alignment.Center){Text("Historical data unavailable",color=Muted,fontSize=11.sp)}}}};item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Text("NSE Watcher Score",fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Text("78 / 100",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=Green);Text("Illustrative score based on momentum, activity and company factors.",color=Muted,fontSize=10.sp)}}}}}
