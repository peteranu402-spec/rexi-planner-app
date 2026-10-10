package com.rexi.planner;

import android.app.Activity;
import android.app.AlertDialog;
import android.webkit.JsResult;

/** Safety net: if the page ever opens a browser alert/confirm, show it as a plain "Rexi Rise" dialog
 *  instead of Android's "The page at file:// says" box. */
final class JsDialogs {
    private JsDialogs() {
    }

    static boolean show(Activity a, String message, final JsResult result, boolean withCancel) {
        try {
            if (a == null || a.isFinishing()) {
                result.cancel();
                return true;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setMessage(message)
                    .setCancelable(true)
                    .setOnCancelListener(d -> result.cancel())
                    .setPositiveButton(withCancel ? "Yes" : "OK", (d, w) -> result.confirm());
            if (withCancel) b.setNegativeButton("Cancel", (d, w) -> result.cancel());
            b.show();
        } catch (Exception e) {
            result.cancel();
        }
        return true;
    }
}
