package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMarketContextPresentationTest {
    private fun stock(symbol: String, change: Double, sector: String) =
        Stock(
            symbol = symbol,
            name = symbol,
            price = 10.0,
            change = change,
            history = emptyList(),
            sector = sector,
            source = "MyStocks Africa",
            observedAt = "2026-09-23T09:30:00Z"
        )

    @Test fun homeContextUsesSectorCalculationButDoesNotRepeatMoverOrNewsItems() {
        val news = NewsItem(
            id = "n1",
            title = "Company update",
            summary = "",
            body = "",
            source = "Issuer",
            publishedAt = "2026-09-23T08:00:00Z",
            category = "Company News",
            symbol = "A",
            companyName = "A",
            imageUrl = "",
            url = "https://example.com/a",
            dividendAmount = "",
            exDate = "",
            paymentDate = "",
            intelligenceRelevance = "market",
            intelligenceRelevanceReason = "Relevant company update"
        )
        val snapshot = HomeIntelligenceEngine.build(
            stocks = listOf(
                stock("A", 4.0, "Banking"),
                stock("B", 2.0, "Banking"),
                stock("C", -1.0, "Energy")
            ),
            news = listOf(news)
        )

        val context = HomeMarketContextPresentation.items(snapshot)

        assertEquals(1, context.size)
        assertTrue(context.single().id.startsWith("sector-"))
        assertTrue(context.none { it.id.startsWith("gainer-") })
        assertTrue(context.none { it.id.startsWith("news-") })
        assertTrue(context.single().evidence.isNotEmpty())
    }

    @Test fun verifiedIndexPulsePrecedesSectorContextWhenAvailable() {
        val snapshot = HomeIntelligenceEngine.build(
            stocks = listOf(stock("A", 2.0, "Banking")),
            news = emptyList(),
            marketIndices = listOf(
                HomeMarketIndex(
                    symbol = "^NASI",
                    name = "NASI",
                    value = 235.0,
                    changePct = 1.0,
                    asOf = "2026-09-23T09:30:00Z",
                    dataMode = HomeMarketDataMode.CURRENT_SESSION
                )
            )
        )

        val context = HomeMarketContextPresentation.items(snapshot)

        assertEquals(listOf("market-index-pulse", "sector-banking"), context.map { it.id })
    }

    @Test fun providerIndicesMapIntoHomeFreshnessWithoutInventingInvalidRows() {
        val rows = HomeMarketIndexPresentation.fromProvider(
            listOf(
                MyStocksCache.MarketIndex(
                    symbol = "^NASI",
                    name = "NASI",
                    value = 235.5,
                    changePct = 1.25,
                    asOf = "2026-09-23T09:30:00Z",
                    freshnessMode = "CURRENT_SESSION"
                ),
                MyStocksCache.MarketIndex(
                    symbol = "^N20I",
                    name = "NSE 20",
                    value = Double.NaN,
                    changePct = null,
                    asOf = "",
                    freshnessMode = "UNKNOWN"
                )
            )
        )

        assertEquals(1, rows.size)
        assertEquals("^NASI", rows.single().symbol)
        assertEquals(HomeMarketDataMode.CURRENT_SESSION, rows.single().dataMode)
        assertEquals(1.25, rows.single().changePct ?: Double.NaN, 0.0001)
    }

    @Test fun providerIndexFreshnessModesArePreservedForHomeEvidence() {
        val rows = HomeMarketIndexPresentation.fromProvider(
            listOf(
                MyStocksCache.MarketIndex("^NASI", "NASI", 235.0, null, "2026-09-23", "END_OF_DAY"),
                MyStocksCache.MarketIndex("^N20I", "NSE 20", 1900.0, -0.5, "2026-09-22", "STALE"),
                MyStocksCache.MarketIndex("^N25I", "NSE 25", 4100.0, 0.2, "", "UNRECOGNIZED")
            )
        )

        assertEquals(
            listOf(
                HomeMarketDataMode.END_OF_DAY,
                HomeMarketDataMode.STALE,
                HomeMarketDataMode.UNKNOWN
            ),
            rows.map { it.dataMode }
        )
    }

    @Test fun noContextCardIsInventedWithoutSectorOrVerifiedIndexData() {
        val snapshot = HomeIntelligenceEngine.build(
            stocks = listOf(stock("A", 2.0, "Other")),
            news = emptyList()
        )

        assertTrue(HomeMarketContextPresentation.items(snapshot).isEmpty())
    }
}
