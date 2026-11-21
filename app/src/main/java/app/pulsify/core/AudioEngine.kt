package app.pulsify.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One smoothed reading of the music, consumed by the renderer every frame. */
class AudioFrame {
    var bass = 0f
    var mid = 0f
    var treble = 0f
    var level = 0f
    var beat = 0f          // decaying pulse, 1 on a beat
    var bpm = 0f
    var active = false     // something is reacting (real music, fallback or demo)
    var source = Source.NONE
    val spectrum = FloatArray(32)

    enum class Source { NONE, MUSIC, GROOVE, DEMO }
}

/**
 * Reads the FFT of whatever the phone is playing through Visualizer(0).
 * Visualizer only reads the music output mix; it never records the microphone.
 * Reference counted: the Visualizer is released whenever nothing is visible.
 */
class AudioEngine(private val context: Context, private val prefs: Prefs) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var visualizer: Visualizer? = null
    private var refs = 0
    private val lock = Any()

    // Raw values written by the Visualizer thread.
    @Volatile private var rawBass = 0f
    @Volatile private var rawMid = 0f
    @Volatile private var rawTreble = 0f
    @Volatile private var lastFftAt = 0L
    @Volatile private var beatCounter = 0
    @Volatile private var bpmEstimate = 0f
    private val rawSpectrum = FloatArray(32)

    private val peak = floatArrayOf(1f, 1f, 1f)
    private var bassAvg = 0f
    private var lastBeatAt = 0L
    private val intervals = ArrayDeque<Float>()

    // Render-thread state.
    private var seenBeat = 0
    private var musicActive = false
    private var lastActiveCheck = 0L
    private var demoPhase = 0.0
    private var lastDemoBeat = -1L

    /** Demo groove toggled from the Studio, so the app works without any music. */
    @Volatile var demo = false

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun acquire() = synchronized(lock) {
        refs++
        if (refs == 1) startVisualizer()
    }

    fun release() = synchronized(lock) {
        refs = max(0, refs - 1)
        if (refs == 0) stopVisualizer()
    }

    /** Call after the music toggle or the permission changes. */
    fun refresh() = synchronized(lock) {
        stopVisualizer()
        if (refs > 0) startVisualizer()
    }

    val visualizerRunning: Boolean get() = visualizer != null

    private fun startVisualizer() {
        if (!prefs.musicReact || !hasPermission() || visualizer != null) return
        try {
            val v = Visualizer(0)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(min(1024, range[1]))
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) try { onFft(fft, samplingRate / 1000f) } catch (_: Throwable) {}
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.setEnabled(true)
            visualizer = v
        } catch (t: Throwable) {
            Log.w("Pulsify", "Visualizer unavailable: ${t.message}")
            visualizer = null
        }
    }

    private fun stopVisualizer() {
        try {
            visualizer?.setEnabled(false)
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
        rawBass = 0f; rawMid = 0f; rawTreble = 0f
    }

    // ---- Visualizer thread ----

    private fun onFft(fft: ByteArray, sampleRateHz: Float) {
        val n = fft.size / 2
        val binHz = sampleRateHz / fft.size
        var b = 0f; var bn = 0
        var m = 0f; var mn = 0
        var t = 0f; var tn = 0
        val spec = FloatArray(32)
        val specN = IntArray(32)
        val logLo = ln(30f)
        val logHi = ln(16000f)
        for (k in 1 until n) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            val mag = sqrt(re * re + im * im)
            val f = k * binHz
            when {
                f < 20f -> {}
                f < 250f -> { b += mag; bn++ }
                f < 4000f -> { m += mag; mn++ }
                f < 16000f -> { t += mag; tn++ }
            }
            if (f in 30f..16000f) {
                val idx = (((ln(f) - logLo) / (logHi - logLo)) * 31.999f).toInt().coerceIn(0, 31)
                spec[idx] += mag; specN[idx]++
            }
        }
        val bv = ln(1f + if (bn > 0) b / bn else 0f)
        val mv = ln(1f + if (mn > 0) m / mn else 0f)
        val tv = ln(1f + if (tn > 0) t / tn else 0f)

        // Adaptive peak normalisation per band.
        val vals = floatArrayOf(bv, mv, tv)
        val norm = FloatArray(3)
        for (i in 0..2) {
            peak[i] = max(vals[i], peak[i] * 0.996f).coerceAtLeast(0.6f)
            norm[i] = (vals[i] / peak[i]).coerceIn(0f, 1f)
        }
        synchronized(rawSpectrum) {
            for (i in 0 until 32) {
                val v = if (specN[i] > 0) ln(1f + spec[i] / specN[i]) / 4.5f else rawSpectrum[i] * 0.8f
                rawSpectrum[i] = v.coerceIn(0f, 1f)
            }
        }
        rawBass = norm[0]; rawMid = norm[1]; rawTreble = norm[2]
        lastFftAt = SystemClock.elapsedRealtime()

        // Beat: bass energy against a moving average, 0.24 s refractory.
        val now = SystemClock.elapsedRealtime()
        bassAvg = if (bassAvg == 0f) bv else bassAvg * 0.93f + bv * 0.07f
        if (norm[0] > 0.35f && bv > bassAvg * 1.25f && now - lastBeatAt > 240) {
            if (lastBeatAt > 0) {
                val dt = (now - lastBeatAt) / 1000f
                if (dt in 0.3f..1.5f) {
                    intervals.addLast(dt)
                    if (intervals.size > 12) intervals.removeFirst()
                    val sorted = intervals.sorted()
                    bpmEstimate = 60f / sorted[sorted.size / 2]
                }
            }
            lastBeatAt = now
            beatCounter++
        }
    }

    // ---- Render thread ----

    /** Fills [out] with smoothed values. [dt] in seconds. */
    fun sample(out: AudioFrame, dt: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActiveCheck > 1000) {
            lastActiveCheck = now
            musicActive = audioManager.isMusicActive
        }
        val liveFft = visualizer != null && now - lastFftAt < 500 && musicActive
        var tb = 0f; var tm = 0f; var tt = 0f
        var beatNow = false

        when {
            liveFft -> {
                tb = rawBass; tm = rawMid; tt = rawTreble
                if (beatCounter != seenBeat) { seenBeat = beatCounter; beatNow = true }
                out.bpm = bpmEstimate
                out.source = AudioFrame.Source.MUSIC
                synchronized(rawSpectrum) {
                    for (i in 0 until 32) out.spectrum[i] += (rawSpectrum[i] - out.spectrum[i]) * min(1f, dt * 18f)
                }
            }
            demo || musicActive -> {
                // Demo groove, or a gentle groove when music plays but we may not read it.
                val bpm = if (demo) 112.0 else 96.0
                val gain = if (demo) 1f else 0.45f
                demoPhase += dt * bpm / 60.0
                val beatIndex = demoPhase.toLong()
                if (beatIndex != lastDemoBeat) { lastDemoBeat = beatIndex; beatNow = true }
                val ph = demoPhase - beatIndex
                tb = (gain * (1.0 - ph).pow(3.0)).toFloat()
                tm = (gain * (0.45 + 0.35 * cos(demoPhase * PI))).toFloat()
                tt = (gain * (0.3 + 0.3 * (if ((demoPhase * 2).toLong() % 2L == 0L) 1 - ph * 2 % 1 else 0.2))).toFloat()
                out.bpm = bpm.toFloat()
                out.source = if (demo) AudioFrame.Source.DEMO else AudioFrame.Source.GROOVE
                for (i in 0 until 32) {
                    val x = i / 31f
                    val target = (tb * (1 - x).pow(2) + tm * (1 - kotlin.math.abs(x - 0.45f) * 1.6f).coerceAtLeast(0f) +
                        tt * x.pow(2) * 0.8f).coerceIn(0f, 1f)
                    out.spectrum[i] += (target - out.spectrum[i]) * min(1f, dt * 14f)
                }
            }
            else -> {
                out.source = AudioFrame.Source.NONE
                out.bpm = 0f
                for (i in 0 until 32) out.spectrum[i] *= max(0f, 1f - dt * 4f)
            }
        }

        // Fast attack, slow release.
        out.bass = smooth(out.bass, tb, dt, 22f, 5f)
        out.mid = smooth(out.mid, tm, dt, 14f, 4f)
        out.treble = smooth(out.treble, tt, dt, 26f, 6f)
        out.level = (out.bass + out.mid + out.treble) / 3f
        out.beat = if (beatNow) 1f else max(0f, out.beat - dt * 3.2f)
        out.active = out.source != AudioFrame.Source.NONE
    }

    private fun smooth(cur: Float, target: Float, dt: Float, up: Float, down: Float): Float {
        val k = if (target > cur) up else down
        return cur + (target - cur) * min(1f, dt * k)
    }
}
