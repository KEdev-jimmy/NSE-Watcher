package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

/**
 * Local user-owned watchlist storage.
 *
 * The watchlist is intentionally empty for a new user. Symbols are added only
 * through an explicit user action; no market/demo symbols are pre-populated.
 */
class WatchlistStore(private val context: Context) {
    val symbols = context.watchlistDataStore.data.map { prefs ->
        prefs[watchlistKey]?.toList() ?: emptyList()
    }

    suspend fun add(symbol: String) {
        val normalized = symbol.trim().uppercase()
        if (normalized.isBlank()) return
        context.watchlistDataStore.edit { prefs ->
            prefs[watchlistKey] = (prefs[watchlistKey] ?: emptySet()) + normalized
        }
    }

    suspend fun remove(symbol: String) {
        val normalized = symbol.trim().uppercase()
        if (normalized.isBlank()) return
        context.watchlistDataStore.edit { prefs ->
            prefs[watchlistKey] = (prefs[watchlistKey] ?: emptySet()) - normalized
        }
    }
}
