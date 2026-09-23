package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertEvent
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

data class AlertQuote(val price: Double, val observedAt: String)
data class AlertMonitorState(
    val quotes: Map<String, AlertQuote> = emptyMap(),
    val processed: Map<String, Long> = emptyMap()
)
data class TriggeredAlert(
    val alertId: String, val symbol: String, val title: String, val message: String,
    val eventId: String, val observedAt: String,
    val articleId: String = "", val articleTitle: String = "", val source: String = "", val sourceUrl: String = ""
) {
    fun event(now: Instant) = AlertEvent(eventId, alertId, symbol, title, message, now.toString(), observedAt,
        articleId, articleTitle, source, sourceUrl)
}

object AlertEvaluator {
    private val zone = ZoneId.of("Africa/Nairobi")
    private val newsWindow = Duration.ofDays(7)
    private val quoteWindow = Duration.ofMinutes(30)
    // Length prefixes prevent IDs containing separators from colliding.
    fun eventKey(vararg parts: String): String = parts.joinToString("") { "${it.length}:$it" }
    fun isNews(type: AlertType) = type == AlertType.NEWS || type == AlertType.CORPORATE_ACTION
    private fun instant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()
    private fun newsTime(raw: String): Instant? = instant(raw) ?: runCatching {
        LocalDate.parse(raw).atStartOfDay(zone).toInstant()
    }.getOrNull()

    fun eligibleQuote(stock: Stock, now: Instant): Boolean {
        if (!stock.price.isFinite() || stock.price <= 0 || stock.freshnessMode.equals("STALE", true)) return false
        val time = instant(stock.observedAt) ?: return false
        return !time.isAfter(now) && Duration.between(time, now) <= quoteWindow &&
            time.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    }

    fun nextQuotes(previous: Map<String, AlertQuote>, stocks: List<Stock>, now: Instant): Map<String, AlertQuote> {
        val result = previous.filterValues { q -> instant(q.observedAt)?.let {
            !it.isAfter(now) && Duration.between(it, now) <= Duration.ofHours(1)
        } == true }.toMutableMap()
        stocks.filter { eligibleQuote(it, now) }.forEach { stock ->
            val symbol = stock.symbol.trim().uppercase(Locale.ROOT)
            val old = result[symbol]?.let { instant(it.observedAt) }
            if (old == null || instant(stock.observedAt)!!.isAfter(old)) result[symbol] = AlertQuote(stock.price, stock.observedAt)
        }
        return result
    }

    fun evaluate(
        alerts: List<PriceAlert>, stocks: List<Stock>, state: AlertMonitorState,
        news: List<NewsItem> = emptyList(), now: Instant = Instant.now(), marketOpen: Boolean = false,
        legacyNewsIds: Map<String, String> = emptyMap(), legacyDailyDates: Map<String, String> = emptyMap()
    ): List<TriggeredAlert> {
        val bySymbol = stocks.associateBy { it.symbol.trim().uppercase(Locale.ROOT) }
        val today = now.atZone(zone).toLocalDate().toString()
        return alerts.filter { it.enabled }.flatMap { alert ->
            val symbol = alert.symbol.trim().uppercase(Locale.ROOT)
            if (isNews(alert.type)) {
                // News remains useful after hours, across weekends and without a quote.
                news.asSequence().filter { it.symbol.trim().equals(symbol, true) && it.id.isNotBlank() }
                    .filter { item ->
                        val corporate = item.category.equals("Corporate Actions", true) || item.category.equals("Dividends", true)
                        when (alert.type) {
                            AlertType.NEWS -> !corporate
                            AlertType.CORPORATE_ACTION -> corporate
                            else -> false
                        }
                    }
                    .filter { item -> newsTime(item.publishedAt)?.let { !it.isAfter(now) && Duration.between(it, now) <= newsWindow } == true }
                    .filter { it.id != legacyNewsIds[alert.id] }
                    .distinctBy { it.id }.sortedByDescending { newsTime(it.publishedAt) }
                    .map { item -> TriggeredAlert(alert.id, symbol,
                        if (alert.type == AlertType.CORPORATE_ACTION) "Corporate action" else "New company news",
                        "${item.companyName.ifBlank { symbol }}: ${item.title}",
                        eventKey(alert.id, "news", item.id), item.publishedAt, item.id, item.title, item.source, item.url) }
                    .toList()
            } else {
                val stock = bySymbol[symbol]
                val threshold = alert.threshold
                if (!marketOpen || stock == null || !eligibleQuote(stock, now) || threshold == null || !threshold.isFinite()) return@flatMap emptyList()
                val observed = instant(stock.observedAt)!!
                val previous = state.quotes[symbol]
                val previousTime = previous?.let { instant(it.observedAt) }
                val crossingReady = previous != null && previous.price.isFinite() && previous.price > 0 && previousTime != null &&
                    previousTime.isBefore(observed) && Duration.between(previousTime, observed) <= quoteWindow &&
                    previousTime.atZone(zone).toLocalDate() == observed.atZone(zone).toLocalDate()
                // Daily and volume conditions notify once per rule per Nairobi day.
                val daily = alert.type in setOf(AlertType.DAILY_GAIN, AlertType.DAILY_LOSS, AlertType.HIGH_VOLUME)
                if (daily && legacyDailyDates[alert.id] == today) return@flatMap emptyList()
                val id = eventKey(alert.id, alert.type.name, if (daily) today else stock.observedAt)
                fun trigger(title: String, message: String) = listOf(TriggeredAlert(alert.id, symbol, title, message, id, stock.observedAt, source = stock.source))
                fun amount(value: Double) = "%.2f".format(Locale.US, value)
                when (alert.type) {
                    AlertType.PRICE_ABOVE -> if (threshold > 0 && crossingReady && previous!!.price < threshold && stock.price >= threshold)
                        trigger("Price alert", "${stock.name} crossed above KSh ${amount(threshold)}. Observed price: KSh ${amount(stock.price)}.") else emptyList()
                    AlertType.PRICE_BELOW -> if (threshold > 0 && crossingReady && previous!!.price > threshold && stock.price <= threshold)
                        trigger("Price alert", "${stock.name} crossed below KSh ${amount(threshold)}. Observed price: KSh ${amount(stock.price)}.") else emptyList()
                    AlertType.DAILY_GAIN -> if (threshold > 0 && stock.changeAvailable && stock.change.isFinite() && stock.change >= threshold)
                        trigger("Daily gain alert", "${stock.name} is up ${amount(stock.change)}% in the latest eligible observation.") else emptyList()
                    AlertType.DAILY_LOSS -> if (threshold > 0 && stock.changeAvailable && stock.change.isFinite() && stock.change <= -abs(threshold))
                        trigger("Daily loss alert", "${stock.name} is down ${amount(abs(stock.change))}% in the latest eligible observation.") else emptyList()
                    AlertType.HIGH_VOLUME -> if (threshold >= 0 && stock.volumeAvailable && stock.volume >= 0 && stock.averageVolumeAvailable && stock.averageVolume > 0 &&
                        stock.volume.toDouble() >= stock.averageVolume.toDouble() * (1 + threshold / 100))
                        trigger("High volume alert", "${stock.name} volume is at least ${amount(threshold)}% above the provider average. Observed volume: ${stock.volume}. Average: ${stock.averageVolume}.") else emptyList()
                    else -> emptyList()
                }
            }
        }.filter { it.eventId !in state.processed }.distinctBy { it.eventId }
    }
}
