package ke.co.nsewatcher

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
        assertEquals("Published company update", brief[0].title)
        assertEquals("Actual publisher", brief[0].source)
        assertEquals("Actual summary", brief[0].detail)
        assertEquals(story(), brief[0].story)
        assertEquals(event(), brief[1].alert)
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
