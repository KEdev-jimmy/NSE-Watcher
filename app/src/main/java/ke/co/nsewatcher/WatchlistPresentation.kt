package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import java.util.Locale

internal object WatchlistPresentation {
    fun symbol(value: String) = value.trim().uppercase(Locale.US)
    val priceTypes = setOf(AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW, AlertType.DAILY_GAIN, AlertType.DAILY_LOSS)
    val supportedTypes = listOf(AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW, AlertType.DAILY_GAIN,
        AlertType.DAILY_LOSS, AlertType.HIGH_VOLUME, AlertType.NEWS, AlertType.CORPORATE_ACTION)

    fun companies(saved: List<String>, catalog: List<Stock>, quotes: List<Stock>): List<Stock> {
        val profiles = catalog.associateBy { symbol(it.symbol) }
        val prices = quotes.associateBy { symbol(it.symbol) }
        return saved.map(::symbol).filter { it.isNotBlank() }.distinct().sorted().map { key ->
            val profile = profiles[key]
            val quote = prices[key]
            if (quote != null) quote.copy(symbol = key,
                name = profile?.name?.takeIf { it.isNotBlank() } ?: quote.name.ifBlank { key },
                logoUrl = quote.logoUrl ?: profile?.logoUrl)
            else Stock(key, profile?.name?.takeIf { it.isNotBlank() } ?: key,
                Double.NaN, Double.NaN, emptyList(), logoUrl = profile?.logoUrl,
                sector = profile?.sector.orEmpty(), changeAvailable = false, volumeAvailable = false)
        }
    }

    fun filter(companies: List<Stock>, query: String, alertsOnly: Boolean, alerts: List<PriceAlert>): List<Stock> {
        val term = query.trim()
        val withRules = alerts.filter { it.type in priceTypes }.map { symbol(it.symbol) }.toSet()
        return companies.filter {
            (!alertsOnly || symbol(it.symbol) in withRules) &&
                (term.isBlank() || it.symbol.contains(term, true) || it.name.contains(term, true))
        }
    }

    fun linkedNews(items: List<NewsItem>, companies: List<Stock>): List<NewsItem> {
        val symbols = companies.map { symbol(it.symbol) }.toSet()
        val names = companies.map { it.name.trim().lowercase(Locale.US) }.filter { it.isNotBlank() }.toSet()
        return items.filter { item ->
            symbol(item.symbol) in symbols || (item.symbol.isBlank() && item.companyName.isNotBlank() && item.companyName.trim().lowercase(Locale.US) in names)
        }.distinctBy { it.id }.sortedByDescending { it.publishedAt }
    }

    fun trend(points: List<MyStocksCache.HistoryPoint>): List<MyStocksCache.HistoryPoint> = points
        .filter { it.close.isFinite() && it.close > 0 && CompanyResearchPresentation.timestamp(it.date) != null }
        .sortedBy { CompanyResearchPresentation.timestamp(it.date) }.distinctBy { CompanyResearchPresentation.timestamp(it.date) }

    fun needsThreshold(type: AlertType) = type != AlertType.NEWS && type != AlertType.CORPORATE_ACTION
    fun threshold(raw: String): Double? = raw.trim().replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

    fun typeLabel(type: AlertType): String = when (type) {
        AlertType.PRICE_ABOVE -> "Price crosses above"
        AlertType.PRICE_BELOW -> "Price crosses below"
        AlertType.DAILY_GAIN -> "Daily gain reaches"
        AlertType.DAILY_LOSS -> "Daily loss reaches"
        AlertType.HIGH_VOLUME -> "Volume above average"
        AlertType.NEWS -> "New company news"
        AlertType.CORPORATE_ACTION -> "Corporate action"
        AlertType.BREAKOUT -> "Breakout (unsupported)"
    }

    fun ruleLabel(rule: PriceAlert): String {
        val value = rule.threshold?.takeIf { it.isFinite() }?.let {
            if (rule.type in setOf(AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW)) String.format(Locale.US, "KSh %,.2f", it)
            else String.format(Locale.US, "%.2f%%", it)
        }
        return listOfNotNull(rule.symbol, typeLabel(rule.type).lowercase(Locale.US), value).joinToString(" ")
    }
}
