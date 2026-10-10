package com.rexi.planner;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/** Morning notification matching the home screen focus message, without quotation marks. */
final class DailyQuote {
    static final String ACTION = "com.rexi.planner.DAILY_FOCUS";
    private static final int RC = 900010;
    private static final int NOTIF_ID = 7101;
    private static final String CHANNEL = "daily_focus";

    private DailyQuote() {}

    private static JSONObject cfg(Context c) {
        try {
            JSONObject s = new JSONObject(AlarmStore.schedule(c));
            return s.optJSONObject("focusQuote");
        } catch (JSONException e) {
            return null;
        }
    }

    private static PendingIntent pending(Context c, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION);
        return PendingIntent.getBroadcast(c, RC, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Schedules the next morning notification (or cancels it when turned off). */
    static void schedule(Context context) {
        Context c = context.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        JSONObject q = cfg(c);
        PendingIntent old = pending(c, PendingIntent.FLAG_NO_CREATE);
        if (old != null) am.cancel(old);
        if (q == null || !q.optBoolean("on", true)) return;

        Calendar when = Calendar.getInstance();
        when.set(Calendar.HOUR_OF_DAY, q.optInt("hour", 7));
        when.set(Calendar.MINUTE, q.optInt("minute", 0));
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        if (when.getTimeInMillis() <= System.currentTimeMillis() + 30_000L) when.add(Calendar.DAY_OF_MONTH, 1);

        PendingIntent pi = pending(c, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pi);
                return;
            }
        } catch (SecurityException ignored) {
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when.getTimeInMillis(), pi);
    }

    /** Posts today's focus beneath a personalised morning greeting. */
    static void show(Context context) {
        Context c = context.getApplicationContext();
        JSONObject q = cfg(c);
        if (q == null || !q.optBoolean("on", true)) return;
        JSONArray list = q.optJSONArray("quotes");
        if (list == null || list.length() == 0) return;
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        // No quotation marks anywhere: "Today's focus: Small steps every day build big results."
        String line = list.optString(day % list.length(), "").replaceAll("[\u201C\u201D\u201E\u00AB\u00BB\"]", "").trim();
        if (line.isEmpty()) return;
        String name = q.optString("name", "").trim();

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Today's focus", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Today's focus message every morning");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 5, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = name.isEmpty() ? "Good morning" : "Good morning, " + name;
        String body = "Today's focus: " + line;
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setColor(0xFFC4572F)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {
            // notifications not allowed
        }
    }
}
