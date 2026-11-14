package app.pulsify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.pulsify.core.Pulse
import app.pulsify.ui.AppState
import app.pulsify.ui.HomeScreen
import app.pulsify.ui.LocalPulse
import app.pulsify.ui.PulsifyTheme
import app.pulsify.ui.Screen
import app.pulsify.ui.SettingsScreen
import app.pulsify.ui.StudioScreen
import app.pulsify.ui.T
import app.pulsify.ui.pressable
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Pulse.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        super.onCreate(savedInstanceState)
        // Debug builds only: a clean full-screen wallpaper with no UI, for the CI portrait shots.
        if (BuildConfig.DEBUG && intent?.hasExtra("pin") == true) {
            // Debug: ask the launcher to place a friend widget (used by the emulator test).
            app.pulsify.ui.pinWidget(this, intent.getIntExtra("pin", 1), 0)
            setContent { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black)) }
            return
        }
        val portrait = BuildConfig.DEBUG && intent?.hasExtra("portrait") == true
        if (portrait) {
            val i = intent
            val p = Pulse.prefs
            val v = i.getIntExtra("video", -1)
            if (v >= 0) p.video = v else {
                p.video = -1
                p.character = i.getIntExtra("char", 1)
                if (i.hasExtra("env")) p.env = i.getIntExtra("env", 0)
                if (i.hasExtra("coat")) p.coat = i.getIntExtra("coat", 0)
                if (i.hasExtra("variant")) p.variant = i.getIntExtra("variant", 0)
            }
            Pulse.forceCalm = !i.hasExtra("charging")
            Pulse.previewMood = when (i.getStringExtra("mood")) {
                "night" -> app.pulsify.core.Mood.NIGHT; "evening" -> app.pulsify.core.Mood.EVENING
                "morning" -> app.pulsify.core.Mood.MORNING; else -> app.pulsify.core.Mood.DAY
            }
            val friends = i.getStringExtra("friends")
            if (friends != null) {
                // A full-screen friends playground (debug shots of the widget creatures).
                val picks = friends.split(',').mapNotNull { it.trim().toIntOrNull() }.let { if (it.size >= 3) it.take(3) else listOf(1, 4, 7) }
                setContent {
                    app.pulsify.ui.PulsifyTheme(Color(0xFFCDB4FF), Color(0xFFFFB36B)) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            app.pulsify.ui.Playground(picks, Modifier, autoDemo = true)
                        }
                    }
                }
                return
            }
            val next = i.getIntExtra("video2", -1)
            if (next >= 0) android.os.Handler(mainLooper).postDelayed({ Pulse.prefs.video = next }, 5000)
            setContent { app.pulsify.ui.LiveSky(Modifier.fillMaxSize(), renderScale = 1f, interactive = true, fpsCap = 0) }
            return
        }
        Pulse.forceCalm = false
        val start = if (intent?.getStringExtra("open") == "friends") Screen.FRIENDS else Screen.HOME
        setContent { App(start) }
    }
}

@Composable
private fun App(start: Screen = Screen.HOME) {
    val state = remember { AppState().also { it.screen = start } }
    DisposableEffect(Unit) { onDispose { state.dispose() } }
    LaunchedEffect(Unit) {
        delay(900)
    }
    val (a1, a2) = state.accentPair
    PulsifyTheme(a1, a2) {
        BackHandler(enabled = state.screen != Screen.HOME) { state.screen = Screen.HOME }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AnimatedContent(
                state.screen,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.97f))
                        .togetherWith(fadeOut() + scaleOut(targetScale = 1.02f))
                },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.HOME -> HomeScreen(state, onOpenStudio = { state.screen = Screen.STUDIO }, onSettings = { state.screen = Screen.SETTINGS }, onFriends = { state.screen = Screen.FRIENDS })
                    Screen.STUDIO -> StudioScreen(state, onBack = { state.screen = Screen.HOME })
                    Screen.SETTINGS -> SettingsScreen(state, onBack = { state.screen = Screen.HOME })
                    Screen.FRIENDS -> app.pulsify.ui.FriendsScreen(state, onBack = { state.screen = Screen.HOME })
                }
            }
            ToastBanner(state, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Celebration banner for daily sparks and discoveries. Slides down, stays 3.2 s. */
@Composable
private fun ToastBanner(state: AppState, modifier: Modifier) {
    val c = LocalPulse.current
    val toast = state.toast
    val hf = LocalHapticFeedback.current
    val last = remember { arrayOfNulls<AppState.Toast>(1) }
    if (toast != null) last[0] = toast
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            hf.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(3200)
            if (state.toast?.id == toast.id) state.toast = null
        }
    }
    AnimatedVisibility(
        toast != null,
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp),
        enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut(),
    ) {
        val t = last[0] ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF1C1726), Color(0xFF15111D))))
                .border(1.dp, Brush.horizontalGradient(listOf(c.accent, c.accent2)), RoundedCornerShape(22.dp))
                .pressable(haptic = false) { state.toast = null }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.accent, c.accent2))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Star, null, tint = c.onAccent, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(t.title, style = T.bodyStrong, color = c.text)
                Text(t.line, style = T.small, color = c.sub)
            }
        }
    }
}
