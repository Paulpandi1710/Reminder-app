package com.example.thiru;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
        if (intent != null && "STOP_ALARM".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String taskTitle = intent != null ? intent.getStringExtra("TASK_TITLE") : "Time to Focus!";

        // 1. Create the indestructible Foreground Notification
        createNotificationChannel();
        Intent fullScreenIntent = new Intent(this, AlarmScreenActivity.class);
        fullScreenIntent.putExtra("TASK_TITLE", taskTitle);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("FocusFlow Alarm")
                .setContentText(taskTitle + " is starting!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true) // Backup wake up
                .setOngoing(true)
                .build();

        // Bind this service to the notification so the OS cannot kill it
        startForeground(ALARM_NOTIFICATION_ID, notification);

        // 2. Play Audio (Looping infinitely until they dismiss)
        playAlarmAudio();

        // 3. Vibrate
        vibratePhone();

        // 4. Force launch the visual screen immediately
        try {
            startActivity(fullScreenIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return START_STICKY; // If Android kills it, restart it immediately
    }

    private void playAlarmAudio() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mediaPlayer.setLooping(true); // Loop it forever!
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void vibratePhone() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            long[] pattern = {0, 1000, 1000}; // Wait 0, vibrate 1s, sleep 1s
            vibrator.vibrate(pattern, 0); // 0 means loop forever
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (vibrator != null) vibrator.cancel();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Active Alarms", NotificationManager.IMPORTANCE_HIGH);
            channel.setBypassDnd(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}