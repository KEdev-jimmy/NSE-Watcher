from pathlib import Path

path = Path("app/src/main/java/ke/co/nsewatcher/DesignActivity.kt")
text = path.read_text()

text = text.replace(
    "import java.util.Locale\n",
    "import java.util.Locale\nimport ke.co.nsewatcher.data.MyStocksCache\n",
    1,
)
text = text.replace(
    "private data class Stock(",
    "data class Stock(",
    1,
)
text = text.replace(
    "private val stocks = listOf(\n",
    "private val fallbackStocks = listOf(\n",
    1,
)
text = text.replace(
    ")\nprivate enum class Page",
    ")\n\nprivate val liveStocks = mutableStateOf(fallbackStocks)\nprivate val stocks: List<Stock> get() = liveStocks.value\n\nprivate enum class Page",
    1,
)
text = text.replace(
    '    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n',
    '    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)\n    LaunchedEffect(Unit) {\n        MyStocksCache.loadStocks().takeIf { it.isNotEmpty() }?.let { liveStocks.value = it }\n    }\n',
    1,
)
text = text.replace('Text("LIVE",color=Green', 'Text("15 MIN DELAYED",color=Green', 1)
text = text.replace('Text("LIVE NSE PRICE",color=Muted', 'Text("NSE DATA • 15 MIN DELAYED",color=Muted', 1)
text = text.replace(
    'Text("Data shown is illustrative until licensed live NSE sources are connected.",color=Muted,fontSize=9.sp)',
    'Text("Prices use MyStocks exchange-supplied delayed NSE data. Fundamental figures remain illustrative until connected to sourced company data.",color=Muted,fontSize=9.sp)',
    1,
)
text = text.replace(
    'Text("NSE MARKET OPEN",color=DarkGreen',
    'Text("NSE MARKET",color=DarkGreen',
    1,
)
text = text.replace(
    'Text("10:24 AM EAT",color=Muted,fontSize=10.sp)',
    'Text("DATA FEED",color=Muted,fontSize=10.sp)',
    1,
)
text = text.replace(
    'Text("Real market price • analysis only • no real trading",color=Muted,fontSize=9.sp)',
    'Text("Exchange-supplied NSE data • analysis only • no real trading",color=Muted,fontSize=9.sp)',
    1,
)

path.write_text(text)
