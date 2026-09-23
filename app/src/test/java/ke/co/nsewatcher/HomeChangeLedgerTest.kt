package ke.co.nsewatcher

import ke.co.nsewatcher.data.HomeChangeLedger
import ke.co.nsewatcher.data.HomeChangeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HomeChangeLedgerTest {
    private val now = Instant.parse("2026-09-23T10:00:00Z")
    private fun change(id: String, symbol: String = "KCB") =
        HomeBriefItem(id, symbol, "Title", "Detail", "Why it may matter", "Uncertainty", "Source", now.toString(), "Review")

    @Test fun firstTrackingSeedsExistingItemsAsKnownInsteadOfCallingThemNew() {
        val state = HomeChangeLedger.reconcile(HomeChangeState(), setOf("KCB"), listOf(change("news:a")), now)
        assertTrue("news:a" in state.reviewedIds)
    }

    @Test fun laterArrivalsForAnAlreadyTrackedCompanyRemainUnreviewed() {
        val seeded = HomeChangeLedger.reconcile(HomeChangeState(), setOf("KCB"), listOf(change("news:a")), now)
        val later = HomeChangeLedger.reconcile(seeded, setOf("KCB"), listOf(change("news:a"), change("news:b")), now.plusSeconds(900))
        assertTrue("news:a" in later.reviewedIds)
        assertFalse("news:b" in later.reviewedIds)
    }

    @Test fun reviewedChangesStayReviewed() {
        val seeded = HomeChangeLedger.reconcile(HomeChangeState(), setOf("KCB"), emptyList(), now)
        val arrived = HomeChangeLedger.reconcile(seeded, setOf("KCB"), listOf(change("alert:1")), now.plusSeconds(900))
        val reviewed = HomeChangeLedger.markReviewed(arrived, setOf("alert:1"), now.plusSeconds(1200))
        assertTrue("alert:1" in reviewed.reviewedIds)
    }

    @Test fun unfollowingAndRefollowingCreatesANewBaseline() {
        val seeded = HomeChangeLedger.reconcile(HomeChangeState(), setOf("KCB"), emptyList(), now)
        val arrived = HomeChangeLedger.reconcile(seeded, setOf("KCB"), listOf(change("news:a")), now.plusSeconds(900))
        assertFalse("news:a" in arrived.reviewedIds)
        val removed = HomeChangeLedger.reconcile(arrived, emptySet(), emptyList(), now.plusSeconds(1200))
        val refollowed = HomeChangeLedger.reconcile(removed, setOf("KCB"), listOf(change("news:a")), now.plusSeconds(1500))
        assertTrue("news:a" in refollowed.reviewedIds)
    }
}
