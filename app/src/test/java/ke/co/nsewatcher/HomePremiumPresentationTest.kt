package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePremiumPresentationTest {
    private fun stock(
        symbol: String,
        change: Double,
        sector: String = "Banking"
    ) = Stock(
        symbol = symbol,
        name = symbol,
        price = 50.0,
        change = change,
        history = emptyList(),
        sector = sector,
        observedAt = "2026-09-24T12:00:00Z"
    )

    @Test fun heroPrefersARealUnreviewedBriefItem() {
        val snapshot = HomeIntelligenceEngine.build(
            stocks = listOf(stock("KCB", 2.0)),
            news = emptyList()
        )
        val item = HomeBriefItem(
            id = "brief-1",
            symbol = "KCB",
            title = "New evidence since your KCB practice decision",
            detail = "KCB published a later update.",
            whyItMayMatter = "Review the later evidence.",
            uncertainty = "Timing does not prove causation.",
            source = "Issuer",
            time = "2026-09-24T12:00:00Z",
            action = "Review decision"
        )

        val hero = HomePremiumPresentation.hero(snapshot, listOf(item))

        assertEquals(item.title, hero.title)
        assertEquals(item.detail, hero.body)
        assertEquals("Review decision", hero.action)
        assertEquals(item, hero.briefItem)
    }

    @Test fun heroFallsBackToCalculatedSectorContextWithoutInventingAnIndex() {
        val snapshot = HomeIntelligenceEngine.build(
            stocks = listOf(
                stock("KCB", 3.0),
                stock("EQTY", 1.0),
                stock("SCOM", -0.5, "Telecommunications")
            ),
            news = emptyList()
        )

        val hero = HomePremiumPresentation.hero(snapshot, emptyList())

        assertTrue(hero.title.contains("Banking"))
        assertTrue(hero.body.contains("feed calculation"))
        assertNull(hero.briefItem)
    }

    @Test fun sentimentIsDerivedOnlyFromAvailableBreadth() {
        val positive = HomePremiumPresentation.sentiment(
            HomeMarketBreadth(advancing = 8, declining = 2, unchanged = 1, reportedVolume = 0L),
            totalStocks = 11
        )
        val cautious = HomePremiumPresentation.sentiment(
            HomeMarketBreadth(advancing = 1, declining = 8, unchanged = 2, reportedVolume = 0L),
            totalStocks = 11
        )
        val mixed = HomePremiumPresentation.sentiment(
            HomeMarketBreadth(advancing = 4, declining = 4, unchanged = 3, reportedVolume = 0L),
            totalStocks = 11
        )

        assertEquals("Positive", positive.label)
        assertEquals("Cautious", cautious.label)
        assertEquals("Mixed", mixed.label)
    }

    @Test fun indexSpotlightIsAbsentWhenNoProviderIndexExists() {
        assertNull(HomePremiumPresentation.indexSpotlight(emptyList()))
    }

    @Test fun indexSpotlightUsesProviderValuesAsReturned() {
        val item = HomePremiumPresentation.indexSpotlight(
            listOf(
                HomeMarketIndex(
                    symbol = "NASI",
                    name = "NSE All Share",
                    value = 132.45,
                    changePct = 0.84
                )
            )
        )

        assertEquals("NSE All Share", item?.label)
        assertEquals("132.45", item?.value)
        assertEquals("+0.84%", item?.change)
    }
}
