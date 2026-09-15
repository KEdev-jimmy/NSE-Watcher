package ke.co.nsewatcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val NSEGreen = Color(0xFF00A859)
private val NSELight = Color(0xFFE9F8F0)
private val NSEDark = Color(0xFF083C27)
private val NSEText = Color(0xFF12231B)
private val NSEMuted = Color(0xFF6C7A72)
private val NSEBorder = Color(0xFFE1EAE5)
private val NSERed = Color(0xFFE04444)

private data class DesignStock(val symbol: String, val name: String, val price: Double, val change: Double, val history: List<Double>)

private val designStocks = listOf(
    DesignStock("SCOM", "Safaricom", 18.50, 5.24, listOf(15.2, 15.5, 15.3, 16.1, 16.8, 16.5, 17.2, 17.9, 18.5)),
    DesignStock("KCB", "KCB Group", 42.30, 3.26, listOf(38.0, 38.8, 39.2, 40.1, 39.7, 40.8, 41.5, 41.9, 42.3)),
    DesignStock("EQTY", "Equity Group", 46.75, 2.98, listOf(43.2, 43.8, 44.0, 44.9, 44.5, 45.1, 45.8, 46.1, 46.75)),
    DesignStock("COOP", "Co-operative Bank", 21.10, 2.41, listOf(19.5, 19.7, 20.0, 19.9, 20.3, 20.5, 20.8, 20.9, 21.1)),
    DesignStock("ABSA", "Absa Bank Kenya", 14.30, -2.17, listOf(15.5, 15.2, 15.0, 14.8, 14.9, 14.6, 14.7, 14.5, 14.3)),
    DesignStock("EABL", "East African Breweries", 155.00, -1.81, listOf(161.0, 160.5, 159.8, 158.7, 159.2, 157.8, 157.0, 156.2, 155.0)),
    DesignStock("KPLC", "Kenya Power", 4.82, -1.22, listOf(5.2, 5.1, 5.0, 5.05, 4.9, 4.95, 4.88, 4.86, 4.82))
)

private enum class Page { HOME, MARKET, COMPANIES, PAPER, MORE, COMPANY, PROFILE, SETTINGS, THEME, NOTIFICATIONS, LIVE_DATA, CHARTS, ALERTS, LANGUAGE, SECURITY, HELP, ABOUT }

class DesignActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("avatar_uri", uri.toString()).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignApp(onPickAvatar = { picker.launch(arrayOf("image/*")) }) }
    }
}

private const val PREFS = "nse_watcher_preferences"

@Composable
private fun DesignApp(onPickAvatar: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var previousPage by rememberSaveable { mutableStateOf(Page.HOME) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf("SCOM") }
    var profileName by rememberSaveable { mutableStateOf(prefs.getString("profile_name", "James Waweru") ?: "James Waweru") }
    var username by rememberSaveable { mutableStateOf(prefs.getString("username", "jameswaweru") ?: "jameswaweru") }
    var email by rememberSaveable { mutableStateOf(prefs.getString("email", "jameswaweru@gmail.com") ?: "jameswaweru@gmail.com") }
    var description by rememberSaveable { mutableStateOf(prefs.getString("description", "IT Graduate | Investor | Learner") ?: "IT Graduate | Investor | Learner") }
    var dark by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var marketAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("market_alerts", true)) }
    var priceAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("price_alerts", true)) }
    var newsAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("news_alerts", true)) }
    var appAlerts by rememberSaveable { mutableStateOf(prefs.getBoolean("app_alerts", true)) }
    var showVolume by rememberSaveable { mutableStateOf(prefs.getBoolean("show_volume", true)) }
    var showChanges by rememberSaveable { mutableStateOf(prefs.getBoolean("show_changes", true)) }
    var autoRefresh by rememberSaveable { mutableStateOf(prefs.getBoolean("auto_refresh", true)) }

    fun save(key: String, value: Any) {
        val edit = prefs.edit()
        when (value) { is Boolean -> edit.putBoolean(key, value); is String -> edit.putString(key, value) }
        edit.apply()
    }
    fun go(to: Page) { previousPage = page; page = to }
    fun back() { page = if (page == Page.COMPANY || page == Page.PROFILE || page == Page.SETTINGS || page == Page.THEME || page == Page.NOTIFICATIONS || page == Page.LIVE_DATA || page == Page.CHARTS || page == Page.ALERTS || page == Page.LANGUAGE || page == Page.SECURITY || page == Page.HELP || page == Page.ABOUT) previousPage else Page.HOME }

    androidx.activity.compose.BackHandler(enabled = page != Page.HOME) { back() }

    val scheme = if (dark) androidx.compose.material3.darkColorScheme(primary = Color(0xFF32D486), background = Color(0xFF0D1712), surface = Color(0xFF132019), onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFB7C7BE)) else androidx.compose.material3.lightColorScheme(primary = NSEGreen, background = Color.White, surface = Color.White, onBackground = NSEText, onSurface = NSEText, onSurfaceVariant = NSEMuted)
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            when (page) {
                Page.HOME, Page.MARKET, Page.COMPANIES, Page.PAPER, Page.MORE -> Scaffold(
                    topBar = { MainTopBar(profileName, go, onSearch = {}) },
                    bottomBar = { DesignNavigation(tab) { tab = it; page = when (it) { 0 -> Page.HOME; 1 -> Page.MARKET; 2 -> Page.COMPANIES; 3 -> Page.PAPER; else -> Page.MORE } } }
                ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) { Page.HOME -> HomeScreen { selected = it; go(Page.COMPANY) }; Page.MARKET -> MarketScreen(); Page.COMPANIES -> CompaniesScreen { selected = it; go(Page.COMPANY) }; Page.PAPER -> PaperInvestScreen(); else -> MoreScreen(go) }
                } }
                Page.COMPANY -> CompanyScreen(designStocks.first { it.symbol == selected }, ::back)
                Page.PROFILE -> ProfileScreen(profileName, username, email, description, prefs.getString("avatar_uri", null), onName = { profileName = it; save("profile_name", it) }, onUsername = { username = it; save("username", it) }, onEmail = { email = it; save("email", it) }, onDescription = { description = it; save("description", it) }, onPickAvatar = onPickAvatar, goBack = ::back, go = ::go)
                Page.SETTINGS -> SettingsScreen(dark, marketAlerts, priceAlerts, newsAlerts, appAlerts, showVolume, showChanges, autoRefresh, onDark = { dark = it; save("dark_mode", it) }, onMarket = { marketAlerts = it; save("market_alerts", it) }, onPrice = { priceAlerts = it; save("price_alerts", it) }, onNews = { newsAlerts = it; save("news_alerts", it) }, onApp = { appAlerts = it; save("app_alerts", it) }, onVolume = { showVolume = it; save("show_volume", it) }, onChanges = { showChanges = it; save("show_changes", it) }, onRefresh = { autoRefresh = it; save("auto_refresh", it) }, goBack = ::back, go = ::go)
                Page.THEME -> ThemeScreen(dark, { dark = it; save("dark_mode", it) }, ::back)
                Page.NOTIFICATIONS -> NotificationsScreen(marketAlerts, priceAlerts, newsAlerts, appAlerts, { marketAlerts = it; save("market_alerts", it) }, { priceAlerts = it; save("price_alerts", it) }, { newsAlerts = it; save("news_alerts", it) }, { appAlerts = it; save("app_alerts", it) }, ::back)
                Page.LIVE_DATA -> LiveDataScreen(autoRefresh, showVolume, showChanges, { autoRefresh = it; save("auto_refresh", it) }, { showVolume = it; save("show_volume", it) }, { showChanges = it; save("show_changes", it) }, ::back)
                Page.CHARTS -> SimpleSettingsScreen("Chart Settings", Icons.Default.ShowChart, listOf("Default timeframe" to "1D", "Chart style" to "Line", "Show grid" to "On", "Show indicators" to "On"), ::back)
                Page.ALERTS -> SimpleSettingsScreen("Price Alerts", Icons.Default.Notifications, listOf("Price alerts" to if (priceAlerts) "Enabled" else "Disabled", "Daily gain/loss" to "Enabled", "High volume" to "Enabled", "Corporate actions" to "Enabled"), ::back)
                Page.LANGUAGE -> SimpleSettingsScreen("Language", Icons.Default.Language, listOf("App language" to "English", "Market currency" to "KSh (Kenyan Shillings)", "Region" to "Kenya"), ::back)
                Page.SECURITY -> SimpleSettingsScreen("Account Security", Icons.Default.Lock, listOf("Password" to "••••••••", "Biometric unlock" to "Off", "Active sessions" to "This device", "Data permissions" to "Review"), ::back)
                Page.HELP -> HelpScreen(::back)
                Page.ABOUT -> AboutScreen(::back)
            }
        }
    }
}

@Composable
private fun MainTopBar(name: String, go: (Page) -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(42.dp), RoundedCornerShape(12.dp), NSELight) { Icon(Icons.Default.ShowChart, null, Modifier.padding(7.dp), NSEGreen) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) { Text("NSE Watcher", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text("Analyse • Understand • Invest Smarter", 10.sp, color = NSEMuted) }
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search", tint = NSEGreen) }
        ProfileAvatar(name, null) { go(Page.PROFILE) }
        IconButton(onClick = { go(Page.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings", tint = NSEGreen) }
    }
}

@Composable
private fun ProfileAvatar(name: String, uri: String?, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val savedUri = uri ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("avatar_uri", null)
    val bitmap by produceState<Bitmap?>(initialValue = null, savedUri) {
        value = try { savedUri?.let { context.contentResolver.openInputStream(Uri.parse(it))?.use(BitmapFactory::decodeStream) } } catch (_: Exception) { null }
    }
    Surface(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), CircleShape, Color(0xFFDDEFE6)) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = "Profile", modifier = Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize(), Alignment.Center) { Text(name.take(1).uppercase(Locale.getDefault()), color = NSEGreen, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun DesignNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("Home" to Icons.Default.Home, "Market" to Icons.Default.CandlestickChart, "Companies" to Icons.Default.Business, "Paper Invest" to Icons.Default.AccountBalanceWallet, "More" to Icons.Default.AutoGraph)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { items.forEachIndexed { index, item -> NavigationBarItem(selected == index, { onSelect(index) }, icon = { Icon(item.second, item.first, Modifier.size(22.dp)) }, label = { Text(item.first, fontSize = 9.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = NSEGreen, selectedTextColor = NSEGreen, indicatorColor = NSELight, unselectedIconColor = NSEMuted, unselectedTextColor = NSEMuted)) } }
}

@Composable
private fun ProfileScreen(name: String, username: String, email: String, description: String, avatar: String?, onName: (String) -> Unit, onUsername: (String) -> Unit, onEmail: (String) -> Unit, onDescription: (String) -> Unit, onPickAvatar: () -> Unit, goBack: () -> Unit, go: (Page) -> Unit) {
    var edit by rememberSaveable { mutableStateOf(false) }
    var n by rememberSaveable(name) { mutableStateOf(name) }; var u by rememberSaveable(username) { mutableStateOf(username) }; var e by rememberSaveable(email) { mutableStateOf(email) }; var d by rememberSaveable(description) { mutableStateOf(description) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Profile", goBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ProfileHero(name, avatar, onPickAvatar) }
            item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) {
                if (edit) {
                    ProfileField("Full name", n, { n = it }, Icons.Default.Person); ProfileField("Username", u, { u = it }, Icons.Default.AccountCircle); ProfileField("Email", e, { e = it }, Icons.Default.Email); ProfileField("Description", d, { d = it }, Icons.Default.Info)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ edit = false }) { Text("Cancel") }; TextButton({ onName(n); onUsername(u); onEmail(e); onDescription(d); edit = false }) { Text("Save", color = NSEGreen, fontWeight = FontWeight.Bold) } }
                } else {
                    InfoLine(Icons.Default.AccountCircle, "Username", "@$username"); InfoLine(Icons.Default.Email, "Email", email); InfoLine(Icons.Default.Person, "Profile", description)
                    SettingsRow(Icons.Default.Tune, "Edit Profile", "Update your personal information") { edit = true }
                }
            } } }
            item { SettingsRow(Icons.Default.PhotoCamera, "Change Profile Picture", "Choose a new photo from your phone", onPickAvatar) }
            item { SettingsRow(Icons.Default.Lock, "Account Security", "Password, sessions and account protection") { go(Page.SECURITY) } }
            item { SettingsRow(Icons.Default.Link, "Linked Accounts", "Manage connected services") { SimpleSettingsScreen("Linked Accounts", Icons.Default.Link, listOf("Google" to "Not connected", "Broker account" to "Not connected"), goBack) } }
            item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Text("NSE Watcher helps you understand market information. It does not execute trades or guarantee investment returns.", Modifier.padding(14.dp), color = NSEDark, fontSize = 11.sp) } }
        }
    }
}

@Composable
private fun ProfileHero(name: String, avatar: String?, onPickAvatar: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uri = avatar ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("avatar_uri", null)
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) { value = try { uri?.let { context.contentResolver.openInputStream(Uri.parse(it))?.use(BitmapFactory::decodeStream) } } catch (_: Exception) { null } }
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(18.dp)) {
        Box(Modifier.size(88.dp)) { Surface(Modifier.fillMaxSize().clip(CircleShape), CircleShape, Color.White) { if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Profile picture", Modifier.fillMaxSize()) else Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.Person, null, Modifier.size(45.dp), NSEGreen) } }; Surface(Modifier.size(30.dp).align(Alignment.BottomEnd).clip(CircleShape).clickable(onClick = onPickAvatar), CircleShape, NSEGreen) { Icon(Icons.Default.PhotoCamera, "Change profile picture", Modifier.padding(7.dp), Color.White) } }
        Spacer(Modifier.height(10.dp)); Text(name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("IT Graduate | Investor | Learner", fontSize = 11.sp, color = NSEMuted)
    } }
}

@Composable
private fun ProfileField(label: String, value: String, onValue: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TextField(value, onValue, Modifier.fillMaxWidth().padding(bottom = 7.dp), label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = NSEGreen) }, singleLine = label != "Description", colors = TextFieldDefaults.colors(focusedIndicatorColor = NSEGreen, focusedLabelColor = NSEGreen))
}

@Composable
private fun SettingsScreen(dark: Boolean, market: Boolean, price: Boolean, news: Boolean, app: Boolean, volume: Boolean, changes: Boolean, refresh: Boolean, onDark: (Boolean) -> Unit, onMarket: (Boolean) -> Unit, onPrice: (Boolean) -> Unit, onNews: (Boolean) -> Unit, onApp: (Boolean) -> Unit, onVolume: (Boolean) -> Unit, onChanges: (Boolean) -> Unit, onRefresh: (Boolean) -> Unit, goBack: () -> Unit, go: (Page) -> Unit) {
    Column(Modifier.fillMaxSize()) { PageHeader("Settings", goBack); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SettingsSection("Appearance") { SettingsRow(Icons.Default.LightMode, "Theme", if (dark) "Dark" else "Light") { go(Page.THEME) }; SettingsRow(Icons.Default.Language, "Language", "English") { go(Page.LANGUAGE) }; SettingsRow(Icons.Default.Visibility, "Font & Display", "Comfortable reading") {} } }
        item { SettingsSection("Notifications") { SettingsRow(Icons.Default.Notifications, "Notifications", "Market alerts, news and updates") { go(Page.NOTIFICATIONS) }; SwitchRow("Market alerts", "Important market movements and trends", market, onMarket); SwitchRow("Price alerts", "Custom price notifications", price, onPrice); SwitchRow("News notifications", "Latest NSE news and company updates", news, onNews); SwitchRow("App notifications", "General updates and reminders", app, onApp) } }
        item { SettingsSection("Data & Display") { SettingsRow(Icons.Default.Storage, "Live Data", "NSE data source and refresh") { go(Page.LIVE_DATA) }; SettingsRow(Icons.Default.ShowChart, "Chart Settings", "Timeframes, indicators and style") { go(Page.CHARTS) }; SwitchRow("Show trading volume", "Display volume where available", volume, onVolume); SwitchRow("Show price changes", "Display daily percentage changes", changes, onChanges) } }
        item { SettingsSection("Market Preferences") { SettingsRow(Icons.Default.NotificationsNone, "Price Alerts", "Configure your favourite-stock alerts") { go(Page.ALERTS) }; SettingsRow(Icons.Default.Tune, "Watchlist & Market Preferences", "Default market views and symbols") {} } }
        item { SettingsSection("Privacy & Security") { SettingsRow(Icons.Default.Lock, "Account Security", "Password and account protection") { go(Page.SECURITY) }; SettingsRow(Icons.Default.PrivacyTip, "Privacy", "How NSE Watcher handles your data") {} } }
        item { SettingsSection("Support") { SettingsRow(Icons.Default.HelpOutline, "Help & Support", "FAQs, contact support and report an issue") { go(Page.HELP) }; SettingsRow(Icons.Default.Info, "About NSE Watcher", "Version, terms and product information") { go(Page.ABOUT) } } }
        item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NSEBorder)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Logout, null, tint = NSERed); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Log out", fontWeight = FontWeight.Bold); Text("End this session on the device", color = NSEMuted, fontSize = 10.sp) }; Text("›", color = NSERed, fontSize = 22.sp) } } }
    } }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) { Column { Text(title, Modifier.padding(bottom = 6.dp), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = NSEMuted); Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(content = content) } } }

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(subtitle, color = NSEMuted, fontSize = 10.sp) }; Switch(checked, onChecked) } }

@Composable
private fun ThemeScreen(dark: Boolean, onDark: (Boolean) -> Unit, back: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader("Theme", back); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { ThemeOption("Light", "Clean white NSE Watcher experience", Icons.Default.LightMode, !dark) { onDark(false) } }; item { ThemeOption("Dark", "Low-light interface for night use", Icons.Default.DarkMode, dark) { onDark(true) } }; item { ThemeOption("System Default", "Follow your phone display setting", Icons.Default.RestartAlt, false) {} }; item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Text("Theme changes apply immediately and are saved on this device.", Modifier.padding(14.dp), color = NSEDark, fontSize = 11.sp) } } } } }

@Composable
private fun ThemeOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, if (selected) NSEGreen else NSEBorder)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(40.dp), CircleShape, NSELight) { Icon(icon, null, Modifier.padding(9.dp), NSEGreen) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = NSEMuted, fontSize = 10.sp) }; Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.Visibility, null, tint = if (selected) NSEGreen else NSEMuted) } } }

@Composable
private fun NotificationsScreen(market: Boolean, price: Boolean, news: Boolean, app: Boolean, onMarket: (Boolean) -> Unit, onPrice: (Boolean) -> Unit, onNews: (Boolean) -> Unit, onApp: (Boolean) -> Unit, back: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader("Notifications", back); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { SwitchCard("Market Alerts", "Get notified about important market movements, trends and events", market, onMarket) }; item { SwitchCard("Price Alerts", "Receive custom alerts for stocks you follow", price, onPrice) }; item { SwitchCard("News Notifications", "Receive the latest NSE news and company updates", news, onNews) }; item { SwitchCard("App Notifications", "General app updates and reminders", app, onApp) }; item { SettingsRow(Icons.Default.Notifications, "Notification Sound", "Default") {}; item { SettingsRow(Icons.Default.Visibility, "Do Not Disturb", "Off") {} } } } }

@Composable
private fun SwitchCard(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NSEBorder)) { SwitchRow(title, subtitle, checked, onChecked) } }

@Composable
private fun LiveDataScreen(refresh: Boolean, volume: Boolean, changes: Boolean, onRefresh: (Boolean) -> Unit, onVolume: (Boolean) -> Unit, onChanges: (Boolean) -> Unit, back: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader("Live Data", back); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = NSEGreen, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)); Column { Text("NSE market data", fontWeight = FontWeight.ExtraBold); Text("Live or delayed data will be shown with its source and timestamp when connected.", color = NSEMuted, fontSize = 10.sp) } } } }; item { SettingsRow(Icons.Default.Web, "Data Source", "NSE / licensed market-data provider") {} }; item { SwitchRow("Auto refresh", "Refresh market information when new data is available", refresh, onRefresh) }; item { SwitchRow("Show trading volume", "Display volume where available", volume, onVolume) }; item { SwitchRow("Show price changes", "Display daily percentage changes", changes, onChanges) }; item { SettingsRow(Icons.Default.Storage, "Data Display", "KSh (Kenyan Shillings)") {} }; item { Text("Note: Real-time market data may be delayed depending on the source and licensing arrangement.", Modifier.padding(8.dp), color = NSEMuted, fontSize = 10.sp) } } } }

@Composable
private fun SimpleSettingsScreen(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, rows: List<Pair<String, String>>, back: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader(title, back); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(rows) { (a, b) -> SettingsRow(icon, a, b) {} } } } }

@Composable
private fun HelpScreen(back: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader("Help & Support", back); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { SettingsRow(Icons.Default.HelpOutline, "FAQs", "Find answers to common questions") {} }; item { SettingsRow(Icons.Default.Email, "Contact Support", "Get in touch with our team") {} }; item { SettingsRow(Icons.Default.ReportProblem, "Report an Issue", "Help us improve the app") {} }; item { SettingsRow(Icons.Default.Info, "User Guide", "Learn how to use NSE Watcher") {} }; item { SettingsRow(Icons.Default.Web, "Terms & Conditions", "Read our terms of service") {} }; item { SettingsRow(Icons.Default.PrivacyTip, "Privacy Policy", "How we handle your data") {} }; item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Column(Modifier.padding(15.dp)) { Text("Need immediate help?", fontWeight = FontWeight.ExtraBold); Text("Email: support@nsewatcher.co.ke", color = NSEMuted, fontSize = 11.sp); Text("We’re here to help.", color = NSEDark, fontSize = 11.sp) } } } } } }

@Composable
private fun AboutScreen(back: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader("About NSE Watcher", back); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.ShowChart, null, Modifier.size(55.dp), NSEGreen); Text("NSE Watcher", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("Market Intelligence • Analyse • Understand", color = NSEMuted, fontSize = 11.sp); Text("Version 1.0", color = NSEGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) } } }; item { SettingsRow(Icons.Default.Info, "What NSE Watcher does", "Helps you understand NSE market information before making your own decision") {} }; item { SettingsRow(Icons.Default.Web, "Terms & Conditions", "Review terms of service") {} }; item { SettingsRow(Icons.Default.PrivacyTip, "Privacy Policy", "Review privacy information") {} }; item { Text("NSE Watcher is an information and analysis product, not a broker. Paper Investing uses virtual money and does not place real trades. Nothing in the app is a guarantee of future returns.", Modifier.padding(8.dp), color = NSEMuted, fontSize = 10.sp) } } } }

@Composable
private fun PageHeader(title: String, back: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }; Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold) } }

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(38.dp), CircleShape, NSELight) { Icon(icon, null, Modifier.padding(8.dp), NSEGreen) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp); if (subtitle.isNotBlank()) Text(subtitle, color = NSEMuted, fontSize = 10.sp) }; Icon(Icons.Default.ChevronRight, null, tint = NSEMuted, modifier = Modifier.size(19.dp)) } }

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(36.dp), CircleShape, NSELight) { Icon(icon, null, Modifier.padding(8.dp), NSEGreen) }; Spacer(Modifier.width(10.dp)); Column { Text(title, color = NSEMuted, fontSize = 10.sp); Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp) } } }

@Composable
private fun HomeScreen(open: (DesignStock) -> Unit) { val gainers = designStocks.filter { it.change > 0 }.sortedByDescending { it.change }; val losers = designStocks.filter { it.change < 0 }.sortedBy { it.change }; LazyColumn(contentPadding = PaddingValues(16.dp, 5.dp, 16.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { MarketStatusCard() }; item { IndexMiniRow() }; item { TrendCard() }; item { SectionHeader("Market Snapshot", "A quick view before you invest") }; item { SnapshotActions() }; item { MoversCard(gainers.take(4), losers.take(3), open) }; item { SectionHeader("Top Companies", "Stocks moving the NSE today") }; item { LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) { items(gainers.take(4)) { StockMiniCard(it, open) } } }; item { SectionHeader("Latest News", "Market events and company updates") }; item { NewsPreview() }; item { PaperBanner() } } }

@Composable private fun MarketStatusCard() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Column(Modifier.padding(15.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(NSEGreen)); Spacer(Modifier.width(7.dp)); Text("NSE MARKET OPEN", color = NSEDark, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("10:24 AM EAT", color = NSEMuted, fontSize = 10.sp) }; Spacer(Modifier.height(10.dp)); Text("Market overview", color = NSEMuted, fontSize = 11.sp); Text("Clear picture of the NSE", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold); Text("before you make an investment decision.", fontSize = 11.sp, color = NSEMuted) } } }
@Composable private fun IndexMiniRow() { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("NSE 20" to "1,843.56", "NASI" to "112.48", "NSE 25" to "3,642.17")) { (n, v) -> Card(Modifier.width(145.dp), RoundedCornerShape(14.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(11.dp)) { Text(n, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(v, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("▲ +1.34%", color = NSEGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } } } }
@Composable private fun TrendCard() { var period by rememberSaveable { mutableStateOf("1D") }; Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("NSE 20 — Market Trend", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text("See how the market has been performing", color = NSEMuted, fontSize = 10.sp) }; Text("+1.34%", color = NSEGreen, fontWeight = FontWeight.ExtraBold) }; Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("1D", "1W", "1M", "3M", "6M", "1Y", "5Y").forEach { FilterChip(period == it, { period = it }, label = { Text(it, fontSize = 10.sp) }) } }; TrendChart(); Text("Historical performance will use sourced NSE market data in live mode.", color = NSEMuted, fontSize = 9.sp) } } }
@Composable private fun TrendChart() { Canvas(Modifier.fillMaxWidth().height(95.dp).padding(vertical = 8.dp)) { val values = listOf(28f, 38f, 34f, 47f, 44f, 58f, 52f, 67f, 61f, 74f, 69f, 83f); val path = Path(); values.forEachIndexed { i, v -> val x = size.width * i / (values.size - 1); val y = size.height - v / 100f * size.height; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; drawPath(path, NSEGreen, style = Stroke(4f, cap = StrokeCap.Round)) } }
@Composable private fun SectionHeader(title: String, subtitle: String) { Column { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp); Text(subtitle, color = NSEMuted, fontSize = 10.sp) } }
@Composable private fun SnapshotActions() { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { ActionTile(Icons.Default.AutoGraph, "Sectors", "Performance", Modifier.weight(1f)); ActionTile(Icons.Default.ShowChart, "Top Movers", "Gainers & Losers", Modifier.weight(1f)); ActionTile(Icons.Default.Business, "Market Analysis", "Trends & Outlook", Modifier.weight(1f)); ActionTile(Icons.Default.NotificationsNone, "News & Events", "Latest Updates", Modifier.weight(1f)) } }
@Composable private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier) { Card(modifier, RoundedCornerShape(13.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = NSEGreen, modifier = Modifier.size(22.dp)); Spacer(Modifier.height(4.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center); Text(subtitle, color = NSEMuted, fontSize = 7.sp, textAlign = TextAlign.Center) } } }
@Composable private fun MoversCard(gainers: List<DesignStock>, losers: List<DesignStock>, open: (DesignStock) -> Unit) { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = BorderStroke(1.dp, NSEBorder)) { Row(Modifier.padding(vertical = 12.dp)) { MoverList("Top Gainers", gainers, NSEGreen, open, Modifier.weight(1f)); Box(Modifier.width(1.dp).height(150.dp).background(NSEBorder)); MoverList("Top Losers", losers, NSERed, open, Modifier.weight(1f)) } } }
@Composable private fun MoverList(title: String, stocks: List<DesignStock>, tint: Color, open: (DesignStock) -> Unit, modifier: Modifier) { Column(modifier.padding(horizontal = 11.dp)) { Text(title, color = tint, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); stocks.forEach { s -> Row(Modifier.fillMaxWidth().clickable { open(s) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.symbol, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(String.format(Locale.US, "KSh %.2f", s.price), color = NSEMuted, fontSize = 9.sp) }; Text(String.format(Locale.US, "%+.2f%%", s.change), color = tint, fontWeight = FontWeight.Bold, fontSize = 10.sp) } } } }
@Composable private fun StockMiniCard(stock: DesignStock, open: (DesignStock) -> Unit) { Card(Modifier.width(175.dp).clickable { open(stock) }, RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(12.dp)) { Text(stock.symbol, fontWeight = FontWeight.ExtraBold); Text(stock.name, color = NSEMuted, fontSize = 9.sp); Text(String.format(Locale.US, "KSh %.2f", stock.price), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text(String.format(Locale.US, "%+.2f%%", stock.change), color = if (stock.change >= 0) NSEGreen else NSERed, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun NewsPreview() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(13.dp)) { Text("Safaricom posts strong Q1 results", fontWeight = FontWeight.Bold); Text("Net profit and operating trends from the company update…", color = NSEMuted, fontSize = 10.sp); Divider(Modifier.padding(vertical = 8.dp), color = NSEBorder); Text("Market breadth improves as banking stocks lead", fontWeight = FontWeight.Bold); Text("2h ago • Market Update", color = NSEMuted, fontSize = 9.sp) } } }
@Composable private fun PaperBanner() { Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = NSEDark)) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(10.dp)); Column { Text("PAPER INVESTING", color = Color.White, fontWeight = FontWeight.ExtraBold); Text("Practice with virtual money using real NSE prices when available.", color = Color.White, fontSize = 10.sp) } } } }

@Composable private fun MarketScreen() { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { SectionHeader("Market Analysis", "Understand the broader NSE") }; item { TrendCard() }; item { SettingsRow(Icons.Default.AutoGraph, "Sector Performance", "Banking, telecom, manufacturing, energy") {} }; item { SettingsRow(Icons.Default.ShowChart, "Market Breadth", "Advancers, decliners and unchanged") {} }; item { SettingsRow(Icons.Default.NotificationsNone, "Corporate Actions", "Dividends, results and announcements") {} } } }
@Composable private fun CompaniesScreen(open: (DesignStock) -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { SectionHeader("NSE Companies", "Explore company intelligence") }; items(designStocks) { s -> SettingsRow(Icons.Default.Business, s.name, "${s.symbol} • KSh ${String.format(Locale.US, "%.2f", s.price)} • ${String.format(Locale.US, "%+.2f%%", s.change)}") { open(s) } } } }
@Composable private fun CompanyScreen(stock: DesignStock, back: () -> Unit) { Column(Modifier.fillMaxSize()) { PageHeader(stock.name, back); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NSELight)) { Column(Modifier.padding(16.dp)) { Text(stock.symbol, color = NSEDark, fontWeight = FontWeight.Bold); Text(String.format(Locale.US, "KSh %.2f", stock.price), fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text(String.format(Locale.US, "%+.2f%% today", stock.change), color = if (stock.change >= 0) NSEGreen else NSERed, fontWeight = FontWeight.Bold) } } }; item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(17.dp), border = BorderStroke(1.dp, NSEBorder)) { Column(Modifier.padding(14.dp)) { Text("Price History", fontWeight = FontWeight.ExtraBold); TrendChart() } } }; item { SettingsSection("NSE Watcher Intelligence") { SettingsRow(Icons.Default.AutoGraph, "Watcher Score", "Illustrative score • explainable factors") {}; SettingsRow(Icons.Default.Business, "Fundamentals", "Valuation, profitability, leverage and growth") {}; SettingsRow(Icons.Default.NotificationsNone, "Corporate Actions", "Dividends, results and announcements") {} } }; item { Text("This screen is currently illustrative. Live NSE data and licensed sources will replace demo values.", color = NSEMuted, fontSize = 10.sp, modifier = Modifier.padding(6.dp)) } } } }
@Composable private fun PaperInvestScreen() { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NSEDark)) { Column(Modifier.padding(17.dp)) { Text("PAPER PORTFOLIO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("KSh 100,000", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold); Text("Virtual balance • No real money", color = Color.White, fontSize = 10.sp) } } }; item { SectionHeader("Holdings", "Hypothetical investments follow market movement") }; item { SettingsRow(Icons.Default.Business, "SCOM", "100 shares • KSh 1,850 value") {}; item { SettingsRow(Icons.Default.Business, "KCB", "50 shares • KSh 2,115 value") {}; item { Text("Paper Investing is educational and does not place trades with a broker.", color = NSEMuted, fontSize = 10.sp, modifier = Modifier.padding(6.dp)) } } }
@Composable private fun MoreScreen(go: (Page) -> Unit) { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { SectionHeader("More", "Your NSE Watcher tools") }; item { SettingsRow(Icons.Default.AccountCircle, "Profile", "Personal information and profile picture") { go(Page.PROFILE) } }; item { SettingsRow(Icons.Default.Settings, "Settings", "Theme, notifications, data and privacy") { go(Page.SETTINGS) } }; item { SettingsRow(Icons.Default.HelpOutline, "Help & Support", "FAQs, contact and report issues") { go(Page.HELP) } }; item { SettingsRow(Icons.Default.Info, "About NSE Watcher", "Version and product information") { go(Page.ABOUT) } } }
