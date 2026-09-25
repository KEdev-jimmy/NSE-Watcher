package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticePresentationTest {
    private val savedAt = "2026-09-25T10:00:00Z"

    private fun state() = PracticeState(
        enabled = true,
        cash = 50_000.0,
        contributed = 100_000.0,
        holdings = listOf(PracticeHolding("KCB", 100, 50_000.0)),
        quotes = listOf(PracticeQuote("KCB", 52.0, savedAt, "KCB Group", "Banking"))
    )

    private fun stock(
        at: String = savedAt,
        price: Double = 52.0
    ) = Stock(
        symbol = "KCB",
        name = "KCB Group",
        price = price,
        change = 0.0,
        history = emptyList(),
        sector = "Banking",
        observedAt = at,
        changeAvailable = false
    )

    @Test
    fun savedValuationIsConfirmedWhenCurrentLoadedFeedHasSameObservation() {
        val s = state()

        assertTrue(
            PracticePortfolioPresentation.quoteConfirmedByCurrentFeed(
                "KCB",
                s,
                listOf(stock())
            )
        )
        assertFalse(
            PracticePortfolioPresentation.valuationFreshness(
                s,
                listOf(stock())
            ).provisional
        )
    }

    @Test
    fun newerLoadedQuoteMakesOlderSavedValuationProvisionalUntilStateCatchesUp() {
        val freshness = PracticePortfolioPresentation.valuationFreshness(
            state(),
            listOf(stock("2026-09-25T10:15:00Z", 53.0))
        )

        assertTrue(freshness.provisional)
        assertEquals(setOf("KCB"), freshness.unconfirmedSymbols)
    }

    @Test
    fun missingCurrentFeedDoesNotCertifyPersistedQuoteAsCurrent() {
        val freshness = PracticePortfolioPresentation.valuationFreshness(
            state(),
            emptyList()
        )

        assertTrue(freshness.provisional)
        assertEquals(setOf("KCB"), freshness.unconfirmedSymbols)
    }

    @Test
    fun holdingsWithoutSavedDatedQuoteAreProvisional() {
        val noSavedQuote = state().copy(quotes = emptyList())

        assertTrue(
            PracticePortfolioPresentation.valuationFreshness(
                noSavedQuote,
                listOf(stock())
            ).provisional
        )
    }

    @Test
    fun attentionSummaryCombinesPendingOrdersAndDecisionFollowUps() {
        val s = state().copy(
            orders = listOf(
                PracticeOrder("pending", "KCB", "BUY", 10, 50.0, 1L, status = "PENDING"),
                PracticeOrder("filled", "KCB", "BUY", 10, 50.0, 2L, status = "FILLED")
            )
        )
        val learning = PracticeLearningInsights(
            totalDecisions = 2,
            reasonsRecorded = 1,
            reviewedAtLeastOnce = 1,
            needsFirstReview = 1,
            newEvidenceAfterReview = 2
        )

        val summary = PracticePortfolioPresentation.attention(s, learning)

        assertEquals(1, summary.pendingOrders)
        assertEquals(1, summary.needsFirstReview)
        assertEquals(2, summary.newEvidence)
        assertEquals(4, summary.total)
    }
}
