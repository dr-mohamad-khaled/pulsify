package app.pulsify.core

import android.content.Context
import android.content.SharedPreferences
import app.pulsify.engine.SceneInput

/** A cheap immutable copy of the settings, swapped whenever prefs change. */
data class Settings(
    val character: Int,
    val coat: Int,
    val variant: Int,
    val accent: Int,
    val brightness: Float,
    val batterySaver: Boolean,
    val musicReact: Boolean,
    val shuffleOnUnlock: Boolean,
)

/** Process-wide services shared by the app screens and the live wallpaper. */
object Pulse {
    lateinit var prefs: Prefs; private set
    lateinit var audio: AudioEngine; private set
    lateinit var device: DeviceState; private set

    @Volatile var settings = Settings(1, 0, 0, 0, 1f, false, false, false); private set

    /** Studio-only overrides. They affect the in-app preview, never the wallpaper. */
    @Volatile var previewMood: Mood? = null
    @Volatile var previewCharging = false
    @Volatile var previewLowBattery = false

    private val listeners = mutableListOf<() -> Unit>()

    // Kept as a field: SharedPreferences only holds listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val audioKey = key == Prefs.K_MUSIC
        reloadSettings()
        if (audioKey) audio.refresh()
        listeners.toList().forEach { it() }
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = Prefs(app)
        audio = AudioEngine(app, prefs)
        device = DeviceState(app)
        device.poll(force = true)
        reloadSettings()
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /** Called on the main thread after any setting changes. */
    fun addSettingsListener(l: () -> Unit) { listeners += l }
    fun removeSettingsListener(l: () -> Unit) { listeners -= l }

    private fun reloadSettings() {
        settings = Settings(
            character = prefs.character,
            coat = prefs.coat,
            variant = prefs.variant,
            accent = prefs.accent,
            brightness = prefs.brightness,
            batterySaver = prefs.batterySaver,
            musicReact = prefs.musicReact,
            shuffleOnUnlock = prefs.shuffleOnUnlock,
        )
    }

    /** Fills a frame's input. Runs on a GL thread. */
    fun fillInput(i: SceneInput, dt: Float, preview: Boolean, parallax: Float) {
        val s = settings
        device.poll()
        i.variant = s.variant
        i.character = s.character
        i.coat = s.coat
        i.accent = s.accent
        i.brightness = s.brightness
        i.parallax = parallax
        i.mood = (if (preview) previewMood else null) ?: DeviceState.moodNow()
        i.charging = (preview && previewCharging) || device.charging
        i.battery = device.batteryLevel
        i.lowBattery = (preview && previewLowBattery) || (device.batteryLevel < 0.15f && !device.charging)
        audio.sample(i.audio, dt)
    }
}
