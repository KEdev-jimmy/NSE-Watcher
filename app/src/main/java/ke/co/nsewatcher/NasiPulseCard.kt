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
 * Home portfolio placeholder. The component remains parameterized so a real
 * holdings/account source can be connected later. Until then, it never
 * presents zero as the user's actual account value or return.
 */
@Composable
fun NasiPulseCard(
    accountValue: Double? = null,
    changePct: Double? = null,
    changeAmount: Double? = null,
    live: Boolean = false
) {
    val connected = accountValue != null && accountValue.isFinite() &&
        changePct != null && changePct.isFinite() &&
        changeAmount != null && changeAmount.isFinite()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(108.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccountBorder),
        colors = CardDefaults.cardColors(containerColor = AccountCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AccountIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = "Total account value",
                        tint = AccountGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Total Account Value", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (connected) "Portfolio connected" else "Portfolio not connected",
                        color = AccountMuted,
                        fontSize = 8.sp
                    )
                }
                Surface(shape = RoundedCornerShape(16.dp), color = AccountLiveBg) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected && live) AccountGreen else AccountMuted))
                        Spacer(Modifier.width(5.dp))
                        Text(if (connected && live) "Live" else "Not connected", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (connected) {
                val value = accountValue!!
                val pct = changePct!!
                val amount = changeAmount!!
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(String.format(Locale.US, "KSh %,.2f", value), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(String.format(Locale.US, "%+.2f%%", pct), color = if (pct >= 0) AccountGreen else Color(0xFFFF817D), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text(String.format(Locale.US, "%+,.2f", amount).replace("+", "+KSh ").replace("-", "-KSh "), color = if (amount >= 0) AccountGreen else Color(0xFFFF817D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connect holdings to calculate your account value", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("No data", color = AccountMuted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
