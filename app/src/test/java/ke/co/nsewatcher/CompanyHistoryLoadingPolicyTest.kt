package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyHistoryLoadingPolicyTest {
    @Test
    fun initialLoadContainsOnlyOverviewNeedsAndDefaultRange() {
        assertEquals(
            setOf("1D", "1M", "3M", "1Y", "3Y"),
            CompanyHistoryLoadingPolicy.initial("1D")
        )
        assertEquals(
            setOf("1D", "1M", "3M", "1Y", "3Y", "6M"),
            CompanyHistoryLoadingPolicy.initial("6M")
        )
        assertFalse("5Y" in CompanyHistoryLoadingPolicy.initial("1D"))
        assertFalse("1W" in CompanyHistoryLoadingPolicy.initial("1D"))
    }

    @Test
    fun tappingAValidRangeAddsOnlyThatRange() {
        val initial = CompanyHistoryLoadingPolicy.initial("1D")
        val requested = CompanyHistoryLoadingPolicy.request(initial, "5Y")
        assertTrue("5Y" in requested)
        assertEquals(initial.size + 1, requested.size)
    }

    @Test
    fun invalidRangeDoesNotExpandRequests() {
        val initial = CompanyHistoryLoadingPolicy.initial("1D")
        assertEquals(initial, CompanyHistoryLoadingPolicy.request(initial, "UNKNOWN"))
    }
}
