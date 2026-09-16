package com.pape.punchring;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.io.FileOutputStream;

/** Render the real Canvas glyphs on Android using synthetic status only. */
public final class IconRenderTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            runOnMainSync(() -> {
                try { render(); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            result.putString("stream", "PASS: Android Canvas icon sheet, actual view entrance/hold/exit\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error + "\n");
            finish(0, result);
        }
    }

    private void render() throws Exception {
        renderGenie();
        renderConnectionMotion();
        renderQuietCharge();
        renderDroplet();
        renderDropletSizes();
        renderVpnPositions();
        StatusIconGlyph glyph = new StatusIconGlyph();
        Bitmap sheet = Bitmap.createBitmap(800, 220, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(12, 16, 23));
        String[] keys = {"charging:1", "full:1", "wifi:1", "bluetooth:1", "vpn:1",
            "hotspot:1", "temperature:warm", "battery:low", "network:warning", "battery:save"};
        for (int i = 0; i < keys.length; i++) {
            glyph.draw(canvas, keys[i], 40 + i * 80, 55, 50, Color.rgb(38, 132, 255));
        }
        android.graphics.Paint white = new android.graphics.Paint();
        white.setColor(Color.WHITE);
        canvas.drawRect(0, 110, 800, 220, white);
        for (int i = 0; i < keys.length; i++) {
            glyph.draw(canvas, keys[i], 40 + i * 80, 165, 50, Color.rgb(0, 76, 255));
        }
        save(sheet, "icon-sheet.png");

        PunchRingView view = new PunchRingView(getTargetContext());
        view.setPreviewMode(true);
        AppSettings.prefs(getTargetContext()).edit().putInt(AppSettings.ICON_HOLD_MS, 1000).apply();
        view.reloadSettings();
        view.layout(0, 0, 720, 300);
        StatusState state = new StatusState();
        state.batteryPercent = 72;
        state.cellularLevel = 4;
        state.wifiLevel = 3;
        state.eventKind = StatusState.EVENT_BLUETOOTH;
        state.eventStartedElapsed = SystemClock.elapsedRealtime();
        state.eventUntilElapsed = state.eventStartedElapsed + 10_000;
        view.updateStatus(state);
        Bitmap before = frame(view);
        save(before, "icon-before.png");
        SystemClock.sleep(650);
        Bitmap hold = frame(view);
        save(hold, "icon-hold.png");
        if (difference(before, hold) < 20) throw new AssertionError("icon did not appear");
        SystemClock.sleep(1200);
        save(frame(view), "icon-return.png");
        SystemClock.sleep(400);
        Bitmap after = frame(view);
        save(after, "icon-after.png");
        if (difference(before, after) != 0) throw new AssertionError("icon did not fully retract");
    }

    private Bitmap frame(PunchRingView view) {
        Bitmap bitmap = Bitmap.createBitmap(720, 300, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(12, 16, 23));
        view.draw(canvas);
        return bitmap;
    }

    private void renderGenie() throws Exception {
        Bitmap panel = Bitmap.createBitmap(320, 360, Bitmap.Config.ARGB_8888);
        Canvas content = new Canvas(panel);
        android.graphics.Paint brush = new android.graphics.Paint(3);
        brush.setColor(Color.rgb(20, 27, 39));
        content.drawRoundRect(0, 0, 320, 360, 22, 22, brush);
        brush.setColor(Color.WHITE);
        brush.setTextSize(22);
        content.drawText("PUNCH RING", 24, 40, brush);
        brush.setTextSize(16);
        content.drawText("DEVICE STATUS", 24, 86, brush);
        content.drawText("Battery 72% / Wi-Fi 4", 24, 119, brush);
        content.drawText("DEVICE STATUS", 24, 175, brush);
        content.drawText("BATTERY 82%", 24, 245, brush);
        brush.setColor(Color.rgb(100, 73, 183));
        content.drawRoundRect(20, 290, 300, 340, 12, 12, brush);
        GenieTransition transition = new GenieTransition();
        Bitmap sheet = Bitmap.createBitmap(2000, 470, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(45, 52, 62));
        for (int i = 0; i < 5; i++) {
            canvas.save();
            canvas.translate(i * 400, 0);
            brush.setColor(Color.BLACK);
            canvas.drawCircle(200, 25, 10, brush);
            transition.draw(canvas, panel, (i + 1) / 5f, 200, 25, 10,
                40, 90, 320, 360, Color.WHITE);
            canvas.restore();
        }
        save(sheet, "genie-phases.png");
        Bitmap finalFrame = Bitmap.createBitmap(400, 470, Bitmap.Config.ARGB_8888);
        Canvas finalCanvas = new Canvas(finalFrame);
        transition.draw(finalCanvas, panel, 1f, 200, 25, 10, 40, 90, 320, 360, Color.WHITE);
        if (finalFrame.getPixel(70, 200) != panel.getPixel(30, 110))
            throw new AssertionError("fluid genie must restore exact live panel pixels");
    }

    private void renderVpnPositions() throws Exception {
        PunchRingView view = new PunchRingView(getTargetContext());
        view.setPreviewMode(true);
        view.layout(0, 0, 720, 300);
        StatusState state = new StatusState();
        state.batteryPercent = 72;
        state.vpnActive = true;
        view.updateStatus(state);
        Bitmap sheet = Bitmap.createBitmap(1440, 600, Bitmap.Config.ARGB_8888);
        Canvas sheetCanvas = new Canvas(sheet);
        for (int corner = 0; corner < 4; corner++) {
            AppSettings.prefs(getTargetContext()).edit().putInt(AppSettings.VPN_DOT_CORNER, corner).apply();
            view.reloadSettings();
            Bitmap bitmap = Bitmap.createBitmap(720, 300, Bitmap.Config.ARGB_8888);
            VpnCanvas canvas = new VpnCanvas(bitmap);
            canvas.drawColor(Color.rgb(12, 16, 23));
            view.draw(canvas);
            if (canvas.badges != 1 || Math.signum(canvas.badgeX - canvas.cameraX) != VpnIndicator.xSign(corner)
                    || Math.signum(canvas.badgeY - canvas.cameraY) != VpnIndicator.ySign(corner))
                throw new AssertionError("wrong VPN badge corner " + corner);
            sheetCanvas.drawBitmap(bitmap, (corner % 2) * 720, (corner / 2) * 300, null);
        }
        state.vpnActive = false;
        view.updateStatus(state);
        VpnCanvas disconnected = new VpnCanvas(Bitmap.createBitmap(720, 300, Bitmap.Config.ARGB_8888));
        view.draw(disconnected);
        if (disconnected.badges != 0) throw new AssertionError("VPN badge remains after disconnect");
        AppSettings.prefs(getTargetContext()).edit().remove(AppSettings.VPN_DOT_CORNER).apply();
        save(sheet, "vpn-corners.png");
    }

    private static final class VpnCanvas extends Canvas {
        float cameraX, cameraY, badgeX, badgeY;
        int badges;
        VpnCanvas(Bitmap bitmap) { super(bitmap); }
        @Override public void drawCircle(float x, float y, float radius, android.graphics.Paint paint) {
            if (radius > 15) { cameraX = x; cameraY = y; }
            if (radius < 15 && (paint.getColor() & 0xffffff) == 0xffffff) {
                badgeX = x; badgeY = y; badges++;
            }
            super.drawCircle(x, y, radius, paint);
        }
    }

    private void renderDropletSizes() throws Exception {
        StatusDroplet droplet = new StatusDroplet();
        android.graphics.Paint brush = new android.graphics.Paint(3);
        int background = Color.rgb(15, 19, 26);
        for (float scale : new float[] {.1f, .8f, 1f, 2f, 5f}) {
            float diameter = 70f * scale;
            float destination = 624 + 47 + 9 + diameter / 2;
            float targetY = Math.max(61, diameter / 2 + 2);
            for (float phase : new float[] {0, .001f, .25f, .5f, .75f, 1f}) {
                Bitmap bitmap = Bitmap.createBitmap(1248, 600, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(background);
                brush.setColor(Color.BLACK);
                canvas.drawCircle(624, 61, 35, brush);
                droplet.draw(canvas, "bluetooth:1", 624, 61, 35, destination, targetY,
                    diameter, phase, Color.WHITE, Color.rgb(94, 45, 220));
                if (bitmap.getPixel(634, 61) != Color.BLACK) throw new AssertionError("large bubble covers camera");
                for (int x = 0; x < 1248; x++) {
                    if (bitmap.getPixel(x, 0) != background || bitmap.getPixel(x, 599) != background)
                        throw new AssertionError("bubble clipped vertically: " + scale + "/" + phase);
                }
                if (phase == 1) {
                    int minX = 1248, maxX = -1;
                    for (int y = 0; y < 600; y++) for (int x = 665; x < 1248; x++) {
                        if (bitmap.getPixel(x, y) != background) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); }
                    }
                    if (Math.abs(maxX - minX + 1 - diameter) > 2)
                        throw new AssertionError("wrong held diameter: " + scale);
                    if (scale == 5) save(bitmap, "icon-size-5x.png");
                }
                bitmap.recycle();
            }
        }
    }

    private void renderQuietCharge() throws Exception {
        PunchRingView view = new PunchRingView(getTargetContext());
        view.setPreviewMode(true);
        view.reloadSettings();
        view.layout(0, 0, 720, 300);
        StatusState state = new StatusState();
        state.batteryPercent = 72;
        state.charging = true;
        state.fastCharging = true;
        state.plugged = true;
        view.updateStatus(state);
        Bitmap start = frame(view);
        SystemClock.sleep(2000);
        Bitmap breathed = frame(view);
        int mintPixels = 0, maxDelta = 0;
        for (int y = 0; y < start.getHeight(); y++) {
            for (int x = 0; x < start.getWidth(); x++) {
                int a = start.getPixel(x, y), b = breathed.getPixel(x, y);
                if (Color.green(a) > 200 && Color.blue(a) > 170 && Color.red(a) < 150) mintPixels++;
                maxDelta = Math.max(maxDelta, Math.abs(Color.red(a) - Color.red(b)));
                maxDelta = Math.max(maxDelta, Math.abs(Color.green(a) - Color.green(b)));
                maxDelta = Math.max(maxDelta, Math.abs(Color.blue(a) - Color.blue(b)));
            }
        }
        if (mintPixels < 20) throw new AssertionError("fast charging must be mint");
        if (maxDelta == 0 || maxDelta > 22) throw new AssertionError("charging must breathe quietly without changing arc length: " + maxDelta);
        save(start, "fast-charge-mint.png");
    }

    private void renderDroplet() throws Exception {
        StatusDroplet droplet = new StatusDroplet();
        android.graphics.Paint brush = new android.graphics.Paint(3);
        Bitmap sheet = Bitmap.createBitmap(1920, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        float[] phases = {0, .25f, .45f, .60f, .72f, .86f, 1, 0};
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < phases.length; i++) {
                canvas.save();
                canvas.translate(i * 240, row * 200);
                brush.setColor(row == 0 ? Color.rgb(15, 19, 26) : Color.rgb(240, 241, 244));
                canvas.drawRect(0, 0, 240, 200, brush);
                brush.setColor(Color.BLACK);
                canvas.drawCircle(55, 85, 24, brush);
                droplet.draw(canvas, "bluetooth:1", 55, 85, 24, 132, 40, phases[i],
                    row == 0 ? Color.WHITE : Color.BLACK,
                    row == 0 ? Color.rgb(94, 45, 220) : Color.rgb(191, 124, 255));
                brush.setColor(row == 0 ? Color.WHITE : Color.BLACK);
                brush.setTextSize(15);
                canvas.drawText("" + phases[i], 45, 165, brush);
                canvas.restore();
                if (sheet.getPixel(i * 240 + 65, row * 200 + 85) != Color.BLACK)
                    throw new AssertionError("camera must remain black");
            }
        }
        if (sheet.getPixel(6 * 240 + 145, 85) != Color.WHITE)
            throw new AssertionError("dark background must use white bubble");
        if (sheet.getPixel(6 * 240 + 145, 285) != Color.BLACK)
            throw new AssertionError("light background must use black bubble");
        int transitionGray = Color.red(sheet.getPixel(2 * 240 + 90, 85));
        if (transitionGray <= 0 || transitionGray >= 255)
            throw new AssertionError("attached white droplet must have a black-to-white gradient");
        save(sheet, "droplet-phases.png");
    }

    private void renderConnectionMotion() throws Exception {
        PunchRingView view = new PunchRingView(getTargetContext());
        view.setPreviewMode(true);
        view.reloadSettings();
        view.layout(0, 0, 720, 300);
        StatusState state = new StatusState();
        state.batteryPercent = 72;
        state.cellularLevel = 4;
        state.wifiLevel = 4;
        view.updateStatus(state);
        Bitmap initial = frame(view);
        view.previewConnection(1);
        frame(view);
        // Observe the actual production drawCircle radius through the first pass.
        Bitmap probe = Bitmap.createBitmap(720, 300, Bitmap.Config.ARGB_8888);
        RadiusCanvas radiusCanvas = new RadiusCanvas(probe);
        float dotRadius = 3.2f * getTargetContext().getResources().getDisplayMetrics().density * .78f;
        boolean shrank = false;
        long probeStarted = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - probeStarted < 720) {
            radiusCanvas.smallest = Float.MAX_VALUE;
            view.draw(radiusCanvas);
            if (radiusCanvas.smallest > .001f && radiusCanvas.smallest < dotRadius * .65f) shrank = true;
            SystemClock.sleep(4);
        }
        if (!shrank) throw new AssertionError("occluded dots must shrink, not only fade");
        Bitmap peak = frame(view);
        save(peak, "spin-peak.png");
        if (difference(initial, peak) < 50) throw new AssertionError("spin did not render");
        boolean grew = false;
        while (SystemClock.elapsedRealtime() - probeStarted < 1510) {
            radiusCanvas.smallest = Float.MAX_VALUE;
            view.draw(radiusCanvas);
            if (radiusCanvas.smallest > .001f && radiusCanvas.smallest < dotRadius * .65f) grew = true;
            SystemClock.sleep(4);
        }
        probe.recycle();
        if (!grew) throw new AssertionError("returning dots must grow before reaching full size");
        if (difference(initial, frame(view)) != 0) throw new AssertionError("spin failed to restore ring");
        state.wifiLevel = 1;
        view.updateStatus(state);
        SystemClock.sleep(400);
        save(frame(view), "dots-transition.png");
        SystemClock.sleep(450);
        Bitmap settled = frame(view);
        SystemClock.sleep(50);
        if (difference(settled, frame(view)) != 0) throw new AssertionError("dots did not settle");
    }

    private static final class RadiusCanvas extends Canvas {
        float smallest = Float.MAX_VALUE;
        RadiusCanvas(Bitmap bitmap) { super(bitmap); }
        @Override public void drawCircle(float cx, float cy, float radius, android.graphics.Paint paint) {
            if (radius < 15f) smallest = Math.min(smallest, radius);
            super.drawCircle(cx, cy, radius, paint);
        }
    }

    private int difference(Bitmap a, Bitmap b) {
        int count = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) if (a.getPixel(x, y) != b.getPixel(x, y)) count++;
        }
        return count;
    }

    private void save(Bitmap bitmap, String name) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getFilesDir(), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }
}
