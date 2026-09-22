package ke.co.nsewatcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ke.co.nsewatcher.NewsItem
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.savedNewsDataStore by preferencesDataStore("nse_watcher_saved_news")
private val storiesKey = stringPreferencesKey("articles")
private val readerSizeKey = intPreferencesKey("reader_size")

class SavedNewsStore(private val context: Context) {
    val articles = context.savedNewsDataStore.data.map { decode(it[storiesKey].orEmpty()) }
    val readerSize = context.savedNewsDataStore.data.map { (it[readerSizeKey] ?: 16).coerceIn(16, 22) }
    suspend fun setReaderSize(size: Int) { context.savedNewsDataStore.edit { it[readerSizeKey] = size.coerceIn(16, 22) } }
    suspend fun save(item: NewsItem) {
        context.savedNewsDataStore.edit { prefs -> prefs[storiesKey] = encode(listOf(item) + decode(prefs[storiesKey].orEmpty()).filterNot { it.id == item.id }) }
    }
    suspend fun updateIfSaved(item: NewsItem) {
        context.savedNewsDataStore.edit { prefs ->
            val current = decode(prefs[storiesKey].orEmpty())
            if (current.any { it.id == item.id }) prefs[storiesKey] = encode(current.map { if (it.id == item.id) item else it })
        }
    }
    suspend fun remove(id: String) { context.savedNewsDataStore.edit { it[storiesKey] = encode(decode(it[storiesKey].orEmpty()).filterNot { item -> item.id == id }) } }
    private fun decode(raw: String): List<NewsItem> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList { for (i in 0 until array.length()) NewsCache.parseItem(array.optJSONObject(i))?.let { add(it) } }.distinctBy { it.id }
    }
    private fun encode(items: List<NewsItem>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().apply {
            put("id", item.id); put("title", item.title); put("summary", item.summary); put("body", item.body)
            put("source", item.source); put("publishedAt", item.publishedAt); put("category", item.category)
            put("symbol", item.symbol); put("companyName", item.companyName); put("imageUrl", item.imageUrl); put("url", item.url)
            put("dividendAmount", item.dividendAmount); put("exDate", item.exDate); put("paymentDate", item.paymentDate)
            put("intelligenceRelevance", item.intelligenceRelevance); put("intelligenceRelevanceReason", item.intelligenceRelevanceReason); put("freshnessMode", item.freshnessMode)
        }) }
    }.toString()
}
