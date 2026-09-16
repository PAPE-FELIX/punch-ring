package com.pape.punchring;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Colors and font for the long-press panel.
 *
 * <p>The panel keeps a fixed neutral dark palette so the app has no network dependency: colors are
 * defined here rather than fetched from anywhere.
 */
final class PanelTheme {

    PanelTheme(Context context) {
        // No state to load - kept as a constructor so callers read naturally.
    }

    int color(String key) {
        String value;
        switch (key) {
            case "background": value = "#0d0f12"; break;
            case "popover": value = "#171a1f"; break;
            case "card": value = "#14171c"; break;
            case "muted": value = "#171a20"; break;
            case "muted-foreground": value = "#8d949f"; break;
            case "border": value = "#262b33"; break;
            case "input": value = "#101318"; break;
            case "primary": value = "#5aa9ff"; break;
            case "primary-foreground": value = "#04121f"; break;
            case "secondary": value = "#1b1f26"; break;
            case "data-success": value = "#7ad17a"; break;
            default: value = "#e7eaef";
        }
        return Color.parseColor(value);
    }

    int radius() {
        return 8;
    }

    /** Public build uses the system font, so this only walks the tree for API symmetry. */
    static void applyFont(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyFont(group.getChildAt(i));
        } else if (view instanceof TextView) {
            ((TextView) view).setTypeface(((TextView) view).getTypeface());
        }
    }
}
