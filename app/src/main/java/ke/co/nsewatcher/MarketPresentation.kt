package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache.HistoryResult
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class MarketBreadth(val rising: Int, val flat: Int, val falling: Int, val total: Int) {
    val covered get() = rising + flat + falling
}
internal data class MarketSector(val name: String, val breadth: MarketBreadth, val average: Double?)
internal data class MarketPerformance(val stock: Stock, val change: Double, val first: String, val last: String)
internal data class MarketEligibility(val performance: MarketPerformance? = null, val reason: String = "")

internal object MarketPresentation {
    val ranges = listOf("1D", "3D", "1W", "1M", "3M", "6M", "YTD", "1Y", "3Y", "5Y")
    fun validChange(stock: Stock) = stock.price.isFinite() && stock.price > 0 && stock.changeAvailable && stock.change.isFinite()
    fun breadth(stocks: List<Stock>): MarketBreadth {
        val valid = stocks.filter(::validChange)
        return MarketBreadth(valid.count { it.change > 0 }, valid.count { it.change == 0.0 }, valid.count { it.change < 0 }, stocks.size)
    }
    fun sectors(stocks: List<Stock>): List<MarketSector> = stocks.groupBy { CompaniesPresentation.sector(it.sector) }.map { (name, members) ->
        val changes = members.filter(::validChange).map { it.change }
        MarketSector(name, breadth(members), changes.takeIf { it.isNotEmpty() }?.average())
    }.sortedWith(compareBy<MarketSector> { it.average == null }.thenByDescending { it.average ?: 0.0 }.thenBy { it.name })
    fun summary(b: MarketBreadth) = when {
        b.covered == 0 -> "Daily movement is unavailable"
        b.rising > b.falling -> "More companies are rising than falling"
        b.falling > b.rising -> "More companies are falling than rising"
        b.rising == 0 -> "Available daily changes are unchanged"
        else -> "Rising and falling companies are balanced"
    }
    fun date(raw: String): LocalDate? = CompanyResearchPresentation.timestamp(raw)?.atZone(CompanyResearchPresentation.zone)?.toLocalDate()
    fun start(range: String, end: LocalDate): LocalDate = when (range) {
        "3D" -> end.minusDays(3); "1W" -> end.minusWeeks(1); "1M" -> end.minusMonths(1)
        "3M" -> end.minusMonths(3); "6M" -> end.minusMonths(6); "YTD" -> end.withDayOfYear(1).minusDays(1)
        "1Y" -> end.minusYears(1); "3Y" -> end.minusYears(3); "5Y" -> end.minusYears(5)
        else -> end
    }
    fun historyPeriod(range: String) = if (range == "YTD") "1y" else range.lowercase(java.util.Locale.US)
    fun endpointTolerance(range: String): Long = when (range) { "3D" -> 1; "1Y", "YTD" -> 10; "3Y", "5Y" -> 35; else -> 4 }
    fun eligible(stock: Stock, range: String, result: HistoryResult, end: LocalDate): MarketEligibility {
        if (range == "1D") {
            if (!validChange(stock)) return MarketEligibility(reason = "Daily change unavailable")
            val observed = date(stock.observedAt) ?: return MarketEligibility(reason = "Quote date unavailable")
            if (observed > end || observed < end.minusDays(4)) return MarketEligibility(reason = "Quote outside recent session window")
            return MarketEligibility(MarketPerformance(stock, stock.change, stock.observedAt, stock.observedAt))
        }
        val start = start(range, end)
        val tolerance = endpointTolerance(range)
        val valid = WatchlistPresentation.trend(result.points).filter { date(it.date)!! <= end }
        val points = if (range == "YTD") {
            val baseline = valid.lastOrNull { date(it.date)!! <= start && date(it.date)!! >= start.minusDays(tolerance) }
                ?: return MarketEligibility(reason = "Year-end baseline unavailable")
            listOf(baseline) + valid.filter { date(it.date)!! > start }
        } else valid.filter { date(it.date)!! >= start }
        if (points.size < 2) return MarketEligibility(reason = "Fewer than two dated observations")
        val firstDate = date(points.first().date)!!
        val lastDate = date(points.last().date)!!
        if (range != "YTD" && firstDate > start.plusDays(tolerance)) return MarketEligibility(reason = "History does not reach period start")
        if (lastDate < end.minusDays(if (range == "3D") 3 else tolerance)) return MarketEligibility(reason = "History does not reach period end")
        if (firstDate >= lastDate) return MarketEligibility(reason = "No dated price interval")
        val maxGap = when (range) { "1Y", "YTD" -> 21; "3Y", "5Y" -> 75; else -> 9 }
        if (points.zipWithNext().any { (a, b) -> ChronoUnit.DAYS.between(date(a.date), date(b.date)) > maxGap }) return MarketEligibility(reason = "Large gaps in returned history")
        val change = (points.last().close / points.first().close - 1.0) * 100
        if (!change.isFinite()) return MarketEligibility(reason = "Invalid price calculation")
        return MarketEligibility(MarketPerformance(stock, change, points.first().date, points.last().date))
    }
    fun ranked(values: List<MarketPerformance>, filter: String): List<MarketPerformance> = when (filter) {
        "Losers" -> values.filter { it.change < 0 }.sortedBy { it.change }
        "Gainers" -> values.filter { it.change > 0 }.sortedByDescending { it.change }
        else -> values.sortedByDescending { it.change }
    }
}

internal data class MarketSavedObservation(val observedAt: String, val checkedAt: String, val source: String,
    val rising: Int, val flat: Int, val falling: Int, val total: Int)

internal fun mergeMarketObservations(old: List<MarketSavedObservation>, incoming: MarketSavedObservation): List<MarketSavedObservation> =
    (listOf(incoming) + old).filter { MarketPresentation.date(it.observedAt) != null }
        .sortedByDescending { CompanyResearchPresentation.timestamp(it.observedAt) }
        .distinctBy { MarketPresentation.date(it.observedAt) }.take(90)
