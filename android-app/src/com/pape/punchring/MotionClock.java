package com.pape.punchring;

/** Continuous graph time, including when the user adjusts playback speed mid-motion. */
final class MotionClock {
    private long last = -1;
    private double time;
    private int speed = 100;

    long now(long realNow) {
        if (last < 0) time = realNow;
        else time += Math.max(0, realNow - last) * speed / 100d;
        last = realNow;
        return Math.round(time);
    }

    void setSpeed(long realNow, int percent) {
        now(realNow);
        speed = Math.max(10, Math.min(500, percent));
    }
}
