package ke.co.nsewatcher.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalHistoryBackendFallbackTest {
    private fun usable(price: Double = 50.0) = MyStocksCache.HistoryResult(
        points = listOf(
            MyStocksCache.HistoryPoint(
                close = price,
                date = "2026-09-26",
                high = price + 1.0,
                low = price - 1.0
            )
        ),
        prices = listOf(price),
        interval = "1d",
        purpose = "technical-analysis"
    )

    @Test fun primaryWorkerResultIsUsedWithoutCallingFallback() = runBlocking {
        val urls = mutableListOf<String>()

        val result = MyStocksCache.loadTechnicalHistoryWithFallback(
            qualifiedSymbol = "SCOM.KE",
            lookbackDays = 400
        ) { url ->
            urls += url
            usable(52.0)
        }

        assertEquals(52.0, result.prices.single(), 0.0001)
        assertEquals(1, urls.size)
        assertTrue(urls.single().contains("workers.dev"))
        assertTrue(urls.single().contains("lookbackDays=400"))
    }

    @Test fun emptyOrOldWorkerContractFallsBackToVercel() = runBlocking {
        val urls = mutableListOf<String>()

        val result = MyStocksCache.loadTechnicalHistoryWithFallback(
            qualifiedSymbol = "KCB.KE",
            lookbackDays = 400
        ) { url ->
            urls += url
            if (url.contains("workers.dev")) {
                MyStocksCache.HistoryResult(
                    points = listOf(MyStocksCache.HistoryPoint(close = 45.0)),
                    interval = "1w",
                    purpose = ""
                )
            } else {
                usable(47.0)
            }
        }

        assertEquals(47.0, result.prices.single(), 0.0001)
        assertEquals(2, urls.size)
        assertTrue(urls[0].contains("workers.dev"))
        assertTrue(urls[1].contains("vercel.app"))
    }

    @Test fun bothUnavailableReturnsEmptyInsteadOfGuessing() = runBlocking {
        var calls = 0

        val result = MyStocksCache.loadTechnicalHistoryWithFallback(
            qualifiedSymbol = "EQTY.KE",
            lookbackDays = 400
        ) {
            calls++
            MyStocksCache.HistoryResult()
        }

        assertEquals(2, calls)
        assertTrue(result.points.isEmpty())
        assertTrue(result.prices.isEmpty())
    }

    @Test fun fallbackBoundsLookbackAndEncodesSymbol() = runBlocking {
        val urls = mutableListOf<String>()

        MyStocksCache.loadTechnicalHistoryWithFallback(
            qualifiedSymbol = "TEST SYMBOL.KE",
            lookbackDays = 10
        ) { url ->
            urls += url
            if (urls.size == 1) MyStocksCache.HistoryResult() else usable()
        }

        assertEquals(2, urls.size)
        urls.forEach { url ->
            assertTrue(url.contains("lookbackDays=260"))
            assertTrue(url.contains("symbol=TEST+SYMBOL.KE"))
        }
    }

    @Test fun technicalHistoryRequiresDailyPurposeContract() {
        assertTrue(MyStocksCache.usableTechnicalHistory(usable()))
        assertTrue(
            !MyStocksCache.usableTechnicalHistory(
                usable().copy(interval = "1w")
            )
        )
        assertTrue(
            !MyStocksCache.usableTechnicalHistory(
                usable().copy(purpose = "")
            )
        )
    }
}
