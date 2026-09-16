package com.pape.punchring;

/** Maps a panel rectangle into a curved neck anchored at the camera hole. */
final class GenieMesh {
    static final int COLUMNS = 16;
    static final int ROWS = 32;

    static void fill(float[] vertices, float progress, float anchorX, float anchorY,
            float left, float top, float width, float height, float neckWidth) {
        float p = clamp(progress);
        float head = p * p * p;
        float foot = 1f - (1f - p) * (1f - p);
        float center = left + width / 2f;
        int index = 0;
        for (int row = 0; row <= ROWS; row++) {
            float v = row / (float) ROWS;
            float delay = 0.30f * (1f - v);
            float spread = smooth(clamp((p - delay) / (1f - delay)));
            float neck = neckWidth * (float) Math.sin(Math.PI * p) * (1f - v);
            float rowWidth = width * spread + neck * (1f - spread);
            float travel = head + (foot - head) * v;
            float xCenter = anchorX + (center - anchorX) * travel;
            float yTop = anchorY + (top - anchorY) * head;
            float yBottom = anchorY + (top + height - anchorY) * foot;
            float y = yTop + (yBottom - yTop) * v;
            for (int column = 0; column <= COLUMNS; column++) {
                vertices[index++] = xCenter + (column / (float) COLUMNS - 0.5f) * rowWidth;
                vertices[index++] = y;
            }
        }
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float smooth(float value) { return value * value * (3f - 2f * value); }
}
