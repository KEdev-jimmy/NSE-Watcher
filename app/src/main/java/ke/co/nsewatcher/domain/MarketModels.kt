package ke.co.nsewatcher.domain

import java.time.Instant

enum class Signal { STRONG, POSITIVE, NEUTRAL, WEAK, WATCH }
enum class MarketStatus { OPEN, CLOSED, UNKNOWN }

data class Quote(
    val companyName: String, val symbol: String, val price: Double,
    val dailyChange: Double, val weeklyChange: Double, val monthlyChange: Double,
    val volume: Long, val averageVolume: Long, val dayHigh: Double, val dayLow: Double,
    val signal: Signal, val signalExplanation: String, val history: List<Double>
)
data class MarketSnapshot(val status: MarketStatus, val quotes: List<Quote>, val updatedAt: Instant, val isDemo: Boolean)
data class NewsItem(val id: String, val headline: String, val source: String, val publishedAt: String, val symbols: List<String>, val category: String)
enum class AlertType { PRICE_ABOVE, PRICE_BELOW, DAILY_GAIN, DAILY_LOSS, HIGH_VOLUME, BREAKOUT, NEWS, CORPORATE_ACTION }
data class PriceAlert(val id: String, val symbol: String, val type: AlertType, val threshold: Double?, val enabled: Boolean)

object StockAnalyzer {
    fun percentageChange(current: Double, previous: Double): Double = if (previous == 0.0) 0.0 else ((current - previous) / previous) * 100
    fun signal(daily: Double, weekly: Double, monthly: Double, volume: Long, average: Long): Signal {
        val score = listOf(daily, weekly, monthly).sum() + if (average > 0 && volume >= average * 1.5) 2 else 0
        return when { score >= 8 -> Signal.STRONG; score >= 2 -> Signal.POSITIVE; score <= -7 -> Signal.WEAK; score <= -2 -> Signal.WATCH; else -> Signal.NEUTRAL }
    }
    fun explanation(signal: Signal): String = when (signal) {
        Signal.STRONG -> "Positive momentum with elevated trading interest. This is not investment advice."
        Signal.POSITIVE -> "Price performance is currently positive across recent periods. This is not investment advice."
        Signal.WEAK -> "Recent price performance is weakening. This is not investment advice."
        Signal.WATCH -> "Mixed or soft momentum merits monitoring, not a prediction."
        Signal.NEUTRAL -> "Recent price and volume signals are balanced."
    }
}
