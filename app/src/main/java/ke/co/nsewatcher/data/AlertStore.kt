package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.alertDataStore by preferencesDataStore("nse_watcher_alerts")
private val alertsKey = stringPreferencesKey("alert_rules")
private val previousPricesKey = stringPreferencesKey("previous_prices")
private val lastDailyTriggerDatesKey = stringPreferencesKey("last_daily_trigger_dates")
private val lastNewsTriggerIdsKey = stringPreferencesKey("last_news_trigger_ids")

class AlertStore(private val context: Context) {
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

    suspend fun previousPrices(): Map<String, Double> = context.alertDataStore.data.map { decodePrices(it[previousPricesKey].orEmpty()) }.first()

    suspend fun lastDailyTriggerDates(): Map<String, String> =
        context.alertDataStore.data.map { decodeStringMap(it[lastDailyTriggerDatesKey].orEmpty()) }.first()

    suspend fun recordDailyTriggers(triggeredIds: Set<String>, date: String) {
        if (triggeredIds.isEmpty()) return
        context.alertDataStore.edit { prefs ->
            val current = decodeStringMap(prefs[lastDailyTriggerDatesKey].orEmpty()).toMutableMap()
            triggeredIds.forEach { current[it] = date }
            prefs[lastDailyTriggerDatesKey] = encodeStringMap(current)
        }
    }

    suspend fun lastNewsTriggerIds(): Map<String, String> =
        context.alertDataStore.data.map { decodeStringMap(it[lastNewsTriggerIdsKey].orEmpty()) }.first()

    suspend fun recordNewsTriggers(triggered: Map<String, String>) {
        if (triggered.isEmpty()) return
        context.alertDataStore.edit { prefs ->
            val current = decodeStringMap(prefs[lastNewsTriggerIdsKey].orEmpty()).toMutableMap()
            triggered.forEach { (alertId, newsId) -> current[alertId] = newsId }
            prefs[lastNewsTriggerIdsKey] = encodeStringMap(current)
        }
    }

    suspend fun recordPrices(prices: Map<String, Double>) {
        if (prices.isEmpty()) return
        context.alertDataStore.edit { prefs -> prefs[previousPricesKey] = encodePrices(prices) }
    }

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

    private fun encodeStringMap(values: Map<String, String>): String {
        val obj = JSONObject()
        values.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
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

    private fun encodePrices(prices: Map<String, Double>): String {
        val obj = JSONObject()
        prices.filterValues { it.isFinite() && it > 0.0 }.forEach { (symbol, price) -> obj.put(symbol.trim().uppercase(), price) }
        return obj.toString()
    }

    private fun decodePrices(raw: String): Map<String, Double> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { symbol ->
                    val price = obj.optDouble(symbol, Double.NaN)
                    if (price.isFinite() && price > 0.0) put(symbol, price)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
