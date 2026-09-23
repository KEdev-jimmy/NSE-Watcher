package ke.co.nsewatcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ke.co.nsewatcher.data.MyStocksCache
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

internal object PracticeBackground {
    fun shouldCheck(state: PracticeState): Boolean =
        state.enabled && state.orders.any { it.status == "PENDING" }

    fun evaluate(
        state: PracticeState,
        quotes: List<Stock>,
        marketOpen: Boolean,
        marketKnown: Boolean,
        now: Long
    ): PracticeState = PracticeEngine.snapshot(
        PracticeEngine.observe(
            PracticeEngine.evaluate(state, quotes, marketOpen, marketKnown, now),
            quotes,
            now
        ),
        now
    )
}

class PracticeOrderWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val store = PracticeStore(applicationContext)
        return try {
            val state = store.read()
            if (!PracticeBackground.shouldCheck(state)) return Result.success()

            val status = MyStocksCache.loadMarketStatus()
            if (!status.isKnown) return Result.retry()

            if (!status.isOpen) {
                val now = System.currentTimeMillis()
                store.update { current ->
                    if (!PracticeBackground.shouldCheck(current)) current
                    else PracticeBackground.evaluate(current, emptyList(), marketOpen = false, marketKnown = true, now = now)
                }
                return Result.success()
            }

            val quotes = MyStocksCache.loadStocks()
            if (quotes.isEmpty()) return Result.retry()

            val now = System.currentTimeMillis()
            store.update { current ->
                if (!PracticeBackground.shouldCheck(current)) current
                else PracticeBackground.evaluate(current, quotes, marketOpen = true, marketKnown = true, now = now)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "nse_watcher_practice_orders"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PracticeOrderWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
