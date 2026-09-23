package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import android.Manifest
import android.os.Build
import kotlinx.coroutines.CancellationException
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
        try {
            val newsEnabled = alerts.any { AlertEvaluator.isNews(it.type) }
            val pricesEnabled = alerts.any { !AlertEvaluator.isNews(it.type) }
            // A quote/status failure must not prevent announcement processing.
            val newsResult = if (newsEnabled) NewsCache.loadFeedResult() else NewsCache.FeedResult(emptyList())
            val status = if (pricesEnabled) MyStocksCache.loadMarketStatus() else MyStocksCache.MarketStatus()
            val priceSession = pricesEnabled && status.isKnown && status.isOpen
            val stocks = if (priceSession) MyStocksCache.loadStocks() else emptyList()
            val now = Instant.now()
            val state = store.monitorState()
            val evaluated = AlertEvaluator.evaluate(alerts, stocks, state, newsResult.items, now, priceSession,
                store.lastNewsTriggerIds(), store.lastDailyTriggerDates())
            val accepted = store.recordEvaluation(evaluated.map { it.event(now) },
                AlertEvaluator.nextQuotes(state.quotes, stocks, now), now)

            if (accepted.isNotEmpty()) {
                ensureChannel()
                val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (allowed) {
                    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    accepted.forEach { event ->
                        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_nse_watcher)
                            .setContentTitle(event.title)
                            .setContentText("${event.symbol}: ${event.message}")
                            .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                            .setContentIntent(AlertNotifications.pendingIntent(applicationContext, AlertDestination.from(event)))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setOnlyAlertOnce(true)
                            .setAutoCancel(true)
                            .build()
                        manager.notify(event.id, 0, notification)
                    }
                }
            }
            return if (newsResult.error != null || (pricesEnabled && !status.isKnown) || (priceSession && stocks.isEmpty())) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Persistent checkpoints mean retries cannot rotate between old stories.
            return Result.retry()
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


