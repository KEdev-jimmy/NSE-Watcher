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


internal object HomeMarketIndexPresentation {
    fun fromProvider(indices: List<ke.co.nsewatcher.data.MyStocksCache.MarketIndex>): List<HomeMarketIndex> =
        indices.mapNotNull { index ->
            if (!index.value.isFinite() || index.value <= 0.0 || index.symbol.isBlank()) return@mapNotNull null
            HomeMarketIndex(
                symbol = index.symbol,
                name = index.name,
                value = index.value,
                changePct = index.changePct?.takeIf { it.isFinite() },
                asOf = index.asOf,
                dataMode = when (index.freshnessMode.uppercase()) {
                    "CURRENT_SESSION" -> HomeMarketDataMode.CURRENT_SESSION
                    "END_OF_DAY" -> HomeMarketDataMode.END_OF_DAY
                    "STALE" -> HomeMarketDataMode.STALE
                    else -> HomeMarketDataMode.UNKNOWN
                }
            )
        }.distinctBy { it.symbol }
