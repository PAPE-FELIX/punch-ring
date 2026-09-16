package com.pape.punchring;

public final class VpnIndicatorTest {
    public static void main(String[] args) {
        check(VpnIndicator.alpha(0) == 255, "100 percent");
        check(VpnIndicator.alpha(1000) == 51, "20 percent");
        for (int t = 0; t < 2000; t++) {
            int a = VpnIndicator.alpha(t);
            check(a >= 51 && a <= 255, "bounded opacity");
            check(a == VpnIndicator.alpha(t + 2000), "exact 2s breathing period");
            check(Math.abs(a - VpnIndicator.alpha(t + 1)) <= 1, "smooth brightness");
        }
        check(VpnIndicator.xSign(0) == -1 && VpnIndicator.ySign(0) == -1, "upper left");
        check(VpnIndicator.xSign(1) == 1 && VpnIndicator.ySign(1) == -1, "upper right");
        check(VpnIndicator.xSign(2) == -1 && VpnIndicator.ySign(2) == 1, "lower left");
        check(VpnIndicator.xSign(3) == 1 && VpnIndicator.ySign(3) == 1, "lower right");
        System.out.println("PASS: VPN 20-100% opacity, 2s period, four corners");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
