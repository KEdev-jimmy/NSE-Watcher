package ke.co.nsewatcher.data

import ke.co.nsewatcher.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only market-data bridge.
 *
 * The MyStocks credential stays on the Vercel backend. The Android app calls
 * the backend and keeps the GitHub cache as a development fallback.
 */
object MyStocksCache {
    private const val BACKEND_STOCKS_URL =
        "https://nse-watcher.vercel.app/api/market?action=stocks"
    private const val BACKEND_CHART_URL =
        "https://nse-watcher.vercel.app/api/market?action=chart"
    private const val FALLBACK_URL =
        "https://raw.githubusercontent.com/KEdev-jimmy/NSE-Watcher/main/data/mystocks/stocks.json"

    private val chartSymbols = setOf("SCOM", "KCB", "EQTY", "ABSA", "COOP", "EABL", "KPLC")

    data class HistoryResult(
        val prices: List<Double> = emptyList(),
        val firstDate: String = "",
        val lastDate: String = "",
        val interval: String = "",
        val observedAt: String = ""
    )

    suspend fun loadStocks(): List<Stock> = withContext(Dispatchers.IO) {
        val base = loadFromUrl(BACKEND_STOCKS_URL).takeIf { it.isNotEmpty() }
            ?: loadFromUrl(FALLBACK_URL)
        if (base.isEmpty()) return@withContext emptyList()

        base.map { stock ->
            if (stock.symbol in chartSymbols) {
                val history = loadHistory(stock.symbol, "1D")
                if (history.size >= 2) {
                    // Use the same 1D observation series shown in Company
                    // Intelligence. The first point is the previous close and
                    // the final point is the latest delayed observation.
                    val latest = history.last()
                    val previousClose = history.first()
                    val dayChange = if (previousClose > 0.0) {
                        ((latest - previousClose) / previousClose) * 100.0
                    } else stock.change
                    stock.copy(
                        price = latest,
                        change = dayChange,
                        history = history
                    )
                } else stock
            } else stock
        }
    }

    suspend fun loadHistory(symbol: String, period: String = "1y"): List<Double> =
        loadHistoryDetails(symbol, period).prices

    suspend fun loadHistoryDetails(symbol: String, period: String = "1y"): HistoryResult = withContext(Dispatchers.IO) {
        val qualified = if (symbol.contains('.')) symbol else "$symbol.KE"
        loadHistoryFromUrl("$BACKEND_CHART_URL&symbol=$qualified&period=$period")
    }

    private fun loadHistoryFromUrl(url: String): HistoryResult = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching HistoryResult()
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val data = root.optJSONObject("data") ?: return@runCatching HistoryResult()
            val candles = data.optJSONArray("candles") ?: return@runCatching HistoryResult()
            val prices = buildList {
                for (i in 0 until candles.length()) {
                    val candle = candles.optJSONObject(i) ?: continue
                    val close = candle.optDouble("close", Double.NaN)
                    if (close.isFinite() && close > 0.0) add(close)
                }
            }
            val firstDate = if (candles.length() > 0) {
                candles.optJSONObject(0)?.optString("date", "")?.ifBlank {
                    candles.optJSONObject(0)?.optString("timestamp", "") ?: ""
                } ?: ""
            } else ""
            val lastDate = if (candles.length() > 0) {
                candles.optJSONObject(candles.length() - 1)?.optString("date", "")?.ifBlank {
                    candles.optJSONObject(candles.length() - 1)?.optString("timestamp", "") ?: ""
                } ?: ""
            } else ""
            val observedAt = root.optString("asOf", "")
                .ifBlank { data.optString("asOf", "") }
                .ifBlank { data.optJSONObject("meta")?.optString("asOf", "") ?: "" }
            HistoryResult(
                prices = prices,
                firstDate = firstDate,
                lastDate = lastDate,
                interval = root.optString("interval", data.optString("interval", "")),
                observedAt = observedAt
            )
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(HistoryResult())

    private fun loadFromUrl(url: String): List<Stock> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching emptyList()
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })

            val dataObject = root.optJSONObject("data")
            val rawContent = root.optString("content")
            val payload = when {
                dataObject != null -> dataObject
                rawContent.isNotBlank() -> JSONObject(rawContent)
                else -> root
            }
            val array = payload.optJSONArray("stocks") ?: return@runCatching emptyList()

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val qualified = item.optString("symbol")
                    if (qualified.isBlank()) continue
                    val symbol = qualified.substringBefore('.')
                    val name = item.optString("name", symbol)
                    val price = item.optDouble("price", Double.NaN)
                    if (!price.isFinite()) continue

                    val previousClose = item.optDouble("previousClose", Double.NaN)
                    val suppliedChange = item.optDouble("change", Double.NaN)
                    val suppliedChangePct = item.optDouble("changePct", Double.NaN)
                    val derivedChangePct = if (previousClose.isFinite() && previousClose > 0.0 && price.isFinite()) {
                        ((price - previousClose) / previousClose) * 100.0
                    } else Double.NaN
                    val changePct = when {
                        suppliedChange.isFinite() && suppliedChange != 0.0 -> suppliedChange
                        suppliedChangePct.isFinite() && suppliedChangePct != 0.0 -> suppliedChangePct
                        derivedChangePct.isFinite() -> derivedChangePct
                        else -> 0.0
                    }
                    val historyStart = if (previousClose.isFinite() && previousClose > 0.0) previousClose else price

                    add(
                        Stock(
                            symbol = symbol,
                            name = name,
                            price = price,
                            change = changePct,
                            history = listOf(historyStart, price),
                            logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() },
                            sector = item.optString("sector", "Other").ifBlank { "Other" },
                            volume = item.optLong("volume", 0L).coerceAtLeast(0L)
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())
}
