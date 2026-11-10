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

    private var appCtx: Context? = null

    fun install(context: Context) {
        dir = context.filesDir
        appCtx = context.applicationContext
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

    /** Java crash we caught ourselves, or a native crash / ANR Android recorded since the last dismiss. */
    fun lastCrash(): String? {
        val own = try { dir?.let { File(it, CRASH) }?.takeIf { it.exists() }?.readText() } catch (_: Throwable) { null }
        return own ?: systemExits().takeIf { it.isNotBlank() }
    }

    /** Process deaths Android itself recorded (catches native GPU-driver crashes and ANRs too). */
    fun systemExits(all: Boolean = false): String {
        val ctx = appCtx ?: return ""
        if (Build.VERSION.SDK_INT < 30) return ""
        return try {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java) ?: return ""
            val seen = ctx.getSharedPreferences("crashlog", Context.MODE_PRIVATE).getLong("seen", 0L)
            val bad = setOf(
                android.app.ApplicationExitInfo.REASON_CRASH,
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                android.app.ApplicationExitInfo.REASON_ANR,
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            )
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 10)
                .filter { it.reason in bad && (all || it.timestamp > seen) }
                .joinToString("\n") { e ->
                    val kind = when (e.reason) {
                        android.app.ApplicationExitInfo.REASON_CRASH -> "Java crash"
                        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash (GPU driver or system library)"
                        android.app.ApplicationExitInfo.REASON_ANR -> "Not responding (ANR)"
                        else -> "Startup failure"
                    }
                    val desc = e.description ?: ""
                    "${SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(e.timestamp))}  $kind  [${e.processName}]  $desc"
                }
        } catch (_: Throwable) { "" }
    }

    fun notes(): String = try { dir?.let { File(it, NOTES) }?.takeIf { it.exists() }?.readText() ?: "" } catch (_: Throwable) { "" }

    fun clear() {
        try { dir?.let { File(it, CRASH).delete() } } catch (_: Throwable) {}
        try { appCtx?.getSharedPreferences("crashlog", Context.MODE_PRIVATE)?.edit()?.putLong("seen", System.currentTimeMillis())?.apply() } catch (_: Throwable) {}
    }

    fun diagnostics(): String = header() + "\n" +
        (try { dir?.let { File(it, CRASH) }?.takeIf { it.exists() }?.readText() } catch (_: Throwable) { null } ?: "No Java crash recorded.") +
        "\n\nAndroid exit records:\n" + systemExits(all = true).ifBlank { "none" } +
        "\n\nGL notes:\n" + notes().ifBlank { "none" }

    private fun header(): String =
        "Pulsify ${app.pulsify.BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Time: ${stamp()}\n"

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
