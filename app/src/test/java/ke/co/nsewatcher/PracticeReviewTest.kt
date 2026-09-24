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

    @Test fun unreviewedFilledDecisionIsClassifiedAsNeedsReviewEvenWhenLaterEvidenceExists() {
        val later = story("later", "KCB", "2026-09-23T08:00:00Z")
        val state = PracticeState(
            enabled = true,
            orders = listOf(order),
            quotes = listOf(PracticeQuote("KCB", 51.0, "2026-09-23T08:30:00Z", name = "KCB Group"))
        )

        val items = PracticeDecisionCenterPresentation.items(
            state = state,
            companies = listOf(stock),
            news = listOf(later),
            companyEvents = emptyList()
        )

        assertEquals(1, items.size)
        assertEquals(PracticeDecisionReviewState.NEEDS_REVIEW, items.single().state)
        assertEquals(1, items.single().evidenceSinceReview)
        assertEquals("news:later", items.single().latestEvidence?.id)
    }

    @Test fun savedReviewWithoutLaterEvidenceIsClassifiedAsReviewed() {
        val reviewAt = Instant.parse("2026-09-23T09:00:00Z").toEpochMilli()
        val state = PracticeState(
            enabled = true,
            orders = listOf(order),
            entries = listOf(
                PracticeEntry(
                    id = "review:order-1:a",
                    time = reviewAt,
                    kind = "REVIEW",
                    text = "Reviewed the decision.",
                    symbol = "KCB"
                )
            )
        )

        val items = PracticeDecisionCenterPresentation.items(
            state = state,
            companies = listOf(stock),
            news = listOf(story("before-review", "KCB", "2026-09-23T08:00:00Z")),
            companyEvents = emptyList()
        )

        assertEquals(PracticeDecisionReviewState.REVIEWED, items.single().state)
        assertEquals(reviewAt, items.single().lastReviewAt)
        assertEquals(0, items.single().evidenceSinceReview)
    }

    @Test fun evidenceAfterLatestSavedReviewMovesDecisionToNewEvidence() {
        val reviewAt = Instant.parse("2026-09-23T08:15:00Z").toEpochMilli()
        val state = PracticeState(
            enabled = true,
            orders = listOf(order),
            entries = listOf(
                PracticeEntry(
                    id = "review:order-1:a",
                    time = reviewAt,
                    kind = "REVIEW",
                    text = "Reviewed the earlier evidence.",
                    symbol = "KCB"
                )
            )
        )

        val items = PracticeDecisionCenterPresentation.items(
            state = state,
            companies = listOf(stock),
            news = listOf(
                story("before-review", "KCB", "2026-09-23T08:00:00Z"),
                story("after-review", "KCB", "2026-09-23T08:30:00Z")
            ),
            companyEvents = emptyList()
        )

        assertEquals(PracticeDecisionReviewState.NEW_EVIDENCE, items.single().state)
        assertEquals(1, items.single().evidenceSinceReview)
        assertEquals("news:after-review", items.single().latestEvidence?.id)
    }

    @Test fun decisionCenterSummaryCountsOnlyFilledDecisions() {
        val reviewedOrder = order.copy(id = "order-2", symbol = "SCOM")
        val pendingOrder = order.copy(id = "order-3", status = "PENDING", filledAt = 0L, quoteAt = "")
        val state = PracticeState(
            enabled = true,
            orders = listOf(order, reviewedOrder, pendingOrder),
            entries = listOf(
                PracticeEntry(
                    id = "review:order-2:a",
                    time = Instant.parse("2026-09-23T09:00:00Z").toEpochMilli(),
                    kind = "REVIEW",
                    text = "Reviewed SCOM.",
                    symbol = "SCOM"
                )
            )
        )

        val items = PracticeDecisionCenterPresentation.items(
            state = state,
            companies = listOf(stock, stock.copy(symbol = "SCOM", name = "Safaricom")),
            news = emptyList(),
            companyEvents = emptyList()
        )
        val summary = PracticeDecisionCenterPresentation.summary(items)

        assertEquals(2, summary.total)
        assertEquals(1, summary.needsReview)
        assertEquals(0, summary.newEvidence)
        assertEquals(1, summary.reviewed)
    }

    @Test fun learningInsightsSummariseReasonsReviewsEvidenceAndRepeatedPatterns() {
        val second = order.copy(
            id = "order-2",
            symbol = "EQTY",
            note = "Profit growth and financial results are why I am testing this."
        )
        val third = order.copy(
            id = "order-3",
            symbol = "SCOM",
            note = ""
        )
        val items = listOf(
            PracticeDecisionReviewItem(
                order = order.copy(note = "I expect better earnings and profit results."),
                state = PracticeDecisionReviewState.NEEDS_REVIEW
            ),
            PracticeDecisionReviewItem(
                order = second,
                state = PracticeDecisionReviewState.NEW_EVIDENCE,
                lastReviewAt = created + 10_000,
                evidenceSinceReview = 1
            ),
            PracticeDecisionReviewItem(
                order = third,
                state = PracticeDecisionReviewState.REVIEWED,
                lastReviewAt = created + 20_000
            )
        )
        val state = PracticeState(enabled = true, orders = items.map { it.order })
        val companies = listOf(
            stock.copy(symbol = "KCB", name = "KCB Group", sector = "Banking"),
            stock.copy(symbol = "EQTY", name = "Equity Group", sector = "Banking"),
            stock.copy(symbol = "SCOM", name = "Safaricom", sector = "Telecommunications")
        )

        val insights = PracticeLearningInsightsPresentation.insights(
            state = state,
            companies = companies,
            items = items
        )

        assertEquals(3, insights.totalDecisions)
        assertEquals(2, insights.reasonsRecorded)
        assertEquals(2, insights.reviewedAtLeastOnce)
        assertEquals(1, insights.needsFirstReview)
        assertEquals(1, insights.newEvidenceAfterReview)
        assertEquals("Company results", insights.recurringThemes.single().label)
        assertEquals(2, insights.recurringThemes.single().count)
        assertEquals("Banking", insights.topSector)
        assertEquals(2, insights.topSectorCount)
    }

    @Test fun learningInsightsDoNotInventARecurringThemeFromOneDecision() {
        val item = PracticeDecisionReviewItem(
            order = order.copy(note = "I am testing the dividend announcement."),
            state = PracticeDecisionReviewState.NEEDS_REVIEW
        )
        val insights = PracticeLearningInsightsPresentation.insights(
            state = PracticeState(enabled = true, orders = listOf(item.order)),
            companies = listOf(stock.copy(sector = "Banking")),
            items = listOf(item)
        )

        assertEquals(1, insights.reasonsRecorded)
        assertTrue(insights.recurringThemes.isEmpty())
        assertEquals(null, insights.topSector)
        assertEquals(0, insights.topSectorCount)
    }

    @Test fun learningInsightsAreEmptyWhenPracticeIsDisabled() {
        val item = PracticeDecisionReviewItem(
            order = order,
            state = PracticeDecisionReviewState.NEEDS_REVIEW
        )
        val insights = PracticeLearningInsightsPresentation.insights(
            state = PracticeState(enabled = false, orders = listOf(order)),
            companies = listOf(stock.copy(sector = "Banking")),
            items = listOf(item)
        )

        assertEquals(0, insights.totalDecisions)
        assertEquals(0, insights.reasonsRecorded)
        assertEquals(0, insights.reviewedAtLeastOnce)
        assertTrue(insights.recurringThemes.isEmpty())
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
