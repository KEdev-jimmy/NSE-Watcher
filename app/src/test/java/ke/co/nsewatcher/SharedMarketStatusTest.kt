package ke.co.nsewatcher

import ke.co.nsewatcher.data.MyStocksCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMarketStatusTest {
    private val unknown = MyStocksCache.MarketStatus()
    private val open = MyStocksCache.MarketStatus(
        isOpen = true,
        status = "OPEN",
        nextClose = "2026-09-23T15:00:00+03:00",
        isKnown = true
    )
    private val closed = MyStocksCache.MarketStatus(
        isOpen = false,
        status = "CLOSED",
        nextOpen = "2026-09-24T09:30:00+03:00",
        isKnown = true
    )

    @Test fun knownStatusIsNotErasedByTemporaryUnknownRefresh() {
        val result = SharedMarketStatus.preferred(open, unknown)

        assertEquals(open, result)
        assertTrue(result.isKnown)
        assertTrue(result.isOpen)
    }

    @Test fun knownRefreshReplacesPreviousKnownState() {
        val result = SharedMarketStatus.preferred(open, closed)

        assertEquals(closed, result)
        assertTrue(result.isKnown)
        assertFalse(result.isOpen)
    }

    @Test fun unknownRemainsHonestWhenNoKnownStateExists() {
        val result = SharedMarketStatus.preferred(unknown, unknown)

        assertEquals(unknown, result)
        assertFalse(result.isKnown)
    }

    @Test fun firstKnownRefreshReplacesUnknownState() {
        assertEquals(open, SharedMarketStatus.preferred(unknown, open))
    }
}
