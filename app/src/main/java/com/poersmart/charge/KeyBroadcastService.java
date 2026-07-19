package com.poersmart.charge;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.Locale;

public class KeyBroadcastService extends Service {
    static final String ACTION_SHOW = "com.poersmart.charge.action.SHOW_NOTIFICATION";
    static final String ACTION_UNLOCK = "com.poersmart.charge.action.UNLOCK";
    static final String ACTION_STOP_CHARGE = "com.poersmart.charge.action.STOP_CHARGE";
    static final String ACTION_STOP_BROADCAST = "com.poersmart.charge.action.STOP_BROADCAST";

    private static final String PREFS = "poersmart_key";
    private static final int NOTIFICATION_ID = 7;
    private static final int BROADCAST_MS = 10000;
    private static final String APP_TITLE = "桩小易蓝牙钥匙";
    private static final String PERMISSION_BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE";
    private static final String PERMISSION_BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private Handler handler;

    private final Runnable autoStopRunnable = new Runnable() {
        @Override public void run() {
            stopAdvertising("广播已自动停止");
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        NotificationWatchdogReceiver.schedule(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (action == null) action = ACTION_SHOW;

        startForeground(NOTIFICATION_ID, buildNotification("待命", "小组件可解锁/停充/停止"));
        recordServiceHeartbeat(action);
        NotificationWatchdogReceiver.schedule(this);

        if (ACTION_UNLOCK.equals(action)) {
            startKeyBroadcast(false);
        } else if (ACTION_STOP_CHARGE.equals(action)) {
            startKeyBroadcast(true);
        } else if (ACTION_STOP_BROADCAST.equals(action)) {
            stopAdvertising("广播已停止");
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        stopAdvertising(null);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("lastServiceDestroyedAt", System.currentTimeMillis())
                .apply();
        NotificationWatchdogReceiver.scheduleSoon(this);
        super.onDestroy();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        NotificationWatchdogReceiver.scheduleSoon(this);
        super.onTaskRemoved(rootIntent);
    }

    private void startKeyBroadcast(boolean stopCharge) {
        try {
            if (!canAdvertise()) {
                notifyState("不可用", capabilityMessage());
                toast(capabilityMessage());
                return;
            }
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            migrateBluetoothModeDefault(prefs);
            String mac = normalizeHex(prefs.getString("mac", ""));
            String key = normalizeHex(prefs.getString("key", ""));
            int deviceType = prefs.getInt("deviceType", 0);
            boolean compatibleMode = prefs.getBoolean("bluetoothCompatibleMode", true);
            if (mac.length() != 12) {
                notifyState("缺少 MAC", "先打开 App 保存充电桩 MAC");
                toast("先打开 App 保存充电桩 MAC");
                return;
            }
            if (key.length() != 0 && key.length() != 32) {
                notifyState("Key 无效", "Key 应为 32 位十六进制或留空");
                toast("Key 应为 32 位十六进制或留空");
                return;
            }

            int command = 0;
            int type = deviceType == 1 ? 1 : 2;
            if (stopCharge) command = deviceType == 1 ? 1 : 2;

            BeaconProtocol.BeaconPayload payload = BeaconProtocol.buildBeaconPayload(mac, key, command, type);
            startAdvertising(payload, compatibleMode);
            String text = stopCharge ? "正在停充，10秒后停止" : "正在解锁，10秒后停止";
            notifyState("广播中", text);
            handler.removeCallbacks(autoStopRunnable);
            handler.postDelayed(autoStopRunnable, BROADCAST_MS);
        } catch (Exception e) {
            String message = safeMessage(e);
            notifyState("广播失败", message);
            toast(message);
        }
    }

    private boolean canAdvertise() {
        if (!hasBluetoothRuntimePermissions()) return false;
        try {
            return bluetoothAdapter != null
                    && bluetoothAdapter.isEnabled()
                    && Build.VERSION.SDK_INT >= 21
                    && bluetoothAdapter.isMultipleAdvertisementSupported()
                    && bluetoothAdapter.getBluetoothLeAdvertiser() != null;
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private String capabilityMessage() {
        if (!hasBluetoothRuntimePermissions()) return "请允许附近设备/蓝牙权限";
        if (bluetoothAdapter == null) return "手机没有蓝牙适配器";
        try {
            if (!bluetoothAdapter.isEnabled()) return "蓝牙未开启";
            if (Build.VERSION.SDK_INT < 21) return "Android 版本过低";
            if (!bluetoothAdapter.isMultipleAdvertisementSupported()) return "手机不支持 BLE 广播";
            if (bluetoothAdapter.getBluetoothLeAdvertiser() == null) return "无法获取 BLE advertiser";
        } catch (SecurityException ignored) {
            return "系统拒绝蓝牙权限，请重新授权";
        }
        return "当前不可广播";
    }

    private boolean hasBluetoothRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 31) return true;
        return checkSelfPermission(PERMISSION_BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(PERMISSION_BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void startAdvertising(BeaconProtocol.BeaconPayload payload, boolean compatibleMode) throws Exception {
        stopAdvertising(null);
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) throw new Exception("无法获取 BLE advertiser");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(compatibleMode ? AdvertiseSettings.ADVERTISE_MODE_BALANCED : AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(compatibleMode ? AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM : AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0x004c, BeaconProtocol.iBeaconData(payload.uuid, 7, payload.minor, 0))
                .build();
        advertiseCallback = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {}
            @Override public void onStartFailure(int errorCode) {
                if (this != advertiseCallback) return;
                handler.removeCallbacks(autoStopRunnable);
                advertiseCallback = null;
                advertiser = null;
                notifyState("广播启动失败", "错误码：" + errorCode);
            }
        };
        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    private void stopAdvertising(String message) {
        handler.removeCallbacks(autoStopRunnable);
        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (Exception ignored) {}
        }
        advertiseCallback = null;
        advertiser = null;
        if (message != null) notifyState("待命", message);
    }

    private Notification buildNotification(String state, String text) {
        PendingIntent open = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class), pendingIntentFlags());

        RemoteViews compact = buildCompactNotificationView(state, text);
        compact.setOnClickPendingIntent(R.id.notification_root, open);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(APP_TITLE)
                .setContentText(text)
                .setSubText(state)
                .setContentIntent(open)
                .setContent(compact)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor(0xff1a73e8);
        }
        Notification notification = builder.build();
        notification.contentView = compact;
        return notification;
    }

    private RemoteViews buildCompactNotificationView(String state, String text) {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_compact);
        views.setTextViewText(R.id.notification_text, text);
        views.setTextViewText(R.id.notification_state, state);
        return views;
    }

    private void notifyState(String state, String text) {
        startForeground(NOTIFICATION_ID, buildNotification(state, text));
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private void recordServiceHeartbeat(String action) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong("lastServiceHeartbeatAt", System.currentTimeMillis())
                .putString("lastServiceAction", action)
                .remove("lastWatchdogError")
                .remove("lastWatchdogErrorAt")
                .apply();
    }

    private void migrateBluetoothModeDefault(SharedPreferences prefs) {
        if (prefs.getBoolean("bluetoothModeDefaultMigrated", false)) return;
        prefs.edit()
                .putBoolean("bluetoothCompatibleMode", true)
                .putBoolean("bluetoothModeDefaultMigrated", true)
                .apply();
    }

    private void toast(final String text) {
        handler.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(KeyBroadcastService.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.length() == 0 ? error.getClass().getSimpleName() : message;
    }

    private static String normalizeHex(String value) {
        return value == null ? "" : value.replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
    }
}
