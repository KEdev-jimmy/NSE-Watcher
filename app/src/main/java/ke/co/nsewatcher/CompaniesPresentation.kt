package ke.co.nsewatcher

import java.util.Locale

internal enum class CompanySort(val label: String) {
    NAME("A–Z"), NAME_DESC("Z–A"), GAIN("Daily change: high to low"), LOSS("Daily change: low to high"), PRICE("Price: low to high")
}

internal object CompaniesPresentation {
    fun sector(raw: String): String = when (val value = raw.trim()) {
        "" -> "Other"
        else -> when (value.lowercase(Locale.US)) {
            "banks", "bank", "banking" -> "Banking"
            "telecommunication", "telecommunications", "telecom" -> "Telecom"
            "energy", "energy & petroleum", "energy and petroleum", "oil & gas", "oil and gas" -> "Energy"
            "other", "unknown", "n/a" -> "Other"
            else -> value
        }
    }

    fun companies(catalog: List<Stock>, quotes: List<Stock>): List<Stock> {
        val profiles = catalog.associateBy { WatchlistPresentation.symbol(it.symbol) }
        return WatchlistPresentation.companies((catalog + quotes).map { it.symbol }, catalog, quotes).map { stock ->
            val supplied = profiles[stock.symbol]?.sector?.takeIf { sector(it) != "Other" } ?: stock.sector
            stock.copy(sector = sector(supplied))
        }
    }

    fun visible(companies: List<Stock>, query: String, selectedSector: String, sort: CompanySort): List<Stock> {
        val term = query.trim()
        val filtered = companies.filter { (selectedSector == "All" || it.sector == selectedSector) &&
            (term.isBlank() || it.name.contains(term, true) || it.symbol.contains(term, true)) }
        val byName = compareBy<Stock> { it.name.lowercase(Locale.US) }.thenBy { it.symbol }
        return when (sort) {
            CompanySort.NAME -> filtered.sortedWith(byName)
            CompanySort.NAME_DESC -> filtered.sortedWith(byName.reversed())
            CompanySort.GAIN, CompanySort.LOSS -> filtered.sortedWith(
                compareBy<Stock> { !it.changeAvailable || !it.change.isFinite() }
                    .thenBy { if (it.changeAvailable && it.change.isFinite()) it.change * if (sort == CompanySort.GAIN) -1 else 1 else 0.0 }
                    .then(byName))
            CompanySort.PRICE -> filtered.sortedWith(compareBy<Stock> { !it.price.isFinite() || it.price <= 0 }
                .thenBy { if (it.price.isFinite() && it.price > 0) it.price else 0.0 }.then(byName))
        }
    }

    fun toggleSelection(selected: List<String>, symbol: String): List<String> {
        val key = WatchlistPresentation.symbol(symbol)
        val normalized = selected.map(WatchlistPresentation::symbol).filter(String::isNotBlank).distinct().take(2)
        return when { key in normalized -> normalized - key; key.isBlank() || normalized.size >= 2 -> normalized; else -> normalized + key }
    }

    fun newsByCompany(news: List<NewsItem>, companies: List<Stock>): Map<String, NewsItem> = companies.mapNotNull { stock ->
        WatchlistPresentation.linkedNews(news, listOf(stock))
            .filter { it.title.isNotBlank() }
            .maxByOrNull { CompanyResearchPresentation.timestamp(it.publishedAt) ?: java.time.Instant.MIN }
            ?.let { stock.symbol to it }
    }.toMap()
}
