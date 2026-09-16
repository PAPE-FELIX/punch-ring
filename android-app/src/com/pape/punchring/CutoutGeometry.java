package com.pape.punchring;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;

import java.util.List;

/** Resolves a usable camera-hole anchor across phones, tablets and foldables. */
final class CutoutGeometry {
    private CutoutGeometry() {}

    static Rect bestPunchHole(List<Rect> bounds, int width, int height, float density) {
        if (bounds == null || bounds.isEmpty()) return null;
        Rect best = null;
        float bestScore = Float.MAX_VALUE;
        float maxDimension = Math.min(Math.max(width, height) * 0.28f, 120f * density);
        for (Rect rect : bounds) {
            if (rect == null || rect.isEmpty()) continue;
            int longSide = Math.max(rect.width(), rect.height());
            int shortSide = Math.min(rect.width(), rect.height());
            if (shortSide <= 0 || longSide > maxDimension) continue;
            float aspectPenalty = Math.abs(1f - shortSide / (float) longSide) * 1000f;
            float edgeDistance = Math.min(Math.min(rect.left, Math.max(0, width - rect.right)),
                Math.min(rect.top, Math.max(0, height - rect.bottom)));
            float score = aspectPenalty + edgeDistance / Math.max(1f, density)
                + longSide / Math.max(1f, density) * 0.05f;
            if (score < bestScore) {
                best = rect;
                bestScore = score;
            }
        }
        return best;
    }

    static Rect bestPunchHole(View view) {
        WindowInsets insets = view.getRootWindowInsets();
        DisplayCutout cutout = insets == null ? null : insets.getDisplayCutout();
        return bestPunchHole(cutout == null ? null : cutout.getBoundingRects(),
            view.getWidth(), view.getHeight(), view.getResources().getDisplayMetrics().density);
    }

    static float holeDiameter(View view) {
        float density = view.getResources().getDisplayMetrics().density;
        Rect hole = bestPunchHole(view);
        if (hole != null) return Math.min(hole.width(), hole.height());
        if (isFold8()) {
            return (AppSettings.isInnerDisplay(view.getContext()) ? 78f : 70f) * density / 2.25f;
        }
        return 24f * density;
    }

    static float[] center(View view) {
        Rect hole = bestPunchHole(view);
        if (hole != null) return new float[] {hole.exactCenterX(), hole.exactCenterY()};
        if (isFold8()) return fold8FallbackCenter(view);
        float density = view.getResources().getDisplayMetrics().density;
        return new float[] {view.getWidth() / 2f, 23f * density};
    }

    static int recommendedDiameterDp(Context context, Rect hole) {
        if (isFold8()) return 35;
        if (hole == null) return AppSettings.DEFAULT_DIAMETER_DP;
        float density = context.getResources().getDisplayMetrics().density;
        return Math.max(14, Math.min(50,
            Math.round(Math.min(hole.width(), hole.height()) / density * 1.2f)));
    }

    private static boolean isFold8() {
        return "SM-F971N".equalsIgnoreCase(Build.MODEL);
    }

    private static float[] fold8FallbackCenter(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        Display display = view.getDisplay();
        int rotation = display == null ? Surface.ROTATION_0 : display.getRotation();
        boolean inner = AppSettings.isInnerDisplay(view.getContext());
        float naturalWidth = inner ? 2448f : 1248f;
        float naturalHeight = inner ? 1848f : 1972f;
        float naturalX = inner ? 1862f : 624f;
        float naturalY = inner ? 57f : 52f;
        float scaleX;
        float scaleY;
        switch (rotation) {
            case Surface.ROTATION_90:
                scaleX = width / naturalHeight;
                scaleY = height / naturalWidth;
                return new float[] {(naturalHeight - naturalY) * scaleX, naturalX * scaleY};
            case Surface.ROTATION_180:
                scaleX = width / naturalWidth;
                scaleY = height / naturalHeight;
                return new float[] {(naturalWidth - naturalX) * scaleX,
                    (naturalHeight - naturalY) * scaleY};
            case Surface.ROTATION_270:
                scaleX = width / naturalHeight;
                scaleY = height / naturalWidth;
                return new float[] {naturalY * scaleX, (naturalWidth - naturalX) * scaleY};
            default:
                scaleX = width / naturalWidth;
                scaleY = height / naturalHeight;
                return new float[] {naturalX * scaleX, naturalY * scaleY};
        }
    }
}
