package com.rexi.planner;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONObject;

/**
 * "Silence notifications while studying": during study time (not breaks) the phone goes into
 * Do Not Disturb that only lets calls (any caller, plus repeat callers) and alarms through.
 * Alarms stay allowed so wake-up alarms and Rexi Rise's own break / goal alerts still sound.
 * The user's previous Do Not Disturb setting is saved and put back when study time stops.
 * Needs "Do Not Disturb access" (notification policy access); without it nothing changes.
 */
final class FocusDnd {
    private FocusDnd() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("focus_dnd", Context.MODE_PRIVATE);
    }

    static boolean hasAccess(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    /** Called whenever the focus state changes (and every second by the Accessibility service). */
    static synchronized void apply(Context c, JSONObject o) {
        boolean want = o != null && FocusReturnPolicy.dndWanted(o.optString("state"), o.optBoolean("silence", true),
                o.optLong("updatedAt", 0), System.currentTimeMillis());
        SharedPreferences p = prefs(c);
        boolean active = p.getBoolean("active", false);
        if (!want && !active) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
            if (!want) p.edit().putBoolean("active", false).apply();
            return;
        }
        try {
            if (want) {
                if (!active) {
                    NotificationManager.Policy old = nm.getNotificationPolicy();
                    SharedPreferences.Editor e = p.edit()
                            .putBoolean("active", true)
                            .putInt("filter", nm.getCurrentInterruptionFilter());
                    if (old != null) {
                        e.putInt("cats", old.priorityCategories)
                                .putInt("calls", old.priorityCallSenders)
                                .putInt("msgs", old.priorityMessageSenders)
                                .putInt("fx", old.suppressedVisualEffects);
                        if (Build.VERSION.SDK_INT >= 30) e.putInt("convs", old.priorityConversationSenders);
                    }
                    e.apply();
                    nm.setNotificationPolicy(studyPolicy());
                }
                if (nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                }
            } else {
                restore(nm, p);
            }
        } catch (RuntimeException e) {
            android.util.Log.e("RexiDnd", "Cannot change Do Not Disturb", e);
        }
    }

    private static NotificationManager.Policy studyPolicy() {
        int cats = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
                | NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
                | NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
        int any = NotificationManager.Policy.PRIORITY_SENDERS_ANY;
        if (Build.VERSION.SDK_INT < 28) return new NotificationManager.Policy(cats, any, any);
        int hide = NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK
                | NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR
                | NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE
                | NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT
                | NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST
                | NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT
                | NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS;
        if (Build.VERSION.SDK_INT >= 30) {
            return new NotificationManager.Policy(cats, any, any, hide, NotificationManager.Policy.CONVERSATION_SENDERS_NONE);
        }
        return new NotificationManager.Policy(cats, any, any, hide);
    }

    private static void restore(NotificationManager nm, SharedPreferences p) {
        if (p.contains("cats")) {
            int cats = p.getInt("cats", 0), calls = p.getInt("calls", 0), msgs = p.getInt("msgs", 0), fx = p.getInt("fx", 0);
            NotificationManager.Policy old;
            if (Build.VERSION.SDK_INT >= 30 && p.contains("convs")) old = new NotificationManager.Policy(cats, calls, msgs, fx, p.getInt("convs", 0));
            else if (Build.VERSION.SDK_INT >= 28) old = new NotificationManager.Policy(cats, calls, msgs, fx);
            else old = new NotificationManager.Policy(cats, calls, msgs);
            nm.setNotificationPolicy(old);
        }
        int filter = p.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL);
        if (filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) filter = NotificationManager.INTERRUPTION_FILTER_ALL;
        nm.setInterruptionFilter(filter);
        p.edit().clear().apply();
    }
}
