package app.pulsify.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash (and a few GL warnings) on the phone, so the app can show it after a
 * restart and the user can copy it. Nothing leaves the device.
 */
object CrashLog {
    private var dir: File? = null
    private const val CRASH = "last_crash.txt"
    private const val NOTES = "gl_notes.txt"

    fun install(context: Context) {
        dir = context.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                File(context.filesDir, CRASH).writeText(header() + "Thread: ${thread.name}\n\n$sw")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun note(line: String) {
        val d = dir ?: return
        try {
            val f = File(d, NOTES)
            val old = if (f.exists()) f.readLines().takeLast(30) else emptyList()
            f.writeText((old + "${stamp()} $line").joinToString("\n"))
        } catch (_: Throwable) {
        }
    }

    fun lastCrash(): String? = try { dir?.let { File(it, CRASH) }?.takeIf { it.exists() }?.readText() } catch (_: Throwable) { null }

    fun notes(): String = try { dir?.let { File(it, NOTES) }?.takeIf { it.exists() }?.readText() ?: "" } catch (_: Throwable) { "" }

    fun clear() {
        try { dir?.let { File(it, CRASH).delete() } } catch (_: Throwable) {}
    }

    fun diagnostics(): String = header() + "\n" + (lastCrash() ?: "No crash recorded.") + "\n\nGL notes:\n" + notes().ifBlank { "none" }

    private fun header(): String =
        "Pulsify ${app.pulsify.BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Time: ${stamp()}\n"

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
