package ke.co.nsewatcher.domain

import ke.co.nsewatcher.NewsItem
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.MovementIntelligenceCache

object EvidenceAdapters {
    fun fromNews(item: NewsItem): EvidenceRecord? {
        if (item.id.isBlank() || item.title.isBlank()) return null
        return EvidenceRecord(
            id = "news:${item.id}",
            symbol = item.symbol.takeIf { it.isNotBlank() },
            companyName = item.companyName.takeIf { it.isNotBlank() },
            type = when {
                item.category.equals("Dividends", ignoreCase = true) -> EvidenceType.DIVIDEND
                item.category.equals("Corporate Actions", ignoreCase = true) -> EvidenceType.CORPORATE_ACTION
                else -> EvidenceType.NEWS
            },
            claim = item.title,
            value = item.summary.takeIf { it.isNotBlank() },
            source = item.source,
            sourceUrl = item.url.takeIf { it.isNotBlank() },
            publishedAt = item.publishedAt.takeIf { it.isNotBlank() }
        )
    }

    fun fromCompanyEvidence(evidence: CompanyIntelligenceCache.Evidence): EvidenceRecord? {
        if (evidence.claim.isBlank() || evidence.symbol.isBlank()) return null
        return EvidenceRecord(
            id = companyEvidenceId(evidence),
            symbol = evidence.symbol.takeIf { it.isNotBlank() },
            type = evidenceTypeForCompanyClaim(evidence.claim),
            claim = evidence.claim,
            value = evidence.value.takeIf { it.isNotBlank() },
            source = evidence.source,
            sourceUrl = evidence.endpoint.takeIf { it.startsWith("http://") || it.startsWith("https://") },
            observedAt = evidence.fetchedAt.takeIf { it.isNotBlank() }
        )
    }

    fun fromMovementEvidence(symbol: String, evidence: MovementIntelligenceCache.Evidence): EvidenceRecord? {
        if (symbol.isBlank() || evidence.title.isBlank()) return null
        val eventType = evidence.eventType.lowercase()
        return EvidenceRecord(
            id = movementEvidenceId(symbol, evidence),
            symbol = symbol,
            type = when {
                eventType.contains("dividend") -> EvidenceType.DIVIDEND
                eventType.contains("corporate") || eventType.contains("action") -> EvidenceType.CORPORATE_ACTION
                eventType.contains("movement") || eventType.contains("price") -> EvidenceType.PRICE_MOVEMENT
                else -> EvidenceType.NEWS
            },
            claim = evidence.title,
            value = evidence.description.takeIf { it.isNotBlank() },
            source = evidence.source,
            sourceUrl = evidence.sourceUrl.takeIf { it.isNotBlank() },
            publishedAt = evidence.date.takeIf { it.isNotBlank() }
        )
    }

    fun relationshipFromMovement(
        movementEvidenceId: String,
        targetEvidenceId: String,
        evidence: MovementIntelligenceCache.Evidence
    ): EvidenceRelationship? {
        if (movementEvidenceId.isBlank() || targetEvidenceId.isBlank() || evidence.title.isBlank()) return null
        val type = when (evidence.relationship.lowercase().trim()) {
            "related" -> EvidenceRelationshipType.RELATED
            "possible" -> EvidenceRelationshipType.POSSIBLE
            "not-established", "not established", "not_established" -> EvidenceRelationshipType.NOT_ESTABLISHED
            else -> return null
        }
        return EvidenceRelationship(
            id = "relationship:$movementEvidenceId:$targetEvidenceId",
            fromEvidenceId = movementEvidenceId,
            toEvidenceId = targetEvidenceId,
            type = type
        )
    }

    private fun evidenceTypeForCompanyClaim(claim: String): EvidenceType {
        val normalized = claim.lowercase()
        return when {
            normalized.contains("dividend") -> EvidenceType.DIVIDEND
            normalized.contains("revenue") || normalized.contains("profit") || normalized.contains("eps") ||
                normalized.contains("roe") || normalized.contains("margin") || normalized.contains("financial") ->
                EvidenceType.FINANCIAL_RESULT
            else -> EvidenceType.MARKET_DATA
        }
    }

    private fun companyEvidenceId(evidence: CompanyIntelligenceCache.Evidence): String =
        "company:${evidence.symbol}:${evidence.source}:${evidence.endpoint}:${evidence.claim}:${evidence.value}"
            .lowercase()
            .replace(Regex("[^a-z0-9:.%+/_-]+"), "-")

    private fun movementEvidenceId(symbol: String, evidence: MovementIntelligenceCache.Evidence): String =
        "movement:$symbol:${evidence.eventType}:${evidence.date}:${evidence.title}"
            .lowercase()
            .replace(Regex("[^a-z0-9:.%+/_-]+"), "-")
}
