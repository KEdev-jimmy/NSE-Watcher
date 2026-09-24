package ke.co.nsewatcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import ke.co.nsewatcher.data.CompanyDataChangeEvent
import java.time.Duration
import java.time.Instant

internal data class PracticeNotificationDestination(
    val orderId: String = ""
)

internal data class PracticeEvidenceNotificationCandidate(
    val orderId: String,
    val symbol: String,
    val evidenceId: String,
    val title: String,
    val detail: String,
    val source: String,
    val time: String,
    val coveredKeys: Set<String>
)

internal object PracticeNotifications {
    private const val ACTION_OPEN_PRACTICE = "ke.co.nsewatcher.OPEN_PRACTICE"
    private const val EXTRA_ORDER_ID = "practice_order_id"
    private val orderIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun pendingIntent(context: Context, orderId: String): PendingIntent {
        val normalized = normalizeOrderId(orderId)
        val intent = Intent(context, DesignActivity::class.java).apply {
            action = ACTION_OPEN_PRACTICE
            data = Uri.parse(deepLinkValue(normalized))
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

    internal fun deepLinkValue(orderId: String): String {
        val normalized = normalizeOrderId(orderId)
        return "nsewatcher://practice/order/" + encodePathSegment(normalized)
    }

    private fun encodePathSegment(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                ':' -> append("%3A")
                else -> append(ch)
            }
        }
    }

    fun normalizeOrderId(raw: String?): String {
        val value = raw.orEmpty().trim()
        return value.takeIf { it.matches(orderIdPattern) }.orEmpty()
    }

    internal fun evidenceCandidates(
        state: PracticeState,
        news: List<NewsItem>,
        companyEvents: List<CompanyDataChangeEvent>,
        now: Instant,
        baselineAt: Instant,
        notifiedKeys: Set<String>
    ): List<PracticeEvidenceNotificationCandidate> {
        if (!state.enabled) return emptyList()
        return state.orders.asSequence()
            .filter { order -> practiceEvidenceOrderActive(state, order, now) }
            .mapNotNull { order ->
                val symbol = WatchlistPresentation.symbol(order.symbol)
                if (symbol.isBlank()) return@mapNotNull null
                val quote = state.quotes.firstOrNull {
                    WatchlistPresentation.symbol(it.symbol) == symbol
                }
                val stock = Stock(
                    symbol = symbol,
                    name = quote?.name?.ifBlank { symbol } ?: symbol,
                    price = quote?.price ?: Double.NaN,
                    change = 0.0,
                    history = emptyList(),
                    sector = quote?.sector ?: "Other",
                    observedAt = quote?.at.orEmpty(),
                    changeAvailable = false,
                    volumeAvailable = false
                )
                val latestReviewAt = PracticeReviewPresentation.savedReviews(state, order)
                    .maxOfOrNull { it.time }
                    ?.let(Instant::ofEpochMilli)
                val unseen = PracticeReviewPresentation.evidence(
                    order = order,
                    stock = stock,
                    news = news,
                    companyEvents = companyEvents
                ).filter { evidence ->
                    val at = CompanyResearchPresentation.timestamp(evidence.time) ?: return@filter false
                    val key = evidenceKey(order.id, evidence.id)
                    at.isAfter(baselineAt) &&
                        !at.isAfter(now) &&
                        Duration.between(at, now) <= Duration.ofDays(7) &&
                        (latestReviewAt == null || at.isAfter(latestReviewAt)) &&
                        key !in notifiedKeys
                }
                if (unseen.isEmpty()) return@mapNotNull null
                val latest = unseen.maxByOrNull {
                    CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN
                } ?: return@mapNotNull null
                PracticeEvidenceNotificationCandidate(
                    orderId = order.id,
                    symbol = symbol,
                    evidenceId = latest.id,
                    title = latest.title,
                    detail = latest.detail,
                    source = latest.source,
                    time = latest.time,
                    coveredKeys = unseen.map { evidenceKey(order.id, it.id) }.toSet()
                )
            }
            .sortedByDescending {
                CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN
            }
            .toList()
    }

    internal fun evidenceKey(orderId: String, evidenceId: String): String =
        normalizeOrderId(orderId) + "|" + evidenceId.trim()

    fun opensPractice(intent: Intent?): Boolean = destination(intent) != null
}
