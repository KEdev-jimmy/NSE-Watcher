package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.MovementIntelligenceCache
import ke.co.nsewatcher.domain.EvidenceAdapters
import ke.co.nsewatcher.domain.EvidenceRelationshipType
import ke.co.nsewatcher.domain.EvidenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceAdaptersTest {
    @Test
    fun newsAdapterPreservesStableIdAndSourceMetadata() {
        val news = NewsItem(
            id = "article-123",
            title = "KCB announces annual results",
            summary = "Annual results announcement",
            body = "",
            source = "Issuer",
            publishedAt = "2026-09-17T08:00:00Z",
            category = "Company News",
            symbol = "KCB",
            companyName = "KCB Group",
            imageUrl = "",
            url = "https://example.com/article-123",
            dividendAmount = "",
            exDate = "",
            paymentDate = ""
        )

        val evidence = EvidenceAdapters.fromNews(news)

        assertNotNull(evidence)
        assertEquals("news:article-123", evidence?.id)
        assertEquals(EvidenceType.NEWS, evidence?.type)
        assertEquals("Issuer", evidence?.source)
        assertEquals("https://example.com/article-123", evidence?.sourceUrl)
        assertEquals("2026-09-17T08:00:00Z", evidence?.publishedAt)
    }

    @Test
    fun companyEvidenceIdDoesNotChangeWhenFetchTimestampChanges() {
        val first = CompanyIntelligenceCache.Evidence(
            claim = "Revenue",
            value = "100",
            source = "Provider",
            endpoint = "/api/company",
            symbol = "KCB",
            fetchedAt = "2026-09-17T08:00:00Z"
        )
        val second = first.copy(fetchedAt = "2026-09-18T08:00:00Z")

        assertEquals(
            EvidenceAdapters.fromCompanyEvidence(first)?.id,
            EvidenceAdapters.fromCompanyEvidence(second)?.id
        )
    }

    @Test
    fun dividendNewsIsTypedAsDividendEvidence() {
        val news = NewsItem(
            id = "div-1", title = "Dividend declared", summary = "KSh 2.00 per share", body = "",
            source = "Issuer", publishedAt = "2026-09-17", category = "Dividends",
            symbol = "SCOM", companyName = "Safaricom", imageUrl = "", url = "",
            dividendAmount = "2.00", exDate = "", paymentDate = ""
        )

        assertEquals(EvidenceType.DIVIDEND, EvidenceAdapters.fromNews(news)?.type)
    }

    @Test
    fun invalidNewsIsNotConverted() {
        val news = NewsItem(
            id = "", title = "", summary = "", body = "", source = "", publishedAt = "",
            category = "", symbol = "", companyName = "", imageUrl = "", url = "",
            dividendAmount = "", exDate = "", paymentDate = ""
        )

        assertNull(EvidenceAdapters.fromNews(news))
    }

    @Test
    fun movementRelationshipPreservesExistingNonCausalClassification() {
        val movementEvidence = MovementIntelligenceCache.Evidence(
            eventType = "news",
            title = "KCB annual results",
            date = "2026-09-17",
            source = "Issuer",
            sourceUrl = "https://example.com/results",
            description = "Annual results",
            relationship = "related",
            daysFromMove = 0
        )

        val relationship = EvidenceAdapters.relationshipFromMovement(
            movementEvidenceId = "movement:kcb:1d",
            targetEvidenceId = "news:article-123",
            evidence = movementEvidence
        )

        assertNotNull(relationship)
        assertEquals(EvidenceRelationshipType.RELATED, relationship?.type)
        assertEquals("movement:kcb:1d", relationship?.fromEvidenceId)
        assertEquals("news:article-123", relationship?.toEvidenceId)
    }
    }
}
