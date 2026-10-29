package ke.co.nsewatcher

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

internal data class PremiumHomePalette(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val secondary: Color,
    val danger: Color,
    val amber: Color,
    val nav: Color
)

private val NseWatcherLightPalette = PremiumHomePalette(
    background = Color(0xFFF7FAF6),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFEDF6F1),
    border = Color(0xFFE2EDE7),
    text = Color(0xFF102F3B),
    muted = Color(0xFF667F88),
    primary = Color(0xFF008965),
    secondary = Color(0xFF078F97),
    danger = Color(0xFFD84053),
    amber = Color(0xFFF7B500),
    nav = Color(0xFFFFFFFF)
)

private val NseWatcherDarkPalette = PremiumHomePalette(
    background = Color(0xFF061C2B),
    surface = Color(0xFF0A2738),
    raised = Color(0xFF103347),
    border = Color(0xFF24495A),
    text = Color(0xFFF4FBFA),
    muted = Color(0xFFAFC4CC),
    primary = Color(0xFF16D9AD),
    secondary = Color(0xFF29A8E5),
    danger = Color(0xFFFF6079),
    amber = Color(0xFFF5BD61),
    nav = Color(0xFF071D2B)
)

internal fun premiumHomePalette(dark: Boolean): PremiumHomePalette =
    if (dark) NseWatcherDarkPalette else NseWatcherLightPalette

private val NseWatcherLightColors = lightColorScheme(
    primary = NseWatcherLightPalette.primary,
    onPrimary = Color.White,
    primaryContainer = NseWatcherLightPalette.raised,
    onPrimaryContainer = NseWatcherLightPalette.text,
    secondary = NseWatcherLightPalette.secondary,
    onSecondary = Color.White,
    secondaryContainer = NseWatcherLightPalette.raised,
    onSecondaryContainer = NseWatcherLightPalette.text,
    background = NseWatcherLightPalette.background,
    onBackground = NseWatcherLightPalette.text,
    surface = NseWatcherLightPalette.surface,
    onSurface = NseWatcherLightPalette.text,
    surfaceVariant = NseWatcherLightPalette.raised,
    onSurfaceVariant = NseWatcherLightPalette.muted,
    outline = NseWatcherLightPalette.border,
    error = NseWatcherLightPalette.danger,
    onError = Color.White,
    tertiary = NseWatcherLightPalette.secondary,
    onTertiary = Color.White,
    tertiaryContainer = NseWatcherLightPalette.raised,
    onTertiaryContainer = NseWatcherLightPalette.text
)

private val NseWatcherDarkColors = darkColorScheme(
    primary = NseWatcherDarkPalette.primary,
    onPrimary = NseWatcherDarkPalette.background,
    primaryContainer = NseWatcherDarkPalette.raised,
    onPrimaryContainer = NseWatcherDarkPalette.text,
    secondary = NseWatcherDarkPalette.secondary,
    onSecondary = NseWatcherDarkPalette.background,
    secondaryContainer = NseWatcherDarkPalette.raised,
    onSecondaryContainer = NseWatcherDarkPalette.text,
    background = NseWatcherDarkPalette.background,
    onBackground = NseWatcherDarkPalette.text,
    surface = NseWatcherDarkPalette.surface,
    onSurface = NseWatcherDarkPalette.text,
    surfaceVariant = NseWatcherDarkPalette.raised,
    onSurfaceVariant = NseWatcherDarkPalette.muted,
    outline = NseWatcherDarkPalette.border,
    error = NseWatcherDarkPalette.danger,
    onError = NseWatcherDarkPalette.background,
    tertiary = NseWatcherDarkPalette.secondary,
    onTertiary = NseWatcherDarkPalette.background,
    tertiaryContainer = NseWatcherDarkPalette.raised,
    onTertiaryContainer = NseWatcherDarkPalette.text
)

@Composable
internal fun NseWatcherTheme(
    darkTheme: Boolean,
    fontScaleMultiplier: Float = 1f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val adjustedDensity = Density(
        density = density.density,
        fontScale = density.fontScale * fontScaleMultiplier.coerceIn(0.85f, 1.20f)
    )
    val palette = premiumHomePalette(darkTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = palette.background.toArgb()
            window.navigationBarColor = palette.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
        MaterialTheme(
            colorScheme = if (darkTheme) NseWatcherDarkColors else NseWatcherLightColors,
            content = content
        )
    }
}
