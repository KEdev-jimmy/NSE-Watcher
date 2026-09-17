package ke.co.nsewatcher

import ke.co.nsewatcher.domain.EvidenceRecord
import ke.co.nsewatcher.domain.EvidenceRelationship
import ke.co.nsewatcher.domain.EvidenceRelationshipType
import ke.co.nsewatcher.domain.EvidenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceModelsTest {
    @Test
    fun evidenceRecordPreservesSourceAndTimeMetadata() {
        val evidence = EvidenceRecord(
            id = "news-1",
            symbol = "ABC",
            companyName = "ABC Holdings",
            type = EvidenceType.NEWS,
            claim = "Company announcement",
            source = "Issuer",
            sourceUrl = "https://example.com/news",
            publishedAt = "2026-09-17"
        )

        assertEquals("ABC", evidence.symbol)
        assertEquals("Issuer", evidence.source)
        assertEquals("https://example.com/news", evidence.sourceUrl)
        assertEquals("2026-09-17", evidence.publishedAt)
    }

    @Test
    fun relationshipUsesOnlyExplicitNonCausalStates() {
        val relationship = EvidenceRelationship(
            id = "rel-1",
            fromEvidenceId = "movement-1",
            toEvidenceId = "news-1",
            type = EvidenceRelationshipType.POSSIBLE,
            supportingEvidenceIds = listOf("news-1")
        )

        assertEquals(EvidenceRelationshipType.POSSIBLE, relationship.type)
        assertTrue(relationship.supportingEvidenceIds.contains("news-1"))
        assertEquals(3, EvidenceRelationshipType.entries.size)
    }
}
