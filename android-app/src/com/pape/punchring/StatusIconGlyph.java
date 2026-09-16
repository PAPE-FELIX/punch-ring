package com.pape.punchring;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Font-independent, small-size line icons in a normalized 24-unit square. */
final class StatusIconGlyph {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF arc = new RectF();

    void draw(Canvas canvas, String key, float x, float y, float size, int color) {
        int save = canvas.save();
        canvas.translate(x - size / 2f, y - size / 2f);
        canvas.scale(size / 24f, size / 24f);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (key.startsWith("temperature")) {
            line(canvas, 10, 14, 10, 5, 12, 3, 14, 5, 14, 14);
            arc.set(8, 12, 16, 21);
            canvas.drawArc(arc, -55, 290, false, paint);
            line(canvas, 12, 9, 12, 17);
            line(canvas, 17, 7, 20, 7);
        } else if (key.startsWith("battery") || key.startsWith("full")) {
            canvas.drawRoundRect(5, 5, 19, 21, 3, 3, paint);
            line(canvas, 10, 2, 14, 2);
            if (key.startsWith("full")) line(canvas, 8, 13, 11, 16, 16, 10);
            else if (key.contains("save")) {
                path.reset();
                path.moveTo(9, 16);
                path.cubicTo(7, 10, 12, 9, 16, 9);
                path.cubicTo(16, 15, 13, 18, 9, 16);
                canvas.drawPath(path, paint);
                line(canvas, 9, 17, 13, 13);
            } else {
                line(canvas, 12, 9, 12, 13);
                canvas.drawPoint(12, 17, paint);
            }
        } else if (key.startsWith("charging")) {
            line(canvas, 14, 2, 5, 14, 11, 14, 10, 22, 19, 10, 13, 10, 14, 2);
        } else if (key.startsWith("bluetooth")) {
            line(canvas, 7, 7, 17, 17, 12, 22, 12, 2, 17, 7, 7, 17);
        } else if (key.startsWith("vpn")) {
            line(canvas, 12, 2, 20, 5, 19, 14, 16, 19, 12, 22, 8, 19, 5, 14, 4, 5, 12, 2);
            line(canvas, 9, 12, 11, 15, 16, 9);
        } else if (key.startsWith("hotspot")) {
            arc.set(2, 2, 22, 22);
            canvas.drawArc(arc, 130, 100, false, paint);
            canvas.drawArc(arc, -50, 100, false, paint);
            arc.set(6, 6, 18, 18);
            canvas.drawArc(arc, 135, 90, false, paint);
            canvas.drawArc(arc, -45, 90, false, paint);
            canvas.drawCircle(12, 12, 1.5f, paint);
        } else {
            arc.set(0, 5, 24, 27);
            canvas.drawArc(arc, 220, 100, false, paint);
            arc.set(5, 10, 19, 24);
            canvas.drawArc(arc, 220, 100, false, paint);
            canvas.drawPoint(12, 20, paint);
            if (key.startsWith("network")) line(canvas, 3, 3, 21, 21);
        }
        canvas.restoreToCount(save);
    }

    private void line(Canvas canvas, float... points) {
        path.reset();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        canvas.drawPath(path, paint);
    }
}
