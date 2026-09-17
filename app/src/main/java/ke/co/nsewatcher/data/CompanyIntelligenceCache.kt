package ke.co.nsewatcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Company-intelligence bridge. Credentials stay on Vercel; Android only sees
 * sourced company/fundamental/dividend data returned by the backend.
 */
object CompanyIntelligenceCache {
    private const val BASE_URL = "https://nse-watcher.vercel.app/api/company?action=intelligence&symbol="

    data class Profile(
        val description: String = "",
        val sector: String = "",
        val headquarters: String = "",
        val website: String = "",
        val marketCap: String = "",
        val revenue: String = "",
        val profit: String = "",
        val eps: String = "",
        val roe: String = "",
        val debtToEquity: String = "",
        val margin: String = "",
        val revenueGrowth: String = "",
        val profitGrowth: String = "",
        val pe: String = "",
        val pb: String = "",
        val dividendYield: String = ""
    )

    data class FinancialPoint(
        val period: String = "",
        val revenue: String = "",
        val profit: String = "",
        val eps: String = "",
        val roe: String = "",
        val margin: String = "",
        val debtToEquity: String = "",
        val pe: String = "",
        val pb: String = "",
        val source: String = "MyStocks Africa"
    )

    data class Evidence(
        val claim: String = "",
        val value: String = "",
        val source: String = "MyStocks Africa",
        val endpoint: String = "",
        val symbol: String = "",
        val fetchedAt: String = ""
    )

    data class Dividend(
        val amount: String,
        val exDate: String,
        val paymentDate: String,
        val declaredDate: String,
        val type: String,
        val status: String
    )

    data class Result(
        val profile: Profile = Profile(),
        val dividends: List<Dividend> = emptyList(),
        val financialHistory: List<FinancialPoint> = emptyList(),
        val evidence: List<Evidence> = emptyList(),
        val source: String = "MyStocks Africa",
        val fetchedAt: String = "",
        val partial: Boolean = false,
        val financialHistoryAvailable: Boolean = false,
        val error: String? = null
    )

    suspend fun load(symbol: String): Result = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(symbol.trim(), Charsets.UTF_8.name())
        val root = request(BASE_URL + encoded)
            ?: return@withContext Result(error = "Unable to reach the company intelligence service.")

        val error = root.optString("error").trim()
        if (error.isNotBlank()) return@withContext Result(error = error)

        val profileObject = root.optJSONObject("profile")
            ?: root.optJSONObject("data")?.optJSONObject("profile")
            ?: root.optJSONObject("data")
            ?: JSONObject()
        val dividendsArray = root.optJSONArray("dividends")
            ?: root.optJSONObject("data")?.optJSONArray("dividends")
            ?: JSONArray()
        val historyArray = root.optJSONArray("financialHistory")
            ?: root.optJSONObject("data")?.optJSONArray("financialHistory")
            ?: JSONArray()
        val evidenceArray = root.optJSONArray("evidence")
            ?: root.optJSONObject("data")?.optJSONArray("evidence")
            ?: JSONArray()
        val quality = root.optJSONObject("dataQuality")

        Result(
            profile = parseProfile(profileObject),
            dividends = parseDividends(dividendsArray),
            financialHistory = parseFinancialHistory(historyArray),
            evidence = parseEvidence(evidenceArray),
            source = root.optString("source", "MyStocks Africa"),
            fetchedAt = root.optString("fetchedAt"),
            partial = root.optBoolean("partial", false),
            financialHistoryAvailable = quality?.optBoolean("financialHistoryAvailable", historyArray.length() > 0)
                ?: historyArray.length() > 0,
            error = null
        )
    }

    private fun parseProfile(root: JSONObject): Profile = Profile(
        description = findText(root, "description", "businessDescription", "companyDescription"),
        sector = findText(root, "sector", "industry"),
        headquarters = findText(root, "headquarters", "hq", "location"),
        website = findText(root, "website", "websiteUrl", "url"),
        marketCap = findText(root, "marketCap", "marketCapitalisation", "marketCapitalization"),
        revenue = findText(root, "revenue", "totalRevenue"),
        profit = findText(root, "profit", "netIncome", "netProfit", "profitAfterTax"),
        eps = findText(root, "eps", "earningsPerShare"),
        roe = findText(root, "roe", "returnOnEquity"),
        debtToEquity = findText(root, "debtToEquity", "debtEquity", "debtToEquityRatio"),
        margin = findText(root, "netMargin", "profitMargin", "margin"),
        revenueGrowth = findText(root, "revenueGrowth", "revenueGrowthRate"),
        profitGrowth = findText(root, "profitGrowth", "netIncomeGrowth", "profitGrowthRate"),
        pe = findText(root, "pe", "peRatio", "priceEarnings", "priceToEarnings"),
        pb = findText(root, "pb", "pbRatio", "priceBook", "priceToBook"),
        dividendYield = findText(root, "dividendYield", "yield")
    )

    private fun parseFinancialHistory(array: JSONArray): List<FinancialPoint> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                FinancialPoint(
                    period = findText(item, "period"),
                    revenue = findText(item, "revenue"),
                    profit = findText(item, "profit"),
                    eps = findText(item, "eps"),
                    roe = findText(item, "roe"),
                    margin = findText(item, "margin"),
                    debtToEquity = findText(item, "debtToEquity"),
                    pe = findText(item, "pe"),
                    pb = findText(item, "pb"),
                    source = findText(item, "source").ifBlank { "MyStocks Africa" }
                )
            )
        }
    }

    private fun parseEvidence(array: JSONArray): List<Evidence> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Evidence(
                    claim = findText(item, "claim"),
                    value = findText(item, "value"),
                    source = findText(item, "source").ifBlank { "MyStocks Africa" },
                    endpoint = findText(item, "endpoint"),
                    symbol = findText(item, "symbol"),
                    fetchedAt = findText(item, "fetchedAt")
                )
            )
        }
    }

    private fun parseDividends(array: JSONArray): List<Dividend> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                Dividend(
                    amount = findText(item, "amount", "dividend", "dividendAmount", "value"),
                    exDate = findText(item, "exDate", "exDividendDate", "bookClosureDate"),
                    paymentDate = findText(item, "paymentDate", "payDate"),
                    declaredDate = findText(item, "declaredDate", "declarationDate"),
                    type = findText(item, "type", "dividendType"),
                    status = findText(item, "status")
                )
            )
        }
    }

    private fun findText(root: JSONObject, vararg keys: String): String {
        val wanted = keys.map { normalize(it) }.toSet()
        fun walk(value: Any?): String? {
            when (value) {
                is JSONObject -> {
                    val names = value.keys()
                    while (names.hasNext()) {
                        val name = names.next()
                        val child = value.opt(name)
                        if (normalize(name) in wanted) {
                            val text = scalarText(child)
                            if (!text.isNullOrBlank()) return text
                        }
                        walk(child)?.let { return it }
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))?.let { return it }
            }
            return null
        }
        return walk(root).orEmpty()
    }

    private fun scalarText(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is Number -> value.toString()
        is Boolean -> value.toString()
        is String -> value.trim().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun normalize(value: String): String = value.filter(Char::isLetterOrDigit).lowercase()

    private fun request(url: String): JSONObject? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: return@runCatching null
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
