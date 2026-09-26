package ke.co.nsewatcher.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalHistoryRepositoryTest {
    private fun result(price: Double) = MyStocksCache.HistoryResult(
        points = listOf(MyStocksCache.HistoryPoint(close = price, date = "2026-09-26")),
        prices = listOf(price),
        interval = "1d",
        purpose = "technical-analysis"
    )

    @Test fun reusesFreshDailyTechnicalHistoryForSameSymbolAndLookback() = runBlocking {
        var calls = 0
        var now = 1_000L
        val repository = TechnicalHistoryRepository(
            loader = { _, _ -> calls++; result(50.0) },
            nowMs = { now },
            maxAgeMs = 15 * 60 * 1000L
        )

        val first = repository.load("kcb.ke", 400)
        now += 60_000L
        val second = repository.load(" KCB ", 400)

        assertEquals(1, calls)
        assertEquals(first, second)
    }

    @Test fun lookbackIsBoundedBeforeProviderCall() = runBlocking {
        val requests = mutableListOf<Int>()
        val repository = TechnicalHistoryRepository(
            loader = { _, days ->
                requests += days
                result(50.0)
            }
        )

        repository.load("KCB", 10)
        repository.load("EQTY", 5_000)

        assertEquals(listOf(260, 730), requests)
    }

    @Test fun forceRefreshBypassesFreshTechnicalCache() = runBlocking {
        var calls = 0
        val repository = TechnicalHistoryRepository(
            loader = { _, _ ->
                calls++
                result(if (calls == 1) 50.0 else 52.0)
            }
        )

        assertEquals(50.0, repository.load("KCB").prices.single(), 0.0001)
        assertEquals(52.0, repository.load("KCB", forceRefresh = true).prices.single(), 0.0001)
        assertEquals(2, calls)
    }

    @Test fun unusableTechnicalHistoryIsNotHeldForFullCacheWindow() = runBlocking {
        var calls = 0
        val repository = TechnicalHistoryRepository(
            loader = { _, _ ->
                calls++
                if (calls == 1) MyStocksCache.HistoryResult(interval = "1d")
                else result(48.0)
            }
        )

        assertTrue(repository.load("KCB").points.isEmpty())
        assertEquals(48.0, repository.load("KCB").prices.single(), 0.0001)
        assertEquals(2, calls)
    }

    @Test fun simultaneousConsumersShareOneProviderRequest() = runBlocking {
        var calls = 0
        val repository = TechnicalHistoryRepository(
            loader = { _, _ ->
                calls++
                delay(50)
                result(51.0)
            }
        )

        val first = async { repository.load("SCOM") }
        val second = async { repository.load("SCOM.KE") }

        assertEquals(51.0, first.await().prices.single(), 0.0001)
        assertEquals(51.0, second.await().prices.single(), 0.0001)
        assertEquals(1, calls)
    }
}
