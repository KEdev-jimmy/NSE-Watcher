package ke.co.nsewatcher.data

import ke.co.nsewatcher.domain.MarketSnapshot

/**
 * Provider boundary for market data.
 *
 * The Android UI depends on this abstraction, not on a specific vendor.
 * This lets NSE Watcher replace MyStocks later without redesigning the app.
 */
interface MarketDataProvider {
    suspend fun snapshot(symbols: List<String> = emptyList()): Result<MarketSnapshot>
    suspend fun news(symbol: String? = null): Result<List<ke.co.nsewatcher.domain.NewsItem>>
}

/**
 * Explicit provider selection. Keep this in one place so a future NSE/ICE/etc.
 * provider can replace MyStocks without changing screens.
 */
enum class MarketDataSource {
    DEMO,
    MYSTOCKS,
    FUTURE_PROVIDER
}
