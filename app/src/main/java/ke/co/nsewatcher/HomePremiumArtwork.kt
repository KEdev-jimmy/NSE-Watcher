package ke.co.nsewatcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
internal fun PremiumNairobiBackdrop(
    dark: Boolean,
    palette: PremiumHomePalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        drawRect(
            brush = if (dark) {
                Brush.linearGradient(
                    listOf(
                        Color(0xFF071D40),
                        Color(0xFF14275D),
                        Color(0xFF4B2859)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            } else {
                Brush.linearGradient(
                    listOf(
                        Color(0xFFF2FBF6),
                        Color(0xFFDFF3F7),
                        Color(0xFFFFEBC8)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            }
        )

        val skylineBase = size.height * 0.88f
        val buildingColor = if (dark) Color(0xFF071529) else Color(0xFF587A80)
        val buildingSoft = if (dark) Color(0xFF102A48) else Color(0xFF88A5A4)
        val start = size.width * 0.48f
        val widths = listOf(0.055f, 0.070f, 0.045f, 0.095f, 0.055f, 0.075f, 0.050f)
        val heights = listOf(0.25f, 0.40f, 0.31f, 0.63f, 0.37f, 0.49f, 0.29f)
        var x = start

        widths.indices.forEach { index ->
            val w = size.width * widths[index]
            val h = size.height * heights[index]
            drawRect(
                color = if (index % 2 == 0) buildingColor else buildingSoft,
                topLeft = Offset(x, skylineBase - h),
                size = Size(w, h)
            )
            val window = if (dark) Color(0xFFFFCC77) else Color.White.copy(alpha = 0.72f)
            var wy = skylineBase - h + 10f
            while (wy < skylineBase - 9f) {
                var wx = x + 7f
                while (wx < x + w - 5f) {
                    drawCircle(
                        window.copy(alpha = if (dark) 0.55f else 0.42f),
                        radius = 1.6f,
                        center = Offset(wx, wy)
                    )
                    wx += 12f
                }
                wy += 13f
            }
            x += w + size.width * 0.012f
        }

        val towerX = size.width * 0.70f
        val towerW = size.width * 0.095f
        val towerH = size.height * 0.66f
        drawRect(
            color = if (dark) Color(0xFF09223F) else Color(0xFF5F8587),
            topLeft = Offset(towerX, skylineBase - towerH),
            size = Size(towerW, towerH)
        )
        drawLine(
            color = if (dark) palette.primary else Color(0xFF176B56),
            start = Offset(towerX + towerW * 0.12f, skylineBase - towerH * 0.72f),
            end = Offset(towerX + towerW * 0.88f, skylineBase - towerH * 0.72f),
            strokeWidth = 4f
        )
        drawLine(
            color = buildingColor,
            start = Offset(towerX + towerW / 2f, skylineBase - towerH),
            end = Offset(towerX + towerW / 2f, skylineBase - towerH - size.height * 0.12f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        val road = Path().apply {
            moveTo(size.width * 0.45f, size.height)
            cubicTo(
                size.width * 0.62f, size.height * 0.83f,
                size.width * 0.79f, size.height * 0.96f,
                size.width, size.height * 0.76f
            )
        }
        drawPath(
            road,
            color = if (dark) {
                Color(0xFF5A7BFF).copy(alpha = 0.55f)
            } else {
                Color(0xFFE6A534).copy(alpha = 0.45f)
            },
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}

@Composable
internal fun PremiumTelecomBackdrop(
    dark: Boolean,
    palette: PremiumHomePalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.linearGradient(
                if (dark) {
                    listOf(Color(0xFF102553), Color(0xFF261E4D), Color(0xFF092A43))
                } else {
                    listOf(Color(0xFFE7F6F5), Color(0xFFFFE6C4), Color(0xFFD7EEF3))
                },
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )

        val base = size.height * 0.88f
        val skyline = if (dark) Color(0xFF091A31) else Color(0xFF6F8D91)
        repeat(7) { index ->
            val w = size.width * (0.035f + (index % 3) * 0.012f)
            val h = size.height * (0.16f + (index % 4) * 0.045f)
            val x = size.width * (0.58f + index * 0.055f)
            drawRect(skyline, Offset(x, base - h), Size(w, h))
        }

        val mastX = size.width * 0.82f
        drawLine(
            color = skyline,
            start = Offset(mastX, base),
            end = Offset(mastX, size.height * 0.18f),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = skyline,
            start = Offset(mastX - size.width * 0.06f, size.height * 0.38f),
            end = Offset(mastX + size.width * 0.06f, size.height * 0.38f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = skyline,
            start = Offset(mastX - size.width * 0.05f, size.height * 0.50f),
            end = Offset(mastX + size.width * 0.05f, size.height * 0.50f),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
        repeat(3) { index ->
            val radius = size.width * (0.035f + index * 0.025f)
            drawArc(
                color = palette.primary.copy(alpha = 0.34f - index * 0.07f),
                startAngle = 300f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(mastX - radius, size.height * 0.27f - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 2f)
            )
        }
    }
}
