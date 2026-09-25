package ke.co.nsewatcher

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.data.WatchlistStore
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PremiumAlertRulesScreen(
    catalog: List<Stock>,
    quotes: List<Stock>,
    back: () -> Unit
) {
    val context = LocalContext.current
    val alertStore = remember { AlertStore(context) }
    val watchlistStore = remember { WatchlistStore(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val alerts by alertStore.alerts.collectAsState(initial = emptyList())
    val watchedSymbols by watchlistStore.symbols.collectAsState(initial = emptyList())
    val companies = remember(watchedSymbols, catalog, quotes) {
        WatchlistPresentation.companies(watchedSymbols, catalog, quotes)
    }

    var selectedCompanyFilter by rememberSaveable { mutableStateOf("") }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf("") }
    var selectedSymbol by rememberSaveable { mutableStateOf("") }
    var selectedTypeName by rememberSaveable { mutableStateOf(AlertType.PRICE_ABOVE.name) }
    var thresholdText by rememberSaveable { mutableStateOf("") }
    var symbolMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PriceAlert?>(null) }
    var notificationsEnabled by remember { mutableStateOf(false) }

    val selectedType = runCatching { AlertType.valueOf(selectedTypeName) }
        .getOrDefault(AlertType.PRICE_ABOVE)
    val editing = alerts.firstOrNull { it.id == editingId }
    val filteredRules = remember(alerts, selectedCompanyFilter) {
        alerts.filter {
            selectedCompanyFilter.isBlank() ||
                WatchlistPresentation.symbol(it.symbol) == selectedCompanyFilter
        }.sortedWith(
            compareByDescending<PriceAlert> { it.enabled }
                .thenBy { WatchlistPresentation.symbol(it.symbol) }
                .thenBy { it.type.name }
        )
    }

    fun refreshNotificationState() {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshNotificationState()
    }

    LaunchedEffect(Unit) {
        refreshNotificationState()
    }

    fun openPhoneNotificationSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        }
    }

    fun openCreate(symbol: String = "") {
        editingId = ""
        selectedSymbol = symbol.ifBlank { selectedCompanyFilter }
            .ifBlank { companies.firstOrNull()?.symbol.orEmpty() }
        selectedTypeName = AlertType.PRICE_ABOVE.name
        thresholdText = ""
        formOpen = true
    }

    fun openEdit(rule: PriceAlert) {
        editingId = rule.id
        selectedSymbol = WatchlistPresentation.symbol(rule.symbol)
        selectedTypeName = rule.type.name
        thresholdText = rule.threshold?.toString().orEmpty()
        formOpen = true
    }

    fun closeForm() {
        formOpen = false
        editingId = ""
        thresholdText = ""
    }

    fun mutate(block: suspend () -> Unit, success: String? = null) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                block()
                AlertWorker.schedule(context)
                if (success != null) snackbar.showSnackbar(success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar("Could not save this alert change. Please try again.")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = ResearchBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = back) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = ResearchText)
                    }
                    NseWatcherBrandLockup(
                        modifier = Modifier.weight(1f),
                        compact = true
                    )
                }
            }

            item {
                Text(
                    "Alert Rules",
                    color = ResearchText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Choose the NSE events you want monitored for companies you follow.",
                    color = ResearchMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }

            item {
                AlertRulesSummary(
                    activeRules = alerts.count { it.enabled },
                    watchedCompanies = companies.size,
                    notificationsEnabled = notificationsEnabled
                )
            }

            if (!notificationsEnabled) {
                item {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.07f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
                        )
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Android notifications are off",
                                    color = ResearchText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Rules remain saved, but the app cannot show their notifications.",
                                    color = ResearchMuted,
                                    fontSize = 9.5.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (
                                        Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        openPhoneNotificationSettings()
                                    }
                                }
                            ) {
                                Text("Enable", color = ResearchGreen)
                            }
                        }
                    }
                }
            }

            item {
                AlertRulesExplainer()
            }

            if (companies.isEmpty()) {
                item {
                    ResearchPanel {
                        Icon(
                            Icons.Default.StarBorder,
                            null,
                            tint = ResearchGreen,
                            modifier = Modifier.size(30.dp)
                        )
                        ResearchTitle("Follow a company first")
                        ResearchCaption(
                            "Alert rules are created only for companies in your Watchlist. " +
                                "Add a company from Companies or My Watchlist, then return here."
                        )
                    }
                }
            } else {
                item {
                    Button(
                        onClick = { openCreate() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ResearchGreen,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.AddAlert, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Create alert rule", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (companies.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        AlertFilterChip(
                            label = "All",
                            selected = selectedCompanyFilter.isBlank()
                        ) {
                            selectedCompanyFilter = ""
                        }
                        companies.forEach { stock ->
                            AlertFilterChip(
                                label = stock.symbol,
                                selected = selectedCompanyFilter == stock.symbol
                            ) {
                                selectedCompanyFilter =
                                    if (selectedCompanyFilter == stock.symbol) "" else stock.symbol
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (selectedCompanyFilter.isBlank()) "Your rules" else "$selectedCompanyFilter rules",
                        Modifier.weight(1f),
                        color = ResearchText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        filteredRules.size.toString(),
                        color = ResearchMuted,
                        fontSize = 10.sp
                    )
                }
            }

            if (filteredRules.isEmpty()) {
                item {
                    ResearchPanel {
                        ResearchTitle("No alert rules here yet")
                        ResearchCaption(
                            if (selectedCompanyFilter.isBlank()) {
                                "Create a price, daily movement, volume, news or corporate-action rule."
                            } else {
                                "No saved rules currently target $selectedCompanyFilter."
                            }
                        )
                        if (companies.isNotEmpty()) {
                            TextButton(
                                onClick = { openCreate(selectedCompanyFilter) }
                            ) {
                                Text("Create a rule", color = ResearchGreen)
                            }
                        }
                    }
                }
            } else {
                items(filteredRules, key = { it.id }) { rule ->
                    AlertRuleCard(
                        rule = rule,
                        saving = saving,
                        onToggle = { enabled ->
                            mutate(
                                block = { alertStore.setEnabled(rule.id, enabled) },
                                success = if (enabled) "Alert resumed" else "Alert paused"
                            )
                        },
                        onEdit = { openEdit(rule) },
                        onDelete = { deleting = rule }
                    )
                }
            }

            item {
                Text(
                    "Background checks are scheduled about every 15 minutes, but Android may delay them. " +
                        "Price alerts require an open market and a quote no more than 30 minutes old. " +
                        "News and corporate-action checks can run after market hours and use up to a seven-day catch-up window.",
                    color = ResearchMuted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }

    if (formOpen) {
        ModalBottomSheet(
            onDismissRequest = { if (!saving) closeForm() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ResearchBackground
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ResearchTitle(if (editing == null) "Create alert" else "Edit alert")
                ResearchCaption(
                    "Rules use provider-backed observations. NSE Watcher does not invent missing prices, volume or events."
                )

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { symbolMenu = true },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selectedSymbol.ifBlank { "Choose company" },
                            Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = symbolMenu,
                        onDismissRequest = { symbolMenu = false }
                    ) {
                        companies.forEach { stock ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            stock.symbol,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            stock.name,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    selectedSymbol = stock.symbol
                                    symbolMenu = false
                                }
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { typeMenu = true },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            WatchlistPresentation.typeLabel(selectedType),
                            Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = typeMenu,
                        onDismissRequest = { typeMenu = false }
                    ) {
                        WatchlistPresentation.supportedTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(WatchlistPresentation.typeLabel(type)) },
                                onClick = {
                                    selectedTypeName = type.name
                                    if (!WatchlistPresentation.needsThreshold(type)) {
                                        thresholdText = ""
                                    }
                                    typeMenu = false
                                }
                            )
                        }
                    }
                }

                if (WatchlistPresentation.needsThreshold(selectedType)) {
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = {
                            Text(
                                when (selectedType) {
                                    AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> "Threshold price (KSh)"
                                    AlertType.HIGH_VOLUME -> "Percent above provider average"
                                    else -> "Daily change threshold (%)"
                                }
                            )
                        }
                    )
                    ResearchCaption("Enter a positive number.")
                }

                ResearchPanel {
                    Text(
                        alertRuleExplanation(selectedType),
                        color = ResearchMuted,
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = ::closeForm,
                        enabled = !saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val threshold = if (WatchlistPresentation.needsThreshold(selectedType)) {
                                WatchlistPresentation.threshold(thresholdText)
                            } else {
                                null
                            }
                            if (
                                selectedSymbol.isBlank() ||
                                (WatchlistPresentation.needsThreshold(selectedType) && threshold == null)
                            ) {
                                scope.launch {
                                    snackbar.showSnackbar(
                                        "Choose a company and enter a valid positive threshold."
                                    )
                                }
                            } else {
                                val next = PriceAlert(
                                    id = editing?.id ?: UUID.randomUUID().toString(),
                                    symbol = selectedSymbol,
                                    type = selectedType,
                                    threshold = threshold,
                                    enabled = editing?.enabled ?: true
                                )
                                mutate(
                                    block = { alertStore.save(next) },
                                    success = if (editing == null) "Alert rule created" else "Alert rule updated"
                                )
                                closeForm()
                                if (
                                    Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (saving) "Saving…" else "Save rule")
                    }
                }
            }
        }
    }

    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { if (!saving) deleting = null },
            containerColor = ResearchCard,
            title = { Text("Delete this alert rule?") },
            text = { Text(WatchlistPresentation.ruleLabel(rule)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        mutate(
                            block = { alertStore.remove(rule.id) },
                            success = "Alert rule deleted"
                        )
                        deleting = null
                        if (editingId == rule.id) closeForm()
                    },
                    enabled = !saving
                ) {
                    Text("Delete", color = ResearchRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    enabled = !saving
                ) {
                    Text("Keep rule")
                }
            }
        )
    }
}

@Composable
private fun AlertRulesSummary(
    activeRules: Int,
    watchedCompanies: Int,
    notificationsEnabled: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 350.dp
        if (compact) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlertSummaryTile(
                    "Active rules",
                    activeRules.toString(),
                    Icons.Default.NotificationsActive,
                    ResearchGreen,
                    Modifier.width(120.dp)
                )
                AlertSummaryTile(
                    "Watchlist",
                    watchedCompanies.toString(),
                    Icons.Default.Star,
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.width(120.dp)
                )
                AlertSummaryTile(
                    "Android",
                    if (notificationsEnabled) "On" else "Off",
                    if (notificationsEnabled) Icons.Default.CheckCircle else Icons.Default.NotificationsOff,
                    if (notificationsEnabled) ResearchGreen else ResearchRed,
                    Modifier.width(120.dp)
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertSummaryTile(
                    "Active rules",
                    activeRules.toString(),
                    Icons.Default.NotificationsActive,
                    ResearchGreen,
                    Modifier.weight(1f)
                )
                AlertSummaryTile(
                    "Watchlist",
                    watchedCompanies.toString(),
                    Icons.Default.Star,
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f)
                )
                AlertSummaryTile(
                    "Android",
                    if (notificationsEnabled) "On" else "Off",
                    if (notificationsEnabled) Icons.Default.CheckCircle else Icons.Default.NotificationsOff,
                    if (notificationsEnabled) ResearchGreen else ResearchRed,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AlertSummaryTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 82.dp),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            Text(
                value,
                color = accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(label, color = ResearchMuted, fontSize = 8.5.sp)
        }
    }
}

@Composable
private fun AlertRulesExplainer() {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.38f)
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Info,
                null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Alerts tell you when a rule you created matches a verified observation or company event. " +
                    "They are prompts to investigate, not investment instructions.",
                color = ResearchMuted,
                fontSize = 9.5.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun AlertFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 9.5.sp) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ResearchCard,
            labelColor = ResearchMuted,
            selectedContainerColor = ResearchGreen,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = BorderStroke(1.dp, if (selected) ResearchGreen else ResearchBorder)
    )
}

@Composable
private fun AlertRuleCard(
    rule: PriceAlert,
    saving: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (rule.type) {
        AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> Icons.Default.PriceChange
        AlertType.DAILY_GAIN -> Icons.Default.TrendingUp
        AlertType.DAILY_LOSS -> Icons.Default.TrendingDown
        AlertType.HIGH_VOLUME -> Icons.Default.BarChart
        AlertType.NEWS -> Icons.Default.Article
        AlertType.CORPORATE_ACTION -> Icons.Default.EventNote
        else -> Icons.Default.Notifications
    }
    val accent = when (rule.type) {
        AlertType.DAILY_LOSS -> ResearchRed
        AlertType.CORPORATE_ACTION -> MaterialTheme.colorScheme.tertiary
        else -> ResearchGreen
    }

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = ResearchCard,
        border = BorderStroke(1.dp, ResearchBorder)
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(
                        accent.copy(alpha = 0.09f),
                        RoundedCornerShape(9.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        rule.symbol,
                        color = ResearchText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        WatchlistPresentation.typeLabel(rule.type),
                        color = ResearchText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val threshold = rule.threshold?.let {
                        if (rule.type in setOf(AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW)) {
                            "KSh " + String.format(java.util.Locale.US, "%,.2f", it)
                        } else {
                            String.format(java.util.Locale.US, "%.2f%%", it)
                        }
                    }
                    if (threshold != null) {
                        Text(threshold, color = ResearchMuted, fontSize = 9.sp)
                    }
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    enabled = !saving
                )
            }

            Text(
                alertRuleExplanation(rule.type),
                color = ResearchMuted,
                fontSize = 8.7.sp,
                lineHeight = 12.5.sp
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit, enabled = !saving) {
                    Text("Edit", color = ResearchGreen)
                }
                TextButton(onClick = onDelete, enabled = !saving) {
                    Text("Delete", color = ResearchRed)
                }
            }
        }
    }
}

private fun alertRuleExplanation(type: AlertType): String = when (type) {
    AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW ->
        "Triggers only when two successive eligible same-day quotes show a real threshold crossing."
    AlertType.DAILY_GAIN, AlertType.DAILY_LOSS ->
        "Uses the provider's reported daily percentage and can notify once per rule per Nairobi day."
    AlertType.HIGH_VOLUME ->
        "Requires current observed volume plus a provider-supplied average volume."
    AlertType.NEWS ->
        "Checks company-linked NSE-relevant news, including after hours, with up to a seven-day catch-up window."
    AlertType.CORPORATE_ACTION ->
        "Checks company-linked dividend and corporate-action items, including after hours, with up to a seven-day catch-up window."
    else ->
        "This alert type is not currently supported."
}
