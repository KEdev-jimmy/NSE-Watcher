package ke.co.nsewatcher

import java.time.Instant

/**
 * Read-only projection of app-owned shared data for a single company.
 *
 * Company Intelligence should derive its news from the same feed that powers
 * Home and News instead of maintaining a second independently fetched list.
 */
internal object CompanySharedData {
    fun companyNews(stock: Stock, feed: List<NewsItem>): List<NewsItem> =
        WatchlistPresentation.linkedNews(feed, listOf(stock))
            .sortedByDescending {
                CompanyResearchPresentation.timestamp(it.publishedAt) ?: Instant.MIN
            }
}
