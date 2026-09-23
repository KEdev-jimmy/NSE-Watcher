package ke.co.nsewatcher

import ke.co.nsewatcher.data.AnalystCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyAnalysisPresentationTest {
    private fun stock(symbol: String, change: Double, sector: String, at: String = "2026-09-23T08:00:00Z") =
        Stock(symbol, symbol, 10.0, change, emptyList(), sector = sector, observedAt = at)

    @Test fun movementContextUsesOnlySameDatePeers() {
        val result = CompanyAnalysisPresentation.movementContext(
            stock("KCB", 3.0, "Banks"),
            listOf(
                stock("KCB", 3.0, "Banks"),
                stock("EQTY", 2.0, "Banking"),
                stock("ABSA", 4.0, "Banks"),
                stock("OLD", -9.0, "Banks", "2026-09-22T08:00:00Z")
            )
        )

        assertEquals(3.0, result.sectorAverage ?: Double.NaN, 0.0001)
        assertEquals(2, result.sectorCount)
        assertTrue(result.interpretation.contains("same direction"))
        assertTrue(result.limitation.contains("not official NSE"))
    }

    @Test fun movementContextDoesNotCompareUndatedCompanyObservation() {
        val result = CompanyAnalysisPresentation.movementContext(
            stock("KCB", 3.0, "Banks", ""),
            listOf(stock("EQTY", 2.0, "Banks"))
        )

        assertNull(result.sectorAverage)
        assertEquals(0, result.sectorCount)
        assertTrue(result.interpretation.contains("session date"))
    }

    @Test fun analystViewRequiresEverySignalToReferenceReturnedEvidence() {
        val analysis = AnalystCache.Analysis(
            headline = "Evidence summary",
            signals = listOf(
                AnalystCache.Signal("FACT", "Revenue", "Revenue changed.", listOf("E1"))
            )
        )
        val valid = AnalystCache.Result(
            analysis = analysis,
            evidence = listOf(AnalystCache.Evidence(id = "E1", claim = "Revenue", source = "Issuer")),
            model = "gemini"
        )
        val invalid = valid.copy(
            analysis = analysis.copy(
                signals = listOf(AnalystCache.Signal("FACT", "Revenue", "Revenue changed.", listOf("E999")))
            )
        )

        assertEquals("gemini", CompanyAnalysisPresentation.verifiedAnalyst(valid)?.model)
        assertNull(CompanyAnalysisPresentation.verifiedAnalyst(invalid))
    }
}
