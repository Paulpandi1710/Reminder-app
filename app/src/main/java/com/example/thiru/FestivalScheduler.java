package com.example.thiru;

import android.content.Context;
import android.util.Log;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class FestivalScheduler {

    private static final String TAG       = "FestivalScheduler";
    private static final String WORK_NAME = "festival_daily_check";

    public static void schedule(Context context) {
        // Run every 24 hours. Initial delay set to next 9:00 AM.
        long initialDelay = calcDelayToNineAm();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                FestivalWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // don't reset on every launch
                request);

        Log.d(TAG, "Festival scheduler set. Next check in ~"
                + (initialDelay / 3600000) + "h");
    }

    private static long calcDelayToNineAm() {
        Calendar now  = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.before(now)) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis() - now.getTimeInMillis();
    }
}