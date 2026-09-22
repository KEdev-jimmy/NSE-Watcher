package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Presentation rules shared by the header, session card and explanation. */
internal object CompanyResearchPresentation {
    val zone: ZoneId = ZoneId.of("Africa/Nairobi")
    val ranges = listOf("1D", "3D", "1W", "1M", "3M", "6M", "1Y", "3Y", "5Y")

    data class Session(
        val latest: Double?, val previousClose: Double?, val open: Double?,
        val high: Double?, val low: Double?, val dailyChange: Double?,
        val observedAt: String, val source: String
    ) {
        val sinceOpen: Double? get() = if (open != null && latest != null && open > 0.0)
            ((latest - open) / open * 100.0).takeIf { it.isFinite() } else null
    }

    fun timestamp(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { LocalDate.parse(raw).atStartOfDay(zone).toInstant() }.getOrNull()

    fun session(stock: Stock, history: MyStocksCache.HistoryResult): Session {
        fun positive(v: Double?) = v?.takeIf { it.isFinite() && it > 0.0 }
        val points = history.points.filter { positive(it.close) != null && timestamp(it.date) != null }.sortedBy { timestamp(it.date) }
        val historyPrice = positive(history.sessionClose) ?: points.lastOrNull()?.close
        val historyAt = history.sessionCloseAt.ifBlank { history.observedAt }.ifBlank { points.lastOrNull()?.date.orEmpty() }
        val quotePrice = positive(stock.price)
        val quoteTime = timestamp(stock.observedAt)
        val historyTime = timestamp(historyAt)
        val useQuote = quotePrice != null && (historyPrice == null ||
            (quoteTime != null && (historyTime == null || !quoteTime.isBefore(historyTime))))
        val at = if (useQuote) stock.observedAt else historyAt
        val date = CompanyChartAccuracy.observationDate(at)
        val sameHistory = date != null && date == CompanyChartAccuracy.observationDate(historyAt)
        val sameQuote = date != null && date == CompanyChartAccuracy.observationDate(stock.observedAt)
        val quoteChange = stock.change.takeIf { quotePrice != null && stock.changeAvailable && it.isFinite() && sameQuote }
        val historyChange = history.dailyChangePct?.takeIf { it.isFinite() && sameHistory }
        val sessionPoints = points.filter { CompanyChartAccuracy.observationDate(it.date) == date }
        val ohlc = CompanyChartAccuracy.observedOhlc(sessionPoints)
        return Session(
            latest = if (useQuote) quotePrice else historyPrice,
            previousClose = positive(history.previousSessionClose).takeIf { sameHistory }
                ?: positive(stock.previousClose).takeIf { sameQuote },
            open = ohlc.open, high = ohlc.high, low = ohlc.low,
            // A missing change cannot be borrowed from an earlier quote merely
            // because it belongs to the same day: it describes a different price.
            dailyChange = if (useQuote) quoteChange ?: historyChange.takeIf { quotePrice == historyPrice && quoteTime == historyTime }
                else historyChange ?: quoteChange.takeIf { quotePrice == historyPrice && quoteTime == historyTime },
            observedAt = at,
            source = if (useQuote) stock.source.ifBlank { "Source unavailable" } else if (historyPrice != null) "MyStocks Africa" else "Source unavailable"
        )
    }

    fun meaning(change: Double?): String = when {
        change == null || !change.isFinite() -> "A reliable daily change is unavailable, so the session direction cannot be established."
        change > 0.0 -> "Price rose ${percent(abs(change), signed = false)} compared with the previous close."
        change < 0.0 -> "Price fell ${percent(abs(change), signed = false)} compared with the previous close."
        else -> "Price was unchanged compared with the previous close."
    }

    fun money(value: Double?): String = value?.takeIf { it.isFinite() && it > 0.0 }
        ?.let { String.format(Locale.US, "KSh %,.2f", it) } ?: "Unavailable"

    fun percent(value: Double, signed: Boolean = true): String =
        String.format(Locale.US, if (signed) "%+.2f%%" else "%.2f%%", value)

    fun date(raw: String): String {
        if (raw.isBlank()) return "Date unavailable"
        return timestamp(raw)?.atZone(zone)?.format(DateTimeFormatter.ofPattern(
            if (raw.length > 10) "dd MMM yyyy, HH:mm 'EAT'" else "dd MMM yyyy", Locale.US
        )) ?: raw
    }

    fun sourceUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim())
        raw.trim().takeIf { uri.scheme?.lowercase(Locale.US) in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null }
    }.getOrNull()

    fun financialValue(raw: String, unit: String): String {
        val clean = raw.trim()
        if (clean.isBlank() || clean == "-" || clean.equals("n/a", true)) return "Unavailable"
        val number = clean.replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() } ?: return clean
        return if (unit.equals("Millions KES", true)) {
            if (abs(number) >= 1000) String.format(Locale.US, "KSh %,.2fB", number / 1000)
            else String.format(Locale.US, "KSh %,.2fM", number)
        } else clean
    }
}
