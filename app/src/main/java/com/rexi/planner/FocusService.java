package com.rexi.planner;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps a "Studying …" notification with a live countdown while a Focus session runs. */
public class FocusService extends Service {
    private static volatile boolean running = false;

    static void refresh(Context c) {
        if (!FocusEngine.isActive(c)) return;
        Intent i = new Intent(c, FocusService.class);
        try {
            if (running) c.startService(i);
            else c.startForegroundService(i);
        } catch (Exception ignored) {
            // App not allowed to start a service right now: the alarms still fire on time.
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!FocusEngine.isActive(this)) {
            startAsForeground();
            stopSelf();
            return START_NOT_STICKY;
        }
        startAsForeground();
        if (!running) {
            running = true;
            tick.removeCallbacks(loop);
            tick.post(loop);
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FocusEngine.ONGOING_ID, FocusEngine.ongoing(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(FocusEngine.ONGOING_ID, FocusEngine.ongoing(this));
        }
    }

    /*
     * Backup for phones that kill the Accessibility service: every second, end an expired break and, while a
     * return to study is pending, keep trying to open the study page. With "Display over other apps" allowed,
     * Android lets the app open itself and this service can also show the full-screen study cover.
     */
    private final android.os.Handler tick = new android.os.Handler(android.os.Looper.getMainLooper());
    private StudyOverlay overlay;
    private long lastTry = 0;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            try {
                FocusEngine.checkBreakDeadline(FocusService.this);
                org.json.JSONObject o = FocusEngine.state(FocusService.this);
                boolean pending = o != null && "study".equals(o.optString("state")) && o.optBoolean("returnToStudy", false);
                boolean backup = !FocusBlockService.isReady(FocusService.this);
                long now = System.currentTimeMillis();
                if (pending && backup && now - lastTry >= 2000) {
                    lastTry = now;
                    FocusEngine.returnToStudy(FocusService.this);
                }
                if (backup) FocusDnd.apply(FocusService.this, o);
                boolean inCall = false;
                android.media.AudioManager am = getSystemService(android.media.AudioManager.class);
                if (am != null) inCall = am.getMode() != android.media.AudioManager.MODE_NORMAL;
                boolean cover = pending && backup && !inCall && !AlarmService.ringing
                        && android.provider.Settings.canDrawOverlays(FocusService.this) && FocusEngine.canShowStudy(FocusService.this);
                if (cover) {
                    if (overlay == null) overlay = new StudyOverlay(FocusService.this,
                            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                    overlay.show(o, true, null);
                } else if (overlay != null) {
                    overlay.hide();
                }
            } catch (RuntimeException e) {
                android.util.Log.e("RexiFocus", "Focus backup check failed", e);
            } finally {
                if (running) tick.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onDestroy() {
        tick.removeCallbacks(loop);
        if (overlay != null) overlay.hide();
        running = false;
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
