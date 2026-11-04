package app.pulsify.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Theme engine: one accent colour (picked, or taken from the current wallpaper)
 * derives the whole gradient system. The base always stays true AMOLED black.
 */
@Immutable
data class PulseColors(
    val accent: Color,
    val accent2: Color,
    val bg: Color = Color.Black,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color = Color.White,
    val sub: Color = Color(0xFF9E96B0),
    val faint: Color = Color(0xFF5E576E),
    val onAccent: Color = Color(0xFF0D0A14),
    val mint: Color = Color(0xFF4ADE80),
    val teal: Color = Color(0xFF2DD4BF),
    val mintBg: Color = Color(0xFF1A2E2B),
    val amber: Color = Color(0xFFFFB36B),
)

fun pulseColors(accent: Color, accent2: Color) = PulseColors(
    accent = accent,
    accent2 = accent2,
    surface = lerp(Color(0xFF0F0C15), accent, 0.045f),
    surface2 = lerp(Color(0xFF17131F), accent, 0.07f),
    border = lerp(Color(0xFF2A2436), accent, 0.18f),
)

val LocalPulse = staticCompositionLocalOf { pulseColors(Color(0xFFCDB4FF), Color(0xFFFFB36B)) }

object T {
    val display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.03).em, lineHeight = 38.sp)
    val title = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.02).em)
    val heading = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.01).em)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val bodyStrong = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val small = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val micro = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.12.em)
    val button = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.01.em)
}

@Composable
fun PulsifyTheme(accent: Color, accent2: Color, content: @Composable () -> Unit) {
    val a by animateColorAsState(accent, tween(700), label = "accent")
    val b by animateColorAsState(accent2, tween(700), label = "accent2")
    val colors = pulseColors(a, b)
    val scheme = darkColorScheme(
        primary = a,
        onPrimary = colors.onAccent,
        secondary = b,
        background = Color.Black,
        surface = colors.surface,
        onSurface = Color.White,
        surfaceVariant = colors.surface2,
        outline = colors.border,
    )
    CompositionLocalProvider(LocalPulse provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
