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
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (action == null) action = ACTION_SHOW;

        startForeground(NOTIFICATION_ID, buildNotification("待命", "小组件可解锁/停充/停止"));

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
        super.onDestroy();
    }

    private void startKeyBroadcast(boolean stopCharge) {
        try {
            if (!canAdvertise()) {
                notifyState("不可用", capabilityMessage());
                toast(capabilityMessage());
                return;
            }
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String mac = normalizeHex(prefs.getString("mac", ""));
            String key = normalizeHex(prefs.getString("key", ""));
            int deviceType = prefs.getInt("deviceType", 0);
            if (mac.length() != 12) {
                notifyState("缺少 MAC", "先打开 App 保存充电桩 MAC");
                toast("先打开 App 保存充电桩 MAC");
                return;
            }

            int command = 0;
            int type = deviceType == 1 ? 1 : 2;
            if (stopCharge) command = deviceType == 1 ? 1 : 2;

            MainActivity.BeaconPayload payload = MainActivity.buildBeaconPayload(mac, key, command, type);
            startAdvertising(payload);
            String text = stopCharge ? "正在停充，10秒后停止" : "正在解锁，10秒后停止";
            notifyState("广播中", text);
            handler.removeCallbacks(autoStopRunnable);
            handler.postDelayed(autoStopRunnable, BROADCAST_MS);
        } catch (Exception e) {
            notifyState("广播失败", e.getMessage());
            toast(e.getMessage());
        }
    }

    private boolean canAdvertise() {
        return bluetoothAdapter != null
                && bluetoothAdapter.isEnabled()
                && Build.VERSION.SDK_INT >= 21
                && bluetoothAdapter.isMultipleAdvertisementSupported()
                && bluetoothAdapter.getBluetoothLeAdvertiser() != null;
    }

    private String capabilityMessage() {
        if (bluetoothAdapter == null) return "手机没有蓝牙适配器";
        if (!bluetoothAdapter.isEnabled()) return "蓝牙未开启";
        if (Build.VERSION.SDK_INT < 21) return "Android 版本过低";
        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) return "手机不支持 BLE 广播";
        if (bluetoothAdapter.getBluetoothLeAdvertiser() == null) return "无法获取 BLE advertiser";
        return "当前不可广播";
    }

    private void startAdvertising(MainActivity.BeaconPayload payload) throws Exception {
        stopAdvertising(null);
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) throw new Exception("无法获取 BLE advertiser");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0x004c, MainActivity.iBeaconData(payload.uuid, 7, payload.minor, 0))
                .build();
        advertiseCallback = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {}
            @Override public void onStartFailure(int errorCode) {
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
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

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

    private void toast(final String text) {
        handler.post(new Runnable() {
            @Override public void run() {
                Toast.makeText(KeyBroadcastService.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static String normalizeHex(String value) {
        return value == null ? "" : value.replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
    }
}
