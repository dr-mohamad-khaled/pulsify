package app.pulsify;

import android.Manifest;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

/** Bridge between the Pulsify engine (JavaScript) and Android: device music (FFT), battery,
 *  screen events, home-screen page offset, haptics and setting the live wallpaper. */
public class Host {
    static final String PREFS = "pulsify";
    final Context ctx; final boolean wallpaper; final Activity activity;
    final AudioManager am;
    volatile float offset = 0f; volatile boolean visible = true; volatile int unlocks = 0, screenOffs = 0;
    private Visualizer vis; private int captureSize = 1024;
    private final float[] bands = new float[3]; private final int[] spec = new int[32]; private volatile long lastCap = 0, lastTry = 0; private volatile float energy = 0;
    private float batLevel = 1f; private boolean charging = false; private long batAt = 0;

    Host(Context c, boolean wallpaper, Activity a) { ctx = c; this.wallpaper = wallpaper; activity = a; am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE); }

    boolean audioGranted() { return ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    synchronized void startAudio() {
        if (vis != null || !audioGranted()) return;
        try {
            vis = new Visualizer(0);
            vis.setEnabled(false);
            int[] r = Visualizer.getCaptureSizeRange();
            captureSize = Math.min(1024, r[1]);
            vis.setCaptureSize(captureSize);
            try { vis.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED); } catch (Throwable ignored) {}
            vis.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int sr) {}
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int sr) { analyze(fft, sr); }
            }, Visualizer.getMaxCaptureRate(), false, true);
            vis.setEnabled(true);
        } catch (Throwable t) { vis = null; }
    }
    synchronized void stopAudio() { if (vis != null) { try { vis.setEnabled(false); vis.release(); } catch (Throwable ignored) {} vis = null; } }

    private void analyze(byte[] fft, int srMilli) {
        int n = fft.length / 2; float sr = srMilli / 1000f; float binHz = sr / fft.length;
        float b = 0, m = 0, t = 0; int bn = 0, mn = 0, tn = 0; float[] lg = new float[32]; int[] lc = new int[32];
        double lo = Math.log(40), hi = Math.log(16000);
        for (int k = 1; k < n; k++) {
            float re = fft[2 * k], im = fft[2 * k + 1];
            float mag = (float) Math.hypot(re, im);
            float db = (float) (20 * Math.log10(mag + 1));
            float v = Math.max(0f, Math.min(1f, (db - 6f) / 34f));
            float f = k * binHz;
            if (f >= 20 && f < 250) { b += v; bn++; } else if (f < 4000) { m += v; mn++; } else if (f < 16000) { t += v; tn++; }
            if (f >= 40 && f < 16000) { int i = (int) ((Math.log(f) - lo) / (hi - lo) * 32); if (i >= 0 && i < 32) { lg[i] = Math.max(lg[i], v); lc[i]++; } }
        }
        synchronized (bands) {
            bands[0] = bn > 0 ? b / bn : 0; bands[1] = mn > 0 ? m / mn : 0; bands[2] = tn > 0 ? t / tn : 0;
            energy = bands[0] + bands[1] + bands[2];
            for (int i = 0; i < 32; i++) spec[i] = (int) (lg[i] * 255);
        }
        lastCap = System.currentTimeMillis();
    }

    private void readBattery() {
        long now = System.currentTimeMillis(); if (now - batAt < 4000) return; batAt = now;
        Intent bi = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi == null) return;
        int lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (lvl >= 0 && sc > 0) batLevel = lvl / (float) sc;
        int st = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL || bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    @JavascriptInterface public String state() {
        readBattery();
        long now = System.currentTimeMillis();
        // keep the visualizer alive: start it once permission arrives, restart it if it went quiet
        if (visible && now - lastTry > 2500) { lastTry = now;
            if (vis == null && audioGranted()) startAudio();
            else if (vis != null && now - lastCap > 3000) { stopAudio(); startAudio(); } }
        boolean recent = vis != null && now - lastCap < 600;
        boolean music = (am != null && am.isMusicActive()) || (recent && energy > 0.06f);
        StringBuilder s = new StringBuilder(220);
        s.append("{\"music\":").append(music).append(",\"battery\":").append(batLevel).append(",\"charging\":").append(charging)
         .append(",\"offset\":").append(offset).append(",\"visible\":").append(visible)
         .append(",\"unlocks\":").append(unlocks).append(",\"screenOffs\":").append(screenOffs);
        if (music && recent) {
            synchronized (bands) {
                s.append(",\"bands\":[").append(bands[0]).append(',').append(bands[1]).append(',').append(bands[2]).append("],\"spec\":[");
                for (int i = 0; i < 32; i++) { if (i > 0) s.append(','); s.append(spec[i]); }
                s.append(']');
            }
        } else s.append(",\"bands\":null,\"spec\":null");
        return s.append('}').toString();
    }
    @JavascriptInterface public boolean isWallpaper() { return wallpaper; }
    @JavascriptInterface public boolean hasAudio() { return audioGranted(); }
    @JavascriptInterface public String config() { return ctx.getSharedPreferences(PREFS, 0).getString("wp", "{}"); }
    @JavascriptInterface public void requestAudio() {
        if (activity != null && !audioGranted()) activity.runOnUiThread(() -> activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MainActivity.REQ_MIC_NATIVE));
    }
    @JavascriptInterface public void haptic(int ms) {
        try { Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE); if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(Math.max(5, Math.min(60, ms)), 80)); else v.vibrate(ms);
        } catch (Throwable ignored) {}
    }
    @JavascriptInterface public void setWallpaper(String json) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, 0).edit(); e.putString("wp", json); e.apply();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(activity, PulsifyWallpaper.class));
                activity.startActivity(i);
            } catch (Throwable t) {
                try { activity.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); Toast.makeText(activity, "Choose Pulsify from the list", Toast.LENGTH_LONG).show(); }
                catch (Throwable ignored) {}
            }
        });
    }
}
