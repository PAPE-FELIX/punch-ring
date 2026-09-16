package com.pape.punchring;

final class StatusState {
    static final int EVENT_NONE = 0;
    static final int EVENT_CHARGING = 1;
    static final int EVENT_FULL = 2;
    static final int EVENT_NETWORK = 3;
    static final int EVENT_BLUETOOTH = 4;
    static final int EVENT_VPN = 5;
    static final int EVENT_HOTSPOT = 6;

    int batteryPercent = 100;
    boolean charging;
    boolean fastCharging;
    boolean plugged;
    boolean cellularRegistered;
    boolean cellularDataConnected;
    int cellularNetworkType;
    long wifiNetworkId = -1;
    boolean powerSave;
    boolean screenInteractive = true;
    int cellularLevel;
    int wifiLevel;
    boolean wifiConnected;
    boolean networkConnected = true;
    boolean networkValidated = true;
    boolean vpnActive;
    float chargeWatts;
    float batteryTemperatureC;
    long chargeRemainingMillis = -1L;
    int eventKind = EVENT_NONE;
    long eventStartedElapsed;
    long eventUntilElapsed;

    StatusState copy() {
        StatusState value = new StatusState();
        value.batteryPercent = batteryPercent;
        value.charging = charging;
        value.fastCharging = fastCharging;
        value.plugged = plugged;
        value.cellularRegistered = cellularRegistered;
        value.cellularDataConnected = cellularDataConnected;
        value.cellularNetworkType = cellularNetworkType;
        value.wifiNetworkId = wifiNetworkId;
        value.powerSave = powerSave;
        value.screenInteractive = screenInteractive;
        value.cellularLevel = cellularLevel;
        value.wifiLevel = wifiLevel;
        value.wifiConnected = wifiConnected;
        value.networkConnected = networkConnected;
        value.networkValidated = networkValidated;
        value.vpnActive = vpnActive;
        value.chargeWatts = chargeWatts;
        value.batteryTemperatureC = batteryTemperatureC;
        value.chargeRemainingMillis = chargeRemainingMillis;
        value.eventKind = eventKind;
        value.eventStartedElapsed = eventStartedElapsed;
        value.eventUntilElapsed = eventUntilElapsed;
        return value;
    }
}
