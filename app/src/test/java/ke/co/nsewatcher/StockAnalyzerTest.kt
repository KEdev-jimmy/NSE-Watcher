package ke.co.nsewatcher

import ke.co.nsewatcher.domain.Signal
import ke.co.nsewatcher.domain.StockAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class StockAnalyzerTest {
    @Test fun `percentage calculation handles gain and zero baseline`() { assertEquals(10.0, StockAnalyzer.percentageChange(110.0, 100.0), 0.001); assertEquals(0.0, StockAnalyzer.percentageChange(1.0, 0.0), 0.001) }
    @Test fun `signal rewards broad positive performance and high volume`() { assertEquals(Signal.STRONG, StockAnalyzer.signal(2.0, 3.0, 4.0, 200, 100)) }
    @Test fun `signal identifies sustained weakness`() { assertEquals(Signal.WEAK, StockAnalyzer.signal(-3.0, -3.0, -2.0, 100, 100)) }
}
