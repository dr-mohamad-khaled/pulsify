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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.produceState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
fun HomeScreen(state: AppState, onOpenStudio: () -> Unit, onSettings: () -> Unit, onFriends: () -> Unit = {}) {
    val c = LocalPulse.current
    var loop by remember { mutableStateOf<GlLoop?>(null) }
    fun openCreature(id: Int) { state.applyCharacter(id); onOpenStudio() }

    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 22.dp)) {
                Gap(10.dp)
                Header(onSettings, Modifier.enter(0))
                Gap(26.dp)
                Greeting(Modifier.enter(1))
                Gap(18.dp)
                CrashCard()
            }
        }
        item {
            Hero(state, onOpenStudio, onLoop = { loop = it }, loop = loop, modifier = Modifier.padding(horizontal = 14.dp).enter(2))
            Gap(22.dp)
            FriendsTeaser(onFriends, Modifier.padding(horizontal = 14.dp).enter(3))
            Gap(36.dp)
        }
        item {
            Shelf("Creatures", "${app.pulsify.engine.CREATURES.size} friends who live on your home screen", Modifier.enter(3)) {
                items(app.pulsify.engine.CREATURES) { cr ->
                    PosterCard(
                        "thumbs/creature_${cr.id}.jpg", cr.name, cr.line, Color(cr.accent),
                        live = state.video < 0 && state.character == cr.id,
                    ) { openCreature(cr.id) }
                }
            }
        }
        item {
            Shelf("Video loops", "Endless cinematic loops, no sound", Modifier.enter(4)) {
                items(app.pulsify.engine.VIDEO_LOOPS) { v ->
                    PosterCard("thumbs/loop_${v.id}.jpg", v.name, v.line, Color(v.c1), live = state.video == v.id, tall = true) {
                        state.applyVideo(v.id); onOpenStudio()
                    }
                }
            }
        }
        item {
            Shelf("Skies", "Just the sky, reacting to touch and music", Modifier.enter(5)) {
                items(SKY_VARIANTS) { v ->
                    PosterCard(
                        if (v.id == 2) "thumbs/sky_2.jpg" else null, v.name, v.tagline, Color(v.accent1),
                        live = state.video < 0 && state.character == 0 && state.variant == v.id, sky = v.id, sky2 = Color(v.accent2),
                    ) { state.applyCharacter(0); state.applyVariant(v.id); onOpenStudio() }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 22.dp).enter(6)) {
                Text("Being made", style = T.title, color = c.text)
                Gap(4.dp)
                Text("Hand-built one at a time.", style = T.small, color = c.sub)
                Gap(14.dp)
                COMING.forEach { cs ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(cs.c1), Color(cs.c2)))))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cs.name, style = T.bodyStrong, color = c.text)
                            Text(cs.line, style = T.small, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(cs.kind.label.dropLast(1), style = T.micro, color = c.faint)
                    }
                }
            }
        }
        item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun Header(onSettings: () -> Unit, modifier: Modifier) {
    val c = LocalPulse.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OrbLogo(28.dp)
        Spacer(Modifier.width(10.dp))
        Text("Pulsify", style = T.name.copy(fontSize = 24.sp), color = c.text)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape).pressable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = c.sub, modifier = Modifier.size(19.dp)) }
    }
}

@Composable
private fun Greeting(modifier: Modifier) {
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
    val hello = when (DeviceState.moodNow()) {
        Mood.MORNING -> "Good morning"
        Mood.DAY -> "Good afternoon"
        Mood.EVENING -> "Good evening"
        Mood.NIGHT -> "Still up?"
    }
    val today = LocalDate.now()
    val date = today.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.ENGLISH) + ", " + today.month.getDisplayName(JTextStyle.FULL, Locale.ENGLISH) + " " + today.dayOfMonth
    Column(modifier) {
        Text(hello, style = T.hero, color = c.text)
        Gap(8.dp)
        Text(date + (if (charging) ", charging at " else ", battery at ") + "${(battery * 100).toInt()}%", style = T.body, color = c.sub)
    }
}

@Composable
private fun Hero(state: AppState, onOpenStudio: () -> Unit, onLoop: (GlLoop) -> Unit, loop: GlLoop?, modifier: Modifier) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    var live by remember { mutableStateOf(AppState.isOurWallpaperActive(ctx)) }
    LaunchedEffect(loop) { while (true) { live = AppState.isOurWallpaperActive(ctx); delay(900) } }
    Box(
        modifier.fillMaxWidth().height(520.dp).clip(RoundedCornerShape(36.dp))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(36.dp)),
    ) {
        LiveSky(Modifier.fillMaxSize(), renderScale = 0.5f, onLoop = onLoop)
        Box(
            Modifier.fillMaxSize().pointerInput(loop) {
                detectTapGestures(
                    onTap = { o -> loop?.tap(o.x / size.width, o.y / size.height) },
                    onLongPress = { o -> loop?.longPress(o.x / size.width, o.y / size.height) },
                )
            },
        )
        StatusChip(if (live) "On your home screen" else "Preview", live = live, modifier = Modifier.padding(16.dp))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.6f), 1f to Color.Black.copy(alpha = 0.9f)))
                .padding(start = 22.dp, end = 18.dp, top = 70.dp, bottom = 20.dp),
        ) {
            AnimatedContent(state.displayName, transitionSpec = { (fadeIn() + androidx.compose.animation.slideInVertically { it / 3 }) togetherWith fadeOut() }, label = "name") { name ->
                Text(name, style = T.display.copy(fontSize = 46.sp, lineHeight = 46.sp), color = c.text)
            }
            Gap(6.dp)
            Text(app.pulsify.engine.videoLoop(state.video)?.line ?: moodLine(state.character), style = T.body, color = Color.White.copy(alpha = 0.78f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Gap(18.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowButton("Customize", Modifier.weight(1f), onClick = onOpenStudio)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(58.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .pressable { if (!AppState.applyWallpaper(ctx)) state.toast = AppState.Toast("Already your wallpaper", "Changes show on your home screen right away.") }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (live) "Live" else "Set wallpaper", style = T.button, color = c.text) }
            }
        }
    }
}

/** A titled horizontal shelf. */
@Composable
private fun Shelf(title: String, line: String, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val c = LocalPulse.current
    Column(modifier) {
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text(title, style = T.title, color = c.text)
            Gap(3.dp)
            Text(line, style = T.small, color = c.sub)
        }
        Gap(14.dp)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
        Gap(34.dp)
    }
}

/** A poster: the real picture, the name set large over it, one line under. */
@Composable
private fun PosterCard(asset: String?, name: String, line: String, tint: Color, live: Boolean, tall: Boolean = false, sky: Int = -1, sky2: Color = tint, onOpen: () -> Unit) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    val pic by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, asset) {
        value = if (asset == null) null else withContext(Dispatchers.IO) { decodeSmall(ctx, asset, 300) }
    }
    Column(Modifier.width(if (tall) 150.dp else 168.dp).pressable(scaleDown = 0.96f, onClick = onOpen)) {
        Box(
            Modifier.fillMaxWidth().height(if (tall) 250.dp else 224.dp).clip(RoundedCornerShape(26.dp))
                .border(if (live) 2.dp else 1.dp, if (live) tint else Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp)),
        ) {
            val p = pic
            if (p != null) androidx.compose.foundation.Image(p, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else MiniSky(tint, sky2, seed = name.hashCode(), modifier = Modifier.fillMaxSize(), style = if (sky >= 0) sky else 2)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
            Text(name, style = T.name.copy(fontSize = 24.sp, lineHeight = 24.sp), color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 10.dp, bottom = 12.dp), maxLines = 2)
            if (live) Row(
                Modifier.padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(c.mint))
                Spacer(Modifier.width(6.dp))
                Text("Yours", style = T.micro, color = Color.White)
            }
        }
        Gap(8.dp)
        Text(line, style = T.small, color = c.sub, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

private fun moodLine(character: Int): String {
    val d = Pulse.device
    if (character == 2) return when {
        d.charging -> "Charging. Kalboob is licking his glowing bone. Try taking it."
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
    if (character == 7) return when {
        d.charging -> "Charging. Nutty is hugging a glowing acorn and nibbling it."
        d.batteryLevel < 0.15f -> "Low battery. Nutty is guarding its very last acorn."
        else -> when (DeviceState.moodNow()) {
            Mood.NIGHT -> "Nutty is curled up asleep under its own tail."
            else -> "Nutty is busy burying things it will forget about."
        }
    }
    if (character == 8) return when {
        d.charging -> "Charging. Bolt plugged itself into the wall."
        d.batteryLevel < 0.15f -> "Low battery. Bolt's screen is flickering."
        else -> when (DeviceState.moodNow()) {
            Mood.NIGHT -> "Bolt is in sleep mode. Screensaver on."
            else -> "Bolt is rolling around. Hold a finger near it."
        }
    }
    if (character == 9) return when {
        d.charging -> "Charging. The gang is passing a hot battery around."
        d.batteryLevel < 0.15f -> "Low battery. The gang is melting a little."
        else -> when (DeviceState.moodNow()) {
            Mood.NIGHT -> "The gang is asleep in a jelly pile."
            else -> "The gang is up to something. Fling one."
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
