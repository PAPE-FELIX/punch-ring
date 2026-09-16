package com.pape.punchring;

import java.util.ArrayDeque;

/** Deterministic transitions, independent of Android's frame rate. */
final class RingStateMotion {
    static final long SPIN_MS = 1450;
    static final long DOT_MS = 650;
    private StatusState previous;
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();
    private int spinning;
    private long spinStarted;
    private long dotsStarted;
    private final float[] fromCell = new float[4], fromWifi = new float[4];
    private final float[] toCell = new float[4], toWifi = new float[4];
    private final boolean[] changed = new boolean[4];

    void update(StatusState state, long now) {
        update(state, now, true);
    }

    void update(StatusState state, long now, boolean animate) {
        boolean first = previous == null;
        if (!animate) {
            queue.clear(); spinning = 0;
            for (int i = 0; i < 4; i++) {
                fromCell[i] = toCell[i] = i < state.cellularLevel ? 1f : 0f;
                fromWifi[i] = toWifi[i] = i < state.wifiLevel ? 1f : 0f;
                changed[i] = false;
            }
            dotsStarted = now - DOT_MS - 150;
            previous = state.copy();
            return;
        }
        if (!first && state.screenInteractive) {
            if (previous.wifiConnected != state.wifiConnected
                    || (state.wifiConnected && previous.wifiNetworkId != state.wifiNetworkId)) enqueue(1);
            if (previous.plugged != state.plugged || previous.charging != state.charging
                    || previous.fastCharging != state.fastCharging) enqueue(2);
            if (previous.cellularRegistered != state.cellularRegistered
                    || previous.cellularDataConnected != state.cellularDataConnected
                    || previous.cellularNetworkType != state.cellularNetworkType) enqueue(3);
        }
        if (first || previous.cellularLevel != state.cellularLevel || previous.wifiLevel != state.wifiLevel) {
            // Snapshot ALL old dots before changing the common animation start.
            for (int i = 0; i < 4; i++) {
                float cell = first ? (i < state.cellularLevel ? 1f : 0f) : cell(i, now);
                float wifi = first ? (i < state.wifiLevel ? 1f : 0f) : wifi(i, now);
                fromCell[i] = cell;
                fromWifi[i] = wifi;
            }
            for (int i = 0; i < 4; i++) {
                toCell[i] = i < state.cellularLevel ? 1f : 0f;
                toWifi[i] = i < state.wifiLevel ? 1f : 0f;
                changed[i] = !first && (Math.abs(fromCell[i] - toCell[i]) > .01f
                    || Math.abs(fromWifi[i] - toWifi[i]) > .01f);
            }
            dotsStarted = first ? now - DOT_MS - 150 : now;
        }
        if (!state.screenInteractive) { queue.clear(); spinning = 0; }
        previous = state.copy();
    }

    void previewSpin(int kind) { enqueue(kind); }
    private void enqueue(int kind) { if (!queue.contains(kind) && queue.size() < 3) queue.add(kind); }

    int advance(long now) {
        if (spinning != 0 && now - spinStarted >= SPIN_MS) spinning = 0;
        if (spinning == 0 && !queue.isEmpty()) { spinning = queue.removeFirst(); spinStarted = now; }
        return spinning;
    }

    float spinProgress(long now) { return ease((now - spinStarted) / (float) SPIN_MS); }
    float startAngle(float original, long now) {
        float p = spinProgress(now);
        float growth = 270f - original;
        // First the head extends, then the tail catches up. Both endpoints always
        // move clockwise; independently rotating and shrinking can reverse the head.
        float catchup = p <= .5f ? 0f : growth * (1f - accent(now));
        return 180f + (360f - growth) * p + catchup;
    }
    float accent(long now) {
        float wave = (float) Math.sin(Math.PI * spinProgress(now));
        return wave * wave;
    }
    float sweep(float original, long now) {
        return original + (270f - original) * accent(now);
    }
    float dotVisibility(float angle, float original, float clearanceDegrees, long now) {
        // Anticipate the head by 180ms, trail the tail by 220ms in graph time.
        // Angular ordering makes the four dots disappear/reappear in quick succession.
        float delayedTail = startAngle(original, now - 220);
        float earlyHead = startAngle(original, now + 180) + sweep(original, now + 180);
        float relative = ((angle - delayedTail) % 360f + 360f) % 360f;
        float length = Math.min(360f, earlyHead - delayedTail);
        if (relative <= length) return 0f;
        // Head and tail share the geometry clock. Shrink only OUTSIDE the stroke,
        // including the round cap and dot radius, so the shapes never overlap.
        float distance = Math.min(relative - length, 360f - relative);
        return ease((distance - clearanceDegrees) / 10f);
    }
    float cell(int i, long now) { return mix(fromCell[i], toCell[i], dotProgress(i, now)); }
    float wifi(int i, long now) { return mix(fromWifi[i], toWifi[i], dotProgress(i, now)); }
    float scale(int i, long now) {
        return changed[i] ? 1f + .5f * (float) Math.sin(Math.PI * dotProgress(i, now)) : 1f;
    }
    boolean running(long now) {
        return spinning != 0 || !queue.isEmpty() || now - dotsStarted < DOT_MS + 135;
    }
    private float dotProgress(int i, long now) { return ease((now - dotsStarted - i * 45) / (float) DOT_MS); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    // CSS ease-in-out = cubic-bezier(.42, 0, .58, 1), including its time axis.
    static float ease(float value) {
        float x = Math.max(0, Math.min(1, value));
        float lo = 0, hi = 1, t = x;
        for (int i = 0; i < 16; i++) {
            t = (lo + hi) / 2;
            float inv = 1 - t;
            float sample = 3 * inv * inv * t * .42f + 3 * inv * t * t * .58f + t * t * t;
            if (sample < x) lo = t; else hi = t;
        }
        return x == 0 ? 0 : x == 1 ? 1 : 3 * (1 - t) * t * t + t * t * t;
    }
}
