package app.pulsify.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pulsify.engine.CREATURES
import app.pulsify.engine.creatureInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** One page of the intro: a picture that moves, a headline, a line of copy. */
private class IntroPage(val title: String, val body: String, val glow: Color)

private val PAGES = listOf(
    IntroPage("Your wallpaper,\nalive.", "Little creatures move into your home screen. They notice your touch, your music and the time of day, and they react.", Color(0xFFCDB4FF)),
    IntroPage("Poke them.\nThey poke back.", "Tap for a giggle, drag for a game, hold for a hug. Every creature has its own tricks and shows off a few every minute.", Color(0xFFFFC46B)),
    IntroPage("They hear\nyour music.", "Bass makes them bounce and beats make them dance. It works with whatever you're playing.", Color(0xFF9D4EDD)),
    IntroPage("They live\nyour day.", "Sleepy at night, cosy while you charge, dramatic when the battery's low. Kalboob licks his bone while you charge. Take it away and he cries.", Color(0xFF6EE7A8)),
    IntroPage("Friends on your\nhome screen.", "Put creatures in widgets. Swipe to their page and they get dizzy, startled or grumpy. Put two side by side and they play catch.", Color(0xFFFF8FB8)),
    IntroPage("Worlds that\nmove.", "Black water that ripples under your finger. Neon you paint with light. A nebula painted live on your phone. Cinematic loops.", Color(0xFF5EE0D0)),
    IntroPage("Pick your\nfirst friend.", "Pure black pixels save battery on AMOLED. No ads, no subscriptions.", Color(0xFFCDB4FF)),
)

@Composable
fun IntroScreen(onDone: (character: Int) -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    var pick by remember { mutableIntStateOf(1) }
    val page = pager.currentPage
    val last = page == PAGES.size - 1
    val glow = PAGES[page].glow
    val g by animateFloatAsState(1f, label = "g")
    PulsifyTheme(if (last) Color(creatureInfo(pick)?.accent ?: 0xFFCDB4FF.toInt()) else glow, Color(0xFFFFB36B)) {
        val c = LocalPulse.current
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // A slow wash of the page's colour behind everything.
            val wash = rememberInfiniteTransition(label = "wash")
            val wx by wash.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "wx")
            Box(Modifier.fillMaxSize().drawBehind {
                drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.28f * g), Color.Transparent),
                    center = Offset(size.width * (0.25f + 0.5f * wx), size.height * 0.3f), radius = size.width * 0.9f))
            })
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
                // Progress: one segment per page, the current one fills.
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in PAGES.indices) {
                            val f = (1f - abs(pager.currentPage + pager.currentPageOffsetFraction - i)).coerceIn(0f, 1f)
                            val done = i < pager.currentPage
                            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.14f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(if (done) 1f else f).background(c.accentInk))
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(if (last) "" else "Skip", style = T.small, color = c.sub, modifier = Modifier.pressable { scope.launch { pager.animateScrollToPage(PAGES.size - 1) } })
                }
                HorizontalPager(pager, Modifier.weight(1f)) { i ->
                    val p = PAGES[i]
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            when (i) {
                                0 -> Constellation()
                                1 -> PokeVisual()
                                2 -> MusicVisual()
                                3 -> DayVisual()
                                4 -> Box(Modifier.fillMaxHeight(0.96f).aspectRatio(0.5f)) { if (pager.currentPage == 4) Playground(listOf(1, 4, 7), Modifier.fillMaxSize()) }
                                5 -> WorldsVisual()
                                else -> PickGrid(pick) { pick = it }
                            }
                        }
                        Column(Modifier.padding(horizontal = 26.dp)) {
                            Gap(14.dp)
                            Text(p.title, style = T.display, color = c.text)
                            Gap(12.dp)
                            Text(p.body, style = T.body, color = c.sub)
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Gap(22.dp)
                    val name = creatureInfo(pick)?.name ?: "Mochi"
                    GlowButton(if (last) "Start with $name" else "Next", Modifier.fillMaxWidth()) {
                        if (last) onDone(pick) else scope.launch { pager.animateScrollToPage(page + 1) }
                    }
                    Gap(16.dp)
                }
            }
        }
    }
}

@Composable
private fun pic(path: String, max: Int = 260): ImageBitmap? {
    val ctx = LocalContext.current
    val b by produceState<ImageBitmap?>(null, path) { value = withContext(Dispatchers.IO) { decodeSmall(ctx, path, max) } }
    return b
}

@Composable
private fun Avatar(id: Int, size: Dp, modifier: Modifier = Modifier) {
    val b = pic("thumbs/creature_$id.jpg", 200)
    val acc = Color(creatureInfo(id)?.accent ?: 0xFFCDB4FF.toInt())
    Box(
        modifier.size(size).drawBehind { drawCircle(Brush.radialGradient(listOf(acc.copy(alpha = 0.45f), Color.Transparent)), radius = this.size.minDimension * 0.75f) }
            .clip(CircleShape).border(2.dp, acc.copy(alpha = 0.85f), CircleShape).background(Color(0xFF15121E)),
    ) { b?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) } }
}

/** Page 1: every creature circling a glowing core, near ones big and bright, far ones small. */
@Composable
private fun Constellation() {
    val t = rememberInfiniteTransition(label = "orbit")
    val a by t.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "a")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val rx = maxWidth * 0.36f; val ry = maxHeight * 0.16f
        val c = LocalPulse.current
        Box(Modifier.size(120.dp).drawBehind {
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.7f), c.accent.copy(alpha = 0.1f), Color.Transparent)), radius = size.minDimension)
            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension * 0.08f)
        })
        val ids = CREATURES.map { it.id }
        val order = ids.indices.sortedBy { sin(a + it * 2f * PI.toFloat() / ids.size) }
        for (k in order) {
            val ang = a + k * 2f * PI.toFloat() / ids.size
            val depth = (sin(ang) + 1f) / 2f   // 0 back .. 1 front
            val s = 0.55f + 0.55f * depth
            Avatar(ids[k], 78.dp, Modifier.offset(rx * cos(ang), ry * sin(ang) - maxHeight * 0.02f).graphicsLayer {
                scaleX = s; scaleY = s; alpha = 0.45f + 0.55f * depth
            })
        }
    }
}

/** Page 2: a creature card, with taps landing on it and little reactions popping out. */
@Composable
private fun PokeVisual() {
    val t = rememberInfiniteTransition(label = "poke")
    val k by t.animateFloat(0f, 3f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "k")
    val b = pic("thumbs/creature_2.jpg", 400)
    val c = LocalPulse.current
    Box(Modifier.fillMaxHeight(0.92f).aspectRatio(0.8f), contentAlignment = Alignment.Center) {
        val phase = k % 1f
        val squish = if (phase < 0.15f) 1f - 0.05f * sin(phase / 0.15f * PI.toFloat()) else 1f
        Box(Modifier.fillMaxSize(0.92f).graphicsLayer { scaleX = 2f - squish; scaleY = squish; rotationZ = (k - 1.5f) * 1.2f }
            .clip(RoundedCornerShape(34.dp)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(34.dp))) {
            b?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        // the finger: ripples where it lands
        val spots = listOf(Offset(0.42f, 0.5f), Offset(0.6f, 0.38f), Offset(0.5f, 0.62f))
        val spot = spots[k.toInt() % 3]
        Canvas(Modifier.fillMaxSize()) {
            val p = Offset(size.width * spot.x, size.height * spot.y)
            for (r in 0..1) {
                val e = ((phase - r * 0.12f) / 0.6f).coerceIn(0f, 1f)
                drawCircle(c.accentInk.copy(alpha = (1f - e) * 0.9f), radius = 12f + e * 110f, center = p, style = Stroke(5f * (1f - e) + 1f))
            }
            drawCircle(Color.White.copy(alpha = (1f - phase * 3f).coerceIn(0f, 0.9f)), radius = 22f, center = p)
        }
        val words = listOf("woof!", "hehe", "again!")
        val w = words[k.toInt() % 3]
        Text(w, style = T.title, color = Color(0xFF1A1325), modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 40.dp)
            .graphicsLayer { val s = if (phase < 0.2f) phase / 0.2f else 1f; scaleX = s; scaleY = s; alpha = if (phase > 0.85f) (1f - phase) / 0.15f else 1f; rotationZ = -6f }
            .clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** Page 3: a creature at the heart of a living spectrum ring. */
@Composable
private fun MusicVisual() {
    val t = rememberInfiniteTransition(label = "music")
    val time by t.animateFloat(0f, 60f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "t")
    val c = LocalPulse.current
    val beat = (sin(time * 2f * PI.toFloat() * 2f) * 0.5f + 0.5f).let { it * it * it }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(300.dp)) {
            val n = 64
            val r0 = size.minDimension * 0.3f
            for (i in 0 until n) {
                val ang = i * 2f * PI.toFloat() / n - PI.toFloat() / 2f
                val v = 0.25f + 0.75f * abs(sin(i * 0.37f + time * 3.1f) * cos(i * 0.13f - time * 1.7f)) * (0.6f + 0.4f * beat)
                val len = size.minDimension * 0.18f * v
                val col = androidx.compose.ui.graphics.lerp(c.accent, Color(0xFFFFB36B), i / n.toFloat())
                drawLine(col, center + Offset(cos(ang) * r0, sin(ang) * r0), center + Offset(cos(ang) * (r0 + len), sin(ang) * (r0 + len)), 6f, StrokeCap.Round)
            }
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.35f + 0.3f * beat), Color.Transparent)), radius = r0 * 1.2f)
        }
        Avatar(1, 150.dp, Modifier.graphicsLayer { val s = 1f + 0.08f * beat; scaleX = s; scaleY = 1f / s; translationY = -14f * beat })
        // notes drifting up
        for (i in 0 until 4) {
            val k = (time * 0.35f + i / 4f) % 1f
            Text("♪", style = T.title, color = listOf(c.accent, Color(0xFFFFB36B), Color(0xFF5EE0D0), Color(0xFFFF8FB8))[i],
                modifier = Modifier.offset(x = ((i - 1.5f) * 70).dp, y = (60 - k * 220).dp).graphicsLayer { alpha = sin(k * PI.toFloat()) })
        }
    }
}

/** Page 4: three cards for morning, charging and night. */
@Composable
private fun DayVisual() {
    val t = rememberInfiniteTransition(label = "day")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    val items = listOf(Triple(7, "Morning", Color(0xFFFFC46B)), Triple(2, "Charging", Color(0xFF6EE7A8)), Triple(3, "Night", Color(0xFF8C9EFF)))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        items.forEachIndexed { i, (id, label, col) ->
            val b = pic("thumbs/creature_$id.jpg", 300)
            val off = (i - 1) * 96
            Box(
                Modifier.width(160.dp).aspectRatio(0.72f)
                    .graphicsLayer { translationX = off.dp.toPx() * (0.9f + 0.1f * k); rotationZ = (i - 1) * (8f + 2f * k); translationY = if (i == 1) -24f * k else 10f }
                    .clip(RoundedCornerShape(26.dp)).border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp)).background(Color(0xFF15121E)),
            ) {
                b?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = T.micro, color = Color.White)
                }
            }
        }
    }
}

/** Page 6: posters of the moving worlds drifting upward in two columns. */
@Composable
private fun WorldsVisual() {
    val t = rememberInfiniteTransition(label = "worlds")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "y")
    val left = listOf("thumbs/sky_3.jpg", "thumbs/loop_7.jpg", "thumbs/sky_2.jpg", "thumbs/loop_5.jpg")
    val right = listOf("thumbs/loop_10.jpg", "thumbs/sky_4.jpg", "thumbs/loop_0.jpg", "thumbs/loop_11.jpg")
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
        val cardH = 190.dp; val gap = 12.dp
        val total = (cardH + gap) * 4
        Row(Modifier.fillMaxSize().padding(horizontal = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((col, list) in listOf(left, right).withIndex()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val shift = (if (col == 0) y else (y + 0.5f) % 1f)
                    for (rep in 0..1) for ((i, path) in list.withIndex()) {
                        val b = pic(path, 300)
                        val top = (cardH + gap) * i + total * rep - total * shift
                        Box(Modifier.offset(y = top).fillMaxWidth().height(cardH).clip(RoundedCornerShape(22.dp)).background(Color(0xFF15121E))) {
                            b?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                    }
                }
            }
        }
        // fade top and bottom
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black, 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color.Black)))
    }
}

/** Last page: choose who moves in first. */
@Composable
private fun PickGrid(pick: Int, onPick: (Int) -> Unit) {
    val c = LocalPulse.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CREATURES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cr ->
                    val sel = cr.id == pick
                    val s by animateFloatAsState(if (sel) 1f else 0.92f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "s")
                    val b = pic("thumbs/creature_${cr.id}.jpg", 260)
                    Box(
                        Modifier.weight(1f).aspectRatio(0.92f).graphicsLayer { scaleX = s; scaleY = s }
                            .clip(RoundedCornerShape(22.dp))
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) Color(cr.accent) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
                            .background(Color(0xFF15121E))
                            .pressable { onPick(cr.id) }
                            .semantics { contentDescription = cr.name },
                    ) {
                        b?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
                        Text(cr.name, style = T.micro, color = if (sel) Color.White else c.sub, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
                    }
                }
            }
        }
    }
}
