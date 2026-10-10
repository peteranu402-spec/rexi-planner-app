package com.rexi.planner;

/** Keeps break-expiry returns pending until the study page is visibly restored. */
final class FocusReturnPolicy {
    private FocusReturnPolicy() {}

    static boolean shouldLaunch(boolean interactive, boolean keyguardLocked, boolean alreadyVisible) {
        return interactive && !keyguardLocked && !alreadyVisible;
    }

    /**
     * Full-screen "back to studying" cover: shown while a break-over return is pending, or when a blocked app
     * has stayed open for 2 s because Android ignored the bounce. Never over calls, a ringing alarm, the lock
     * screen, a sleeping display, or when the study page is already on screen (canShow covers the last three).
     */
    static boolean shouldCover(String state, boolean pendingReturn, boolean blockedForMs2s,
                               boolean canShow, boolean inCall, boolean alarmRinging) {
        return "study".equals(state) && (pendingReturn || blockedForMs2s) && canShow && !inCall && !alarmRinging;
    }

    /** Study-time Do Not Disturb: only while studying (not breaks or pause), never for a stale session. */
    static boolean dndWanted(String state, boolean silence, long updatedAt, long now) {
        if (!silence || !"study".equals(state)) return false;
        return updatedAt <= 0 || now - updatedAt <= 8L * 3600_000L;
    }

    static boolean shouldReturn(boolean sameSession, String previousState,
                                boolean pendingReturn, String nextState) {
        return sameSession && "study".equals(nextState)
                && (pendingReturn || "break".equals(previousState));
    }
}
