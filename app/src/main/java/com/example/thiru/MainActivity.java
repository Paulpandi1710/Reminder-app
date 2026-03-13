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
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    private final ActivityResultLauncher<String> calendarPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);

        // ══════════════════════════════════════════════════
        //   THE ONE LINE EVERY AI MISSED
        //
        //   On API 30+, without this line:
        //   → ime.bottom is ALWAYS 0 in every inset listener
        //   → adjustResize is silently ignored by the system
        //   → WindowInsetsControllerCompat.show() fires but
        //     nothing resizes because window never reports IME
        //
        //   This MUST be called BEFORE setContentView()
        //   It tells the system: "I will handle all insets
        //   manually — give me raw IME/systemBar values"
        // ══════════════════════════════════════════════════
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        WeeklySummaryScheduler.schedule(this);

        FestivalScheduler.schedule(this);
        GeofenceHelper.reRegisterAll(this);

        checkAndRequestPermissions();

        View fragmentContainer = findViewById(R.id.fragment_container);
        BottomAppBar bottomAppBar = findViewById(R.id.bottomAppBar);
        int bottomBarHeight = (int) (80 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime        = insets.getInsets(WindowInsetsCompat.Type.ime());

            if (ime.bottom > 0) {
                // Keyboard OPEN — push fragment above keyboard
                fragmentContainer.setPadding(
                        systemBars.left,
                        systemBars.top,
                        systemBars.right,
                        ime.bottom
                );
            } else {
                // Keyboard CLOSED — restore space for BottomAppBar
                fragmentContainer.setPadding(
                        systemBars.left,
                        systemBars.top,
                        systemBars.right,
                        bottomBarHeight + systemBars.bottom
                );
            }

            // BottomAppBar floats above gesture nav bar
            bottomAppBar.setPadding(0, 0, 0, systemBars.bottom);

            return WindowInsetsCompat.CONSUMED;
        });

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), false);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home)     return loadFragment(new HomeFragment(), true);
            if (id == R.id.nav_planner)  return loadFragment(new PlannerFragment(), true);
            if (id == R.id.nav_stats)    return loadFragment(new StatsFragment(), true);
            if (id == R.id.nav_ai)       return loadFragment(new AIFragment(), true);
            if (id == R.id.nav_settings) return loadFragment(new SettingsFragment(), true);
            return false;
        });

        findViewById(R.id.fab).setOnClickListener(v -> {
            AddTaskBottomSheet bottomSheet = new AddTaskBottomSheet();
            bottomSheet.show(getSupportFragmentManager(), "AddTaskBottomSheet");
        });

        requestCalendarPermissionIfNeeded();
    }

    private void requestCalendarPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            boolean askedBefore = prefs.getBoolean("calendar_perm_asked", false);
            if (!askedBefore) {
                prefs.edit().putBoolean("calendar_perm_asked", true).apply();
                findViewById(R.id.main).postDelayed(
                        () -> calendarPermLauncher.launch(
                                Manifest.permission.READ_CALENDAR),
                        1500);
            }
        }
    }

    private boolean loadFragment(Fragment fragment, boolean animate) {
        FragmentTransaction transaction =
                getSupportFragmentManager().beginTransaction();
        if (animate) {
            transaction.setCustomAnimations(
                    R.anim.fragment_enter,
                    R.anim.fragment_exit);
        }
        transaction.replace(R.id.fragment_container, fragment).commit();
        return true;
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
                return;
            }
        }
        checkSpecialPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) checkSpecialPermissions();
    }

    private void checkSpecialPermissions() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                showEducationalDialog("Exact Alarm Required",
                        "To ensure your alarm rings exactly on time, "
                                + "please allow 'Alarms & Reminders'.",
                        new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName())));
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                showEducationalDialog("Full Screen Alarm Required",
                        "Android 14 requires you to allow full-screen alarms.",
                        new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:" + getPackageName())));
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showEducationalDialog("Display Over Apps",
                        "To show the alarm screen over other apps, "
                                + "please allow 'Display over other apps'.",
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                return;
            }
        }

        String manufacturer = Build.MANUFACTURER.toLowerCase();
        boolean isProblemDevice = manufacturer.contains("vivo")
                || manufacturer.contains("xiaomi")
                || manufacturer.contains("oppo")
                || manufacturer.contains("realme")
                || manufacturer.contains("samsung")
                || manufacturer.contains("huawei");
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
                .setPositiveButton("Go to Settings",
                        (dialog, which) -> startActivity(intent))
                .setNegativeButton("Not Now", null)
                .setCancelable(false)
                .show();
    }
}