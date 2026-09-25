package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupDataLoaderTest {
    private val quote = Stock(
        symbol = "KCB",
        name = "KCB Group",
        price = 50.0,
        change = 1.0,
        history = emptyList()
    )

    private val story = NewsItem(
        id = "n1",
        title = "KCB update",
        summary = "",
        body = "",
        source = "Issuer",
        publishedAt = "2026-09-25T08:00:00Z",
        category = "Company News",
        symbol = "KCB",
        companyName = "KCB Group",
        imageUrl = "",
        url = "https://example.com/kcb",
        dividendAmount = "",
        exDate = "",
        paymentDate = ""
    )

    @Test
    fun slowNewsCannotDiscardReadyMarketSources() = runTest {
        val loader = StartupDataLoader(
            stocks = { listOf(quote) },
            news = {
                delay(2_000L)
                NewsCache.FeedResult(listOf(story))
            },
            companies = { listOf(quote.copy(price = Double.NaN)) },
            status = {
                MyStocksCache.MarketStatus(
                    isOpen = false,
                    status = "CLOSED",
                    isKnown = true
                )
            },
            sourceTimeoutMs = 1_000L
        )

        val result = loader.load()

        assertEquals("KCB", result.stocks.single().symbol)
        assertEquals("KCB", result.companies.single().symbol)
        assertTrue(result.marketStatus.isKnown)
        assertTrue(result.news.isEmpty())
        assertFalse(result.completed)
        assertEquals(setOf("news"), result.timedOutSources)
    }

    @Test
    fun completedStartupKeepsAllIndependentSources() = runTest {
        val loader = StartupDataLoader(
            stocks = { listOf(quote) },
            news = { NewsCache.FeedResult(listOf(story)) },
            companies = { listOf(quote.copy(price = Double.NaN)) },
            status = {
                MyStocksCache.MarketStatus(
                    isOpen = true,
                    status = "OPEN",
                    isKnown = true
                )
            },
            sourceTimeoutMs = 1_000L
        )

        val result = loader.load()

        assertTrue(result.completed)
        assertTrue(result.timedOutSources.isEmpty())
        assertEquals("KCB", result.stocks.single().symbol)
        assertEquals("n1", result.news.single().id)
        assertEquals("KCB", result.companies.single().symbol)
        assertEquals("OPEN", result.marketStatus.status)
    }
}
