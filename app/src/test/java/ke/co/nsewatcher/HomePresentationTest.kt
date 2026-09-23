package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyDataChangeEvent
import ke.co.nsewatcher.data.CompanyDataChangeKind
import ke.co.nsewatcher.domain.AlertEvent
import ke.co.nsewatcher.domain.mergeAlertEvents
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HomePresentationTest {
    private val now = Instant.parse("2026-09-22T10:00:00Z")
    private fun quote(symbol: String = "KCB", at: String = "2026-09-22T09:45:00Z") =
        Stock(symbol, "KCB Group", 50.0, 2.0, emptyList(), observedAt = at, delayMinutes = 15)
    private fun story(symbol: String = "KCB", date: String = "2026-09-22T08:00:00Z") =
        NewsItem("news", "Published company update", "Actual summary", "", "Actual publisher", date, "News", symbol, "", "", "https://example.com/article", "", "", "")
    private fun event(id: String = "e", symbol: String = "KCB", at: String = "2026-09-22T09:00:00Z") =
        AlertEvent(id, "rule", symbol, "Price alert", "KCB crossed your price.", at, "2026-09-22T08:45:00Z")

    @Test fun briefUsesActualPublishedTextAndRecordedEvents() {
        val brief = HomePresentation.brief(listOf(quote()), listOf(story()), listOf(event()), now)
        assertEquals(2, brief.size)
        assertEquals(event(), brief[0].alert)
        assertEquals("Published company update", brief[1].title)
        assertEquals("Actual publisher", brief[1].source)
        assertEquals("Actual summary", brief[1].detail)
        assertEquals(story(), brief[1].story)
    }


    @Test fun reviewedChangesDisappearFromBriefButRemainInChangeHistory() {
        val all = HomePresentation.changes(listOf(quote()), listOf(story()), listOf(event()), now)
        val brief = HomePresentation.brief(listOf(quote()), listOf(story()), listOf(event()), now, setOf("news:news"))
        assertEquals(2, all.size)
        assertEquals(1, brief.size)
        assertEquals("alert:e", brief.single().id)
    }

    @Test fun newsAlertForTheSameArticleDoesNotDuplicateThePublishedStory() {
        val newsAlert = event(id = "news-alert").copy(articleId = "news")
        val changes = HomePresentation.changes(listOf(quote()), listOf(story()), listOf(newsAlert), now)
        assertEquals(1, changes.size)
        assertEquals("news:news", changes.single().id)
    }

    @Test fun briefExplainsWhyItMayMatterAndStatesUncertainty() {
        val relevantStory = story().copy(
            intelligenceRelevance = "market",
            intelligenceRelevanceReason = "Issuer announced a material operational update"
        )
        val newsChange = HomePresentation.changes(listOf(quote()), listOf(relevantStory), emptyList(), now).single()
        assertTrue(newsChange.whyItMayMatter.contains("Feed relevance note"))
        assertTrue(newsChange.whyItMayMatter.contains("material operational update"))
        assertTrue(newsChange.uncertainty.contains("does not prove"))
        assertEquals("Read evidence", newsChange.action)

        val alertChange = HomePresentation.changes(listOf(quote()), emptyList(), listOf(event()), now).single()
        assertTrue(alertChange.whyItMayMatter.contains("condition you configured"))
        assertTrue(alertChange.uncertainty.contains("does not establish why"))
        assertEquals("Research KCB", alertChange.action)
    }

    @Test fun structuredCompanyDataChangeAppearsInBriefAndOpensResearch() {
        val companyChange = CompanyDataChangeEvent(
            id = "company-data:kcb:reporting_period:1",
            symbol = "KCB",
            kind = CompanyDataChangeKind.REPORTING_PERIOD,
            title = "New reported period for KCB",
            detail = "The provider now reports FY 2026 for KCB.",
            source = "Verified provider",
            observedAt = "2026-09-22T09:30:00Z"
        )

        val changes = HomePresentation.changes(
            watched = listOf(quote()),
            news = emptyList(),
            events = emptyList(),
            now = now,
            companyDataEvents = listOf(companyChange)
        )

        assertEquals(1, changes.size)
        assertEquals(companyChange.id, changes.single().id)
        assertEquals("Research KCB", changes.single().action)
        assertEquals(quote(), changes.single().stock)
        assertTrue(changes.single().whyItMayMatter.contains("reporting period"))
        assertTrue(changes.single().uncertainty.contains("when NSE Watcher detected"))
    }

    @Test fun dividendDataChangeIsSuppressedWhenDividendArticleAlreadyRepresentsIt() {
        val dividendStory = story().copy(
            category = "Dividends",
            dividendAmount = "1.50",
            exDate = "2026-10-01"
        )
        val companyChange = CompanyDataChangeEvent(
            id = "company-data:kcb:dividend:1",
            symbol = "KCB",
            kind = CompanyDataChangeKind.DIVIDEND,
            title = "Reported dividend data updated for KCB",
            detail = "Dividend records differ.",
            source = "Verified provider",
            observedAt = "2026-09-22T09:30:00Z"
        )

        val changes = HomePresentation.changes(
            watched = listOf(quote()),
            news = listOf(dividendStory),
            events = emptyList(),
            now = now,
            companyDataEvents = listOf(companyChange)
        )

        assertEquals(1, changes.size)
        assertEquals("news:news", changes.single().id)
    }

    @Test fun CurrentPriceAloneNeverCreatesAnAlert() {
        assertTrue(HomePresentation.brief(listOf(quote().copy(price = 100.0)), emptyList(), emptyList(), now).isEmpty())
    }

    @Test fun briefExcludesUnfollowedOldUndatedAndFutureItems() {
        assertTrue(HomePresentation.brief(listOf(quote()), listOf(story("SCOM")), listOf(event(symbol = "SCOM")), now).isEmpty())
        for (date in listOf("2026-09-01", "unknown", "2026-09-23T09:00:00Z")) {
            assertTrue(HomePresentation.brief(listOf(quote()), listOf(story(date = date)), listOf(event(at = date)), now).isEmpty())
        }
    }

    @Test fun newsSortsParsedDatesAndKeepsOlderArticlesOutsideBrief() {
        val older = story(date = "2026-09-01T09:00:00Z").copy(id = "old")
        val fresh = story(date = "2026-09-22T09:00:00+03:00")
        assertEquals(listOf(fresh, older), HomePresentation.companyNews(listOf(older, fresh), listOf(quote())))
    }

    @Test fun attentionDigestSummarizesAffectedCompaniesAndDevelopmentTypes() {
        val companyChange = CompanyDataChangeEvent(
            id = "company-data:kcb:figures:1",
            symbol = "KCB",
            kind = CompanyDataChangeKind.REPORTED_FIGURES,
            title = "Reported figures updated for KCB",
            detail = "Provider observation changed.",
            source = "Verified provider",
            observedAt = "2026-09-22T09:30:00Z"
        )
        val changes = HomePresentation.changes(
            watched = listOf(quote("KCB"), quote("EQTY")),
            news = listOf(story("EQTY", "2026-09-22T09:10:00Z")),
            events = listOf(event(symbol = "KCB", at = "2026-09-22T09:20:00Z")),
            now = now,
            companyDataEvents = listOf(companyChange)
        )

        val digest = HomePresentation.attentionDigest(changes)

        assertNotNull(digest)
        assertEquals("3 new developments across 2 followed companies", digest?.summary)
        assertEquals(
            listOf("1 news update", "1 alert", "1 company-data update"),
            digest?.breakdown
        )
        assertNull(HomePresentation.attentionDigest(emptyList()))
    }

    @Test fun marketSummaryDoesNotTreatMissingDataAsBalance() {
        assertEquals("Daily market movement is unavailable.", HomePresentation.marketSummary(HomeMarketBreadth(0, 0, 0, 0)))
        assertEquals("Available daily changes are unchanged.", HomePresentation.marketSummary(HomeMarketBreadth(0, 0, 4, 0)))
        assertEquals("More shares are rising than falling.", HomePresentation.marketSummary(HomeMarketBreadth(3, 1, 0, 0)))
    }

    @Test fun freshnessSeparatesMissingDelayedOldAndPreviousSession() {
        assertEquals("Quotes unavailable", HomePresentation.freshness(emptyList(), now))
        assertEquals("Observation time unavailable", HomePresentation.freshness(listOf(quote(at = "")), now))
        assertEquals("Quotes delayed 15 min", HomePresentation.freshness(listOf(quote()), now))
        assertEquals("Latest observation is over 30 min old", HomePresentation.freshness(listOf(quote(at = "2026-09-22T08:00:00Z")), now))
        assertEquals("Previous-session observations", HomePresentation.freshness(listOf(quote(at = "2026-09-21T12:00:00Z")), now))
        assertEquals("Provider timestamp is ahead of device time", HomePresentation.freshness(listOf(quote(at = "2026-09-23T12:00:00Z")), now))
    }

    @Test fun greetingUsesNairobiTimeAndSavedName() {
        assertEquals("Good afternoon, James", HomePresentation.greeting(" James Waweru ", now))
        assertEquals("Good morning", HomePresentation.greeting("", Instant.parse("2026-09-22T03:00:00Z")))
    }

    @Test fun eventHistoryDeduplicatesRepeatedChecksAndPreservesDetectionTime() {
        val original = event()
        val repeated = event(at = "2026-09-22T09:15:00Z")
        assertEquals(listOf(original), mergeAlertEvents(listOf(original), listOf(repeated)))
    }

    @Test fun eventHistoryIsBoundedAndKeepsLatestFifty() {
        val events = (0..59).map { event(id = it.toString(), at = now.minusSeconds(it.toLong()).toString()) }
        val merged = mergeAlertEvents(emptyList(), events.reversed())
        assertEquals(50, merged.size)
        assertEquals("0", merged.first().id)
        assertEquals("49", merged.last().id)
    }
}
