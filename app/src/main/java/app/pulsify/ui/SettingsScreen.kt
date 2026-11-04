package app.pulsify.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pulsify.core.Pulse
import app.pulsify.core.SKY_VARIANTS
import app.pulsify.engine.SkyScene

@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    val c = LocalPulse.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.setMusicReact(true)
    }
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp),
    ) {
        Gap(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(14.dp))
            Text("Settings", style = T.title, color = c.text)
        }
        Gap(22.dp)

        MicroLabel("Music")
        Gap(8.dp)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ToggleRow(
                    "React to my music",
                    "Uses Android's Visualizer to read only what your phone is playing. Nothing is recorded or sent anywhere.",
                    state.musicReact && Pulse.audio.hasPermission(),
                ) { on ->
                    if (on && !Pulse.audio.hasPermission()) launcher.launch(Manifest.permission.RECORD_AUDIO) else state.setMusicReact(on)
                }
            }
        }
        Gap(20.dp)

        MicroLabel("Battery")
        Gap(8.dp)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ToggleRow("Battery saver", "Caps the wallpaper at 30 fps and a lower resolution", state.batterySaver) { state.setBatterySaver(it) }
                ToggleRow("Shuffle on unlock", "A different sky each time you unlock your phone", state.shuffle) { state.setShuffle(it) }
                Gap(6.dp)
                Text(
                    "The wallpaper runs at 60 fps only while you touch it or music plays, 30 fps idle, 15 fps at night, and stops completely when it's hidden.",
                    style = T.small, color = c.faint,
                )
                Gap(8.dp)
            }
        }
        Gap(20.dp)

        MicroLabel("Theme colour")
        Gap(8.dp)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("The whole app takes its gradients from one colour.", style = T.small, color = c.sub)
                Gap(12.dp)
                ACCENT_PRESETS.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { argb ->
                            val sel = state.accent == argb
                            val v = SKY_VARIANTS[state.variant.coerceIn(0, 2)]
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(
                                        if (argb == 0) Brush.sweepGradient(listOf(Color(v.accent1), Color(v.accent2), Color(v.accent1)))
                                        else Brush.linearGradient(listOf(Color(argb), Color(SkyScene.shiftHue(argb, 38f)))),
                                    )
                                    .border(if (sel) 2.5.dp else 0.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                                    .pressable { state.setAccent(argb) },
                                contentAlignment = Alignment.Center,
                            ) { if (argb == 0) Text("AUTO", style = T.micro, color = Color.Black) }
                        }
                    }
                    Gap(12.dp)
                }
            }
        }
        Gap(20.dp)
        Text("Pulsify 3.0 · milestone 1", style = T.small, color = c.faint)
        Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp))
    }
}
