package ke.co.nsewatcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val NasiGreen = Color(0xFF00A859)
private val NasiLightGreen = Color(0xFFE9F8F0)
private val NasiDarkGreen = Color(0xFF063D2A)
private val NasiTextDark = Color(0xFF12352A)
private val NasiMuted = Color(0xFF64756D)
private val NasiBorder = Color(0xFFDDE9E3)
private val NasiRed = Color(0xFFE94A4A)

@Composable
fun NasiPulseCard(
    value: Double = 237.59,
    changePct: Double = -3.38,
    observedLabel: String = "Latest available session"
) {
    var showDetails by remember { mutableStateOf(false) }
    val positive = changePct >= 0.0
    val accent = if (positive) NasiGreen else NasiRed
    val bg = if (positive) NasiLightGreen else Color(0xFFFFF1F1)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDetails = true },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, NasiBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(34.dp), RoundedCornerShape(10.dp), bg) {
                    Icon(Icons.Default.ShowChart, null, tint = accent, modifier = Modifier.padding(8.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("NSE ALL-SHARE INDEX", color = NasiDarkGreen, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Overall NSE market pulse", color = NasiMuted, fontSize = 9.sp)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = NasiLightGreen) {
                    Text("15 MIN DELAYED", color = NasiGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.US, "%.2f", value), color = NasiTextDark, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(8.dp))
                Text(String.format(Locale.US, "%+.2f%% today", changePct), color = accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            NasiSparkline(accent)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(NasiMuted))
                Spacer(Modifier.width(5.dp))
                Text(observedLabel, color = NasiMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text("View market pulse", color = NasiDarkGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ChevronRight, null, tint = NasiMuted, modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showDetails) {
        NasiPulseDialog(value, changePct, observedLabel) { showDetails = false }
    }
}

@Composable
private fun NasiSparkline(tint: Color) {
    Canvas(Modifier.fillMaxWidth().height(42.dp)) {
        val values = listOf(38f, 48f, 44f, 57f, 52f, 61f, 55f, 68f, 62f, 74f)
        val path = Path()
        values.forEachIndexed { index, point ->
            val x = if (values.size == 1) 0f else size.width * index / (values.size - 1)
            val y = size.height - (point / 100f * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, tint, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
private fun NasiPulseDialog(value: Double, changePct: Double, observedLabel: String, onDismiss: () -> Unit) {
    val positive = changePct >= 0.0
    val accent = if (positive) NasiGreen else NasiRed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NSE All-Share Index", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(String.format(Locale.US, "%.2f", value), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = NasiTextDark)
                Text(String.format(Locale.US, "%+.2f%% today", changePct), color = accent, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Text("What this shows", fontWeight = FontWeight.Bold)
                Text("The All-Share Index gives a broad view of how the NSE market is moving. It is an index level, not a share price.", color = NasiMuted, fontSize = 11.sp)
                Text("Data status: 15-minute delayed", color = NasiMuted, fontSize = 10.sp)
                Text(observedLabel, color = NasiMuted, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        shape = RoundedCornerShape(22.dp)
    )
}
