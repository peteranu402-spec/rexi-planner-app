package com.rexi.planner;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plays packaged voice alerts offline; phone text-to-speech handles other dynamic speech
 * and acts as a fallback if a recording cannot be played. Playback runs on the main looper.
 */
final class Speaker implements TextToSpeech.OnInitListener {
    private static Speaker instance;

    private final Context ctx;
    private final TextToSpeech tts;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private boolean ready = false;
    private final List<String> pending = new ArrayList<>();

    static synchronized Speaker get(Context c) {
        if (instance == null) instance = new Speaker(c.getApplicationContext());
        return instance;
    }

    private Speaker(Context c) {
        ctx = c;
        tts = new TextToSpeech(c, this);
    }

    @Override
    public void onInit(int status) {
        handler.post(() -> finishInit(status));
    }

    private void finishInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;
        try {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            applyVoice();
            // Normal pace with a slightly brighter pitch: warm, friendly and encouraging.
            tts.setSpeechRate(1.0f);
            tts.setPitch(1.08f);
        } catch (Exception ignored) {
        }
        ready = true;
        for (String s : pending) speakNow(s);
        pending.clear();
    }

    /** Uses the most natural English voice available on the phone. */
    private void applyVoice() {
        Voice best = null;
        int bestScore = -1;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices != null) {
                for (Voice v : voices) {
                    if (v == null || v.getLocale() == null) continue;
                    int sc = score(v);
                    if (sc > bestScore) {
                        bestScore = sc;
                        best = v;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (best != null) {
            try {
                tts.setVoice(best);
                return;
            } catch (Exception ignored) {
            }
        }
        int r = tts.setLanguage(Locale.UK);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US);
    }

    private static boolean isBroken(Voice v) {
        Set<String> f = v.getFeatures();
        return f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
    }

    /** Higher = more natural. Prefers high-quality offline English voices. */
    private static int score(Voice v) {
        Locale l = v.getLocale();
        if (!"en".equals(l.getLanguage()) || isBroken(v)) return -1;
        int s = v.getQuality();                       // 100..500
        if (v.isNetworkConnectionRequired()) s -= 150; // works offline is more reliable for alarms
        String c = l.getCountry();
        if ("US".equals(c)) s += 60;
        else if ("GB".equals(c)) s += 45;
        else if ("AU".equals(c) || "CA".equals(c)) s += 25;
        // Bright, friendly voices in Google's and Samsung's engines.
        String n = v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT);
        if (n.contains("sfg")) s += 40;
        else if (n.contains("tpf")) s += 36;
        else if (n.contains("iog")) s += 32;
        else if (n.contains("tpc")) s += 28;
        else if (n.contains("iob")) s += 24;
        else if (n.contains("smtf")) s += 20;
        if (v.getLatency() <= Voice.LATENCY_NORMAL) s += 5;
        return s;
    }

    private static final Set<String> KEEP_CAPS = new java.util.HashSet<>(java.util.Arrays.asList(
            "AM", "PM", "OK", "LORD", "GOD", "JESUS", "CHRIST", "US", "UK", "TV"));

    /**
     * Makes text safe to read aloud: drops emoji, course codes (CVE301, MTH 201), acronyms the voice
     * would spell letter by letter (GST, KJV) and stray symbols, then tidies what's left.
     */
    static String clean(String t) {
        if (t == null) return "";
        String s = t;
        s = s.replaceAll("\\(\\s*[A-Z]{2,6}\\s*\\)", " ");
        s = s.replaceAll("[\\p{So}\\p{Cs}\\x{FE0F}\\x{200D}\\x{20E3}]", "");
        s = s.replace("·", ",").replace("—", ", ").replace("–", ", ").replace("→", " ").replace("…", ".")
                .replace("&", " and ").replace("#", " ").replace("*", " ").replace("_", " ");
        s = s.replaceAll("\\b[A-Z]{2,6}\\s?-?\\d{2,4}[A-Za-z]?\\b", " ");
        s = s.replaceAll("\\b(?!\\d+(?:st|nd|rd|th)\\b)(?=[A-Za-z0-9]*[A-Za-z])(?=[A-Za-z0-9]*\\d)[A-Za-z0-9]+(?:[-/][A-Za-z0-9]+)*\\b", " ");
        Matcher m = Pattern.compile("\\b[A-Z]{2,6}\\b").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, KEEP_CAPS.contains(m.group()) ? m.group() : " ");
        m.appendTail(sb);
        s = sb.toString();
        s = s.replaceAll("\\(\\s*\\)", " ");
        s = s.replaceAll("\\bpick up\\s+(?=again\\b|now\\b|[.!?,]|$)", "pick it up ");
        s = s.replaceAll("\\b(to|of|on|for|with|about)\\s+(?=again\\b|now\\b|[.!?,]|$)", "$1 it ");
        s = s.replaceAll("\\s+([.,!?])", "$1").replaceAll(",\\s*(?=[.!?,])", "")
                .replaceAll("\\s{2,}", " ").replaceAll("^[\\s,.]+", "").trim();
        return s;
    }

    void say(String raw) {
        handler.post(() -> {
            stopNow();
            sayNow(raw);
        });
    }

    private void sayNow(String raw) {
        String text = clean(raw);
        if (text.isEmpty()) return;
        if (!ready) {
            pending.add(text);
            return;
        }
        speakNow(text);
    }

    void play(String event, String fallback) {
        if (VoiceClips.file(event) == null) return;
        handler.post(() -> playNow(event, fallback));
    }

    private void playNow(String event, String fallback) {
        stopNow();
        try (AssetFileDescriptor fd = ctx.getAssets().openFd(VoiceClips.file(event))) {
            MediaPlayer clip = new MediaPlayer();
            player = clip;
            clip.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            // CPU wake only: audio may finish with the display off, without holding the screen on.
            clip.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK);
            clip.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            clip.setOnPreparedListener(p -> {
                if (player != p) return;
                try { p.start(); } catch (RuntimeException e) {
                    releaseClip();
                    sayNow(fallback);
                }
            });
            clip.setOnCompletionListener(p -> { if (player == p) releaseClip(); });
            clip.setOnErrorListener((p, what, extra) -> {
                if (player == p) {
                    releaseClip();
                    sayNow(fallback);
                }
                return true;
            });
            clip.prepareAsync();
        } catch (Exception e) {
            releaseClip();
            sayNow(fallback);
        }
    }

    void stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) stopNow();
        else handler.post(this::stopNow);
    }

    private void stopNow() {
        pending.clear();
        try { tts.stop(); } catch (Exception ignored) {}
        releaseClip();
    }

    private void releaseClip() {
        MediaPlayer old = player;
        player = null;
        if (old != null) {
            try { old.release(); } catch (Exception ignored) {}
        }
    }

    /** Speaks sentence by sentence with small natural pauses. */
    private void speakNow(String text) {
        try {
            String[] parts = text.trim().split("(?<=[.!?])\\s+");
            String base = "rexi-" + System.currentTimeMillis();
            for (int i = 0; i < parts.length; i++) {
                int mode = i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
                if (i > 0) tts.playSilentUtterance(260, TextToSpeech.QUEUE_ADD, base + "-p" + i);
                tts.speak(parts[i], mode, new Bundle(), base + "-" + i);
            }
        } catch (Exception ignored) {
        }
    }
}
