package ke.co.nsewatcher.domain

/**
 * Neutral evidence contract shared by intelligence features.
 *
 * This layer describes evidence and relationships without replacing the
 * provider-specific cache models. It deliberately does not assert causation.
 */
enum class EvidenceType {
    MARKET_DATA,
    FINANCIAL_RESULT,
    DIVIDEND,
    CORPORATE_ACTION,
    NEWS,
    PRICE_MOVEMENT
}

enum class EvidenceRelationshipType {
    RELATED,
    POSSIBLE,
    NOT_ESTABLISHED
}

data class EvidenceRecord(
    val id: String,
    val symbol: String? = null,
    val companyName: String? = null,
    val type: EvidenceType,
    val claim: String,
    val value: String? = null,
    val source: String,
    val sourceUrl: String? = null,
    val publishedAt: String? = null,
    val observedAt: String? = null,
    val period: String? = null
)

data class EvidenceRelationship(
    val id: String,
    val fromEvidenceId: String,
    val toEvidenceId: String,
    val type: EvidenceRelationshipType,
    val supportingEvidenceIds: List<String> = emptyList()
)
