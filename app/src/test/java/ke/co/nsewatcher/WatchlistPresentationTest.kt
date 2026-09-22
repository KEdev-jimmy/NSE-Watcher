package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache.HistoryPoint
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import org.junit.Assert.*
import org.junit.Test

class WatchlistPresentationTest {
    private fun stock(symbol: String = "KCB", name: String = "KCB Group", price: Double = 50.0) = Stock(symbol, name, price, 2.0, emptyList())
    private fun story(id: String, symbol: String, name: String = "", at: String = "2026-09-22") =
        NewsItem(id, "Headline", "", "", "Source", at, "News", symbol, name, "", "", "", "", "")

    @Test fun savedSymbolsSurviveMissingQuotesWithoutCataloguePrices() {
        val rows = WatchlistPresentation.companies(listOf(" kcb ", "KCB", "MISSING", ""), listOf(stock()), emptyList())
        assertEquals(listOf("KCB", "MISSING"), rows.map { it.symbol })
        assertEquals("KCB Group", rows.first().name)
        assertTrue(rows.all { it.price.isNaN() && !it.changeAvailable && !it.volumeAvailable })
    }

    @Test fun actualQuoteValuesArePreservedWhileMetadataIsMerged() {
        val quote = stock("kcb", "", 60.0).copy(observedAt = "2026-09-22T11:00:00Z", change = -1.5)
        val row = WatchlistPresentation.companies(listOf("KCB"), listOf(stock()), listOf(quote)).single()
        assertEquals("KCB Group", row.name)
        assertEquals(60.0, row.price, 0.0)
        assertEquals(-1.5, row.change, 0.0)
        assertEquals(quote.observedAt, row.observedAt)
    }

    @Test fun priceFilterIncludesPausedRulesButExcludesNewsOnlyRules() {
        val rows = listOf(stock(), stock("SCOM", "Safaricom"))
        val rules = listOf(PriceAlert("a", "kcb", AlertType.PRICE_ABOVE, 55.0, false), PriceAlert("b", "SCOM", AlertType.NEWS, null, true))
        assertEquals(listOf("KCB"), WatchlistPresentation.filter(rows, " group ", true, rules).map { it.symbol })
        assertTrue(WatchlistPresentation.filter(rows, "scom", true, rules).isEmpty())
        assertEquals("SCOM", WatchlistPresentation.filter(rows, "safaricom", false, rules).single().symbol)
    }

    @Test fun companyNewsRequiresExactIdentityAndDeduplicatesArticles() {
        val items = listOf(story("a", "kcb"), story("a", "KCB"), story("b", "", "KCB Group"),
            story("c", "SCOM", "KCB Group"), story("d", "", "KCB"), story("e", ""))
        assertEquals(setOf("a", "b"), WatchlistPresentation.linkedNews(items, listOf(stock())).map { it.id }.toSet())
        assertTrue(WatchlistPresentation.linkedNews(items, emptyList()).isEmpty())
    }

    @Test fun chartsRejectInvalidDataAndUseChronologicalUniqueDates() {
        val points = listOf(HistoryPoint(12.0, "2026-09-22"), HistoryPoint(10.0, "2026-09-21"),
            HistoryPoint(10.0, "2026-09-21"), HistoryPoint(0.0, "2026-09-20"),
            HistoryPoint(Double.NaN, "2026-09-19"), HistoryPoint(9.0, "unknown"))
        assertEquals(listOf(10.0, 12.0), WatchlistPresentation.trend(points).map { it.close })
        assertTrue(WatchlistPresentation.trend(listOf(HistoryPoint(25.0))).isEmpty())
    }

    @Test fun thresholdsRejectNonfiniteAndNonpositiveInput() {
        for (invalid in listOf("", "NaN", "Infinity", "1e999", "0", "-4", "abc")) assertNull(invalid, WatchlistPresentation.threshold(invalid))
        assertEquals(1250.5, WatchlistPresentation.threshold("1,250.50")!!, 0.0)
        assertFalse(WatchlistPresentation.needsThreshold(AlertType.NEWS))
        assertFalse(WatchlistPresentation.supportedTypes.contains(AlertType.BREAKOUT))
    }
}
