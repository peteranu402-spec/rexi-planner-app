package com.rexi.planner;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Methods the planner's JavaScript can call as window.AndroidAlarm.* */
public class AlarmBridge {
    private static final int NOTIF_REQ = 43;
    private static final int MIC_REQ = 44;

    private final Activity activity;
    private volatile String alarmInfo; // non-null only on the alarm screen
    private WebView web;
    private SpeechRecognizer recognizer;

    AlarmBridge(Activity activity, String alarmInfo) {
        this.activity = activity;
        this.alarmInfo = alarmInfo;
    }

    void attach(WebView web) {
        this.web = web;
    }

    void release() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        web = null;
    }

    private void sendToPage(final String js) {
        activity.runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(js, null);
        });
    }

    /** True when the phone can listen for the affirmation mission (mic allowed + a speech service exists). */
    @JavascriptInterface
    public boolean canListen() {
        return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && SpeechRecognizer.isRecognitionAvailable(activity);
    }

    @JavascriptInterface
    public void requestMic() {
        activity.runOnUiThread(() -> {
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQ);
            }
        });
    }

    private void destroyRecognizer() {
        SpeechRecognizer old = recognizer;
        recognizer = null; // Late callbacks from a cancelled request cannot affect the next mission.
        if (old != null) {
            try { old.cancel(); } catch (Exception ignored) {}
            try { old.destroy(); } catch (Exception ignored) {}
        }
    }

    @JavascriptInterface
    public void cancelListening() {
        activity.runOnUiThread(() -> {
            destroyRecognizer();
            AlarmService.resumeAfterSpeech();
        });
    }

    /** Legacy entry point retained for older web assets. */
    @JavascriptInterface
    public void listen() { listenMission(null); }

    /** Each request reports its token so a late result cannot solve a replacement mission. */
    @JavascriptInterface
    public void listenMission(final String requestId) {
        activity.runOnUiThread(() -> {
            Speaker.get(activity).stop();
            destroyRecognizer();
            final String token = requestId == null ? "null" : JSONObject.quote(requestId);
            if (!canListen()) {
                AlarmService.resumeAfterSpeech();
                sendToPage("window.__onSpeechError&&window.__onSpeechError(-1," + token + ")");
                return;
            }
            try {
                final SpeechRecognizer request = SpeechRecognizer.createSpeechRecognizer(activity);
                recognizer = request;
                request.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rmsdB) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {}
                    @Override public void onPartialResults(Bundle partialResults) {
                        // Keep what was heard so far: if the recogniser stops at a pause, these words still count.
                        ArrayList<String> p = partialResults == null ? null : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (recognizer == request && p != null && !p.isEmpty() && p.get(0) != null && p.get(0).trim().length() > 0) lastPartial = p.get(0);
                    }
                    @Override public void onEvent(int eventType, Bundle params) {}

                    @Override public void onError(int error) {
                        if (recognizer != request) return;
                        String heard = lastPartial;
                        if ((error == 6 || error == 7) && heard != null && heard.trim().length() > 0) {
                            // Timed out or "no match" after hearing some words: check those words instead of failing.
                            destroyRecognizer();
                            AlarmService.resumeAfterSpeech();
                            sendToPage("window.__onSpeech&&window.__onSpeech(" + JSONObject.quote(heard.replace("|", " ")) + "," + token + ")");
                            return;
                        }
                        destroyRecognizer();
                        AlarmService.resumeAfterSpeech();
                        sendToPage("window.__onSpeechError&&window.__onSpeechError(" + error + "," + token + ")");
                    }

                    @Override public void onResults(Bundle results) {
                        if (recognizer != request) return;
                        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        StringBuilder sb = new StringBuilder();
                        if (list != null) for (String r : list) {
                            if (sb.length() > 0) sb.append('|');
                            sb.append(r.replace("|", " "));
                        }
                        // The last partial can be longer than the final text when the recogniser trims it.
                        if (lastPartial != null && lastPartial.trim().length() > 0) {
                            if (sb.length() > 0) sb.append('|');
                            sb.append(lastPartial.replace("|", " "));
                        }
                        destroyRecognizer();
                        AlarmService.resumeAfterSpeech();
                        sendToPage("window.__onSpeech&&window.__onSpeech(" + JSONObject.quote(sb.toString()) + "," + token + ")");
                    }
                });
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
                String lang = speechLanguage();
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
                i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                // Wait longer before deciding you've finished, so a breath between phrases doesn't cut the verse.
                i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
                i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
                lastPartial = null;
                AlarmService.pauseForSpeech();
                request.startListening(i);
            } catch (Exception e) {
                destroyRecognizer();
                AlarmService.resumeAfterSpeech();
                sendToPage("window.__onSpeechError&&window.__onSpeechError(-2," + token + ")");
            }
        });
    }

    private volatile String lastPartial;

    /** English for your region (e.g. en-NG on Nigerian phones) so accents are recognised better; en-US otherwise. */
    private String speechLanguage() {
        java.util.Locale l = java.util.Locale.getDefault();
        String country = l.getCountry() == null ? "" : l.getCountry().toUpperCase(java.util.Locale.ROOT);
        if (!country.isEmpty() && java.util.Arrays.asList("NG", "GH", "KE", "ZA", "GB", "IN", "AU", "CA", "IE", "NZ", "PH", "SG", "TZ", "UG").contains(country)) {
            return "en-" + country;
        }
        return "en-US";
    }

    /** Short vibration feedback for missions: "wrong" = strong double buzz, anything else = light tap. */
    @JavascriptInterface
    public void buzz(String kind) {
        AlarmService.feedbackBuzz(activity, "wrong".equals(kind));
    }

    /* ---------- Focus Mode ---------- */

    @JavascriptInterface
    public void speak(String text) {
        Speaker.get(activity).say(text);
    }

    @JavascriptInterface
    public void speakAlert(String event, String fallback) {
        Speaker.get(activity).play(event, fallback);
    }

    @JavascriptInterface
    public void stopVoice() {
        Speaker.get(activity).stop();
    }

    @JavascriptInterface
    public boolean focusUpdate(String json) {
        return FocusEngine.update(activity, json);
    }

    @JavascriptInterface
    public void focusStop() {
        FocusEngine.stop(activity);
    }

    /** "Lock screen" mode: Android screen pinning keeps the phone on the timer. */
    @JavascriptInterface
    public void lockTask(final boolean on) {
        if (activity instanceof AlarmActivity) return;
        activity.runOnUiThread(() -> {
            try {
                if (on) activity.startLockTask();
                else activity.stopLockTask();
            } catch (Exception ignored) {
            }
        });
    }

    /** Launchable apps the user can choose to block, as [{p: package, l: label}]. */
    @JavascriptInterface
    public String getApps() {
        JSONArray out = new JSONArray();
        try {
            PackageManager pm = activity.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            Set<String> never = FocusEngine.neverBlock(activity);
            ResolveInfo dialer = pm.resolveActivity(new Intent(Intent.ACTION_DIAL), 0);
            if (dialer != null && dialer.activityInfo != null) never.add(dialer.activityInfo.packageName);
            List<String[]> rows = new ArrayList<>();
            Set<String> seen = new java.util.HashSet<>();
            for (ResolveInfo r : list) {
                String pkg = r.activityInfo.packageName;
                if (never.contains(pkg) || !seen.add(pkg)) continue;
                rows.add(new String[]{pkg, String.valueOf(r.loadLabel(pm))});
            }
            Collections.sort(rows, (a, b) -> a[1].compareToIgnoreCase(b[1]));
            for (String[] r : rows) {
                JSONObject o = new JSONObject();
                o.put("p", r[0]);
                o.put("l", r[1]);
                out.put(o);
            }
        } catch (Exception ignored) {
        }
        return out.toString();
    }

    /** Requires a connected, responsive accessibility service as well as its enabled setting. */
    @JavascriptInterface
    public boolean blockerOn() {
        return FocusBlockService.isReady(activity);
    }

    void setAlarmInfo(String info) {
        this.alarmInfo = info;
    }

    /** The alarm being shown on the alarm screen, or "" on the normal planner screen. */
    @JavascriptInterface
    public String alarmInfo() {
        return alarmInfo == null ? "" : alarmInfo;
    }

    @JavascriptInterface
    public void clearLate() {
        AlarmStore.prefs(activity).edit().remove("lateDue").remove("lateMs").apply();
    }

    @JavascriptInterface
    public void setSchedule(String json) {
        AlarmStore.saveSchedule(activity, json);
        AlarmScheduler.scheduleAll(activity);
    }

    @JavascriptInterface
    public String status() {
        JSONObject o = new JSONObject();
        try {
            NotificationManager nm = activity.getSystemService(NotificationManager.class);
            boolean notif = nm == null || nm.areNotificationsEnabled();
            if (Build.VERSION.SDK_INT >= 33) {
                notif = notif && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            o.put("notif", notif);

            boolean exact = true;
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am = activity.getSystemService(AlarmManager.class);
                exact = am == null || am.canScheduleExactAlarms();
            }
            o.put("exact", exact);

            boolean fullScreen = true;
            if (Build.VERSION.SDK_INT >= 34 && nm != null) {
                fullScreen = nm.canUseFullScreenIntent();
            }
            o.put("fullScreen", fullScreen);

            PowerManager pm = activity.getSystemService(PowerManager.class);
            o.put("battery", pm == null || pm.isIgnoringBatteryOptimizations(activity.getPackageName()));

            // Do Not Disturb: true when alarms can still ring
            boolean dndOk = true;
            try {
                if (nm != null) {
                    int f = nm.getCurrentInterruptionFilter();
                    if (f == NotificationManager.INTERRUPTION_FILTER_NONE) dndOk = false;
                    else if (f == NotificationManager.INTERRUPTION_FILTER_PRIORITY && Build.VERSION.SDK_INT >= 28) {
                        NotificationManager.Policy pol = nm.getNotificationPolicy();
                        dndOk = pol == null || (pol.priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0;
                    }
                }
            } catch (Exception ignored) {
            }
            o.put("dnd", dndOk);
            o.put("dndAccess", FocusDnd.hasAccess(activity));
            android.content.SharedPreferences ap = AlarmStore.prefs(activity);
            o.put("lateDue", ap.getLong("lateDue", 0));
            o.put("lateMin", Math.round(ap.getLong("lateMs", 0) / 60000.0));
            o.put("overlay", Settings.canDrawOverlays(activity));

            boolean bgOk = true;
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    android.app.ActivityManager am2 = activity.getSystemService(android.app.ActivityManager.class);
                    bgOk = am2 == null || !am2.isBackgroundRestricted();
                }
            } catch (Exception ignored) {
            }
            o.put("bg", bgOk);
            o.put("maker", String.valueOf(Build.MANUFACTURER).toLowerCase(Locale.ROOT));
            o.put("model", String.valueOf(Build.MODEL));
            o.put("android", Build.VERSION.RELEASE);
        } catch (JSONException ignored) {
        }
        return o.toString();
    }

    /** Guided setup: a short hint (e.g. "Turn on Rexi Rise, then press Back") shown as the settings page opens. */
    @JavascriptInterface
    public void openSettingsHint(final String which, final String hint) {
        activity.runOnUiThread(() -> {
            if (hint != null && !hint.trim().isEmpty()) {
                try {
                    android.widget.Toast.makeText(activity, hint, android.widget.Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {
                }
            }
        });
        openSettings(which);
    }

    @JavascriptInterface
    public void openSettings(final String which) {
        activity.runOnUiThread(() -> {
            String pkg = activity.getPackageName();
            Intent i = null;
            if ("notif".equals(which)) {
                if (Build.VERSION.SDK_INT >= 33
                        && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Show Android's simple "Allow" pop-up the first time (and while Android still allows asking again).
                    android.content.SharedPreferences ui = activity.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE);
                    boolean askedBefore = ui.getBoolean("notifAsked", false);
                    if (!askedBefore || activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        ui.edit().putBoolean("notifAsked", true).apply();
                        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_REQ);
                        return;
                    }
                }
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
            } else if ("fullscreen".equals(which) && Build.VERSION.SDK_INT >= 34) {
                i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + pkg));
            } else if ("exact".equals(which) && Build.VERSION.SDK_INT >= 31) {
                i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkg));
            } else if ("accessibility".equals(which)) {
                // Newer Android can open Rexi Rise's own accessibility page instead of the long list.
                Intent d = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
                d.putExtra(Intent.EXTRA_COMPONENT_NAME, new ComponentName(activity, FocusBlockService.class).flattenToString());
                i = d.resolveActivity(activity.getPackageManager()) != null ? d : new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            } else if ("battery".equals(which)) {
                i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg));
            } else if ("dndaccess".equals(which)) {
                i = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            } else if ("overlay".equals(which)) {
                i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg));
            } else if ("dnd".equals(which)) {
                i = new Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS);
            } else if ("autostart".equals(which)) {
                String[][] comps = {
                        {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                        {"com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"},
                        {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
                        {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                        {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
                        {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                        {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                        {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                        {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
                        {"com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"}};
                for (String[] cmp : comps) {
                    Intent t = new Intent();
                    t.setComponent(new ComponentName(cmp[0], cmp[1]));
                    t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        activity.startActivity(t);
                        return;
                    } catch (Exception ignored) {
                    }
                }
            }
            if (i == null) i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            try {
                activity.startActivity(i);
            } catch (Exception e) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                } catch (Exception ignored) {
                }
            }
        });
    }

    @JavascriptInterface
    public void testIn(int seconds, String infoJson) {
        long when = System.currentTimeMillis() + Math.max(1, seconds) * 1000L;
        AlarmScheduler.scheduleOneOff(activity, infoJson, when, AlarmScheduler.TEST_CODE);
    }

    /** Network request for sign-in and backup. Result goes to window.__onHttp(id, status, body). */
    @JavascriptInterface
    public void http(final String id, final String method, final String url, final String headersJson, final String body) {
        new Thread(() -> {
            int status = 0;
            String resp = "";
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(25000);
                c.setUseCaches(false);
                try {
                    c.setRequestMethod(method);
                } catch (java.net.ProtocolException pe) {
                    // e.g. PATCH on older phones: Google APIs accept POST with an override header.
                    c.setRequestMethod("POST");
                    c.setRequestProperty("X-HTTP-Method-Override", method);
                }
                JSONObject h = new JSONObject(headersJson == null || headersJson.isEmpty() ? "{}" : headersJson);
                java.util.Iterator<String> keys = h.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    c.setRequestProperty(k, h.optString(k));
                }
                if (body != null && !body.isEmpty()) {
                    c.setDoOutput(true);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    c.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream os = c.getOutputStream()) {
                        os.write(bytes);
                    }
                }
                status = c.getResponseCode();
                java.io.InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    in.close();
                    resp = bo.toString("UTF-8");
                }
            } catch (Exception e) {
                status = 0;
                resp = "";
            } finally {
                if (c != null) c.disconnect();
            }
            final String js = "window.__onHttp&&window.__onHttp(" + JSONObject.quote(id) + "," + status + "," + JSONObject.quote(resp) + ")";
            activity.runOnUiThread(() -> {
                if (web != null) web.evaluateJavascript(js, null);
            });
        }).start();
    }

    /** Google sign-in with the phone's Google account. Result: window.__onGoogle(idToken) or __onGoogleError(code). */
    @JavascriptInterface
    public void googleSignIn(final String webClientId) {
        activity.runOnUiThread(() -> GoogleSignIn.start(activity, webClientId, new GoogleSignIn.Result() {
            @Override
            public void ok(String idToken) {
                js("window.__onGoogle&&window.__onGoogle(" + JSONObject.quote(idToken) + ")");
            }

            @Override
            public void fail(String code) {
                js("window.__onGoogleError&&window.__onGoogleError(" + JSONObject.quote(code) + ")");
            }
        }));
    }

    private void js(final String code) {
        activity.runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    /** Silences the ringing alarm for N seconds while the mission is done; it rings again if the user stops or leaves. */
    @JavascriptInterface
    public void quiet(int seconds) {
        AlarmService.quiet(seconds);
    }

    @JavascriptInterface
    public void resumeSound() {
        AlarmService.resumeNow();
    }

    @JavascriptInterface
    public void missionDone() {
        AlarmService.missionDone();
    }

    @JavascriptInterface
    public String version() {
        try {
            android.content.pm.PackageInfo pi = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    /** Rings a one-off alarm in N minutes (used for the Wake-up Check). */
    @JavascriptInterface
    public void later(int minutes, String infoJson) {
        long when = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        AlarmScheduler.scheduleOneOff(activity, infoJson, when, AlarmScheduler.CHECK_CODE);
    }

    /** Opens the phone's tone list ("system") or a file picker for the user's own audio ("file"). */
    @JavascriptInterface
    public void pickSound(final String kind) {
        if (!(activity instanceof MainActivity)) return;
        activity.runOnUiThread(() -> {
            try {
                if ("system".equals(kind)) {
                    Intent i = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER);
                    i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                            android.media.RingtoneManager.TYPE_ALARM | android.media.RingtoneManager.TYPE_RINGTONE);
                    i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound");
                    i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                    i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                    activity.startActivityForResult(i, MainActivity.SOUND_SYSTEM_REQ);
                } else {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("audio/*");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    activity.startActivityForResult(i, MainActivity.SOUND_FILE_REQ);
                }
            } catch (Exception ignored) {
            }
        });
    }

    @JavascriptInterface
    public void previewSound(String json) {
        Sounds.preview(activity, json);
    }

    @JavascriptInterface
    public void stopPreview() {
        Sounds.stopPreview();
    }

    /** Mission solved (or Stop tapped): silence the alarm and close the alarm screen. */
    @JavascriptInterface
    public void stop() {
        activity.stopService(new Intent(activity, AlarmService.class));
        try {
            NotificationManager nm = activity.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(AlarmService.NOTIF_ID);
        } catch (Exception ignored) {
        }
        if (activity instanceof AlarmActivity) activity.runOnUiThread(activity::finish);
    }

    @JavascriptInterface
    public void snooze(int minutes, int snoozesLeft) {
        try {
            JSONObject o = new JSONObject(alarmInfo == null || alarmInfo.isEmpty() ? "{}" : alarmInfo);
            o.put("snoozeLeft", Math.max(0, snoozesLeft));
            String title = o.optString("title", "Alarm");
            if (!title.endsWith(" (snoozed)")) o.put("title", title + " (snoozed)");
            long when = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
            AlarmScheduler.scheduleOneOff(activity, o.toString(), when, AlarmScheduler.SNOOZE_CODE);
        } catch (JSONException ignored) {
        }
        stop();
    }

    /** Matches the status/navigation bar colours to the planner's light or dark theme. */
    @JavascriptInterface
    public void setBars(final String color, final boolean lightIcons) {
        if (activity instanceof AlarmActivity) return;
        activity.runOnUiThread(() -> {
            try {
                Window w = activity.getWindow();
                int c = Color.parseColor(color);
                w.setStatusBarColor(c);
                w.setNavigationBarColor(c);
                View d = w.getDecorView();
                int flags = d.getSystemUiVisibility();
                if (lightIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 27) {
                    if (lightIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                d.setSystemUiVisibility(flags);
                // Remember the app's own theme so the next launch opens on the right colour (no light/dark flash).
                activity.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit()
                        .putString("bg", color).putBoolean("lightIcons", lightIcons).apply();
            } catch (Exception ignored) {
            }
        });
    }

    /** Saves a backup or calendar file into the phone's Downloads folder. */
    @JavascriptInterface
    public boolean saveFile(String name, String mime, String text) {
        try {
            byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver r = activity.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                try (OutputStream out = r.openOutputStream(uri)) {
                    if (out == null) return false;
                    out.write(bytes);
                }
                return true;
            }
            final Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, name);
            send.putExtra(Intent.EXTRA_TEXT, text);
            activity.runOnUiThread(() -> {
                try {
                    activity.startActivity(Intent.createChooser(send, "Save " + name));
                } catch (Exception ignored) {
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
