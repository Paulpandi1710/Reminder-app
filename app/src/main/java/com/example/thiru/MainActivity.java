package com.example.thiru;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // --- TRUE DARK MODE INITIALIZATION ---
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // KICK OFF THE UNIVERSAL PERMISSION CHECK
        checkAndRequestPermissions();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) return loadFragment(new HomeFragment());
            if (itemId == R.id.nav_planner) return loadFragment(new PlannerFragment());
            if (itemId == R.id.nav_stats) return loadFragment(new StatsFragment());
            if (itemId == R.id.nav_settings) return loadFragment(new SettingsFragment());
            return false;
        });

        findViewById(R.id.fab).setOnClickListener(v -> {
            AddTaskBottomSheet bottomSheet = new AddTaskBottomSheet();
            bottomSheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
        });
    }

    // --- STEP 1: STANDARD NOTIFICATION POPUP ---
    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
                return; // Stop here and wait for the user to click Allow/Deny
            }
        }
        // If granted or not needed, move to the complex permissions
        checkSpecialPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            // Once they answer the popup, proceed to the next checks
            checkSpecialPermissions();
        }
    }

    // --- STEP 2: SYSTEM LEVEL PERMISSIONS (SAMSUNG, VIVO, ANDROID 14 FIXES) ---
    private void checkSpecialPermissions() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        // 1. Exact Alarm Permission (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                showEducationalDialog(
                        "Exact Alarm Required",
                        "To ensure your alarm rings exactly on time, please allow 'Alarms & Reminders'.",
                        new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))
                );
                return;
            }
        }

        // 2. NEW FIX: Full Screen Intent Permission (CRITICAL FOR SAMSUNG ON ANDROID 14)
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 (Upside Down Cake)
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                showEducationalDialog(
                        "Full Screen Alarm Required",
                        "Android 14 requires you to explicitly allow this app to show full-screen alarms. Please allow this setting.",
                        new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))
                );
                return;
            }
        }

        // 3. Display Over Other Apps Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showEducationalDialog(
                        "Display Over Apps",
                        "To show the alarm screen while you are using other apps, please allow 'Display over other apps'.",
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))
                );
                return;
            }
        }

        // 4. The Universal Battery Saver Bouncer
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        boolean isProblemDevice = manufacturer.contains("vivo") || manufacturer.contains("xiaomi") ||
                manufacturer.contains("oppo") || manufacturer.contains("realme") ||
                manufacturer.contains("samsung") || manufacturer.contains("huawei");
        boolean oemSetupDone = prefs.getBoolean("oem_setup_done", false);

        if (isProblemDevice && !oemSetupDone) {
            startActivity(new Intent(this, OemPermissionActivity.class));
            finish();
            return;
        }

        prefs.edit().putBoolean("isFirstLaunch", false).apply();
    }

    private void showEducationalDialog(String title, String message, Intent intent) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    startActivity(intent);
                })
                .setNegativeButton("Not Now", null)
                .setCancelable(false)
                .show();
    }

    private boolean loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
        return true;
    }
}