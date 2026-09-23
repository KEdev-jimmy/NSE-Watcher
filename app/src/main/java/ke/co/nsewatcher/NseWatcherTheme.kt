package ke.co.nsewatcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NseWatcherLightColors = lightColorScheme(
    primary = Color(0xFF008F5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF5E8),
    onPrimaryContainer = Color(0xFF083C27),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF12231B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF12231B),
    surfaceVariant = Color(0xFFEDF3EF),
    onSurfaceVariant = Color(0xFF5F6F66),
    outline = Color(0xFFD7E2DC),
    error = Color(0xFFC93B45),
    onError = Color.White,
    tertiary = Color(0xFF0A6FA4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9EFFB),
    onTertiaryContainer = Color(0xFF123447)
)

private val NseWatcherDarkColors = darkColorScheme(
    primary = Color(0xFF00D084),
    onPrimary = Color(0xFF061625),
    primaryContainer = Color(0xFF123B2C),
    onPrimaryContainer = Color(0xFFB9F6D7),
    background = Color(0xFF061625),
    onBackground = Color(0xFFF4F7FA),
    surface = Color(0xFF0A1F32),
    onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF10283D),
    onSurfaceVariant = Color(0xFFA9BCD0),
    outline = Color(0xFF17364F),
    error = Color(0xFFFF6971),
    onError = Color(0xFF3B0710),
    tertiary = Color(0xFF75C5FF),
    onTertiary = Color(0xFF06233A),
    tertiaryContainer = Color(0xFF143E4E),
    onTertiaryContainer = Color(0xFFD9F0FF)
)

@Composable
internal fun NseWatcherTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NseWatcherDarkColors else NseWatcherLightColors,
        content = content
    )
}
