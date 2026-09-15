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

    suspend fun loadStocks(): List<Stock> = withContext(Dispatchers.IO) {
        val base = loadFromUrl(BACKEND_STOCKS_URL).takeIf { it.isNotEmpty() }
            ?: loadFromUrl(FALLBACK_URL)
        if (base.isEmpty()) return@withContext emptyList()

        // Keep the first live slice deliberately small: the dashboard/company
        // experience gets real historical data without firing dozens of API calls.
        // The same chart endpoint will be used for lazy per-company loading next.
        base.map { stock ->
            if (stock.symbol in chartSymbols) {
                val history = loadHistory(stock.symbol, "1y")
                if (history.size >= 2) stock.copy(history = history) else stock
            } else stock
        }
    }

    suspend fun loadHistory(symbol: String, period: String = "1y"): List<Double> = withContext(Dispatchers.IO) {
        val qualified = if (symbol.contains('.')) symbol else "$symbol.KE"
        loadHistoryFromUrl("$BACKEND_CHART_URL&symbol=$qualified&period=$period")
    }

    private fun loadHistoryFromUrl(url: String): List<Double> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching emptyList()
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val data = root.optJSONObject("data") ?: return@runCatching emptyList()
            val candles = data.optJSONArray("candles") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until candles.length()) {
                    val candle = candles.optJSONObject(i) ?: continue
                    val close = candle.optDouble("close", Double.NaN)
                    if (close.isFinite() && close > 0.0) add(close)
                }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())

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

            // Backend response: { data: { stocks: [...] } }.
            // GitHub fallback response: { content: "{ stocks: [...] }" }.
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
                    val previousClose = item.optDouble("previousClose", price)
                    val changePct = item.optDouble("changePct", 0.0) * 100.0
                    add(
                        Stock(
                            symbol = symbol,
                            name = name,
                            price = price,
                            change = changePct,
                            history = listOf(previousClose, price)
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())
}
