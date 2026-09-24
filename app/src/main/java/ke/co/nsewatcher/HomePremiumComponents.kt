package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ke.co.nsewatcher.data.MyStocksCache
import java.util.Locale

@Composable
internal fun PremiumResponsivePair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 370.dp) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                first()
                second()
            }
        }
    }
}

@Composable
internal fun PremiumCardSurface(
    palette: PremiumHomePalette,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
    } else modifier
    Surface(
        modifier = clickableModifier,
        shape = RoundedCornerShape(20.dp),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            content = content
        )
    }
}

@Composable
internal fun PremiumSectionHeader(
    title: String,
    palette: PremiumHomePalette,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = palette.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showChevron) {
            Icon(Icons.Default.ChevronRight, null, tint = palette.muted, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
internal fun PremiumChip(
    text: String,
    accent: Color,
    palette: PremiumHomePalette
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Text(
            text,
            color = accent,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun PremiumMessage(message: String, palette: PremiumHomePalette) {
    Text(message, color = palette.muted, fontSize = 10.5.sp, lineHeight = 15.sp)
}

@Composable
internal fun PremiumCompanyLogo(stock: Stock, size: Int) {
    val fallback = stock.logoUrl?.takeIf(String::isNotBlank)
        ?: "https://mystocks.africa/logos/" + stock.symbol.lowercase(Locale.US) + "-ke.svg"
    Surface(
        modifier = Modifier.size(size.dp),
        shape = RoundedCornerShape(11.dp),
        color = Color.White
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = fallback,
                contentDescription = stock.symbol,
                modifier = Modifier.fillMaxSize().padding(5.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                stock.symbol.take(2),
                color = Color(0xFF0A5A3A).copy(alpha = 0.24f),
                fontSize = 7.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
internal fun PremiumSparkline(
    values: List<Double>,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val valid = values.filter { it.isFinite() && it > 0.0 }
    if (valid.size < 2) {
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Text("Trend unavailable", color = tint.copy(alpha = 0.65f), fontSize = 7.5.sp)
        }
        return
    }

    Canvas(modifier) {
        val min = valid.minOrNull() ?: return@Canvas
        val max = valid.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        valid.forEachIndexed { index, value ->
            val x = size.width * index / valid.lastIndex.coerceAtLeast(1)
            val y = size.height - (((value - min) / range).toFloat() * size.height * 0.82f) - size.height * 0.08f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

internal fun formatHomeVolume(value: Long): String = when {
    value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB shares", value / 1_000_000_000.0)
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM shares", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fK shares", value / 1_000.0)
    else -> "$value shares"
}
