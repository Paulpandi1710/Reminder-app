package com.example.thiru;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * XPManager — Handles all XP earning, level calculation, and title assignment.
 *
 * EXPLOIT PROTECTION:
 * Each ActionItem ID is stored when XP is awarded.
 * If the same item is unchecked and re-checked, NO XP is given again.
 * XP can only be earned ONCE per item per day.
 * The "completed IDs" set resets each new day automatically.
 *
 * XP values:
 * Complete a task     → +10 XP  (once per item per day)
 * Complete a routine  → +15 XP  (once per item per day)
 * First task of day   → +5 XP bonus (once per day total)
 * 100% daily done     → +50 XP bonus (once per day)
 * Streak day          → +20 XP per consecutive day (once per day)
 * Daily Login         → +15 XP (once per day)
 *
 * Level thresholds:
 * 1  Novice          0 XP
 * 2  Apprentice      100 XP
 * 3  Focused         250 XP
 * 4  Dedicated       500 XP
 * 5  Disciplined     800 XP
 * 6  Flow State      1200 XP
 * 7  Deep Worker     1700 XP
 * 8  Peak Performer  2300 XP
 * 9  Elite Focus     3000 XP
 * 10 Focus Master    4000 XP
 * 11 Legend          5500 XP
 * 12 Focus God       7500 XP
 */
public class XPManager {

    private static final String PREFS             = "XPPrefs";
    private static final String KEY_XP            = "total_xp";
    private static final String KEY_STREAK        = "streak_days";
    private static final String KEY_LAST_DAY      = "last_completion_date";
    private static final String KEY_FIRST_TASK    = "first_task_date";
    private static final String KEY_DAILY_BONUS   = "daily_bonus_date";
    private static final String KEY_EARNED_IDS    = "earned_ids_date";
    private static final String KEY_EARNED_IDS_SET = "earned_ids";

    // NEW: For tracking daily app opens
    private static final String KEY_LAST_LOGIN_DATE = "last_login_date";

    private static final int[] LEVEL_XP = {
            0, 100, 250, 500, 800, 1200, 1700, 2300, 3000, 4000, 5500, 7500
    };

    private static final String[] LEVEL_TITLES = {
            "Novice", "Apprentice", "Focused", "Dedicated", "Disciplined",
            "Flow State", "Deep Worker", "Peak Performer", "Elite Focus",
            "Focus Master", "Legend", "Focus God"
    };

    private static final String[] LEVEL_BADGES = {
            "🌱", "📖", "🎯", "💪", "⚡",
            "🌊", "🧠", "🚀", "👑", "🏆", "🌟", "🔥"
    };

    // ─────────────────────────────────────────────────────
    //   PUBLIC API
    // ─────────────────────────────────────────────────────

    /**
     * ── THE NEW FEATURE: DAILY LOGIN ──
     * Call when the app opens. If they haven't opened the app today, give them +15 XP!
     */
    public static XPResult handleDailyLogin(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = getTodayString();
        String lastLogin = prefs.getString(KEY_LAST_LOGIN_DATE, "");

        if (!today.equals(lastLogin)) {
            // First login today! Save the date so it doesn't trigger again.
            prefs.edit().putString(KEY_LAST_LOGIN_DATE, today).apply();
            return addXP(ctx, 15);
        }
        return null; // Already logged in today
    }

    /**
     * Call when a TASK item is checked as completed.
     */
    public static XPResult onTaskCompleted(Context ctx, int itemId) {
        if (!canEarnXP(ctx, itemId)) return null;
        markXPEarned(ctx, itemId);
        int earned = 10 + getFirstTaskBonus(ctx);
        return addXP(ctx, earned);
    }

    /**
     * Call when a ROUTINE item is checked as completed.
     */
    public static XPResult onRoutineCompleted(Context ctx, int itemId) {
        if (!canEarnXP(ctx, itemId)) return null;
        markXPEarned(ctx, itemId);
        int earned = 15 + getFirstTaskBonus(ctx);
        return addXP(ctx, earned);
    }

    /**
     * Call when user hits 100% daily completion.
     */
    public static XPResult onDailyComplete(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = getTodayString();
        if (today.equals(prefs.getString(KEY_DAILY_BONUS, ""))) return null;
        prefs.edit().putString(KEY_DAILY_BONUS, today).apply();
        int streakBonus = updateStreakAndGetBonus(ctx);
        return addXP(ctx, 50 + streakBonus);
    }

    // ── Read-only getters ─────────────────────────────────

    public static int getTotalXP(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_XP, 0);
    }

    public static int getLevel(Context ctx) {
        return getLevelForXP(getTotalXP(ctx));
    }

    public static String getTitle(Context ctx) {
        return LEVEL_TITLES[getLevel(ctx) - 1];
    }

    public static String getBadge(Context ctx) {
        return LEVEL_BADGES[getLevel(ctx) - 1];
    }

    public static int getXPForNextLevel(Context ctx) {
        int level = getLevel(ctx);
        if (level >= LEVEL_TITLES.length) return 0;
        return LEVEL_XP[level];
    }

    public static int getXPForCurrentLevel(Context ctx) {
        return LEVEL_XP[getLevel(ctx) - 1];
    }

    public static float getLevelProgress(Context ctx) {
        int totalXP  = getTotalXP(ctx);
        int level    = getLevel(ctx);
        if (level >= LEVEL_TITLES.length) return 1f;
        int levelStart = LEVEL_XP[level - 1];
        int levelEnd   = LEVEL_XP[level];
        if (levelEnd == levelStart) return 1f;
        return (float)(totalXP - levelStart) / (levelEnd - levelStart);
    }

    public static int getXPInCurrentLevel(Context ctx) {
        return getTotalXP(ctx) - getXPForCurrentLevel(ctx);
    }

    public static int getXPNeededForLevel(Context ctx) {
        int level = getLevel(ctx);
        if (level >= LEVEL_TITLES.length) return 0;
        return LEVEL_XP[level] - LEVEL_XP[level - 1];
    }

    public static int getStreak(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_STREAK, 0);
    }

    public static String getLevelSummary(Context ctx) {
        return "Level " + getLevel(ctx) + " · " + getTitle(ctx) + "  " + getBadge(ctx);
    }

    public static String getNextTitle(Context ctx) {
        int level = getLevel(ctx);
        if (level >= LEVEL_TITLES.length) return "Focus God 🔥";
        return LEVEL_TITLES[level];
    }

    public static String getNextBadge(Context ctx) {
        int level = getLevel(ctx);
        if (level >= LEVEL_BADGES.length) return "🔥";
        return LEVEL_BADGES[level];
    }

    // ─────────────────────────────────────────────────────
    //   EXPLOIT PROTECTION HELPERS
    // ─────────────────────────────────────────────────────

    private static boolean canEarnXP(Context ctx, int itemId) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today    = getTodayString();
        String savedDay = prefs.getString(KEY_EARNED_IDS_SET + "_day", "");

        if (!today.equals(savedDay)) {
            prefs.edit()
                    .putString(KEY_EARNED_IDS_SET + "_day", today)
                    .putString(KEY_EARNED_IDS, "")
                    .apply();
            return true;
        }

        String earnedIds = prefs.getString(KEY_EARNED_IDS, "");
        String searchToken = "," + itemId + ",";
        String wrappedIds  = "," + earnedIds + ",";
        return !wrappedIds.contains(searchToken);
    }

    private static void markXPEarned(Context ctx, int itemId) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = prefs.getString(KEY_EARNED_IDS, "");
        String updated = current.isEmpty() ? String.valueOf(itemId)
                : current + "," + itemId;
        prefs.edit().putString(KEY_EARNED_IDS, updated).apply();
    }

    // ─────────────────────────────────────────────────────
    //   INTERNAL HELPERS
    // ─────────────────────────────────────────────────────

    private static XPResult addXP(Context ctx, int amount) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int oldXP    = prefs.getInt(KEY_XP, 0);
        int newXP    = oldXP + amount;
        int oldLevel = getLevelForXP(oldXP);
        int newLevel = getLevelForXP(newXP);
        prefs.edit().putInt(KEY_XP, newXP).apply();

        boolean leveledUp = newLevel > oldLevel;
        return new XPResult(amount, newXP, newLevel, leveledUp,
                leveledUp ? LEVEL_TITLES[newLevel - 1] : null,
                leveledUp ? LEVEL_BADGES[newLevel - 1] : null);
    }

    private static int getLevelForXP(int xp) {
        int level = 1;
        for (int i = LEVEL_XP.length - 1; i >= 0; i--) {
            if (xp >= LEVEL_XP[i]) { level = i + 1; break; }
        }
        return Math.min(level, LEVEL_TITLES.length);
    }

    private static int getFirstTaskBonus(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = getTodayString();
        if (!today.equals(prefs.getString(KEY_FIRST_TASK, ""))) {
            prefs.edit().putString(KEY_FIRST_TASK, today).apply();
            return 5;
        }
        return 0;
    }

    private static int updateStreakAndGetBonus(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today     = getTodayString();
        String lastDay   = prefs.getString(KEY_LAST_DAY, "");
        int streak       = prefs.getInt(KEY_STREAK, 0);
        if (today.equals(lastDay)) return streak * 20;
        streak = getYesterdayString().equals(lastDay) ? streak + 1 : 1;
        prefs.edit().putString(KEY_LAST_DAY, today).putInt(KEY_STREAK, streak).apply();
        return streak * 20;
    }

    private static String getTodayString() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return c.get(java.util.Calendar.YEAR) + "-"
                + c.get(java.util.Calendar.MONTH) + "-"
                + c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    private static String getYesterdayString() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_YEAR, -1);
        return c.get(java.util.Calendar.YEAR) + "-"
                + c.get(java.util.Calendar.MONTH) + "-"
                + c.get(java.util.Calendar.DAY_OF_MONTH);
    }

    // ─────────────────────────────────────────────────────
    //   RESULT OBJECT
    // ─────────────────────────────────────────────────────

    public static class XPResult {
        public final int xpEarned;
        public final int totalXP;
        public final int newLevel;
        public final boolean leveledUp;
        public final String newTitle;
        public final String newBadge;

        XPResult(int xpEarned, int totalXP, int newLevel,
                 boolean leveledUp, String newTitle, String newBadge) {
            this.xpEarned  = xpEarned;
            this.totalXP   = totalXP;
            this.newLevel  = newLevel;
            this.leveledUp = leveledUp;
            this.newTitle  = newTitle;
            this.newBadge  = newBadge;
        }
    }
}