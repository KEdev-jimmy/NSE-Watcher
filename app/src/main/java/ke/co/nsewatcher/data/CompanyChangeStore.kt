package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.WatchlistPresentation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.Locale

private val Context.companyChangeDataStore by preferencesDataStore("nse_watcher_company_changes")
private val companyChangeStateKey = stringPreferencesKey("state_v1")

internal enum class CompanyDataChangeKind {
    REPORTING_PERIOD,
    REPORTED_FIGURES,
    DIVIDEND
}

internal data class CompanyDataSnapshot(
    val symbol: String,
    val financialPeriod: String = "",
    val revenue: String = "",
    val profit: String = "",
    val eps: String = "",
    val dividendFingerprint: String = ""
)

internal data class CompanyDataChangeEvent(
    val id: String,
    val symbol: String,
    val kind: CompanyDataChangeKind,
    val title: String,
    val detail: String,
    val source: String,
    val observedAt: String
)

internal data class CompanyChangeState(
    val snapshots: Map<String, CompanyDataSnapshot> = emptyMap(),
    val lastCheckedAt: Map<String, String> = emptyMap(),
    val events: List<CompanyDataChangeEvent> = emptyList()
)

internal data class CompanyChangePlan(
    val state: CompanyChangeState,
    val dueSymbols: List<String>
)

internal object CompanyChangeDetector {
    fun snapshot(symbol: String, result: CompanyIntelligenceCache.Result): CompanyDataSnapshot {
        val normalized = WatchlistPresentation.symbol(symbol)
        return CompanyDataSnapshot(
            symbol = normalized,
            financialPeriod = result.profile.financialPeriod.trim(),
            revenue = result.profile.revenue.trim(),
            profit = result.profile.profit.trim(),
            eps = result.profile.eps.trim(),
            dividendFingerprint = dividendFingerprint(result)
        )
    }

    fun detect(
        previous: CompanyDataSnapshot?,
        current: CompanyDataSnapshot,
        result: CompanyIntelligenceCache.Result,
        now: Instant
    ): List<CompanyDataChangeEvent> {
        if (previous == null || current.symbol.isBlank()) return emptyList()
        val source = result.source.trim().ifBlank { "Company intelligence source" }
        val events = mutableListOf<CompanyDataChangeEvent>()

        if (current.financialPeriod.isNotBlank() && current.financialPeriod != previous.financialPeriod) {
            val figures = listOf(
                "Revenue" to current.revenue,
                "Profit" to current.profit,
                "EPS" to current.eps
            ).filter { it.second.isNotBlank() }
                .joinToString(" • ") { (label, value) -> "$label $value" }
            val detail = buildString {
                append("The provider now reports ")
                append(current.financialPeriod)
                append(" for ")
                append(current.symbol)
                append(".")
                if (figures.isNotBlank()) append(" $figures.")
            }
            events += event(
                current.symbol,
                CompanyDataChangeKind.REPORTING_PERIOD,
                current.financialPeriod,
                "New reported period for ${current.symbol}",
                detail,
                source,
                now
            )
        } else if (
            current.financialPeriod.isNotBlank() &&
            previous.financialPeriod == current.financialPeriod
        ) {
            val changed = listOf(
                Triple("Revenue", previous.revenue, current.revenue),
                Triple("Profit", previous.profit, current.profit),
                Triple("EPS", previous.eps, current.eps)
            ).filter { (_, before, after) ->
                before.isNotBlank() && after.isNotBlank() && before != after
            }
            if (changed.isNotEmpty()) {
                val detail = changed.joinToString(" • ") { (label, before, after) ->
                    "$label $before → $after"
                }
                val key = current.financialPeriod + "|" + detail
                events += event(
                    current.symbol,
                    CompanyDataChangeKind.REPORTED_FIGURES,
                    key,
                    "Reported figures updated for ${current.symbol}",
                    "${current.financialPeriod} • $detail",
                    source,
                    now
                )
            }
        }

        if (
            current.dividendFingerprint.isNotBlank() &&
            current.dividendFingerprint != previous.dividendFingerprint
        ) {
            events += event(
                current.symbol,
                CompanyDataChangeKind.DIVIDEND,
                current.dividendFingerprint,
                "Reported dividend data updated for ${current.symbol}",
                "The provider's dividend records differ from the previous NSE Watcher observation. Review the current amount and dates in Company Intelligence.",
                source,
                now
            )
        }

        return events.distinctBy { it.id }
    }

    private fun dividendFingerprint(result: CompanyIntelligenceCache.Result): String =
        result.dividends.map { dividend ->
            listOf(
                dividend.amount.trim(),
                dividend.exDate.trim(),
                dividend.paymentDate.trim(),
                dividend.declaredDate.trim(),
                dividend.type.trim(),
                dividend.status.trim()
            ).joinToString("~")
        }.filter { row -> row.replace("~", "").isNotBlank() }
            .sorted()
            .joinToString("|")

    private fun event(
        symbol: String,
        kind: CompanyDataChangeKind,
        key: String,
        title: String,
        detail: String,
        source: String,
        now: Instant
    ): CompanyDataChangeEvent {
        val hash = Integer.toHexString(key.hashCode())
        return CompanyDataChangeEvent(
            id = "company-data:${symbol.lowercase(Locale.US)}:${kind.name.lowercase(Locale.US)}:$hash",
            symbol = symbol,
            kind = kind,
            title = title,
            detail = detail,
            source = source,
            observedAt = now.toString()
        )
    }
}

internal object CompanyChangeLedger {
    private val checkInterval = Duration.ofHours(6)
    private val retention = Duration.ofDays(30)
    private const val maxEvents = 100
    private const val maxChecksPerRun = 6

    fun sync(current: CompanyChangeState, watchedSymbols: Set<String>, now: Instant): CompanyChangePlan {
        val watched = watchedSymbols.map(WatchlistPresentation::symbol).filter(String::isNotBlank).toSet()
        if (watched.isEmpty()) return CompanyChangePlan(CompanyChangeState(), emptyList())

        val cutoff = now.minus(retention)
        val next = CompanyChangeState(
            snapshots = current.snapshots.filterKeys { it in watched },
            lastCheckedAt = current.lastCheckedAt.filterKeys { it in watched },
            events = current.events.filter { event ->
                event.symbol in watched &&
                    runCatching { Instant.parse(event.observedAt) }.getOrNull()?.let { !it.isBefore(cutoff) } == true
            }.distinctBy { it.id }.takeLast(maxEvents)
        )
        val due = watched.sortedWith(
            compareBy<String> {
                next.lastCheckedAt[it]?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() } ?: Instant.MIN
            }.thenBy { it }
        ).filter { symbol ->
            val last = next.lastCheckedAt[symbol]?.let { runCatching { Instant.parse(it) }.getOrNull() }
            last == null || Duration.between(last, now) >= checkInterval
        }.take(maxChecksPerRun)

        return CompanyChangePlan(next, due)
    }

    fun observe(
        current: CompanyChangeState,
        symbol: String,
        result: CompanyIntelligenceCache.Result,
        now: Instant
    ): CompanyChangeState {
        val normalized = WatchlistPresentation.symbol(symbol)
        if (normalized.isBlank()) return current
        val snapshot = CompanyChangeDetector.snapshot(normalized, result)
        val detected = CompanyChangeDetector.detect(current.snapshots[normalized], snapshot, result, now)
        val mergedEvents = (current.events + detected)
            .distinctBy { it.id }
            .sortedBy { runCatching { Instant.parse(it.observedAt) }.getOrDefault(Instant.MIN) }
            .takeLast(maxEvents)

        return current.copy(
            snapshots = current.snapshots + (normalized to snapshot),
            lastCheckedAt = current.lastCheckedAt + (normalized to now.toString()),
            events = mergedEvents
        )
    }

    fun markChecked(current: CompanyChangeState, symbol: String, now: Instant): CompanyChangeState {
        val normalized = WatchlistPresentation.symbol(symbol)
        if (normalized.isBlank()) return current
        return current.copy(lastCheckedAt = current.lastCheckedAt + (normalized to now.toString()))
    }
}

internal class CompanyChangeStore(private val context: Context) {
    val events: Flow<List<CompanyDataChangeEvent>> = context.companyChangeDataStore.data.map { prefs ->
        decode(prefs[companyChangeStateKey].orEmpty()).events.sortedByDescending {
            runCatching { Instant.parse(it.observedAt) }.getOrDefault(Instant.MIN)
        }
    }

    suspend fun syncAndDue(watchedSymbols: Set<String>, now: Instant): List<String> {
        var due = emptyList<String>()
        context.companyChangeDataStore.edit { prefs ->
            val plan = CompanyChangeLedger.sync(
                decode(prefs[companyChangeStateKey].orEmpty()),
                watchedSymbols,
                now
            )
            due = plan.dueSymbols
            prefs[companyChangeStateKey] = encode(plan.state)
        }
        return due
    }

    suspend fun recordObservation(
        symbol: String,
        result: CompanyIntelligenceCache.Result,
        now: Instant
    ) {
        context.companyChangeDataStore.edit { prefs ->
            val current = decode(prefs[companyChangeStateKey].orEmpty())
            prefs[companyChangeStateKey] = encode(CompanyChangeLedger.observe(current, symbol, result, now))
        }
    }

    suspend fun markChecked(symbol: String, now: Instant) {
        context.companyChangeDataStore.edit { prefs ->
            val current = decode(prefs[companyChangeStateKey].orEmpty())
            prefs[companyChangeStateKey] = encode(CompanyChangeLedger.markChecked(current, symbol, now))
        }
    }

    private fun encode(state: CompanyChangeState): String = JSONObject().apply {
        put("snapshots", JSONObject().apply {
            state.snapshots.toSortedMap().forEach { (symbol, snapshot) ->
                put(symbol, JSONObject().apply {
                    put("financialPeriod", snapshot.financialPeriod)
                    put("revenue", snapshot.revenue)
                    put("profit", snapshot.profit)
                    put("eps", snapshot.eps)
                    put("dividendFingerprint", snapshot.dividendFingerprint)
                })
            }
        })
        put("lastCheckedAt", JSONObject().apply {
            state.lastCheckedAt.toSortedMap().forEach { (symbol, checkedAt) -> put(symbol, checkedAt) }
        })
        put("events", JSONArray().apply {
            state.events.forEach { event ->
                put(JSONObject().apply {
                    put("id", event.id)
                    put("symbol", event.symbol)
                    put("kind", event.kind.name)
                    put("title", event.title)
                    put("detail", event.detail)
                    put("source", event.source)
                    put("observedAt", event.observedAt)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): CompanyChangeState = runCatching {
        if (raw.isBlank()) return@runCatching CompanyChangeState()
        val root = JSONObject(raw)
        val snapshotsObject = root.optJSONObject("snapshots") ?: JSONObject()
        val checkedObject = root.optJSONObject("lastCheckedAt") ?: JSONObject()
        val eventsArray = root.optJSONArray("events") ?: JSONArray()

        CompanyChangeState(
            snapshots = buildMap {
                snapshotsObject.keys().forEach { rawSymbol ->
                    val symbol = WatchlistPresentation.symbol(rawSymbol)
                    val item = snapshotsObject.optJSONObject(rawSymbol) ?: return@forEach
                    if (symbol.isBlank()) return@forEach
                    put(
                        symbol,
                        CompanyDataSnapshot(
                            symbol = symbol,
                            financialPeriod = item.optString("financialPeriod").trim(),
                            revenue = item.optString("revenue").trim(),
                            profit = item.optString("profit").trim(),
                            eps = item.optString("eps").trim(),
                            dividendFingerprint = item.optString("dividendFingerprint").trim()
                        )
                    )
                }
            },
            lastCheckedAt = buildMap {
                checkedObject.keys().forEach { rawSymbol ->
                    val symbol = WatchlistPresentation.symbol(rawSymbol)
                    val checkedAt = checkedObject.optString(rawSymbol).trim()
                    if (symbol.isNotBlank() && runCatching { Instant.parse(checkedAt) }.isSuccess) {
                        put(symbol, checkedAt)
                    }
                }
            },
            events = buildList {
                for (i in 0 until eventsArray.length()) {
                    val item = eventsArray.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    val symbol = WatchlistPresentation.symbol(item.optString("symbol"))
                    val kind = runCatching {
                        CompanyDataChangeKind.valueOf(item.optString("kind"))
                    }.getOrNull() ?: continue
                    val observedAt = item.optString("observedAt").trim()
                    if (
                        id.isBlank() ||
                        symbol.isBlank() ||
                        runCatching { Instant.parse(observedAt) }.isFailure
                    ) continue
                    add(
                        CompanyDataChangeEvent(
                            id = id,
                            symbol = symbol,
                            kind = kind,
                            title = item.optString("title").trim(),
                            detail = item.optString("detail").trim(),
                            source = item.optString("source").trim(),
                            observedAt = observedAt
                        )
                    )
                }
            }
        )
    }.getOrDefault(CompanyChangeState())
}
