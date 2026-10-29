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

private val BrandTeal = Color(0xFF16D9AD)
private val BrandGreen = Color(0xFF008965)
private val BrandDeep = Color(0xFF086C77)
private val BrandAmber = Color(0xFFF7B500)

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
        val radius = w * 0.075f

        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to BrandTeal,
                0.52f to BrandGreen,
                1f to BrandDeep
            ),
            topLeft = Offset(w * 0.07f, h * 0.22f),
            size = Size(w * 0.22f, h * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF1AE4BD),
                0.60f to BrandTeal,
                1f to BrandDeep
            ),
            topLeft = Offset(w * 0.71f, h * 0.22f),
            size = Size(w * 0.22f, h * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
        )

        val diagonal = Path().apply {
            moveTo(w * 0.23f, h * 0.22f)
            lineTo(w * 0.42f, h * 0.22f)
            lineTo(w * 0.78f, h * 0.72f)
            lineTo(w * 0.78f, h * 0.84f)
            lineTo(w * 0.58f, h * 0.84f)
            lineTo(w * 0.22f, h * 0.36f)
            close()
        }
        drawPath(
            path = diagonal,
            brush = Brush.linearGradient(
                colors = listOf(BrandTeal, BrandGreen, BrandDeep),
                start = Offset(w * 0.18f, h * 0.20f),
                end = Offset(w * 0.88f, h * 0.88f)
            )
        )
        drawRoundRect(
            color = BrandAmber,
            topLeft = Offset(w * 0.72f, h * 0.015f),
            size = Size(w * 0.18f, h * 0.16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.035f)
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
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        NseWatcherNMark(Modifier.size(if (compact) 33.dp else 42.dp))
        Spacer(Modifier.width(if (compact) 8.dp else 11.dp))
        Text(
            "NSE WATCHER",
            color = title,
            fontSize = if (compact) 17.sp else 19.sp,
            letterSpacing = 0.2.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
