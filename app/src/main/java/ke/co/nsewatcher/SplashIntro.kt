package ke.co.nsewatcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private val SplashBackground = Color(0xFFE9F8F0)
private val SplashNavy = Color(0xFF111827)
private val SplashBlue = Color(0xFF4D9AF0)
private val SplashGreen = Color(0xFF54D6A5)
private val SplashDarkGreen = Color(0xFF063D2A)
private val SplashMutedGreen = Color(0xFF58736A)
private val SplashButton = Color(0xFF19A96B)

private const val INTRO_MIN_MS = 3_000L

@Composable
fun NSEWatcherOpeningScreen(
    ready: Boolean,
    onFinished: () -> Unit
) {
    var stage by remember { mutableIntStateOf(0) }
    val lineProgress = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val logoAlpha = remember { Animatable(0f) }
    val glowPulse = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        logoScale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        stage = 1
        delay(260)
        stage = 2
        delay(220)
        lineProgress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        stage = 3
        delay(160)
        glowPulse.animateTo(1f, tween(180))
        glowPulse.animateTo(0f, tween(420))
        stage = 4
        delay(240)
        stage = 5
    }

    LaunchedEffect(ready, stage) {
        if (!ready || stage < 5) return@LaunchedEffect
        delay(620)
        onFinished()
    }

    val bars = listOf(
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (stage >= 2) 0.42f else 0f,
            animationSpec = tween(420, easing = FastOutSlowInEasing),
            label = "bar1"
        ).value,
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (stage >= 2) 0.62f else 0f,
            animationSpec = tween(460, delayMillis = 100, easing = FastOutSlowInEasing),
            label = "bar2"
        ).value,
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (stage >= 2) 0.80f else 0f,
            animationSpec = tween(500, delayMillis = 190, easing = FastOutSlowInEasing),
            label = "bar3"
        ).value
    )

    val textAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (stage >= 4) 1f else 0f,
        animationSpec = tween(480, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )
    val buttonAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (stage >= 5) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "buttonAlpha"
    )
    val buttonOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (stage >= 5) 0.dp else 28.dp,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "buttonOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        SplashSkyline(modifier = Modifier.align(Alignment.BottomCenter))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.18f))

            Box(
                modifier = Modifier
                    .size(176.dp)
                    .alpha(logoAlpha.value)
                    .graphicsLayerScale(logoScale.value)
            ) {
                NSEWatcherAnimatedLogo(
                    modifier = Modifier.fillMaxSize(),
                    bars = bars,
                    lineProgress = lineProgress.value,
                    glow = glowPulse.value
                )
            }

            Spacer(Modifier.height(42.dp))

            Column(
                modifier = Modifier.alpha(textAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NSE Watcher",
                    color = SplashNavy,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 47.sp,
                    lineHeight = 50.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(15.dp))
                Canvas(
                    modifier = Modifier
                        .width(245.dp)
                        .height(14.dp)
                ) {
                    val y = size.height / 2f
                    val dotX = size.width / 2f
                    drawLine(SplashGreen.copy(alpha = 0.42f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(dotX - 18.dp.toPx(), y), 1.5.dp.toPx())
                    drawLine(SplashGreen.copy(alpha = 0.42f), androidx.compose.ui.geometry.Offset(dotX + 18.dp.toPx(), y), androidx.compose.ui.geometry.Offset(size.width, y), 1.5.dp.toPx())
                    drawCircle(SplashGreen.copy(alpha = 0.85f), 5.dp.toPx(), androidx.compose.ui.geometry.Offset(dotX, y))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Real data. Clear calculations.\nTraceable evidence.",
                    color = SplashMutedGreen,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(0.34f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .offset(y = buttonOffset)
                    .alpha(buttonAlpha)
                    .clip(RoundedCornerShape(25.dp))
                    .background(SplashButton)
                    .clickable(enabled = ready, onClick = onFinished),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Start Exploring",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        text = "→",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NSEWatcherAnimatedLogo(
    modifier: Modifier,
    bars: List<Float>,
    lineProgress: Float,
    glow: Float
) {
    Canvas(modifier = modifier) {
        val left = size.width * 0.08f
        val top = size.height * 0.06f
        val right = size.width * 0.92f
        val bottom = size.height * 0.94f
        val corner = size.width * 0.23f

        drawRoundRect(
            color = SplashNavy,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
        )

        val chartLeft = size.width * 0.25f
        val chartRight = size.width * 0.78f
        val chartBottom = size.height * 0.73f
        val chartTop = size.height * 0.25f

        val barWidth = size.width * 0.12f
        val gap = size.width * 0.045f
        val barBase = chartBottom + size.height * 0.04f
        val heights = listOf(
            size.height * 0.20f * bars[0],
            size.height * 0.31f * bars[1],
            size.height * 0.42f * bars[2]
        )

        listOf(0, 1, 2).forEach { i ->
            val x = chartLeft + i * (barWidth + gap)
            val h = heights[i]
            if (h > 0f) {
                drawRoundRect(
                    color = SplashBlue,
                    topLeft = androidx.compose.ui.geometry.Offset(x, barBase - h),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
            }
        }

        val points = listOf(
            androidx.compose.ui.geometry.Offset(chartLeft - size.width * 0.02f, chartBottom - size.height * 0.08f),
            androidx.compose.ui.geometry.Offset(chartLeft + size.width * 0.20f, chartTop + size.height * 0.17f),
            androidx.compose.ui.geometry.Offset(chartLeft + size.width * 0.41f, chartTop + size.height * 0.23f),
            androidx.compose.ui.geometry.Offset(chartRight, chartTop)
        )

        val p = min(1f, max(0f, lineProgress))
        val totalSegments = points.size - 1
        val scaled = p * totalSegments
        val completed = min(totalSegments, scaled.toInt())
        val local = scaled - completed

        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        for (i in 1..completed) {
            path.lineTo(points[i].x, points[i].y)
        }
        if (completed < totalSegments) {
            val a = points[completed]
            val b = points[completed + 1]
            path.lineTo(
                a.x + (b.x - a.x) * local,
                a.y + (b.y - a.y) * local
            )
        }

        if (p > 0f) {
            drawPath(
                path = path,
                color = SplashGreen.copy(alpha = 0.18f + glow * 0.18f),
                style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = path,
                color = SplashGreen,
                style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        points.dropLast(1).forEachIndexed { index, point ->
            if (p >= (index + 1f) / totalSegments) {
                drawCircle(SplashGreen, 7.dp.toPx(), point)
            }
        }

        if (p >= 1f) {
            val tip = points.last()
            val arrowSize = 13.dp.toPx()
            drawLine(SplashGreen, tip, tip + androidx.compose.ui.geometry.Offset(-arrowSize, arrowSize * 0.2f), 5.dp.toPx(), StrokeCap.Round)
            drawLine(SplashGreen, tip, tip + androidx.compose.ui.geometry.Offset(-arrowSize * 0.15f, arrowSize), 5.dp.toPx(), StrokeCap.Round)
            if (glow > 0f) {
                drawCircle(SplashGreen.copy(alpha = glow * 0.16f), 27.dp.toPx(), tip)
            }
        }
    }
}

@Composable
private fun SplashSkyline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(230.dp)) {
        val base = size.height * 0.93f
        val buildingColor = SplashGreen.copy(alpha = 0.075f)
        val buildingColor2 = SplashGreen.copy(alpha = 0.055f)

        val widths = listOf(0.035f, 0.06f, 0.045f, 0.075f, 0.05f, 0.09f, 0.055f, 0.08f, 0.045f, 0.07f, 0.06f, 0.085f, 0.05f, 0.07f)
        val heights = listOf(0.24f, 0.39f, 0.30f, 0.53f, 0.34f, 0.65f, 0.42f, 0.58f, 0.32f, 0.48f, 0.70f, 0.43f, 0.56f, 0.36f)

        var x = -0.01f
        widths.forEachIndexed { index, widthFraction ->
            val w = size.width * widthFraction
            val h = size.height * heights[index]
            drawRect(
                color = if (index % 3 == 0) buildingColor else buildingColor2,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * x, base - h),
                size = androidx.compose.ui.geometry.Size(w, h)
            )
            if (index % 4 == 1) {
                drawLine(
                    color = buildingColor,
                    start = androidx.compose.ui.geometry.Offset(size.width * (x + widthFraction / 2f), base - h),
                    end = androidx.compose.ui.geometry.Offset(size.width * (x + widthFraction / 2f), base - h - size.height * 0.10f),
                    strokeWidth = 2.dp.toPx()
                )
            }
            x += widthFraction + 0.018f
        }

        val curve = Path()
        curve.moveTo(size.width * 0.08f, base)
        curve.cubicTo(
            size.width * 0.26f, size.height * 0.88f,
            size.width * 0.46f, size.height * 0.98f,
            size.width * 0.63f, size.height * 0.82f
        )
        curve.cubicTo(
            size.width * 0.78f, size.height * 0.68f,
            size.width * 0.89f, size.height * 0.74f,
            size.width * 1.04f, size.height * 0.42f
        )
        drawPath(
            curve,
            color = SplashGreen.copy(alpha = 0.12f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
        listOf(0.26f to 0.88f, 0.46f to 0.98f, 0.63f to 0.82f, 0.78f to 0.68f, 0.89f to 0.74f).forEach { (px, py) ->
            drawCircle(
                color = SplashGreen.copy(alpha = 0.15f),
                radius = 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * px, size.height * py)
            )
        }

        drawRect(
            color = SplashGreen.copy(alpha = 0.06f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, base),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - base)
        )
    }
}

private fun Modifier.graphicsLayerScale(scale: Float): Modifier =
    androidx.compose.ui.graphics.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
