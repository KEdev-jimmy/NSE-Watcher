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


internal fun shouldCompactBottomNav(
    widthDp: Float,
    fontScale: Float,
    itemCount: Int = 5
): Boolean {
    if (itemCount <= 0) return false
    val perItem = widthDp.coerceAtLeast(0f) / itemCount
    val effectiveScale = fontScale.coerceAtLeast(1f)
    return widthDp < 340f || effectiveScale > 1.30f || perItem < (58f * effectiveScale)
}
