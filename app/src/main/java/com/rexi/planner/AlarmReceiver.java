package com.rexi.planner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Receives alarm firings, plus phone restarts / time changes (to re-schedule alarms). */
public class AlarmReceiver extends BroadcastReceiver {
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
            Intent ring = new Intent(context, AlarmService.class);
            ring.setAction(AlarmService.ACTION_RING);
            ring.putExtra("info", info == null ? "{}" : info);
            try {
                context.startForegroundService(ring);
            } catch (Exception e) {
                AlarmService.postFallback(context, info == null ? "{}" : info);
            }
        }
        // Weekly alarms: schedule the next occurrence (also handles boot / time changes).
        AlarmScheduler.scheduleAll(context);
    }
}
