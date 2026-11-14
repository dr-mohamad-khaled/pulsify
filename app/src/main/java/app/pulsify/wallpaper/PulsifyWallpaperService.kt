package app.pulsify.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import app.pulsify.core.BuddyPoke
import app.pulsify.core.BuddySlot
import app.pulsify.core.Buddies
import app.pulsify.core.Pulse
import app.pulsify.engine.BuddyFeed
import app.pulsify.widget.BuddyWidgetProvider
import app.pulsify.core.SKY_VARIANTS
import app.pulsify.engine.GlLoop
import app.pulsify.engine.SceneListener
import kotlin.random.Random

class PulsifyWallpaperService : WallpaperService() {

    override fun onCreate() {
        super.onCreate()
        Pulse.init(this)
    }

    override fun onCreateEngine(): Engine = PulseEngine()

    inner class PulseEngine : Engine() {
        private val main = Handler(Looper.getMainLooper())
        private var parallax = 0f
        private var baseW = 0
        private var baseH = 0
        private var visible = false
        private var audioHeld = false
        @Volatile private var gone = false

        private val loop = GlLoop(
            this@PulsifyWallpaperService,
            provideInput = { input, dt -> Pulse.fillInput(input, dt, preview = false, parallax = parallax) },
        )

        /** The real home-screen widgets (only for the engine on the home screen, not previews). */
        private val feed = object : BuddyFeed() {
            override fun slots(): List<BuddySlot> = Buddies.slots
            override fun pollPoke(): BuddyPoke? = Buddies.pokes.poll()
            override fun placed(widgetId: Int, page: Int, l: Float, t: Float, r: Float, b: Float) = Buddies.place(widgetId, page, l, t, r, b)
        }
        private val keyguard by lazy { getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager }

        private val settingsListener: () -> Unit = {
            loop.batterySaver = Pulse.settings.batterySaver
        }

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                feed.hidden = false
                feed.greetSeq++
                if (Pulse.settings.shuffleOnUnlock) {
                    val cur = Pulse.settings.variant
                    var next = Random.nextInt(SKY_VARIANTS.size)
                    if (next == cur) next = (next + 1) % SKY_VARIANTS.size
                    Pulse.prefs.variant = next
                }
                loop.greet()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            loop.batterySaver = Pulse.settings.batterySaver
            Pulse.addSettingsListener(settingsListener)
            if (!isPreview) {
                Buddies.pokes.clear()
                loop.buddyFeed = feed
                Buddies.wallpaperLive = true
                main.postDelayed({ BuddyWidgetProvider.refresh(applicationContext) }, 600)
            }
            val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(unlockReceiver, filter)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            baseW = width; baseH = height
            loop.attach(holder.surface, width, height)
            // A resize can recreate the surface without a visibility change; keep drawing.
            loop.setRunning(visible)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            loop.setRunning(false)
            loop.detach()
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            try {
                if (isVisible) {
                    if (!audioHeld) { Pulse.audio.acquire(); audioHeld = true }
                    feed.hidden = try { keyguard.isKeyguardLocked } catch (_: Throwable) { false }
                    Pulse.device.poll(force = true)
                } else if (audioHeld) {
                    Pulse.audio.release(); audioHeld = false
                }
            } catch (t: Throwable) { app.pulsify.core.CrashLog.note("visibility: $t") }
            loop.setRunning(isVisible)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            // Swiping home-screen pages only sways the sky a little.
            parallax = (xOffset - 0.5f) * 2f
            // ...but the widget creatures ride along with the pages.
            val known = xStep > 0.0001f && xStep <= 1f
            feed.pagesKnown = known
            feed.pagePos = if (known) xOffset / xStep else 0f
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (gone) return
            val w = if (baseW > 0) baseW else surfaceHolder.surfaceFrame.width()
            val h = if (baseH > 0) baseH else surfaceHolder.surfaceFrame.height()
            if (w <= 0 || h <= 0) return
            val nx = event.x / w
            val ny = event.y / h
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> loop.touchDown(nx, ny)
                MotionEvent.ACTION_MOVE -> loop.touchMove(nx, ny)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> loop.touchUp()
            }
        }

        override fun onDestroy() {
            gone = true
            if (!isPreview) {
                Buddies.wallpaperLive = false
                val ctx = applicationContext
                Handler(Looper.getMainLooper()).postDelayed({ BuddyWidgetProvider.refresh(ctx) }, 1500)
            }
            main.removeCallbacksAndMessages(null)
            Pulse.removeSettingsListener(settingsListener)
            try { unregisterReceiver(unlockReceiver) } catch (_: Throwable) {}
            if (audioHeld) { Pulse.audio.release(); audioHeld = false }
            loop.quit(waitMs = 300)
            super.onDestroy()
        }
    }
}
