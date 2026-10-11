package com.rexi.planner;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/** Turns the planner's weekly alarms into real Android alarms. */
final class AlarmScheduler {
    static final String ACTION_FIRE = "com.rexi.planner.FIRE";
    static final int TEST_CODE = 900001;
    static final int SNOOZE_CODE = 900002;
    static final int CHECK_CODE = 900003;

    private AlarmScheduler() {}

    static synchronized void scheduleAll(Context context) {
        Context c = context.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        SharedPreferences p = AlarmStore.prefs(c);

        // Cancel everything scheduled last time.
        Set<String> old = p.getStringSet("codes", new HashSet<>());
        for (String code : old) {
            try {
                PendingIntent pi = PendingIntent.getBroadcast(c, Integer.parseInt(code), fireIntent(c, null),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Set<String> codes = new HashSet<>();
        JSONArray pending = new JSONArray();
        JSONObject schedule;
        try {
            schedule = new JSONObject(AlarmStore.schedule(c));
        } catch (JSONException e) {
            schedule = new JSONObject();
        }
        JSONArray alarms = schedule.optJSONArray("alarms");
        int snoozeMax = schedule.optInt("snoozeMax", 2);
        if (alarms != null) {
            for (int i = 0; i < alarms.length(); i++) {
                JSONObject a = alarms.optJSONObject(i);
                if (a == null) continue;
                long when;
                if (a.has("at")) {
                    // One-time alarm: ring once at an exact moment, skip if already past.
                    when = a.optLong("at", 0);
                    if (when <= System.currentTimeMillis()) continue;
                } else {
                    when = nextTrigger(a.optInt("day", 1), a.optInt("hour", 0), a.optInt("minute", 0));
                }
                int code = requestCode(a.optString("id", "alarm" + i));
                JSONObject info;
                try {
                    info = new JSONObject(a.toString());
                    info.put("stop", "stop".equals(a.optString("kind")));
                    info.put("snoozeLeft", a.optInt("snoozeMax", snoozeMax));
                } catch (JSONException e) {
                    info = new JSONObject();
                }
                setExact(c, am, code, info.toString(), when);
                codes.add(String.valueOf(code));
                try {
                    if (!"study".equals(a.optString("kind"))) {
                        JSONObject rec = new JSONObject(info.toString());
                        rec.put("due", when);
                        pending.put(rec);
                    }
                } catch (JSONException ignored) {
                }
            }
        }
        p.edit().putStringSet("codes", codes).putString("pending", pending.toString()).apply();
        DailyQuote.schedule(c);
    }

    /**
     * After the phone was off: alarms whose time passed while it was off. One missed in the last 15 minutes rings now;
     * older ones (up to 36 hours) are reported in a "missed alarm" notification. Call before scheduleAll().
     */
    static void handleMissedWhileOff(Context context) {
        Context c = context.getApplicationContext();
        SharedPreferences p = AlarmStore.prefs(c);
        JSONArray pending;
        try {
            pending = new JSONArray(p.getString("pending", "[]"));
        } catch (JSONException e) {
            return;
        }
        long now = System.currentTimeMillis();
        JSONObject ringNow = null;
        java.util.List<String> missed = new java.util.ArrayList<>();
        for (int i = 0; i < pending.length(); i++) {
            JSONObject a = pending.optJSONObject(i);
            if (a == null) continue;
            long due = a.optLong("due", 0);
            if (due <= 0 || due > now - 30_000L || due < now - 36L * 3600_000L) continue;
            if (now - due <= 15L * 60_000L) {
                if (ringNow == null || due > ringNow.optLong("due", 0)) ringNow = a;
            } else {
                java.text.DateFormat f = android.text.format.DateFormat.getTimeFormat(c);
                String name = a.optString("title", a.optString("name", "")).trim();
                missed.add(f.format(new java.util.Date(due)) + (name.isEmpty() || "Alarm".equals(name) ? "" : " (" + name + ")"));
            }
        }
        p.edit().putString("pending", "[]").apply();
        if (ringNow != null) {
            Intent ring = new Intent(c, AlarmService.class);
            ring.setAction(AlarmService.ACTION_RING);
            ring.putExtra("info", ringNow.toString());
            try {
                c.startForegroundService(ring);
            } catch (Exception e) {
                AlarmService.postFallback(c, ringNow.toString());
            }
        }
        if (missed.isEmpty()) return;
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        if (nm == null) return;
        AlarmService.ensureChannels(c);
        String text = missed.size() == 1
                ? "Your " + missed.get(0) + " alarm didn't ring because your phone was switched off."
                : "These alarms didn't ring because your phone was switched off: " + android.text.TextUtils.join(", ", missed) + ".";
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 7301, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification n = new android.app.Notification.Builder(c, AlarmService.CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(missed.size() == 1 ? "Missed alarm" : "Missed alarms")
                .setContentText(text)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(text))
                .setColor(0xFFC4572F)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        try {
            nm.notify(7301, n);
        } catch (Exception ignored) {
        }
    }

    static void scheduleOneOff(Context context, String info, long when, int code) {
        Context c = context.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        setExact(c, am, code, info, when);
    }

    /** day: 1 = Monday ... 7 = Sunday. Returns the next time this weekly alarm should ring. */
    static long nextTrigger(int day, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar c = (Calendar) now.clone();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int target = day == 7 ? Calendar.SUNDAY : day + 1; // Calendar.MONDAY == 2
        int diff = (target - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        c.add(Calendar.DAY_OF_MONTH, diff);
        if (c.getTimeInMillis() <= now.getTimeInMillis()) c.add(Calendar.DAY_OF_MONTH, 7);
        return c.getTimeInMillis();
    }

    private static int requestCode(String id) {
        return 1000 + (id.hashCode() & 0x7fffffff) % 800000;
    }

    private static Intent fireIntent(Context c, String info) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_FIRE);
        if (info != null) i.putExtra("info", info);
        return i;
    }

    private static void setExact(Context c, AlarmManager am, int code, String info, long when) {
        // Remember when it is due, so a late ring (phone holding the app back) can be detected and explained.
        try {
            JSONObject j = new JSONObject(info == null ? "{}" : info);
            j.put("due", when);
            info = j.toString();
        } catch (JSONException ignored) {
        }
        PendingIntent op = PendingIntent.getBroadcast(c, code, fireIntent(c, info),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        try {
            if (exactAllowed) {
                Intent show = new Intent(c, MainActivity.class);
                PendingIntent showPi = PendingIntent.getActivity(c, 0, show,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showPi), op);
                return;
            }
        } catch (SecurityException ignored) {
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, op);
    }
}
