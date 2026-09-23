package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache

/**
 * One preference rule for market-status refreshes across the app.
 *
 * A temporary unavailable/UNKNOWN refresh should not erase a previously known
 * OPEN/CLOSED state. If no known state exists yet, UNKNOWN remains honest.
 */
internal object SharedMarketStatus {
    fun preferred(
        current: MyStocksCache.MarketStatus,
        refreshed: MyStocksCache.MarketStatus
    ): MyStocksCache.MarketStatus =
        if (refreshed.isKnown || !current.isKnown) refreshed else current
}
