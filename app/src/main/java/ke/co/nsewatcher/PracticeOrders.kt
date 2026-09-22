package ke.co.nsewatcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import java.util.UUID

@Composable internal fun PracticeOrderTicket(s: PracticeState, stock: Stock, side: String, editId: String,
    market: MyStocksCache.MarketStatus, working: Boolean, onSubmit: (PracticeOrder) -> Unit, onSide: (String) -> Unit) {
    val editing = s.orders.firstOrNull { it.id == editId }
    var quantity by rememberSaveable(stock.symbol, editId) { mutableStateOf(editing?.shares?.toString() ?: "1") }
    var limit by rememberSaveable(stock.symbol, editId) { mutableStateOf(editing?.limit?.toString() ?: stock.price.takeIf { it.isFinite() && it > 0 }?.let { PracticeEngine.money(it).toString() }.orEmpty()) }
    var note by rememberSaveable(stock.symbol, editId) { mutableStateOf(editing?.note.orEmpty()) }
    var review by remember { mutableStateOf<PracticeOrder?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var typeHelp by rememberSaveable { mutableStateOf(false) }
    val shares = quantity.toLongOrNull() ?: 0L
    val price = limit.toDoubleOrNull() ?: Double.NaN
    val gross = PracticeEngine.money(shares * price)
    val fee = PracticeEngine.money(gross * PracticeEngine.FEE)
    val validAmount = shares > 0 && price.isFinite() && price > 0 && gross.isFinite()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { PracticeCompanyIcon(stock); Column { ResearchTitle(stock.name); ResearchCaption(stock.symbol) } } }
            item { MarketChoiceRow(listOf("Buy", "Sell"), if (side == "BUY") "Buy" else "Sell") { onSide(it.uppercase()); error = null } }
            item { ResearchPanel {
                ResearchCaption("Last observed price"); ResearchTitle(stock.price.takeIf { it.isFinite() && it > 0 }?.let(::practiceMoney) ?: "Unavailable")
                ResearchCaption(CompanyResearchPresentation.date(stock.observedAt)); ResearchCaption("Delayed observations • not an executable broker quote")
            } }
            item { ResearchPanel {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { PracticeIcon(Icons.Outlined.Schedule, true); Text(when { !market.isKnown -> "Market status unavailable"; !market.isOpen -> "Market closed"; else -> "Wait for a new eligible quote" }, color = PracticeAmber, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) }
                ResearchCaption("Your order waits for an eligible session quote observed after confirmation. Nothing fills at an old quote.")
            } }
            item {
                OutlinedButton(onClick = { typeHelp = !typeHelp }, modifier = Modifier.fillMaxWidth()) { Text("Limit order ⌄") }
                ResearchCaption(if (side == "BUY") "Buy only at your limit price or lower." else "Sell only at your limit price or higher.")
                if (typeHelp) ResearchCaption("This simulator supports limit orders only. Orders remain pending until filled or cancelled; real order-book liquidity and settlement delays are not simulated.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(10) }, label = { Text("Whole shares") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(limit, { limit = it }, label = { Text("Limit (KSh)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row { TextButton(onClick = { quantity = (shares - 1).coerceAtLeast(1).toString() }) { Text("− 1 share") }; TextButton(onClick = { quantity = (shares + 1).coerceAtMost(1_000_000_000L).toString() }) { Text("+ 1 share") } }
            }
            item { ResearchPanel {
                ResearchTitle("Order summary")
                PracticeLine(if (side == "BUY") "Maximum share cost" else "Share value at limit", if (validAmount) practiceMoney(gross) else "—")
                PracticeLine("Practice fee estimate", if (validAmount) practiceMoney(fee) else "—")
                PracticeLine(if (side == "BUY") "Maximum cash reserved" else "Net proceeds at limit", if (validAmount) practiceMoney(if (side == "BUY") gross + fee else gross - fee) else "—")
                ResearchCaption("2% simulation assumption • Not a broker quote")
                if (side == "SELL") ResearchCaption("${shares.coerceAtLeast(0)} shares will be reserved; proceeds depend on the eventual simulated fill.")
            } }
            item { PracticeLine("Unreserved cash", practiceMoney(PracticeEngine.available(s, editId))); if (side == "SELL") PracticeLine("Unreserved shares", ((s.holdings.firstOrNull { it.symbol == stock.symbol }?.shares ?: 0) - PracticeEngine.reservedShares(s, stock.symbol, editId)).toString()) }
            item { OutlinedTextField(note, { note = it.take(2000) }, label = { Text("Why this trade? (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            if (error != null) item { Text(error.orEmpty(), color = PracticeAmber) }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(enabled = !working, onClick = {
                val order = PracticeOrder(editId.ifBlank { UUID.randomUUID().toString() }, stock.symbol, side, shares, price, System.currentTimeMillis(), note.trim())
                error = PracticeEngine.validate(s, order)
                if (error == null) review = order
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (editId.isBlank()) "Review practice order" else "Review order changes") }
            ResearchCaption("Nothing is sent to a broker. No fill is guaranteed.")
        }
    }
    review?.let { order -> AlertDialog(onDismissRequest = { if (!working) review = null }, title = { Text("Confirm practice ${order.side.lowercase()}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${order.shares} ${order.symbol} shares • Limit ${practiceMoney(order.limit)}")
            Text(if (order.side == "BUY") "Reserve up to ${practiceMoney(PracticeEngine.money(order.shares * order.limit) + PracticeEngine.money(order.shares * order.limit * PracticeEngine.FEE))}, including the practice fee." else "Reserve ${order.shares} shares until filled or cancelled.")
            Text("This queues an order; it does not confirm a fill. Orders are checked while Practice Portfolio is active.")
        } }, confirmButton = { TextButton(enabled = !working, onClick = { review = null; onSubmit(order.copy(created = System.currentTimeMillis())) }) { Text("Confirm practice order") } }, dismissButton = { TextButton(onClick = { review = null }) { Text("Go back") } }) }
}

@Composable internal fun PracticeActivity(s: PracticeState, tab: String, onTab: (String) -> Unit,
    onCancel: (String) -> Unit, onEdit: (PracticeOrder) -> Unit, onDetails: (String) -> Unit,
    onNote: () -> Unit, onRules: () -> Unit, working: Boolean) {
    var cancelling by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MarketChoiceRow(listOf("Orders", "Transactions", "Notes"), tab, onTab)
        when (tab) {
            "Orders" -> {
                ResearchTitle("Your orders"); ResearchCaption("${s.orders.count { it.status == "PENDING" }} pending")
                if (s.orders.isEmpty()) ResearchCaption("Your practice orders will appear here after confirmation.")
                s.orders.sortedWith(compareBy<PracticeOrder> { it.status != "PENDING" }.thenByDescending { it.created }).forEach { o ->
                    ResearchPanel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { PracticeIcon(if (o.status == "PENDING") Icons.Outlined.Schedule else if (o.status == "FILLED") Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, o.status == "PENDING"); Column(Modifier.weight(1f)) { ResearchBody("${o.symbol} • ${o.side.lowercase().replaceFirstChar { it.uppercase() }} ${o.shares} shares"); Text(o.status.lowercase().replaceFirstChar { it.uppercase() }, color = if (o.status == "PENDING") PracticeAmber else ResearchMuted, fontSize = 12.sp) } }
                        PracticeLine("Limit price", practiceMoney(o.limit))
                        if (o.status == "PENDING") {
                            PracticeLine(if (o.side == "BUY") "Reserved cash" else "Reserved shares", if (o.side == "BUY") practiceMoney(PracticeEngine.money(o.limit * o.shares) + PracticeEngine.money(o.limit * o.shares * PracticeEngine.FEE)) else o.shares.toString())
                            ResearchCaption(o.reason); ResearchCaption("Your shares have not changed for this order.")
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { OutlinedButton(enabled = !working, onClick = { onEdit(o) }) { Text("Edit order") }; OutlinedButton(enabled = !working, onClick = { cancelling = o.id }) { Text("Cancel order", color = ResearchRed) } }
                        } else ResearchCaption(o.reason)
                        TextButton(onClick = { onDetails(o.id) }) { Text("View price and costs →") }
                    }
                }
                ResearchPanel { ResearchTitle("How practice fills work"); ResearchCaption("We check eligible quotes against your limit. Real queue position and liquidity are not reproduced."); TextButton(onClick = onRules) { Text("Read the simulation rules →") } }
                ResearchTitle("Completed activity")
                s.entries.filter { it.kind != "NOTE" }.takeLast(3).reversed().forEach { PracticeEntryCard(it) }
            }
            "Transactions" -> {
                ResearchTitle("Account activity")
                if (s.entries.none { it.kind != "NOTE" }) ResearchCaption("No transactions yet.")
                s.entries.filter { it.kind != "NOTE" }.reversed().forEach { e -> PracticeEntryCard(e); if (e.kind == "TRADE") TextButton(onClick = { onDetails(e.id) }) { Text("View trade receipt →") } }
            }
            else -> {
                ResearchTitle("Trade journal")
                val notes = s.entries.filter { it.kind == "NOTE" }
                val orders = s.orders.filter { it.note.isNotBlank() }
                if (notes.isEmpty() && orders.isEmpty()) ResearchCaption("Write why you bought and what would change your mind.")
                notes.reversed().forEach { PracticeEntryCard(it) }
                orders.reversed().forEach { o -> ResearchPanel { ResearchCaption("${o.symbol} • ${practiceTime(o.created)}"); ResearchBody(o.note) } }
            }
        }
        ResearchPanel { PracticeIcon(Icons.Outlined.MenuBook); ResearchTitle("Trade journal"); ResearchCaption("Record why you bought. Review what changed."); TextButton(onClick = onNote) { Text("Add a note →") } }
    }
    cancelling?.let { id -> AlertDialog(onDismissRequest = { cancelling = null }, title = { Text("Cancel this practice order?") }, text = { Text("Reserved cash or shares will become available again. Filled trades are not reversed.") }, confirmButton = { TextButton(enabled = !working, onClick = { cancelling = null; onCancel(id) }) { Text("Cancel order") } }, dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep order") } }) }
}
@Composable private fun PracticeEntryCard(e: PracticeEntry) {
    ResearchPanel {
        ResearchCaption(if (e.kind == "LEGACY") "Legacy activity • Date retained below" else practiceTime(e.time))
        ResearchBody(e.text)
        if (e.amount != 0.0) Text(practiceGain(e.amount), color = if (e.kind == "CAPITAL") ResearchText else researchChangeColor(e.amount), fontSize = 15.sp)
        if (e.kind == "CAPITAL") ResearchCaption("Contribution • Not investment profit")
        if (e.symbol.isNotBlank() && e.kind == "NOTE") ResearchCaption(e.symbol)
    }
}
@Composable internal fun PracticeOrderReceipt(o: PracticeOrder) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PracticeBadge(); ResearchTitle("${o.side} ${o.shares} ${o.symbol}"); ResearchBody(o.status)
        PracticeLine("Limit", practiceMoney(o.limit)); ResearchCaption("Submitted ${practiceTime(o.created)}")
        if (o.status == "FILLED") {
            PracticeLine("Simulated fill price", practiceMoney(o.price)); PracticeLine("Share value", practiceMoney(PracticeEngine.money(o.price * o.shares)))
            PracticeLine("Practice fee", practiceMoney(o.fee)); PracticeLine(if (o.side == "BUY") "Cash debited" else "Cash credited", practiceMoney(PracticeEngine.money(o.price * o.shares) + if (o.side == "BUY") o.fee else -o.fee))
            if (o.side == "SELL") PracticeLine("Realised gain / loss", practiceGain(o.realised))
            ResearchCaption("Filled ${practiceTime(o.filledAt)}"); ResearchCaption("Quote observed ${CompanyResearchPresentation.date(o.quoteAt)}")
        }
        ResearchCaption(o.reason)
        if (o.note.isNotBlank()) { ResearchTitle("Your reason"); ResearchBody(o.note) }
        ResearchCaption("Simulation only • 2% assumed fee per trade. Legacy cost bases may exclude earlier buy fees.")
    }
}
