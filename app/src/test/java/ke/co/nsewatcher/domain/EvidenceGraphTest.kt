package ke.co.nsewatcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceGraphTest {
    private fun evidence(id: String) = EvidenceRecord(
        id = id,
        type = EvidenceType.MARKET_DATA,
        claim = id,
        source = "Test"
    )

    @Test
    fun graphRemovesDuplicateRecordsAndInvalidRelationships() {
        val graph = EvidenceGraph.of(
            records = listOf(evidence("a"), evidence("a"), evidence("b")),
            relationships = listOf(
                EvidenceRelationship("valid", "a", "b", EvidenceRelationshipType.RELATED),
                EvidenceRelationship("missing", "a", "missing-record", EvidenceRelationshipType.RELATED),
                EvidenceRelationship("self", "a", "a", EvidenceRelationshipType.RELATED)
            )
        )

        assertEquals(listOf("a", "b"), graph.records.map { it.id })
        assertEquals(listOf("valid"), graph.relationships.map { it.id })
        assertTrue(graph.validationErrors().isEmpty())
    }

    @Test
    fun relatedToReturnsOnlyExplicitlyRelatedEvidence() {
        val graph = EvidenceGraph.of(
            records = listOf(evidence("a"), evidence("b"), evidence("c")),
            relationships = listOf(
                EvidenceRelationship("ab", "a", "b", EvidenceRelationshipType.RELATED),
                EvidenceRelationship("ac", "a", "c", EvidenceRelationshipType.POSSIBLE)
            )
        )

        assertEquals(listOf("b"), graph.relatedTo("a", EvidenceRelationshipType.RELATED).map { it.id })
        assertEquals(listOf("c"), graph.relatedTo("a", EvidenceRelationshipType.POSSIBLE).map { it.id })
    }
}
