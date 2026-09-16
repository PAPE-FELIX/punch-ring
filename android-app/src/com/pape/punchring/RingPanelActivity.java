package com.pape.punchring;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RingPanelActivity extends Activity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PanelTheme design;
    private ProgressBar progress;
    private boolean sending;
    private GeniePanelLayout genieRoot;
    private LinearLayout panel;
    private boolean finished;
    private Runnable afterClose;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        RingTouchOverlayController.setPanelForeground(true);
        design = new PanelTheme(this);
        configureWindow();
        View content = buildContent();
        setContentView(content);
        PanelTheme.applyFont(content);
        overridePendingTransition(0, 0);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::finish);
        }
        genieRoot.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                genieRoot.getViewTreeObserver().removeOnPreDrawListener(this);
                float[] origin = panelOrigin();
                genieRoot.bind(panel, origin[0], origin[1], panelHoleRadius());
                if (!finished) genieRoot.open();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        RingTouchOverlayController.setPanelForeground(true);
    }

    @Override
    protected void onStop() {
        RingTouchOverlayController.setPanelForeground(false);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        finished = true;
        if (genieRoot != null) genieRoot.dispose();
        RingTouchOverlayController.setPanelForeground(false);
        network.shutdownNow();
        super.onDestroy();
    }

    @Override public void finish() {
        if (finished) return;
        finished = true;
        if (genieRoot == null) finishImmediately();
        else {
            genieRoot.performHapticFeedback(HapticFeedbackConstants.GESTURE_END);
            genieRoot.close(this::finishImmediately);
        }
    }

    @Override public void onBackPressed() { finish(); }

    private void finishImmediately() {
        super.finish();
        overridePendingTransition(0, 0);
        if (afterClose != null) {
            Runnable action = afterClose;
            afterClose = null;
            action.run();
        }
    }

    private float panelHoleRadius() {
        return CutoutGeometry.holeDiameter(genieRoot) / 2f;
    }

    private float[] panelOrigin() {
        float[] origin = RingTouchOverlayController.panelOrigin();
        if (origin == null) {
            origin = CutoutGeometry.center(genieRoot);
        }
        int[] location = new int[2];
        genieRoot.getLocationOnScreen(location);
        return new float[] {origin[0] - location[0], origin[1] - location[1]};
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0f;
        attributes.setBlurBehindRadius(dp(28));
        attributes.windowAnimations = 0;
        attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        window.setAttributes(attributes);
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(dp(36));
        } catch (RuntimeException ignored) {
            // Some vendor compositors disable cross-window blur; translucent glass remains.
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
    }

    private View buildContent() {
        GeniePanelLayout root = new GeniePanelLayout(this);
        genieRoot = root;
        root.setOnClickListener(view -> finish());
        root.setPadding(dp(12), dp(42), dp(12), dp(24));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(rounded(withAlpha(design.color("popover"), 232), 26,
            Color.argb(72, 255, 255, 255), 1));
        panel.setElevation(dp(22));
        panel.setOnClickListener(view -> {});
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            Math.min(dp(440), getResources().getDisplayMetrics().widthPixels - dp(24)),
            Math.min(dp(720), getResources().getDisplayMetrics().heightPixels - dp(74)),
            Gravity.CENTER);
        root.addView(panel, panelParams);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            root.setPadding(dp(12), Math.max(dp(42), bars.top + dp(12)),
                dp(12), Math.max(dp(24), Math.max(bars.bottom, keyboard) + dp(8)));
            fitPanel(root, panelParams, root.getWidth(), root.getHeight());
            return insets;
        });
        root.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            fitPanel(root, panelParams, right - left, bottom - top);
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("PUNCH RING", 21, design.color("foreground"), true);
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = button(tr("닫기", "Close"), design.color("secondary"), design.color("foreground"));
        close.setOnClickListener(view -> finish());
        header.addView(close, new LinearLayout.LayoutParams(dp(64), dp(40)));
        panel.addView(header);

        TextView gestureHint = text(tr("짧게 누르면 셀피 · 길게 누르면 이 패널",
            "Tap for selfie · long-press for this panel"), 12,
            design.color("muted-foreground"), false);
        gestureHint.setPadding(0, dp(2), 0, dp(12));
        panel.addView(gestureHint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        section(body, "DEVICE STATUS", design.color("data-success"));
        TextView status = text(formatStatus(PunchRingRuntime.snapshot()), 14,
            design.color("foreground"), false);
        status.setLineSpacing(dp(3), 1f);
        status.setPadding(dp(13), dp(12), dp(13), dp(12));
        status.setBackground(rounded(design.color("card"), 15,
            design.color("border"), 1));
        body.addView(status, matchWithBottom(dp(14)));

        return root;
    }

    private void fitPanel(FrameLayout root, FrameLayout.LayoutParams params, int width, int height) {
        if (width <= 0 || height <= 0 || finished) return;
        int panelWidth = Math.min(dp(440), Math.max(1, width - root.getPaddingLeft() - root.getPaddingRight()));
        int panelHeight = Math.min(dp(720), Math.max(1, height - root.getPaddingTop() - root.getPaddingBottom()));
        if (params.width != panelWidth || params.height != panelHeight) {
            params.width = panelWidth;
            params.height = panelHeight;
            panel.setLayoutParams(params);
        }
    }

    private String formatStatus(StatusState state) {
        String batteryMode = state.fastCharging ? tr("고속충전", "Fast charging")
            : state.charging ? tr("충전 중", "Charging")
            : state.powerSave ? tr("절전", "Power saver") : tr("사용 중", "In use");
        String watts = state.charging && state.chargeWatts > 0.1f
            ? String.format(UiText.formatLocale(this), " · %.1fW", state.chargeWatts) : "";
        String network = state.networkValidated ? tr("인터넷 정상", "Internet OK")
            : state.networkConnected ? tr("인터넷 확인 중", "Checking internet") : tr("인터넷 끊김", "No internet");
        String estimate = state.charging && state.chargeRemainingMillis > 0
            ? tr("\n완충까지 약 ", "\nAbout ") + Math.max(1L, Math.round(state.chargeRemainingMillis / 60_000d))
                + tr("분", " min to full")
            : "";
        return String.format(UiText.formatLocale(this), tr(
            "배터리  %d%% · %s%s · %.1f°C\n셀룰러  %d/4\nWi-Fi  %d/4 · %s%s",
            "Battery  %d%% · %s%s · %.1f°C\nCellular  %d/4\nWi-Fi  %d/4 · %s%s"),
            state.batteryPercent, batteryMode, watts, state.batteryTemperatureC,
            state.cellularLevel, state.wifiLevel, network,
            state.vpnActive ? " · Tailscale/VPN" : "") + estimate;
    }


    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
            ? tr("잠시 후 다시 시도해 주세요.", "Please try again shortly.") : message;
    }

    private void section(LinearLayout parent, String title, int color) {
        TextView view = text(title, 12, color, true);
        view.setLetterSpacing(0.08f);
        view.setPadding(0, dp(4), 0, dp(7));
        parent.addView(view);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setBackground(rounded(background, 14, Color.TRANSPARENT, 0));
        return button;
    }

    private LinearLayout.LayoutParams matchWithBottom(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, bottom);
        return params;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp >= 24 ? design.radius() * 3 : design.radius()));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private String tr(String korean, String english) {
        return UiText.get(this, korean, english);
    }
}
