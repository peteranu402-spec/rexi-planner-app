package com.rexi.planner;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps the alarm schedule the planner sends, so alarms survive restarts. */
final class AlarmStore {
    private AlarmStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("alarms", Context.MODE_PRIVATE);
    }

    static void saveSchedule(Context c, String json) {
        prefs(c).edit().putString("schedule", json == null ? "{}" : json).apply();
    }

    static String schedule(Context c) {
        return prefs(c).getString("schedule", "{}");
    }
}
