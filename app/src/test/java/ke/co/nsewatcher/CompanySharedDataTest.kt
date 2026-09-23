package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanySharedDataTest {
    private val stock = Stock("KCB", "KCB Group", 50.0, 0.0, emptyList())

    private fun story(
        id: String,
        symbol: String = "KCB",
        companyName: String = "",
        publishedAt: String
    ) = NewsItem(
        id = id,
        title = "Update $id",
        summary = "",
        body = "",
        source = "Issuer",
        publishedAt = publishedAt,
        category = "News",
        symbol = symbol,
        companyName = companyName,
        imageUrl = "",
        url = "https://example.com/$id",
        dividendAmount = "",
        exDate = "",
        paymentDate = ""
    )

    @Test fun companyNewsUsesTheSharedFeedAndExcludesOtherCompanies() {
        val rows = CompanySharedData.companyNews(
            stock,
            listOf(
                story("kcb", publishedAt = "2026-09-23T08:00:00Z"),
                story("scom", symbol = "SCOM", publishedAt = "2026-09-23T09:00:00Z")
            )
        )

        assertEquals(listOf("kcb"), rows.map { it.id })
    }

    @Test fun companyNewsCanLinkByCompanyNameWhenFeedSymbolIsMissing() {
        val rows = CompanySharedData.companyNews(
            stock,
            listOf(
                story(
                    id = "name-linked",
                    symbol = "",
                    companyName = "KCB Group",
                    publishedAt = "2026-09-23T08:00:00Z"
                )
            )
        )

        assertEquals(listOf("name-linked"), rows.map { it.id })
    }

    @Test fun companyNewsSortsUsingParsedPublicationTime() {
        val rows = CompanySharedData.companyNews(
            stock,
            listOf(
                story("older", publishedAt = "2026-09-23T09:30:00+03:00"),
                story("newer", publishedAt = "2026-09-23T07:00:00Z")
            )
        )

        assertEquals(listOf("newer", "older"), rows.map { it.id })
    }
}
