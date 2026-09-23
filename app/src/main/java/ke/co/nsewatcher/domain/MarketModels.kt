package ke.co.nsewatcher.domain

/**
 * User-owned alert rule models.
 *
 * Market quotes, snapshots, history and news now use the app's current verified
 * provider boundary in MarketData rather than the retired provider-era models
 * that previously lived in this file.
 */
enum class AlertType {
    PRICE_ABOVE,
    PRICE_BELOW,
    DAILY_GAIN,
    DAILY_LOSS,
    HIGH_VOLUME,
    BREAKOUT,
    NEWS,
    CORPORATE_ACTION
}

data class PriceAlert(
    val id: String,
    val symbol: String,
    val type: AlertType,
    val threshold: Double?,
    val enabled: Boolean
)
