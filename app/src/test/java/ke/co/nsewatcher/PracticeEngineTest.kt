package ke.co.nsewatcher

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PracticeEngineTest {
    private val placed = Instant.parse("2026-09-22T07:00:00Z").toEpochMilli()
    private val now = Instant.parse("2026-09-22T07:20:00Z").toEpochMilli()
    private fun account(cash: Double = 100000.0) = PracticeState(true, cash, cash)
    private fun order(id: String = "a", side: String = "BUY", shares: Long = 100, limit: Double = 100.0) = PracticeOrder(id, "KCB", side, shares, limit, placed)
    private fun quote(price: Double = 95.0, date: String = "2026-09-22T07:10:00Z") = Stock("KCB", "KCB Group", price, 1.0, emptyList(), observedAt = date)
    @Test fun pendingBuyReservesCashWithoutChangingWealth() {
        val s = PracticeEngine.submit(account(), order())
        assertEquals(10200.0, PracticeEngine.reserved(s), 0.001)
        assertEquals(89800.0, PracticeEngine.available(s), 0.001)
        assertEquals(100000.0, PracticeEngine.value(s), 0.001)
        assertTrue(s.holdings.isEmpty())
    }
    @Test fun reservationsPreventOverspendingAndCancellationReleasesCash() {
        val s = PracticeEngine.submit(account(15000.0), order())
        assertNotNull(PracticeEngine.validate(s, order("b")))
        assertEquals(15000.0, PracticeEngine.available(PracticeEngine.cancel(s, "a")), 0.001)
    }
    @Test fun editReplacesReservationAndCannotEditFilledOrder() {
        val s = PracticeEngine.submit(account(15000.0), order())
        val edited = PracticeEngine.submit(s, order(shares = 50))
        assertEquals(1, edited.orders.size)
        assertEquals(5100.0, PracticeEngine.reserved(edited), 0.001)
        val filled = PracticeEngine.evaluate(edited, listOf(quote()), true, true, now)
        assertThrows(IllegalArgumentException::class.java) { PracticeEngine.submit(filled, order(shares = 50)) }
    }
    @Test fun fillsAtObservedPriceIncludesFeeAndIsIdempotent() {
        val s = PracticeEngine.submit(account(), order())
        val filled = PracticeEngine.evaluate(s, listOf(quote()), true, true, now)
        assertEquals(90310.0, filled.cash, 0.001)
        assertEquals(9690.0, filled.holdings.single().cost, 0.001)
        assertEquals(95.0, filled.orders.single().price, 0.0)
        assertEquals(190.0, filled.orders.single().fee, 0.001)
        assertEquals(0.0, PracticeEngine.reserved(filled), 0.0)
        assertEquals(filled, PracticeEngine.evaluate(filled, listOf(quote()), true, true, now))
        val valued = PracticeEngine.observe(filled, listOf(quote()), now)
        assertEquals(-190.0, PracticeEngine.value(valued)-valued.contributed, 0.001)
    }
    @Test fun staleFuturePreorderUnknownClosedAndNonSessionQuotesNeverFill() {
        val s = PracticeEngine.submit(account(), order())
        for ((open, known) in listOf(false to true, true to false)) assertEquals("PENDING", PracticeEngine.evaluate(s, listOf(quote()), open, known, now).orders.single().status)
        for (at in listOf("", "2026-09-22", "2026-09-22T06:59:59Z", "2026-09-22T07:21:00Z", "2026-09-21T07:10:00Z")) assertNotNull(PracticeEngine.waiting(order(), quote(date = at), true, true, now))
        val noon = Instant.parse("2026-09-22T12:10:00Z").toEpochMilli()
        assertNotNull(PracticeEngine.waiting(order(), quote(date = "2026-09-22T12:05:00Z"), true, true, noon))
        val staleNow = now + 31 * 60000
        assertNotNull(PracticeEngine.waiting(order(), quote(), true, true, staleNow))
    }
    @Test fun buyAndSellLimitsAreDirectional() {
        assertNotNull(PracticeEngine.waiting(order(), quote(101.0), true, true, now))
        assertNotNull(PracticeEngine.waiting(order(side = "SELL"), quote(99.0), true, true, now))
        assertNull(PracticeEngine.waiting(order(side = "SELL"), quote(101.0), true, true, now))
    }
    @Test fun pendingSalesReserveSharesAndPartialSaleAllocatesCost() {
        val initial = account().copy(holdings = listOf(PracticeHolding("KCB", 100, 9000.0)))
        val pending = PracticeEngine.submit(initial, order(side = "SELL", shares = 40))
        assertNotNull(PracticeEngine.validate(pending, order("b", "SELL", 70)))
        val sold = PracticeEngine.evaluate(pending, listOf(quote(110.0)), true, true, now)
        assertEquals(60L, sold.holdings.single().shares)
        assertEquals(5400.0, sold.holdings.single().cost, 0.001)
        assertEquals(712.0, sold.orders.single().realised, 0.001)
        assertEquals(104312.0, sold.cash, 0.001)
    }
    @Test fun lastKnownQuotesSurviveMissingInvalidAndOlderFeeds() {
        val s = account().copy(holdings = listOf(PracticeHolding("KCB", 100, 9000.0)))
        val current = PracticeEngine.observe(s, listOf(quote()), now)
        val missing = PracticeEngine.observe(current, emptyList(), now)
        assertEquals(current, missing)
        assertEquals(current, PracticeEngine.observe(current, listOf(quote(Double.NaN)), now))
        assertEquals(current, PracticeEngine.observe(current, listOf(quote(80.0, "2026-09-21T07:10:00Z")), now))
        assertEquals(9500.0, PracticeEngine.positions(missing).single().value, 0.001)
        assertTrue(PracticeEngine.positions(s).single().estimated)
        assertTrue(PracticeEngine.snapshot(s, now).snapshots.isEmpty())
    }
    @Test fun snapshotsKeepContributionsSeparateAndAvoidUnchangedDuplicates() {
        val s = PracticeEngine.snapshot(account(), now)
        assertEquals(s, PracticeEngine.snapshot(s, now + 1000))
        val added = PracticeEngine.snapshot(s.copy(cash = 110000.0, contributed = 110000.0), now + 2000)
        assertEquals(0.0, added.snapshots.last().value - added.snapshots.last().contributed, 0.0)
    }
    @Test fun invalidInputsCannotEnterTheLedger() {
        for (p in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 100.001)) assertNotNull(PracticeEngine.validate(account(), order(limit = p)))
        for (n in listOf(0L, -1L, Long.MAX_VALUE)) assertNotNull(PracticeEngine.validate(account(), order(shares = n)))
        assertNotNull(PracticeEngine.validate(account().copy(enabled = false), order()))
    }
}
