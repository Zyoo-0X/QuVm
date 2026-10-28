package app.aemu.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * QuVm "Aurora": a deep midnight surface with an indigo → cyan glow in the dark theme, a soft lavender
 * paper in the light one. Accent colours stay the same family in both so the app feels like one thing.
 */
object Aurora {
    val Indigo = Color(0xFF6C7BFF)
    val Violet = Color(0xFF9B6BFF)
    val Cyan = Color(0xFF35D6E6)
    val Pink = Color(0xFFFF7BC0)
    val Mint = Color(0xFF3DDC97)

    /** the brand gradient: buttons, hero cards, the logo word */
    val Brand: Brush get() = Brush.linearGradient(listOf(Color(0xFF5B6CFF), Color(0xFF8B5CFF), Color(0xFF2FC8E8)))
    val BrandColors = listOf(Color(0xFF5B6CFF), Color(0xFF8B5CFF), Color(0xFF2FC8E8))
}

private val Light = lightColorScheme(
    primary = Color(0xFF4350E8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E2FF), onPrimaryContainer = Color(0xFF0A0F63),
    secondary = Color(0xFF00838C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2F3F6), onSecondaryContainer = Color(0xFF002B2E),
    tertiary = Color(0xFFB4287D), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EC), onTertiaryContainer = Color(0xFF3B0026),
    background = Color(0xFFF6F6FD), onBackground = Color(0xFF191B2A),
    surface = Color(0xFFF6F6FD), onSurface = Color(0xFF191B2A),
    surfaceVariant = Color(0xFFE3E3F2), onSurfaceVariant = Color(0xFF50546E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F0FA),
    surfaceContainer = Color(0xFFEAEAF6), surfaceContainerHigh = Color(0xFFE3E3F2),
    surfaceContainerHighest = Color(0xFFDCDCEE),
    outline = Color(0xFF8286A3), outlineVariant = Color(0xFFC9CBE0),
    error = Color(0xFFBA1A3A), onError = Color.White,
    errorContainer = Color(0xFFFFDAE0), onErrorContainer = Color(0xFF40000F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FA8FF), onPrimary = Color(0xFF14146B),
    primaryContainer = Color(0xFF2B2F8F), onPrimaryContainer = Color(0xFFE0E1FF),
    secondary = Color(0xFF5EE0E8), onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F54), onSecondaryContainer = Color(0xFFA6F1F5),
    tertiary = Color(0xFFFFA8D8), onTertiary = Color(0xFF560B3C),
    tertiaryContainer = Color(0xFF5C1F4A), onTertiaryContainer = Color(0xFFFFD8EC),
    background = Color(0xFF0A0B14), onBackground = Color(0xFFE5E6F4),
    surface = Color(0xFF0A0B14), onSurface = Color(0xFFE5E6F4),
    surfaceVariant = Color(0xFF252A40), onSurfaceVariant = Color(0xFFA9ADC6),
    surfaceContainerLowest = Color(0xFF06070D), surfaceContainerLow = Color(0xFF10121D),
    surfaceContainer = Color(0xFF151826), surfaceContainerHigh = Color(0xFF1C2033),
    surfaceContainerHighest = Color(0xFF252A40),
    outline = Color(0xFF5E6380), outlineVariant = Color(0xFF2B2F47),
    error = Color(0xFFFF8A9B), onError = Color(0xFF4A0013),
    errorContainer = Color(0xFF5C1425), onErrorContainer = Color(0xFFFFDAE0),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val base = Typography()
private val AppType = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

@Composable
fun AemuTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // the main screen stays alive while the settings screen changes the theme: follow the stored value
    val rev = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(ctx) {
        val sp = app.aemu.AppPrefs.prefs(ctx)
        val l = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rev.intValue++ }
        sp.registerOnSharedPreferenceChangeListener(l)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(l) }
    }
    rev.intValue
    val dark = forceDark ?: when (app.aemu.AppPrefs.theme(ctx)) {
        app.aemu.AppPrefs.THEME_LIGHT -> false
        app.aemu.AppPrefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && forceDark == null && app.aemu.AppPrefs.dynamicColor(ctx) ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppType,
        content = content,
    )
}

val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
