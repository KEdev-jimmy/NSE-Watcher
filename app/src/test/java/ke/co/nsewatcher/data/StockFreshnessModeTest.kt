package ke.co.nsewatcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StockFreshnessModeTest {
    private val zone = ZoneId.of("Africa/Nairobi")

    @Test
    fun sameDayQuoteWithoutSessionStatusIsStillIdentifiedAsCurrentDay() {
        val today = LocalDate.now(zone).toString()

        assertEquals(
            "CURRENT_DAY",
            MyStocksCache.stockFreshnessMode(
                observedAt = today,
                marketOpen = null
            )
        )
    }

    @Test
    fun knownSessionStillProducesSessionSpecificFreshness() {
        val today = LocalDate.now(zone).toString()

        assertEquals(
            "CURRENT_SESSION",
            MyStocksCache.stockFreshnessMode(today, marketOpen = true)
        )
        assertEquals(
            "END_OF_DAY",
            MyStocksCache.stockFreshnessMode(today, marketOpen = false)
        )
    }

    @Test
    fun providerStaleAndPreviousDayQuotesRemainStale() {
        val today = LocalDate.now(zone).toString()
        val yesterday = LocalDate.now(zone).minusDays(1).toString()

        assertEquals(
            "STALE",
            MyStocksCache.stockFreshnessMode(
                observedAt = today,
                marketOpen = null,
                providerStale = true
            )
        )
        assertEquals(
            "STALE",
            MyStocksCache.stockFreshnessMode(
                observedAt = yesterday,
                marketOpen = null
            )
        )
    }

    @Test
    fun missingOrInvalidObservationTimeRemainsUnknown() {
        assertEquals("UNKNOWN", MyStocksCache.stockFreshnessMode("", null))
        assertEquals("UNKNOWN", MyStocksCache.stockFreshnessMode("not-a-date", null))
    }
}
