package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache.HistoryPoint
import ke.co.nsewatcher.data.MyStocksCache.HistoryResult
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MarketPresentationTest {
    private val end = LocalDate.parse("2026-09-22")
    private fun stock(change: Double = 2.0) = Stock("KCB", "KCB Group", 50.0, change, emptyList(), sector = "Banking", observedAt = "2026-09-22T11:00:00Z")
    private fun history(start: LocalDate, finish: LocalDate = end, days: Long = 7): HistoryResult {
        val dates = generateSequence(start) { it.plusDays(days) }.takeWhile { it < finish }.toList() + finish
        return HistoryResult(points = dates.mapIndexed { i, date -> HistoryPoint(100.0 + i, date.toString()) })
    }
    @Test fun whyMovingActionRequiresARealDailyChange() {
        assertTrue(MarketPresentation.movementQuestionAvailable(stock(2.0)))
        assertFalse(MarketPresentation.movementQuestionAvailable(stock(Double.NaN)))
        assertFalse(MarketPresentation.movementQuestionAvailable(stock().copy(changeAvailable = false)))
        assertFalse(MarketPresentation.movementQuestionAvailable(stock().copy(price = 0.0)))
    }

    @Test fun missingChangesAreNotFlatAndInvalidPricesAreExcluded() {
        val b = MarketPresentation.breadth(listOf(stock(), stock(0.0), stock(-1.0), stock().copy(changeAvailable = false), stock(Double.NaN), stock().copy(price = 0.0)))
        assertEquals(MarketBreadth(1, 1, 1, 6), b)
    }
    @Test fun sectorsUseEqualWeightAndRetainMissingCoverage() {
        val sectors = MarketPresentation.sectors(listOf(stock(4.0), stock(-2.0), stock().copy(changeAvailable = false), stock().copy(sector = "Other", changeAvailable = false)))
        assertEquals("Banking", sectors.first().name)
        assertEquals(1.0, sectors.first().average!!, 0.0001)
        assertEquals(2, sectors.first().breadth.covered)
        assertEquals(3, sectors.first().breadth.total)
        assertNull(sectors.last().average)
    }
    @Test fun dailyChangeUsesProviderValueAndRejectsOldOrFutureQuotes() {
        assertEquals(2.0, MarketPresentation.eligible(stock(), "1D", HistoryResult(), end).performance!!.change, 0.0)
        for (date in listOf("", "2026-09-01", "2026-09-23")) assertNull(MarketPresentation.eligible(stock().copy(observedAt = date), "1D", HistoryResult(), end).performance)
    }
    @Test fun datedHistoryUsesActualEndpointsAndSortsObservations() {
        val h = history(end.minusMonths(1))
        val dirty = h.copy(points = h.points.reversed() + h.points.first() + HistoryPoint(0.0, "2026-09-03") + HistoryPoint(8.0, "unknown"))
        val p = MarketPresentation.eligible(stock(), "1M", dirty, end).performance!!
        assertEquals("2026-08-22", p.first)
        assertEquals("2026-09-22", p.last)
        assertEquals(5.0, p.change, 0.0001)
    }
    @Test fun truncatedStartAndEndAreExcluded() {
        assertEquals("History does not reach period start", MarketPresentation.eligible(stock(), "1Y", history(end.minusMonths(1)), end).reason)
        assertEquals("History does not reach period end", MarketPresentation.eligible(stock(), "1M", history(end.minusMonths(1), end.minusDays(10)), end).reason)
    }
    @Test fun endpointsAloneDoNotHideMissingInteriorHistory() {
        val h = HistoryResult(points = listOf(HistoryPoint(50.0, "2025-09-22"), HistoryPoint(60.0, "2026-09-22")))
        assertEquals("Large gaps in returned history", MarketPresentation.eligible(stock(), "1Y", h, end).reason)
    }
    @Test fun ytdRequiresPriorYearEndBaseline() {
        assertNull(MarketPresentation.eligible(stock(), "YTD", history(LocalDate.parse("2026-01-02")), end).performance)
        val p = MarketPresentation.eligible(stock(), "YTD", history(LocalDate.parse("2025-12-31")), end).performance!!
        assertEquals("2025-12-31", p.first)
        assertEquals("1y", MarketPresentation.historyPeriod("YTD"))
    }
    @Test fun longPeriodsAcceptMonthlySamplingButNotMissingYears() {
        for (range in listOf("3Y", "5Y")) {
            val start = MarketPresentation.start(range, end)
            val dates = generateSequence(start) { it.plusMonths(1) }.takeWhile { it <= end }.toList()
            val h = HistoryResult(points = dates.mapIndexed { i, d -> HistoryPoint(100.0 + i, d.toString()) })
            assertNotNull(MarketPresentation.eligible(stock(), range, h, end).performance)
        }
    }
    @Test fun losersNeverContainGainersOrUnchangedCompanies() {
        val rows = listOf(-1.0, 0.0, 2.0, -5.0).map { MarketPerformance(stock(it), it, "", "") }
        assertEquals(listOf(-5.0, -1.0), MarketPresentation.ranked(rows, "Losers").map { it.change })
        assertEquals(listOf(2.0), MarketPresentation.ranked(rows, "Gainers").map { it.change })
    }
    private fun observation(at: String) = MarketSavedObservation(at, at, "Provider", 1, 0, 0, 1)
    @Test fun savedDatesAreBoundedAndKeepLatestActualObservation() {
        val old = (0..100).map { observation(end.minusDays(it.toLong()).toString()) }
        val merged = mergeMarketObservations(old, observation("2026-09-22T12:00:00Z"))
        assertEquals(90, merged.size)
        assertEquals("2026-09-22T12:00:00Z", merged.first().observedAt)
        assertEquals(merged, mergeMarketObservations(merged, observation("unknown")))
        assertEquals(merged, mergeMarketObservations(merged, observation("2026-09-22T09:00:00Z")))
    }
    @Test
    fun unknownRefreshDoesNotErasePreviouslyKnownDisplayStatus() {
        val known = ke.co.nsewatcher.data.MyStocksCache.MarketStatus(
            isOpen = true,
            status = "OPEN",
            isKnown = true
        )
        val unknown = ke.co.nsewatcher.data.MyStocksCache.MarketStatus()

        assertEquals(known, MarketPresentation.displayStatus(known, unknown))
    }

    @Test
    fun newlyKnownRefreshReplacesPriorDisplayStatus() {
        val closed = ke.co.nsewatcher.data.MyStocksCache.MarketStatus(
            isOpen = false,
            status = "CLOSED",
            isKnown = true
        )
        val open = ke.co.nsewatcher.data.MyStocksCache.MarketStatus(
            isOpen = true,
            status = "OPEN",
            isKnown = true
        )

        assertEquals(open, MarketPresentation.displayStatus(closed, open))
        assertEquals(open, MarketPresentation.displayStatus(
            ke.co.nsewatcher.data.MyStocksCache.MarketStatus(),
            open
        ))
    }

}
