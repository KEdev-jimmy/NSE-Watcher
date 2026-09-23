package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileDefaultsTest {
    @Test fun freshInstallProfileIsNeutralAndContainsNoPersonalContactDetails() {
        assertEquals("Investor", ProfileDefaults.displayName)
        assertEquals("", ProfileDefaults.username)
        assertEquals("", ProfileDefaults.email)
        assertEquals("", ProfileDefaults.description)
        assertFalse(ProfileDefaults.displayName.contains("James", ignoreCase = true))
        assertFalse(ProfileDefaults.email.contains("@"))
    }
}
