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

    private static final String CHANNEL_ID = "focusflow_alarms_v5";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(
                "AppPrefs", Context.MODE_PRIVATE);

        // Kill switch
        boolean pushNotifs = prefs.getBoolean("pushNotifs", true);
        if (!pushNotifs) return;

        // Safe title extraction
        String taskTitle = intent.getStringExtra("TASK_TITLE");
        if (taskTitle == null || taskTitle.isEmpty()) taskTitle = "Your Task";

        boolean isPreWarning     = intent.getBooleanExtra("IS_PRE_WARNING", false);
        boolean isAlarmEnabled   = prefs.getBoolean("enable_alarm_screen", true);
        boolean isVibrateEnabled = prefs.getBoolean("enable_vibration", true);
        String userName          = prefs.getString("user_name", "Champion");

        // Save title as backup so AlarmScreenActivity can
        // always retrieve it even if intent extras are dropped
        prefs.edit().putString("current_alarm_title", taskTitle).apply();

        // ── Build notification channel ────────────────────
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Focus Alarms",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setBypassDnd(true);
            channel.enableVibration(isVibrateEnabled);
            nm.createNotificationChannel(channel);
        }

        // Tap notification → open MainActivity safely
        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // ════════════════════════════════════════════════
        // PRE-WARNING NOTIFICATION (1 hour before)
        // ════════════════════════════════════════════════
        if (isPreWarning) {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_timer)
                            .setContentTitle("Starting in 1 hour, " + userName)
                            .setContentText(taskTitle)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(tapPi)
                            .setAutoCancel(true)
                            .setDefaults(isVibrateEnabled
                                    ? NotificationCompat.DEFAULT_ALL
                                    : NotificationCompat.DEFAULT_SOUND);
            nm.notify((int) System.currentTimeMillis(), builder.build());
            return;
        }

        // ════════════════════════════════════════════════
        // MAIN ALARM
        // ════════════════════════════════════════════════
        if (isAlarmEnabled) {

            // ── THE CORRECT FIX FOR ALL ANDROID VERSIONS ──
            // Start AlarmService as a FOREGROUND SERVICE.
            // The service plays sound + vibration + launches
            // AlarmScreenActivity. This works on Android
            // 13, 14, 15, 16 because foreground services
            // ARE allowed to launch activities.
            Intent serviceIntent = new Intent(context, AlarmService.class);
            serviceIntent.putExtra("TASK_TITLE", taskTitle);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Must use startForegroundService on Android 8+
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

        } else {
            // Alarm screen disabled by user — show silent notification only
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_timer)
                            .setContentTitle("Time to Focus, " + userName + "!")
                            .setContentText(taskTitle + " is starting now.")
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setContentIntent(tapPi)
                            .setAutoCancel(true)
                            .setDefaults(isVibrateEnabled
                                    ? NotificationCompat.DEFAULT_ALL
                                    : NotificationCompat.DEFAULT_SOUND);
            nm.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}