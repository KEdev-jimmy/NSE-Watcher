package ke.co.nsewatcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalIndicatorAdapterTest {
    @Test fun verifiedHistoryMapsIntoPureIndicatorEngineWithoutInventingVolume() {
        val history = MyStocksCache.HistoryResult(
            points = (1..40).map { index ->
                MyStocksCache.HistoryPoint(
                    close = index.toDouble(),
                    high = index + 1.0,
                    low = index - 1.0,
                    volume = if (index > 20 && index != 30) 1_000.0 + index else null,
                    volumeAvailable = index > 20 && index != 30
                )
            },
            purpose = "technical-analysis",
            interval = "1d"
        )

        val result = TechnicalIndicators.calculate(history)

        assertEquals(40, result.observationCount)
        assertEquals(40.0, result.latestClose ?: Double.NaN, 0.0000001)
        assertTrue("RSI14" in result.availableIndicators)
        assertTrue("MACD" in result.availableIndicators)
        assertTrue("STOCHASTIC14" in result.availableIndicators)
        assertNull(result.volume20)
    }

    @Test fun fullyVerifiedTrailingVolumeEnablesVolumeConfirmation() {
        val history = MyStocksCache.HistoryResult(
            points = (1..20).map { index ->
                MyStocksCache.HistoryPoint(
                    close = 100.0 + index,
                    high = 101.0 + index,
                    low = 99.0 + index,
                    volume = 10_000.0 + index,
                    volumeAvailable = true
                )
            }
        )

        val result = TechnicalIndicators.calculate(history)

        assertTrue("VOLUME20" in result.availableIndicators)
        assertEquals(10_020.0, result.volume20?.latestVolume ?: Double.NaN, 0.0000001)
    }
}
