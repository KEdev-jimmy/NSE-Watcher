package ke.co.nsewatcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

internal object AlertNotifications {
    private const val ACTION = "ke.co.nsewatcher.OPEN_ALERT"
    fun pendingIntent(context: Context, target: AlertDestination): PendingIntent {
        val intent = Intent(context, DesignActivity::class.java).apply {
            action = ACTION
            data = Uri.parse("nsewatcher://alert/${Uri.encode(target.eventId)}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("alert_event", target.eventId)
            putExtra("alert_symbol", target.symbol)
            putExtra("alert_article", target.articleId)
            putExtra("alert_title", target.title)
            putExtra("alert_published", target.publishedAt)
            putExtra("alert_source", target.source)
            putExtra("alert_url", target.sourceUrl)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun destination(intent: Intent?): AlertDestination? {
        if (intent?.action != ACTION) return null
        val event = intent.getStringExtra("alert_event").orEmpty()
        val symbol = intent.getStringExtra("alert_symbol").orEmpty().trim().uppercase(java.util.Locale.ROOT)
        if (event.isBlank() || !symbol.matches(Regex("[A-Z0-9][A-Z0-9._-]{0,24}"))) return null
        return AlertDestination(event, symbol, intent.getStringExtra("alert_article").orEmpty(),
            intent.getStringExtra("alert_title").orEmpty(), intent.getStringExtra("alert_published").orEmpty(),
            intent.getStringExtra("alert_source").orEmpty(), intent.getStringExtra("alert_url").orEmpty())
    }
}
