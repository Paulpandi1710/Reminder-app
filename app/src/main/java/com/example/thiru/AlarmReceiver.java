package com.example.thiru;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "focusflow_alarms";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        // --- THE ULTIMATE KILL SWITCH ---
        boolean pushNotifs = prefs.getBoolean("pushNotifs", true);
        if (!pushNotifs) {
            return; // Instantly abort. No alarms, no notifications, total silence.
        }

        String taskTitle = intent.getStringExtra("TASK_TITLE");
        boolean isPreWarning = intent.getBooleanExtra("IS_PRE_WARNING", false);
        if (taskTitle == null) taskTitle = "Upcoming Task";

        // --- FETCH THE CUSTOM NAME FROM SETTINGS ---
        String userName = prefs.getString("user_name", "Champion");
        String personalAlert = "Time to Focus, " + userName + "!";

        boolean isAlarmEnabled = prefs.getBoolean("enable_alarm_screen", true);
        boolean isVibrateEnabled = prefs.getBoolean("enable_vibration", true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH);
            channel.setBypassDnd(true);
            if (isVibrateEnabled) channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        Intent tapIntent = new Intent(context, MainActivity.class);
        PendingIntent tapPi = PendingIntent.getActivity(context, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE);

        if (isPreWarning) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_timer)
                    .setContentTitle("Starting in 1 hour, " + userName) // Personalized Pre-warning
                    .setContentText(taskTitle)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(tapPi)
                    .setAutoCancel(true)
                    .setDefaults(isVibrateEnabled ? NotificationCompat.DEFAULT_ALL : NotificationCompat.DEFAULT_SOUND);

            nm.notify((int) System.currentTimeMillis(), builder.build());

        } else {
            if (isAlarmEnabled) {
                // If Full Screen Alarm is ON
                Intent fullScreenIntent = new Intent(context, AlarmScreenActivity.class);
                fullScreenIntent.putExtra("TASK_TITLE", taskTitle);
                fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent fullScreenPi = PendingIntent.getActivity(context, (int) System.currentTimeMillis(), fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_timer)
                        .setContentTitle(personalAlert) // Personalized Title
                        .setContentText(taskTitle + " is starting now.")
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setFullScreenIntent(fullScreenPi, true)
                        .setAutoCancel(true);
                nm.notify((int) System.currentTimeMillis(), builder.build());

            } else {
                // If Full Screen Alarm is OFF (Just standard drop-down)
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_timer)
                        .setContentTitle(personalAlert) // Personalized Title
                        .setContentText(taskTitle + " is starting now.")
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(tapPi)
                        .setAutoCancel(true)
                        .setDefaults(isVibrateEnabled ? NotificationCompat.DEFAULT_ALL : NotificationCompat.DEFAULT_SOUND);

                nm.notify((int) System.currentTimeMillis(), builder.build());
            }
        }
    }
}