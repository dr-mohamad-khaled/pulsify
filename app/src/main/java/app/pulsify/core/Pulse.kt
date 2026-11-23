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
    val video: Int = -1,
    val style: Int = 0,
    val bgColor: Int = 0xFF000000.toInt(),
    /** What the live wallpaper shows (the app's preview shows the fields above). */
    val active: Prefs.Active? = null,
    /** The user-photo world of the draft and of the live wallpaper (see [Prefs.userBgKey]). */
    val userBg: String = "",
    val activeUserBg: String = "",
)

/** Process-wide services shared by the app screens and the live wallpaper. */
object Pulse {
    lateinit var prefs: Prefs; private set
    lateinit var audio: AudioEngine; private set
    lateinit var device: DeviceState; private set

    @Volatile var settings = Settings(1, 0, 0, 0, 0, 1f, false, false, false); private set

    /** Studio-only overrides. They affect the in-app preview, never the wallpaper. */
    @Volatile var previewMood: Mood? = null
    /** Studio sun dial: an exact preview hour (0..24), or null for the real clock. */
    @Volatile var previewHour: Float = -1f
    @Volatile var previewCharging = false
    @Volatile var previewLowBattery = false
    /** Debug portrait shots: everyday look, never charging or low. */
    @Volatile var forceCalm = false

    private val listeners = mutableListOf<() -> Unit>()

    // Kept as a field: SharedPreferences only holds listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val audioKey = key == Prefs.K_MUSIC
        reloadSettings()
        if (audioKey) audio.refresh()
        listeners.toList().forEach { it() }
    }

    fun init(context: Context) {
        Buddies.init(context)
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
            video = prefs.video,
            style = prefs.style,
            bgColor = prefs.bgColor,
            active = prefs.active,
            userBg = prefs.userBgKey(prefs.character, prefs.style),
            activeUserBg = prefs.active.let { prefs.userBgKey(it.character, it.style) },
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
        val a = if (preview) null else s.active
        val character = a?.character ?: s.character
        val env = a?.env ?: s.env
        i.character = character
        i.video = a?.video ?: s.video
        i.coat = a?.coat ?: s.coat
        i.style = a?.style ?: s.style
        i.bgColor = a?.bgColor ?: s.bgColor
        // the user's own photo: which file, and where they put the creature on it
        i.userBg = if (a != null) s.activeUserBg else s.userBg
        val info = app.pulsify.engine.creatureInfo(character)
        if (info != null) {
            val list = app.pulsify.engine.envsFor(info, i.style)
            val e = list[env.coerceIn(0, list.size - 1)]
            i.world = e.world
            i.variant = e.skyVariant
        } else {
            i.world = -1
            i.variant = app.pulsify.core.liveSky(a?.variant ?: s.variant)
        }
        val pm = if (preview) previewMood else null
        val ph = if (preview) previewHour else -1f
        i.hour = if (ph >= 0f) ph else when (pm) {
            Mood.MORNING -> 7.2f
            Mood.DAY -> 13f
            Mood.EVENING -> 18.6f
            Mood.NIGHT -> 23f
            null -> clockHour()
        }
        i.accent = s.accent
        i.brightness = s.brightness
        i.parallax = parallax
        i.tiltX = Tilt.x; i.tiltY = Tilt.y
        i.kiwiWords = s.kiwiWords
        i.mood = (if (preview && ph < 0f) previewMood else null) ?: when (i.hour.toInt()) {
            in 5..9 -> Mood.MORNING
            in 10..16 -> Mood.DAY
            in 17..22 -> Mood.EVENING
            else -> Mood.NIGHT
        }
        i.charging = !forceCalm && ((preview && previewCharging) || device.charging)
        i.battery = device.batteryLevel
        i.lowBattery = !forceCalm && ((preview && previewLowBattery) || (device.batteryLevel < 0.15f && !device.charging))
        audio.sample(i.audio, dt)
    }
}
