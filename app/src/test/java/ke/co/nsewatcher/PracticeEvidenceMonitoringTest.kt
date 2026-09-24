package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PracticeEvidenceMonitoringTest {
    private val now = Instant.parse("2026-09-24T12:00:00Z")

    private fun filledOrder(
        id: String,
        symbol: String,
        filledAt: Instant,
        status: String = "FILLED"
    ) = PracticeOrder(
        id = id,
        symbol = symbol,
        side = "BUY",
        shares = 10,
        limit = 50.0,
        created = filledAt.minusSeconds(60).toEpochMilli(),
        status = status,
        filledAt = filledAt.toEpochMilli(),
        price = 49.5,
        quoteAt = filledAt.minusSeconds(30).toString()
    )

    @Test fun combinedEvidenceSetIncludesWatchlistAndRecentFilledPracticeCompanies() {
        val practice = PracticeState(
            enabled = true,
            orders = listOf(
                filledOrder("p1", " kcb ", now.minusSeconds(2 * 24 * 3600L)),
                filledOrder("p2", "SCOM", now.minusSeconds(3 * 24 * 3600L))
            )
        )

        val symbols = companyEvidenceMonitoringSymbols(
            watchedSymbols = setOf("EQTY", "kcb"),
            practiceState = practice,
            now = now
        )

        assertEquals(setOf("EQTY", "KCB", "SCOM"), symbols)
    }

    @Test fun pendingCancelledAndExpiredPracticeOrdersDoNotExtendMonitoring() {
        val practice = PracticeState(
            enabled = true,
            orders = listOf(
                filledOrder("recent", "KCB", now.minusSeconds(2 * 24 * 3600L)),
                filledOrder("pending", "SCOM", now.minusSeconds(2 * 24 * 3600L), status = "PENDING"),
                filledOrder("cancelled", "ABSA", now.minusSeconds(2 * 24 * 3600L), status = "CANCELLED"),
                filledOrder("expired", "EQTY", now.minusSeconds(31 * 24 * 3600L))
            )
        )

        val symbols = practiceEvidenceMonitoringSymbols(practice, now)

        assertEquals(setOf("KCB"), symbols)
    }

    @Test fun recentDecisionReviewRefreshesMonitoringWindowForAnOlderFill() {
        val oldFill = now.minusSeconds(60 * 24 * 3600L)
        val recentReview = now.minusSeconds(5 * 24 * 3600L)
        val order = filledOrder("old-order", "KCB", oldFill)
        val practice = PracticeState(
            enabled = true,
            orders = listOf(order),
            entries = listOf(
                PracticeEntry(
                    id = "review:old-order:1",
                    time = recentReview.toEpochMilli(),
                    kind = "REVIEW",
                    text = "Revisited the decision.",
                    symbol = "KCB"
                )
            )
        )

        assertTrue("KCB" in practiceEvidenceMonitoringSymbols(practice, now))
    }

    @Test fun unrelatedEntriesCannotRefreshAnExpiredPracticeDecision() {
        val oldFill = now.minusSeconds(60 * 24 * 3600L)
        val practice = PracticeState(
            enabled = true,
            orders = listOf(filledOrder("old-order", "KCB", oldFill)),
            entries = listOf(
                PracticeEntry(
                    id = "note:old-order",
                    time = now.minusSeconds(24 * 3600L).toEpochMilli(),
                    kind = "NOTE",
                    text = "General note.",
                    symbol = "KCB"
                ),
                PracticeEntry(
                    id = "review:different-order:1",
                    time = now.minusSeconds(24 * 3600L).toEpochMilli(),
                    kind = "REVIEW",
                    text = "Different decision.",
                    symbol = "KCB"
                )
            )
        )

        assertFalse("KCB" in practiceEvidenceMonitoringSymbols(practice, now))
    }

    @Test fun disabledPracticePortfolioDoesNotAddBackgroundEvidenceChecks() {
        val practice = PracticeState(
            enabled = false,
            orders = listOf(filledOrder("p1", "KCB", now.minusSeconds(24 * 3600L)))
        )

        assertTrue(practiceEvidenceMonitoringSymbols(practice, now).isEmpty())
        assertEquals(
            setOf("SCOM"),
            companyEvidenceMonitoringSymbols(setOf("SCOM"), practice, now)
        )
    }

    @Test fun futureFillTimestampIsIgnoredRatherThanCreatingAFalseMonitoringWindow() {
        val practice = PracticeState(
            enabled = true,
            orders = listOf(filledOrder("future", "KCB", now.plusSeconds(3600L)))
        )

        assertTrue(practiceEvidenceMonitoringSymbols(practice, now).isEmpty())
    }
}
