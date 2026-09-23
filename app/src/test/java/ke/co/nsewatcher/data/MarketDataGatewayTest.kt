package ke.co.nsewatcher.data

import ke.co.nsewatcher.NewsItem
import ke.co.nsewatcher.Stock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketDataGatewayTest {
    private class FakeProvider : MarketDataProvider {
        val calls = mutableListOf<String>()

        override suspend fun stocks(): List<Stock> {
            calls += "stocks"
            return listOf(
                Stock(
                    symbol = "KCB",
                    name = "KCB Group",
                    price = 50.0,
                    change = 1.0,
                    history = emptyList()
                )
            )
        }

        override suspend fun companies(): List<Stock> {
            calls += "companies"
            return listOf(
                Stock(
                    symbol = "EQTY",
                    name = "Equity Group",
                    price = Double.NaN,
                    change = Double.NaN,
                    history = emptyList(),
                    changeAvailable = false,
                    volumeAvailable = false
                )
            )
        }

        override suspend fun status(): MyStocksCache.MarketStatus {
            calls += "status"
            return MyStocksCache.MarketStatus(isOpen = true, status = "OPEN", isKnown = true)
        }

        override suspend fun indices(marketOpen: Boolean): List<MyStocksCache.MarketIndex> {
            calls += "indices:$marketOpen"
            return listOf(MyStocksCache.MarketIndex("^NASI", "NASI", 200.0, 1.0))
        }

        override suspend fun history(
            symbol: String,
            period: String
        ): MyStocksCache.HistoryResult {
            calls += "history:$symbol:$period"
            return MyStocksCache.HistoryResult(prices = listOf(10.0))
        }

        override suspend fun newsFeed(forceRefresh: Boolean): NewsCache.FeedResult {
            calls += "news:$forceRefresh"
            return NewsCache.FeedResult(
                items = listOf(
                    NewsItem(
                        id = "n1",
                        title = "KCB update",
                        summary = "",
                        body = "",
                        source = "Issuer",
                        publishedAt = "2026-09-23T09:00:00Z",
                        category = "Company News",
                        symbol = "KCB",
                        companyName = "KCB Group",
                        imageUrl = "",
                        url = "https://example.com/kcb",
                        dividendAmount = "",
                        exDate = "",
                        paymentDate = ""
                    )
                )
            )
        }
    }

    @Test fun delegatesCurrentMarketShapesWithoutReinterpretingThem() = runBlocking {
        val provider = FakeProvider()
        val gateway = MarketDataGateway(provider)

        assertEquals("KCB", gateway.stocks().single().symbol)
        assertEquals("EQTY", gateway.companies().single().symbol)
        assertEquals("OPEN", gateway.status().status)
        assertEquals("^NASI", gateway.indices(true).single().symbol)
        assertEquals(10.0, gateway.history("KCB", "1m").prices.single(), 0.0001)
        assertEquals("n1", gateway.newsFeed(forceRefresh = true).items.single().id)

        assertEquals(
            listOf(
                "stocks",
                "companies",
                "status",
                "indices:true",
                "history:KCB:1m",
                "news:true"
            ),
            provider.calls
        )
    }
}
