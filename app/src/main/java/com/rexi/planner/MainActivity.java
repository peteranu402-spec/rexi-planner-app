package com.rexi.planner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

public class MainActivity extends Activity {
    static volatile boolean foreground;
    private static final int FILE_REQ = 41;
    private static final int NOTIF_REQ = 42;
    static final int SOUND_SYSTEM_REQ = 45;
    static final int SOUND_FILE_REQ = 46;

    private WebView web;
    private AlarmBridge bridge;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingBlocked;
    private boolean pendingStudyReturn;
    private boolean pageReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySavedTheme();
        bridge = new AlarmBridge(this, null);
        web = WebViewFactory.create(this, bridge);
        bridge.attach(web);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                return JsDialogs.show(MainActivity.this, message, result, false);
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                return JsDialogs.show(MainActivity.this, message, result, true);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose your backup file"), FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl(WebViewFactory.START_URL);
        pendingBlocked = getIntent().getStringExtra("blocked");
        pendingStudyReturn = getIntent().getBooleanExtra("returnToStudy", false);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_REQ);
        }
        AlarmScheduler.scheduleAll(this);
    }

    /** Paint the window in the theme the user picked inside the app (not just the phone's), before the page loads. */
    @SuppressWarnings("deprecation")
    private void applySavedTheme() {
        try {
            android.content.SharedPreferences p = getSharedPreferences("ui", MODE_PRIVATE);
            String bg = p.getString("bg", null);
            if (bg == null) return;
            int c = android.graphics.Color.parseColor(bg);
            boolean light = p.getBoolean("lightIcons", true);
            android.view.Window w = getWindow();
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(c));
            w.setStatusBarColor(c);
            w.setNavigationBarColor(c);
            android.view.View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            if (light) f |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else f &= ~(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            d.setSystemUiVisibility(f);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        lastTouch = android.os.SystemClock.uptimeMillis();
        sleepHandler.removeCallbacks(sleepCheck);
        sleepHandler.postDelayed(sleepCheck, 3000);
        // Focus follows the user's normal display timeout; only ringing alarms keep it awake.
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (web != null) web.setKeepScreenOn(false);
        // If an alarm is still ringing (e.g. its notification was swiped away), bring the mission back.
        String ringing = AlarmService.currentInfo;
        if (AlarmService.ringing && ringing != null) {
            Intent i = new Intent(this, AlarmActivity.class);
            i.putExtra("info", ringing);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        FocusEngine.checkBreakDeadline(this);
        org.json.JSONObject focus = FocusEngine.state(this);
        if (focus != null && focus.optBoolean("returnToStudy", false)) pendingStudyReturn = true;
        refreshPage();
    }

    /*
     * Screen sleep on the study page. Some phones never dim while the study timer is showing, so the app follows
     * the phone's own "Screen timeout" itself: after that long without a touch during a focus session, the screen
     * is turned off (like pressing the power button). Waking and unlocking the phone shows the study page again.
     */
    private long lastTouch = android.os.SystemClock.uptimeMillis();
    private final android.os.Handler sleepHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable sleepCheck = new Runnable() {
        @Override public void run() {
            try {
                if (foreground && FocusEngine.isActive(MainActivity.this) && !AlarmService.ringing) {
                    android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
                    long timeout = screenTimeoutMs();
                    long idle = android.os.SystemClock.uptimeMillis() - lastTouch;
                    if (pm != null && pm.isInteractive() && timeout > 0 && idle >= timeout) {
                        lastTouch = android.os.SystemClock.uptimeMillis();
                        FocusBlockService.lockScreen();
                    }
                }
            } catch (RuntimeException ignored) {
            }
            if (foreground) sleepHandler.postDelayed(this, 3000);
        }
    };

    /** The phone's Screen timeout setting; 0 when it is set to never turn off (or longer than 30 minutes). */
    private long screenTimeoutMs() {
        try {
            long t = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 60_000);
            if (t <= 0 || t > 30L * 60_000L) return 0;
            return Math.max(15_000L, t);
        } catch (RuntimeException e) {
            return 60_000L;
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastTouch = android.os.SystemClock.uptimeMillis();
    }

    @Override
    protected void onPause() {
        sleepHandler.removeCallbacks(sleepCheck);
        foreground = false;
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            org.json.JSONObject focus = FocusEngine.state(this);
            if (focus != null && focus.optBoolean("returnToStudy", false)) {
                pendingStudyReturn = true;
                refreshPage();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingBlocked = intent.getStringExtra("blocked");
        pendingStudyReturn = intent.getBooleanExtra("returnToStudy", false);
        refreshPage();
    }

    void onPlannerLoaded() {
        pageReady = true;
        refreshPage();
    }

    void refreshPage() {
        if (web != null && pageReady) {
            if (pendingStudyReturn) {
                ackStudyReturn(0);
                pendingStudyReturn = false;
            }
            web.evaluateJavascript("window.__planner&&window.__planner.refresh&&window.__planner.refresh()", null);
            if (pendingBlocked != null) {
                web.evaluateJavascript("window.__planner&&window.__planner.blocked&&window.__planner.blocked(" + org.json.JSONObject.quote(pendingBlocked) + ")", null);
                pendingBlocked = null;
            }
        }
    }

    /**
     * Shows the study page and clears the pending return once it is really on screen. The page can be a
     * moment behind the activity (WebView still waking up), so retry a few times instead of giving up.
     */
    private void ackStudyReturn(int attempt) {
        if (web == null) return;
        web.evaluateJavascript("window.__planner&&window.__planner.returnToStudy&&window.__planner.returnToStudy()", result -> {
            // An activity resume alone does not mean the timer has been shown.
            if ("true".equals(result) && hasWindowFocus()) {
                FocusEngine.backInApp(this);
                return;
            }
            org.json.JSONObject focus = FocusEngine.state(this);
            if (attempt < 6 && foreground && focus != null && focus.optBoolean("returnToStudy", false) && web != null) {
                web.postDelayed(() -> ackStudyReturn(attempt + 1), 700);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshPage();
        if (web != null) web.evaluateJavascript("window.__onMic&&window.__onMic()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQ && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
        if (requestCode == SOUND_SYSTEM_REQ || requestCode == SOUND_FILE_REQ) {
            if (resultCode != RESULT_OK || data == null) return;
            Uri uri;
            String kind;
            if (requestCode == SOUND_SYSTEM_REQ) {
                uri = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                kind = "system";
            } else {
                uri = data.getData();
                kind = "file";
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (uri == null || web == null) return;
            String name = soundName(uri, kind);
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("kind", kind);
                o.put("uri", uri.toString());
                o.put("name", name);
                web.evaluateJavascript("window.__onSoundPicked&&window.__onSoundPicked(" + org.json.JSONObject.quote(o.toString()) + ")", null);
            } catch (org.json.JSONException ignored) {
            }
        }
    }

    private String soundName(Uri uri, String kind) {
        try {
            if ("system".equals(kind)) {
                android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, uri);
                if (r != null) return r.getTitle(this);
            } else {
                try (android.database.Cursor c = getContentResolver().query(uri,
                        new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        String n = c.getString(0);
                        int dot = n.lastIndexOf('.');
                        return dot > 0 ? n.substring(0, dot) : n;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "file".equals(kind) ? "My sound" : "Phone tone";
    }

    @Override
    protected void onDestroy() {
        Sounds.stopPreview();
        if (bridge != null) bridge.release();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        String js = "(function(){try{return (window.__planner&&window.__planner.back&&window.__planner.back())?'yes':'no';}catch(e){return 'no';}})()";
        web.evaluateJavascript(js, value -> {
            if (value == null || !value.contains("yes")) {
                moveTaskToBack(true);
            }
        });
    }
}
