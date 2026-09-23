package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertEvent
import java.time.Instant
import java.time.Duration
import java.time.ZoneId

internal data class HomeBriefItem(
    val id: String, val symbol: String, val title: String, val detail: String, val source: String,
    val time: String, val action: String, val story: NewsItem? = null,
    val stock: Stock? = null, val alert: AlertEvent? = null
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

    fun changes(watched: List<Stock>, news: List<NewsItem>, events: List<AlertEvent>, now: Instant): List<HomeBriefItem> {
        val bySymbol = watched.associateBy { WatchlistPresentation.symbol(it.symbol) }
        val byName = watched.associateBy { it.name.trim().lowercase(java.util.Locale.US) }
        val articles = companyNews(news, watched).filter { recent(it.publishedAt, now) }
        val articleIds = articles.map { it.id }.toSet()
        val articleChanges = articles.mapNotNull { item ->
            val symbol = WatchlistPresentation.symbol(item.symbol).takeIf { it in bySymbol }
                ?: byName[item.companyName.trim().lowercase(java.util.Locale.US)]?.symbol?.let(WatchlistPresentation::symbol)
                ?: return@mapNotNull null
            HomeBriefItem(
                id = "news:${item.id}",
                symbol = symbol,
                title = item.title,
                detail = item.summary.takeIf(String::isNotBlank)
                    ?: "Read the published update and its source before drawing a conclusion.",
                source = item.source.ifBlank { "Source unavailable" },
                time = item.publishedAt,
                action = "Read the update",
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
                    source = "Your alert · detected",
                    time = event.recordedAt,
                    action = "Review this alert",
                    alert = event,
                    stock = bySymbol[symbol]
                )
            }.toList()
        return (articleChanges + alertChanges).distinctBy { it.id }
            .sortedByDescending { CompanyResearchPresentation.timestamp(it.time) ?: Instant.MIN }
    }

    fun brief(
        watched: List<Stock>,
        news: List<NewsItem>,
        events: List<AlertEvent>,
        now: Instant,
        reviewedIds: Set<String> = emptySet()
    ): List<HomeBriefItem> = changes(watched, news, events, now)
        .filter { it.id !in reviewedIds }
        .take(3)

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
