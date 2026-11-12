package app.pulsify.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulsify.core.AudioFrame
import app.pulsify.core.Pulse
import app.pulsify.core.SKY_VARIANTS
import app.pulsify.engine.GlLoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the mode dial can show. Which ones appear depends on what is on the wallpaper. */
private enum class Mode(val label: String) { LOOK("Look"), WORLD("World"), TRICKS("Tricks"), TIME("Time"), SOUND("Sound"), SKY("Sky"), LOOPS("Loops") }

val ACCENT_PRESETS = listOf(
    0, // auto: follow the wallpaper
    0xFFCDB4FF.toInt(), 0xFFFFB36B.toInt(), 0xFF2DD4BF.toInt(), 0xFFFF5FA2.toInt(),
    0xFF5B8CFF.toInt(), 0xFF4ADE80.toInt(), 0xFFFF6B5B.toInt(), 0xFFF5E663.toInt(),
)

/**
 * The Studio: the live wallpaper fills the screen; the controls are a mode dial (swipe it like a
 * camera's) and one tactile panel at a time, on a soft scrim instead of a box.
 */
@Composable
fun StudioScreen(state: AppState, onBack: () -> Unit) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    var loop by remember { mutableStateOf<GlLoop?>(null) }
    var hidden by remember { mutableStateOf(false) }
    var bass by remember { mutableFloatStateOf(0f) }
    var source by remember { mutableStateOf(AudioFrame.Source.NONE) }
    val spectrum = remember { FloatArray(32) }
    var tick by remember { mutableIntStateOf(0) }

    val modes = when {
        state.video >= 0 -> listOf(Mode.LOOPS)
        app.pulsify.engine.creatureInfo(state.character) != null -> listOf(Mode.LOOK, Mode.WORLD, Mode.TRICKS, Mode.TIME, Mode.SOUND)
        else -> listOf(Mode.SKY, Mode.LOOK, Mode.TIME, Mode.SOUND)
    }
    var mode by remember { mutableStateOf(Mode.LOOK) }
    if (mode !in modes) mode = modes.first()

    LaunchedEffect(loop, mode) {
        val l = loop ?: return@LaunchedEffect
        val live = mode == Mode.SOUND
        while (true) {
            if (!live) kotlinx.coroutines.delay(200)
            withFrameNanos {
                val a = l.input.audio
                bass = a.bass; source = a.source
                System.arraycopy(a.spectrum, 0, spectrum, 0, 32)
                tick++
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            Pulse.previewMood = null; Pulse.previewHour = -1f; Pulse.previewCharging = false; Pulse.previewLowBattery = false
            Pulse.audio.demo = false
        }
    }
    var rootH by remember { mutableIntStateOf(1) }
    var dockTop by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(loop, hidden, dockTop, rootH) {
        val l = loop ?: return@LaunchedEffect
        l.setBottomInset(if (hidden || dockTop <= 0f) 0f else ((rootH - dockTop) / rootH).coerceIn(0f, 0.6f))
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { rootH = it.height.coerceAtLeast(1) }) {
        LiveSky(Modifier.fillMaxSize(), renderScale = 0.65f, interactive = true, onLoop = { loop = it })

        // Top: back, the name set big, hide.
        AnimatedVisibility(!hidden, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(state.displayName, transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() }, label = "nm") { n ->
                            Text(n, style = T.title, color = c.text, maxLines = 1)
                        }
                        Text(subtitle(state), style = T.small, color = c.sub, maxLines = 1)
                    }
                    GlassCircle(onClick = { hidden = true }) { EyeGlyph(c.text) }
                }
            }
        }

        // Bottom: panel, dial, action. No box: a long soft scrim lets the wallpaper breathe.
        AnimatedVisibility(
            !hidden, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 },
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { dockTop = it.positionInRoot().y + 140f }
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black.copy(alpha = 0.6f), 1f to Color.Black.copy(alpha = 0.92f)))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 40.dp, bottom = 12.dp),
            ) {
                AnimatedContent(
                    mode,
                    transitionSpec = {
                        val dir = if (modes.indexOf(targetState) > modes.indexOf(initialState)) 1 else -1
                        (fadeIn(tween(220)) + slideInHorizontally { it / 6 * dir }) togetherWith (fadeOut(tween(160)) + slideOutHorizontally { -it / 6 * dir })
                    },
                    label = "panel",
                ) { m ->
                    Box(Modifier.fillMaxWidth().height(236.dp)) {
                        when (m) {
                            Mode.LOOK -> LookPanel(state)
                            Mode.WORLD -> WorldPanel(state)
                            Mode.TRICKS -> TricksPanel(state, loop)
                            Mode.TIME -> TimePanel()
                            Mode.SOUND -> SoundPanel(state, source, spectrum, tick)
                            Mode.SKY -> SkyPanel(state)
                            Mode.LOOPS -> LoopsPanel(state)
                        }
                    }
                }
                Gap(6.dp)
                ModeDial(modes, mode) { mode = it }
                Gap(14.dp)
                var live by remember { mutableStateOf(AppState.isOurWallpaperActive(ctx)) }
                LaunchedEffect(Unit) { while (true) { live = AppState.isOurWallpaperActive(ctx); kotlinx.coroutines.delay(800) } }
                if (live) LiveBadge(Modifier.align(Alignment.CenterHorizontally))
                else GlowButton("Set as wallpaper", Modifier.fillMaxWidth().padding(horizontal = 20.dp), pulse = bass) {
                    if (!AppState.applyWallpaper(ctx)) state.toast = AppState.Toast("Already your wallpaper", "Changes show on your home screen right away.")
                }
            }
        }

        // Hidden: one quiet tap target brings the controls back.
        AnimatedVisibility(
            hidden, modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 22.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .pressable { hidden = false }.padding(horizontal = 20.dp, vertical = 11.dp),
            ) { Text("Show controls", style = T.bodyStrong, color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}

private fun subtitle(state: AppState): String {
    if (app.pulsify.engine.videoLoop(state.video) != null) return "Video loop"
    val info = app.pulsify.engine.creatureInfo(state.character) ?: return "Live sky"
    val env = info.envs.getOrNull(state.env)?.name ?: ""
    val coat = info.coats.getOrNull(state.coat)?.name ?: ""
    return "$coat in $env"
}

@Composable
private fun GlassCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "Hide the controls": an eye drawn as two arcs and a pupil. */
@Composable
private fun EyeGlyph(col: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.06f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.02f, w * 0.94f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.98f, w * 0.06f, h * 0.5f)
        }
        drawPath(p, col, style = Stroke(w * 0.08f))
        drawCircle(col, radius = w * 0.14f, center = Offset(w / 2, h / 2))
    }
}

/** Shown instead of the button once Pulsify is the wallpaper. */
@Composable
private fun LiveBadge(modifier: Modifier) {
    val c = LocalPulse.current
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
    Row(
        modifier.height(50.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).drawBehind {
            drawCircle(c.mint.copy(alpha = 0.35f * a), radius = size.minDimension * 1.3f)
            drawCircle(c.mint)
        })
        Spacer(Modifier.width(12.dp))
        Text("On your home screen now", style = T.bodyStrong, color = c.text)
    }
}

/** A camera-style mode dial: the chosen mode sits in the middle; swipe or tap to move. */
@Composable
private fun ModeDial(modes: List<Mode>, selected: Mode, onSelect: (Mode) -> Unit) {
    val c = LocalPulse.current
    val density = LocalDensity.current
    val centers = remember(modes) { FloatArray(modes.size) }
    var measured by remember(modes) { mutableIntStateOf(0) }
    var width by remember { mutableIntStateOf(0) }
    val idx = modes.indexOf(selected).coerceAtLeast(0)
    val target = if (width > 0 && measured > 0) width / 2f - centers[idx] else 0f
    val shift by animateFloatAsState(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "dial")
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(46.dp).onSizeChanged { width = it.width }
            .pointerInput(modes, selected) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        val step = with(density) { 36.dp.toPx() }
                        if (drag < -step && idx < modes.size - 1) onSelect(modes[idx + 1])
                        else if (drag > step && idx > 0) onSelect(modes[idx - 1])
                    },
                ) { _, d -> drag += d }
            },
    ) {
        Row(Modifier.offset { IntOffset(shift.roundToInt(), 0) }.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            modes.forEachIndexed { i, m ->
                val sel = i == idx
                val k by animateFloatAsState(if (sel) 1f else 0f, tween(260), label = "k")
                Column(
                    Modifier.onGloballyPositioned {
                        val px = it.parentLayoutCoordinates?.localPositionOf(it, Offset.Zero)?.x ?: 0f
                        centers[i] = px + it.size.width / 2f; measured++
                    }.pressable(scaleDown = 0.9f) { onSelect(m) }.padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        m.label, style = T.dial,
                        color = lerp(Color.White.copy(alpha = 0.38f), c.text, k),
                        modifier = Modifier.graphicsLayer { scaleX = 0.9f + 0.18f * k; scaleY = 0.9f + 0.18f * k },
                    )
                    Gap(5.dp)
                    Box(Modifier.size(5.dp).graphicsLayer { alpha = k }.drawBehind {
                        drawCircle(c.accent.copy(alpha = 0.5f), radius = size.minDimension * 1.6f)
                        drawCircle(c.accentInk)
                    })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Look

@Composable
private fun LookPanel(state: AppState) {
    val c = LocalPulse.current
    val info = app.pulsify.engine.creatureInfo(state.character)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        if (info != null) {
            PanelTitle(when (info.id) { 4 -> "Species"; 5 -> "Fish"; 8 -> "Paint job"; 9 -> "How many"; else -> "Coat" }, info.coats.getOrNull(state.coat)?.name ?: "")
            OrbRow(info.coats.map { Color(it.base) to Color(it.rim) }, state.coat) { state.applyCoat(it) }
            Gap(10.dp)
        }
        PanelTitle("Glow colour", if (state.accent == 0) "From the wallpaper" else "")
        OrbRow(
            ACCENT_PRESETS.map { if (it == 0) c.accent to c.accent2 else Color(it) to Color(app.pulsify.engine.SkyScene.shiftHue(it, 38f)) },
            ACCENT_PRESETS.indexOf(state.accent).coerceAtLeast(0), small = true,
        ) { state.applyAccent(ACCENT_PRESETS[it]) }
        Gap(6.dp)
        BrightnessLine(state)
    }
}

@Composable
private fun PanelTitle(title: String, value: String) {
    val c = LocalPulse.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = T.heading, color = c.text)
        Spacer(Modifier.weight(1f))
        AnimatedContent(value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "pv") { v -> Text(v, style = T.small, color = c.accentInk) }
    }
}

@Composable
private fun OrbRow(colors: List<Pair<Color, Color>>, selected: Int, small: Boolean = false, onPick: (Int) -> Unit) {
    val size = if (small) 44.dp else 60.dp
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(colors) { i, pair ->
            ColorOrb(pair.first, pair.second, i == selected, size, Modifier.pressable(scaleDown = 0.85f) { onPick(i) }.padding(4.dp))
        }
    }
}

/** Brightness as a hairline you slide along, with the value at the end. */
@Composable
private fun BrightnessLine(state: AppState) {
    val c = LocalPulse.current
    var v by remember { mutableFloatStateOf(state.brightness) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Brightness", style = T.small, color = c.sub)
        Spacer(Modifier.width(14.dp))
        Canvas(
            Modifier.weight(1f).height(36.dp)
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { o -> v = 0.6f + 0.8f * (o.x / size.width).coerceIn(0f, 1f); state.applyBrightness(v) }) { ch, _ ->
                        v = 0.6f + 0.8f * (ch.position.x / size.width).coerceIn(0f, 1f); state.applyBrightness(v)
                    }
                }
                .pointerInput(Unit) { detectTapGestures { o -> v = 0.6f + 0.8f * (o.x / size.width).coerceIn(0f, 1f); state.applyBrightness(v) } },
        ) {
            val k = ((v - 0.6f) / 0.8f).coerceIn(0f, 1f)
            val y = size.height / 2
            drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.4f), c.accentInk)), Offset(0f, y), Offset(size.width * k, y), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(c.accent.copy(alpha = 0.35f), radius = 14.dp.toPx(), center = Offset(size.width * k, y))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(size.width * k, y))
        }
        Spacer(Modifier.width(10.dp))
        Text("${(v * 100).toInt()}%", style = T.small, color = c.text, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

// ---------------------------------------------------------------- World

/** Worlds as tall posters (the real painting, day or night with the clock), swiped like a deck. */
@Composable
private fun WorldPanel(state: AppState) {
    val info = app.pulsify.engine.creatureInfo(state.character) ?: return
    val night = isNightNow()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        PanelTitle("World", info.envs.getOrNull(state.env)?.name ?: "")
        Gap(4.dp)
        val ls = rememberLazyListState(initialFirstVisibleItemIndex = state.env.coerceAtLeast(0))
        LazyRow(state = ls, contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(info.envs) { i, e ->
                Poster(
                    if (e.world >= 0) "worlds/w${e.world}_${if (night) "night" else "day"}.jpg" else null,
                    e.name, i == state.env, Color(e.c1), Color(e.c2), skyStyle = e.skyVariant,
                ) { state.applyEnv(i) }
            }
        }
    }
}

private fun isNightNow(): Boolean {
    val h = if (Pulse.previewHour >= 0f) Pulse.previewHour else java.time.LocalTime.now().let { it.hour + it.minute / 60f }
    return h < 6f || h > 19.5f
}

/** A tall picture card. Pictures are decoded small and off the main thread. */
@Composable
private fun Poster(asset: String?, name: String, selected: Boolean, c1: Color, c2: Color, skyStyle: Int = 0, width: Int = 112, onClick: () -> Unit) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    val pic by produceState<ImageBitmap?>(null, asset) {
        value = if (asset == null) null else withContext(Dispatchers.IO) { decodeSmall(ctx, asset, 280) }
    }
    val k by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.65f, stiffness = 400f), label = "sel")
    Box(
        Modifier.width(width.dp).height((width * 1.5f).dp)
            .graphicsLayer { scaleX = 0.92f + 0.08f * k; scaleY = 0.92f + 0.08f * k }
            .drawBehind {
                if (k > 0.01f) drawRoundRect(c.accent.copy(alpha = 0.45f * k), topLeft = Offset(-6f, -6f), size = Size(size.width + 12f, size.height + 12f), cornerRadius = CornerRadius(24.dp.toPx()))
            }
            .clip(RoundedCornerShape(20.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .pressable(scaleDown = 0.94f, onClick = onClick)
            .semantics { contentDescription = "World: $name" },
    ) {
        val p = pic
        if (p != null) Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else MiniSky(c1, c2, seed = name.hashCode(), modifier = Modifier.fillMaxSize(), style = skyStyle)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Text(name, style = T.name.copy(fontSize = 16.sp, lineHeight = 17.sp), color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (selected) Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp)) }
    }
}

fun decodeSmall(ctx: android.content.Context, path: String, targetW: Int): ImageBitmap? = try {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (o.outWidth / (s * 2) >= targetW) s *= 2
    val o2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
    ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it, null, o2) }?.asImageBitmap()
} catch (_: Throwable) { null }

// ---------------------------------------------------------------- Tricks

/** A remote control: big round buttons that ripple when pressed. */
@Composable
private fun TricksPanel(state: AppState, loop: GlLoop?) {
    val c = LocalPulse.current
    val info = app.pulsify.engine.creatureInfo(state.character) ?: return
    var last by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        PanelTitle("Ask ${info.name}", if (last.isEmpty()) "tap a button" else last)
        Gap(6.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            info.tricks.chunked(2).forEach { pair ->
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t -> TrickButton(t) { loop?.perform(t.id); last = t.label } }
                }
            }
        }
        if (info.id == 4) {
            Gap(10.dp)
            KiwiWords(state, loop)
        } else {
            Gap(8.dp)
            Text(info.hint, style = T.small, color = c.sub, modifier = Modifier.padding(horizontal = 22.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrickButton(t: app.pulsify.engine.TrickInfo, onTap: () -> Unit) {
    val c = LocalPulse.current
    val scope = rememberCoroutineScope()
    val ring = remember { Animatable(0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Box(
            Modifier.size(62.dp)
                .drawBehind {
                    val r = ring.value
                    if (r > 0f && r < 1f) drawCircle(c.accent.copy(alpha = 0.6f * (1f - r)), radius = size.minDimension / 2 * (1f + r * 0.7f), style = Stroke(3.dp.toPx()))
                }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)), center = Offset(50f, 40f)))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .pressable(scaleDown = 0.86f) {
                    onTap(); scope.launch { ring.snapTo(0f); ring.animateTo(1f, tween(520)) }
                }
                .semantics { contentDescription = "Trick: ${t.label}" },
            contentAlignment = Alignment.Center,
        ) { Text(t.emoji, style = T.title.copy(fontSize = 26.sp)) }
        Gap(5.dp)
        Text(t.label, style = T.micro, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun KiwiWords(state: AppState, loop: GlLoop?) {
    val c = LocalPulse.current
    var text by remember { mutableStateOf("") }
    val taught = state.kiwiWords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it.replace(",", " ").take(40) },
            singleLine = true,
            textStyle = T.body.copy(color = c.text),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            modifier = Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 12.dp)
                .semantics { contentDescription = "Kiwi word" },
            decorationBox = { inner -> if (text.isEmpty()) Text("Teach Kiwi a word", style = T.body, color = c.faint); inner() },
        )
        Spacer(Modifier.width(8.dp))
        GlowButton("Teach", Modifier.width(92.dp), height = 46.dp) {
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
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            taught.forEach { w ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = 0.16f))
                        .pressable { loop?.perform("say:$w") }.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(w, style = T.small, color = c.text, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                            .pressable { state.applyKiwiWords(taught.filter { it != w }.joinToString(", ")) },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", style = T.small, color = c.sub) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Time

/** Drag the sun across the sky to preview any hour; past dusk it becomes the moon. */
@Composable
private fun TimePanel() {
    val c = LocalPulse.current
    var hour by remember { mutableFloatStateOf(Pulse.previewHour) }
    var charging by remember { mutableStateOf(Pulse.previewCharging) }
    var low by remember { mutableStateOf(Pulse.previewLowBattery) }
    val shown = if (hour >= 0f) hour else java.time.LocalTime.now().let { it.hour + it.minute / 60f }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.Bottom) {
            Text(clock(shown), style = T.display, color = c.text)
            Spacer(Modifier.width(10.dp))
            Text(if (hour < 0f) "now" else "preview", style = T.small, color = c.sub, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            if (hour >= 0f) Pill("Back to now", false) { hour = -1f; Pulse.previewHour = -1f; Pulse.previewMood = null }
        }
        Gap(4.dp)
        SunArc(shown) { h -> hour = h; Pulse.previewHour = h }
        Gap(8.dp)
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StateChip("charging", charging) { charging = it; Pulse.previewCharging = it }
            StateChip("low battery", low) { low = it; Pulse.previewLowBattery = it }
        }
    }
}

private fun clock(h: Float): String {
    val hh = ((h.toInt() % 24) + 24) % 24; val mm = ((h - h.toInt()) * 60).toInt().coerceIn(0, 59)
    val h12 = if (hh % 12 == 0) 12 else hh % 12
    return "$h12:${mm.toString().padStart(2, '0')} ${if (hh < 12) "am" else "pm"}"
}

@Composable
private fun SunArc(hour: Float, onHour: (Float) -> Unit) {
    // The arc runs from 4 am on the left, over 4 pm at the top, to 4 am again on the right.
    fun hourToT(h: Float) = (((h - 4f) % 24f + 24f) % 24f) / 24f
    fun tToHour(t: Float) = (4f + t * 24f) % 24f
    val t = hourToT(hour)
    Canvas(
        Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 22.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { o ->
                    val cx = size.width / 2f; val cy = size.height * 1.05f
                    onHour(tToHour(1f - atan2(cy - o.y, o.x - cx).coerceIn(0f, PI.toFloat()) / PI.toFloat()))
                }) { ch, _ ->
                    val o = ch.position
                    val cx = size.width / 2f; val cy = size.height * 1.05f
                    onHour(tToHour(1f - atan2(cy - o.y, o.x - cx).coerceIn(0f, PI.toFloat()) / PI.toFloat()))
                }
            },
    ) {
        val cx = size.width / 2f; val cy = size.height * 1.05f
        val r = minOf(size.width / 2f, size.height * 0.98f)
        // The colour of the sky at this hour, washed behind the arc.
        val sky = when {
            hour < 5f || hour >= 21f -> Color(0xFF2B2E7A)
            hour < 8f -> Color(0xFFFF8A5C)
            hour < 17f -> Color(0xFF5FB2FF)
            else -> Color(0xFFE0609A)
        }
        drawCircle(Brush.radialGradient(listOf(sky.copy(alpha = 0.45f), Color.Transparent), center = Offset(cx, cy), radius = r * 1.25f), radius = r * 1.25f, center = Offset(cx, cy))
        drawArc(Color.White.copy(alpha = 0.18f), 180f, 180f, false, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        for (i in 0..24) {
            val a = PI.toFloat() * (1f - i / 24f)
            val inner = if (i % 6 == 0) r - 10.dp.toPx() else r - 5.dp.toPx()
            drawLine(Color.White.copy(alpha = if (i % 6 == 0) 0.45f else 0.18f), Offset(cx + cos(a) * inner, cy - sin(a) * inner), Offset(cx + cos(a) * r, cy - sin(a) * r), 1.5.dp.toPx())
        }
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
        val a = PI.toFloat() * (1f - t)
        val p = Offset(cx + cos(a) * r, cy - sin(a) * r)
        val night = hour < 6f || hour >= 19.5f
        if (night) {
            drawCircle(Color(0xFFCDD7FF).copy(alpha = 0.3f), radius = 26.dp.toPx(), center = p)
            drawCircle(Color(0xFFF2F0FF), radius = 13.dp.toPx(), center = p)
            drawCircle(Color.Black.copy(alpha = 0.85f), radius = 11.dp.toPx(), center = p + Offset(6.dp.toPx(), -4.dp.toPx()))
        } else {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.6f), Color.Transparent), center = p, radius = 34.dp.toPx()), radius = 34.dp.toPx(), center = p)
            for (k in 0 until 8) {
                val ra = k * PI.toFloat() / 4f
                val d = Offset(cos(ra), sin(ra))
                drawLine(Color(0xFFFFD27A), p + d * 17.dp.toPx(), p + d * 22.dp.toPx(), 2.5.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(Color(0xFFFFE6A8), radius = 12.dp.toPx(), center = p)
        }
    }
}

@Composable
private fun StateChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalPulse.current
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(50))
            .background(if (on) c.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (on) c.accentInk.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .pressable { onChange(!on) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) c.accentInk else Color.White.copy(alpha = 0.25f)))
        Spacer(Modifier.width(9.dp))
        Text("Pretend $label", style = T.small, color = if (on) c.text else c.sub)
    }
}

// ---------------------------------------------------------------- Sound

@Composable
private fun SoundPanel(state: AppState, source: AudioFrame.Source, spectrum: FloatArray, tick: Int) {
    val c = LocalPulse.current
    var demo by remember { mutableStateOf(Pulse.audio.demo) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) state.applyMusicReact(true)
        else state.toast = AppState.Toast("Music reaction is off", "Without permission it still grooves gently when music plays.")
    }
    val on = state.musicReact && Pulse.audio.hasPermission()
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.Bottom) {
        SpectrumRing(spectrum, tick, source != AudioFrame.Source.NONE, Modifier.size(150.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                when (source) {
                    AudioFrame.Source.MUSIC -> "Dancing to your music"
                    AudioFrame.Source.DEMO -> "Dancing to the demo beat"
                    AudioFrame.Source.GROOVE -> "A gentle groove"
                    AudioFrame.Source.NONE -> "Quiet"
                },
                style = T.name, color = c.text,
            )
            SwitchLine("My music", on) { v ->
                if (v && !Pulse.audio.hasPermission()) launcher.launch(Manifest.permission.RECORD_AUDIO) else state.applyMusicReact(v)
            }
            SwitchLine("Demo beat", demo) { demo = it; Pulse.audio.demo = it }
            Text("Listens only to what your phone plays. Never records.", style = T.micro, color = c.faint)
        }
    }
}

@Composable
private fun SwitchLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalPulse.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
        PillSwitch(on, onChange)
    }
}

/** The spectrum bent into a ring around a glowing core that swells with the bass. */
@Composable
private fun SpectrumRing(spectrum: FloatArray, tick: Int, live: Boolean, modifier: Modifier) {
    val c = LocalPulse.current
    Canvas(modifier) {
        if (tick < 0) return@Canvas
        val cx = size.width / 2; val cy = size.height / 2
        val r0 = size.minDimension * 0.26f
        val bass = (spectrum[0] + spectrum[1] + spectrum[2]) / 3f
        drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.5f + 0.4f * bass), Color.Transparent), radius = r0 * (1.4f + bass)), radius = r0 * (1.4f + bass))
        drawCircle(lerp(c.accent, Color.White, 0.3f), radius = r0 * (0.55f + 0.25f * bass))
        val n = 48
        for (i in 0 until n) {
            val v = if (live) spectrum[i % 24].coerceIn(0.04f, 1f) else 0.06f + 0.03f * sin(i * 0.7f + tick * 0.05f)
            val a = i * 2f * PI.toFloat() / n - PI.toFloat() / 2
            val r1 = r0 * 1.15f; val r2 = r1 + v * size.minDimension * 0.24f
            drawLine(lerp(c.accent2, c.accentInk, v), Offset(cx + cos(a) * r1, cy + sin(a) * r1), Offset(cx + cos(a) * r2, cy + sin(a) * r2), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- Sky & loops

@Composable
private fun SkyPanel(state: AppState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        PanelTitle("Sky", SKY_VARIANTS.getOrNull(state.variant)?.name ?: "")
        Gap(4.dp)
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(SKY_VARIANTS) { _, v ->
                Poster(if (v.id == 2) "sky/nebula.jpg" else null, v.name, state.character == 0 && state.variant == v.id, Color(v.accent1), Color(v.accent2), skyStyle = v.id) {
                    state.applyCharacter(0); state.applyVariant(v.id)
                }
            }
        }
    }
}

@Composable
private fun LoopsPanel(state: AppState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        PanelTitle("Video loops", app.pulsify.engine.videoLoop(state.video)?.name ?: "")
        Gap(4.dp)
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(app.pulsify.engine.VIDEO_LOOPS) { _, v ->
                Poster("thumbs/loop_${v.id}.jpg", v.name, state.video == v.id, Color(v.c1), Color(v.c2)) { state.applyVideo(v.id) }
            }
        }
    }
}

// ---------------------------------------------------------------- Shared with Settings

@Composable
fun ToggleRow(title: String, line: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalPulse.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = c.text)
            Text(line, style = T.small, color = c.sub)
        }
        Spacer(Modifier.width(12.dp))
        PillSwitch(checked, onChange)
    }
}

/** Decodes a small asset picture for a tile (null when missing). */
fun loadThumb(ctx: android.content.Context, path: String): ImageBitmap? = try {
    ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap()
} catch (_: Throwable) { null }
