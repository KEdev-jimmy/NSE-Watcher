package ke.co.nsewatcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Single durable transaction for orders, reservations, holdings and the ledger. */
internal class PracticeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nse_watcher_paper_portfolio", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    fun read(): PracticeState = synchronized(lock) {
        val raw = prefs.getString("practice_v2", null)
        if (raw != null) decode(JSONObject(raw)) else migrate()
    }
    fun update(block: (PracticeState) -> PracticeState): PracticeState = synchronized(lock) {
        val next = block(read())
        val editor = prefs.edit().putString("practice_v2", encode(next).toString())
            .putBoolean("enabled", next.enabled).putFloat("cash", next.cash.toFloat())
        // Keep the Home practice balance compatible with the existing entry point.
        check(editor.commit()) { "Could not save your practice account. Please try again." }
        next
    }
    fun create(amount: Double): PracticeState = update { old ->
        require(!old.enabled) { "A practice account already exists." }
        require(amount.isFinite() && amount >= 1000 && amount <= PracticeEngine.MAX_CASH) { "Choose KSh 1,000 to KSh 1 trillion." }
        val now = System.currentTimeMillis(); val cash = PracticeEngine.money(amount)
        PracticeState(true, cash, cash, entries = listOf(PracticeEntry(UUID.randomUUID().toString(), now, "CAPITAL", "Starting virtual cash", cash)), snapshots = listOf(PracticeSnapshot(now, cash, cash)))
    }
    fun addCash(amount: Double): PracticeState = update { s ->
        require(s.enabled && amount.isFinite() && amount >= 1000 && s.contributed + amount <= PracticeEngine.MAX_CASH) { "Enter at least KSh 1,000 within the KSh 1 trillion account limit." }
        val cash = PracticeEngine.money(amount); val now = System.currentTimeMillis()
        PracticeEngine.snapshot(s.copy(cash = PracticeEngine.money(s.cash + cash), contributed = PracticeEngine.money(s.contributed + cash), entries = s.entries + PracticeEntry(UUID.randomUUID().toString(), now, "CAPITAL", "Virtual cash added • Not investment profit", cash)), now)
    }
    fun reset(): PracticeState = synchronized(lock) {
        check(prefs.edit().clear().commit()) { "Could not reset the account." }; PracticeState()
    }
    private fun migrate(): PracticeState {
        if (!prefs.getBoolean("enabled", false)) return PracticeState()
        val holdings = array(prefs.getString("holdings", "[]")).mapObjects { PracticeHolding(it.getString("symbol"), it.getLong("shares"), PracticeEngine.money(it.getLong("shares") * it.getDouble("averageCost")), true) }
        val contributions = array(prefs.getString("contributions", "[]")).mapObjects { it.getDouble("amount") }
        val cash = prefs.getFloat("cash", 0f).toDouble()
        val total = contributions.sum().takeIf { it > 0 } ?: prefs.getFloat("initial", 0f).toDouble()
        val old = array(prefs.getString("trades", "[]"))
        val now = System.currentTimeMillis()
        val entries = (0 until old.length()).map { PracticeEntry("legacy-$it", now, "LEGACY", old.getString(it)) }
        // Historical totals in the previous version could use cost as a missing quote.
        // Preserve that old history in its original key; do not certify it as a new valuation.
        return PracticeState(true, cash, total, holdings, entries = entries + PracticeEntry("migration", now, "INFO", "Existing practice account retained. Older cost bases exclude buy fees; old activity dates appear in their text."))
    }
    private fun array(raw: String?) = JSONArray(raw ?: "[]")
    private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }
    private fun <T> arrayOf(items: List<T>, block: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(block(it)) } }
    private fun decode(o: JSONObject): PracticeState = PracticeState(
        enabled = o.getBoolean("enabled"), cash = o.getDouble("cash"), contributed = o.getDouble("contributed"),
        holdings = o.getJSONArray("holdings").mapObjects { PracticeHolding(it.getString("symbol"), it.getLong("shares"), it.getDouble("cost"), it.optBoolean("legacy")) },
        orders = o.getJSONArray("orders").mapObjects {
            PracticeOrder(
                it.getString("id"),
                it.getString("symbol"),
                it.getString("side"),
                it.getLong("shares"),
                it.getDouble("limit"),
                it.getLong("created"),
                it.optString("note"),
                it.getString("status"),
                it.optString("reason"),
                it.optLong("filledAt"),
                it.optDouble("price", 0.0),
                it.optDouble("fee", 0.0),
                it.optDouble("realised", 0.0),
                it.optString("quoteAt"),
                decodeDecisionSnapshot(it.optJSONObject("decisionSnapshot"))
            )
        },
        quotes = o.getJSONArray("quotes").mapObjects { PracticeQuote(it.getString("symbol"), it.getDouble("price"), it.getString("at"), it.optString("name"), it.optString("sector", "Other")) },
        entries = o.getJSONArray("entries").mapObjects { PracticeEntry(it.getString("id"), it.getLong("time"), it.getString("kind"), it.getString("text"), it.optDouble("amount", 0.0), it.optString("symbol")) },
        snapshots = o.getJSONArray("snapshots").mapObjects { PracticeSnapshot(it.getLong("time"), it.getDouble("value"), it.getDouble("contributed")) }
    )
    private fun decodeDecisionSnapshot(o: JSONObject?): PracticeDecisionSnapshot {
        if (o == null) return PracticeDecisionSnapshot()
        val evidence = o.optJSONArray("evidence")?.mapObjects {
            PracticeDecisionEvidence(
                id = it.optString("id"),
                title = it.optString("title"),
                detail = it.optString("detail"),
                source = it.optString("source"),
                time = it.optString("time")
            )
        }.orEmpty()
        return PracticeDecisionSnapshot(
            capturedAt = o.optLong("capturedAt", 0L),
            quotePrice = if (o.has("quotePrice")) o.optDouble("quotePrice", Double.NaN) else Double.NaN,
            quoteObservedAt = o.optString("quoteObservedAt"),
            quoteSource = o.optString("quoteSource"),
            quoteDelayMinutes = if (o.has("quoteDelayMinutes") && !o.isNull("quoteDelayMinutes")) o.optInt("quoteDelayMinutes") else null,
            dailyChangePct = if (o.has("dailyChangePct") && !o.isNull("dailyChangePct")) o.optDouble("dailyChangePct") else null,
            previousClose = if (o.has("previousClose") && !o.isNull("previousClose")) o.optDouble("previousClose") else null,
            evidence = evidence
        )
    }

    private fun encodeDecisionSnapshot(snapshot: PracticeDecisionSnapshot) = JSONObject().apply {
        put("capturedAt", snapshot.capturedAt)
        if (snapshot.quotePrice.isFinite()) put("quotePrice", snapshot.quotePrice)
        put("quoteObservedAt", snapshot.quoteObservedAt)
        put("quoteSource", snapshot.quoteSource)
        snapshot.quoteDelayMinutes?.let { put("quoteDelayMinutes", it) }
        snapshot.dailyChangePct?.takeIf { it.isFinite() }?.let { put("dailyChangePct", it) }
        snapshot.previousClose?.takeIf { it.isFinite() }?.let { put("previousClose", it) }
        put("evidence", arrayOf(snapshot.evidence) {
            JSONObject()
                .put("id", it.id)
                .put("title", it.title)
                .put("detail", it.detail)
                .put("source", it.source)
                .put("time", it.time)
        })
    }

    private fun encode(s: PracticeState) = JSONObject().apply {
        put("version", 3); put("enabled", s.enabled); put("cash", s.cash); put("contributed", s.contributed)
        put("holdings", arrayOf(s.holdings) { JSONObject().put("symbol", it.symbol).put("shares", it.shares).put("cost", it.cost).put("legacy", it.legacy) })
        put("orders", arrayOf(s.orders) {
            JSONObject()
                .put("id", it.id)
                .put("symbol", it.symbol)
                .put("side", it.side)
                .put("shares", it.shares)
                .put("limit", it.limit)
                .put("created", it.created)
                .put("note", it.note)
                .put("status", it.status)
                .put("reason", it.reason)
                .put("filledAt", it.filledAt)
                .put("price", it.price)
                .put("fee", it.fee)
                .put("realised", it.realised)
                .put("quoteAt", it.quoteAt)
                .put("decisionSnapshot", encodeDecisionSnapshot(it.decisionSnapshot))
        })
        put("quotes", arrayOf(s.quotes) { JSONObject().put("symbol", it.symbol).put("price", it.price).put("at", it.at).put("name", it.name).put("sector", it.sector) })
        put("entries", arrayOf(s.entries) { JSONObject().put("id", it.id).put("time", it.time).put("kind", it.kind).put("text", it.text).put("amount", it.amount).put("symbol", it.symbol) })
        put("snapshots", arrayOf(s.snapshots) { JSONObject().put("time", it.time).put("value", it.value).put("contributed", it.contributed) })
    }
}
