package nz.ac.aut.comp713.tarocartmobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// defines the fallback palette used when the device is in dark mode
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// defines the branded light palette used throughout the app
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF17665F),
    secondary = Color(0xFF526464),
    tertiary = Color(0xFF17665F),
    background = Color(0xFFF4F6F6),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onBackground = Color(0xFF243333),
    onSurface = Color(0xFF243333),
    error = Color(0xFFA52828)
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
        content = content
    )
}
