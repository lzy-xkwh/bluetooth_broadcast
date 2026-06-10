package com.poersmart.charge;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final String PREFS = "poersmart_key";
    private static final int BROADCAST_MS = 10000;
    private static final int REQUEST_PERMISSIONS = 1001;

    private EditText macInput;
    private EditText keyInput;
    private EditText autoBluetoothTriggerInput;
    private EditText xpengKeywordInput;
    private CheckBox autoBluetoothCheck;
    private CheckBox autoXpengNotificationCheck;
    private Spinner deviceTypeSpinner;
    private TextView statusText;
    private TextView uuidText;
    private TextView savedConfigText;
    private Button unlockButton;
    private Button stopChargeButton;
    private Button stopBroadcastButton;
    private View focusAnchor;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private Handler handler;
    private static int encryptedCounter = 0;
    private int selectedDeviceType = 0;

    private final Runnable autoStopRunnable = new Runnable() {
        @Override public void run() {
            stopAdvertising("广播已自动停止");
        }
    };

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        handler = new Handler(Looper.getMainLooper());
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();

        buildUi();
        loadPrefs();
        clearInputFocus();
        requestRuntimePermissions();
        updateCapabilityStatus();
        startShortcutService();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xfff7f9fb);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(24));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        focusAnchor = root;
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("桩小易蓝牙钥匙");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTextColor(0xff202124);
        root.addView(title, fullWidth());

        TextView hint = new TextView(this);
        hint.setText("保存充电桩信息后，可手动、通知栏、小组件或小鹏自动化触发 10 秒广播。");
        hint.setTextSize(14);
        hint.setTextColor(0xff5f6368);
        hint.setPadding(0, dp(12), 0, dp(18));
        root.addView(hint, fullWidth());

        root.addView(section("状态与操作"));

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setTextColor(0xff202124);
        statusText.setPadding(dp(12), dp(12), dp(12), dp(12));
        statusText.setBackground(statusBackground(0xffffffff, 0xffdadce0));
        root.addView(statusText, fullWidth());

        uuidText = new TextView(this);
        uuidText.setTextSize(13);
        uuidText.setTextColor(0xff5f6368);
        uuidText.setTextIsSelectable(true);
        uuidText.setPadding(0, dp(8), 0, dp(4));
        root.addView(uuidText, fullWidth());

        unlockButton = new Button(this);
        unlockButton.setText("解锁/启动充电");
        unlockButton.setAllCaps(false);
        styleButton(unlockButton, 0xff1a73e8, 0xffffffff, 0xff1a73e8);
        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startKeyBroadcast(false);
            }
        });
        root.addView(unlockButton, buttonLayout());

        stopChargeButton = new Button(this);
        stopChargeButton.setText("停止充电");
        stopChargeButton.setAllCaps(false);
        styleButton(stopChargeButton, 0xfffbbc04, 0xff202124, 0xfffbbc04);
        stopChargeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startKeyBroadcast(true);
            }
        });
        root.addView(stopChargeButton, buttonLayout());

        stopBroadcastButton = new Button(this);
        stopBroadcastButton.setText("停止广播");
        stopBroadcastButton.setAllCaps(false);
        styleButton(stopBroadcastButton, 0xffffffff, 0xffd93025, 0xfff4c7c3);
        stopBroadcastButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopAdvertising("广播已停止");
            }
        });
        root.addView(stopBroadcastButton, buttonLayout());

        Button checkButton = new Button(this);
        checkButton.setText("检测蓝牙广播能力");
        checkButton.setAllCaps(false);
        styleButton(checkButton, 0xffffffff, 0xff3c4043, 0xffdadce0);
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                updateCapabilityStatus();
            }
        });
        root.addView(checkButton, buttonLayout());

        Button shortcutButton = new Button(this);
        shortcutButton.setText("刷新通知快捷按钮");
        shortcutButton.setAllCaps(false);
        styleButton(shortcutButton, 0xffffffff, 0xff3c4043, 0xffdadce0);
        shortcutButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                savePrefs();
                clearInputFocus();
                startShortcutService();
                toast("通知快捷按钮已刷新");
            }
        });
        root.addView(shortcutButton, buttonLayout());

        root.addView(section("充电桩信息"));

        macInput = new EditText(this);
        macInput.setHint("充电桩 MAC，例如 FCE892123456");
        macInput.setTextSize(16);
        macInput.setSingleLine(true);
        macInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        macInput.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(17)});
        root.addView(label("MAC"));
        root.addView(macInput, fullWidth());

        keyInput = new EditText(this);
        keyInput.setHint("可选 Key，32 位十六进制；没有就留空");
        keyInput.setTextSize(16);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyInput.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(32)});
        root.addView(label("Key"));
        root.addView(keyInput, fullWidth());

        deviceTypeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                new String[]{"普通/交流桩", "直流桩"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceTypeSpinner.setAdapter(adapter);
        deviceTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDeviceType = position;
                savePrefs();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(label("设备类型"));
        root.addView(deviceTypeSpinner, fullWidth());

        savedConfigText = new TextView(this);
        savedConfigText.setTextSize(14);
        savedConfigText.setTextColor(0xff3c4043);
        savedConfigText.setPadding(dp(12), dp(10), dp(12), dp(10));
        savedConfigText.setBackground(statusBackground(0xffffffff, 0xffdadce0));
        LinearLayout.LayoutParams savedLp = fullWidth();
        savedLp.topMargin = dp(12);
        root.addView(savedConfigText, savedLp);

        Button saveConfigButton = new Button(this);
        saveConfigButton.setText("保存配置");
        saveConfigButton.setAllCaps(false);
        styleButton(saveConfigButton, 0xff1a73e8, 0xffffffff, 0xff1a73e8);
        saveConfigButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                savePrefsWithValidation();
            }
        });
        root.addView(saveConfigButton, buttonLayout());

        root.addView(section("自动触发"));

        autoBluetoothCheck = new CheckBox(this);
        autoBluetoothCheck.setText("连接车载蓝牙时自动解锁");
        autoBluetoothCheck.setTextSize(14);
        autoBluetoothCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                savePrefs();
            }
        });
        root.addView(autoBluetoothCheck, fullWidth());

        autoBluetoothTriggerInput = new EditText(this);
        autoBluetoothTriggerInput.setHint("车载蓝牙名称/地址关键字；留空表示任意蓝牙连接");
        autoBluetoothTriggerInput.setTextSize(15);
        autoBluetoothTriggerInput.setSingleLine(true);
        autoBluetoothTriggerInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(label("自动触发条件"));
        root.addView(autoBluetoothTriggerInput, fullWidth());

        autoXpengNotificationCheck = new CheckBox(this);
        autoXpengNotificationCheck.setText("小鹏通知关键词自动解锁");
        autoXpengNotificationCheck.setTextSize(14);
        autoXpengNotificationCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                savePrefs();
            }
        });
        root.addView(autoXpengNotificationCheck, fullWidth());

        xpengKeywordInput = new EditText(this);
        xpengKeywordInput.setHint("小鹏通知关键词，例如 POER_UNLOCK_CHARGER");
        xpengKeywordInput.setTextSize(15);
        xpengKeywordInput.setSingleLine(true);
        xpengKeywordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(label("小鹏通知关键词"));
        root.addView(xpengKeywordInput, fullWidth());

        Button notificationAccessButton = new Button(this);
        notificationAccessButton.setText("打开通知使用权设置");
        notificationAccessButton.setAllCaps(false);
        styleButton(notificationAccessButton, 0xffffffff, 0xff1a73e8, 0xffd2e3fc);
        notificationAccessButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                savePrefs();
                try {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                } catch (Exception e) {
                    toast("无法打开通知使用权设置：" + e.getMessage());
                }
            }
        });
        root.addView(notificationAccessButton, buttonLayout());

        setContentView(scroll);
        root.requestFocus();
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(0xff3c4043);
        label.setPadding(0, dp(10), 0, dp(3));
        return label;
    }

    private TextView section(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(0xff202124);
        label.setPadding(0, dp(18), 0, dp(6));
        return label;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams lp = fullWidth();
        lp.topMargin = dp(10);
        return lp;
    }

    private void styleButton(Button button, int fillColor, int textColor, int strokeColor) {
        button.setTextColor(textColor);
        button.setTextSize(16);
        button.setMinHeight(dp(48));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), strokeColor);
        button.setBackground(bg);
    }

    private GradientDrawable statusBackground(int fillColor, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), strokeColor);
        return bg;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void clearInputFocus() {
        if (macInput != null) macInput.clearFocus();
        if (keyInput != null) keyInput.clearFocus();
        if (autoBluetoothTriggerInput != null) autoBluetoothTriggerInput.clearFocus();
        if (xpengKeywordInput != null) xpengKeywordInput.clearFocus();
        if (focusAnchor != null) focusAnchor.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && focusAnchor != null) {
            imm.hideSoftInputFromWindow(focusAnchor.getWindowToken(), 0);
        }
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        macInput.setText(prefs.getString("mac", ""));
        keyInput.setText(prefs.getString("key", ""));
        autoBluetoothTriggerInput.setText(prefs.getString("autoBluetoothTrigger", ""));
        autoBluetoothCheck.setChecked(prefs.getBoolean("autoBluetooth", false));
        xpengKeywordInput.setText(prefs.getString("xpengKeyword", "POER_UNLOCK_CHARGER"));
        autoXpengNotificationCheck.setChecked(prefs.getBoolean("autoXpengNotification", true));
        selectedDeviceType = prefs.getInt("deviceType", 0);
        deviceTypeSpinner.setSelection(selectedDeviceType);
        updateSavedConfigText();
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString("mac", normalizeHex(macInput.getText().toString()))
                .putString("key", normalizeHex(keyInput.getText().toString()))
                .putBoolean("autoBluetooth", autoBluetoothCheck != null && autoBluetoothCheck.isChecked())
                .putString("autoBluetoothTrigger", autoBluetoothTriggerInput == null ? "" : autoBluetoothTriggerInput.getText().toString().trim())
                .putBoolean("autoXpengNotification", autoXpengNotificationCheck != null && autoXpengNotificationCheck.isChecked())
                .putString("xpengKeyword", xpengKeywordInput == null ? "POER_UNLOCK_CHARGER" : xpengKeywordInput.getText().toString().trim())
                .putInt("deviceType", selectedDeviceType)
                .apply();
        updateSavedConfigText();
    }

    private void savePrefsWithValidation() {
        try {
            String mac = normalizeHex(macInput.getText().toString());
            String key = normalizeHex(keyInput.getText().toString());
            validateMac(mac);
            if (key.length() > 0) validateKey(key);
            savePrefs();
            clearInputFocus();
            toast("配置已保存");
        } catch (Exception e) {
            toast(e.getMessage());
        }
    }

    private void updateSavedConfigText() {
        if (savedConfigText == null) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String mac = normalizeHex(prefs.getString("mac", ""));
        String key = normalizeHex(prefs.getString("key", ""));
        int deviceType = prefs.getInt("deviceType", 0);
        String macText = mac.length() == 12 ? mac : "未设置";
        String keyText = key.length() == 32 ? "已保存" : "未填写";
        String typeText = deviceType == 1 ? "直流桩" : "普通/交流桩";
        savedConfigText.setText("已保存 MAC：" + macText + "\nKey：" + keyText + "，设备类型：" + typeText);
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                    "android.permission.BLUETOOTH_ADVERTISE",
                    "android.permission.BLUETOOTH_CONNECT"
            }, REQUEST_PERMISSIONS);
        }
    }

    private void startShortcutService() {
        Intent intent = new Intent(this, KeyBroadcastService.class);
        intent.setAction(KeyBroadcastService.ACTION_SHOW);
        startService(intent);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateCapabilityStatus();
    }

    private void updateCapabilityStatus() {
        String status;
        boolean ok = canAdvertise();
        if (bluetoothAdapter == null) {
            status = "不可用：车机没有蓝牙适配器";
        } else if (!bluetoothAdapter.isEnabled()) {
            status = "不可用：蓝牙未开启";
        } else if (Build.VERSION.SDK_INT < 21) {
            status = "不可用：Android 版本过低，不支持 BLE 广播";
        } else if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            status = "不可用：此车机蓝牙芯片不支持 BLE 广播/Peripheral 模式";
        } else {
            status = "可用：车机支持 BLE 广播";
        }
        status += "\n通知监听：" + (isNotificationListenerEnabled() ? "已授权" : "未授权");
        statusText.setText(status);
        unlockButton.setEnabled(ok);
        stopChargeButton.setEnabled(ok);
    }

    @Override protected void onResume() {
        super.onResume();
        if (statusText != null) updateCapabilityStatus();
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (enabled == null) return false;
            ComponentName mine = new ComponentName(this, XpengNotificationListenerService.class);
            String flat = mine.flattenToString();
            String shortFlat = mine.flattenToShortString();
            return enabled.contains(flat) || enabled.contains(shortFlat) || enabled.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canAdvertise() {
        return bluetoothAdapter != null
                && bluetoothAdapter.isEnabled()
                && Build.VERSION.SDK_INT >= 21
                && bluetoothAdapter.isMultipleAdvertisementSupported()
                && bluetoothAdapter.getBluetoothLeAdvertiser() != null;
    }

    private void startKeyBroadcast(boolean stopCharge) {
        try {
            if (!canAdvertise()) {
                updateCapabilityStatus();
                toast("当前车机不能发 BLE 广播");
                return;
            }
            String mac = normalizeHex(macInput.getText().toString());
            String key = normalizeHex(keyInput.getText().toString());
            validateMac(mac);
            if (key.length() > 0) validateKey(key);
            savePrefs();

            int command = 0;
            int type = selectedDeviceType == 1 ? 1 : 2;
            if (stopCharge) {
                command = selectedDeviceType == 1 ? 1 : 2;
            }

            BeaconPayload payload = buildBeaconPayload(mac, key, command, type);
            startAdvertising(payload);
            uuidText.setText("UUID: " + payload.uuid + "\nmajor: 7, minor: " + payload.minor
                    + "\n模式: " + (key.length() > 0 ? "Key 加密广播" : "普通广播"));
            statusText.setText((stopCharge ? "停止充电" : "解锁/启动") + "广播中，10 秒后自动停止");
            handler.removeCallbacks(autoStopRunnable);
            handler.postDelayed(autoStopRunnable, BROADCAST_MS);
        } catch (Exception e) {
            statusText.setText("失败：" + e.getMessage());
            toast(e.getMessage());
        }
    }

    static BeaconPayload buildBeaconPayload(String mac, String key, int command, int type) throws Exception {
        if (key.length() > 0) {
            return new BeaconPayload(encryptedUuid(mac, key), 8);
        }
        return new BeaconPayload(legacyUuid(mac, command, type), 10);
    }

    static String legacyUuid(String mac, int command, int type) {
        byte[] macBytes = hexToBytes(mac);
        byte[] data = new byte[21];
        data[0] = 'L';
        data[1] = '@';
        data[2] = '2';
        data[3] = '1';
        data[4] = 0;
        System.arraycopy(macBytes, 0, data, 5, 6);
        if (type == 1) {
            byte[] fixed = hexToBytes("FCE892000000");
            System.arraycopy(fixed, 0, data, 11, 6);
        } else {
            int now = (int) (System.currentTimeMillis() / 1000L);
            putIntLE(data, 11, now);
            data[15] = 0;
            data[16] = 0;
        }
        data[17] = 19;
        if (command == 1) data[18] = (byte) 129;
        else if (command == 2) data[18] = 1;
        else data[18] = 0;

        long crc = crc32User(data, 0, 19);
        byte[] crcBig = ByteBuffer.allocate(4).putInt((int) crc).array();
        if (type != 1) {
            data[15] = crcBig[0];
            data[16] = crcBig[1];
        }
        data[19] = crcBig[2];
        data[20] = crcBig[3];
        return uuidFromBytes(data, 5);
    }

    static synchronized String encryptedUuid(String mac, String key) throws Exception {
        byte[] macBytes = hexToBytes(mac);
        byte[] keyBytes = hexToBytes(key);
        byte[] aad = new byte[4];
        aad[0] = macBytes[4];
        aad[1] = macBytes[5];
        aad[2] = (byte) ((encryptedCounter >> 8) & 0xff);
        aad[3] = (byte) (encryptedCounter & 0xff);

        byte[] plain = new byte[8];
        int now = (int) (System.currentTimeMillis() / 1000L);
        putIntBE(plain, 0, now);
        plain[4] = 1;
        plain[5] = 0;
        plain[6] = 0;
        plain[7] = 0;

        byte[] nonce = new byte[13];
        System.arraycopy(macBytes, 0, nonce, 0, 6);
        nonce[6] = (byte) ((encryptedCounter >> 8) & 0xff);
        nonce[7] = (byte) (encryptedCounter & 0xff);
        nonce[8] = 86;
        nonce[9] = 23;
        nonce[10] = (byte) 154;
        nonce[11] = 60;
        nonce[12] = 104;

        byte[] cipher = ccmCrypt(keyBytes, nonce, plain);
        byte[] tag = ccmMac(keyBytes, nonce, aad, plain, 4);
        byte[] out = new byte[16];
        System.arraycopy(aad, 0, out, 0, 4);
        System.arraycopy(cipher, 0, out, 4, 8);
        System.arraycopy(tag, 0, out, 12, 4);
        encryptedCounter = (encryptedCounter + 1) & 0xffff;
        return formatUuid(out);
    }

    private void startAdvertising(BeaconPayload payload) throws Exception {
        stopAdvertising(null);
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) throw new Exception("无法获取 BLE advertiser");

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addManufacturerData(0x004c, iBeaconData(payload.uuid, 7, payload.minor, 0))
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                statusText.setText(statusText.getText() + "\n广播启动成功");
            }

            @Override public void onStartFailure(int errorCode) {
                statusText.setText("广播启动失败，错误码：" + errorCode);
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
        if (message != null) statusText.setText(message);
    }

    static byte[] iBeaconData(String uuid, int major, int minor, int measuredPower) {
        ByteBuffer buffer = ByteBuffer.allocate(23).order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) 0x02);
        buffer.put((byte) 0x15);
        buffer.put(uuidToBytes(uuid));
        buffer.putShort((short) major);
        buffer.putShort((short) minor);
        buffer.put((byte) measuredPower);
        return buffer.array();
    }

    private static byte[] ccmCrypt(byte[] key, byte[] nonce, byte[] plain) throws Exception {
        byte[] out = new byte[plain.length];
        int offset = 0;
        int counter = 1;
        while (offset < plain.length) {
            byte[] stream = aesBlock(key, ctrBlock(nonce, counter));
            int n = Math.min(16, plain.length - offset);
            for (int i = 0; i < n; i++) out[offset + i] = (byte) (plain[offset + i] ^ stream[i]);
            offset += n;
            counter++;
        }
        return out;
    }

    private static byte[] ccmMac(byte[] key, byte[] nonce, byte[] aad, byte[] plain, int tagSize) throws Exception {
        int flags = (aad.length > 0 ? 0x40 : 0) | (((tagSize - 2) / 2) << 3) | 0x01;
        byte[] b0 = new byte[16];
        b0[0] = (byte) flags;
        System.arraycopy(nonce, 0, b0, 1, 13);
        b0[14] = (byte) ((plain.length >> 8) & 0xff);
        b0[15] = (byte) (plain.length & 0xff);

        byte[] x = aesBlock(key, b0);
        byte[] formatted = formatCcmInput(aad, plain);
        for (int offset = 0; offset < formatted.length; offset += 16) {
            byte[] block = new byte[16];
            System.arraycopy(formatted, offset, block, 0, 16);
            xorInPlace(block, x);
            x = aesBlock(key, block);
        }
        byte[] s0 = aesBlock(key, ctrBlock(nonce, 0));
        byte[] tag = new byte[tagSize];
        for (int i = 0; i < tagSize; i++) tag[i] = (byte) (x[i] ^ s0[i]);
        return tag;
    }

    private static byte[] formatCcmInput(byte[] aad, byte[] plain) {
        int aadPart = aad.length == 0 ? 0 : round16(2 + aad.length);
        int plainPart = round16(plain.length);
        byte[] out = new byte[aadPart + plainPart];
        int pos = 0;
        if (aad.length > 0) {
            out[pos++] = (byte) ((aad.length >> 8) & 0xff);
            out[pos++] = (byte) (aad.length & 0xff);
            System.arraycopy(aad, 0, out, pos, aad.length);
            pos = aadPart;
        }
        System.arraycopy(plain, 0, out, pos, plain.length);
        return out;
    }

    private static int round16(int value) {
        return value == 0 ? 0 : ((value + 15) / 16) * 16;
    }

    private static byte[] ctrBlock(byte[] nonce, int counter) {
        byte[] out = new byte[16];
        out[0] = 0x01;
        System.arraycopy(nonce, 0, out, 1, 13);
        out[14] = (byte) ((counter >> 8) & 0xff);
        out[15] = (byte) (counter & 0xff);
        return out;
    }

    private static byte[] aesBlock(byte[] key, byte[] block) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(block);
    }

    private static void xorInPlace(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) a[i] = (byte) (a[i] ^ b[i]);
    }

    private static long crc32User(byte[] bytes, int off, int len) {
        long t = 0xffffffffL;
        for (int i = off; i < off + len; i++) {
            t ^= bytes[i] & 0xffL;
            for (int j = 0; j < 4; j++) {
                long u = CRC_TABLE[(int) ((t >> 24) & 0xff)];
                t = ((t << 8) & 0xffffffffL) ^ u;
                t &= 0xffffffffL;
            }
        }
        return t;
    }

    private static String normalizeHex(String value) {
        return value == null ? "" : value.replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.US);
    }

    private static void validateMac(String mac) throws Exception {
        if (mac.length() != 12) throw new Exception("MAC 应为 12 位十六进制");
        if (!mac.matches("[0-9A-F]{12}")) throw new Exception("MAC 只能包含 0-9/A-F");
    }

    private static void validateKey(String key) throws Exception {
        if (key.length() != 32) throw new Exception("Key 应为 32 位十六进制");
        if (!key.matches("[0-9A-F]{32}")) throw new Exception("Key 只能包含 0-9/A-F");
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static void putIntLE(byte[] out, int offset, int value) {
        out[offset] = (byte) (value & 0xff);
        out[offset + 1] = (byte) ((value >> 8) & 0xff);
        out[offset + 2] = (byte) ((value >> 16) & 0xff);
        out[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private static void putIntBE(byte[] out, int offset, int value) {
        out[offset] = (byte) ((value >> 24) & 0xff);
        out[offset + 1] = (byte) ((value >> 16) & 0xff);
        out[offset + 2] = (byte) ((value >> 8) & 0xff);
        out[offset + 3] = (byte) (value & 0xff);
    }

    private static String uuidFromBytes(byte[] bytes, int offset) {
        byte[] out = new byte[16];
        System.arraycopy(bytes, offset, out, 0, 16);
        return formatUuid(out);
    }

    private static String formatUuid(byte[] bytes) {
        String hex = bytesToHex(bytes);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-"
                + hex.substring(20);
    }

    private static byte[] uuidToBytes(String uuid) {
        return hexToBytes(uuid.replace("-", ""));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xff));
        return sb.toString();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    static class BeaconPayload {
        final String uuid;
        final int minor;
        BeaconPayload(String uuid, int minor) {
            this.uuid = uuid;
            this.minor = minor;
        }
    }

    private static final long[] CRC_TABLE = new long[]{
            0L,79764919L,159529838L,222504665L,319059676L,398814059L,445009330L,507990021L,638119352L,583659535L,797628118L,726387553L,890018660L,835552979L,1015980042L,944750013L,
            1276238704L,1221641927L,1167319070L,1095957929L,1595256236L,1540665371L,1452775106L,1381403509L,1780037320L,1859660671L,1671105958L,1733955601L,2031960084L,2111593891L,1889500026L,1952343757L,
            2552477408L,2632100695L,2443283854L,2506133561L,2334638140L,2414271883L,2191915858L,2254759653L,3190512472L,3135915759L,3081330742L,3009969537L,2905550212L,2850959411L,2762807018L,2691435357L,
            3560074640L,3505614887L,3719321342L,3648080713L,3342211916L,3287746299L,3467911202L,3396681109L,4063920168L,4143685023L,4223187782L,4286162673L,3779000052L,3858754371L,3904687514L,3967668269L,
            881225847L,809987520L,1023691545L,969234094L,662832811L,591600412L,771767749L,717299826L,311336399L,374308984L,453813921L,533576470L,25881363L,88864420L,134795389L,214552010L,
            2023205639L,2086057648L,1897238633L,1976864222L,1804852699L,1867694188L,1645340341L,1724971778L,1587496639L,1516133128L,1461550545L,1406951526L,1302016099L,1230646740L,1142491917L,1087903418L,
            2896545431L,2825181984L,2770861561L,2716262478L,3215044683L,3143675388L,3055782693L,3001194130L,2326604591L,2389456536L,2200899649L,2280525302L,2578013683L,2640855108L,2418763421L,2498394922L,
            3769900519L,3832873040L,3912640137L,3992402750L,4088425275L,4151408268L,4197601365L,4277358050L,3334271071L,3263032808L,3476998961L,3422541446L,3585640067L,3514407732L,3694837229L,3640369242L,
            1762451694L,1842216281L,1619975040L,1682949687L,2047383090L,2127137669L,1938468188L,2001449195L,1325665622L,1271206113L,1183200824L,1111960463L,1543535498L,1489069629L,1434599652L,1363369299L,
            622672798L,568075817L,748617968L,677256519L,907627842L,853037301L,1067152940L,995781531L,51762726L,131386257L,177728840L,240578815L,269590778L,349224269L,429104020L,491947555L,
            4046411278L,4126034873L,4172115296L,4234965207L,3794477266L,3874110821L,3953728444L,4016571915L,3609705398L,3555108353L,3735388376L,3664026991L,3290680682L,3236090077L,3449943556L,3378572211L,
            3174993278L,3120533705L,3032266256L,2961025959L,2923101090L,2868635157L,2813903052L,2742672763L,2604032198L,2683796849L,2461293480L,2524268063L,2284983834L,2364738477L,2175806836L,2238787779L,
            1569362073L,1498123566L,1409854455L,1355396672L,1317987909L,1246755826L,1192025387L,1137557660L,2072149281L,2135122070L,1912620623L,1992383480L,1753615357L,1816598090L,1627664531L,1707420964L,
            295390185L,358241886L,404320391L,483945776L,43990325L,106832002L,186451547L,266083308L,932423249L,861060070L,1041341759L,986742920L,613929101L,542559546L,756411363L,701822548L,
            3316196985L,3244833742L,3425377559L,3370778784L,3601682597L,3530312978L,3744426955L,3689838204L,3819031489L,3881883254L,3928223919L,4007849240L,4037393693L,4100235434L,4180117107L,4259748804L,
            2310601993L,2373574846L,2151335527L,2231098320L,2596047829L,2659030626L,2470359227L,2550115596L,2947551409L,2876312838L,2788305887L,2733848168L,3165939309L,3094707162L,3040238851L,2985771188L
    };
}
