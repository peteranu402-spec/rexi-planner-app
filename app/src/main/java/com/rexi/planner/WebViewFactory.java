package com.rexi.planner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Builds the WebView that shows the planner (shared by the main screen and the alarm screen). */
final class WebViewFactory {
    static final String START_URL = "file:///android_asset/www/index.html";

    private WebViewFactory() {}

    static WebView create(final Activity activity, AlarmBridge bridge) {
        // On the planner screen the display must follow the phone's own screen timeout, even during a study
        // countdown. WebView turns "keep screen on" on by itself for some media / wake-lock requests; ignore that.
        // The alarm screen keeps its own window flag, so ringing alarms still stay awake.
        WebView web = activity instanceof MainActivity ? new WebView(activity) {
            @Override
            public void setKeepScreenOn(boolean keepScreenOn) {
                super.setKeepScreenOn(false);
            }
        } : new WebView(activity);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // Roboto runs a little small next to most phone fonts, so show all text 6% larger (normal size, not too big).
        s.setTextZoom(106);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.addJavascriptInterface(bridge, "AndroidAlarm");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (activity instanceof MainActivity) ((MainActivity) activity).onPlannerLoaded();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) return false;
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        return web;
    }
}
