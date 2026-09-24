package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.HomeBriefItem
import ke.co.nsewatcher.WatchlistPresentation
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

private val Context.homeChangeDataStore by preferencesDataStore("nse_watcher_home_changes")
private val homeChangeStateKey = stringPreferencesKey("state_v1")

internal data class HomeChangeRecord(
    val id: String,
    val symbol: String,
    val firstSeenAt: String,
    val reviewedAt: String = ""
)

internal data class HomeChangeState(
    val trackedSymbols: Set<String> = emptySet(),
    val baselineAtBySymbol: Map<String, String> = emptyMap(),
    val entries: Map<String, HomeChangeRecord> = emptyMap()
) {
    val reviewedIds: Set<String>
        get() = entries.values.filter { it.reviewedAt.isNotBlank() }.mapTo(mutableSetOf()) { it.id }
}

internal object HomeChangeLedger {
    private val retention = Duration.ofDays(30)

    fun reconcile(
        current: HomeChangeState,
        watchedSymbols: Set<String>,
        changes: List<HomeBriefItem>,
        now: Instant
    ): HomeChangeState {
        val watched = watchedSymbols.map(WatchlistPresentation::symbol).filter(String::isNotBlank).toSet()
        if (watched.isEmpty()) return HomeChangeState()

        val newlyTracked = watched - current.trackedSymbols
        val cutoff = now.minus(retention)
        val baselines = watched.associateWith { symbol ->
            current.baselineAtBySymbol[symbol]
                ?.takeIf { runCatching { Instant.parse(it) }.isSuccess }
                ?: now.toString()
        }
        val kept = current.entries.values.filter { record ->
            record.symbol in watched &&
                runCatching { Instant.parse(record.firstSeenAt) }.getOrNull()?.let { !it.isBefore(cutoff) } == true
        }.associateByTo(linkedMapOf()) { it.id }

        changes.asSequence()
            .filter { WatchlistPresentation.symbol(it.symbol) in watched }
            .distinctBy { it.id }
            .forEach { change ->
                if (change.id !in kept) {
                    val symbol = WatchlistPresentation.symbol(change.symbol)
                    val baseline = baselines[symbol]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                    val eventTime = runCatching { Instant.parse(change.time) }.getOrNull()
                    val existedBeforeBaseline = baseline == null || eventTime == null || !eventTime.isAfter(baseline)
                    val practiceFollowUp = change.practiceOrderId != null
                    kept[change.id] = HomeChangeRecord(
                        id = change.id,
                        symbol = symbol,
                        firstSeenAt = now.toString(),
                        reviewedAt = if (!practiceFollowUp && (symbol in newlyTracked || existedBeforeBaseline)) now.toString() else ""
                    )
                }
            }

        return HomeChangeState(
            trackedSymbols = watched,
            baselineAtBySymbol = baselines,
            entries = kept
        )
    }

    fun markReviewed(current: HomeChangeState, ids: Set<String>, now: Instant): HomeChangeState {
        if (ids.isEmpty()) return current
        val stamp = now.toString()
        return current.copy(entries = current.entries.mapValues { (id, record) ->
            if (id in ids && record.reviewedAt.isBlank()) record.copy(reviewedAt = stamp) else record
        })
    }
}

internal class HomeChangeStore(private val context: Context) {
    suspend fun reconcile(
        watchedSymbols: Set<String>,
        changes: List<HomeBriefItem>,
        now: Instant
    ): HomeChangeState {
        var next = HomeChangeState()
        context.homeChangeDataStore.edit { prefs ->
            val current = decode(prefs[homeChangeStateKey].orEmpty())
            next = HomeChangeLedger.reconcile(current, watchedSymbols, changes, now)
            prefs[homeChangeStateKey] = encode(next)
        }
        return next
    }

    suspend fun markReviewed(ids: Set<String>, now: Instant): HomeChangeState {
        var next = HomeChangeState()
        context.homeChangeDataStore.edit { prefs ->
            val current = decode(prefs[homeChangeStateKey].orEmpty())
            next = HomeChangeLedger.markReviewed(current, ids, now)
            prefs[homeChangeStateKey] = encode(next)
        }
        return next
    }

    suspend fun state(): HomeChangeState =
        decode(context.homeChangeDataStore.data.first()[homeChangeStateKey].orEmpty())

    private fun encode(state: HomeChangeState): String = JSONObject().apply {
        put("trackedSymbols", JSONArray(state.trackedSymbols.sorted()))
        put("baselineAtBySymbol", JSONObject().apply {
            state.baselineAtBySymbol.toSortedMap().forEach { (symbol, baselineAt) ->
                put(symbol, baselineAt)
            }
        })
        put("entries", JSONArray().apply {
            state.entries.values.forEach { record ->
                put(JSONObject().apply {
                    put("id", record.id)
                    put("symbol", record.symbol)
                    put("firstSeenAt", record.firstSeenAt)
                    put("reviewedAt", record.reviewedAt)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): HomeChangeState = runCatching {
        if (raw.isBlank()) return@runCatching HomeChangeState()
        val root = JSONObject(raw)
        val symbols = root.optJSONArray("trackedSymbols") ?: JSONArray()
        val baselineObject = root.optJSONObject("baselineAtBySymbol") ?: JSONObject()
        val entries = root.optJSONArray("entries") ?: JSONArray()
        HomeChangeState(
            trackedSymbols = buildSet {
                for (i in 0 until symbols.length()) {
                    symbols.optString(i).trim().takeIf(String::isNotBlank)?.let {
                        add(WatchlistPresentation.symbol(it))
                    }
                }
            },
            baselineAtBySymbol = buildMap {
                val keys = baselineObject.keys()
                while (keys.hasNext()) {
                    val rawSymbol = keys.next()
                    val symbol = WatchlistPresentation.symbol(rawSymbol)
                    val baselineAt = baselineObject.optString(rawSymbol).trim()
                    if (symbol.isNotBlank() && runCatching { Instant.parse(baselineAt) }.isSuccess) {
                        put(symbol, baselineAt)
                    }
                }
            },
            entries = buildMap {
                for (i in 0 until entries.length()) {
                    val item = entries.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    val symbol = WatchlistPresentation.symbol(item.optString("symbol"))
                    val firstSeenAt = item.optString("firstSeenAt").trim()
                    if (id.isBlank() || symbol.isBlank() || firstSeenAt.isBlank()) continue
                    put(id, HomeChangeRecord(id, symbol, firstSeenAt, item.optString("reviewedAt").trim()))
                }
            }
        )
    }.getOrDefault(HomeChangeState())
}
