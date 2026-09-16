package com.pape.punchring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = AppSettings.prefs(context).getBoolean(AppSettings.ENABLED, false);
        if (!enabled || !Settings.canDrawOverlays(context)) return;
        Intent service = new Intent(context, OverlayService.class).setAction(OverlayService.ACTION_START);
        context.startForegroundService(service);
    }
}
