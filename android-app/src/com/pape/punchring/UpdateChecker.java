package com.pape.punchring;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * OTA updates from GitHub releases. Android does not let a normal app install silently, so the flow is:
 * check latest release → notify → download into a PackageInstaller session → system confirm dialog.
 */
final class UpdateChecker {
    static final String ACTION_INSTALL = "com.pape.punchring.INSTALL_UPDATE";
    static final String EXTRA_URL = "apk_url";
    /** 공개판은 GitHub 릴리스, 사내판은 인사이드 서버를 본다 (사내판은 이 상수만 바꾼다). */
    static final String SOURCE = "https://api.github.com/repos/PAPE-FELIX/punch-ring/releases/latest";
    private static final String CHANNEL = "punch_ring_updates";
    private static final String LAST_CHECK = "update_last_check";
    private static final long INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private UpdateChecker() {}

    /** Checks in the background. Automatic checks are throttled to once every 6 hours. */
    static void check(Context context, boolean userInitiated) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        if (!userInitiated && now - AppSettings.prefs(app).getLong(LAST_CHECK, 0L) < INTERVAL_MS) return;
        AppSettings.prefs(app).edit().putLong(LAST_CHECK, now).apply();
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(get(SOURCE));
                String latest = release.optString("tag_name", release.optString("version", "")).replaceFirst("^v", "");
                String url = release.optString("url", null);   // 사내 배포 manifest: {"version":"0.17.1","url":"…apk"}
                JSONArray assets = release.optJSONArray("assets");
                for (int i = 0; url == null && assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break; }
                }
                String current = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
                if (url != null && compare(latest, current) > 0) {
                    notifyUpdate(app, latest, url);
                    if (userInitiated) toast(app, "새 버전 " + latest + " — 알림을 눌러 설치하세요 / Update available");
                } else if (userInitiated) {
                    toast(app, "최신 버전이에요 (" + current + ") / Up to date");
                }
            } catch (Exception e) {
                if (userInitiated) toast(app, "업데이트 확인 실패 / Update check failed");
            }
        }, "punch-ring-update-check").start();
    }

    /** Downloads the APK straight into an install session; the system then shows its install dialog. */
    static void install(Context context, String apkUrl) {
        Context app = context.getApplicationContext();
        toast(app, "업데이트 받는 중… / Downloading update…");
        new Thread(() -> {
            try {
                PackageInstaller installer = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(app.getPackageName());
                int sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    HttpURLConnection conn = open(apkUrl);
                    try (InputStream in = conn.getInputStream();
                         OutputStream out = session.openWrite("base.apk", 0, -1)) {
                        byte[] buf = new byte[64 * 1024];
                        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                        session.fsync(out);
                    }
                    Intent result = new Intent(app, UpdateReceiver.class);
                    PendingIntent pending = PendingIntent.getBroadcast(app, sessionId, result,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    session.commit(pending.getIntentSender());
                }
            } catch (Exception e) {
                toast(app, "업데이트 설치 실패 / Update failed: " + e.getMessage());
            }
        }, "punch-ring-update-install").start();
    }

    /** 0.17.0 vs 0.16.2 → positive when a is newer. */
    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9].*$", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static void notifyUpdate(Context app, String version, String url) {
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "업데이트 / Updates", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(app, MainActivity.class).setAction(ACTION_INSTALL).putExtra(EXTRA_URL, url)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(app, 7001, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Punch Ring " + version)
            .setContentText("새 버전이 있어요. 눌러서 업데이트 / Tap to update")
            .setContentIntent(tap).setAutoCancel(true).build();
        nm.notify(7001, n);
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "PunchRing-Updater");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static void toast(Context app, String text) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, text, Toast.LENGTH_LONG).show());
    }
}
