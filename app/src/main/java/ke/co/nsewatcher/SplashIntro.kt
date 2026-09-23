package ke.co.nsewatcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val LaunchNavy = Color(0xFF061625)
private val LaunchCard = Color(0xFF0A1F32)
private val LaunchRaised = Color(0xFF10283D)
private val LaunchGreen = Color(0xFF00D084)
private val LaunchText = Color(0xFFF4F7FA)
private val LaunchMuted = Color(0xFFA9BCD0)

internal fun shouldShowFirstLaunchOnboarding(
    completed: Boolean,
    firstInstallTime: Long,
    lastUpdateTime: Long
): Boolean = !completed && firstInstallTime >= lastUpdateTime - 1_000L

@Composable
fun NSEWatcherOpeningScreen(
    ready: Boolean,
    onFinished: () -> Unit
) {
    var minimumElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_100)
        minimumElapsed = true
    }
    LaunchedEffect(ready, minimumElapsed) {
        if (ready && minimumElapsed) {
            delay(180)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunchNavy)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            NseWatcherBarsMark(Modifier.size(86.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                "NSE Watcher",
                color = LaunchText,
                fontSize = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Learn. Track. Investigate. Grow.",
                color = LaunchMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.70f).height(4.dp).clip(CircleShape),
                color = LaunchGreen,
                trackColor = LaunchRaised
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (ready) "Opening NSE Watcher…" else "Loading market data…",
                color = LaunchMuted,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(34.dp))
        }
    }
}

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val icon: ImageVector
)

private val onboardingPages = listOf(
    OnboardingPage(
        eyebrow = "FOLLOW WHAT MATTERS",
        title = "Track what matters",
        body = "Follow your favourite companies and see meaningful changes without digging through a generic market dashboard.",
        icon = Icons.Default.NotificationsActive
    ),
    OnboardingPage(
        eyebrow = "UNDERSTAND THE STORY",
        title = "Understand the story",
        body = "Connect price moves with financials, company news and traceable evidence. AI explanations stay grounded in the supplied sources.",
        icon = Icons.Default.Insights
    ),
    OnboardingPage(
        eyebrow = "PRACTICE AND LEARN",
        title = "Practice and learn",
        body = "Test an investment idea with virtual money, record your reasoning and review what happened later — without placing a real trade.",
        icon = Icons.Default.AccountBalanceWallet
    )
)

@Composable
fun NSEWatcherOnboardingScreen(onFinished: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val item = onboardingPages[page]

    Box(
        Modifier.fillMaxSize().background(LaunchNavy).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding"
        ) { current ->
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NseWatcherBarsMark(Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("NSE Watcher", color = LaunchText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(0.75f))
                Surface(
                    modifier = Modifier.size(150.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = LaunchCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LaunchRaised)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(current.icon, null, tint = LaunchGreen, modifier = Modifier.size(62.dp))
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                            shape = CircleShape,
                            color = LaunchGreen
                        ) {
                            NseWatcherBarsMark(Modifier.size(30.dp), darkBackground = false)
                        }
                    }
                }
                Spacer(Modifier.height(34.dp))
                Text(
                    current.eyebrow,
                    color = LaunchGreen,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    current.title,
                    color = LaunchText,
                    fontSize = 29.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    current.body,
                    color = LaunchMuted,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(1f))
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (page < onboardingPages.lastIndex) {
                        Text(
                            "Skip",
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = onFinished)
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            color = Color(0xFF5F6F66),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Spacer(Modifier.width(52.dp))
                    }
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        onboardingPages.indices.forEach { index ->
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (index == page) 18.dp else 7.dp, 7.dp)
                                    .clip(CircleShape)
                                    .background(if (index == page) Color(0xFF00A86B) else Color(0xFFD8E1DC))
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (page == onboardingPages.lastIndex) onFinished() else page++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A86B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (page == onboardingPages.lastIndex) "Start Exploring" else "Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun NseWatcherBarsMark(
    modifier: Modifier = Modifier,
    darkBackground: Boolean = true
) {
    Canvas(
        modifier.semantics { contentDescription = "NSE Watcher market bars logo" }
    ) {
        val w = size.width
        val h = size.height
        val barWidth = w * 0.13f
        val gap = w * 0.075f
        val total = barWidth * 4 + gap * 3
        val startX = (w - total) / 2f
        val heights = listOf(0.34f, 0.50f, 0.69f, 0.88f)
        heights.forEachIndexed { index, fraction ->
            val left = startX + index * (barWidth + gap)
            drawRoundRect(
                color = LaunchGreen,
                topLeft = androidx.compose.ui.geometry.Offset(left, h * (1f - fraction)),
                size = androidx.compose.ui.geometry.Size(barWidth, h * fraction),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.28f)
            )
        }
        val lineColor = if (darkBackground) Color(0xFFB8FFE0) else Color.White
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(startX - w * 0.04f, h * 0.73f),
            end = androidx.compose.ui.geometry.Offset(startX + total + w * 0.02f, h * 0.20f),
            strokeWidth = (w * 0.035f).coerceAtLeast(2f),
            cap = StrokeCap.Round
        )
    }
}
