package com.pape.punchring;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

final class PunchRingView extends View {
    interface GeometryListener {
        void onRingGeometry(float centerX, float centerY, float touchDiameter, boolean visible);
    }

    static final int COLOR_LIME = Color.rgb(185, 246, 118);
    static final int COLOR_SKY = Color.rgb(38, 132, 255);
    static final int COLOR_ORANGE = Color.rgb(255, 159, 10);
    static final int COLOR_RED = Color.rgb(255, 69, 58);
    static final int COLOR_NORMAL = Color.rgb(245, 247, 250);

    private final Paint batteryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cameraPreviewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final StatusIconMotion iconMotion = new StatusIconMotion();
    private final RingStateMotion stateMotion = new RingStateMotion();
    private final BatteryFillMotion batteryFillMotion = new BatteryFillMotion();
    private final MotionClock graphClock = new MotionClock();
    private final StatusDroplet statusDroplet = new StatusDroplet();
    private final StatusHaptic statusHaptic = new StatusHaptic();
    private int iconSizePercent = 80;
    private int iconGapDp = 4;
    private int iconHoldMs = 2200;
    private int vpnDotCorner = AppSettings.DEFAULT_VPN_DOT_CORNER;
    private StatusState state = new StatusState();
    private boolean previewMode;
    private int batteryDiameterDp = AppSettings.DEFAULT_DIAMETER_DP;
    private int dotDiameterDp = AppSettings.DEFAULT_DOT_DIAMETER_DP;
    private int offsetXDp = AppSettings.DEFAULT_OFFSET_X_DP;
    private int offsetYDp = AppSettings.DEFAULT_OFFSET_Y_DP;
    private int strokeTenthsDp = AppSettings.DEFAULT_STROKE_TENTHS_DP;
    private boolean showLayoutGuide;
    private boolean autoContrast = AppSettings.DEFAULT_AUTO_CONTRAST;
    private boolean smartHide = AppSettings.DEFAULT_SMART_HIDE;
    /** "camera" turns the ring into a smiling face while the camera is open. */
    private String longPressAction = AppSettings.DEFAULT_LONG_PRESS_ACTION;
    private long smileChangedAt;
    private boolean smileShown;
    private boolean previewSmile;
    private boolean dimOnScreenOff = AppSettings.DEFAULT_DIM_ON_SCREEN_OFF;
    private boolean burnInProtection = AppSettings.DEFAULT_BURN_IN_PROTECTION;
    private int stylePreset = AppSettings.DEFAULT_STYLE_PRESET;
    private int lowBatteryPercent = AppSettings.DEFAULT_LOW_BATTERY_PERCENT;
    // Smart-hide fade: fullscreen/camera hide and show ease in and out instead of popping.
    private boolean hideShown = false;
    private boolean forceHidden = false;   // set by OverlayService while a permission/installer screen is in front
    private long hideChangedAt = 0L;
    private int animationSpeedPercent = AppSettings.DEFAULT_ANIMATION_SPEED_PERCENT;
    private int thermalThresholdC = AppSettings.DEFAULT_THERMAL_THRESHOLD_C;
    private List<String> priorityOrder;
    private String foregroundPackage = "";
    private Boolean loadedInnerProfile;
    private boolean themeInitialized;
    private float lightThemeProgress;
    private ValueAnimator themeAnimator;
    private ValueAnimator pressAnimator;
    private float pressProgress;
    private GeometryListener geometryListener;
    private float publishedCenterX = Float.NaN;
    private float publishedCenterY = Float.NaN;
    private float publishedTouchDiameter = Float.NaN;
    private boolean publishedVisible;
    private boolean geometryPublished;

    PunchRingView(Context context) {
        super(context);
        batteryPaint.setStyle(Paint.Style.STROKE);
        batteryPaint.setStrokeCap(Paint.Cap.ROUND);
        cameraPreviewPaint.setColor(Color.rgb(3, 4, 5));
        guidePaint.setStyle(Paint.Style.STROKE);
        setWillNotDraw(false);
    }

    void setPreviewMode(boolean previewMode) {
        this.previewMode = previewMode;
        invalidate();
    }

    void setGeometryListener(GeometryListener listener) {
        geometryListener = listener;
        geometryPublished = false;
        invalidate();
    }

    void updateStatus(StatusState state) {
        if (previewMode) iconMotion.reset();
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        stateMotion.update(state, graphClock.now(SystemClock.elapsedRealtime()), animate);
        if (!previewMode) performStateHaptic(statusHaptic.observe(state));
        this.state = state.copy();
        invalidate();
    }

    void setPressTarget(boolean pressed) {
        float target = pressed ? 1f : 0f;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            pressProgress = target;
            invalidate();
            return;
        }
        if (pressAnimator != null) pressAnimator.cancel();
        pressAnimator = ValueAnimator.ofFloat(pressProgress, target);
        pressAnimator.setDuration(pressed ? 110L : 210L);
        pressAnimator.setInterpolator(new android.view.animation.PathInterpolator(.22f, 1f, .36f, 1f));
        pressAnimator.addUpdateListener(value -> {
            pressProgress = (float) value.getAnimatedValue();
            invalidate();
        });
        pressAnimator.start();
    }

    private void performStateHaptic(int kind) {
        if (kind == StatusHaptic.CHARGE) {
            performHapticFeedback(HapticFeedbackConstants.GESTURE_START);
            postDelayed(() -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK), 95L);
        } else if (kind == StatusHaptic.WARNING) {
            performHapticFeedback(HapticFeedbackConstants.REJECT);
        } else if (kind == StatusHaptic.CONNECT) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        }
    }

    void previewConnection(int kind) {
        stateMotion.previewSpin(kind);
        invalidate();
    }

    void reloadSettings() {
        SharedPreferences prefs = AppSettings.prefs(getContext());
        boolean inner = AppSettings.isInnerDisplay(getContext());
        loadedInnerProfile = inner;
        batteryDiameterDp = prefs.getInt(AppSettings.profileKey(AppSettings.DIAMETER_DP, inner),
            AppSettings.DEFAULT_DIAMETER_DP);
        dotDiameterDp = prefs.getBoolean(AppSettings.profileKey(AppSettings.SYNC_DIAMETERS, inner),
            AppSettings.DEFAULT_SYNC_DIAMETERS)
            ? batteryDiameterDp
            : prefs.getInt(AppSettings.profileKey(AppSettings.DOT_DIAMETER_DP, inner),
                AppSettings.DEFAULT_DOT_DIAMETER_DP);
        offsetXDp = prefs.getInt(AppSettings.profileKey(AppSettings.OFFSET_X_DP, inner),
            AppSettings.DEFAULT_OFFSET_X_DP);
        offsetYDp = prefs.getInt(AppSettings.profileKey(AppSettings.OFFSET_Y_DP, inner),
            AppSettings.DEFAULT_OFFSET_Y_DP);
        strokeTenthsDp = prefs.getInt(AppSettings.profileKey(AppSettings.STROKE_TENTHS_DP, inner),
            AppSettings.DEFAULT_STROKE_TENTHS_DP);
        showLayoutGuide = prefs.getBoolean(
            AppSettings.SHOW_LAYOUT_GUIDE, AppSettings.DEFAULT_SHOW_LAYOUT_GUIDE);
        autoContrast = prefs.getBoolean(
            AppSettings.AUTO_CONTRAST, AppSettings.DEFAULT_AUTO_CONTRAST);
        longPressAction = prefs.getString(
            AppSettings.LONG_PRESS_ACTION, AppSettings.DEFAULT_LONG_PRESS_ACTION);
        smartHide = prefs.getBoolean(AppSettings.SMART_HIDE, AppSettings.DEFAULT_SMART_HIDE);
        dimOnScreenOff = prefs.getBoolean(
            AppSettings.DIM_ON_SCREEN_OFF, AppSettings.DEFAULT_DIM_ON_SCREEN_OFF);
        burnInProtection = prefs.getBoolean(
            AppSettings.BURN_IN_PROTECTION, AppSettings.DEFAULT_BURN_IN_PROTECTION);
        stylePreset = prefs.getInt(AppSettings.STYLE_PRESET, AppSettings.DEFAULT_STYLE_PRESET);
        lowBatteryPercent = Math.max(5, Math.min(30, prefs.getInt(
            AppSettings.LOW_BATTERY_PERCENT, AppSettings.DEFAULT_LOW_BATTERY_PERCENT)));
        statusHaptic.lowBatteryPercent = lowBatteryPercent;
        animationSpeedPercent = Math.max(10, Math.min(500, prefs.getInt(
            AppSettings.ANIMATION_SPEED_PERCENT, AppSettings.DEFAULT_ANIMATION_SPEED_PERCENT)));
        graphClock.setSpeed(SystemClock.elapsedRealtime(), animationSpeedPercent);
        iconSizePercent = Math.max(10, Math.min(500, prefs.getInt(AppSettings.ICON_SIZE_PERCENT, 80)));
        iconGapDp = Math.max(2, Math.min(14, prefs.getInt(AppSettings.ICON_GAP_DP, 4)));
        iconHoldMs = Math.max(1000, Math.min(10000, prefs.getInt(AppSettings.ICON_HOLD_MS, 2200)));
        vpnDotCorner = Math.max(0, Math.min(3, prefs.getInt(
            AppSettings.VPN_DOT_CORNER, AppSettings.DEFAULT_VPN_DOT_CORNER)));
        thermalThresholdC = prefs.getInt(
            AppSettings.THERMAL_THRESHOLD_C, AppSettings.DEFAULT_THERMAL_THRESHOLD_C);
        priorityOrder = AppSettings.priorityOrder(getContext());
        foregroundPackage = prefs.getString(AppSettings.FOREGROUND_PACKAGE, "");
        boolean lightBackground = autoContrast
            && prefs.getBoolean(AppSettings.BACKGROUND_IS_LIGHT, false);
        setLightBackground(lightBackground);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean currentInner = AppSettings.isInnerDisplay(getContext());
        if (loadedInnerProfile == null || loadedInnerProfile != currentInner) reloadSettings();

        float density = getResources().getDisplayMetrics().density;
        float[] center = previewMode ? new float[] { getWidth() / 2f, getHeight() * 0.42f }
                                     : resolveCutoutCenter();
        float cx = center[0] + offsetXDp * density;
        float cy = center[1] + offsetYDp * density;
        if (!previewMode && burnInProtection) {
            int[] shift = burnInShift();
            cx += shift[0];
            cy += shift[1];
        }
        float pressScale = 1f - .06f * pressProgress;
        float batteryRadius = batteryDiameterDp * density / 2f * pressScale;
        float dotOrbit = dotDiameterDp * density / 2f * pressScale;
        float stroke = strokeTenthsDp * density / 10f * (1f + .16f * pressProgress);
        boolean cameraOpen = previewMode ? previewSmile : isCameraForeground();
        boolean smileMode = previewMode || AppSettings.LONG_PRESS_CAMERA.equals(longPressAction);
        if (smileShown != (cameraOpen && smileMode)) {
            smileShown = cameraOpen && smileMode;
            smileChangedAt = SystemClock.elapsedRealtime();
        }
        float smile = smileProgress();
        // In smile mode the ring stays on top of the camera instead of hiding.
        boolean hidden = !previewMode && (forceHidden || (smartHide && shouldHideForContext() && smile <= 0f));
        if (!previewMode) {
            float visualDiameter = Math.max(batteryDiameterDp, dotDiameterDp) * density;
            // The physical camera hole has no touch sensor. Keep the artwork unchanged,
            // but extend the invisible target into the live panel surrounding the hole.
            float touchDiameter = Math.max(64f * density, visualDiameter + 28f * density);
            publishGeometry(cx, cy, touchDiameter, !hidden);
        }
        iconMotion.observe(attentionKeys(), !hidden && (previewMode || state.screenInteractive));
        if (hideShown != hidden) { hideShown = hidden; hideChangedAt = SystemClock.elapsedRealtime(); }
        float shown = shownProgress();
        if (shown <= 0f) return;
        int hideLayer = -1;
        if (shown < 1f) {
            hideLayer = canvas.saveLayerAlpha(0f, 0f, getWidth(), getHeight(), Math.round(255f * shown));
            float scale = 0.72f + 0.28f * shown;
            canvas.scale(scale, scale, cx, cy);
        }

        float dotRadius = Math.min(
            dotOrbit * 0.14f,
            Math.max(1.2f * density, stroke * 0.78f));

        int saveLayer = -1;
        if (!previewMode && dimOnScreenOff && !state.screenInteractive) {
            saveLayer = canvas.saveLayerAlpha(0f, 0f, getWidth(), getHeight(), 72);
        }

        if (previewMode) {
            float previewHoleRadius = resolveHoleDiameter(density) / 2f;
            canvas.drawCircle(cx, cy, previewHoleRadius, cameraPreviewPaint);
        }

        boolean animate = ValueAnimator.areAnimatorsEnabled();
        // The event bubble remains full-strength while the ring and dots recede.
        float eventFocus = drawStatusIcon(canvas, cx, cy,
            Math.max(batteryRadius, dotOrbit), stroke, density, animate);
        int focusLayer = eventFocus <= 0f ? -1 : canvas.saveLayerAlpha(
            0f, 0f, getWidth(), getHeight(), Math.round(255f - 153f * eventFocus));
        // Smile mode: spin the whole face with the screen, slide the arc down into a mouth.
        int smileLayer = -1;
        if (smile > 0f) {
            smileLayer = canvas.save();
            float spin = faceRotation();
            if (spin != 0f) canvas.rotate(spin, cx, cy);
        }
        batteryPaint.setStrokeWidth(stroke);
        batteryPaint.setShader(null);
        int brightTrack = Color.argb(stylePreset == 2 ? 34 : 62, 245, 247, 250);
        int darkTrack = Color.argb(82, 0, 0, 0);
        int trackColor = blend(brightTrack, darkTrack, lightThemeProgress);
        batteryPaint.setColor(trackColor);
        // 웃는 얼굴이어도 호는 제자리 — 원래 링 테두리 위에서 180도만 돈다 (형 지시 2026-09-16)
        RectF batteryBounds = new RectF(
            cx - batteryRadius, cy - batteryRadius,
            cx + batteryRadius, cy + batteryRadius);
        if (smile < 1f) {
            // 링 배경(트랙)은 원래 반투명(알파 34~82)이다. 예전엔 setAlpha(255) 로 덮어써서 불투명한 흰 링이 됐다 (형 신고 2026-09-18).
            batteryPaint.setAlpha(Math.round(Color.alpha(trackColor) * (1f - smile)));
            canvas.drawArc(batteryBounds, 180f, 180f, false, batteryPaint);
        }

        float targetSweep = 180f * Math.max(0, Math.min(100, state.batteryPercent)) / 100f;
        long motionNow = graphClock.now(SystemClock.elapsedRealtime());
        int spinKind = stateMotion.advance(motionNow);
        float visibleSweep = targetSweep;
        int batteryColor = resolveBatteryColor();
        float chargeFill = batteryFillMotion.frame(motionNow, state.fastCharging,
            spinKind != 0, 100);
        float accent = spinKind == 0 ? 0f : stateMotion.accent(motionNow);
        int eventColor = spinKind == 1 ? themed(wifiBright(), wifiSaturated())
            : spinKind == 3 ? themed(cellularBright(), cellularSaturated()) : batteryColor;

        int arcColor = blend(batteryColor, eventColor, accent);
        // Fast charging keeps the actual battery length; only a quiet mint breath.
        if (state.fastCharging) arcColor = withAlpha(arcColor, Math.round(255f * chargeFill));
        batteryPaint.setColor(arcColor);
        if (smile > 0f) {
            // Top half (180°) rotates to the bottom half (0°) while filling out to a full sweep.
            float mouthStart = 180f + 180f * smile;
            float mouthSweep = visibleSweep + (180f - visibleSweep) * smile;
            drawBatteryArc(canvas, batteryBounds, mouthStart, mouthSweep);
        } else if (spinKind != 0) {
            drawBatteryArc(canvas, batteryBounds, stateMotion.startAngle(visibleSweep, motionNow),
                stateMotion.sweep(visibleSweep, motionNow));
        } else {
            drawBatteryArc(canvas, batteryBounds, 180f, visibleSweep);
        }
        batteryPaint.setShader(null);

        // The four dot positions share the lower circular orbit instead of a straight row.
        // Wi-Fi fully covers the cellular color at the same coordinates and size.
        dotOrbit *= 1.02f;
        float[] dotAngles = new float[] { 145f, 110f, 70f, 35f };
        /* 웃는 얼굴에서는 가운데 두 점(110°·70°)이 같은 원 테두리를 타고 위로 올라가 눈이 되고,
           바깥 두 점은 그 자리에서 사라진다. 반지름은 그대로라 눈·입이 모두 원래 링 위에 놓인다. */
        float[] smileAngles = new float[] { 145f, 215f, 325f, 35f };
        if (smile > 0f) dotOrbit = batteryRadius;   // 호와 같은 반지름 위로

        for (int index = 0; index < 4; index++) {
            float angle = dotAngles[index] + (smileAngles[index] - dotAngles[index]) * smile;
            double radians = Math.toRadians(angle);
            float x = cx + (float) Math.cos(radians) * dotOrbit;
            float dotsY = cy + (float) Math.sin(radians) * dotOrbit;
            int inactive = blend(Color.argb(72, 150, 155, 164),
                Color.argb(88, 25, 28, 32), lightThemeProgress);
            int cellular = blend(inactive, themed(cellularBright(), cellularSaturated()),
                stateMotion.cell(index, motionNow));
            int color = blend(cellular, themed(wifiBright(), wifiSaturated()),
                stateMotion.wifi(index, motionNow));
            if (state.wifiConnected && !state.networkValidated) {
                color = withAlpha(color, Math.round(Color.alpha(color)
                    * (0.35f + 0.65f * oscillate(900L))));
            }
            float animatedDotRadius = dotRadius * stateMotion.scale(index, motionNow);
            if (spinKind != 0) {
                float clearance = (float) Math.toDegrees(Math.asin(Math.min(1f,
                    (animatedDotRadius + stroke / 2f) / Math.max(1f, batteryRadius))));
                // Use the same eased visibility for scale, not just opacity.
                // Compute clearance BEFORE shrinking to avoid timing feedback.
                animatedDotRadius *= stateMotion.dotVisibility(
                    dotAngles[index], visibleSweep, clearance, motionNow);
            }
            dotPaint.setColor(color);
            // 눈이 되는 가운데 두 점은 살리고, 바깥 두 점만 사라진다
            if (index == 0 || index == 3) animatedDotRadius *= (1f - smile);
            else animatedDotRadius *= 1f + 0.6f * smile;   // 눈은 조금 또렷하게
            if (animatedDotRadius > .001f) canvas.drawCircle(x, dotsY, animatedDotRadius, dotPaint);
        }

        if (focusLayer >= 0) canvas.restoreToCount(focusLayer);
        if (smileLayer >= 0) canvas.restoreToCount(smileLayer);

        if (showLayoutGuide) {
            guidePaint.setColor(Color.argb(230, 255, 45, 45));
            guidePaint.setStrokeWidth(Math.max(1f, density));
            float guideRadius = Math.max(batteryRadius, dotOrbit) + 3f * density;
            canvas.drawCircle(cx, cy, guideRadius, guidePaint);
        }

        if (state.vpnActive) {
            float radius = 2f * density;
            float diagonal = (Math.max(batteryRadius, dotOrbit) + stroke / 2f + radius + 3f * density)
                * .70710678f;
            float x = cx + VpnIndicator.xSign(vpnDotCorner) * diagonal;
            float y = cy + VpnIndicator.ySign(vpnDotCorner) * diagonal;
            x = Math.max(radius, Math.min(getWidth() - radius, x));
            y = Math.max(radius, Math.min(getHeight() - radius, y));
            int alpha = animate ? VpnIndicator.alpha(SystemClock.elapsedRealtime()) : 255;
            dotPaint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(x, y, radius, dotPaint);
        }

        if (saveLayer >= 0) canvas.restoreToCount(saveLayer);
        if (hideLayer >= 0) canvas.restoreToCount(hideLayer);

        if (state.fastCharging
                || iconMotion.running()
                || stateMotion.running(motionNow)
                || (state.wifiConnected && !state.networkValidated)) {
            postInvalidateOnAnimation();
        } else if (state.vpnActive && animate) {
            postInvalidateDelayed(state.screenInteractive ? 33L : 100L);
        } else if (!previewMode && burnInProtection) {
            postInvalidateDelayed(60_000L);
        }
    }

    private void publishGeometry(float centerX, float centerY, float touchDiameter,
            boolean visible) {
        GeometryListener listener = geometryListener;
        if (listener == null) return;
        if (geometryPublished
                && Math.abs(centerX - publishedCenterX) < 0.5f
                && Math.abs(centerY - publishedCenterY) < 0.5f
                && Math.abs(touchDiameter - publishedTouchDiameter) < 0.5f
                && visible == publishedVisible) return;
        geometryPublished = true;
        publishedCenterX = centerX;
        publishedCenterY = centerY;
        publishedTouchDiameter = touchDiameter;
        publishedVisible = visible;
        post(() -> {
            if (geometryListener != null) {
                geometryListener.onRingGeometry(centerX, centerY, touchDiameter, visible);
            }
        });
    }

    private int resolveBatteryColor() {
        // Priority agreed for the UI: charging, low battery, power saver, normal.
        if (stylePreset == 3 && !state.charging && state.batteryPercent >= lowBatteryPercent
                && !state.powerSave) return themed(Color.WHITE, Color.BLACK);
        // Fast charging: vivid Apple-battery green (system green on dark, deeper green on light backgrounds)
        if (state.fastCharging) return themed(Color.rgb(48, 209, 88), Color.rgb(36, 168, 70));
        if (state.charging) return themed(cellularBright(), cellularSaturated());
        if (state.batteryPercent < lowBatteryPercent) return themed(COLOR_RED, Color.rgb(210, 0, 30));
        if (state.powerSave) return themed(COLOR_ORANGE, Color.rgb(230, 103, 0));
        return themed(COLOR_NORMAL, Color.BLACK);
    }

    private List<String> attentionKeys() {
        if (priorityOrder == null) priorityOrder = AppSettings.priorityOrder(getContext());
        List<String> keys = new ArrayList<>();
        long now = SystemClock.elapsedRealtime();
        for (String item : priorityOrder) {
            if ("temperature".equals(item) && state.batteryTemperatureC >= thermalThresholdC) {
                keys.add(state.batteryTemperatureC >= thermalThresholdC + 4 ? "temperature:hot" : "temperature:warm");
            }
            if ("network".equals(item) && (!state.networkConnected
                    || (state.wifiConnected && !state.networkValidated))) keys.add("network:warning");
            if ("battery".equals(item)) {
                if (state.batteryPercent < lowBatteryPercent) keys.add("battery:low");
                else if (state.powerSave) keys.add("battery:save");
            }
            if ("event".equals(item) && state.eventKind != StatusState.EVENT_NONE
                    && now < state.eventUntilElapsed) {
                String[] names = {"", "charging", "full", "wifi", "bluetooth", "vpn", "hotspot"};
                if (state.eventKind < names.length) {
                    keys.add(names[state.eventKind] + ":" + state.eventStartedElapsed);
                }
            }
        }
        return keys;
    }

    private float drawStatusIcon(Canvas canvas, float cx, float cy, float ringRadius,
            float stroke, float density, boolean animate) {
        long now = SystemClock.elapsedRealtime();
        String key = iconMotion.advance(now, iconHoldMs, animationSpeedPercent, animate);
        if (key == null) return 0f;
        float reveal = iconMotion.reveal(now, iconHoldMs, animationSpeedPercent, animate);
        float holeDiameter = resolveHoleDiameter(density);
        float size = holeDiameter * iconSizePercent / 100f;
        float rightEdge = cx + ringRadius + stroke / 2f;
        float destination = Math.min(getWidth() - size / 2f - density,
            rightEdge + iconGapDp * density + size / 2f);
        float destinationY = Math.max(cy, size / 2f + density);
        // Big bubbles descend smoothly from the fixed cutout instead of clipping
        // against the screen top. Preserve a gap even in a narrow preview/window.
        float separation = ringRadius + stroke / 2f + iconGapDp * density + size / 2f;
        float dx = destination - cx;
        if (dx * dx + (destinationY - cy) * (destinationY - cy) < separation * separation) {
            destinationY = Math.max(destinationY, cy + (float) Math.sqrt(
                Math.max(0f, separation * separation - dx * dx)));
        }
        destinationY = Math.min(getHeight() - size / 2f - density, destinationY);
        int bubble = themed(Color.WHITE, Color.BLACK);
        int color = iconColor(key);
        statusDroplet.draw(canvas, key, cx, cy, holeDiameter / 2f, destination, destinationY,
            size, reveal, bubble, color);
        return reveal;
    }

    private float resolveHoleDiameter(float density) {
        return previewMode ? 24f * density : CutoutGeometry.holeDiameter(this);
    }

    private int iconColor(String key) {
        if (key.startsWith("charging") || key.startsWith("full"))
            return bubbleIconColor(cellularBright(), cellularSaturated());
        if (key.startsWith("wifi")) return bubbleIconColor(wifiBright(), wifiSaturated());
        if (key.startsWith("bluetooth"))
            return bubbleIconColor(Color.rgb(191, 124, 255), Color.rgb(94, 45, 220));
        if (key.startsWith("vpn"))
            return bubbleIconColor(Color.rgb(255, 214, 64), Color.rgb(205, 125, 0));
        if (key.equals("battery:low") || key.equals("temperature:hot"))
            return bubbleIconColor(COLOR_RED, Color.rgb(205, 0, 28));
        return bubbleIconColor(COLOR_ORANGE, Color.rgb(220, 85, 0));
    }

    private int bubbleIconColor(int bright, int saturated) {
        // Bubble contrast is opposite to the underlying app's background.
        return blend(saturated, bright, lightThemeProgress);
    }

    /** True while a camera app is in front. Same package test the smart-hide rule uses. */
    private boolean isCameraForeground() {
        return foregroundPackage != null && (foregroundPackage.contains("camera")
            || foregroundPackage.contains("com.sec.android.app.camera"));
    }

    /** 0 = normal ring, 1 = full smiling face. Eased both ways so it never snaps. */
    /** Preview toggle so the smiling face can be demonstrated without opening a camera. */
    void previewSmile(boolean smiling) {
        previewSmile = smiling;
        smileShown = smiling;
        smileChangedAt = SystemClock.elapsedRealtime();
        invalidate();
    }

    /** Fade the ring out/in (same easing as smart hide) before the service detaches or after it re-attaches the window. */
    void setForceHidden(boolean hide) {
        if (forceHidden == hide) return;
        forceHidden = hide;
        invalidate();
    }

    /** 1 = fully visible, 0 = fully hidden. Eased fade + shrink when smart hide toggles. */
    private float shownProgress() {
        long span = Math.max(1L, Math.round(280L * 100f / Math.max(10, animationSpeedPercent)));
        float linear = hideChangedAt == 0L ? 1f
            : Math.min(1f, (SystemClock.elapsedRealtime() - hideChangedAt) / (float) span);
        if (!ValueAnimator.areAnimatorsEnabled()) linear = 1f;
        float eased = linear < .5f ? 2f * linear * linear : 1f - (float) Math.pow(-2f * linear + 2f, 2) / 2f;
        if (linear < 1f) postInvalidateOnAnimation();
        return hideShown ? 1f - eased : eased;
    }

    /**
     * Battery arc with a thin opposite-brightness halo underneath. The normal (near-white) arc used to
     * vanish on light status bars, so the level was only visible while charging or in power saver.
     */
    private void drawBatteryArc(Canvas canvas, RectF bounds, float start, float sweep) {
        int color = batteryPaint.getColor();
        float width = batteryPaint.getStrokeWidth();
        int luma = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
        batteryPaint.setColor(withAlpha(luma > 150 ? Color.BLACK : Color.WHITE, Math.round(Color.alpha(color) * 0.5f)));
        batteryPaint.setStrokeWidth(width + Math.max(1.5f, width * 0.5f));
        canvas.drawArc(bounds, start, sweep, false, batteryPaint);
        batteryPaint.setStrokeWidth(width);
        batteryPaint.setColor(color);
        canvas.drawArc(bounds, start, sweep, false, batteryPaint);
    }

    private float smileProgress() {
        if (previewMode && !previewSmile && !smileShown) return 0f;
        long span = Math.max(1L, Math.round(460L * 100f / Math.max(10, animationSpeedPercent)));
        float linear = Math.min(1f, (SystemClock.elapsedRealtime() - smileChangedAt) / (float) span);
        if (!ValueAnimator.areAnimatorsEnabled()) linear = 1f;
        float eased = linear < .5f ? 2f * linear * linear : 1f - (float) Math.pow(-2f * linear + 2f, 2) / 2f;
        // 전환 중에는 다음 프레임을 직접 요청한다 — 다른 애니메이션이 없으면 한 번 그리고 멈춰 버린다
        if (linear < 1f) postInvalidateOnAnimation();
        return smileShown ? eased : 1f - eased;
    }

    /**
     * Degrees to spin the face so it stays upright.
     *
     * <p>When the overlay window itself turns with the screen the canvas is already upright and
     * this returns 0; when it stays portrait we rotate the face instead.
     */
    private float faceRotation() {
        Display display = getDisplay();
        if (display == null) return 0f;
        boolean windowTurned = getWidth() > getHeight();
        switch (display.getRotation()) {
            case Surface.ROTATION_90: return windowTurned ? 0f : -90f;
            case Surface.ROTATION_270: return windowTurned ? 0f : 90f;
            case Surface.ROTATION_180: return windowTurned ? 0f : 180f;
            default: return 0f;
        }
    }


    private boolean shouldHideForContext() {
        if (foregroundPackage != null && (foregroundPackage.contains("camera")
                || foregroundPackage.contains("com.sec.android.app.camera"))) return true;
        WindowInsets insets = getRootWindowInsets();
        return Build.VERSION.SDK_INT >= 30 && insets != null
            && !insets.isVisible(WindowInsets.Type.statusBars());
    }

    private int[] burnInShift() {
        int[][] positions = {
            {0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
        };
        int index = (int) ((SystemClock.elapsedRealtime() / 60_000L) % positions.length);
        return positions[index];
    }

    private float animationPhase(long baseDuration) {
        return (graphClock.now(SystemClock.elapsedRealtime()) % baseDuration) / (float) baseDuration;
    }

    private float oscillate(long baseDuration) {
        return (float) ((Math.sin(animationPhase(baseDuration) * Math.PI * 2d) + 1d) / 2d);
    }

    private int cellularBright() {
        if (stylePreset == 1) return Color.rgb(175, 255, 35);
        if (stylePreset == 2) return Color.rgb(210, 231, 196);
        if (stylePreset == 3) return Color.WHITE;
        if (stylePreset == 4) return Color.rgb(255, 214, 10);
        return COLOR_LIME;
    }

    private int cellularSaturated() {
        if (stylePreset == 1) return Color.rgb(40, 205, 50);
        if (stylePreset == 2) return Color.rgb(60, 120, 72);
        if (stylePreset == 3) return Color.BLACK;
        if (stylePreset == 4) return Color.rgb(190, 125, 0);
        return Color.rgb(20, 166, 70);
    }

    private int wifiBright() {
        if (stylePreset == 1) return Color.rgb(0, 164, 255);
        if (stylePreset == 2) return Color.rgb(160, 195, 235);
        if (stylePreset == 3) return Color.rgb(190, 198, 208);
        if (stylePreset == 4) return Color.rgb(42, 128, 255);
        return COLOR_SKY;
    }

    private int wifiSaturated() {
        if (stylePreset == 1) return Color.rgb(0, 67, 255);
        if (stylePreset == 2) return Color.rgb(55, 95, 125);
        if (stylePreset == 3) return Color.rgb(70, 75, 82);
        if (stylePreset == 4) return Color.rgb(0, 62, 235);
        return Color.rgb(0, 76, 255);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
            Color.red(color), Color.green(color), Color.blue(color));
    }

    private void setLightBackground(boolean light) {
        float target = light ? 1f : 0f;
        if (!themeInitialized) {
            themeInitialized = true;
            lightThemeProgress = target;
            return;
        }
        if (Math.abs(lightThemeProgress - target) < 0.001f) return;
        if (themeAnimator != null) themeAnimator.cancel();
        themeAnimator = ValueAnimator.ofFloat(lightThemeProgress, target);
        themeAnimator.setDuration(500L);
        themeAnimator.addUpdateListener(animation -> {
            lightThemeProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        themeAnimator.start();
    }

    private int themed(int darkBackgroundColor, int lightBackgroundColor) {
        return blend(darkBackgroundColor, lightBackgroundColor, lightThemeProgress);
    }

    private static int blend(int start, int end, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(Color.alpha(start) + (Color.alpha(end) - Color.alpha(start)) * t);
        int r = Math.round(Color.red(start) + (Color.red(end) - Color.red(start)) * t);
        int g = Math.round(Color.green(start) + (Color.green(end) - Color.green(start)) * t);
        int b = Math.round(Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t);
        return Color.argb(a, r, g, b);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (themeAnimator != null) themeAnimator.cancel();
        if (pressAnimator != null) pressAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private float[] resolveCutoutCenter() {
        return CutoutGeometry.center(this);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1f - value;
        return 1f - inverse * inverse * inverse;
    }
}
