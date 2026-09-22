package ke.co.nsewatcher.domain

/** A condition detected by the worker; this is not proof of notification delivery. */
data class AlertEvent(
    val id: String, val ruleId: String, val symbol: String,
    val title: String, val message: String, val recordedAt: String, val observedAt: String
)

internal fun mergeAlertEvents(existing: List<AlertEvent>, incoming: List<AlertEvent>): List<AlertEvent> =
    (existing + incoming).distinctBy { it.id }.sortedByDescending { it.recordedAt }.take(50)
