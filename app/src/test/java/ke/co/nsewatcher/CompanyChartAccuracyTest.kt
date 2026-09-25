package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache.HistoryPoint
import ke.co.nsewatcher.data.MyStocksCache.HistoryResult
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CompanyChartAccuracyTest {
    private val today = LocalDate.of(2026, 9, 22)
    private fun result(vararg points: HistoryPoint) = HistoryResult(points = points.toList())
    private fun value(range: String, history: HistoryResult) =
        CompanyChartAccuracy.periodReturn(range, history, today = today)

    private fun sampled(
        start: LocalDate,
        stepDays: Long = 7,
        firstPrice: Double = 100.0,
        lastPrice: Double = 110.0
    ): HistoryResult {
        val dates = (
            generateSequence(start) { it.plusDays(stepDays) }
                .takeWhile { it < today }
                .toList() + today
        ).distinct()
        val denominator = (dates.size - 1).coerceAtLeast(1)
        return HistoryResult(
            points = dates.mapIndexed { index, date ->
                val progress = index.toDouble() / denominator
                HistoryPoint(firstPrice + ((lastPrice - firstPrice) * progress), date.toString())
            }
        )
    }

    @Test fun singleObservationDoesNotBecomeZeroReturn() {
        assertNull(value("3D", result(HistoryPoint(100.0, "2026-09-22"))))
    }

    @Test fun shortHistoryCannotMasqueradeAsFiveYears() {
        assertNull(value("5Y", result(
            HistoryPoint(100.0, "2026-09-19"), HistoryPoint(110.0, "2026-09-22")
        )))
    }

    @Test fun completeDatedRangeProducesReturn() {
        assertEquals(10.0, value("1M", sampled(today.minusMonths(1)))!!, 0.00001)
    }

    @Test fun unorderedObservationsUseChronologicalBoundaries() {
        val ordered = sampled(today.minusMonths(1))
        assertEquals(
            10.0,
            value("1M", ordered.copy(points = ordered.points.reversed()))!!,
            0.00001
        )
    }

    @Test fun missingInvalidAndDuplicateDatesAreUnavailable() {
        for (date in listOf("", "bad-date", "2026-09-22")) {
            assertNull(value("1M", result(
                HistoryPoint(100.0, date), HistoryPoint(110.0, "2026-09-22")
            )))
        }
    }

    @Test fun staleAndFutureEndpointsAreUnavailable() {
        for (date in listOf("2026-09-01", "2026-09-23")) {
            assertNull(value("1M", result(
                HistoryPoint(100.0, "2026-08-22"), HistoryPoint(110.0, date)
            )))
        }
    }

    @Test fun invalidPricesAreUnavailable() {
        for (price in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(value("1M", result(
                HistoryPoint(price, "2026-08-22"), HistoryPoint(110.0, "2026-09-22")
            )))
        }
    }

    @Test fun allSupportedHistoricalRangesHaveCoverageChecks() {
        val starts = mapOf(
            "3D" to today.minusDays(3), "1W" to today.minusWeeks(1),
            "1M" to today.minusMonths(1), "3M" to today.minusMonths(3),
            "6M" to today.minusMonths(6), "1Y" to today.minusYears(1),
            "3Y" to today.minusYears(3), "5Y" to today.minusYears(5)
        )
        for ((range, start) in starts) {
            val step = when (range) {
                "3D" -> 1L
                "1W" -> 2L
                "1Y" -> 14L
                "3Y", "5Y" -> 30L
                else -> 7L
            }
            assertEquals(range, 10.0, value(range, sampled(start, step))!!, 0.00001)
        }
    }

    @Test fun zeroReturnIsValidWithCoveredObservations() {
        assertEquals(
            0.0,
            value("1M", sampled(today.minusMonths(1), firstPrice = 100.0, lastPrice = 100.0))!!,
            0.00001
        )
    }

    @Test fun largeInteriorGapIsRejectedExactlyLikeMarketPerformance() {
        val sparse = result(
            HistoryPoint(100.0, today.minusMonths(1).toString()),
            HistoryPoint(110.0, today.toString())
        )
        assertNull(value("1M", sparse))
        assertEquals(
            "Large gaps in returned history",
            MarketPresentation.historicalCoverage("1M", sparse, today).reason
        )
    }

    @Test fun sessionHeadingUsesNairobiDateAndNeverInventsToday() {
        assertEquals("Today at a glance", CompanyChartAccuracy.sessionTitle("2026-09-21T22:30:00Z", today))
        assertEquals("Session: 2026-09-18", CompanyChartAccuracy.sessionTitle("2026-09-18T12:00:00Z", today))
        assertEquals("Session date unavailable", CompanyChartAccuracy.sessionTitle("", today))
    }

    @Test fun observedExtremesUseOhlcNotClosingPrices() {
        val ohlc = CompanyChartAccuracy.observedOhlc(listOf(
            HistoryPoint(100.0, "2026-09-22", open = 99.0, high = 108.0, low = 97.0),
            HistoryPoint(102.0, "2026-09-22", open = 100.0, high = 110.0, low = 98.0)
        ))
        assertEquals(99.0, ohlc.open!!, 0.00001)
        assertEquals(110.0, ohlc.high!!, 0.00001)
        assertEquals(97.0, ohlc.low!!, 0.00001)
    }

    @Test fun missingOhlcNeverFallsBackToClose() {
        val ohlc = CompanyChartAccuracy.observedOhlc(listOf(HistoryPoint(100.0)))
        assertNull(ohlc.open)
        assertNull(ohlc.high)
        assertNull(ohlc.low)
    }

    @Test fun incompleteOrInvalidExtremesStayUnavailable() {
        val ohlc = CompanyChartAccuracy.observedOhlc(listOf(
            HistoryPoint(100.0, high = 105.0, low = 95.0),
            HistoryPoint(101.0, high = 99.0)
        ))
        assertNull(ohlc.high)
        assertNull(ohlc.low)
    }

    @Test fun dailyReturnPreservesSuppliedMovement() {
        assertEquals(2.5, CompanyChartAccuracy.periodReturn("1D", HistoryResult(dailyChangePct = 2.5))!!, 0.00001)
        assertEquals(-1.0, CompanyChartAccuracy.periodReturn("1D", HistoryResult(), -1.0)!!, 0.00001)
        assertNull(CompanyChartAccuracy.periodReturn("1D", HistoryResult()))
    }
}
