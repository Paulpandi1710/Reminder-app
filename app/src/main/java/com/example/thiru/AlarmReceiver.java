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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "focusflow_alarms_v5";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(
                "AppPrefs", Context.MODE_PRIVATE);

        boolean pushNotifs = prefs.getBoolean("pushNotifs", true);
        if (!pushNotifs) return;

        // ── Safe title extraction (both key names supported) ─
        String taskTitle = intent.getStringExtra("TASK_TITLE");
        if (taskTitle == null || taskTitle.isEmpty())
            taskTitle = intent.getStringExtra("item_title");
        if (taskTitle == null || taskTitle.isEmpty())
            taskTitle = prefs.getString("current_alarm_title", "Your Task");
        if (taskTitle == null || taskTitle.isEmpty())
            taskTitle = "Your Task";

        boolean isPreWarning     = intent.getBooleanExtra("IS_PRE_WARNING", false);
        boolean isAlarmEnabled   = prefs.getBoolean("enable_alarm_screen", true);
        boolean isVibrateEnabled = prefs.getBoolean("enable_vibration", true);
        String  userName         = prefs.getString("user_name", "Champion");

        // Backup title for AlarmScreenActivity
        prefs.edit().putString("current_alarm_title", taskTitle).apply();

        // ── Notification channel ──────────────────────────
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Focus Alarms",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setBypassDnd(true);
            channel.enableVibration(isVibrateEnabled);
            nm.createNotificationChannel(channel);
        }

        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tapPi = PendingIntent.getActivity(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Get exact time for beautiful logging
        String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());

        // ════════════════════════════════════════════════
        //   PRE-WARNING (1 hour before)
        // ════════════════════════════════════════════════
        if (isPreWarning) {
            String body = taskTitle + " starts in 1 hour.";
            NotificationCompat.Builder b =
                    new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_timer)
                            .setContentTitle("⏰ Starting soon, " + userName)
                            .setContentText(body)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setContentIntent(tapPi)
                            .setAutoCancel(true)
                            .setDefaults(isVibrateEnabled
                                    ? NotificationCompat.DEFAULT_ALL
                                    : NotificationCompat.DEFAULT_SOUND);
            nm.notify((int) System.currentTimeMillis(), b.build());

            // ── Single clean pre-warning log ───────────────
            NotificationHelper.add(context,
                    taskTitle,
                    "Starts in 1 hour • Reminder sent at " + currentTime, "alarm");
            return;
        }

        // ════════════════════════════════════════════════
        //   MAIN ALARM
        // ════════════════════════════════════════════════
        if (isAlarmEnabled) {
            // Start foreground service — plays audio, vibrates,
            // and launches AlarmScreenActivity
            Intent serviceIntent = new Intent(context, AlarmService.class);
            serviceIntent.putExtra("TASK_TITLE", taskTitle);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else {
            // Alarm screen disabled — show notification only
            String body = taskTitle + " is starting now.";
            NotificationCompat.Builder b =
                    new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_timer)
                            .setContentTitle("⏰ " + userName + ", time to focus!")
                            .setContentText(body)
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(body))
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setContentIntent(tapPi)
                            .setAutoCancel(true)
                            .setDefaults(isVibrateEnabled
                                    ? NotificationCompat.DEFAULT_ALL
                                    : NotificationCompat.DEFAULT_SOUND);
            nm.notify((int) System.currentTimeMillis(), b.build());
        }

        // ── Single, elegant log to in-app notifications ─
        NotificationHelper.add(context,
                taskTitle,
                "It's time to focus! • Ringing at " + currentTime, "alarm");
    }
}