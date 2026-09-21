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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

private val SplashBackground = Color(0xFFE9F8F0)
private val SplashNavy = Color(0xFF111827)
private val SplashBlue = Color(0xFF4D9AF0)
private val SplashGreen = Color(0xFF54D6A5)
private val SplashTextDark = Color(0xFF12352A)
private val SplashMutedGreen = Color(0xFF58736A)
private val SplashButton = Color(0xFF19A96B)

@Composable
fun NSEWatcherOpeningScreen(
    ready: Boolean,
    onFinished: () -> Unit
) {
    var showLogoOnly by rememberSaveable { mutableStateOf(true) }
    var stage by remember { mutableIntStateOf(0) }
    val lineProgress = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val logoAlpha = remember { Animatable(0f) }
    val glowPulse = remember { Animatable(0f) }

    // The first screen is a short branded hand-off from Android's system splash.
    // It transitions automatically into the real startup gate; the second screen
    // remains until the user taps Start Exploring.
    LaunchedEffect(Unit) {
        delay(1_650)
        showLogoOnly = false

        logoAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        logoScale.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        stage = 1
        delay(180)
        stage = 2
        delay(180)
        lineProgress.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
        stage = 3
        delay(130)
        stage = 4
        delay(160)
        stage = 5

        while (isActive) {
            glowPulse.animateTo(1f, tween(240))
            glowPulse.animateTo(0f, tween(700))
            delay(1_700)
        }
    }

    if (showLogoOnly) {
        NSEWatcherLogoOnlyIntro()
        return
    }

    val bars = listOf(
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (stage >= 2) 0.48f else 0f,
            animationSpec = tween(420, easing = FastOutSlowInEasing),
            label = "bar1"
        ).value,
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (stage >= 2) 0.70f else 0f,
            animationSpec = tween(470, delayMillis = 90, easing = FastOutSlowInEasing),
            label = "bar2"
        ).value,
        androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (stage >= 2) 0.92f else 0f,
            animationSpec = tween(520, delayMillis = 170, easing = FastOutSlowInEasing),
            label = "bar3"
        ).value
    )

    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (stage >= 4) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    val buttonAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (stage >= 5) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "buttonAlpha"
    )
    val buttonOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (stage >= 5) 0.dp else 22.dp,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "buttonOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        SplashSkyline(modifier = Modifier.align(Alignment.BottomCenter))
        SplashWaves(modifier = Modifier.align(Alignment.BottomCenter))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(168.dp)
                    .alpha(logoAlpha.value)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    }
            ) {
                NSEWatcherAnimatedLogo(
                    bars = bars,
                    lineProgress = lineProgress.value,
                    glow = glowPulse.value
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.72f))
                    .padding(horizontal = 15.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "NSE  •  KENYA MARKET INTELLIGENCE",
                    color = SplashMutedGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.75.sp
                )
            }

            Spacer(Modifier.height(13.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxWidth(0.66f).height(8.dp)) {
                    val centerY = size.height / 2f
                    drawLine(
                        color = SplashGreen.copy(alpha = 0.28f),
                        start = androidx.compose.ui.geometry.Offset.Zero.x.let { androidx.compose.ui.geometry.Offset(it, centerY) },
                        end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = SplashGreen.copy(alpha = 0.28f),
                        radius = 2.4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.42f, centerY)
                    )
                    drawCircle(
                        color = SplashGreen,
                        radius = 5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.57f, centerY)
                    )
                    drawCircle(
                        color = SplashBackground,
                        radius = 2.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.57f, centerY)
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            Column(
                modifier = Modifier.alpha(contentAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NSE Watcher",
                    color = SplashNavy,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 42.sp,
                    lineHeight = 45.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Real data. Clear calculations.\nTraceable evidence.",
                    color = SplashTextDark,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(17.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    SplashFeature("↗", "Live\nMarket Data")
                    SplashFeature("✓", "Verified\nSources")
                    SplashFeature("✦", "Smarter\nInsights")
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(buttonAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.padding(bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!ready) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = SplashButton,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Preparing your market view…",
                            color = SplashMutedGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(50.dp))
                                .background(SplashButton)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Market view ready",
                            color = SplashButton,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .widthIn(min = 240.dp, max = 340.dp)
                        .fillMaxWidth(0.88f)
                        .height(60.dp)
                        .offset(y = buttonOffset)
                        .clip(RoundedCornerShape(21.dp))
                        .background(
                            if (ready) SplashButton else SplashNavy.copy(alpha = 0.10f)
                        )
                        .clickable(enabled = ready, onClick = onFinished),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (ready) "Start Exploring" else "Preparing…",
                            color = if (ready) Color.White else SplashMutedGreen,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (ready) {
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "→",
                                color = Color.White,
                                fontSize = 25.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (ready) "Tap to enter NSE Watcher" else "Loading verified market data and news",
                    color = SplashMutedGreen.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable
private fun NSEWatcherLogoOnlyIntro() {
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            rotation = (rotation + 30f) % 360f
            delay(80)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF123454), Color(0xFF061422), Color(0xFF020A12))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(236.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val base = size.minDimension * 0.33f
                    drawCircle(
                        color = SplashGreen.copy(alpha = 0.08f),
                        radius = base * 1.58f
                    )
                    drawCircle(
                        color = SplashGreen.copy(alpha = 0.28f),
                        radius = base * 1.34f,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = SplashGreen.copy(alpha = 0.20f),
                        radius = base * 1.13f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    val arcRadius = base * 1.44f
                    drawArc(
                        color = SplashGreen,
                        startAngle = rotation,
                        sweepAngle = 92f,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - arcRadius,
                            center.y - arcRadius
                        ),
                        size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                NSEWatcherAnimatedLogo(
                    modifier = Modifier.size(164.dp),
                    bars = listOf(1f, 1f, 1f),
                    lineProgress = 1f,
                    glow = 1f
                )
            }

            Spacer(Modifier.height(28.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = SplashGreen,
                strokeWidth = 3.dp
            )

            Spacer(Modifier.height(25.dp))

            Text(
                text = "N S E   W A T C H E R",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }
    }
}

@Composable
private fun SplashFeature(icon: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(94.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.48f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = SplashButton,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = SplashTextDark,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NSEWatcherAnimatedLogo(
    modifier: Modifier = Modifier,
    bars: List<Float>,
    lineProgress: Float,
    glow: Float
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val inset = size.width * 0.075f
        val shadowOffset = size.width * 0.035f
        val face = size.width - inset * 2f
        val corner = size.width * 0.23f

        // Soft neon aura.
        if (glow > 0f) {
            drawRoundRect(
                color = SplashGreen.copy(alpha = 0.08f + glow * 0.11f),
                topLeft = androidx.compose.ui.geometry.Offset(inset - shadowOffset, inset - shadowOffset),
                size = androidx.compose.ui.geometry.Size(face + shadowOffset * 2f, face + shadowOffset * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + shadowOffset, corner + shadowOffset)
            )
        }

        // Dark lower extrusion for the 3D depth.
        drawRoundRect(
            color = Color(0xFF020812),
            topLeft = androidx.compose.ui.geometry.Offset(inset + shadowOffset, inset + shadowOffset * 1.7f),
            size = androidx.compose.ui.geometry.Size(face, face),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
        )

        // Bright rim: this is the clean light border visible around the mark.
        drawRoundRect(
            color = Color(0xFFF7FFFB),
            topLeft = androidx.compose.ui.geometry.Offset(inset - 1.5.dp.toPx(), inset - 1.5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(face + 3.dp.toPx(), face + 3.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + 1.5.dp.toPx(), corner + 1.5.dp.toPx())
        )

        // Glossy navy face.
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF173E69), Color(0xFF0B1B32), Color(0xFF050D1A))
            ),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(face, face),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
        )

        // Top glass highlight.
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = androidx.compose.ui.geometry.Offset(inset + face * 0.05f, inset + face * 0.04f),
            size = androidx.compose.ui.geometry.Size(face * 0.90f, face * 0.30f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.72f, corner * 0.72f)
        )

        val chartLeft = size.width * 0.27f
        val chartRight = size.width * 0.78f
        val chartBottom = size.width * 0.73f
        val chartTop = size.width * 0.25f

        val barWidth = size.width * 0.105f
        val gap = size.width * 0.052f
        val barBase = chartBottom + size.width * 0.035f
        val heights = listOf(
            size.height * 0.18f * bars[0],
            size.height * 0.29f * bars[1],
            size.height * 0.40f * bars[2]
        )

        val barBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF8BC5FF), Color(0xFF2C7CF0))
        )
        val barShadow = Color(0xFF0A2E64)

        listOf(0, 1, 2).forEach { i ->
            val x = chartLeft + i * (barWidth + gap)
            val h = heights[i]
            if (h > 0f) {
                drawRoundRect(
                    color = barShadow,
                    topLeft = androidx.compose.ui.geometry.Offset(x + size.width * 0.012f, barBase - h + size.width * 0.012f),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())
                )
                drawRoundRect(
                    brush = barBrush,
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
                color = SplashGreen.copy(alpha = 0.16f + glow * 0.18f),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = path,
                color = SplashGreen,
                style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = path,
                color = Color(0xFFB5FFDB),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        if (p >= 1f) {
            val tip = points.last()
            val arrowSize = 13.dp.toPx()
            drawLine(
                SplashGreen,
                tip,
                tip + androidx.compose.ui.geometry.Offset(-arrowSize, arrowSize * 0.2f),
                5.dp.toPx(),
                StrokeCap.Round
            )
            drawLine(
                SplashGreen,
                tip,
                tip + androidx.compose.ui.geometry.Offset(-arrowSize * 0.15f, arrowSize),
                5.dp.toPx(),
                StrokeCap.Round
            )
            if (glow > 0f) {
                drawCircle(
                    SplashGreen.copy(alpha = glow * 0.18f),
                    27.dp.toPx(),
                    tip
                )
            }
        }
    }
}


@Composable
private fun SplashWaves(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(155.dp)) {
        fun wave(offset: Float, amplitude: Float, alpha: Float, width: Float) {
            val path = Path()
            path.moveTo(-size.width * 0.05f, size.height * offset)
            path.cubicTo(
                size.width * 0.22f, size.height * (offset - amplitude),
                size.width * 0.42f, size.height * (offset + amplitude),
                size.width * 0.66f, size.height * (offset - amplitude * 0.45f)
            )
            path.cubicTo(
                size.width * 0.82f, size.height * (offset - amplitude * 0.78f),
                size.width * 0.94f, size.height * (offset + amplitude * 0.40f),
                size.width * 1.06f, size.height * (offset - amplitude * 0.20f)
            )
            drawPath(
                path,
                color = SplashGreen.copy(alpha = alpha),
                style = Stroke(width = width.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        wave(0.82f, 0.16f, 0.16f, 1.2f)
        wave(0.92f, 0.14f, 0.25f, 2.0f)
        wave(1.02f, 0.16f, 0.34f, 3.2f)
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

