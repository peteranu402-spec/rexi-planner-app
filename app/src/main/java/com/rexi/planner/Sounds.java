package com.rexi.planner;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import org.json.JSONObject;

/** Works out which sound an alarm should play and plays previews. */
final class Sounds {
    private static MediaPlayer preview;

    private Sounds() {}

    /** Returns the selected sound, with a musical Happy Alley fallback before the emergency beep. */
    static Uri resolve(Context c, JSONObject sound) {
        Uri beep = Uri.parse("android.resource://" + c.getPackageName() + "/" + R.raw.alarm_beep);
        int musicalDefault = c.getResources().getIdentifier("alarm_happyalley", "raw", c.getPackageName());
        Uri fallback = musicalDefault == 0 ? beep : Uri.parse("android.resource://" + c.getPackageName() + "/" + musicalDefault);
        if (sound == null) return fallback;
        String kind = sound.optString("kind", "builtin");
        if ("system".equals(kind) || "file".equals(kind)) {
            String u = sound.optString("uri", "");
            return u.isEmpty() ? fallback : Uri.parse(u);
        }
        int res = c.getResources().getIdentifier("alarm_" + sound.optString("id", "happyalley"), "raw", c.getPackageName());
        if (res == 0) res = musicalDefault;
        return res == 0 ? beep : Uri.parse("android.resource://" + c.getPackageName() + "/" + res);
    }

    static Uri beep(Context c) {
        return Uri.parse("android.resource://" + c.getPackageName() + "/" + R.raw.alarm_beep);
    }

    static synchronized void preview(Context c, String json) {
        stopPreview();
        try {
            JSONObject s = new JSONObject(json);
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            p.setDataSource(c, resolve(c, s));
            p.setOnCompletionListener(mp -> stopPreview());
            p.prepare();
            p.start();
            preview = p;
        } catch (Exception ignored) {
            stopPreview();
        }
    }

    static synchronized void stopPreview() {
        if (preview != null) {
            try { preview.stop(); } catch (Exception ignored) {}
            try { preview.release(); } catch (Exception ignored) {}
            preview = null;
        }
    }
}
