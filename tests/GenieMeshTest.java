package com.pape.punchring;

public final class GenieMeshTest {
    public static void main(String[] args) {
        float[] mesh = new float[(GenieMesh.COLUMNS + 1) * (GenieMesh.ROWS + 1) * 2];
        for (float anchor : new float[] {320f, 1100f}) {
            for (int step = 0; step <= 100; step++) {
                float p = step / 100f;
                GenieMesh.fill(mesh, p, anchor, 30f, 100f, 100f, 440f, 680f, 20f);
                for (int row = 0; row <= GenieMesh.ROWS; row++) {
                    for (int col = 0; col <= GenieMesh.COLUMNS; col++) {
                        int i = (row * (GenieMesh.COLUMNS + 1) + col) * 2;
                        check(Float.isFinite(mesh[i]) && Float.isFinite(mesh[i + 1]), "finite");
                        if (col > 0) check(mesh[i] >= mesh[i - 2], "no horizontal fold");
                        if (row > 0) check(mesh[i + 1] >= mesh[i + 1 - (GenieMesh.COLUMNS + 1) * 2], "no vertical fold");
                        if (step == 0) check(mesh[i] == anchor && mesh[i + 1] == 30f, "collapsed at punch hole");
                        if (step == 100) {
                            check(Math.abs(mesh[i] - (100f + 440f * col / GenieMesh.COLUMNS)) < .001f, "exact final x");
                            check(Math.abs(mesh[i + 1] - (100f + 680f * row / GenieMesh.ROWS)) < .001f, "exact final y");
                        }
                    }
                }
            }
        }
        System.out.println("PASS: cover/inner origins, 101 frames each, no inverted mesh, exact endpoints");
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
