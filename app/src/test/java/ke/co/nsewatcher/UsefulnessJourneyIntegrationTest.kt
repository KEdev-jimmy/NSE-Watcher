package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyDataChangeEvent
import ke.co.nsewatcher.data.CompanyDataChangeKind
import ke.co.nsewatcher.data.HomeChangeLedger
import ke.co.nsewatcher.data.HomeChangeState
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsefulnessJourneyIntegrationTest {
    private fun stock(
        price: Double,
        observedAt: String,
        change: Double = 0.0
    ) = Stock(
        symbol = "KCB",
        name = "KCB Group",
        price = price,
        change = change,
        history = emptyList(),
        source = "Verified provider",
        observedAt = observedAt,
        freshnessMode = "CURRENT_SESSION",
        previousClose = 49.0
    )

    private fun news(id: String, at: String) = NewsItem(
        id = id,
        title = "KCB published a company update",
        summary = "A later company update is available for review.",
        body = "",
        source = "Issuer",
        publishedAt = at,
        category = "Company News",
        symbol = "KCB",
        companyName = "KCB Group",
        imageUrl = "",
        url = "https://example.com/$id",
        dividendAmount = "",
        exDate = "",
        paymentDate = ""
    )

    @Test fun followedCompanyCanMoveThroughChangeResearchPracticeAndReviewWithoutRetrospectiveData() {
        val followedAt = Instant.parse("2026-09-23T07:00:00Z")
        val watched = listOf(stock(49.0, "2026-09-23T07:00:00Z"))

        // Following a company establishes a baseline; existing content is not called new.
        val baseline = HomeChangeLedger.reconcile(
            current = HomeChangeState(),
            watchedSymbols = setOf("KCB"),
            changes = emptyList(),
            now = followedAt
        )
        assertEquals(followedAt.toString(), baseline.baselineAtBySymbol["KCB"])

        // A genuine later threshold crossing becomes a recorded alert.
        val alertNow = Instant.parse("2026-09-23T07:12:00Z")
        val alert = PriceAlert(
            id = "rule-kcb-50",
            symbol = "KCB",
            type = AlertType.PRICE_ABOVE,
            threshold = 50.0,
            enabled = true
        )
        val alertState = AlertMonitorState(
            quotes = mapOf("KCB" to AlertQuote(49.0, "2026-09-23T07:05:00Z"))
        )
        val triggered = AlertEvaluator.evaluate(
            alerts = listOf(alert),
            stocks = listOf(stock(51.0, "2026-09-23T07:10:00Z", change = 4.08)),
            state = alertState,
            now = alertNow,
            marketOpen = true
        )
        assertEquals(1, triggered.size)

        // The same event becomes a Home change and is unreviewed because it occurred after follow.
        val alertEvent = triggered.single().event(alertNow)
        val homeChanges = HomePresentation.changes(
            watched = watched,
            news = emptyList(),
            events = listOf(alertEvent),
            now = alertNow
        )
        assertEquals(1, homeChanges.size)
        assertNotNull(homeChanges.single().stock)
        assertTrue(homeChanges.single().action.contains("Research"))

        val afterDetection = HomeChangeLedger.reconcile(
            current = baseline,
            watchedSymbols = setOf("KCB"),
            changes = homeChanges,
            now = alertNow
        )
        assertFalse(homeChanges.single().id in afterDetection.reviewedIds)

        val reviewed = HomeChangeLedger.markReviewed(
            afterDetection,
            setOf(homeChanges.single().id),
            alertNow.plusSeconds(30)
        )
        assertTrue(homeChanges.single().id in reviewed.reviewedIds)

        // Research can become a Practice decision, but an old/saved quote cannot fill it.
        val orderCreated = Instant.parse("2026-09-23T07:13:00Z")
        val pending = PracticeEngine.submit(
            PracticeState(enabled = true, cash = 100_000.0, contributed = 100_000.0),
            PracticeOrder(
                id = "practice-kcb-1",
                symbol = "KCB",
                side = "BUY",
                shares = 100,
                limit = 51.0,
                created = orderCreated.toEpochMilli(),
                note = "Testing whether the new evidence changes my view."
            )
        )

        val oldQuoteAttempt = PracticeOrderProcessor.process(
            initial = pending,
            quotes = listOf(stock(50.0, "2026-09-23T07:12:00Z")),
            marketOpen = true,
            marketKnown = true,
            now = Instant.parse("2026-09-23T07:14:00Z").toEpochMilli()
        )
        assertTrue(oldQuoteAttempt.filledOrders.isEmpty())
        assertEquals("PENDING", oldQuoteAttempt.state.orders.single().status)

        // A new eligible same-session quote observed after confirmation can fill it.
        val fillObservedAt = "2026-09-23T07:16:00Z"
        val filled = PracticeOrderProcessor.process(
            initial = oldQuoteAttempt.state,
            quotes = listOf(stock(50.0, fillObservedAt)),
            marketOpen = true,
            marketKnown = true,
            now = Instant.parse("2026-09-23T07:17:00Z").toEpochMilli()
        )
        assertEquals(1, filled.filledOrders.size)
        assertEquals("FILLED", filled.state.orders.single().status)
        assertEquals(fillObservedAt, filled.state.orders.single().quoteAt)

        // Later dated observations and later evidence are available for decision review.
        val filledOrder = filled.state.orders.single()
        val reviewPrice = PracticeReviewPresentation.price(
            filledOrder,
            PracticeQuote("KCB", 52.0, "2026-09-23T08:00:00Z")
        )
        assertNotNull(reviewPrice)
        assertTrue((reviewPrice?.changePct ?: 0.0) > 0.0)

        val evidence = PracticeReviewPresentation.evidence(
            order = filledOrder,
            stock = stock(52.0, "2026-09-23T08:00:00Z"),
            news = listOf(news("later-news", "2026-09-23T07:30:00Z")),
            companyEvents = listOf(
                CompanyDataChangeEvent(
                    id = "company-data:kcb:later",
                    symbol = "KCB",
                    kind = CompanyDataChangeKind.REPORTED_FIGURES,
                    title = "Reported figures updated for KCB",
                    detail = "A later provider observation differs from the prior one.",
                    source = "Verified provider",
                    observedAt = "2026-09-23T07:40:00Z"
                )
            )
        )
        assertEquals(2, evidence.size)
        assertTrue(evidence.all { CompanyResearchPresentation.timestamp(it.time)!!.isAfter(orderCreated) })
    }
}
