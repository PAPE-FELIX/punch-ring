package com.pape.punchring;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.Arrays;

public final class BackgroundContrastService extends AccessibilityService {
    private static final long SAMPLE_INTERVAL_MS = 1300L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean screenshotInFlight;
    private WindowManager windowManager;
    private RingTouchView touchView;
    private WindowManager.LayoutParams touchParams;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            sampleBackground();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = getSystemService(WindowManager.class);
        String foreground = AppSettings.prefs(this).getString(
            AppSettings.FOREGROUND_PACKAGE, "");
        RingTouchOverlayController.setCameraForeground(isCameraPackage(foreground));
        RingTouchOverlayController.attach(this);
        handler.removeCallbacks(sampler);
        handler.post(sampler);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && event.getPackageName() != null) {
            String packageName = event.getPackageName().toString();
            if (!getPackageName().equals(packageName)
                    && !"com.android.systemui".equals(packageName)) {
                RingTouchOverlayController.setCameraForeground(isCameraPackage(packageName));
                String previous = AppSettings.prefs(this).getString(
                    AppSettings.FOREGROUND_PACKAGE, "");
                if (!packageName.equals(previous)) {
                    AppSettings.prefs(this).edit()
                        .putString(AppSettings.FOREGROUND_PACKAGE, packageName).apply();
                }
            }
        }
        handler.removeCallbacks(sampler);
        handler.postDelayed(sampler, 180L);
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sampler);
        RingTouchOverlayController.detach(this);
        if (touchView != null && windowManager != null) {
            try { windowManager.removeView(touchView); } catch (RuntimeException ignored) {}
            touchView = null;
            touchParams = null;
        }
        super.onDestroy();
    }

    void updateRingTouchTarget(float centerX, float centerY, float diameter,
            boolean visible) {
        if (windowManager == null) return;
        int size = Math.max(1, Math.round(diameter));
        if (touchView == null) {
            touchView = new RingTouchView(this, new RingTouchView.Listener() {
                @Override public void onRingTap() { openSelfieCamera(); }
                @Override public void onRingLongPress() {
                    // Long-press opens the device status panel by default; users can switch it
                    // to the rear camera, where the ring turns into a smiling face.
                    String action = getSharedPreferences(AppSettings.PREFS, MODE_PRIVATE)
                        .getString(AppSettings.LONG_PRESS_ACTION, AppSettings.DEFAULT_LONG_PRESS_ACTION);
                    if (AppSettings.LONG_PRESS_CAMERA.equals(action)) openRearCamera();
                    else openRingPanel();
                }
                @Override public void onRingPressed(boolean pressed) {
                    RingTouchOverlayController.setRingPressed(pressed);
                }
            });
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            touchParams = new WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
            touchParams.gravity = Gravity.TOP | Gravity.START;
            touchParams.setTitle("Punch Ring selfie touch target");
            if (Build.VERSION.SDK_INT >= 28) {
                touchParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            }
            try {
                windowManager.addView(touchView, touchParams);
            } catch (RuntimeException failure) {
                touchView = null;
                touchParams = null;
                return;
            }
        }

        touchParams.width = size;
        touchParams.height = size;
        touchParams.x = Math.round(centerX - size / 2f);
        touchParams.y = Math.round(centerY - size / 2f);
        if (visible) {
            touchParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            touchView.setVisibility(View.VISIBLE);
        } else {
            touchParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            touchView.setVisibility(View.GONE);
        }
        try {
            windowManager.updateViewLayout(touchView, touchParams);
        } catch (RuntimeException ignored) {
            // The service may be disconnecting while a final geometry callback is queued.
        }
    }

    /** Rear camera for the smiling-face mode. Mirrors openSelfieCamera with the back lens. */
    private void openRearCamera() {
        RingTouchOverlayController.setCameraForeground(true);
        Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .setPackage("com.sec.android.app.camera")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("android.intent.extras.CAMERA_FACING", 0)
            .putExtra("android.intent.extras.LENS_FACING_BACK", 1)
            .putExtra("android.intent.extra.USE_FRONT_CAMERA", false);
        try {
            if (camera.resolveActivity(getPackageManager()) == null) camera.setPackage(null);
            startActivity(camera);
            return;
        } catch (RuntimeException ignored) {
            // Fall through to the generic launcher below.
        }
        Intent fallback = getPackageManager().getLaunchIntentForPackage("com.sec.android.app.camera");
        if (fallback == null) fallback = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(fallback);
        } catch (RuntimeException failure) {
            RingTouchOverlayController.setCameraForeground(false);
            Toast.makeText(this, UiText.get(this, "카메라를 열 수 없습니다.",
                "Could not open the camera."), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSelfieCamera() {
        RingTouchOverlayController.setCameraForeground(true);
        Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .setPackage("com.sec.android.app.camera")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("android.intent.extras.CAMERA_FACING", 1)
            .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            .putExtra("com.google.assistant.extra.USE_FRONT_CAMERA", true)
            .putExtra("com.sec.android.app.camera.extra.USE_FRONT_CAMERA", true);
        try {
            if (camera.resolveActivity(getPackageManager()) == null) camera.setPackage(null);
            startActivity(camera);
        } catch (RuntimeException firstFailure) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(
                "com.sec.android.app.camera");
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("android.intent.extras.CAMERA_FACING", 1)
                    .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                    .putExtra("com.google.assistant.extra.USE_FRONT_CAMERA", true)
                    .putExtra("com.sec.android.app.camera.extra.USE_FRONT_CAMERA", true);
                try {
                    startActivity(fallback);
                    return;
                } catch (RuntimeException ignored) {
                    // Report the failure below without crashing the accessibility service.
                }
            }
            RingTouchOverlayController.setCameraForeground(false);
            Toast.makeText(this, UiText.get(this, "셀피 카메라를 열 수 없습니다.",
                "Could not open the selfie camera."), Toast.LENGTH_SHORT).show();
        }
    }

    private void openRingPanel() {
        handler.postDelayed(() -> RingTouchOverlayController.setRingPressed(false), 90L);
        RingTouchOverlayController.setPanelForeground(true);
        Intent panel = new Intent(this, RingPanelActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(panel);
        } catch (RuntimeException failure) {
            RingTouchOverlayController.setPanelForeground(false);
            Toast.makeText(this, UiText.get(this, "Punch Ring 패널을 열 수 없습니다.",
                "Could not open the Punch Ring panel."), Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean isCameraPackage(String packageName) {
        return packageName != null && (packageName.contains("camera")
            || packageName.contains("com.sec.android.app.camera"));
    }

    private void sampleBackground() {
        if (!AppSettings.prefs(this).getBoolean(
            AppSettings.AUTO_CONTRAST, AppSettings.DEFAULT_AUTO_CONTRAST)) {
            scheduleNext();
            return;
        }
        if (screenshotInFlight) return;
        screenshotInFlight = true;
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult screenshot) {
                try {
                    Boolean light = calculateLightBackground(screenshot);
                    if (light != null) publishIfChanged(light);
                } finally {
                    screenshotInFlight = false;
                    scheduleNext();
                }
            }

            @Override
            public void onFailure(int errorCode) {
                screenshotInFlight = false;
                scheduleNext();
            }
        });
    }

    private Boolean calculateLightBackground(ScreenshotResult screenshot) {
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        Bitmap hardware = null;
        Bitmap crop = null;
        Bitmap scaled = null;
        Bitmap software = null;
        try {
            hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
            if (hardware == null || hardware.getWidth() < 100 || hardware.getHeight() < 60) return null;

            int width = hardware.getWidth();
            int height = hardware.getHeight();
            int centerX = width > 2000 ? Math.round(width * 0.7606f) : width / 2;
            int halfSpan = Math.max(90, Math.round(width * 0.13f));
            int left = Math.max(0, centerX - halfSpan);
            int right = Math.min(width, centerX + halfSpan);
            int sampleHeight = Math.min(height, Math.max(72, Math.round(width * 0.09f)));
            if (right - left < 32) return null;

            crop = Bitmap.createBitmap(hardware, left, 0, right - left, sampleHeight);
            scaled = Bitmap.createScaledBitmap(crop, 64, 16, true);
            software = scaled.getConfig() == Bitmap.Config.HARDWARE
                ? scaled.copy(Bitmap.Config.ARGB_8888, false) : scaled;
            if (software == null) return null;

            float[] luminances = new float[64 * 16];
            int count = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 64; x++) {
                    // Ignore the center where the physical hole and Punch Ring live.
                    if (x >= 23 && x <= 41) continue;
                    int pixel = software.getPixel(x, y);
                    float luminance = (0.2126f * Color.red(pixel)
                        + 0.7152f * Color.green(pixel)
                        + 0.0722f * Color.blue(pixel)) / 255f;
                    luminances[count++] = luminance;
                }
            }
            if (count == 0) return null;
            Arrays.sort(luminances, 0, count);
            float median = luminances[count / 2];
            boolean wasLight = AppSettings.prefs(this).getBoolean(
                AppSettings.BACKGROUND_IS_LIGHT, false);
            // Hysteresis prevents rapid theme flicker around middle gray.
            return wasLight ? median >= 0.48f : median >= 0.60f;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            if (software != null && software != scaled) software.recycle();
            if (scaled != null) scaled.recycle();
            if (crop != null) crop.recycle();
            if (hardware != null) hardware.recycle();
            buffer.close();
        }
    }

    private void publishIfChanged(boolean light) {
        boolean previous = AppSettings.prefs(this).getBoolean(
            AppSettings.BACKGROUND_IS_LIGHT, false);
        if (previous == light) return;
        AppSettings.prefs(this).edit()
            .putBoolean(AppSettings.BACKGROUND_IS_LIGHT, light)
            .apply();
    }

    private void scheduleNext() {
        handler.removeCallbacks(sampler);
        handler.postDelayed(sampler, SAMPLE_INTERVAL_MS);
    }
}
