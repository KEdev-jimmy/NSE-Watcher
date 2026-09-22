package ke.co.nsewatcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchlistAlertsSheet(
    store: AlertStore, alerts: List<PriceAlert>, companies: List<Stock>, initialSymbol: String?,
    statusFor: (PriceAlert) -> String, dismiss: () -> Unit, onSaved: () -> Unit,
    preferences: () -> Unit, phoneSettings: () -> Unit, notificationsEnabled: Boolean
) {
    var filter by remember { mutableStateOf(initialSymbol) }
    var form by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PriceAlert?>(null) }
    var symbol by remember { mutableStateOf(initialSymbol ?: companies.firstOrNull()?.symbol.orEmpty()) }
    var type by remember { mutableStateOf(AlertType.PRICE_ABOVE) }
    var threshold by remember { mutableStateOf("") }
    var symbolMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PriceAlert?>(null) }
    val scope = rememberCoroutineScope()
    fun mutate(action: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try { action(); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not save this change. Please try again." }
            finally { saving = false }
        }
    }
    fun edit(rule: PriceAlert?) {
        editing = rule
        symbol = rule?.symbol ?: filter ?: companies.firstOrNull()?.symbol.orEmpty()
        type = rule?.type?.takeIf { it in WatchlistPresentation.supportedTypes } ?: AlertType.PRICE_ABOVE
        threshold = rule?.threshold?.toString().orEmpty()
        error = null
        form = true
    }
    val rules = alerts.filter { filter == null || WatchlistPresentation.symbol(it.symbol) == filter }
    ModalBottomSheet(onDismissRequest = { if (!saving) dismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = ResearchBackground) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                ResearchTitle(if (filter == null) "Manage alerts" else "$filter alerts")
                ResearchCaption("Saved rules use the existing background alert service.")
                if (filter != null) TextButton(onClick = { filter = null }) { Text("Show all alerts", color = ResearchGreen) }
            }
            if (error != null) item { Text(error.orEmpty(), color = ResearchRed) }
            item {
                if (!notificationsEnabled) {
                    ResearchCaption("Android notifications are off. Enable them to receive your alerts.")
                    TextButton(onClick = phoneSettings) { Text("Open notification settings", color = ResearchGreen) }
                }
                TextButton(onClick = preferences) { Text("Alert preferences →", color = ResearchGreen) }
            }
            if (form) item {
                ResearchPanel {
                    ResearchTitle(if (editing == null) "Create an alert" else "Edit alert")
                    Box {
                        OutlinedButton(onClick = { symbolMenu = true }, enabled = !saving) { Text("Company: ${symbol.ifBlank { "Choose company" }}") }
                        DropdownMenu(expanded = symbolMenu, onDismissRequest = { symbolMenu = false }) {
                            (companies.map { it.symbol } + listOfNotNull(editing?.symbol)).distinct().sorted().forEach { key ->
                                DropdownMenuItem(text = { Text(key) }, onClick = { symbol = key; symbolMenu = false })
                            }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { typeMenu = true }, enabled = !saving) { Text(WatchlistPresentation.typeLabel(type)) }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            WatchlistPresentation.supportedTypes.forEach { option ->
                                DropdownMenuItem(text = { Text(WatchlistPresentation.typeLabel(option)) }, onClick = { type = option; typeMenu = false })
                            }
                        }
                    }
                    if (WatchlistPresentation.needsThreshold(type)) {
                        val unit = if (type in setOf(AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW)) "Price (KSh)" else if (type == AlertType.HIGH_VOLUME) "Percent above average volume" else "Daily change (%)"
                        OutlinedTextField(threshold, { threshold = it }, Modifier.fillMaxWidth(), enabled = !saving,
                            label = { Text(unit) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        ResearchCaption("Enter a number greater than zero.")
                    }
                    ResearchCaption(when (type) {
                        AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> "Triggers when successive available quotes cross your price. The first check establishes a baseline."
                        AlertType.HIGH_VOLUME -> "Requires current volume and an available provider average. For example, 50 means 50% above average."
                        AlertType.NEWS, AlertType.CORPORATE_ACTION -> "Checks company-linked items from the current Nairobi trading day."
                        else -> "Uses the provider’s reported daily percentage change."
                    })
                    if (editing?.enabled == false) ResearchCaption("This rule will stay paused after editing. Use its switch to resume it.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val value = if (WatchlistPresentation.needsThreshold(type)) WatchlistPresentation.threshold(threshold) else null
                            if (symbol.isBlank() || (WatchlistPresentation.needsThreshold(type) && value == null)) {
                                error = "Choose a company and enter a valid positive threshold."
                            } else {
                                val rule = PriceAlert(editing?.id ?: UUID.randomUUID().toString(), symbol, type, value, editing?.enabled ?: true)
                                mutate { store.save(rule); form = false; onSaved() }
                            }
                        }, enabled = !saving) { Text(if (saving) "Saving…" else "Save alert") }
                        TextButton(onClick = { form = false; error = null }, enabled = !saving) { Text("Cancel") }
                    }
                }
            } else item {
                Button(onClick = { edit(null) }, enabled = !saving && companies.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create alert")
                }
                if (companies.isEmpty()) ResearchCaption("Add a company to your watchlist to create a new alert.")
            }
            item {
                ResearchCaption("Checks run about every 15 minutes while the market is open, subject to Android scheduling. Alerts are not real-time price guarantees.")
                if (rules.isEmpty()) ResearchBody("No rules saved here yet.")
            }
            items(rules, key = { it.id }) { rule ->
                ResearchPanel {
                    ResearchBody(WatchlistPresentation.ruleLabel(rule))
                    ResearchCaption(statusFor(rule))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { edit(rule) }, enabled = !saving) { Text("Edit", color = ResearchGreen) }
                        IconButton(onClick = { deleting = rule }, enabled = !saving) { Icon(Icons.Default.DeleteOutline, "Delete ${WatchlistPresentation.ruleLabel(rule)}", tint = ResearchMuted) }
                        Spacer(Modifier.weight(1f))
                        Text(if (rule.enabled) "On" else "Paused", color = ResearchMuted)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = rule.enabled, onCheckedChange = { enabled -> mutate { store.setEnabled(rule.id, enabled); if (enabled) onSaved() } }, enabled = !saving && rule.type in WatchlistPresentation.supportedTypes)
                    }
                }
            }
            item { TextButton(onClick = dismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Done", color = ResearchGreen) } }
        }
    }
    deleting?.let { rule ->
        AlertDialog(onDismissRequest = { if (!saving) deleting = null }, containerColor = ResearchCard,
            title = { Text("Delete this alert?") }, text = { Text(WatchlistPresentation.ruleLabel(rule)) },
            confirmButton = { TextButton(onClick = { mutate { store.remove(rule.id); if (editing?.id == rule.id) form = false; deleting = null } }, enabled = !saving) { Text("Delete", color = ResearchRed) } },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !saving) { Text("Keep alert") } })
    }
}
