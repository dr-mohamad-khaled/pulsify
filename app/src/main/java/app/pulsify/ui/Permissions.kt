package app.pulsify.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.pulsify.core.Pulse

/**
 * First-run permissions, asked once right after the intro, each with the reason before the system
 * dialog: music reaction (the visualizer needs the audio permission; it never records), staying
 * alive (battery optimisation off, so Android doesn't close the wallpaper), and on Xiaomi / Redmi /
 * Poco phones the extra "No restrictions" battery setting their system adds on top.
 */
object Perms {
    fun audio(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun battery(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    val isXiaomi: Boolean get() = Build.MANUFACTURER.lowercase().let { it.contains("xiaomi") || it.contains("redmi") || it.contains("poco") }

    /** The system dialog "Let Pulsify run in the background?"; falls back to the battery optimisation list. */
    @SuppressLint("BatteryLife")
    fun askBattery(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
        if (!start(ctx, direct)) start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Xiaomi's own battery saver for this app (set it to "No restrictions"), else the app's info page. */
    fun askXiaomi(ctx: Context) {
        val miui = Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", ctx.packageName).putExtra("package_label", "Pulsify")
        if (!start(ctx, miui)) start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Throwable) { false }
}

@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    // re-check when we come back from a system screen
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    var xiaomiOpened by remember { mutableStateOf(Pulse.prefs.xiaomiBatteryOpened) }
    val audioOk = remember(tick) { Perms.audio(ctx) }
    val batteryOk = remember(tick) { Perms.battery(ctx) }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Pulse.prefs.musicReact = true
        tick++
    }
    fun askXiaomi() { Pulse.prefs.xiaomiBatteryOpened = true; xiaomiOpened = true; Perms.askXiaomi(ctx) }
    // the one big button walks through whatever is still missing
    val next: (() -> Unit)? = when {
        !audioOk -> { { mic.launch(Manifest.permission.RECORD_AUDIO) } }
        !batteryOk -> { { Perms.askBattery(ctx) } }
        Perms.isXiaomi && !xiaomiOpened -> { { askXiaomi() } }
        else -> null
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 22.dp, vertical = 26.dp),
        ) {
            Text("Before you start", style = T.title, color = c.text)
            Gap(8.dp)
            Text("Pulsify needs a few things to work as a live wallpaper. Here's what each one is for.", style = T.body, color = c.sub)
            Gap(22.dp)
            PermCard("Music reaction", "So creatures dance and wallpapers pulse with the music your phone plays. Pulsify only reads the beat of the sound that's playing. It never records you and nothing leaves your phone.",
                audioOk, if (audioOk) null else { { mic.launch(Manifest.permission.RECORD_AUDIO) } })
            Gap(12.dp)
            PermCard("Keep running", "Lets Pulsify keep your wallpaper alive in the background, so Android doesn't close it and leave a blank screen. A live wallpaper only draws while it's on screen, so it stays light on battery.",
                batteryOk, if (batteryOk) null else { { Perms.askBattery(ctx) } })
            if (Perms.isXiaomi) {
                Gap(12.dp)
                PermCard("Xiaomi battery saver", "Xiaomi, Redmi and Poco phones add their own battery saver on top. On the screen that opens, set Battery saver to \"No restrictions\" for Pulsify.",
                    xiaomiOpened, { askXiaomi() }, doneLabel = "Opened")
            }
            Gap(140.dp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (next != null) {
                GlowButton("Allow", Modifier.fillMaxWidth()) { next() }
                Gap(6.dp)
                Text("Skip for now", style = T.small, color = c.sub, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(10.dp).pressable { onDone() })
            } else {
                GlowButton("Continue", Modifier.fillMaxWidth()) { onDone() }
            }
        }
    }
}

@Composable
private fun PermCard(title: String, why: String, done: Boolean, onAllow: (() -> Unit)?, doneLabel: String = "Allowed") {
    val c = LocalPulse.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121E))
            .border(1.dp, if (done) Color(0x554ADE80) else Color(0x22FFFFFF), RoundedCornerShape(22.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = T.bodyStrong, color = c.text)
            if (done) {
                Text(doneLabel.uppercase(), style = T.small, color = Color(0xFF4ADE80),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A2E2B)).padding(horizontal = 8.dp, vertical = 3.dp))
            } else if (onAllow != null) {
                Text("Allow", style = T.small, color = Color(0xFF0D0A14),
                    modifier = Modifier.clip(CircleShape).background(c.accent).pressable { onAllow() }.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        Gap(8.dp)
        Text(why, style = T.small, color = c.sub)
    }
}
