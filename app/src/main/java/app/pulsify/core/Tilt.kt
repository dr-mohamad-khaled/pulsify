package app.pulsify.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.max
import kotlin.math.min

/**
 * How the phone is tilted, for depth parallax in the painted worlds. Only *changes* matter: the
 * resting angle drifts back to zero over a few seconds, so holding the phone at any angle looks
 * the same and only moving it makes the world shift. Values are -1..1. Ref-counted start/stop,
 * low sensor rate, nothing runs while nobody is looking.
 */
object Tilt : SensorEventListener {
    @Volatile var x = 0f; private set
    @Volatile var y = 0f; private set
    private var users = 0
    private var sm: SensorManager? = null
    private var baseX = Float.NaN; private var baseY = 0f
    private var lastNs = 0L

    @Synchronized fun start(ctx: Context) {
        users++
        if (users > 1) return
        val m = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm = m; baseX = Float.NaN
        try { m.registerListener(this, s, SensorManager.SENSOR_DELAY_UI) } catch (_: Throwable) {}
    }

    @Synchronized fun stop() {
        users = max(0, users - 1)
        if (users > 0) return
        try { sm?.unregisterListener(this) } catch (_: Throwable) {}
        sm = null; x = 0f; y = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        val gx = e.values[0] / 9.81f; val gy = e.values[1] / 9.81f
        val dt = if (lastNs == 0L) 0.02f else min(0.2f, (e.timestamp - lastNs) / 1e9f)
        lastNs = e.timestamp
        if (baseX.isNaN()) { baseX = gx; baseY = gy }
        // the rest angle follows slowly (about 4 s), so only movement shows
        val k = min(1f, dt / 4f)
        baseX += (gx - baseX) * k; baseY += (gy - baseY) * k
        val tx = ((gx - baseX) * 3f).coerceIn(-1f, 1f); val ty = ((gy - baseY) * 3f).coerceIn(-1f, 1f)
        val s = min(1f, dt * 10f)
        x += (tx - x) * s; y += (ty - y) * s
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
