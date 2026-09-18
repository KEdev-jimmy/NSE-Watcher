package ke.co.nsewatcher

import java.util.Locale
import ke.co.nsewatcher.domain.EvidenceAdapters
import ke.co.nsewatcher.domain.EvidenceGraph
import ke.co.nsewatcher.domain.EvidenceRecord
import ke.co.nsewatcher.domain.EvidenceRelationship
import ke.co.nsewatcher.domain.EvidenceRelationshipType
import ke.co.nsewatcher.domain.EvidenceType

/**
 * Home-facing domain models. These keep market calculations and provenance out
 * of Compose rendering so the Dashboard can consume structured intelligence.
 */
data class HomeMarketBreadth(
    val advancing: Int,
    val declining: Int,
    val unchanged: Int,
    val reportedVolume: Long
)

data class HomeSectorPulse(
    val sector: String,
    val averageChangePct: Double,
    val memberCount: Int
)

enum class HomeIntelligenceType { FACT, CALCULATION, NEWS }

data class HomeEvidenceReference(
    val id: String,
    val source: String,
    val sourceUrl: String = "",
    val date: String = "",
    val symbol: String = "",
    val category: String = ""
)

data class HomeIntelligenceItem(
    val id: String,
    val type: HomeIntelligenceType,
    val symbol: String = "",
    val company: String = "",
    val fact: String,
    val calculation: String = "",
    val interpretation: String = "",
    val evidence: List<HomeEvidenceReference> = emptyList(),
    val source: String = "",
    val sourceUrl: String = ""
)

data class HomeChangeItem(
    val id: String,
    val label: String,
    val detail: String,
    val value: String,
    val symbol: String = "",
    val type: HomeIntelligenceType = HomeIntelligenceType.CALCULATION,
    val source: HomeEvidenceReference? = null
)

data class HomeIntelligenceSnapshot(
    val breadth: HomeMarketBreadth,
    val gainers: List<Stock>,
    val losers: List<Stock>,
    val sectors: List<HomeSectorPulse>,
    val intelligence: List<HomeIntelligenceItem>,
    val changes: List<HomeChangeItem>,
    val corporateActions: List<NewsItem>,
    val companyNews: List<NewsItem>,
    val evidenceGraph: EvidenceGraph
)

object HomeIntelligenceEngine {
    fun build(stocks: List<Stock>, news: List<NewsItem>): HomeIntelligenceSnapshot {
        val valid = stocks.filter { it.change.isFinite() }
        val gainers = valid.filter { it.change > 0 }.sortedByDescending { it.change }
        val losers = valid.filter { it.change < 0 }.sortedBy { it.change }
        val breadth = HomeMarketBreadth(
            advancing = gainers.size,
            declining = losers.size,
            unchanged = valid.count { it.change == 0.0 },
            reportedVolume = valid.sumOf { it.volume.coerceAtLeast(0L) }
        )

        val sectors = valid
            .filter { it.sector.isNotBlank() && !it.sector.equals("Other", ignoreCase = true) }
            .groupBy { it.sector.trim() }
            .map { (sector, members) ->
                HomeSectorPulse(sector, members.map { it.change }.average(), members.size)
            }
            .sortedByDescending { kotlin.math.abs(it.averageChangePct) }

        val corporateActions = news.filter { it.isCorporateAction() }
        val companyNews = news.filter {
            (it.symbol.isNotBlank() || it.companyName.isNotBlank()) &&
                !it.isCorporateAction() &&
                it.intelligenceRelevance == "market"
        }
        val companyNewsEvidence = companyNews.mapNotNull(EvidenceAdapters::fromNews)

        val marketEvidence = valid.mapNotNull(EvidenceAdapters::fromStock)
        val graphRecords = mutableListOf<EvidenceRecord>()
        graphRecords += marketEvidence
        graphRecords += companyNewsEvidence

        val graphRelationships = mutableListOf<EvidenceRelationship>()
        if (valid.isNotEmpty()) {
            val breadthId = "calculation:market-breadth"
            graphRecords += EvidenceRecord(
                id = breadthId,
                type = EvidenceType.MARKET_DATA,
                claim = "Market breadth calculated from the available stock feed",
                value = breadth.advancing.toString() + " advancing, " + breadth.declining + " declining, " + breadth.unchanged + " unchanged",
                source = "MyStocks Africa"
            )
            marketEvidence.forEach { evidence ->
                graphRelationships += EvidenceRelationship(
                    id = "relationship:" + breadthId + ":" + evidence.id,
                    fromEvidenceId = breadthId,
                    toEvidenceId = evidence.id,
                    type = EvidenceRelationshipType.RELATED
                )
            }
        }

        sectors.forEach { sector ->
            val sectorId = "calculation:sector-" + sector.sector.lowercase(Locale.US)
            graphRecords += EvidenceRecord(
                id = sectorId,
                type = EvidenceType.MARKET_DATA,
                claim = displaySector(sector.sector) + " sector average calculated from available counters",
                value = sector.memberCount.toString() + " counters average " + signedPercent(sector.averageChangePct),
                source = "MyStocks Africa"
            )
            valid.filter { it.sector.trim().equals(sector.sector, ignoreCase = true) }
                .mapNotNull(EvidenceAdapters::fromStock)
                .forEach { evidence ->
                    graphRelationships += EvidenceRelationship(
                        id = "relationship:" + sectorId + ":" + evidence.id,
                        fromEvidenceId = sectorId,
                        toEvidenceId = evidence.id,
                        type = EvidenceRelationshipType.RELATED
                    )
                }
        }

        val evidenceGraph = EvidenceGraph.of(graphRecords, graphRelationships)

        val strongest = sectors.maxByOrNull { it.averageChangePct }
        val weakest = sectors.minByOrNull { it.averageChangePct }
        val topGainer = gainers.firstOrNull()
        val topLoser = losers.firstOrNull()

        val intelligence = buildList {
            strongest?.let { sector ->
                add(
                    HomeIntelligenceItem(
                        id = "sector-${sector.sector.lowercase(Locale.US)}",
                        type = HomeIntelligenceType.CALCULATION,
                        fact = "${displaySector(sector.sector)} has the strongest sector average in the current stock feed.",
                        calculation = "${sector.memberCount} counters average ${signedPercent(sector.averageChangePct)}.",
                        interpretation = "This is an average of the available counters, not an official NSE sector index.",
                        evidence = listOfNotNull(
                            evidenceGraph.record("calculation:sector-${sector.sector.lowercase(Locale.US)}")?.let {
                                homeEvidenceReference(it, "market calculation")
                            }
                        ),
                        source = "MyStocks Africa • calculated from current stock feed"
                    )
                )
            }

            topGainer?.let { stock ->
                add(
                    HomeIntelligenceItem(
                        id = "gainer-${stock.symbol}",
                        type = HomeIntelligenceType.FACT,
                        symbol = stock.symbol,
                        company = stock.name,
                        fact = "${stock.symbol} is the largest current gainer in the available stock feed.",
                        calculation = "Price ${formatPrice(stock.price)}; daily movement ${signedPercent(stock.change)}.",
                        evidence = listOfNotNull(
                            evidenceGraph.record("market:${stock.symbol.lowercase()}")?.let {
                                homeEvidenceReference(it, "market movement")
                            }
                        ),
                        source = "MyStocks Africa • current stock feed"
                    )
                )
            }

            companyNewsEvidence.firstOrNull()?.let { evidence ->
                val item = companyNews.firstOrNull { it.id == evidence.id.removePrefix("news:") } ?: return@let
                add(
                    HomeIntelligenceItem(
                        id = "news-${item.id}",
                        type = HomeIntelligenceType.NEWS,
                        symbol = evidence.symbol.orEmpty(),
                        company = evidence.companyName.orEmpty(),
                        fact = evidence.claim,
                        calculation = evidence.value.orEmpty(),
                        interpretation = "The story is presented as news; no financial conclusion is inferred from the headline alone.",
                        evidence = listOf(
                            HomeEvidenceReference(
                                id = evidence.id,
                                source = evidence.source.ifBlank { "News feed" },
                                sourceUrl = evidence.sourceUrl.orEmpty(),
                                date = evidence.publishedAt.orEmpty(),
                                symbol = evidence.symbol.orEmpty(),
                                category = item.category
                            )
                        ),
                        source = listOf(evidence.companyName.orEmpty().ifBlank { evidence.symbol.orEmpty() }, evidence.source)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                            .ifBlank { "News feed" },
                        sourceUrl = evidence.sourceUrl.orEmpty()
                    )
                )
            }

        }

        val changes = buildList {
            if (valid.isNotEmpty()) {
                val breadthEvidence = evidenceGraph.record("calculation:market-breadth")
                add(HomeChangeItem(
                    id = "breadth",
                    label = "Market breadth",
                    detail = "${breadth.advancing} advancing • ${breadth.declining} declining • ${breadth.unchanged} unchanged",
                    value = signedInt(breadth.advancing - breadth.declining),
                    source = breadthEvidence?.let { HomeEvidenceReference(it.id, it.source, sourceUrl = it.sourceUrl ?: "", date = it.publishedAt ?: "", category = "market calculation") }
                ))
            }
            strongest?.let {
                add(HomeChangeItem(
                    id = "strongest-sector",
                    label = "Strongest sector",
                    detail = "${displaySector(it.sector)} • ${it.memberCount} counters",
                    value = signedPercent(it.averageChangePct),
                    source = evidenceGraph.record("calculation:sector-${it.sector.lowercase(Locale.US)}")?.let {
                        HomeEvidenceReference(it.id, it.source, sourceUrl = it.sourceUrl ?: "", date = it.publishedAt ?: "", category = "sector calculation")
                    }
                ))
            }
            weakest?.takeIf { strongest?.sector != it.sector }?.let {
                add(HomeChangeItem(
                    id = "weakest-sector",
                    label = "Weakest sector",
                    detail = "${displaySector(it.sector)} • ${it.memberCount} counters",
                    value = signedPercent(it.averageChangePct),
                    source = evidenceGraph.record("calculation:sector-${it.sector.lowercase(Locale.US)}")?.let {
                        HomeEvidenceReference(it.id, it.source, category = "sector calculation")
                    }
                ))
            }
            topGainer?.let {
                add(HomeChangeItem(
                    id = "largest-gainer",
                    label = "Largest gainer",
                    detail = it.symbol,
                    value = signedPercent(it.change),
                    symbol = it.symbol,
                    type = HomeIntelligenceType.FACT,
                    source = evidenceGraph.record("market:${it.symbol.lowercase()}")?.let {
                        HomeEvidenceReference(it.id, it.source ?: "MyStocks Africa", symbol = it.symbol ?: "", category = "market movement")
                    }
                ))
            }
            topLoser?.let {
                add(HomeChangeItem(
                    id = "largest-loser",
                    label = "Largest loser",
                    detail = it.symbol,
                    value = signedPercent(it.change),
                    symbol = it.symbol,
                    type = HomeIntelligenceType.FACT,
                    source = evidenceGraph.record("market:${it.symbol.lowercase()}")?.let {
                        HomeEvidenceReference(it.id, it.source ?: "MyStocks Africa", symbol = it.symbol ?: "", category = "market movement")
                    }
                ))
            }
            companyNews.firstOrNull()?.let {
                val evidence = companyNewsEvidence.firstOrNull { e -> e.id == "news:${it.id}" }
                add(HomeChangeItem(
                    id = "latest-company-news",
                    label = "Latest company news",
                    detail = it.companyName.ifBlank { it.symbol }.ifBlank { "News" },
                    value = "Open",
                    symbol = it.symbol,
                    type = HomeIntelligenceType.NEWS,
                    source = evidence?.let { e ->
                        HomeEvidenceReference(e.id, e.source, e.sourceUrl.orEmpty(), e.publishedAt.orEmpty(), e.symbol.orEmpty(), it.category)
                    }
                ))
            }
        }

        return HomeIntelligenceSnapshot(
            breadth = breadth,
            gainers = gainers,
            losers = losers,
            sectors = sectors,
            intelligence = intelligence,
            changes = changes,
            corporateActions = corporateActions,
            companyNews = companyNews,
            evidenceGraph = evidenceGraph
        )
    }

    private fun homeEvidenceReference(record: EvidenceRecord, category: String): HomeEvidenceReference =
        HomeEvidenceReference(
            id = record.id,
            source = record.source,
            sourceUrl = record.sourceUrl.orEmpty(),
            date = record.publishedAt ?: record.observedAt.orEmpty(),
            symbol = record.symbol.orEmpty(),
            category = category
        )

    private fun signedPercent(value: Double): String = String.format(Locale.US, "%+.2f%%", value)
    private fun signedInt(value: Int): String = String.format(Locale.US, "%+d", value)
    private fun formatPrice(value: Double): String = if (value.isFinite()) String.format(Locale.US, "KSh %.2f", value) else "Price unavailable"
    private fun displaySector(sector: String): String = when (sector.lowercase(Locale.US)) {
        "telecommunication", "telecommunications" -> "Telecom"
        "oil & gas", "oil and gas" -> "Energy"
        "banks" -> "Banking"
        else -> sector
    }
}

private fun NewsItem.isCorporateAction(): Boolean {
    val category = category.lowercase(Locale.US)
    return category.contains("dividend") || category.contains("corporate") ||
        category.contains("rights") || category.contains("bonus") || category.contains("action")
}
