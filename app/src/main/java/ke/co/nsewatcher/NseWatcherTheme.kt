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
    background = Color(0xFFFAF9F2),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFF2F7F1),
    border = Color(0xFFE1E8DF),
    text = Color(0xFF0B2B31),
    muted = Color(0xFF6D7D77),
    primary = Color(0xFF008D5D),
    secondary = Color(0xFF117A9B),
    danger = Color(0xFFE23F50),
    amber = Color(0xFFF0B531),
    nav = Color(0xFFFFFEFA)
)

private val NseWatcherDarkPalette = PremiumHomePalette(
    background = Color(0xFF031326),
    surface = Color(0xFF071D35),
    raised = Color(0xFF0B2743),
    border = Color(0xFF173E61),
    text = Color(0xFFF7FAFF),
    muted = Color(0xFFA7B8CB),
    primary = Color(0xFF20F0C1),
    secondary = Color(0xFF27B6FF),
    danger = Color(0xFFFF526B),
    amber = Color(0xFFF6C65B),
    nav = Color(0xFF061A31)
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
