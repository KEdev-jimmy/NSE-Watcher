package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketBreadthStrengthTest {
    private fun sector(name: String, average: Double?, covered: Int = 5): MarketSector =
        MarketSector(
            name = name,
            breadth = MarketBreadth(
                rising = covered,
                flat = 0,
                falling = 0,
                total = covered
            ),
            average = average
        )

    @Test fun broadAdvancingMarketWithPositiveSectorsProducesBroadStrength() {
        val result = MarketBreadthStrengthEngine.calculate(
            breadth = MarketBreadth(rising = 35, flat = 5, falling = 10, total = 50),
            sectors = listOf(
                sector("Banking", 1.2),
                sector("Telecom", 0.8),
                sector("Energy", 0.4),
                sector("Insurance", 0.3),
                sector("Manufacturing", 0.1)
            )
        )

        assertEquals(83, result.score)
        assertEquals(MarketBreadthStrengthLabel.BROAD_STRENGTH, result.label)
        assertEquals(75, result.breadthScore)
        assertEquals(100, result.sectorParticipationScore)
        assertEquals(100, result.confidenceScore)
        assertTrue(result.dataSufficient)
    }

    @Test fun broadDecliningMarketProducesBroadWeakness() {
        val result = MarketBreadthStrengthEngine.calculate(
            breadth = MarketBreadth(rising = 10, flat = 5, falling = 35, total = 50),
            sectors = listOf(
                sector("Banking", -1.2),
                sector("Telecom", -0.8),
                sector("Energy", -0.4),
                sector("Insurance", -0.3),
                sector("Manufacturing", -0.1)
            )
        )

        assertEquals(18, result.score)
        assertEquals(MarketBreadthStrengthLabel.BROAD_WEAKNESS, result.label)
        assertEquals(25, result.breadthScore)
        assertEquals(0, result.sectorParticipationScore)
    }

    @Test fun balancedBreadthAndSectorsStayMixed() {
        val result = MarketBreadthStrengthEngine.calculate(
            breadth = MarketBreadth(rising = 20, flat = 10, falling = 20, total = 50),
            sectors = listOf(
                sector("Banking", 0.5),
                sector("Telecom", -0.5),
                sector("Energy", 0.0),
                sector("Insurance", 0.2),
                sector("Manufacturing", -0.2)
            )
        )

        assertEquals(50, result.score)
        assertEquals(MarketBreadthStrengthLabel.MIXED, result.label)
        assertEquals(50, result.breadthScore)
        assertEquals(50, result.sectorParticipationScore)
    }

    @Test fun lowCompanyCoverageWithholdsMarketScore() {
        val result = MarketBreadthStrengthEngine.calculate(
            breadth = MarketBreadth(rising = 7, flat = 1, falling = 2, total = 50),
            sectors = listOf(
                sector("Banking", 1.0),
                sector("Telecom", 0.5)
            )
        )

        assertNull(result.score)
        assertEquals(MarketBreadthStrengthLabel.INSUFFICIENT_DATA, result.label)
        assertTrue(!result.dataSufficient)
        assertTrue(result.cautions.any { it.contains("confidence is reduced") })
    }

    @Test fun confidenceReflectsCoverageAndSectorAvailabilityRatherThanDirection() {
        val positive = MarketBreadthStrengthEngine.calculate(
            breadth = MarketBreadth(rising = 25, flat = 0, falling = 5, total = 50),
            sectors = listOf(
                sector("Banking", 1.0),
                sector("Telecom", 0.5),
                sector("Energy", 0.2),
                sector("Insurance", 0.1)
            )
        )
        val negative = MarketBreadthStrengthEngine.calculate(
            breadth = MarketBreadth(rising = 5, flat = 0, falling = 25, total = 50),
            sectors = listOf(
                sector("Banking", -1.0),
                sector("Telecom", -0.5),
                sector("Energy", -0.2),
                sector("Insurance", -0.1)
            )
        )

        assertEquals(65, positive.confidenceScore)
        assertEquals(positive.confidenceScore, negative.confidenceScore)
        assertTrue(positive.score!! > 50)
        assertTrue(negative.score!! < 50)
    }

    @Test fun missingSectorAveragesStillAllowsBreadthOnlyScoreWhenCompanyCoverageIsGood() {
        val result = MarketBreadthStrengthEngine.calculate(
            breadth = MarketBreadth(rising = 30, flat = 10, falling = 10, total = 50),
            sectors = listOf(
                sector("Banking", null),
                sector("Telecom", null)
            )
        )

        assertEquals(70, result.score)
        assertEquals(70, result.breadthScore)
        assertNull(result.sectorParticipationScore)
        assertTrue(result.dataSufficient)
        assertTrue(result.cautions.any { it.contains("fewer than three sectors") })
    }
}
