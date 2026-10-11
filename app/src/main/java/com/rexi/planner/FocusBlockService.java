package com.rexi.planner;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * "Block apps" and "Strict lock" modes. Only looks at which app came to the front (its package name)
 * and, during a study session, sends the user back to the Focus timer if that app isn't allowed.
 */
public class FocusBlockService extends AccessibilityService {
    private static volatile boolean connected;
    private static volatile FocusBlockService instance;

    /** Turns the screen off (like pressing the power button). Used to honour the phone's sleep timer on the study page. */
    static boolean lockScreen() {
        FocusBlockService s = instance;
        if (s == null || android.os.Build.VERSION.SDK_INT < 28) return false;
        try {
            return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (RuntimeException e) {
            return false;
        }
    }
    private static volatile long lastHealthyTick;

    /** An enabled setting alone does not prove Android has bound a working service. */
    static boolean isReady(android.content.Context c) {
        if (!connected || android.os.SystemClock.elapsedRealtime() - lastHealthyTick > 5000) return false;
        try {
            String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            android.content.ComponentName ours = new android.content.ComponentName(c, FocusBlockService.class);
            for (String value : enabled.split(":")) {
                if (ours.equals(android.content.ComponentName.unflattenFromString(value))) return true;
            }
        } catch (RuntimeException e) {
            android.util.Log.w("RexiAccessibility", "Cannot check service readiness", e);
        }
        return false;
    }

    private final android.os.Handler deadlineHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable deadlineCheck = new Runnable() {
        @Override public void run() {
            try {
            FocusEngine.checkBreakDeadline(FocusBlockService.this);
            org.json.JSONObject o = FocusEngine.state(FocusBlockService.this);
            if (o != null && o.optBoolean("returnToStudy", false) && System.currentTimeMillis() - lastBounce >= 1500) {
                lastBounce = System.currentTimeMillis();
                FocusEngine.returnToStudy(FocusBlockService.this);
            }
            // A stationary Home screen produces no new window-change event at break expiry.
            enforceForeground();
            // If Android refused to bring the planner forward, cover the screen instead.
            updateOverlay();
            // Keep study-time Do Not Disturb in step (and switch it off for stale sessions).
            FocusDnd.apply(FocusBlockService.this, FocusEngine.state(FocusBlockService.this));
            lastHealthyTick = android.os.SystemClock.elapsedRealtime();
            } catch (RuntimeException e) {
                lastHealthyTick = 0;
                android.util.Log.e("RexiAccessibility", "Focus deadline check failed", e);
            } finally {
                deadlineHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        instance = this;
        // The phone may have cleared our alarms while the app was killed; put them back whenever the service restarts.
        try { AlarmScheduler.scheduleAll(this); } catch (RuntimeException ignored) { }
        lastHealthyTick = android.os.SystemClock.elapsedRealtime();
        deadlineHandler.removeCallbacks(deadlineCheck);
        deadlineHandler.post(deadlineCheck);
    }

    private void disconnected() {
        if (overlay != null) overlay.hide();
        connected = false;
        instance = null;
        lastHealthyTick = 0;
        deadlineHandler.removeCallbacks(deadlineCheck);
    }

    @Override public boolean onUnbind(Intent intent) {
        disconnected();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        disconnected();
        super.onDestroy();
    }

    private StudyOverlay overlay;
    private String blockedPkg = null, blockedLabel = null;
    private long blockedSince = 0;
    private long lastBounce = 0;
    private String lastPkg = null;
    private String foregroundPkg = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handleWindowEvent(event);
        } catch (RuntimeException e) {
            lastHealthyTick = 0;
            android.util.Log.e("RexiAccessibility", "Focus window handling failed", e);
        }
    }

    private void handleWindowEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = event.getPackageName();
        if (p == null) return;
        // Our own cover window must not count as "the planner is open".
        if (p.toString().equals(getPackageName()) && overlay != null && overlay.isShowing()) return;
        foregroundPkg = p.toString();
        FocusEngine.checkBreakDeadline(this);
        enforceForeground();
    }

    private void enforceForeground() {
        if (!FocusEngine.canShowStudy(this)) return;
        String pkg = foregroundPkg;
        if (pkg == null) return;
        // Never get in the way of a phone or WhatsApp call (ringing or in progress).
        try {
            android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
            if (am != null && am.getMode() != android.media.AudioManager.MODE_NORMAL) return;
        } catch (Exception ignored) {
        }
        FocusEngine.checkBreakDeadline(this);
        if (!FocusEngine.shouldBlock(this, pkg)) return;
        boolean strict = FocusEngine.isStrict(this);
        long now = System.currentTimeMillis();
        if (pkg.equals(lastPkg) && now - lastBounce < (strict ? 600 : 1500)) return;
        lastPkg = pkg;
        lastBounce = now;

        String label = pkg;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = pm.getApplicationLabel(ai).toString();
        } catch (Exception ignored) {
        }
        if (!strict) {
            try {
                performGlobalAction(GLOBAL_ACTION_HOME);
            } catch (Exception ignored) {
            }
        } else if (isLauncher(pkg)) {
            label = "Home screen";
        }
        blockedLabel = label;
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("blocked", label);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /**
     * Shows the full-screen study cover while a return to study is pending (break over) or a blocked app
     * stays open for 2 s because Android ignored the bounce, and the planner is not on screen.
     */
    private void updateOverlay() {
        if (overlay == null) overlay = new StudyOverlay(this);
        org.json.JSONObject o = FocusEngine.state(this);
        if (o == null || !"study".equals(o.optString("state"))) {
            overlay.hide();
            blockedPkg = null;
            return;
        }
        long now = System.currentTimeMillis();
        String pkg = foregroundPkg;
        boolean blockedNow = pkg != null && !pkg.equals(getPackageName()) && FocusEngine.shouldBlock(this, pkg);
        if (!blockedNow) blockedPkg = null;
        else if (!pkg.equals(blockedPkg)) { blockedPkg = pkg; blockedSince = now; }
        boolean pending = o.optBoolean("returnToStudy", false);
        boolean stuck = blockedNow && now - blockedSince >= 2000;
        boolean inCall = false;
        try {
            android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
            inCall = am != null && am.getMode() != android.media.AudioManager.MODE_NORMAL;
        } catch (Exception ignored) {
        }
        if (FocusReturnPolicy.shouldCover(o.optString("state"), pending, stuck, FocusEngine.canShowStudy(this), inCall, AlarmService.ringing)) {
            overlay.show(o, pending, stuck ? blockedLabel : null);
        } else {
            overlay.hide();
        }
    }

    private boolean isLauncher(String pkg) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
    }
}
