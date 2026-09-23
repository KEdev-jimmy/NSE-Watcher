package ke.co.nsewatcher

/**
 * Selects market-level intelligence that adds context beyond the dedicated
 * breadth and movers UI already present on Home.
 */
internal object HomeMarketContextPresentation {
    fun items(snapshot: HomeIntelligenceSnapshot): List<HomeIntelligenceItem> = listOfNotNull(
        snapshot.intelligence.firstOrNull { it.id == "market-index-pulse" },
        snapshot.intelligence.firstOrNull { it.id.startsWith("sector-") }
    )
}
