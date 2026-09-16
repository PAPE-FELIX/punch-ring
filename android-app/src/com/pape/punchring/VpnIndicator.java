package com.pape.punchring;

/** Clock and corner mapping for the persistent VPN badge. Independent of graph speed. */
final class VpnIndicator {
    static int alpha(long now) {
        double phase = Math.floorMod(now, 2000L) / 2000d;
        return (int) Math.round(153d + 102d * Math.cos(phase * Math.PI * 2d));
    }
    static int xSign(int corner) { return corner == 0 || corner == 2 ? -1 : 1; }
    static int ySign(int corner) { return corner == 0 || corner == 1 ? -1 : 1; }
}
