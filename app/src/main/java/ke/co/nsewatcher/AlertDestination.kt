package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertEvent

/** Carries enough published evidence to open the reader even if the feed drops an item. */
data class AlertDestination(
    val eventId: String, val symbol: String, val articleId: String = "", val title: String = "",
    val publishedAt: String = "", val source: String = "", val sourceUrl: String = ""
) {
    fun article(): NewsItem? = articleId.takeIf { it.isNotBlank() }?.let {
        NewsItem(id = it, title = title.ifBlank { "$symbol company update" }, summary = "", body = "",
            source = source, publishedAt = publishedAt, category = "Company News", symbol = symbol,
            companyName = "", imageUrl = "", url = sourceUrl, dividendAmount = "", exDate = "", paymentDate = "")
    }
    fun company(companies: List<Stock>): Stock = companies.firstOrNull { it.symbol.equals(symbol, true) }
        ?: Stock(symbol, symbol, Double.NaN, 0.0, emptyList(), changeAvailable = false, volumeAvailable = false)

    companion object {
        fun from(event: AlertEvent) = AlertDestination(event.id, event.symbol, event.articleId,
            event.articleTitle, event.observedAt, event.source, event.sourceUrl)
    }
}
