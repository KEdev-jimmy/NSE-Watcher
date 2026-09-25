package ke.co.nsewatcher.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object MovementIntelligenceCache {
    private const val BASE_URL = "https://nse-watcher.vercel.app/api/moving?symbol="
    private const val CONNECTION_VERSION = "movement-ui-v1"

    data class Move(
        val periodDays: Int = 1,
        val from: String = "",
        val to: String = "",
        val priceBefore: Double? = null,
        val priceAfter: Double? = null,
        val change: String = ""
    )

    data class Evidence(
        val eventType: String = "",
        val title: String = "",
        val date: String = "",
        val source: String = "",
        val sourceUrl: String = "",
        val description: String = "",
        val relationship: String = "possible",
        val daysFromMove: Int? = null
    )

    data class Result(
        val symbol: String = "",
        val move: Move? = null,
        val summary: String = "",
        val evidence: List<Evidence> = emptyList(),
        val limitations: List<String> = emptyList(),
        val partial: Boolean = false,
        val cacheState: String = "LIVE",
        val cacheAgeMinutes: Long? = null,
        val cacheMessage: String = "",
        val error: String? = null
    )

    private val repository by lazy {
        MovementIntelligenceRepository(loader = ::fetch)
    }

    suspend fun load(
        symbol: String,
        forceRefresh: Boolean = false
    ): Result = repository.load(symbol, forceRefresh)

    suspend fun clear(symbol: String? = null) = repository.clear(symbol)

    private suspend fun fetch(symbol: String): Result = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(symbol.trim(), Charsets.UTF_8.name())
        val root = request(BASE_URL + encoded)
            ?: return@withContext Result(error = "Movement intelligence is unavailable right now.")
        val error = root.optString("error").trim()
        if (error.isNotBlank()) return@withContext Result(error = error)

        Result(
            symbol = root.optString("symbol"),
            move = parseMove(root.optJSONObject("move")),
            summary = root.optString("summary"),
            evidence = parseEvidence(root.optJSONArray("evidence") ?: JSONArray()),
            limitations = parseStrings(root.optJSONArray("limitations") ?: JSONArray()),
            partial = root.optBoolean("partial", false)
        )
    }

    private fun parseMove(obj: JSONObject?): Move? = obj?.let {
        Move(
            periodDays = it.optInt("periodDays", 1),
            from = it.optString("from"),
            to = it.optString("to"),
            priceBefore = it.optDoubleOrNull("priceBefore"),
            priceAfter = it.optDoubleOrNull("priceAfter"),
            change = it.optString("change")
        )
    }

    private fun parseEvidence(array: JSONArray): List<Evidence> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Evidence(
                    eventType = item.optString("eventType"),
                    title = item.optString("title"),
                    date = item.optString("date"),
                    source = item.optString("source"),
                    sourceUrl = item.optString("sourceUrl"),
                    description = item.optString("description"),
                    relationship = item.optString("relationship", "possible"),
                    daysFromMove = if (item.has("daysFromMove") && !item.isNull("daysFromMove")) item.optInt("daysFromMove") else null
                )
            )
        }
    }

    private fun parseStrings(array: JSONArray): List<String> = buildList {
        for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun request(url: String): JSONObject? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: return@runCatching null
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}


internal class MovementIntelligenceRepository(
    private val loader: suspend (String) -> MovementIntelligenceCache.Result,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxAgeMs: Long = 15L * 60L * 1000L,
    private val maxStaleMs: Long = 2L * 60L * 60L * 1000L,
    private val maxEntries: Int = 64
) {
    private data class Entry(
        val result: MovementIntelligenceCache.Result,
        val loadedAtMs: Long
    )

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<MovementIntelligenceCache.Result>>()

    suspend fun load(
        symbol: String,
        forceRefresh: Boolean = false
    ): MovementIntelligenceCache.Result {
        val key = normalizeSymbol(symbol)
        if (key.isBlank()) {
            return MovementIntelligenceCache.Result(
                error = "Movement intelligence requires a company symbol."
            )
        }

        var owner = false
        var staleEntry: Entry? = null
        val pending = mutex.withLock {
            val now = nowMs()
            val cached = entries[key]
            staleEntry = cached?.takeIf { now - it.loadedAtMs <= maxStaleMs }

            if (!forceRefresh && cached != null && now - cached.loadedAtMs < maxAgeMs) {
                return cached.result.copy(
                    cacheState = "FRESH_CACHE",
                    cacheAgeMinutes = (now - cached.loadedAtMs).coerceAtLeast(0L) / 60_000L,
                    cacheMessage = ""
                )
            }

            inFlight[key] ?: CompletableDeferred<MovementIntelligenceCache.Result>().also {
                inFlight[key] = it
                owner = true
            }
        }

        if (owner) {
            try {
                val live = loader(key)
                val delivered = if (usable(live)) {
                    val clean = live.copy(
                        cacheState = "LIVE",
                        cacheAgeMinutes = 0,
                        cacheMessage = ""
                    )
                    mutex.withLock {
                        entries[key] = Entry(clean, nowMs())
                        trimLocked()
                        inFlight.remove(key)
                    }
                    clean
                } else {
                    val fallback = staleEntry?.toFallback(nowMs())
                    mutex.withLock { inFlight.remove(key) }
                    fallback ?: live
                }
                pending.complete(delivered)
            } catch (cancelled: CancellationException) {
                mutex.withLock { inFlight.remove(key) }
                pending.completeExceptionally(cancelled)
                throw cancelled
            } catch (_: Exception) {
                val fallback = staleEntry?.toFallback(nowMs())
                val result = fallback ?: MovementIntelligenceCache.Result(
                    error = "Movement intelligence is unavailable right now."
                )
                mutex.withLock { inFlight.remove(key) }
                pending.complete(result)
            }
        }

        return pending.await()
    }

    suspend fun clear(symbol: String? = null) {
        mutex.withLock {
            if (symbol == null) {
                entries.clear()
            } else {
                entries.remove(normalizeSymbol(symbol))
            }
        }
    }

    private fun Entry.toFallback(now: Long): MovementIntelligenceCache.Result {
        val age = (now - loadedAtMs).coerceAtLeast(0L)
        return result.copy(
            partial = true,
            cacheState = "STALE_FALLBACK",
            cacheAgeMinutes = age / 60_000L,
            cacheMessage = "Live movement evidence could not be refreshed. Showing the most recent cached evidence.",
            error = null
        )
    }

    private fun usable(result: MovementIntelligenceCache.Result): Boolean =
        result.error == null && (
            result.move != null ||
                result.summary.isNotBlank() ||
                result.evidence.isNotEmpty() ||
                result.limitations.isNotEmpty()
            )

    private fun normalizeSymbol(value: String): String =
        value.trim().uppercase(Locale.US).removeSuffix(".KE")

    private fun trimLocked() {
        while (entries.size > maxEntries) {
            val oldest = entries.minByOrNull { it.value.loadedAtMs }?.key ?: break
            entries.remove(oldest)
        }
    }
}
