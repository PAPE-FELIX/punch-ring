package com.pape.punchring;

final class PunchRingRuntime {
    private static StatusState latest = new StatusState();

    private PunchRingRuntime() {}

    static synchronized void update(StatusState state) {
        latest = state.copy();
    }

    static synchronized StatusState snapshot() {
        return latest.copy();
    }
}
