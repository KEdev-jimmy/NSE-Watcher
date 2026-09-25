package ke.co.nsewatcher.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementIntelligenceRepositoryTest {
    private fun result(
        symbol: String = "KCB",
        change: String = "+4.0%"
    ) = MovementIntelligenceCache.Result(
        symbol = symbol,
        move = MovementIntelligenceCache.Move(
            periodDays = 3,
            from = "2026-09-22",
            to = "2026-09-25",
            change = change
        ),
        summary = "Dated company evidence was returned."
    )

    @Test
    fun reusesFreshMovementEvidenceForNormalizedSymbol() = runBlocking {
        var calls = 0
        var now = 1_000L
        val repository = MovementIntelligenceRepository(
            loader = {
                calls++
                result()
            },
            nowMs = { now },
            maxAgeMs = 15 * 60 * 1000L,
            maxStaleMs = 2 * 60 * 60 * 1000L
        )

        val first = repository.load("kcb.ke")
        now += 60_000L
        val second = repository.load(" KCB ")

        assertEquals(1, calls)
        assertEquals("LIVE", first.cacheState)
        assertEquals("FRESH_CACHE", second.cacheState)
        assertEquals("+4.0%", second.move?.change)
    }

    @Test
    fun forceRefreshBypassesFreshMovementCache() = runBlocking {
        var calls = 0
        val repository = MovementIntelligenceRepository(
            loader = {
                calls++
                result(change = if (calls == 1) "+4.0%" else "+5.0%")
            }
        )

        assertEquals("+4.0%", repository.load("KCB").move?.change)
        assertEquals(
            "+5.0%",
            repository.load("KCB", forceRefresh = true).move?.change
        )
        assertEquals(2, calls)
    }

    @Test
    fun marketAndCompanyConsumersShareOneInFlightMovementRequest() = runBlocking {
        var calls = 0
        val repository = MovementIntelligenceRepository(
            loader = {
                calls++
                delay(50)
                result()
            }
        )

        val market = async { repository.load("KCB") }
        val company = async { repository.load("KCB.KE") }

        assertEquals("+4.0%", market.await().move?.change)
        assertEquals("+4.0%", company.await().move?.change)
        assertEquals(1, calls)
    }

    @Test
    fun recentMovementCacheBecomesExplicitFallbackWhenLiveRefreshFails() = runBlocking {
        var calls = 0
        var now = 0L
        val repository = MovementIntelligenceRepository(
            loader = {
                calls++
                if (calls == 1) result()
                else MovementIntelligenceCache.Result(error = "backend unavailable")
            },
            nowMs = { now },
            maxAgeMs = 1_000L,
            maxStaleMs = 10_000L
        )

        repository.load("KCB")
        now = 2_000L
        val fallback = repository.load("KCB")

        assertEquals(2, calls)
        assertNull(fallback.error)
        assertEquals("STALE_FALLBACK", fallback.cacheState)
        assertTrue(fallback.partial)
        assertTrue(fallback.cacheMessage.isNotBlank())
        assertEquals("+4.0%", fallback.move?.change)
    }

    @Test
    fun expiredMovementCacheIsNotUsedAsCurrentEvidence() = runBlocking {
        var calls = 0
        var now = 0L
        val repository = MovementIntelligenceRepository(
            loader = {
                calls++
                if (calls == 1) result()
                else MovementIntelligenceCache.Result(error = "backend unavailable")
            },
            nowMs = { now },
            maxAgeMs = 1_000L,
            maxStaleMs = 3_000L
        )

        repository.load("KCB")
        now = 5_000L
        val failed = repository.load("KCB")

        assertEquals(2, calls)
        assertEquals("backend unavailable", failed.error)
        assertFalse(failed.cacheState == "STALE_FALLBACK")
    }
}
