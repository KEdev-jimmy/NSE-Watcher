package ke.co.nsewatcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ke.co.nsewatcher.data.CompanyDataChangeEvent
import java.time.Instant

internal data class PracticeReviewPrice(
    val fillPrice: Double,
    val currentPrice: Double,
    val observedAt: String,
    val changePct: Double
)

internal data class PracticeReviewEvidence(
    val id: String,
    val title: String,
    val detail: String,
    val source: String,
    val time: String,
    val story: NewsItem? = null
)

internal object PracticeReviewPresentation {
    fun captureDecision(
        stock: Stock,
        news: List<NewsItem>,
        companyEvents: List<CompanyDataChangeEvent>,
        capturedAt: Long
    ): PracticeDecisionSnapshot {
        val decisionTime = Instant.ofEpochMilli(capturedAt)
        val linkedNews = WatchlistPresentation.linkedNews(news, listOf(stock))
            .mapNotNull { item ->
                val published = CompanyResearchPresentation.timestamp(item.publishedAt) ?: return@mapNotNull null
                if (published.isAfter(decisionTime)) return@mapNotNull null
                PracticeDecisionEvidence(
                    id = "news:${item.id}",
                    title = item.title,
                    detail = item.summary.ifBlank { "Published company update." },
                    source = item.source.ifBlank { "Source unavailable" },
                    time = item.publishedAt
                )
            }

        val companyChanges = companyEvents.mapNotNull { event ->
            if (!WatchlistPresentation.symbol(event.symbol).equals(stock.symbol, ignoreCase = true)) return@mapNotNull null
            val detected = CompanyResearchPresentation.timestamp(event.observedAt) ?: return@mapNotNull null
            if (detected.isAfter(decisionTime)) return@mapNotNull null
            PracticeDecisionEvidence(
                id = event.id,
                title = event.title,
                detail = event.detail,
                source = event.source.ifBlank { "Company intelligence source" },
                time = event.observedAt
            )
        }

        val evidence = (linkedNews + companyChanges)
            .distinctBy { it.id }
            .sortedByDescending { CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN }
            .take(5)

        return PracticeDecisionSnapshot(
            capturedAt = capturedAt,
            quotePrice = stock.price.takeIf { it.isFinite() && it > 0.0 } ?: Double.NaN,
            quoteObservedAt = stock.observedAt,
            quoteSource = stock.source.ifBlank { "Source unavailable" },
            quoteDelayMinutes = stock.delayMinutes?.takeIf { it >= 0 },
            dailyChangePct = stock.change.takeIf { stock.changeAvailable && it.isFinite() },
            previousClose = stock.previousClose?.takeIf { it.isFinite() && it > 0.0 },
            evidence = evidence
        )
    }

    fun price(order: PracticeOrder, quote: PracticeQuote?): PracticeReviewPrice? {
        if (order.status != "FILLED" || order.price <= 0.0 || !order.price.isFinite()) return null
        val current = quote ?: return null
        if (!current.price.isFinite() || current.price <= 0.0) return null
        val quoteAt = CompanyResearchPresentation.timestamp(current.at) ?: return null
        val fillAt = CompanyResearchPresentation.timestamp(order.quoteAt)
            ?: order.filledAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
            ?: return null
        if (!quoteAt.isAfter(fillAt)) return null
        return PracticeReviewPrice(
            fillPrice = order.price,
            currentPrice = current.price,
            observedAt = current.at,
            changePct = (current.price / order.price - 1.0) * 100.0
        )
    }

    fun evidence(
        order: PracticeOrder,
        stock: Stock,
        news: List<NewsItem>,
        companyEvents: List<CompanyDataChangeEvent>
    ): List<PracticeReviewEvidence> {
        val decisionAt = Instant.ofEpochMilli(order.created)
        val linkedNews = WatchlistPresentation.linkedNews(news, listOf(stock))
            .mapNotNull { item ->
                val published = CompanyResearchPresentation.timestamp(item.publishedAt) ?: return@mapNotNull null
                if (!published.isAfter(decisionAt)) return@mapNotNull null
                PracticeReviewEvidence(
                    id = "news:${item.id}",
                    title = item.title,
                    detail = item.summary.ifBlank { "Published company update." },
                    source = item.source.ifBlank { "Source unavailable" },
                    time = item.publishedAt,
                    story = item
                )
            }

        val companyChanges = companyEvents.mapNotNull { event ->
            if (!WatchlistPresentation.symbol(event.symbol).equals(order.symbol, ignoreCase = true)) return@mapNotNull null
            val detected = CompanyResearchPresentation.timestamp(event.observedAt) ?: return@mapNotNull null
            if (!detected.isAfter(decisionAt)) return@mapNotNull null
            PracticeReviewEvidence(
                id = event.id,
                title = event.title,
                detail = event.detail,
                source = event.source.ifBlank { "Company intelligence source" },
                time = event.observedAt
            )
        }

        return (linkedNews + companyChanges)
            .distinctBy { it.id }
            .sortedByDescending { CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN }
    }

    fun savedReviews(state: PracticeState, order: PracticeOrder): List<PracticeEntry> =
        state.entries.filter { entry ->
            entry.kind == "REVIEW" && entry.id.startsWith("review:${order.id}:")
        }.sortedByDescending { it.time }
}

@Composable
internal fun PracticeDecisionReviewPanel(
    state: PracticeState,
    order: PracticeOrder,
    stock: Stock,
    news: List<NewsItem>,
    companyEvents: List<CompanyDataChangeEvent>,
    companyChangesUnavailable: Boolean,
    working: Boolean,
    onNews: (NewsItem) -> Unit,
    onResearch: () -> Unit,
    onSaveReview: (String) -> Unit
) {
    val price = PracticeReviewPresentation.price(
        order,
        state.quotes.firstOrNull { it.symbol.equals(order.symbol, ignoreCase = true) }
    )
    val evidence = PracticeReviewPresentation.evidence(order, stock, news, companyEvents)
    val savedReviews = PracticeReviewPresentation.savedReviews(state, order)
    var reflection by rememberSaveable(order.id) { mutableStateOf("") }

    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
        ResearchPanel {
            ResearchTitle("Your original decision")
            ResearchCaption("${order.side} ${order.shares} ${order.symbol} • ${practiceTime(order.created)}")
            if (order.note.isBlank()) {
                ResearchCaption("You did not record a reason for this order.")
            } else {
                ResearchBody(order.note)
            }
            ResearchCaption("This is your saved reasoning, not an investment recommendation.")
        }

        ResearchPanel {
            ResearchTitle("What you knew then")
            val snapshot = order.decisionSnapshot
            if (snapshot.capturedAt <= 0L) {
                ResearchCaption("Decision-time evidence was not recorded for this older order. NSE Watcher does not reconstruct it retrospectively.")
            } else {
                ResearchCaption("Snapshot saved ${practiceTime(snapshot.capturedAt)} when you confirmed this practice decision.")
                PracticeLine(
                    "Observed price",
                    snapshot.quotePrice.takeIf { it.isFinite() && it > 0.0 }?.let(::practiceMoney) ?: "Unavailable"
                )
                snapshot.dailyChangePct?.let {
                    PracticeLine("Daily change then", CompanyResearchPresentation.percent(it))
                }
                snapshot.previousClose?.let {
                    PracticeLine("Previous close then", practiceMoney(it))
                }
                val timing = buildString {
                    append(
                        if (snapshot.quoteObservedAt.isBlank()) "Observation time unavailable"
                        else "Quote observed " + CompanyResearchPresentation.date(snapshot.quoteObservedAt)
                    )
                    snapshot.quoteDelayMinutes?.takeIf { it > 0 }?.let { append(" • ").append(it).append("-min delayed") }
                }
                ResearchCaption(timing)
                ResearchCaption("Quote source: " + snapshot.quoteSource.ifBlank { "Source unavailable" })
                if (snapshot.evidence.isEmpty()) {
                    ResearchCaption("No matching loaded company news or detected company-data changes were available in the saved snapshot.")
                } else {
                    snapshot.evidence.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = ResearchBorder)
                        ResearchBody(item.title)
                        if (item.detail.isNotBlank()) ResearchCaption(item.detail)
                        ResearchCaption(item.source + " • " + CompanyResearchPresentation.date(item.time))
                    }
                }
                ResearchCaption("This preserves the context that was loaded at decision time; it does not prove that the evidence caused a price move.")
            }
        }

        ResearchPanel {
            ResearchTitle("What the price did afterward")
            if (price == null) {
                ResearchCaption("A later dated quote after the simulated fill is not available yet.")
            } else {
                PracticeLine("Simulated fill", practiceMoney(price.fillPrice))
                PracticeLine("Later observed price", practiceMoney(price.currentPrice))
                PracticeLine("Price change", CompanyResearchPresentation.percent(price.changePct))
                ResearchCaption("Later quote observed ${CompanyResearchPresentation.date(price.observedAt)}")
                ResearchCaption("This is price movement only. It is not total return and does not include dividends; your simulated fees are recorded separately.")
            }
        }

        ResearchPanel {
            ResearchTitle("Evidence since your decision")
            ResearchCaption("${evidence.size} later item${if (evidence.size == 1) "" else "s"} available from the loaded evidence sources.")
            if (companyChangesUnavailable) {
                ResearchCaption("Detected company-data changes could not be read. News evidence is still shown.")
            }
            if (evidence.isEmpty()) {
                ResearchCaption("No later loaded company news or detected company-data changes are available for this decision yet.")
            } else {
                evidence.take(6).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = ResearchBorder)
                    ResearchBody(item.title)
                    if (item.detail.isNotBlank()) ResearchCaption(item.detail)
                    ResearchCaption("${item.source} • ${CompanyResearchPresentation.date(item.time)}")
                    if (item.story != null) {
                        TextButton(onClick = { onNews(item.story) }) { Text("Read evidence →", color = ResearchGreen) }
                    } else {
                        TextButton(onClick = onResearch) { Text("Open Company Intelligence →", color = ResearchGreen) }
                    }
                }
            }
            ResearchCaption("Later evidence can challenge or support your original reasoning, but timing alone does not establish why the price moved.")
        }

        ResearchPanel {
            ResearchTitle("What changed in your thinking?")
            ResearchCaption("Write what you learned, what surprised you, and what evidence would change your view next time.")
            OutlinedTextField(
                value = reflection,
                onValueChange = { reflection = it.take(2000) },
                label = { Text("Decision review") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val text = reflection.trim()
                    if (text.isNotBlank()) {
                        onSaveReview(text)
                        reflection = ""
                    }
                },
                enabled = reflection.isNotBlank() && !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save decision review")
            }
            savedReviews.take(3).forEach { review ->
                HorizontalDivider(color = ResearchBorder)
                ResearchCaption("Saved ${practiceTime(review.time)}")
                ResearchBody(review.text)
            }
        }
    }
}
