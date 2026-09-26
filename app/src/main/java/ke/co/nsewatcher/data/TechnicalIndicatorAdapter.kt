package ke.co.nsewatcher.data

import ke.co.nsewatcher.domain.TechnicalCandle
import ke.co.nsewatcher.domain.TechnicalIndicatorEngine
import ke.co.nsewatcher.domain.TechnicalIndicatorSnapshot

internal object TechnicalIndicators {
    fun calculate(history: MyStocksCache.HistoryResult): TechnicalIndicatorSnapshot =
        TechnicalIndicatorEngine.calculate(
            history.points.map { point ->
                TechnicalCandle(
                    close = point.close,
                    high = point.high,
                    low = point.low,
                    volume = point.volume,
                    volumeAvailable = point.volumeAvailable
                )
            }
        )
}
