package app.pulsify.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Springy press feedback + a light haptic tick. */
fun Modifier.pressable(scaleDown: Float = 0.95f, haptic: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) scaleDown else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val hf = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null) {
            if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Dark glass card with a 1 px accent-to-amber specular rim. No blur: it may sit over live canvases. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    rim: Float = 0.38f,
    content: @Composable () -> Unit,
) {
    val c = LocalPulse.current
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.surface2, c.surface)))
            .border(
                1.dp,
                Brush.linearGradient(listOf(c.accent.copy(alpha = rim), c.border.copy(alpha = 0.4f), c.accent2.copy(alpha = rim * 0.8f))),
                shape,
            ),
    ) { content() }
}

@Composable
fun MicroLabel(text: String, color: Color = LocalPulse.current.sub, modifier: Modifier = Modifier) {
    Text(text, style = T.micro, color = color, modifier = modifier)
}

/** The main action: a solid pill in the character's colour with a soft halo that breathes with the music. */
@Composable
fun GlowButton(
    text: String,
    modifier: Modifier = Modifier,
    pulse: Float = 0f,
    height: Dp = 58.dp,
    onClick: () -> Unit,
) {
    val c = LocalPulse.current
    val glow = 0.22f + 0.45f * pulse
    Box(
        modifier
            .height(height)
            .drawBehind {
                drawRoundRect(
                    brush = Brush.radialGradient(listOf(c.accent.copy(alpha = glow), Color.Transparent), center = Offset(size.width / 2, size.height * 0.75f), radius = size.width * 0.55f),
                    topLeft = Offset(-size.width * 0.05f, -size.height * 0.2f),
                    size = Size(size.width * 1.1f, size.height * 1.9f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
                )
            }
            .clip(RoundedCornerShape(50))
            .background(c.accent)
            .drawBehind {
                // A glassy top highlight so it reads as a physical button.
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), endY = size.height * 0.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height),
                )
            }
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.button, color = c.onAccent)
    }
}

/** Secondary outlined pill. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, height: Dp = 48.dp, onClick: () -> Unit) {
    val c = LocalPulse.current
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(c.surface2.copy(alpha = 0.85f))
            .border(1.dp, c.border, RoundedCornerShape(50))
            .pressable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.bodyStrong, color = c.text) }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalPulse.current
    val bg by androidx.compose.animation.animateColorAsState(if (selected) c.text else Color.White.copy(alpha = 0.06f), label = "pill")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) Color(0xFF0B0A0C) else c.sub, label = "pillfg")
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.bodyStrong, color = fg) }
}

/** Status badge: mint text on deep teal, with an optional live dot. */
@Composable
fun StatusChip(text: String, live: Boolean, modifier: Modifier = Modifier, color: Color = LocalPulse.current.mint) {
    val c = LocalPulse.current
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (live) color.copy(alpha = a) else c.faint))
        Spacer(Modifier.width(6.dp))
        Text(text, style = T.micro, color = if (live) c.text else c.sub)
    }
}

/** Animated gradient orb used as the logo. */
@Composable
fun OrbLogo(size: Dp = 30.dp) {
    val c = LocalPulse.current
    val t = rememberInfiniteTransition(label = "orb")
    val r by t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "r")
    Canvas(Modifier.size(size)) {
        drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.45f), Color.Transparent), radius = this.size.minDimension), radius = this.size.minDimension * 0.75f)
        rotate(r) {
            drawCircle(Brush.sweepGradient(listOf(c.accent, Color(0xFF9D4EDD), c.accent2, c.accent)), radius = this.size.minDimension / 2.4f)
        }
        drawCircle(Color.Black.copy(alpha = 0.55f), radius = this.size.minDimension / 5.2f, center = Offset(this.size.width * 0.56f, this.size.height * 0.44f))
    }
}

/** Ring that fills with bond XP progress. */
@Composable
fun BondRing(progress: Float, level: Int, size: Dp = 64.dp, labelStyle: TextStyle = T.heading) {
    val c = LocalPulse.current
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.09f
            drawCircle(c.border.copy(alpha = 0.6f), style = Stroke(stroke))
            drawArc(
                Brush.sweepGradient(listOf(c.accent, c.accent2, c.accent)),
                startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text("$level", style = labelStyle, color = c.text)
    }
}

/**
 * A tiny live sky drawn with Compose (cheap): used for gallery tiles so the home
 * screen never runs extra GL contexts.
 */
@Composable
fun MiniSky(c1: Color, c2: Color, seed: Int, modifier: Modifier = Modifier, style: Int = 0) {
    val stars = remember(seed) {
        val r = Random(seed)
        List(46) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 1.6f + 0.5f, r.nextFloat() * 6.28f) }
    }
    val t = rememberInfiniteTransition(label = "mini")
    val time by t.animateFloat(0f, 2f * PI.toFloat() * 10f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "t")
    Canvas(modifier.background(Color.Black)) {
        val w = size.width; val h = size.height
        when (style) {
            1 -> { // aurora
                for (i in 0 until 3) {
                    val y = h * (0.28f + 0.08f * i + 0.03f * sin(time * 1.3f + i))
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, c2.copy(alpha = 0.18f), c1.copy(alpha = 0.45f), Color.Transparent), startY = y - h * 0.22f, endY = y + h * 0.06f),
                        topLeft = Offset(0f, y - h * 0.22f), size = Size(w, h * 0.28f),
                    )
                }
            }
            2 -> { // nebula
                drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.38f), Color.Transparent), center = Offset(w * (0.4f + 0.05f * sin(time)), h * 0.42f), radius = w * 0.55f), radius = w, center = Offset(w / 2, h / 2))
                drawCircle(Brush.radialGradient(listOf(c2.copy(alpha = 0.3f), Color.Transparent), center = Offset(w * 0.68f, h * (0.6f + 0.04f * cos(time))), radius = w * 0.45f), radius = w, center = Offset(w / 2, h / 2))
            }
            3 -> { // creature orb
                val cx = w / 2; val cy = h * 0.52f + h * 0.02f * sin(time * 2f)
                drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.32f), Color.Transparent), center = Offset(cx, cy), radius = w * 0.6f), radius = w * 0.6f, center = Offset(cx, cy))
                drawCircle(Brush.radialGradient(listOf(c1, c2.copy(alpha = 0.85f), c2.copy(alpha = 0.1f)), center = Offset(cx - w * 0.06f, cy - w * 0.08f), radius = w * 0.3f), radius = w * 0.24f, center = Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = 0.5f), radius = w * 0.035f, center = Offset(cx - w * 0.08f, cy - w * 0.1f))
            }
            else -> { // milky band
                drawRect(Brush.linearGradient(listOf(Color.Transparent, c1.copy(alpha = 0.16f), c2.copy(alpha = 0.12f), Color.Transparent), start = Offset(0f, h * 0.15f), end = Offset(w, h * 0.85f)))
            }
        }
        for (s in stars) {
            val tw = 0.45f + 0.55f * (0.5f + 0.5f * sin(time * s[2] + s[3]))
            drawCircle(Color.White.copy(alpha = 0.85f * tw), radius = s[2] * 0.9f, center = Offset(s[0] * w, s[1] * h))
        }
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HRow(modifier: Modifier = Modifier, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) { content() }
}


/** A soft on/off switch: a pill track that fills with the accent, with a glossy knob. */
@Composable
fun PillSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPulse.current
    val k by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "sw")
    Box(
        modifier.size(54.dp, 32.dp).clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.1f), c.accent, k))
            .pressable(scaleDown = 0.92f) { onChange(!checked) },
    ) {
        Box(
            Modifier.padding(3.dp).graphicsLayer { translationX = k * 22.dp.toPx() }.size(26.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFDCD8DF)), center = Offset(18f, 14f))),
        )
    }
}

/** A glossy little planet of colour: used for coats, accents and paint jobs. */
@Composable
fun ColorOrb(base: Color, rim: Color, selected: Boolean, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val c = LocalPulse.current
    val s by animateFloatAsState(if (selected) 1f else 0.78f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "orb")
    Canvas(modifier.size(size).graphicsLayer { scaleX = s; scaleY = s }) {
        val r = this.size.minDimension / 2f
        if (selected) drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.55f), Color.Transparent), radius = r * 1.6f), radius = r * 1.6f)
        drawCircle(
            Brush.radialGradient(
                listOf(androidx.compose.ui.graphics.lerp(base, Color.White, 0.55f), base, androidx.compose.ui.graphics.lerp(rim, Color.Black, 0.35f)),
                center = Offset(center.x - r * 0.35f, center.y - r * 0.4f), radius = r * 1.5f,
            ),
            radius = r * 0.86f,
        )
        drawCircle(Color.White.copy(alpha = 0.55f), radius = r * 0.16f, center = Offset(center.x - r * 0.32f, center.y - r * 0.36f))
        if (selected) drawCircle(c.text, radius = r * 0.97f, style = Stroke(r * 0.05f))
    }
}
