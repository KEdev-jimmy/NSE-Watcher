package ke.co.nsewatcher.data

import ke.co.nsewatcher.domain.TechnicalStrengthDataQuality
import ke.co.nsewatcher.domain.TechnicalStrengthEngine
import ke.co.nsewatcher.domain.TechnicalStrengthResult

internal object TechnicalStrengths {
    fun calculate(history: MyStocksCache.HistoryResult): TechnicalStrengthResult {
        val indicators = TechnicalIndicators.calculate(history)
        val historyQuality = history.dataQuality
        val quality = TechnicalStrengthDataQuality(
            ohlcCoveragePct = historyQuality.ohlcCoveragePct.takeIf {
                historyQuality.candleCount > 0 || historyQuality.ohlcCoveragePct > 0.0
            },
            qualityIssueCount = historyQuality.qualityIssues.size,
            sandbox = historyQuality.sandbox
        )
        return TechnicalStrengthEngine.calculate(indicators, quality)
    }
}
