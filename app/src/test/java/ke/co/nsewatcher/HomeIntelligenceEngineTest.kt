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
    }

    @Test
    fun newsIsEvidenceNotAnInventedFinancialConclusion() {
        val news = NewsItem(
            id = "n1", title = "Company announcement", summary = "Management update", body = "",
            source = "Issuer", publishedAt = "2026-09-17", category = "Company",
            symbol = "ABC", companyName = "ABC Holdings", imageUrl = "", url = "https://example.com/news",
            dividendAmount = "", exDate = "", paymentDate = ""
        )

        val snapshot = HomeIntelligenceEngine.build(listOf(stock("ABC", 2.0, "Banking")), listOf(news))
        val item = snapshot.intelligence.first { it.type == HomeIntelligenceType.NEWS }

        assertEquals("Company announcement", item.fact)
        assertTrue(item.interpretation.contains("no financial conclusion"))
        assertEquals("https://example.com/news", item.sourceUrl)
    }
}
