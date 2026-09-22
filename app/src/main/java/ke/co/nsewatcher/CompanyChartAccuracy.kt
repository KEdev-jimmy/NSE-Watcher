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
        if (range == "1D") return (result.dailyChangePct ?: oneDayFallback)?.takeIf { it.isFinite() }
        if (result.points.size < 2) return null
        val dated = result.points.map { point ->
            if (!point.close.isFinite() || point.close <= 0.0) return null
            (observationDate(point.date) ?: return null) to point.close
        }.sortedBy { it.first }
        if (dated.map { it.first }.distinct().size != dated.size) return null
        val start = when (range) {
            "3D" -> today.minusDays(3)
            "1W" -> today.minusWeeks(1)
            "1M" -> today.minusMonths(1)
            "3M" -> today.minusMonths(3)
            "6M" -> today.minusMonths(6)
            "1Y" -> today.minusYears(1)
            "3Y" -> today.minusYears(3)
            "5Y" -> today.minusYears(5)
            else -> return null
        }
        // Boundary tolerance for weekends and daily/weekly/monthly aggregation.
        // This does not claim that every intervening trading session is present.
        val tolerance = when (range) {
            "3D" -> 1L
            "1Y" -> 8L
            "3Y", "5Y" -> 32L
            else -> 3L
        }
        val first = dated.first()
        val last = dated.last()
        if (first.first.isBefore(start.minusDays(tolerance)) ||
            first.first.isAfter(start.plusDays(tolerance)) ||
            last.first.isBefore(today.minusDays(tolerance)) || last.first.isAfter(today)) return null
        return ((last.second - first.second) / first.second * 100.0).takeIf { it.isFinite() }
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
