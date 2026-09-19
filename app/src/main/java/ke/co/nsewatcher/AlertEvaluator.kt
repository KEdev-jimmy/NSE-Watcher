package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import java.util.Locale
import kotlin.math.abs

data class TriggeredAlert(val alertId: String, val symbol: String, val title: String, val message: String)

object AlertEvaluator {
    fun evaluate(alerts: List<PriceAlert>, stocks: List<Stock>, previousPrices: Map<String, Double>): List<TriggeredAlert> {
        val bySymbol = stocks.associateBy { it.symbol.uppercase() }
        return alerts.asSequence().filter { it.enabled }.mapNotNull { alert ->
            val stock = bySymbol[alert.symbol.uppercase()] ?: return@mapNotNull null
            if (!stock.price.isFinite() || stock.price <= 0.0) return@mapNotNull null
            val threshold = alert.threshold ?: return@mapNotNull null
            if (!threshold.isFinite()) return@mapNotNull null
            when (alert.type) {
                AlertType.PRICE_ABOVE -> {
                    val previous = previousPrices[stock.symbol.uppercase()] ?: return@mapNotNull null
                    if (previous < threshold && stock.price >= threshold)
                        TriggeredAlert(alert.id, stock.symbol, "Price alert", stock.name + " crossed above KSh " + "%.2f".format(Locale.US, threshold) + ". Current price: KSh " + "%.2f".format(Locale.US, stock.price) + ".")
                    else null
                }
                AlertType.PRICE_BELOW -> {
                    val previous = previousPrices[stock.symbol.uppercase()] ?: return@mapNotNull null
                    if (previous > threshold && stock.price <= threshold)
                        TriggeredAlert(alert.id, stock.symbol, "Price alert", stock.name + " crossed below KSh " + "%.2f".format(Locale.US, threshold) + ". Current price: KSh " + "%.2f".format(Locale.US, stock.price) + ".")
                    else null
                }
                AlertType.DAILY_GAIN -> if (stock.changeAvailable && stock.change >= threshold)
                    TriggeredAlert(alert.id, stock.symbol, "Daily gain alert", stock.name + " is up " + "%.2f".format(Locale.US, stock.change) + "% today.")
                else null
                AlertType.DAILY_LOSS -> if (stock.changeAvailable && stock.change <= -abs(threshold))
                    TriggeredAlert(alert.id, stock.symbol, "Daily loss alert", stock.name + " is down " + "%.2f".format(Locale.US, abs(stock.change)) + "% today.")
                else null
                AlertType.HIGH_VOLUME, AlertType.BREAKOUT, AlertType.NEWS, AlertType.CORPORATE_ACTION -> null
            }
        }.toList()
    }
}
