package ke.co.nsewatcher

import androidx.compose.runtime.Composable

@Composable
fun NewsDashboard(
    newsFeed: List<NewsItem>,
    catalog: List<Stock>,
    quotes: List<Stock>,
    offlineSnapshot: Boolean = false,
    onNewsLoaded: (List<NewsItem>) -> Unit,
    openAlerts: () -> Unit,
    open: (NewsItem) -> Unit
) {
    PremiumNewsDashboard(
        newsFeed = newsFeed,
        catalog = catalog,
        quotes = quotes,
        offlineSnapshot = offlineSnapshot,
        onNewsLoaded = onNewsLoaded,
        openAlerts = openAlerts,
        open = open
    )
}
