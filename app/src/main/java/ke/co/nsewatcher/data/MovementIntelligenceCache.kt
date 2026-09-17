package ke.co.nsewatcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MovementIntelligenceCache {
    private const val BASE_URL = "https://nse-watcher.vercel.app/api/moving?symbol="

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
        val error: String? = null
    )

    suspend fun load(symbol: String): Result = withContext(Dispatchers.IO) {
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
