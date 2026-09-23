package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyChangeDetector
import ke.co.nsewatcher.data.CompanyChangeLedger
import ke.co.nsewatcher.data.CompanyChangeState
import ke.co.nsewatcher.data.CompanyDataChangeKind
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CompanyChangeStoreTest {
    private val now = Instant.parse("2026-09-23T10:00:00Z")

    private fun intelligence(
        period: String = "FY 2025",
        revenue: String = "100",
        profit: String = "20",
        eps: String = "2.00",
        dividends: List<CompanyIntelligenceCache.Dividend> = emptyList()
    ) = CompanyIntelligenceCache.Result(
        profile = CompanyIntelligenceCache.Profile(
            financialPeriod = period,
            revenue = revenue,
            profit = profit,
            eps = eps
        ),
        dividends = dividends,
        source = "Verified provider"
    )

    private fun dividend(amount: String, exDate: String = "2026-10-01") =
        CompanyIntelligenceCache.Dividend(
            amount = amount,
            exDate = exDate,
            paymentDate = "2026-10-20",
            declaredDate = "2026-09-20",
            type = "Final",
            status = "Declared"
        )

    @Test fun firstCompanyObservationEstablishesBaselineWithoutAChange() {
        val state = CompanyChangeLedger.observe(
            CompanyChangeState(),
            "KCB",
            intelligence(),
            now
        )

        assertTrue(state.events.isEmpty())
        assertEquals("FY 2025", state.snapshots["KCB"]?.financialPeriod)
    }

    @Test fun newReportingPeriodCreatesOneGroupedDevelopment() {
        val baseline = CompanyChangeLedger.observe(
            CompanyChangeState(),
            "KCB",
            intelligence(),
            now
        )
        val next = CompanyChangeLedger.observe(
            baseline,
            "KCB",
            intelligence(period = "FY 2026", revenue = "120", profit = "25", eps = "2.40"),
            now.plusSeconds(6 * 3600)
        )

        assertEquals(1, next.events.size)
        assertEquals(CompanyDataChangeKind.REPORTING_PERIOD, next.events.single().kind)
        assertTrue(next.events.single().detail.contains("FY 2026"))
        assertTrue(next.events.single().detail.contains("Revenue 120"))
    }

    @Test fun changedFiguresInsideSamePeriodAreGroupedAsProviderUpdate() {
        val baseline = CompanyChangeLedger.observe(
            CompanyChangeState(),
            "KCB",
            intelligence(),
            now
        )
        val next = CompanyChangeLedger.observe(
            baseline,
            "KCB",
            intelligence(revenue = "101", profit = "21"),
            now.plusSeconds(6 * 3600)
        )

        assertEquals(1, next.events.size)
        assertEquals(CompanyDataChangeKind.REPORTED_FIGURES, next.events.single().kind)
        assertTrue(next.events.single().detail.contains("Revenue 100 → 101"))
        assertTrue(next.events.single().detail.contains("Profit 20 → 21"))
    }

    @Test fun dividendRecordDifferenceCreatesOneDividendDevelopment() {
        val baseline = CompanyChangeLedger.observe(
            CompanyChangeState(),
            "KCB",
            intelligence(dividends = listOf(dividend("1.00"))),
            now
        )
        val next = CompanyChangeLedger.observe(
            baseline,
            "KCB",
            intelligence(dividends = listOf(dividend("1.50"))),
            now.plusSeconds(6 * 3600)
        )

        assertEquals(1, next.events.size)
        assertEquals(CompanyDataChangeKind.DIVIDEND, next.events.single().kind)
    }

    @Test fun identicalObservationDoesNotCreateDuplicateEvents() {
        val baseline = CompanyChangeLedger.observe(
            CompanyChangeState(),
            "KCB",
            intelligence(),
            now
        )
        val next = CompanyChangeLedger.observe(
            baseline,
            "KCB",
            intelligence(),
            now.plusSeconds(6 * 3600)
        )

        assertTrue(next.events.isEmpty())
    }

    @Test fun dueScheduleChecksAtMostSixSymbolsAndPrunesUnfollowedState() {
        val symbols = (1..8).map { "S$it" }.toSet()
        val plan = CompanyChangeLedger.sync(CompanyChangeState(), symbols, now)

        assertEquals(6, plan.dueSymbols.size)
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5", "S6"), plan.dueSymbols)

        val observed = CompanyChangeLedger.observe(plan.state, "S1", intelligence(), now)
        val tooSoon = CompanyChangeLedger.sync(observed, setOf("S1"), now.plusSeconds(3600))
        assertTrue(tooSoon.dueSymbols.isEmpty())

        val dueLater = CompanyChangeLedger.sync(observed, setOf("S1"), now.plusSeconds(6 * 3600))
        assertEquals(listOf("S1"), dueLater.dueSymbols)

        val pruned = CompanyChangeLedger.sync(observed, setOf("KCB"), now.plusSeconds(6 * 3600)).state
        assertTrue("S1" !in pruned.snapshots)
    }

    @Test fun detectorUsesStableIdsForSameNewObservation() {
        val before = CompanyChangeDetector.snapshot("KCB", intelligence())
        val result = intelligence(revenue = "101")
        val after = CompanyChangeDetector.snapshot("KCB", result)

        val first = CompanyChangeDetector.detect(before, after, result, now)
        val second = CompanyChangeDetector.detect(before, after, result, now.plusSeconds(60))

        assertEquals(first.single().id, second.single().id)
    }
}
