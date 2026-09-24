package ke.co.nsewatcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

internal data class PracticeNotificationDestination(
    val orderId: String = ""
)

internal object PracticeNotifications {
    private const val ACTION_OPEN_PRACTICE = "ke.co.nsewatcher.OPEN_PRACTICE"
    private const val EXTRA_ORDER_ID = "practice_order_id"
    private val orderIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun pendingIntent(context: Context, orderId: String): PendingIntent {
        val intent = Intent(context, DesignActivity::class.java).apply {
            action = ACTION_OPEN_PRACTICE
            data = deepLink(orderId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ORDER_ID, normalizeOrderId(orderId))
        }
        return PendingIntent.getActivity(
            context,
            ("practice:" + normalized).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun destination(intent: Intent?): PracticeNotificationDestination? =
        destination(
            action = intent?.action,
            extraOrderId = intent?.getStringExtra(EXTRA_ORDER_ID),
            pathOrderId = intent?.data?.lastPathSegment?.let(Uri::decode)
        )

    internal fun destination(
        action: String?,
        extraOrderId: String?,
        pathOrderId: String?
    ): PracticeNotificationDestination? {
        if (action != ACTION_OPEN_PRACTICE) return null
        val fromExtra = normalizeOrderId(extraOrderId)
        if (fromExtra.isNotBlank()) return PracticeNotificationDestination(fromExtra)

        val fromPath = normalizeOrderId(pathOrderId)
        // Keep old already-posted notifications useful: they can still open Practice
        // even though they were created before an order id was embedded.
        return PracticeNotificationDestination(fromPath)
    }

    internal fun practiceAction(): String = ACTION_OPEN_PRACTICE

    internal fun deepLink(orderId: String): Uri {
        val normalized = normalizeOrderId(orderId)
        return Uri.Builder()
            .scheme("nsewatcher")
            .authority("practice")
            .appendPath("order")
            .appendPath(normalized)
            .build()
    }

    fun normalizeOrderId(raw: String?): String {
        val value = raw.orEmpty().trim()
        return value.takeIf { it.matches(orderIdPattern) }.orEmpty()
    }

    fun opensPractice(intent: Intent?): Boolean = destination(intent) != null
}
