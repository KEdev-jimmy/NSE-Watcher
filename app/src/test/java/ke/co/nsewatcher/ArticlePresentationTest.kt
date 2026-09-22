package ke.co.nsewatcher

import org.junit.Assert.*
import org.junit.Test

class ArticlePresentationTest {
    private fun story(body: String = "", summary: String = "", symbol: String = "KCB", company: String = "KCB Group") =
        NewsItem("id", "An actual headline", summary, body, "Publisher", "2026-09-22", "News", symbol, company, "", "https://example.com/article", "", "", "")
    private fun stock(symbol: String = "KCB", name: String = "KCB Group") = Stock(symbol, name, 50.0, 1.0, emptyList())

    @Test fun blankAndDuplicateBodiesAreSummaryOnly() {
        assertFalse(ArticlePresentation.hasDistinctBody(story(summary = "Summary only.")))
        assertFalse(ArticlePresentation.hasDistinctBody(story(body = "<p>Same summary.</p>", summary = "Same summary.")))
        assertFalse(ArticlePresentation.hasDistinctBody(story(body = "An actual headline")))
        assertTrue(ArticlePresentation.hasDistinctBody(story(body = "Separate source text.", summary = "Summary.")))
    }

    @Test fun keyPointsAreVerbatimSummaryExcerptsNeverGeneratedClaims() {
        val first = "Revenue rose in the reporting period."
        val second = "The source describes higher costs."
        val item = story(body = "Longer actual article body.", summary = "$first $second")
        assertEquals(listOf(first, second), ArticlePresentation.keyPoints(item))
        assertTrue(ArticlePresentation.keyPoints(story(body = "Article with no summary")).isEmpty())
        assertTrue(ArticlePresentation.keyPoints(story(summary = first)).isEmpty())
    }

    @Test fun htmlIsReadableAndNeverRunsActiveContent() {
        val blocks = ArticlePresentation.blocks("<script>alert('bad')</script><style>body{}</style><h2>Source heading</h2><p>Revenue &amp; costs.</p><p>Second paragraph.</p>")
        assertEquals(listOf(ArticleBlock("Source heading", true), ArticleBlock("Revenue & costs."), ArticleBlock("Second paragraph.")), blocks)
    }

    @Test fun plainTextParagraphsAndNumericEntitiesArePreserved() {
        assertEquals(listOf(ArticleBlock("First paragraph."), ArticleBlock("Second paragraph.")), ArticlePresentation.blocks("First paragraph.\n\nSecond paragraph."))
        assertEquals("KES 50 — ‘text’", ArticlePresentation.text("KES 50 &#x2014; &#8216;text&#8217;"))
    }

    @Test fun companyResolutionRequiresExactUnambiguousIdentity() {
        assertEquals(stock(), ArticlePresentation.company(story(), listOf(stock())))
        assertNull(ArticlePresentation.company(story(symbol = "SCOM"), listOf(stock())))
        assertEquals(stock(), ArticlePresentation.company(story(symbol = "", company = "kcb group"), listOf(stock())))
        assertNull(ArticlePresentation.company(story(symbol = "", company = "KCB"), listOf(stock())))
        assertNull(ArticlePresentation.company(story(symbol = ""), listOf(stock(), stock("OTHER"))))
    }

    @Test fun failedOrPartialRefreshCannotReplaceAnArticleWithAnotherOne() {
        val original = story(body = "Stored article", summary = "Stored summary")
        assertEquals(original, ArticlePresentation.merge(original, story().copy(id = "other")))
        val merged = ArticlePresentation.merge(original, story().copy(url = "", imageUrl = "", source = "", title = "Updated title"))
        assertEquals(original.body, merged.body)
        assertEquals(original.summary, merged.summary)
        assertEquals(original.url, merged.url)
        assertEquals(original.source, merged.source)
        assertEquals("Updated title", merged.title)
    }

    @Test fun originalLinksRejectUnsafeSchemesAndCredentials() {
        for (url in listOf("javascript:alert(1)", "file:///secret", "https://user:pass@example.com/a", "not a URL")) assertNull(CompanyResearchPresentation.sourceUrl(url))
        assertEquals("https://example.com/article", CompanyResearchPresentation.sourceUrl("https://example.com/article"))
    }
}
