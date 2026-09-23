package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.watchlistDataStore by preferencesDataStore("nse_watcher_preferences")
private val watchlistKey = stringSetPreferencesKey("watchlist_symbols")

/**
 * Local user-owned watchlist storage.
 *
 * Market/network reads live behind MarketData. This store intentionally owns only
 * local user state so provider changes cannot affect a user's followed companies.
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
