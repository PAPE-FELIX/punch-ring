package com.pape.punchring;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class AppSettings {
    static final String PREFS = "punch_ring_settings";
    static final String ENABLED = "overlay_enabled";
    static final String DIAMETER_DP = "diameter_dp";
    static final String DOT_DIAMETER_DP = "dot_diameter_dp";
    static final String SYNC_DIAMETERS = "sync_diameters";
    static final String SHOW_LAYOUT_GUIDE = "show_layout_guide";
    static final String AUTO_CONTRAST = "auto_contrast";
    /** Long-press target: "panel" (device status) or "camera" (rear camera + smile face). */
    static final String LONG_PRESS_ACTION = "long_press_action";
    static final String LONG_PRESS_PANEL = "panel";
    static final String LONG_PRESS_CAMERA = "camera";
    static final String DEFAULT_LONG_PRESS_ACTION = LONG_PRESS_PANEL;

    static final String BACKGROUND_IS_LIGHT = "background_is_light";
    static final String OFFSET_X_DP = "offset_x_dp";
    static final String OFFSET_Y_DP = "offset_y_dp";
    static final String STROKE_TENTHS_DP = "stroke_tenths_dp";
    static final String FAST_THRESHOLD_WATTS = "fast_threshold_watts";
    static final String THERMAL_THRESHOLD_C = "thermal_threshold_c";
    static final String SMART_HIDE = "smart_hide";
    static final String DIM_ON_SCREEN_OFF = "dim_on_screen_off";
    static final String BURN_IN_PROTECTION = "burn_in_protection";
    static final String STYLE_PRESET = "style_preset";
    static final String ANIMATION_SPEED_PERCENT = "animation_speed_percent";
    static final String ICON_SIZE_PERCENT = "icon_size_percent";
    static final String ICON_GAP_DP = "icon_gap_dp";
    static final String ICON_HOLD_MS = "icon_hold_ms";
    static final String VPN_DOT_CORNER = "vpn_dot_corner";
    static final int DEFAULT_VPN_DOT_CORNER = 1; // upper right
    static final String PRIORITY_ORDER = "priority_order";
    static final String FOREGROUND_PACKAGE = "foreground_package";
    private static final String SETTINGS_VERSION = "settings_version";

    // Tuned on the Galaxy Z Fold8 cover display. These are also used after a fresh install.
    static final int DEFAULT_DIAMETER_DP = 35;
    static final int DEFAULT_DOT_DIAMETER_DP = 35;
    static final boolean DEFAULT_SYNC_DIAMETERS = true;
    static final int DEFAULT_OFFSET_X_DP = 0;
    static final int DEFAULT_OFFSET_Y_DP = 4;
    static final int DEFAULT_STROKE_TENTHS_DP = 32;
    static final boolean DEFAULT_SHOW_LAYOUT_GUIDE = false;
    static final boolean DEFAULT_AUTO_CONTRAST = true;
    static final int DEFAULT_FAST_THRESHOLD_WATTS = 12;
    static final int DEFAULT_THERMAL_THRESHOLD_C = 42;
    static final boolean DEFAULT_SMART_HIDE = true;
    static final boolean DEFAULT_DIM_ON_SCREEN_OFF = true;
    static final boolean DEFAULT_BURN_IN_PROTECTION = true;
    static final int DEFAULT_STYLE_PRESET = 0;
    static final int DEFAULT_ANIMATION_SPEED_PERCENT = 100;
    static final String DEFAULT_PRIORITY_ORDER = "temperature,network,event,battery";

    private static final String[] PROFILE_INT_KEYS = {
        DIAMETER_DP, DOT_DIAMETER_DP, OFFSET_X_DP, OFFSET_Y_DP, STROKE_TENTHS_DP
    };

    private AppSettings() {}

    static SharedPreferences prefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int settingsVersion = prefs.getInt(SETTINGS_VERSION, 0);
        if (settingsVersion < 6) {
            SharedPreferences.Editor editor = prefs.edit();
            if (settingsVersion < 2) {
                // v2 deliberately resets only visual scale. Position tuning is retained.
                editor.putInt(DIAMETER_DP, DEFAULT_DIAMETER_DP)
                    .putInt(STROKE_TENTHS_DP, DEFAULT_STROKE_TENTHS_DP);
            }
            int batteryDiameter = settingsVersion < 2
                ? DEFAULT_DIAMETER_DP
                : prefs.getInt(DIAMETER_DP, DEFAULT_DIAMETER_DP);
            int dotDiameter = settingsVersion < 3
                ? batteryDiameter
                : prefs.getInt(DOT_DIAMETER_DP, DEFAULT_DOT_DIAMETER_DP);
            boolean sync = prefs.getBoolean(SYNC_DIAMETERS, DEFAULT_SYNC_DIAMETERS);
            int offsetX = prefs.getInt(OFFSET_X_DP, DEFAULT_OFFSET_X_DP);
            int offsetY = prefs.getInt(OFFSET_Y_DP, DEFAULT_OFFSET_Y_DP);
            int stroke = prefs.getInt(STROKE_TENTHS_DP, DEFAULT_STROKE_TENTHS_DP);
            for (boolean inner : new boolean[] { false, true }) {
                editor.putInt(profileKey(DIAMETER_DP, inner), batteryDiameter)
                    .putInt(profileKey(DOT_DIAMETER_DP, inner), dotDiameter)
                    .putBoolean(profileKey(SYNC_DIAMETERS, inner), sync)
                    .putInt(profileKey(OFFSET_X_DP, inner), offsetX)
                    .putInt(profileKey(OFFSET_Y_DP, inner), offsetY)
                    .putInt(profileKey(STROKE_TENTHS_DP, inner), stroke);
            }
            editor.putInt(DOT_DIAMETER_DP, dotDiameter)
                .putBoolean(SYNC_DIAMETERS, sync)
                .putInt(SETTINGS_VERSION, 6)
                .apply();
        }
        return prefs;
    }

    static boolean isInnerDisplay(Context context) {
        WindowManager manager = context.getSystemService(WindowManager.class);
        Rect bounds = manager == null ? null : manager.getCurrentWindowMetrics().getBounds();
        int width = bounds == null ? context.getResources().getDisplayMetrics().widthPixels
            : bounds.width();
        int height = bounds == null ? context.getResources().getDisplayMetrics().heightPixels
            : bounds.height();
        return Math.max(width, height) > 2100
            || (float) Math.min(width, height) / Math.max(width, height) > 0.70f;
    }

    static String profileKey(String base, boolean inner) {
        return base + (inner ? "_inner" : "_cover");
    }

    static int getProfileInt(Context context, String base, int fallback) {
        return prefs(context).getInt(profileKey(base, isInnerDisplay(context)), fallback);
    }

    static boolean getProfileBoolean(Context context, String base, boolean fallback) {
        return prefs(context).getBoolean(profileKey(base, isInnerDisplay(context)), fallback);
    }

    static void putProfileInt(Context context, String base, int value) {
        prefs(context).edit().putInt(profileKey(base, isInnerDisplay(context)), value).apply();
    }

    static void putProfileBoolean(Context context, String base, boolean value) {
        prefs(context).edit().putBoolean(profileKey(base, isInnerDisplay(context)), value).apply();
    }

    static List<String> priorityOrder(Context context) {
        String stored = prefs(context).getString(PRIORITY_ORDER, DEFAULT_PRIORITY_ORDER);
        List<String> valid = Arrays.asList("temperature", "network", "event", "battery");
        List<String> result = new ArrayList<>();
        if (stored != null) {
            for (String item : stored.split(",")) {
                if (valid.contains(item) && !result.contains(item)) result.add(item);
            }
        }
        for (String item : valid) if (!result.contains(item)) result.add(item);
        return result;
    }

    static boolean isProfileIntKey(String key) {
        for (String base : PROFILE_INT_KEYS) {
            if (profileKey(base, false).equals(key) || profileKey(base, true).equals(key)) return true;
        }
        return false;
    }

    static boolean isImportableKey(String key) {
        if (isProfileIntKey(key)) return true;
        if (profileKey(SYNC_DIAMETERS, false).equals(key)
                || profileKey(SYNC_DIAMETERS, true).equals(key)) return true;
        return SHOW_LAYOUT_GUIDE.equals(key)
            || AUTO_CONTRAST.equals(key)
            || FAST_THRESHOLD_WATTS.equals(key)
            || THERMAL_THRESHOLD_C.equals(key)
            || SMART_HIDE.equals(key)
            || DIM_ON_SCREEN_OFF.equals(key)
            || BURN_IN_PROTECTION.equals(key)
            || STYLE_PRESET.equals(key)
            || ANIMATION_SPEED_PERCENT.equals(key)
            || ICON_SIZE_PERCENT.equals(key)
            || ICON_GAP_DP.equals(key)
            || ICON_HOLD_MS.equals(key)
            || VPN_DOT_CORNER.equals(key)
            || PRIORITY_ORDER.equals(key);
    }
}
