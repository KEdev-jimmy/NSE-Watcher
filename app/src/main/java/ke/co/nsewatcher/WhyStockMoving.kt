package ke.co.nsewatcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.co.nsewatcher.data.MovementIntelligenceCache

private val MovementGreen = Color(0xFF00A859)
private val MovementLight = Color(0xFFE9F8F0)
private val MovementDark = Color(0xFF083C27)
private val MovementText = Color(0xFF12231B)
private val MovementMuted = Color(0xFF6C7A72)
private val MovementBorder = Color(0xFFE1EAE5)

@Composable
fun WhyStockMovingSection(symbol: String) {
    var result by remember(symbol) { mutableStateOf(MovementIntelligenceCache.Result()) }
    var loading by remember(symbol) { mutableStateOf(true) }

    LaunchedEffect(symbol) {
        loading = true
        result = MovementIntelligenceCache.load(symbol)
        loading = false
    }

    SectionTitle("Why is this stock moving?", "Evidence around the latest price movement", Icons.Default.Insights)
    IntelligenceCard {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MovementGreen)
                Spacer(Modifier.size(9.dp))
                Text("Checking price movement and dated company evidence…", color = MovementMuted, fontSize = 10.sp)
            }
            result.error != null -> Text(result.error!!, color = MovementMuted, fontSize = 10.sp)
            result.move == null -> Text("A reliable movement window is not available yet.", color = MovementMuted, fontSize = 10.sp)
            else -> {
                Text(
                    result.move.change.ifBlank { "Movement available" },
                    color = if (result.move.change.startsWith("-")) Color(0xFFE04444) else MovementGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    movementWindow(result.move),
                    color = MovementMuted,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    result.summary.ifBlank { "No movement explanation is available from the current evidence." },
                    color = MovementText,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(12.dp))

                if (result.evidence.isEmpty()) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        RoundedCornerShape(12.dp),
                        MovementLight
                    ) {
                        Text(
                            "No dated company event was found close enough to the movement to link it as evidence.",
                            Modifier.padding(11.dp),
                            color = MovementText,
                            fontSize = 10.sp
                        )
                    }
                } else {
                    Text("What we found", color = MovementText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(5.dp))
                    result.evidence.take(5).forEachIndexed { index, evidence ->
                        MovementEvidenceRow(evidence)
                        if (index < result.evidence.take(5).lastIndex) {
                            androidx.compose.material3.HorizontalDivider(color = MovementBorder)
                        }
                    }
                }

                Spacer(Modifier.height(9.dp))
                Text(
                    "Correlation is not proof of causation. Market-wide and sector-wide drivers are not yet attributed in this first evidence pass.",
                    color = MovementMuted,
                    fontSize = 8.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MovementEvidenceRow(evidence: MovementIntelligenceCache.Evidence) {
    val relationshipLabel = when (evidence.relationship.lowercase()) {
        "related" -> "RELATED"
        "possible" -> "POSSIBLE"
        else -> "NOT ESTABLISHED"
    }
    val relationshipColor = when (evidence.relationship.lowercase()) {
        "related" -> MovementGreen
        "possible" -> Color(0xFFB7791F)
        else -> MovementMuted
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(Modifier.size(32.dp), RoundedCornerShape(9.dp), MovementLight) {
            Icon(
                if (evidence.eventType == "financial-results" || evidence.eventType == "dividend") Icons.Default.Insights else Icons.Default.Newspaper,
                null,
                tint = MovementGreen,
                modifier = Modifier.padding(7.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(evidence.title, color = MovementText, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 3)
            Spacer(Modifier.height(2.dp))
            Text(
                listOf(evidence.date, evidence.source).filter(String::isNotBlank).joinToString(" • "),
                color = MovementMuted,
                fontSize = 8.sp
            )
            if (evidence.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(evidence.description, color = MovementMuted, fontSize = 8.sp, maxLines = 2, lineHeight = 12.sp)
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(relationshipLabel, color = relationshipColor, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun movementWindow(move: MovementIntelligenceCache.Move): String {
    val label = when (move.periodDays) {
        1 -> "Latest 1-day movement"
        7 -> "Latest 1-week movement"
        30 -> "Latest 1-month movement"
        90 -> "Latest 3-month movement"
        else -> "Latest movement"
    }
    return if (move.from.isNotBlank() && move.to.isNotBlank()) "$label • ${move.from} to ${move.to}" else label
}
