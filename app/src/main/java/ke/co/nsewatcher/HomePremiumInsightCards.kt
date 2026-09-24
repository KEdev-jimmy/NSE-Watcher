package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PremiumWhyMovingCard(
    palette: PremiumHomePalette,
    dark: Boolean,
    attention: MarketAttentionItem?,
    openMarket: () -> Unit,
    openCompany: (Stock) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                role = Role.Button,
                onClick = {
                    val stock = attention?.stock
                    if (stock != null) openCompany(stock) else openMarket()
                }
            ),
        shape = RoundedCornerShape(22.dp),
        color = palette.surface,
        border = BorderStroke(
            1.dp,
            if (attention == null) palette.border else palette.secondary.copy(alpha = 0.65f)
        )
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
            PremiumTelecomBackdrop(
                dark = dark,
                palette = palette,
                modifier = Modifier.matchParentSize()
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.surface.copy(alpha = 0.99f),
                            palette.surface.copy(alpha = 0.88f),
                            palette.surface.copy(alpha = 0.20f)
                        )
                    )
                )
            )

            Column(Modifier.padding(16.dp).fillMaxWidth(0.74f)) {
                PremiumSectionHeader("Why it's moving", palette)
                Spacer(Modifier.height(10.dp))

                if (attention == null) {
                    Text(
                        "No unusual move stands out right now",
                        color = palette.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "NSE Watcher will surface a movement here when the quote, peer context or evidence is strong enough to investigate.",
                        color = palette.muted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    PremiumChip("Explore market →", palette.secondary, palette)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PremiumCompanyLogo(attention.stock, 48)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    attention.stock.name.ifBlank { attention.stock.symbol },
                                    color = palette.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    CompanyResearchPresentation.percent(attention.stock.change),
                                    color = if (attention.stock.change < 0) palette.danger else palette.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(attention.stock.symbol, color = palette.muted, fontSize = 9.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val lead = attention.reasons.firstOrNull()
                    Text(
                        lead?.detail ?: "This move stands out in the latest comparable market observations.",
                        color = palette.text,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(9.dp))
                    val count = attention.reasons.size
                    PremiumChip(
                        count.toString() + " key point" + if (count == 1) "" else "s" + "  →",
                        palette.secondary,
                        palette
                    )
                }
            }

            if (attention != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(152.dp)
                        .padding(12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = palette.surface.copy(alpha = 0.91f),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        attention.reasons.take(3).forEach { reason ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    reason.title,
                                    color = palette.text,
                                    fontSize = 8.5.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("▲", color = palette.primary, fontSize = 7.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PremiumPracticeOverview(
    palette: PremiumHomePalette,
    enabled: Boolean,
    cash: Double,
    insights: PracticeLearningInsights,
    openPractice: () -> Unit
) {
    PremiumResponsivePair(
        forceHorizontal = true,
        first = {
            PremiumPracticeCard(
                palette = palette,
                enabled = enabled,
                cash = cash,
                openPractice = openPractice,
                modifier = Modifier.fillMaxWidth()
            )
        },
        second = {
            PremiumLearningProgressCard(
                palette = palette,
                insights = insights,
                openPractice = openPractice,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

@Composable
private fun PremiumPracticeCard(
    palette: PremiumHomePalette,
    enabled: Boolean,
    cash: Double,
    openPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumCardSurface(
        palette = palette,
        modifier = modifier.heightIn(min = 188.dp),
        onClick = openPractice
    ) {
        PremiumSectionHeader("Practice & learn", palette)
        Spacer(Modifier.height(9.dp))
        Row {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(palette.secondary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.School,
                    null,
                    tint = palette.secondary,
                    modifier = Modifier.size(29.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Build your skills",
                    color = palette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (enabled && cash.isFinite()) {
                        "Virtual cash " + practiceMoney(cash) + " available."
                    } else {
                        "Try a real market simulation with virtual funds."
                    },
                    color = palette.muted,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                )
            }
            PremiumLearningBars(
                palette = palette,
                modifier = Modifier.width(48.dp).height(82.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = openPractice,
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.primary,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 17.dp, vertical = 9.dp)
        ) {
            Text(
                if (enabled) "Open practice  →" else "Start practice  →",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PremiumLearningProgressCard(
    palette: PremiumHomePalette,
    insights: PracticeLearningInsights,
    openPractice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reviewed = insights.reviewedAtLeastOnce
    val total = insights.totalDecisions
    val fraction = if (total > 0) reviewed / total.toFloat() else 0f
    val percent = (fraction * 100).toInt()
    val label = when {
        total == 0 -> "Ready to start"
        insights.newEvidenceAfterReview > 0 -> "New evidence"
        insights.needsFirstReview > 0 -> "Keep reviewing"
        else -> "On track"
    }

    PremiumCardSurface(
        palette = palette,
        modifier = modifier.heightIn(min = 188.dp),
        onClick = openPractice
    ) {
        PremiumSectionHeader("Learning progress", palette)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PremiumProgressRing(
                fraction = fraction,
                percent = percent,
                palette = palette,
                modifier = Modifier.size(88.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = palette.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (total == 0) {
                        "Filled practice decisions will build your review history here."
                    } else {
                        reviewed.toString() + " of " + total + " filled decisions reviewed at least once."
                    },
                    color = palette.muted,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp
                )
                if (insights.newEvidenceAfterReview > 0) {
                    Spacer(Modifier.height(4.dp))
                    val count = insights.newEvidenceAfterReview
                    Text(
                        count.toString() + " decision" + if (count == 1) "" else "s" + " have later evidence.",
                        color = palette.secondary,
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumProgressRing(
    fraction: Float,
    percent: Int,
    palette: PremiumHomePalette,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().padding(7.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = palette.border,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (fraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(palette.secondary, palette.primary, palette.secondary)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            if (percent > 0) percent.toString() + "%" else "—",
            color = palette.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun PremiumLearningBars(
    palette: PremiumHomePalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val widths = size.width / 7f
        val gap = widths * 0.55f
        val heights = listOf(0.32f, 0.53f, 0.72f, 0.94f)
        heights.forEachIndexed { index, fraction ->
            val left = index * (widths + gap)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(palette.primary, palette.secondary)
                ),
                topLeft = androidx.compose.ui.geometry.Offset(
                    left,
                    size.height * (1f - fraction)
                ),
                size = androidx.compose.ui.geometry.Size(
                    widths,
                    size.height * fraction
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    widths * 0.25f
                )
            )
        }
    }
}
