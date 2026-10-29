package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HomeBriefingPresentationTest {
    private val at = "2026-10-29T10:00:00Z"

    private fun stock(symbol: String, volume: Long, average: Long = 0L) = Stock(
        symbol = symbol,
        name = symbol,
        price = 25.0,
        change = 2.0,
        history = emptyList(),
        volume = volume,
        volumeAvailable = true,
        averageVolume = average,
        averageVolumeAvailable = average > 0,
        observedAt = at,
        freshnessMode = "RECENT"
    )

    @Test fun homePortfolioUsesSharesCostNotTotalDeposits() {
        val state = PracticeState(
            enabled = true,
            contributed = 1_000_000.0,
            cash = 600_000.0,
            holdings = listOf(PracticeHolding("KCB", 100, 100_000.0)),
            quotes = listOf(PracticeQuote("KCB", 900.0, at))
        )
        val current = listOf(stock("KCB", 100))

        val summary = HomeBriefingPresentation.portfolio(state, current)

        assertEquals(100_000.0, summary!!.invested, 0.01)
        assertEquals(90_000.0, summary.marketValue, 0.01)
        assertEquals(-10_000.0, summary.gain, 0.01)
        assertEquals(-10.0, summary.gainPercent!!, 0.01)
        assertEquals(600_000.0, summary.availableCash, 0.01)
    }

    @Test fun missingHoldingQuoteShowsEstimateNotCertifiedProfit() {
        val state = PracticeState(
            enabled = true,
            cash = 10_000.0,
            holdings = listOf(PracticeHolding("ABSA", 50, 5_000.0))
        )

        val summary = HomeBriefingPresentation.portfolio(state, emptyList())!!

        assertTrue(summary.provisional)
        assertNull(summary.gainPercent)
        assertNull(summary.topPerformer)
        assertEquals(5_000.0, summary.marketValue, 0.01)
    }

    @Test fun topPerformerUsesHoldingReturnNotDailyQuoteChange() {
        val state = PracticeState(
            enabled = true,
            holdings = listOf(
                PracticeHolding("KCB", 10, 500.0),
                PracticeHolding("ABSA", 10, 1000.0)
            ),
            quotes = listOf(PracticeQuote("KCB", 60.0, at), PracticeQuote("ABSA", 95.0, at))
        )
        val quotes = listOf(stock("KCB", 10), stock("ABSA", 10))

        val summary = HomeBriefingPresentation.portfolio(state, quotes)!!

        assertEquals("KCB", summary.topPerformer?.symbol)
        assertEquals(20.0, summary.topPerformer!!.gainPercent, 0.01)
    }

    @Test fun tradingActivityNeverInfersLiquidityWithoutVerifiedVolumes() {
        val recent = stock("KCB", 50_000, average = 25_000)
        val unavailable = stock("SCOM", 1_000_000).copy(volumeAvailable = false)
        val stale = stock("EQTY", 2_000_000).copy(freshnessMode = "STALE")

        val rows = HomeBriefingPresentation.tradingActivity(
            listOf(recent, unavailable, stale),
            Instant.parse("2026-10-29T13:00:00Z")
        )

        assertEquals(listOf("KCB"), rows.map { it.stock.symbol })
        assertEquals(2.0, rows.single().vsAverage!!, 0.01)
        assertFalse(rows.any { it.stock.symbol == "SCOM" || it.stock.symbol == "EQTY" })
    }

    @Test fun marketStatusIsShortAndHonestWhenUnknown() {
        assertEquals("Market open", HomeBriefingPresentation.marketStatus(true, true))
        assertEquals("Market closed", HomeBriefingPresentation.marketStatus(true, false))
        assertEquals("Status unavailable", HomeBriefingPresentation.marketStatus(false, false))
    }
}
