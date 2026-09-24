package ke.co.nsewatcher

import java.util.Locale
import kotlin.math.abs

internal data class HomePremiumHero(
    val title: String,
    val body: String,
    val action: String,
    val briefItem: HomeBriefItem? = null,
    val stock: Stock? = null
)

internal data class HomePremiumSentiment(
    val label: String,
    val detail: String,
    val breadthNet: Int,
    val coverage: Int,
    val total: Int
)

internal data class HomePremiumIndexSpotlight(
    val label: String,
    val value: String,
    val change: String?,
    val positive: Boolean?
)

internal object HomePremiumPresentation {
    fun hero(
        intelligence: HomeIntelligenceSnapshot,
        briefItems: List<HomeBriefItem>
    ): HomePremiumHero {
        briefItems.firstOrNull()?.let { item ->
            return HomePremiumHero(
                title = item.title,
                body = item.detail.ifBlank {
                    item.whyItMayMatter.ifBlank { "Open the evidence to review what changed." }
                },
                action = item.action.ifBlank { "Review now" },
                briefItem = item,
                stock = item.stock
            )
        }

        val sector = intelligence.sectors.maxByOrNull { abs(it.averageChangePct) }
        if (sector != null) {
            val display = displaySector(sector.sector)
            val direction = when {
                sector.averageChangePct > 0.0 -> "leads the available market"
                sector.averageChangePct < 0.0 -> "is under pressure"
                else -> "is broadly unchanged"
            }
            return HomePremiumHero(
                title = "$display $direction",
                body = "${sector.memberCount} available counters average " +
                    String.format(Locale.US, "%+.2f%%", sector.averageChangePct) +
                    ". This is a feed calculation, not an official NSE sector index.",
                action = "See what's moving"
            )
        }

        val breadth = intelligence.breadth
        val total = breadth.advancing + breadth.declining + breadth.unchanged
        return if (total > 0) {
            HomePremiumHero(
                title = when {
                    breadth.advancing > breadth.declining -> "More shares are rising than falling"
                    breadth.declining > breadth.advancing -> "More shares are falling than rising"
                    else -> "The available market is balanced"
                },
                body = "${breadth.advancing} rising · ${breadth.unchanged} unchanged · ${breadth.declining} falling in the latest available daily changes.",
                action = "Explore market"
            )
        } else {
            HomePremiumHero(
                title = "Market picture is still loading",
                body = "NSE Watcher will show the latest supported market context when observations become available.",
                action = "Explore market"
            )
        }
    }

    fun sentiment(breadth: HomeMarketBreadth, totalStocks: Int): HomePremiumSentiment {
        val coverage = breadth.advancing + breadth.declining + breadth.unchanged
        val net = breadth.advancing - breadth.declining
        val threshold = (coverage * 0.15).coerceAtLeast(1.0)
        val label = when {
            coverage == 0 -> "Unavailable"
            net >= threshold -> "Positive"
            net <= -threshold -> "Cautious"
            else -> "Mixed"
        }
        val detail = when (label) {
            "Positive" -> "More available counters are rising than falling."
            "Cautious" -> "More available counters are falling than rising."
            "Mixed" -> "Rising and falling counters are relatively balanced."
            else -> "Not enough daily changes are available yet."
        }
        return HomePremiumSentiment(
            label = label,
            detail = detail,
            breadthNet = net,
            coverage = coverage,
            total = totalStocks.coerceAtLeast(coverage)
        )
    }

    fun indexSpotlight(indices: List<HomeMarketIndex>): HomePremiumIndexSpotlight? {
        val index = indices.firstOrNull { it.value.isFinite() } ?: return null
        val change = index.changePct?.takeIf(Double::isFinite)
        return HomePremiumIndexSpotlight(
            label = index.name.ifBlank { index.symbol },
            value = String.format(Locale.US, "%,.2f", index.value),
            change = change?.let { String.format(Locale.US, "%+.2f%%", it) },
            positive = change?.let { it >= 0.0 }
        )
    }

    fun displaySector(raw: String): String = when (raw.trim().lowercase(Locale.US)) {
        "banks", "bank", "banking" -> "Banking"
        "telecommunication", "telecommunications", "telecom" -> "Telecom"
        "oil & gas", "oil and gas", "energy" -> "Energy"
        else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
}
