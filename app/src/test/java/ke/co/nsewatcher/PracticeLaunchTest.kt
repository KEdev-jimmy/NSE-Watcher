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
        assertFalse(PracticeLaunch.shouldOpenOrder(false, "KCB", false))
        assertTrue(PracticeLaunch.shouldOpenOrder(true, "KCB", false))
    }

    @Test fun handledResearchLaunchDoesNotReopenTheOrderTicket() {
        assertFalse(PracticeLaunch.shouldOpenOrder(true, "KCB", true))
        assertFalse(PracticeLaunch.shouldOpenOrder(true, "", false))
    }
}
