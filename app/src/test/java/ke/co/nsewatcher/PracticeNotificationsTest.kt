package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyDataChangeEvent
import ke.co.nsewatcher.data.CompanyDataChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PracticeNotificationsTest {
    @Test fun fillNotificationDestinationCarriesExactOrderId() {
        val target = PracticeNotifications.destination(
            action = PracticeNotifications.practiceAction(),
            extraOrderId = "order-abc-123",
            pathOrderId = null
        )

        assertEquals("order-abc-123", target?.orderId)
    }

    @Test fun dataPathCanRecoverOrderIdWhenExtraIsMissing() {
        val target = PracticeNotifications.destination(
            action = PracticeNotifications.practiceAction(),
            extraOrderId = null,
            pathOrderId = "order:path_2"
        )

        assertEquals("order:path_2", target?.orderId)
    }

    @Test fun fillNotificationDeepLinkPreservesExactOrderId() {
        val orderId = "order:path_2"
        val deepLink = PracticeNotifications.deepLinkValue(orderId)

        assertEquals("nsewatcher://practice/order/order%3Apath_2", deepLink)
        assertEquals(
            orderId,
            PracticeNotifications.destination(
                action = PracticeNotifications.practiceAction(),
                extraOrderId = orderId,
                pathOrderId = null
            )?.orderId
        )
    }

    @Test fun invalidOrderIdProducesGenericPracticeDeepLink() {
        val deepLink = PracticeNotifications.deepLinkValue("../../bad order")

        assertEquals("nsewatcher://practice/order/", deepLink)
        assertEquals(
            "",
            PracticeNotifications.destination(
                action = PracticeNotifications.practiceAction(),
                extraOrderId = "../../bad order",
                pathOrderId = null
            )?.orderId
        )
    }

    @Test fun unrelatedIntentCannotOpenPractice() {
        assertNull(
            PracticeNotifications.destination(
                action = "ke.co.nsewatcher.UNRELATED",
                extraOrderId = "order-1",
                pathOrderId = "order-1"
            )
        )
    }

    @Test fun invalidOrderIdFallsBackToGenericPracticeForLegacySafety() {
        val target = PracticeNotifications.destination(
            action = PracticeNotifications.practiceAction(),
            extraOrderId = "../../bad order",
            pathOrderId = null
        )

        assertEquals("", target?.orderId)
    }

    @Test fun newPostDecisionEvidenceCreatesOneExactDecisionNotificationCandidate() {
        val now = Instant.parse("2026-09-24T12:00:00Z")
        val order = filledOrder("order-1", "KCB", now.minusSeconds(2 * 24 * 3600L))
        val state = PracticeState(
            enabled = true,
            orders = listOf(order),
            quotes = listOf(PracticeQuote("KCB", 52.0, now.toString(), name = "KCB Group"))
        )
        val news = listOf(
            story("older", "KCB", "2026-09-24T09:00:00Z"),
            story("latest", "KCB", "2026-09-24T11:30:00Z")
        )
        val companyEvents = listOf(
            CompanyDataChangeEvent(
                id = "company-data:kcb:1",
                symbol = "KCB",
                kind = CompanyDataChangeKind.REPORTED_FIGURES,
                title = "KCB reported figures changed",
                detail = "Provider figures changed.",
                source = "Verified provider",
                observedAt = "2026-09-24T10:30:00Z"
            )
        )

        val candidates = PracticeNotifications.evidenceCandidates(
            state = state,
            news = news,
            companyEvents = companyEvents,
            now = now,
            baselineAt = Instant.parse("2026-09-24T08:00:00Z"),
            notifiedKeys = emptySet()
        )

        assertEquals(1, candidates.size)
        val candidate = candidates.single()
        assertEquals("order-1", candidate.orderId)
        assertEquals("KCB", candidate.symbol)
        assertEquals("news:latest", candidate.evidenceId)
        assertTrue(candidate.coveredKeys.contains("order-1|news:older"))
        assertTrue(candidate.coveredKeys.contains("order-1|news:latest"))
        assertTrue(candidate.coveredKeys.contains("order-1|company-data:kcb:1"))
    }

    @Test fun alreadyNotifiedAndAlreadyReviewedEvidenceDoesNotNotifyAgain() {
        val now = Instant.parse("2026-09-24T12:00:00Z")
        val order = filledOrder("order-1", "KCB", now.minusSeconds(2 * 24 * 3600L))
        val reviewAt = Instant.parse("2026-09-24T10:45:00Z")
        val state = PracticeState(
            enabled = true,
            orders = listOf(order),
            quotes = listOf(PracticeQuote("KCB", 52.0, now.toString(), name = "KCB Group")),
            entries = listOf(
                PracticeEntry(
                    id = "review:order-1:1",
                    time = reviewAt.toEpochMilli(),
                    kind = "REVIEW",
                    text = "Reviewed the earlier evidence.",
                    symbol = "KCB"
                )
            )
        )
        val news = listOf(
            story("before-review", "KCB", "2026-09-24T10:30:00Z"),
            story("after-review", "KCB", "2026-09-24T11:30:00Z")
        )
        val already = setOf("order-1|news:after-review")

        val candidates = PracticeNotifications.evidenceCandidates(
            state = state,
            news = news,
            companyEvents = emptyList(),
            now = now,
            baselineAt = Instant.parse("2026-09-24T08:00:00Z"),
            notifiedKeys = already
        )

        assertTrue(candidates.isEmpty())
    }

    @Test fun activationBaselinePreventsRetrospectiveEvidenceNotification() {
        val now = Instant.parse("2026-09-24T12:00:00Z")
        val order = filledOrder("order-1", "KCB", now.minusSeconds(2 * 24 * 3600L))
        val state = PracticeState(
            enabled = true,
            orders = listOf(order),
            quotes = listOf(PracticeQuote("KCB", 52.0, now.toString(), name = "KCB Group"))
        )

        val candidates = PracticeNotifications.evidenceCandidates(
            state = state,
            news = listOf(story("old", "KCB", "2026-09-24T10:00:00Z")),
            companyEvents = emptyList(),
            now = now,
            baselineAt = Instant.parse("2026-09-24T11:00:00Z"),
            notifiedKeys = emptySet()
        )

        assertTrue(candidates.isEmpty())
    }

    @Test fun unrelatedCompanyEvidenceCannotTriggerPracticeDecisionNotification() {
        val now = Instant.parse("2026-09-24T12:00:00Z")
        val order = filledOrder("order-1", "KCB", now.minusSeconds(24 * 3600L))
        val state = PracticeState(
            enabled = true,
            orders = listOf(order),
            quotes = listOf(PracticeQuote("KCB", 52.0, now.toString(), name = "KCB Group"))
        )

        val candidates = PracticeNotifications.evidenceCandidates(
            state = state,
            news = listOf(story("other", "SCOM", "2026-09-24T11:30:00Z")),
            companyEvents = emptyList(),
            now = now,
            baselineAt = Instant.parse("2026-09-24T08:00:00Z"),
            notifiedKeys = emptySet()
        )

        assertTrue(candidates.isEmpty())
    }

    private fun filledOrder(id: String, symbol: String, filledAt: Instant) = PracticeOrder(
        id = id,
        symbol = symbol,
        side = "BUY",
        shares = 10,
        limit = 50.0,
        created = filledAt.minusSeconds(3600).toEpochMilli(),
        status = "FILLED",
        filledAt = filledAt.toEpochMilli(),
        price = 49.5,
        quoteAt = filledAt.toString()
    )

    private fun story(id: String, symbol: String, at: String) = NewsItem(
        id = id,
        title = "Company update " + id,
        summary = "Published evidence",
        body = "",
        source = "Issuer",
        publishedAt = at,
        category = "Company News",
        symbol = symbol,
        companyName = if (symbol == "KCB") "KCB Group" else "",
        imageUrl = "",
        url = "https://example.com/" + id,
        dividendAmount = "",
        exDate = "",
        paymentDate = ""
    )
}
