package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.CompanyIntelligenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyIntelligenceEngineTest {
    @Test
    fun buildExposesNormalizedCompanyAndNewsEvidence() {
        val source = CompanyIntelligenceCache.Result(
            profile = CompanyIntelligenceCache.Profile(
                revenue = "100",
                profit = "20",
                eps = "2.00",
                revenueGrowth = "5%",
                profitGrowth = "8%"
            ),
            evidence = listOf(
                CompanyIntelligenceCache.Evidence(
                    claim = "Revenue",
                    value = "100",
                    source = "StockAnalysis / S&P Global Market Intelligence",
                    endpoint = "https://example.com/financials",
                    symbol = "KCB",
                    fetchedAt = "2026-09-18T08:00:00Z"
                )
            )
        )
        val news = listOf(
            NewsItem(
                id = "article-1",
                title = "KCB annual results",
                summary = "Results published",
                body = "",
                source = "Issuer",
                publishedAt = "2026-09-18T07:00:00Z",
                category = "Company News",
                symbol = "KCB",
                companyName = "KCB Group",
                imageUrl = "",
                url = "https://example.com/results",
                dividendAmount = "",
                exDate = "",
                paymentDate = ""
            )
        )

        val result = CompanyIntelligenceEngine.build(
            stock = Stock("KCB", "KCB Group", 40.0, 1.0, listOf(39.0, 40.0)),
            source = source,
            priceHistory = listOf(39.0, 40.0),
            news = news
        )

        assertEquals(2, result.evidenceRecords.size)
        assertTrue(result.evidenceRecords.any { it.id.startsWith("company:kcb:") })
        assertTrue(result.evidenceRecords.any { it.id == "news:article-1" })
        assertEquals("https://example.com/financials", result.evidenceRecords.first { it.id.startsWith("company:kcb:") }.sourceUrl)
    }

    @Test
    fun evidenceRecordIdentityRemainsStableAcrossFetches() {
        val first = CompanyIntelligenceCache.Evidence(
            claim = "Profit",
            value = "20",
            source = "Provider",
            endpoint = "https://example.com/financials",
            symbol = "KCB",
            fetchedAt = "2026-09-17T08:00:00Z"
        )
        val second = first.copy(fetchedAt = "2026-09-18T08:00:00Z")

        val firstId = ke.co.nsewatcher.domain.EvidenceAdapters.fromCompanyEvidence(first)?.id
        val secondId = ke.co.nsewatcher.domain.EvidenceAdapters.fromCompanyEvidence(second)?.id

        assertEquals(firstId, secondId)
    }
}
