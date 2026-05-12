package ke.co.bingwa.companion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CompanionDarkColors = darkColorScheme(
    primary = Color(0xFF56E39F),
    onPrimary = Color(0xFF082316),
    primaryContainer = Color(0xFF0E3B28),
    onPrimaryContainer = Color(0xFFB9FFD8),
    secondary = Color(0xFF7ED7FF),
    onSecondary = Color(0xFF082535),
    secondaryContainer = Color(0xFF14384A),
    onSecondaryContainer = Color(0xFFC6EEFF),
    tertiary = Color(0xFFFFC96B),
    onTertiary = Color(0xFF3A2600),
    background = Color(0xFF08110F),
    onBackground = Color(0xFFE8F6F0),
    surface = Color(0xFF101B18),
    onSurface = Color(0xFFE8F6F0),
    surfaceVariant = Color(0xFF172623),
    onSurfaceVariant = Color(0xFFB7CAC2),
    outline = Color(0xFF3A524B),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF3C0003),
)

@Composable
fun BingwaCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        CompanionDarkColors
    } else {
        CompanionDarkColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
