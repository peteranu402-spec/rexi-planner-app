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
            }
        }
        p.edit().putStringSet("codes", codes).apply();
        DailyQuote.schedule(c);
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
