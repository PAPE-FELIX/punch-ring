package com.pape.punchring;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothDevice;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

final class StatusMonitor {
    interface Listener {
        void onStatusChanged(StatusState state);
    }

    private final Context context;
    private final Listener listener;
    private final StatusState state = new StatusState();
    private final ConnectivityManager connectivity;
    private final TelephonyManager telephony;
    private final PowerManager power;
    private final BatteryManager battery;
    private final WifiManager wifi;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean batteryInitialized;
    private boolean networkInitialized;
    private boolean previousCharging;
    private boolean previousFull;
    private boolean previousNetworkConnected;
    private boolean previousNetworkValidated;
    private boolean previousVpn;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                updateBattery(intent);
            } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
                state.powerSave = power.isPowerSaveMode();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                state.screenInteractive = power == null || power.isInteractive();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                    || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                fireEvent(StatusState.EVENT_BLUETOOTH, 2800L);
            } else if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                fireEvent(StatusState.EVENT_HOTSPOT, 3000L);
            } else {
                updateNetwork();
            }
            dispatch();
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback =
        new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { updateNetworkAndDispatch(); }
            @Override public void onLost(Network network) { updateNetworkAndDispatch(); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                updateNetworkAndDispatch();
            }
        };

    private final TelephonyCallback signalCallback = new SignalCallback();
    private final ConnectivityManager.NetworkCallback wifiCallback =
        new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { updateNetworkAndDispatch(); }
            @Override public void onLost(Network network) { updateNetworkAndDispatch(); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                updateNetworkAndDispatch();
            }
        };

    private final class SignalCallback extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.ServiceStateListener, TelephonyCallback.DataConnectionStateListener {
        @Override public void onServiceStateChanged(ServiceState service) {
            state.cellularRegistered = service.getState() == ServiceState.STATE_IN_SERVICE;
            if (!state.cellularRegistered) state.cellularLevel = 0;
            dispatch();
        }
        @Override public void onDataConnectionStateChanged(int connectionState, int networkType) {
            state.cellularDataConnected = connectionState == TelephonyManager.DATA_CONNECTED;
            state.cellularNetworkType = networkType;
            dispatch();
        }
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            state.cellularLevel = clamp(signalStrength.getLevel());
            dispatch();
        }
    }

    StatusMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        connectivity = this.context.getSystemService(ConnectivityManager.class);
        telephony = this.context.getSystemService(TelephonyManager.class);
        power = this.context.getSystemService(PowerManager.class);
        battery = this.context.getSystemService(BatteryManager.class);
        wifi = this.context.getSystemService(WifiManager.class);
    }

    void start() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction("android.net.wifi.RSSI_CHANGED");
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) {
            sticky = context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            sticky = context.registerReceiver(receiver, filter);
        }
        if (sticky != null) updateBattery(sticky);

        state.powerSave = power != null && power.isPowerSaveMode();
        state.screenInteractive = power == null || power.isInteractive();
        updateNetwork();
        try {
            if (connectivity != null) connectivity.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {}
        try {
            if (connectivity != null) connectivity.registerNetworkCallback(
                new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCallback);
        } catch (RuntimeException ignored) {}

        if (telephony != null && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                telephony.registerTelephonyCallback(context.getMainExecutor(), signalCallback);
                SignalStrength initial = telephony.getSignalStrength();
                if (initial != null) state.cellularLevel = clamp(initial.getLevel());
            } catch (SecurityException ignored) {}
        }
        dispatch();
    }

    void stop() {
        try { context.unregisterReceiver(receiver); } catch (RuntimeException ignored) {}
        try {
            if (connectivity != null) connectivity.unregisterNetworkCallback(networkCallback);
        } catch (RuntimeException ignored) {}
        try {
            if (connectivity != null) connectivity.unregisterNetworkCallback(wifiCallback);
        } catch (RuntimeException ignored) {}
        try {
            if (telephony != null) telephony.unregisterTelephonyCallback(signalCallback);
        } catch (RuntimeException ignored) {}
    }

    private void updateBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        state.batteryPercent = scale > 0 ? clampPercent(Math.round(level * 100f / scale)) : 100;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        state.charging = status == BatteryManager.BATTERY_STATUS_CHARGING;
        state.plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        state.chargeWatts = estimateChargeWatts(intent);
        int threshold = AppSettings.prefs(context).getInt(
            AppSettings.FAST_THRESHOLD_WATTS, AppSettings.DEFAULT_FAST_THRESHOLD_WATTS);

        boolean samsungFastFlag = intent.getBooleanExtra("isFastCharge", false)
            || intent.getIntExtra("high_voltage_charger", 0) > 0
            || intent.getIntExtra("charger_type", 0) >= 2;
        state.fastCharging = state.charging && (samsungFastFlag || state.chargeWatts >= threshold);
        state.batteryTemperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        state.chargeRemainingMillis = state.charging ? estimateChargeRemaining() : -1L;

        boolean full = status == BatteryManager.BATTERY_STATUS_FULL || state.batteryPercent >= 100;
        if (batteryInitialized) {
            if (!previousCharging && state.charging) fireEvent(StatusState.EVENT_CHARGING, 8000L);
            if (!previousFull && full) fireEvent(StatusState.EVENT_FULL, 4200L);
        }
        previousCharging = state.charging;
        previousFull = full;
        batteryInitialized = true;
    }

    private float estimateChargeWatts(Intent intent) {
        // These long-standing BATTERY_CHANGED extras are emitted by Samsung but are
        // not exposed as public constants in every Android SDK revision.
        long maxMicroAmps = intent.getIntExtra("max_charging_current", 0);
        long maxMicroVolts = intent.getIntExtra("max_charging_voltage", 0);
        if (maxMicroAmps > 0 && maxMicroVolts > 0) {
            return (float) ((double) maxMicroAmps * (double) maxMicroVolts / 1_000_000_000_000d);
        }

        long currentMicroAmps = battery != null
            ? Math.abs(battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) : 0;
        long voltageMilliVolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        if (currentMicroAmps > 0 && voltageMilliVolts > 0) {
            return (float) ((double) currentMicroAmps * (double) voltageMilliVolts / 1_000_000_000d);
        }
        return 0f;
    }

    private long estimateChargeRemaining() {
        if (battery == null) return -1L;
        try {
            long platformEstimate = battery.computeChargeTimeRemaining();
            if (platformEstimate > 0L) return platformEstimate;
        } catch (RuntimeException ignored) {}
        long chargeMicroAh = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long currentMicroAmps = Math.abs(
            battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        if (state.batteryPercent <= 0 || state.batteryPercent >= 100
                || chargeMicroAh <= 0 || currentMicroAmps < 50_000) return -1L;
        double totalMicroAh = chargeMicroAh * 100d / state.batteryPercent;
        long estimate = Math.round((totalMicroAh - chargeMicroAh) / currentMicroAmps * 3_600_000d);
        return estimate > 0L && estimate <= 24L * 60L * 60L * 1000L ? estimate : -1L;
    }

    private void updateNetworkAndDispatch() {
        mainHandler.post(() -> {
            updateNetwork();
            dispatch();
        });
    }

    private void updateNetwork() {
        state.wifiLevel = 0;
        state.wifiConnected = false;
        state.wifiNetworkId = -1;
        state.networkConnected = false;
        state.networkValidated = false;
        state.vpnActive = false;
        if (connectivity == null) return;
        try {
            Network active = connectivity.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
            if (caps != null) {
                state.networkConnected = true;
                state.networkValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                state.vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                state.wifiConnected = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            }

            // A VPN is often the default network, so inspect every network to retain
            // the underlying Wi-Fi strength while Tailscale or another VPN is active.
            for (Network candidate : connectivity.getAllNetworks()) {
                NetworkCapabilities candidateCaps = connectivity.getNetworkCapabilities(candidate);
                if (candidateCaps == null
                        || !candidateCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
                state.wifiConnected = true;
                state.wifiNetworkId = candidate.getNetworkHandle();
                int rssi = candidateCaps.getSignalStrength();
                if (candidateCaps.getTransportInfo() instanceof WifiInfo) {
                    int wifiRssi = ((WifiInfo) candidateCaps.getTransportInfo()).getRssi();
                    if (wifiRssi > -127) rssi = wifiRssi;
                }
                state.wifiLevel = wifiDots(rssi);
                break;
            }

            // A non-bypassable VPN can hide its underlying Network from ordinary
            // apps. The active VPN still reports WIFI, and WifiManager provides the
            // physical radio RSSI for the dots.
            if (state.wifiConnected && state.wifiLevel == 0 && wifi != null) {
                WifiInfo info = wifi.getConnectionInfo();
                if (info != null && info.getRssi() > -127) {
                    state.wifiLevel = wifiDots(info.getRssi());
                    if (state.wifiNetworkId == -1) state.wifiNetworkId = info.getNetworkId();
                }
            }

            if (networkInitialized) {
                if (previousNetworkConnected != state.networkConnected
                        || previousNetworkValidated != state.networkValidated) {
                    fireEvent(StatusState.EVENT_NETWORK, 3000L);
                } else if (previousVpn != state.vpnActive) {
                    fireEvent(StatusState.EVENT_VPN, 3000L);
                }
            }
            previousNetworkConnected = state.networkConnected;
            previousNetworkValidated = state.networkValidated;
            previousVpn = state.vpnActive;
            networkInitialized = true;
        } catch (SecurityException ignored) {}
    }

    private void fireEvent(int kind, long durationMs) {
        state.eventKind = kind;
        state.eventStartedElapsed = SystemClock.elapsedRealtime();
        state.eventUntilElapsed = state.eventStartedElapsed + durationMs;
    }

    private void dispatch() {
        StatusState snapshot = state.copy();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onStatusChanged(snapshot);
        } else {
            mainHandler.post(() -> listener.onStatusChanged(snapshot));
        }
    }

    private static int wifiDots(int rssi) {
        if (rssi >= 0 && rssi <= 4) return rssi;
        if (rssi >= -55) return 4;
        if (rssi >= -65) return 3;
        if (rssi >= -75) return 2;
        if (rssi >= -85) return 1;
        return 0;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(4, value));
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
