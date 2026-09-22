package ke.co.nsewatcher

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalTime
import java.math.BigDecimal
import java.math.RoundingMode

internal data class PracticeHolding(val symbol: String, val shares: Long, val cost: Double, val legacy: Boolean = false)
internal data class PracticeQuote(val symbol: String, val price: Double, val at: String, val name: String = "", val sector: String = "Other")
internal data class PracticeOrder(val id: String, val symbol: String, val side: String, val shares: Long, val limit: Double,
    val created: Long, val note: String = "", val status: String = "PENDING", val reason: String = "Waiting for an eligible quote",
    val filledAt: Long = 0, val price: Double = 0.0, val fee: Double = 0.0, val realised: Double = 0.0, val quoteAt: String = "")
internal data class PracticeEntry(val id: String, val time: Long, val kind: String, val text: String, val amount: Double = 0.0, val symbol: String = "")
internal data class PracticeSnapshot(val time: Long, val value: Double, val contributed: Double)
internal data class PracticeState(val enabled: Boolean = false, val cash: Double = 0.0, val contributed: Double = 0.0,
    val holdings: List<PracticeHolding> = emptyList(), val orders: List<PracticeOrder> = emptyList(),
    val quotes: List<PracticeQuote> = emptyList(), val entries: List<PracticeEntry> = emptyList(), val snapshots: List<PracticeSnapshot> = emptyList())
internal data class PracticeValuation(val holding: PracticeHolding, val quote: PracticeQuote?) {
    val estimated get() = quote == null
    val value get() = quote?.let { PracticeEngine.money(it.price * holding.shares) } ?: holding.cost
    val gain get() = value - holding.cost
}

internal object PracticeEngine {
    const val FEE = 0.02
    const val MAX_CASH = 1_000_000_000_000.0
    fun money(value: Double): Double = if (value.isFinite()) BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble() else value
    fun reserved(s: PracticeState, except: String = "") = s.orders.filter { it.status == "PENDING" && it.side == "BUY" && it.id != except }.sumOf { money(it.shares * it.limit) + money(it.shares * it.limit * FEE) }
    fun available(s: PracticeState, except: String = "") = money(s.cash - reserved(s, except))
    fun reservedShares(s: PracticeState, symbol: String, except: String = "") = s.orders.filter { it.status == "PENDING" && it.side == "SELL" && it.symbol == symbol && it.id != except }.sumOf { it.shares }
    fun positions(s: PracticeState) = s.holdings.map { h -> PracticeValuation(h, s.quotes.firstOrNull { it.symbol == h.symbol }) }
    fun value(s: PracticeState) = money(s.cash + positions(s).sumOf { it.value })
    fun validate(s: PracticeState, o: PracticeOrder): String? {
        if (!s.enabled) return "Create your practice portfolio first."
        if (o.side !in listOf("BUY", "SELL") || o.symbol.isBlank()) return "Select a company and order side."
        if (o.shares <= 0 || o.shares > 1_000_000_000L) return "Enter between 1 and 1,000,000,000 whole shares."
        if (!o.limit.isFinite() || o.limit < 0.01 || o.limit > 1_000_000) return "Enter a limit price between KSh 0.01 and KSh 1,000,000."
        if (kotlin.math.abs(o.limit - money(o.limit)) > 0.000001) return "Use at most two decimal places for the practice limit."
        val gross = money(o.shares * o.limit)
        if (!gross.isFinite() || gross > MAX_CASH) return "This practice order is too large."
        if (o.side == "BUY" && gross + money(gross * FEE) > available(s, o.id) + 0.000001) return "Not enough unreserved practice cash."
        if (o.side == "SELL" && o.shares > (s.holdings.firstOrNull { it.symbol == o.symbol }?.shares ?: 0) - reservedShares(s, o.symbol, o.id)) return "Not enough unreserved shares to sell."
        return null
    }
    fun submit(s: PracticeState, o: PracticeOrder): PracticeState {
        require(validate(s, o) == null) { validate(s, o).orEmpty() }
        val old = s.orders.firstOrNull { it.id == o.id }
        require(old == null || old.status == "PENDING") { "Only pending orders can be edited." }
        return s.copy(orders = s.orders.filterNot { it.id == o.id } + o.copy(status = "PENDING", filledAt = 0, price = 0.0, fee = 0.0, realised = 0.0))
    }
    fun cancel(s: PracticeState, id: String) = s.copy(orders = s.orders.map { if (it.id == id && it.status == "PENDING") it.copy(status = "CANCELLED", reason = "Cancelled by you; reservations released") else it })
    fun waiting(o: PracticeOrder, q: Stock?, open: Boolean, known: Boolean, now: Long): String? {
        if (!known) return "Waiting for verified market status"
        if (!open) return "Waiting for market to open"
        if (q == null || !q.price.isFinite() || q.price <= 0) return "Waiting for a valid company quote"
        val at = runCatching { Instant.parse(q.observedAt) }.getOrNull() ?: return "Waiting for a timed quote"
        if (at.toEpochMilli() < o.created) return "Waiting for a quote observed after your order"
        if (at.toEpochMilli() > now || now - at.toEpochMilli() > 30 * 60_000L) return "Waiting for a recent eligible quote"
        val local = at.atZone(CompanyResearchPresentation.zone)
        val today = Instant.ofEpochMilli(now).atZone(CompanyResearchPresentation.zone).toLocalDate()
        if (local.toLocalDate() != today || local.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) || local.toLocalTime() < LocalTime.of(9, 30) || local.toLocalTime() > LocalTime.of(15, 0)) return "Waiting for a continuous-session quote"
        if ((o.side == "BUY" && q.price > o.limit) || (o.side == "SELL" && q.price < o.limit)) return "Waiting for your limit price"
        return null
    }
    fun evaluate(initial: PracticeState, quotes: List<Stock>, open: Boolean, known: Boolean, now: Long): PracticeState {
        var s = initial
        for (order in initial.orders.filter { it.status == "PENDING" }.sortedBy { it.created }) {
            val q = quotes.firstOrNull { it.symbol.equals(order.symbol, true) }
            val reason = waiting(order, q, open, known, now) ?: validate(s, order)
            if (reason != null) { s = s.copy(orders = s.orders.map { if (it.id == order.id) it.copy(reason = reason) else it }); continue }
            val price = q!!.price
            val gross = money(price * order.shares)
            val fee = money(gross * FEE)
            val old = s.holdings.firstOrNull { it.symbol == order.symbol }
            val remaining = s.holdings.filterNot { it.symbol == order.symbol }.toMutableList()
            var realised = 0.0
            val cash = if (order.side == "BUY") {
                remaining += PracticeHolding(order.symbol, (old?.shares ?: 0) + order.shares, money((old?.cost ?: 0.0) + gross + fee), old?.legacy ?: false)
                money(s.cash - gross - fee)
            } else {
                val allocated = money(old!!.cost * order.shares / old.shares)
                realised = money(gross - fee - allocated)
                if (old.shares > order.shares) remaining += old.copy(shares = old.shares - order.shares, cost = money(old.cost - allocated))
                money(s.cash + gross - fee)
            }
            val filled = order.copy(status = "FILLED", reason = "Simulated fill from an eligible observed quote", filledAt = now, price = price, fee = fee, realised = realised, quoteAt = q.observedAt)
            s = s.copy(cash = cash, holdings = remaining, orders = s.orders.map { if (it.id == order.id) filled else it },
                entries = s.entries + PracticeEntry(order.id, now, "TRADE", "${order.side} ${order.shares} ${order.symbol} @ ${CompanyResearchPresentation.money(price)}", if (order.side == "BUY") -gross-fee else gross-fee, order.symbol))
        }
        return s
    }
    fun observe(s: PracticeState, stocks: List<Stock>, now: Long): PracticeState {
        val map = s.quotes.associateBy { it.symbol }.toMutableMap()
        stocks.forEach { stock ->
            val at = CompanyResearchPresentation.timestamp(stock.observedAt)
            val prior = map[stock.symbol]?.let { CompanyResearchPresentation.timestamp(it.at) }
            if (stock.price.isFinite() && stock.price > 0 && at != null && at.toEpochMilli() <= now && (prior == null || at >= prior))
                map[stock.symbol] = PracticeQuote(stock.symbol, stock.price, stock.observedAt, stock.name, stock.sector)
        }
        return s.copy(quotes = map.values.toList())
    }
    fun snapshot(s: PracticeState, now: Long): PracticeState {
        if (!s.enabled || positions(s).any { it.estimated }) return s
        val v = value(s)
        if (s.snapshots.lastOrNull()?.let { it.value == v && it.contributed == s.contributed } == true) return s
        return s.copy(snapshots = (s.snapshots + PracticeSnapshot(now, v, s.contributed)).takeLast(4000))
    }
}
