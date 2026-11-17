package app.ancraft.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Pumpkin-orange fallback palette for devices without Material You dynamic colour (Android < 12).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF9A4600),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC6),
    onPrimaryContainer = Color(0xFF331300),
    secondary = Color(0xFF765849),
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF2B160B),
    tertiary = Color(0xFF4F6525),
    tertiaryContainer = Color(0xFFD1EB9D),
    onTertiaryContainer = Color(0xFF111F00),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF4DED4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB68B),
    onPrimary = Color(0xFF532200),
    primaryContainer = Color(0xFF763300),
    onPrimaryContainer = Color(0xFFFFDBC6),
    secondary = Color(0xFFE6BEAB),
    secondaryContainer = Color(0xFF5C4032),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFB5CE82),
    tertiaryContainer = Color(0xFF384D10),
    onTertiaryContainer = Color(0xFFD1EB9D),
    background = Color(0xFF1A110C),
    surface = Color(0xFF1A110C),
    surfaceVariant = Color(0xFF52443C),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnCraftTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
