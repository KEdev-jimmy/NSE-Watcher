package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache.HistoryPoint
import ke.co.nsewatcher.data.MyStocksCache.HistoryResult
import org.junit.Assert.*
import org.junit.Test

class CompanyResearchPresentationTest {
    private fun quote(price: Double = 110.0, at: String = "2026-09-22T12:00:00Z", change: Double = 2.0, available: Boolean = true) =
        Stock("KCB", "KCB Group", price, change, emptyList(), observedAt = at, changeAvailable = available, previousClose = 105.0, source = "MyStocks Africa")

    @Test fun newerQuoteWinsWithoutRecalculatingProviderChange() {
        val s = CompanyResearchPresentation.session(quote(change = 0.0), HistoryResult(
            sessionClose = 108.0, sessionCloseAt = "2026-09-22T11:00:00Z", dailyChangePct = 3.0, previousSessionClose = 100.0
        ))
        assertEquals(110.0, s.latest!!, 0.001)
        assertEquals(0.0, s.dailyChange!!, 0.001)
        assertEquals("2026-09-22T12:00:00Z", s.observedAt)
    }

    @Test fun staleQuoteCannotOverrideNewerSession() {
        val s = CompanyResearchPresentation.session(quote(at = "2026-09-21T12:00:00Z", change = -5.0), HistoryResult(
            sessionClose = 108.0, sessionCloseAt = "2026-09-22T11:00:00Z", dailyChangePct = 3.0
        ))
        assertEquals(108.0, s.latest!!, 0.001)
        assertEquals(3.0, s.dailyChange!!, 0.001)
        assertNull(s.previousClose)
    }

    @Test fun yesterdayHistoryCannotSupplyTodayOpenOrDailyChange() {
        val s = CompanyResearchPresentation.session(quote(available = false), HistoryResult(
            points = listOf(HistoryPoint(101.0, "2026-09-21T12:00:00Z", open = 99.0, high = 103.0, low = 97.0)),
            previousSessionClose = 98.0, dailyChangePct = 3.0
        ))
        assertNull(s.open); assertNull(s.high); assertNull(s.low); assertNull(s.dailyChange)
        assertEquals(105.0, s.previousClose!!, 0.001)
    }

    @Test fun missingDataIsNotZeroMovement() {
        val s = CompanyResearchPresentation.session(quote(price = Double.NaN, at = "", change = Double.NaN), HistoryResult())
        assertNull(s.latest); assertNull(s.dailyChange); assertNull(s.sinceOpen)
        assertTrue(CompanyResearchPresentation.meaning(s.dailyChange).contains("unavailable"))
        assertFalse(CompanyResearchPresentation.meaning(s.dailyChange).contains("unchanged"))
    }

    @Test fun sessionOhlcUsesChronologicalActualCandles() {
        val s = CompanyResearchPresentation.session(quote(price = Double.NaN), HistoryResult(points = listOf(
            HistoryPoint(108.0, "2026-09-22T12:00:00Z", 103.0, 110.0, 102.0),
            HistoryPoint(102.0, "2026-09-22T07:00:00Z", 100.0, 104.0, 99.0)
        )))
        assertEquals(108.0, s.latest!!, 0.001)
        assertEquals(100.0, s.open!!, 0.001)
        assertEquals(110.0, s.high!!, 0.001)
        assertEquals(99.0, s.low!!, 0.001)
        assertEquals(8.0, s.sinceOpen!!, 0.001)
    }

    @Test fun earlierSameDayChangeCannotDescribeNewerPrice() {
        val s = CompanyResearchPresentation.session(quote(available = false), HistoryResult(
            sessionClose = 108.0, sessionCloseAt = "2026-09-22T11:00:00Z", dailyChangePct = 1.5
        ))
        assertNull(s.dailyChange)
    }

    @Test fun identicalObservationCanUseHistoryChangeWhenQuoteChangeIsMissing() {
        val s = CompanyResearchPresentation.session(quote(available = false), HistoryResult(
            sessionClose = 110.0, sessionCloseAt = "2026-09-22T12:00:00Z", dailyChangePct = 1.5
        ))
        assertEquals(1.5, s.dailyChange!!, 0.001)
    }

    @Test fun suppliedMillionsKeepTheirMagnitudeAndSign() {
        assertEquals("KSh 12.50B", CompanyResearchPresentation.financialValue("12,500", "Millions KES"))
        assertEquals("KSh -1.50B", CompanyResearchPresentation.financialValue("-1500", "Millions KES"))
        assertEquals("KSh 25.00M", CompanyResearchPresentation.financialValue("25", "Millions KES"))
        assertEquals("25", CompanyResearchPresentation.financialValue("25", ""))
        assertEquals("Unavailable", CompanyResearchPresentation.financialValue("n/a", "Millions KES"))
    }

    @Test fun linksRequireExplicitWebSchemeAndHost() {
        assertEquals("https://example.com/results", CompanyResearchPresentation.sourceUrl("https://example.com/results"))
        for (invalid in listOf("", "financials", "javascript:alert(1)", "file:///tmp/report", "https:///report", "https://user:password@example.com")) {
            assertNull(invalid, CompanyResearchPresentation.sourceUrl(invalid))
        }
    }

    @Test fun timestampsKeepNairobiDatesAndNeverInventTimes() {
        assertEquals("23 Sep 2026, 01:00 EAT", CompanyResearchPresentation.date("2026-09-22T22:00:00Z"))
        assertEquals("22 Sep 2026", CompanyResearchPresentation.date("2026-09-22"))
        assertNull(CompanyResearchPresentation.timestamp("unknown"))
        assertEquals("Date unavailable", CompanyResearchPresentation.date(""))
    }
}
