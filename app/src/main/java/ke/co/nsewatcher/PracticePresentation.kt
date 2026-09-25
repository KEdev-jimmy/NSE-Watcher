package ke.co.nsewatcher

internal data class PracticeValuationFreshness(
    val provisional: Boolean,
    val unconfirmedSymbols: Set<String> = emptySet()
)

internal data class PracticeAttentionSummary(
    val pendingOrders: Int,
    val needsFirstReview: Int,
    val newEvidence: Int
) {
    val total: Int get() = pendingOrders + needsFirstReview + newEvidence
}

internal object PracticePortfolioPresentation {
    fun quoteConfirmedByCurrentFeed(
        symbol: String,
        state: PracticeState,
        currentQuotes: List<Stock>
    ): Boolean {
        val normalized = WatchlistPresentation.symbol(symbol)
        if (normalized.isBlank()) return false

        val saved = state.quotes.firstOrNull {
            WatchlistPresentation.symbol(it.symbol) == normalized
        } ?: return false
        if (!saved.price.isFinite() || saved.price <= 0.0) return false

        val savedAt = CompanyResearchPresentation.timestamp(saved.at) ?: return false
        val current = currentQuotes.firstOrNull {
            WatchlistPresentation.symbol(it.symbol) == normalized &&
                it.price.isFinite() &&
                it.price > 0.0
        } ?: return false
        val currentAt = CompanyResearchPresentation.timestamp(current.observedAt) ?: return false

        // The stored Practice observation is considered confirmed when it is at least
        // as recent as the quote currently loaded by the app. A missing current quote
        // is not treated as proof that an older saved observation is current.
        return !savedAt.isBefore(currentAt)
    }

    fun valuationFreshness(
        state: PracticeState,
        currentQuotes: List<Stock>
    ): PracticeValuationFreshness {
        if (state.holdings.isEmpty()) return PracticeValuationFreshness(provisional = false)
        val unconfirmed = state.holdings
            .map { WatchlistPresentation.symbol(it.symbol) }
            .filterTo(linkedSetOf()) { symbol ->
                symbol.isBlank() || !quoteConfirmedByCurrentFeed(symbol, state, currentQuotes)
            }
        return PracticeValuationFreshness(
            provisional = unconfirmed.isNotEmpty(),
            unconfirmedSymbols = unconfirmed
        )
    }

    fun attention(
        state: PracticeState,
        learning: PracticeLearningInsights
    ): PracticeAttentionSummary = PracticeAttentionSummary(
        pendingOrders = state.orders.count { it.status == "PENDING" },
        needsFirstReview = learning.needsFirstReview,
        newEvidence = learning.newEvidenceAfterReview
    )
}
