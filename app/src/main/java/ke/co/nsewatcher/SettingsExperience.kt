package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsHomeScreen(
    name: String,
    username: String,
    back: () -> Unit,
    openProfile: () -> Unit,
    openAccount: () -> Unit,
    openNotifications: () -> Unit,
    openMarketData: () -> Unit,
    openDisplay: () -> Unit,
    openCharts: () -> Unit,
    openLanguage: () -> Unit,
    openPrivacy: () -> Unit,
    openAbout: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val entries = remember {
        listOf(
            SettingsEntry("Account & Sign in", "Manage your account, sign in, security", Icons.Default.AccountCircle, openAccount),
            SettingsEntry("Notifications", "Alerts, news and reminders", Icons.Default.Notifications, openNotifications),
            SettingsEntry("Market & Data", "Data preferences, refresh and coverage", Icons.Default.ShowChart, openMarketData),
            SettingsEntry("Display & Appearance", "Theme, font size and layout", Icons.Default.Palette, openDisplay),
            SettingsEntry("Charts", "Timeframe, chart style and grid", Icons.Default.Timeline, openCharts),
            SettingsEntry("Language & Region", "English, Kenya and KSh", Icons.Default.Language, openLanguage),
            SettingsEntry("Privacy & Data", "Local data, permissions and account sync", Icons.Default.PrivacyTip, openPrivacy),
            SettingsEntry("About NSE Watcher", "Version, product information and support", Icons.Default.Info, openAbout)
        )
    }
    val filtered = entries.filter {
        query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search settings…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = openProfile),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(46.dp), CircleShape, MaterialTheme.colorScheme.primary) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                name.trim().take(1).uppercase().ifBlank { "I" },
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name.trim().ifBlank { ProfileDefaults.displayName }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (username.isBlank()) "Not signed in" else "@$username · Local profile",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            if (username.isBlank()) "Complete your profile" else "Profile stored on this device",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(filtered.size) { index ->
            val entry = filtered[index]
            SettingsEntryRow(entry)
        }
        item {
            Text(
                "NSE Watcher provides market information, analysis and a virtual Practice Portfolio. It does not execute real trades.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

private data class SettingsEntry(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val action: () -> Unit
)

@Composable
private fun SettingsEntryRow(entry: SettingsEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = entry.action),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(entry.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    entry.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
internal fun AccountSignInScreen(
    name: String,
    username: String,
    back: () -> Unit,
    openProfile: () -> Unit
) {
    var authMessage by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SettingsHeader("Account & Sign in", back) }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(82.dp), CircleShape, MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(19.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (username.isBlank()) "Get the most from NSE Watcher" else name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (username.isBlank()) "Sign in later to sync your watchlist, Practice Portfolio and settings across devices."
                    else "Your current profile is local to this device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { authMessage = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Surface(Modifier.size(24.dp), CircleShape, MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("G", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("Continue with Google")
            }
        }
        item {
            OutlinedButton(
                onClick = { authMessage = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Email, null)
                Spacer(Modifier.width(12.dp))
                Text("Continue with Email")
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsFact(Icons.Default.CheckCircle, "Your market browsing works without an account.")
                    SettingsFact(Icons.Default.CheckCircle, "Your current profile, watchlist and Practice data stay local.")
                    SettingsFact(Icons.Default.CheckCircle, "Google sign-in will not require a separate NSE Watcher password.")
                }
            }
        }
        item {
            TextButton(onClick = openProfile, modifier = Modifier.fillMaxWidth()) {
                Text("Edit local profile")
            }
        }
    }

    if (authMessage) {
        AlertDialog(
            onDismissRequest = { authMessage = false },
            icon = { Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Cloud accounts are not enabled yet") },
            text = {
                Text("The account screen is ready, but Google/email authentication and cloud sync still require a real account backend and OAuth configuration. This build keeps your data on this device rather than pretending you are signed in.")
            },
            confirmButton = { TextButton(onClick = { authMessage = false }) { Text("Got it") } }
        )
    }
}

@Composable
internal fun DisplayAppearanceScreen(
    dark: Boolean,
    fontSize: String,
    onDark: (Boolean) -> Unit,
    onFontSize: (String) -> Unit,
    back: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SettingsHeader("Display & Appearance", back) }
        item {
            SettingsSection("Theme") {
                SettingsRadioRow("Light", !dark) { onDark(false) }
                SettingsRadioRow("Dark", dark) { onDark(true) }
            }
        }
        item {
            SettingsSection("Font size") {
                listOf("Small", "Medium", "Large").forEach { size ->
                    SettingsRadioRow(size, fontSize == size) { onFontSize(size) }
                }
            }
        }
        item {
            SettingsSection("App layout") {
                SettingsUnavailableRow(
                    "Compact cards",
                    "Layout-density switching is not enabled yet.",
                    Icons.Default.ViewCompact
                )
                SettingsUnavailableRow(
                    "Animations",
                    "Uses the standard app animations and Android accessibility settings.",
                    Icons.Default.Animation
                )
            }
        }
    }
}

@Composable
internal fun ChartSettingsScreen(
    defaultRange: String,
    showGrid: Boolean,
    onDefaultRange: (String) -> Unit,
    onShowGrid: (Boolean) -> Unit,
    back: () -> Unit
) {
    val ranges = listOf("1D", "1W", "1M", "3M", "1Y")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SettingsHeader("Charts", back) }
        item {
            SettingsSection("Default timeframe") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ranges.forEach { range ->
                        FilterChip(
                            selected = defaultRange == range,
                            onClick = { onDefaultRange(range) },
                            label = { Text(range, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        item {
            SettingsSection("Chart style") {
                SettingsRadioRow("Line", true) {}
                SettingsUnavailableRow("Candlestick", "Requires verified OHLC history from the data provider.", Icons.Default.CandlestickChart)
                SettingsUnavailableRow("Area", "Not enabled in the current research chart.", Icons.Default.AreaChart)
            }
        }
        item {
            SettingsSection("Chart details") {
                SettingsSwitchRow("Show grid", "Horizontal reference lines on Company Intelligence charts", showGrid, onShowGrid)
                SettingsUnavailableRow("Show volume", "Intraday chart volume is not available in the current chart feed.", Icons.Default.BarChart)
                SettingsUnavailableRow("Indicators", "Technical indicators are not enabled yet.", Icons.Default.Functions)
            }
        }
    }
}

@Composable
internal fun LanguageRegionScreen(back: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SettingsHeader("Language & Region", back) }
        item {
            SettingsSection("Current market experience") {
                SettingsValueRow("App language", "English")
                SettingsValueRow("Currency", "KSh · Kenyan Shilling")
                SettingsValueRow("Region", "Kenya")
                SettingsValueRow("Market timezone", "Africa/Nairobi · EAT")
            }
        }
        item {
            Text(
                "NSE Watcher currently targets the Nairobi Securities Exchange, so market times, currency and region remain fixed to Kenya in this build.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
internal fun PrivacyDataScreen(back: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SettingsHeader("Privacy & Data", back) }
        item {
            SettingsSection("Your data") {
                SettingsValueRow("Profile & preferences", "Stored on this device")
                SettingsValueRow("Watchlist & Practice Portfolio", "Stored locally")
                SettingsValueRow("Cloud sync", "Off · no account connected")
                SettingsValueRow("Analytics SDK", "Not configured")
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "NSE Watcher does not execute trades. Market and news requests are sent only to the configured data services needed to load the app experience.",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SettingsSwitchRow(label: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsUnavailableRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Text("Unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsFact(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
