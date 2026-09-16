package com.pape.punchring;

/** Maps real state edges to a small, stable haptic vocabulary. */
final class StatusHaptic {
    static final int NONE = 0;
    static final int CONNECT = 1;
    static final int CHARGE = 2;
    static final int WARNING = 3;
    private StatusState previous;

    int observe(StatusState next) {
        if (previous == null) { previous = next.copy(); return NONE; }
        int result = NONE;
        if (!previous.charging && next.charging) result = CHARGE;
        else if ((previous.networkConnected && !next.networkConnected)
                || (previous.wifiConnected && !next.wifiConnected)
                || (previous.cellularRegistered && !next.cellularRegistered)
                || (previous.batteryPercent >= 15 && next.batteryPercent < 15)) result = WARNING;
        else if ((!previous.networkConnected && next.networkConnected)
                || (!previous.wifiConnected && next.wifiConnected)
                || (!previous.cellularRegistered && next.cellularRegistered)) result = CONNECT;
        previous = next.copy();
        return next.screenInteractive ? result : NONE;
    }
}
