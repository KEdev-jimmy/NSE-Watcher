package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertEvent
import org.junit.Assert.*
import org.junit.Test

class AlertDestinationTest {
    @Test fun newsDestinationRetainsArticleIdentityDateAndOriginalSource() {
        val event = AlertEvent("event", "rule", "SCOM", "News", "message", "detected", "published",
            "article-42", "Results announced", "Publisher", "https://example.com/results")
        val target = AlertDestination.from(event)
        val article = target.article()!!
        assertEquals("article-42", article.id)
        assertEquals("Results announced", article.title)
        assertEquals("published", article.publishedAt)
        assertEquals("https://example.com/results", article.url)
        assertEquals("SCOM", article.symbol)
        assertTrue(article.body.isEmpty())
    }
    @Test fun priceAndLegacyEventsOpenTheMatchingCompany() {
        val target = AlertDestination.from(AlertEvent("event", "rule", "KCB", "Price", "message", "detected", "observed"))
        val quote = Stock("KCB", "KCB Group", 50.0, 2.0, emptyList())
        assertNull(target.article())
        assertEquals(quote, target.company(listOf(quote)))
    }
    @Test fun companyDestinationStillWorksWhenQuotesAndCatalogAreUnavailable() {
        val fallback = AlertDestination("event", "KCB").company(emptyList())
        assertEquals("KCB", fallback.symbol)
        assertTrue(fallback.price.isNaN())
        assertFalse(fallback.changeAvailable)
        assertFalse(fallback.volumeAvailable)
    }
}
