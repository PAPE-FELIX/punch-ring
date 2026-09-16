package com.pape.punchring;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** Shared droplet separation feeds the genie mesh; reverse the same path to absorb. */
final class GenieTransition {
    private final StatusDroplet droplet = new StatusDroplet();
    private final float[] vertices = new float[(GenieMesh.COLUMNS + 1) * (GenieMesh.ROWS + 1) * 2];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path cameraMask = new Path();

    void draw(Canvas canvas, Bitmap snapshot, float progress, float ax, float ay, float radius,
            float left, float top, float width, float height, int bubbleColor) {
        float p = RingStateMotion.ease(progress);
        float reveal = RingStateMotion.ease(p / .48f);
        float travel = radius * 3.1f;
        float bubbleY = ay + travel * reveal;
        float bubbleAlpha = 1f - RingStateMotion.ease((p - .35f) / .28f);
        if (bubbleAlpha > 0f && p > 0f) {
            int save = canvas.saveLayerAlpha(ax - radius * 3, ay - radius,
                ax + radius * 3, ay + travel + radius * 2, Math.round(255f * bubbleAlpha));
            canvas.rotate(90f, ax, ay);
            droplet.draw(canvas, null, ax, ay, radius, ax + travel,
                radius * 1.6f, reveal, bubbleColor, Color.TRANSPARENT);
            canvas.restoreToCount(save);
        }
        float meshProgress = Math.max(0f, Math.min(1f, (p - .18f) / .82f));
        if (meshProgress <= 0f) return;
        int save = canvas.save();
        cameraMask.reset();
        cameraMask.addCircle(ax, ay, radius, Path.Direction.CW);
        canvas.clipOutPath(cameraMask);
        GenieMesh.fill(vertices, meshProgress, ax, bubbleY, left, top, width, height, radius * .65f);
        paint.setAlpha(Math.round(255f * RingStateMotion.ease((p - .18f) / .22f)));
        canvas.drawBitmapMesh(snapshot, GenieMesh.COLUMNS, GenieMesh.ROWS, vertices, 0, null, 0, paint);
        canvas.restoreToCount(save);
    }
}
