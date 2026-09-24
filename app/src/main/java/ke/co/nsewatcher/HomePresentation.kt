package ke.co.nsewatcher

import ke.co.nsewatcher.data.CompanyDataChangeEvent
import ke.co.nsewatcher.data.CompanyDataChangeKind
import ke.co.nsewatcher.domain.AlertEvent
import java.time.Instant
import java.time.Duration
import java.time.ZoneId

internal data class HomeBriefItem(
    val id: String, val symbol: String, val title: String, val detail: String,
    val whyItMayMatter: String, val uncertainty: String, val source: String,
    val time: String, val action: String, val story: NewsItem? = null,
    val stock: Stock? = null, val alert: AlertEvent? = null,
    val practiceOrderId: String? = null,
    val relatedChangeId: String = ""
)

internal object HomePresentation {
    private val zone = ZoneId.of("Africa/Nairobi")
    fun greeting(name: String, now: Instant): String {
        val greeting = when (now.atZone(zone).hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
        return name.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }?.let { "$greeting, $it" } ?: greeting
    }

    fun recent(time: String, now: Instant): Boolean {
        val instant = CompanyResearchPresentation.timestamp(time) ?: return false
        return !instant.isAfter(now) && Duration.between(instant, now) <= Duration.ofDays(7)
    }

    fun companyNews(news: List<NewsItem>, watched: List<Stock>): List<NewsItem> =
        WatchlistPresentation.linkedNews(news, watched).sortedByDescending { CompanyResearchPresentation.timestamp(it.publishedAt) }

    fun changes(
        watched: List<Stock>,
        news: List<NewsItem>,
        events: List<AlertEvent>,
        now: Instant,
        companyDataEvents: List<CompanyDataChangeEvent> = emptyList(),
        practiceState: PracticeState? = null,
        companies: List<Stock> = watched
    ): List<HomeBriefItem> {
        val bySymbol = watched.associateBy { WatchlistPresentation.symbol(it.symbol) }
        val byName = watched.associateBy { it.name.trim().lowercase(java.util.Locale.US) }
        val practiceChanges = practiceFollowUps(
            state = practiceState,
            companies = companies,
            news = news,
            companyDataEvents = companyDataEvents,
            now = now
        )
        val practiceRelatedIds = practiceChanges.map { it.relatedChangeId }.filter(String::isNotBlank).toSet()
        val articles = companyNews(news, watched).filter { recent(it.publishedAt, now) }
        val articleIds = articles.map { it.id }.toSet()
        val articleChanges = articles.mapNotNull { item ->
            if ("news:${item.id}" in practiceRelatedIds) return@mapNotNull null
            val symbol = WatchlistPresentation.symbol(item.symbol).takeIf { it in bySymbol }
                ?: byName[item.companyName.trim().lowercase(java.util.Locale.US)]?.symbol?.let(WatchlistPresentation::symbol)
                ?: return@mapNotNull null
            HomeBriefItem(
                id = "news:${item.id}",
                symbol = symbol,
                title = item.title,
                detail = item.summary.takeIf(String::isNotBlank)
                    ?: "The feed returned a published update without a summary.",
                whyItMayMatter = newsWhyItMayMatter(item),
                uncertainty = "Published timing does not prove this update caused a price move or that any investment action is appropriate.",
                source = item.source.ifBlank { "Source unavailable" },
                time = item.publishedAt,
                action = "Read evidence",
                story = item
            )
        }
        val alertChanges = events.asSequence()
            .filter { WatchlistPresentation.symbol(it.symbol) in bySymbol && recent(it.recordedAt, now) }
            // A news alert and its article are one development, not two Home changes.
            .filter { it.articleId.isBlank() || it.articleId !in articleIds }
            .map { event ->
                val symbol = WatchlistPresentation.symbol(event.symbol)
                HomeBriefItem(
                    id = "alert:${event.id}",
                    symbol = symbol,
                    title = "${event.symbol} · ${event.title}",
                    detail = event.message,
                    whyItMayMatter = "A condition you configured was detected for $symbol, so it may deserve a fresh look at the company and the underlying observation.",
                    uncertainty = if (event.observedAt.isBlank())
                        "This recorded alert has no quote observation time. It should not be treated as a live price signal or explanation of cause."
                    else "The alert records that a condition was detected; it does not establish why the price moved or what will happen next.",
                    source = event.source.ifBlank { "Your alert · detected" },
                    time = event.recordedAt,
                    action = "Research $symbol",
                    alert = event,
                    stock = bySymbol[symbol]
                )
            }.toList()
        val companyChanges = companyDataEvents.asSequence()
            .filter { WatchlistPresentation.symbol(it.symbol) in bySymbol && recent(it.observedAt, now) }
            .filterNot { event ->
                event.kind == CompanyDataChangeKind.DIVIDEND &&
                    articles.any { article ->
                        val articleSymbol = WatchlistPresentation.symbol(article.symbol)
                        articleSymbol == WatchlistPresentation.symbol(event.symbol) &&
                            (
                                article.category.contains("dividend", ignoreCase = true) ||
                                    article.dividendAmount.isNotBlank() ||
                                    article.exDate.isNotBlank()
                            )
                    }
            }
            .filter { it.id !in practiceRelatedIds }
            .map { event ->
                val symbol = WatchlistPresentation.symbol(event.symbol)
                HomeBriefItem(
                    id = event.id,
                    symbol = symbol,
                    title = event.title,
                    detail = event.detail,
                    whyItMayMatter = companyDataWhyItMayMatter(event.kind, symbol),
                    uncertainty = "This records when NSE Watcher detected a change in provider data. It does not establish when the issuer changed or disclosed the information, why it changed, or what the share price will do.",
                    source = event.source.ifBlank { "Company intelligence source" },
                    time = event.observedAt,
                    action = "Research $symbol",
                    stock = bySymbol[symbol]
                )
            }.toList()
        return (practiceChanges + articleChanges + alertChanges + companyChanges).distinctBy { it.id }
            .sortedByDescending { CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN }
    }

    fun practiceFollowUps(
        state: PracticeState?,
        companies: List<Stock>,
        news: List<NewsItem>,
        companyDataEvents: List<CompanyDataChangeEvent>,
        now: Instant
    ): List<HomeBriefItem> {
        if (state?.enabled != true) return emptyList()
        val stocksBySymbol = companies.associateBy { WatchlistPresentation.symbol(it.symbol) }
        return state.orders.asSequence()
            .filter { it.status == "FILLED" && it.filledAt > 0L }
            .mapNotNull { order ->
                val symbol = WatchlistPresentation.symbol(order.symbol)
                if (symbol.isBlank()) return@mapNotNull null
                val stock = stocksBySymbol[symbol]
                    ?: state.quotes.firstOrNull { WatchlistPresentation.symbol(it.symbol) == symbol }?.let {
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
                    ?: Stock(symbol, symbol, Double.NaN, 0.0, emptyList(), changeAvailable = false, volumeAvailable = false)

                val fillTime = CompanyResearchPresentation.timestamp(order.quoteAt)
                    ?: Instant.ofEpochMilli(order.filledAt)
                if (fillTime.isAfter(now) || Duration.between(fillTime, now) > Duration.ofDays(7)) return@mapNotNull null

                val lastSavedReviewAt = PracticeReviewPresentation.savedReviews(state, order)
                    .maxOfOrNull { it.time }
                    ?.let(Instant::ofEpochMilli)

                val laterEvidence = PracticeReviewPresentation.evidence(
                    order = order,
                    stock = stock,
                    news = news,
                    companyEvents = companyDataEvents
                ).filter {
                    val evidenceAt = CompanyResearchPresentation.timestamp(it.time)
                    evidenceAt != null && evidenceAt.isAfter(fillTime) && !evidenceAt.isAfter(now)
                }

                val latestEvidence = laterEvidence.maxByOrNull {
                    CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN
                }
                val latestEvidenceAt = latestEvidence?.let {
                    CompanyResearchPresentation.timestamp(it.time)
                }

                if (latestEvidence != null && latestEvidenceAt != null &&
                    (lastSavedReviewAt == null || latestEvidenceAt.isAfter(lastSavedReviewAt))
                ) {
                    HomeBriefItem(
                        id = "practice-evidence:${order.id}:${latestEvidence.id}",
                        symbol = symbol,
                        title = "New evidence since your $symbol practice decision",
                        detail = latestEvidence.title,
                        whyItMayMatter = "You recorded a practice decision for $symbol before this later evidence appeared. Reviewing both can help you compare your original reasoning with what became known afterward.",
                        uncertainty = "Later evidence does not prove that it caused the price move or that your original decision was right or wrong.",
                        source = latestEvidence.source.ifBlank { "Evidence source unavailable" },
                        time = latestEvidence.time,
                        action = "Review decision",
                        stock = stock,
                        practiceOrderId = order.id,
                        relatedChangeId = latestEvidence.id
                    )
                } else if (lastSavedReviewAt == null || fillTime.isAfter(lastSavedReviewAt)) {
                    HomeBriefItem(
                        id = "practice-fill:${order.id}",
                        symbol = symbol,
                        title = "$symbol practice order filled",
                        detail = "${order.side} ${order.shares} shares at ${practiceMoney(order.price)} · ready to review",
                        whyItMayMatter = "The simulated order has an observed fill, so you can now compare the saved decision-time context with what happened afterward.",
                        uncertainty = "This is a simulated fill using eligible observed data. It does not reproduce real order-book queue position, liquidity or broker execution.",
                        source = "Practice Portfolio · simulated fill",
                        time = order.quoteAt.ifBlank { Instant.ofEpochMilli(order.filledAt).toString() },
                        action = "Review decision",
                        stock = stock,
                        practiceOrderId = order.id
                    )
                } else null
            }
            .distinctBy { it.id }
            .sortedByDescending { CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN }
            .toList()
    }

    private fun companyDataWhyItMayMatter(kind: CompanyDataChangeKind, symbol: String): String = when (kind) {
        CompanyDataChangeKind.REPORTING_PERIOD ->
            "A newly observed reporting period can change the basis for understanding $symbol's revenue, profit and earnings. Review the sourced figures before comparing periods."
        CompanyDataChangeKind.REPORTED_FIGURES ->
            "A reported figure for the same period differs from the previous provider observation. This may reflect a source correction, revision or update worth checking."
        CompanyDataChangeKind.DIVIDEND ->
            "Dividend records now differ from the previous provider observation. Review the amount, ex-date, payment date and source before relying on it."
    }

    private fun newsWhyItMayMatter(item: NewsItem): String {
        val reason = item.intelligenceRelevanceReason.trim()
        if (reason.isNotBlank()) return "Feed relevance note: $reason"
        val category = item.category.trim().ifBlank { "company" }
        return "This is a new $category update for a company you follow. Read the source to decide whether it changes your understanding of the company."
    }

    fun brief(
        watched: List<Stock>,
        news: List<NewsItem>,
        events: List<AlertEvent>,
        now: Instant,
        reviewedIds: Set<String> = emptySet(),
        companyDataEvents: List<CompanyDataChangeEvent> = emptyList()
    ): List<HomeBriefItem> = changes(watched, news, events, now, companyDataEvents)
        .filter { it.id !in reviewedIds }
        .take(3)

    fun visibleBrief(changes: List<HomeBriefItem>): List<HomeBriefItem> = changes.take(3)

    fun dailyBriefTitle(
        items: List<HomeBriefItem>,
        loading: Boolean,
        hasError: Boolean,
        hasWatchlist: Boolean
    ): String = when {
        loading -> "Checking what changed…"
        items.size == 1 -> "1 thing needs your attention"
        items.size > 1 -> "${items.size} things need your attention"
        !hasWatchlist -> "Build your daily brief"
        hasError -> "Some changes are unavailable"
        else -> "You're caught up"
    }

    data class AttentionDigest(
        val summary: String,
        val breakdown: List<String>
    )

    fun attentionDigest(changes: List<HomeBriefItem>): AttentionDigest? {
        if (changes.isEmpty()) return null
        val companies = changes.map { WatchlistPresentation.symbol(it.symbol) }
            .filter(String::isNotBlank)
            .distinct()
            .size
        val practiceCount = changes.count { it.practiceOrderId != null }
        val newsCount = changes.count { it.practiceOrderId == null && it.story != null }
        val alertCount = changes.count { it.practiceOrderId == null && it.alert != null }
        val companyDataCount = changes.size - practiceCount - newsCount - alertCount

        fun label(count: Int, singular: String, plural: String = singular + "s") =
            "${count} ${if (count == 1) singular else plural}"

        return AttentionDigest(
            summary = "${changes.size} new ${if (changes.size == 1) "development" else "developments"} across " +
                "${companies} ${if (companies == 1) "company" else "companies"}",
            breakdown = buildList {
                if (practiceCount > 0) add(label(practiceCount, "practice follow-up"))
                if (newsCount > 0) add(label(newsCount, "news update"))
                if (alertCount > 0) add(label(alertCount, "alert"))
                if (companyDataCount > 0) add(label(companyDataCount, "company-data update"))
            }
        )
    }

    fun marketSummary(breadth: HomeMarketBreadth): String = when {
        breadth.advancing + breadth.declining + breadth.unchanged == 0 -> "Daily market movement is unavailable."
        breadth.advancing > breadth.declining -> "More shares are rising than falling."
        breadth.declining > breadth.advancing -> "More shares are falling than rising."
        breadth.advancing == 0 -> "Available daily changes are unchanged."
        else -> "Rising and falling shares are balanced."
    }

    fun freshness(stocks: List<Stock>, now: Instant): String {
        val valid = stocks.filter { it.price.isFinite() && it.price > 0 }
        if (valid.isEmpty()) return "Quotes unavailable"
        val newest = valid.mapNotNull { CompanyResearchPresentation.timestamp(it.observedAt) }.maxOrNull()
            ?: return "Observation time unavailable"
        if (newest.isAfter(now.plusSeconds(60))) return "Provider timestamp is ahead of device time"
        if (newest.atZone(zone).toLocalDate() < now.atZone(zone).toLocalDate()) return "Previous-session observations"
        if (Duration.between(newest, now).toMinutes() > 30) return "Latest observation is over 30 min old"
        val delays = valid.mapNotNull { it.delayMinutes }.filter { it >= 0 }.distinct()
        return if (delays.size == 1 && delays.single() > 0) "Quotes delayed ${delays.single()} min" else "Delayed provider quotes"
    }
}
