package com.pape.punchring;

import java.util.List;

public final class StatusIconMotionTest {
    public static void main(String[] args) {
        StatusIconMotion motion = new StatusIconMotion();
        motion.observe(List.of("temperature:warm", "bluetooth:1"), true);
        check("temperature:warm".equals(motion.advance(100, 2200, 100)), "priority order");
        check(motion.reveal(100, 2200, 100) == 0, "starts concealed");
        check(motion.reveal(720, 2200, 100) == 1, "fully emerged");
        float exit = motion.reveal(3180, 2200, 100);
        check(exit > 0 && exit < 1, "returns smoothly");
        check("bluetooth:1".equals(motion.advance(3440, 2200, 100)), "queued event");
        motion.observe(List.of("temperature:warm", "bluetooth:1"), true);
        check(motion.advance(6780, 2200, 100) == null, "unchanged conditions do not repeat");
        motion.observe(List.of(), true);
        motion.observe(List.of("temperature:warm"), true);
        check(motion.advance(7000, 1000, 200) != null, "rearmed after recovery");
        check(motion.reveal(7310, 1000, 200) == 1, "speed applies to entrance");
        check(motion.advance(8570, 1000, 200) == null, "speed applies to exit");
        motion.observe(List.of("vpn:2"), true);
        motion.observe(List.of(), true);
        check(motion.advance(8700, 2200, 100) == null, "expired queued event removed");
        motion.observe(List.of("wifi:3"), false);
        motion.observe(List.of("wifi:3"), true);
        check(!motion.running(), "hidden events do not replay on wake");
        motion.reset();
        motion.observe(List.of("wifi:reduced"), true);
        check(motion.advance(9000, 2200, 100, false) != null
            && motion.reveal(9000, 2200, 100, false) == 1, "reduce motion displays without travel");
        for (int speed : new int[] {10, 100, 500}) {
            motion.reset();
            motion.observe(List.of("charging:100"), true);
            motion.advance(0, 10000, speed);
            long entrance = 62000 / speed;
            long end = 114000 / speed + 10000;
            check(motion.reveal(entrance, 10000, speed) == 1, "full speed range entrance");
            check(motion.reveal(entrance + 9999, 10000, speed) == 1, "10s hold independent of speed");
            check(motion.advance(end, 10000, speed) == null, "full speed range completion");
        }
        System.out.println("PASS: priority, timing, exit, deduplication, recovery, speed, expiry, screen-off");
    }

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
