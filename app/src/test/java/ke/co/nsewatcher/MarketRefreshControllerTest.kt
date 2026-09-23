package ke.co.nsewatcher

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarketRefreshControllerTest {
    private lateinit var original: MarketRefreshController.State

    @Before fun rememberState() {
        original = MarketRefreshController.state.value
        MarketRefreshController.state.value = MarketRefreshController.State()
    }

    @After fun restoreState() {
        MarketRefreshController.state.value = original
    }

    @Test fun emptyQuoteStateCanRefreshImmediately() {
        assertTrue(MarketRefreshController.shouldRefreshQuotes(hasQuotes = false, nowMs = 1_000L))
    }

    @Test fun existingQuotesWaitForTheSharedFifteenMinuteCadence() {
        MarketRefreshController.state.value = MarketRefreshController.State(lastSuccessfulRefreshMs = 1_000L)
        assertFalse(MarketRefreshController.shouldRefreshQuotes(hasQuotes = true, nowMs = 1_000L + MarketRefreshController.REFRESH_INTERVAL_MS - 1L))
        assertTrue(MarketRefreshController.shouldRefreshQuotes(hasQuotes = true, nowMs = 1_000L + MarketRefreshController.REFRESH_INTERVAL_MS))
    }

    @Test fun anInFlightRefreshSuppressesDuplicateRequestsEvenWithoutQuotes() {
        MarketRefreshController.state.value = MarketRefreshController.State(refreshInProgress = true)
        assertFalse(MarketRefreshController.shouldRefreshQuotes(hasQuotes = false, nowMs = 1_000L))
    }

    @Test fun quotesWithoutASuccessCheckpointAreEligibleForRefresh() {
        MarketRefreshController.state.value = MarketRefreshController.State(lastSuccessfulRefreshMs = null)
        assertTrue(MarketRefreshController.shouldRefreshQuotes(hasQuotes = true, nowMs = 1_000L))
    }
}
