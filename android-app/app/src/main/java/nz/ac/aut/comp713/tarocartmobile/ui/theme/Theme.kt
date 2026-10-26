package nz.ac.aut.comp713.tarocartmobile.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// defines the fallback palette used when the device is in dark mode
private val DarkColorScheme = darkColorScheme(
    primary = GreenDarkPrimary,
    onPrimary = GreenDarkOnPrimary,
    primaryContainer = GreenDarkPrimaryContainer,
    onPrimaryContainer = GreenDarkOnPrimaryContainer,
    secondary = GreenDarkSecondary,
    secondaryContainer = GreenDarkSecondaryContainer,
    onSecondaryContainer = GreenDarkOnSecondaryContainer,
    background = GreenDarkBackground,
    surface = GreenDarkSurface,
    surfaceVariant = GreenDarkSurfaceVariant,
    surfaceContainer = Color(0xFF222A27),
    surfaceContainerLow = Color(0xFF1C2421),
    surfaceContainerHigh = Color(0xFF29312E),
    surfaceContainerHighest = Color(0xFF303935),
    outlineVariant = Color(0xFF4A5550)
)

// defines the branded light palette used throughout the app
private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = GreenPrimaryContainer,
    onPrimaryContainer = GreenOnPrimaryContainer,
    secondary = GreenSecondary,
    secondaryContainer = GreenSecondaryContainer,
    onSecondaryContainer = GreenOnSecondaryContainer,
    tertiary = Color(0xFF3F6374),
    background = GreenBackground,
    surface = GreenSurface,
    surfaceVariant = GreenSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F6F6),
    surfaceContainer = SoftGrey,
    surfaceContainerHigh = Color(0xFFE9ECEB),
    surfaceContainerHighest = Color(0xFFE2E6E4),
    outlineVariant = SoftGreyOutline,
    onSurfaceVariant = Color(0xFF46514E),
    onBackground = Color(0xFF17201D),
    onSurface = Color(0xFF17201D),
    outline = GreenOutline,
    error = Color(0xFFBA1A1A)
)

// applies a consistent rounded hierarchy to cards, fields, dialogs, and buttons
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TaroCartMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // dynamic color is available on android 12 and later
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // prefers supported device colors before falling back to the app palettes
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // supplies consistent colors and typography to every child composable
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
