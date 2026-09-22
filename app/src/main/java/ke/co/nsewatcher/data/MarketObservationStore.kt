package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.MarketSavedObservation
import ke.co.nsewatcher.mergeMarketObservations
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.marketObservationStore by preferencesDataStore("nse_watcher_market_observations")
private val observationsKey = stringPreferencesKey("summaries")

internal class MarketObservationStore(private val context: Context) {
    val observations = context.marketObservationStore.data.map { decode(it[observationsKey].orEmpty()) }
    suspend fun record(observation: MarketSavedObservation) {
        context.marketObservationStore.edit { prefs ->
            prefs[observationsKey] = JSONArray().apply {
                mergeMarketObservations(decode(prefs[observationsKey].orEmpty()), observation).forEach { row ->
                    put(JSONObject().apply {
                        put("observedAt", row.observedAt); put("checkedAt", row.checkedAt); put("source", row.source)
                        put("rising", row.rising); put("flat", row.flat); put("falling", row.falling); put("total", row.total)
                    })
                }
            }.toString()
        }
    }
    private fun decode(raw: String): List<MarketSavedObservation> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList { for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            add(MarketSavedObservation(o.optString("observedAt"), o.optString("checkedAt"), o.optString("source"), o.optInt("rising"), o.optInt("flat"), o.optInt("falling"), o.optInt("total")))
        } }
    }
}
