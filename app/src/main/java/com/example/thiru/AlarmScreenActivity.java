package com.example.thiru;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlarmScreenActivity extends AppCompatActivity {

    private String originalTaskTitle;
    private final Handler timeoutHandler  = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Screen wake flags ─────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            KeyguardManager km =
                    (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                km.requestDismissKeyguard(this, null);
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_alarm_screen);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String userName = prefs.getString("user_name", "Champion");

        // ── Title (dual source) ───────────────────────────
        originalTaskTitle = getIntent().getStringExtra("TASK_TITLE");
        if (originalTaskTitle == null || originalTaskTitle.isEmpty())
            originalTaskTitle = prefs.getString("current_alarm_title", "Your Task");
        if (originalTaskTitle == null || originalTaskTitle.isEmpty())
            originalTaskTitle = "Your Task";

        TextView tvTitle = findViewById(R.id.tvAlarmTaskTitle);
        if (tvTitle != null)
            tvTitle.setText(userName + ", it's time for:\n" + originalTaskTitle);

        // ══════════════════════════════════════════════════
        //   CRITICAL FIX — DO NOT start audio/vibration here.
        //   AlarmService already plays ringtone + vibrates.
        //   Starting it again here caused double sound.
        //   This activity ONLY shows the UI.
        // ══════════════════════════════════════════════════

        // Auto-dismiss after 60s
        timeoutRunnable = () -> {
            Toast.makeText(this, "Alarm ignored. Moved to Pending.",
                    Toast.LENGTH_LONG).show();
            handleAction("PENDING");
        };
        timeoutHandler.postDelayed(timeoutRunnable, 60_000L);

        // ── Button listeners ──────────────────────────────
        findViewById(R.id.btnReady).setOnClickListener(v   -> handleAction("READY"));
        findViewById(R.id.btnSnooze).setOnClickListener(v  -> handleAction("SNOOZE"));
        findViewById(R.id.btnPending).setOnClickListener(v -> handleAction("PENDING"));
        findViewById(R.id.btnDismiss).setOnClickListener(v -> handleAction("DISMISS"));
    }

    // ── Stop AlarmService (which holds audio + vibration) ─
    private void stopAlarmService() {
        try {
            Intent stop = new Intent(this, AlarmService.class);
            stop.setAction("STOP_ALARM");
            stopService(stop);
        } catch (Exception ignored) {}

        // Cancel notifications
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(999);  // AlarmService foreground notif ID
                nm.cancel(1001); // AlarmReceiver notif ID
            }
        } catch (Exception ignored) {}

        // Cancel timeout
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void handleAction(String action) {
        stopAlarmService();

        if ("DISMISS".equals(action)) {
            finish();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                ActionItem item = FocusDatabase.getInstance(this)
                        .actionDao().getTaskByTitle(originalTaskTitle);

                if (item == null) {
                    runOnUiThread(this::finish);
                    return;
                }

                if ("SNOOZE".equals(action)) {
                    handleSnooze(item);

                } else if ("READY".equals(action)) {
                    handleReady(item);

                } else if ("PENDING".equals(action)) {
                    item.isPending   = true;
                    item.isCompleted = false;
                    FocusDatabase.getInstance(this).actionDao().update(item);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Moved to Pending.",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                // Log notification
                NotificationHelper.add(this,
                        "⏰ " + originalTaskTitle,
                        "Alarm action: " + action.toLowerCase() + " at "
                                + formatCurrentTime(),
                        "alarm");

            } catch (Exception e) {
                runOnUiThread(this::finish);
            }
        });
    }

    private void handleSnooze(ActionItem item) {
        Calendar cal = Calendar.getInstance();
        if ("tasks".equals(item.type)) {
            cal.set(item.year, item.month, item.day, item.hour, item.minute, 0);
        } else {
            cal.set(Calendar.HOUR_OF_DAY, item.hour);
            cal.set(Calendar.MINUTE,      item.minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= System.currentTimeMillis())
                cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        cal.add(Calendar.MINUTE, 10);

        item.hour   = cal.get(Calendar.HOUR_OF_DAY);
        item.minute = cal.get(Calendar.MINUTE);
        if ("tasks".equals(item.type)) {
            item.year  = cal.get(Calendar.YEAR);
            item.month = cal.get(Calendar.MONTH);
            item.day   = cal.get(Calendar.DAY_OF_MONTH);
        }

        FocusDatabase.getInstance(this).actionDao().update(item);
        scheduleAlarmForItem(item);

        runOnUiThread(() -> {
            Toast.makeText(this, "Snoozed for 10 minutes!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void handleReady(ActionItem item) {
        item.isCompleted = true;
        item.isPending   = false;
        FocusDatabase.getInstance(this).actionDao().update(item);

        boolean isRepeating = item.repeatMode != null
                && !item.repeatMode.isEmpty()
                && !"None".equals(item.repeatMode);

        if (isRepeating) {
            // ════════════════════════════════════════════
            //   FIX: DO NOT insert a new ActionItem.
            //   HomeFragment.observeProgressAndTimer()
            //   will reset this item on the next day.
            //   We only need to schedule the next alarm.
            // ════════════════════════════════════════════
            scheduleNextAlarmOnly(item);
        }

        runOnUiThread(() -> {
            Toast.makeText(this, "Flow Started! 🎉", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    // ── Schedule next alarm WITHOUT creating a new DB item ─
    private void scheduleNextAlarmOnly(ActionItem item) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, item.hour);
            cal.set(Calendar.MINUTE,      item.minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            if ("Daily".equals(item.repeatMode)) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            } else if (item.repeatMode.startsWith("Weekly")) {
                cal.add(Calendar.WEEK_OF_YEAR, 1);
            } else {
                return; // "None" — no reschedule
            }

            if (cal.getTimeInMillis() <= System.currentTimeMillis()) return;

            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("TASK_TITLE", item.title);
            intent.putExtra("IS_PRE_WARNING", false);

            int reqCode = (int)(cal.getTimeInMillis() % Integer.MAX_VALUE);
            PendingIntent pi = PendingIntent.getBroadcast(this, reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            am.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), pi), pi);

        } catch (Exception ignored) {}
    }

    // ── Standard alarm scheduling (for snooze, task repeat) ──
    private void scheduleAlarmForItem(ActionItem item) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;

            Calendar exactTime = Calendar.getInstance();
            if ("tasks".equals(item.type)) {
                exactTime.set(item.year, item.month, item.day,
                        item.hour, item.minute, 0);
                exactTime.set(Calendar.MILLISECOND, 0);
            } else {
                exactTime.set(Calendar.HOUR_OF_DAY, item.hour);
                exactTime.set(Calendar.MINUTE,      item.minute);
                exactTime.set(Calendar.SECOND, 0);
                exactTime.set(Calendar.MILLISECOND, 0);
                if (exactTime.getTimeInMillis() <= System.currentTimeMillis())
                    exactTime.add(Calendar.DAY_OF_YEAR, 1);
            }

            if (exactTime.getTimeInMillis() <= System.currentTimeMillis()) return;

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("TASK_TITLE", item.title);
            intent.putExtra("IS_PRE_WARNING", false);

            int reqCode = (int)(exactTime.getTimeInMillis() % Integer.MAX_VALUE);
            PendingIntent pi = PendingIntent.getBroadcast(this, reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            am.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(
                            exactTime.getTimeInMillis(), pi), pi);

        } catch (Exception ignored) {}
    }

    private String formatCurrentTime() {
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    @Override
    protected void onDestroy() {
        // Always stop the alarm service when activity is destroyed
        stopAlarmService();
        super.onDestroy();
    }
}