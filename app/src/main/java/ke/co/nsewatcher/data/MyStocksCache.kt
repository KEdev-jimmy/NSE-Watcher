package ke.co.nsewatcher.data

import ke.co.nsewatcher.Stock
import ke.co.nsewatcher.MarketRefreshController
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
        val status: String = "UNKNOWN",
        val nextOpen: String = "",
        val nextClose: String = "",
        val isKnown: Boolean = false
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
                val rawStatus = root.optString("status", "").trim()
                val status = when (rawStatus.uppercase()) {
                    "OPEN", "TRADING" -> "OPEN"
                    "CLOSED", "NOT_TRADING" -> "CLOSED"
                    else -> "UNKNOWN"
                }
                val hasExplicitIsOpen = root.has("isOpen") && !root.isNull("isOpen")
                val explicitIsOpen = if (hasExplicitIsOpen) root.optBoolean("isOpen") else null
                val isKnown = root.optBoolean("isKnown", false) &&
                    status != "UNKNOWN"

                MarketStatus(
                    isOpen = when {
                        isKnown -> explicitIsOpen ?: (status == "OPEN")
                        else -> false
                    },
                    status = if (isKnown) status else "UNKNOWN",
                    nextOpen = root.optString("nextOpen", ""),
                    nextClose = root.optString("nextClose", ""),
                    isKnown = isKnown
                )
            } finally { connection.disconnect() }
        }.getOrDefault(MarketStatus())
    }

    suspend fun loadStocks(): List<Stock> = withContext(Dispatchers.IO) {
        MarketRefreshController.markStarted()
        try {
            val marketStatus = loadMarketStatus()
            val marketOpen = marketStatus.isOpen.takeIf { marketStatus.isKnown }
            val backend = loadFromUrl(BACKEND_STOCKS_URL, "backend", marketOpen)
            // Never silently substitute the static catalogue while the Nairobi
            // continuous market is open. A fallback price can make a live market
            // look frozen and can turn missing change data into misleading 0.00%.
            // During a known closed session the catalogue remains useful for
            // browsing, but it is explicitly marked as fallback data.
            val base = when {
                backend.isNotEmpty() -> backend
                marketOpen == true -> emptyList()
                else -> loadFromUrl(FALLBACK_URL, "fallback", marketOpen)
            }
            if (base.isEmpty()) {
                MarketRefreshController.markFailed()
                return@withContext emptyList()
            }
            val refreshed = base.map { stock ->
                if (stock.symbol in chartSymbols) {
                    val history = loadHistoryDetails(stock.symbol, "1D")
                    if (history.prices.size >= 2) {
                        val latest = history.sessionClose?.takeIf { it.isFinite() && it > 0.0 }
                            ?: history.prices.last()
                        // The stock quote feed remains the source of the daily change.
                        // Do not derive "today" change from the first chart observation:
                        // the 1D chart now contains only actual intraday observations.
                        stock.copy(price = latest, history = history.prices)
                    } else stock
                } else stock
            }
            MarketRefreshController.markSucceeded()
            refreshed
        } catch (_: Exception) {
            MarketRefreshController.markFailed()
            emptyList()
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

    private fun loadFromUrl(url: String, dataOrigin: String, marketOpen: Boolean?): List<Stock> = runCatching {
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
                    val suppliedChangePct = item.optDouble("changePct", Double.NaN)
                    val derived = if (previousClose.isFinite() && previousClose > 0.0) ((price - previousClose) / previousClose) * 100.0 else Double.NaN
                    // Stock.change is a percentage. Prefer the provider's explicit changePct;
                    // derive it from price/previousClose only when changePct is absent.
                    // Do not treat a generic "change" field as a percentage because its unit
                    // is provider-schema dependent and may be an absolute price delta.
                    val changePct = when {
                        suppliedChangePct.isFinite() -> suppliedChangePct
                        derived.isFinite() -> derived
                        else -> Double.NaN
                    }
                    val changeAvailable = changePct.isFinite()
                    val volumeValue = item.optDouble("volume", Double.NaN)
                    val volumeAvailable = volumeValue.isFinite() && volumeValue >= 0.0
                    val volume = if (volumeAvailable) volumeValue.toLong() else 0L
                    val averageVolumeValue = item.optDouble("averageVolume", Double.NaN).let { if (it.isFinite()) it else item.optDouble("avgVolume", Double.NaN) }
                    val averageVolumeAvailable = averageVolumeValue.isFinite() && averageVolumeValue > 0.0
                    val averageVolume = if (averageVolumeAvailable) averageVolumeValue.toLong() else 0L
                    val source = item.optString("source", "").trim().ifBlank { if (dataOrigin == "backend") "MyStocks Africa" else "NSE Watcher fallback catalogue" }
                    val observedAt = item.optString("lastPriceUpdate", "").trim().ifBlank { item.optString("asOf", "").trim() }
                    val stale = item.optBoolean("stale", false)
                    val freshnessMode = stockFreshnessMode(observedAt, marketOpen, stale)
                    // Stock.history is reserved for real historical observations.
                    // The quote endpoint does not provide a time series, so never synthesize
                    // a two-point series from previousClose/price. Company Intelligence loads
                    // sourced history through loadHistoryDetails() instead.
                    add(Stock(symbol, name, price, changePct, emptyList(), item.optString("logoUrl").takeIf { it.isNotBlank() }, item.optString("sector", "Other").ifBlank { "Other" }, volume, changeAvailable, volumeAvailable, source, observedAt, freshnessMode, dataOrigin, averageVolume, averageVolumeAvailable, previousClose.takeIf { it.isFinite() && it > 0.0 }))
                }
            }
        } finally { connection.disconnect() }
    }.getOrDefault(emptyList())

    private fun stockFreshnessMode(observedAt: String, marketOpen: Boolean?, providerStale: Boolean = false): String {
        if (providerStale) return "STALE"
        if (observedAt.isBlank()) return "UNKNOWN"
        val now = java.time.Instant.now().atZone(java.time.ZoneId.of("Africa/Nairobi"))
        val instant = runCatching { java.time.Instant.parse(observedAt) }.getOrNull()
        if (instant != null) {
            val observedDate = instant.atZone(java.time.ZoneId.of("Africa/Nairobi")).toLocalDate()
            return when {
                observedDate.isBefore(now.toLocalDate()) -> "STALE"
                observedDate == now.toLocalDate() && marketOpen == true -> "CURRENT_SESSION"
                observedDate == now.toLocalDate() && marketOpen == false -> "END_OF_DAY"
                else -> "UNKNOWN"
            }
        }
        val date = runCatching { java.time.LocalDate.parse(observedAt.take(10)) }.getOrNull() ?: return "UNKNOWN"
        return when {
            date.isBefore(now.toLocalDate()) -> "STALE"
            date == now.toLocalDate() && marketOpen == true -> "CURRENT_SESSION"
            date == now.toLocalDate() && marketOpen == false -> "END_OF_DAY"
            else -> "UNKNOWN"
        }
    }
}
