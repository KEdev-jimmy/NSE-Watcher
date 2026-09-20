package ke.co.nsewatcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AnalystCache {
    private const val ENDPOINT = "https://nse-watcher.vercel.app/api/analyst"

    data class Evidence(
        val id: String = "",
        val claim: String = "",
        val value: String = "",
        val source: String = "",
        val url: String = "",
        val period: String = ""
    )

    data class Signal(
        val type: String = "",
        val title: String = "",
        val detail: String = "",
        val evidenceIds: List<String> = emptyList()
    )

    data class Analysis(
        val headline: String = "",
        val summary: String = "",
        val signals: List<Signal> = emptyList(),
        val interpretation: String = "",
        val unknowns: List<String> = emptyList()
    )

    data class Result(
        val answer: String = "",
        val analysis: Analysis? = null,
        val evidence: List<Evidence> = emptyList(),
        val model: String = "",
        val responseId: String = "",
        val message: String = "",
        val error: String = ""
    )

    suspend fun ask(symbol: String, question: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject().put("symbol", symbol).put("question", question).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            val json = runCatching { JSONObject(responseText) }.getOrElse {
                return@withContext Result(error = "Invalid Analyst response ($status)")
            }
            if (status !in 200..299) {
                return@withContext Result(error = json.optString("detail").ifBlank { json.optString("error").ifBlank { "Request failed ($status)" } })
            }
            val evidence = mutableListOf<Evidence>()
            val evidenceArray = json.optJSONArray("evidence")
            if (evidenceArray != null) {
                for (i in 0 until evidenceArray.length()) {
                    val item = evidenceArray.optJSONObject(i) ?: continue
                    evidence += Evidence(item.optString("id"), item.optString("claim"), item.optString("value"), item.optString("source"), item.optString("url"), item.optString("period"))
                }
            }
            val analysisObject = json.optJSONObject("analysis")
            val signals = mutableListOf<Signal>()
            val signalArray = analysisObject?.optJSONArray("signals")
            if (signalArray != null) {
                for (i in 0 until signalArray.length()) {
                    val item = signalArray.optJSONObject(i) ?: continue
                    val ids = mutableListOf<String>()
                    val idsArray = item.optJSONArray("evidenceIds")
                    if (idsArray != null) {
                        for (j in 0 until idsArray.length()) ids += idsArray.optString(j)
                    }
                    signals += Signal(
                        type = item.optString("type"),
                        title = item.optString("title"),
                        detail = item.optString("detail"),
                        evidenceIds = ids
                    )
                }
            }
            val analysis = analysisObject?.let {
                val unknowns = mutableListOf<String>()
                val unknownArray = it.optJSONArray("unknowns")
                if (unknownArray != null) {
                    for (i in 0 until unknownArray.length()) unknowns += unknownArray.optString(i)
                }
                Analysis(
                    headline = it.optString("headline"),
                    summary = it.optString("summary"),
                    signals = signals,
                    interpretation = it.optString("interpretation"),
                    unknowns = unknowns
                )
            }
            Result(
                answer = json.optString("answer"),
                analysis = analysis,
                evidence = evidence,
                model = json.optString("model"),
                responseId = json.optString("responseId"),
                message = json.optString("message")
            )
        }.getOrElse { Result(error = it.message ?: "Network error") }
    }
}
