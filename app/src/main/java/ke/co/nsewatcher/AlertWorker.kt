package ke.co.nsewatcher

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import ke.co.nsewatcher.data.CompanyChangeStore
import ke.co.nsewatcher.data.CompanyIntelligenceCache
import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.data.NewsCache
import ke.co.nsewatcher.data.WatchlistStore
import ke.co.nsewatcher.domain.AlertType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Shared 15-minute background monitor for user alerts, pending Practice orders and
 * periodic watched-company data checks.
 *
 * Practice fills deliberately use only the current fetched quote. Stored quotes and
 * chart history are never replayed to infer a fill that may have happened while the
 * app was not checking.
 */
class AlertWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            val alertStore = AlertStore(applicationContext)
            val companyChangeStore = CompanyChangeStore(applicationContext)
            val practiceStore = PracticeStore(applicationContext)
            val prefs = applicationContext.getSharedPreferences("nse_watcher_preferences", Context.MODE_PRIVATE)
            val configuredAlerts = alertStore.alerts.first()
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
            val initialPractice = practiceStore.read()
            val practicePending = initialPractice.enabled && initialPractice.orders.any { it.status == "PENDING" }
            val now = Instant.now()
            val watchedSymbols = WatchlistStore(applicationContext).symbols.first().toSet()
            val companyChecks = companyChangeStore.syncAndDue(watchedSymbols, now)
            if (alerts.isEmpty() && !practicePending && companyChecks.isEmpty()) return Result.success()

            val newsEnabled = alerts.any { AlertEvaluator.isNews(it.type) }
            val priceAlertsEnabled = alerts.any { !AlertEvaluator.isNews(it.type) }
            val quotesNeeded = priceAlertsEnabled || practicePending

            // Announcement checks remain independent from quote/status availability.
            val newsResult = if (newsEnabled) NewsCache.loadFeedResult() else NewsCache.FeedResult(emptyList())
            val status = if (quotesNeeded) MyStocksCache.loadMarketStatus() else MyStocksCache.MarketStatus()
            val marketSession = quotesNeeded && status.isKnown && status.isOpen
            val stocks = if (marketSession) MyStocksCache.loadStocks() else emptyList()

            for (symbol in companyChecks) {
                val result = CompanyIntelligenceCache.load(symbol)
                if (result.error == null) {
                    companyChangeStore.recordObservation(symbol, result, now)
                } else {
                    // A failed source check is not a company change. Back off until the next
                    // scheduled company-data check instead of hammering the endpoint every 15 minutes.
                    companyChangeStore.markChecked(symbol, now)
                }
            }

            if (practicePending) {
                var filledOrders = emptyList<PracticeOrder>()
                practiceStore.update { current ->
                    val processed = PracticeOrderProcessor.process(
                        initial = current,
                        quotes = stocks,
                        marketOpen = status.isOpen,
                        marketKnown = status.isKnown,
                        now = now.toEpochMilli()
                    )
                    filledOrders = processed.filledOrders
                    processed.state
                }
                if (filledOrders.isNotEmpty() && prefs.getBoolean("app_alerts", true)) {
                    notifyPracticeFills(filledOrders)
                }
            }

            if (alerts.isNotEmpty()) {
                val alertPriceSession = priceAlertsEnabled && status.isKnown && status.isOpen
                val state = alertStore.monitorState()
                val evaluated = AlertEvaluator.evaluate(
                    alerts, stocks, state, newsResult.items, now, alertPriceSession,
                    alertStore.lastNewsTriggerIds(), alertStore.lastDailyTriggerDates()
                )
                val accepted = alertStore.recordEvaluation(
                    evaluated.map { it.event(now) },
                    AlertEvaluator.nextQuotes(state.quotes, stocks, now),
                    now
                )
                notifyAlerts(accepted)
            }

            val quoteFailure = quotesNeeded && (!status.isKnown || (status.isOpen && stocks.isEmpty()))
            return if (newsResult.error != null || quoteFailure) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Persistent alert checkpoints and PracticeStore's transaction lock make retries idempotent.
            return Result.retry()
        }
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun notifyAlerts(events: List<ke.co.nsewatcher.domain.AlertEvent>) {
        if (events.isEmpty() || !notificationsAllowed()) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "NSE Watcher alerts", NotificationManager.IMPORTANCE_DEFAULT)
        )
        events.forEach { event ->
            val notification = NotificationCompat.Builder(applicationContext, ALERT_CHANNEL_ID)
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

    private fun notifyPracticeFills(orders: List<PracticeOrder>) {
        if (!notificationsAllowed()) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(PRACTICE_CHANNEL_ID, "Practice Portfolio", NotificationManager.IMPORTANCE_DEFAULT)
        )
        orders.forEach { order ->
            val message = "${order.side.lowercase().replaceFirstChar { it.uppercase() }} ${order.shares} ${order.symbol} at ${CompanyResearchPresentation.money(order.price)}"
            val notification = NotificationCompat.Builder(applicationContext, PRACTICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nse_watcher)
                .setContentTitle("Practice order filled")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$message using an eligible observed quote. Simulation only."))
                .setContentIntent(PracticeNotifications.pendingIntent(applicationContext, order.id))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
            manager.notify(("practice:" + order.id).hashCode(), notification)
        }
    }

    companion object {
        private const val WORK_NAME = "nse_watcher_alert_monitor"
        private const val ALERT_CHANNEL_ID = "market_alerts"
        private const val PRACTICE_CHANNEL_ID = "practice_orders"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
