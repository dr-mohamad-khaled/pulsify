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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import app.pulsify.R
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
    val surface: Color = Color(0xFF0C0C0E),
    val surface2: Color = Color(0xFF161618),
    val border: Color = Color(0x1FFFFFFF),
    val text: Color = Color(0xFFF6F3EF),
    val sub: Color = Color(0xFFA9A4AD),
    val faint: Color = Color(0xFF6C6770),
    val onAccent: Color = Color(0xFF0B0A0C),
    val mint: Color = Color(0xFF6EE7A8),
    val teal: Color = Color(0xFF5EE0D0),
    val mintBg: Color = Color(0x2622C55E),
    val amber: Color = Color(0xFFFFB36B),
    /** Accent pushed toward white: for text and fine lines on black. */
    val accentInk: Color = lerp(accent, Color.White, 0.35f),
)

/**
 * The palette is true black and warm white; the only colour comes from the character on
 * screen, so the whole app takes on Kalboob's gold, Kiwi's green, Glowy's teal...
 */
fun pulseColors(accent: Color, accent2: Color): PulseColors {
    val lum = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    return PulseColors(
        accent = accent,
        accent2 = accent2,
        surface2 = lerp(Color(0xFF151517), accent, 0.06f),
        onAccent = if (lum > 0.55f) Color(0xFF0B0A0C) else Color.White,
    )
}

val LocalPulse = staticCompositionLocalOf { pulseColors(Color(0xFFCDB4FF), Color(0xFFFFB36B)) }

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float = 100f, opsz: Float = 48f) = Font(
    R.font.bricolage, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width), FontVariation.Setting("opsz", opsz)),
)
@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(R.font.figtree, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Bricolage Grotesque for names and headings (condensed, big optical size), Figtree for UI text. */
val DisplayFace = FontFamily(bricolage(760, 86f, 96f), bricolage(600, 92f, 60f))
val DisplayWideFace = FontFamily(bricolage(700, 100f, 40f))
val TextFace = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700), figtree(800))

object T {
    val hero = TextStyle(fontFamily = DisplayFace, fontWeight = FontWeight(760), fontSize = 52.sp, letterSpacing = (-0.035).em, lineHeight = 50.sp)
    val display = TextStyle(fontFamily = DisplayFace, fontWeight = FontWeight(760), fontSize = 40.sp, letterSpacing = (-0.03).em, lineHeight = 40.sp)
    val title = TextStyle(fontFamily = DisplayFace, fontWeight = FontWeight(760), fontSize = 27.sp, letterSpacing = (-0.02).em, lineHeight = 30.sp)
    val name = TextStyle(fontFamily = DisplayFace, fontWeight = FontWeight(760), fontSize = 21.sp, letterSpacing = (-0.015).em, lineHeight = 22.sp)
    val heading = TextStyle(fontFamily = TextFace, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = (-0.005).em)
    val body = TextStyle(fontFamily = TextFace, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val bodyStrong = TextStyle(fontFamily = TextFace, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val small = TextStyle(fontFamily = TextFace, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)
    val micro = TextStyle(fontFamily = TextFace, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.01.em)
    val button = TextStyle(fontFamily = TextFace, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.005.em)
    val dial = TextStyle(fontFamily = DisplayWideFace, fontWeight = FontWeight(700), fontSize = 17.sp, letterSpacing = (-0.01).em)
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
