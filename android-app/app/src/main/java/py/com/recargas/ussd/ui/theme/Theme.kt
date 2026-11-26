package py.com.recargas.ussd.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import py.com.recargas.ussd.preferences.AccentPalette
import py.com.recargas.ussd.preferences.ThemeMode

val TigoBlue = Color(0xFF00377B)
val ClaroRed = Color(0xFFDA291C)

private fun accentColor(palette: AccentPalette): Color = when (palette) {
    AccentPalette.BLUE -> Color(0xFF1565C0)
    AccentPalette.RED -> Color(0xFFC62828)
    AccentPalette.EMERALD -> Color(0xFF1B7A4B)
    AccentPalette.PURPLE -> Color(0xFF6A1B9A)
    AccentPalette.TEAL -> Color(0xFF00695C)
}

@Composable
fun RecargasTheme(
    themeMode: ThemeMode,
    accentPalette: AccentPalette,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val primary = accentColor(accentPalette)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary.copy(alpha = 0.9f),
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.28f),
            onPrimaryContainer = Color.White,
            secondary = Color(0xFF80CBC4),
            secondaryContainer = primary.copy(alpha = 0.25f),
            onSecondaryContainer = Color.White,
            background = Color(0xFF101318),
            surface = Color(0xFF171C24),
            surfaceVariant = Color(0xFF222936),
            onBackground = Color(0xFFECEFF4),
            onSurface = Color(0xFFECEFF4)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = primary,
            secondary = Color(0xFF00796B),
            secondaryContainer = primary.copy(alpha = 0.14f),
            onSecondaryContainer = primary,
            background = Color(0xFFF6F8FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFEAEEF5),
            onBackground = Color(0xFF111827),
            onSurface = Color(0xFF111827)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
