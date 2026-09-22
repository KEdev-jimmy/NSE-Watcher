package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertEvent
import java.time.Instant
import java.time.Duration
import java.time.ZoneId

internal data class HomeBriefItem(
    val id: String, val title: String, val detail: String, val source: String,
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

    fun brief(watched: List<Stock>, news: List<NewsItem>, events: List<AlertEvent>, now: Instant): List<HomeBriefItem> {
        val symbols = watched.map { WatchlistPresentation.symbol(it.symbol) }.toSet()
        val article = companyNews(news, watched).firstOrNull { recent(it.publishedAt, now) }
        val event = events.filter { WatchlistPresentation.symbol(it.symbol) in symbols && recent(it.recordedAt, now) }
            .maxByOrNull { CompanyResearchPresentation.timestamp(it.recordedAt)!! }
        return buildList {
            article?.let { add(HomeBriefItem("news:${it.id}", it.title,
                it.summary.takeIf(String::isNotBlank) ?: "Read the published update and its source before drawing a conclusion.",
                it.source.ifBlank { "Source unavailable" }, it.publishedAt, "Read the update", story = it)) }
            event?.let { add(HomeBriefItem("alert:${it.id}", "${it.symbol} · ${it.title}", it.message,
                "Your alert · detected", it.recordedAt, "Review this alert", alert = it,
                stock = watched.firstOrNull { stock -> stock.symbol.equals(it.symbol, true) })) }
        }.take(2)
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
