package ke.co.nsewatcher

import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

internal data class MarketAttentionReason(
    val title: String,
    val detail: String
)

internal data class MarketAttentionItem(
    val stock: Stock,
    val score: Int,
    val context: CompanyMovementContext,
    val reasons: List<MarketAttentionReason>,
    val latestEvidence: NewsItem? = null
)

internal object MarketMovementContextPresentation {
    fun context(stock: Stock, marketStocks: List<Stock>): CompanyMovementContext {
        if (!MarketPresentation.validChange(stock)) {
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
                MarketPresentation.validChange(candidate) &&
                CompanyChartAccuracy.observationDate(candidate.observedAt) == targetDate
        }
        val peers = valid.filterNot { it.symbol.equals(stock.symbol, ignoreCase = true) }
        val sectorKey = sectorKey(stock.sector)
        val sectorPeers = if (sectorKey.isBlank()) {
            emptyList()
        } else {
            peers.filter { sectorKey(it.sector) == sectorKey }
        }
        val sectorAverage = sectorPeers.map { it.change }.takeIf { it.isNotEmpty() }?.average()
        val marketAverage = peers.map { it.change }.takeIf { it.isNotEmpty() }?.average()

        val interpretation = when {
            stock.change == 0.0 ->
                "The company was unchanged in the available daily observation; peer averages provide context but not a cause."
            sectorAverage != null && sameDirection(stock.change, sectorAverage) ->
                "The company moved in the same direction as the available same-sector peers. Company evidence may be relevant, but a broader sector move is also plausible context."
            marketAverage != null && sameDirection(stock.change, marketAverage) ->
                "The company moved in the same direction as the broader available stock feed. Company-specific evidence should not be treated as the only possible explanation."
            sectorAverage != null || marketAverage != null ->
                "The company moved differently from the available peer average. Company-specific evidence may deserve closer review, but the cause is not established."
            else ->
                "There are not enough same-date peer observations to add broader movement context."
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

    internal fun sameDirection(first: Double, second: Double): Boolean =
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

internal object MarketAttentionEngine {
    private const val MIN_SCORE = 2

    fun rank(
        stocks: List<Stock>,
        news: List<NewsItem>,
        now: Instant,
        limit: Int = 3
    ): List<MarketAttentionItem> {
        if (limit <= 0) return emptyList()
        val valid = stocks.filter(MarketPresentation::validChange)
        val latestDate = valid.mapNotNull {
            CompanyChartAccuracy.observationDate(it.observedAt)
        }.maxOrNull() ?: return emptyList()

        return valid.asSequence()
            .filter { CompanyChartAccuracy.observationDate(it.observedAt) == latestDate }
            .mapNotNull { stock -> attentionItem(stock, stocks, news, now) }
            .filter { it.score >= MIN_SCORE }
            .sortedWith(
                compareByDescending<MarketAttentionItem> { it.score }
                    .thenByDescending { abs(it.stock.change) }
                    .thenBy { it.stock.symbol }
            )
            .take(limit)
            .toList()
    }

    private fun attentionItem(
        stock: Stock,
        stocks: List<Stock>,
        news: List<NewsItem>,
        now: Instant
    ): MarketAttentionItem? {
        val context = MarketMovementContextPresentation.context(stock, stocks)
        var score = 0
        val reasons = mutableListOf<MarketAttentionReason>()
        val magnitude = abs(stock.change)

        when {
            magnitude >= 4.0 -> {
                score += 3
                reasons += MarketAttentionReason(
                    "Large daily move",
                    "${CompanyResearchPresentation.percent(stock.change)} in the latest available observation."
                )
            }
            magnitude >= 2.5 -> {
                score += 2
                reasons += MarketAttentionReason(
                    "Notable daily move",
                    "${CompanyResearchPresentation.percent(stock.change)} in the latest available observation."
                )
            }
            magnitude >= 1.5 -> {
                score += 1
                reasons += MarketAttentionReason(
                    "Daily move",
                    "${CompanyResearchPresentation.percent(stock.change)} in the latest available observation."
                )
            }
        }

        val sectorAverage = context.sectorAverage
        if (sectorAverage != null && context.sectorCount >= 2) {
            val difference = abs(stock.change - sectorAverage)
            val opposite = stock.change != 0.0 && sectorAverage != 0.0 &&
                !MarketMovementContextPresentation.sameDirection(stock.change, sectorAverage)
            when {
                opposite && magnitude >= 1.5 -> {
                    score += 3
                    reasons += MarketAttentionReason(
                        "Moving against sector peers",
                        "Available same-sector peers averaged ${CompanyResearchPresentation.percent(sectorAverage)} across ${context.sectorCount} observations."
                    )
                }
                difference >= 2.0 -> {
                    score += 2
                    reasons += MarketAttentionReason(
                        "Diverging from sector peers",
                        "The move differs from the available same-sector average by ${String.format(Locale.US, "%.1f", difference)} percentage points."
                    )
                }
                abs(sectorAverage) >= 2.0 &&
                    MarketMovementContextPresentation.sameDirection(stock.change, sectorAverage) -> {
                    score += 1
                    reasons += MarketAttentionReason(
                        "Sector-wide movement",
                        "Available same-sector peers are moving in the same direction, averaging ${CompanyResearchPresentation.percent(sectorAverage)}."
                    )
                }
            }
        }

        val marketAverage = context.marketAverage
        if (marketAverage != null && context.marketCount >= 5) {
            val difference = abs(stock.change - marketAverage)
            val opposite = stock.change != 0.0 && marketAverage != 0.0 &&
                !MarketMovementContextPresentation.sameDirection(stock.change, marketAverage)
            if ((opposite && magnitude >= 2.0) || difference >= 3.0) {
                score += 1
                reasons += MarketAttentionReason(
                    "Different from the broader feed",
                    "Available same-date peers averaged ${CompanyResearchPresentation.percent(marketAverage)} across ${context.marketCount} observations."
                )
            }
        }

        if (
            stock.volumeAvailable &&
            stock.averageVolumeAvailable &&
            stock.volume > 0L &&
            stock.averageVolume > 0L
        ) {
            val ratio = stock.volume.toDouble() / stock.averageVolume.toDouble()
            when {
                ratio >= 2.0 -> {
                    score += 2
                    reasons += MarketAttentionReason(
                        "Unusually high reported volume",
                        "Reported volume is ${String.format(Locale.US, "%.1f", ratio)}× the supplied average-volume baseline."
                    )
                }
                ratio >= 1.5 -> {
                    score += 1
                    reasons += MarketAttentionReason(
                        "Higher reported volume",
                        "Reported volume is ${String.format(Locale.US, "%.1f", ratio)}× the supplied average-volume baseline."
                    )
                }
            }
        }

        val evidence = WatchlistPresentation.linkedNews(news, listOf(stock))
            .filter { item ->
                val published = CompanyResearchPresentation.timestamp(item.publishedAt) ?: return@filter false
                !published.isAfter(now) && Duration.between(published, now) <= Duration.ofDays(3)
            }
            .maxByOrNull { CompanyResearchPresentation.timestamp(it.publishedAt) ?: Instant.MIN }

        if (evidence != null) {
            score += 1
            reasons += MarketAttentionReason(
                "Fresh company evidence available",
                "${evidence.source.ifBlank { "Source unavailable" }} published a recent company update; timing alone does not prove it caused the move."
            )
        }

        if (reasons.isEmpty()) return null
        return MarketAttentionItem(
            stock = stock,
            score = score,
            context = context,
            reasons = reasons,
            latestEvidence = evidence
        )
    }
}
