package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.time.Instant

/** Contract intentionally isolates the UI from demo data and future secure backend/API providers. */
interface MarketRepository {
    val watchlist: Flow<List<String>>
    suspend fun snapshot(forceRefresh: Boolean = false): Result<MarketSnapshot>
    suspend fun news(symbol: String? = null): Result<List<NewsItem>>
    suspend fun addSymbol(symbol: String)
    suspend fun removeSymbol(symbol: String)
    suspend fun alerts(): List<PriceAlert>
    suspend fun saveAlert(alert: PriceAlert)
}

private val Context.watchlistDataStore by preferencesDataStore("nse_watcher_preferences")
private val watchlistKey = stringSetPreferencesKey("watchlist_symbols")

/** DataStore-backed user selection remains available after the app is restarted. */
class WatchlistStore(private val context: Context) {
    val symbols = context.watchlistDataStore.data.map { prefs -> prefs[watchlistKey]?.toList() ?: defaults }
    suspend fun add(symbol: String) { context.watchlistDataStore.edit { it[watchlistKey] = (it[watchlistKey] ?: defaults.toSet()) + symbol } }
    suspend fun remove(symbol: String) { context.watchlistDataStore.edit { it[watchlistKey] = (it[watchlistKey] ?: defaults.toSet()) - symbol } }
    companion object { val defaults = listOf("SCOM.KE", "EQTY.KE", "KCB.KE", "ABSA.KE") }
}

class DemoMarketRepository(context: Context) : MarketRepository {
    private val store = WatchlistStore(context.applicationContext)
    private val savedAlerts = mutableListOf(PriceAlert("scom-move", "SCOM.KE", AlertType.DAILY_GAIN, 5.0, true))
    override val watchlist: Flow<List<String>> = store.symbols
    private fun quote(name: String, symbol: String, price: Double, day: Double, week: Double, month: Double, volume: Long, avg: Long, series: List<Double>): Quote {
        val signal = StockAnalyzer.signal(day, week, month, volume, avg)
        return Quote(name, symbol, price, day, week, month, volume, avg, price * 1.02, price * .98, signal, StockAnalyzer.explanation(signal), series)
    }
    private val allQuotes = listOf(
        quote("Safaricom", "SCOM.KE", 18.65, 1.4, 3.1, 4.8, 15_800_000, 9_900_000, listOf(17.3,17.6,17.2,17.9,18.1,18.0,18.4,18.65)),
        quote("Equity Group Holdings", "EQTY.KE", 47.20, -0.8, 1.2, 6.4, 1_240_000, 1_500_000, listOf(44.4,45.1,44.8,46.1,45.9,46.8,47.6,47.2)),
        quote("KCB Group", "KCB.KE", 39.85, 0.3, -2.5, -1.7, 2_100_000, 1_700_000, listOf(41.0,40.6,40.3,39.8,40.0,39.6,39.7,39.85)),
        quote("Absa Bank Kenya", "ABSA.KE", 13.40, -1.1, -0.5, 2.1, 710_000, 540_000, listOf(13.0,13.2,13.1,13.5,13.6,13.7,13.55,13.4))
    )
    override suspend fun snapshot(forceRefresh: Boolean) = Result.success(MarketSnapshot(MarketStatus.UNKNOWN, allQuotes.filter { it.symbol in store.symbols.first() }, Instant.now(), true))
    override suspend fun news(symbol: String?) = Result.success(listOf(
        NewsItem("market", "NSE Watcher demo: connect a market-news provider through the backend", "Demo feed", "Sample data", emptyList(), "Market"),
        NewsItem("scom", "Sample Safaricom corporate-news placeholder", "Demo feed", "Sample data", listOf("SCOM.KE"), "Company")
    ).filter { symbol == null || it.symbols.isEmpty() || symbol in it.symbols })
    override suspend fun addSymbol(symbol: String) { if (allQuotes.any { it.symbol == symbol }) store.add(symbol) }
    override suspend fun removeSymbol(symbol: String) { store.remove(symbol) }
    override suspend fun alerts() = savedAlerts.toList()
    override suspend fun saveAlert(alert: PriceAlert) { savedAlerts.removeAll { it.id == alert.id }; savedAlerts += alert }
}
