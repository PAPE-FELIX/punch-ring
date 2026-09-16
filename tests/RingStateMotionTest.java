package com.pape.punchring;

public final class RingStateMotionTest {
    public static void main(String[] args) {
        RingStateMotion motion = new RingStateMotion();
        StatusState state = new StatusState();
        state.cellularLevel = 4;
        motion.update(state, 1000);
        check(motion.advance(1000) == 0, "no launch spin");
        state.wifiLevel = 2;
        motion.update(state, 1500, false);
        check(motion.advance(1500) == 0 && motion.wifi(1, 1500) == 1
            && motion.wifi(2, 1500) == 0, "reduce motion snaps without rotation or dot tween");
        state.wifiLevel = 0;
        motion.update(state, 1600, true);
        state.wifiConnected = true;
        state.wifiNetworkId = 10;
        state.wifiLevel = 4;
        motion.update(state, 2000);
        check(motion.advance(2000) == 1, "wifi connect spin");
        check(motion.startAngle(110, 2000) == 180, "starts at original angle");
        check(Math.abs(motion.sweep(110, 2725) - 270) < .01, "270 degree peak");
        check(motion.startAngle(110, 3450) == 540, "exactly one full turn");
        check(Math.abs(motion.sweep(110, 3450) - 110) < .01, "restores original sweep");
        check(motion.wifi(3, 2785) == 1, "all dots settle");
        for (float original : new float[] {0, 2, 27, 74, 110, 180}) {
            float lastTail = 180, lastHead = 180 + original;
            for (long t = 2000; t <= 3450; t++) {
                float tail = motion.startAngle(original, t);
                float head = tail + motion.sweep(original, t);
                check(tail >= lastTail - .0002 && head >= lastHead - .0002,
                    "neither endpoint reverses at " + original + "/" + t);
                check(tail - lastTail < 1.5 && head - lastHead < 1.5, "no endpoint jump");
                lastTail = tail; lastHead = head;
            }
        }
        for (float angle : new float[] {35, 70, 110, 145}) {
            check(motion.dotVisibility(angle, 110, 12, 2000) == 1, "dot visible before head");
            boolean hidden = false, restored = false;
            long hidesAt = 0, returnsAt = 0, normalHide = 0, normalReturn = 0;
            float last = 1;
            for (long t = 2000; t <= 3450; t++) {
                float visible = motion.dotVisibility(angle, 110, 12, t);
                if (visible == 0) hidden = true;
                if (hidden && visible == 1) restored = true;
                if (visible == 0 && hidesAt == 0) hidesAt = t;
                if (visible == 1 && hidesAt > 0 && returnsAt == 0) returnsAt = t;
                float rel = ((angle - motion.startAngle(110, t)) % 360 + 360) % 360;
                float length = motion.sweep(110, t);
                float distance = rel <= length ? 0 : Math.min(rel - length, 360 - rel);
                if (distance <= 12 && normalHide == 0) normalHide = t;
                if (distance >= 22 && normalHide > 0 && normalReturn == 0) normalReturn = t;
                check(Math.abs(visible - last) < .2, "dot fade is continuous " + angle + "/" + t + ": " + last + " -> " + visible);
                last = visible;
            }
            check(hidden && restored, "head hides dot and tail restores it");
            check(hidesAt <= normalHide - 140 && returnsAt >= normalReturn + 180,
                "dots anticipate head and lag tail in sequence");
        }
        check(motion.advance(3450) == 0, "spin finishes");
        state.wifiLevel = 1;
        motion.update(state, 4000);
        check(motion.advance(4000) == 0, "RSSI change uses dots only");
        check(motion.scale(2, 4415) > 1.4, "changed dot visibly pulses");
        state.wifiConnected = false;
        state.wifiNetworkId = -1;
        state.plugged = true;
        state.cellularRegistered = true;
        motion.update(state, 5000);
        check(motion.advance(5000) == 1, "disconnect spin");
        check(motion.advance(6450) == 2, "charging queued");
        check(motion.advance(7900) == 3, "cellular queued");
        check(motion.advance(9350) == 0, "no loop");
        check(RingStateMotion.ease(0) == 0 && RingStateMotion.ease(1) == 1, "ease endpoints");
        BatteryFillMotion fill = new BatteryFillMotion();
        fill.frame(0, true, false, 100);
        float frozen = 0;
        for (int t = 16; t <= 320; t += 16) frozen = fill.frame(t, true, false, 100);
        check(fill.frame(336, true, true, 100) == frozen, "freeze at spin entry");
        check(fill.frame(1770, true, true, 100) == frozen, "hold charging during spin");
        check(fill.frame(1786, true, false, 100) == frozen, "resume at identical charge phase");
        for (int t = 1; t <= 5400; t++) {
            check(Math.abs(BatteryFillMotion.factor(t) - BatteryFillMotion.factor(t - 1)) < .01,
                "charging loop has no reset jump");
            check(BatteryFillMotion.factor(t) >= .9199f && BatteryFillMotion.factor(t) <= 1f,
                "fast charging breath is subtle");
        }
        for (int speed : new int[] {10, 100, 500}) {
            MotionClock clock = new MotionClock();
            clock.setSpeed(0, speed);
            check(clock.now(145000 / speed) == 1450, "full graph speed range");
            long before = clock.now(145000 / speed);
            clock.setSpeed(145000 / speed, 200);
            check(clock.now(145000 / speed) == before, "speed adjustment has no phase jump");
            check(clock.now(145000 / speed + 100) == before + 200, "new speed applies continuously");
        }
        System.out.println("PASS: monotonic head/tail, no jumps, 270 degree peak, dot occlusion, charge pause/resume, smooth loops, queue");
    }
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
