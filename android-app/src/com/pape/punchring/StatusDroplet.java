package com.pape.punchring;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/** Two circles joined by a pinching Bezier neck. No blur, bitmap, or extra window. */
final class StatusDroplet {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path neck = new Path();
    private final Path cameraMask = new Path();
    private final StatusIconGlyph glyph = new StatusIconGlyph();

    void draw(Canvas canvas, String key, float cx, float cy, float sourceRadius,
            float destination, float diameter, float reveal, int bubbleColor, int glyphColor) {
        draw(canvas, key, cx, cy, sourceRadius, destination, cy, diameter, reveal, bubbleColor, glyphColor);
    }

    void draw(Canvas canvas, String key, float cx, float cy, float sourceRadius,
            float destination, float destinationY, float diameter, float reveal, int bubbleColor, int glyphColor) {
        if (reveal <= 0f) return;
        float p = Math.max(0f, Math.min(1f, reveal));
        float travel = destination - cx;
        float bx = cx + travel * p;
        float growth = smooth(p);
        float by = cy + (destinationY - cy) * growth;
        float initialRadius = Math.min(sourceRadius * .65f, diameter * .225f);
        float radius = initialRadius + (diameter / 2f - initialRadius) * growth;
        float stretch = 1f + .18f * (float) Math.sin(Math.PI * p);
        float rx = radius * stretch, ry = radius / stretch;
        float separation = Math.max(1f, travel * .88f);
        float pinch = smooth((travel * p / separation - .48f) / .52f);
        float attachment = 1f - pinch;

        int save = canvas.save();
        cameraMask.reset();
        cameraMask.addCircle(cx, cy, sourceRadius, Path.Direction.CW);
        canvas.clipOutPath(cameraMask); // Never whiten the camera/punch-hole region.
        float distance = Math.max(1f, (float) Math.hypot(bx - cx, by - cy));
        float ux = (bx - cx) / distance, uy = (by - cy) / distance;
        float gradientEnd = Math.max(sourceRadius + diameter * .5f, distance - rx);
        paint.setShader(new LinearGradient(cx + ux * sourceRadius, cy + uy * sourceRadius,
            cx + ux * gradientEnd, cy + uy * gradientEnd,
            Color.BLACK, bubbleColor, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);

        if (attachment > 0f && bx + rx > cx + sourceRadius) {
            float h0 = sourceRadius * .66f * attachment;
            float h1 = ry * .72f * attachment;
            float x0 = cx + (float) Math.sqrt(sourceRadius * sourceRadius - h0 * h0);
            float x1 = bx - rx * (float) Math.sqrt(Math.max(0f, 1f - h1 * h1 / (ry * ry)));
            if (x1 > x0) {
                float middle = (x0 + x1) / 2f;
                float middleY = (cy + by) / 2f;
                float throat = Math.min(h0, h1) * .58f * attachment;
                neck.reset();
                neck.moveTo(x0, cy - h0);
                neck.cubicTo(x0 + (middle - x0) * .5f, cy - h0,
                    middle - (middle - x0) * .4f, middleY - throat, middle, middleY - throat);
                neck.cubicTo(middle + (x1 - middle) * .4f, middleY - throat,
                    x1 - (x1 - middle) * .5f, by - h1, x1, by - h1);
                neck.lineTo(x1, by + h1);
                neck.cubicTo(x1 - (x1 - middle) * .5f, by + h1,
                    middle + (x1 - middle) * .4f, middleY + throat, middle, middleY + throat);
                neck.cubicTo(middle - (middle - x0) * .4f, middleY + throat,
                    x0 + (middle - x0) * .5f, cy + h0, x0, cy + h0);
                neck.close();
                canvas.drawPath(neck, paint);
            }
        }
        canvas.drawOval(bx - rx, by - ry, bx + rx, by + ry, paint);
        paint.setShader(null);
        float glyphReveal = smooth((p - .48f) / .40f);
        if (key != null) glyph.draw(canvas, key, bx, by, diameter * .66f,
            (glyphColor & 0x00ffffff) | (Math.round(255f * glyphReveal) << 24));
        canvas.restoreToCount(save);
    }

    private static float smooth(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return t * t * (3f - 2f * t);
    }
}
