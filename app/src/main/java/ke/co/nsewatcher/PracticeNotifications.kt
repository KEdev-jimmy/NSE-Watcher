package ke.co.nsewatcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

internal data class PracticeNotificationDestination(
    val orderId: String = ""
)

internal object PracticeNotifications {
    private const val ACTION_OPEN_PRACTICE = "ke.co.nsewatcher.OPEN_PRACTICE"
    private const val EXTRA_ORDER_ID = "practice_order_id"
    private val orderIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun pendingIntent(context: Context, orderId: String): PendingIntent {
        val normalized = normalizeOrderId(orderId)
        val intent = Intent(context, DesignActivity::class.java).apply {
            action = ACTION_OPEN_PRACTICE
            data = Uri.parse("nsewatcher://practice/order/${Uri.encode(normalized)}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ORDER_ID, normalized)
        }
        return PendingIntent.getActivity(
            context,
            ("practice:" + normalized).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun destination(intent: Intent?): PracticeNotificationDestination? {
        if (intent?.action != ACTION_OPEN_PRACTICE) return null
        val fromExtra = normalizeOrderId(intent.getStringExtra(EXTRA_ORDER_ID))
        if (fromExtra.isNotBlank()) return PracticeNotificationDestination(fromExtra)

        val pathOrder = intent.data?.lastPathSegment?.let(Uri::decode).orEmpty()
        val fromPath = normalizeOrderId(pathOrder)
        // Keep old already-posted notifications useful: they can still open Practice
        // even though they were created before an order id was embedded.
        return PracticeNotificationDestination(fromPath)
    }

    fun normalizeOrderId(raw: String?): String {
        val value = raw.orEmpty().trim()
        return value.takeIf { it.matches(orderIdPattern) }.orEmpty()
    }

    fun opensPractice(intent: Intent?): Boolean = destination(intent) != null
}
