package ke.co.nsewatcher.data

import ke.co.nsewatcher.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object MyStocksCache {
    private const val BACKEND_STOCKS_URL = "https://nse-watcher.vercel.app/api/market?action=stocks"
    private const val BACKEND_STATUS_URL = "https://nse-watcher.vercel.app/api/market?action=status"
    private const val BACKEND_INDICES_URL = "https://nse-watcher.vercel.app/api/market?action=indices"
    private const val BACKEND_CHART_URL = "https://nse-watcher.vercel.app/api/market?action=chart"
    private const val FALLBACK_URL = "https://raw.githubusercontent.com/KEdev-jimmy/NSE-Watcher/main/data/mystocks/stocks.json"

    private val chartSymbols = setOf("SCOM", "KCB", "EQTY", "ABSA", "COOP", "EABL", "KPLC")

    data class HistoryPoint(
        val close: Double,
        val date: String = ""
    )

    data class HistoryResult(
        val prices: List<Double> = emptyList(),
        val points: List<HistoryPoint> = emptyList(),
        val firstDate: String = "",
        val lastDate: String = "",
        val interval: String = "",
        val observedAt: String = "",
        val sessionOpen: Double? = null,
        val sessionClose: Double? = null,
        val sessionChangePct: Double? = null,
        val sessionOpenAt: String = "",
        val sessionCloseAt: String = ""
    )

    data class MarketIndex(
        val symbol: String,
        val name: String,
        val value: Double,
        val changePct: Double?,
        val asOf: String = "",
        val freshnessMode: String = "UNKNOWN"
    )

    suspend fun loadMarketIndices(marketOpen: Boolean = false): List<MarketIndex> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(BACKEND_INDICES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching emptyList()
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val array = root.optJSONArray("indices") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val symbol = item.optString("symbol", "").trim()
                        val name = item.optString("name", symbol).trim().ifBlank { symbol }
                        val value = item.optDouble("value", Double.NaN)
                        if (symbol.isBlank() || !value.isFinite()) continue
                        val change = item.optDouble("changePct", Double.NaN)
                        val asOf = item.optString("asOf", "")
                        add(MarketIndex(symbol, name, value, change.takeIf { it.isFinite() }, asOf, indexFreshnessMode(asOf, marketOpen)))
                    }
                }
            } finally { connection.disconnect() }
        }.getOrDefault(emptyList())
    }

    data class MarketStatus(
        val isOpen: Boolean = false,
        val status: String = "CLOSED",
        val nextOpen: String = "",
        val nextClose: String = ""
    )

    suspend fun loadMarketStatus(): MarketStatus = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(BACKEND_STATUS_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching MarketStatus()
                val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val status = root.optString("status", "").ifBlank { "CLOSED" }
                MarketStatus(
                    isOpen = root.optBoolean("isOpen", status.equals("OPEN", ignoreCase = true)),
                    status = status,
                    nextOpen = root.optString("nextOpen", ""),
                    nextClose = root.optString("nextClose", "")
                )
            } finally { connection.disconnect() }
        }.getOrDefault(MarketStatus())
    }

    suspend fun loadStocks(): List<Stock> = withContext(Dispatchers.IO) {
        val backend = loadFromUrl(BACKEND_STOCKS_URL, "backend")
        val base = backend.takeIf { it.isNotEmpty() } ?: loadFromUrl(FALLBACK_URL, "fallback")
        if (base.isEmpty()) return@withContext emptyList()
        base.map { stock ->
            if (stock.symbol in chartSymbols) {
                val history = loadHistoryDetails(stock.symbol, "1D")
                if (history.prices.size >= 2) {
                    val latest = history.prices.last()
                    val previousClose = history.prices.first()
                    val dayChange = if (previousClose > 0.0) ((latest - previousClose) / previousClose) * 100.0 else stock.change
                    stock.copy(price = latest, change = dayChange, history = history.prices)
                } else stock
            } else stock
        }
    }

    suspend fun loadHistory(symbol: String, period: String = "1y"): List<Double> = loadHistoryDetails(symbol, period).prices

    suspend fun loadHistoryDetails(symbol: String, period: String = "1y"): HistoryResult = withContext(Dispatchers.IO) {
        val qualified = if (symbol.contains('.')) symbol else "$symbol.KE"
        loadHistoryFromUrl("$BACKEND_CHART_URL&symbol=$qualified&period=$period")
    }

    private fun loadHistoryFromUrl(url: String): HistoryResult = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching HistoryResult()
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val data = root.optJSONObject("data") ?: return@runCatching HistoryResult()
            val candles = data.optJSONArray("candles") ?: return@runCatching HistoryResult()
            val points = buildList {
                for (i in 0 until candles.length()) {
                    val candle = candles.optJSONObject(i) ?: continue
                    val close = candle.optDouble("close", Double.NaN)
                    if (close.isFinite() && close > 0.0) {
                        val date = candle.optString("date", "").ifBlank { candle.optString("timestamp", "") }
                        add(HistoryPoint(close, date))
                    }
                }
            }
            val prices = points.map { it.close }
            val first = candles.optJSONObject(0)
            val last = candles.optJSONObject(candles.length() - 1)
            val firstDate = first?.optString("date", "")?.ifBlank { first.optString("timestamp", "") } ?: ""
            val lastDate = last?.optString("date", "")?.ifBlank { last.optString("timestamp", "") } ?: ""
            val session = root.optJSONObject("session")
            HistoryResult(
                prices = prices,
                points = points,
                firstDate = firstDate,
                lastDate = lastDate,
                interval = root.optString("interval", data.optString("interval", "")),
                observedAt = root.optString("latestObservationAt", "").ifBlank { root.optString("asOf", "") }.ifBlank { data.optString("asOf", "") },
                sessionOpen = session?.optDouble("open", Double.NaN)?.takeIf { it.isFinite() },
                sessionClose = session?.optDouble("close", Double.NaN)?.takeIf { it.isFinite() },
                sessionChangePct = session?.optDouble("changePct", Double.NaN)?.takeIf { it.isFinite() },
                sessionOpenAt = session?.optString("openAt", "") ?: "",
                sessionCloseAt = session?.optString("closeAt", "") ?: ""
            )
        } finally { connection.disconnect() }
    }.getOrDefault(HistoryResult())

    private fun indexFreshnessMode(asOf: String, marketOpen: Boolean): String {
        if (asOf.isBlank()) return "UNKNOWN"
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Africa/Nairobi"))
        if (asOf.length == 10) {
            val date = runCatching { java.time.LocalDate.parse(asOf) }.getOrNull() ?: return "UNKNOWN"
            return when {
                date.isBefore(now.toLocalDate()) -> "STALE"
                !marketOpen -> "END_OF_DAY"
                else -> "UNKNOWN"
            }
        }
        return runCatching {
            val observed = java.time.Instant.parse(asOf).atZone(java.time.ZoneId.of("Africa/Nairobi"))
            when {
                observed.toLocalDate().isBefore(now.toLocalDate()) -> "STALE"
                observed.toLocalDate() == now.toLocalDate() && marketOpen -> "CURRENT_SESSION"
                else -> "UNKNOWN"
            }
        }.getOrDefault("UNKNOWN")
    }

    private fun loadFromUrl(url: String, dataOrigin: String): List<Stock> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 8_000; readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching emptyList()
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val dataObject = root.optJSONObject("data")
            val rawContent = root.optString("content")
            val payload = when { dataObject != null -> dataObject; rawContent.isNotBlank() -> JSONObject(rawContent); else -> root }
            val array = payload.optJSONArray("stocks") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val qualified = item.optString("symbol"); if (qualified.isBlank()) continue
                    val symbol = qualified.substringBefore('.')
                    val name = item.optString("name", symbol)
                    val price = item.optDouble("price", Double.NaN); if (!price.isFinite()) continue
                    val previousClose = item.optDouble("previousClose", Double.NaN)
                    val suppliedChange = item.optDouble("change", Double.NaN)
                    val suppliedChangePct = item.optDouble("changePct", Double.NaN)
                    val derived = if (previousClose.isFinite() && previousClose > 0.0) ((price - previousClose) / previousClose) * 100.0 else Double.NaN
                    val changePct = when {
                        suppliedChange.isFinite() && suppliedChange != 0.0 -> suppliedChange
                        suppliedChangePct.isFinite() && suppliedChangePct != 0.0 -> suppliedChangePct
                        derived.isFinite() -> derived
                        else -> 0.0
                    }
                    val changeAvailable = suppliedChange.isFinite() || suppliedChangePct.isFinite() || derived.isFinite()
                    val volumeValue = item.optDouble("volume", Double.NaN)
                    val volumeAvailable = volumeValue.isFinite() && volumeValue >= 0.0
                    val volume = if (volumeAvailable) volumeValue.toLong() else 0L
                    val source = item.optString("source", "").trim().ifBlank { if (dataOrigin == "backend") "MyStocks Africa" else "NSE Watcher fallback catalogue" }
                    val observedAt = item.optString("lastPriceUpdate", "").trim().ifBlank { item.optString("asOf", "").trim() }
                    val freshnessMode = stockFreshnessMode(observedAt)
                    val historyStart = if (previousClose.isFinite() && previousClose > 0.0) previousClose else price
                    add(Stock(symbol, name, price, changePct, listOf(historyStart, price), item.optString("logoUrl").takeIf { it.isNotBlank() }, item.optString("sector", "Other").ifBlank { "Other" }, volume, changeAvailable, volumeAvailable, source, observedAt, freshnessMode, dataOrigin))
                }
            }
        } finally { connection.disconnect() }

    private fun stockFreshnessMode(observedAt: String): String {
        if (observedAt.isBlank()) return "UNKNOWN"
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Africa/Nairobi"))
        val instant = runCatching { java.time.Instant.parse(observedAt) }.getOrNull()
        if (instant != null) return if (instant.atZone(java.time.ZoneId.of("Africa/Nairobi")).toLocalDate().isBefore(now.toLocalDate())) "STALE" else "UNKNOWN"
        val date = runCatching { java.time.LocalDate.parse(observedAt.take(10)) }.getOrNull() ?: return "UNKNOWN"
        return if (date.isBefore(now.toLocalDate())) "STALE" else "UNKNOWN"
    }
}
