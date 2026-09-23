package ke.co.nsewatcher.data

import ke.co.nsewatcher.MarketRefreshController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Shared in-memory history repository for chart consumers.
 *
 * Company Intelligence and Practice can ask for the same symbol/range without
 * creating separate provider observations. Fresh history is reused for the
 * provider's 15-minute market-data cadence; explicit refresh/retry bypasses it.
 */
internal class MarketHistoryRepository(
    private val loader: suspend (String, String) -> MyStocksCache.HistoryResult,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = MarketRefreshController.REFRESH_INTERVAL_MS,
    private val maxEntries: Int = 128
) {
    private data class Key(val symbol: String, val period: String)
    private data class Entry(val result: MyStocksCache.HistoryResult, val loadedAtMs: Long)

    private val mutex = Mutex()
    private val entries = mutableMapOf<Key, Entry>()
    private val inFlight = mutableMapOf<Key, CompletableDeferred<MyStocksCache.HistoryResult>>()

    suspend fun load(
        symbol: String,
        period: String,
        forceRefresh: Boolean = false
    ): MyStocksCache.HistoryResult {
        val key = Key(normalizeSymbol(symbol), normalizePeriod(period))
        if (key.symbol.isBlank() || key.period.isBlank()) return MyStocksCache.HistoryResult()

        var owner = false
        val pending = mutex.withLock {
            val cached = entries[key]
            if (!forceRefresh && cached != null && nowMs() - cached.loadedAtMs < maxAgeMs) {
                return cached.result
            }
            inFlight[key] ?: CompletableDeferred<MyStocksCache.HistoryResult>().also {
                inFlight[key] = it
                owner = true
            }
        }

        if (owner) {
            try {
                val result = loader(key.symbol, providerPeriod(key.period))
                mutex.withLock {
                    if (usable(result)) {
                        entries[key] = Entry(result, nowMs())
                        while (entries.size > maxEntries) {
                            val oldest = entries.minByOrNull { it.value.loadedAtMs }?.key ?: break
                            entries.remove(oldest)
                        }
                    }
                    inFlight.remove(key)
                }
                pending.complete(result)
            } catch (cancelled: CancellationException) {
                mutex.withLock { inFlight.remove(key) }
                pending.completeExceptionally(cancelled)
                throw cancelled
            } catch (_: Exception) {
                val empty = MyStocksCache.HistoryResult()
                mutex.withLock { inFlight.remove(key) }
                pending.complete(empty)
            }
        }

        return pending.await()
    }

    suspend fun clear(symbol: String? = null) {
        mutex.withLock {
            if (symbol == null) entries.clear()
            else {
                val normalized = normalizeSymbol(symbol)
                entries.keys.removeAll { it.symbol == normalized }
            }
        }
    }

    private fun normalizeSymbol(value: String): String =
        value.trim().uppercase(Locale.US).removeSuffix(".KE")

    private fun normalizePeriod(value: String): String =
        value.trim().uppercase(Locale.US)

    private fun providerPeriod(value: String): String =
        if (value == "1D") value else value.lowercase(Locale.US)

    private fun usable(result: MyStocksCache.HistoryResult): Boolean =
        result.points.isNotEmpty() ||
            result.prices.isNotEmpty() ||
            result.observedAt.isNotBlank() ||
            result.sessionClose != null ||
            result.previousSessionClose != null
}

internal object MarketHistoryCache {
    private val repository = MarketHistoryRepository(
        loader = MarketData::history
    )

    suspend fun load(
        symbol: String,
        period: String,
        forceRefresh: Boolean = false
    ): MyStocksCache.HistoryResult =
        repository.load(symbol, period, forceRefresh)

    suspend fun clear(symbol: String? = null) = repository.clear(symbol)
}
