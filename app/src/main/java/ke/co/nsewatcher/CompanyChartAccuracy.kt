package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Conservative display rules; missing observations are never filled in. */
internal object CompanyChartAccuracy {
    private val zone = ZoneId.of("Africa/Nairobi")

    fun observationDate(raw: String): LocalDate? =
        runCatching { Instant.parse(raw).atZone(zone).toLocalDate() }.getOrNull()
            ?: raw.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun sessionTitle(observedAt: String, today: LocalDate = LocalDate.now(zone)): String {
        val date = observationDate(observedAt) ?: return "Session date unavailable"
        return if (date == today) "Today at a glance" else "Session: $date"
    }

    fun periodReturn(
        range: String,
        result: MyStocksCache.HistoryResult,
        oneDayFallback: Double? = null,
        today: LocalDate = LocalDate.now(zone)
    ): Double? {
        if (range == "1D") {
            return (result.dailyChangePct ?: oneDayFallback)?.takeIf { it.isFinite() }
        }
        return MarketPresentation.historicalCoverage(range, result, today).change
    }

    data class ObservedOhlc(val open: Double?, val high: Double?, val low: Double?)

    fun observedOhlc(points: List<MyStocksCache.HistoryPoint>): ObservedOhlc {
        fun positive(value: Double?) = value?.takeIf { it.isFinite() && it > 0.0 }
        val highs = points.map { point -> positive(point.high)?.takeIf { it >= point.close } }
        val lows = points.map { point -> positive(point.low)?.takeIf { it <= point.close } }
        return ObservedOhlc(
            positive(points.firstOrNull()?.open),
            if (highs.isNotEmpty() && highs.all { it != null }) highs.filterNotNull().maxOrNull() else null,
            if (lows.isNotEmpty() && lows.all { it != null }) lows.filterNotNull().minOrNull() else null
        )
    }
}
