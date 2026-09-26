package ke.co.nsewatcher.data

import ke.co.nsewatcher.domain.TechnicalConfidenceLabel
import ke.co.nsewatcher.domain.TechnicalStrengthLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalStrengthAdapterTest {
    @Test fun verifiedHistoryProducesExplainableStrengthResult() {
        val points = (1..220).map { index ->
            val close = 80.0 + (index * 0.2)
            MyStocksCache.HistoryPoint(
                close = close,
                open = close - 0.1,
                high = close + 0.4,
                low = close - 0.4,
                volume = 100_000.0 + (index * 100.0),
                volumeAvailable = true
            )
        }
        val history = MyStocksCache.HistoryResult(
            points = points,
            prices = points.map { it.close },
            interval = "1d",
            purpose = "technical-analysis",
            dataQuality = MyStocksCache.HistoryQuality(
                candleCount = 220,
                completeOhlcCount = 220,
                volumeAvailableCount = 220,
                ohlcCoveragePct = 100.0,
                volumeCoveragePct = 100.0
            )
        )

        val result = TechnicalStrengths.calculate(history)

        assertTrue(result.dataSufficient)
        assertTrue(result.score != null)
        assertTrue(result.reasons.isNotEmpty())
        assertTrue(result.cautions.any { it.contains("not a personal recommendation") })
    }

    @Test fun providerQualityFlagsFlowIntoConfidence() {
        val points = (1..220).map { index ->
            val close = 100.0 + index
            MyStocksCache.HistoryPoint(
                close = close,
                high = close + 1.0,
                low = close - 1.0,
                volume = 1_000.0 + index,
                volumeAvailable = true
            )
        }
        val clean = MyStocksCache.HistoryResult(
            points = points,
            dataQuality = MyStocksCache.HistoryQuality(
                candleCount = 220,
                ohlcCoveragePct = 100.0
            )
        )
        val degraded = clean.copy(
            dataQuality = clean.dataQuality.copy(
                ohlcCoveragePct = 30.0,
                qualityIssues = listOf("OLDER_OHLC_PARTIAL", "VOLUME_GAPS"),
                sandbox = true
            )
        )

        val cleanResult = TechnicalStrengths.calculate(clean)
        val degradedResult = TechnicalStrengths.calculate(degraded)

        assertEquals(cleanResult.score, degradedResult.score)
        assertTrue(degradedResult.confidenceScore < cleanResult.confidenceScore)
        assertTrue(
            degradedResult.confidenceLabel == TechnicalConfidenceLabel.MODERATE ||
                degradedResult.confidenceLabel == TechnicalConfidenceLabel.LIMITED
        )
        assertTrue(degradedResult.cautions.any { it.contains("2 provider data-quality issues") })
        assertTrue(degradedResult.cautions.any { it.contains("sandbox") })
    }

    @Test fun shortHistoryNeverGetsDirectionalLabelFromPartialIndicators() {
        val points = (1..20).map { index ->
            MyStocksCache.HistoryPoint(close = 50.0 + index)
        }
        val result = TechnicalStrengths.calculate(
            MyStocksCache.HistoryResult(
                points = points,
                dataQuality = MyStocksCache.HistoryQuality(
                    candleCount = 20,
                    ohlcCoveragePct = 0.0
                )
            )
        )

        assertEquals(TechnicalStrengthLabel.INSUFFICIENT_DATA, result.label)
        assertTrue(result.score == null)
    }
}
