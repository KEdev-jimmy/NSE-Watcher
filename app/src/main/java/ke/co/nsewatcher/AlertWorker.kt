package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ke.co.nsewatcher.data.AlertStore
import ke.co.nsewatcher.data.NewsCache
import kotlinx.coroutines.flow.first
import ke.co.nsewatcher.domain.AlertType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

class AlertWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = AlertStore(applicationContext)
        val configuredAlerts = store.alerts.first()
        val prefs = applicationContext.getSharedPreferences("nse_watcher_preferences", Context.MODE_PRIVATE)
        val activeTypes = buildSet {
            if (prefs.getBoolean("price_alerts", true)) {
                add(AlertType.PRICE_ABOVE)
                add(AlertType.PRICE_BELOW)
            }
            if (prefs.getBoolean("market_alerts", true)) {
                add(AlertType.DAILY_GAIN)
                add(AlertType.DAILY_LOSS)
                add(AlertType.HIGH_VOLUME)
            }
            if (prefs.getBoolean("news_alerts", true)) {
                add(AlertType.NEWS)
                add(AlertType.CORPORATE_ACTION)
            }
        }
        val alerts = configuredAlerts.filter { it.enabled && it.type in activeTypes }
        if (alerts.isEmpty()) return Result.success()
        val marketStatus = MyStocksCache.loadMarketStatus()
        if (!marketStatus.isKnown || !marketStatus.isOpen) return Result.success()
        val stocks = MyStocksCache.loadStocks()
        if (stocks.isEmpty()) return Result.retry()

        val previous = store.previousPrices()
        val newsAlertsEnabled = alerts.any { it.type in setOf(AlertType.NEWS, AlertType.CORPORATE_ACTION) }
        val news = if (newsAlertsEnabled) NewsCache.loadFeed() else emptyList()
        val lastNews = store.lastNewsTriggerIds()
        val evaluated = AlertEvaluator.evaluate(alerts, stocks, previous, news, lastNews)
        val today = LocalDate.now(ZoneId.of("Africa/Nairobi")).toString()
        val lastDaily = store.lastDailyTriggerDates()
        val triggered = evaluated.filter { item ->
            val source = alerts.firstOrNull { it.id == item.alertId }
            source?.type !in setOf(AlertType.DAILY_GAIN, AlertType.DAILY_LOSS) || lastDaily[item.alertId] != today
        }
        store.recordNewsTriggers(
            triggered.filter { item -> alerts.firstOrNull { it.id == item.alertId }?.type in setOf(AlertType.NEWS, AlertType.CORPORATE_ACTION) }
                .associate { it.alertId to (news.firstOrNull { n -> n.symbol.equals(it.symbol, true) && isPublishedToday(n.publishedAt) && (it.message.endsWith(n.title) || it.message.contains(n.title)) }?.id ?: "") }
                .filterValues { it.isNotBlank() }
        )
        store.recordDailyTriggers(
            triggered.filter { item -> alerts.firstOrNull { it.id == item.alertId }?.type in setOf(AlertType.DAILY_GAIN, AlertType.DAILY_LOSS) }
                .map { it.alertId }.toSet(), today
        )
        store.recordPrices(stocks.filter { it.price.isFinite() && it.price > 0.0 }.associate { it.symbol.uppercase() to it.price })

        if (triggered.isNotEmpty()) {
            ensureChannel()
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                triggered.forEach { item ->
                    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_nse_watcher)
                        .setContentTitle(item.title)
                        .setContentText(item.symbol + ": " + item.message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()
                    manager.notify(item.alertId.hashCode(), notification)
                }
            }
        }
        return Result.success()
    }

    private fun isPublishedToday(publishedAt: String): Boolean {
        if (publishedAt.isBlank()) return false
        return try {
            Instant.parse(publishedAt).atZone(ZoneId.of("Africa/Nairobi")).toLocalDate() == LocalDate.now(ZoneId.of("Africa/Nairobi"))
        } catch (_: DateTimeParseException) {
            publishedAt.take(10) == LocalDate.now(ZoneId.of("Africa/Nairobi")).toString()
        }
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "NSE Watcher alerts", NotificationManager.IMPORTANCE_DEFAULT))
    }

    companion object {
        private const val WORK_NAME = "nse_watcher_alert_monitor"
        private const val CHANNEL_ID = "market_alerts"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
