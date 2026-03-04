package com.example.thiru;

import android.app.KeyguardManager;
import android.content.Context;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlarmScreenActivity extends AppCompatActivity {

    private String taskTitle;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    // The timer that will auto-kill the alarm if ignored
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_alarm_screen);

        // --- FETCH THE CUSTOM NAME FROM SETTINGS ---
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String userName = prefs.getString("user_name", "Champion");

        taskTitle = getIntent().getStringExtra("TASK_TITLE");
        if (taskTitle == null) {
            taskTitle = "Time to Focus, " + userName + "!";
        } else {
            // Prepend the user's name to the actual task title!
            taskTitle = userName + ", it's time for: " + taskTitle;
        }

        TextView tvTitle = findViewById(R.id.tvAlarmTaskTitle);
        tvTitle.setText(taskTitle);

        startRingtoneAndVibration();

        // If ignored for 60 seconds, automatically move to PENDING and close.
        timeoutRunnable = () -> {
            Toast.makeText(this, "Alarm ignored. Moved to Pending.", Toast.LENGTH_LONG).show();
            handleAction("PENDING");
        };
        timeoutHandler.postDelayed(timeoutRunnable, 60000); // 60,000 milliseconds = 1 minute

        findViewById(R.id.btnReady).setOnClickListener(v -> handleAction("READY"));
        findViewById(R.id.btnPending).setOnClickListener(v -> handleAction("PENDING"));
        findViewById(R.id.btnDismiss).setOnClickListener(v -> handleAction("DISMISS"));
    }

    private void startRingtoneAndVibration() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        boolean isVibrationEnabled = prefs.getBoolean("enable_vibration", true);
        String savedUriString = prefs.getString("custom_ringtone", null);

        try {
            Uri ringtoneUri;
            if (savedUriString != null && !savedUriString.isEmpty()) {
                ringtoneUri = Uri.parse(savedUriString);
            } else {
                ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (ringtoneUri == null) {
                    ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, ringtoneUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (isVibrationEnabled) {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                long[] pattern = {0, 1000, 1000};
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopAlarm() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        // Cancel the auto-kill timer if the user clicked a button
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    private void handleAction(String action) {
        stopAlarm();

        if (action.equals("DISMISS")) {
            finish();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            // We have to clean the "Name, it's time for:" off the title to find it in the DB
            String originalTitle = getIntent().getStringExtra("TASK_TITLE");
            if (originalTitle == null) originalTitle = "";

            ActionItem item = FocusDatabase.getInstance(this).actionDao().getTaskByTitle(originalTitle);
            if (item != null) {
                if (action.equals("READY")) {
                    item.isCompleted = true;
                    item.isPending = false;
                } else if (action.equals("PENDING")) {
                    item.isPending = true;
                    item.isCompleted = false;
                }
                FocusDatabase.getInstance(this).actionDao().update(item);

                runOnUiThread(() -> {
                    if (!action.equals("PENDING")) {
                        Toast.makeText(this, "Flow Started!", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                });
            } else {
                runOnUiThread(this::finish);
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }
}