package com.example.thiru;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DurationEstimator — learns your personal task durations over time.
 * 100% offline. No internet needed.
 *
 * Priority order:
 *  1. Exact title match from your history (most accurate)
 *  2. Keyword overlap with past completed tasks
 *  3. Category average from your history
 *  4. Category built-in defaults (first-time use)
 *  5. Returns -1 → no suggestion shown
 */
public class DurationEstimator {

    private static final String PREFS = "DurationHistory";

    private static final Map<String, Integer> CATEGORY_DEFAULTS = new HashMap<String, Integer>() {{
        put("coding",   60);
        put("study",    45);
        put("health",   30);
        put("errands",  20);
        put("work",     45);
        put("home",     20);
        put("finance",  15);
        put("personal", 20);
    }};

    // ── Predict duration in minutes. Returns -1 if no prediction. ──
    public static int predict(Context ctx, String title,
                              String categoryId, List<ActionItem> history) {
        if (title == null || title.trim().length() < 3) return -1;
        String lower = title.toLowerCase().trim();

        // 1. Exact title match
        int exact = getExact(ctx, lower);
        if (exact > 0) return exact;

        // 2. Keyword overlap with history
        if (history != null && !history.isEmpty()) {
            int kw = keywordMatch(lower, history);
            if (kw > 0) return kw;
        }

        // 3. Category average from history
        if (history != null && !history.isEmpty() && categoryId != null) {
            int avg = categoryAvg(categoryId, history);
            if (avg > 0) return avg;
        }

        // 4. Category default
        if (categoryId != null && CATEGORY_DEFAULTS.containsKey(categoryId)) {
            return CATEGORY_DEFAULTS.get(categoryId);
        }

        return -1;
    }

    // ── Record a task completion so future predictions improve ──
    public static void record(Context ctx, String title, int minutes) {
        if (title == null || title.trim().isEmpty()) return;
        if (minutes <= 0 || minutes > 480) return;

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "t_" + title.toLowerCase().trim();
        int oldAvg   = prefs.getInt(key + "_a", 0);
        int oldCount = prefs.getInt(key + "_c", 0);
        int newCount = oldCount + 1;
        int newAvg   = oldCount == 0 ? minutes : (oldAvg * oldCount + minutes) / newCount;
        prefs.edit().putInt(key + "_a", newAvg).putInt(key + "_c", newCount).apply();
    }

    // ── Label shown in UI e.g. "⏱  ~45 min  ·  tap to use" ──
    public static String label(Context ctx, String title,
                               String categoryId, List<ActionItem> history) {
        int min = predict(ctx, title, categoryId, history);
        if (min <= 0) return null;
        String time = min >= 60
                ? (min / 60) + "h" + (min % 60 > 0 ? " " + min % 60 + "m" : "")
                : min + " min";
        String src = getExact(ctx, title != null ? title.toLowerCase().trim() : "") > 0
                ? "your history" : "similar tasks";
        return "⏱  ~" + time + "  ·  based on " + src;
    }

    // ── Private helpers ──
    private static int getExact(Context ctx, String lowerTitle) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt("t_" + lowerTitle + "_a", 0);
    }

    private static int keywordMatch(String lower, List<ActionItem> history) {
        String[] words = lower.split("\\s+");
        int total = 0, count = 0;
        for (ActionItem item : history) {
            if (item.duration <= 0 || item.duration > 480 || item.title == null) continue;
            String itemLower = item.title.toLowerCase();
            for (String w : words) {
                if (w.length() >= 4 && itemLower.contains(w)) {
                    total += item.duration;
                    count++;
                    break;
                }
            }
        }
        return count == 0 ? -1 : total / count;
    }

    private static int categoryAvg(String catId, List<ActionItem> history) {
        int total = 0, count = 0;
        for (ActionItem item : history) {
            if (item.duration <= 0 || item.duration > 480) continue;
            if (catId.equals(item.category)) { total += item.duration; count++; }
        }
        return count == 0 ? -1 : total / count;
    }
}