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
import java.util.Locale;

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
        float[] known = knownHole(view.getContext());
        if (known != null) return known[4] * density;
        return 24f * density;
    }

    static float[] center(View view) {
        float density = view.getResources().getDisplayMetrics().density;
        Rect hole = bestPunchHole(view);
        if (hole != null) return new float[] {hole.exactCenterX(), hole.exactCenterY()};
        float[] known = knownHole(view.getContext());
        if (known != null) {
            float[] center = knownCenter(view, known);
            // The Fold7 udcCutout is the exact hole; DEFAULT_OFFSET_Y_DP is tuned for top-edge cutout rects.
            if (isFold7()) center[1] -= AppSettings.DEFAULT_OFFSET_Y_DP * density;
            return center;
        }
        return new float[] {view.getWidth() / 2f, 23f * density};
    }

    /** Horizontal hole position as a fraction of the natural width, for screenshot sampling. */
    static float centerXFraction(Context context) {
        float[] known = knownHole(context);
        return known == null ? 0.5f : known[2] / known[0];
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

    private static boolean isFold7() {
        return Build.MODEL.toUpperCase(Locale.ROOT).startsWith("SM-F966");
    }

    /**
     * Hole geometry for displays that report no DisplayCutout:
     * {natural width px, natural height px, center x px, center y px, diameter dp}.
     */
    private static float[] knownHole(Context context) {
        boolean inner = AppSettings.isInnerDisplay(context);
        if (isFold8()) {
            return inner ? new float[] {2448f, 1848f, 1862f, 57f, 78f / 2.25f}
                : new float[] {1248f, 1972f, 624f, 52f, 70f / 2.25f};
        }
        // Fold7 exposes its inner punch hole only as Samsung's private udcCutout (dumpsys window displays).
        if (isFold7() && inner) return new float[] {1968f, 2184f, 1485f, 53f, 70f / 2.625f};
        return null;
    }

    private static float[] knownCenter(View view, float[] known) {
        int width = view.getWidth();
        int height = view.getHeight();
        Display display = view.getDisplay();
        int rotation = display == null ? Surface.ROTATION_0 : display.getRotation();
        float naturalWidth = known[0];
        float naturalHeight = known[1];
        float naturalX = known[2];
        float naturalY = known[3];
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
