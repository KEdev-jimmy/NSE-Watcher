package ke.co.nsewatcher

import java.time.Duration
import java.time.Instant

internal data class HomeTopPerformer(
    val symbol: String,
    val gainPercent: Double
)

internal data class HomePortfolioSummary(
    val invested: Double,
    val marketValue: Double,
    val gain: Double,
    val gainPercent: Double?,
    val availableCash: Double,
    val provisional: Boolean,
    val symbols: List<String>,
    val topPerformer: HomeTopPerformer?,
    val balanceHistory: List<Double>
)

internal data class HomeTradingActivity(
    val stock: Stock,
    val vsAverage: Double?
)

internal data class HomeMinuteSummary(
    val advancing: Int,
    val declining: Int,
    val watched: Int,
    val verified: Int
)

/** Presentation rules shared by the Home cards; no illustrative market numbers. */
internal object HomeBriefingPresentation {
    /** Market breadth from observed, non-stale quotes only, not an NSE-wide claim. */
    fun minute(stocks: List<Stock>, watched: List<Stock>, now: Instant): HomeMinuteSummary {
        val verified = stocks.asSequence()
            .filter { it.price.isFinite() && it.price > 0.0 }
            .filter { it.changeAvailable && it.change.isFinite() }
            .filterNot { it.freshnessMode.equals("STALE", ignoreCase = true) }
            .filter { stock ->
                val observed = CompanyResearchPresentation.timestamp(stock.observedAt)
                    ?: return@filter false
                !observed.isAfter(now.plusSeconds(60)) &&
                    Duration.between(observed, now) <= Duration.ofDays(7)
            }
            .distinctBy { it.symbol.trim().uppercase() }
            .toList()
        val watchSymbols = watched.map { it.symbol.trim().uppercase() }.toSet()
        return HomeMinuteSummary(
            advancing = verified.count { it.change > 0.0 },
            declining = verified.count { it.change < 0.0 },
            watched = verified.count { it.symbol.trim().uppercase() in watchSymbols },
            verified = verified.size
        )
    }

    fun portfolio(state: PracticeState?, currentQuotes: List<Stock>): HomePortfolioSummary? {
        if (state?.enabled != true) return null
        val positions = PracticeEngine.positions(state)
        val invested = PracticeEngine.money(state.holdings.sumOf { it.cost })
        val marketValue = PracticeEngine.money(positions.sumOf { it.value })
        val gain = PracticeEngine.money(marketValue - invested)
        val freshness = PracticePortfolioPresentation.valuationFreshness(
            state,
            currentQuotes.filterNot { it.freshnessMode.equals("STALE", ignoreCase = true) }
        )
        val provisional = positions.any { it.estimated } || freshness.provisional
        val validPositions = if (provisional) emptyList() else positions.filter { it.holding.cost > 0.0 }
        val top = validPositions.maxByOrNull { (it.gain / it.holding.cost) * 100.0 }
        return HomePortfolioSummary(
            invested = invested,
            marketValue = marketValue,
            gain = gain,
            gainPercent = if (provisional || invested <= 0.0) null else gain * 100.0 / invested,
            availableCash = PracticeEngine.available(state),
            provisional = provisional,
            symbols = state.holdings.map { it.symbol }.distinct().take(5),
            topPerformer = top?.let {
                HomeTopPerformer(it.holding.symbol, it.gain * 100.0 / it.holding.cost)
            },
            balanceHistory = state.snapshots.map { it.value }.filter { it.isFinite() && it > 0.0 }.takeLast(40)
        )
    }

    /** Volume illustrates trading activity, not order-book depth or execution liquidity. */
    fun tradingActivity(stocks: List<Stock>, now: Instant, limit: Int = 2): List<HomeTradingActivity> =
        stocks.asSequence()
            .filter { it.volumeAvailable && it.volume > 0L }
            .filterNot { it.freshnessMode.equals("STALE", ignoreCase = true) }
            .filter { stock ->
                val observed = CompanyResearchPresentation.timestamp(stock.observedAt)
                    ?: return@filter false
                !observed.isAfter(now.plusSeconds(60)) &&
                    Duration.between(observed, now) <= Duration.ofDays(7)
            }
            .sortedWith(compareByDescending<Stock> { it.volume }.thenBy { it.symbol })
            .take(limit.coerceAtLeast(0))
            .map { stock ->
                HomeTradingActivity(
                    stock,
                    if (stock.averageVolumeAvailable && stock.averageVolume > 0L) {
                        stock.volume.toDouble() / stock.averageVolume.toDouble()
                    } else null
                )
            }
            .toList()

    fun latestNews(news: List<NewsItem>, now: Instant, limit: Int = 2): List<NewsItem> =
        news.asSequence()
            .filter { it.title.isNotBlank() && it.source.isNotBlank() && it.url.isNotBlank() }
            .filter { item ->
                CompanyResearchPresentation.timestamp(item.publishedAt)
                    ?.let { !it.isAfter(now.plusSeconds(60)) } == true
            }
            .distinctBy { it.id.ifBlank { it.url } }
            .sortedByDescending { CompanyResearchPresentation.timestamp(it.publishedAt) }
            .take(limit.coerceAtLeast(0))
            .toList()

    fun marketStatus(known: Boolean, open: Boolean): String = when {
        !known -> "Status unavailable"
        open -> "Market opened"
        else -> "Market closed"
    }
}
