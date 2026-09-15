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
    private const val BACKEND_URL =
        "https://nse-watcher.vercel.app/api/market?action=stocks"
    private const val FALLBACK_URL =
        "https://raw.githubusercontent.com/KEdev-jimmy/NSE-Watcher/main/data/mystocks/stocks.json"

    suspend fun loadStocks(): List<Stock> = withContext(Dispatchers.IO) {
        loadFromUrl(BACKEND_URL).takeIf { it.isNotEmpty() }
            ?: loadFromUrl(FALLBACK_URL)
    }

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
