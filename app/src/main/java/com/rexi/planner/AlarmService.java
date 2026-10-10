package com.rexi.planner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import org.json.JSONException;
import org.json.JSONObject;

/** Rings the alarm (alarm volume, so it works on silent) until the mission is solved. */
public class AlarmService extends Service {
    static final String ACTION_RING = "com.rexi.planner.RING";
    static final String ACTION_STOP = "com.rexi.planner.STOP";
    static final String CHANNEL = "alarm_ring_v1";
    static final String CHANNEL_MISSED = "alarm_missed_v1";
    static final int NOTIF_ID = 7001;
    static final int MISSED_ID = 7002;
    private static final long TIMEOUT_MS = 15 * 60 * 1000L;

    static volatile boolean ringing = false;
    static volatile String currentInfo = null;
    private static AlarmService instance;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private int savedVolume = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long quietUntil = 0;
    private boolean vibrateOn = true;

    private JSONObject infoJson() {
        try {
            return new JSONObject(currentInfo == null ? "{}" : currentInfo);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            if (!ringing) {
                // Make sure we satisfy the foreground-service contract before stopping.
                try {
                    startAsForeground(buildNotification(this, currentInfo == null ? "{}" : currentInfo));
                } catch (Exception ignored) {
                }
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        String info = intent.getStringExtra("info");
        if (info == null) info = "{}";
        currentInfo = info;
        instance = this;
        ensureChannels(this);
        startAsForeground(buildNotification(this, info));
        ringing = true;
        startRinging();

        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::timeout, TIMEOUT_MS);

        // Try to open the mission screen straight away (Android may only allow this via the full-screen notification).
        try {
            Intent open = new Intent(this, AlarmActivity.class);
            open.putExtra("info", info);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            startActivity(open);
        } catch (Exception ignored) {
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void startRinging() {
        stopSound();
        quietUntil = 0;
        JSONObject cfg = infoJson();
        vibrateOn = cfg.optBoolean("vibrate", true);
        int volPct = Math.max(60, Math.min(100, cfg.optInt("volume", 100))); // never below 60% of max
        AudioManager am = getSystemService(AudioManager.class);
        if (am != null) {
            try {
                if (savedVolume < 0) savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM);
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, Math.round(max * volPct / 100f)), 0);
            } catch (Exception e) {
                savedVolume = -1;
            }
        }

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        try {
            JSONObject snd = null;
            try { snd = new JSONObject(currentInfo == null ? "{}" : currentInfo).optJSONObject("sound"); } catch (Exception ignored) {}
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(this, Sounds.resolve(this, snd));
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            // Chosen sound missing or unreadable (e.g. file deleted) - fall back to the beep.
            stopSound();
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(attrs);
                player.setDataSource(this, Sounds.beep(this));
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception e2) {
                stopSound();
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = getSystemService(VibratorManager.class);
                vibrator = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = getSystemService(Vibrator.class);
            }
            if (vibrateOn && vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0), attrs);
            }
        } catch (Exception ignored) {
        }

        try {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RexiPlanner:alarm");
                wakeLock.acquire(TIMEOUT_MS + 60_000L);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopSound() {
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private void timeout() {
        String info = currentInfo;
        AlarmActivity.finishIfOpen();
        stopSelf();
        try {
            JSONObject o = new JSONObject(info == null ? "{}" : info);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                Intent open = new Intent(this, MainActivity.class);
                PendingIntent pi = PendingIntent.getActivity(this, 3, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification n = new Notification.Builder(this, CHANNEL_MISSED)
                        .setSmallIcon(R.drawable.ic_stat_alarm)
                        .setContentTitle("Missed alarm")
                        .setContentText(o.optString("title", "Alarm") + " rang for 15 minutes.")
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build();
                nm.notify(MISSED_ID, n);
            }
        } catch (JSONException ignored) {
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopSound();
        AudioManager am = getSystemService(AudioManager.class);
        if (am != null && savedVolume >= 0) {
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0);
            } catch (Exception ignored) {
            }
        }
        savedVolume = -1;
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
        wakeLock = null;
        ringing = false;
        currentInfo = null;
        if (instance == this) instance = null;
        AlarmActivity.finishIfOpen();
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    /**
     * Silences the alarm while the mission is being done. Each call extends the quiet window.
     * When the window runs out (the user stopped or left the app) the alarm rings again and the
     * mission screen is brought back.
     */
    static void quiet(int seconds) {
        final AlarmService s = instance;
        if (s == null) return;
        s.handler.post(() -> {
            if (!ringing || s.quietUntil == Long.MAX_VALUE) return;
            s.quietUntil = System.currentTimeMillis() + Math.max(5, seconds) * 1000L;
            try {
                if (s.player != null && s.player.isPlaying()) s.player.pause();
                if (s.vibrator != null) s.vibrator.cancel();
            } catch (Exception ignored) {
            }
            s.handler.removeCallbacks(s.quietEnd);
            s.handler.postDelayed(s.quietEnd, Math.max(5, seconds) * 1000L);
        });
    }

    /** Ring at full volume again right away (e.g. the mission switched from speaking to typing). */
    static void resumeNow() {
        final AlarmService s = instance;
        if (s == null) return;
        s.handler.post(() -> {
            if (!ringing || s.quietUntil == Long.MAX_VALUE) return;
            s.quietUntil = 0;
            s.handler.removeCallbacks(s.quietEnd);
            s.resumeRunnable.run();
        });
    }

    /** Mission finished: silence for good and close by itself shortly if the user doesn't tap Continue. */
    static void missionDone() {
        final AlarmService s = instance;
        if (s == null) return;
        s.handler.post(() -> {
            s.quietUntil = Long.MAX_VALUE;
            ringing = false; // the mission is done: don't reopen the alarm screen
            s.handler.removeCallbacks(s.quietEnd);
            try {
                if (s.player != null && s.player.isPlaying()) s.player.pause();
                if (s.vibrator != null) s.vibrator.cancel();
            } catch (Exception ignored) {
            }
            s.handler.postDelayed(s::stopSelf, 30_000L);
        });
    }

    private final Runnable quietEnd = () -> {
        if (!ringing) return;
        quietUntil = 0;
        this.resumeRunnable.run();
        try {
            Intent open = new Intent(this, AlarmActivity.class);
            open.putExtra("info", currentInfo == null ? "{}" : currentInfo);
            open.putExtra("resume", true);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(open);
        } catch (Exception ignored) {
        }
        // Android 10+ may block opening the screen from the background: re-post the full-screen alert too.
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(this, currentInfo == null ? "{}" : currentInfo));
        } catch (Exception ignored) {
        }
    };

    /** Quiets the alarm briefly so the microphone can hear the affirmation. */
    static void pauseForSpeech() {
        final AlarmService s = instance;
        if (s == null) return;
        s.handler.post(() -> {
            try {
                if (s.player != null && s.player.isPlaying()) s.player.pause();
                if (s.vibrator != null) s.vibrator.cancel();
            } catch (Exception ignored) {
            }
            s.handler.removeCallbacks(s.resumeRunnable);
            s.handler.postDelayed(s.resumeRunnable, 12_000L);
        });
    }

    static void resumeAfterSpeech() {
        final AlarmService s = instance;
        if (s == null) return;
        s.handler.post(() -> {
            s.handler.removeCallbacks(s.resumeRunnable);
            s.resumeRunnable.run();
        });
    }

    private final Runnable resumeRunnable = () -> {
        if (!ringing) return;
        if (System.currentTimeMillis() < quietUntil) return; // still in the quiet window of a mission
        try {
            if (player != null && !player.isPlaying()) player.start();
            if (vibrateOn && vibrator != null && vibrator.hasVibrator()) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0), attrs);
            }
        } catch (Exception ignored) {
        }
    };

    /** Plays a distinct feedback vibration; pauses the alarm's own vibration briefly so it can be felt. */
    static void feedbackBuzz(Context c, boolean wrong) {
        final long[] pattern = wrong ? new long[]{0, 90, 60, 90, 60, 260} : new long[]{0, 40};
        final AlarmService s = instance;
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = c.getSystemService(VibratorManager.class);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = c.getSystemService(Vibrator.class);
            }
            if (v == null || !v.hasVibrator()) return;
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            v.cancel();
            v.vibrate(VibrationEffect.createWaveform(pattern, -1), attrs);
            if (s != null && ringing) {
                s.handler.removeCallbacks(s.resumeVibration);
                s.handler.postDelayed(s.resumeVibration, 900L);
            }
        } catch (Exception ignored) {
        }
    }

    private final Runnable resumeVibration = () -> {
        if (!ringing || vibrator == null || !vibrateOn || System.currentTimeMillis() < quietUntil) return;
        try {
            if (vibrator.hasVibrator()) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 800, 600}, 0), attrs);
            }
        } catch (Exception ignored) {
        }
    };

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Shown while an alarm is ringing");
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setBypassDnd(true);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CHANNEL_MISSED) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_MISSED, "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }
    }

    static Notification buildNotification(Context c, String info) {
        ensureChannels(c);
        JSONObject o;
        try {
            o = new JSONObject(info);
        } catch (JSONException e) {
            o = new JSONObject();
        }
        boolean mission = o.optBoolean("mission", false);
        Intent full = new Intent(c, AlarmActivity.class);
        full.putExtra("info", info);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullPi = PendingIntent.getActivity(c, 1, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(o.optString("title", "Alarm"))
                .setContentText(mission ? "Tap to solve the mission and stop the alarm" : "Tap to stop the alarm")
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(0xFFC4572F)
                .setContentIntent(fullPi)
                .setFullScreenIntent(fullPi, true);
        if (!mission) {
            Intent stop = new Intent(c, AlarmService.class);
            stop.setAction(ACTION_STOP);
            PendingIntent stopPi = PendingIntent.getService(c, 2, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(null, "Stop", stopPi).build());
        }
        return b.build();
    }

    /** Used only if Android refuses to start the ringing service: show a plain alarm notification. */
    static void postFallback(Context c, String info) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        try {
            nm.notify(NOTIF_ID, buildNotification(c, info));
        } catch (Exception ignored) {
        }
    }
}
