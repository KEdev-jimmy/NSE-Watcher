package ke.co.nsewatcher.data

import ke.co.nsewatcher.NewsItem
import ke.co.nsewatcher.Stock
import ke.co.nsewatcher.domain.EvidenceAdapters
import ke.co.nsewatcher.domain.EvidenceRecord
import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic intelligence layer.
 *
 * It does not invent causes, predict prices, or issue BUY/SELL instructions.
 * It turns sourced company, market and news fields into explainable signals,
 * watchpoints and explicit unknowns. AI can sit above this layer later.
 */
object CompanyIntelligenceEngine {
    enum class SignalType { SUPPORTING, CAUTION, UNKNOWN }

    data class Signal(
        val type: SignalType,
        val text: String,
        val evidence: String = ""
    )

    data class DataQuality(
        val profile: Boolean,
        val financialHistory: Boolean,
        val dividends: Boolean,
        val news: Boolean,
        val priceHistory: Boolean,
        val evidenceCount: Int
    ) {
        val availableCount: Int get() = listOf(profile, financialHistory, dividends, news, priceHistory).count { it }
        val state: String
            get() = when {
                availableCount >= 4 -> "GOOD COVERAGE"
                availableCount >= 2 -> "PARTIAL COVERAGE"
                else -> "LIMITED EVIDENCE"
            }
    }

    data class Result(
        val summary: String,
        val signals: List<Signal>,
        val risks: List<String>,
        val unknowns: List<String>,
        val quality: DataQuality,
        val confidence: String,
        val evidenceRecords: List<EvidenceRecord>
    )

    fun build(
        stock: Stock,
        source: CompanyIntelligenceCache.Result,
        priceHistory: List<Double>,
        news: List<NewsItem>
    ): Result {
        val profile = source.profile
        val monthlyReturn = percentReturn(priceHistory)
        val signals = mutableListOf<Signal>()
        val risks = mutableListOf<String>()
        val unknowns = mutableListOf<String>()

        if (monthlyReturn != null) {
            val direction = if (monthlyReturn >= 0) "gained" else "fallen"
            signals += Signal(
                if (monthlyReturn >= 0) SignalType.SUPPORTING else SignalType.CAUTION,
                "${stock.name} has $direction ${formatPercent(abs(monthlyReturn))} over the loaded monthly history.",
                "MyStocks Africa price history"
            )
        } else {
            unknowns += "A complete monthly price history is not available, so recent market behaviour cannot be described reliably."
        }

        val financialTrend = buildFinancialTrendSignals(source.financialHistory)
        signals += financialTrend.signals
        unknowns += financialTrend.unknowns

        parsePercent(profile.revenueGrowth)?.let { growth ->
            signals += Signal(
                if (growth >= 0) SignalType.SUPPORTING else SignalType.CAUTION,
                "Reported revenue growth is ${formatPercent(growth)}.",
                "MyStocks Africa company profile"
            )
        } ?: unknowns.add("Revenue growth is not available in the current company response.")

        parsePercent(profile.profitGrowth)?.let { growth ->
            signals += Signal(
                if (growth >= 0) SignalType.SUPPORTING else SignalType.CAUTION,
                "Reported profit growth is ${formatPercent(growth)}.",
                "MyStocks Africa company profile"
            )
        } ?: unknowns.add("Profit growth is not available in the current company response.")

        parseNumber(profile.eps)?.let { eps ->
            if (eps < 0) {
                signals += Signal(SignalType.CAUTION, "Reported EPS is negative.", "MyStocks Africa company profile")
                risks += "Review the latest results and management commentary to understand the reason for the negative EPS."
            }
        } ?: unknowns.add("EPS is not available in the current company response.")

        parseNumber(profile.debtToEquity)?.let { debt ->
            if (debt > 1.5) {
                signals += Signal(SignalType.CAUTION, "Reported debt/equity is ${formatNumber(debt)}.", "MyStocks Africa company profile")
                risks += "Compare leverage with the company's own history and relevant sector peers."
            }
        } ?: unknowns.add("Debt/equity is not available for leverage context.")

        if (source.dividends.isNotEmpty()) {
            signals += Signal(SignalType.SUPPORTING, "Dividend records are available for review.", "MyStocks Africa dividend history")
        } else {
            unknowns += "No dividend history was returned by the current provider response."
        }

        if (news.isNotEmpty()) {
            signals += Signal(SignalType.SUPPORTING, "${news.size} recent company intelligence item${if (news.size == 1) " is" else "s are"} available.", "MyStocks Africa company news feed")
        } else {
            unknowns += "No recent company intelligence was returned. Check issuer and NSE announcements for material events."
            risks += "Recent company events could not be established from the current feed."
        }

        if (abs(stock.change) >= 5.0) {
            val direction = if (stock.change >= 0) "positive" else "negative"
            risks += "Today's ${direction} ${formatPercent(abs(stock.change))} move deserves an event/news check rather than an assumed explanation."
        }

        if (profile.pe.isBlank() || profile.pb.isBlank()) {
            unknowns += "Complete valuation context is not available from the current provider response."
        }
        if (source.financialHistory.isEmpty()) {
            unknowns += "Comparable historical financial statements are not available yet."
        }
        if (source.error != null) {
            risks += "The company intelligence service reported an error: ${source.error}."
        }

        val evidenceRecords = (source.evidence.mapNotNull { EvidenceAdapters.fromCompanyEvidence(it) } +
            news.mapNotNull { EvidenceAdapters.fromNews(it) })
            .distinctBy { it.id }

        val quality = DataQuality(
            profile = profile.description.isNotBlank() || profile.sector.isNotBlank() || profile.revenue.isNotBlank(),
            financialHistory = source.financialHistoryAvailable && source.financialHistory.isNotEmpty(),
            dividends = source.dividends.isNotEmpty(),
            news = news.isNotEmpty(),
            priceHistory = priceHistory.count { it.isFinite() && it > 0.0 } >= 2,
            evidenceCount = source.evidence.size
        )

        val confidence = when {
            quality.availableCount >= 4 && quality.evidenceCount >= 5 -> "HIGHER EVIDENCE COVERAGE"
            quality.availableCount >= 2 -> "MODERATE EVIDENCE COVERAGE"
            else -> "INSUFFICIENT EVIDENCE"
        }

        val summary = when {
            quality.state == "LIMITED EVIDENCE" -> "The current data set is incomplete. NSE Watcher will show what is known and clearly mark what still needs evidence."
            monthlyReturn != null && financialTrend.hasTrend && news.isNotEmpty() -> "${stock.name} has moved ${formatPercent(monthlyReturn)} over the loaded monthly history. Reported financial trend and recent company intelligence are available for context."
            monthlyReturn != null && news.isNotEmpty() -> "${stock.name} has moved ${formatPercent(monthlyReturn)} over the loaded monthly history, with recent company intelligence available for context."
            monthlyReturn != null && financialTrend.hasTrend -> "${stock.name} has moved ${formatPercent(monthlyReturn)} over the loaded monthly history, with a reported financial trend available for context."
            monthlyReturn != null -> "${stock.name} has moved ${formatPercent(monthlyReturn)} over the loaded monthly history. Company context is still partial."
            financialTrend.hasTrend -> "Reported financial trend is available, but recent price context is incomplete."
            else -> "Current company and market evidence is available, but recent price context is incomplete."
        }

        return Result(
            summary = summary,
            signals = signals.distinctBy { it.type to it.text }.take(12),
            risks = risks.distinct().take(8),
            unknowns = unknowns.distinct().take(8),
            quality = quality,
            confidence = confidence,
            evidenceRecords = evidenceRecords
        )
    }

    private data class FinancialTrendResult(
        val signals: List<Signal>,
        val unknowns: List<String>,
        val hasTrend: Boolean
    )

    private fun buildFinancialTrendSignals(history: List<CompanyIntelligenceCache.FinancialPoint>): FinancialTrendResult {
        if (history.size < 2) {
            return FinancialTrendResult(
                signals = emptyList(),
                unknowns = listOf("At least two reported financial periods are needed before NSE Watcher describes a financial trend."),
                hasTrend = false
            )
        }

        val previous = history[history.lastIndex - 1]
        val latest = history.last()
        val signals = mutableListOf<Signal>()
        val unknowns = mutableListOf<String>()

        val revenuePrevious = parseNumber(previous.revenue)
        val revenueLatest = parseNumber(latest.revenue)
        when {
            revenuePrevious != null && revenueLatest != null && revenuePrevious != 0.0 -> {
                val change = ((revenueLatest - revenuePrevious) / abs(revenuePrevious)) * 100.0
                signals += Signal(
                    if (change >= 0) SignalType.SUPPORTING else SignalType.CAUTION,
                    "Reported revenue is ${formatPercent(change)} versus the preceding returned period.",
                    "MyStocks Africa financial history"
                )
            }
            else -> unknowns += "Revenue trend cannot be calculated because comparable numeric values were not returned for the latest two periods."
        }

        val profitPrevious = parseNumber(previous.profit)
        val profitLatest = parseNumber(latest.profit)
        when {
            profitPrevious != null && profitLatest != null && profitPrevious != 0.0 -> {
                val change = ((profitLatest - profitPrevious) / abs(profitPrevious)) * 100.0
                signals += Signal(
                    if (change >= 0) SignalType.SUPPORTING else SignalType.CAUTION,
                    "Reported profit is ${formatPercent(change)} versus the preceding returned period.",
                    "MyStocks Africa financial history"
                )
            }
            else -> unknowns += "Profit trend cannot be calculated because comparable numeric values were not returned for the latest two periods."
        }

        return FinancialTrendResult(
            signals = signals,
            unknowns = unknowns,
            hasTrend = signals.isNotEmpty()
        )
    }

    private fun parsePercent(value: String): Double? {
        val cleaned = value.trim().replace(",", "")
        val match = Regex("[-+]?\\d+(?:\\.\\d+)?").find(cleaned) ?: return null
        return match.value.toDoubleOrNull()
    }

    private fun parseNumber(value: String): Double? = Regex("[-+]?\\d+(?:\\.\\d+)?")
        .find(value.replace(",", ""))?.value?.toDoubleOrNull()

    private fun percentReturn(values: List<Double>): Double? {
        val valid = values.filter { it.isFinite() && it > 0.0 }
        if (valid.size < 2) return null
        val first = valid.first()
        return ((valid.last() - first) / first) * 100.0
    }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%+.1f%%", value)
    private fun formatNumber(value: Double): String = String.format(Locale.US, "%.2f", value)
}
