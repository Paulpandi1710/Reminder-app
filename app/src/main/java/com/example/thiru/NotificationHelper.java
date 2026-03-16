package com.example.thiru;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class NotificationHelper {

    private static final String PREFS_NAME = "FocusNotifications";
    private static final String KEY_NOTIFS = "notifications_json";
    private static final int    MAX_NOTIFS = 60;

    // ── Add a notification ────────────────────────────────
    public static void add(Context context, String title, String body, String type) {
        try {
            List<NotificationItem> list = getAll(context);
            NotificationItem item = new NotificationItem(title, body, type);
            list.add(0, item);
            if (list.size() > MAX_NOTIFS) list = list.subList(0, MAX_NOTIFS);
            saveAll(context, list);
        } catch (Exception ignored) {}
    }

    // ── Get all notifications (newest first) ─────────────
    public static List<NotificationItem> getAll(Context context) {
        List<NotificationItem> list = new ArrayList<>();
        try {
            String json = prefs(context).getString(KEY_NOTIFS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                NotificationItem n = new NotificationItem();
                n.id        = o.optString("id", "");
                n.title     = o.optString("title", "");
                n.body      = o.optString("body", "");
                n.type      = o.optString("type", "system");
                n.timestamp = o.optLong("timestamp", 0);
                n.isRead    = o.optBoolean("isRead", false);
                list.add(n);
            }
        } catch (Exception ignored) {}
        return list;
    }

    // ── Unread count (for badge) ──────────────────────────
    public static int getUnreadCount(Context context) {
        int count = 0;
        for (NotificationItem n : getAll(context)) {
            if (!n.isRead) count++;
        }
        return count;
    }

    // ── Mark all as read ──────────────────────────────────
    public static void markAllRead(Context context) {
        try {
            List<NotificationItem> all = getAll(context);
            for (NotificationItem n : all) n.isRead = true;
            saveAll(context, all);
        } catch (Exception ignored) {}
    }

    // ── Clear all ─────────────────────────────────────────
    public static void clearAll(Context context) {
        prefs(context).edit().remove(KEY_NOTIFS).apply();
    }

    // ── Type → emoji ──────────────────────────────────────
    public static String typeIcon(String type) {
        if (type == null) return "🔔";
        switch (type) {
            case "alarm":    return "⏰";
            case "festival": return "🎉";
            case "geofence": return "📍";
            case "weekly":   return "📊";
            case "xp":       return "⚡";
            default:         return "🔔";
        }
    }

    // ── Relative time label ───────────────────────────────
    public static String relativeTime(long ts) {
        long diff = System.currentTimeMillis() - ts;
        long mins = diff / 60_000L;
        if (mins < 1)   return "Just now";
        if (mins < 60)  return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7)   return days + "d ago";
        return new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                .format(new java.util.Date(ts));
    }

    // ── Internal ──────────────────────────────────────────
    private static void saveAll(Context context, List<NotificationItem> list) {
        try {
            JSONArray arr = new JSONArray();
            for (NotificationItem n : list) {
                JSONObject o = new JSONObject();
                o.put("id",        n.id);
                o.put("title",     n.title);
                o.put("body",      n.body);
                o.put("type",      n.type);
                o.put("timestamp", n.timestamp);
                o.put("isRead",    n.isRead);
                arr.put(o);
            }
            prefs(context).edit().putString(KEY_NOTIFS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}