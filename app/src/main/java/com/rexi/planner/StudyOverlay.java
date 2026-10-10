package com.rexi.planner;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Full-screen "back to studying" cover drawn by the Accessibility service.
 *
 * Android (and Tecno / Infinix / Xiaomi phones in particular) often refuses to let an app open itself
 * from the background, so after a break the planner could only post a notification while the user
 * stayed in another app. An accessibility overlay needs no extra permission and cannot be blocked
 * that way: it covers whatever is on screen until the user is back on the study page.
 */
final class StudyOverlay {
    private final android.content.Context service;
    private final int windowType;
    private LinearLayout root;
    private TextView title, subtitle, timeLeft, quote;

    /** Drawn by the Accessibility service (no extra permission needed). */
    StudyOverlay(android.accessibilityservice.AccessibilityService service) {
        this(service, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    }

    /** Backup used by the study timer service when Accessibility is not running ("Display over other apps"). */
    StudyOverlay(android.content.Context context, int windowType) {
        this.service = context;
        this.windowType = windowType;
    }

    boolean isShowing() {
        return root != null;
    }

    /** Show (or refresh) the cover. breakOver = the break just ended; otherwise a blocked app was opened. */
    void show(JSONObject o, boolean breakOver, String blockedLabel) {
        if (o == null) return;
        try {
            if (root == null) create();
            if (root == null) return;
            String subject = o.optString("subject", "your subject");
            title.setText(breakOver ? "Break's over" : (blockedLabel == null || blockedLabel.isEmpty() ? "Study time" : blockedLabel + " is locked"));
            subtitle.setText("Back to " + subject + ". Your phone stays on the study page until your goal.");
            String q = o.optString("quote", "").trim();
            quote.setText(q.isEmpty() ? "" : "“" + q + "”");
            quote.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
            timeLeft.setText(timeLeftText(o));
        } catch (RuntimeException e) {
            android.util.Log.e("RexiOverlay", "Cannot show study overlay", e);
            hide();
        }
    }

    void hide() {
        if (root == null) return;
        try {
            WindowManager wm = service.getSystemService(WindowManager.class);
            if (wm != null) wm.removeViewImmediate(root);
        } catch (RuntimeException ignored) {
        }
        root = null;
    }

    private void create() {
        WindowManager wm = service.getSystemService(WindowManager.class);
        if (wm == null) return;
        float d = service.getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(service);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF14161C);
        int pad = (int) (28 * d);
        box.setPadding(pad, pad, pad, pad);
        box.setClickable(true); // swallow every touch so the app underneath can't be used

        TextView icon = text("📖", 54, false, Color.WHITE);
        title = text("", 30, true, Color.WHITE);
        subtitle = text("", 16, false, 0xCCFFFFFF);
        TextView hint = text("If the button doesn't open it, tap the Rexi Rise notification.", 13, false, 0x80FFFFFF);
        timeLeft = text("", 40, true, 0xFFE2734A);
        quote = text("", 15, false, 0x99FFFFFF);
        quote.setTypeface(quote.getTypeface(), android.graphics.Typeface.ITALIC);

        Button back = new Button(service);
        back.setText("Back to studying");
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFC4572F);
        bg.setCornerRadius(18 * d);
        back.setBackground(bg);
        back.setOnClickListener(v -> openStudy());

        box.addView(icon, wrap(0));
        box.addView(title, wrap((int) (12 * d)));
        box.addView(subtitle, wrap((int) (10 * d)));
        box.addView(timeLeft, wrap((int) (24 * d)));
        box.addView(quote, wrap((int) (18 * d)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (58 * d));
        bp.topMargin = (int) (34 * d);
        box.addView(back, bp);
        box.addView(hint, wrap((int) (14 * d)));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(box, lp);
        root = box;
    }

    /** A tap from our own visible window is always allowed to open the planner. */
    private void openStudy() {
        Intent i = new Intent(service, MainActivity.class);
        i.putExtra("returnToStudy", true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            service.startActivity(i);
        } catch (RuntimeException e) {
            android.util.Log.e("RexiOverlay", "Cannot open the study page", e);
        }
    }

    private static String timeLeftText(JSONObject o) {
        long goalMs = o.optLong("goalMin", 0) * 60_000L;
        long elapsed = o.optLong("sessionMs", o.optLong("studiedMs", 0))
                + Math.max(0, System.currentTimeMillis() - o.optLong("updatedAt", System.currentTimeMillis()));
        long left = goalMs - elapsed;
        if (left <= 0) return "Goal reached";
        long s = left / 1000, h = s / 3600, m = s % 3600 / 60, sec = s % 60;
        return (h > 0 ? h + ":" + two(m) : String.valueOf(m)) + ":" + two(sec) + " left";
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(service);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private static LinearLayout.LayoutParams wrap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        return p;
    }
}
