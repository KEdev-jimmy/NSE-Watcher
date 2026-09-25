package ke.co.nsewatcher

internal fun shouldScrollTabs(
    widthDp: Float,
    fontScale: Float,
    optionCount: Int,
    minimumOptionWidthDp: Float = 70f,
    spacingDp: Float = 5f
): Boolean {
    if (optionCount <= 1) return false
    val safeWidth = widthDp.coerceAtLeast(0f)
    val effectiveScale = fontScale.coerceAtLeast(1f)
    val required = (minimumOptionWidthDp * effectiveScale * optionCount) +
        (spacingDp * (optionCount - 1))
    return safeWidth < required
}
