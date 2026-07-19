package com.poersmart.charge;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Locale;

public class AutoBluetoothReceiver extends BroadcastReceiver {
    private static final String PREFS = "poersmart_key";
    private static final long COOLDOWN_MS = 60000L;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("autoBluetooth", false)) return;
        if (normalizeHex(prefs.getString("mac", "")).length() != 12) return;

        long now = System.currentTimeMillis();
        long last = prefs.getLong("lastBluetoothUnlockAt", 0L);
        if (last > 0L && now >= last && now - last < COOLDOWN_MS) return;

        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String name = "";
        String address = "";
        if (device != null) {
            try {
                name = device.getName();
            } catch (Exception ignored) {}
            try {
                address = device.getAddress();
            } catch (Exception ignored) {}
        }
        if (name == null) name = "";
        if (address == null) address = "";

        String trigger = prefs.getString("autoBluetoothTrigger", "");
        trigger = trigger == null ? "" : trigger.trim().toLowerCase(Locale.US);
        String source = (name + " " + address).toLowerCase(Locale.US);
        if (trigger.length() > 0 && !source.contains(trigger)) return;

        try {
            Intent service = new Intent(context, KeyBroadcastService.class);
            service.setAction(KeyBroadcastService.ACTION_UNLOCK);
            context.startService(service);
            prefs.edit()
                    .putLong("lastBluetoothUnlockAt", now)
                    .putString("lastBluetoothSource", source)
                    .apply();
        } catch (RuntimeException e) {
            prefs.edit()
                    .putString("lastBluetoothStartError", safeMessage(e))
                    .putLong("lastBluetoothStartErrorAt", now)
                    .apply();
            NotificationWatchdogReceiver.scheduleSoon(context);
        }
    }

    private static String normalizeHex(String value) {
        return value == null ? "" : value.replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.length() == 0
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
    }
}
