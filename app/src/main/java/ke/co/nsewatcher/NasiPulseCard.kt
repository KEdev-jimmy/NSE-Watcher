package ke.co.nsewatcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val AccountGreen = Color(0xFF35E39A)
private val AccountCard = Color(0xEE062F2A)
private val AccountBorder = Color(0xFF138A68)
private val AccountMuted = Color(0xFFA8C8BE)
private val AccountIconBg = Color(0xFF0B5D49)
private val AccountLiveBg = Color(0xFF0A4D3E)

/**
 * Hero portfolio card. HomeHero continues to call this in the same position.
 * The fixed height keeps the existing hero layout from moving while the
 * portfolio presentation replaces the old NSE index content.
 *
 * Portfolio values are parameters so a real holdings/account source can be
 * connected later. Zero is used until that source is connected rather than
 * inventing a user's portfolio value.
 */
@Composable
fun NasiPulseCard(
    accountValue: Double = 0.0,
    changePct: Double = 0.0,
    changeAmount: Double = 0.0,
    live: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(175.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AccountBorder),
        colors = CardDefaults.cardColors(containerColor = AccountCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(AccountIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = "Total account value",
                        tint = AccountGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(9.dp))

                Text(
                    "Total Account Value",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AccountLiveBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (live) AccountGreen else AccountMuted)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (live) "Live" else "Offline",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                String.format(Locale.US, "KSh %,.2f", accountValue),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )

            Spacer(Modifier.height(5.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(Locale.US, "%+.2f%%", changePct),
                    color = AccountGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(9.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(17.dp)
                        .background(AccountMuted.copy(alpha = 0.45f))
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    String.format(Locale.US, "%+,.2f", changeAmount)
                        .replace("+", "+KSh ")
                        .replace("-", "-KSh "),
                    color = AccountGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(3.dp))

            Text(
                "Today",
                color = AccountMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
