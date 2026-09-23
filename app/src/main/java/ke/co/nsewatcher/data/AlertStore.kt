package ke.co.nsewatcher.data

import android.content.Context
import ke.co.nsewatcher.AlertMonitorState
import ke.co.nsewatcher.AlertQuote
import java.time.Instant
import java.time.Duration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.domain.AlertEvent
import ke.co.nsewatcher.domain.mergeAlertEvents
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.alertDataStore by preferencesDataStore("nse_watcher_alerts")
private val alertsKey = stringPreferencesKey("alert_rules")
private val lastDailyTriggerDatesKey = stringPreferencesKey("last_daily_trigger_dates")
private val lastNewsTriggerIdsKey = stringPreferencesKey("last_news_trigger_ids")

private val monitorKey = stringPreferencesKey("alert_monitor_v2")

private val alertEventsKey = stringPreferencesKey("alert_events")

class AlertStore(private val context: Context) {
    val events: Flow<List<AlertEvent>> = context.alertDataStore.data.map { decodeEvents(it[alertEventsKey].orEmpty()) }

    suspend fun monitorState(): AlertMonitorState = decodeMonitor(context.alertDataStore.data.first()[monitorKey].orEmpty())

    // Claim detections and save evidence/checkpoints in one transaction. The ledger is
    // detection history, not a promise that Android delivered a notification.
    suspend fun recordEvaluation(events: List<AlertEvent>, quotes: Map<String, AlertQuote>, now: Instant): List<AlertEvent> {
        var accepted = emptyList<AlertEvent>()
        context.alertDataStore.edit { prefs ->
            val current = decodeMonitor(prefs[monitorKey].orEmpty())
            accepted = events.filter { it.id !in current.processed }.distinctBy { it.id }
            val cutoff = now.minus(Duration.ofDays(8)).toEpochMilli()
            val processed = current.processed.filterValues { it >= cutoff }.toMutableMap()
            accepted.forEach { processed[it.id] = now.toEpochMilli() }
            prefs[monitorKey] = JSONObject().apply {
                put("processed", JSONObject().apply { processed.forEach { (id, at) -> put(id, at) } })
                put("quotes", JSONObject().apply { quotes.forEach { (symbol, q) ->
                    put(symbol, JSONObject().apply { put("price", q.price); put("observedAt", q.observedAt) })
                } })
            }.toString()
            prefs[alertEventsKey] = encodeEvents(mergeAlertEvents(decodeEvents(prefs[alertEventsKey].orEmpty()), accepted))
        }
        return accepted
    }

    private fun decodeMonitor(raw: String): AlertMonitorState {
        if (raw.isBlank()) return AlertMonitorState()
        // Do not silently reset deduplication on corrupt state and resend old events.
        val obj = JSONObject(raw)
        val processed = obj.optJSONObject("processed") ?: JSONObject()
        val quotes = obj.optJSONObject("quotes") ?: JSONObject()
        return AlertMonitorState(
            buildMap { quotes.keys().forEach { symbol ->
                val q = quotes.getJSONObject(symbol)
                val price = q.optDouble("price", Double.NaN)
                if (price.isFinite() && price > 0) put(symbol, AlertQuote(price, q.optString("observedAt")))
            } },
            buildMap { processed.keys().forEach { id -> put(id, processed.getLong(id)) } }
        )
    }

    private fun encodeEvents(events: List<AlertEvent>): String = JSONArray().apply {
        events.forEach { event -> put(JSONObject().apply {
            put("id", event.id); put("ruleId", event.ruleId); put("symbol", event.symbol)
            put("title", event.title); put("message", event.message)
            put("recordedAt", event.recordedAt); put("observedAt", event.observedAt)
            put("articleId", event.articleId); put("articleTitle", event.articleTitle)
            put("source", event.source); put("sourceUrl", event.sourceUrl)
        }) }
    }.toString()

    private fun decodeEvents(raw: String): List<AlertEvent> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("id").isBlank() || item.optString("symbol").isBlank()) continue
                add(AlertEvent(item.optString("id"), item.optString("ruleId"), item.optString("symbol"),
                    item.optString("title"), item.optString("message"), item.optString("recordedAt"), item.optString("observedAt"),
                    item.optString("articleId"), item.optString("articleTitle"), item.optString("source"), item.optString("sourceUrl")))
            }
        }
    }.getOrDefault(emptyList())

    val alerts: Flow<List<PriceAlert>> = context.alertDataStore.data.map { prefs -> decodeAlerts(prefs[alertsKey].orEmpty()) }

    suspend fun save(alert: PriceAlert) {
        val normalized = alert.copy(id = alert.id.ifBlank { java.util.UUID.randomUUID().toString() }, symbol = alert.symbol.trim().uppercase())
        context.alertDataStore.edit { prefs ->
            val current = decodeAlerts(prefs[alertsKey].orEmpty()).filterNot { it.id == normalized.id }
            prefs[alertsKey] = encodeAlerts(current + normalized)
        }
    }

    suspend fun remove(id: String) {
        context.alertDataStore.edit { prefs -> prefs[alertsKey] = encodeAlerts(decodeAlerts(prefs[alertsKey].orEmpty()).filterNot { it.id == id }) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        context.alertDataStore.edit { prefs ->
            prefs[alertsKey] = encodeAlerts(decodeAlerts(prefs[alertsKey].orEmpty()).map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
    }

    suspend fun lastDailyTriggerDates(): Map<String, String> =
        context.alertDataStore.data.map { decodeStringMap(it[lastDailyTriggerDatesKey].orEmpty()) }.first()

    suspend fun lastNewsTriggerIds(): Map<String, String> =
        context.alertDataStore.data.map { decodeStringMap(it[lastNewsTriggerIdsKey].orEmpty()) }.first()

    private fun encodeAlerts(alerts: List<PriceAlert>): String {
        val array = JSONArray()
        alerts.forEach { alert ->
            array.put(JSONObject().apply {
                put("id", alert.id); put("symbol", alert.symbol); put("type", alert.type.name)
                if (alert.threshold == null) put("threshold", JSONObject.NULL) else put("threshold", alert.threshold)
                put("enabled", alert.enabled)
            })
        }
        return array.toString()
    }

    private fun decodeAlerts(raw: String): List<PriceAlert> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    val symbol = item.optString("symbol").trim().uppercase()
                    val type = runCatching { AlertType.valueOf(item.optString("type")) }.getOrNull() ?: continue
                    val threshold = if (item.isNull("threshold")) null else item.optDouble("threshold", Double.NaN).takeIf { it.isFinite() }
                    if (id.isNotBlank() && symbol.isNotBlank()) add(PriceAlert(id, symbol, type, threshold, item.optBoolean("enabled", true)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeStringMap(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key -> put(key, obj.optString(key)) }
            }
        }.getOrDefault(emptyMap())
    }

}
