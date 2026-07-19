package com.poersmart.charge;

import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Locale;

public class XpengNotificationListenerService extends NotificationListenerService {
    private static final String PREFS = "poersmart_key";
    private static final String DEFAULT_KEYWORD = "POER_UNLOCK_CHARGER";
    private static final long COOLDOWN_MS = 60000L;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        NotificationWatchdogReceiver.schedule(this);
        startKeyService(KeyBroadcastService.ACTION_SHOW);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        if (getPackageName().equals(sbn.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean("autoXpengNotification", true)) return;
        if (normalizeHex(prefs.getString("mac", "")).length() != 12) return;

        String keyword = prefs.getString("xpengKeyword", DEFAULT_KEYWORD);
        keyword = keyword == null ? DEFAULT_KEYWORD : keyword.trim();
        if (keyword.length() == 0) keyword = DEFAULT_KEYWORD;

        String packageFilter = prefs.getString("xpengPackage", "");
        packageFilter = packageFilter == null ? "" : packageFilter.trim();
        if (packageFilter.length() > 0 && !sbn.getPackageName().equalsIgnoreCase(packageFilter)) return;

        String text = notificationText(sbn);
        if (!containsIgnoreCase(text, keyword)) return;

        long now = System.currentTimeMillis();
        long last = prefs.getLong("lastXpengNotificationUnlockAt", 0L);
        if (last > 0L && now >= last && now - last < COOLDOWN_MS) return;

        if (!startKeyService(KeyBroadcastService.ACTION_UNLOCK)) return;
        prefs.edit()
                .putLong("lastXpengNotificationUnlockAt", now)
                .putString("lastXpengNotificationPackage", sbn.getPackageName())
                .apply();
    }

    private boolean startKeyService(String action) {
        try {
            Intent service = new Intent(this, KeyBroadcastService.class);
            service.setAction(action);
            startService(service);
            return true;
        } catch (RuntimeException e) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("lastAutomationStartError", safeMessage(e))
                    .putLong("lastAutomationStartErrorAt", System.currentTimeMillis())
                    .apply();
            NotificationWatchdogReceiver.scheduleSoon(this);
            return false;
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.length() == 0
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
    }

    private static String notificationText(StatusBarNotification sbn) {
        StringBuilder sb = new StringBuilder();
        append(sb, sbn.getPackageName());
        Notification notification = sbn.getNotification();
        if (notification.extras != null) {
            append(sb, notification.extras.getCharSequence(Notification.EXTRA_TITLE));
            append(sb, notification.extras.getCharSequence(Notification.EXTRA_TEXT));
            append(sb, notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            append(sb, notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            CharSequence[] lines = notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                for (CharSequence line : lines) append(sb, line);
            }
        }
        if (notification.tickerText != null) append(sb, notification.tickerText);
        return sb.toString();
    }

    private static void append(StringBuilder sb, CharSequence value) {
        if (value == null) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(value);
    }

    private static boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) return false;
        return text.toLowerCase(Locale.US).contains(keyword.toLowerCase(Locale.US));
    }

    private static String normalizeHex(String value) {
        return value == null ? "" : value.replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
    }
}
