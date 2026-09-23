package ke.co.nsewatcher.data

import ke.co.nsewatcher.Stock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketDataProviderTest {
    private class FakeProvider : MarketDataProvider {
        override val source = MarketDataSource.FUTURE_PROVIDER
        val calls = mutableListOf<String>()

        override suspend fun loadStocks(): List<Stock> {
            calls += "stocks"
            return listOf(Stock("KCB", "KCB Group", 50.0, 1.0, emptyList()))
        }

        override suspend fun loadCompanies(): List<Stock> {
            calls += "companies"
            return listOf(Stock("EQTY", "Equity Group", Double.NaN, Double.NaN, emptyList()))
        }

        override suspend fun loadMarketStatus(): MyStocksCache.MarketStatus {
            calls += "status"
            return MyStocksCache.MarketStatus(isOpen = true, status = "OPEN", isKnown = true)
        }

        override suspend fun loadMarketIndices(marketOpen: Boolean): List<MyStocksCache.MarketIndex> {
            calls += "indices:$marketOpen"
            return listOf(MyStocksCache.MarketIndex("^NASI", "NASI", 235.0, 1.0))
        }

        override suspend fun loadHistoryDetails(
            symbol: String,
            period: String
        ): MyStocksCache.HistoryResult {
            calls += "history:$symbol:$period"
            return MyStocksCache.HistoryResult(prices = listOf(50.0))
        }
    }

    @Test fun gatewayDelegatesEveryMarketOperationToSelectedProvider() = runBlocking {
        val provider = FakeProvider()
        val gateway = MarketDataGateway(provider)

        assertEquals(MarketDataSource.FUTURE_PROVIDER, gateway.source)
        assertEquals("KCB", gateway.loadStocks().single().symbol)
        assertEquals("EQTY", gateway.loadCompanies().single().symbol)
        assertEquals("OPEN", gateway.loadMarketStatus().status)
        assertEquals("^NASI", gateway.loadMarketIndices(true).single().symbol)
        assertEquals(50.0, gateway.loadHistoryDetails("KCB", "1m").prices.single(), 0.0001)
        assertEquals(
            listOf(
                "stocks",
                "companies",
                "status",
                "indices:true",
                "history:KCB:1m"
            ),
            provider.calls
        )
    }
}
