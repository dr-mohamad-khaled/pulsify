package app.pulsify.ui

import android.Manifest
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pulsify.core.AudioFrame
import app.pulsify.core.DISCOVERIES
import app.pulsify.core.Mood
import app.pulsify.core.Pulse
import app.pulsify.core.SKY_VARIANTS
import app.pulsify.engine.GlLoop

private enum class Dock { OPEN, MINI, HIDDEN }
private enum class Tab(val label: String) { LOOK("Look"), TRICKS("Tricks"), MOOD("Mood"), MUSIC("Music") }

val ACCENT_PRESETS = listOf(
    0, // auto: follow the wallpaper
    0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt(), 0xFF2DD4BF.toInt(), 0xFFFF5FA2.toInt(),
    0xFF5B8CFF.toInt(), 0xFF4ADE80.toInt(), 0xFFFF6B5B.toInt(), 0xFFF5E663.toInt(),
)

@Composable
fun StudioScreen(state: AppState, onBack: () -> Unit) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    var dock by remember { mutableStateOf(Dock.OPEN) }
    var tab by remember { mutableStateOf(Tab.LOOK) }
    var loop by remember { mutableStateOf<GlLoop?>(null) }
    var bass by remember { mutableFloatStateOf(0f) }
    var source by remember { mutableStateOf(AudioFrame.Source.NONE) }
    val spectrum = remember { FloatArray(32) }
    var tick by remember { mutableIntStateOf(0) }
    var fps by remember { mutableFloatStateOf(0f) }

    // Mirror the engine's audio into the UI (spectrum, CTA glow) every frame.
    LaunchedEffect(loop, tab, dock) {
        val l = loop ?: return@LaunchedEffect
        var lastFps = 0L
        // Only the Music panel needs per-frame UI updates; elsewhere 4 per second is plenty.
        val live = tab == Tab.MUSIC && dock == Dock.OPEN
        while (true) {
            if (!live) kotlinx.coroutines.delay(250)
            withFrameNanos { t ->
                val a = l.input.audio
                bass = a.bass
                source = a.source
                System.arraycopy(a.spectrum, 0, spectrum, 0, 32)
                tick++
                if (t - lastFps > 500_000_000L) { fps = l.fps; lastFps = t }
            }
        }
    }
    // Studio overrides end when you leave.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            Pulse.previewMood = null; Pulse.previewCharging = false; Pulse.previewLowBattery = false
            Pulse.audio.demo = false
        }
    }

    var rootH by remember { mutableIntStateOf(1) }
    var dockTop by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(loop, dock, dockTop, rootH) {
        val l = loop ?: return@LaunchedEffect
        l.setBottomInset(if (dock == Dock.HIDDEN || dockTop <= 0f) 0f else ((rootH - dockTop) / rootH).coerceIn(0f, 0.8f))
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootH = it.height.coerceAtLeast(1) }) {
        LiveSky(Modifier.fillMaxSize(), renderScale = 0.65f, interactive = true, onLoop = { loop = it })

        // Top bar.
        AnimatedVisibility(
            dock != Dock.HIDDEN,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    MicroLabel("Live world · ${fps.toInt()} fps", color = c.teal)
                    Text(state.displayName, style = T.heading, color = c.text)
                }
                Box(
                    Modifier.height(38.dp).clip(RoundedCornerShape(50)).background(c.surface2.copy(alpha = 0.9f))
                        .border(1.dp, c.border, RoundedCornerShape(50)).pressable { dock = Dock.HIDDEN }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Hide", style = T.bodyStrong, color = c.text) }
            }
        }

        // Bottom dock.
        AnimatedVisibility(
            dock != Dock.HIDDEN,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { dockTop = it.positionInRoot().y }
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Brush.verticalGradient(listOf(c.surface2.copy(alpha = 0.94f), c.surface.copy(alpha = 0.97f))))
                    .border(1.dp, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.45f), Color.Transparent)), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 14.dp),
            ) {
                // Drag handle: down closes the panel, down again hides the dock; up reopens.
                var drag by remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag > 50f) dock = if (dock == Dock.OPEN) Dock.MINI else Dock.HIDDEN
                                else if (drag < -50f) dock = Dock.OPEN
                            },
                        ) { _, dy -> drag += dy }
                    }.pressable(haptic = false) { dock = if (dock == Dock.OPEN) Dock.MINI else Dock.OPEN },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(50)).background(c.faint)) }

                Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tab.values().forEach { t ->
                        val sel = dock == Dock.OPEN && tab == t
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (sel) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                                .border(1.dp, if (sel) c.accent.copy(alpha = 0.55f) else Color.Transparent, RoundedCornerShape(14.dp))
                                .pressable { tab = t; dock = Dock.OPEN },
                            contentAlignment = Alignment.Center,
                        ) { Text(t.label, style = T.bodyStrong, color = if (sel) c.accent else c.sub) }
                    }
                }

                AnimatedVisibility(dock == Dock.OPEN, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "panel") { t ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                            when (t) {
                                Tab.LOOK -> LookPanel(state)
                                Tab.TRICKS -> TricksPanel(state, loop)
                                Tab.MOOD -> MoodPanel()
                                Tab.MUSIC -> MusicPanel(state, source, spectrum, tick)
                            }
                        }
                    }
                }

                Gap(10.dp)
                var live by remember { mutableStateOf(AppState.isOurWallpaperActive(ctx)) }
                LaunchedEffect(Unit) { while (true) { live = AppState.isOurWallpaperActive(ctx); kotlinx.coroutines.delay(800) } }
                GlowButton(
                    if (live) "Live on your home screen  ✓" else "Set as live wallpaper",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    pulse = bass,
                ) {
                    if (!AppState.applyWallpaper(ctx)) state.toast = AppState.Toast("Already live", "Every change you make here is on your home screen right now.")
                }
            }
        }

        // Show-controls pill when the UI is hidden.
        AnimatedVisibility(
            dock == Dock.HIDDEN,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 18.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .pressable { dock = Dock.OPEN }.padding(horizontal = 18.dp, vertical = 10.dp),
            ) { Text("Show controls", style = T.bodyStrong, color = Color.White.copy(alpha = 0.8f)) }
        }
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalPulse.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(c.surface2.copy(alpha = 0.9f)).border(1.dp, c.border, CircleShape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun LookPanel(state: AppState) {
    val c = LocalPulse.current
    Column {
        MicroLabel("Creature")
        Gap(8.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.pulsify.engine.CREATURES.forEach { cr -> Pill(cr.name, state.character == cr.id) { state.applyCharacter(cr.id) } }
            Pill("Just the sky", state.character == 0) { state.applyCharacter(0) }
        }
        val info = app.pulsify.engine.creatureInfo(state.character)
        if (info != null) {
            Gap(12.dp)
            MicroLabel(if (info.id == 4) "Species" else "Coat")
            Gap(8.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                info.coats.forEachIndexed { i, coat ->
                    val sel = state.coat == i
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable { state.applyCoat(i) }) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color(coat.base), Color(coat.base), Color(coat.rim))))
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) c.accent else c.border, CircleShape),
                        )
                        Gap(4.dp)
                        Text(coat.name, style = T.small, color = if (sel) c.text else c.sub)
                    }
                }
            }
        }
        Gap(14.dp)
        val envInfo = app.pulsify.engine.creatureInfo(state.character)
        if (envInfo != null) {
            MicroLabel("World")
            Gap(8.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                envInfo.envs.forEachIndexed { i, e ->
                    EnvTile(e, selected = state.env == i, modifier = Modifier.width(104.dp)) { state.applyEnv(i) }
                }
            }
        } else {
            MicroLabel("Sky")
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SKY_VARIANTS.forEach { v ->
                    val sel = state.variant == v.id
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .border(if (sel) 1.5.dp else 1.dp, if (sel) c.accent else c.border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .pressable { state.applyVariant(v.id) },
                    ) {
                        MiniSky(Color(v.accent1), Color(v.accent2), seed = v.id * 31 + 7, modifier = Modifier.fillMaxWidth().height(56.dp), style = v.id)
                        Text(v.name, style = T.small, color = if (sel) c.text else c.sub, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
        Gap(14.dp)
        MicroLabel("Colour")
        Gap(8.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ACCENT_PRESETS.forEach { argb ->
                val sel = state.accent == argb
                val v = SKY_VARIANTS[state.variant.coerceIn(0, 2)]
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(
                            if (argb == 0) Brush.sweepGradient(listOf(Color(v.accent1), Color(v.accent2), Color(v.accent1)))
                            else Brush.linearGradient(listOf(Color(argb), Color(app.pulsify.engine.SkyScene.shiftHue(argb, 38f)))),
                        )
                        .border(if (sel) 2.5.dp else 0.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                        .pressable { state.applyAccent(argb) },
                    contentAlignment = Alignment.Center,
                ) { if (argb == 0) Text("A", style = T.micro, color = Color.Black) }
            }
        }
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("Brightness")
            Spacer(Modifier.weight(1f))
            Text("${(state.brightness * 100).toInt()}%", style = T.small, color = c.sub)
        }
        var b by remember { mutableFloatStateOf(state.brightness) }
        Slider(
            value = b, onValueChange = { b = it; state.applyBrightness(it) }, valueRange = 0.6f..1.4f,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.border),
        )
    }
}

@Composable
private fun TricksPanel(state: AppState, loop: GlLoop?) {
    val c = LocalPulse.current
    val info = app.pulsify.engine.creatureInfo(state.character)
    Column {
        if (info == null || info.tricks.isEmpty()) {
            MicroLabel("Just the sky")
            Gap(6.dp)
            Text("Tap the sky for a burst. Hold it for a shooting star. Pick a creature in Look to unlock tricks.", style = T.body, color = c.sub)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("Ask ${info.name}")
            Spacer(Modifier.weight(1f))
            Text("tap one", style = T.small, color = c.faint)
        }
        Gap(10.dp)
        info.tricks.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(18.dp))
                            .background(Brush.verticalGradient(listOf(c.surface2, c.bg.copy(alpha = 0.7f))))
                            .border(1.dp, c.border.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                            .pressable { loop?.perform(t.id) }
                            .semantics { contentDescription = "Trick: ${t.label}" }
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(t.emoji, style = T.title)
                        Gap(4.dp)
                        Text(t.label, style = T.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Gap(8.dp)
        }
        Gap(2.dp)
        Text(info.hint, style = T.small, color = c.faint)
        if (info.id == 4) {
            Gap(14.dp)
            KiwiWords(state, loop)
        }
    }
}

@Composable
private fun KiwiWords(state: AppState, loop: GlLoop?) {
    val c = LocalPulse.current
    var text by remember { mutableStateOf("") }
    val taught = state.kiwiWords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    MicroLabel("Teach Kiwi to talk")
    Gap(8.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it.replace(",", " ").take(40) },
            singleLine = true,
            textStyle = T.body.copy(color = c.text),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(c.bg.copy(alpha = 0.7f))
                .border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 13.dp)
                .semantics { contentDescription = "Kiwi word" },
            decorationBox = { inner -> if (text.isEmpty()) Text("Your name, a joke, anything...", style = T.body, color = c.faint); inner() },
        )
        Spacer(Modifier.width(8.dp))
        GlowButton("Teach", Modifier.width(96.dp), height = 46.dp) {
            val w = text.trim()
            if (w.isNotEmpty()) {
                state.applyKiwiWords((taught.filter { it != w } + w).takeLast(12).joinToString(", "))
                loop?.perform("say:$w")
                text = ""
            }
        }
    }
    if (taught.isNotEmpty()) {
        Gap(8.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            taught.forEach { w ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.14f))
                        .pressable { loop?.perform("say:$w") }.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(w, style = T.small, color = c.text, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(c.surface2)
                            .pressable { state.applyKiwiWords(taught.filter { it != w }.joinToString(", ")) },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", style = T.small, color = c.sub) }
                }
            }
        }
    }
}

@Composable
private fun MoodPanel() {
    val c = LocalPulse.current
    var mood by remember { mutableStateOf(Pulse.previewMood) }
    var charging by remember { mutableStateOf(Pulse.previewCharging) }
    var low by remember { mutableStateOf(Pulse.previewLowBattery) }
    Column {
        MicroLabel("Time of day  ·  preview only")
        Gap(8.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Auto", mood == null) { mood = null; Pulse.previewMood = null }
            Mood.values().forEach { m -> Pill(m.label, mood == m) { mood = m; Pulse.previewMood = m } }
        }
        Gap(12.dp)
        ToggleRow("Pretend it's charging", "The horizon fills with green to your battery level", charging) {
            charging = it; Pulse.previewCharging = it
        }
        ToggleRow("Pretend battery is low", "The sky dims and slows down to save power", low) {
            low = it; Pulse.previewLowBattery = it
        }
        Gap(4.dp)
        Text("On your home screen the real clock and battery decide.", style = T.small, color = c.faint)
    }
}

@Composable
private fun MusicPanel(state: AppState, source: AudioFrame.Source, spectrum: FloatArray, tick: Int) {
    val c = LocalPulse.current
    var demo by remember { mutableStateOf(Pulse.audio.demo) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.applyMusicReact(true)
        else state.toast = AppState.Toast("Music reaction is off", "Without permission the sky still grooves gently when music plays.")
    }
    Column {
        ToggleRow(
            "React to my music",
            "Reads only the sound your phone is playing. It never records the microphone.",
            state.musicReact && Pulse.audio.hasPermission(),
        ) { on ->
            if (on && !Pulse.audio.hasPermission()) launcher.launch(Manifest.permission.RECORD_AUDIO)
            else state.applyMusicReact(on)
        }
        ToggleRow("Demo groove", "A built-in beat so you can feel it without music", demo) {
            demo = it; Pulse.audio.demo = it
        }
        Gap(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel("Spectrum")
            Spacer(Modifier.weight(1f))
            val label = when (source) {
                AudioFrame.Source.MUSIC -> "Your music"
                AudioFrame.Source.DEMO -> "Demo groove"
                AudioFrame.Source.GROOVE -> "Gentle groove"
                AudioFrame.Source.NONE -> "Silence"
            }
            StatusChip(label, live = source != AudioFrame.Source.NONE)
        }
        Gap(8.dp)
        Spectrum(spectrum, tick, Modifier.fillMaxWidth().height(64.dp))
    }
}

@Composable
private fun Spectrum(spectrum: FloatArray, tick: Int, modifier: Modifier) {
    val c = LocalPulse.current
    Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        if (tick < 0) return@Canvas // reading tick redraws every frame
        val n = spectrum.size
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n + 1)) / n
        for (i in 0 until n) {
            val v = spectrum[i].coerceIn(0.03f, 1f)
            val h = v * (size.height - gap * 2)
            val x = gap + i * (bw + gap)
            drawRoundRect(
                Brush.verticalGradient(listOf(c.accent2, c.accent, Color(0xFF9D4EDD)), startY = size.height - h, endY = size.height),
                topLeft = Offset(x, size.height - gap - h),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2),
            )
        }
    }
}

@Composable
fun ToggleRow(title: String, line: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalPulse.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = c.text)
            Text(line, style = T.small, color = c.sub)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
                uncheckedThumbColor = c.sub, uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.border,
            ),
        )
    }
}

/** A world thumbnail: a pre-rendered picture for painted worlds, a live mini sky for the sky shader. */
@Composable
fun EnvTile(e: app.pulsify.engine.EnvInfo, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalPulse.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val thumb = androidx.compose.runtime.remember(e.world) {
        if (e.world < 0) null else try {
            ctx.assets.open("thumbs/world_${e.world}.jpg").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } catch (_: Throwable) { null }
    }
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.accent else c.border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .androidx_semantics("Environment: ${e.name}")
            .pressable(onClick = onClick),
    ) {
        if (thumb != null) {
            androidx.compose.foundation.Image(
                thumb, contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            MiniSky(Color(e.c1), Color(e.c2), seed = e.name.hashCode(), modifier = Modifier.fillMaxWidth().height(72.dp), style = e.skyVariant)
        }
        Text(e.name, style = T.small, color = if (selected) c.text else c.sub, modifier = Modifier.padding(8.dp), maxLines = 1)
    }
}

private fun Modifier.androidx_semantics(desc: String): Modifier =
    this.then(Modifier.semantics(mergeDescendants = true) { contentDescription = desc })
