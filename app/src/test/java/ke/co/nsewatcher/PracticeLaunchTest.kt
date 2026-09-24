package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeLaunchTest {
    @Test fun researchLaunchNormalizesTheSelectedCompany() {
        assertEquals("KCB", PracticeLaunch.symbol(" kcb "))
    }

    @Test fun researchLaunchWaitsUntilPracticePortfolioIsEnabled() {
        assertFalse(PracticeLaunch.shouldOpenOrder(false, "KCB", launchRevision = 1, handledRevision = 0))
        assertTrue(PracticeLaunch.shouldOpenOrder(true, "KCB", launchRevision = 1, handledRevision = 0))
    }

    @Test fun handledResearchLaunchDoesNotReopenTheOrderTicket() {
        assertFalse(PracticeLaunch.shouldOpenOrder(true, "KCB", launchRevision = 1, handledRevision = 1))
        assertFalse(PracticeLaunch.shouldOpenOrder(true, "", launchRevision = 2, handledRevision = 1))
    }

    @Test fun homeFollowUpOpensExactDecisionReviewOnlyOncePerLaunchRevision() {
        assertTrue(PracticeLaunch.shouldOpenReview(true, "order-1", launchRevision = 4, handledRevision = 3))
        assertFalse(PracticeLaunch.shouldOpenReview(true, "order-1", launchRevision = 4, handledRevision = 4))
        assertFalse(PracticeLaunch.shouldOpenReview(true, "", launchRevision = 5, handledRevision = 4))
        assertFalse(PracticeLaunch.shouldOpenReview(false, "order-1", launchRevision = 5, handledRevision = 4))
    }

    @Test fun aNewLaunchRevisionReopensTheSameCompanyOrderTicket() {
        assertTrue(PracticeLaunch.shouldOpenOrder(true, "KCB", launchRevision = 2, handledRevision = 1))
        assertFalse(PracticeLaunch.shouldOpenOrder(true, "KCB", launchRevision = 2, handledRevision = 2))
        assertTrue(PracticeLaunch.shouldOpenOrder(true, "KCB", launchRevision = 3, handledRevision = 2))
    }
}
