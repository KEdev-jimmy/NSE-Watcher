package ke.co.nsewatcher.data

import ke.co.nsewatcher.NewsItem
import ke.co.nsewatcher.Stock

/**
 * Vendor-neutral boundary used by foreground app reads.
 *
 * Screens depend on MarketData rather than calling a concrete provider/cache directly.
 * The current implementation delegates to the existing verified NSE Watcher backend
 * adapters; swapping providers later should happen here instead of across the UI.
 */
internal interface MarketDataProvider {
    suspend fun stocks(): List<Stock>
    suspend fun companies(): List<Stock>
    suspend fun status(): MyStocksCache.MarketStatus
    suspend fun indices(marketOpen: Boolean): List<MyStocksCache.MarketIndex>
    suspend fun history(symbol: String, period: String): MyStocksCache.HistoryResult
    suspend fun technicalHistory(symbol: String, lookbackDays: Int = 400): MyStocksCache.HistoryResult
    suspend fun newsFeed(forceRefresh: Boolean = false): NewsCache.FeedResult
    suspend fun newsDetail(id: String): NewsItem?
    suspend fun companyNews(symbol: String): NewsCache.FeedResult
}

internal object NseWatcherMarketDataProvider : MarketDataProvider {
    override suspend fun stocks(): List<Stock> = MyStocksCache.loadStocks()

    override suspend fun companies(): List<Stock> = MyStocksCache.loadCompanies()

    override suspend fun status(): MyStocksCache.MarketStatus =
        MyStocksCache.loadMarketStatus()

    override suspend fun indices(marketOpen: Boolean): List<MyStocksCache.MarketIndex> =
        MyStocksCache.loadMarketIndices(marketOpen)

    override suspend fun history(
        symbol: String,
        period: String
    ): MyStocksCache.HistoryResult =
        MyStocksCache.loadHistoryDetails(symbol, period)

    override suspend fun technicalHistory(
        symbol: String,
        lookbackDays: Int
    ): MyStocksCache.HistoryResult =
        MyStocksCache.loadTechnicalHistoryDetails(symbol, lookbackDays)

    override suspend fun newsFeed(forceRefresh: Boolean): NewsCache.FeedResult =
        NewsCache.loadFeedResult(forceRefresh)

    override suspend fun newsDetail(id: String): NewsItem? =
        NewsCache.loadDetail(id)

    override suspend fun companyNews(symbol: String): NewsCache.FeedResult =
        NewsCache.loadCompanyNews(symbol)
}

internal class MarketDataGateway(
    private val provider: MarketDataProvider
) {
    suspend fun stocks(): List<Stock> = provider.stocks()

    suspend fun companies(): List<Stock> = provider.companies()

    suspend fun status(): MyStocksCache.MarketStatus = provider.status()

    suspend fun indices(marketOpen: Boolean): List<MyStocksCache.MarketIndex> =
        provider.indices(marketOpen)

    suspend fun history(
        symbol: String,
        period: String
    ): MyStocksCache.HistoryResult =
        provider.history(symbol, period)

    suspend fun technicalHistory(
        symbol: String,
        lookbackDays: Int = 400
    ): MyStocksCache.HistoryResult =
        provider.technicalHistory(symbol, lookbackDays)

    suspend fun newsFeed(forceRefresh: Boolean = false): NewsCache.FeedResult =
        provider.newsFeed(forceRefresh)

    suspend fun newsDetail(id: String): NewsItem? =
        provider.newsDetail(id)

    suspend fun companyNews(symbol: String): NewsCache.FeedResult =
        provider.companyNews(symbol)
}

internal object MarketData {
    private val gateway = MarketDataGateway(NseWatcherMarketDataProvider)

    suspend fun stocks(): List<Stock> = gateway.stocks()

    suspend fun companies(): List<Stock> = gateway.companies()

    suspend fun status(): MyStocksCache.MarketStatus = gateway.status()

    suspend fun indices(marketOpen: Boolean): List<MyStocksCache.MarketIndex> =
        gateway.indices(marketOpen)

    suspend fun history(
        symbol: String,
        period: String
    ): MyStocksCache.HistoryResult =
        gateway.history(symbol, period)

    suspend fun technicalHistory(
        symbol: String,
        lookbackDays: Int = 400
    ): MyStocksCache.HistoryResult =
        gateway.technicalHistory(symbol, lookbackDays)

    suspend fun newsFeed(forceRefresh: Boolean = false): NewsCache.FeedResult =
        gateway.newsFeed(forceRefresh)

    suspend fun newsDetail(id: String): NewsItem? =
        gateway.newsDetail(id)

    suspend fun companyNews(symbol: String): NewsCache.FeedResult =
        gateway.companyNews(symbol)
}
