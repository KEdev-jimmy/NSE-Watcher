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
import ke.co.nsewatcher.data.MarketData
import ke.co.nsewatcher.data.WatchlistStore
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shared 15-minute background monitor for user alerts, pending Practice orders and
 * periodic company-data checks for watched companies plus recent filled Practice decisions.
 *
 * Practice fills deliberately use only the current fetched quote. Stored quotes and
 * chart history are never replayed to infer a fill that may have happened while the
 * app was not checking.
 */
internal fun automaticWatchlistAlertRules(
    watchedSymbols: Set<String>,
    configured: List<PriceAlert>,
    newsEnabled: Boolean,
    corporateEnabled: Boolean,
    newsSinceMillis: Long = 0L,
    corporateSinceMillis: Long = 0L
): List<PriceAlert> = buildList {
    watchedSymbols.map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct().forEach { symbol ->
        if (newsEnabled && configured.none { it.enabled && it.symbol.equals(symbol, true) && it.type == AlertType.NEWS }) {
            add(PriceAlert("watchlist-news:$symbol", symbol, AlertType.NEWS, newsSinceMillis.takeIf { it > 0 }?.toDouble(), true))
        }
        if (corporateEnabled && configured.none { it.enabled && it.symbol.equals(symbol, true) && it.type == AlertType.CORPORATE_ACTION }) {
            add(PriceAlert("watchlist-corporate:$symbol", symbol, AlertType.CORPORATE_ACTION, corporateSinceMillis.takeIf { it > 0 }?.toDouble(), true))
        }
    }
}


private val PRACTICE_EVIDENCE_RETENTION: Duration = Duration.ofDays(30)

internal fun practiceEvidenceOrderActive(
    state: PracticeState,
    order: PracticeOrder,
    now: Instant,
    retention: Duration = PRACTICE_EVIDENCE_RETENTION
): Boolean {
    if (!state.enabled || order.status != "FILLED") return false
    val fillAt = when {
        order.filledAt > 0L -> Instant.ofEpochMilli(order.filledAt)
        order.quoteAt.isNotBlank() -> runCatching { Instant.parse(order.quoteAt) }.getOrNull()
        else -> null
    } ?: return false

    val latestReviewAt = state.entries.asSequence()
        .filter { it.kind == "REVIEW" && it.id.startsWith("review:" + order.id + ":") }
        .mapNotNull { entry ->
            entry.time.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        }
        .maxOrNull()

    val latestActivity = listOfNotNull(fillAt, latestReviewAt).maxOrNull() ?: fillAt
    return !latestActivity.isAfter(now) && !latestActivity.isBefore(now.minus(retention))
}

internal fun practiceEvidenceMonitoringSymbols(
    state: PracticeState,
    now: Instant,
    retention: Duration = PRACTICE_EVIDENCE_RETENTION
): Set<String> {
    if (!state.enabled) return emptySet()
    return state.orders.asSequence()
        .filter { practiceEvidenceOrderActive(state, it, now, retention) }
        .mapNotNull { WatchlistPresentation.symbol(it.symbol).takeIf(String::isNotBlank) }
        .toSet()
}

internal fun companyEvidenceMonitoringSymbols(
    watchedSymbols: Set<String>,
    practiceState: PracticeState,
    now: Instant
): Set<String> =
    watchedSymbols.asSequence()
        .map(WatchlistPresentation::symbol)
        .filter(String::isNotBlank)
        .toSet() + practiceEvidenceMonitoringSymbols(practiceState, now)

class AlertWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            val alertStore = AlertStore(applicationContext)
            val companyChangeStore = CompanyChangeStore(applicationContext)
            val practiceStore = PracticeStore(applicationContext)
            val prefs = applicationContext.getSharedPreferences("nse_watcher_preferences", Context.MODE_PRIVATE)
            val configuredAlerts = alertStore.alerts.first()
            val now = Instant.now()
            val watchedSymbols = WatchlistStore(applicationContext).symbols.first()
                .map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotBlank() }.toSet()
            val configuredCorporateEnabled = if (prefs.contains("corporate_action_alerts")) {
                prefs.getBoolean("corporate_action_alerts", true)
            } else prefs.getBoolean("news_alerts", true)
            val practiceAlertsEnabled = if (prefs.contains("practice_alerts")) {
                prefs.getBoolean("practice_alerts", true)
            } else prefs.getBoolean("app_alerts", true)
            val configuredTypes = buildSet {
                if (prefs.getBoolean("price_alerts", true)) {
                    add(AlertType.PRICE_ABOVE)
                    add(AlertType.PRICE_BELOW)
                }
                if (prefs.getBoolean("market_alerts", true)) {
                    add(AlertType.DAILY_GAIN)
                    add(AlertType.DAILY_LOSS)
                    add(AlertType.HIGH_VOLUME)
                }
                if (prefs.getBoolean("news_alerts", true)) add(AlertType.NEWS)
                if (configuredCorporateEnabled) add(AlertType.CORPORATE_ACTION)
            }
            val activeConfigured = configuredAlerts.filter { it.enabled && it.type in configuredTypes }
            val watchlistNewsEnabled = prefs.getBoolean("watchlist_news_alerts", false)
            val watchlistCorporateEnabled = prefs.getBoolean("watchlist_corporate_alerts", false)
            val automaticWatchlistAlerts = automaticWatchlistAlertRules(
                watchedSymbols = watchedSymbols,
                configured = activeConfigured,
                newsEnabled = watchlistNewsEnabled,
                corporateEnabled = watchlistCorporateEnabled,
                newsSinceMillis = prefs.getLong("watchlist_news_enabled_at", now.toEpochMilli()),
                corporateSinceMillis = prefs.getLong("watchlist_corporate_enabled_at", now.toEpochMilli())
            )
            val alerts = activeConfigured + automaticWatchlistAlerts
            val initialPractice = practiceStore.read()
            val practicePending = initialPractice.enabled && initialPractice.orders.any { it.status == "PENDING" }
            val activePracticeEvidenceSymbols = practiceEvidenceMonitoringSymbols(initialPractice, now)
            val practiceEvidenceNotificationsEnabled =
                practiceAlertsEnabled && activePracticeEvidenceSymbols.isNotEmpty()
            val evidenceMonitoringSymbols = companyEvidenceMonitoringSymbols(
                watchedSymbols = watchedSymbols,
                practiceState = initialPractice,
                now = now
            )
            val companyChecks = companyChangeStore.syncAndDue(evidenceMonitoringSymbols, now)
            if (alerts.isEmpty() && !practicePending && companyChecks.isEmpty() && !practiceEvidenceNotificationsEnabled) {
                syncPracticeEvidenceNotificationActivation(
                    enabled = practiceAlertsEnabled,
                    now = now
                )
                return Result.success()
            }

            val newsEnabled = alerts.any { AlertEvaluator.isNews(it.type) } || practiceEvidenceNotificationsEnabled
            val priceAlertsEnabled = alerts.any { !AlertEvaluator.isNews(it.type) }
            val quotesNeeded = priceAlertsEnabled || practicePending

            // Announcement checks remain independent from quote/status availability.
            val newsResult = if (newsEnabled) MarketData.newsFeed() else ke.co.nsewatcher.data.NewsCache.FeedResult(emptyList())
            val status = if (quotesNeeded) MarketData.status() else ke.co.nsewatcher.data.MyStocksCache.MarketStatus()
            val marketSession = quotesNeeded && status.isKnown && status.isOpen
            val stocks = if (marketSession) MarketData.stocks() else emptyList()

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

            if (practiceEvidenceNotificationsEnabled) {
                val baseline = syncPracticeEvidenceNotificationActivation(
                    enabled = true,
                    now = now
                )
                if (baseline != null) {
                    val companyEvents = companyChangeStore.events.first()
                    val notifiedKeys = prefs.getStringSet(PRACTICE_EVIDENCE_NOTIFIED_KEY, emptySet())
                        .orEmpty()
                        .toSet()
                    val candidates = PracticeNotifications.evidenceCandidates(
                        state = initialPractice,
                        news = newsResult.items,
                        companyEvents = companyEvents,
                        now = now,
                        baselineAt = baseline,
                        notifiedKeys = notifiedKeys
                    )
                    if (candidates.isNotEmpty() && notificationsAllowed()) {
                        notifyPracticeEvidence(candidates)
                        recordPracticeEvidenceNotifications(
                            candidates = candidates,
                            activeOrderIds = initialPractice.orders
                                .filter { practiceEvidenceOrderActive(initialPractice, it, now) }
                                .map { it.id }
                                .toSet()
                        )
                    }
                }
            } else {
                syncPracticeEvidenceNotificationActivation(
                    enabled = practiceAlertsEnabled,
                    now = now
                )
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
                if (filledOrders.isNotEmpty() && practiceAlertsEnabled) {
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
        val silent = notificationSoundIsSilent()
        val channelId = ensureChannel(manager, practice = false, silent = silent)
        events.forEach { event ->
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_nse_watcher)
                .setContentTitle(event.title)
                .setContentText("${event.symbol}: ${event.message}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                .setContentIntent(AlertNotifications.pendingIntent(applicationContext, AlertDestination.from(event)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(silent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
            manager.notify(event.id, 0, notification)
        }
    }

    private fun notifyPracticeEvidence(candidates: List<PracticeEvidenceNotificationCandidate>) {
        if (candidates.isEmpty() || !notificationsAllowed()) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val silent = notificationSoundIsSilent()
        val channelId = ensureChannel(manager, practice = true, silent = silent)
        candidates.forEach { candidate ->
            val message = candidate.title.ifBlank { "New company evidence is available for review." }
            val detail = buildString {
                append(message)
                if (candidate.source.isNotBlank()) {
                    append(" • ")
                    append(candidate.source)
                }
                append(". Review what changed since your practice decision.")
            }
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_nse_watcher)
                .setContentTitle("New evidence since your " + candidate.symbol + " practice decision")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(PracticeNotifications.pendingIntent(applicationContext, candidate.orderId))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(silent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
            manager.notify(("practice-evidence:" + candidate.orderId).hashCode(), notification)
        }
    }

    private fun syncPracticeEvidenceNotificationActivation(
        enabled: Boolean,
        now: Instant
    ): Instant? {
        val prefs = prefsForNotifications()
        val wasEnabled = prefs.getBoolean(PRACTICE_EVIDENCE_ACTIVE_KEY, false)
        if (!enabled) {
            prefs.edit()
                .putBoolean(PRACTICE_EVIDENCE_ACTIVE_KEY, false)
                .putLong(PRACTICE_EVIDENCE_BASELINE_KEY, now.toEpochMilli())
                .apply()
            return null
        }
        if (!wasEnabled) {
            prefs.edit()
                .putBoolean(PRACTICE_EVIDENCE_ACTIVE_KEY, true)
                .putLong(PRACTICE_EVIDENCE_BASELINE_KEY, now.toEpochMilli())
                .apply()
            return null
        }
        val baselineMillis = prefs.getLong(PRACTICE_EVIDENCE_BASELINE_KEY, 0L)
        return baselineMillis.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
    }

    private fun recordPracticeEvidenceNotifications(
        candidates: List<PracticeEvidenceNotificationCandidate>,
        activeOrderIds: Set<String>
    ) {
        val prefs = prefsForNotifications()
        val existing = prefs.getStringSet(PRACTICE_EVIDENCE_NOTIFIED_KEY, emptySet()).orEmpty()
        val activePrefixes = activeOrderIds.map { PracticeNotifications.normalizeOrderId(it) + "|" }
        val retained = existing.filter { key -> activePrefixes.any(key::startsWith) }.toMutableSet()
        candidates.flatMapTo(retained) { it.coveredKeys }
        prefs.edit()
            .putStringSet(PRACTICE_EVIDENCE_NOTIFIED_KEY, retained.take(1000).toSet())
            .apply()
    }

    private fun prefsForNotifications() =
        applicationContext.getSharedPreferences("nse_watcher_preferences", Context.MODE_PRIVATE)

    private fun notifyPracticeFills(orders: List<PracticeOrder>) {
        if (!notificationsAllowed()) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val silent = notificationSoundIsSilent()
        val channelId = ensureChannel(manager, practice = true, silent = silent)
        orders.forEach { order ->
            val message = "${order.side.lowercase().replaceFirstChar { it.uppercase() }} ${order.shares} ${order.symbol} at ${CompanyResearchPresentation.money(order.price)}"
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_nse_watcher)
                .setContentTitle("Practice order filled")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$message using an eligible observed quote. Simulation only."))
                .setContentIntent(PracticeNotifications.pendingIntent(applicationContext, order.id))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(silent)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
            manager.notify(("practice:" + order.id).hashCode(), notification)
        }
    }

    private fun notificationSoundIsSilent(): Boolean =
        applicationContext.getSharedPreferences("nse_watcher_preferences", Context.MODE_PRIVATE)
            .getString("notification_sound", "Default").equals("Silent", true)

    private fun ensureChannel(manager: NotificationManager, practice: Boolean, silent: Boolean): String {
        val id = when {
            practice && silent -> PRACTICE_CHANNEL_SILENT
            practice -> PRACTICE_CHANNEL_DEFAULT
            silent -> ALERT_CHANNEL_SILENT
            else -> ALERT_CHANNEL_DEFAULT
        }
        val name = when {
            practice && silent -> "Practice Portfolio (silent)"
            practice -> "Practice Portfolio"
            silent -> "NSE Watcher alerts (silent)"
            else -> "NSE Watcher alerts"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    if (silent) {
                        setSound(null, null)
                        enableVibration(false)
                    }
                }
            )
        }
        return id
    }

    companion object {
        private const val WORK_NAME = "nse_watcher_alert_monitor"
        private const val ALERT_CHANNEL_DEFAULT = "market_alerts_v2"
        private const val ALERT_CHANNEL_SILENT = "market_alerts_silent_v2"
        private const val PRACTICE_CHANNEL_DEFAULT = "practice_orders_v2"
        private const val PRACTICE_CHANNEL_SILENT = "practice_orders_silent_v2"
        private const val PRACTICE_EVIDENCE_ACTIVE_KEY = "practice_evidence_notifications_active_v1"
        private const val PRACTICE_EVIDENCE_BASELINE_KEY = "practice_evidence_notifications_baseline_v1"
        private const val PRACTICE_EVIDENCE_NOTIFIED_KEY = "practice_evidence_notifications_notified_v1"

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
