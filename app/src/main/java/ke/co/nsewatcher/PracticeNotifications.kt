package ke.co.nsewatcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

internal object PracticeNotifications {
    private const val ACTION_OPEN_PRACTICE = "ke.co.nsewatcher.OPEN_PRACTICE"

    fun pendingIntent(context: Context, orderId: String): PendingIntent {
        val intent = Intent(context, DesignActivity::class.java).apply {
            action = ACTION_OPEN_PRACTICE
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            ("practice:" + orderId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun opensPractice(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_PRACTICE
}
