package com.poersmart.charge;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

public class NotificationWatchdogReceiver extends BroadcastReceiver {
    private static final String ACTION_CHECK = "com.poersmart.charge.action.CHECK_SERVICE";
    private static final String PREFS = "poersmart_key";
    private static final int REQUEST_CODE = 701;
    private static final long CHECK_INTERVAL_MS = 15L * 60L * 1000L;
    private static final long QUICK_RECOVERY_MS = 60L * 1000L;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_CHECK.equals(intent.getAction())) return;

        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("lastWatchdogCheckAt", now)
                .apply();

        // Schedule the next check first so a transient service-start failure does not stop recovery.
        schedule(context);
        try {
            Intent service = new Intent(context, KeyBroadcastService.class);
            service.setAction(KeyBroadcastService.ACTION_SHOW);
            context.startService(service);
        } catch (RuntimeException e) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("lastWatchdogError", safeMessage(e))
                    .putLong("lastWatchdogErrorAt", now)
                    .apply();
            scheduleSoon(context);
        }
    }

    static void schedule(Context context) {
        scheduleAfter(context, CHECK_INTERVAL_MS);
    }

    static void scheduleSoon(Context context) {
        scheduleAfter(context, QUICK_RECOVERY_MS);
    }

    static long intervalMinutes() {
        return CHECK_INTERVAL_MS / 60000L;
    }

    private static void scheduleAfter(Context context, long delayMs) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                new Intent(context, NotificationWatchdogReceiver.class).setAction(ACTION_CHECK),
                PendingIntent.FLAG_UPDATE_CURRENT);
        long triggerAt = SystemClock.elapsedRealtime() + delayMs;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException ignored) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong("nextWatchdogCheckAt", System.currentTimeMillis() + delayMs)
                .apply();
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.length() == 0
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
    }
}
