package nz.ac.aut.comp713.tarocartmobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PortalColorScheme = lightColorScheme(
    primary = PortalGreen,
    onPrimary = PortalSurface,
    primaryContainer = PortalPaleGreen,
    onPrimaryContainer = PortalGreenDark,
    secondary = PortalGreenDark,
    onSecondary = PortalSurface,
    secondaryContainer = PortalPaleGreen,
    onSecondaryContainer = PortalGreenDark,
    background = PortalBackground,
    onBackground = PortalInk,
    surface = PortalSurface,
    onSurface = PortalInk,
    surfaceVariant = PortalBackground,
    onSurfaceVariant = PortalMuted,
    surfaceContainerLowest = PortalSurface,
    surfaceContainerLow = PortalSurface,
    surfaceContainer = PortalBackground,
    surfaceContainerHigh = Color(0xFFEDF3F1),
    surfaceContainerHighest = PortalPaleGreen,
    outline = PortalLine,
    outlineVariant = PortalLine,
    error = PortalError,
    onError = PortalSurface,
    errorContainer = PortalErrorBackground,
    onErrorContainer = PortalError
)

private val PortalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TaroCartMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PortalColorScheme,
        typography = Typography,
        shapes = PortalShapes,
        content = content
    )
}
