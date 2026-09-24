package ke.co.nsewatcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MarketAttentionEngineTest {
    private val now = Instant.parse("2026-09-24T12:00:00Z")

    private fun stock(
        symbol: String,
        change: Double,
        sector: String,
        observedAt: String = "2026-09-24T11:00:00Z",
        volume: Long = 0L,
        averageVolume: Long = 0L,
        averageVolumeAvailable: Boolean = false
    ) = Stock(
        symbol = symbol,
        name = symbol,
        price = 50.0,
        change = change,
        history = emptyList(),
        sector = sector,
        observedAt = observedAt,
        volume = volume,
        volumeAvailable = volume > 0L,
        averageVolume = averageVolume,
        averageVolumeAvailable = averageVolumeAvailable
    )

    private fun news(id: String, symbol: String, publishedAt: String) = NewsItem(
        id = id,
        title = "Company update " + id,
        summary = "Published company evidence",
        body = "",
        source = "Issuer",
        publishedAt = publishedAt,
        category = "Company News",
        symbol = symbol,
        companyName = symbol,
        imageUrl = "",
        url = "https://example.com/" + id,
        dividendAmount = "",
        exDate = "",
        paymentDate = ""
    )

    @Test fun attentionUsesOnlyLatestComparableObservationDate() {
        val rows = listOf(
            stock("KCB", 3.0, "Banking"),
            stock("EQTY", 0.5, "Banking"),
            stock("ABSA", -0.5, "Banking"),
            stock("OLD", 12.0, "Banking", observedAt = "2026-09-23T11:00:00Z")
        )

        val attention = MarketAttentionEngine.rank(rows, emptyList(), now, limit = 10)

        assertTrue(attention.any { it.stock.symbol == "KCB" })
        assertFalse(attention.any { it.stock.symbol == "OLD" })
    }

    @Test fun staleAndFutureQuotesCannotBecomeAttentionItems() {
        val rows = listOf(
            stock("KCB", 5.0, "Banking", observedAt = "2026-09-24T11:00:00Z"),
            stock("EQTY", 0.2, "Banking", observedAt = "2026-09-24T11:00:00Z"),
            stock("ABSA", -0.2, "Banking", observedAt = "2026-09-24T11:00:00Z"),
            stock("FUT", 20.0, "Banking", observedAt = "2026-09-25T11:00:00Z"),
            stock("OLD", 20.0, "Banking", observedAt = "2026-09-18T11:00:00Z")
        )

        val attention = MarketAttentionEngine.rank(rows, emptyList(), now, limit = 10)

        assertTrue(attention.any { it.stock.symbol == "KCB" })
        assertFalse(attention.any { it.stock.symbol == "FUT" })
        assertFalse(attention.any { it.stock.symbol == "OLD" })
    }

    @Test fun sectorDivergenceVolumeAndFreshEvidenceIncreaseAttentionTransparently() {
        val kcb = stock(
            "KCB",
            4.2,
            "Banking",
            volume = 2_500_000,
            averageVolume = 1_000_000,
            averageVolumeAvailable = true
        )
        val rows = listOf(
            kcb,
            stock("EQTY", 1.0, "Banking"),
            stock("ABSA", 1.5, "Banking"),
            stock("SCOM", 0.2, "Telecommunications"),
            stock("EABL", -0.2, "Manufacturing"),
            stock("BAT", 0.1, "Manufacturing")
        )

        val attention = MarketAttentionEngine.rank(
            rows,
            listOf(news("kcb-results", "KCB", "2026-09-24T09:00:00Z")),
            now,
            limit = 3
        )

        val item = attention.first { it.stock.symbol == "KCB" }
        assertTrue(item.score >= 8)
        assertTrue(item.reasons.any { it.title == "Large daily move" })
        assertTrue(item.reasons.any { it.title == "Diverging from sector peers" })
        assertTrue(item.reasons.any { it.title == "Unusually high reported volume" })
        assertNotNull(item.latestEvidence)
    }

    @Test fun oneSmallOrdinaryMoveDoesNotBecomeAttentionItem() {
        val rows = listOf(
            stock("KCB", 0.8, "Banking"),
            stock("EQTY", 0.7, "Banking"),
            stock("ABSA", 0.9, "Banking")
        )

        val attention = MarketAttentionEngine.rank(rows, emptyList(), now)

        assertTrue(attention.isEmpty())
    }

    @Test fun recentCompanyEvidenceCanLiftAModerateMoveIntoTheShortlistWithoutClaimingCause() {
        val rows = listOf(
            stock("KCB", 1.5, "Banking"),
            stock("EQTY", 1.4, "Banking"),
            stock("ABSA", 1.6, "Banking")
        )
        val evidence = news("kcb-update", "KCB", "2026-09-24T10:00:00Z")

        val attention = MarketAttentionEngine.rank(rows, listOf(evidence), now, limit = 10)

        val item = attention.single { it.stock.symbol == "KCB" }
        assertEquals("kcb-update", item.latestEvidence?.id)
        assertTrue(item.reasons.any { it.title == "Fresh company evidence available" })
        assertTrue(item.reasons.first { it.title == "Fresh company evidence available" }
            .detail.contains("does not prove"))
    }

    @Test fun companyAnalysisAndAttentionShareTheSamePeerContext() {
        val target = stock("KCB", 3.0, "Banks")
        val rows = listOf(
            target,
            stock("EQTY", 2.0, "Banking"),
            stock("ABSA", 4.0, "Banks")
        )

        val shared = MarketMovementContextPresentation.context(target, rows)
        val company = CompanyAnalysisPresentation.movementContext(target, rows)

        assertEquals(shared, company)
        assertEquals(3.0, shared.sectorAverage ?: Double.NaN, 0.0001)
        assertEquals(2, shared.sectorCount)
    }
}
