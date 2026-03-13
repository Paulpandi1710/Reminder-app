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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WeeklySummaryWorker extends Worker {

    private static final String TAG        = "WeeklySummaryWorker";
    private static final String CHANNEL_ID = "weekly_summary_channel";
    private static final int    NOTIF_ID   = 9001;

    public WeeklySummaryWorker(@NonNull Context context,
                               @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Weekly summary worker started");
        try {
            Context ctx = getApplicationContext();

            // ── Fetch all tasks from DB (sync) ────────────
            List<ActionItem> allItems =
                    FocusDatabase.getInstance(ctx).actionDao().getAllItemsSync();
            if (allItems == null) allItems = new ArrayList<>();

            // ── Count stats ───────────────────────────────
            int totalCount = 0, completedCount = 0, pendingCount = 0, routineCount = 0;
            for (ActionItem item : allItems) {
                if (item.type == null || item.type.equals("history_routine")) continue;
                if (item.type.equals("routines")) { routineCount++; continue; }
                totalCount++;
                if (item.isCompleted) completedCount++;
                if (item.isPending)   pendingCount++;
            }

            // ── XP snapshot ───────────────────────────────
            int levelVal    = XPManager.getLevel(ctx);
            String rankVal  = XPManager.getTitle(ctx);
            int streakVal   = XPManager.getStreak(ctx);
            int xpVal       = XPManager.getTotalXP(ctx);

            // ══════════════════════════════════════════════
            //   FIX: declare final copies for lambda use
            //   Java requires variables in lambdas to be
            //   final or effectively final
            // ══════════════════════════════════════════════
            final int     total    = totalCount;
            final int     completed= completedCount;
            final int     pending  = pendingCount;
            final int     routines = routineCount;
            final int     level    = levelVal;
            final String  rank     = rankVal;
            final int     streak   = streakVal;
            final int     xp       = xpVal;
            final List<ActionItem> itemsFinal = allItems;

            // ── Build prompt ──────────────────────────────
            String prompt = buildSummaryPrompt(
                    total, completed, pending, routines, level, rank, streak, xp);

            // ── Call Groq (blocking with latch) ───────────
            final String[]       summaryHolder = {null};
            final CountDownLatch latch         = new CountDownLatch(1);

            List<AIEngine.AIMessage> history = new ArrayList<>();
            history.add(new AIEngine.AIMessage(prompt, true));

            GroqService.ask(ctx, history, prompt, itemsFinal, result -> {
                summaryHolder[0] = result.wasOnline
                        ? result.text
                        : buildOfflineSummary(total, completed, pending, streak, rank);
                latch.countDown();
            });

            // Wait max 15 seconds for Groq response
            boolean done = latch.await(15, TimeUnit.SECONDS);
            if (!done || summaryHolder[0] == null) {
                summaryHolder[0] = buildOfflineSummary(
                        total, completed, pending, streak, rank);
            }

            // ── Post notification ─────────────────────────
            postNotification(ctx, summaryHolder[0], completed, total);
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork error: " + e.getMessage());
            return Result.retry();
        }
    }

    // ══════════════════════════════════════════════════════
    //   BUILD GROQ PROMPT
    // ══════════════════════════════════════════════════════

    private String buildSummaryPrompt(int total, int completed, int pending,
                                      int routines, int level, String rank,
                                      int streak, int xp) {
        return "You are FocusFlow AI. Generate a SHORT, motivating weekly summary "
                + "(max 3 sentences, friendly tone, no markdown) based on:\n"
                + "- Tasks this week: " + total + " total, " + completed
                + " completed, " + pending + " pending\n"
                + "- Active routines: " + routines + "\n"
                + "- User level: " + level + " (" + rank + ")\n"
                + "- Current streak: " + streak + " days\n"
                + "- Total XP: " + xp + "\n"
                + "Start with an emoji. Be specific about numbers. "
                + "End with one actionable tip for next week.";
    }

    private String buildOfflineSummary(int total, int completed,
                                       int pending, int streak, String rank) {
        int pct = total > 0 ? (completed * 100 / total) : 0;
        String emoji = pct >= 80 ? "🔥" : pct >= 50 ? "💪" : "⚡";
        return emoji + " Week recap: " + completed + "/" + total
                + " tasks done (" + pct + "%) — you're a " + rank
                + " with a " + streak + "-day streak! "
                + (pending > 0
                ? pending + " tasks pending — clear them first next week. "
                : "Clean slate! ")
                + "Keep the momentum going 🚀";
    }

    // ══════════════════════════════════════════════════════
    //   POST NOTIFICATION
    // ══════════════════════════════════════════════════════

    private void postNotification(Context ctx, String summary,
                                  int completed, int total) {
        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Weekly Summary",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Your weekly FocusFlow AI recap");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(ctx, MainActivity.class);
        intent.putExtra("open_tab", R.id.nav_ai);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = completed == total && total > 0
                ? "🏆 Perfect week, champion!"
                : "📊 Your weekly FocusFlow recap";

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_send)
                        .setContentTitle(title)
                        .setContentText(summary)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setColor(0xFF4263EB);

        nm.notify(NOTIF_ID, builder.build());
        Log.d(TAG, "Weekly summary notification posted");
    }
}