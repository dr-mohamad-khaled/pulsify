package app.pulsify.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pulsify.engine.creatureInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * The intro: five pages on pure black. A parrot flies in and lands under the logo; a phantom finger
 * pokes the duckling; a spectrum dances around Mochi; the Pharaohs fly in one after another under
 * glowing gold marks; and the cast lines up so you pick your first friend.
 * (Browser preview: "Pulsify — New intro".)
 */

private val LAV = Color(0xFFCDB4FF); private val AMB = Color(0xFFFFB36B); private val VIO = Color(0xFF9D4EDD); private val MINT = Color(0xFF4ADE80)
private val OUT = CubicBezierEasing(.2f, .9f, .2f, 1f)

private class Page(val over: String, val l1: String, val l2: String, val body: String, val acc: Color)
private val PAGES = listOf(
    Page("Live wallpapers", "Your wallpaper,", "alive.", "Creatures and super wallpapers that notice your touch, your music and your day.", LAV),
    Page("Touch", "Poke it.", "It pokes back.", "Its eyes follow your finger. Pick it up, fling it, watch it tumble and get back up.", AMB),
    Page("Music", "It hears", "your music.", "Bass makes it bounce, beats make it dance. Whatever you're playing.", VIO),
    Page("Pharaohs", "Unlock.", "Watch it fly in.", "Turn the screen on and it zooms in. Unlock and the camera flies around it. Every page, a new angle.", AMB),
    Page("Pure black", "Pick your", "first friend.", "True black pixels stay off on AMOLED screens, so it looks alive and sips battery.", LAV),
)
private val CAST = listOf(1 to "Mochi", 4 to "Kiwi", 10 to "Snowy", 5 to "Glowy", 6 to "Gromble", 8 to "Bolt", 9 to "Gloop", 11 to "Cosmo")

@Composable
private fun asset(path: String, max: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val b by produceState<ImageBitmap?>(null, path) { value = withContext(Dispatchers.IO) { decodeSmall(ctx, path, max) } }
    return b
}

/** Seconds since this composable appeared, ticking every frame (only while it is on screen). */
@Composable
private fun clock(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val t0 = withFrameNanos { it }; while (true) withFrameNanos { t = (it - t0) / 1e9f } }
    return t
}

@Composable
fun IntroScreen(onDone: (character: Int) -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    var pick by remember { mutableIntStateOf(1) }
    val page = pager.currentPage
    val last = page == PAGES.size - 1
    val acc = PAGES[page].acc
    PulsifyTheme(if (last) AMB else LAV, AMB) {
        val c = LocalPulse.current
        val accA by androidx.compose.animation.animateColorAsState(acc, tween(900), label = "acc")
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Starfield(pager.currentPage + pager.currentPageOffsetFraction, accA)
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
                // progress segments + skip
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (i in PAGES.indices) {
                            val f by animateFloatAsState(if (i <= page) 1f else 0f, tween(500, easing = OUT), label = "seg")
                            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.14f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(accA))
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    val sk by animateFloatAsState(if (last) 0f else 1f, label = "skip")
                    Text("Skip", style = T.small, color = c.sub, modifier = Modifier.graphicsLayer { alpha = sk }
                        .pressable { if (!last) scope.launch { pager.animateScrollToPage(PAGES.size - 1) } })
                }
                HorizontalPager(pager, Modifier.weight(1f)) { i ->
                    val p = PAGES[i]
                    val on = pager.currentPage == i && !pager.isScrollInProgress || (pager.currentPage == i && abs(pager.currentPageOffsetFraction) < .2f)
                    val off = (pager.currentPage - i) + pager.currentPageOffsetFraction
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                            // the stage drifts slower than the page and shrinks a little as it leaves
                            translationX = off * size.width * .35f; val k = 1f - .12f * abs(off).coerceAtMost(1f); scaleX = k; scaleY = k; alpha = 1f - .7f * abs(off).coerceAtMost(1f)
                        }, contentAlignment = Alignment.Center) {
                            when (i) {
                                0 -> LogoStage(on)
                                1 -> TouchStage(on)
                                2 -> MusicStage(on)
                                3 -> StatueStage(on)
                                else -> PickStage(on, pick) { pick = it }
                            }
                        }
                        Copy(p, on)
                    }
                }
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Gap(18.dp)
                    val name = creatureInfo(pick)?.name ?: "Mochi"
                    GlowButton(if (last) "Start with $name" else "Next", Modifier.fillMaxWidth()) {
                        if (last) onDone(pick) else scope.launch { pager.animateScrollToPage(page + 1, animationSpec = tween(800, easing = CubicBezierEasing(.7f, 0f, .2f, 1f))) }
                    }
                    Gap(10.dp)
                    Text("By continuing you agree to the Terms and the Privacy Policy (Settings).", style = T.micro.copy(fontSize = 11.sp), color = Color(0xFF6C6580),
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Gap(14.dp)
                }
            }
        }
    }
}

/** Overline, a two-line headline that rises line by line, and the copy fading in after it. */
@Composable
private fun Copy(p: Page, on: Boolean) {
    val c = LocalPulse.current
    val a1 = remember { Animatable(0f) }; val a2 = remember { Animatable(0f) }; val a3 = remember { Animatable(0f) }
    LaunchedEffect(on) {
        if (on) { launch { a1.animateTo(1f, tween(900, easing = OUT)) }; launch { delay(80); a2.animateTo(1f, tween(900, easing = OUT)) }; launch { delay(250); a3.animateTo(1f, tween(800, easing = OUT)) } }
        else { a1.snapTo(0f); a2.snapTo(0f); a3.snapTo(0f) }
    }
    Column(Modifier.padding(horizontal = 26.dp)) {
        Text(p.over.uppercase(), style = T.micro.copy(fontSize = 11.sp, letterSpacing = .14.em, fontWeight = FontWeight.Bold), color = p.acc,
            modifier = Modifier.graphicsLayer { alpha = a3.value; translationY = (1 - a3.value) * 40f })
        Gap(12.dp)
        for ((line, a) in listOf(p.l1 to a1, p.l2 to a2)) {
            Box(Modifier.clip(RoundedCornerShape(0.dp))) {
                Text(line, style = T.display, color = c.text, maxLines = 1, modifier = Modifier.graphicsLayer { translationY = (1 - a.value) * size.height * 1.1f; alpha = .2f + .8f * a.value })
            }
        }
        Gap(12.dp)
        Text(p.body, style = T.body, color = c.sub, modifier = Modifier.graphicsLayer { alpha = a3.value; translationY = (1 - a3.value) * 40f })
        Gap(8.dp)
    }
}

/** Twinkling stars with parallax across the pages, and a slow wash of the page colour. */
@Composable
private fun Starfield(pos: Float, acc: Color) {
    val stars = remember { List(110) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), .2f + Random.nextFloat() * .8f, Random.nextFloat() * 6f) } }
    val t = clock()
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Brush.radialGradient(listOf(acc.copy(alpha = .22f), Color.Transparent), center = Offset(size.width * .5f, size.height * .33f), radius = size.width * .85f),
            radius = size.width * .85f, center = Offset(size.width * .5f, size.height * .33f))
        for (s in stars) {
            val tw = .35f + .65f * abs(sin(t * 1.1f * s[2] + s[3]))
            var x = (s[0] * size.width - pos * size.width * .12f * s[2]) % size.width; if (x < 0) x += size.width
            drawCircle(if (s[2] > .85f) AMB else Color(0xFFE8E0FF), radius = s[2] * 1.6f * density, center = Offset(x, s[1] * size.height), alpha = tw * s[2] * .8f)
        }
    }
}

// ------------------------------------------------------------------ 1: logo + parrot landing
@Composable
private fun LogoStage(on: Boolean) {
    val t = clock()
    val bird = asset("intro/parrot.png", 420)
    val land = remember { Animatable(0f) }
    val letters = remember { List(7) { Animatable(0f) } }
    LaunchedEffect(on) {
        if (on) {
            letters.forEachIndexed { i, a -> launch { delay(i * 60L); a.animateTo(1f, spring(dampingRatio = .45f, stiffness = 220f)) } }
            launch { delay(400); land.animateTo(1f, tween(2000, easing = CubicBezierEasing(.3f, .9f, .3f, 1f))) }
        } else { land.snapTo(0f); letters.forEach { it.snapTo(0f) } }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = LocalDensity.current
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height * .52f
            // dashed orbit with an amber moon
            rotate(t * 9f, Offset(cx, cy)) {
                drawCircle(LAV.copy(alpha = .16f), radius = 165.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                drawCircle(AMB, radius = 4.dp.toPx(), center = Offset(cx, cy - 165.dp.toPx()))
                drawCircle(AMB.copy(alpha = .25f), radius = 12.dp.toPx(), center = Offset(cx, cy - 165.dp.toPx()))
            }
            // pulse rings
            if (on) for (k in 0..2) { val ph = ((t + k * 1.2f) % 3.6f) / 3.6f; val e = 1 - (1 - ph).pow(3)
                drawCircle(if (k == 2) AMB else LAV, radius = 120.dp.toPx() * (.3f + 1.6f * e), center = Offset(cx, cy), alpha = .9f * (1 - ph), style = Stroke(1.5.dp.toPx())) }
        }
        // logo letters spring in
        Row(Modifier.align(Alignment.TopCenter).padding(top = maxHeight * .1f)) {
            "Pulsify".forEachIndexed { i, ch ->
                val a = letters[i].value
                Text(ch.toString(), style = T.hero.copy(fontSize = 46.sp), color = if (i == 1) LAV else Color.White,
                    modifier = Modifier.graphicsLayer { alpha = a.coerceIn(0f, 1f); translationY = (1 - a) * 60f; scaleX = .6f + .4f * a; scaleY = .6f + .4f * a })
            }
            Box(Modifier.padding(top = 34.dp).size(8.dp).graphicsLayer { alpha = letters[6].value.coerceIn(0f, 1f) }.clip(CircleShape).background(AMB))
        }
        // the parrot flies in from the upper right and lands, then breathes
        if (bird != null) {
            val l = land.value
            val bob = if (l >= 1f) sin(t * 2f) * 6f else 0f
            Image(bird, null, Modifier.align(Alignment.BottomCenter).width(150.dp).aspectRatio(bird.width.toFloat() / bird.height).graphicsLayer {
                val inv = 1 - l; val over = sin(l * PI.toFloat()) * .02f
                translationX = with(d) { (180.dp * inv * inv).toPx() }; translationY = with(d) { (-240.dp * inv * inv).toPx() } + bob
                rotationZ = 18f * inv - 3f * sin(l * PI.toFloat()); scaleX = .6f + .4f * l + over; scaleY = scaleX; alpha = (l * 5f).coerceIn(0f, 1f)
            })
        }
    }
}

// ------------------------------------------------------------------ 2: a phantom finger pokes the duckling
@Composable
private fun TouchStage(on: Boolean) {
    val duck = asset("intro/duck.png", 380)
    val squash = remember { Animatable(0f) }
    val finger = remember { Animatable(0f) }      // 0 off-screen .. 1 on the duck
    val tap = remember { Animatable(0f) }         // ripple + bubble life 0..1
    var bubble by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    fun poke() = scope.launch { launch { squash.snapTo(0f); squash.animateTo(1f, tween(700, easing = LinearEasing)) }; launch { tap.snapTo(0f); tap.animateTo(1f, tween(1100, easing = LinearEasing)) } }
    LaunchedEffect(on) {
        if (!on) { finger.snapTo(0f); return@LaunchedEffect }
        while (true) {
            finger.snapTo(0f); finger.animateTo(1f, tween(900, easing = CubicBezierEasing(.3f, .8f, .3f, 1f)))
            poke(); delay(350); finger.animateTo(1.4f, tween(400)); delay(1500)
        }
    }
    val chips = remember { Animatable(0f) }
    LaunchedEffect(on) { if (on) chips.animateTo(1f, tween(700, easing = OUT)) else chips.snapTo(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().pressable(scaleDown = 1f) { poke() }) {
        val W = maxWidth; val H = maxHeight
        Row(Modifier.align(Alignment.TopCenter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tap", "Carry & throw", "Hold for a hug").forEachIndexed { i, s ->
                val a = ((chips.value * 1.6f) - i * .2f).coerceIn(0f, 1f)
                Text(s, style = T.small, color = Color(0xFFD9D2EA), modifier = Modifier.graphicsLayer { alpha = a; translationY = (1 - a) * 30f }
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xA61E192B)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        // floor glow
        Canvas(Modifier.align(Alignment.BottomCenter).width(240.dp).height(36.dp)) {
            drawOval(Brush.radialGradient(listOf(AMB.copy(alpha = .35f), Color.Transparent), radius = size.width / 2), size = size)
        }
        if (duck != null) {
            val q = squash.value
            // squash & stretch: squash 18%, spring up, settle
            val sx = when { q < .18f -> 1 + .18f * q / .18f; q < .45f -> 1.18f - .28f * (q - .18f) / .27f; q < .7f -> .9f + .14f * (q - .45f) / .25f; else -> 1.04f - .04f * (q - .7f) / .3f }
            val sy = 2f - sx
            val up = if (q in .18f..1f) sin((q - .18f) / .82f * PI.toFloat()) * 26f else 0f
            Image(duck, null, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).width(190.dp).aspectRatio(duck.width.toFloat() / duck.height).graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f); scaleX = if (q > 0f && q < 1f) sx else 1f; scaleY = if (q > 0f && q < 1f) sy else 1f; translationY = -up * density
            })
        }
        // the quack bubble
        val b = tap.value
        if (b > 0f && b < 1f) {
            val a = if (b < .25f) b / .25f else if (b > .8f) (1 - b) / .2f else 1f
            val s = if (b < .25f) .4f + .7f * (b / .25f) else if (b < .4f) 1.1f - .1f * ((b - .25f) / .15f) else 1f
            Text("Quack!", style = T.bodyStrong.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF0D0A14), modifier = Modifier.align(Alignment.TopEnd).padding(top = H * .14f, end = W * .1f)
                .graphicsLayer { alpha = a; scaleX = s; scaleY = s; rotationZ = -8f * (1 - s.coerceAtMost(1f)); translationY = -(b - .8f).coerceAtLeast(0f) * 70f }
                .clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        // phantom finger + ripple
        Canvas(Modifier.fillMaxSize()) {
            val tx = size.width * .62f; val ty = size.height * .55f
            val f = finger.value.coerceAtMost(1f); val gone = (finger.value - 1f).coerceAtLeast(0f) / .4f
            val fx = size.width * .95f + (tx - size.width * .95f) * f; val fy = size.height * .95f + (ty - size.height * .95f) * f
            val alpha = (f * 3f).coerceAtMost(1f) * (1 - gone)
            if (alpha > 0f) {
                drawCircle(LAV.copy(alpha = .35f * alpha), radius = 40.dp.toPx(), center = Offset(fx, fy))
                drawCircle(Color.White.copy(alpha = .14f * alpha), radius = 28.dp.toPx() * (if (b in 0f..0.15f) .85f else 1f), center = Offset(fx, fy))
                drawCircle(Color.White.copy(alpha = .7f * alpha), radius = 28.dp.toPx(), center = Offset(fx, fy), style = Stroke(1.5.dp.toPx()))
            }
            if (b > 0f && b < .75f) { val r = b / .75f; drawCircle(AMB.copy(alpha = 1 - r), radius = 10.dp.toPx() * (1 + 6 * r), center = Offset(tx, ty), style = Stroke(2.dp.toPx())) }
        }
    }
}

// ------------------------------------------------------------------ 3: a radial spectrum around Mochi
@Composable
private fun MusicStage(on: Boolean) {
    val mochi = asset("thumbs/creature_1.jpg", 300)
    val t = clock()
    val lv = remember { FloatArray(64) }
    val beatPh = t * 2.07f
    val beat = max(0f, cos(beatPh * PI.toFloat() * 2f)).pow(8)
    val hat = max(0f, cos(beatPh * PI.toFloat() * 4f + 1f)).pow(12)
    if (on) for (i in 0 until 64) {
        val f = i / 64f
        val tg = (if (f < .2f) beat * (1 - f * 2) else 0f) + (.25f + .35f * sin(t * 3.3f + i * .7f) * sin(t * 5.9f + i)) * (if (f > .15f && f < .7f) 1f else .4f) + (if (f > .6f) hat * .8f else 0f)
        lv[i] += (max(.05f, tg) - lv[i]) * .25f
    }
    val badge = remember { Animatable(0f) }
    LaunchedEffect(on) { if (on) { delay(600); badge.animateTo(1f, tween(600)) } else badge.snapTo(0f) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("AUDIO SYNC ON", style = T.micro.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .08.em), color = MINT,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).graphicsLayer { alpha = badge.value }.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A2E2B)).padding(horizontal = 12.dp, vertical = 7.dp))
        Canvas(Modifier.size(340.dp)) {
            val cx = size.width / 2; val cy = size.height / 2; val u = size.width / 340f
            for (i in 0 until 64) {
                val a = i / 64f * 2f * PI.toFloat() - PI.toFloat() / 2
                val r0 = (75f + beat * 3f) * u; val r1 = r0 + (9f + lv[i] * 55f) * u
                val p0 = Offset(cx + cos(a) * r0, cy + sin(a) * r0); val p1 = Offset(cx + cos(a) * r1, cy + sin(a) * r1)
                drawLine(Brush.linearGradient(listOf(VIO, if (i % 2 == 1) AMB else LAV), p0, p1), p0, p1, strokeWidth = 4.5f * u, cap = StrokeCap.Round)
            }
            drawCircle(Brush.radialGradient(listOf(VIO.copy(alpha = .45f), Color.Transparent), radius = 110f * u), radius = 110f * u)
        }
        if (mochi != null) Image(mochi, null, contentScale = ContentScale.Crop, modifier = Modifier.size(150.dp).graphicsLayer {
            val s = 1f + beat * .08f; scaleX = s; scaleY = s; rotationZ = sin(t * 1.7f) * 4f }.clip(CircleShape).border(2.dp, LAV.copy(alpha = .35f), CircleShape))
    }
}

// ------------------------------------------------------------------ 4: the Pharaohs fly in, gold marks glow
private val GLYPHS = listOf(
    "M12 2a4 5 0 1 1 0 10a4 5 0 1 1 0-10zM12 12v10M6 14h12", "M2 12q10-9 20 0q-10 9-20 0zM12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6zM9 16l-2 5M12 17v4",
    "M12 5a5 6 0 1 1 0 12a5 6 0 1 1 0-12zM12 5v12M4 8l4 3M20 8l-4 3M4 18l4-3M20 18l-4-3", "M12 4a8 8 0 1 1 0 16a8 8 0 1 1 0-16zM12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6z",
    "M9 3h6v18H9zM6 7h12M6 11h12M6 15h12", "M12 2q8 8 0 20q-8-12 0-20zM12 4v17",
)
@Composable
private fun StatueStage(on: Boolean) {
    val imgs = listOf(asset("thumbs/statue_0.jpg", 520), asset("thumbs/statue_1.jpg", 520), asset("thumbs/statue_2.jpg", 520))
    val paths = remember { GLYPHS.map { PathParser().parsePathString(it).toPath() } }
    var cur by remember { mutableIntStateOf(0) }
    val zoom = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val t = clock()
    LaunchedEffect(on) {
        if (!on) return@LaunchedEffect
        while (true) {
            launch { fade.snapTo(0f); fade.animateTo(1f, tween(1100)) }
            zoom.snapTo(0f); zoom.animateTo(1f, tween(3200, easing = CubicBezierEasing(.2f, .8f, .2f, 1f)))
            cur = (cur + 1) % 3
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            paths.forEachIndexed { i, p ->
                val a = i / paths.size.toFloat() * 2f * PI.toFloat() + .3f
                val x = cx + cos(a) * 160.dp.toPx(); val y = cy + sin(a) * 200.dp.toPx()
                val g = .15f + .8f * (.5f + .5f * sin(t * 2.6f + i * 1.1f))
                translate(x - 13.dp.toPx(), y - 13.dp.toPx() - g * 4f) { scale(26.dp.toPx() / 24f, Offset.Zero) {
                    drawPath(p, AMB.copy(alpha = g * .35f), style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(p, AMB.copy(alpha = g), style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
            }
        }
        Box(Modifier.width(250.dp).height(360.dp).clip(RoundedCornerShape(28.dp)).border(1.dp, AMB.copy(alpha = .25f), RoundedCornerShape(28.dp)).background(Color.Black)) {
            val prev = imgs[(cur + 2) % 3]; val now = imgs[cur]
            if (prev != null) Image(prev, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - fade.value; scaleX = 1.04f; scaleY = 1.04f })
            if (now != null) Image(now, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer {
                val z = 1.6f - .56f * zoom.value; scaleX = z; scaleY = z; alpha = fade.value })
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f)), radius = 600f)))
        }
    }
}

// ------------------------------------------------------------------ 5: pick your first friend
@Composable
private fun PickStage(on: Boolean, pick: Int, onPick: (Int) -> Unit) {
    val ins = remember { Animatable(0f) }
    LaunchedEffect(on) { if (on) ins.animateTo(1f, tween(1100, easing = OUT)) else ins.snapTo(0f) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(Modifier.align(Alignment.TopCenter).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in listOf("60 FPS", "92% BLACK PIXELS")) Text(s, style = T.micro.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = MINT,
                modifier = Modifier.graphicsLayer { alpha = ins.value }.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A2E2B)).padding(horizontal = 12.dp, vertical = 7.dp))
        }
        Column(Modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CAST.chunked(4).forEachIndexed { row, four ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    four.forEachIndexed { col, (id, name) ->
                        val k = ((ins.value * 1.8f) - (row * 4 + col) * .1f).coerceIn(0f, 1f)
                        val sel = id == pick
                        val sc by animateFloatAsState(if (sel) 1.05f else 1f, spring(dampingRatio = .5f), label = "sel")
                        val pic = asset("thumbs/creature_$id.jpg", 200)
                        Box(Modifier.weight(1f).aspectRatio(.82f).graphicsLayer { alpha = k; translationY = (1 - k) * 60f; val s = (.9f + .1f * k) * sc; scaleX = s; scaleY = s }
                            .clip(RoundedCornerShape(18.dp)).border(if (sel) 2.dp else 0.dp, if (sel) LAV else Color.Transparent, RoundedCornerShape(18.dp))
                            .pressable(scaleDown = .94f) { onPick(id) }) {
                            if (pic != null) Image(pic, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.55f to Color.Transparent, 1f to Color.Black.copy(alpha = .85f))))
                            Text(name, style = T.micro.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}
