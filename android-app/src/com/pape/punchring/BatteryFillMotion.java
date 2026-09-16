package com.pape.punchring;

/** Quiet brightness breath; battery arc length never changes during fast charging. */
final class BatteryFillMotion {
    private long lastFrame = -1;
    private double elapsed;
    private boolean wasCharging;
    private boolean wasSpinning;

    float frame(long now, boolean charging, boolean spinning, int speedPercent) {
        if (!charging || !wasCharging) elapsed = 0;
        if (charging && wasCharging && !spinning && !wasSpinning && lastFrame >= 0) {
            elapsed += Math.max(0, now - lastFrame)
                * Math.max(10, Math.min(500, speedPercent)) / 100d;
        }
        lastFrame = now;
        wasCharging = charging;
        wasSpinning = spinning;
        return factor(elapsed);
    }

    static float factor(double elapsed) {
        return .96f + .04f * (float) Math.cos(2d * Math.PI * elapsed / 4000d);
    }
}
