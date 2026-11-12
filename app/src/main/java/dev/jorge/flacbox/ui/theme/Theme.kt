package dev.jorge.flacbox.ui.theme

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

private val Green = Color(0xFF8FD6C0)
private val GreenDark = Color(0xFF2F6F5C)
private val Ink = Color(0xFF0E1512)
private val InkSoft = Color(0xFF17211D)
private val InkCard = Color(0xFF1E2A25)
private val Paper = Color(0xFFE7F0EB)

private val DarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF06251C),
    primaryContainer = GreenDark,
    onPrimaryContainer = Color(0xFFD5F5EA),
    secondary = Color(0xFF9FB4AC),
    onSecondary = Ink,
    secondaryContainer = InkCard,
    onSecondaryContainer = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = InkSoft,
    onSurfaceVariant = Color(0xFFB4C4BE),
    surfaceContainer = InkSoft,
    surfaceContainerHigh = InkCard,
    surfaceContainerHighest = Color(0xFF24312B),
    outline = Color(0xFF3B4B45),
    outlineVariant = Color(0xFF28352F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF17614D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F0D8),
    onPrimaryContainer = Color(0xFF002118),
    secondary = Color(0xFF4C635B),
    background = Color(0xFFF5FBF7),
    surface = Color(0xFFF5FBF7),
    surfaceVariant = Color(0xFFDBE5DF),
)

@Composable
fun FlacBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
