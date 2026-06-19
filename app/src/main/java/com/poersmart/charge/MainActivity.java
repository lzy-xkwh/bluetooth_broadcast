package com.poersmart.charge;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
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

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "poersmart_key";
    private static final int REQUEST_PERMISSIONS = 1001;

    private EditText macInput;
    private EditText keyInput;
    private EditText autoBluetoothTriggerInput;
    private EditText xpengKeywordInput;
    private EditText xpengPackageInput;
    private CheckBox autoBluetoothCheck;
    private CheckBox autoXpengNotificationCheck;
    private CheckBox bluetoothCompatibleModeCheck;
    private Spinner deviceTypeSpinner;
    private TextView statusText;
    private TextView uuidText;
    private TextView savedConfigText;
    private Button unlockButton;
    private Button stopChargeButton;
    private Button stopBroadcastButton;
    private View focusAnchor;

    private BluetoothAdapter bluetoothAdapter;
    private int selectedDeviceType = 0;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
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
        hint.setText("保存充电桩信息后，可手动、小组件或小鹏自动化触发 10 秒广播。");
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
                requestServiceBroadcast(false);
            }
        });
        root.addView(unlockButton, buttonLayout());

        stopChargeButton = new Button(this);
        stopChargeButton.setText("停止充电");
        stopChargeButton.setAllCaps(false);
        styleButton(stopChargeButton, 0xfffbbc04, 0xff202124, 0xfffbbc04);
        stopChargeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                requestServiceBroadcast(true);
            }
        });
        root.addView(stopChargeButton, buttonLayout());

        stopBroadcastButton = new Button(this);
        stopBroadcastButton.setText("停止广播");
        stopBroadcastButton.setAllCaps(false);
        styleButton(stopBroadcastButton, 0xffffffff, 0xffd93025, 0xfff4c7c3);
        stopBroadcastButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startServiceAction(KeyBroadcastService.ACTION_STOP_BROADCAST);
                statusText.setText("已请求停止广播，请查看通知栏状态");
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
        shortcutButton.setText("刷新常驻通知");
        shortcutButton.setAllCaps(false);
        styleButton(shortcutButton, 0xffffffff, 0xff3c4043, 0xffdadce0);
        shortcutButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                savePrefs();
                clearInputFocus();
                startShortcutService();
                toast("常驻通知已刷新");
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

        bluetoothCompatibleModeCheck = new CheckBox(this);
        bluetoothCompatibleModeCheck.setText("蓝牙兼容模式（降低广播强度）");
        bluetoothCompatibleModeCheck.setTextSize(14);
        bluetoothCompatibleModeCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                savePrefs();
            }
        });
        root.addView(bluetoothCompatibleModeCheck, fullWidth());

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

        xpengPackageInput = new EditText(this);
        xpengPackageInput.setHint("可选，小鹏 App 包名；留空不限制来源");
        xpengPackageInput.setTextSize(15);
        xpengPackageInput.setSingleLine(true);
        xpengPackageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(label("小鹏通知来源包名"));
        root.addView(xpengPackageInput, fullWidth());

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
        if (xpengPackageInput != null) xpengPackageInput.clearFocus();
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
        xpengPackageInput.setText(prefs.getString("xpengPackage", ""));
        autoXpengNotificationCheck.setChecked(prefs.getBoolean("autoXpengNotification", true));
        bluetoothCompatibleModeCheck.setChecked(prefs.getBoolean("bluetoothCompatibleMode", true));
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
                .putString("xpengPackage", xpengPackageInput == null ? "" : xpengPackageInput.getText().toString().trim())
                .putBoolean("bluetoothCompatibleMode", bluetoothCompatibleModeCheck != null && bluetoothCompatibleModeCheck.isChecked())
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
        String modeText = prefs.getBoolean("bluetoothCompatibleMode", true) ? "兼容模式" : "高强度模式";
        savedConfigText.setText("已保存 MAC：" + macText + "\nKey：" + keyText + "，设备类型：" + typeText + "，蓝牙：" + modeText);
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

    private void requestServiceBroadcast(boolean stopCharge) {
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

            String action = stopCharge ? KeyBroadcastService.ACTION_STOP_CHARGE : KeyBroadcastService.ACTION_UNLOCK;
            startServiceAction(action);
            uuidText.setText("广播由后台服务执行，通知栏会同步显示状态。");
            statusText.setText("已请求" + (stopCharge ? "停止充电" : "解锁/启动") + "广播，10 秒后自动停止");
        } catch (Exception e) {
            statusText.setText("失败：" + e.getMessage());
            toast(e.getMessage());
        }
    }

    private void startServiceAction(String action) {
        Intent intent = new Intent(this, KeyBroadcastService.class);
        intent.setAction(action);
        startService(intent);
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
