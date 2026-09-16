package com.pape.punchring;

import java.lang.ref.WeakReference;

/** Shares ring geometry with the enabled accessibility overlay in this app process. */
final class RingTouchOverlayController {
    private static WeakReference<BackgroundContrastService> host = new WeakReference<>(null);
    private static WeakReference<PunchRingView> ring = new WeakReference<>(null);
    private static boolean hasGeometry;
    private static float centerX;
    private static float centerY;
    private static float diameter;
    private static boolean geometryVisible;
    private static boolean cameraForeground;
    private static boolean panelForeground;

    private RingTouchOverlayController() {}

    static float[] panelOrigin() {
        return hasGeometry ? new float[] {centerX, centerY} : null;
    }

    static void attach(BackgroundContrastService service) {
        host = new WeakReference<>(service);
        dispatch();
    }

    static void attachRing(PunchRingView view) { ring = new WeakReference<>(view); }
    static void detachRing(PunchRingView view) { if (ring.get() == view) ring.clear(); }
    static void setRingPressed(boolean pressed) {
        PunchRingView view = ring.get();
        if (view != null) view.setPressTarget(pressed);
    }

    static void detach(BackgroundContrastService service) {
        if (host.get() == service) host.clear();
    }

    static void updateGeometry(float x, float y, float touchDiameter, boolean isVisible) {
        hasGeometry = true;
        centerX = x;
        centerY = y;
        diameter = touchDiameter;
        geometryVisible = isVisible;
        dispatch();
    }

    static void setCameraForeground(boolean isCameraForeground) {
        cameraForeground = isCameraForeground;
        dispatch();
    }

    static void setPanelForeground(boolean isPanelForeground) {
        panelForeground = isPanelForeground;
        dispatch();
    }

    static void hide() {
        geometryVisible = false;
        dispatch();
    }

    private static void dispatch() {
        BackgroundContrastService service = host.get();
        if (service != null && hasGeometry) {
            service.updateRingTouchTarget(
                centerX, centerY, diameter,
                geometryVisible && !cameraForeground && !panelForeground);
        }
    }
}
