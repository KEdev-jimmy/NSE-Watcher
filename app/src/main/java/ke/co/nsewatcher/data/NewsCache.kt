package ke.co.nsewatcher.data

import ke.co.nsewatcher.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object NewsCache {
    private const val FEED_URL = "https://nse-watcher.vercel.app/api/news?action=feed"
    private const val DETAIL_URL = "https://nse-watcher.vercel.app/api/news?action=detail&id="
    private const val COMPANY_URL = "https://nse-watcher.vercel.app/api/news?action=company&symbol="

    data class FeedResult(
        val items: List<NewsItem>,
        val error: String? = null
    )

    suspend fun loadFeed(): List<NewsItem> = loadFeedResult().items

    suspend fun loadFeedResult(): FeedResult = withContext(Dispatchers.IO) {
        val response = request(FEED_URL)
        if (response == null) {
            return@withContext FeedResult(emptyList(), "Unable to reach the news service.")
        }
        if (response.optString("error").isNotBlank()) {
            return@withContext FeedResult(
                emptyList(),
                response.optString("error", "News service unavailable")
            )
        }

        val items = response.optJSONArray("items")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    parseItem(array.optJSONObject(i))?.let(::add)
                }
            }
        } ?: emptyList()

        FeedResult(items)
    }

    suspend fun loadCompanyNews(symbol: String): FeedResult = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(symbol.trim(), Charsets.UTF_8.name())
        val response = request(COMPANY_URL + encoded)
        if (response == null) {
            return@withContext FeedResult(emptyList(), "Unable to reach the news service.")
        }
        if (response.optString("error").isNotBlank()) {
            return@withContext FeedResult(
                emptyList(),
                response.optString("error", "Company news unavailable")
            )
        }
        val items = response.optJSONArray("items")?.let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    parseItem(array.optJSONObject(i))?.let(::add)
                }
            }
        } ?: emptyList()
        FeedResult(items)
    }

    suspend fun loadDetail(id: String): NewsItem? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(id, Charsets.UTF_8.name())
        parseItem(request(DETAIL_URL + encoded)?.optJSONObject("item"))
    }

    private fun parseItem(item: JSONObject?): NewsItem? {
        if (item == null) return null
        val id = item.optString("id").trim()
        val title = item.optString("title").trim()
        if (id.isBlank() || title.isBlank()) return null
        return NewsItem(
            id = id,
            title = title,
            summary = item.optString("summary").trim(),
            body = item.optString("body").trim(),
            source = item.optString("source", "MyStocks Africa").trim(),
            publishedAt = item.optString("publishedAt").trim(),
            category = item.optString("category", "Market").trim(),
            symbol = item.optString("symbol").trim(),
            companyName = item.optString("companyName").trim(),
            imageUrl = item.optString("imageUrl").trim(),
            url = item.optString("url").trim(),
            dividendAmount = item.optString("dividendAmount").trim(),
            exDate = item.optString("exDate").trim(),
            paymentDate = item.optString("paymentDate").trim()
        )
    }

    private fun request(url: String): JSONObject? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@runCatching null
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
