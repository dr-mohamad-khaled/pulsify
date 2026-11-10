package app.pulsify.core

import android.content.Context
import android.content.SharedPreferences
import app.pulsify.engine.SceneInput

/** A cheap immutable copy of the settings, swapped whenever prefs change. */
data class Settings(
    val character: Int,
    val coat: Int,
    val env: Int,
    val variant: Int,
    val accent: Int,
    val brightness: Float,
    val batterySaver: Boolean,
    val musicReact: Boolean,
    val shuffleOnUnlock: Boolean,
    val kiwiWords: String = "",
)

/** Process-wide services shared by the app screens and the live wallpaper. */
object Pulse {
    lateinit var prefs: Prefs; private set
    lateinit var audio: AudioEngine; private set
    lateinit var device: DeviceState; private set

    @Volatile var settings = Settings(1, 0, 0, 0, 0, 1f, false, false, false); private set

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
            env = prefs.env,
            variant = prefs.variant,
            accent = prefs.accent,
            brightness = prefs.brightness,
            batterySaver = prefs.batterySaver,
            musicReact = prefs.musicReact,
            shuffleOnUnlock = prefs.shuffleOnUnlock,
            kiwiWords = prefs.kiwiWords,
        )
    }

    @Volatile private var hourCache = 12f
    @Volatile private var hourAt = 0L
    /** Local time as a fraction of the day; refreshed every 15 s (no per-frame allocation). */
    private fun clockHour(): Float {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - hourAt > 15_000L || hourAt == 0L) {
            hourAt = now
            hourCache = java.time.LocalTime.now().let { it.hour + it.minute / 60f }
        }
        return hourCache
    }

    /** Fills a frame's input. Runs on a GL thread. */
    fun fillInput(i: SceneInput, dt: Float, preview: Boolean, parallax: Float) {
        val s = settings
        device.poll()
        i.character = s.character
        i.coat = s.coat
        val info = app.pulsify.engine.creatureInfo(s.character)
        if (info != null) {
            val e = info.envs[s.env.coerceIn(0, info.envs.size - 1)]
            i.world = e.world
            i.variant = e.skyVariant
        } else {
            i.world = -1
            i.variant = s.variant
        }
        val pm = if (preview) previewMood else null
        i.hour = when (pm) {
            Mood.MORNING -> 7.2f
            Mood.DAY -> 13f
            Mood.EVENING -> 18.6f
            Mood.NIGHT -> 23f
            null -> clockHour()
        }
        i.accent = s.accent
        i.brightness = s.brightness
        i.parallax = parallax
        i.kiwiWords = s.kiwiWords
        i.mood = (if (preview) previewMood else null) ?: when (i.hour.toInt()) {
            in 5..9 -> Mood.MORNING
            in 10..16 -> Mood.DAY
            in 17..22 -> Mood.EVENING
            else -> Mood.NIGHT
        }
        i.charging = (preview && previewCharging) || device.charging
        i.battery = device.batteryLevel
        i.lowBattery = (preview && previewLowBattery) || (device.batteryLevel < 0.15f && !device.charging)
        audio.sample(i.audio, dt)
    }
}
