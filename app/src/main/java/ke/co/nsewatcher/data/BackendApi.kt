package ke.co.nsewatcher.data

import retrofit2.http.GET
import retrofit2.http.Path

/** Calls must point to your own authenticated backend; never put a provider key in this app. */
interface BackendApi {
    @GET("market/status") suspend fun marketStatus(): Any
    @GET("market/summary") suspend fun marketSummary(): Any
    @GET("stocks") suspend fun stocks(): Any
    @GET("stocks/{symbol}/quote") suspend fun quote(@Path("symbol") symbol: String): Any
    @GET("stocks/{symbol}/candles") suspend fun candles(@Path("symbol") symbol: String): Any
    @GET("stocks/{symbol}/news") suspend fun news(@Path("symbol") symbol: String): Any
    @GET("stocks/{symbol}/corporate-actions") suspend fun corporateActions(@Path("symbol") symbol: String): Any
    @GET("movers") suspend fun movers(): Any
}
