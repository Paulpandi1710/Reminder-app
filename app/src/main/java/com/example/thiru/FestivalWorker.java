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
import java.util.HashMap;
import java.util.Map;

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
        checkAndNotify();
        return Result.success();
    }

    private void checkAndNotify() {
        Calendar today    = Calendar.getInstance();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        int todayMonth = today.get(Calendar.MONTH) + 1;
        int todayDay   = today.get(Calendar.DAY_OF_MONTH);
        int todayYear  = today.get(Calendar.YEAR);
        int tmrMonth   = tomorrow.get(Calendar.MONTH) + 1;
        int tmrDay     = tomorrow.get(Calendar.DAY_OF_MONTH);
        int tmrYear    = tomorrow.get(Calendar.YEAR);

        Map<String, String> festivals = buildFestivalMap(todayYear);

        // Check today
        String todayKey = todayMonth + "-" + todayDay;
        String todayFullKey = todayYear + "-" + todayMonth + "-" + todayDay;
        String todayFest = festivals.containsKey(todayFullKey)
                ? festivals.get(todayFullKey) : festivals.get(todayKey);
        if (todayFest != null) {
            postFestivalNotification(todayFest, true);
        }

        // Check tomorrow
        String tmrKey = tmrMonth + "-" + tmrDay;
        String tmrFullKey = tmrYear + "-" + tmrMonth + "-" + tmrDay;
        String tmrFest = festivals.containsKey(tmrFullKey)
                ? festivals.get(tmrFullKey) : festivals.get(tmrKey);
        if (tmrFest != null) {
            postTomorrowNotification(tmrFest);
        }
    }

    private Map<String, String> buildFestivalMap(int year) {
        Map<String, String> f = new HashMap<>();

        // ── Fixed-date festivals (MM-DD) ──────────────────
        f.put("1-1",   "New Year's Day 🎆");
        f.put("1-14",  "Pongal / Makar Sankranti 🌾");
        f.put("1-15",  "Mattu Pongal 🐄");
        f.put("1-26",  "Republic Day 🇮🇳");
        f.put("2-14",  "Valentine's Day 💝");
        f.put("4-14",  "Tamil New Year / Vishu 🌸");
        f.put("4-22",  "Earth Day 🌍");
        f.put("8-15",  "Independence Day 🇮🇳");
        f.put("10-2",  "Gandhi Jayanti 🕊️");
        f.put("10-31", "Halloween 🎃");
        f.put("11-14", "Children's Day 🧒");
        f.put("12-25", "Christmas 🎄");
        f.put("12-31", "New Year's Eve 🎉");

        // ── Year-specific moon-based festivals (YYYY-MM-DD) ─
        // 2025
        f.put("2025-2-26",  "Maha Shivaratri 🔱");
        f.put("2025-3-14",  "Holi 🎨");
        f.put("2025-3-30",  "Ram Navami 🙏");
        f.put("2025-4-6",   "Hanuman Jayanti 🙏");
        f.put("2025-4-18",  "Good Friday ✝️");
        f.put("2025-4-20",  "Easter Sunday ✝️");
        f.put("2025-5-12",  "Eid ul-Fitr 🌙");
        f.put("2025-6-7",   "Eid ul-Adha 🌙");
        f.put("2025-8-16",  "Janmashtami 🪈");
        f.put("2025-8-27",  "Ganesh Chaturthi 🐘");
        f.put("2025-10-1",  "Navratri Begins 🕺");
        f.put("2025-10-2",  "Gandhi Jayanti + Navratri 🕊️");
        f.put("2025-10-8",  "Dussehra / Vijayadasami 🏹");
        f.put("2025-10-20", "Diwali 🪔");
        f.put("2025-10-22", "Diwali Main Day 🪔✨");
        f.put("2025-11-5",  "Bhai Dooj 🤝");
        f.put("2025-11-15", "Guru Nanak Jayanti 🙏");

        // 2026
        f.put("2026-2-15",  "Maha Shivaratri 🔱");
        f.put("2026-3-3",   "Holi 🎨");
        f.put("2026-5-1",   "Eid ul-Fitr 🌙");
        f.put("2026-8-5",   "Janmashtami 🪈");
        f.put("2026-10-11", "Dussehra 🏹");
        f.put("2026-11-8",  "Diwali 🪔");

        return f;
    }

    private void postFestivalNotification(String festivalName, boolean isToday) {
        NotificationManager nm = (NotificationManager)
                getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        String title = isToday
                ? "🎉 " + festivalName + " Today!"
                : "🔔 " + festivalName + " Tomorrow!";
        String body  = isToday
                ? "Wishing you a joyful " + cleanName(festivalName)
                + "! Remember to complete your flows 💪"
                : "Get ready for " + cleanName(festivalName)
                + " tomorrow! Plan your day in FocusFlow 📋";

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
        Log.d(TAG, "Festival notification posted: " + festivalName);
    }

    private void postTomorrowNotification(String festivalName) {
        postFestivalNotification(festivalName, false);
    }

    private String cleanName(String name) {
        // Remove emoji characters for cleaner body text
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