package com.example.thiru;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlarmScreenActivity extends AppCompatActivity {

    private String originalTaskTitle;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Safe Screen Wake Flags ───────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON    |
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            KeyguardManager km =
                    (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km.requestDismissKeyguard(this, null);
            }
        } catch (Exception ignored) {}

        setContentView(R.layout.activity_alarm_screen);

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String userName = prefs.getString("user_name", "Champion");

        // ── KEY FIX: Dual-source title retrieval ─────────
        // Try intent extra first, fall back to SharedPrefs
        // backup that AlarmReceiver always saves before launch
        originalTaskTitle = getIntent().getStringExtra("TASK_TITLE");
        if (originalTaskTitle == null || originalTaskTitle.isEmpty()) {
            originalTaskTitle = prefs.getString("current_alarm_title", "Your Task");
        }
        if (originalTaskTitle == null || originalTaskTitle.isEmpty()) {
            originalTaskTitle = "Your Task";
        }

        TextView tvTitle = findViewById(R.id.tvAlarmTaskTitle);
        if (tvTitle != null) {
            tvTitle.setText(userName + ", it's time for:\n" + originalTaskTitle);
        }

        startRingtoneAndVibration();

        // Auto-dismiss to Pending after 60 seconds
        timeoutRunnable = () -> {
            Toast.makeText(this,
                    "Alarm ignored. Moved to Pending.",
                    Toast.LENGTH_LONG).show();
            handleAction("PENDING");
        };
        timeoutHandler.postDelayed(timeoutRunnable, 60000);

        // ── Button Listeners ─────────────────────────────
        findViewById(R.id.btnReady).setOnClickListener(v -> handleAction("READY"));
        findViewById(R.id.btnSnooze).setOnClickListener(v -> handleAction("SNOOZE"));
        findViewById(R.id.btnPending).setOnClickListener(v -> handleAction("PENDING"));
        findViewById(R.id.btnDismiss).setOnClickListener(v -> handleAction("DISMISS"));
    }

    private void startRingtoneAndVibration() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        boolean isVibrationEnabled = prefs.getBoolean("enable_vibration", true);
        String savedUriString = prefs.getString("custom_ringtone", null);

        try {
            Uri ringtoneUri = null;
            if (savedUriString != null && !savedUriString.isEmpty()) {
                ringtoneUri = Uri.parse(savedUriString);
            }
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
            if (ringtoneUri == null) {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            if (ringtoneUri != null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(this, ringtoneUri);
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception ignored) {}

        if (isVibrationEnabled) {
            try {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(new long[]{0, 1000, 1000}, 0);
                }
            } catch (Exception ignored) {}
        }
    }

    private void stopAlarm() {
        // Stop the AlarmService — this stops sound + vibration
        try {
            Intent stopService = new Intent(this, AlarmService.class);
            stopService.setAction("STOP_ALARM");
            stopService(stopService);
        } catch (Exception ignored) {}

        // Also clean up any local media just in case
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}

        try {
            if (vibrator != null) {
                vibrator.cancel();
                vibrator = null;
            }
        } catch (Exception ignored) {}

        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }

        // Cancel the ongoing notification
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(999);  // AlarmService notification ID
                nm.cancel(1001); // AlarmReceiver notification ID
            }
        } catch (Exception ignored) {}
    }
    private void handleAction(String action) {
        stopAlarm();

        if (action.equals("DISMISS")) {
            finish();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                ActionItem item = FocusDatabase.getInstance(this)
                        .actionDao().getTaskByTitle(originalTaskTitle);

                if (item != null) {

                    // ── SNOOZE ───────────────────────────────────
                    if (action.equals("SNOOZE")) {
                        Calendar cal = Calendar.getInstance();

                        if (item.type.equals("tasks")) {
                            cal.set(item.year, item.month, item.day,
                                    item.hour, item.minute, 0);
                        } else {
                            // ROUTINE — time only, no fixed date
                            cal.set(Calendar.HOUR_OF_DAY, item.hour);
                            cal.set(Calendar.MINUTE, item.minute);
                            cal.set(Calendar.SECOND, 0);
                            cal.set(Calendar.MILLISECOND, 0);
                            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                                cal.add(Calendar.DAY_OF_YEAR, 1);
                            }
                        }

                        cal.add(Calendar.MINUTE, 10);
                        item.hour   = cal.get(Calendar.HOUR_OF_DAY);
                        item.minute = cal.get(Calendar.MINUTE);

                        if (item.type.equals("tasks")) {
                            item.year  = cal.get(Calendar.YEAR);
                            item.month = cal.get(Calendar.MONTH);
                            item.day   = cal.get(Calendar.DAY_OF_MONTH);
                        }

                        FocusDatabase.getInstance(this).actionDao().update(item);
                        scheduleAlarmForItem(item);

                        runOnUiThread(() -> {
                            Toast.makeText(this,
                                    "Snoozed! See you in 10 minutes.",
                                    Toast.LENGTH_LONG).show();
                            finish();
                        });
                        return;
                    }

                    // ── READY ────────────────────────────────────
                    if (action.equals("READY")) {
                        item.isCompleted = true;
                        item.isPending   = false;

                        if (item.repeatMode != null
                                && !item.repeatMode.equals("None")) {
                            try {
                                Calendar cal = Calendar.getInstance();
                                if (item.type.equals("tasks")) {
                                    cal.set(item.year, item.month, item.day,
                                            item.hour, item.minute, 0);
                                } else {
                                    cal.set(Calendar.HOUR_OF_DAY, item.hour);
                                    cal.set(Calendar.MINUTE, item.minute);
                                    cal.set(Calendar.SECOND, 0);
                                    if (cal.getTimeInMillis()
                                            <= System.currentTimeMillis()) {
                                        cal.add(Calendar.DAY_OF_YEAR, 1);
                                    }
                                }

                                if (item.repeatMode.equals("Daily")) {
                                    cal.add(Calendar.DAY_OF_YEAR, 1);
                                } else if (item.repeatMode.startsWith("Weekly")) {
                                    cal.add(Calendar.WEEK_OF_YEAR, 1);
                                }

                                ActionItem nextItem = new ActionItem(
                                        item.type, item.title, item.timeString,
                                        item.hour, item.minute,
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH),
                                        item.duration, item.description,
                                        item.repeatMode);
                                FocusDatabase.getInstance(this)
                                        .actionDao().insert(nextItem);
                                scheduleAlarmForItem(nextItem);
                            } catch (Exception ignored) {}
                        }

                        // ── PENDING ──────────────────────────────────
                    } else if (action.equals("PENDING")) {
                        item.isPending   = true;
                        item.isCompleted = false;
                    }

                    FocusDatabase.getInstance(this).actionDao().update(item);

                    runOnUiThread(() -> {
                        if (action.equals("READY")) {
                            Toast.makeText(this,
                                    "Flow Started! 🎉",
                                    Toast.LENGTH_SHORT).show();
                        }
                        finish();
                    });

                } else {
                    runOnUiThread(this::finish);
                }

            } catch (Exception e) {
                // Safety net — never crash and loop
                runOnUiThread(this::finish);
            }
        });
    }

    private void scheduleAlarmForItem(ActionItem item) {
        try {
            AlarmManager alarmManager =
                    (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Calendar exactTime = Calendar.getInstance();

            if (item.type.equals("tasks")) {
                exactTime.set(item.year, item.month, item.day,
                        item.hour, item.minute, 0);
                exactTime.set(Calendar.MILLISECOND, 0);
            } else {
                exactTime.set(Calendar.HOUR_OF_DAY, item.hour);
                exactTime.set(Calendar.MINUTE, item.minute);
                exactTime.set(Calendar.SECOND, 0);
                exactTime.set(Calendar.MILLISECOND, 0);
                if (exactTime.getTimeInMillis() <= System.currentTimeMillis()) {
                    exactTime.add(Calendar.DAY_OF_YEAR, 1);
                }
            }

            if (exactTime.getTimeInMillis() <= System.currentTimeMillis()) return;

            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.putExtra("TASK_TITLE", item.title);
            intent.putExtra("IS_PRE_WARNING", false);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    (int) exactTime.getTimeInMillis(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(
                            exactTime.getTimeInMillis(), pendingIntent),
                    pendingIntent);

        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }
}