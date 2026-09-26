package ke.co.nsewatcher.domain

import kotlin.math.max

data class TechnicalCandle(
    val close: Double,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Double? = null,
    val volumeAvailable: Boolean = false
)

data class MovingAverageSnapshot(
    val sma20: Double? = null,
    val sma50: Double? = null,
    val sma100: Double? = null,
    val sma200: Double? = null,
    val ema20: Double? = null,
    val ema50: Double? = null
)

data class MacdSnapshot(
    val macd: Double,
    val signal: Double,
    val histogram: Double
)

data class StochasticSnapshot(
    val k: Double,
    val d: Double?
)

data class VolumeConfirmationSnapshot(
    val latestVolume: Double,
    val averageVolume20: Double,
    val ratioToAverage20: Double?
)

data class TechnicalIndicatorSnapshot(
    val observationCount: Int,
    val latestClose: Double?,
    val movingAverages: MovingAverageSnapshot,
    val rsi14: Double?,
    val macd: MacdSnapshot?,
    val stochastic14: StochasticSnapshot?,
    val volume20: VolumeConfirmationSnapshot?
) {
    val availableIndicators: Set<String>
        get() = buildSet {
            if (movingAverages.sma20 != null) add("SMA20")
            if (movingAverages.sma50 != null) add("SMA50")
            if (movingAverages.sma100 != null) add("SMA100")
            if (movingAverages.sma200 != null) add("SMA200")
            if (movingAverages.ema20 != null) add("EMA20")
            if (movingAverages.ema50 != null) add("EMA50")
            if (rsi14 != null) add("RSI14")
            if (macd != null) add("MACD")
            if (stochastic14 != null) add("STOCHASTIC14")
            if (volume20 != null) add("VOLUME20")
        }
}

/**
 * Pure, deterministic technical-indicator calculations.
 *
 * This engine does not fetch data, infer missing observations, or create trading
 * recommendations. It only calculates indicators from the observations supplied
 * to it. Missing OHLC or volume therefore remains unavailable to the indicators
 * that require those fields.
 */
object TechnicalIndicatorEngine {
    private const val RSI_PERIOD = 14
    private const val MACD_FAST = 12
    private const val MACD_SLOW = 26
    private const val MACD_SIGNAL = 9
    private const val STOCHASTIC_PERIOD = 14
    private const val STOCHASTIC_SIGNAL = 3
    private const val VOLUME_PERIOD = 20

    fun calculate(candles: List<TechnicalCandle>): TechnicalIndicatorSnapshot {
        val valid = candles.filter { it.close.isFinite() && it.close > 0.0 }
        val closes = valid.map { it.close }

        val movingAverages = MovingAverageSnapshot(
            sma20 = sma(closes, 20),
            sma50 = sma(closes, 50),
            sma100 = sma(closes, 100),
            sma200 = sma(closes, 200),
            ema20 = ema(closes, 20),
            ema50 = ema(closes, 50)
        )

        return TechnicalIndicatorSnapshot(
            observationCount = valid.size,
            latestClose = closes.lastOrNull(),
            movingAverages = movingAverages,
            rsi14 = rsi(closes, RSI_PERIOD),
            macd = macd(closes, MACD_FAST, MACD_SLOW, MACD_SIGNAL),
            stochastic14 = stochastic(valid, STOCHASTIC_PERIOD, STOCHASTIC_SIGNAL),
            volume20 = volumeConfirmation(valid, VOLUME_PERIOD)
        )
    }

    internal fun sma(values: List<Double>, period: Int): Double? {
        if (period <= 0 || values.size < period) return null
        val window = values.takeLast(period)
        if (window.any { !it.isFinite() }) return null
        return window.average()
    }

    internal fun ema(values: List<Double>, period: Int): Double? =
        emaSeries(values, period).lastOrNull()

    internal fun rsi(values: List<Double>, period: Int = RSI_PERIOD): Double? {
        if (period <= 0 || values.size < period + 1) return null
        if (values.any { !it.isFinite() }) return null

        var gains = 0.0
        var losses = 0.0
        for (index in 1..period) {
            val delta = values[index] - values[index - 1]
            if (delta > 0.0) gains += delta else if (delta < 0.0) losses += -delta
        }

        var averageGain = gains / period
        var averageLoss = losses / period

        for (index in period + 1 until values.size) {
            val delta = values[index] - values[index - 1]
            val gain = max(delta, 0.0)
            val loss = max(-delta, 0.0)
            averageGain = ((averageGain * (period - 1)) + gain) / period
            averageLoss = ((averageLoss * (period - 1)) + loss) / period
        }

        return when {
            averageGain == 0.0 && averageLoss == 0.0 -> 50.0
            averageLoss == 0.0 -> 100.0
            averageGain == 0.0 -> 0.0
            else -> {
                val relativeStrength = averageGain / averageLoss
                100.0 - (100.0 / (1.0 + relativeStrength))
            }
        }
    }

    internal fun macd(
        values: List<Double>,
        fastPeriod: Int = MACD_FAST,
        slowPeriod: Int = MACD_SLOW,
        signalPeriod: Int = MACD_SIGNAL
    ): MacdSnapshot? {
        if (fastPeriod <= 0 || slowPeriod <= fastPeriod || signalPeriod <= 0) return null
        if (values.size < slowPeriod + signalPeriod - 1 || values.any { !it.isFinite() }) return null

        val fast = emaAligned(values, fastPeriod)
        val slow = emaAligned(values, slowPeriod)
        val macdSeries = buildList {
            for (index in values.indices) {
                val fastValue = fast[index]
                val slowValue = slow[index]
                if (fastValue != null && slowValue != null) {
                    add(fastValue - slowValue)
                }
            }
        }

        val signal = ema(macdSeries, signalPeriod) ?: return null
        val currentMacd = macdSeries.lastOrNull() ?: return null
        return MacdSnapshot(
            macd = currentMacd,
            signal = signal,
            histogram = currentMacd - signal
        )
    }

    internal fun stochastic(
        candles: List<TechnicalCandle>,
        period: Int = STOCHASTIC_PERIOD,
        signalPeriod: Int = STOCHASTIC_SIGNAL
    ): StochasticSnapshot? {
        if (period <= 0 || signalPeriod <= 0 || candles.size < period) return null

        val kSeries = mutableListOf<Double?>()
        for (index in candles.indices) {
            if (index < period - 1) {
                kSeries += null
                continue
            }
            val window = candles.subList(index - period + 1, index + 1)
            val highs = window.map { it.high }
            val lows = window.map { it.low }
            if (highs.any { it == null || !it.isFinite() } || lows.any { it == null || !it.isFinite() }) {
                kSeries += null
                continue
            }

            val highest = highs.filterNotNull().maxOrNull()
            val lowest = lows.filterNotNull().minOrNull()
            if (highest == null || lowest == null) {
                kSeries += null
                continue
            }
            val close = candles[index].close
            val range = highest - lowest
            val k = if (range == 0.0) 50.0 else ((close - lowest) / range) * 100.0
            kSeries += k.coerceIn(0.0, 100.0)
        }

        val latestK = kSeries.lastOrNull() ?: return null
        val latestSignalWindow = kSeries.takeLast(signalPeriod)
        val d = if (
            latestSignalWindow.size == signalPeriod &&
            latestSignalWindow.all { it != null && it.isFinite() }
        ) {
            latestSignalWindow.filterNotNull().average()
        } else null

        return StochasticSnapshot(k = latestK, d = d)
    }

    internal fun volumeConfirmation(
        candles: List<TechnicalCandle>,
        period: Int = VOLUME_PERIOD
    ): VolumeConfirmationSnapshot? {
        if (period <= 0 || candles.size < period) return null
        val window = candles.takeLast(period)
        if (window.any {
                !it.volumeAvailable ||
                    it.volume == null ||
                    !it.volume.isFinite() ||
                    it.volume < 0.0
            }
        ) return null

        val volumes = window.map { it.volume!! }
        val average = volumes.average()
        val latest = volumes.last()
        return VolumeConfirmationSnapshot(
            latestVolume = latest,
            averageVolume20 = average,
            ratioToAverage20 = if (average > 0.0) latest / average else null
        )
    }

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        if (period <= 0 || values.size < period || values.any { !it.isFinite() }) return emptyList()
        val alpha = 2.0 / (period + 1.0)
        var current = values.take(period).average()
        val result = mutableListOf(current)
        for (index in period until values.size) {
            current = ((values[index] - current) * alpha) + current
            result += current
        }
        return result
    }

    private fun emaAligned(values: List<Double>, period: Int): List<Double?> {
        val aligned = MutableList<Double?>(values.size) { null }
        val series = emaSeries(values, period)
        series.forEachIndexed { index, value ->
            aligned[index + period - 1] = value
        }
        return aligned
    }
}
