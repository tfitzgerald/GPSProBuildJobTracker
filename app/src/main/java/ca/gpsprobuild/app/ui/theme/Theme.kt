package ca.gpsprobuild.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import ca.gpsprobuild.app.data.prefs.ThemeMode
import ca.gpsprobuild.app.domain.model.PrivacyMode
import ca.gpsprobuild.app.ui.components.LocalPrivacyMode

/**
 * Dynamic colour is deliberately not used. This is a business identity that shows
 * up on quotes and invoices as well as on screen, and it should not change because
 * someone set a purple wallpaper.
 */
private val LightScheme = lightColorScheme(
    primary = IronworkNavy,
    onPrimary = Color.White,
    primaryContainer = IronworkNavyContainer,
    onPrimaryContainer = IronworkNavy,
    secondary = LevelAmber,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = Color(0xFF4A2D00),
    tertiary = BlueprintTeal,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceContainerLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFC3C7CF),
    error = ErrorLight,
    onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = IronworkNavyLight,
    onPrimary = OnNavyDark,
    primaryContainer = IronworkNavyContainerDark,
    onPrimaryContainer = IronworkNavyContainer,
    secondary = LevelAmberLight,
    onSecondary = Color(0xFF422C00),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberContainer,
    tertiary = BlueprintTealLight,
    onTertiary = Color(0xFF003733),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF43474E),
    error = ErrorDark,
    onError = Color(0xFF690005)
)

@Composable
fun GpsProBuildTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    privacyMode: PrivacyMode = PrivacyMode.FULL,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatusColors else LightStatusColors,
        LocalPrivacyMode provides privacyMode
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = GpsTypography,
            shapes = GpsShapes,
            content = content
        )
    }
}
