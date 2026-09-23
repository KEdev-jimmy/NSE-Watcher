package ke.co.nsewatcher.data

import ke.co.nsewatcher.Stock

/**
 * Vendor boundary for provider-backed NSE market observations.
 *
 * UI and worker code should depend on this contract rather than calling the
 * concrete MyStocks transport directly. The current implementation delegates
 * to MyStocksCache, so this refactor changes ownership, not data semantics.
 */
interface MarketDataProvider {
    val source: MarketDataSource

    suspend fun loadStocks(): List<Stock>
    suspend fun loadCompanies(): List<Stock>
    suspend fun loadMarketStatus(): MyStocksCache.MarketStatus
    suspend fun loadMarketIndices(marketOpen: Boolean = false): List<MyStocksCache.MarketIndex>
    suspend fun loadHistoryDetails(
        symbol: String,
        period: String = "1y"
    ): MyStocksCache.HistoryResult
}

enum class MarketDataSource {
    MYSTOCKS,
    FUTURE_PROVIDER
}

/** Concrete provider adapter for the current backend/MyStocks data path. */
internal object MyStocksMarketDataProvider : MarketDataProvider {
    override val source: MarketDataSource = MarketDataSource.MYSTOCKS

    override suspend fun loadStocks(): List<Stock> = MyStocksCache.loadStocks()

    override suspend fun loadCompanies(): List<Stock> = MyStocksCache.loadCompanies()

    override suspend fun loadMarketStatus(): MyStocksCache.MarketStatus =
        MyStocksCache.loadMarketStatus()

    override suspend fun loadMarketIndices(
        marketOpen: Boolean
    ): List<MyStocksCache.MarketIndex> =
        MyStocksCache.loadMarketIndices(marketOpen)

    override suspend fun loadHistoryDetails(
        symbol: String,
        period: String
    ): MyStocksCache.HistoryResult =
        MyStocksCache.loadHistoryDetails(symbol, period)
}

/**
 * Small injectable gateway used by production and unit tests.
 *
 * Keeping provider selection here means a future provider/fallback can be
 * introduced without changing screen or worker code.
 */
internal class MarketDataGateway(
    private val provider: MarketDataProvider
) {
    val source: MarketDataSource get() = provider.source

    suspend fun loadStocks(): List<Stock> = provider.loadStocks()

    suspend fun loadCompanies(): List<Stock> = provider.loadCompanies()

    suspend fun loadMarketStatus(): MyStocksCache.MarketStatus =
        provider.loadMarketStatus()

    suspend fun loadMarketIndices(
        marketOpen: Boolean = false
    ): List<MyStocksCache.MarketIndex> =
        provider.loadMarketIndices(marketOpen)

    suspend fun loadHistoryDetails(
        symbol: String,
        period: String = "1y"
    ): MyStocksCache.HistoryResult =
        provider.loadHistoryDetails(symbol, period)
}

/** Production market-data entry point. */
object MarketData {
    private val gateway = MarketDataGateway(MyStocksMarketDataProvider)

    val source: MarketDataSource get() = gateway.source

    suspend fun loadStocks(): List<Stock> = gateway.loadStocks()

    suspend fun loadCompanies(): List<Stock> = gateway.loadCompanies()

    suspend fun loadMarketStatus(): MyStocksCache.MarketStatus =
        gateway.loadMarketStatus()

    suspend fun loadMarketIndices(
        marketOpen: Boolean = false
    ): List<MyStocksCache.MarketIndex> =
        gateway.loadMarketIndices(marketOpen)

    suspend fun loadHistoryDetails(
        symbol: String,
        period: String = "1y"
    ): MyStocksCache.HistoryResult =
        gateway.loadHistoryDetails(symbol, period)
}
