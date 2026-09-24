package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyDataChangeEvent
import ke.co.nsewatcher.data.CompanyDataChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PracticeReviewTest {
    private val created = Instant.parse("2026-09-23T07:00:00Z").toEpochMilli()
    private val fillObserved = "2026-09-23T07:15:00Z"
    private val order = PracticeOrder(
        id = "order-1",
        symbol = "KCB",
        side = "BUY",
        shares = 100,
        limit = 50.0,
        created = created,
        note = "I expected the reported results to improve.",
        status = "FILLED",
        reason = "Filled from an eligible observed quote",
        filledAt = Instant.parse("2026-09-23T07:16:00Z").toEpochMilli(),
        price = 49.0,
        fee = 98.0,
        quoteAt = fillObserved
    )
    private val stock = Stock("KCB", "KCB Group", 51.0, 1.0, emptyList())

    private fun story(id: String, symbol: String, at: String) = NewsItem(
        id = id,
        title = "Company update $id",
        summary = "Published evidence",
        body = "",
        source = "Issuer",
        publishedAt = at,
        category = "News",
        symbol = symbol,
        companyName = "",
        imageUrl = "",
        url = "https://example.com/$id",
        dividendAmount = "",
        exDate = "",
        paymentDate = ""
    )

    @Test fun decisionSnapshotCapturesOnlyEvidenceAvailableAtConfirmation() {
        val capturedAt = Instant.parse("2026-09-23T07:00:00Z").toEpochMilli()
        val decisionStock = Stock(
            symbol = "KCB",
            name = "KCB Group",
            price = 50.5,
            change = 2.0,
            history = emptyList(),
            source = "Verified provider",
            observedAt = "2026-09-23T06:45:00Z",
            delayMinutes = 15,
            previousClose = 49.5
        )
        val news = listOf(
            story("before", "KCB", "2026-09-23T06:30:00Z"),
            story("future", "KCB", "2026-09-23T07:30:00Z"),
            story("other", "SCOM", "2026-09-23T06:40:00Z")
        )
        val events = listOf(
            CompanyDataChangeEvent(
                id = "company-data:kcb:before",
                symbol = "KCB",
                kind = CompanyDataChangeKind.REPORTED_FIGURES,
                title = "Reported figures updated for KCB",
                detail = "Provider observation changed.",
                source = "Verified provider",
                observedAt = "2026-09-23T06:50:00Z"
            ),
            CompanyDataChangeEvent(
                id = "company-data:kcb:future",
                symbol = "KCB",
                kind = CompanyDataChangeKind.DIVIDEND,
                title = "Later dividend update",
                detail = "This was not known yet.",
                source = "Verified provider",
                observedAt = "2026-09-23T07:10:00Z"
            )
        )

        val snapshot = PracticeReviewPresentation.captureDecision(
            stock = decisionStock,
            news = news,
            companyEvents = events,
            capturedAt = capturedAt
        )

        assertEquals(capturedAt, snapshot.capturedAt)
        assertEquals(50.5, snapshot.quotePrice, 0.0)
        assertEquals("2026-09-23T06:45:00Z", snapshot.quoteObservedAt)
        assertEquals("Verified provider", snapshot.quoteSource)
        assertEquals(15, snapshot.quoteDelayMinutes)
        assertEquals(2.0, snapshot.dailyChangePct ?: Double.NaN, 0.0)
        assertEquals(49.5, snapshot.previousClose ?: Double.NaN, 0.0)
        assertEquals(
            listOf("company-data:kcb:before", "news:before"),
            snapshot.evidence.map { it.id }
        )
    }

    @Test fun oldOrdersDefaultToAnEmptySnapshotInsteadOfReconstructingHistory() {
        val legacyOrder = PracticeOrder(
            id = "legacy",
            symbol = "KCB",
            side = "BUY",
            shares = 1,
            limit = 50.0,
            created = created
        )

        assertEquals(0L, legacyOrder.decisionSnapshot.capturedAt)
        assertTrue(legacyOrder.decisionSnapshot.evidence.isEmpty())
    }

    @Test fun priceReviewRequiresAQuoteObservedAfterTheFillQuote() {
        assertNull(
            PracticeReviewPresentation.price(
                order,
                PracticeQuote("KCB", 51.0, fillObserved)
            )
        )

        val review = PracticeReviewPresentation.price(
            order,
            PracticeQuote("KCB", 51.0, "2026-09-23T08:00:00Z")
        )

        assertEquals(51.0, review?.currentPrice ?: Double.NaN, 0.0)
        assertEquals((51.0 / 49.0 - 1.0) * 100.0, review?.changePct ?: Double.NaN, 0.0001)
    }

    @Test fun reviewEvidenceOnlyIncludesLaterItemsForTheDecisionCompany() {
        val news = listOf(
            story("before", "KCB", "2026-09-23T06:30:00Z"),
            story("after", "KCB", "2026-09-23T08:00:00Z"),
            story("other", "SCOM", "2026-09-23T09:00:00Z")
        )
        val companyEvents = listOf(
            CompanyDataChangeEvent(
                id = "company-data:kcb:1",
                symbol = "KCB",
                kind = CompanyDataChangeKind.REPORTED_FIGURES,
                title = "Reported figures updated for KCB",
                detail = "FY 2025 figures changed.",
                source = "Verified provider",
                observedAt = "2026-09-23T08:30:00Z"
            ),
            CompanyDataChangeEvent(
                id = "company-data:scom:1",
                symbol = "SCOM",
                kind = CompanyDataChangeKind.REPORTING_PERIOD,
                title = "Other company",
                detail = "",
                source = "Verified provider",
                observedAt = "2026-09-23T09:30:00Z"
            )
        )

        val evidence = PracticeReviewPresentation.evidence(order, stock, news, companyEvents)

        assertEquals(2, evidence.size)
        assertEquals("company-data:kcb:1", evidence[0].id)
        assertEquals("news:after", evidence[1].id)
    }

    @Test fun savedDecisionReviewsRemainLinkedToTheirOrder() {
        val state = PracticeState(
            enabled = true,
            entries = listOf(
                PracticeEntry("review:order-1:a", created + 1000, "REVIEW", "I changed my view.", symbol = "KCB"),
                PracticeEntry("review:order-2:b", created + 2000, "REVIEW", "Different order.", symbol = "KCB"),
                PracticeEntry("note", created + 3000, "NOTE", "General note.", symbol = "KCB")
            )
        )

        val reviews = PracticeReviewPresentation.savedReviews(state, order)

        assertEquals(1, reviews.size)
        assertTrue(reviews.single().text.contains("changed my view"))
    }
}
