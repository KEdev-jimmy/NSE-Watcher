package ke.co.nsewatcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalStrengthEngineTest {
    private fun snapshot(
        latestClose: Double = 120.0,
        previousClose: Double = 118.0,
        movingAverages: MovingAverageSnapshot = MovingAverageSnapshot(
            sma20 = 115.0,
            sma50 = 110.0,
            sma100 = 106.0,
            sma200 = 100.0,
            ema20 = 114.0,
            ema50 = 108.0
        ),
        rsi: Double? = 62.0,
        macd: MacdSnapshot? = MacdSnapshot(macd = 2.0, signal = 1.0, histogram = 1.0),
        stochastic: StochasticSnapshot? = StochasticSnapshot(k = 70.0, d = 60.0),
        volume: VolumeConfirmationSnapshot? = VolumeConfirmationSnapshot(
            latestVolume = 150.0,
            averageVolume20 = 100.0,
            ratioToAverage20 = 1.5
        ),
        observations: Int = 220
    ): TechnicalIndicatorSnapshot {
        val change = if (previousClose > 0.0) ((latestClose - previousClose) / previousClose) * 100.0 else null
        return TechnicalIndicatorSnapshot(
            observationCount = observations,
            latestClose = latestClose,
            previousClose = previousClose,
            latestChangePct = change,
            movingAverages = movingAverages,
            rsi14 = rsi,
            macd = macd,
            stochastic14 = stochastic,
            volume20 = volume
        )
    }

    private val fullQuality = TechnicalStrengthDataQuality(
        ohlcCoveragePct = 100.0,
        qualityIssueCount = 0,
        sandbox = false
    )

    @Test fun fullyAlignedBullishSignalsProduceStrongBullishWithHighConfidence() {
        val result = TechnicalStrengthEngine.calculate(snapshot(), fullQuality)

        assertEquals(100, result.score)
        assertEquals(TechnicalStrengthLabel.STRONG_BULLISH, result.label)
        assertEquals(100, result.trend.score)
        assertEquals(100, result.momentum.score)
        assertEquals(100, result.participation.score)
        assertEquals(100, result.confidenceScore)
        assertEquals(TechnicalConfidenceLabel.HIGH, result.confidenceLabel)
        assertTrue(result.dataSufficient)
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test fun fullyAlignedBearishSignalsProduceStrongBearish() {
        val result = TechnicalStrengthEngine.calculate(
            snapshot(
                latestClose = 80.0,
                previousClose = 82.0,
                movingAverages = MovingAverageSnapshot(
                    sma20 = 85.0,
                    sma50 = 90.0,
                    sma100 = 95.0,
                    sma200 = 100.0,
                    ema20 = 86.0,
                    ema50 = 92.0
                ),
                rsi = 38.0,
                macd = MacdSnapshot(macd = -2.0, signal = -1.0, histogram = -1.0),
                stochastic = StochasticSnapshot(k = 30.0, d = 40.0)
            ),
            fullQuality
        )

        assertEquals(0, result.score)
        assertEquals(TechnicalStrengthLabel.STRONG_BEARISH, result.label)
        assertEquals(0, result.trend.score)
        assertEquals(0, result.momentum.score)
        assertEquals(0, result.participation.score)
        assertEquals(100, result.confidenceScore)
    }

    @Test fun balancedSignalsRemainNeutralRatherThanForcingDirection() {
        val result = TechnicalStrengthEngine.calculate(
            snapshot(
                latestClose = 100.0,
                previousClose = 100.0,
                movingAverages = MovingAverageSnapshot(
                    sma20 = 100.0,
                    sma50 = 100.0,
                    sma100 = 100.0,
                    sma200 = 100.0,
                    ema20 = 100.0,
                    ema50 = 100.0
                ),
                rsi = 50.0,
                macd = MacdSnapshot(macd = 0.0, signal = 0.0, histogram = 0.0),
                stochastic = StochasticSnapshot(k = 50.0, d = 50.0),
                volume = VolumeConfirmationSnapshot(100.0, 100.0, 1.0)
            ),
            fullQuality
        )

        assertEquals(50, result.score)
        assertEquals(TechnicalStrengthLabel.NEUTRAL, result.label)
        assertEquals(50, result.trend.score)
        assertEquals(50, result.momentum.score)
        assertEquals(50, result.participation.score)
    }

    @Test fun insufficientCoverageWithholdsOverallScoreInsteadOfGuessing() {
        val result = TechnicalStrengthEngine.calculate(
            snapshot(
                latestClose = 100.0,
                previousClose = 99.0,
                movingAverages = MovingAverageSnapshot(sma20 = 98.0),
                rsi = 60.0,
                macd = null,
                stochastic = null,
                volume = null,
                observations = 20
            ),
            fullQuality
        )

        assertNull(result.score)
        assertEquals(TechnicalStrengthLabel.INSUFFICIENT_DATA, result.label)
        assertTrue(!result.dataSufficient)
        assertTrue(result.cautions.any { it.contains("withheld") })
    }

    @Test fun overboughtOscillatorsAddCautionInsteadOfExtraBullishVotes() {
        val result = TechnicalStrengthEngine.calculate(
            snapshot(
                rsi = 75.0,
                stochastic = StochasticSnapshot(k = 85.0, d = 80.0)
            ),
            fullQuality
        )

        assertEquals(
            TechnicalSignalDirection.NEUTRAL,
            result.signals.single { it.id == "rsi14" }.direction
        )
        assertEquals(
            TechnicalSignalDirection.NEUTRAL,
            result.signals.single { it.id == "stochastic14" }.direction
        )
        assertTrue(result.cautions.any { it.contains("RSI is in an overbought") })
        assertTrue(result.cautions.any { it.contains("Stochastic is in an overbought") })
    }

    @Test fun missingVolumeRemovesParticipationWithoutDestroyingTrendScore() {
        val result = TechnicalStrengthEngine.calculate(
            snapshot(volume = null),
            fullQuality
        )

        assertEquals(TechnicalStrengthLabel.STRONG_BULLISH, result.label)
        assertEquals(100, result.score)
        assertNull(result.participation.score)
        assertTrue(result.confidenceScore < 100)
        assertTrue(result.cautions.any { it.contains("volume confirmation is unavailable") })
    }

    @Test fun poorProviderQualityReducesConfidenceButDoesNotRewriteDirection() {
        val result = TechnicalStrengthEngine.calculate(
            snapshot(),
            TechnicalStrengthDataQuality(
                ohlcCoveragePct = 20.0,
                qualityIssueCount = 2,
                sandbox = true
            )
        )

        assertEquals(100, result.score)
        assertEquals(TechnicalStrengthLabel.STRONG_BULLISH, result.label)
        assertEquals(62, result.confidenceScore)
        assertEquals(TechnicalConfidenceLabel.MODERATE, result.confidenceLabel)
        assertTrue(result.cautions.any { it.contains("sandbox") })
        assertTrue(result.cautions.any { it.contains("2 provider data-quality issues") })
    }

    @Test fun tinyMovingAverageDifferenceFallsInsideNeutralNoiseBand() {
        val result = TechnicalStrengthEngine.calculate(
            snapshot(
                latestClose = 100.4,
                previousClose = 100.0,
                movingAverages = MovingAverageSnapshot(
                    sma20 = 100.0,
                    sma50 = 95.0,
                    sma100 = 92.0,
                    sma200 = 90.0,
                    ema20 = 98.0,
                    ema50 = 95.0
                )
            ),
            fullQuality
        )

        assertEquals(
            TechnicalSignalDirection.NEUTRAL,
            result.signals.single { it.id == "price_vs_sma20" }.direction
        )
    }
}
