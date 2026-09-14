package ke.co.nsewatcher

import ke.co.nsewatcher.domain.Quote
import kotlin.math.roundToInt

data class MarketIndexDemo(
    val name: String,
    val value: Double,
    val change: Double,
    val description: String
)

data class IntelligenceFundamentalDemo(
    val marketCap: String,
    val peRatio: String,
    val pbRatio: String,
    val eps: String,
    val dividendYield: String,
    val roe: String,
    val revenueGrowth: String,
    val profitGrowth: String,
    val debtToEquity: String
)

data class CorporateEventDemo(
    val type: String,
    val title: String,
    val date: String,
    val detail: String
)

data class ScoreBreakdown(
    val total: Int,
    val momentum: Int,
    val volume: Int,
    val trend: Int,
    val liquidity: Int,
    val label: String,
    val reasons: List<String>
)

object DemoIntelligenceData {
    val indices = listOf(
        MarketIndexDemo("NASI", 188.42, 0.74, "NSE All Share Index"),
        MarketIndexDemo("NSE 20", 1765.31, -0.18, "NSE 20 Share Index"),
        MarketIndexDemo("NSE 25", 3981.72, 0.31, "NSE 25 Share Index")
    )

    private val fundamentals = mapOf(
        "SCOM.KE" to IntelligenceFundamentalDemo("KSh 740.0B", "12.8x", "2.1x", "1.46", "4.1%", "16.8%", "8.2%", "11.4%", "0.38x"),
        "EQTY.KE" to IntelligenceFundamentalDemo("KSh 178.0B", "6.9x", "1.2x", "6.84", "7.0%", "17.5%", "9.1%", "12.6%", "0.62x"),
        "KCB.KE" to IntelligenceFundamentalDemo("KSh 126.0B", "4.8x", "0.9x", "8.30", "8.4%", "19.1%", "7.4%", "10.8%", "0.74x"),
        "ABSA.KE" to IntelligenceFundamentalDemo("KSh 71.0B", "5.7x", "0.8x", "2.35", "8.9%", "14.7%", "6.0%", "8.1%", "0.69x"),
        "COOP.KE" to IntelligenceFundamentalDemo("KSh 96.0B", "5.2x", "1.0x", "3.04", "7.8%", "18.0%", "7.9%", "9.8%", "0.71x"),
        "EABL.KE" to IntelligenceFundamentalDemo("KSh 305.0B", "18.4x", "4.2x", "2.10", "2.8%", "23.2%", "6.4%", "5.9%", "1.20x"),
        "KPLC.KE" to IntelligenceFundamentalDemo("KSh 31.0B", "N/A", "N/A", "-0.42", "0.0%", "N/A", "3.1%", "-", "N/A")
    )

    private val events = mapOf(
        "SCOM.KE" to listOf(CorporateEventDemo("Dividend", "Sample final dividend reminder", "Demo date", "Dividend event placeholder for the demo.")),
        "KCB.KE" to listOf(CorporateEventDemo("Results", "Sample full-year results", "Demo date", "Results-calendar placeholder for the demo.")),
        "EQTY.KE" to listOf(CorporateEventDemo("AGM", "Sample annual general meeting", "Demo date", "AGM-calendar placeholder for the demo.")),
        "EABL.KE" to listOf(CorporateEventDemo("Dividend", "Sample dividend event", "Demo date", "Corporate-action placeholder for the demo."))
    )

    fun fundamentals(symbol: String): IntelligenceFundamentalDemo = fundamentals[symbol]
        ?: IntelligenceFundamentalDemo("—", "—", "—", "—", "—", "—", "—", "—", "—")

    fun events(symbol: String): List<CorporateEventDemo> = events[symbol].orEmpty()

    fun score(q: Quote): ScoreBreakdown {
        val momentum = (((q.dailyChange.coerceIn(-5.0, 5.0) + 5.0) / 10.0) * 30).roundToInt()
        val trend = ((((q.weeklyChange + q.monthlyChange).coerceIn(-15.0, 15.0) + 15.0) / 30.0) * 30).roundToInt()
        val volumeRatio = if (q.averageVolume > 0) q.volume.toDouble() / q.averageVolume else 1.0
        val volume = (volumeRatio.coerceIn(0.0, 2.0) / 2.0 * 20).roundToInt()
        val liquidity = if (q.volume >= 1_000_000) 20 else 12
        val total = (momentum + trend + volume + liquidity).coerceIn(0, 100)
        val label = when {
            total >= 80 -> "Strong"
            total >= 65 -> "Positive"
            total >= 45 -> "Neutral"
            total >= 30 -> "Watch"
            else -> "Weak"
        }
        val reasons = buildList {
            if (q.dailyChange > 0) add("Today's price movement is positive in the demo dataset.") else add("Today's price movement is negative in the demo dataset.")
            if (q.weeklyChange + q.monthlyChange > 0) add("Recent weekly/monthly momentum is positive.") else add("Recent weekly/monthly momentum is mixed or negative.")
            if (volumeRatio >= 1.5) add("Trading volume is materially above the demo average.") else add("Trading volume is within a normal demo range.")
            add("Score is an analytical indicator, not a BUY/SELL recommendation.")
        }
        return ScoreBreakdown(total, momentum, volume, trend, liquidity, label, reasons)
    }
}
