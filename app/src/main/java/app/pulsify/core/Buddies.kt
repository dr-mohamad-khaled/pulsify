package app.pulsify.core

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import app.pulsify.widget.BuddyWidgetProvider
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Home-screen friends: creatures that live in widgets.
 *
 * A widget can't animate at 60 fps by itself, so the widget is a transparent window and the
 * live wallpaper paints the creature exactly behind it. Each tap on a widget tells us where it
 * is on screen (the launcher passes the tap's bounds); the wallpaper adds which home-screen page
 * was showing. From then on the creature rides along with the pages, reacts when you swipe to
 * it, and plays with the friends next to it.
 */
data class BuddySlot(
    val widgetId: Int,
    val creature: Int,
    /** Colour (coat) of that creature. */
    val coat: Int = 0,
    /** Home-screen page (0, 1, ...) or -1 while we don't know where it is yet. */
    val page: Int = -1,
    /** Bounds as fractions of the screen (left, top, right, bottom). */
    val l: Float = 0f, val t: Float = 0f, val r: Float = 0f, val b: Float = 0f,
) {
    val placed: Boolean get() = page >= 0 && r > l && b > t
}

/** A tap on a widget, in screen fractions. */
class BuddyPoke(val widgetId: Int, val l: Float, val t: Float, val r: Float, val b: Float)

object Buddies {
    const val MAX_LIVE = 5
    private const val FILE = "buddies"
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())

    @Volatile var slots: List<BuddySlot> = emptyList(); private set
    /** Taps waiting for the wallpaper (it knows which page is showing). */
    val pokes = ConcurrentLinkedQueue<BuddyPoke>()
    /** The creature the next pinned widget gets (set by the app before asking the launcher). */
    /** Kept on disk: the launcher may add the widget after our process was gone. */
    var pendingCreature: Int
        get() = if (::prefs.isInitialized) prefs.getInt("pending", 1) else 1
        set(v) { if (::prefs.isInitialized) prefs.edit().putInt("pending", v).apply() }
    var pendingCoat: Int
        get() = if (::prefs.isInitialized) prefs.getInt("pendingCoat", 0) else 0
        set(v) { if (::prefs.isInitialized) prefs.edit().putInt("pendingCoat", v).apply() }
    /** True while a live wallpaper engine is on the home screen and consuming taps. */
    @Volatile var wallpaperLive = false

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        slots = load()
    }

    private fun load(): List<BuddySlot> {
        val raw = prefs.getString("slots", "") ?: ""
        return raw.split(';').mapNotNull { s ->
            val p = s.split(',')
            if (p.size < 8) return@mapNotNull null
            try {
                BuddySlot(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toFloat(), p[5].toFloat(), p[6].toFloat(), p[7].toFloat())
            } catch (_: Throwable) { null }
        }
    }

    private fun save() {
        prefs.edit().putString("slots", slots.joinToString(";") { "${it.widgetId},${it.creature},${it.coat},${it.page},${it.l},${it.t},${it.r},${it.b}" }).apply()
        main.post { listeners.forEach { runCatching { it() } } }
    }

    fun slot(widgetId: Int): BuddySlot? = slots.firstOrNull { it.widgetId == widgetId }

    @Synchronized fun assign(widgetId: Int, creature: Int, coat: Int = 0) {
        val old = slot(widgetId)
        slots = slots.filter { it.widgetId != widgetId } + (old?.copy(creature = creature, coat = coat) ?: BuddySlot(widgetId, creature, coat))
        save()
    }

    @Synchronized fun remove(ids: IntArray) {
        slots = slots.filter { it.widgetId !in ids }
        save()
    }

    /** The widget was resized: its old bounds are stale until the next tap. */
    @Synchronized fun unplace(widgetId: Int) {
        slots = slots.map { if (it.widgetId == widgetId) it.copy(page = -1) else it }
        save()
    }

    /** Called by the wallpaper (GL thread) once it has turned a tap into a page and bounds. */
    @Synchronized fun place(widgetId: Int, page: Int, l: Float, t: Float, r: Float, b: Float) {
        val old = slot(widgetId) ?: return
        val moved = old.page != page || kotlin.math.abs(old.l - l) > 0.01f || kotlin.math.abs(old.t - t) > 0.01f
        if (!moved && old.placed) return
        slots = slots.map { if (it.widgetId == widgetId) it.copy(page = page, l = l, t = t, r = r, b = b) else it }
        save()
        main.post { BuddyWidgetProvider.refresh(app, intArrayOf(widgetId)) }
    }

    /** Turns a launcher tap rectangle (screen pixels) into screen fractions and queues it. */
    fun poke(context: Context, widgetId: Int, bounds: Rect?) {
        if (bounds == null || bounds.isEmpty) { pokes.add(BuddyPoke(widgetId, -1f, -1f, -1f, -1f)); return }
        val (sw, sh) = screenSize(context)
        pokes.add(BuddyPoke(widgetId, bounds.left / sw, bounds.top / sh, bounds.right / sw, bounds.bottom / sh))
    }

    fun screenSize(context: Context): Pair<Float, Float> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width().toFloat().coerceAtLeast(1f) to b.height().toFloat().coerceAtLeast(1f)
        } else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels.toFloat().coerceAtLeast(1f) to m.heightPixels.toFloat().coerceAtLeast(1f)
        }
    }

    /** Is Pulsify the home-screen wallpaper right now? */
    fun isOurWallpaper(context: Context): Boolean = try {
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName
    } catch (_: Throwable) { false }
}
