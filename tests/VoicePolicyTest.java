package com.rexi.planner;

public class VoicePolicyTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        String[] events = {"study_start", "break_suggested", "break_start", "break_end",
                "goal_reached", "study_complete", "bible_nlt"};
        for (String event : events) check(VoiceClips.file(event).equals("www/voices/" + event + ".mp3"), event);
        check(VoiceClips.file("session_resumed") == null, "resumed voice excluded");
        check(VoiceClips.file("../other") == null && VoiceClips.file(null) == null, "unknown clips rejected");
        check(FocusVoicePolicy.goal(true, false, true), "foreground goal announcement");
        check(!FocusVoicePolicy.goal(true, true, true), "native-first goal does not repeat");
        check(!FocusVoicePolicy.goal(false, false, true), "new-session state does not repeat an old goal");
        check(FocusVoicePolicy.breakEnd(true, "break", "study", 100, 100, 0), "foreground expiry");
        check(!FocusVoicePolicy.breakEnd(true, "break", "study", 100, 99, 0), "manual return has no resumed voice");
        check(!FocusVoicePolicy.breakEnd(true, "break", "study", 100, 120, 100), "stale break cannot speak twice");
        check(!FocusVoicePolicy.breakEnd(true, "study", "study", 100, 120, 0), "native-first expiry does not repeat");
        check(!FocusVoicePolicy.breakEnd(false, "break", "study", 100, 120, 0), "no cross-session expiry");
        check(FocusVoicePolicy.suggestion(100, 0), "first suggestion");
        check(!FocusVoicePolicy.suggestion(100, 100), "foreground/native suggestion race");
        check(FocusVoicePolicy.suggestion(200, 100), "next study interval gets its suggestion");
        check(!FocusVoicePolicy.suggestion(0, 100), "no disabled suggestion");
        System.out.println("Native voice mapping and announcement policy tests passed.");
    }
}
