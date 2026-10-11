package com.rexi.planner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Receives alarm firings, plus phone restarts / time changes (to re-schedule alarms). */
public class AlarmReceiver extends BroadcastReceiver {
    /** Records an alarm that rang more than 2 minutes after its time (Android or the phone maker delayed it). */
    static void noteLateness(Context context, String info) {
        try {
            long due = new org.json.JSONObject(info == null ? "{}" : info).optLong("due", 0);
            long late = System.currentTimeMillis() - due;
            if (due > 0 && late > 120_000L) {
                AlarmStore.prefs(context).edit().putLong("lateDue", due).putLong("lateMs", late).apply();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (FocusEngine.ACTION_EVENT.equals(action)) {
            // Keep the receiver alive briefly so the spoken alert can finish.
            final PendingResult result = goAsync();
            try {
                FocusEngine.onEvent(context, intent.getStringExtra("type"));
            } finally {
                new Handler(Looper.getMainLooper()).postDelayed(result::finish, 8000);
            }
            return;
        }
        if (DailyQuote.ACTION.equals(action)) {
            DailyQuote.show(context);
            DailyQuote.schedule(context);
            return;
        }
        if (AlarmScheduler.ACTION_FIRE.equals(action)) {
            String info = intent.getStringExtra("info");
            noteLateness(context, info);
            Intent ring = new Intent(context, AlarmService.class);
            ring.setAction(AlarmService.ACTION_RING);
            ring.putExtra("info", info == null ? "{}" : info);
            try {
                context.startForegroundService(ring);
            } catch (Exception e) {
                AlarmService.postFallback(context, info == null ? "{}" : info);
            }
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            // The phone was off: ring an alarm missed in the last few minutes, and report older ones.
            AlarmScheduler.handleMissedWhileOff(context);
        }
        // Weekly alarms: schedule the next occurrence (also handles boot / time changes).
        AlarmScheduler.scheduleAll(context);
    }
}
