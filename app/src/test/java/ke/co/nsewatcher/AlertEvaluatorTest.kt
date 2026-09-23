package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.Duration

class AlertEvaluatorTest {
    private val now = Instant.parse("2026-09-23T10:00:00Z")
    private fun stock(price: Double = 30.5, change: Double = 5.1, at: Instant = now.minusSeconds(900)) =
        Stock("SCOM", "Safaricom", price, change, emptyList(), observedAt = at.toString(), source = "Provider",
            volume = 1500, averageVolume = 1000, averageVolumeAvailable = true)
    private fun rule(type: AlertType = AlertType.PRICE_ABOVE, threshold: Double? = 30.0, id: String = "rule") =
        PriceAlert(id, "SCOM", type, threshold, true)
    private fun prior(price: Double = 29.0, at: Instant = now.minusSeconds(1800)) =
        AlertMonitorState(mapOf("SCOM" to AlertQuote(price, at.toString())))
    private fun article(id: String = "a", at: Instant = now.minusSeconds(3600), category: String = "Company News") =
        NewsItem(id, "Published story $id", "", "", "Publisher", at.toString(), category, "SCOM", "Safaricom", "",
            "https://example.com/$id", "", "", "")
    private fun evaluate(alert: PriceAlert = rule(), quote: Stock = stock(), state: AlertMonitorState = prior(), time: Instant = now, open: Boolean = true) =
        AlertEvaluator.evaluate(listOf(alert), listOf(quote), state, now = time, marketOpen = open)
    private fun remember(events: List<TriggeredAlert>, state: AlertMonitorState = AlertMonitorState()) =
        state.copy(processed = state.processed + events.associate { it.eventId to now.toEpochMilli() })

    @Test fun crossingsRequireAnActualTransitionInEitherDirection() {
        assertEquals(1, evaluate().size)
        assertTrue(evaluate(state = prior(30.1)).isEmpty())
        assertEquals(1, evaluate(rule(AlertType.PRICE_BELOW), stock(29.0), prior(31.0)).size)
        assertTrue(evaluate(rule(AlertType.PRICE_BELOW), stock(29.0), prior(29.5)).isEmpty())
    }
    @Test fun crossingCannotUseAnUndatedStaleRepeatedOrFutureBaseline() {
        val invalid = listOf(prior(at = now.minusSeconds(7200)), prior(at = now.minusSeconds(900)), prior(at = now),
            AlertMonitorState(mapOf("SCOM" to AlertQuote(29.0, ""))), AlertMonitorState())
        invalid.forEach { assertTrue(evaluate(state = it).isEmpty()) }
    }
    @Test fun staleUndatedFutureAndProviderStaleQuotesNeverTrigger() {
        val invalid = listOf(stock(at = now.minusSeconds(1801)), stock(at = now.plusSeconds(1)),
            stock().copy(observedAt = ""), stock().copy(observedAt = "2026-09-23"), stock().copy(freshnessMode = "STALE"),
            stock(Double.NaN), stock(0.0))
        listOf(rule(), rule(AlertType.DAILY_GAIN, 5.0), rule(AlertType.HIGH_VOLUME, 50.0)).forEach { alert ->
            invalid.forEach { assertTrue(evaluate(alert, it).isEmpty()) }
        }
    }
    @Test fun freshnessBoundaryAllowsThirtyMinutesButNotPreviousNairobiDay() {
        assertTrue(AlertEvaluator.eligibleQuote(stock(at = now.minusSeconds(1800)), now))
        val midnight = Instant.parse("2026-09-22T21:05:00Z")
        assertFalse(AlertEvaluator.eligibleQuote(stock(at = midnight.minusSeconds(600)), midnight))
    }
    @Test fun closedOrUnknownSessionSuppressesOnlyPriceConditions() {
        assertTrue(evaluate(open = false).isEmpty())
        val news = AlertEvaluator.evaluate(listOf(rule(AlertType.NEWS, null)), emptyList(), AlertMonitorState(),
            listOf(article()), now, marketOpen = false)
        assertEquals(1, news.size)
    }
    @Test fun fridayAfterHoursStoryIsStillEligibleOnMondayWithoutAQuote() {
        val monday = Instant.parse("2026-09-21T07:00:00Z")
        val friday = article(at = Instant.parse("2026-09-18T17:00:00Z"))
        assertEquals(1, AlertEvaluator.evaluate(listOf(rule(AlertType.NEWS, null)), emptyList(), AlertMonitorState(),
            listOf(friday), monday, false).size)
    }
    @Test fun rejectsFutureUndatedAndOlderThanSevenDayNews() {
        val news = listOf(article("old", now.minus(Duration.ofDays(7)).minusSeconds(1)),
            article("future", now.plusSeconds(1)), article("unknown").copy(publishedAt = ""))
        assertTrue(AlertEvaluator.evaluate(listOf(rule(AlertType.NEWS, null)), emptyList(), AlertMonitorState(), news, now).isEmpty())
        assertEquals(1, AlertEvaluator.evaluate(listOf(rule(AlertType.NEWS, null)), emptyList(), AlertMonitorState(),
            listOf(article(at = now.minus(Duration.ofDays(7)))), now).size)
    }
    @Test fun twoStoriesAreBothDetectedOnceAndNeverAlternateOnLaterChecks() {
        val alerts = listOf(rule(AlertType.NEWS, null))
        val feed = listOf(article("a"), article("b", now.minusSeconds(7200)), article("a"))
        val first = AlertEvaluator.evaluate(alerts, emptyList(), AlertMonitorState(), feed, now)
        assertEquals(setOf("a", "b"), first.map { it.articleId }.toSet())
        assertEquals(2, first.size)
        val persisted = remember(first)
        repeat(3) { assertTrue(AlertEvaluator.evaluate(alerts, emptyList(), persisted, feed.reversed(), now.plusSeconds(900L * it)).isEmpty()) }
        val next = AlertEvaluator.evaluate(alerts, emptyList(), persisted, feed + article("c"), now)
        assertEquals(listOf("c"), next.map { it.articleId })
    }
    @Test fun migrationRespectsPreviouslySentNewsAndDailyAlerts() {
        assertTrue(AlertEvaluator.evaluate(listOf(rule(AlertType.NEWS, null)), emptyList(), AlertMonitorState(),
            listOf(article()), now, legacyNewsIds = mapOf("rule" to "a")).isEmpty())
        assertTrue(AlertEvaluator.evaluate(listOf(rule(AlertType.DAILY_GAIN, 5.0)), listOf(stock()), AlertMonitorState(),
            now = now, marketOpen = true, legacyDailyDates = mapOf("rule" to "2026-09-23")).isEmpty())
    }
    @Test fun newsAndCorporateActionRulesStayInSeparateCategories() {
        val ordinary = article("ordinary", category = "Company News")
        val dividend = article("dividend", category = "Dividends")
        val action = article("action", category = "Corporate Actions")
        val news = AlertEvaluator.evaluate(listOf(rule(AlertType.NEWS, null)), emptyList(), AlertMonitorState(),
            listOf(ordinary, dividend, action), now)
        val corporate = AlertEvaluator.evaluate(listOf(rule(AlertType.CORPORATE_ACTION, null, "corp")), emptyList(), AlertMonitorState(),
            listOf(ordinary, dividend, action), now)
        assertEquals(listOf("ordinary"), news.map { it.articleId })
        assertEquals(setOf("dividend", "action"), corporate.map { it.articleId }.toSet())
    }

    @Test fun corporateActionsFilterCategoryAndCompany() {
        val feed = listOf(article("ordinary"), article("dividend", category = "Dividends"),
            article("action", category = "Corporate Actions"), article("other", category = "Dividends").copy(symbol = "KCB"))
        val result = AlertEvaluator.evaluate(listOf(rule(AlertType.CORPORATE_ACTION, null)), emptyList(), AlertMonitorState(), feed, now)
        assertEquals(setOf("dividend", "action"), result.map { it.articleId }.toSet())
    }
    @Test fun newsCarriesExactEvidenceInsteadOfQuoteTimeOrTitleMatching() {
        val item = article()
        val result = AlertEvaluator.evaluate(listOf(rule(AlertType.NEWS, null)), listOf(stock()), AlertMonitorState(), listOf(item), now).single().event(now)
        assertEquals(item.id, result.articleId)
        assertEquals(item.publishedAt, result.observedAt)
        assertEquals(item.url, result.sourceUrl)
        assertEquals(item.source, result.source)
        assertEquals(item.title, result.articleTitle)
    }
    @Test fun volumeAndDailyRulesNotifyOncePerNairobiDayThenRearm() {
        for (alert in listOf(rule(AlertType.HIGH_VOLUME, 50.0), rule(AlertType.DAILY_GAIN, 5.0), rule(AlertType.DAILY_LOSS, 5.0))) {
            val quote = stock(change = if (alert.type == AlertType.DAILY_LOSS) -6.0 else 6.0)
            val first = evaluate(alert, quote)
            assertEquals(1, first.size)
            val state = remember(first)
            assertTrue(evaluate(alert, quote.copy(observedAt = now.toString()), state).isEmpty())
            val tomorrow = now.plus(Duration.ofDays(1))
            assertEquals(1, evaluate(alert, quote.copy(observedAt = tomorrow.minusSeconds(900).toString()), state, tomorrow).size)
        }
    }
    @Test fun volumeNeedsProviderAverageAndChangeMustBeFiniteAndAvailable() {
        assertTrue(evaluate(rule(AlertType.HIGH_VOLUME, 50.0), stock().copy(averageVolumeAvailable = false)).isEmpty())
        assertTrue(evaluate(rule(AlertType.HIGH_VOLUME, 50.0), stock().copy(volume = 1499)).isEmpty())
        assertTrue(evaluate(rule(AlertType.HIGH_VOLUME, 50.0), stock().copy(averageVolume = 0)).isEmpty())
        assertTrue(evaluate(rule(AlertType.DAILY_GAIN, 5.0), stock(change = Double.POSITIVE_INFINITY)).isEmpty())
        assertTrue(evaluate(rule(AlertType.DAILY_GAIN, 5.0), stock().copy(changeAvailable = false)).isEmpty())
        assertTrue(evaluate(rule(AlertType.DAILY_GAIN, Double.NaN)).isEmpty())
    }
    @Test fun replayedCrossingDoesNotNotifyAgainButLaterRecrossingCan() {
        val first = evaluate()
        val state = remember(first, prior())
        assertTrue(evaluate(state = state).isEmpty())
        val later = stock(at = now)
        assertEquals(1, evaluate(quote = later, state = state.copy(quotes = mapOf("SCOM" to AlertQuote(29.0, now.minusSeconds(600).toString())))).size)
    }
    @Test fun quoteCheckpointNeverMovesBackwardsOrStoresStaleObservations() {
        val state = prior(at = now.minusSeconds(600))
        val next = AlertEvaluator.nextQuotes(state.quotes, listOf(stock()), now)
        assertEquals(state.quotes, next)
        assertTrue(AlertEvaluator.nextQuotes(emptyMap(), listOf(stock(at = now.minusSeconds(3600))), now).isEmpty())
    }
    @Test fun disabledAndUnsupportedRulesDoNotTrigger() {
        assertTrue(evaluate(rule().copy(enabled = false)).isEmpty())
        assertTrue(evaluate(rule(AlertType.BREAKOUT, null)).isEmpty())
    }
    @Test fun opaqueArticleAndRuleIdsCannotCollide() {
        assertNotEquals(AlertEvaluator.eventKey("a|b", "c"), AlertEvaluator.eventKey("a", "b|c"))
    }
}
