package ke.co.nsewatcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchExperienceTest {
    @Test fun freshInstallShowsOnboardingWhenItHasNotCompleted() {
        assertTrue(shouldShowFirstLaunchOnboarding(false, 1_000L, 1_000L))
    }

    @Test fun completedOnboardingNeverShowsAgain() {
        assertFalse(shouldShowFirstLaunchOnboarding(true, 1_000L, 1_000L))
    }

    @Test fun appUpgradeDoesNotForceExistingUsersThroughOnboarding() {
        assertFalse(shouldShowFirstLaunchOnboarding(false, 1_000L, 5_000L))
    }
}
