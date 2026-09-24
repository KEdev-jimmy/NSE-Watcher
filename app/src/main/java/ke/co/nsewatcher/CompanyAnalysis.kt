package ke.co.nsewatcher

import ke.co.nsewatcher.data.AnalystCache
import java.util.Locale

internal data class CompanyMovementContext(
    val companyChange: Double? = null,
    val observationDate: String = "",
    val sectorAverage: Double? = null,
    val sectorCount: Int = 0,
    val marketAverage: Double? = null,
    val marketCount: Int = 0,
    val interpretation: String = "",
    val limitation: String = ""
)

internal data class VerifiedAnalystView(
    val analysis: AnalystCache.Analysis,
    val evidenceById: Map<String, AnalystCache.Evidence>,
    val model: String
)

internal object CompanyAnalysisPresentation {
    fun movementContext(stock: Stock, marketStocks: List<Stock>): CompanyMovementContext =
        MarketMovementContextPresentation.context(stock, marketStocks)

    fun movementRelationshipLabel(raw: String): String = when (raw.trim().lowercase(Locale.US)) {
        "related" -> "Close in time"
        "possible" -> "Possible context"
        "not-established" -> "Not established"
        else -> "Not established"
    }

    fun verifiedAnalyst(result: AnalystCache.Result): VerifiedAnalystView? {
        if (result.error.isNotBlank()) return null
        val analysis = result.analysis ?: return null
        val evidenceById = result.evidence
            .filter { it.id.isNotBlank() }
            .associateBy { it.id }
        if (evidenceById.isEmpty() || analysis.signals.isEmpty()) return null

        val validSignals = analysis.signals.all { signal ->
            signal.evidenceIds.isNotEmpty() &&
                signal.evidenceIds.all { it in evidenceById }
        }
        if (!validSignals) return null

        return VerifiedAnalystView(
            analysis = analysis,
            evidenceById = evidenceById,
            model = result.model
        )
    }

}
