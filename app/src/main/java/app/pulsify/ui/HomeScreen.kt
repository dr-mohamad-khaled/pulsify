package app.pulsify.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pulsify.core.COMING
import app.pulsify.core.ComingSoon
import app.pulsify.core.DISCOVERIES
import app.pulsify.core.DeviceState
import app.pulsify.core.Kind
import app.pulsify.core.Mood
import app.pulsify.core.Pulse
import app.pulsify.core.SKY_VARIANTS
import app.pulsify.engine.GlLoop
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun HomeScreen(state: AppState, onOpenStudio: () -> Unit, onSettings: () -> Unit) {
    val c = LocalPulse.current
    var filter by remember { mutableStateOf<Kind?>(null) }
    var loop by remember { mutableStateOf<GlLoop?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().background(c.bg),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp)) {
                Gap(10.dp)
                Header(state, onSettings, Modifier.enter(0))
                Gap(22.dp)
                Greeting(state, Modifier.enter(1))
                Gap(18.dp)
                CrashCard()
            }
        }
        item {
            Column {
                Hero(state, onOpenStudio, onLoop = { loop = it }, loop = loop, modifier = Modifier.padding(horizontal = 16.dp).enter(2))
                Gap(28.dp)
            }
        }
        item {
            Column {
                Column(Modifier.padding(horizontal = 20.dp).enter(4)) {
                    MicroLabel("Explore")
                    Gap(4.dp)
                    Text("Pick your vibe", style = T.title, color = c.text)
                    Gap(14.dp)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).enter(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("All", filter == null) { filter = null }
                    Kind.values().forEach { k -> Pill(k.label, filter == k) { filter = k } }
                }
                Gap(16.dp)
            }
        }

        val tiles = buildTiles(filter)
        tiles.chunked(2).forEachIndexed { row, pair ->
            item {
                Column {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { i, tile ->
                        TileCard(
                            tile, state,
                            modifier = Modifier.weight(1f).enter(5 + row * 2 + i, 40L),
                            onOpen = {
                                if (tile.video >= 0) { state.applyVideo(tile.video); onOpenStudio() }
                                else if (tile.creature > 0) { state.applyCharacter(tile.creature); onOpenStudio() }
                                else if (tile.variant != null) { state.applyCharacter(0); state.applyVariant(tile.variant); onOpenStudio() }
                                else tile.coming?.let { cs ->
                                    state.toast = AppState.Toast("${cs.name} is in the lab", "Arrives in milestone ${cs.milestone}. Each one is hand-built, one at a time.")
                                }
                            },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Gap(12.dp)
                }
            }
        }
        item {
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Header(state: AppState, onSettings: () -> Unit, modifier: Modifier) {
    val c = LocalPulse.current
    val musicOn = state.musicReact && Pulse.audio.hasPermission()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OrbLogo(30.dp)
        Spacer(Modifier.width(10.dp))
        Text("Pulsify", style = T.title, color = c.text)
        Spacer(Modifier.weight(1f))
        StatusChip("Amoled", live = true, color = c.teal)
        Spacer(Modifier.width(6.dp))
        StatusChip(if (musicOn) "Music sync" else "Music off", live = musicOn)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).pressable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = c.sub, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun Greeting(state: AppState, modifier: Modifier) {
    val c = LocalPulse.current
    var battery by remember { mutableFloatStateOf(Pulse.device.batteryLevel) }
    var charging by remember { mutableStateOf(Pulse.device.charging) }
    LaunchedEffect(Unit) {
        while (true) {
            Pulse.device.poll()
            battery = Pulse.device.batteryLevel; charging = Pulse.device.charging
            delay(15_000)
        }
    }
    val mood = DeviceState.moodNow()
    val hello = when (mood) {
        Mood.MORNING -> "Good morning"
        Mood.DAY -> "Good afternoon"
        Mood.EVENING -> "Good evening"
        Mood.NIGHT -> "Still up?"
    }
    val today = LocalDate.now()
    val date = today.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH) + ", " +
        today.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH) + " " + today.dayOfMonth
    Column(modifier) {
        Text(hello, style = T.display, color = c.text)
        Gap(6.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$date  ·  ${(battery * 100).toInt()}%" + (if (charging) " charging" else ""), style = T.body, color = c.sub)
        }
    }
}

@Composable
private fun Hero(state: AppState, onOpenStudio: () -> Unit, onLoop: (GlLoop) -> Unit, loop: GlLoop?, modifier: Modifier) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    var fps by remember { mutableFloatStateOf(0f) }
    var live by remember { mutableStateOf(AppState.isOurWallpaperActive(ctx)) }
    LaunchedEffect(loop) {
        while (true) {
            fps = loop?.fps ?: 0f
            live = AppState.isOurWallpaperActive(ctx)
            delay(700)
        }
    }
    Box(
        modifier.fillMaxWidth().height(460.dp).clip(RoundedCornerShape(32.dp))
            .border(1.dp, Brush.linearGradient(listOf(c.accent.copy(alpha = 0.5f), Color.Transparent, c.accent2.copy(alpha = 0.4f))), RoundedCornerShape(32.dp)),
    ) {
        LiveSky(Modifier.fillMaxSize(), renderScale = 0.5f, onLoop = onLoop)
        // Taps on the hero go straight into the sky; scrolling still works.
        Box(
            Modifier.fillMaxSize().pointerInput(loop) {
                detectTapGestures(
                    onTap = { o -> loop?.tap(o.x / size.width, o.y / size.height) },
                    onLongPress = { o -> loop?.longPress(o.x / size.width, o.y / size.height) },
                )
            },
        )
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(if (live) "On your home screen" else "Preview", live = live)
            Spacer(Modifier.width(6.dp))
            if (fps > 0f) StatusChip("${fps.toInt()} fps", live = true, color = c.teal)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f), Color.Black)))
                .padding(start = 20.dp, end = 16.dp, top = 56.dp, bottom = 18.dp),
        ) {
            MicroLabel("Your wallpaper", color = c.accent)
            Gap(4.dp)
            AnimatedContent(state.displayName, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "name") { name ->
                Text(name, style = T.display, color = c.text)
            }
            Gap(2.dp)
            Text(app.pulsify.engine.videoLoop(state.video)?.line ?: moodLine(state.character), style = T.body, color = c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Gap(16.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowButton("Open Studio", Modifier.weight(1f), onClick = onOpenStudio)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.accent.copy(alpha = 0.5f), CircleShape)
                        .pressable {
                            if (!AppState.applyWallpaper(ctx)) state.toast = AppState.Toast("Already live", "Your home screen updates the moment you change anything.")
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Set as wallpaper", tint = c.accent) }
            }
            Gap(8.dp)
            Text(app.pulsify.engine.creatureInfo(state.character)?.hint ?: "Tap the sky. Hold it. Swipe fast. See what happens.", style = T.small, color = c.faint)
        }
    }
}

private fun moodLine(character: Int): String {
    val d = Pulse.device
    if (character == 2) return when {
        d.charging -> "Charging. Kalboob is chewing a glowing bone."
        d.batteryLevel < 0.15f -> "Low battery. Kalboob is lying down with sad eyes."
        else -> when (DeviceState.moodNow()) {
            Mood.MORNING -> "Kalboob is stretching. Walk time?"
            Mood.DAY -> "Kalboob wants to play. Throw the ball."
            Mood.EVENING -> "Kalboob is chilling. Belly rubs welcome."
            Mood.NIGHT -> "Shh. Kalboob is dreaming about squirrels."
        }
    }
    if (character == 5) return when {
        d.charging -> "Charging. Glowy's lure turned into a little light bulb."
        d.batteryLevel < 0.15f -> "Low battery. Glowy's light is flickering."
        else -> when (DeviceState.moodNow()) {
            Mood.NIGHT -> "Glowy is asleep, glowing softly in the dark."
            else -> "Glowy is drifting around. Wiggle a finger for the lure."
        }
    }
    if (character == 6) return when {
        d.charging -> "Charging. Gromble is hugging a jar of lightning."
        d.batteryLevel < 0.15f -> "Low battery. Gromble has fainted dramatically."
        else -> when (DeviceState.moodNow()) {
            Mood.NIGHT -> "Gromble is asleep with a teddy he says isn't his."
            else -> "Gromble is plotting something. It will not work."
        }
    }
    if (character == 4) return when {
        d.charging -> "Charging. Kiwi is nibbling a glowing millet spray."
        d.batteryLevel < 0.15f -> "Low battery. Kiwi is fluffed up and squawking about it."
        else -> when (DeviceState.moodNow()) {
            Mood.MORNING -> "Kiwi is up early and very loud about it."
            Mood.DAY -> "Kiwi wants to talk. Teach it a word."
            Mood.EVENING -> "Kiwi is preening for the evening."
            Mood.NIGHT -> "Kiwi is asleep on one leg. Shh."
        }
    }
    if (character == 3) return when {
        d.charging -> "Charging. Kitty is napping on a warm pad."
        d.batteryLevel < 0.15f -> "Low battery. Kitty is in power-saving loaf mode."
        else -> when (DeviceState.moodNow()) {
            Mood.MORNING -> "Kitty is stretching. Breakfast was 5 minutes late."
            Mood.DAY -> "Kitty is pretending not to watch you. Drag a finger."
            Mood.EVENING -> "Kitty is in a loaf. Do not disturb (please disturb)."
            Mood.NIGHT -> "Kitty's eyes are glowing. It sees everything."
        }
    }
    if (character == 1) return when {
        d.charging -> "Charging. Mochi is sipping its battery juice."
        d.batteryLevel < 0.15f -> "Low battery. Mochi is running on fumes."
        else -> when (DeviceState.moodNow()) {
            Mood.MORNING -> "Mochi is waking up with a big stretch."
            Mood.DAY -> "Mochi is bored. Go poke it."
            Mood.EVENING -> "Mochi is winding down for the evening."
            Mood.NIGHT -> "Shh. Mochi is asleep."
        }
    }
    return when {
        d.charging -> "Charging. The horizon glows with your battery."
        d.batteryLevel < 0.15f -> "Low battery. The stars are dimming to save power."
        else -> when (DeviceState.moodNow()) {
            Mood.MORNING -> "Morning light is creeping in."
            Mood.DAY -> "Clear sky. Try catching a shooting star."
            Mood.EVENING -> "Evening. The stars are coming out."
            Mood.NIGHT -> "The sky is half asleep. Shooting stars fall more often now."
        }
    }
}

private class Tile(val title: String, val line: String, val label: String, val c1: Int, val c2: Int, val style: Int, val variant: Int? = null, val coming: ComingSoon? = null, val creature: Int = 0, val video: Int = -1)

private fun buildTiles(filter: Kind?): List<Tile> {
    val out = mutableListOf<Tile>()
    if (filter == null || filter == Kind.CREATURE) {
        app.pulsify.engine.CREATURES.forEach { cr ->
            out += Tile(cr.name, cr.line, "Live creature", cr.coats[0].base, cr.accent, 3, creature = cr.id)
        }
    }
    if (filter == null || filter == Kind.WORLD) {
        SKY_VARIANTS.forEach { v -> out += Tile(v.name, v.tagline, "Live world", v.accent1, v.accent2, v.id, variant = v.id) }
    }
    if (filter == null || filter == Kind.LOOP) {
        app.pulsify.engine.VIDEO_LOOPS.forEach { v -> out += Tile(v.name, v.line, "Video loop", v.c1, v.c2, 2, video = v.id) }
    }
    COMING.filter { filter == null || it.kind == filter }.forEach { cs ->
        out += Tile(cs.name, cs.line, "${cs.kind.label.dropLast(1)} · M${cs.milestone}", cs.c1, cs.c2, if (cs.kind == Kind.CREATURE) 3 else 2, coming = cs)
    }
    return out
}

@Composable
private fun TileCard(tile: Tile, state: AppState, modifier: Modifier, onOpen: () -> Unit) {
    val c = LocalPulse.current
    val selected = if (tile.video >= 0) state.video == tile.video
        else if (state.video >= 0) false
        else if (tile.creature > 0) state.character == tile.creature else tile.variant != null && state.character == 0 && tile.variant == state.variant
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(c.surface)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) Brush.linearGradient(listOf(c.accent, c.accent2)) else Brush.linearGradient(listOf(c.border.copy(alpha = 0.7f), c.border.copy(alpha = 0.3f))),
                RoundedCornerShape(24.dp),
            )
            .pressable(onClick = onOpen),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.92f)) {
            val ctx = LocalContext.current
            val pic = remember(tile.creature, tile.video, tile.variant) {
                when {
                    tile.video >= 0 -> loadThumb(ctx, "thumbs/loop_${tile.video}.jpg")
                    tile.creature > 0 -> loadThumb(ctx, "thumbs/creature_${tile.creature}.jpg")
                    tile.variant != null -> loadThumb(ctx, "thumbs/sky_${tile.variant}.jpg")
                    else -> null
                }
            }
            if (pic != null) {
                androidx.compose.foundation.Image(pic, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                MiniSky(Color(tile.c1), Color(tile.c2), seed = tile.title.hashCode(), modifier = Modifier.fillMaxSize(), style = tile.style)
            }
            Row(Modifier.padding(10.dp)) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text(tile.label.uppercase(), style = T.micro, color = if (tile.coming == null) c.mint else c.sub) }
            }
            if (tile.coming != null) {
                Icon(Icons.Default.Lock, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(14.dp))
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(tile.title, style = T.heading, color = c.text, maxLines = 1)
            Gap(2.dp)
            Text(tile.line, style = T.small, color = c.sub, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Shown once after a crash: what happened, and a button to copy the details for a bug report. */
@Composable
private fun CrashCard() {
    val c = LocalPulse.current
    var report by remember { mutableStateOf(app.pulsify.core.CrashLog.lastCrash()) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val r = report ?: return
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 16.dp), rim = 0.6f) {
        Column(Modifier.padding(16.dp)) {
            MicroLabel("Sorry about that", color = c.amber)
            Gap(4.dp)
            Text("Pulsify closed unexpectedly last time", style = T.heading, color = c.text)
            Gap(4.dp)
            Text(r.lines().firstOrNull { it.contains("Exception") || it.contains("Error") || it.contains("crash") || it.contains("ANR") } ?: "Unknown error", style = T.small, color = c.sub, maxLines = 3)
            Gap(4.dp)
            Text("Tap Copy details and send it to the developer.", style = T.small, color = c.faint)
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(if (copied) "Copied" else "Copy details") {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(app.pulsify.core.CrashLog.diagnostics()))
                    copied = true
                }
                GhostButton("Dismiss") { app.pulsify.core.CrashLog.clear(); report = null }
            }
        }
    }
}
