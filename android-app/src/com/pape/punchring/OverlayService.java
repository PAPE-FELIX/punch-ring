package com.pape.punchring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;

public final class OverlayService extends Service
        implements StatusMonitor.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private WindowManager.LayoutParams overlayParams;
    private boolean overlayAttached;
    private static final long UPDATE_CHECK_MS = 6L * 60L * 60L * 1000L;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateCheck = new Runnable() {
        @Override
        public void run() {
            UpdateChecker.check(OverlayService.this, false);
            updateHandler.postDelayed(this, UPDATE_CHECK_MS);
        }
    };
    private final Runnable detachOverlay = () -> {
        if (!this.overlayAttached || this.overlayView == null || this.windowManager == null) return;
        try { this.windowManager.removeView(this.overlayView); this.overlayAttached = false; } catch (RuntimeException ignored) {}
    };
    static final String ACTION_START = "com.pape.punchring.START";
    static final String ACTION_STOP = "com.pape.punchring.STOP";
    private static final int NOTIFICATION_ID = 8108;
    private static final String CHANNEL_ID = "punch_ring_active";

    private WindowManager windowManager;
    private PunchRingView overlayView;
    private StatusMonitor monitor;
    private boolean foregroundStartAllowed = true;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification());
        } catch (ForegroundServiceStartNotAllowedException denied) {
            foregroundStartAllowed = false;
            stopSelf();
        }
        AppSettings.prefs(this).registerOnSharedPreferenceChangeListener(this);
        updateHandler.post(updateCheck);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundStartAllowed) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AppSettings.prefs(this).edit().putBoolean(AppSettings.ENABLED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            AppSettings.prefs(this).edit().putBoolean(AppSettings.ENABLED, false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        AppSettings.prefs(this).edit().putBoolean(AppSettings.ENABLED, true).apply();
        ensureOverlay();
        return START_STICKY;
    }

    private void ensureOverlay() {
        if (overlayView != null) return;
        windowManager = getSystemService(WindowManager.class);
        overlayView = new PunchRingView(this);
        RingTouchOverlayController.attachRing(overlayView);
        overlayView.setGeometryListener(RingTouchOverlayController::updateGeometry);
        overlayView.reloadSettings();
        overlayView.setSystemUiVisibility(
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        int overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            type,
            overlayFlags,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("Punch Ring overlay");
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        overlayParams = params;
        windowManager.addView(overlayView, params);
        overlayAttached = true;

        monitor = new StatusMonitor(this, this);
        monitor.start();
    }

    @Override
    public void onStatusChanged(StatusState state) {
        PunchRingRuntime.update(state);
        if (overlayView != null) overlayView.updateStatus(state);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (overlayView != null) overlayView.reloadSettings();
        if (AppSettings.FOREGROUND_PACKAGE.equals(key)) syncOverlayForSensitiveScreens(prefs.getString(key, ""));
    }

    /**
     * Android blocks taps on permission/install dialogs while another app's window covers the screen
     * (tapjacking protection). The overlay is a full-screen window, so merely drawing nothing is not
     * enough — detach the window while such a dialog is in front and re-attach afterwards.
     */
    private void syncOverlayForSensitiveScreens(String foreground) {
        if (overlayView == null || windowManager == null || overlayParams == null) return;
        boolean sensitive = isSensitivePackage(foreground);
        overlayView.setForceHidden(sensitive);
        overlayView.removeCallbacks(detachOverlay);
        if (sensitive && overlayAttached) {
            // let the fade-out play, then take the window away so the dialog accepts taps
            overlayView.postDelayed(detachOverlay, 320L);
        } else if (!sensitive && !overlayAttached) {
            try {
                windowManager.addView(overlayView, overlayParams);   // view is still fading from hidden → eases back in
                overlayAttached = true;
            } catch (RuntimeException ignored) {}
        }
    }

    static boolean isSensitivePackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return pkg.contains("permissioncontroller") || pkg.contains("packageinstaller")
            || pkg.equals("com.android.settings") || pkg.equals("com.samsung.android.settings")
            || pkg.contains("com.google.android.gms") && pkg.contains("auth");
    }

    @Override
    public void onDestroy() {
        AppSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
        updateHandler.removeCallbacks(updateCheck);
        RingTouchOverlayController.hide();
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        if (overlayView != null && windowManager != null) {
            RingTouchOverlayController.detachRing(overlayView);
            if (overlayAttached) { try { windowManager.removeView(overlayView); } catch (RuntimeException ignored) {} }
            overlayAttached = false;
            overlayView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, UiText.get(this, "Punch Ring 실행", "Punch Ring running"),
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(UiText.get(this,
            "펀치홀 배터리 및 네트워크 표시가 실행 중입니다.",
            "The camera-cutout battery and network indicator is running."));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentTitle(UiText.get(this, "Punch Ring 실행 중", "Punch Ring is running"))
            .setContentText(UiText.get(this,
                "펀치홀에 배터리 · 셀룰러 · Wi-Fi를 표시합니다.",
                "Showing battery, cellular, and Wi-Fi around the camera cutout."))
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, UiText.get(this, "중지", "Stop"), stop)
            .setOngoing(true)
            .build();
    }
}
