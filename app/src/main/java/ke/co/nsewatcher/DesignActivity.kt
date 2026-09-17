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
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache

private val Green = Color(0xFF00A859)
private val LightGreen = Color(0xFFE9F8F0)
private val DarkGreen = Color(0xFF083C27)
private val TextDark = Color(0xFF12231B)
private val Muted = Color(0xFF6C7A72)
private val Border = Color(0xFFE1EAE5)
private val Red = Color(0xFFE04444)
private const val PREFS = "nse_watcher_preferences"

data class Stock(val symbol:String,val name:String,val price:Double,val change:Double,val history:List<Double>,val logoUrl:String?=null,val sector:String="Other",val volume:Long=0L)

data class NewsItem(
    val id:String, val title:String, val summary:String, val body:String, val source:String,
    val publishedAt:String, val category:String, val symbol:String, val companyName:String,
    val imageUrl:String, val url:String, val dividendAmount:String, val exDate:String, val paymentDate:String
)
private val liveStocks = mutableStateOf(emptyList<Stock>())
private val stocks: List<Stock> get() = liveStocks.value

private enum class Page { HOME, MARKET, NEWS, COMPANIES, PAPER, MORE, COMPANY, NEWS_DETAIL, PROFILE, SETTINGS, THEME, NOTIFICATIONS, LIVE_DATA, CHARTS, ALERTS, LANGUAGE, SECURITY, PRIVACY, DISPLAY, HELP, ABOUT }

class DesignActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("avatar_uri", uri.toString()).apply()
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App { picker.launch(arrayOf("image/*")) } } }
}

@Composable
private fun App(pickAvatar:()->Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { liveStocks.value = it }
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
            Page.HOME,Page.MARKET,Page.NEWS,Page.COMPANIES,Page.PAPER,Page.MORE -> Scaffold(topBar={if(page!=Page.HOME && page!=Page.MARKET) TopBar(name,::go)},bottomBar={BottomNav(tab){tab=it;history=emptyList();page=when(it){0->Page.HOME;1->Page.MARKET;2->Page.NEWS;3->Page.COMPANIES;else->Page.MORE}}}){pad->Box(Modifier.fillMaxSize().padding(pad)){when(page){Page.HOME->HomeDashboard(stocks,{selected=it;go(Page.COMPANY)},{selectedNews=it;go(Page.NEWS_DETAIL)});Page.MARKET->MarketDashboard(stocks);Page.NEWS->NewsDashboard{selectedNews=it;go(Page.NEWS_DETAIL)};Page.COMPANIES->Companies{selected=it;go(Page.COMPANY)};Page.PAPER->Paper();else->More(::go)}}}
            Page.COMPANY->Company(selected,::back)
            Page.NEWS_DETAIL->selectedNews?.let { NewsDetail(it,::back) }
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
            Page.HELP->HelpPage(::back)
            Page.ABOUT->AboutPage(::back)
            else->{page=Page.HOME}
        }
    }}
}

@Composable private fun TopBar(name:String,go:(Page)->Unit){Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(42.dp),RoundedCornerShape(12.dp),LightGreen){Icon(Icons.Default.ShowChart,null,Modifier.padding(7.dp),Green)};Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text("NSE Watcher",fontSize=18.sp,fontWeight=FontWeight.ExtraBold);Text("Analyse • Understand • Invest Smarter",fontSize=10.sp,color=Muted)};Avatar(name){go(Page.PROFILE)}}}
@Composable private fun Avatar(name:String,onClick:()->Unit){val c=androidx.compose.ui.platform.LocalContext.current;val u=c.getSharedPreferences(PREFS,0).getString("avatar_uri",null);val b by produceState<Bitmap?>(null,u){value=try{u?.let{c.contentResolver.openInputStream(Uri.parse(it))?.use{stream->BitmapFactory.decodeStream(stream)}}}catch(_:Exception){null}};Surface(Modifier.size(38.dp).clip(CircleShape).clickable(onClick=onClick),CircleShape,Color(0xFFDDEFE6)){if(b!=null)Image(b!!.asImageBitmap(),"Profile",Modifier.fillMaxSize())else Box(Modifier.fillMaxSize(),Alignment.Center){Text(name.take(1).uppercase(),color=Green,fontWeight=FontWeight.Bold)}}}
@Composable private fun BottomNav(selected:Int,onSelect:(Int)->Unit){val items=listOf("Home" to Icons.Default.Home,"Market" to Icons.Default.CandlestickChart,"News" to Icons.Default.Article,"Companies" to Icons.Default.Business,"More" to Icons.Default.AutoGraph);NavigationBar{items.forEachIndexed{i,x->NavigationBarItem(selected==i,{onSelect(i)},icon={Icon(x.second,x.first)},label={Text(x.first,fontSize=9.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Green,selectedTextColor=Green,indicatorColor=LightGreen,unselectedIconColor=Muted,unselectedTextColor=Muted))}}}
@Composable private fun Header(title:String,sub:String?=null,back:(()->Unit)?=null){Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){if(back!=null)IconButton(back){Icon(Icons.Default.ArrowBack,"Back")};Column{Text(title,fontSize=20.sp,fontWeight=FontWeight.ExtraBold);if(sub!=null)Text(sub,fontSize=10.sp,color=Muted)}}}

@Composable
private fun Home(open:(Stock)->Unit){
    val gainers=stocks.filter{it.change>0}.sortedByDescending{it.change}.take(4)
    val losers=stocks.filter{it.change<0}.sortedBy{it.change}.take(3)
    val portfolioValue=1254830L
    val totalInvested=920000L
    val totalReturn=portfolioValue-totalInvested
    LazyColumn(contentPadding=PaddingValues(16.dp,8.dp,16.dp,20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Card(Modifier.fillMaxWidth(),RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=DarkGreen)){
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("Good morning,",color=Color.White,fontSize=15.sp)
                        Text("Investor ☀️",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.ExtraBold)
                        Text("Stay informed. Make better decisions.",color=Color(0xFFD5E9DF),fontSize=11.sp)
                    }
                    Surface(Modifier.size(62.dp),CircleShape,color=Color(0xFF0B6B46)){Icon(Icons.Default.Spa,"Growth",tint=Color(0xFF8BE0B3),modifier=Modifier.padding(14.dp))}
                }
            }
        }
        item{
            Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Border)){
                Column(Modifier.padding(12.dp)){
                    Text("Portfolio Value (Paper Invest)",color=DarkGreen,fontSize=11.sp)
                    Text(String.format(Locale.US,"KSh %,d",portfolioValue),fontSize=25.sp,fontWeight=FontWeight.ExtraBold,color=TextDark)
                    Text("▲ 12.4%",color=Green,fontSize=12.sp,fontWeight=FontWeight.ExtraBold)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){Text("vs. last 30 days",color=Muted,fontSize=9.sp)}
                }
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)){
                Card(Modifier.weight(1f),RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Border)){
                    Column(Modifier.padding(10.dp)){Text("Total Invested",color=Muted,fontSize=10.sp);Text(String.format(Locale.US,"KSh %,d",totalInvested),fontWeight=FontWeight.ExtraBold,fontSize=16.sp,color=TextDark)}
                }
                Card(Modifier.weight(1f),RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Border)){
                    Column(Modifier.padding(10.dp)){Text("Total Return",color=Muted,fontSize=10.sp);Text(String.format(Locale.US,"KSh %,d",totalReturn),fontWeight=FontWeight.ExtraBold,fontSize=16.sp,color=TextDark);Text("▲ 36.4%",color=Green,fontSize=10.sp,fontWeight=FontWeight.Bold)}
                }
            }
        }
        item{
            Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Border)){
                Column(Modifier.padding(14.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Market Pulse",fontWeight=FontWeight.ExtraBold,fontSize=17.sp);Spacer(Modifier.weight(1f));Text("15 MIN DELAYED",color=Green,fontSize=9.sp,fontWeight=FontWeight.Bold)}
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Column(Modifier.weight(1f)){Text("Top Gainer",color=Muted,fontSize=9.sp);gainers.firstOrNull()?.let{Text(it.symbol,fontWeight=FontWeight.ExtraBold,fontSize=14.sp);Text(String.format(Locale.US,"%+.2f%%",it.change),color=Green,fontWeight=FontWeight.Bold,fontSize=10.sp)}}
                        Column(Modifier.weight(1f)){Text("Top Loser",color=Muted,fontSize=9.sp);losers.firstOrNull()?.let{Text(it.symbol,fontWeight=FontWeight.ExtraBold,fontSize=14.sp);Text(String.format(Locale.US,"%+.2f%%",it.change),color=Red,fontWeight=FontWeight.Bold,fontSize=10.sp)}}
                    }
                }
            }
        }
        item{
            Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF7E2)),border=BorderStroke(1.dp,Color(0xFFF1E3B8))){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Lightbulb,null,tint=Color(0xFFFFB300),modifier=Modifier.size(28.dp));Spacer(Modifier.width(10.dp));Text("Small steps in learning lead to big results in investing.",fontSize=11.sp,color=TextDark,fontWeight=FontWeight.Medium)}
            }
        }
        item{
            Column{
                Text("Recent Activity",fontWeight=FontWeight.ExtraBold,fontSize=17.sp)
                Spacer(Modifier.height(7.dp))
                Card(Modifier.fillMaxWidth(),RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,Border)){
                    Column(Modifier.padding(horizontal=13.dp)){
                        gainers.take(3).forEachIndexed{index,s->Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Logo(s.symbol,30);Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.Bold,fontSize=11.sp);Text("Market watch",color=Muted,fontSize=9.sp)};Text(String.format(Locale.US,"%+.2f%%",s.change),color=Green,fontWeight=FontWeight.Bold,fontSize=10.sp)}}
                    }
                }
            }
        }
    }
}
@Composable private fun MarketStatusCard(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Column(Modifier.padding(15.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).clip(CircleShape).background(Green));Spacer(Modifier.width(7.dp));Text("NSE MARKET",color=DarkGreen,fontWeight=FontWeight.ExtraBold,fontSize=12.sp);Spacer(Modifier.weight(1f));Text("DATA FEED",color=Muted,fontSize=10.sp)};Spacer(Modifier.height(10.dp));Row(verticalAlignment=Alignment.Bottom){Column(Modifier.weight(1f)){Text("Market overview",color=Muted,fontSize=11.sp);Text("Clear picture of the NSE",fontSize=19.sp,fontWeight=FontWeight.ExtraBold);Text("before you make an investment decision.",fontSize=11.sp,color=Muted)};Surface(shape=RoundedCornerShape(10.dp),color=Color.White){Text("15 MIN DELAYED",color=Green,fontWeight=FontWeight.ExtraBold,fontSize=11.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp))}}}}}
@Composable private fun IndexRow(){val x=listOf("NSE 20" to "1,843.56","NASI" to "112.48","NSE 25" to "3,642.17");LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(x){(n,v)->Card(Modifier.width(145.dp),RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(11.dp)){Text(n,fontSize=11.sp,fontWeight=FontWeight.Bold);Text(v,fontSize=17.sp,fontWeight=FontWeight.ExtraBold);Text("▲ +1.34%",color=Green,fontSize=10.sp,fontWeight=FontWeight.Bold)}}}}}
@Composable private fun TrendCard(){var p by rememberSaveable{mutableStateOf("1D")};Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("NSE 20 — Market Trend",fontWeight=FontWeight.ExtraBold,fontSize=15.sp);Text("See how the market has been performing",color=Muted,fontSize=10.sp)};Text("+1.34%",color=Green,fontWeight=FontWeight.ExtraBold)};Spacer(Modifier.height(8.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("1D","1W","1M","3M","6M","1Y","5Y").forEach{x->FilterChip(selected=p==x,onClick={p=x},label={Text(x,fontSize=10.sp)})}};Spacer(Modifier.height(5.dp));Chart(listOf(28f,38f,34f,47f,44f,58f,52f,67f,61f,74f,69f,83f),Green);Text("Historical performance will use sourced NSE market data in live mode.",color=Muted,fontSize=9.sp)}}}
@Composable private fun Chart(values:List<Float>,tint:Color){Canvas(Modifier.fillMaxWidth().height(95.dp).padding(vertical=8.dp)){val path=Path();values.forEachIndexed{i,v->{val x=size.width*i/(values.size-1);val y=size.height-(v/100f*size.height);if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}};drawPath(path=path,color=tint,style=Stroke(width=4f,cap=StrokeCap.Round))}}
@Composable private fun Section(t:String,s:String){Column{Text(t,fontWeight=FontWeight.ExtraBold,fontSize=16.sp);Text(s,color=Muted,fontSize=10.sp)}}
@Composable private fun SnapshotActions(){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){Tile(Icons.Default.AutoGraph,"Sectors","Performance",Modifier.weight(1f));Tile(Icons.Default.ShowChart,"Top Movers","Gainers & Losers",Modifier.weight(1f));Tile(Icons.Default.Business,"Market Analysis","Trends & Outlook",Modifier.weight(1f));Tile(Icons.Default.Article,"News & Events","Latest Updates",Modifier.weight(1f))}}
@Composable private fun Tile(i:ImageVector,t:String,s:String,m:Modifier){Card(m,RoundedCornerShape(13.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(i,null,tint=Green,modifier=Modifier.size(22.dp));Spacer(Modifier.height(4.dp));Text(t,fontWeight=FontWeight.Bold,fontSize=10.sp,textAlign=TextAlign.Center);Text(s,color=Muted,fontSize=7.sp,textAlign=TextAlign.Center)}}}
@Composable private fun Movers(g:List<Stock>,l:List<Stock>,open:(Stock)->Unit){Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Row(Modifier.padding(vertical=12.dp)){MoverList("Top Gainers",g,Green,open,Modifier.weight(1f));Box(Modifier.width(1.dp).height(165.dp).background(Border));MoverList("Top Losers",l,Red,open,Modifier.weight(1f))}}}
@Composable private fun MoverList(title:String,list:List<Stock>,tint:Color,open:(Stock)->Unit,m:Modifier){Column(m.padding(horizontal=11.dp)){Text(title,color=tint,fontWeight=FontWeight.ExtraBold,fontSize=12.sp);Spacer(Modifier.height(7.dp));list.forEach{s->Row(Modifier.fillMaxWidth().clickable{open(s)}.padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Logo(s.symbol,27,s.logoUrl);Spacer(Modifier.width(6.dp));Text(s.symbol,fontWeight=FontWeight.Bold,fontSize=11.sp,modifier=Modifier.weight(1f));Text(String.format(Locale.US,"%+.2f%%",s.change),color=tint,fontWeight=FontWeight.ExtraBold,fontSize=10.sp)}}}}
@Composable private fun Logo(symbol:String,size:Int,logoUrl:String?=null){
    val resolvedUrl = logoUrl?.takeIf{it.isNotBlank()} ?: "https://mystocks.africa/logos/${symbol.lowercase(Locale.US)}-ke.svg"
    Surface(Modifier.size(size.dp),RoundedCornerShape(8.dp),when(symbol){"SCOM"->Color(0xFF0B8F4D);"KCB"->Color(0xFF1B4D9B);"EQTY"->Color(0xFF137A45);"ABSA"->Color(0xFFC6283D);"COOP"->Color(0xFF1769AA);"EABL"->Color(0xFFB8A23A);else->Color(0xFF285C8C)}){
        Box(Modifier.fillMaxSize(),Alignment.Center){
            AsyncImage(model=resolvedUrl,contentDescription=symbol,modifier=Modifier.fillMaxSize().padding(5.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
            Text(symbol.take(3),color=Color.White.copy(alpha=.85f),fontWeight=FontWeight.ExtraBold,fontSize=8.sp)
        }
    }
}
@Composable private fun StockMini(s:Stock,open:()->Unit){Card(Modifier.width(145.dp).clickable(onClick=open),RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(10.dp)){Row(verticalAlignment=Alignment.CenterVertically){Logo(s.symbol,27,s.logoUrl);Spacer(Modifier.width(6.dp));Text(s.symbol,fontWeight=FontWeight.ExtraBold,fontSize=11.sp)};Text(String.format(Locale.US,"KSh %.2f",s.price),fontWeight=FontWeight.ExtraBold,fontSize=15.sp);Text(String.format(Locale.US,"%+.2f%%",s.change),color=if(s.change>=0)Green else Red,fontWeight=FontWeight.Bold,fontSize=10.sp);Chart(s.history.map{it.toFloat()},if(s.change>=0)Green else Red)}}}
@Composable private fun NewsPreview(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Border)){Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(58.dp),RoundedCornerShape(10.dp),LightGreen){Icon(Icons.Default.Article,null,tint=Green,modifier=Modifier.padding(17.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("NSE market maintaining bullish trend as investors regain confidence",fontWeight=FontWeight.Bold,fontSize=12.sp);Text("Business Daily • 2h ago",color=Muted,fontSize=9.sp);Text("Market movement and company news",color=Muted,fontSize=9.sp)};Icon(Icons.Default.ChevronRight,null,tint=Muted)}}}
@Composable private fun PaperBanner(){Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=DarkGreen)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(42.dp),CircleShape,Green){Icon(Icons.Default.AccountBalanceWallet,null,tint=Color.White,modifier=Modifier.padding(9.dp))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("Paper Investing",color=Color.White,fontWeight=FontWeight.ExtraBold);Text("Real market data. Virtual money. Real learning.",color=Color(0xFFB9D8C8),fontSize=10.sp)};Text("Try it",color=Color.White,fontWeight=FontWeight.Bold)}}}

private data class BreadthStats(val advancing:Int,val declining:Int,val unchanged:Int){
    val total:Int get() = advancing + declining + unchanged
    val net:Int get() = advancing - declining
}

private fun marketBreadth(): BreadthStats {
    val valid = stocks.filter { it.change.isFinite() }
    return BreadthStats(valid.count { it.change > 0.0 },valid.count { it.change < 0.0 },valid.count { it.change == 0.0 })
}

private fun formatShares(value:Long):String = when {
    value >= 1_000_000_000L -> String.format(Locale.US,"%.2fB",value / 1_000_000_000.0)
    value >= 1_000_000L -> String.format(Locale.US,"%.2fM",value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US,"%.1fK",value / 1_000.0)
    else -> String.format(Locale.US,"%,d",value)
}

@Composable private fun Market(){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Section("Market Analysis","Understand the big picture")};item{TrendCard()};item{SectorCard()};item{BreadthCard()};item{MarketSnapshotDetail()}}}

@Composable private fun SectorCard(){
    val rows=stocks.filter{it.sector.isNotBlank()&&it.sector!="Other"&&it.change.isFinite()}.groupBy{it.sector.trim()}.map{(sector,members)->sector to members.map{it.change}.average()}.sortedByDescending{kotlin.math.abs(it.second)}.take(6)
    val maxAbs=rows.maxOfOrNull{kotlin.math.abs(it.second)}?.coerceAtLeast(0.01)?:1.0
    Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){
        Text("Sector Performance",fontWeight=FontWeight.ExtraBold)
        Text("Average daily move of available NSE constituents",color=Muted,fontSize=10.sp)
        Spacer(Modifier.height(6.dp))
        if(rows.isEmpty()) Text("Sector data unavailable",color=Muted,fontSize=11.sp) else {rows.forEach{(sector,change)->Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text(sector,fontSize=11.sp,modifier=Modifier.width(118.dp),maxLines=1);Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(5.dp)).background(Border)){Box(Modifier.fillMaxWidth((kotlin.math.abs(change)/maxAbs).toFloat().coerceIn(0f,1f)).height(7.dp).clip(RoundedCornerShape(5.dp)).background(if(change>=0)Green else Red))};Spacer(Modifier.width(7.dp));Text(String.format(Locale.US,"%+.2f%%",change),color=if(change>=0)Green else Red,fontSize=9.sp,fontWeight=FontWeight.Bold)}};Spacer(Modifier.height(3.dp));Text("Equal-weighted average; not an official NSE sector index level.",color=Muted,fontSize=8.sp)}
    }}
}

@Composable private fun BreadthCard(){val breadth=marketBreadth();val total=breadth.total.coerceAtLeast(1);Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){
    Text("Market Breadth",fontWeight=FontWeight.ExtraBold)
    Text("Advancing, declining and unchanged stocks with valid daily moves",color=Muted,fontSize=10.sp)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp))){if(breadth.advancing>0)Box(Modifier.weight(breadth.advancing.toFloat()).fillMaxSize().background(Green));if(breadth.unchanged>0)Box(Modifier.weight(breadth.unchanged.toFloat()).fillMaxSize().background(Border));if(breadth.declining>0)Box(Modifier.weight(breadth.declining.toFloat()).fillMaxSize().background(Red))}
    Row(Modifier.fillMaxWidth().padding(top=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("${breadth.advancing} Advancing",color=Green,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("${breadth.unchanged} Unchanged",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("${breadth.declining} Declining",color=Red,fontSize=10.sp,fontWeight=FontWeight.Bold)}
    Spacer(Modifier.height(6.dp));Text("Net breadth: ${if(breadth.net>=0) "+" else ""}${breadth.net} • ${String.format(Locale.US,"%.1f%%",breadth.advancing*100.0/total)} advancing",color=TextDark,fontSize=9.sp,fontWeight=FontWeight.SemiBold)
}}}

@Composable private fun MarketSnapshotDetail(){val breadth=marketBreadth();val reportedVolume=stocks.sumOf{it.volume.coerceAtLeast(0L)};val volumeStocks=stocks.count{it.volume>0L};Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){
    Text("Market Snapshot",fontWeight=FontWeight.ExtraBold)
    Text("Activity and breadth from the current NSE stock feed",color=Muted,fontSize=10.sp)
    Spacer(Modifier.height(8.dp))
    listOf("Stocks covered" to String.format(Locale.US,"%,d",stocks.size),"Reported share volume" to "${formatShares(reportedVolume)} shares","Advancing / declining" to "${breadth.advancing} / ${breadth.declining}","Net breadth" to "${if(breadth.net>=0) "+" else ""}${breadth.net}").forEach{(label,value)->Row(Modifier.fillMaxWidth().padding(vertical=7.dp)){Text(label,Modifier.weight(1f),fontSize=11.sp);Text(value,fontSize=11.sp,fontWeight=FontWeight.Bold,color=if(label=="Net breadth") Green else TextDark)}}
    Text("Volume is the sum of provider-reported per-stock volume for $volumeStocks counters; it is not a replacement for the NSE's official session turnover report.",color=Muted,fontSize=8.sp)
    Spacer(Modifier.height(3.dp));Text("MyStocks Africa • exchange-supplied • 15 min delayed",color=Muted,fontSize=8.sp,fontWeight=FontWeight.SemiBold)
}}}

@Composable
private fun Companies(open:(Stock)->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    val filtered=stocks.filter{it.name.contains(query,true)||it.symbol.contains(query,true)}
    Box(Modifier.fillMaxSize().background(Color(0xFF062A23))){
        LazyColumn(contentPadding=PaddingValues(16.dp,0.dp,16.dp,20.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
            item{
                Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(bottomStart=26.dp,bottomEnd=26.dp))){
                    Image(painter=androidx.compose.ui.res.painterResource(id=ke.co.nsewatcher.R.drawable.companies_city_background),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color(0x99052B24)))
                    Column(Modifier.fillMaxSize().padding(10.dp,20.dp,10.dp,18.dp),verticalArrangement=Arrangement.Bottom){Text("Discover",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Text("Great Companies",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.height(5.dp));Text("Research. Analyze. Invest.\nFind the right companies for your future.",color=Color.White,fontSize=11.sp)}
                }
            }
            item{OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search companies...",color=Muted)},leadingIcon={Icon(Icons.Default.Search,null,tint=Muted)},shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Color.White,focusedContainerColor=Color.White,unfocusedBorderColor=Color.Transparent,focusedBorderColor=Green,unfocusedTextColor=TextDark,focusedTextColor=TextDark))}
            item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Companies",color=Color.White,fontWeight=FontWeight.ExtraBold,fontSize=17.sp);Spacer(Modifier.weight(1f));Text("View all",color=Color(0xFF55E0A0),fontSize=11.sp,fontWeight=FontWeight.Bold)}}
            item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(horizontal=12.dp)){filtered.forEachIndexed{index,s->Row(Modifier.fillMaxWidth().clickable{open(s)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Logo(s.symbol,40,s.logoUrl);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.name,color=TextDark,fontWeight=FontWeight.ExtraBold,fontSize=12.sp);Text(s.symbol,color=Muted,fontSize=10.sp);Text(String.format(Locale.US,"KSh %.2f",s.price),color=Muted,fontSize=10.sp)};Column(horizontalAlignment=Alignment.End){Text(String.format(Locale.US,"KSh %.2f",s.price),color=TextDark,fontWeight=FontWeight.Bold,fontSize=11.sp);Text(String.format(Locale.US,"%+.1f%%",s.change),color=if(s.change>=0)Green else Red,fontWeight=FontWeight.Bold,fontSize=10.sp)}};if(index<filtered.lastIndex)HorizontalDivider(color=Border)}}}}
        }
    }
}
@Composable
private fun Company(s: Stock, back: () -> Unit) {
    CompanyIntelligence(s, back)
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
            AsyncImage(model="https://upload.wikimedia.org/wikipedia/commons/8/80/Nairobi_Skyline_from_West.jpg",contentDescription="Nairobi skyline",modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0xB3083C27)))
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
        if(detail.url.isNotBlank()) item{Button({/* source URL will be wired to an external browser in the next link pass */},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Icon(Icons.Default.Link,null);Spacer(Modifier.width(7.dp));Text("Source / Related Link")}}
        item{Text("NSE Watcher presents sourced information for analysis. News is not a recommendation to buy or sell a security. For material corporate actions, confirm the issuer's official announcement or exchange filing.",color=Muted,fontSize=9.sp)}
    }
}

private fun newsDate(value:String):String=when{value.isBlank()->"Latest";value.length>=10->value.take(10);else->value}

@Composable private fun Paper(){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){item{Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=DarkGreen)){Column(Modifier.padding(17.dp)){Text("PAPER PORTFOLIO",color=Color.White,fontWeight=FontWeight.Bold);Text("KSh 100,000",color=Color.White,fontSize=29.sp,fontWeight=FontWeight.ExtraBold);Text("Virtual balance • No real money",color=Color.White,fontSize=10.sp)}}};item{Section("Holdings","Hypothetical investments follow market movement")};item{RowItem(Icons.Default.Business,"SCOM","100 shares • KSh 1,850 value")};item{RowItem(Icons.Default.Business,"KCB","50 shares • KSh 2,115 value")};item{Note("Paper Investing is educational and does not place trades with a broker.")}}}
@Composable private fun More(go:(Page)->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){item{Section("More","Your NSE Watcher tools")};item{RowItem(Icons.Default.AccountCircle,"Profile","Personal information and profile picture"){go(Page.PROFILE)}};item{RowItem(Icons.Default.AccountBalanceWallet,"Paper Investing","Practice with virtual money"){go(Page.PAPER)}};item{RowItem(Icons.Default.Settings,"Settings","Theme, notifications, data and privacy"){go(Page.SETTINGS)}};item{RowItem(Icons.Default.HelpOutline,"Help & Support","FAQs, contact and report issues"){go(Page.HELP)}};item{RowItem(Icons.Default.Info,"About NSE Watcher","Version and product information"){go(Page.ABOUT)}}}}

@Composable private fun Profile(name:String,username:String,email:String,description:String,onName:(String)->Unit,onUsername:(String)->Unit,onEmail:(String)->Unit,onDescription:(String)->Unit,pick:()->Unit,back:()->Unit,go:(Page)->Unit){var editing by rememberSaveable{mutableStateOf(false)};var n by rememberSaveable(name){mutableStateOf(name)};var u by rememberSaveable(username){mutableStateOf(username)};var e by rememberSaveable(email){mutableStateOf(email)};var d by rememberSaveable(description){mutableStateOf(description)};LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Header("Profile","Your NSE Watcher account",back)};item{ProfileHero(name,username,pick)};item{if(editing){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileField("Full name",n,{n=it},Icons.Default.Person);ProfileField("Username",u,{u=it},Icons.Default.AccountCircle);ProfileField("Email",e,{e=it},Icons.Default.Email);ProfileField("Description",d,{d=it},Icons.Default.Info);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({editing=false}){Text("Cancel")};Button({onName(n);onUsername(u);onEmail(e);onDescription(d);editing=false},colors=ButtonDefaults.buttonColors(containerColor=Green)){Text("Save")}}}}}else{Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){ProfileInfo(Icons.Default.AccountCircle,"Username","@$username");ProfileInfo(Icons.Default.Email,"Email",email);ProfileInfo(Icons.Default.Info,"Description",description);Spacer(Modifier.height(5.dp));Button({editing=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Green)){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(7.dp));Text("Edit Profile")}}}}};item{RowItem(Icons.Default.PhotoCamera,"Profile Picture","Change your profile photo",pick)};item{RowItem(Icons.Default.AccountBalanceWallet,"Paper Investing","Practice with virtual money"){go(Page.PAPER)}};item{RowItem(Icons.Default.Settings,"Settings","Theme, notifications, data and privacy"){go(Page.SETTINGS)}};item{RowItem(Icons.Default.Lock,"Account Security","Password and device protection"){go(Page.SECURITY)}};item{RowItem(Icons.Default.PrivacyTip,"Privacy","Review your privacy settings"){go(Page.PRIVACY)}};item{Note("NSE Watcher provides market information and analysis. It does not execute trades or guarantee returns.")}}}
@Composable private fun ProfileHero(name:String,username:String,pick:()->Unit){val c=androidx.compose.ui.platform.LocalContext.current;val u=c.getSharedPreferences(PREFS,0).getString("avatar_uri",null);val b by produceState<Bitmap?>(null,u){value=try{u?.let{c.contentResolver.openInputStream(Uri.parse(it))?.use{stream->BitmapFactory.decodeStream(stream)}}}catch(_:Exception){null}};Card(Modifier.fillMaxWidth(),RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(100.dp)){Surface(Modifier.fillMaxSize(),CircleShape,Color.White){if(b!=null)Image(b!!.asImageBitmap(),"Profile picture",Modifier.fillMaxSize())else Icon(Icons.Default.Person,null,tint=Green,modifier=Modifier.padding(25.dp))};Surface(Modifier.size(32.dp).align(Alignment.BottomEnd).clickable(onClick=pick),CircleShape,Green){Icon(Icons.Default.PhotoCamera,"Change profile picture",modifier=Modifier.padding(7.dp),tint=Color.White)}};Spacer(Modifier.height(10.dp));Text(name,fontSize=21.sp,fontWeight=FontWeight.ExtraBold);Text("@$username",fontSize=11.sp,color=Muted)}}}
@Composable private fun ProfileField(label:String,value:String,onValue:(String)->Unit,i:ImageVector){OutlinedTextField(value,onValue,Modifier.fillMaxWidth().padding(bottom=8.dp),label={Text(label)},leadingIcon={Icon(i,null,tint=Green)},singleLine=label!="Description")}
@Composable private fun ProfileInfo(i:ImageVector,label:String,value:String){Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(38.dp),CircleShape,LightGreen){Icon(i,null,tint=Green,modifier=Modifier.padding(9.dp))};Spacer(Modifier.width(11.dp));Column{Text(label,fontSize=10.sp,color=Muted);Text(value,fontSize=13.sp,fontWeight=FontWeight.Bold)}}}

@Composable
private fun Settings(dark:Boolean,market:Boolean,price:Boolean,news:Boolean,app:Boolean,refresh:Boolean,volume:Boolean,changes:Boolean,onDark:(Boolean)->Unit,onMarket:(Boolean)->Unit,onPrice:(Boolean)->Unit,onNews:(Boolean)->Unit,onApp:(Boolean)->Unit,onRefresh:(Boolean)->Unit,onVolume:(Boolean)->Unit,onChanges:(Boolean)->Unit,back:()->Unit,go:(Page)->Unit) {
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Header("Settings","Customize NSE Watcher",back) }
        item { SettingsCard("Appearance",Icons.Default.Palette) { ToggleRow(Icons.Default.DarkMode,"Dark theme","Switch between light and dark mode",dark,onDark); RowItem(Icons.Default.Palette,"Theme","Light / Dark / System"){go(Page.THEME)}; RowItem(Icons.Default.Language,"Language","English • Kenya • KSh"){go(Page.LANGUAGE)}; RowItem(Icons.Default.Visibility,"Font & Display","Size, density and animations"){go(Page.DISPLAY)} } }
        item { SettingsCard("Notifications",Icons.Default.Notifications) { ToggleRow(Icons.Default.NotificationsActive,"Market alerts","Important NSE market updates",market,onMarket); ToggleRow(Icons.Default.PriceChange,"Price alerts","Watchlist and threshold alerts",price,onPrice); ToggleRow(Icons.Default.Article,"News alerts","Company and market news",news,onNews); ToggleRow(Icons.Default.Apps,"App notifications","Product and service updates",app,onApp); RowItem(Icons.Default.Notifications,"Notification settings","Detailed notification controls"){go(Page.NOTIFICATIONS)} } }
        item { SettingsCard("Market & Data",Icons.Default.ShowChart) { ToggleRow(Icons.Default.Sync,"Auto refresh","Refresh market information automatically",refresh,onRefresh); ToggleRow(Icons.Default.BarChart,"Show volume","Display trading volume where available",volume,onVolume); ToggleRow(Icons.Default.TrendingUp,"Show price changes","Display daily gains and losses",changes,onChanges); RowItem(Icons.Default.ShowChart,"Live data settings","Refresh and market data preferences"){go(Page.LIVE_DATA)}; RowItem(Icons.Default.Tune,"Chart settings","Timeframes, style and indicators"){go(Page.CHARTS)}; RowItem(Icons.Default.NotificationsNone,"Price alerts","Manage alert rules"){go(Page.ALERTS)} } }
        item { SettingsCard("Account & Privacy",Icons.Default.AccountCircle) { RowItem(Icons.Default.Lock,"Account security","Password, sessions and protection"){go(Page.SECURITY)}; RowItem(Icons.Default.PrivacyTip,"Privacy","Personalisation and data controls"){go(Page.PRIVACY)} } }
        item { SettingsCard("Support",Icons.Default.HelpOutline) { RowItem(Icons.Default.HelpOutline,"Help & Support","FAQs, feedback and report issues"){go(Page.HELP)}; RowItem(Icons.Default.Info,"About NSE Watcher","Version and product information"){go(Page.ABOUT)} } }
        item { Note("NSE Watcher is an analysis and education product. Paper Investing uses virtual money only and does not place real trades.") }
    }
}
@Composable private fun SettingsCard(title:String,icon:ImageVector,content:@Composable ColumnScope.()->Unit){Card(Modifier.fillMaxWidth(),RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Border)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Surface(Modifier.size(34.dp),CircleShape,LightGreen){Icon(icon,null,tint=Green,modifier=Modifier.padding(8.dp))};Spacer(Modifier.width(9.dp));Text(title,fontWeight=FontWeight.ExtraBold,fontSize=15.sp)};Spacer(Modifier.height(4.dp));content()}}}
@Composable private fun ToggleRow(icon:ImageVector,title:String,sub:String,checked:Boolean,onChecked:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(21.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Switch(checked,onChecked)}}
@Composable private fun RowItem(icon:ImageVector,title:String,sub:String,onClick:()->Unit={}){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Green,modifier=Modifier.size(22.dp));Spacer(Modifier.width(11.dp));Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold);Text(sub,fontSize=9.sp,color=Muted)};Icon(Icons.Default.ChevronRight,null,tint=Muted,modifier=Modifier.size(19.dp))}}
@Composable private fun ThemePage(dark:Boolean,set:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Theme","Choose how NSE Watcher looks",back)};item{ThemeChoice("Light","Bright white interface",!dark){set(false)}};item{ThemeChoice("Dark","Low-light interface",dark){set(true)}};item{ThemeChoice("System","Follow your device setting",false){}}}}
@Composable private fun ThemeChoice(title:String,sub:String,selected:Boolean,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable(onClick=onClick),RoundedCornerShape(16.dp),border=BorderStroke(if(selected)2.dp else 1.dp,if(selected)Green else Border),colors=CardDefaults.cardColors(containerColor=if(selected)LightGreen else MaterialTheme.colorScheme.surface)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(title=="Light")Icons.Default.LightMode else if(title=="Dark")Icons.Default.DarkMode else Icons.Default.SettingsSystemDaydream,null,tint=Green);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(sub,fontSize=10.sp,color=Muted)};if(selected)Icon(Icons.Default.CheckCircle,null,tint=Green)}}}
@Composable private fun NotificationsPage(m:Boolean,p:Boolean,n:Boolean,a:Boolean,sm:(Boolean)->Unit,sp:(Boolean)->Unit,sn:(Boolean)->Unit,sa:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Notifications","Choose what you want to hear about",back)};item{SettingsCard("Notification Types",Icons.Default.Notifications){ToggleRow(Icons.Default.ShowChart,"Market updates","NSE-wide movements and daily briefs",m,sm);ToggleRow(Icons.Default.PriceChange,"Price alerts","Your watchlist thresholds",p,sp);ToggleRow(Icons.Default.Article,"News alerts","Company and market news",n,sn);ToggleRow(Icons.Default.Apps,"App notifications","Product updates",a,sa)}};item{Note("Notifications will use real market events when live data and alert services are connected.")}}}
@Composable private fun LiveData(refresh:Boolean,volume:Boolean,changes:Boolean,sr:(Boolean)->Unit,sv:(Boolean)->Unit,sc:(Boolean)->Unit,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Live Data","Market-data display preferences",back)};item{SettingsCard("Data Display",Icons.Default.ShowChart){ToggleRow(Icons.Default.Sync,"Auto refresh","Keep market information current",refresh,sr);ToggleRow(Icons.Default.BarChart,"Show volume","Display trading activity",volume,sv);ToggleRow(Icons.Default.TrendingUp,"Show price changes","Display daily percentage moves",changes,sc)}}}}
@Composable private fun SimplePage(title:String,icon:ImageVector,rows:List<Pair<String,String>>,back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header(title,null,back)};item{SettingsCard(title,icon){rows.forEach{RowItem(icon,it.first,it.second)}}}}}
@Composable private fun HelpPage(back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("Help & Support","Get help using NSE Watcher",back)};item{SettingsCard("Support",Icons.Default.HelpOutline){RowItem(Icons.Default.MenuBook,"Getting started","Learn how to read the market dashboard");RowItem(Icons.Default.QuestionMark,"Frequently asked questions","Common NSE Watcher questions");RowItem(Icons.Default.ReportProblem,"Report a problem","Tell us about an issue")}}}}
@Composable private fun AboutPage(back:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Header("About NSE Watcher","Market intelligence for the NSE",back)};item{SettingsCard("NSE Watcher",Icons.Default.Info){Text("Version 0.1.0",fontWeight=FontWeight.Bold);Text("Trading apps help you buy. NSE Watcher helps you understand what you're buying.",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=7.dp));Spacer(Modifier.height(9.dp));Text("NSE Watcher does not execute real trades and does not guarantee investment returns.",fontSize=10.sp,color=Muted)}}}}
@Composable private fun Note(text:String){Card(Modifier.fillMaxWidth(),RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=LightGreen)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null,tint=Green);Spacer(Modifier.width(9.dp));Text(text,fontSize=9.sp,color=Muted)}}}