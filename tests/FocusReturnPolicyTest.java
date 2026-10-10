package com.rexi.planner;

public class FocusReturnPolicyTest {
    private static void check(boolean expected, boolean actual, String message) {
        if (expected != actual) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(false, FocusReturnPolicy.shouldLaunch(false, false, false), "Do not launch while display sleeps");
        check(false, FocusReturnPolicy.shouldLaunch(true, true, false), "Wait for keyguard unlock");
        check(false, FocusReturnPolicy.shouldLaunch(true, false, true), "Do not relaunch visible timer");
        check(true, FocusReturnPolicy.shouldLaunch(true, false, false), "Return from Home when awake and unlocked");
        check(true, FocusReturnPolicy.shouldReturn(true, "break", false, "study"), "Web timer expiry must request return");
        check(true, FocusReturnPolicy.shouldReturn(true, "study", true, "study"), "Timer sync must preserve pending native return");
        check(false, FocusReturnPolicy.shouldReturn(true, "study", false, "study"), "Acknowledged return must stay cleared");
        check(false, FocusReturnPolicy.shouldReturn(true, "study", true, "paused"), "Emergency pause must cancel return");
        check(false, FocusReturnPolicy.shouldReturn(true, "study", true, "break"), "New break must remain unlocked");
        check(false, FocusReturnPolicy.shouldReturn(false, "study", true, "study"), "Old return must not leak to new session");
        check(true, FocusReturnPolicy.shouldCover("study", true, false, true, false, false), "Break over in another app: cover the screen");
        check(true, FocusReturnPolicy.shouldCover("study", false, true, true, false, false), "Blocked app Android would not leave: cover it");
        check(false, FocusReturnPolicy.shouldCover("study", true, false, false, false, false), "Study page already showing / screen off / locked: no cover");
        check(false, FocusReturnPolicy.shouldCover("study", true, false, true, true, false), "Never cover a phone or WhatsApp call");
        check(false, FocusReturnPolicy.shouldCover("study", true, false, true, false, true), "Never cover a ringing wake-up alarm");
        check(false, FocusReturnPolicy.shouldCover("break", true, true, true, false, false), "Breaks stay free");
        check(false, FocusReturnPolicy.shouldCover("paused", true, true, true, false, false), "Emergency pause stays free");
        check(false, FocusReturnPolicy.shouldCover("study", false, false, true, false, false), "Nothing pending, allowed app: no cover");
        check(true, FocusReturnPolicy.dndWanted("study", true, 1000, 2000), "Silence notifications during study time");
        check(false, FocusReturnPolicy.dndWanted("break", true, 1000, 2000), "Notifications come back on breaks");
        check(false, FocusReturnPolicy.dndWanted("paused", true, 1000, 2000), "Notifications come back on emergency pause");
        check(false, FocusReturnPolicy.dndWanted("study", false, 1000, 2000), "Setting off: never silence");
        check(false, FocusReturnPolicy.dndWanted("study", true, 1000, 1000 + 9L * 3600_000L), "Stale session never keeps the phone silenced");
        System.out.println("Break-return policy tests passed.");
    }
}
