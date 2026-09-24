package ke.co.nsewatcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

private val BrandTeal = Color(0xFF18F0C1)
private val BrandGreen = Color(0xFF00B983)
private val BrandBlue = Color(0xFF1677FF)
private val BrandDeep = Color(0xFF083E65)

@Composable
internal fun NseWatcherNMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = "NSE Watcher logo"
) {
    Canvas(
        modifier = modifier.then(
            if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
            else Modifier
        )
    ) {
        val w = size.width
        val h = size.height
        val radius = w * 0.10f

        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to BrandTeal,
                0.52f to BrandGreen,
                1f to BrandBlue
            ),
            topLeft = Offset(w * 0.07f, h * 0.18f),
            size = Size(w * 0.22f, h * 0.66f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF19F6C8),
                0.60f to BrandTeal,
                1f to BrandDeep
            ),
            topLeft = Offset(w * 0.71f, h * 0.07f),
            size = Size(w * 0.22f, h * 0.77f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )

        val diagonal = Path().apply {
            moveTo(w * 0.23f, h * 0.20f)
            lineTo(w * 0.42f, h * 0.20f)
            lineTo(w * 0.78f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.84f)
            lineTo(w * 0.58f, h * 0.84f)
            lineTo(w * 0.22f, h * 0.33f)
            close()
        }
        drawPath(
            path = diagonal,
            brush = Brush.linearGradient(
                colors = listOf(BrandBlue, BrandTeal, BrandGreen),
                start = Offset(w * 0.18f, h * 0.78f),
                end = Offset(w * 0.86f, h * 0.16f)
            )
        )

        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(w * 0.17f, h * 0.20f),
            end = Offset(w * 0.17f, h * 0.66f),
            strokeWidth = (w * 0.025f).coerceAtLeast(1f),
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun NseWatcherBrandLockup(
    modifier: Modifier = Modifier,
    dark: Boolean? = null,
    compact: Boolean = false
) {
    val resolvedDark = dark ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val palette = premiumHomePalette(resolvedDark)
    val title = palette.text
    val muted = palette.muted

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        NseWatcherNMark(Modifier.size(if (compact) 36.dp else 46.dp))
        Spacer(Modifier.width(if (compact) 9.dp else 11.dp))
        Column {
            Text(
                "NSE Watcher",
                color = title,
                fontSize = if (compact) 18.sp else 23.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(1.dp))
            Text(
                "LEARN  INVEST  GROW",
                color = muted,
                fontSize = if (compact) 7.5.sp else 8.5.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
