package com.pape.punchring;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.accessibility.AccessibilityManager;
import android.view.Gravity;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int OVERLAY_REQUEST = 108;
    private static final int PERMISSIONS_REQUEST = 109;
    private static final int BACKUP_CREATE_REQUEST = 110;
    private static final int BACKUP_OPEN_REQUEST = 111;
    private final StatusState demoState = new StatusState();
    private PunchRingView preview;
    private TextView permissionStatus;
    private TextView liveStatus;
    private Button startButton;
    private StatusMonitor statusMonitor;
    private SettingControl dotDiameterControl;
    private Switch syncDiameterSwitch;
    private boolean smilePreviewOn;
    private TextView contrastStatus;
    private LinearLayout priorityList;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        demoState.batteryPercent = 72;
        demoState.cellularLevel = 4;
        demoState.wifiLevel = 3;
        showSettings();
        handleUpdateIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleUpdateIntent(intent);
    }

    private String pendingUpdateUrl;

    private void handleUpdateIntent(Intent intent) {
        if (intent == null || !UpdateChecker.ACTION_INSTALL.equals(intent.getAction())) return;
        String url = intent.getStringExtra(UpdateChecker.EXTRA_URL);
        intent.setAction(null);
        if (url == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            pendingUpdateUrl = url;
            Toast.makeText(this, tr("이 앱의 설치 허용을 켜고 돌아오세요", "Allow installs from this app, then come back"),
                Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())));
            return;
        }
        UpdateChecker.install(this, url);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionState();
        if (pendingUpdateUrl != null && getPackageManager().canRequestPackageInstalls()) {
            String url = pendingUpdateUrl;
            pendingUpdateUrl = null;
            UpdateChecker.install(this, url);
        }
        if (Settings.canDrawOverlays(this)
                && AppSettings.prefs(this).getBoolean(AppSettings.ENABLED, false)) {
            Intent intent = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_START);
            startForegroundService(intent);
        }
        refreshContrastState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        statusMonitor = new StatusMonitor(this, state -> runOnUiThread(() -> showLiveState(state)));
        statusMonitor.start();
    }

    @Override
    protected void onStop() {
        if (statusMonitor != null) {
            statusMonitor.stop();
            statusMonitor = null;
        }
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSIONS_REQUEST) startOverlayIfAllowed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            refreshPermissionState();
            if (Settings.canDrawOverlays(this)) requestRuntimePermissionsThenStart();
        } else if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == BACKUP_CREATE_REQUEST) exportSettings(data.getData());
            else if (requestCode == BACKUP_OPEN_REQUEST) importSettings(data.getData());
        }
    }

    private void showSettings() {
        float density = getResources().getDisplayMetrics().density;
        int horizontal = Math.round(22 * density);
        int small = Math.round(10 * density);
        int medium = Math.round(16 * density);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 10, 14));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int topPadding = Math.round(28 * density);
        int bottomPadding = Math.round(40 * density);
        root.setPadding(horizontal, topPadding, horizontal, bottomPadding);
        // Status bar height varies by device and by folded/unfolded state, so take it from the
        // window instead of a fixed inset. Without this the title sits under the clock.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(horizontal, topPadding + bars.top, horizontal, bottomPadding + bars.bottom);
            return insets;
        });
        scroll.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eyebrow = label("ANDROID CAMERA CUTOUT", 12, Color.rgb(110, 220, 255));
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow);

        TextView title = label("Punch Ring", 32, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(0, small / 2, 0, small);
        root.addView(title);

        TextView intro = label(tr(
            "실제 카메라 펀치홀을 중심으로 배터리 게이지와 겹쳐진 셀룰러 · Wi-Fi 4점을 표시합니다.",
            "Shows a battery gauge and layered cellular/Wi-Fi dots around the actual camera cutout."),
            15, Color.rgb(185, 190, 199));
        intro.setLineSpacing(0, 1.18f);
        root.addView(intro);

        FrameLayout previewCard = new FrameLayout(this);
        previewCard.setBackground(rounded(Color.rgb(18, 22, 29), 24));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.round(220 * density));
        previewParams.setMargins(0, medium, 0, small);
        root.addView(previewCard, previewParams);

        preview = new PunchRingView(this);
        preview.setPreviewMode(true);
        preview.reloadSettings();
        preview.updateStatus(demoState);
        previewCard.addView(preview, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView previewBadge = label(tr("미리보기 · 테스트", "PREVIEW · TEST"), 12, PunchRingView.COLOR_SKY);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.START);
        badgeParams.leftMargin = medium;
        badgeParams.topMargin = small;
        previewCard.addView(previewBadge, badgeParams);

        TextView legend = label(tr("상단 배터리  ·  연두 셀룰러  ·  하늘색 Wi-Fi",
            "TOP BATTERY  ·  LIME CELLULAR  ·  BLUE WI-FI"), 12,
            Color.rgb(144, 150, 160));
        FrameLayout.LayoutParams legendParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        legendParams.bottomMargin = small;
        previewCard.addView(legend, legendParams);

        LinearLayout stateButtons = new LinearLayout(this);
        stateButtons.setOrientation(LinearLayout.HORIZONTAL);
        stateButtons.setGravity(Gravity.CENTER);
        addDemoButton(stateButtons, tr("일반", "Normal"), 0);
        addDemoButton(stateButtons, tr("충전", "Charge"), 1);
        addDemoButton(stateButtons, tr("고속", "Fast"), 2);
        addDemoButton(stateButtons, tr("절전", "Saver"), 3);
        addDemoButton(stateButtons, "12%", 4);
        root.addView(stateButtons, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout signalButtons = new LinearLayout(this);
        signalButtons.setOrientation(LinearLayout.HORIZONTAL);
        signalButtons.setGravity(Gravity.CENTER);
        addDemoButton(signalButtons, tr("인터넷X", "Offline"), 5);
        addDemoButton(signalButtons, tr("고온", "Hot"), 7);
        addDemoButton(signalButtons, tr("이벤트", "Event"), 8);
        // Smiling face normally needs the camera in front; this button shows it right here.
        Button smileDemo = new Button(this);
        smileDemo.setText(tr("웃는 얼굴", "Smile"));
        smileDemo.setTextSize(11);
        smileDemo.setTextColor(Color.WHITE);
        smileDemo.setAllCaps(false);
        smileDemo.setMinWidth(0);
        smileDemo.setMinimumWidth(0);
        smileDemo.setPadding(4, 0, 4, 0);
        smileDemo.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(Color.rgb(42, 48, 59)));
        smileDemo.setOnClickListener(view -> {
            smilePreviewOn = !smilePreviewOn;
            preview.previewSmile(smilePreviewOn);
        });
        signalButtons.addView(smileDemo, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(signalButtons, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        liveStatus = label(tr("실시간 값 불러오는 중…", "Loading live status…"), 13, Color.rgb(190, 196, 205));
        liveStatus.setPadding(small, small, small, small);
        liveStatus.setBackground(rounded(Color.rgb(18, 22, 29), 12));
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        liveParams.setMargins(0, small, 0, 0);
        root.addView(liveStatus, liveParams);

        permissionStatus = label("", 14, Color.WHITE);
        permissionStatus.setPadding(0, medium, 0, small);
        root.addView(permissionStatus);

        startButton = actionButton(tr("화면 위 표시 권한 허용 및 시작", "Allow overlay and start"), PunchRingView.COLOR_SKY);
        startButton.setOnClickListener(view -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_REQUEST);
            } else {
                requestRuntimePermissionsThenStart();
            }
        });
        root.addView(startButton, matchButtonParams(small));

        Button stopButton = actionButton(tr("오버레이 중지", "Stop overlay"), Color.rgb(53, 59, 70));
        stopButton.setTextColor(Color.WHITE);
        stopButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP);
            startService(intent);
            AppSettings.prefs(this).edit().putBoolean(AppSettings.ENABLED, false).apply();
            refreshPermissionState();
        });
        root.addView(stopButton, matchButtonParams(small));

        sectionTitle(root, tr("위치와 크기", "Position & size"), medium);
        SharedPreferences prefs = AppSettings.prefs(this);
        boolean innerProfile = AppSettings.isInnerDisplay(this);
        TextView profileBadge = label(
            tr("현재 자동 프로필 · ", "Active profile · ")
                + (innerProfile ? tr("큰 화면", "Large display") : tr("작은 화면", "Compact display")),
            13, PunchRingView.COLOR_LIME);
        profileBadge.setPadding(0, 0, 0, small);
        root.addView(profileBadge);
        addSetting(root, tr("배터리 게이지 지름", "Battery gauge diameter"),
            tr("상단 반원 게이지의 전체 폭", "Overall width of the upper semicircle"),
            14, 50, AppSettings.getProfileInt(this, AppSettings.DIAMETER_DP,
                AppSettings.DEFAULT_DIAMETER_DP),
            " dp", this::saveBatteryDiameter);
        syncDiameterSwitch = addToggle(root, tr("지름 동기화", "Sync diameters"),
            tr("배터리 지름을 바꾸면 신호 점의 원형 배치도 같이 변경",
                "Change the circular signal-dot path with the battery diameter"),
            AppSettings.getProfileBoolean(this, AppSettings.SYNC_DIAMETERS,
                AppSettings.DEFAULT_SYNC_DIAMETERS),
            this::setDiameterSync);
        dotDiameterControl = addSetting(root, tr("셀룰러/Wi-Fi 배치 지름", "Cellular/Wi-Fi path diameter"),
            tr("하단 네 점이 놓이는 원형 궤도의 지름", "Diameter of the circular path for the four lower dots"),
            14, 50, AppSettings.getProfileInt(this, AppSettings.DOT_DIAMETER_DP,
                AppSettings.DEFAULT_DOT_DIAMETER_DP),
            " dp", value -> saveInt(AppSettings.DOT_DIAMETER_DP, value));
        updateDiameterSyncUi();
        addSetting(root, tr("가로 미세조정", "Horizontal fine-tune"), tr("자동 감지 위치에서 좌우 이동", "Move left or right from the detected position"),
            -80, 80, AppSettings.getProfileInt(this, AppSettings.OFFSET_X_DP,
                AppSettings.DEFAULT_OFFSET_X_DP),
            " dp", value -> saveInt(AppSettings.OFFSET_X_DP, value));
        addSetting(root, tr("세로 미세조정", "Vertical fine-tune"), tr("자동 감지 위치에서 상하 이동", "Move up or down from the detected position"),
            -60, 100, AppSettings.getProfileInt(this, AppSettings.OFFSET_Y_DP,
                AppSettings.DEFAULT_OFFSET_Y_DP),
            " dp", value -> saveInt(AppSettings.OFFSET_Y_DP, value));
        addSetting(root, tr("게이지 두께", "Gauge thickness"), tr("배터리 반원과 셀룰러 점의 두께", "Thickness of the battery arc and cellular dots"),
            10, 50, AppSettings.getProfileInt(this, AppSettings.STROKE_TENTHS_DP,
                AppSettings.DEFAULT_STROKE_TENTHS_DP),
            "", value -> saveInt(AppSettings.STROKE_TENTHS_DP, value));

        Button calibrate = actionButton(tr("현재 화면 펀치홀 자동 맞춤", "Auto-fit current camera cutout"), PunchRingView.COLOR_LIME);
        calibrate.setOnClickListener(view -> performAutoCalibration());
        root.addView(calibrate, matchButtonParams(small));
        addToggle(root, tr("레이아웃 가이드", "Layout guide"),
            tr("두 지름 중 큰 범위를 감싸는 빨간 원형 테두리 표시", "Show a red guide around the larger diameter"),
            prefs.getBoolean(AppSettings.SHOW_LAYOUT_GUIDE,
                AppSettings.DEFAULT_SHOW_LAYOUT_GUIDE),
            value -> saveBoolean(AppSettings.SHOW_LAYOUT_GUIDE, value));

        sectionTitle(root, tr("배경 자동 대비 · 링 터치", "Auto contrast · ring touch"), medium);
        addToggle(root, tr("자동 색상 전환", "Automatic color switching"),
            tr("어두운 배경은 흰 선과 밝은 색, 밝은 배경은 검은 선과 진한 색",
                "Light lines and colors on dark backgrounds; dark, saturated colors on light backgrounds"),
            prefs.getBoolean(AppSettings.AUTO_CONTRAST, AppSettings.DEFAULT_AUTO_CONTRAST),
            value -> saveBoolean(AppSettings.AUTO_CONTRAST, value));
        contrastStatus = label("", 13, Color.rgb(190, 196, 205));
        contrastStatus.setPadding(small, small, small, small);
        contrastStatus.setBackground(rounded(Color.rgb(18, 22, 29), 12));
        root.addView(contrastStatus, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button contrastPermission = actionButton(tr("자동 대비 · 링 터치 접근성 설정", "Auto contrast · ring touch accessibility"), Color.rgb(53, 59, 70));
        contrastPermission.setTextColor(Color.WHITE);
        contrastPermission.setOnClickListener(view ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        LinearLayout.LayoutParams contrastButtonParams = matchButtonParams(small);
        contrastButtonParams.setMargins(0, small, 0, small);
        root.addView(contrastPermission, contrastButtonParams);

        sectionTitle(root, tr("길게 누르기", "Long press"), medium);
        addToggle(root, tr("후면 카메라 + 웃는 얼굴", "Rear camera with smiling face"),
            tr("켜면 길게 눌러 후면 카메라를 열고, 카메라를 쓰는 동안 링이 웃는 얼굴이 됩니다. "
                + "끄면 기존처럼 기기 상태 패널이 열립니다.",
                "Open the rear camera on long press and turn the ring into a smiling face while "
                + "the camera is open. When off, the device status panel opens instead."),
            AppSettings.LONG_PRESS_CAMERA.equals(prefs.getString(AppSettings.LONG_PRESS_ACTION,
                AppSettings.DEFAULT_LONG_PRESS_ACTION)),
            value -> {
                AppSettings.prefs(this).edit().putString(AppSettings.LONG_PRESS_ACTION,
                    value ? AppSettings.LONG_PRESS_CAMERA : AppSettings.LONG_PRESS_PANEL).apply();
                preview.reloadSettings();
            });

        sectionTitle(root, tr("스마트 표시", "Smart display"), medium);
        addToggle(root, tr("전체화면·카메라 자동 숨김", "Hide in fullscreen and camera"),
            tr("상태바가 숨겨진 영상·게임과 카메라 실행 중 오버레이 숨김",
                "Hide the overlay in fullscreen video, games, and camera apps"),
            prefs.getBoolean(AppSettings.SMART_HIDE, AppSettings.DEFAULT_SMART_HIDE),
            value -> saveBoolean(AppSettings.SMART_HIDE, value));
        addToggle(root, tr("화면 꺼짐·AOD 저휘도", "Low brightness on screen-off/AOD"),
            tr("화면이 꺼지면 전체 밝기를 약 28%로 낮춤", "Reduce brightness to about 28% when the screen turns off"),
            prefs.getBoolean(AppSettings.DIM_ON_SCREEN_OFF, AppSettings.DEFAULT_DIM_ON_SCREEN_OFF),
            value -> saveBoolean(AppSettings.DIM_ON_SCREEN_OFF, value));
        addToggle(root, tr("OLED 번인 방지", "OLED burn-in protection"),
            tr("표시 전체를 1픽셀 범위에서 매분 천천히 이동", "Shift the overlay slowly within one pixel each minute"),
            prefs.getBoolean(AppSettings.BURN_IN_PROTECTION,
                AppSettings.DEFAULT_BURN_IN_PROTECTION),
            value -> saveBoolean(AppSettings.BURN_IN_PROTECTION, value));
        addSetting(root, tr("배터리 부족 기준", "Low battery threshold"),
            tr("이 값 미만이면 빨간 링·경고 진동 · 기본 15%", "Below this: red ring and warning haptic · default 15%"),
            5, 30, prefs.getInt(AppSettings.LOW_BATTERY_PERCENT, AppSettings.DEFAULT_LOW_BATTERY_PERCENT),
            "%", value -> saveInt(AppSettings.LOW_BATTERY_PERCENT, value));

        sectionTitle(root, tr("색상과 움직임", "Color & motion"), medium);
        addPresetButtons(root, prefs.getInt(
            AppSettings.STYLE_PRESET, AppSettings.DEFAULT_STYLE_PRESET));
        addSetting(root, tr("전체 그래프 애니메이션 속도", "Overall animation speed"),
            tr("0.1~5배 · 회전·도트·충전·아이콘 이동에 적용", "0.1–5× · affects spins, dots, charging, and icon travel"),
            10, 500, prefs.getInt(AppSettings.ANIMATION_SPEED_PERCENT,
                AppSettings.DEFAULT_ANIMATION_SPEED_PERCENT),
            "×", value -> saveInt(AppSettings.ANIMATION_SPEED_PERCENT, value));

        sectionTitle(root, tr("VPN 표시 점", "VPN indicator dot"), medium);
        TextView vpnHint = label(tr("VPN 연결 중 흰 점 · 불투명도 20~100% · 2초 호흡 (배속과 독립)\n위치를 누르면 미리보기에 표시됩니다.",
            "White dot while VPN is active · 20–100% opacity · 2-second breathing (independent of speed)\nTap a position to preview it."),
            12, Color.rgb(145, 151, 161));
        root.addView(vpnHint);
        LinearLayout vpnPositions = new LinearLayout(this);
        String[] cornerNames = UiText.isKorean(this)
            ? new String[] {"좌상", "우상", "좌하", "우하"}
            : new String[] {"Top left", "Top right", "Bottom left", "Bottom right"};
        Button[] cornerButtons = new Button[4];
        Runnable refreshCorners = () -> {
            int selected = prefs.getInt(AppSettings.VPN_DOT_CORNER, AppSettings.DEFAULT_VPN_DOT_CORNER);
            for (int index = 0; index < cornerButtons.length; index++) {
                cornerButtons[index].setText((index == selected ? "✓ " : "") + cornerNames[index]);
                cornerButtons[index].setTextColor(index == selected ? PunchRingView.COLOR_SKY : Color.WHITE);
            }
        };
        for (int index = 0; index < cornerButtons.length; index++) {
            final int corner = index;
            Button button = actionButton(cornerNames[index], Color.rgb(42, 48, 59));
            cornerButtons[index] = button;
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setOnClickListener(view -> {
                saveInt(AppSettings.VPN_DOT_CORNER, corner);
                demoState.vpnActive = true;
                preview.updateStatus(demoState);
                refreshCorners.run();
                scroll.smoothScrollTo(0, 0);
            });
            vpnPositions.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        root.addView(vpnPositions);
        refreshCorners.run();

        sectionTitle(root, tr("오른쪽 상태 아이콘", "Right-side status icon"), medium);
        addSetting(root, tr("아이콘 크기", "Icon size"),
            tr("펀치홀 지름의 0.1~5배 · 방울과 아이콘 함께 확대 · 기본 0.8배", "0.1–5× the cutout diameter · scales bubble and icon · default 0.8×"),
            10, 500, prefs.getInt(AppSettings.ICON_SIZE_PERCENT, 80),
            "×", value -> saveInt(AppSettings.ICON_SIZE_PERCENT, value));
        addSetting(root, tr("링과 아이콘 간격", "Ring-to-icon gap"), tr("오른쪽으로 펼쳐졌을 때의 간격", "Gap when the icon expands to the right"),
            2, 14, prefs.getInt(AppSettings.ICON_GAP_DP, 4),
            " dp", value -> saveInt(AppSettings.ICON_GAP_DP, value));
        addSetting(root, tr("상태 아이콘 표시 시간", "Status icon duration"), tr("분리 후 머무는 시간 · 1~10초 · 속도 설정과 독립", "Time held after separation · 1–10 seconds · independent of speed"),
            1000, 10000, prefs.getInt(AppSettings.ICON_HOLD_MS, 2200),
            "sec", value -> saveInt(AppSettings.ICON_HOLD_MS, value));
        LinearLayout iconTests = new LinearLayout(this);
        for (int event = 1; event <= 6; event++) {
            final int selectedEvent = event;
            String[] labels = UiText.isKorean(this)
                ? new String[] {"", "충전", "완충", "Wi-Fi", "BT", "VPN", "핫스팟"}
                : new String[] {"", "Charge", "Full", "Wi-Fi", "BT", "VPN", "Hotspot"};
            Button test = actionButton(labels[event], Color.rgb(42, 48, 59));
            test.setTextColor(Color.WHITE);
            test.setTextSize(11);
            test.setMinWidth(0);
            test.setMinimumWidth(0);
            test.setOnClickListener(view -> {
                setDemoMode(0);
                demoState.eventKind = selectedEvent;
                demoState.eventStartedElapsed = SystemClock.elapsedRealtime();
                demoState.eventUntilElapsed = demoState.eventStartedElapsed + 10_000L;
                preview.updateStatus(demoState);
                scroll.smoothScrollTo(0, 0);
            });
            iconTests.addView(test, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        root.addView(iconTests);

        LinearLayout connectionTests = new LinearLayout(this);
        String[] connectionLabels = UiText.isKorean(this)
            ? new String[] {"Wi-Fi 회전", "충전 회전", "셀룰러 회전", "도트 변화"}
            : new String[] {"Wi-Fi spin", "Charge spin", "Cellular spin", "Dot change"};
        for (int i = 0; i < connectionLabels.length; i++) {
            final int motion = i;
            Button test = actionButton(connectionLabels[i], Color.rgb(42, 48, 59));
            test.setTextColor(Color.WHITE);
            test.setTextSize(11);
            test.setMinWidth(0);
            test.setMinimumWidth(0);
            test.setOnClickListener(view -> {
                if (motion < 3) preview.previewConnection(motion + 1);
                else {
                    demoState.wifiLevel = demoState.wifiLevel > 1 ? 1 : 4;
                    demoState.cellularLevel = demoState.cellularLevel > 2 ? 2 : 4;
                    preview.updateStatus(demoState);
                }
                scroll.smoothScrollTo(0, 0);
            });
            connectionTests.addView(test, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        root.addView(connectionTests);

        sectionTitle(root, tr("경고와 우선순위", "Warnings & priority"), medium);
        addSetting(root, tr("배터리 온도 경고", "Battery temperature warning"),
            tr("설정 온도 이상으로 올라가면 오른쪽 온도 아이콘 표시", "Show a temperature icon when the battery exceeds this value"),
            35, 50, prefs.getInt(AppSettings.THERMAL_THRESHOLD_C,
                AppSettings.DEFAULT_THERMAL_THRESHOLD_C),
            "°C", value -> saveInt(AppSettings.THERMAL_THRESHOLD_C, value));
        addPriorityEditor(root);

        sectionTitle(root, tr("고속충전 감지", "Fast-charge detection"), medium);
        addSetting(root, tr("고속충전 기준", "Fast-charge threshold"),
            tr("충전기가 알리는 최대 전력이 이 값 이상이면 그라데이션 루프", "Use the gradient loop when the charger's reported maximum power exceeds this value"),
            5, 30, prefs.getInt(AppSettings.FAST_THRESHOLD_WATTS,
                AppSettings.DEFAULT_FAST_THRESHOLD_WATTS),
            " W", value -> saveInt(AppSettings.FAST_THRESHOLD_WATTS, value));

        sectionTitle(root, tr("설정 백업", "Settings backup"), medium);
        LinearLayout backupRow = new LinearLayout(this);
        backupRow.setOrientation(LinearLayout.HORIZONTAL);
        Button backup = actionButton(tr("파일로 내보내기", "Export to file"), PunchRingView.COLOR_SKY);
        backup.setOnClickListener(view -> chooseBackupDestination());
        Button restore = actionButton(tr("파일에서 복원", "Restore from file"), Color.rgb(185, 246, 118));
        restore.setOnClickListener(view -> chooseRestoreFile());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(52), 1f);
        half.setMargins(0, 0, dp(4), small);
        backupRow.addView(backup, half);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(0, dp(52), 1f);
        halfRight.setMargins(dp(4), 0, 0, small);
        backupRow.addView(restore, halfRight);
        root.addView(backupRow);

        sectionTitle(root, tr("업데이트", "Updates"), medium);
        Button update = actionButton(tr("업데이트 확인 · 현재 ", "Check for updates · v") + versionName(),
            Color.rgb(53, 59, 70));
        update.setTextColor(Color.WHITE);
        update.setOnClickListener(view -> UpdateChecker.check(this, true));
        root.addView(update, matchButtonParams(small));
        root.addView(label(tr(
            "6시간마다 GitHub 릴리스를 확인해 새 버전이 있으면 알림을 띄워요. 알림을 누르면 받아서 설치 창을 엽니다.",
            "Checks GitHub releases every 6 hours and notifies you. Tap the notification to download and install."),
            13, Color.rgb(145, 151, 161)));

        TextView note = label(tr(
            "표시 우선순위  고속충전 → 충전 → 배터리 부족(설정값 미만) → 절전 → 일반\n" +
                "네트워크 점은 같은 네 좌표에 셀룰러를 먼저 그리고 Wi-Fi를 위에 겹칩니다.",
            "Display priority  Fast charge → Charging → Low battery (below threshold) → Power saver → Normal\n" +
                "Cellular dots are drawn first at the same four positions, with Wi-Fi layered above."),
            13, Color.rgb(145, 151, 161));
        note.setLineSpacing(0, 1.25f);
        note.setPadding(0, medium, 0, 0);
        root.addView(note);

        setContentView(scroll);
        PanelTheme.applyFont(scroll);
        refreshPermissionState();
        refreshContrastState();
    }


    private void requestRuntimePermissionsThenStart() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.READ_PHONE_STATE);
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(missing, Manifest.permission.NEARBY_WIFI_DEVICES);
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (missing.isEmpty()) startOverlayIfAllowed();
        else requestPermissions(missing.toArray(new String[0]), PERMISSIONS_REQUEST);
    }

    private void addIfMissing(List<String> permissions, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void startOverlayIfAllowed() {
        if (!Settings.canDrawOverlays(this)) return;
        AppSettings.prefs(this).edit().putBoolean(AppSettings.ENABLED, true).apply();
        Intent intent = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_START);
        startForegroundService(intent);
        refreshPermissionState();
    }

    private void refreshPermissionState() {
        if (permissionStatus == null || startButton == null) return;
        boolean permission = Settings.canDrawOverlays(this);
        boolean enabled = AppSettings.prefs(this).getBoolean(AppSettings.ENABLED, false);
        if (!permission) {
            permissionStatus.setText(tr("● 화면 위 표시 권한이 필요합니다", "● Display-over-other-apps permission required"));
            permissionStatus.setTextColor(Color.rgb(255, 159, 10));
            startButton.setText(tr("화면 위 표시 권한 허용 및 시작", "Allow overlay and start"));
        } else if (enabled) {
            permissionStatus.setText(tr("● Punch Ring 실행 중", "● Punch Ring is running"));
            permissionStatus.setTextColor(PunchRingView.COLOR_LIME);
            startButton.setText(tr("오버레이 다시 시작", "Restart overlay"));
        } else {
            permissionStatus.setText(tr("● 권한 허용됨 · 오버레이 꺼짐", "● Permission granted · overlay off"));
            permissionStatus.setTextColor(PunchRingView.COLOR_SKY);
            startButton.setText(tr("Punch Ring 시작", "Start Punch Ring"));
        }
    }

    private void refreshContrastState() {
        if (contrastStatus == null) return;
        boolean enabled = isContrastServiceEnabled();
        if (enabled) {
            boolean light = AppSettings.prefs(this).getBoolean(
                AppSettings.BACKGROUND_IS_LIGHT, false);
            contrastStatus.setText(tr("● 링 터치 활성 · 자동 대비 현재 감지: ",
                    "● Ring touch active · detected background: ")
                + (light ? tr("밝은 배경 / 진한 색", "light / saturated colors")
                    : tr("어두운 배경 / 밝은 색", "dark / bright colors")));
            contrastStatus.setTextColor(PunchRingView.COLOR_LIME);
        } else {
            contrastStatus.setText(tr(
                "● 링 터치와 자동 대비를 쓰려면 접근성 서비스를 켜야 합니다\n화면은 저장·전송하지 않고 상단 밝기 값만 계산합니다.",
                "● Enable the accessibility service for ring touch and auto contrast\nThe screen is never saved or sent; only top-area brightness is measured."));
            contrastStatus.setTextColor(PunchRingView.COLOR_ORANGE);
        }
    }

    private boolean isContrastServiceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() != null
                    && info.getResolveInfo().serviceInfo != null
                    && getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && BackgroundContrastService.class.getName().equals(
                        info.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private void addDemoButton(LinearLayout row, String text, int mode) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(4, 0, 4, 0);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(42, 48, 59)));
        button.setOnClickListener(view -> setDemoMode(mode));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        row.addView(button, params);
    }

    private void setDemoMode(int mode) {
        demoState.batteryPercent = mode == 4 ? 12 : 72;
        demoState.charging = mode == 1 || mode == 2;
        demoState.fastCharging = mode == 2;
        demoState.powerSave = mode == 3;
        demoState.batteryTemperatureC = mode == 7 ? 45f : 34f;
        demoState.networkConnected = true;
        demoState.networkValidated = mode != 5;
        demoState.wifiConnected = true;
        demoState.eventKind = mode == 8 ? StatusState.EVENT_BLUETOOTH : StatusState.EVENT_NONE;
        demoState.eventStartedElapsed = SystemClock.elapsedRealtime();
        demoState.eventUntilElapsed = mode == 8
            ? demoState.eventStartedElapsed + 5000L : 0L;
        preview.updateStatus(demoState);
    }

    private void showLiveState(StatusState state) {
        if (liveStatus == null) return;
        String batteryMode;
        if (state.fastCharging) batteryMode = String.format(
            UiText.formatLocale(this), tr("고속충전 %.1fW", "Fast charge %.1fW"), state.chargeWatts);
        else if (state.charging) batteryMode = String.format(
            UiText.formatLocale(this), tr("충전 %.1fW", "Charging %.1fW"), state.chargeWatts);
        else if (state.powerSave) batteryMode = tr("절전", "Power saver");
        else batteryMode = tr("일반", "Normal");
        String internet = !state.networkConnected ? tr("오프라인", "Offline")
            : state.networkValidated ? tr("인터넷 정상", "Internet OK") : tr("인터넷 확인 필요", "Check internet");
        String eta = state.charging && state.chargeRemainingMillis > 0
            ? tr("  ·  완충 약 ", "  ·  Full in about ")
                + Math.max(1L, Math.round(state.chargeRemainingMillis / 60_000d)) + tr("분", " min")
            : "";
        liveStatus.setText(tr("실시간 값  ·  배터리 ", "Live  ·  Battery ") + state.batteryPercent + "% " + batteryMode
            + String.format(UiText.formatLocale(this), " · %.1f°C", state.batteryTemperatureC)
            + eta
            + tr("  ·  셀룰러 ", "  ·  Cellular ") + state.cellularLevel + "/4"
            + "  ·  Wi-Fi " + state.wifiLevel + "/4 · " + internet
            + (state.vpnActive ? "  ·  VPN" : ""));
    }

    private interface ValueListener { void onValue(int value); }
    private interface BooleanListener { void onValue(boolean value); }

    private static final class SettingControl {
        final LinearLayout card;
        final SeekBar seek;
        final TextView valueText;
        final int min;
        final String suffix;

        SettingControl(LinearLayout card, SeekBar seek, TextView valueText, int min, String suffix) {
            this.card = card;
            this.seek = seek;
            this.valueText = valueText;
            this.min = min;
            this.suffix = suffix;
        }
    }

    private SettingControl addSetting(LinearLayout root, String title, String detail, int min, int max,
            int initial, String suffix, ValueListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(10));
        card.setBackground(rounded(Color.rgb(18, 22, 29), 18));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(8));
        root.addView(card, cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = label(title, 15, Color.WHITE);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView valueText = label(formatSettingValue(initial, suffix), 14, PunchRingView.COLOR_SKY);
        valueText.setGravity(Gravity.END);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(valueText, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(header);

        TextView hint = label(detail, 12, Color.rgb(133, 140, 151));
        hint.setPadding(0, dp(3), 0, dp(4));
        card.addView(hint);

        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(initial - min);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(PunchRingView.COLOR_SKY));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(PunchRingView.COLOR_SKY));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = min + progress;
                valueText.setText(formatSettingValue(value, suffix));
                if (fromUser) listener.onValue(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        card.addView(seek, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SettingControl(card, seek, valueText, min, suffix);
    }

    private Switch addToggle(LinearLayout root, String title, String detail,
            boolean initial, BooleanListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(13), dp(12), dp(13));
        card.setBackground(rounded(Color.rgb(18, 22, 29), 18));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(8));
        root.addView(card, cardParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(title, 15, Color.WHITE);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextView hint = label(detail, 12, Color.rgb(133, 140, 151));
        hint.setPadding(0, dp(3), dp(8), 0);
        copy.addView(name);
        copy.addView(hint);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(initial);
        toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(PunchRingView.COLOR_SKY));
        toggle.setOnCheckedChangeListener((button, checked) -> listener.onValue(checked));
        card.addView(toggle, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return toggle;
    }

    private String formatSettingValue(int value, String suffix) {
        if ("×".equals(suffix)) return String.format(UiText.formatLocale(this), "%.2f×", value / 100f);
        if ("sec".equals(suffix)) return String.format(UiText.formatLocale(this), "%.2f%s",
            value / 1000f, tr("초", " sec"));
        if (suffix.isEmpty()) return String.format(UiText.formatLocale(this), "%.1f dp", value / 10f);
        return value + suffix;
    }

    private void saveInt(String key, int value) {
        if (isProfileBaseKey(key)) AppSettings.putProfileInt(this, key, value);
        else AppSettings.prefs(this).edit().putInt(key, value).apply();
        preview.reloadSettings();
    }

    private void saveBoolean(String key, boolean value) {
        AppSettings.prefs(this).edit().putBoolean(key, value).apply();
        preview.reloadSettings();
    }

    private void saveBatteryDiameter(int value) {
        SharedPreferences prefs = AppSettings.prefs(this);
        boolean inner = AppSettings.isInnerDisplay(this);
        SharedPreferences.Editor editor = prefs.edit().putInt(
            AppSettings.profileKey(AppSettings.DIAMETER_DP, inner), value);
        if (prefs.getBoolean(AppSettings.profileKey(AppSettings.SYNC_DIAMETERS, inner),
            AppSettings.DEFAULT_SYNC_DIAMETERS)) {
            editor.putInt(AppSettings.profileKey(AppSettings.DOT_DIAMETER_DP, inner), value);
            if (dotDiameterControl != null) {
                dotDiameterControl.seek.setProgress(value - dotDiameterControl.min);
                dotDiameterControl.valueText.setText(value + dotDiameterControl.suffix);
            }
        }
        editor.apply();
        preview.reloadSettings();
    }

    private void setDiameterSync(boolean enabled) {
        SharedPreferences prefs = AppSettings.prefs(this);
        boolean inner = AppSettings.isInnerDisplay(this);
        SharedPreferences.Editor editor = prefs.edit().putBoolean(
            AppSettings.profileKey(AppSettings.SYNC_DIAMETERS, inner), enabled);
        if (enabled) {
            int batteryDiameter = prefs.getInt(AppSettings.profileKey(
                AppSettings.DIAMETER_DP, inner), AppSettings.DEFAULT_DIAMETER_DP);
            editor.putInt(AppSettings.profileKey(
                AppSettings.DOT_DIAMETER_DP, inner), batteryDiameter);
            if (dotDiameterControl != null) {
                dotDiameterControl.seek.setProgress(batteryDiameter - dotDiameterControl.min);
                dotDiameterControl.valueText.setText(batteryDiameter + dotDiameterControl.suffix);
            }
        }
        editor.apply();
        updateDiameterSyncUi();
        preview.reloadSettings();
    }

    private void updateDiameterSyncUi() {
        if (dotDiameterControl == null) return;
        boolean synced = AppSettings.getProfileBoolean(
            this, AppSettings.SYNC_DIAMETERS, AppSettings.DEFAULT_SYNC_DIAMETERS);
        dotDiameterControl.seek.setEnabled(!synced);
        dotDiameterControl.card.setAlpha(synced ? 0.55f : 1f);
    }

    private void sectionTitle(LinearLayout root, String title, int topPadding) {
        TextView view = label(title, 19, Color.WHITE);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, topPadding, 0, dp(10));
        root.addView(view);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private Button actionButton(String text, int tint) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.rgb(6, 12, 16));
        button.setAllCaps(false);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tint));
        return button;
    }

    private LinearLayout.LayoutParams matchButtonParams(int marginBottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.setMargins(0, 0, 0, marginBottom);
        return params;
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isProfileBaseKey(String key) {
        return AppSettings.DIAMETER_DP.equals(key)
            || AppSettings.DOT_DIAMETER_DP.equals(key)
            || AppSettings.OFFSET_X_DP.equals(key)
            || AppSettings.OFFSET_Y_DP.equals(key)
            || AppSettings.STROKE_TENTHS_DP.equals(key);
    }

    private void addPresetButtons(LinearLayout root, int selected) {
        String[] names = UiText.isKorean(this)
            ? new String[] {"기본", "네온", "최소", "무채색", "색각보정"}
            : new String[] {"Default", "Neon", "Minimal", "Mono", "Color aid"};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < names.length; index++) {
            final int preset = index;
            Button button = actionButton((selected == index ? "✓ " : "") + names[index],
                selected == index ? PunchRingView.COLOR_SKY : Color.rgb(53, 59, 70));
            button.setTextSize(11);
            if (selected != index) button.setTextColor(Color.WHITE);
            button.setPadding(2, 0, 2, 0);
            button.setOnClickListener(view -> {
                AppSettings.prefs(this).edit().putInt(AppSettings.STYLE_PRESET, preset).apply();
                preview.reloadSettings();
                for (int child = 0; child < row.getChildCount(); child++) {
                    Button item = (Button) row.getChildAt(child);
                    boolean active = child == preset;
                    item.setText((active ? "✓ " : "") + names[child]);
                    item.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        active ? PunchRingView.COLOR_SKY : Color.rgb(53, 59, 70)));
                    item.setTextColor(active ? Color.rgb(6, 12, 16) : Color.WHITE);
                }
                Toast.makeText(this, names[preset] + tr(" 프리셋 적용", " preset applied"), Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
            params.setMargins(dp(2), 0, dp(2), dp(8));
            row.addView(button, params);
        }
        root.addView(row);
    }

    private void addPriorityEditor(LinearLayout root) {
        TextView help = label(tr("동시에 여러 상태가 발생할 때 외곽 링으로 먼저 표시할 항목",
                "Choose which state takes priority on the outer ring"),
            12, Color.rgb(133, 140, 151));
        help.setPadding(0, 0, 0, dp(6));
        root.addView(help);
        priorityList = new LinearLayout(this);
        priorityList.setOrientation(LinearLayout.VERTICAL);
        root.addView(priorityList);
        renderPriorityEditor();
    }

    private void renderPriorityEditor() {
        if (priorityList == null) return;
        priorityList.removeAllViews();
        List<String> order = AppSettings.priorityOrder(this);
        for (int index = 0; index < order.size(); index++) {
            final int position = index;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(6), dp(8), dp(6));
            row.setBackground(rounded(Color.rgb(18, 22, 29), 14));
            TextView name = label((index + 1) + "  " + priorityLabel(order.get(index)),
                14, Color.WHITE);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(40), 1f));
            Button up = smallOrderButton("↑");
            up.setEnabled(index > 0);
            up.setOnClickListener(view -> movePriority(position, -1));
            Button down = smallOrderButton("↓");
            down.setEnabled(index < order.size() - 1);
            down.setOnClickListener(view -> movePriority(position, 1));
            row.addView(up, new LinearLayout.LayoutParams(dp(48), dp(40)));
            row.addView(down, new LinearLayout.LayoutParams(dp(48), dp(40)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, dp(5));
            priorityList.addView(row, params);
        }
    }

    private Button smallOrderButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(53, 59, 70)));
        return button;
    }

    private void movePriority(int from, int direction) {
        List<String> order = new ArrayList<>(AppSettings.priorityOrder(this));
        int to = from + direction;
        if (from < 0 || from >= order.size() || to < 0 || to >= order.size()) return;
        Collections.swap(order, from, to);
        AppSettings.prefs(this).edit()
            .putString(AppSettings.PRIORITY_ORDER, String.join(",", order)).apply();
        renderPriorityEditor();
        preview.reloadSettings();
    }

    private String priorityLabel(String key) {
        if ("temperature".equals(key)) return tr("배터리 온도", "Battery temperature");
        if ("network".equals(key)) return tr("네트워크 이상", "Network issue");
        if ("event".equals(key)) return tr("상태 이벤트", "Status event");
        return tr("배터리 경고", "Battery warning");
    }

    private void performAutoCalibration() {
        boolean inner = AppSettings.isInnerDisplay(this);
        int recommended = AppSettings.DEFAULT_DIAMETER_DP;
        WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
        DisplayCutout cutout = insets == null ? null : insets.getDisplayCutout();
        Rect bounds = getWindow().getDecorView().getRootView().getWidth() > 0
            ? CutoutGeometry.bestPunchHole(cutout == null ? null : cutout.getBoundingRects(),
                getWindow().getDecorView().getRootView().getWidth(),
                getWindow().getDecorView().getRootView().getHeight(),
                getResources().getDisplayMetrics().density)
            : null;
        recommended = CutoutGeometry.recommendedDiameterDp(this, bounds);
        SharedPreferences prefs = AppSettings.prefs(this);
        String suffixDiameter = AppSettings.profileKey(AppSettings.DIAMETER_DP, inner);
        String suffixDot = AppSettings.profileKey(AppSettings.DOT_DIAMETER_DP, inner);
        prefs.edit()
            .putInt(suffixDiameter, recommended)
            .putInt(suffixDot, recommended)
            .putBoolean(AppSettings.profileKey(AppSettings.SYNC_DIAMETERS, inner), true)
            .putInt(AppSettings.profileKey(AppSettings.OFFSET_X_DP, inner), 0)
            .putInt(AppSettings.profileKey(AppSettings.OFFSET_Y_DP, inner),
                AppSettings.DEFAULT_OFFSET_Y_DP)
            .putBoolean(AppSettings.SHOW_LAYOUT_GUIDE, true)
            .apply();
        Toast.makeText(this, (inner ? tr("큰", "Large") : tr("작은", "Compact"))
                + tr(" 화면 자동 맞춤 완료 · ", " display auto-fit complete · ") + recommended
                + tr("dp · 가이드 켜짐", "dp · guide enabled"),
            Toast.LENGTH_LONG).show();
        recreate();
    }

    private void chooseBackupDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "punch-ring-settings.json");
        startActivityForResult(intent, BACKUP_CREATE_REQUEST);
    }

    private void chooseRestoreFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json");
        startActivityForResult(intent, BACKUP_OPEN_REQUEST);
    }

    private void exportSettings(Uri uri) {
        try {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : AppSettings.prefs(this).getAll().entrySet()) {
                if (!AppSettings.isImportableKey(entry.getKey())) continue;
                values.put(entry.getKey(), entry.getValue());
            }
            JSONObject root = new JSONObject();
            root.put("format", "punch-ring-settings");
            root.put("version", 1);
            root.put("values", values);
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IllegalStateException("output unavailable");
                output.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, tr("설정 백업 완료", "Settings backup complete"), Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, tr("설정 백업 실패", "Settings backup failed"), Toast.LENGTH_LONG).show();
        }
    }

    private void importSettings(Uri uri) {
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (content.length() + line.length() > 64 * 1024) {
                        throw new IllegalArgumentException("too large");
                    }
                    content.append(line);
                }
            }
            JSONObject root = new JSONObject(content.toString());
            if (!"punch-ring-settings".equals(root.optString("format"))) {
                throw new IllegalArgumentException("wrong format");
            }
            JSONObject values = root.getJSONObject("values");
            SharedPreferences.Editor editor = AppSettings.prefs(this).edit();
            Iterator<String> keys = values.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!AppSettings.isImportableKey(key)) continue;
                Object value = values.get(key);
                if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else if (value instanceof Number) {
                    editor.putInt(key, sanitizeImportedInt(key, ((Number) value).intValue()));
                }
                else if (value instanceof String) editor.putString(key, (String) value);
            }
            editor.apply();
            Toast.makeText(this, tr("설정 복원 완료", "Settings restored"), Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Exception error) {
            Toast.makeText(this, tr("올바른 Punch Ring 백업 파일이 아닙니다", "This is not a valid Punch Ring backup"), Toast.LENGTH_LONG).show();
        }
    }

    private int sanitizeImportedInt(String key, int value) {
        if (key.startsWith(AppSettings.DIAMETER_DP)
                || key.startsWith(AppSettings.DOT_DIAMETER_DP)) return clamp(value, 14, 50);
        if (key.startsWith(AppSettings.OFFSET_X_DP)) return clamp(value, -80, 80);
        if (key.startsWith(AppSettings.OFFSET_Y_DP)) return clamp(value, -60, 100);
        if (key.startsWith(AppSettings.STROKE_TENTHS_DP)) return clamp(value, 10, 50);
        if (AppSettings.FAST_THRESHOLD_WATTS.equals(key)) return clamp(value, 5, 30);
        if (AppSettings.THERMAL_THRESHOLD_C.equals(key)) return clamp(value, 35, 50);
        if (AppSettings.STYLE_PRESET.equals(key)) return clamp(value, 0, 4);
        if (AppSettings.ANIMATION_SPEED_PERCENT.equals(key)) return clamp(value, 10, 500);
        if (AppSettings.ICON_SIZE_PERCENT.equals(key)) return clamp(value, 10, 500);
        if (AppSettings.ICON_GAP_DP.equals(key)) return clamp(value, 2, 14);
        if (AppSettings.ICON_HOLD_MS.equals(key)) return clamp(value, 1000, 10000);
        if (AppSettings.VPN_DOT_CORNER.equals(key)) return clamp(value, 0, 3);
        return value;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String tr(String korean, String english) {
        return UiText.get(this, korean, english);
    }
}
