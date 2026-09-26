package ke.co.nsewatcher.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class TechnicalSignalGroup { TREND, MOMENTUM, PARTICIPATION }

enum class TechnicalSignalDirection(val numeric: Double) {
    BULLISH(1.0),
    NEUTRAL(0.0),
    BEARISH(-1.0)
}

enum class TechnicalStrengthLabel(val displayName: String) {
    STRONG_BULLISH("Strong Bullish"),
    BULLISH("Bullish"),
    NEUTRAL("Neutral"),
    BEARISH("Bearish"),
    STRONG_BEARISH("Strong Bearish"),
    INSUFFICIENT_DATA("Insufficient data")
}

enum class TechnicalConfidenceLabel(val displayName: String) {
    HIGH("High"),
    MODERATE("Moderate"),
    LIMITED("Limited"),
    LOW("Low")
}

data class TechnicalStrengthDataQuality(
    val ohlcCoveragePct: Double? = null,
    val qualityIssueCount: Int = 0,
    val sandbox: Boolean = false
)

data class TechnicalStrengthSignal(
    val id: String,
    val group: TechnicalSignalGroup,
    val title: String,
    val direction: TechnicalSignalDirection,
    val weight: Double,
    val explanation: String
)

data class TechnicalStrengthComponent(
    val group: TechnicalSignalGroup,
    val score: Int?,
    val availableWeight: Double,
    val bullishCount: Int,
    val neutralCount: Int,
    val bearishCount: Int
)

data class TechnicalStrengthResult(
    val score: Int?,
    val label: TechnicalStrengthLabel,
    val confidenceScore: Int,
    val confidenceLabel: TechnicalConfidenceLabel,
    val trend: TechnicalStrengthComponent,
    val momentum: TechnicalStrengthComponent,
    val participation: TechnicalStrengthComponent,
    val signals: List<TechnicalStrengthSignal>,
    val reasons: List<String>,
    val cautions: List<String>,
    val summary: String,
    val dataSufficient: Boolean
)

/**
 * Converts already-verified technical indicators into a transparent strength score.
 *
 * The result describes current technical conditions. It is not a personal investment
 * recommendation and it deliberately separates directional strength from data confidence.
 */
object TechnicalStrengthEngine {
    private const val PRICE_BAND_PCT = 0.5
    private const val VOLUME_CONFIRMATION_RATIO = 1.20
    private const val PRICE_MOVE_CONFIRMATION_PCT = 0.25

    private const val PRICE_SMA20_WEIGHT = 0.8
    private const val PRICE_SMA50_WEIGHT = 1.0
    private const val PRICE_SMA200_WEIGHT = 1.4
    private const val SMA20_SMA50_WEIGHT = 1.0
    private const val SMA50_SMA200_WEIGHT = 1.4
    private const val EMA20_EMA50_WEIGHT = 0.9
    private const val RSI_WEIGHT = 0.9
    private const val MACD_WEIGHT = 1.4
    private const val STOCHASTIC_WEIGHT = 0.7
    private const val VOLUME_WEIGHT = 0.7

    private const val TOTAL_POSSIBLE_SIGNAL_WEIGHT =
        PRICE_SMA20_WEIGHT +
            PRICE_SMA50_WEIGHT +
            PRICE_SMA200_WEIGHT +
            SMA20_SMA50_WEIGHT +
            SMA50_SMA200_WEIGHT +
            EMA20_EMA50_WEIGHT +
            RSI_WEIGHT +
            MACD_WEIGHT +
            STOCHASTIC_WEIGHT +
            VOLUME_WEIGHT

    fun calculate(
        indicators: TechnicalIndicatorSnapshot,
        quality: TechnicalStrengthDataQuality = TechnicalStrengthDataQuality()
    ): TechnicalStrengthResult {
        val signals = buildList {
            addRelativeSignal(
                id = "price_vs_sma20",
                group = TechnicalSignalGroup.TREND,
                title = "Price vs SMA20",
                left = indicators.latestClose,
                right = indicators.movingAverages.sma20,
                weight = PRICE_SMA20_WEIGHT,
                leftName = "Price",
                rightName = "20-day average"
            )
            addRelativeSignal(
                id = "price_vs_sma50",
                group = TechnicalSignalGroup.TREND,
                title = "Price vs SMA50",
                left = indicators.latestClose,
                right = indicators.movingAverages.sma50,
                weight = PRICE_SMA50_WEIGHT,
                leftName = "Price",
                rightName = "50-day average"
            )
            addRelativeSignal(
                id = "price_vs_sma200",
                group = TechnicalSignalGroup.TREND,
                title = "Price vs SMA200",
                left = indicators.latestClose,
                right = indicators.movingAverages.sma200,
                weight = PRICE_SMA200_WEIGHT,
                leftName = "Price",
                rightName = "200-day average"
            )
            addRelativeSignal(
                id = "sma20_vs_sma50",
                group = TechnicalSignalGroup.TREND,
                title = "SMA20 vs SMA50",
                left = indicators.movingAverages.sma20,
                right = indicators.movingAverages.sma50,
                weight = SMA20_SMA50_WEIGHT,
                leftName = "20-day average",
                rightName = "50-day average"
            )
            addRelativeSignal(
                id = "sma50_vs_sma200",
                group = TechnicalSignalGroup.TREND,
                title = "SMA50 vs SMA200",
                left = indicators.movingAverages.sma50,
                right = indicators.movingAverages.sma200,
                weight = SMA50_SMA200_WEIGHT,
                leftName = "50-day average",
                rightName = "200-day average"
            )
            addRelativeSignal(
                id = "ema20_vs_ema50",
                group = TechnicalSignalGroup.TREND,
                title = "EMA20 vs EMA50",
                left = indicators.movingAverages.ema20,
                right = indicators.movingAverages.ema50,
                weight = EMA20_EMA50_WEIGHT,
                leftName = "20-day EMA",
                rightName = "50-day EMA"
            )

            indicators.rsi14?.let { rsi ->
                val direction = when {
                    rsi <= 30.0 -> TechnicalSignalDirection.NEUTRAL
                    rsi < 45.0 -> TechnicalSignalDirection.BEARISH
                    rsi <= 55.0 -> TechnicalSignalDirection.NEUTRAL
                    rsi < 70.0 -> TechnicalSignalDirection.BULLISH
                    else -> TechnicalSignalDirection.NEUTRAL
                }
                val explanation = when {
                    rsi <= 30.0 -> "RSI is " + format1(rsi) + ", an oversold zone. That shows weak recent momentum but can also precede reversals, so it is treated as caution rather than an automatic bullish signal."
                    rsi < 45.0 -> "RSI is " + format1(rsi) + ", showing weaker recent momentum."
                    rsi <= 55.0 -> "RSI is " + format1(rsi) + ", close to the middle of its range and not strongly directional."
                    rsi < 70.0 -> "RSI is " + format1(rsi) + ", showing positive momentum without being in the overbought zone."
                    else -> "RSI is " + format1(rsi) + ", an overbought zone. Momentum is strong, but extension risk is elevated, so it is treated as caution rather than extra bullish strength."
                }
                add(
                    TechnicalStrengthSignal(
                        id = "rsi14",
                        group = TechnicalSignalGroup.MOMENTUM,
                        title = "RSI (14)",
                        direction = direction,
                        weight = RSI_WEIGHT,
                        explanation = explanation
                    )
                )
            }

            indicators.macd?.let { macd ->
                val scale = maxOf(abs(macd.macd), abs(macd.signal), 1e-9)
                val neutralBand = scale * 0.001
                val direction = when {
                    macd.histogram > neutralBand -> TechnicalSignalDirection.BULLISH
                    macd.histogram < -neutralBand -> TechnicalSignalDirection.BEARISH
                    else -> TechnicalSignalDirection.NEUTRAL
                }
                val explanation = when (direction) {
                    TechnicalSignalDirection.BULLISH ->
                        "MACD is above its signal line by " + format2(abs(macd.histogram)) + ", supporting positive momentum."
                    TechnicalSignalDirection.BEARISH ->
                        "MACD is below its signal line by " + format2(abs(macd.histogram)) + ", supporting negative momentum."
                    TechnicalSignalDirection.NEUTRAL ->
                        "MACD and its signal line are effectively aligned, so momentum is not clearly directional."
                }
                add(
                    TechnicalStrengthSignal(
                        id = "macd",
                        group = TechnicalSignalGroup.MOMENTUM,
                        title = "MACD",
                        direction = direction,
                        weight = MACD_WEIGHT,
                        explanation = explanation
                    )
                )
            }

            indicators.stochastic14?.let { stochastic ->
                val d = stochastic.d
                if (d != null) {
                    val difference = stochastic.k - d
                    val direction = when {
                        stochastic.k >= 80.0 -> TechnicalSignalDirection.NEUTRAL
                        stochastic.k <= 20.0 -> TechnicalSignalDirection.NEUTRAL
                        difference > 1.0 -> TechnicalSignalDirection.BULLISH
                        difference < -1.0 -> TechnicalSignalDirection.BEARISH
                        else -> TechnicalSignalDirection.NEUTRAL
                    }
                    val explanation = when {
                        stochastic.k >= 80.0 ->
                            "Stochastic %K is " + format1(stochastic.k) + ", an overbought zone, so the signal is treated as caution rather than extra bullish strength."
                        stochastic.k <= 20.0 ->
                            "Stochastic %K is " + format1(stochastic.k) + ", an oversold zone, so the signal is treated as caution rather than an automatic bullish reversal signal."
                        direction == TechnicalSignalDirection.BULLISH ->
                            "Stochastic %K (" + format1(stochastic.k) + ") is above %D (" + format1(d) + "), supporting positive short-term momentum."
                        direction == TechnicalSignalDirection.BEARISH ->
                            "Stochastic %K (" + format1(stochastic.k) + ") is below %D (" + format1(d) + "), supporting negative short-term momentum."
                        else ->
                            "Stochastic %K and %D are close together, so short-term momentum is mixed."
                    }
                    add(
                        TechnicalStrengthSignal(
                            id = "stochastic14",
                            group = TechnicalSignalGroup.MOMENTUM,
                            title = "Stochastic (14)",
                            direction = direction,
                            weight = STOCHASTIC_WEIGHT,
                            explanation = explanation
                        )
                    )
                }
            }

            val volume = indicators.volume20
            val latestChangePct = indicators.latestChangePct
            if (volume?.ratioToAverage20 != null && latestChangePct != null) {
                val ratio = volume.ratioToAverage20
                val direction = when {
                    ratio >= VOLUME_CONFIRMATION_RATIO && latestChangePct > PRICE_MOVE_CONFIRMATION_PCT ->
                        TechnicalSignalDirection.BULLISH
                    ratio >= VOLUME_CONFIRMATION_RATIO && latestChangePct < -PRICE_MOVE_CONFIRMATION_PCT ->
                        TechnicalSignalDirection.BEARISH
                    else -> TechnicalSignalDirection.NEUTRAL
                }
                val explanation = when (direction) {
                    TechnicalSignalDirection.BULLISH ->
                        "Latest reported volume is " + format1(ratio) + "× its 20-session average while price rose " + formatSigned1(latestChangePct) + ", confirming participation in the upward move."
                    TechnicalSignalDirection.BEARISH ->
                        "Latest reported volume is " + format1(ratio) + "× its 20-session average while price fell " + formatSigned1(latestChangePct) + ", confirming participation in the downward move."
                    TechnicalSignalDirection.NEUTRAL ->
                        "Latest reported volume is " + format1(ratio) + "× its 20-session average; it does not strongly confirm the latest " + formatSigned1(latestChangePct) + " price move."
                }
                add(
                    TechnicalStrengthSignal(
                        id = "volume20",
                        group = TechnicalSignalGroup.PARTICIPATION,
                        title = "Volume confirmation",
                        direction = direction,
                        weight = VOLUME_WEIGHT,
                        explanation = explanation
                    )
                )
            }
        }

        val trend = component(TechnicalSignalGroup.TREND, signals)
        val momentum = component(TechnicalSignalGroup.MOMENTUM, signals)
        val participation = component(TechnicalSignalGroup.PARTICIPATION, signals)
        val confidenceScore = confidence(indicators, quality, signals)
        val confidenceLabel = confidenceLabel(confidenceScore)

        val dataSufficient =
            signals.size >= 4 &&
                trend.availableWeight >= 1.8 &&
                momentum.availableWeight >= RSI_WEIGHT &&
                confidenceScore >= 40

        val overallScore = if (dataSufficient) {
            weightedComponentScore(
                listOf(
                    trend to 0.50,
                    momentum to 0.40,
                    participation to 0.10
                )
            )
        } else null

        val label = overallScore?.let(::labelForScore) ?: TechnicalStrengthLabel.INSUFFICIENT_DATA
        val reasons = reasons(label, signals)
        val cautions = cautions(indicators, quality, signals, dataSufficient)
        val summary = summary(label, overallScore)

        return TechnicalStrengthResult(
            score = overallScore,
            label = label,
            confidenceScore = confidenceScore,
            confidenceLabel = confidenceLabel,
            trend = trend,
            momentum = momentum,
            participation = participation,
            signals = signals,
            reasons = reasons,
            cautions = cautions,
            summary = summary,
            dataSufficient = dataSufficient
        )
    }

    private fun MutableList<TechnicalStrengthSignal>.addRelativeSignal(
        id: String,
        group: TechnicalSignalGroup,
        title: String,
        left: Double?,
        right: Double?,
        weight: Double,
        leftName: String,
        rightName: String
    ) {
        if (left == null || right == null || !left.isFinite() || !right.isFinite() || right <= 0.0) return
        val pct = ((left - right) / right) * 100.0
        val direction = when {
            pct > PRICE_BAND_PCT -> TechnicalSignalDirection.BULLISH
            pct < -PRICE_BAND_PCT -> TechnicalSignalDirection.BEARISH
            else -> TechnicalSignalDirection.NEUTRAL
        }
        val relationship = when (direction) {
            TechnicalSignalDirection.BULLISH -> "above"
            TechnicalSignalDirection.BEARISH -> "below"
            TechnicalSignalDirection.NEUTRAL -> "close to"
        }
        add(
            TechnicalStrengthSignal(
                id = id,
                group = group,
                title = title,
                direction = direction,
                weight = weight,
                explanation = leftName + " is " + format1(abs(pct)) + "% " + relationship + " the " + rightName + "."
            )
        )
    }

    private fun component(
        group: TechnicalSignalGroup,
        signals: List<TechnicalStrengthSignal>
    ): TechnicalStrengthComponent {
        val available = signals.filter { it.group == group }
        val weight = available.sumOf { it.weight }
        val score = if (weight > 0.0) {
            val raw = available.sumOf { it.direction.numeric * it.weight } / weight
            (50.0 + (raw * 50.0)).roundToInt().coerceIn(0, 100)
        } else null

        return TechnicalStrengthComponent(
            group = group,
            score = score,
            availableWeight = weight,
            bullishCount = available.count { it.direction == TechnicalSignalDirection.BULLISH },
            neutralCount = available.count { it.direction == TechnicalSignalDirection.NEUTRAL },
            bearishCount = available.count { it.direction == TechnicalSignalDirection.BEARISH }
        )
    }

    private fun weightedComponentScore(
        components: List<Pair<TechnicalStrengthComponent, Double>>
    ): Int {
        val available = components.filter { it.first.score != null }
        val weight = available.sumOf { it.second }
        if (weight <= 0.0) return 50
        return (
            available.sumOf { (component, componentWeight) ->
                component.score!!.toDouble() * componentWeight
            } / weight
        ).roundToInt().coerceIn(0, 100)
    }

    private fun confidence(
        indicators: TechnicalIndicatorSnapshot,
        quality: TechnicalStrengthDataQuality,
        signals: List<TechnicalStrengthSignal>
    ): Int {
        val availability = (
            signals.sumOf { it.weight } / TOTAL_POSSIBLE_SIGNAL_WEIGHT
        ).coerceIn(0.0, 1.0)
        val historyDepth = (indicators.observationCount / 200.0).coerceIn(0.0, 1.0)
        val ohlcQuality = (
            (quality.ohlcCoveragePct?.coerceIn(0.0, 100.0) ?: 50.0) / 100.0
        )

        var score = (availability * 60.0) + (historyDepth * 25.0) + (ohlcQuality * 15.0)
        score -= (quality.qualityIssueCount.coerceAtLeast(0) * 3).coerceAtMost(12)
        if (quality.sandbox) score -= 20.0

        return score.roundToInt().coerceIn(0, 100)
    }

    private fun confidenceLabel(score: Int): TechnicalConfidenceLabel = when {
        score >= 80 -> TechnicalConfidenceLabel.HIGH
        score >= 60 -> TechnicalConfidenceLabel.MODERATE
        score >= 40 -> TechnicalConfidenceLabel.LIMITED
        else -> TechnicalConfidenceLabel.LOW
    }

    private fun labelForScore(score: Int): TechnicalStrengthLabel = when {
        score >= 80 -> TechnicalStrengthLabel.STRONG_BULLISH
        score >= 60 -> TechnicalStrengthLabel.BULLISH
        score >= 40 -> TechnicalStrengthLabel.NEUTRAL
        score >= 20 -> TechnicalStrengthLabel.BEARISH
        else -> TechnicalStrengthLabel.STRONG_BEARISH
    }

    private fun reasons(
        label: TechnicalStrengthLabel,
        signals: List<TechnicalStrengthSignal>
    ): List<String> {
        if (label == TechnicalStrengthLabel.INSUFFICIENT_DATA) {
            return listOf("There are not yet enough verified trend and momentum observations to publish a technical-strength score.")
        }

        val preferredDirection = when (label) {
            TechnicalStrengthLabel.STRONG_BULLISH,
            TechnicalStrengthLabel.BULLISH -> TechnicalSignalDirection.BULLISH
            TechnicalStrengthLabel.STRONG_BEARISH,
            TechnicalStrengthLabel.BEARISH -> TechnicalSignalDirection.BEARISH
            else -> TechnicalSignalDirection.NEUTRAL
        }

        val preferred = signals
            .filter { it.direction == preferredDirection }
            .sortedByDescending { it.weight }
            .take(3)
            .map { it.explanation }

        if (preferred.isNotEmpty()) return preferred

        return signals
            .sortedByDescending { it.weight }
            .take(3)
            .map { it.explanation }
    }

    private fun cautions(
        indicators: TechnicalIndicatorSnapshot,
        quality: TechnicalStrengthDataQuality,
        signals: List<TechnicalStrengthSignal>,
        dataSufficient: Boolean
    ): List<String> = buildList {
        indicators.rsi14?.let { rsi ->
            if (rsi >= 70.0) add("RSI is in an overbought zone; strong momentum can still reverse.")
            if (rsi <= 30.0) add("RSI is in an oversold zone; weak momentum can still rebound.")
        }
        indicators.stochastic14?.k?.let { k ->
            if (k >= 80.0) add("Stochastic is in an overbought zone.")
            if (k <= 20.0) add("Stochastic is in an oversold zone.")
        }
        if (signals.none { it.id == "volume20" }) {
            add("Verified 20-session volume confirmation is unavailable, so participation is not scored.")
        }
        if (quality.qualityIssueCount > 0) {
            add(quality.qualityIssueCount.toString() + " provider data-quality issue" + if (quality.qualityIssueCount == 1) " was reported." else "s were reported.")
        }
        if (quality.sandbox) {
            add("The provider marked this history as sandbox data, so confidence is reduced.")
        }
        if (!dataSufficient) {
            add("A score is withheld until enough verified trend and momentum data is available.")
        }
        add("Technical strength describes current price/volume conditions; it is not a personal recommendation or a guarantee of future returns.")
    }.distinct()

    private fun summary(label: TechnicalStrengthLabel, score: Int?): String {
        val suffix = if (score == null) "" else " (" + score + "/100)"
        return when (label) {
            TechnicalStrengthLabel.STRONG_BULLISH ->
                "Most available trend and momentum signals point upward" + suffix + "."
            TechnicalStrengthLabel.BULLISH ->
                "Available technical signals lean upward" + suffix + ", although not all signals agree."
            TechnicalStrengthLabel.NEUTRAL ->
                "Available technical signals are mixed and do not show a clear directional edge" + suffix + "."
            TechnicalStrengthLabel.BEARISH ->
                "Available technical signals lean downward" + suffix + ", although not all signals agree."
            TechnicalStrengthLabel.STRONG_BEARISH ->
                "Most available trend and momentum signals point downward" + suffix + "."
            TechnicalStrengthLabel.INSUFFICIENT_DATA ->
                "Technical strength is unavailable because verified data coverage is not sufficient yet."
        }
    }

    private fun format1(value: Double): String = String.format(Locale.US, "%.1f", value)
    private fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun formatSigned1(value: Double): String = String.format(Locale.US, "%+.1f%%", value)
}
