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
