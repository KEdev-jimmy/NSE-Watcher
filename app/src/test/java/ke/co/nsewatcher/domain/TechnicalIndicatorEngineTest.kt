package ke.co.nsewatcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalIndicatorEngineTest {
    @Test fun simpleMovingAverageUsesOnlyRequestedTrailingWindow() {
        val values = (1..10).map(Int::toDouble)

        assertEquals(8.0, TechnicalIndicatorEngine.sma(values, 5) ?: Double.NaN, 0.0000001)
        assertNull(TechnicalIndicatorEngine.sma(values, 11))
    }

    @Test fun exponentialMovingAverageUsesSmaSeedAndStandardMultiplier() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

        assertEquals(4.0, TechnicalIndicatorEngine.ema(values, 3) ?: Double.NaN, 0.0000001)
    }

    @Test fun rsiHandlesRisingFallingAndFlatSeriesWithoutDivisionErrors() {
        val rising = (1..20).map(Int::toDouble)
        val falling = (1..20).reversed().map(Int::toDouble)
        val flat = List(20) { 50.0 }

        assertEquals(100.0, TechnicalIndicatorEngine.rsi(rising) ?: Double.NaN, 0.0000001)
        assertEquals(0.0, TechnicalIndicatorEngine.rsi(falling) ?: Double.NaN, 0.0000001)
        assertEquals(50.0, TechnicalIndicatorEngine.rsi(flat) ?: Double.NaN, 0.0000001)
        assertNull(TechnicalIndicatorEngine.rsi(List(14) { 10.0 }))
    }

    @Test fun macdUsesAlignedTwelveTwentySixAndNinePeriodEmas() {
        val steadilyRising = (1..40).map(Int::toDouble)

        val result = TechnicalIndicatorEngine.macd(steadilyRising)

        assertEquals(7.0, result?.macd ?: Double.NaN, 0.0000001)
        assertEquals(7.0, result?.signal ?: Double.NaN, 0.0000001)
        assertEquals(0.0, result?.histogram ?: Double.NaN, 0.0000001)
        assertNull(TechnicalIndicatorEngine.macd(List(33) { 10.0 }))
    }

    @Test fun stochasticUsesActualHighLowRangeAndThreeValueSignalAverage() {
        val candles = (0 until 16).map { index ->
            TechnicalCandle(
                close = 10.0 + index,
                high = 100.0,
                low = 0.0
            )
        }

        val result = TechnicalIndicatorEngine.stochastic(candles)

        assertEquals(25.0, result?.k ?: Double.NaN, 0.0000001)
        assertEquals(24.0, result?.d ?: Double.NaN, 0.0000001)
    }

    @Test fun stochasticDoesNotInventMissingHighOrLowValues() {
        val candles = (0 until 14).map { index ->
            TechnicalCandle(
                close = 20.0 + index,
                high = if (index == 13) null else 25.0 + index,
                low = 15.0 + index
            )
        }

        assertNull(TechnicalIndicatorEngine.stochastic(candles))
    }

    @Test fun volumeConfirmationUsesOnlyVerifiedTrailingTwentySessionVolumes() {
        val candles = (0 until 20).map { index ->
            TechnicalCandle(
                close = 50.0,
                volume = if (index == 19) 200.0 else 100.0,
                volumeAvailable = true
            )
        }

        val result = TechnicalIndicatorEngine.volumeConfirmation(candles)

        assertEquals(200.0, result?.latestVolume ?: Double.NaN, 0.0000001)
        assertEquals(105.0, result?.averageVolume20 ?: Double.NaN, 0.0000001)
        assertEquals(200.0 / 105.0, result?.ratioToAverage20 ?: Double.NaN, 0.0000001)
    }

    @Test fun volumeConfirmationStaysUnavailableWhenAnyRequiredVolumeIsMissing() {
        val candles = (0 until 20).map { index ->
            TechnicalCandle(
                close = 50.0,
                volume = if (index == 10) null else 100.0,
                volumeAvailable = index != 10
            )
        }

        assertNull(TechnicalIndicatorEngine.volumeConfirmation(candles))
    }

    @Test fun calculationExposesOnlyIndicatorsSupportedByAvailableObservations() {
        val candles = (1..210).map { index ->
            TechnicalCandle(
                close = index.toDouble(),
                high = index + 1.0,
                low = index - 1.0,
                volume = 1_000.0 + index,
                volumeAvailable = true
            )
        }

        val result = TechnicalIndicatorEngine.calculate(candles)

        assertEquals(210, result.observationCount)
        assertEquals(210.0, result.latestClose ?: Double.NaN, 0.0000001)
        assertEquals(200.5, result.movingAverages.sma20 ?: Double.NaN, 0.0000001)
        assertEquals(185.5, result.movingAverages.sma50 ?: Double.NaN, 0.0000001)
        assertEquals(160.5, result.movingAverages.sma100 ?: Double.NaN, 0.0000001)
        assertEquals(110.5, result.movingAverages.sma200 ?: Double.NaN, 0.0000001)
        assertEquals(100.0, result.rsi14 ?: Double.NaN, 0.0000001)
        assertTrue("SMA200" in result.availableIndicators)
        assertTrue("MACD" in result.availableIndicators)
        assertTrue("STOCHASTIC14" in result.availableIndicators)
        assertTrue("VOLUME20" in result.availableIndicators)
    }
}
