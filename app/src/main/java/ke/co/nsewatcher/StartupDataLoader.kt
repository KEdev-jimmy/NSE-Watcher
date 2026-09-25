package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal data class StartupDataSnapshot(
    val stocks: List<Stock> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val companies: List<Stock> = emptyList(),
    val marketStatus: MyStocksCache.MarketStatus = MyStocksCache.MarketStatus(),
    val completed: Boolean = false,
    val timedOutSources: Set<String> = emptySet()
)

internal class StartupDataLoader(
    private val stocks: suspend () -> List<Stock>,
    private val news: suspend () -> NewsCache.FeedResult,
    private val companies: suspend () -> List<Stock>,
    private val status: suspend () -> MyStocksCache.MarketStatus,
    private val sourceTimeoutMs: Long = 6_000L
) {
    private data class Bounded<T>(
        val value: T,
        val timedOut: Boolean
    )

    suspend fun load(): StartupDataSnapshot = coroutineScope {
        val stocksRequest = async {
            bounded(emptyList<Stock>()) { stocks() }
        }
        val newsRequest = async {
            bounded(NewsCache.FeedResult(emptyList())) { news() }
        }
        val companiesRequest = async {
            bounded(emptyList<Stock>()) { companies() }
        }
        val statusRequest = async {
            bounded(MyStocksCache.MarketStatus()) { status() }
        }

        val loadedStocks = stocksRequest.await()
        val loadedNews = newsRequest.await()
        val loadedCompanies = companiesRequest.await()
        val loadedStatus = statusRequest.await()

        val timedOut = buildSet {
            if (loadedStocks.timedOut) add("stocks")
            if (loadedNews.timedOut) add("news")
            if (loadedCompanies.timedOut) add("companies")
            if (loadedStatus.timedOut) add("status")
        }

        StartupDataSnapshot(
            stocks = loadedStocks.value,
            news = loadedNews.value.items,
            companies = loadedCompanies.value,
            marketStatus = loadedStatus.value,
            completed = timedOut.isEmpty(),
            timedOutSources = timedOut
        )
    }

    private suspend fun <T : Any> bounded(
        fallback: T,
        load: suspend () -> T
    ): Bounded<T> {
        val value = withTimeoutOrNull(sourceTimeoutMs) {
            try {
                load()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fallback
            }
        }
        return if (value == null) {
            Bounded(fallback, timedOut = true)
        } else {
            Bounded(value, timedOut = false)
        }
    }
}
