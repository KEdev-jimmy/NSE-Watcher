package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val AccountGreen = Color(0xFF006A4A)
private val AccountGreenDark = Color(0xFF00553B)
private val AccountSoftGreen = Color(0xFFE8F7EF)
private val AccountRed = Color(0xFFD92D20)
private val AccountGold = Color(0xFFE7A51D)

private data class ProfileDesignRow(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val action: () -> Unit,
    val destructive: Boolean = false
)

@Composable
internal fun DesignedProfileScreen(
    name: String,
    username: String,
    email: String,
    avatarUri: String?,
    onName: (String) -> Unit,
    onUsername: (String) -> Unit,
    onEmail: (String) -> Unit,
    pickAvatar: () -> Unit,
    back: () -> Unit,
    openAccount: () -> Unit,
    openPractice: () -> Unit,
    openAlerts: () -> Unit,
    openWatchlist: () -> Unit,
    openSettings: () -> Unit,
    openHelp: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var infoMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val displayName = name.trim().ifBlank { ProfileDefaults.displayName }
    val displayEmail = email.trim().ifBlank { "Local profile" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccountGreen)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = back,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Text(
                        "Profile",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button) { editing = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(74.dp),
                        shape = CircleShape,
                        color = Color(0xFFF2F5F7),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f))
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                displayName.split(" ").filter { it.isNotBlank() }.take(2)
                                    .joinToString("") { it.take(1).uppercase() }
                                    .ifBlank { "NW" },
                                color = Color(0xFF526170),
                                fontWeight = FontWeight.Medium,
                                fontSize = 22.sp
                            )
                            if (!avatarUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUri,
                                    contentDescription = "Profile photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            displayEmail,
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF16C66B)
                        ) {
                            Text(
                                "Free Account",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
                    .clickable(role = Role.Button) {
                        infoMessage = "Premium features are planned for a future release. NSE Watcher will keep the current free experience available while those features are being developed."
                    },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(Modifier.size(38.dp), CircleShape, AccountGold.copy(alpha = 0.13f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = AccountGold, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Upgrade (Coming Soon)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Access advanced features",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.5.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ProfileDesignSection(
                title = "Your Activity",
                rows = listOf(
                    ProfileDesignRow("Practice Portfolio", "View your performance", Icons.Default.BarChart, openPractice),
                    ProfileDesignRow("Alerts", "Manage your price alerts", Icons.Default.NotificationsNone, openAlerts),
                    ProfileDesignRow("Watchlist", "Your tracked companies", Icons.Default.StarBorder, openWatchlist),
                    ProfileDesignRow("Learning Progress", "Track your market knowledge", Icons.Default.School, openPractice)
                )
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            ProfileDesignSection(
                title = "Account & App",
                rows = listOf(
                    ProfileDesignRow("Settings", "App preferences, notifications, data", Icons.Default.Settings, openSettings),
                    ProfileDesignRow("Help & Support", "FAQs, contact us", Icons.Default.HelpOutline, openHelp),
                    ProfileDesignRow(
                        "Sign out",
                        "No cloud session is connected on this build",
                        Icons.Default.Logout,
                        { infoMessage = "NSE Watcher is currently using a local profile, so there is no cloud session to sign out from yet. Your local watchlist, Practice Portfolio and preferences remain on this device." },
                        destructive = true
                    )
                )
            )
        }

        item {
            TextButton(
                onClick = openAccount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
            ) {
                Text("Account & sign in", color = AccountGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (editing) {
        ProfileEditDialog(
            name = name,
            username = username,
            email = email,
            avatarUri = avatarUri,
            pickAvatar = pickAvatar,
            onDismiss = { editing = false },
            onSave = { newName, newUsername, newEmail ->
                onName(newName.trim().ifBlank { ProfileDefaults.displayName })
                onUsername(newUsername.trim())
                onEmail(newEmail.trim())
                editing = false
            }
        )
    }

    infoMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { infoMessage = null },
            title = { Text("NSE Watcher") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { infoMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun ProfileDesignSection(title: String, rows: List<ProfileDesignRow>) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = row.action)
                            .padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            row.icon,
                            null,
                            tint = if (row.destructive) AccountRed else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.title,
                                color = if (row.destructive) AccountRed else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                row.subtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                        if (!row.destructive) {
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 48.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    name: String,
    username: String,
    email: String,
    avatarUri: String?,
    pickAvatar: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var draftName by rememberSaveable(name) { mutableStateOf(name) }
    var draftUsername by rememberSaveable(username) { mutableStateOf(username) }
    var draftEmail by rememberSaveable(email) { mutableStateOf(email) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile information") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(54.dp), CircleShape, MaterialTheme.colorScheme.surfaceVariant) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(draftName.trim().take(1).uppercase().ifBlank { "N" }, fontWeight = FontWeight.Bold)
                            if (!avatarUri.isNullOrBlank()) {
                                AsyncImage(avatarUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = pickAvatar) { Text("Change photo") }
                }
                OutlinedTextField(draftName, { draftName = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(draftUsername, { draftUsername = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    draftEmail,
                    { draftEmail = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Email address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = { onSave(draftName, draftUsername, draftEmail) }) { Text("Save") }
        }
    )
}

@Composable
internal fun DesignedSettingsScreen(
    back: () -> Unit,
    openProfile: () -> Unit,
    openAccount: () -> Unit,
    priceAlerts: Boolean,
    marketAlerts: Boolean,
    newsAndCompanyAlerts: Boolean,
    onPriceAlerts: (Boolean) -> Unit,
    onMarketAlerts: (Boolean) -> Unit,
    onNewsAndCompanyAlerts: (Boolean) -> Unit,
    darkTheme: Boolean,
    defaultView: String,
    onDefaultView: (String) -> Unit,
    openAppearance: () -> Unit,
    openMarketData: () -> Unit,
    openPrivacy: () -> Unit,
    openAbout: () -> Unit
) {
    val context = LocalContext.current
    var dialogTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogMessage by rememberSaveable { mutableStateOf("") }
    var showDefaultView by rememberSaveable { mutableStateOf(false) }
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
        }.getOrDefault("0.1.0")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().background(AccountGreen).padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text(
                    "Settings",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(48.dp))
            }
        }

        item {
            SettingsDesignSection("Account") {
                SettingsDesignRow(Icons.Default.PersonOutline, "Profile Information", "Name, email, account details", onClick = openProfile)
                SettingsDesignDivider()
                SettingsDesignRow(Icons.Default.Lock, "Change Password", "Update your password", onClick = openAccount)
                SettingsDesignDivider()
                SettingsDesignRow(
                    Icons.Default.CloudQueue,
                    "Account Sync",
                    "Sync your data across devices",
                    trailing = "Off",
                    onClick = {
                        dialogTitle = "Account sync"
                        dialogMessage = "Cross-device sync will be enabled only after NSE Watcher has a real authentication and cloud-sync backend. Your data remains local for now."
                    }
                )
                SettingsDesignDivider()
                SettingsDesignRow(
                    Icons.Default.AdminPanelSettings,
                    "Manage Account",
                    "Download or delete your data",
                    onClick = openAccount
                )
            }
        }

        item {
            SettingsDesignSection("Notifications") {
                SettingsToggleRow(Icons.Default.NotificationsNone, "Price Alerts", "Push notifications for price alerts", priceAlerts, onPriceAlerts)
                SettingsDesignDivider()
                SettingsToggleRow(Icons.Default.ShowChart, "Market Updates", "Daily summaries and market news", marketAlerts, onMarketAlerts)
                SettingsDesignDivider()
                SettingsToggleRow(Icons.Default.Description, "News & Company Updates", "Breaking news and company insights", newsAndCompanyAlerts, onNewsAndCompanyAlerts)
            }
        }

        item {
            SettingsDesignSection("App Preferences") {
                SettingsDesignRow(
                    Icons.Default.Palette,
                    "Theme",
                    "Light, Dark, or System",
                    trailing = if (darkTheme) "Dark" else "Light",
                    onClick = openAppearance
                )
                SettingsDesignDivider()
                SettingsDesignRow(
                    Icons.Default.GridView,
                    "Default View",
                    "Choose your starting tab",
                    trailing = defaultView,
                    onClick = { showDefaultView = true }
                )
                SettingsDesignDivider()
                SettingsDesignRow(Icons.Default.TextFields, "Display & Accessibility", "Font size, contrast, accessibility", onClick = openAppearance)
                SettingsDesignDivider()
                SettingsDesignRow(Icons.Default.Wifi, "Data Usage", "Manage offline data and cache", onClick = openMarketData)
            }
        }

        item {
            SettingsDesignSection("About") {
                SettingsDesignRow(Icons.Default.Info, "App Version", version, onClick = openAbout)
                SettingsDesignDivider()
                SettingsDesignRow(
                    Icons.Default.Article,
                    "Terms of Service",
                    "",
                    onClick = {
                        dialogTitle = "Terms of Service"
                        dialogMessage = "NSE Watcher provides market information, research tools and a virtual Practice Portfolio. It does not execute real trades or provide personal investment advice."
                    }
                )
                SettingsDesignDivider()
                SettingsDesignRow(Icons.Default.VerifiedUser, "Privacy Policy", "", onClick = openPrivacy)
                SettingsDesignDivider()
                SettingsDesignRow(
                    Icons.Default.Code,
                    "Open Source Licences",
                    "",
                    onClick = {
                        dialogTitle = "Open source licences"
                        dialogMessage = "NSE Watcher uses open-source Android and Kotlin libraries including AndroidX Compose, WorkManager, Coil, Retrofit, Kotlin coroutines and kotlinx serialization. Licence details will be expanded before production release."
                    }
                )
            }
        }
    }

    if (showDefaultView) {
        AlertDialog(
            onDismissRequest = { showDefaultView = false },
            title = { Text("Default View") },
            text = {
                Column {
                    listOf("Home", "Market", "News", "Companies", "Practice").forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onDefaultView(option)
                                showDefaultView = false
                            }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = defaultView == option, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDefaultView = false }) { Text("Cancel") } }
        )
    }

    dialogTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { dialogTitle = null },
            title = { Text(title) },
            text = { Text(dialogMessage) },
            confirmButton = { TextButton(onClick = { dialogTitle = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun SettingsDesignSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsDesignRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(trailing, color = if (trailing == "On") AccountGreen else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Spacer(Modifier.width(5.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChecked(!checked) }
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.5.sp,
                lineHeight = 13.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsDesignDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 44.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    )
}

@Composable
internal fun AuthLandingScreen(
    back: () -> Unit,
    openCreateAccount: () -> Unit,
    openSignIn: () -> Unit,
    continueAsGuest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.size(62.dp),
                shape = RoundedCornerShape(16.dp),
                color = AccountGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ShowChart, null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("NSE Watcher", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Kenya's Markets. In Your Hands.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(24.dp))
            AuthGrowthIllustration()
            Spacer(Modifier.height(22.dp))
            Text(
                "Track. Learn. Practice.\nStay Informed.",
                fontSize = 23.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Live market data, company insights, news and a risk-free practice portfolio for the Nairobi Securities Exchange.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = openCreateAccount,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccountGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create account", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = openSignIn,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Sign in")
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = continueAsGuest) {
                Text("Continue as guest", color = AccountGreen, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "You can always sign in later from Settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.5.sp
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthGrowthIllustration() {
    val green = AccountGreen
    Canvas(
        modifier = Modifier.fillMaxWidth().height(170.dp)
            .background(AccountSoftGreen.copy(alpha = 0.38f), RoundedCornerShape(18.dp))
            .padding(8.dp)
    ) {
        val h = size.height
        val w = size.width
        val bars = listOf(0.22f, 0.32f, 0.28f, 0.43f, 0.52f, 0.45f, 0.63f, 0.76f, 0.69f, 0.89f)
        val step = w / (bars.size + 1)
        bars.forEachIndexed { index, value ->
            val x = step * (index + 0.65f)
            val barHeight = h * value * 0.72f
            drawRoundRect(
                color = green.copy(alpha = 0.10f),
                topLeft = androidx.compose.ui.geometry.Offset(x, h - barHeight - 12f),
                size = androidx.compose.ui.geometry.Size(step * 0.55f, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(9f, 9f)
            )
        }
        val path = Path()
        bars.forEachIndexed { index, value ->
            val x = step * (index + 0.95f)
            val y = h - (h * value * 0.72f) - 20f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = green.copy(alpha = 0.42f), style = Stroke(width = 3f))
        bars.forEachIndexed { index, value ->
            val x = step * (index + 0.95f)
            val y = h - (h * value * 0.72f) - 20f
            drawCircle(green.copy(alpha = 0.55f), radius = 5f, center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
internal fun CreateAccountScreen(
    initialName: String,
    back: () -> Unit,
    openSignIn: () -> Unit
) {
    var fullName by rememberSaveable { mutableStateOf(initialName.takeUnless { it == ProfileDefaults.displayName }.orEmpty()) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showUnavailable by rememberSaveable { mutableStateOf(false) }

    AuthFormScaffold(title = "Create your account", back = back) {
        Text(
            "Set up your account to sync your portfolio, alerts and preferences across devices.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(22.dp))
        AuthField(
            value = fullName,
            onValueChange = { fullName = it },
            label = "Full name",
            icon = Icons.Default.PersonOutline
        )
        Spacer(Modifier.height(10.dp))
        AuthField(
            value = email,
            onValueChange = { email = it },
            label = "Email address",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(10.dp))
        AuthPasswordField(password, { password = it }, passwordVisible, { passwordVisible = !passwordVisible })
        Spacer(Modifier.height(8.dp))
        PasswordRequirement("At least 8 characters", password.length >= 8)
        PasswordRequirement("Include a number", password.any(Char::isDigit))
        PasswordRequirement("Include a letter", password.any(Char::isLetter))
        Spacer(Modifier.height(22.dp))
        AuthPrimaryButton("Create account") { showUnavailable = true }
        Spacer(Modifier.height(16.dp))
        AuthDivider()
        Spacer(Modifier.height(14.dp))
        SocialAuthButton("G", "Continue with Google") { showUnavailable = true }
        Spacer(Modifier.height(9.dp))
        SocialAuthButton("●", "Continue with Apple") { showUnavailable = true }
        Spacer(Modifier.height(18.dp))
        Text(
            "By creating an account, you agree to our\nTerms of Service and Privacy Policy.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = openSignIn, modifier = Modifier.fillMaxWidth()) {
            Text("Already have an account? Sign in", color = AccountGreen)
        }
    }

    if (showUnavailable) AuthUnavailableDialog { showUnavailable = false }
}

@Composable
internal fun SignInScreen(
    back: () -> Unit,
    openCreateAccount: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var keepSignedIn by rememberSaveable { mutableStateOf(true) }
    var showUnavailable by rememberSaveable { mutableStateOf(false) }

    AuthFormScaffold(title = "Sign in", back = back) {
        Text(
            "Welcome back. Sign in to access your portfolio, alerts and preferences.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(24.dp))
        AuthField(
            value = email,
            onValueChange = { email = it },
            label = "Email address",
            icon = Icons.Default.Email,
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(10.dp))
        AuthPasswordField(password, { password = it }, passwordVisible, { passwordVisible = !passwordVisible })
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = keepSignedIn, onCheckedChange = { keepSignedIn = it })
            Text("Keep me signed in", fontSize = 10.5.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { showUnavailable = true }) {
                Text("Forgot password?", color = AccountGreen, fontSize = 10.5.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        AuthPrimaryButton("Sign in") { showUnavailable = true }
        Spacer(Modifier.height(18.dp))
        AuthDivider()
        Spacer(Modifier.height(14.dp))
        SocialAuthButton("G", "Continue with Google") { showUnavailable = true }
        Spacer(Modifier.height(9.dp))
        SocialAuthButton("●", "Continue with Apple") { showUnavailable = true }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Don't have an account?", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
            TextButton(onClick = openCreateAccount, contentPadding = PaddingValues(horizontal = 5.dp)) {
                Text("Create one", color = AccountGreen, fontSize = 10.5.sp)
            }
        }
    }

    if (showUnavailable) AuthUnavailableDialog { showUnavailable = false }
}

@Composable
private fun AuthFormScaffold(
    title: String,
    back: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        item {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Column(content = content)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(19.dp)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    toggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Password") },
        leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(19.dp)) },
        trailingIcon = {
            IconButton(onClick = toggleVisibility) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle password visibility")
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun PasswordRequirement(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            null,
            tint = if (met) AccountGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun AuthPrimaryButton(label: String, action: () -> Unit) {
    Button(
        onClick = action,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccountGreen),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AuthDivider() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text("  or continue with  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SocialAuthButton(mark: String, label: String, action: () -> Unit) {
    OutlinedButton(
        onClick = action,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(mark, fontWeight = FontWeight.ExtraBold, color = if (mark == "G") AccountGreen else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(11.dp))
        Text(label)
    }
}

@Composable
private fun AuthUnavailableDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudOff, null, tint = AccountGreen) },
        title = { Text("Account service not connected yet") },
        text = {
            Text(
                "The design is ready, but NSE Watcher does not yet have a production authentication backend or OAuth configuration. No account, password reset, Google sign-in, Apple sign-in or cloud sync is being faked in this build."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}
