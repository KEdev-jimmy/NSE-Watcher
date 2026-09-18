package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIntelligenceEngineTest {
    private fun stock(symbol: String, change: Double, sector: String, volume: Long = 1_000L) =
        Stock(symbol, symbol, 10.0, change, listOf(9.0, 10.0), sector = sector, volume = volume)

    @Test
    fun buildsStrictGainersAndLosersWithoutOverlap() {
        val snapshot = HomeIntelligenceEngine.build(
            listOf(
                stock("UP", 5.0, "Banking"),
                stock("DOWN", -3.0, "Banking"),
                stock("FLAT", 0.0, "Energy")
            ),
            emptyList()
        )

        assertEquals(listOf("UP"), snapshot.gainers.map { it.symbol })
        assertEquals(listOf("DOWN"), snapshot.losers.map { it.symbol })
        assertEquals(1, snapshot.breadth.unchanged)
        assertTrue(snapshot.evidenceGraph.validationErrors().isEmpty())
        assertTrue(snapshot.evidenceGraph.records.any { it.id == "calculation:market-breadth" })
        assertTrue(snapshot.changes.first { it.id == "breadth" }.source?.source == "MyStocks Africa")
    }

    @Test
    fun sectorPulseIsAnExplicitAverageCalculation() {
        val snapshot = HomeIntelligenceEngine.build(
            listOf(
                stock("A", 4.0, "Banking"),
                stock("B", 2.0, "Banking"),
                stock("C", -1.0, "Energy")
            ),
            emptyList()
        )

        val banking = snapshot.sectors.first { it.sector == "Banking" }
        assertEquals(3.0, banking.averageChangePct, 0.0001)
        assertEquals(2, banking.memberCount)
        assertTrue(snapshot.intelligence.any { it.type == HomeIntelligenceType.CALCULATION })
        val sectorEvidence = snapshot.evidenceGraph.record("calculation:sector-banking")
        assertTrue(sectorEvidence != null)
        assertEquals(2, snapshot.evidenceGraph.relatedTo("calculation:sector-banking").size)
        assertTrue(snapshot.changes.first { it.id == "strongest-sector" }.source != null)
        val sectorItem = snapshot.intelligence.first { it.id == "sector-banking" }
        assertEquals("calculation:sector-banking", sectorItem.evidence.single().id)
        val gainerItem = snapshot.intelligence.first { it.id == "gainer-A" }
        assertEquals("market:a", gainerItem.evidence.single().id)
    }

    @Test
    fun newsIsEvidenceNotAnInventedFinancialConclusion() {
        val news = NewsItem(
            id = "n1", title = "Company announcement", summary = "Management update", body = "",
            source = "Issuer", publishedAt = "2026-09-17", category = "Company",
            symbol = "ABC", companyName = "ABC Holdings", imageUrl = "", url = "https://example.com/news",
            dividendAmount = "", exDate = "", paymentDate = "",
            intelligenceRelevance = "market", intelligenceRelevanceReason = "test market event"
        )

        val snapshot = HomeIntelligenceEngine.build(listOf(stock("ABC", 2.0, "Banking")), listOf(news))
        val item = snapshot.intelligence.first { it.type == HomeIntelligenceType.NEWS }

        assertEquals("Company announcement", item.fact)
        assertTrue(item.interpretation.contains("no financial conclusion"))
        assertEquals("https://example.com/news", item.sourceUrl)
        assertEquals("Issuer", item.evidence.single().source)
        assertEquals("2026-09-17", item.evidence.single().date)
    }
    @Test
    fun indexObservationsArePartOfHomeEvidenceGraph() {
        val snapshot = HomeIntelligenceEngine.build(
            listOf(stock("ABC", 1.0, "Banking")),
            emptyList(),
            listOf(HomeMarketIndex("^NASI", "NSE All-Share Index", 235.26, -0.98, "2026-09-17"))
        )

        assertEquals(1, snapshot.marketIndices.size)
        val evidence = snapshot.evidenceGraph.record("index:^nasi")
        assertTrue(evidence != null)
        assertEquals("MyStocks Africa", evidence?.source)
        assertEquals("2026-09-17", evidence?.observedAt)
    }

    @Test
    fun marketIndexPulseUsesObservedDirectionWithoutPrediction() {
        val snapshot = HomeIntelligenceEngine.build(
            listOf(stock("ABC", 1.0, "Banking")),
            emptyList(),
            listOf(
                HomeMarketIndex("^NASI", "NASI", 235.26, -0.98, "2026-09-17"),
                HomeMarketIndex("^N20I", "NSE 20", 1900.0, -0.50, "2026-09-17")
            )
        )

        val pulse = snapshot.intelligence.first { it.id == "market-index-pulse" }
        assertTrue(pulse.fact.contains("lower"))
        assertTrue(pulse.interpretation.contains("not a forecast"))
        assertEquals(2, pulse.evidence.size)
        assertTrue(snapshot.evidenceGraph.relatedTo("calculation:market-breadth").isNotEmpty())
    }

}
