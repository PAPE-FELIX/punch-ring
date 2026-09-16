package com.pape.punchring;

public final class StatusHapticTest {
    public static void main(String[] args) {
        StatusHaptic haptic = new StatusHaptic();
        StatusState state = new StatusState();
        state.networkConnected = true;
        check(haptic.observe(state) == StatusHaptic.NONE, "no startup haptic");
        state.charging = true;
        check(haptic.observe(state) == StatusHaptic.CHARGE, "charging has two-stage grammar");
        state.charging = false;
        haptic.observe(state);
        state.networkConnected = false;
        check(haptic.observe(state) == StatusHaptic.WARNING, "disconnect warning");
        state.networkConnected = true;
        check(haptic.observe(state) == StatusHaptic.CONNECT, "connection confirmation");
        state.batteryPercent = 14;
        check(haptic.observe(state) == StatusHaptic.WARNING, "low battery edge warning");
        state.screenInteractive = false;
        state.networkConnected = false;
        check(haptic.observe(state) == StatusHaptic.NONE, "silent with screen off");
        System.out.println("PASS: haptic startup guard, connect, charging, warning, screen-off");
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
