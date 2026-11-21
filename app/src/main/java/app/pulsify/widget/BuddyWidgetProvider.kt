package app.pulsify.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import app.pulsify.MainActivity
import app.pulsify.R
import app.pulsify.core.Buddies
import app.pulsify.core.Pulse
import app.pulsify.engine.creatureInfo

/**
 * A home-screen friend. With Pulsify as the wallpaper the widget is a clear window and the
 * wallpaper paints the creature behind it; otherwise it shows a picture of the creature.
 */
class BuddyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Pulse.init(context)
        for (id in ids) if (Buddies.slot(id) == null) Buddies.assign(id, Buddies.pendingCreature, Buddies.pendingCoat)
        refresh(context, ids)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        Pulse.init(context)
        Buddies.remove(ids)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        Pulse.init(context)
        // Resized: where it sits on screen changed, so the next tap places it again.
        Buddies.unplace(id)
        refresh(context, intArrayOf(id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_POKE) return
        Pulse.init(context)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (Buddies.wallpaperLive && Buddies.isOurWallpaper(context)) {
            Buddies.poke(context, id, intent.sourceBounds)
        } else {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open", "friends"))
        }
    }

    companion object {
        const val ACTION_POKE = "app.pulsify.BUDDY_POKE"

        fun allIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, BuddyWidgetProvider::class.java))

        /** Redraws the widgets (all of them when [ids] is null). */
        fun refresh(context: Context, ids: IntArray? = null) {
            try {
                Buddies.init(context)
                val mgr = AppWidgetManager.getInstance(context)
                val list = ids ?: allIds(context)
                val ours = Buddies.isOurWallpaper(context)
                for (id in list) mgr.updateAppWidget(id, views(context, id, ours))
            } catch (t: Throwable) {
                app.pulsify.core.CrashLog.note("widget refresh: ${t.message}")
            }
        }

        private fun views(context: Context, id: Int, ours: Boolean): RemoteViews {
            val slot = Buddies.slot(id)
            val creature = slot?.creature ?: 1
            val name = creatureInfo(creature)?.name ?: "Friend"
            val v = RemoteViews(context.packageName, R.layout.widget_buddy)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val poke = Intent(context, BuddyWidgetProvider::class.java).setAction(ACTION_POKE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getBroadcast(context, id, poke, flags))
            when {
                ours && slot?.placed == true -> {
                    // Live: nothing here but glass. The wallpaper paints the creature behind it.
                    v.setViewVisibility(R.id.still, View.GONE)
                    v.setViewVisibility(R.id.hint, View.GONE)
                }
                ours -> {
                    still(context, v, creature, 110)
                    v.setViewVisibility(R.id.hint, View.VISIBLE)
                    v.setTextViewText(R.id.hint, "Tap to wake $name")
                }
                else -> {
                    still(context, v, creature, 255)
                    v.setViewVisibility(R.id.hint, View.VISIBLE)
                    v.setTextViewText(R.id.hint, "Set Pulsify as wallpaper")
                }
            }
            return v
        }

        private fun still(context: Context, v: RemoteViews, creature: Int, alpha: Int) {
            val bmp = try { context.assets.open("thumbs/creature_$creature.jpg").use { BitmapFactory.decodeStream(it) } } catch (_: Throwable) { null }
            if (bmp != null) v.setImageViewBitmap(R.id.still, bmp)
            v.setInt(R.id.still, "setImageAlpha", alpha)
            v.setViewVisibility(R.id.still, View.VISIBLE)
        }
    }
}
