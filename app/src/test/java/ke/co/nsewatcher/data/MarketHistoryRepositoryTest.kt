package ke.co.nsewatcher.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketHistoryRepositoryTest {
    private fun result(price: Double, observedAt: String = "2026-09-23T09:30:00Z") =
        MyStocksCache.HistoryResult(
            prices = listOf(price),
            points = listOf(MyStocksCache.HistoryPoint(close = price, date = observedAt)),
            observedAt = observedAt
        )

    @Test fun reusesFreshProviderHistoryForSameNormalizedSymbolAndRange() = runBlocking {
        var calls = 0
        var now = 1_000L
        val repository = MarketHistoryRepository(
            loader = { _, _ -> calls++; result(50.0) },
            nowMs = { now },
            maxAgeMs = 15 * 60 * 1000L
        )

        val first = repository.load("kcb.ke", "1m")
        now += 60_000L
        val second = repository.load(" KCB ", "1M")

        assertEquals(1, calls)
        assertEquals(first, second)
    }

    @Test fun forceRefreshBypassesFreshCacheAndReplacesObservation() = runBlocking {
        var calls = 0
        val repository = MarketHistoryRepository(
            loader = { _, _ ->
                calls++
                result(if (calls == 1) 50.0 else 52.0)
            }
        )

        assertEquals(50.0, repository.load("KCB", "1M").prices.single(), 0.0001)
        assertEquals(
            52.0,
            repository.load("KCB", "1M", forceRefresh = true).prices.single(),
            0.0001
        )
        assertEquals(2, calls)
        assertEquals(52.0, repository.load("KCB", "1M").prices.single(), 0.0001)
        assertEquals(2, calls)
    }

    @Test fun emptyUnavailableHistoryIsNotHeldForFullCacheWindow() = runBlocking {
        var calls = 0
        val repository = MarketHistoryRepository(
            loader = { _, _ ->
                calls++
                if (calls == 1) MyStocksCache.HistoryResult() else result(48.0)
            }
        )

        assertTrue(repository.load("KCB", "1W").points.isEmpty())
        assertEquals(48.0, repository.load("KCB", "1W").prices.single(), 0.0001)
        assertEquals(2, calls)
    }

    @Test fun normalizesProviderRequestWithoutChangingOneDayConvention() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val repository = MarketHistoryRepository(
            loader = { symbol, period ->
                requests += symbol to period
                result(50.0)
            }
        )

        repository.load("kcb.ke", "1d")
        repository.load("eqty", "3M")

        assertEquals(listOf("KCB" to "1D", "EQTY" to "3m"), requests)
    }

    @Test fun simultaneousConsumersShareOneInFlightProviderRequest() = runBlocking {
        var calls = 0
        val repository = MarketHistoryRepository(
            loader = { _, _ ->
                calls++
                delay(50)
                result(51.0)
            }
        )

        val company = async { repository.load("KCB", "1M") }
        val practice = async { repository.load("KCB.KE", "1m") }

        assertEquals(51.0, company.await().prices.single(), 0.0001)
        assertEquals(51.0, practice.await().prices.single(), 0.0001)
        assertEquals(1, calls)
    }
}
