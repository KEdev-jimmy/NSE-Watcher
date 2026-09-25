package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun PremiumNewsHeader(
    searching: Boolean,
    onSearch: () -> Unit,
    openAlerts: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NseWatcherBrandLockup(modifier = Modifier.weight(1f), compact = true)
            PremiumNewsHeaderButton(
                icon = if (searching) Icons.Default.Close else Icons.Default.Search,
                description = if (searching) "Close search" else "Search news",
                onClick = onSearch
            )
            Spacer(Modifier.width(4.dp))
            PremiumNewsHeaderButton(Icons.Default.NotificationsNone, "Alerts", openAlerts, showDot = true)
        }
        Text("News", color = MaterialTheme.colorScheme.onBackground, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Relevant NSE news and updates.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
    }
}

@Composable
private fun PremiumNewsHeaderButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    showDot: Boolean = false
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Box(
            Modifier.size(37.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, description, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
            if (showDot) {
                Box(
                    Modifier.size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}

@Composable
internal fun PremiumNewsSearch(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValue("") }) { Icon(Icons.Default.Close, "Clear search") }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
internal fun PremiumNewsTabs(selected: String, onSelected: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.12f
        if (compact) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                PremiumNewsTabLabels.forEach { label ->
                    PremiumNewsTab(label, selected == label, Modifier.widthIn(min = 72.dp)) { onSelected(label) }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PremiumNewsTabLabels.forEach { label ->
                    PremiumNewsTab(label, selected == label, Modifier.weight(1f)) { onSelected(label) }
                }
            }
        }
    }
}

@Composable
private fun PremiumNewsTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(43.dp).clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (label == "Dividends") 8.5.sp else 9.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun PremiumNewsFeaturedCard(item: NewsItem, open: (NewsItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = "Read top story") { open(item) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 230.dp)) {
            PremiumNewsImage(item, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
                        )
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 88.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "TOP STORY",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                PremiumNewsMeta(item)
            }
        }
    }
}

@Composable
private fun PremiumNewsImage(item: NewsItem, modifier: Modifier = Modifier) {
    var loaded by remember(item.imageUrl) { mutableStateOf(false) }
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.surface
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Icon(
                Icons.Default.Newspaper,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(42.dp)
            )
        }
        if (item.imageUrl.isNotBlank()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().alpha(if (loaded) 1f else 0f),
                onSuccess = { loaded = true },
                onError = { loaded = false }
            )
        }
    }
}

@Composable
internal fun PremiumNewsSectionHeading(
    title: String,
    action: String?,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(action, color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
internal fun PremiumNewsListRow(item: NewsItem, open: (NewsItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Button, onClickLabel = "Read article") { open(item) },
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PremiumNewsImage(item, Modifier.size(56.dp).clip(RoundedCornerShape(9.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                PremiumNewsMeta(item)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun PremiumNewsMeta(item: NewsItem) {
    val identity = when {
        item.symbol.isNotBlank() -> item.symbol
        item.companyName.isNotBlank() -> item.companyName
        else -> item.category.ifBlank { "Market" }
    }
    Text(
        "$identity - ${item.source.ifBlank { "Source unavailable" }} - ${premiumNewsDate(item.publishedAt)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun PremiumCompanyNewsChip(stock: Stock, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(70.dp).height(70.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    ) {
        Column(
            Modifier.fillMaxSize().padding(7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(30.dp).background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stock.symbol.take(2),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(stock.symbol, color = MaterialTheme.colorScheme.onBackground, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
internal fun PremiumMarketNewsPulse(stories: List<NewsItem>) {
    val companyLinked = stories.count(::premiumIsCompany)
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumNewsIconBubble(Icons.Default.BarChart, MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Market News Pulse", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "A summary of the market-wide stories in the current NSE feed.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (stories.isEmpty()) "No market-wide story is currently available."
                        else "${stories.size} market-wide ${if (stories.size == 1) "story" else "stories"} are in the current relevant feed.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (stories.isNotEmpty()) {
                        Text(
                            if (companyLinked > 0) "$companyLinked also reference a listed company."
                            else "These stories are not being presented as proof of any individual price move.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumResultRow(item: NewsItem, open: (NewsItem) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Button) { open(item) },
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.symbol.ifBlank { "NSE" }.take(4),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.companyName.ifBlank { item.symbol.ifBlank { "Company result" } },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                PremiumNewsMeta(item)
            }
            Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    "Report",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
internal fun PremiumCorporateEventRow(item: NewsItem, open: (NewsItem) -> Unit) {
    val date = when {
        premiumIsDividend(item) && item.exDate.isNotBlank() -> item.exDate
        else -> item.publishedAt
    }
    val parsed = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()
    val type = when {
        premiumIsDividend(item) -> "Dividend"
        premiumIsAgm(item) -> "AGM"
        else -> "Corporate Action"
    }
    val accent = when (type) {
        "Dividend" -> Color(0xFFF0B531)
        "AGM" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Button) { open(item) },
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    Modifier.width(48.dp).padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        parsed?.dayOfMonth?.toString()?.padStart(2, '0') ?: "-",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        parsed?.month?.name?.take(3) ?: "DATE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.sp
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.companyName.ifBlank { item.symbol.ifBlank { item.source.ifBlank { "NSE event" } } },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.dividendAmount.isNotBlank()) {
                    Text("Dividend: ${item.dividendAmount}", color = MaterialTheme.colorScheme.onBackground, fontSize = 9.sp)
                }
                if (item.paymentDate.isNotBlank()) {
                    Text(
                        "Payment: ${premiumNewsDate(item.paymentDate)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 8.5.sp
                    )
                }
            }
            Surface(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
            ) {
                Text(
                    type,
                    color = accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
internal fun PremiumNewsFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                label,
                fontSize = 9.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
    )
}

@Composable
internal fun PremiumNewsExplainer(icon: ImageVector, title: String, body: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
            PremiumNewsIconBubble(icon, MaterialTheme.colorScheme.primary, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
private fun PremiumNewsIconBubble(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(tint.copy(alpha = 0.10f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(if (size >= 40.dp) 21.dp else 18.dp))
    }
}

@Composable
internal fun PremiumNewsMessage(title: String, detail: String, action: String, onAction: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Newspaper, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
            TextButton(onClick = onAction) { Text(action, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

internal fun premiumNewsDate(value: String): String {
    if (value.isBlank()) return "Date unavailable"
    return runCatching {
        LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
    }.getOrDefault(value.take(10))
}
