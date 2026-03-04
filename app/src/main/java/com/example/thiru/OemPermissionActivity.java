package com.example.thiru;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class OemPermissionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oem_permission);

        TextView tvWarning = findViewById(R.id.tvDeviceWarning);
        String manufacturer = Build.MANUFACTURER.toUpperCase();

        // Customized message based on brand
        if (manufacturer.contains("SAMSUNG")) {
            tvWarning.setText("Warning: You are using a Samsung device. Samsung's 'Deep Sleeping Apps' feature will block your alarms. You MUST disable battery optimization for FocusFlow.");
        } else {
            tvWarning.setText("Warning: You are using a " + manufacturer + " device. Its battery saver will block your alarms. You MUST enable Autostart and disable Battery Saver.");
        }

        findViewById(R.id.btnBattery).setOnClickListener(v -> requestBatteryOptimization());
        findViewById(R.id.btnAutoStart).setOnClickListener(v -> openAutoStartSettings());

        findViewById(R.id.btnDone).setOnClickListener(v -> {
            getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putBoolean("oem_setup_done", true).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Please disable battery optimization in your phone settings manually.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openAutoStartSettings() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        try {
            Intent intent = new Intent();
            if (manufacturer.contains("vivo") || manufacturer.contains("bbk")) {
                intent.setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
            } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
                intent.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } else if (manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus")) {
                intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            } else if (manufacturer.contains("samsung")) {
                // Samsung usually handles this in the standard App Details or Battery settings
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                Toast.makeText(this, "Go to Battery -> Unrestricted", Toast.LENGTH_LONG).show();
            } else {
                intent = new Intent(Settings.ACTION_SETTINGS); // Fallback for all others
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + getPackageName()));
            startActivity(fallback);
            Toast.makeText(this, "Please find 'Autostart' or 'Background Permissions' in settings.", Toast.LENGTH_LONG).show();
        }
    }
}