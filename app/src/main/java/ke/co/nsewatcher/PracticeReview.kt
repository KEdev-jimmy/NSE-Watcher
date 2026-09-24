package ke.co.nsewatcher

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.Alignment
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

internal enum class PracticeDecisionReviewState {
    NEEDS_REVIEW,
    NEW_EVIDENCE,
    REVIEWED
}

internal data class PracticeDecisionReviewItem(
    val order: PracticeOrder,
    val state: PracticeDecisionReviewState,
    val lastReviewAt: Long? = null,
    val latestEvidence: PracticeReviewEvidence? = null,
    val evidenceSinceReview: Int = 0
)

internal data class PracticeDecisionReviewSummary(
    val total: Int,
    val needsReview: Int,
    val newEvidence: Int,
    val reviewed: Int
)

internal data class PracticeLearningTheme(
    val label: String,
    val count: Int
)

internal data class PracticeLearningInsights(
    val totalDecisions: Int,
    val reasonsRecorded: Int,
    val reviewedAtLeastOnce: Int,
    val needsFirstReview: Int,
    val newEvidenceAfterReview: Int,
    val recurringThemes: List<PracticeLearningTheme> = emptyList(),
    val topSector: String? = null,
    val topSectorCount: Int = 0
)

internal object PracticeLearningInsightsPresentation {
    private val themeKeywords = linkedMapOf(
        "Company results" to listOf("result", "earnings", "profit", "revenue", "eps", "margin", "financial"),
        "Dividends" to listOf("dividend", "payout", "yield"),
        "Price / valuation" to listOf("price", "valuation", "value", "cheap", "expensive", "undervalued", "overvalued"),
        "Growth" to listOf("growth", "expand", "expansion"),
        "News / announcements" to listOf("news", "announcement", "announced", "update")
    )

    fun insights(
        state: PracticeState,
        companies: List<Stock>,
        items: List<PracticeDecisionReviewItem>
    ): PracticeLearningInsights {
        if (!state.enabled) {
            return PracticeLearningInsights(
                totalDecisions = 0,
                reasonsRecorded = 0,
                reviewedAtLeastOnce = 0,
                needsFirstReview = 0,
                newEvidenceAfterReview = 0
            )
        }

        val reasons = items.map { it.order.note.trim() }.filter(String::isNotBlank)
        val themes = themeKeywords.mapNotNull { (label, keywords) ->
            val count = reasons.count { reason ->
                val text = reason.lowercase()
                keywords.any(text::contains)
            }
            count.takeIf { it >= 2 }?.let { PracticeLearningTheme(label, it) }
        }.sortedWith(compareByDescending<PracticeLearningTheme> { it.count }.thenBy { it.label })

        val sectorBySymbol = companies.associate { stock ->
            WatchlistPresentation.symbol(stock.symbol) to stock.sector.trim()
        }
        val sectorCounts = items.mapNotNull { item ->
            sectorBySymbol[WatchlistPresentation.symbol(item.order.symbol)]
                ?.takeIf { it.isNotBlank() && !it.equals("Other", ignoreCase = true) }
        }.groupingBy { it }.eachCount()
        val topSector = sectorCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.takeIf { it.value >= 2 }

        return PracticeLearningInsights(
            totalDecisions = items.size,
            reasonsRecorded = reasons.size,
            reviewedAtLeastOnce = items.count { it.lastReviewAt != null },
            needsFirstReview = items.count { it.state == PracticeDecisionReviewState.NEEDS_REVIEW },
            newEvidenceAfterReview = items.count { it.state == PracticeDecisionReviewState.NEW_EVIDENCE },
            recurringThemes = themes.take(2),
            topSector = topSector?.key,
            topSectorCount = topSector?.value ?: 0
        )
    }
}

internal object PracticeDecisionCenterPresentation {
    fun items(
        state: PracticeState,
        companies: List<Stock>,
        news: List<NewsItem>,
        companyEvents: List<CompanyDataChangeEvent>
    ): List<PracticeDecisionReviewItem> =
        state.orders.asSequence()
            .filter { it.status == "FILLED" }
            .map { order ->
                val stock = stockFor(order, state, companies)
                val reviews = PracticeReviewPresentation.savedReviews(state, order)
                val latestReviewAt = reviews.maxOfOrNull { it.time }
                val evidence = PracticeReviewPresentation.evidence(order, stock, news, companyEvents)
                val evidenceSinceReview = evidence.filter { item ->
                    val observed = CompanyResearchPresentation.timestamp(item.time) ?: return@filter false
                    latestReviewAt == null || observed.isAfter(Instant.ofEpochMilli(latestReviewAt))
                }
                val reviewState = when {
                    latestReviewAt == null -> PracticeDecisionReviewState.NEEDS_REVIEW
                    evidenceSinceReview.isNotEmpty() -> PracticeDecisionReviewState.NEW_EVIDENCE
                    else -> PracticeDecisionReviewState.REVIEWED
                }
                PracticeDecisionReviewItem(
                    order = order,
                    state = reviewState,
                    lastReviewAt = latestReviewAt,
                    latestEvidence = evidenceSinceReview.maxByOrNull {
                        CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN
                    },
                    evidenceSinceReview = evidenceSinceReview.size
                )
            }
            .sortedWith(
                compareBy<PracticeDecisionReviewItem> {
                    when (it.state) {
                        PracticeDecisionReviewState.NEEDS_REVIEW -> 0
                        PracticeDecisionReviewState.NEW_EVIDENCE -> 1
                        PracticeDecisionReviewState.REVIEWED -> 2
                    }
                }.thenByDescending { item ->
                    item.latestEvidence?.let {
                        CompanyResearchPresentation.timestamp(it.time)?.toEpochMilli()
                    } ?: item.lastReviewAt ?: item.order.filledAt
                }
            )
            .toList()

    fun summary(items: List<PracticeDecisionReviewItem>) = PracticeDecisionReviewSummary(
        total = items.size,
        needsReview = items.count { it.state == PracticeDecisionReviewState.NEEDS_REVIEW },
        newEvidence = items.count { it.state == PracticeDecisionReviewState.NEW_EVIDENCE },
        reviewed = items.count { it.state == PracticeDecisionReviewState.REVIEWED }
    )

    private fun stockFor(
        order: PracticeOrder,
        state: PracticeState,
        companies: List<Stock>
    ): Stock {
        val normalized = WatchlistPresentation.symbol(order.symbol)
        return companies.firstOrNull { WatchlistPresentation.symbol(it.symbol) == normalized }
            ?: state.quotes.firstOrNull { WatchlistPresentation.symbol(it.symbol) == normalized }?.let {
                Stock(
                    symbol = it.symbol,
                    name = it.name.ifBlank { it.symbol },
                    price = it.price,
                    change = 0.0,
                    history = emptyList(),
                    sector = it.sector,
                    observedAt = it.at,
                    changeAvailable = false
                )
            }
            ?: Stock(
                symbol = normalized,
                name = normalized,
                price = Double.NaN,
                change = 0.0,
                history = emptyList(),
                changeAvailable = false,
                volumeAvailable = false
            )
    }
}

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
internal fun PracticeLearningInsightsCard(
    insights: PracticeLearningInsights,
    onOpenCenter: () -> Unit
) {
    ResearchPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PracticeIcon(Icons.Outlined.CheckCircle)
            Column(Modifier.weight(1f)) {
                ResearchTitle("Your learning so far")
                ResearchCaption(
                    if (insights.totalDecisions == 0) {
                        "Filled practice decisions will build your learning history here."
                    } else {
                        "${insights.totalDecisions} filled decision${if (insights.totalDecisions == 1) "" else "s"} in your learning history."
                    }
                )
            }
        }

        PracticeLine("Reasons recorded", "${insights.reasonsRecorded} / ${insights.totalDecisions}")
        PracticeLine("Reviewed at least once", "${insights.reviewedAtLeastOnce} / ${insights.totalDecisions}")
        PracticeLine("Still need first review", insights.needsFirstReview.toString())
        PracticeLine("New evidence after review", insights.newEvidenceAfterReview.toString())

        if (insights.recurringThemes.isNotEmpty() || insights.topSector != null) {
            HorizontalDivider()
            ResearchTitle("Patterns from your history")
            insights.recurringThemes.forEach { theme ->
                ResearchCaption(
                    "${theme.label} appears in ${theme.count} written reason" +
                        if (theme.count == 1) "." else "s."
                )
            }
            insights.topSector?.let { sector ->
                ResearchCaption(
                    "Most practised sector: $sector • ${insights.topSectorCount} decision" +
                        if (insights.topSectorCount == 1) "." else "s."
                )
            }
            ResearchCaption("These patterns describe your saved Practice history; they do not judge the quality of a decision.")
        }

        TextButton(onClick = onOpenCenter, modifier = Modifier.fillMaxWidth()) {
            Text(if (insights.totalDecisions == 0) "Open decision center →" else "Review your decisions →")
        }
    }
}

@Composable
internal fun PracticeDecisionReviewCenter(
    items: List<PracticeDecisionReviewItem>,
    onReview: (PracticeOrder) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ResearchTitle("Decision Review Center")
        ResearchCaption(
            "Revisit what you decided, what evidence was available then, and what changed later. " +
                "This is a learning history, not a score of whether a trade was good or bad."
        )

        if (items.isEmpty()) {
            ResearchPanel {
                PracticeIcon(Icons.Outlined.Schedule)
                ResearchTitle("No filled decisions yet")
                ResearchCaption("After a practice order fills, it will appear here for decision review.")
            }
            return
        }

        val sections = listOf(
            PracticeDecisionReviewState.NEEDS_REVIEW to "Needs review",
            PracticeDecisionReviewState.NEW_EVIDENCE to "New evidence",
            PracticeDecisionReviewState.REVIEWED to "Reviewed"
        )
        sections.forEach { (state, title) ->
            val rows = items.filter { it.state == state }
            if (rows.isNotEmpty()) {
                ResearchTitle("$title · ${rows.size}")
                rows.forEach { item ->
                    PracticeDecisionReviewCenterRow(item, onReview)
                }
            }
        }
    }
}

@Composable
private fun PracticeDecisionReviewCenterRow(
    item: PracticeDecisionReviewItem,
    onReview: (PracticeOrder) -> Unit
) {
    val order = item.order
    val (icon, label) = when (item.state) {
        PracticeDecisionReviewState.NEEDS_REVIEW -> Icons.Outlined.Schedule to "Needs review"
        PracticeDecisionReviewState.NEW_EVIDENCE -> Icons.Outlined.Notifications to "New evidence"
        PracticeDecisionReviewState.REVIEWED -> Icons.Outlined.CheckCircle to "Reviewed"
    }
    ResearchPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PracticeIcon(icon, item.state == PracticeDecisionReviewState.NEEDS_REVIEW)
            Column(Modifier.weight(1f)) {
                ResearchBody(
                    "${order.symbol} • ${order.side.lowercase().replaceFirstChar { it.uppercase() }} " +
                        "${order.shares} shares"
                )
                ResearchCaption(label)
            }
        }
        PracticeLine(
            "Simulated fill",
            order.price.takeIf { it.isFinite() && it > 0.0 }?.let(::practiceMoney) ?: "Unavailable"
        )
        ResearchCaption(
            order.filledAt.takeIf { it > 0L }?.let { "Filled ${practiceTime(it)}" }
                ?: "Fill time unavailable"
        )
        ResearchCaption(if (order.note.isBlank()) "Reason not recorded" else "Reason recorded with this decision")
        item.lastReviewAt?.let { ResearchCaption("Last reviewed ${practiceTime(it)}") }

        when (item.state) {
            PracticeDecisionReviewState.NEEDS_REVIEW -> {
                if (item.evidenceSinceReview > 0) {
                    ResearchCaption(
                        "${item.evidenceSinceReview} later evidence item" +
                            if (item.evidenceSinceReview == 1) " is available." else "s are available."
                    )
                }
            }
            PracticeDecisionReviewState.NEW_EVIDENCE -> {
                item.latestEvidence?.let { evidence ->
                    ResearchBody(evidence.title)
                    ResearchCaption(
                        evidence.source.ifBlank { "Evidence source unavailable" } +
                            " • " + CompanyResearchPresentation.date(evidence.time)
                    )
                    if (item.evidenceSinceReview > 1) {
                        ResearchCaption("${item.evidenceSinceReview} evidence items appeared since your last saved review.")
                    }
                }
            }
            PracticeDecisionReviewState.REVIEWED -> {
                ResearchCaption("No later loaded evidence is currently waiting after your latest saved review.")
            }
        }

        TextButton(onClick = { onReview(order) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (item.state == PracticeDecisionReviewState.REVIEWED) {
                    "Open decision review →"
                } else {
                    "Review decision →"
                }
            )
        }
    }
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
