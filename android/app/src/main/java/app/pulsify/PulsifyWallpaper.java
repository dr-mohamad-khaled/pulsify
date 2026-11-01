package app.pulsify;

import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.webkit.WebView;

/** Live wallpaper: the Pulsify engine runs in a WebView shown on a private virtual display
 *  whose output surface is the wallpaper surface, so WebGL and canvas render straight to it. */
public class PulsifyWallpaper extends WallpaperService {
    @Override public Engine onCreateEngine() { return new WebEngine(); }

    class WebEngine extends Engine {
        private VirtualDisplay vd; private Presentation pres; private WebView web; private Host host; private BroadcastReceiver rx;
        private int w, h;

        @Override public void onCreate(SurfaceHolder sh) {
            super.onCreate(sh);
            setTouchEventsEnabled(true);
            setOffsetNotificationsEnabled(true);
            host = new Host(PulsifyWallpaper.this, true, null);
            rx = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    String a = i.getAction();
                    if (Intent.ACTION_USER_PRESENT.equals(a)) host.unlocks++;
                    else if (Intent.ACTION_SCREEN_OFF.equals(a)) host.screenOffs++;
                }
            };
            IntentFilter f = new IntentFilter(); f.addAction(Intent.ACTION_USER_PRESENT); f.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_EXPORTED); else registerReceiver(rx, f);
        }

        @Override public void onSurfaceChanged(SurfaceHolder sh, int format, int width, int height) {
            super.onSurfaceChanged(sh, format, width, height);
            if (vd != null && width == w && height == h) return;
            w = width; h = height;
            teardown();
            try { build(sh); } catch (Throwable t) { fallback(sh, t); }
        }

        private void build(SurfaceHolder sh) {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            int dpi = getResources().getDisplayMetrics().densityDpi;
            vd = dm.createVirtualDisplay("pulsify-wallpaper", w, h, dpi, sh.getSurface(), 0);
            pres = new Presentation(PulsifyWallpaper.this, vd.getDisplay());
            if (pres.getWindow() != null) pres.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            web = new WebView(pres.getContext());
            Web.setup(web, host);
            pres.setContentView(web);
            pres.show();
            web.loadUrl(Web.URL + "?wp=1");
            if (isVisible()) host.startAudio();
        }

        private void fallback(SurfaceHolder sh, Throwable t) {
            try {
                Canvas c = sh.lockCanvas(); if (c == null) return;
                c.drawColor(Color.BLACK);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xFFCDB4FF); p.setTextSize(42); p.setTextAlign(Paint.Align.CENTER);
                c.drawText("Pulsify could not start here", w / 2f, h / 2f, p);
                p.setTextSize(28); p.setColor(0xFF9E96B0); c.drawText(String.valueOf(t.getClass().getSimpleName()), w / 2f, h / 2f + 50, p);
                sh.unlockCanvasAndPost(c);
            } catch (Throwable ignored) {}
        }

        private void teardown() {
            try { if (pres != null) pres.dismiss(); } catch (Throwable ignored) {}
            try { if (web != null) web.destroy(); } catch (Throwable ignored) {}
            try { if (vd != null) vd.release(); } catch (Throwable ignored) {}
            pres = null; web = null; vd = null;
        }

        @Override public void onTouchEvent(MotionEvent e) { if (web != null) web.dispatchTouchEvent(e); }

        @Override public void onOffsetsChanged(float xOff, float yOff, float xStep, float yStep, int xPx, int yPx) { host.offset = xOff; }

        @Override public void onVisibilityChanged(boolean v) {
            host.visible = v;
            if (web == null) return;
            if (v) { web.onResume(); host.startAudio(); } else { web.onPause(); host.stopAudio(); }
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder sh) { teardown(); super.onSurfaceDestroyed(sh); }

        @Override public void onDestroy() {
            teardown(); host.stopAudio();
            try { unregisterReceiver(rx); } catch (Throwable ignored) {}
            super.onDestroy();
        }
    }
}
