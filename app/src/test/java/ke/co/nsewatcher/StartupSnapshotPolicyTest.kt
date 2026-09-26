package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSnapshotPolicyTest {
    private val stock = Stock(
        symbol = "KCB",
        name = "KCB Group",
        price = 52.0,
        change = 1.2,
        history = emptyList(),
        observedAt = "2026-09-25T10:00:00Z",
        freshnessMode = "CURRENT_DAY",
        dataOrigin = "backend"
    )

    private val company = stock.copy(
        price = Double.NaN,
        change = Double.NaN,
        changeAvailable = false,
        volumeAvailable = false,
        dataOrigin = "company_catalog"
    )

    private val story = NewsItem(
        id = "n1",
        title = "KCB update",
        summary = "",
        body = "",
        source = "Issuer",
        publishedAt = "2026-09-25T09:00:00Z",
        category = "Company News",
        symbol = "KCB",
        companyName = "KCB Group",
        imageUrl = "",
        url = "https://example.com/kcb",
        dividendAmount = "",
        exDate = "",
        paymentDate = "",
        freshnessMode = "CURRENT"
    )

    @Test
    fun recentOfflineQuotesAreAlwaysMarkedStale() {
        val now = 10_000_000L
        val fallback = StartupSnapshotPolicy.fallback(
            PersistedStartupSnapshot(
                stocks = listOf(stock),
                stocksSavedAtMs = now - 60_000L
            ),
            now
        )

        assertEquals("STALE", fallback.stocks.single().freshnessMode)
        assertEquals("offline_snapshot", fallback.stocks.single().dataOrigin)
        assertEquals(setOf("stocks"), fallback.restoredSources)
    }

    @Test
    fun expiredQuoteSnapshotIsNotRestored() {
        val now = StartupSnapshotPolicy.STOCK_MAX_AGE_MS + 10_000L
        val fallback = StartupSnapshotPolicy.fallback(
            PersistedStartupSnapshot(
                stocks = listOf(stock),
                stocksSavedAtMs = 1L
            ),
            now
        )

        assertTrue(fallback.stocks.isEmpty())
        assertFalse(fallback.hasAny)
    }

    @Test
    fun companyCatalogueCanOutliveQuoteSnapshot() {
        val now = StartupSnapshotPolicy.STOCK_MAX_AGE_MS + 86_400_000L
        val fallback = StartupSnapshotPolicy.fallback(
            PersistedStartupSnapshot(
                stocks = listOf(stock),
                stocksSavedAtMs = 1L,
                companies = listOf(company),
                companiesSavedAtMs = now - 86_400_000L
            ),
            now
        )

        assertTrue(fallback.stocks.isEmpty())
        assertEquals("KCB", fallback.companies.single().symbol)
        assertEquals("offline_snapshot_catalog", fallback.companies.single().dataOrigin)
        assertFalse(fallback.companies.single().price.isFinite())
    }

    @Test
    fun recentNewsIsRestoredAsStaleEvidence() {
        val now = 5_000_000L
        val fallback = StartupSnapshotPolicy.fallback(
            PersistedStartupSnapshot(
                news = listOf(story),
                newsSavedAtMs = now - 1_000L
            ),
            now
        )

        assertEquals("STALE", fallback.news.single().freshnessMode)
        assertEquals(setOf("news"), fallback.restoredSources)
    }
}
