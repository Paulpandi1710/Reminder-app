package com.example.thiru;

import android.content.Context;
import android.util.Log;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class WeeklySummaryScheduler {

    private static final String TAG      = "WeeklySummaryScheduler";
    private static final String WORK_TAG = "weekly_ai_summary";

    // ══════════════════════════════════════════════════════
    //   Schedule weekly summary every Sunday at 8:00 PM
    //   Uses WorkManager — survives app restarts & reboots
    //   KEEP_EXISTING so we don't reset the timer on every
    //   app launch — only schedules once
    // ══════════════════════════════════════════════════════
    public static void schedule(Context ctx) {
        long initialDelay = calculateInitialDelay();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WeeklySummaryWorker.class,
                7, TimeUnit.DAYS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build();

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request);

        Log.d(TAG, "Weekly summary scheduled. Next run in "
                + (initialDelay / 1000 / 60 / 60) + " hours");
    }

    // ══════════════════════════════════════════════════════
    //   Calculate milliseconds until next Sunday 8:00 PM
    // ══════════════════════════════════════════════════════
    private static long calculateInitialDelay() {
        Calendar now  = Calendar.getInstance();
        Calendar next = Calendar.getInstance();

        next.set(Calendar.HOUR_OF_DAY, 20); // 8 PM
        next.set(Calendar.MINUTE,       0);
        next.set(Calendar.SECOND,       0);
        next.set(Calendar.MILLISECOND,  0);

        // Roll forward to next Sunday
        int daysUntilSunday = Calendar.SUNDAY - now.get(Calendar.DAY_OF_WEEK);
        if (daysUntilSunday < 0) daysUntilSunday += 7;
        // If today is Sunday but past 8pm, schedule for next Sunday
        if (daysUntilSunday == 0 && now.get(Calendar.HOUR_OF_DAY) >= 20)
            daysUntilSunday = 7;

        next.add(Calendar.DAY_OF_MONTH, daysUntilSunday);

        long delay = next.getTimeInMillis() - now.getTimeInMillis();
        return Math.max(delay, TimeUnit.MINUTES.toMillis(1));
    }

    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_TAG);
        Log.d(TAG, "Weekly summary cancelled");
    }
}