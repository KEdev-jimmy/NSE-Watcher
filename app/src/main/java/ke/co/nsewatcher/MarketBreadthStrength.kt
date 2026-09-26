package ke.co.nsewatcher

import kotlin.math.roundToInt

internal enum class MarketBreadthStrengthLabel(val displayName: String) {
    BROAD_STRENGTH("Broad Strength"),
    POSITIVE("Positive"),
    MIXED("Mixed"),
    WEAK("Weak"),
    BROAD_WEAKNESS("Broad Weakness"),
    INSUFFICIENT_DATA("Insufficient data")
}

internal data class MarketBreadthStrengthResult(
    val score: Int?,
    val label: MarketBreadthStrengthLabel,
    val confidenceScore: Int,
    val breadthScore: Int?,
    val sectorParticipationScore: Int?,
    val coveragePct: Double,
    val coveredCompanies: Int,
    val totalCompanies: Int,
    val positiveSectors: Int,
    val negativeSectors: Int,
    val flatSectors: Int,
    val eligibleSectors: Int,
    val reasons: List<String>,
    val cautions: List<String>,
    val dataSufficient: Boolean
)

/**
 * Describes how broadly the latest available daily move is shared across the
 * market. It deliberately uses only already-loaded quote/sector breadth data;
 * it does not make extra per-company history requests and is not a forecast.
 */
internal object MarketBreadthStrengthEngine {
    fun calculate(
        breadth: MarketBreadth,
        sectors: List<MarketSector>
    ): MarketBreadthStrengthResult {
        val total = breadth.total.coerceAtLeast(0)
        val maxCovered = total.takeIf { it > 0 } ?: breadth.covered.coerceAtLeast(0)
        val covered = breadth.covered.coerceIn(0, maxCovered)
        val coverageRatio = if (total > 0) covered.toDouble() / total else 0.0
        val coveragePct = coverageRatio * 100.0

        val breadthScore = if (covered > 0) {
            val direction = (breadth.rising - breadth.falling).toDouble() / covered
            (50.0 + direction * 50.0).roundToInt().coerceIn(0, 100)
        } else null

        val eligibleSectorRows = sectors.filter { sector ->
            sector.average?.isFinite() == true && sector.breadth.covered > 0
        }
        val positiveSectors = eligibleSectorRows.count { (it.average ?: 0.0) > 0.0 }
        val negativeSectors = eligibleSectorRows.count { (it.average ?: 0.0) < 0.0 }
        val flatSectors = eligibleSectorRows.size - positiveSectors - negativeSectors
        val sectorScore = if (eligibleSectorRows.isNotEmpty()) {
            val direction = (positiveSectors - negativeSectors).toDouble() / eligibleSectorRows.size
            (50.0 + direction * 50.0).roundToInt().coerceIn(0, 100)
        } else null

        val minimumCovered = if (total >= 20) 10 else maxOf(5, (total * 0.35).roundToInt())
        val dataSufficient =
            total > 0 &&
                covered >= minimumCovered &&
                coverageRatio >= 0.35 &&
                breadthScore != null

        val score = if (dataSufficient) {
            val parts = buildList<Pair<Int, Double>> {
                breadthScore?.let { add(it to 0.70) }
                sectorScore?.let { add(it to 0.30) }
            }
            val weight = parts.sumOf { it.second }
            (parts.sumOf { it.first * it.second } / weight).roundToInt().coerceIn(0, 100)
        } else null

        val confidenceScore = (
            (coverageRatio.coerceIn(0.0, 1.0) * 75.0) +
                ((eligibleSectorRows.size / 5.0).coerceIn(0.0, 1.0) * 25.0)
            ).roundToInt().coerceIn(0, 100)

        val label = score?.let(::labelForScore) ?: MarketBreadthStrengthLabel.INSUFFICIENT_DATA
        val reasons = buildList {
            if (covered > 0) {
                add(
                    breadth.rising.toString() + " of " + covered + " covered shares are advancing, " +
                        breadth.falling + " are declining and " + breadth.flat + " are unchanged."
                )
            }
            if (eligibleSectorRows.isNotEmpty()) {
                add(
                    positiveSectors.toString() + " of " + eligibleSectorRows.size +
                        " covered sectors have a positive equal-weight daily average; " +
                        negativeSectors + " are negative."
                )
            }
            if (!dataSufficient) {
                add("There is not enough comparable daily coverage to publish a market-breadth score.")
            }
        }

        val cautions = buildList {
            if (coverageRatio < 0.70 && total > 0) {
                add(
                    "Only " + coveragePct.roundToInt() +
                        "% of the current company list has a comparable daily change, so confidence is reduced."
                )
            }
            if (eligibleSectorRows.size < 3) {
                add("Sector participation is limited because fewer than three sectors have comparable averages.")
            }
            add(
                "This score measures the breadth of the latest available daily move. " +
                    "It does not predict future returns or tell you whether to buy shares."
            )
        }

        return MarketBreadthStrengthResult(
            score = score,
            label = label,
            confidenceScore = confidenceScore,
            breadthScore = breadthScore,
            sectorParticipationScore = sectorScore,
            coveragePct = coveragePct,
            coveredCompanies = covered,
            totalCompanies = total,
            positiveSectors = positiveSectors,
            negativeSectors = negativeSectors,
            flatSectors = flatSectors,
            eligibleSectors = eligibleSectorRows.size,
            reasons = reasons,
            cautions = cautions,
            dataSufficient = dataSufficient
        )
    }

    private fun labelForScore(score: Int): MarketBreadthStrengthLabel = when {
        score >= 80 -> MarketBreadthStrengthLabel.BROAD_STRENGTH
        score >= 60 -> MarketBreadthStrengthLabel.POSITIVE
        score >= 40 -> MarketBreadthStrengthLabel.MIXED
        score >= 20 -> MarketBreadthStrengthLabel.WEAK
        else -> MarketBreadthStrengthLabel.BROAD_WEAKNESS
    }
}
