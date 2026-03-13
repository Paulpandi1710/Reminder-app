package com.example.thiru;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import androidx.core.app.NotificationCompat;

public class AlarmService extends Service {

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private static final int ALARM_NOTIFICATION_ID = 999;
    private static final String CHANNEL_ID = "focusflow_master_alarm";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Stop command from AlarmScreenActivity when user
        // taps Ready / Pending / Dismiss / Snooze
        if (intent != null && "STOP_ALARM".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Safe title extraction — dual source
        String taskTitle = null;
        if (intent != null) {
            taskTitle = intent.getStringExtra("TASK_TITLE");
        }
        if (taskTitle == null || taskTitle.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    "AppPrefs", Context.MODE_PRIVATE);
            taskTitle = prefs.getString("current_alarm_title", "Your Task");
        }
        if (taskTitle == null || taskTitle.isEmpty()) {
            taskTitle = "Your Task";
        }

        // Read user preferences
        SharedPreferences prefs = getSharedPreferences(
                "AppPrefs", Context.MODE_PRIVATE);
        boolean isVibrateEnabled = prefs.getBoolean("enable_vibration", true);
        String savedRingtone     = prefs.getString("custom_ringtone", null);

        // ── Step 1: Create foreground notification ────────
        // This is REQUIRED before doing anything else on
        // Android 8+ — must call startForeground() quickly
        createNotificationChannel();

        Intent fullScreenIntent = new Intent(this, AlarmScreenActivity.class);
        fullScreenIntent.putExtra("TASK_TITLE", taskTitle);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreenPi = PendingIntent.getActivity(
                this, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tap notification → open alarm screen
        PendingIntent tapPi = PendingIntent.getActivity(
                this, 1, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("⏰ FocusFlow Alarm")
                .setContentText(taskTitle + " is starting now!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(tapPi)
                .setFullScreenIntent(fullScreenPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();

        // Bind service to notification — OS cannot kill it
        startForeground(ALARM_NOTIFICATION_ID, notification);

        // ── Step 2: Play alarm audio ──────────────────────
        playAlarmAudio(savedRingtone);

        // ── Step 3: Vibrate ───────────────────────────────
        if (isVibrateEnabled) {
            vibratePhone();
        }

        // ── Step 4: Launch AlarmScreenActivity ───────────
        // Foreground services ARE allowed to launch activities
        // on all Android versions — this is the key fix!
        try {
            startActivity(fullScreenIntent);
        } catch (Exception e) {
            // If direct launch fails (edge case), the
            // fullScreenIntent on the notification acts
            // as a reliable fallback
            e.printStackTrace();
        }

        // START_STICKY — if OS kills service, restart it
        return START_STICKY;
    }

    private void playAlarmAudio(String savedRingtoneUri) {
        try {
            Uri alarmUri = null;

            // Use user's chosen ringtone if set
            if (savedRingtoneUri != null && !savedRingtoneUri.isEmpty()) {
                alarmUri = Uri.parse(savedRingtoneUri);
            }

            // Fall back to system alarm sound
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_ALARM);
            }

            // Final fallback — ringtone
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_RINGTONE);
            }

            if (alarmUri != null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(this, alarmUri);
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build());
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void vibratePhone() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                // Pattern: wait 0ms, vibrate 1s, pause 1s — loop forever
                long[] pattern = {0, 1000, 1000};
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        // Clean up audio
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignored) {}

        // Clean up vibration
        try {
            if (vibrator != null) {
                vibrator.cancel();
                vibrator = null;
            }
        } catch (Exception ignored) {}

        // Remove the ongoing notification
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(
                            Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(ALARM_NOTIFICATION_ID);
        } catch (Exception ignored) {}

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Active Alarms",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setBypassDnd(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            NotificationManager manager = getSystemService(
                    NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}