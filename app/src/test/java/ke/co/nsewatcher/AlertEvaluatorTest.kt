package ke.co.nsewatcher

import ke.co.nsewatcher.domain.AlertType
import ke.co.nsewatcher.domain.PriceAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {
    private fun stock(price: Double, change: Double = 0.0, volume: Long = 0L, averageVolume: Long = 0L, averageVolumeAvailable: Boolean = false) = Stock("SCOM", "Safaricom", price, change, emptyList(), volume = volume, averageVolume = averageVolume, averageVolumeAvailable = averageVolumeAvailable)

    @Test fun priceAboveTriggersOnlyOnCrossing() {
        val alert = PriceAlert("a1", "SCOM", AlertType.PRICE_ABOVE, 30.0, true)
        assertTrue(AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.5)), mapOf("SCOM" to 29.9)).isNotEmpty())
        assertEquals(0, AlertEvaluator.evaluate(listOf(alert), listOf(stock(31.0)), mapOf("SCOM" to 30.5)).size)
    }

    @Test fun priceBelowTriggersOnlyOnCrossing() {
        val alert = PriceAlert("a2", "SCOM", AlertType.PRICE_BELOW, 30.0, true)
        assertTrue(AlertEvaluator.evaluate(listOf(alert), listOf(stock(29.5)), mapOf("SCOM" to 30.1)).isNotEmpty())
        assertEquals(0, AlertEvaluator.evaluate(listOf(alert), listOf(stock(29.0)), mapOf("SCOM" to 29.5)).size)
    }

    @Test fun dailyGainUsesProviderChangeOnly() {
        val alert = PriceAlert("a3", "SCOM", AlertType.DAILY_GAIN, 5.0, true)
        assertTrue(AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.0, 5.1)), emptyMap()).isNotEmpty())
    }

    @Test fun newsAlertUsesCurrentDayCompanyNewsAndDeduplicates() {
        val alert = PriceAlert("news1", "SCOM", AlertType.NEWS, null, true)
        val item = NewsItem("n1","Safaricom reports results","","","MyStocks",java.time.Instant.now().toString(),"Company News","SCOM","Safaricom","","","","","","","","WITHIN_WINDOW")
        assertEquals(1, AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.0)), emptyMap(), listOf(item)).size)
        assertEquals(0, AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.0)), emptyMap(), listOf(item), mapOf("news1" to "n1")).size)
    }

    @Test fun corporateActionAlertUsesCorporateActionOrDividendNews() {
        val alert = PriceAlert("ca1", "SCOM", AlertType.CORPORATE_ACTION, null, true)
        val item = NewsItem("n2","Safaricom dividend declaration","","","MyStocks",java.time.Instant.now().toString(),"Dividends","SCOM","Safaricom","","","","","","","","WITHIN_WINDOW")
        assertEquals(1, AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.0)), emptyMap(), listOf(item)).size)
    }

    @Test fun highVolumeUsesProviderAverageAndThreshold() {
        val alert = PriceAlert("a5", "SCOM", AlertType.HIGH_VOLUME, 50.0, true)
        assertTrue(AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.0, volume = 1500L, averageVolume = 1000L, averageVolumeAvailable = true)), emptyMap()).isNotEmpty())
        assertEquals(0, AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.0, volume = 1499L, averageVolume = 1000L, averageVolumeAvailable = true)), emptyMap()).size)
        assertEquals(0, AlertEvaluator.evaluate(listOf(alert), listOf(stock(30.0, volume = 2000L, averageVolume = 0L)), emptyMap()).size)
    }

    @Test fun unsupportedAlertTypesDoNotFabricateSignals() {
        val alert = PriceAlert("a4", "SCOM", AlertType.BREAKOUT, null, true)
        assertEquals(0, AlertEvaluator.evaluate(listOf(alert), listOf(stock(50.0)), emptyMap()).size)
    }
}
