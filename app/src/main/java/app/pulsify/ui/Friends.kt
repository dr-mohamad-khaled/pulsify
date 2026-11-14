package app.pulsify.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulsify.core.BuddyPoke
import app.pulsify.core.BuddySlot
import app.pulsify.core.Buddies
import app.pulsify.engine.BuddyFeed
import app.pulsify.engine.CREATURES
import app.pulsify.engine.CreatureInfo
import app.pulsify.engine.GlLoop
import app.pulsify.engine.creatureInfo
import app.pulsify.widget.BuddyWidgetProvider
import app.pulsify.widget.PinnedReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pretend widgets for the in-app playground. */
class SimFeed : BuddyFeed() {
    @Volatile var list: List<BuddySlot> = emptyList()
    val taps = ConcurrentLinkedQueue<BuddyPoke>()
    init { pagesKnown = true }
    override fun slots(): List<BuddySlot> = list
    override fun pollPoke(): BuddyPoke? = taps.poll()
}

// The pretend home screen: a 4 x 6 grid between the status bar and the dock.
private const val GX = 0.06f; private const val GY = 0.125f; private const val CW = 0.22f; private const val CH = 0.112f
private fun cell(col: Int, row: Int) = floatArrayOf(GX + col * CW, GY + row * CH)

/** Where the three playground widgets sit (page, column, row; each is 2 x 2 cells). */
private val PLAY_SLOTS = listOf(intArrayOf(0, 0, 0), intArrayOf(0, 2, 2), intArrayOf(1, 1, 1))

private fun playSlots(creatures: List<Int>): List<BuddySlot> = PLAY_SLOTS.mapIndexed { i, s ->
    val (x, y) = cell(s[1], s[2]).let { it[0] to it[1] }
    BuddySlot(1000 + i, creatures[i], 0, s[0], x + 0.01f, y + 0.005f, x + 2 * CW - 0.01f, y + 2 * CH - 0.005f)
}

// Icons on the pretend pages: page, column, row, colour.
private val ICONS = listOf(
    intArrayOf(0, 2, 0, 0), intArrayOf(0, 3, 0, 1), intArrayOf(0, 2, 1, 2), intArrayOf(0, 3, 1, 3),
    intArrayOf(0, 0, 4, 4), intArrayOf(0, 1, 4, 5), intArrayOf(0, 0, 3, 6), intArrayOf(0, 1, 3, 7),
    intArrayOf(1, 0, 0, 3), intArrayOf(1, 1, 0, 6), intArrayOf(1, 2, 0, 0), intArrayOf(1, 3, 0, 5),
    intArrayOf(1, 0, 4, 1), intArrayOf(1, 3, 3, 2), intArrayOf(1, 3, 4, 7), intArrayOf(1, 0, 3, 4),
)
private val ICON_TINTS = listOf(0xFF7CF2A8, 0xFFFFB36B, 0xFF8FC7FF, 0xFFFF7FB2, 0xFFCDB4FF, 0xFF5EE0D0, 0xFFFFD86B, 0xFF9D4EDD).map { Color(it) }

@Composable
fun FriendsScreen(state: AppState, onBack: () -> Unit) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    val picks = remember { mutableStateListOf(1, 4, 7) }
    var addId by remember { mutableIntStateOf(if (state.character in 1..9) state.character else 1) }
    var addCoat by remember { mutableIntStateOf(0) }
    var placed by remember { mutableStateOf(Buddies.slots) }
    var ours by remember { mutableStateOf(Buddies.isOurWallpaper(ctx)) }
    DisposableEffect(Unit) {
        val l: () -> Unit = { placed = Buddies.slots; ours = Buddies.isOurWallpaper(ctx) }
        Buddies.addListener(l)
        onDispose { Buddies.removeListener(l) }
    }
    LaunchedEffect(Unit) { while (true) { delay(2500); ours = Buddies.isOurWallpaper(ctx); placed = Buddies.slots } }

    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(bottom = 56.dp)) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 22.dp)) {
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackCircle(onBack)
                    Spacer(Modifier.weight(1f))
                    StatusChip(if (placed.any { it.placed } && ours) "${placed.count { it.placed }} living on your home screen" else "Widgets", live = placed.any { it.placed } && ours)
                }
                Gap(24.dp)
                Text("Friends for your home screen", style = T.display, color = c.text, modifier = Modifier.enter(0))
                Gap(12.dp)
                Text(
                    "Put a creature in a widget. Swipe to its page and it gets dizzy, startled or grumpy. " +
                        "Put two side by side and they play catch, gossip and trade places.",
                    style = T.body, color = c.sub, modifier = Modifier.enter(1),
                )
                Gap(24.dp)
            }
        }
        item {
            Playground(picks, Modifier.padding(horizontal = 22.dp).enter(2))
            Gap(14.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                picks.forEachIndexed { i, id ->
                    SlotSwap(id) { picks[i] = nextCreature(id, picks) }
                }
                Spacer(Modifier.width(4.dp))
                Text("Tap a face to swap who's in the demo", style = T.small, color = c.faint, modifier = Modifier.weight(1f))
            }
            Gap(40.dp)
        }
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text("Add one to your home screen", style = T.title, color = c.text)
                Gap(16.dp)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(CREATURES) { cr -> AvatarPick(cr, cr.id == addId) { addId = cr.id; addCoat = 0 } }
            }
            val info = creatureInfo(addId)
            if (info != null && info.coats.size > 1) {
                Gap(14.dp)
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(info.coats.size) { i ->
                        val co = info.coats[i]
                        ColorOrb(Color(co.base), Color(co.rim), i == addCoat, size = 46.dp, modifier = Modifier.pressable { addCoat = i }.semantics { contentDescription = co.name })
                    }
                }
            }
            Gap(18.dp)
            Column(Modifier.padding(horizontal = 22.dp)) {
                val name = info?.name ?: "Friend"
                GlowButton("Add $name to home screen", Modifier.fillMaxWidth()) {
                    if (!pinWidget(ctx, addId, addCoat)) state.toast = AppState.Toast("Long-press your home screen", "Widgets → Pulsify → Pulsify friend")
                }
                if (!ours) {
                    Gap(12.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface2).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Friends come alive with Pulsify as your wallpaper", style = T.bodyStrong, color = c.text)
                            Gap(3.dp)
                            Text("Until then the widget shows their picture.", style = T.small, color = c.sub)
                        }
                        Spacer(Modifier.width(12.dp))
                        GhostButton("Set", height = 40.dp) { AppState.applyWallpaper(ctx) }
                    }
                }
                Gap(36.dp)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text("How they find their spot", style = T.title, color = c.text)
                Gap(14.dp)
                Step(1, "Add the widget", "From here, or long-press your home screen → Widgets → Pulsify.")
                Step(2, "Tap it once", "That's how it learns where it lives. Moved it? Tap it again.")
                Step(3, "Swipe away and back", "Every arrival gets a different reaction.")
                Gap(30.dp)
                if (placed.isNotEmpty()) {
                    Text("On your home screen", style = T.title, color = c.text)
                    Gap(12.dp)
                    placed.sortedBy { it.page }.forEach { s -> PlacedRow(s, ours) }
                }
            }
        }
    }
}

@Composable
private fun BackCircle(onBack: () -> Unit) {
    val c = LocalPulse.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).pressable(onClick = onBack)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            drawLine(c.text, Offset(w * 0.62f, w * 0.15f), Offset(w * 0.28f, w * 0.5f), w * 0.11f, StrokeCap.Round)
            drawLine(c.text, Offset(w * 0.28f, w * 0.5f), Offset(w * 0.62f, w * 0.85f), w * 0.11f, StrokeCap.Round)
        }
    }
}

private fun nextCreature(id: Int, used: List<Int>): Int {
    var n = id
    repeat(CREATURES.size) { n = n % CREATURES.size + 1; if (n !in used) return n }
    return n
}

/** Pins a widget straight onto the home screen (the launcher asks the user where). */
fun pinWidget(ctx: Context, creature: Int, coat: Int): Boolean {
    val mgr = AppWidgetManager.getInstance(ctx)
    if (!mgr.isRequestPinAppWidgetSupported) return false
    Buddies.pendingCreature = creature; Buddies.pendingCoat = coat
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    val cb = PendingIntent.getBroadcast(ctx, creature * 10 + coat,
        Intent(ctx, PinnedReceiver::class.java).putExtra("creature", creature).putExtra("coat", coat), flags)
    return try { mgr.requestPinAppWidget(ComponentName(ctx, BuddyWidgetProvider::class.java), null, cb) } catch (_: Throwable) { false }
}

@Composable
private fun thumb(id: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val pic by produceState<ImageBitmap?>(null, id) { value = withContext(Dispatchers.IO) { decodeSmall(ctx, "thumbs/creature_$id.jpg", 160) } }
    return pic
}

@Composable
private fun Face(id: Int, size: androidx.compose.ui.unit.Dp, ring: Color?, modifier: Modifier = Modifier) {
    val pic = thumb(id)
    Box(
        modifier.size(size).clip(CircleShape)
            .then(if (ring != null) Modifier.border(2.5.dp, ring, CircleShape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape))
            .background(Color(0xFF15121E)),
    ) {
        if (pic != null) Image(pic, null, Modifier.fillMaxSize().padding(if (ring != null) 3.dp else 0.dp).clip(CircleShape), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
    }
}

@Composable
private fun SlotSwap(id: Int, onClick: () -> Unit) {
    val info = creatureInfo(id)
    Face(id, 46.dp, info?.let { Color(it.accent) }, Modifier.pressable(onClick = onClick).semantics { contentDescription = "Swap ${info?.name}" })
}

@Composable
private fun AvatarPick(cr: CreatureInfo, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPulse.current
    val s by animateFloatAsState(if (selected) 1f else 0.86f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "pick")
    Column(Modifier.width(76.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }) {
            if (selected) Box(Modifier.size(80.dp).drawBehind {
                drawCircle(Brush.radialGradient(listOf(Color(cr.accent).copy(alpha = 0.45f), Color.Transparent)), radius = size.minDimension / 2f)
            })
            Face(cr.id, 64.dp, if (selected) Color(cr.accent) else null)
        }
        Gap(6.dp)
        Text(cr.name, style = T.micro, color = if (selected) c.text else c.faint, maxLines = 1)
    }
}

@Composable
private fun Step(n: Int, title: String, line: String) {
    val c = LocalPulse.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", style = T.micro, color = c.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.bodyStrong, color = c.text)
            Gap(2.dp)
            Text(line, style = T.small, color = c.sub)
        }
    }
}

@Composable
private fun PlacedRow(s: BuddySlot, ours: Boolean) {
    val c = LocalPulse.current
    val info = creatureInfo(s.creature)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Face(s.creature, 48.dp, info?.let { Color(it.accent) })
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(info?.name ?: "Friend", style = T.bodyStrong, color = c.text)
            Gap(2.dp)
            Text(
                when {
                    !ours -> "Showing a picture (Pulsify isn't your wallpaper)"
                    s.placed -> "Lives on home-screen page ${s.page + 1}"
                    else -> "Waiting for a tap so it can find its spot"
                },
                style = T.small, color = if (s.placed && ours) c.mint else c.sub,
            )
        }
    }
}

/**
 * A little phone with a home screen you can swipe: the real engine draws the friends behind
 * pretend app icons, exactly as the wallpaper does behind real widgets.
 */
@Composable
fun Playground(picks: List<Int>, modifier: Modifier = Modifier, autoDemo: Boolean = true) {
    val c = LocalPulse.current
    val sim = remember { SimFeed() }
    val page = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(picks.toList()) { sim.list = playSlots(picks.toList()) }
    LaunchedEffect(Unit) { snapshotFlow { page.value }.collect { sim.pagePos = it } }
    if (autoDemo) LaunchedEffect(Unit) {
        // Shows off the reactions until the user takes over.
        delay(3800)
        while (true) {
            if (System.currentTimeMillis() - lastTouch > 7000) {
                val to = if (page.value < 0.5f) 1f else 0f
                page.animateTo(to, tween(420, easing = FastOutSlowInEasing))
            }
            delay(6500)
        }
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(0.5f).clip(RoundedCornerShape(38.dp))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(38.dp))
            .background(Color.Black),
    ) {
        LiveSky(Modifier.fillMaxSize(), renderScale = 0.6f, fpsCap = 30, onLoop = { l: GlLoop -> l.showMainCreature = false; l.buddyFeed = sim })
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { lastTouch = System.currentTimeMillis() },
                        onDragEnd = {
                            lastTouch = System.currentTimeMillis()
                            scope.launch { page.animateTo(page.value.roundToInt().coerceIn(0, 1).toFloat(), spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) }
                        },
                        onDragCancel = { scope.launch { page.animateTo(page.value.roundToInt().coerceIn(0, 1).toFloat()) } },
                    ) { ch, dx ->
                        ch.consume()
                        lastTouch = System.currentTimeMillis()
                        scope.launch { page.snapTo((page.value - dx / size.width).coerceIn(-0.3f, 1.3f)) }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        lastTouch = System.currentTimeMillis()
                        val nx = o.x / size.width; val ny = o.y / size.height
                        sim.list.firstOrNull { s ->
                            val l = s.l + (s.page - page.value); val r = s.r + (s.page - page.value)
                            nx in l..r && ny in s.t..s.b
                        }?.let { sim.taps.add(BuddyPoke(it.widgetId, -1f, -1f, -1f, -1f)) }
                    }
                },
        ) {
            val w = maxWidth; val h = maxHeight
            // Status bar.
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("9:41", style = T.micro.copy(fontSize = 12.sp), color = Color.White)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(width = 18.dp, height = 9.dp).clip(RoundedCornerShape(3.dp)).border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(3.dp)).padding(1.5.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(1.5.dp)).background(Color.White.copy(alpha = 0.9f)))
                }
            }
            // The two pages of icons slide together with the swipe.
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = -page.value * size.width }) {
                ICONS.forEach { ic ->
                    val p = cell(ic[1], ic[2])
                    val x = w * (p[0] + ic[0] + CW * 0.18f); val y = h * (p[1] + CH * 0.12f)
                    AppIcon(ICON_TINTS[ic[3]], ic[3], Modifier.offset(x, y).size(w * CW * 0.64f))
                }
            }
            // Search bar and dock stay put.
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = h * 0.115f).fillMaxWidth(0.84f).height(h * 0.045f)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = h * 0.03f).fillMaxWidth(0.88f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) { repeat(4) { i -> AppIcon(ICON_TINTS[(i * 3 + 1) % ICON_TINTS.size], i + 9, Modifier.size(w * CW * 0.64f)) } }
            // Page dots.
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = h * 0.175f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { i ->
                    val on = 1f - abs(page.value - i).coerceIn(0f, 1f)
                    Box(Modifier.size(width = (6 + 10 * on).dp, height = 6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.35f + 0.6f * on)))
                }
            }
        }
    }
}

/** A pretend app icon: a glassy squircle with a simple glyph. */
@Composable
private fun AppIcon(tint: Color, glyph: Int, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        drawRoundRect(
            Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(tint, Color.White, 0.25f), androidx.compose.ui.graphics.lerp(tint, Color.Black, 0.45f)), Offset.Zero, Offset(s, s)),
            cornerRadius = CornerRadius(s * 0.3f), alpha = 0.92f,
        )
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = s * 0.55f), cornerRadius = CornerRadius(s * 0.3f))
        val g = Color.White.copy(alpha = 0.92f); val m = s * 0.5f
        when (glyph % 6) {
            0 -> drawCircle(g, s * 0.17f, Offset(m, m), style = Stroke(s * 0.07f))
            1 -> { drawLine(g, Offset(s * 0.3f, s * 0.68f), Offset(s * 0.5f, s * 0.3f), s * 0.08f, StrokeCap.Round); drawLine(g, Offset(s * 0.5f, s * 0.3f), Offset(s * 0.7f, s * 0.68f), s * 0.08f, StrokeCap.Round) }
            2 -> drawRoundRect(g, Offset(s * 0.3f, s * 0.32f), Size(s * 0.4f, s * 0.36f), CornerRadius(s * 0.06f), style = Stroke(s * 0.07f))
            3 -> { drawCircle(g, s * 0.07f, Offset(s * 0.38f, s * 0.62f)); drawLine(g, Offset(s * 0.44f, s * 0.62f), Offset(s * 0.44f, s * 0.3f), s * 0.06f, StrokeCap.Round); drawLine(g, Offset(s * 0.44f, s * 0.3f), Offset(s * 0.64f, s * 0.36f), s * 0.06f, StrokeCap.Round) }
            4 -> { drawLine(g, Offset(s * 0.3f, s * 0.4f), Offset(s * 0.7f, s * 0.4f), s * 0.07f, StrokeCap.Round); drawLine(g, Offset(s * 0.3f, s * 0.6f), Offset(s * 0.6f, s * 0.6f), s * 0.07f, StrokeCap.Round) }
            else -> drawArc(g, 200f, 280f, false, Offset(s * 0.3f, s * 0.3f), Size(s * 0.4f, s * 0.4f), style = Stroke(s * 0.07f, cap = StrokeCap.Round))
        }
    }
}

/** The friends' card on the Home screen: a little live phone and a way in. */
@Composable
fun FriendsTeaser(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPulse.current
    val breathe = rememberInfiniteTransition(label = "teaser")
    val b by breathe.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "b")
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1B1626), Color(0xFF0E0C13))))
            .border(1.dp, Brush.linearGradient(listOf(c.accent.copy(alpha = 0.5f + 0.2f * b), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(30.dp))
            .pressable(scaleDown = 0.98f, onClick = onOpen)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Home-screen friends", style = T.title, color = c.text)
            Gap(8.dp)
            Text("Creatures as widgets that react when you swipe to them and play with each other.", style = T.small, color = c.sub)
            Gap(16.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(4, 1, 7).forEachIndexed { i, id ->
                    Face(id, 40.dp, creatureInfo(id)?.let { Color(it.accent) }, Modifier.offset(x = (-10 * i).dp).graphicsLayer { translationY = if (i == 1) -6f * b else 0f })
                }
                Spacer(Modifier.width(4.dp))
                Text("Try it", style = T.bodyStrong, color = c.accent)
            }
        }
    }
}
