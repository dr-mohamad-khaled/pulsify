package app.pulsify.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pulsify.core.Buddies
import app.pulsify.core.Pulse
import app.pulsify.engine.CREATURES
import app.pulsify.engine.creatureInfo
import app.pulsify.ui.ColorOrb
import app.pulsify.ui.Gap
import app.pulsify.ui.GlowButton
import app.pulsify.ui.LocalPulse
import app.pulsify.ui.PulsifyTheme
import app.pulsify.ui.T
import app.pulsify.ui.decodeSmall
import app.pulsify.ui.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens when a friend widget is added from the launcher: pick who moves in. */
class BuddySetupActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        Pulse.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val start = Buddies.slot(widgetId)
        setContent {
            var pick by remember { mutableIntStateOf(start?.creature ?: Pulse.settings.character.coerceIn(1, CREATURES.size)) }
            var coat by remember { mutableIntStateOf(start?.coat ?: 0) }
            val info = creatureInfo(pick) ?: CREATURES.first()
            val co = info.coats.getOrNull(coat) ?: info.coats.first()
            PulsifyTheme(Color(info.accent), Color(co.rim)) {
                SetupScreen(pick, coat, onPick = { pick = it; coat = 0 }, onCoat = { coat = it }) { done(pick, coat) }
            }
        }
    }

    private fun done(creature: Int, coat: Int) {
        Buddies.assign(widgetId, creature, coat)
        BuddyWidgetProvider.refresh(this, intArrayOf(widgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

/** The launcher calls this after a pinned widget lands (pinned from inside the app). */
class PinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Pulse.init(context)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        Buddies.assign(id, intent.getIntExtra("creature", 1), intent.getIntExtra("coat", 0))
        BuddyWidgetProvider.refresh(context, intArrayOf(id))
    }
}

@Composable
private fun SetupScreen(pick: Int, coat: Int, onPick: (Int) -> Unit, onCoat: (Int) -> Unit, onDone: () -> Unit) {
    val c = LocalPulse.current
    val ctx = LocalContext.current
    val info = creatureInfo(pick) ?: CREATURES.first()
    val ours = remember { Buddies.isOurWallpaper(ctx) }
    val bob = rememberInfiniteTransition(label = "bob")
    val y by bob.animateFloat(0f, 1f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "y")
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Gap(26.dp)
            Text("Who moves in?", style = T.display, color = c.text)
            Gap(8.dp)
            Text("They'll live in this widget, react when you swipe to them and play with friends next door.", style = T.body, color = c.sub)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            // The widget, as it will feel on the home screen: a glowing tile, gently breathing.
            Box(
                Modifier.fillMaxWidth(0.68f).aspectRatio(1f)
                    .graphicsLayer { translationY = -10f * y; rotationZ = (y - 0.5f) * 1.6f }
                    .drawBehind {
                        drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.42f), Color.Transparent)), radius = size.maxDimension * 0.78f)
                    },
            ) {
                AnimatedContent(pick, transitionSpec = { (fadeIn(tween(260)) + scaleIn(spring(dampingRatio = 0.6f), 0.9f)).togetherWith(fadeOut(tween(160))) }, label = "pick") { id ->
                    val pic by produceState<ImageBitmap?>(null, id) { value = withContext(Dispatchers.IO) { decodeSmall(ctx, "thumbs/creature_$id.jpg", 300) } }
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(34.dp)).border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(34.dp)).background(c.surface2)) {
                        pic?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                            Text(creatureInfo(id)?.name ?: "", style = T.title, color = Color.White)
                            Text(creatureInfo(id)?.line ?: "", style = T.small, color = Color.White.copy(alpha = 0.78f), maxLines = 2)
                        }
                    }
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(CREATURES) { cr ->
                val sel = cr.id == pick
                val pic by produceState<ImageBitmap?>(null, cr.id) { value = withContext(Dispatchers.IO) { decodeSmall(ctx, "thumbs/creature_${cr.id}.jpg", 160) } }
                Column(Modifier.width(70.dp).pressable { onPick(cr.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(62.dp).graphicsLayer { val s = if (sel) 1f else 0.86f; scaleX = s; scaleY = s }.clip(CircleShape)
                            .border(if (sel) 2.5.dp else 1.dp, if (sel) Color(cr.accent) else Color.White.copy(alpha = 0.1f), CircleShape)
                            .padding(if (sel) 3.dp else 0.dp).clip(CircleShape).background(c.surface2),
                    ) { pic?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter) } }
                    Gap(6.dp)
                    Text(cr.name, style = T.micro, color = if (sel) c.text else c.faint, maxLines = 1)
                }
            }
        }
        if (info.coats.size > 1) {
            Gap(14.dp)
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(info.coats.size) { i ->
                    val co = info.coats[i]
                    ColorOrb(Color(co.base), Color(co.rim), i == coat, size = 46.dp, modifier = Modifier.pressable { onCoat(i) }.semantics { contentDescription = co.name })
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Gap(20.dp)
            GlowButton("Move ${info.name} in", Modifier.fillMaxWidth(), onClick = onDone)
            Gap(12.dp)
            Text(
                if (ours) "Tap the widget once after it's placed so ${info.name} can find its spot."
                else "${info.name} comes alive once Pulsify is your wallpaper. Until then you'll see a picture.",
                style = T.small, color = c.faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Gap(16.dp)
        }
    }
}
