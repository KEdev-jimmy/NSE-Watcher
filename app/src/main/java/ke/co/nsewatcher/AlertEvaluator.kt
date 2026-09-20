package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

data class TriggeredAlert(val alertId: String, val symbol: String, val title: String, val message: String)

object AlertEvaluator {
    fun evaluate(alerts: List<PriceAlert>, stocks: List<Stock>, previousPrices: Map<String, Double>, news: List<NewsItem> = emptyList(), lastNewsTriggerIds: Map<String, String> = emptyMap()): List<TriggeredAlert> {
        val bySymbol = stocks.associateBy { it.symbol.uppercase() }
        val today = LocalDate.now(ZoneId.of("Africa/Nairobi"))
        fun isCurrentDay(item: NewsItem): Boolean = runCatching {
            Instant.parse(item.publishedAt).atZone(ZoneId.of("Africa/Nairobi")).toLocalDate() == today
        }.getOrElse {
            runCatching { LocalDate.parse(item.publishedAt.take(10)) == today }.getOrDefault(false)
        }
        return alerts.asSequence().filter { it.enabled }.mapNotNull { alert ->
            val stock = bySymbol[alert.symbol.uppercase()] ?: return@mapNotNull null
            if (!stock.price.isFinite() || stock.price <= 0.0) return@mapNotNull null
            val threshold = alert.threshold
            if (threshold != null && !threshold.isFinite()) return@mapNotNull null
            when (alert.type) {
                AlertType.PRICE_ABOVE -> {
                    val thresholdValue = threshold ?: return@mapNotNull null
                    val previous = previousPrices[stock.symbol.uppercase()] ?: return@mapNotNull null
                    if (previous < thresholdValue && stock.price >= thresholdValue)
                        TriggeredAlert(alert.id, stock.symbol, "Price alert", stock.name + " crossed above KSh " + "%.2f".format(Locale.US, thresholdValue) + ". Current price: KSh " + "%.2f".format(Locale.US, stock.price) + ".")
                    else null
                }
                AlertType.PRICE_BELOW -> {
                    val thresholdValue = threshold ?: return@mapNotNull null
                    val previous = previousPrices[stock.symbol.uppercase()] ?: return@mapNotNull null
                    if (previous > thresholdValue && stock.price <= thresholdValue)
                        TriggeredAlert(alert.id, stock.symbol, "Price alert", stock.name + " crossed below KSh " + "%.2f".format(Locale.US, thresholdValue) + ". Current price: KSh " + "%.2f".format(Locale.US, stock.price) + ".")
                    else null
                }
                AlertType.DAILY_GAIN -> if (threshold != null && stock.changeAvailable && stock.change >= threshold)
                    TriggeredAlert(alert.id, stock.symbol, "Daily gain alert", stock.name + " is up " + "%.2f".format(Locale.US, stock.change) + "% today.")
                else null
                AlertType.DAILY_LOSS -> if (threshold != null && stock.changeAvailable && stock.change <= -abs(threshold))
                    TriggeredAlert(alert.id, stock.symbol, "Daily loss alert", stock.name + " is down " + "%.2f".format(Locale.US, abs(stock.change)) + "% today.")
                else null
                AlertType.HIGH_VOLUME -> {
                    val thresholdValue = threshold ?: return@mapNotNull null
                    if (thresholdValue < 0.0 || !stock.volumeAvailable || !stock.averageVolumeAvailable || stock.averageVolume <= 0L) {
                        null
                    } else if (stock.volume.toDouble() >= stock.averageVolume.toDouble() * (1.0 + thresholdValue / 100.0)) {
                        TriggeredAlert(
                            alert.id,
                            stock.symbol,
                            "High volume alert",
                            stock.name + " volume is " + "%.0f".format(Locale.US, thresholdValue) + "% above the provider-supplied average. Current volume: " + stock.volume + ". Average volume: " + stock.averageVolume + "."
                        )
                    } else null
                }
                AlertType.BREAKOUT -> null
                AlertType.NEWS, AlertType.CORPORATE_ACTION -> {
                    val alreadySent = lastNewsTriggerIds[alert.id]
                    news.asSequence()
                        .filter { it.symbol.trim().uppercase() == stock.symbol.uppercase() }
                        .filter(::isCurrentDay)
                        .filter { item -> alert.type == AlertType.NEWS || item.category.equals("Corporate Actions", true) || item.category.equals("Dividends", true) }
                        .filter { it.id.isNotBlank() && it.id != alreadySent }
                        .sortedByDescending { it.publishedAt }
                        .firstOrNull()
                        ?.let { item ->
                            val label = if (alert.type == AlertType.CORPORATE_ACTION) "Corporate action" else "New company news"
                            TriggeredAlert(alert.id, stock.symbol, label, stock.name + ": " + item.title)
                        }
                }
            }
        }.toList()
    }
}
