package app.pulsify.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.pulsify.core.DISCOVERIES
import app.pulsify.core.Prefs
import app.pulsify.core.Pulse
import app.pulsify.core.SKY_VARIANTS
import app.pulsify.engine.SkyScene
import app.pulsify.wallpaper.PulsifyWallpaperService

enum class Screen { HOME, STUDIO, SETTINGS }

/** Compose-observable mirror of the shared prefs. */
class AppState {
    var screen by mutableStateOf(Screen.HOME)
    var variant by mutableIntStateOf(0); private set
    var character by mutableIntStateOf(1); private set
    var coat by mutableIntStateOf(0); private set
    var env by mutableIntStateOf(0); private set
    var accent by mutableIntStateOf(0); private set
    var brightness by mutableFloatStateOf(1f); private set
    var musicReact by mutableStateOf(false); private set
    var batterySaver by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var kiwiWords by mutableStateOf(""); private set

    /** A short celebratory banner (daily spark, discovery). */
    var toast by mutableStateOf<Toast?>(null)

    data class Toast(val title: String, val line: String, val id: Long = System.nanoTime())

    private val listener: () -> Unit = { reload() }
    private var loaded = false

    init {
        reload()
        Pulse.addSettingsListener(listener)
    }

    fun dispose() = Pulse.removeSettingsListener(listener)

    private fun reload() {
        val p = Pulse.prefs
        variant = p.variant
        character = p.character
        coat = p.coat
        env = p.env
        accent = p.accent
        brightness = p.brightness
        musicReact = p.musicReact
        batterySaver = p.batterySaver
        shuffle = p.shuffleOnUnlock
        kiwiWords = p.kiwiWords
        loaded = true
    }

    fun applyVariant(v: Int) { Pulse.prefs.variant = v }
    fun applyCharacter(c: Int) { Pulse.prefs.character = c }
    fun applyCoat(c: Int) { Pulse.prefs.coat = c }
    fun applyEnv(e: Int) { Pulse.prefs.env = e }

    val displayName: String
        get() = app.pulsify.engine.creatureInfo(character)?.name ?: SKY_VARIANTS[variant.coerceIn(0, SKY_VARIANTS.size - 1)].name
    fun applyAccent(argb: Int) { Pulse.prefs.accent = argb }
    fun applyBrightness(b: Float) { Pulse.prefs.brightness = b }
    fun applyMusicReact(on: Boolean) { Pulse.prefs.musicReact = on }
    fun applyBatterySaver(on: Boolean) { Pulse.prefs.batterySaver = on }
    fun applyShuffle(on: Boolean) { Pulse.prefs.shuffleOnUnlock = on }
    fun applyKiwiWords(v: String) { Pulse.prefs.kiwiWords = v }


    val accentPair: Pair<Color, Color>
        get() {
            val v = SKY_VARIANTS[variant.coerceIn(0, SKY_VARIANTS.size - 1)]
            val info = app.pulsify.engine.creatureInfo(character)
            if (accent == 0 && info != null) {
                val c = info.coats[coat.coerceIn(0, info.coats.size - 1)]
                return Color(info.accent) to Color(c.rim)
            }
            return if (accent == 0) Color(v.accent1) to Color(v.accent2)
            else Color(accent) to Color(SkyScene.shiftHue(accent, 38f))
        }

    companion object {
        fun isOurWallpaperActive(ctx: Context): Boolean =
            try { WallpaperManager.getInstance(ctx).wallpaperInfo?.packageName == ctx.packageName } catch (_: Throwable) { false }

        /** Opens the system preview. Returns false if we were already live (changes apply instantly). */
        fun applyWallpaper(ctx: Context): Boolean {
            if (isOurWallpaperActive(ctx)) return false
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, PulsifyWallpaperService::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(intent)
            } catch (_: Throwable) {
                try { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
            }
            return true
        }
    }
}
