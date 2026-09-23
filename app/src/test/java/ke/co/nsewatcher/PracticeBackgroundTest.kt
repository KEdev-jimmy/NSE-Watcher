package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PracticeBackgroundTest {
    private val placed = Instant.parse("2026-09-22T07:00:00Z").toEpochMilli()
    private val now = Instant.parse("2026-09-22T07:20:00Z").toEpochMilli()
    private fun order(status: String = "PENDING") =
        PracticeOrder("a", "KCB", "BUY", 10, 100.0, placed, status = status)
    private fun state(enabled: Boolean = true, status: String = "PENDING") =
        PracticeState(enabled = enabled, cash = 100_000.0, contributed = 100_000.0, orders = listOf(order(status)))
    private fun quote(price: Double = 95.0, at: String = "2026-09-22T07:10:00Z") =
        Stock("KCB", "KCB Group", price, 1.0, emptyList(), observedAt = at)

    @Test fun backgroundWorkOnlyNeedsEnabledAccountsWithPendingOrders() {
        assertTrue(PracticeBackground.shouldCheck(state()))
        assertFalse(PracticeBackground.shouldCheck(state(enabled = false)))
        assertFalse(PracticeBackground.shouldCheck(state(status = "FILLED")))
        assertFalse(PracticeBackground.shouldCheck(PracticeState(enabled = true)))
    }

    @Test fun backgroundEvaluationUsesTheSameEligibleQuoteRulesAsForeground() {
        val filled = PracticeBackground.evaluate(state(), listOf(quote()), true, true, now)
        assertEquals("FILLED", filled.orders.single().status)
        assertEquals(95.0, filled.orders.single().price, 0.0)

        val closed = PracticeBackground.evaluate(state(), listOf(quote()), false, true, now)
        assertEquals("PENDING", closed.orders.single().status)
        assertEquals("Waiting for market to open", closed.orders.single().reason)

        val stale = PracticeBackground.evaluate(
            state(),
            listOf(quote(at = "2026-09-22T06:40:00Z")),
            true,
            true,
            now
        )
        assertEquals("PENDING", stale.orders.single().status)
    }
}
