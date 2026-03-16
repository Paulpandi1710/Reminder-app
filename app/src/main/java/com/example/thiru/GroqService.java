package com.example.thiru;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

public class GroqService {

    private static final String TAG     = "GroqService";
    private static final String MODEL   = "llama-3.3-70b-versatile";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final int CONNECT_TIMEOUT   = 10000;
    private static final int READ_TIMEOUT      = 20000;
    private static final int MAX_HISTORY_TURNS = 6;

    public static final String ACTION_CREATE_TASK    = "CREATE_TASK";
    public static final String ACTION_CREATE_ROUTINE = "CREATE_ROUTINE";
    public static final String ACTION_NONE           = "NONE";

    public static class GroqResult {
        public final String     text;
        public final boolean    wasOnline;
        public final String     actionType;
        public final JSONObject actionData;

        public GroqResult(String text, boolean wasOnline,
                          String actionType, JSONObject actionData) {
            this.text       = text;
            this.wasOnline  = wasOnline;
            this.actionType = actionType;
            this.actionData = actionData;
        }
    }

    public interface GroqCallback {
        void onResult(GroqResult result);
    }

    // ─────────────────────────────────────────────────────
    //   PUBLIC ENTRY POINT
    // ─────────────────────────────────────────────────────

    public static void ask(Context context,
                           List<AIEngine.AIMessage> history,
                           String newMessage,
                           List<ActionItem> items,
                           GroqCallback callback) {

        if (!isOnline(context)) {
            Log.w(TAG, "Offline — using AIEngine");
            AIEngine.AIMessage r = AIEngine.chat(context, newMessage, items);
            callback.onResult(new GroqResult(r.text, false, ACTION_NONE, null));
            return;
        }

        String apiKey = getApiKey();
        if (apiKey.isEmpty() || apiKey.length() < 20) {
            Log.e(TAG, "Bad Groq API key");
            AIEngine.AIMessage r = AIEngine.chat(context, newMessage, items);
            callback.onResult(new GroqResult(r.text, false, ACTION_NONE, null));
            return;
        }

        Log.d(TAG, "Calling Groq | history="
                + (history != null ? history.size() : 0)
                + " | msg=" + newMessage);

        SharedPreferences prefs = context.getSharedPreferences(
                "AppPrefs", Context.MODE_PRIVATE);
        String rawName   = prefs.getString("user_name", "").trim();
        // ══════════════════════════════════════════════════
        //   FIX: If user has set a name, ALWAYS use it.
        //   Never fall back to "friend" when a real name
        //   exists. "friend" was appearing because the
        //   fallback "friend" was passed to Groq and Groq
        //   used it literally in every response.
        // ══════════════════════════════════════════════════
        String firstName = rawName.isEmpty()
                ? null  // null = no name set
                : rawName.split("\\s+")[0];

        boolean isActionRequest = detectActionIntent(newMessage);

        final String key          = apiKey;
        final String systemPrompt = isActionRequest
                ? buildActionSystemPrompt(firstName, context, items)
                : buildChatSystemPrompt(firstName, context, items);

        final double temperature = isActionRequest ? 0.1 : 0.7;

        Executors.newSingleThreadExecutor().execute(() -> {
            String raw = callGroq(key, systemPrompt, history,
                    newMessage, temperature);

            if (raw != null && !raw.isEmpty()) {
                Log.d(TAG, "Groq raw: "
                        + raw.substring(0, Math.min(200, raw.length())));

                if (isActionRequest) {
                    GroqResult parsed = parseActionResponse(raw);
                    if (parsed.actionType.equals(ACTION_NONE)) {
                        Log.w(TAG, "Action parse failed, retrying extraction");
                        // firstName may be null — pass empty string to tryHardParseJSON
                        GroqResult retry = tryHardParseJSON(raw,
                                firstName != null ? firstName : "");
                        callback.onResult(retry);
                    } else {
                        callback.onResult(parsed);
                    }
                } else {
                    callback.onResult(
                            new GroqResult(raw, true, ACTION_NONE, null));
                }
            } else {
                Log.w(TAG, "Groq failed — falling back to AIEngine");
                AIEngine.AIMessage r = AIEngine.chat(context, newMessage, items);
                callback.onResult(
                        new GroqResult(r.text, false, ACTION_NONE, null));
            }
        });
    }

    // ─────────────────────────────────────────────────────
    //   ACTION INTENT DETECTION — unchanged
    // ─────────────────────────────────────────────────────

    public static boolean detectActionIntent(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase().trim();

        String[] actionWords = {
                "add task", "create task", "new task", "set task",
                "add routine", "create routine", "new routine", "set routine",
                "schedule", "remind me", "add a task", "create a task",
                "add a routine", "create a routine", "set an alarm",
                "add reminder", "set reminder", "remind me to",
                "set a reminder", "make a task", "make a routine",
                "add", "create", "set", "schedule me", "put",
                "make me", "i need to", "don't forget"
        };

        String[] timeWords = {"at", "by", "on", "today", "tomorrow",
                "morning", "afternoon", "evening", "night",
                "am", "pm", "o'clock", "daily", "every"};

        for (String kw : actionWords) {
            if (lower.contains(kw)) {
                if (kw.equals("add") || kw.equals("create") || kw.equals("set")
                        || kw.equals("put") || kw.equals("make me")
                        || kw.equals("i need to") || kw.equals("don't forget")) {
                    for (String tw : timeWords) {
                        if (lower.contains(tw)) return true;
                    }
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────
    //   SYSTEM PROMPTS
    // ─────────────────────────────────────────────────────

    private static String buildChatSystemPrompt(String name, Context ctx,
                                                List<ActionItem> items) {
        String contextBlock = buildContextBlock(name, ctx, items);

        // ══════════════════════════════════════════════════
        //   FIX: "friend" was appearing because:
        //   1. name was passed as "friend" when no name set
        //   2. System prompt rule was weak — Groq ignored it
        //
        //   Now:
        //   - name is null when user hasn't set one
        //   - If null → do NOT address the user by name
        //   - If set  → MANDATORY to use it, forbidden to
        //     use "friend", "buddy", "pal" or any generic
        //     address word instead of the real name
        // ══════════════════════════════════════════════════
        String nameRule;
        if (name != null && !name.isEmpty()) {
            nameRule = "MANDATORY: The user's name is " + name + ". "
                    + "You MUST address them as " + name + " in every response. "
                    + "NEVER use 'friend', 'buddy', 'pal', 'there', or any other "
                    + "generic address word. ONLY use " + name + ".";
        } else {
            nameRule = "The user has not set their name. "
                    + "Do NOT use 'friend', 'buddy', or any generic placeholder. "
                    + "Simply do not address them by name at all.";
        }

        return "You are a smart, warm productivity coach inside FocusFlow app.\n"
                + contextBlock
                + "\nSTRICT RULES:\n"
                + "1. " + nameRule + "\n"
                + "2. Never start reply with 'I'.\n"
                + "3. Never repeat advice from earlier in the conversation.\n"
                + "4. Max 4 sentences. **Bold** key phrases. No bullet lists.\n"
                + "5. Be specific, warm, genuine — not generic.\n"
                + "6. Reference real XP/level/streak/task data when relevant.";
    }

    private static String buildActionSystemPrompt(String name, Context ctx,
                                                  List<ActionItem> items) {
        Calendar cal     = Calendar.getInstance();
        int todayDay     = cal.get(Calendar.DAY_OF_MONTH);
        int todayMonth   = cal.get(Calendar.MONTH) + 1;
        int todayYear    = cal.get(Calendar.YEAR);
        int tomorrowDay  = todayDay + 1;
        int todayHour    = cal.get(Calendar.HOUR_OF_DAY);

        // Use name in confirm message — fallback to empty string if null
        String displayName = (name != null && !name.isEmpty()) ? name : "";

        return "You are an AI assistant. Extract task/routine details and return ONLY JSON.\n"
                + "Today: " + todayDay + "/" + todayMonth + "/" + todayYear
                + " Time: " + todayHour + ":00\n\n"
                + "For a TASK (one-time):\n"
                + "{\"action\":\"CREATE_TASK\","
                + "\"title\":\"task title\","
                + "\"hour\":14,"
                + "\"minute\":0,"
                + "\"day\":" + todayDay + ","
                + "\"month\":" + todayMonth + ","
                + "\"year\":" + todayYear + ","
                + "\"description\":\"brief desc\","
                + "\"confirm_message\":\"✅ Done"
                + (displayName.isEmpty() ? "" : ", " + displayName)
                + "! Added [title] to your schedule at [time].\"}\n\n"
                + "For a ROUTINE (daily repeating):\n"
                + "{\"action\":\"CREATE_ROUTINE\","
                + "\"title\":\"routine title\","
                + "\"hour\":7,"
                + "\"minute\":0,"
                + "\"description\":\"brief desc\","
                + "\"confirm_message\":\"✅ Done"
                + (displayName.isEmpty() ? "" : ", " + displayName)
                + "! [title] set as daily routine at [time].\"}\n\n"
                + "RULES (follow exactly):\n"
                + "- Return ONLY the JSON object. Zero extra text before or after.\n"
                + "- No markdown, no code blocks, no explanation.\n"
                + "- 'tomorrow'=day " + tomorrowDay + ", 'today'=day " + todayDay + "\n"
                + "- 'morning'=7, 'afternoon'=14, 'evening'=18, 'night'=21\n"
                + "- 24-hour format for hour field.\n"
                + "- Repeating/daily → CREATE_ROUTINE. One-time → CREATE_TASK.\n"
                + "- If no time given: use 9 for tasks, 7 for routines.";
    }

    private static String buildContextBlock(String name, Context ctx,
                                            List<ActionItem> items) {
        int level    = XPManager.getLevel(ctx);
        String rank  = XPManager.getTitle(ctx);
        String badge = XPManager.getBadge(ctx);
        int xp       = XPManager.getTotalXP(ctx);
        int nextXP   = XPManager.getXPForNextLevel(ctx);
        int streak   = XPManager.getStreak(ctx);

        Calendar cal = Calendar.getInstance();
        int hour     = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfW   = cal.get(Calendar.DAY_OF_WEEK);
        String[] days = {"","Sunday","Monday","Tuesday",
                "Wednesday","Thursday","Friday","Saturday"};
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("d MMM yyyy",
                        java.util.Locale.getDefault());
        String date = sdf.format(cal.getTime());

        int total = 0, done = 0, pending = 0;
        StringBuilder sched = new StringBuilder();
        for (ActionItem item : items) {
            if (item.type == null || item.type.equals("history_routine")) continue;
            total++;
            if (item.isCompleted) done++;
            if (item.isPending)   pending++;
            if (sched.length() < 400)
                sched.append("• ").append(item.title)
                        .append(" [").append(item.isCompleted ? "done"
                                : item.isPending ? "pending" : "upcoming")
                        .append("]\n");
        }

        // Use "User: (name)" only if name is set
        String userLine = (name != null && !name.isEmpty())
                ? "User: " + name + "\n"
                : "User: (name not set)\n";

        return userLine
                + "Date: " + days[dayOfW] + " " + date
                + " | Time: " + formatHour(hour) + "\n"
                + "Level: " + level + " (" + rank + " " + badge
                + ") | XP: " + xp + "/" + nextXP
                + " | Streak: " + streak + " days\n"
                + "Tasks: " + done + "/" + total + " done"
                + (pending > 0 ? ", " + pending + " pending" : "") + "\n"
                + (sched.length() > 0 ? "Schedule:\n" + sched : "No tasks.\n");
    }

    // ─────────────────────────────────────────────────────
    //   PARSE ACTION RESPONSE — unchanged
    // ─────────────────────────────────────────────────────

    private static GroqResult parseActionResponse(String raw) {
        try {
            String json = extractJSON(raw);
            if (json == null) return new GroqResult(raw, true, ACTION_NONE, null);

            JSONObject obj    = new JSONObject(json);
            String action     = obj.optString("action", ACTION_NONE);
            String confirmMsg = obj.optString("confirm_message",
                    "✅ Done! I've added that to your schedule.");

            if (action.equals(ACTION_CREATE_TASK)
                    || action.equals(ACTION_CREATE_ROUTINE)) {
                return new GroqResult(confirmMsg, true, action, obj);
            }
            return new GroqResult(raw, true, ACTION_NONE, null);

        } catch (Exception e) {
            Log.e(TAG, "parseActionResponse failed: " + e.getMessage());
            return new GroqResult(raw, true, ACTION_NONE, null);
        }
    }

    private static GroqResult tryHardParseJSON(String raw, String userName) {
        try {
            String[] lines = raw.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("{") && line.endsWith("}")) {
                    try {
                        JSONObject obj = new JSONObject(line);
                        String action = obj.optString("action", ACTION_NONE);
                        if (action.equals(ACTION_CREATE_TASK)
                                || action.equals(ACTION_CREATE_ROUTINE)) {
                            String msg = obj.optString("confirm_message",
                                    "✅ Added to your schedule!");
                            return new GroqResult(msg, true, action, obj);
                        }
                    } catch (Exception ignored) {}
                }
            }

            int first = raw.indexOf('{');
            int last  = raw.lastIndexOf('}');
            if (first >= 0 && last > first) {
                String candidate = raw.substring(first, last + 1);
                JSONObject obj = new JSONObject(candidate);
                String action  = obj.optString("action", ACTION_NONE);
                if (action.equals(ACTION_CREATE_TASK)
                        || action.equals(ACTION_CREATE_ROUTINE)) {
                    String msg = obj.optString("confirm_message",
                            "✅ Added to your schedule!");
                    return new GroqResult(msg, true, action, obj);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "tryHardParseJSON: " + e.getMessage());
        }
        return new GroqResult(raw, true, ACTION_NONE, null);
    }

    private static String extractJSON(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.trim();

        if (s.contains("```json")) {
            int start = s.indexOf("```json") + 7;
            int end   = s.lastIndexOf("```");
            if (end > start) s = s.substring(start, end).trim();
        } else if (s.contains("```")) {
            int start = s.indexOf("```") + 3;
            int end   = s.lastIndexOf("```");
            if (end > start) s = s.substring(start, end).trim();
        }

        int firstBrace = s.indexOf('{');
        int lastBrace  = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1).trim();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────
    //   GROQ API CALL — unchanged
    // ─────────────────────────────────────────────────────

    private static String callGroq(String apiKey,
                                   String systemPrompt,
                                   List<AIEngine.AIMessage> history,
                                   String newMessage,
                                   double temperature) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);

            JSONArray messages = new JSONArray();

            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt));

            if (history != null && history.size() > 1) {
                int start = Math.max(1, history.size() - MAX_HISTORY_TURNS);
                for (int i = start; i < history.size(); i++) {
                    AIEngine.AIMessage msg = history.get(i);
                    if (msg.text == null || msg.text.trim().isEmpty()) continue;
                    String text = msg.text.length() > 300
                            ? msg.text.substring(0, 300) + "…" : msg.text;
                    messages.put(new JSONObject()
                            .put("role", msg.isUser ? "user" : "assistant")
                            .put("content", text));
                }
            }

            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", newMessage));

            JSONObject body = new JSONObject()
                    .put("model", MODEL)
                    .put("messages", messages)
                    .put("max_tokens", 400)
                    .put("temperature", temperature)
                    .put("top_p", 0.95);

            byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(input.length);
            try (OutputStream os = conn.getOutputStream()) { os.write(input); }

            int code = conn.getResponseCode();
            Log.d(TAG, "HTTP " + code);

            if (code == 429) {
                Log.w(TAG, "Rate limit hit");
                conn.disconnect();
                return null;
            }

            if (code != HttpURLConnection.HTTP_OK) {
                try (BufferedReader err = new BufferedReader(new InputStreamReader(
                        conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder e = new StringBuilder();
                    String line;
                    while ((line = err.readLine()) != null) e.append(line);
                    Log.e(TAG, "Error body: " + e);
                } catch (Exception ignored) {}
                conn.disconnect();
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            conn.disconnect();

            JSONObject resp   = new JSONObject(sb.toString());
            JSONArray choices = resp.getJSONArray("choices");
            if (choices.length() == 0) { Log.w(TAG, "0 choices"); return null; }
            return choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content").trim();

        } catch (Exception e) {
            Log.e(TAG, "callGroq exception: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────
    //   HELPERS — unchanged
    // ─────────────────────────────────────────────────────

    public static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return false; }
    }

    private static String getApiKey() {
        try { return BuildConfig.GROQ_API_KEY.trim(); }
        catch (Exception e) { return ""; }
    }

    private static String formatHour(int hour) {
        String ap = hour >= 12 ? "PM" : "AM";
        int h = hour % 12;
        if (h == 0) h = 12;
        return h + ":00 " + ap;
    }
}