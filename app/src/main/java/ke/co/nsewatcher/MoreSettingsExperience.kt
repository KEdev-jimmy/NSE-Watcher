package ke.co.nsewatcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private const val SUPPORT_EMAIL = "jameswaweru399@gmail.com"

@Composable
internal fun MoreHubScreen(
    name: String,
    username: String,
    email: String,
    openProfile: () -> Unit,
    openPractice: () -> Unit,
    openWatchlist: () -> Unit,
    openAlerts: () -> Unit,
    openCompare: () -> Unit,
    openSettings: () -> Unit,
    openNotifications: () -> Unit,
    openMarketData: () -> Unit,
    openAppearance: () -> Unit,
    openHelp: () -> Unit,
    openAbout: () -> Unit
) {
    val context = LocalContext.current
    var emailError by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            ) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HubAvatar(name, 62)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name.trim().ifBlank { ProfileDefaults.displayName },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            email.trim().ifBlank { username.trim().takeIf { it.isNotEmpty() }?.let { "@$it" } ?: "Local profile" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(7.dp))
                        FilledTonalButton(
                            onClick = openProfile,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("View profile", fontSize = 11.sp)
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        item { HubSectionTitle("Quick tools", "Tools that help you investigate and learn") }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stack = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HubToolCard("Practice Portfolio", "Practice with virtual money", Icons.Default.AccountBalanceWallet, openPractice, Modifier.fillMaxWidth())
                        HubToolCard("Watchlist", "Track companies you follow", Icons.Default.Bookmark, openWatchlist, Modifier.fillMaxWidth())
                        HubToolCard("Alerts", "Price, news and company events", Icons.Default.NotificationsActive, openAlerts, Modifier.fillMaxWidth())
                        HubToolCard("Compare companies", "Review sourced figures side by side", Icons.Default.CompareArrows, openCompare, Modifier.fillMaxWidth())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HubToolCard("Practice Portfolio", "Practice with virtual money", Icons.Default.AccountBalanceWallet, openPractice, Modifier.weight(1f))
                            HubToolCard("Watchlist", "Track companies you follow", Icons.Default.Bookmark, openWatchlist, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HubToolCard("Alerts", "Price, news and company events", Icons.Default.NotificationsActive, openAlerts, Modifier.weight(1f))
                            HubToolCard("Compare companies", "Review sourced figures side by side", Icons.Default.CompareArrows, openCompare, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            HubListCard(
                title = "App & Preferences",
                subtitle = "Personalize your NSE Watcher experience",
                rows = listOf(
                    HubRow("All settings", "Account, privacy and preferences", Icons.Default.Settings, openSettings),
                    HubRow("Notifications", "Alerts, sound and delivery controls", Icons.Default.Notifications, openNotifications),
                    HubRow("Market & Data", "Refresh cadence, source and market timing", Icons.Default.Storage, openMarketData),
                    HubRow("Appearance", "Theme, font size and display", Icons.Default.Palette, openAppearance)
                )
            )
        }
        item {
            HubListCard(
                title = "Support",
                subtitle = "Help, feedback and product information",
                rows = listOf(
                    HubRow("Help & Support", "FAQs, contact and report issues", Icons.Default.HelpOutline, openHelp),
                    HubRow("Send feedback", "Share an idea or tell us what is not working", Icons.Default.Email) {
                        emailError = !launchSupportEmail(
                            context,
                            "NSE Watcher Feedback",
                            "Hi James,\n\nI have feedback about NSE Watcher:\n\n"
                        )
                    },
                    HubRow("About NSE Watcher", "Version, data notes and product information", Icons.Default.Info, openAbout)
                )
            )
        }
        item {
            Text(
                "NSE Watcher is an analysis and education product. Practice Portfolio uses virtual money and no trade is sent to a broker.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
    }

    if (emailError) {
        AlertDialog(
            onDismissRequest = { emailError = false },
            title = { Text("No email app found") },
            text = { Text("You can contact support at $SUPPORT_EMAIL.") },
            confirmButton = { TextButton(onClick = { emailError = false }) { Text("OK") } }
        )
    }
}

@Composable
internal fun ProfileHubScreen(
    name: String,
    username: String,
    email: String,
    description: String,
    onName: (String) -> Unit,
    onUsername: (String) -> Unit,
    onEmail: (String) -> Unit,
    onDescription: (String) -> Unit,
    pickAvatar: () -> Unit,
    back: () -> Unit,
    openAccount: () -> Unit,
    openWatchlist: () -> Unit,
    openPractice: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftName by rememberSaveable(name) { mutableStateOf(name) }
    var draftUsername by rememberSaveable(username) { mutableStateOf(username) }
    var draftEmail by rememberSaveable(email) { mutableStateOf(email) }
    var draftDescription by rememberSaveable(description) { mutableStateOf(description) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HubHeader("Profile", back) }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    HubAvatar(name, 88)
                    Spacer(Modifier.height(12.dp))
                    Text(name.trim().ifBlank { ProfileDefaults.displayName }, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        username.trim().takeIf { it.isNotEmpty() }?.let { "@$it" } ?: "Local profile",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    if (email.isNotBlank()) Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(50.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stored on this device", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (editing) {
            item {
                HubCard {
                    OutlinedTextField(draftName, { draftName = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(draftUsername, { draftUsername = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(draftEmail, { draftEmail = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(draftDescription, { draftDescription = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            draftName = name; draftUsername = username; draftEmail = email; draftDescription = description; editing = false
                        }) { Text("Cancel") }
                        Button(onClick = {
                            onName(draftName.trim().ifBlank { ProfileDefaults.displayName })
                            onUsername(draftUsername.trim())
                            onEmail(draftEmail.trim())
                            onDescription(draftDescription.trim())
                            editing = false
                        }) { Text("Save") }
                    }
                }
            }
        } else {
            item {
                Button(onClick = { editing = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Edit profile")
                }
            }
        }

        item {
            HubListCard(
                title = "Account",
                subtitle = "Your identity and local data",
                rows = listOf(
                    HubRow("Account & sign in", "Google/email sync is not connected yet", Icons.Default.AccountCircle, openAccount),
                    HubRow("Profile photo", "Choose a picture from this device", Icons.Default.PhotoCamera, pickAvatar),
                    HubRow("Watchlist", "Open companies you follow", Icons.Default.Bookmark, openWatchlist),
                    HubRow("Practice Portfolio", "Open your virtual investing account", Icons.Default.AccountBalanceWallet, openPractice)
                )
            )
        }
        if (description.isNotBlank()) item {
            HubCard {
                Text("About you", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
internal fun SettingsOverviewScreen(
    back: () -> Unit,
    openAccount: () -> Unit,
    openNotifications: () -> Unit,
    openMarketData: () -> Unit,
    openAppearance: () -> Unit,
    openCharts: () -> Unit,
    openLanguage: () -> Unit,
    openPrivacy: () -> Unit,
    openHelp: () -> Unit,
    openAbout: () -> Unit
) {
    val context = LocalContext.current
    var emailError by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HubHeader("Settings", back) }
        item { HubSectionTitle("Quick access", "Common settings, one tap away") }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stack = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HubToolCard("Account & sign in", "Identity and future sync", Icons.Default.AccountCircle, openAccount, Modifier.fillMaxWidth())
                        HubToolCard("Notifications", "Alerts and sounds", Icons.Default.Notifications, openNotifications, Modifier.fillMaxWidth())
                        HubToolCard("Market & Data", "Sources and refresh", Icons.Default.Storage, openMarketData, Modifier.fillMaxWidth())
                        HubToolCard("Appearance", "Theme and text", Icons.Default.Palette, openAppearance, Modifier.fillMaxWidth())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HubToolCard("Account & sign in", "Identity and future sync", Icons.Default.AccountCircle, openAccount, Modifier.weight(1f))
                            HubToolCard("Notifications", "Alerts and sounds", Icons.Default.Notifications, openNotifications, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HubToolCard("Market & Data", "Sources and refresh", Icons.Default.Storage, openMarketData, Modifier.weight(1f))
                            HubToolCard("Appearance", "Theme and text", Icons.Default.Palette, openAppearance, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            HubListCard(
                title = "Preferences",
                subtitle = "Control how NSE Watcher behaves",
                rows = listOf(
                    HubRow("Charts", "Default timeframe and chart grid", Icons.Default.Timeline, openCharts),
                    HubRow("Language & Region", "English, Kenya and KSh", Icons.Default.Language, openLanguage),
                    HubRow("Privacy & Data", "Local storage and cloud status", Icons.Default.PrivacyTip, openPrivacy)
                )
            )
        }
        item {
            HubListCard(
                title = "Support & Information",
                subtitle = "Get help and learn about the product",
                rows = listOf(
                    HubRow("Help & Support", "FAQs, contact and problem reports", Icons.Default.HelpOutline, openHelp),
                    HubRow("Send feedback", "Email the NSE Watcher team", Icons.Default.Email) {
                        emailError = !launchSupportEmail(context, "NSE Watcher Feedback", "Hi James,\n\nMy feedback:\n\n")
                    },
                    HubRow("About NSE Watcher", "Version, data notes and disclaimer", Icons.Default.Info, openAbout)
                )
            )
        }
    }
    if (emailError) {
        AlertDialog(
            onDismissRequest = { emailError = false },
            title = { Text("No email app found") },
            text = { Text("You can contact support at $SUPPORT_EMAIL.") },
            confirmButton = { TextButton(onClick = { emailError = false }) { Text("OK") } }
        )
    }
}

@Composable
internal fun NotificationCenterScreen(
    marketAlerts: Boolean,
    priceAlerts: Boolean,
    newsAlerts: Boolean,
    corporateAlerts: Boolean,
    practiceAlerts: Boolean,
    soundMode: String,
    onMarketAlerts: (Boolean) -> Unit,
    onPriceAlerts: (Boolean) -> Unit,
    onNewsAlerts: (Boolean) -> Unit,
    onCorporateAlerts: (Boolean) -> Unit,
    onPracticeAlerts: (Boolean) -> Unit,
    onSoundMode: (String) -> Unit,
    openAlertRules: () -> Unit,
    back: () -> Unit
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HubHeader("Notifications", back) }
        item {
            Surface(
                color = if (permissionGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (permissionGranted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        null,
                        tint = if (permissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (permissionGranted) "Notifications allowed" else "Notification permission needed", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            if (permissionGranted) "NSE Watcher can deliver enabled alerts." else "Android must allow notifications before alerts can appear.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!permissionGranted && Build.VERSION.SDK_INT >= 33) TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow")
                    }
                }
            }
        }
        item {
            HubSettingsSection("What should alert you?") {
                HubSwitchRow("Price thresholds", "Your price-above and price-below rules", priceAlerts, onPriceAlerts)
                HubSwitchRow("Market movement rules", "Daily gain, loss and unusual-volume rules you create", marketAlerts, onMarketAlerts)
                HubSwitchRow("Watched-company news", "Automatically monitor followed companies for new company stories", newsAlerts, onNewsAlerts)
                HubSwitchRow("Dividends & corporate actions", "Automatically monitor followed companies for dividend and corporate-action stories", corporateAlerts, onCorporateAlerts)
                HubSwitchRow("Practice Portfolio fills", "Notify when an eligible observed quote fills a pending virtual order", practiceAlerts, onPracticeAlerts)
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = openAlertRules).padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Manage alert rules", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Create thresholds for companies on your Watchlist", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            HubSettingsSection("Sound") {
                listOf("Default" to "Use the Android notification sound", "Silent" to "Deliver enabled alerts without sound").forEach { (mode, sub) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSoundMode(mode) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = soundMode == mode, onClick = { onSoundMode(mode) })
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(if (mode == "Default") "System default" else "Silent", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(sub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { openAndroidNotificationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Open Android notification settings")
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Delivery notes", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Background checks are scheduled about every 15 minutes; Android may delay them. Price conditions require an open market and eligible recent quotes. Company news can be checked outside market hours.", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("NSE index alerts are not enabled yet because the app does not currently have a verified official live NSE index source.", fontSize = 10.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun HelpSupportExperienceScreen(back: () -> Unit) {
    val context = LocalContext.current
    var emailError by remember { mutableStateOf(false) }
    val faqs = listOf(
        "Is NSE Watcher a broker?" to "No. NSE Watcher explains available NSE market information and provides a virtual Practice Portfolio. It does not place real trades.",
        "How current are prices?" to "Quotes are provider-supplied and are presented with their observation time. The current market feed is generally about 15 minutes delayed where the provider supplies that delay.",
        "Why can a value show as unavailable?" to "The app avoids inventing missing prices, previous closes, indices or financial values. If the verified source does not provide a value, NSE Watcher keeps it unavailable.",
        "How do Watchlist notifications work?" to "When watched-company news or corporate-action notifications are enabled, the background monitor can check followed companies and notify on eligible new stories. Android may delay background work.",
        "What is Practice Portfolio?" to "It is a simulation using virtual money. Orders only fill from eligible observed quotes under the app's Practice rules and are never sent to a broker.",
        "Does the AI Analyst give investment advice?" to "No. The Analyst is designed to explain supplied evidence and uncertainty. It should not issue BUY, SELL or HOLD instructions or guarantee returns.",
        "Why are NSE index alerts unavailable?" to "A verified official live index source is not configured yet, so NSE Watcher does not fabricate index values or alerts."
    )
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HubHeader("Help & Support", back) }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Need help?", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Contact NSE Watcher support or browse the common questions below.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        emailError = !launchSupportEmail(context, "NSE Watcher Support", "Hi James,\n\nI need help with:\n\n")
                    }) {
                        Icon(Icons.Default.Email, null)
                        Spacer(Modifier.width(7.dp))
                        Text("Email support")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(SUPPORT_EMAIL, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
        item { HubSectionTitle("Frequently asked questions", "Tap a question to expand it") }
        items(faqs.size) { index ->
            FaqCard(faqs[index].first, faqs[index].second)
        }
        item {
            HubListCard(
                title = "Report or suggest",
                subtitle = "Help improve NSE Watcher",
                rows = listOf(
                    HubRow("Report a problem", "Describe what happened and what you expected", Icons.Default.ReportProblem) {
                        emailError = !launchSupportEmail(context, "NSE Watcher Problem Report", "Hi James,\n\nWhat happened:\n\nWhat I expected:\n\nDevice / Android version:\n")
                    },
                    HubRow("Send product feedback", "Share an idea or improvement", Icons.Default.Lightbulb) {
                        emailError = !launchSupportEmail(context, "NSE Watcher Product Feedback", "Hi James,\n\nMy idea or feedback:\n\n")
                    }
                )
            )
        }
    }
    if (emailError) {
        AlertDialog(
            onDismissRequest = { emailError = false },
            title = { Text("No email app found") },
            text = { Text("You can contact support at $SUPPORT_EMAIL.") },
            confirmButton = { TextButton(onClick = { emailError = false }) { Text("OK") } }
        )
    }
}

@Composable
internal fun AboutNseWatcherExperienceScreen(back: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { HubHeader("About NSE Watcher", back) }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(18.dp)) {
                    Text("NSE Watcher", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Learn. Track. Investigate. Grow.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            HubListCard(
                title = "Product",
                subtitle = "What NSE Watcher is built to do",
                rows = listOf(
                    HubRow("Market intelligence", "Follow companies, detect changes and investigate evidence", Icons.Default.Insights) {},
                    HubRow("Practice Portfolio", "Test ideas with virtual money and review decisions later", Icons.Default.AccountBalanceWallet) {},
                    HubRow("Evidence-first analysis", "Missing values remain unavailable rather than being fabricated", Icons.Default.Verified) {}
                ),
                clickableRows = false
            )
        }
        item {
            HubSettingsSection("Data notes") {
                HubValueRow("Primary market source", "MyStocks Africa via NSE Watcher gateway")
                HubValueRow("Quote presentation", "Observation time shown; generally ~15 min delayed where supplied")
                HubValueRow("Market timezone", "Africa/Nairobi · EAT")
                HubValueRow("Official NSE indices", "Unavailable until a verified source is configured")
            }
        }
        item {
            HubSettingsSection("Important") {
                Text("NSE Watcher provides information, analysis and education. It does not execute trades, guarantee returns or provide a promise of future market performance.", fontSize = 11.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 7.dp))
                Text("Support: $SUPPORT_EMAIL", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 7.dp))
            }
        }
    }
}

private data class HubRow(val title: String, val subtitle: String, val icon: ImageVector, val action: () -> Unit)

@Composable
private fun HubSectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

@Composable
private fun HubToolCard(title: String, subtitle: String, icon: ImageVector, action: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 112.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = action),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.size(38.dp), RoundedCornerShape(12.dp), MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
            }
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HubListCard(title: String, subtitle: String, rows: List<HubRow>, clickableRows: Boolean = true) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            rows.forEachIndexed { index, row ->
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (clickableRows) Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = row.action) else Modifier)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(Modifier.size(34.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) {
                        Icon(row.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(row.subtitle, fontSize = 9.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (clickableRows) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                if (index < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun HubHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun HubSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HubCard(content)
    }
}

@Composable
private fun HubSwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 9.5.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun HubValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, Modifier.weight(1.2f), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FaqCard(question: String, answer: String) {
    var expanded by rememberSaveable(question) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(question, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(answer, fontSize = 10.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HubAvatar(name: String, size: Int) {
    val context = LocalContext.current
    val uri = context.getSharedPreferences("nse_watcher_preferences", Context.MODE_PRIVATE).getString("avatar_uri", null)
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = try {
            uri?.let { value ->
                context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)
            }
        } catch (_: Exception) {
            null
        }
    }
    Surface(Modifier.size(size.dp), CircleShape, MaterialTheme.colorScheme.primary) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), "Profile photo", Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    profileInitials(name),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = if (size >= 80) 26.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private fun profileInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "I"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun launchSupportEmail(context: Context, subject: String, body: String): Boolean = try {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Email NSE Watcher"))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: Exception) {
    false
}

private fun openAndroidNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
