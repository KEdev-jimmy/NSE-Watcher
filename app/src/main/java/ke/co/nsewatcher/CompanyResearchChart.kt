package ke.co.nsewatcher

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MyStocksCache
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun CompanyResearchChart(points: List<MyStocksCache.HistoryPoint>, range: String, loading: Boolean, retry: () -> Unit, title: String? = null, purchaseMarkers: List<Pair<Long, Double>> = emptyList(), allowZero: Boolean = false) {
    val dated = remember(points, allowZero) {
        points.mapNotNull { point ->
            CompanyResearchPresentation.timestamp(point.date)?.takeIf { point.close.isFinite() && (point.close > 0.0 || (allowZero && point.close == 0.0)) }
                ?.let { point to it.toEpochMilli() }
        }.sortedBy { it.second }.distinctBy { it.second }
    }
    var selected by remember(dated, range) { mutableStateOf<Int?>(null) }
    var zoom by remember(dated, range) { mutableFloatStateOf(1f) }
    var startFraction by remember(dated, range) { mutableFloatStateOf(0f) }
    fun displayValue(value: Double) = if (allowZero && value == 0.0) "KSh 0.00" else CompanyResearchPresentation.money(value)
    val density = LocalDensity.current
    val leftPx = with(density) { 54.dp.toPx() }
    val rightPx = with(density) { 14.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ResearchCaption(title ?: if (range == "1D") "Session price · KSh" else "$range price history · KSh", Modifier.weight(1f))
            if (zoom > 1.01f) TextButton(onClick = { zoom = 1f; startFraction = 0f; selected = null }) {
                Text("Reset zoom", color = ResearchGreen)
            }
        }
        when {
            loading && dated.isEmpty() -> Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ResearchGreen)
            }
            dated.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ResearchCaption("Dated chart observations are unavailable for this period.")
                TextButton(onClick = retry) { Text("Retry chart", color = ResearchGreen) }
            }
            else -> {
                val intraday = range == "1D" && dated.all { it.first.date.length > 10 }
                val firstTime = dated.first().second
                val lastTime = dated.last().second
                val firstDate = Instant.ofEpochMilli(firstTime).atZone(CompanyResearchPresentation.zone).toLocalDate()
                val lastDate = Instant.ofEpochMilli(lastTime).atZone(CompanyResearchPresentation.zone).toLocalDate()
                // Expand to the regular session only for actual same-day timed data.
                // Out-of-session timestamps remain at their real times, never clamped.
                val regularStart = firstDate.atTime(9, 30).atZone(CompanyResearchPresentation.zone).toInstant().toEpochMilli()
                val regularEnd = firstDate.atTime(15, 0).atZone(CompanyResearchPresentation.zone).toInstant().toEpochMilli()
                val domainStart = if (intraday && firstDate == lastDate) minOf(firstTime, regularStart) else firstTime
                val domainEnd = if (intraday && firstDate == lastDate) maxOf(lastTime, regularEnd) else maxOf(lastTime, firstTime + 86_400_000L)
                val totalSpan = (domainEnd - domainStart).toDouble().coerceAtLeast(1.0)
                val viewStart = domainStart + startFraction * totalSpan
                val viewSpan = totalSpan / zoom

                fun nearest(x: Float, width: Int): Int {
                    val fraction = ((x - leftPx) / (width - leftPx - rightPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val time = viewStart + fraction * viewSpan
                    return dated.indices.minByOrNull { abs(dated[it].second - time) } ?: 0
                }

                val chosen = selected?.let { dated.getOrNull(it) }
                val description = "$range price chart. ${dated.size} observations. First ${displayValue(dated.first().first.close)} on ${CompanyResearchPresentation.date(dated.first().first.date)}. Latest ${displayValue(dated.last().first.close)} on ${CompanyResearchPresentation.date(dated.last().first.date)}."
                Canvas(
                    Modifier.fillMaxWidth().height(220.dp)
                        .semantics { contentDescription = description }
                        .pointerInput(dated, zoom, startFraction) { detectTapGestures { point -> selected = nearest(point.x, size.width) } }
                        .pointerInput(dated, range) {
                            detectTransformGestures { centroid, pan, factor, _ ->
                                val plotWidth = (size.width - leftPx - rightPx).coerceAtLeast(1f)
                                val anchor = ((centroid.x - leftPx) / plotWidth).coerceIn(0f, 1f)
                                val oldSpan = 1f / zoom
                                val nextZoom = (zoom * factor).coerceIn(1f, 5f)
                                val newSpan = 1f / nextZoom
                                startFraction = (startFraction + anchor * (oldSpan - newSpan) - pan.x / plotWidth * newSpan)
                                    .coerceIn(0f, 1f - newSpan)
                                zoom = nextZoom
                                selected = null
                            }
                        }
                ) {
                    val left = leftPx
                    val right = size.width - rightPx
                    val top = 12.dp.toPx()
                    val bottom = size.height - 30.dp.toPx()
                    val low = dated.minOf { it.first.close }
                    val high = dated.maxOf { it.first.close }
                    val pad = maxOf((high - low) * 0.12, high * 0.002, 0.01)
                    val minY = low - pad
                    val maxY = high + pad
                    fun x(time: Long) = left + ((time - viewStart) / viewSpan).toFloat() * (right - left)
                    fun y(value: Double) = bottom - ((value - minY) / (maxY - minY)).toFloat() * (bottom - top)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(169, 188, 208)
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.RIGHT
                    }
                    repeat(4) { i ->
                        val value = maxY - (maxY - minY) * i / 3
                        val at = y(value)
                        drawLine(ResearchBorder, Offset(left, at), Offset(right, at), strokeWidth = 1.dp.toPx())
                        val label = String.format(Locale.US, if (high < 100) "%.2f" else if (high < 1000) "%.1f" else "%.0f", value)
                        drawContext.canvas.nativeCanvas.drawText(label, left - 6.dp.toPx(), at + 3.dp.toPx(), paint)
                    }
                    val line = Path()
                    dated.forEachIndexed { index, (point, time) ->
                        if (index == 0) line.moveTo(x(time), y(point.close)) else line.lineTo(x(time), y(point.close))
                    }
                    clipRect(left, top, right, bottom) {
                        if (dated.size > 1) {
                            val fill = Path().apply {
                                addPath(line); lineTo(x(dated.last().second), bottom); lineTo(x(dated.first().second), bottom); close()
                            }
                            drawPath(fill, Brush.verticalGradient(listOf(ResearchGreen.copy(alpha = 0.18f), Color.Transparent), top, bottom))
                            drawPath(line, ResearchGreen, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        dated.forEach { (point, time) -> drawCircle(ResearchGreen, if (dated.size == 1) 4.dp.toPx() else 1.5.dp.toPx(), Offset(x(time), y(point.close))) }
                        purchaseMarkers.filter { it.first >= viewStart && it.first <= viewStart + viewSpan }.forEach { (time, price) ->
                            if (price.isFinite() && price > 0) {
                                drawLine(ResearchMuted.copy(alpha = 0.5f), Offset(x(time), top), Offset(x(time), bottom), 1.dp.toPx())
                                drawCircle(ResearchText, 5.dp.toPx(), Offset(x(time), y(price)))
                                drawCircle(ResearchGreen, 3.dp.toPx(), Offset(x(time), y(price)))
                            }
                        }
                        chosen?.let { (point, time) ->
                            drawLine(ResearchMuted.copy(alpha = 0.6f), Offset(x(time), top), Offset(x(time), bottom), 1.dp.toPx())
                            drawCircle(ResearchText, 4.dp.toPx(), Offset(x(time), y(point.close)))
                        }
                    }
                    val formatter = DateTimeFormatter.ofPattern(if (intraday) "HH:mm" else if (range in listOf("3Y", "5Y")) "MMM yy" else "dd MMM", Locale.US)
                    repeat(3) { i ->
                        paint.textAlign = when (i) { 0 -> Paint.Align.LEFT; 2 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
                        val time = (viewStart + viewSpan * i / 2).toLong()
                        val label = Instant.ofEpochMilli(time).atZone(CompanyResearchPresentation.zone).format(formatter)
                        drawContext.canvas.nativeCanvas.drawText(label, left + (right - left) * i / 2, size.height - 7.dp.toPx(), paint)
                    }
                }
                if (chosen != null) ResearchCaption("${displayValue(chosen.first.close)} · ${CompanyResearchPresentation.date(chosen.first.date)}")
                else ResearchCaption(if (intraday) "Times in EAT · Tap an observation · Pinch to zoom" else "Dated observations · Tap to inspect · Pinch to zoom")
                if (dated.size == 1) ResearchCaption("Only one dated observation is available; no trend is inferred.")
                if (loading) ResearchCaption("Refreshing observations…")
            }
        }
    }
}

