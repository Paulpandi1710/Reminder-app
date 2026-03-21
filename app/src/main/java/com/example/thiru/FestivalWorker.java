package com.example.thiru;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FestivalWorker extends Worker {

    private static final String TAG        = "FestivalWorker";
    private static final String CHANNEL_ID = "festival_channel";

    public FestivalWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        createChannel();
        checkAndNotifyDynamic();
        return Result.success();
    }

    private void checkAndNotifyDynamic() {
        if (!CalendarHelper.hasPermission(getApplicationContext())) {
            Log.d(TAG, "No calendar permission. Cannot check festivals.");
            return;
        }

        // ── 1. Check for TODAY ──
        Calendar today = Calendar.getInstance();
        int tYear = today.get(Calendar.YEAR);
        int tMonth = today.get(Calendar.MONTH);
        int tDay = today.get(Calendar.DAY_OF_MONTH);

        List<CalendarHelper.CalendarEvent> todayEvents =
                CalendarHelper.getEventsForDay(getApplicationContext(), tYear, tMonth, tDay);

        Set<String> seenToday = new HashSet<>();
        for (CalendarHelper.CalendarEvent event : todayEvents) {
            if (event.allDay) {
                String normalizedTitle = event.title.trim().toLowerCase();
                // .add() returns true only if the item wasn't already in the Set (Stops 3x duplicates!)
                if (seenToday.add(normalizedTitle)) {
                    postFestivalNotification(event.title, true);
                }
            }
        }

        // ── 2. Check for TOMORROW ──
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        int tmrYear = tomorrow.get(Calendar.YEAR);
        int tmrMonth = tomorrow.get(Calendar.MONTH);
        int tmrDay = tomorrow.get(Calendar.DAY_OF_MONTH);

        List<CalendarHelper.CalendarEvent> tomorrowEvents =
                CalendarHelper.getEventsForDay(getApplicationContext(), tmrYear, tmrMonth, tmrDay);

        Set<String> seenTomorrow = new HashSet<>();
        for (CalendarHelper.CalendarEvent event : tomorrowEvents) {
            if (event.allDay) {
                String normalizedTitle = event.title.trim().toLowerCase();
                // Stops duplicates for tomorrow's alerts too
                if (seenTomorrow.add(normalizedTitle)) {
                    postFestivalNotification(event.title, false);
                }
            }
        }
    }

    private void postFestivalNotification(String festivalName, boolean isToday) {
        NotificationManager nm = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String title = isToday
                ? "🎉 " + festivalName + " Today!"
                : "🔔 Tomorrow is " + festivalName + "!";
        String body  = isToday
                ? "Wishing you a joyful " + cleanName(festivalName) + "! Remember to complete your flows 💪"
                : "Get ready for " + cleanName(festivalName) + " tomorrow! Plan your day in FocusFlow 📋";

        Intent openApp = new Intent(getApplicationContext(), MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                festivalName.hashCode(), openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .setContentIntent(pi);

        nm.notify(festivalName.hashCode(), builder.build());
        NotificationHelper.add(getApplicationContext(), title, body, "festival");
        Log.d(TAG, "Dynamic festival notification posted for: " + festivalName);
    }

    private String cleanName(String name) {
        return name.replaceAll("[^\\p{L}\\p{N}\\s/]", "").trim();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Festival Reminders",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Festival and holiday notifications");
        nm.createNotificationChannel(channel);
    }
}