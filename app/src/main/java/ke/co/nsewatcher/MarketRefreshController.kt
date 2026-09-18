package ke.co.nsewatcher

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.TimeUnit

object MarketRefreshController {
    const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
    data class State(
        val lastSuccessfulRefreshMs: Long? = null,
        val refreshInProgress: Boolean = false,
        val lastRefreshFailed: Boolean = false
    )
    val state: MutableState<State> = mutableStateOf(State())
    fun markStarted() { state.value = state.value.copy(refreshInProgress = true, lastRefreshFailed = false) }
    fun markSucceeded() { state.value = State(System.currentTimeMillis(), false, false) }
    fun markFailed() { state.value = state.value.copy(refreshInProgress = false, lastRefreshFailed = true) }
    fun secondsUntilNextCheck(nowMs: Long = System.currentTimeMillis()): Long? {
        val last = state.value.lastSuccessfulRefreshMs ?: return null
        return ((last + REFRESH_INTERVAL_MS - nowMs).coerceAtLeast(0L)) / 1000L
    }
    fun formatCountdown(seconds: Long?): String {
        if (seconds == null) return "Waiting for first refresh"
        val minutes = TimeUnit.SECONDS.toMinutes(seconds)
        val remaining = seconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, remaining)
    }
}
