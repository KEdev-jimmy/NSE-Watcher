package ke.co.nsewatcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class PersistedStartupSnapshot(
    val stocks: List<Stock> = emptyList(),
    val stocksSavedAtMs: Long = 0L,
    val news: List<NewsItem> = emptyList(),
    val newsSavedAtMs: Long = 0L,
    val companies: List<Stock> = emptyList(),
    val companiesSavedAtMs: Long = 0L
)

internal data class StartupOfflineSnapshot(
    val stocks: List<Stock> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val companies: List<Stock> = emptyList(),
    val restoredSources: Set<String> = emptySet()
) {
    val hasAny: Boolean
        get() = stocks.isNotEmpty() || news.isNotEmpty() || companies.isNotEmpty()
}

internal object StartupSnapshotPolicy {
    internal const val STOCK_MAX_AGE_MS = 72L * 60L * 60L * 1000L
    internal const val NEWS_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    internal const val COMPANIES_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    fun fallback(
        persisted: PersistedStartupSnapshot,
        nowMs: Long
    ): StartupOfflineSnapshot {
        fun fresh(savedAt: Long, maxAge: Long): Boolean =
            savedAt > 0L && nowMs >= savedAt && nowMs - savedAt <= maxAge

        val stocks = if (fresh(persisted.stocksSavedAtMs, STOCK_MAX_AGE_MS)) {
            persisted.stocks.map { stock ->
                stock.copy(
                    freshnessMode = "STALE",
                    dataOrigin = "offline_snapshot"
                )
            }
        } else {
            emptyList()
        }

        val news = if (fresh(persisted.newsSavedAtMs, NEWS_MAX_AGE_MS)) {
            persisted.news.map { item -> item.copy(freshnessMode = "STALE") }
        } else {
            emptyList()
        }

        val companies = if (fresh(persisted.companiesSavedAtMs, COMPANIES_MAX_AGE_MS)) {
            persisted.companies.map { company ->
                company.copy(
                    price = Double.NaN,
                    change = Double.NaN,
                    history = emptyList(),
                    changeAvailable = false,
                    volumeAvailable = false,
                    freshnessMode = "UNKNOWN",
                    dataOrigin = "offline_snapshot_catalog"
                )
            }
        } else {
            emptyList()
        }

        return StartupOfflineSnapshot(
            stocks = stocks,
            news = news,
            companies = companies,
            restoredSources = buildSet {
                if (stocks.isNotEmpty()) add("stocks")
                if (news.isNotEmpty()) add("news")
                if (companies.isNotEmpty()) add("companies")
            }
        )
    }
}

internal class StartupSnapshotStore(context: Context) {
    private val file = File(context.filesDir, "startup_snapshot_v1.json")

    suspend fun loadFallback(
        nowMs: Long = System.currentTimeMillis()
    ): StartupOfflineSnapshot = withContext(Dispatchers.IO) {
        val persisted = readPersisted() ?: return@withContext StartupOfflineSnapshot()
        StartupSnapshotPolicy.fallback(persisted, nowMs)
    }

    suspend fun saveSuccessfulSources(
        snapshot: StartupDataSnapshot,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val current = readPersisted() ?: PersistedStartupSnapshot()
        val unavailable = snapshot.failedSources + snapshot.timedOutSources

        val next = PersistedStartupSnapshot(
            stocks = if ("stocks" !in unavailable && snapshot.stocks.isNotEmpty()) {
                snapshot.stocks.take(120)
            } else {
                current.stocks
            },
            stocksSavedAtMs = if ("stocks" !in unavailable && snapshot.stocks.isNotEmpty()) {
                nowMs
            } else {
                current.stocksSavedAtMs
            },
            news = if ("news" !in unavailable) {
                snapshot.news.take(120)
            } else {
                current.news
            },
            newsSavedAtMs = if ("news" !in unavailable) {
                nowMs
            } else {
                current.newsSavedAtMs
            },
            companies = if ("companies" !in unavailable && snapshot.companies.isNotEmpty()) {
                snapshot.companies.take(160)
            } else {
                current.companies
            },
            companiesSavedAtMs = if ("companies" !in unavailable && snapshot.companies.isNotEmpty()) {
                nowMs
            } else {
                current.companiesSavedAtMs
            }
        )

        if (next.stocks.isEmpty() && next.news.isEmpty() && next.companies.isEmpty()) return@withContext
        writePersisted(next)
    }

    private fun readPersisted(): PersistedStartupSnapshot? = runCatching {
        if (!file.exists()) return@runCatching null
        val root = JSONObject(file.readText())
        PersistedStartupSnapshot(
            stocks = stockList(root.optJSONArray("stocks")),
            stocksSavedAtMs = root.optLong("stocksSavedAtMs", 0L),
            news = newsList(root.optJSONArray("news")),
            newsSavedAtMs = root.optLong("newsSavedAtMs", 0L),
            companies = stockList(root.optJSONArray("companies")),
            companiesSavedAtMs = root.optLong("companiesSavedAtMs", 0L)
        )
    }.getOrNull()

    private fun writePersisted(snapshot: PersistedStartupSnapshot) {
        val root = JSONObject()
            .put("stocksSavedAtMs", snapshot.stocksSavedAtMs)
            .put("newsSavedAtMs", snapshot.newsSavedAtMs)
            .put("companiesSavedAtMs", snapshot.companiesSavedAtMs)
            .put("stocks", JSONArray().also { array ->
                snapshot.stocks.forEach { array.put(stockJson(it)) }
            })
            .put("news", JSONArray().also { array ->
                snapshot.news.forEach { array.put(newsJson(it)) }
            })
            .put("companies", JSONArray().also { array ->
                snapshot.companies.forEach { array.put(stockJson(it)) }
            })

        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(root.toString())
        if (!temp.renameTo(file)) {
            file.writeText(root.toString())
            temp.delete()
        }
    }

    private fun stockJson(stock: Stock): JSONObject = JSONObject()
        .put("symbol", stock.symbol)
        .put("name", stock.name)
        .putFinite("price", stock.price)
        .putFinite("change", stock.change)
        .put("logoUrl", stock.logoUrl ?: JSONObject.NULL)
        .put("sector", stock.sector)
        .put("volume", stock.volume)
        .put("changeAvailable", stock.changeAvailable)
        .put("volumeAvailable", stock.volumeAvailable)
        .put("source", stock.source)
        .put("observedAt", stock.observedAt)
        .put("freshnessMode", stock.freshnessMode)
        .put("dataOrigin", stock.dataOrigin)
        .put("averageVolume", stock.averageVolume)
        .put("averageVolumeAvailable", stock.averageVolumeAvailable)
        .put("previousClose", stock.previousClose ?: JSONObject.NULL)
        .put("delayMinutes", stock.delayMinutes ?: JSONObject.NULL)

    private fun newsJson(item: NewsItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("title", item.title)
        .put("summary", item.summary)
        .put("body", item.body)
        .put("source", item.source)
        .put("publishedAt", item.publishedAt)
        .put("category", item.category)
        .put("symbol", item.symbol)
        .put("companyName", item.companyName)
        .put("imageUrl", item.imageUrl)
        .put("url", item.url)
        .put("dividendAmount", item.dividendAmount)
        .put("exDate", item.exDate)
        .put("paymentDate", item.paymentDate)
        .put("intelligenceRelevance", item.intelligenceRelevance)
        .put("intelligenceRelevanceReason", item.intelligenceRelevanceReason)
        .put("freshnessMode", item.freshnessMode)

    private fun stockList(array: JSONArray?): List<Stock> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val symbol = item.optString("symbol").trim()
                val name = item.optString("name").trim()
                if (symbol.isBlank() || name.isBlank()) continue
                add(
                    Stock(
                        symbol = symbol,
                        name = name,
                        price = item.optFinite("price"),
                        change = item.optFinite("change"),
                        history = emptyList(),
                        logoUrl = item.optString("logoUrl")
                            .takeIf { it.isNotBlank() && !it.equals("null", true) },
                        sector = item.optString("sector", "Other").ifBlank { "Other" },
                        volume = item.optLong("volume", 0L),
                        changeAvailable = item.optBoolean("changeAvailable", false),
                        volumeAvailable = item.optBoolean("volumeAvailable", false),
                        source = item.optString("source"),
                        observedAt = item.optString("observedAt"),
                        freshnessMode = item.optString("freshnessMode", "UNKNOWN"),
                        dataOrigin = item.optString("dataOrigin", "unknown"),
                        averageVolume = item.optLong("averageVolume", 0L),
                        averageVolumeAvailable = item.optBoolean("averageVolumeAvailable", false),
                        previousClose = item.optFiniteOrNull("previousClose"),
                        delayMinutes = item.optIntOrNull("delayMinutes")
                    )
                )
            }
        }
    }

    private fun newsList(array: JSONArray?): List<NewsItem> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isBlank() || title.isBlank()) continue
                add(
                    NewsItem(
                        id = id,
                        title = title,
                        summary = item.optString("summary"),
                        body = item.optString("body"),
                        source = item.optString("source"),
                        publishedAt = item.optString("publishedAt"),
                        category = item.optString("category"),
                        symbol = item.optString("symbol"),
                        companyName = item.optString("companyName"),
                        imageUrl = item.optString("imageUrl"),
                        url = item.optString("url"),
                        dividendAmount = item.optString("dividendAmount"),
                        exDate = item.optString("exDate"),
                        paymentDate = item.optString("paymentDate"),
                        intelligenceRelevance = item.optString("intelligenceRelevance", "unknown"),
                        intelligenceRelevanceReason = item.optString("intelligenceRelevanceReason"),
                        freshnessMode = item.optString("freshnessMode", "UNKNOWN")
                    )
                )
            }
        }
    }

    private fun JSONObject.putFinite(key: String, value: Double): JSONObject =
        put(key, if (value.isFinite()) value else JSONObject.NULL)

    private fun JSONObject.optFinite(key: String): Double =
        if (!has(key) || isNull(key)) Double.NaN else optDouble(key, Double.NaN)

    private fun JSONObject.optFiniteOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}
