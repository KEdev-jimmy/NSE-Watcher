package ke.co.nsewatcher.data

import ke.co.nsewatcher.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Temporary read-only bridge to the GitHub-published MyStocks cache.
 *
 * The MyStocks credential never enters the Android app. GitHub Actions refreshes
 * the cache with the repository secret, and the app only reads the public cache.
 * Replace this bridge with the real NSE Watcher backend before production.
 */
object MyStocksCache {
    private const val STOCKS_URL =
        "https://raw.githubusercontent.com/KEdev-jimmy/NSE-Watcher/main/data/mystocks/stocks.json"

    suspend fun loadStocks(): List<Stock> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(STOCKS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching emptyList()
                val outer = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val rawContent = outer.optString("content")
                val payload = JSONObject(if (rawContent.isNotBlank()) rawContent else outer.toString())
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
}
