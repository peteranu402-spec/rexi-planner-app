package com.rexi.planner;

/** Shared claim rules keep native alarms and foreground timer updates from speaking twice. */
final class FocusVoicePolicy {
    static boolean goal(boolean sameSession, boolean previouslyReached, boolean reached) {
        return sameSession && !previouslyReached && reached;
    }

    static boolean breakEnd(boolean sameSession, String previousState, String nextState,
                            long endAt, long now, long spokenEndAt) {
        return sameSession && "break".equals(previousState) && "study".equals(nextState)
                && endAt > 0 && now >= endAt && endAt > spokenEndAt;
    }

    static boolean suggestion(long threshold, long spokenThreshold) {
        return threshold > 0 && threshold > spokenThreshold;
    }
}
