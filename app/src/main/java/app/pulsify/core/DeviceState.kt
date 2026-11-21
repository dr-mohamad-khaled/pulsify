package app.pulsify.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import java.time.LocalTime

enum class Mood(val label: String) { MORNING("Morning"), DAY("Day"), EVENING("Evening"), NIGHT("Night") }

/** Battery and time of day. Polled cheaply (sticky intent, at most every 20 s). */
class DeviceState(private val context: Context) {
    @Volatile var batteryLevel = 1f; private set
    @Volatile var charging = false; private set
    private var lastPoll = 0L

    fun poll(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPoll < 20_000) return
        lastPoll = now
        val i: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) batteryLevel = level / scale.toFloat()
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    companion object {
        fun moodNow(t: LocalTime = LocalTime.now()): Mood = when (t.hour) {
            in 5..9 -> Mood.MORNING
            in 10..16 -> Mood.DAY
            in 17..22 -> Mood.EVENING
            else -> Mood.NIGHT
        }
    }
}
