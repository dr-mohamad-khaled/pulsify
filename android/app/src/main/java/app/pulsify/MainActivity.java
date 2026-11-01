package app.pulsify;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/** The Pulsify app: browse, preview and set wallpapers. */
public class MainActivity extends Activity {
    static final int REQ_MIC = 7, REQ_FILE = 8, REQ_MIC_NATIVE = 9;
    private WebView web; private Host host;
    private PermissionRequest pendingMic; private ValueCallback<Uri[]> fileCb;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        setContentView(web);
        immersive();
        host = new Host(this, false, this);
        Web.setup(web, host);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) req.grant(req.getResources());
                    else { pendingMic = req; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); }
                });
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(p.createIntent(), REQ_FILE); } catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        if (b == null) web.loadUrl(Web.URL); else web.restoreState(b);
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) { c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_MIC && pendingMic != null) { if (ok) pendingMic.grant(pendingMic.getResources()); else pendingMic.deny(); pendingMic = null; }
        if (ok) host.startAudio();
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_FILE && fileCb != null) { fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data)); fileCb = null; }
        else super.onActivityResult(code, result, data);
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("(function(){if(typeof view!=='undefined'&&view==='studio'){closeStudio();return 1}return 0})()",
                v -> { if (!"1".equals(v)) MainActivity.super.onBackPressed(); });
    }
    @Override protected void onPause() { super.onPause(); web.onPause(); host.stopAudio(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); immersive(); host.startAudio(); }
    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
}
