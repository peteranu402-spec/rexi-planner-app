package com.rexi.planner;

import android.content.Context;
import android.content.SharedPreferences;

/** Keeps the alarm schedule the planner sends, so alarms survive restarts. */
final class AlarmStore {
    private AlarmStore() {}

    private static volatile boolean moved;

    /**
     * Alarms live in device-protected storage, which Android can read straight after a restart, before the phone is
     * unlocked. That lets alarms ring even if the phone restarted overnight and nobody has unlocked it yet.
     */
    static SharedPreferences prefs(Context c) {
        Context app = c.getApplicationContext();
        Context device = app.createDeviceProtectedStorageContext();
        if (!moved) {
            try {
                // One-time move of the schedule saved by older versions (only possible once the phone is unlocked).
                if (device.moveSharedPreferencesFrom(app, "alarms")) moved = true;
            } catch (RuntimeException ignored) {
            }
        }
        return device.getSharedPreferences("alarms", Context.MODE_PRIVATE);
    }

    static void saveSchedule(Context c, String json) {
        prefs(c).edit().putString("schedule", json == null ? "{}" : json).apply();
    }

    static String schedule(Context c) {
        return prefs(c).getString("schedule", "{}");
    }
}
