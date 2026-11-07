package app.pulsify.ui

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pulsify.core.Pulse
import app.pulsify.engine.GlLoop
import app.pulsify.engine.SceneListener
import kotlin.math.roundToInt

/**
 * The real wallpaper engine running inside the app (same shader, same scene logic).
 * Uses a TextureView so it clips to rounded cards. Pauses with the activity.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun LiveSky(
    modifier: Modifier = Modifier,
    renderScale: Float = 0.75f,
    interactive: Boolean = false,
    onLoop: (GlLoop) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val main = remember { Handler(Looper.getMainLooper()) }
    val loop = remember {
        GlLoop(context, provideInput = { input, dt -> Pulse.fillInput(input, dt, preview = true, parallax = 0f) }).also { l ->
            l.batterySaver = false
            l.listener = object : SceneListener {
                override fun onDiscovery(id: String) { main.post { Pulse.prefs.discover(id) } }
                override fun onBondXp(amount: Int) { main.post { Pulse.prefs.addXp(amount) } }
            }
        }
    }
    val holder = remember { arrayOfNulls<Surface>(1) }
    val visible = remember { booleanArrayOf(false, true) } // [hasSurface, resumed]

    fun updateRunning() {
        loop.setRunning(visible[0] && visible[1])
    }

    DisposableEffect(lifecycle) {
        Pulse.audio.acquire()
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> { visible[1] = true; updateRunning() }
                Lifecycle.Event.ON_PAUSE -> { visible[1] = false; updateRunning() }
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onLoop(loop)
        onDispose {
            lifecycle.removeObserver(obs)
            Pulse.audio.release()
            loop.setRunning(false)
            loop.quit(waitMs = 300)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        val bw = (width * renderScale).roundToInt().coerceAtLeast(1)
                        val bh = (height * renderScale).roundToInt().coerceAtLeast(1)
                        st.setDefaultBufferSize(bw, bh)
                        val s = Surface(st)
                        holder[0] = s
                        loop.attach(s, bw, bh)
                        visible[0] = true
                        updateRunning()
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                        val bw = (width * renderScale).roundToInt().coerceAtLeast(1)
                        val bh = (height * renderScale).roundToInt().coerceAtLeast(1)
                        st.setDefaultBufferSize(bw, bh)
                        loop.resize(bw, bh)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        visible[0] = false
                        if (!loop.dead) {
                            loop.setRunning(false)
                            loop.detach()
                        }
                        holder[0]?.release()
                        holder[0] = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
                if (interactive) {
                    setOnTouchListener { v, e ->
                        val nx = e.x / v.width.coerceAtLeast(1)
                        val ny = e.y / v.height.coerceAtLeast(1)
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> loop.touchDown(nx, ny)
                            MotionEvent.ACTION_MOVE -> loop.touchMove(nx, ny)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> loop.touchUp()
                        }
                        true
                    }
                }
            }
        },
    )
}
