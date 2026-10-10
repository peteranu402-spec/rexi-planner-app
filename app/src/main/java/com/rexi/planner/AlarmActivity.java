package com.rexi.planner;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import java.lang.ref.WeakReference;

/** Full-screen alarm shown over the lock screen. The planner page runs in "alarm mode" with the mission. */
public class AlarmActivity extends Activity {
    private static WeakReference<AlarmActivity> current;

    private WebView web;
    private AlarmBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        current = new WeakReference<>(this);

        String info = getIntent().getStringExtra("info");
        if (info == null) info = AlarmService.currentInfo;
        if (info == null) info = "{}";
        bridge = new AlarmBridge(this, info);
        web = WebViewFactory.create(this, bridge);
        bridge.attach(web);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(android.webkit.WebView view, String url, String message, android.webkit.JsResult result) {
                return JsDialogs.show(AlarmActivity.this, message, result, false);
            }

            @Override
            public boolean onJsConfirm(android.webkit.WebView view, String url, String message, android.webkit.JsResult result) {
                return JsDialogs.show(AlarmActivity.this, message, result, true);
            }
        });
        setContentView(web);
        web.loadUrl(WebViewFactory.START_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("resume", false)) return; // ringing again after a quiet window: keep the mission as it was
        String info = intent.getStringExtra("info");
        if (info != null && bridge != null && web != null) {
            bridge.setAlarmInfo(info);
            web.loadUrl(WebViewFactory.START_URL);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (web != null) web.evaluateJavascript("window.__onMic&&window.__onMic()", null);
    }

    @Override
    public void onBackPressed() {
        // Back does nothing: complete the mission to stop the alarm.
    }

    @Override
    protected void onDestroy() {
        if (current != null && current.get() == this) current = null;
        if (bridge != null) bridge.release();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    static void finishIfOpen() {
        AlarmActivity a = current != null ? current.get() : null;
        if (a != null) a.runOnUiThread(a::finish);
    }
}
