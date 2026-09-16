package com.pape.punchring;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One entrance/hold/exit per state transition; no recurring warning animation. */
final class StatusIconMotion {
    static final int ENTER_MS = 620;
    static final int EXIT_MS = 520;
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private Set<String> observed = new HashSet<>();
    private String current;
    private long started;
    private int activeSpeed = 100;
    private int activeHold = 2200;

    void reset() {
        pending.clear();
        observed.clear();
        current = null;
    }

    void observe(List<String> keys, boolean visible) {
        Set<String> next = new HashSet<>(keys);
        pending.removeIf(key -> !next.contains(key));
        if (visible) {
            for (String key : keys) {
                if (!observed.contains(key) && pending.size() < 6) pending.add(key);
            }
        } else {
            pending.clear();
            current = null;
        }
        observed = next;
    }

    String advance(long now, int holdMs, int speedPercent) {
        return advance(now, holdMs, speedPercent, true);
    }

    String advance(long now, int holdMs, int speedPercent, boolean animate) {
        if (current != null && now - started >= duration(activeHold, activeSpeed, animate)) current = null;
        if (current == null && !pending.isEmpty()) {
            current = pending.removeFirst();
            started = now;
            activeSpeed = Math.max(10, Math.min(500, speedPercent));
            activeHold = Math.max(1000, Math.min(10000, holdMs));
        }
        return current;
    }

    float reveal(long now, int holdMs, int speedPercent) {
        return reveal(now, holdMs, speedPercent, true);
    }

    float reveal(long now, int holdMs, int speedPercent, boolean animate) {
        if (!animate) return current == null ? 0f : 1f;
        float edge = ENTER_MS * 100f / activeSpeed;
        float exit = EXIT_MS * 100f / activeSpeed;
        float elapsed = Math.max(0, now - started);
        if (elapsed < edge) return RingStateMotion.ease(elapsed / edge);
        if (elapsed <= edge + activeHold) return 1f;
        float t = Math.min(1f, (elapsed - edge - activeHold) / exit);
        return 1f - RingStateMotion.ease(t);
    }

    boolean running() { return current != null || !pending.isEmpty(); }

    private long duration(int holdMs, int speed) {
        return duration(holdMs, speed, true);
    }

    private long duration(int holdMs, int speed, boolean animate) {
        return (animate ? Math.round((ENTER_MS + EXIT_MS) * 100f
            / Math.max(10, Math.min(500, speed))) : 0L) + holdMs;
    }
}
