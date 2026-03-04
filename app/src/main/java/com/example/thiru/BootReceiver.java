package com.example.thiru;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Double check that the phone actually just booted up
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {

            // Run in the background so we don't freeze the phone during startup
            Executors.newSingleThreadExecutor().execute(() -> {

                // Grab all tasks that are active
                List<ActionItem> activeTasks = FocusDatabase.getInstance(context).actionDao().getActiveTasksSync();

                if (activeTasks != null) {
                    for (ActionItem item : activeTasks) {
                        scheduleAlarm(context, item);
                    }
                }
            });
        }
    }

    private void scheduleAlarm(Context context, ActionItem item) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Calendar exactTime = Calendar.getInstance();

        // Recalculate the exact time depending on if it's a Routine or a Task
        if (item.type.equals("routines")) {
            exactTime.set(Calendar.HOUR_OF_DAY, item.hour);
            exactTime.set(Calendar.MINUTE, item.minute);
            exactTime.set(Calendar.SECOND, 0);
            // If the routine time has already passed today, push it to tomorrow
            if (exactTime.getTimeInMillis() <= System.currentTimeMillis()) {
                exactTime.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else {
            exactTime.set(item.year, item.month, item.day, item.hour, item.minute, 0);
        }

        // Only schedule if the time is actually in the future
        if (exactTime.getTimeInMillis() > System.currentTimeMillis()) {

            // 1. The Full Screen Alarm
            Intent exactIntent = new Intent(context, AlarmReceiver.class);
            exactIntent.putExtra("TASK_TITLE", item.title);
            exactIntent.putExtra("IS_PRE_WARNING", false);

            PendingIntent exactPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) exactTime.getTimeInMillis(), // Unique ID prevents overwriting
                    exactIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(exactTime.getTimeInMillis(), exactPendingIntent);
            alarmManager.setAlarmClock(alarmClockInfo, exactPendingIntent);

            // 2. The 1-Hour Pre-warning Notification
            Calendar preWarningTime = (Calendar) exactTime.clone();
            preWarningTime.add(Calendar.HOUR_OF_DAY, -1);

            if (preWarningTime.getTimeInMillis() > System.currentTimeMillis()) {
                Intent preIntent = new Intent(context, AlarmReceiver.class);
                preIntent.putExtra("TASK_TITLE", item.title);
                preIntent.putExtra("IS_PRE_WARNING", true);

                PendingIntent prePendingIntent = PendingIntent.getBroadcast(
                        context,
                        (int) exactTime.getTimeInMillis() + 1,
                        preIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preWarningTime.getTimeInMillis(), prePendingIntent);
            }
        }
    }
}