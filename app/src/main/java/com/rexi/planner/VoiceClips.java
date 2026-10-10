package com.rexi.planner;

/** Only known alert identifiers may resolve to a packaged recording. */
final class VoiceClips {
    static String file(String event) {
        if (event == null) return null;
        switch (event) {
            case "study_start":
            case "break_suggested":
            case "break_start":
            case "break_end":
            case "goal_reached":
            case "study_complete":
            case "bible_nlt":
                return "www/voices/" + event + ".mp3";
            default:
                return null;
        }
    }
}
