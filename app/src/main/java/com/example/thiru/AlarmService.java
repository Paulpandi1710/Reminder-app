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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class AlarmService extends Service {

    private static final String TAG                 = "AlarmService";
    private static final int    ALARM_NOTIFICATION_ID = 999;
    private static final String CHANNEL_ID           = "focusflow_master_alarm";

    private MediaPlayer mediaPlayer;
    private Vibrator    vibrator;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // STOP command from AlarmScreenActivity
        if (intent != null && "STOP_ALARM".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // ── Title — dual source ───────────────────────────
        String taskTitle = null;
        if (intent != null) {
            taskTitle = intent.getStringExtra("TASK_TITLE");
            if (taskTitle == null || taskTitle.isEmpty())
                taskTitle = intent.getStringExtra("item_title");
        }
        if (taskTitle == null || taskTitle.isEmpty()) {
            SharedPreferences p = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            taskTitle = p.getString("current_alarm_title", "Your Task");
        }
        if (taskTitle == null || taskTitle.isEmpty()) taskTitle = "Your Task";

        SharedPreferences prefs  = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        boolean vibrateEnabled   = prefs.getBoolean("enable_vibration", true);
        String  savedRingtone    = prefs.getString("custom_ringtone", null);

        // ── Step 1: startForeground — must be first ───────
        createNotificationChannel();

        Intent screenIntent = new Intent(this, AlarmScreenActivity.class);
        screenIntent.putExtra("TASK_TITLE", taskTitle);
        screenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreenPi = PendingIntent.getActivity(
                this, 0, screenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent tapPi = PendingIntent.getActivity(
                this, 1, screenIntent,
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

        startForeground(ALARM_NOTIFICATION_ID, notification);

        // ── Step 2: Play audio ────────────────────────────
        playAlarmAudio(savedRingtone);

        // ── Step 3: Vibrate ───────────────────────────────
        if (vibrateEnabled) vibratePhone();

        // ── Step 4: Launch alarm screen ───────────────────
        try {
            startActivity(screenIntent);
        } catch (Exception e) {
            Log.e(TAG, "startActivity failed: " + e.getMessage());
        }

        // REMOVED duplicate logging here. AlarmReceiver handles it perfectly now!

        return START_STICKY;
    }

    private void playAlarmAudio(String savedRingtoneUri) {
        try {
            Uri alarmUri = null;
            if (savedRingtoneUri != null && !savedRingtoneUri.isEmpty()) {
                alarmUri = Uri.parse(savedRingtoneUri);
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            if (alarmUri == null) return;

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Log.d(TAG, "Audio playing");
        } catch (Exception e) {
            Log.e(TAG, "playAlarmAudio: " + e.getMessage());
        }
    }

    private void vibratePhone() {
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null) return;
            long[] pattern = {0, 1000, 800};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                //noinspection deprecation
                vibrator.vibrate(pattern, 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "vibratePhone: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
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

        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(ALARM_NOTIFICATION_ID);
        } catch (Exception ignored) {}

        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Active Alarms",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setBypassDnd(true);
        channel.enableVibration(true);
        channel.setShowBadge(true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}