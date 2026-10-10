package com.rexi.planner;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Mirrors the planner's Focus Mode state natively so breaks, the goal and the app blocker
 * keep working even when the app is in the background or the screen is off.
 */
final class FocusEngine {
    static final String ACTION_EVENT = "com.rexi.planner.FOCUS_EVENT";
    static final String CH_ALERT = "focus_alert_v1";
    static final String CH_ONGOING = "focus_ongoing_v1";
    static final int ONGOING_ID = 8001;
    static final int ALERT_ID = 8002;
    private static final int RC_SUGGEST = 910001, RC_BREAK_END = 910002, RC_GOAL = 910003;

    private static JSONObject cached;

    private FocusEngine() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("focus", Context.MODE_PRIVATE);
    }

    static synchronized JSONObject state(Context c) {
        if (cached != null) return cached;
        String s = prefs(c).getString("state", null);
        if (s == null) return null;
        try {
            cached = new JSONObject(s);
        } catch (JSONException e) {
            cached = null;
        }
        return cached;
    }

    private static synchronized void saveState(Context c, JSONObject o) {
        cached = o;
        if (o == null) prefs(c).edit().remove("state").apply();
        else prefs(c).edit().putString("state", o.toString()).apply();
    }

    /** Called from JavaScript whenever the session changes. */
    static synchronized boolean update(Context c, String json) {
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            return false;
        }
        JSONObject previous = state(c);
        boolean starting = previous == null || !o.optString("sessionId").equals(previous.optString("sessionId"));
        boolean resuming = previous != null && "paused".equals(previous.optString("state"))
                && "study".equals(o.optString("state"));
        if ((starting || resuming) && !FocusBlockService.isReady(c)) return false;
        // WebView timer updates must not erase a native break-expiry return request.
        boolean sameSession = previous != null && !starting;
        boolean oldGoal = sameSession && previous.optBoolean("goalHit", false);
        long oldBreakVoice = sameSession ? previous.optLong("spokenBreakEndAt", 0) : 0;
        long oldSuggestion = sameSession ? previous.optLong("suggestedAtMs", 0) : 0;
        long requestedSuggestion = o.optLong("suggestedAtMs", 0);
        long previousBreakEnd = sameSession ? previous.optLong("breakEndAt", 0) : 0;
        boolean goalVoice = FocusVoicePolicy.goal(sameSession, oldGoal, o.optBoolean("goalHit", false));
        boolean breakVoice = FocusVoicePolicy.breakEnd(sameSession,
                sameSession ? previous.optString("state") : "", o.optString("state"),
                previousBreakEnd, System.currentTimeMillis(), oldBreakVoice);
        boolean suggestVoice = sameSession && FocusVoicePolicy.suggestion(requestedSuggestion, oldSuggestion);
        boolean returning = FocusReturnPolicy.shouldReturn(sameSession,
                previous == null ? "" : previous.optString("state"),
                previous != null && previous.optBoolean("returnToStudy", false), o.optString("state"));
        // The planner itself sent this while on screen (e.g. "Back to studying"): the study page is already showing.
        if (returning && MainActivity.foreground) returning = false;
        try {
            o.put("returnToStudy", returning);
            o.put("goalHit", oldGoal || o.optBoolean("goalHit", false));
            o.put("spokenBreakEndAt", breakVoice ? previousBreakEnd : oldBreakVoice);
            o.put("suggestedAtMs", Math.max(requestedSuggestion, oldSuggestion));
            o.put("noBreaksNotified", sameSession && previous.optBoolean("noBreaksNotified", false));
        } catch (JSONException e) { return false; }
        // Came back early from the last break: the break pool is now empty.
        boolean noMoreBreaks = sameSession && "break".equals(previous.optString("state"))
                && "study".equals(o.optString("state")) && o.optLong("breakLeftMs", 60_000L) < 60_000L
                && !o.optBoolean("noBreaksNotified", false);
        if (noMoreBreaks) { try { o.put("noBreaksNotified", true); } catch (JSONException ignored) { } }
        saveState(c, o);
        // A delayed WebView can send the old break after the native alarm already fired.
        checkBreakDeadline(c);
        schedule(c, state(c));
        FocusService.refresh(c);
        if (o.optBoolean("voice", true)) {
            if (breakVoice) Speaker.get(c).play("break_end", "Your break is over. Let's get back to studying.");
            if (goalVoice) Speaker.get(c).play("goal_reached", "You've reached your study goal. Well done! You can finish here or keep going.");
            if (suggestVoice) Speaker.get(c).play("break_suggested", "You've been focusing for a while. This could be a good moment to take a short break.");
        }
        if (returning) returnToStudy(c);
        if (noMoreBreaks) noBreaksLeft(c, o.optString("subject", "your subject"));
        FocusDnd.apply(c, state(c));
        return true;
    }

    static void stop(Context c) {
        saveState(c, null);
        FocusDnd.apply(c, null);
        cancel(c, RC_SUGGEST);
        cancel(c, RC_BREAK_END);
        cancel(c, RC_GOAL);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ALERT_ID);
        c.stopService(new Intent(c, FocusService.class));
    }

    static boolean isActive(Context c) {
        return state(c) != null;
    }

    /** True when the blocker should bounce the user out of this app right now. */
    static boolean shouldBlock(Context c, String pkg) {
        JSONObject o = state(c);
        if (o == null || pkg == null) return false;
        if (!"study".equals(o.optString("state"))) return false;
        // Safety net: never keep a phone locked by a stale session (e.g. the app was killed mid-session).
        long updated = o.optLong("updatedAt", 0);
        if (updated > 0 && System.currentTimeMillis() - updated > 8L * 3600_000L) return false;
        String lock = o.optString("lock");
        if ("pin".equals(lock)) {
            // Strict lock: everything is blocked except Rexi Rise, calls, the keyboard, system dialogs and allowed apps.
            if (strictAlwaysAllowed(c).contains(pkg)) return false;
            if (pkg.equals(currentKeyboard(c))) return false;
            JSONArray allowed = o.optJSONArray("allowed");
            if (allowed != null) {
                for (int i = 0; i < allowed.length(); i++) {
                    if (pkg.equals(allowed.optString(i))) return false;
                }
            }
            return true;
        }
        if (!"block".equals(lock)) return false;
        if (neverBlock(c).contains(pkg)) return false;
        JSONArray arr = o.optJSONArray("blocked");
        if (arr == null) return false;
        for (int i = 0; i < arr.length(); i++) {
            if (pkg.equals(arr.optString(i))) return true;
        }
        return false;
    }

    static boolean isStrict(Context c) {
        JSONObject o = state(c);
        return o != null && "pin".equals(o.optString("lock")) && "study".equals(o.optString("state"));
    }

    private static Set<String> strictCache;

    /** Package of the keyboard the user has selected right now (read fresh each time). */
    static String currentKeyboard(Context c) {
        try {
            String id = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
            if (id == null) return "";
            android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(id);
            return cn == null ? "" : cn.getPackageName();
        } catch (Exception e) {
            return "";
        }
    }

    /** Packages that must keep working during a strict lock. */
    static Set<String> strictAlwaysAllowed(Context c) {
        if (strictCache != null) return strictCache;
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("android");
        s.add("com.android.systemui");
        // Calls and emergency
        String[] phone = {"com.android.phone", "com.android.server.telecom", "com.android.incallui", "com.android.dialer",
                "com.google.android.dialer", "com.samsung.android.incallui", "com.samsung.android.dialer", "com.android.emergency",
                "com.google.android.apps.safetyhub", "com.android.contacts", "com.miui.securitycenter.remote"};
        for (String p : phone) s.add(p);
        try {
            android.telecom.TelecomManager tm = c.getSystemService(android.telecom.TelecomManager.class);
            if (tm != null && tm.getDefaultDialerPackage() != null) s.add(tm.getDefaultDialerPackage());
        } catch (Exception ignored) {
        }
        // Permission pop-ups
        s.add("com.android.permissioncontroller");
        s.add("com.google.android.permissioncontroller");
        // Older Android permission pop-ups, file pickers and app choosers
        s.add("com.android.packageinstaller");
        s.add("com.google.android.packageinstaller");
        s.add("com.android.documentsui");
        s.add("com.google.android.documentsui");
        s.add("com.android.intentresolver");
        // Common keyboards (the one in use is also checked live in currentKeyboard)
        String[] kb = {"com.google.android.inputmethod.latin", "com.samsung.android.honeyboard", "com.touchtype.swiftkey",
                "com.sohu.inputmethod.sogou.xiaomi", "com.baidu.input_mi", "com.transsion.inputmethod"};
        for (String p : kb) s.add(p);
        strictCache = s;
        return s;
    }

    private static void schedule(Context c, JSONObject o) {
        cancel(c, RC_SUGGEST);
        cancel(c, RC_BREAK_END);
        cancel(c, RC_GOAL);
        long updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        long studied = o.optLong("studiedMs", 0);
        long elapsed = o.optLong("sessionMs", studied);
        long goalMs = o.optLong("goalMin", 0) * 60_000L;
        if (!"paused".equals(o.optString("state")) && !o.optBoolean("goalHit", false)) {
            set(c, RC_GOAL, "goal", updatedAt + Math.max(0, goalMs - elapsed));
        }
        if ("break".equals(o.optString("state"))) {
            long end = o.optLong("breakEndAt", 0);
            if (end > 0) set(c, RC_BREAK_END, "breakEnd", end);
            return;
        }
        if ("paused".equals(o.optString("state"))) return; // emergency pause: no timers, no lock
        long suggest = o.optLong("nextSuggestMs", 0);
        if (o.optInt("breaksRemaining", 1) > 0 && suggest > studied) set(c, RC_SUGGEST, "suggest", updatedAt + (suggest - studied));
    }

    /** An alarm for a break / goal moment fired. */
    static synchronized void onEvent(Context c, String type) {
        JSONObject o = state(c);
        if (o == null) return;
        boolean voice = o.optBoolean("voice", true);
        String subject = o.optString("subject", "your subject");
        try {
            if ("breakEnd".equals(type) && "break".equals(o.optString("state"))) {
                long end = o.optLong("breakEndAt", System.currentTimeMillis());
                boolean speakBreak = end > o.optLong("spokenBreakEndAt", 0);
                o.put("spokenBreakEndAt", Math.max(end, o.optLong("spokenBreakEndAt", 0)));
                long studied = o.optLong("studiedMs", 0);
                int studyMin = o.optInt("studyMin", 0);
                o.put("sessionMs", o.optLong("sessionMs", studied) + Math.max(0, end - o.optLong("updatedAt", end)));
                o.put("returnToStudy", true);
                o.put("state", "study");
                o.put("updatedAt", end);
                o.put("breakEndAt", 0);
                o.put("nextSuggestMs", studyMin > 0 && o.optInt("breaksRemaining", 1) > 0 ? studied + studyMin * 60_000L : 0);
                saveState(c, o);
                schedule(c, o);
                FocusService.refresh(c);
                o.put("nudged", false);
                boolean lastBreak = o.optLong("breakLeftAfterMs", 60_000L) < 60_000L;
                if (lastBreak) {
                    o.put("noBreaksNotified", true);
                    o.put("breakLeftMs", 0);
                    o.put("breaksRemaining", 0);
                    o.put("nextSuggestMs", 0);
                }
                saveState(c, o);
                FocusDnd.apply(c, o);
                if (voice && speakBreak) Speaker.get(c).play("break_end", "Your break is over. Let's get back to studying.");
                // Resume the lock immediately; the notification is also an entry point.
                returnToStudy(c);
                alert(c, "Break's over", lastBreak
                        ? "Back to " + subject + ". That was your last break — no more breaks in this session."
                        : "Back to " + subject + " — your focus lock has resumed.", true, deleteNudge(c));
            } else if ("breakNudge".equals(type) && "study".equals(o.optString("state")) && !o.optBoolean("nudged", false)) {
                // Second (and last) nudge after the first was swiped away: stays put and brings today's focus.
                o.put("nudged", true);
                saveState(c, o);
                String quote = o.optString("quote", "").trim(), note = o.optString("quoteNote", "").trim();
                if (voice) {
                    StringBuilder s = new StringBuilder("Okay" + nm(o) + ", we're heading back now.");
                    if (!quote.isEmpty()) s.append(" Here's today's focus. ").append(quote);
                    if (!note.isEmpty()) s.append(" ").append(note);
                    s.append(" Back to ").append(subject).append(".");
                    Speaker.get(c).say(s.toString());
                }
                returnToStudy(c);
                comeBack(c, subject, quote);
            } else if ("goal".equals(type) && !"paused".equals(o.optString("state")) && !o.optBoolean("goalHit", false)) {
                o.put("goalHit", true);
                saveState(c, o);
                FocusService.refresh(c);
                if (voice) Speaker.get(c).play("goal_reached", "You've reached your study goal. Well done! You can finish here or keep going.");
                alert(c, "Goal reached 🎉", "You studied " + goalWords(o.optInt("goalMin", 0)) + " of " + subject + ".", false);
            } else if ("suggest".equals(type) && "study".equals(o.optString("state"))) {
                long threshold = o.optLong("nextSuggestMs", 0);
                if (!FocusVoicePolicy.suggestion(threshold, o.optLong("suggestedAtMs", 0))) return;
                o.put("suggestedAtMs", threshold);
                saveState(c, o);
                int breakMin = o.optInt("breakMin", 10);
                if (voice) Speaker.get(c).play("break_suggested", "You've been focusing for a while. This could be a good moment to take a short break.");
                alert(c, "Time for a breather?", "You can take a " + breakMin + "-minute break.", false);
            }
        } catch (JSONException ignored) {
        }
    }

    /** ", Temitope" when we know the user's first name. */
    private static String nm(JSONObject o) {
        String n = o.optString("name", "").trim();
        return n.isEmpty() ? "" : ", " + n;
    }

    static String goalWords(int min) {
        int h = min / 60, m = min % 60;
        if (h > 0 && m > 0) return h + " hour" + (h > 1 ? "s" : "") + " " + m + " minute";
        if (h > 0) return h + " hour";
        return m + " minute";
    }

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ALERT, "Study alerts", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Breaks and study goals");
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CH_ONGOING) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ONGOING, "Study timer", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shown while a study session is running");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    static PendingIntent openApp(Context c, int rc) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("returnToStudy", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, rc, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void alert(Context c, String title, String text, boolean fullScreen) {
        alert(c, title, text, fullScreen, null);
    }

    /** Fires the second nudge when the first "Break's over" alert is swiped away. */
    private static PendingIntent deleteNudge(Context c) {
        return PendingIntent.getBroadcast(c, 910004, eventIntent(c, "breakNudge"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The second nudge: can't be swiped away, opens the app full screen and shows today's focus. */
    private static void comeBack(Context c, String subject, String quote) {
        ensureChannels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        PendingIntent pi = openApp(c, 13);
        String text = quote.isEmpty() ? "Back to " + subject + "." : "Today's focus: \u201C" + quote + "\u201D";
        Notification.Builder b = new Notification.Builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("We're heading back to " + subject)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFFC4572F)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(pi);
        if (canShowStudy(c)) b.setFullScreenIntent(pi, true);
        try {
            nm.notify(ALERT_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    /** Do not wake the display or repeatedly reopen the already visible study activity. */
    static boolean canShowStudy(Context c) {
        android.os.PowerManager power = c.getSystemService(android.os.PowerManager.class);
        android.app.KeyguardManager keyguard = c.getSystemService(android.app.KeyguardManager.class);
        return FocusReturnPolicy.shouldLaunch(power != null && power.isInteractive(),
                keyguard != null && keyguard.isKeyguardLocked(), MainActivity.foreground);
    }

    /** Accessibility also calls this, so allowed apps cannot bypass an expired break. */
    static void returnToStudy(Context c) {
        if (!canShowStudy(c)) return;
        // Let calls finish before bringing the study page back.
        android.media.AudioManager audio = c.getSystemService(android.media.AudioManager.class);
        if (audio != null && audio.getMode() != android.media.AudioManager.MODE_NORMAL) return;
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("returnToStudy", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    static void checkBreakDeadline(Context c) {
        JSONObject o = state(c);
        if (o != null && "break".equals(o.optString("state"))
                && System.currentTimeMillis() >= o.optLong("breakEndAt", Long.MAX_VALUE)) onEvent(c, "breakEnd");
    }

    /** Clears the "heading back" notification once the user is back in the app. */
    static synchronized void backInApp(Context c) {
        JSONObject o = state(c);
        if (o == null || !"study".equals(o.optString("state"))) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ALERT_ID);
        try { o.put("returnToStudy", false); saveState(c, o); } catch (JSONException ignored) { }
    }

    private static void alert(Context c, String title, String text, boolean fullScreen, PendingIntent onSwipe) {
        ensureChannels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        PendingIntent pi = openApp(c, 11);
        Notification.Builder b = new Notification.Builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(0xFFC4572F)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (fullScreen && canShowStudy(c)) b.setFullScreenIntent(pi, true);
        if (onSwipe != null) b.setDeleteIntent(onSwipe);
        try {
            nm.notify(ALERT_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    static final int NO_BREAKS_ID = 8003;

    /** "No more breaks" — the session's break time is used up. */
    private static void noBreaksLeft(Context c, String subject) {
        ensureChannels(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        String text = "You've used all your break time. Stay with " + subject + " until your goal.";
        Notification.Builder b = new Notification.Builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle("No more breaks")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFFC4572F)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp(c, 14));
        try {
            nm.notify(NO_BREAKS_ID, b.build());
        } catch (Exception ignored) {
        }
    }

    /** The ongoing "Studying …" notification with a live countdown. */
    static Notification ongoing(Context c) {
        ensureChannels(c);
        JSONObject o = state(c);
        Notification.Builder b = new Notification.Builder(c, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setColor(0xFFC4572F)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(c, 12));
        if (o == null) return b.setContentTitle("Study session").build();
        String subject = o.optString("subject", "Study");
        long updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        if ("paused".equals(o.optString("state"))) {
            b.setContentTitle("⏸ Paused — " + subject).setContentText("Emergency pause. Tap to resume when you're ready.");
        } else if ("break".equals(o.optString("state"))) {
            b.setContentTitle("☕ Break — " + subject).setContentText("Back to studying when the timer ends");
            long end = o.optLong("breakEndAt", 0);
            if (end > 0) b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(end);
        } else {
            long goalMs = o.optLong("goalMin", 0) * 60_000L, studied = o.optLong("sessionMs", o.optLong("studiedMs", 0));
            b.setContentTitle("📖 Studying " + subject);
            if (goalMs > studied) {
                b.setContentText("Time left until your goal").setUsesChronometer(true).setChronometerCountDown(true).setWhen(updatedAt + (goalMs - studied));
            } else {
                b.setContentText("Goal reached — bonus time").setUsesChronometer(true).setWhen(updatedAt - (studied - goalMs));
            }
        }
        return b.build();
    }

    private static Intent eventIntent(Context c, String type) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_EVENT);
        if (type != null) i.putExtra("type", type);
        return i;
    }

    private static void set(Context c, int rc, String type, long when) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, rc, eventIntent(c, type),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                return;
            }
        } catch (SecurityException ignored) {
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    private static void cancel(Context c, int rc) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, rc, eventIntent(c, null),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null && am != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    static Set<String> neverBlock(Context c) {
        Set<String> s = new HashSet<>();
        s.add(c.getPackageName());
        s.add("com.android.settings");
        s.add("com.android.systemui");
        return s;
    }
}
