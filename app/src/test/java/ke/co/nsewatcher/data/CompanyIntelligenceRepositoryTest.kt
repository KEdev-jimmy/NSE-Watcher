package ke.co.nsewatcher.data

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyIntelligenceRepositoryTest {
    private fun result(
        revenue: String = "1000",
        fetchedAt: String = "2026-09-25T10:00:00Z"
    ) = CompanyIntelligenceCache.Result(
        profile = CompanyIntelligenceCache.Profile(
            revenue = revenue,
            financialPeriod = "FY 2025"
        ),
        fetchedAt = fetchedAt
    )

    @Test
    fun reusesFreshCompanyResearchForNormalizedSymbol() = runBlocking {
        var calls = 0
        var now = 1_000L
        val repository = CompanyIntelligenceRepository(
            loader = {
                calls++
                result()
            },
            nowMs = { now },
            maxAgeMs = 30 * 60 * 1000L,
            maxStaleMs = 6 * 60 * 60 * 1000L
        )

        val first = repository.load("kcb.ke")
        now += 60_000L
        val second = repository.load(" KCB ")

        assertEquals(1, calls)
        assertEquals("LIVE", first.cacheState)
        assertEquals("FRESH_CACHE", second.cacheState)
        assertEquals("1000", second.profile.revenue)
    }

    @Test
    fun forceRefreshBypassesFreshCache() = runBlocking {
        var calls = 0
        val repository = CompanyIntelligenceRepository(
            loader = {
                calls++
                result(revenue = if (calls == 1) "1000" else "1200")
            }
        )

        assertEquals("1000", repository.load("KCB").profile.revenue)
        assertEquals(
            "1200",
            repository.load("KCB", forceRefresh = true).profile.revenue
        )
        assertEquals(2, calls)
    }

    @Test
    fun simultaneousConsumersShareOneInFlightRequest() = runBlocking {
        var calls = 0
        val repository = CompanyIntelligenceRepository(
            loader = {
                calls++
                delay(50)
                result()
            }
        )

        val company = async { repository.load("KCB") }
        val comparison = async { repository.load("KCB.KE") }

        assertEquals("1000", company.await().profile.revenue)
        assertEquals("1000", comparison.await().profile.revenue)
        assertEquals(1, calls)
    }

    @Test
    fun recentCacheBecomesExplicitStaleFallbackWhenLiveRefreshFails() = runBlocking {
        var calls = 0
        var now = 0L
        val repository = CompanyIntelligenceRepository(
            loader = {
                calls++
                if (calls == 1) result()
                else CompanyIntelligenceCache.Result(error = "backend unavailable")
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
        assertEquals("1000", fallback.profile.revenue)
    }

    @Test
    fun expiredStaleCacheIsNotUsedAsCurrentResearch() = runBlocking {
        var calls = 0
        var now = 0L
        val repository = CompanyIntelligenceRepository(
            loader = {
                calls++
                if (calls == 1) result()
                else CompanyIntelligenceCache.Result(error = "backend unavailable")
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
