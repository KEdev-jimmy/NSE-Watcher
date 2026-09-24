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
    fun movementContext(stock: Stock, marketStocks: List<Stock>): CompanyMovementContext {
        if (!stock.changeAvailable || !stock.change.isFinite()) {
            return CompanyMovementContext(
                interpretation = "A reliable company daily change is unavailable, so peer context cannot be calculated.",
                limitation = "No peer comparison is inferred without a reliable company movement."
            )
        }
        val targetDate = CompanyChartAccuracy.observationDate(stock.observedAt)
            ?: return CompanyMovementContext(
                companyChange = stock.change,
                interpretation = "The company observation has no reliable session date, so peer context is unavailable.",
                limitation = "Only same-date market observations are compared."
            )

        val valid = marketStocks.filter { candidate ->
            candidate.symbol.isNotBlank() &&
                candidate.changeAvailable &&
                candidate.change.isFinite() &&
                CompanyChartAccuracy.observationDate(candidate.observedAt) == targetDate
        }
        val peers = valid.filterNot { it.symbol.equals(stock.symbol, ignoreCase = true) }
        val sectorKey = sectorKey(stock.sector)
        val sectorPeers = if (sectorKey.isBlank()) emptyList() else peers.filter { sectorKey(it.sector) == sectorKey }
        val sectorAverage = sectorPeers.map { it.change }.takeIf { it.isNotEmpty() }?.average()
        val marketAverage = peers.map { it.change }.takeIf { it.isNotEmpty() }?.average()

        val interpretation = when {
            stock.change == 0.0 -> "The company was unchanged in the available daily observation; peer averages provide context but not a cause."
            sectorAverage != null && sameDirection(stock.change, sectorAverage) ->
                "The company moved in the same direction as the available same-sector peers. Company news may be relevant, but a broader sector move is also plausible context."
            marketAverage != null && sameDirection(stock.change, marketAverage) ->
                "The company moved in the same direction as the broader available stock feed. Company-specific evidence should not be treated as the only possible explanation."
            sectorAverage != null || marketAverage != null ->
                "The company moved differently from the available peer average. Company-specific evidence may deserve closer review, but the cause is not established."
            else -> "There are not enough same-date peer observations to add broader movement context."
        }

        return CompanyMovementContext(
            companyChange = stock.change,
            observationDate = targetDate.toString(),
            sectorAverage = sectorAverage,
            sectorCount = sectorPeers.size,
            marketAverage = marketAverage,
            marketCount = peers.size,
            interpretation = interpretation,
            limitation = "Peer averages are calculated from available same-date stock observations. They are not official NSE market or sector indices."
        )
    }

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

    private fun sameDirection(first: Double, second: Double): Boolean =
        (first > 0.0 && second > 0.0) || (first < 0.0 && second < 0.0)

    private fun sectorKey(raw: String): String = when (
        raw.trim().lowercase(Locale.US).replace("&", "and")
    ) {
        "bank", "banks", "banking" -> "banking"
        "telecommunication", "telecommunications", "telecom" -> "telecom"
        "oil and gas", "energy" -> "energy"
        "insurance", "insurers" -> "insurance"
        else -> raw.trim().lowercase(Locale.US)
            .takeUnless { it.isBlank() || it == "other" }
            .orEmpty()
    }
}
