package ke.co.nsewatcher

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
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AlertWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val store = AlertStore(applicationContext)
        val alerts = store.alerts.first()
        if (alerts.none { it.enabled }) return Result.success()
        val stocks = MyStocksCache.loadStocks()
        if (stocks.isEmpty()) return Result.retry()

        val previous = store.previousPrices()
        val triggered = AlertEvaluator.evaluate(alerts, stocks, previous)
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
