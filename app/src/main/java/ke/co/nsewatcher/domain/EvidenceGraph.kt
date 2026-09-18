package ke.co.nsewatcher.domain

/**
 * Shared evidence graph used by intelligence features.
 *
 * Records are the factual observations. Relationships describe explicit,
 * deterministic links between those observations. The graph never implies
 * causation by itself.
 */
data class EvidenceGraph(
    val records: List<EvidenceRecord>,
    val relationships: List<EvidenceRelationship>
) {
    private val recordIds: Set<String> = records.map { it.id }.toSet()

    fun record(id: String): EvidenceRecord? = records.firstOrNull { it.id == id }

    fun relationshipsFor(id: String): List<EvidenceRelationship> =
        relationships.filter { it.fromEvidenceId == id || it.toEvidenceId == id }

    fun relatedTo(id: String, type: EvidenceRelationshipType? = null): List<EvidenceRecord> {
        val ids = relationshipsFor(id)
            .filter { type == null || it.type == type }
            .flatMap { relationship ->
                listOf(relationship.fromEvidenceId, relationship.toEvidenceId)
            }
            .filter { it != id }
            .distinct()
        return ids.mapNotNull(::record)
    }

    /**
     * Returns structural problems without changing the supplied graph.
     * This is validation, not inference.
     */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        val duplicateIds = records.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }
            .keys
        duplicateIds.forEach { id -> errors += "Duplicate evidence id: " + id }

        relationships.forEach { relationship ->
            if (relationship.fromEvidenceId !in recordIds) {
                errors += "Relationship " + relationship.id + " references missing source " + relationship.fromEvidenceId
            }
            if (relationship.toEvidenceId !in recordIds) {
                errors += "Relationship " + relationship.id + " references missing target " + relationship.toEvidenceId
            }
            relationship.supportingEvidenceIds.forEach { id ->
                if (id !in recordIds) {
                    errors += "Relationship " + relationship.id + " references missing supporting evidence " + id
                }
            }
            if (relationship.fromEvidenceId == relationship.toEvidenceId) {
                errors += "Relationship " + relationship.id + " links evidence to itself"
            }
        }
        return errors.distinct()
    }

    companion object {
        fun of(
            records: List<EvidenceRecord>,
            relationships: List<EvidenceRelationship>
        ): EvidenceGraph {
            val uniqueRecords = records.distinctBy { it.id }
            val ids = uniqueRecords.map { it.id }.toSet()
            val uniqueRelationships = relationships
                .distinctBy { it.id }
                .filter {
                    it.fromEvidenceId in ids &&
                        it.toEvidenceId in ids &&
                        it.fromEvidenceId != it.toEvidenceId &&
                        it.supportingEvidenceIds.all { supportingId -> supportingId in ids }
                }
            return EvidenceGraph(uniqueRecords, uniqueRelationships)
        }
    }
}
