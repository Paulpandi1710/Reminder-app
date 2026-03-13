package com.example.thiru;

import android.content.Context;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AIEngine — 100% offline rule-based AI assistant.
 * No internet required. No API keys. Works on any device.
 *
 * Capabilities:
 *   1. Smart task suggestions based on existing schedule
 *   2. Productivity coach chat (answers questions about goals/habits)
 *   3. Auto-schedule: suggest best time slot for a new task
 *   4. Weekly performance summary with tips
 *   5. Conflict detection with calendar events
 */
public class AIEngine {

    // ── AI Message POJO ────────────────────────────────────
    public static class AIMessage {
        public final String text;
        public final boolean isUser;
        public final long timestamp;
        public final List<String> suggestions; // quick-reply chips

        public AIMessage(String text, boolean isUser) {
            this.text       = text;
            this.isUser     = isUser;
            this.timestamp  = System.currentTimeMillis();
            this.suggestions = new ArrayList<>();
        }

        public AIMessage(String text, boolean isUser, List<String> suggestions) {
            this.text        = text;
            this.isUser      = isUser;
            this.timestamp   = System.currentTimeMillis();
            this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        }
    }

    // ── Suggestion POJO ────────────────────────────────────
    public static class TaskSuggestion {
        public final String title;
        public final String type;       // "tasks" or "routines"
        public final int suggestedHour;
        public final int suggestedMinute;
        public final String reason;

        public TaskSuggestion(String title, String type,
                              int hour, int minute, String reason) {
            this.title          = title;
            this.type           = type;
            this.suggestedHour  = hour;
            this.suggestedMinute = minute;
            this.reason         = reason;
        }

        public String getTimeLabel() {
            String amPm = suggestedHour >= 12 ? "PM" : "AM";
            int h = suggestedHour % 12;
            if (h == 0) h = 12;
            return String.format(java.util.Locale.getDefault(),
                    "%d:%02d %s", h, suggestedMinute, amPm);
        }
    }

    // ─────────────────────────────────────────────────────
    //   1. CHAT — answers questions offline
    // ─────────────────────────────────────────────────────

    /**
     * Process a user message and return an AI reply.
     * Fully offline — uses keyword matching + context-aware rules.
     */
    public static AIMessage chat(Context context, String userMessage,
                                 List<ActionItem> currentItems) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new AIMessage("What would you like help with today?",
                    false, getDefaultChips());
        }

        String msg = userMessage.toLowerCase().trim();

        // ── XP / Level questions ──
        if (containsAny(msg, "level", "xp", "rank", "progress")) {
            int level    = XPManager.getLevel(context);
            String title = XPManager.getTitle(context);
            int total    = XPManager.getTotalXP(context);
            int streak   = XPManager.getStreak(context);
            int needed   = XPManager.getXPForNextLevel(context) - total;
            String next  = XPManager.getNextTitle(context);
            String reply = "You're at **Level " + level + " — " + title + "** 🏆\n\n"
                    + "Total XP: " + total + "\n"
                    + "Streak: " + streak + " day" + (streak != 1 ? "s 🔥" : " 🔥") + "\n\n";
            if (needed > 0) {
                reply += "You need **" + needed + " more XP** to reach " + next + ". "
                        + "Complete " + Math.ceil(needed / 10.0) + " more tasks to level up!";
            } else {
                reply += "You're at the maximum rank — Focus God 🔥 Amazing!";
            }
            return new AIMessage(reply, false,
                    list("How to earn more XP?", "Show my streak", "Today's tasks"));
        }

        // ── Productivity / tips ──
        if (containsAny(msg, "tip", "advice", "productivity", "improve", "better")) {
            return new AIMessage(getProductivityTip(currentItems), false,
                    list("Best time to work?", "How to build a habit?",
                            "Suggest tasks for me"));
        }

        // ── Habit building ──
        if (containsAny(msg, "habit", "routine", "consistent", "daily")) {
            return new AIMessage(getHabitAdvice(currentItems), false,
                    list("Add a morning routine", "Show my routines",
                            "Productivity tips"));
        }

        // ── Scheduling / time ──
        if (containsAny(msg, "schedule", "time", "when", "best time", "slot")) {
            return new AIMessage(getBestTimeAdvice(currentItems), false,
                    list("Suggest a task time", "Show free slots",
                            "What should I do now?"));
        }

        // ── What to do now / next ──
        if (containsAny(msg, "what", "now", "next", "do", "should", "today")) {
            return new AIMessage(getNextActionSuggestion(context, currentItems), false,
                    list("Show today's tasks", "Give me a tip",
                            "How am I doing?"));
        }

        // ── Stress / overwhelm ──
        if (containsAny(msg, "stress", "overwhelm", "too much", "busy", "can't",
                "tired", "exhausted")) {
            return new AIMessage(getStressAdvice(currentItems), false,
                    list("Focus on one task", "Take a break tip",
                            "Reduce my load"));
        }

        // ── Weekly summary ──
        if (containsAny(msg, "week", "summary", "report", "how", "doing",
                "performance")) {
            return new AIMessage(getWeeklySummary(context, currentItems), false,
                    list("How to improve?", "Best streak tips",
                            "Show my level"));
        }

        // ── Motivation ──
        if (containsAny(msg, "motivat", "inspire", "encourage", "push",
                "cheer", "help")) {
            return new AIMessage(getMotivation(context), false,
                    list("Give me a tip", "What should I do now?",
                            "How am I doing?"));
        }

        // ── Count / stats ──
        if (containsAny(msg, "how many", "count", "total", "stats")) {
            return new AIMessage(getStats(currentItems), false,
                    list("Weekly summary", "My level?",
                            "Productivity tips"));
        }

        // ── Default fallback with context ──
        return new AIMessage(getContextualGreeting(context, currentItems), false,
                getDefaultChips());
    }

    // ─────────────────────────────────────────────────────
    //   2. SMART TASK SUGGESTIONS
    // ─────────────────────────────────────────────────────

    /**
     * Generates task suggestions based on existing schedule patterns.
     * Offline — analyzes existing items only.
     */
    public static List<TaskSuggestion> getSmartSuggestions(List<ActionItem> items) {
        List<TaskSuggestion> suggestions = new ArrayList<>();

        boolean hasMorningRoutine = false;
        boolean hasEvening        = false;
        boolean hasExercise       = false;
        boolean hasReview         = false;
        int     taskCount         = 0;
        int     routineCount      = 0;

        for (ActionItem item : items) {
            if (item.type.equals("history_routine")) continue;
            String t = item.title.toLowerCase();
            if (item.type.equals("routines") && item.hour < 10) hasMorningRoutine = true;
            if (item.type.equals("routines") && item.hour >= 18) hasEvening = true;
            if (containsAny(t, "exercise", "gym", "workout", "run", "walk")) hasExercise = true;
            if (containsAny(t, "review", "plan", "reflect")) hasReview = true;
            if (item.type.equals("tasks")) taskCount++;
            if (item.type.equals("routines")) routineCount++;
        }

        // Suggest morning routine if none exists
        if (!hasMorningRoutine) {
            suggestions.add(new TaskSuggestion(
                    "Morning Routine",
                    "routines", 7, 0,
                    "Starting your day with a routine boosts productivity by 40%"));
        }

        // Suggest exercise
        if (!hasExercise) {
            suggestions.add(new TaskSuggestion(
                    "Daily Exercise",
                    "routines", 7, 30,
                    "Even 20 minutes of movement improves focus and mood"));
        }

        // Suggest evening review
        if (!hasReview) {
            suggestions.add(new TaskSuggestion(
                    "Daily Review & Planning",
                    "routines", 21, 0,
                    "5 minutes of evening review triples next-day productivity"));
        }

        // Suggest evening wind-down
        if (!hasEvening) {
            suggestions.add(new TaskSuggestion(
                    "Evening Wind-Down",
                    "routines", 22, 0,
                    "A consistent bedtime routine improves sleep quality"));
        }

        // Suggest deep work block if < 3 tasks today
        int todayTaskCount = countTodayItems(items);
        if (todayTaskCount < 3) {
            int bestHour = findBestFreeSlot(items);
            suggestions.add(new TaskSuggestion(
                    "Deep Work Block",
                    "tasks", bestHour, 0,
                    "You have free time at " + formatHour(bestHour)
                            + " — great for focused work"));
        }

        return suggestions;
    }

    // ─────────────────────────────────────────────────────
    //   3. AUTO-SCHEDULE — best time for a new task
    // ─────────────────────────────────────────────────────

    /**
     * Finds the best available time slot for a new task today.
     * Avoids existing scheduled items and calendar events.
     */
    public static int findBestFreeSlot(List<ActionItem> items) {
        // Build a set of busy hours
        java.util.Set<Integer> busyHours = new java.util.HashSet<>();
        for (ActionItem item : items) {
            if (!item.type.equals("history_routine")) {
                busyHours.add(item.hour);
                if (item.duration > 0) {
                    int endHour = item.hour + (item.minute + item.duration) / 60;
                    for (int h = item.hour; h <= endHour; h++) busyHours.add(h);
                }
            }
        }

        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        // Prefer focus hours: 9-11, 14-16, then others
        int[] preferredSlots = {9, 10, 14, 15, 16, 11, 8, 17, 7, 18, 19, 20};
        for (int slot : preferredSlots) {
            if (slot > currentHour && !busyHours.contains(slot)) return slot;
        }

        // Fallback: next available hour
        for (int h = currentHour + 1; h < 22; h++) {
            if (!busyHours.contains(h)) return h;
        }
        return 9; // default
    }

    // ─────────────────────────────────────────────────────
    //   4. WEEKLY SUMMARY
    // ─────────────────────────────────────────────────────

    public static AIMessage getWeeklySummaryMessage(Context context,
                                                    List<ActionItem> items) {
        return new AIMessage(getWeeklySummary(context, items), false,
                list("How to improve?", "My level?", "Task suggestions"));
    }

    // ─────────────────────────────────────────────────────
    //   5. CONFLICT DETECTION
    // ─────────────────────────────────────────────────────

    /**
     * Checks if a task at the given time conflicts with calendar events.
     * Returns a warning string, or null if no conflict.
     */
    public static String checkConflict(Context context, int hour, int minute,
                                       int durationMinutes) {
        if (!CalendarHelper.hasPermission(context)) return null;
        List<CalendarHelper.CalendarEvent> events =
                CalendarHelper.getTodayEvents(context);
        for (CalendarHelper.CalendarEvent event : events) {
            if (event.overlapsWith(hour, minute,
                    durationMinutes > 0 ? durationMinutes : 60)) {
                return "⚠️ Conflicts with \"" + event.title + "\" ("
                        + event.timeLabel + ")";
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────
    //   PRIVATE RESPONSE GENERATORS
    // ─────────────────────────────────────────────────────

    private static String getProductivityTip(List<ActionItem> items) {
        int completed = 0, total = 0;
        for (ActionItem i : items) {
            if (i.type.equals("history_routine")) continue;
            total++;
            if (i.isCompleted) completed++;
        }

        String[] tips = {
                "🎯 The **2-minute rule**: If a task takes less than 2 minutes, do it immediately.",
                "🧠 **Deep work** is most effective between 9–11 AM when your brain is freshest.",
                "⚡ **Time-blocking**: Assign each task a fixed time slot — it reduces decision fatigue.",
                "🔥 **Eat the frog**: Do your hardest task first thing in the morning.",
                "📵 Turn off notifications during your focus blocks — interruptions cost 23 minutes of recovery.",
                "✅ **Batch similar tasks** together — checking emails, making calls, writing.",
                "🌊 **Flow state** requires 20–25 minutes of uninterrupted focus to enter.",
                "😴 Sleep is your secret productivity weapon — 7–8 hours = 40% better focus."
        };

        int idx = (int)(System.currentTimeMillis() / 86_400_000L) % tips.length;
        String tip = tips[idx];

        if (total > 0 && completed == 0) {
            tip += "\n\nYou have " + total + " tasks waiting — start with the smallest one to build momentum! 💪";
        }
        return tip;
    }

    private static String getHabitAdvice(List<ActionItem> items) {
        long routineCount = 0;
        for (ActionItem i : items) {
            if (i.type.equals("routines")) routineCount++;
        }

        if (routineCount == 0) {
            return "You don't have any routines set yet! 🌱\n\n"
                    + "**Start with just one**: Pick a habit you want to build and schedule it at the same time every day. "
                    + "Consistency matters more than perfection.\n\n"
                    + "Tap **+** to add your first routine. I suggest a **7:00 AM Morning Routine** to start your day strong!";
        }

        return "You have **" + routineCount + " routine"
                + (routineCount > 1 ? "s" : "") + "** set up — great foundation! 🌟\n\n"
                + "**Habit stacking tip**: Link your new habit to an existing one.\n"
                + "Example: *'After I brush my teeth, I will meditate for 5 minutes'*\n\n"
                + "The brain builds habits through: **Cue → Routine → Reward**. "
                + "Make sure each routine has a clear trigger and a small reward!";
    }

    private static String getBestTimeAdvice(List<ActionItem> items) {
        int bestSlot = findBestFreeSlot(items);
        String timeStr = formatHour(bestSlot);
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        String energyZone;
        if (currentHour >= 6 && currentHour < 12) {
            energyZone = "You're in your **morning peak** 🌅 — best time for deep, creative work.";
        } else if (currentHour >= 12 && currentHour < 14) {
            energyZone = "It's **midday** ☀️ — good for meetings and collaborative tasks. Save hard thinking for later.";
        } else if (currentHour >= 14 && currentHour < 17) {
            energyZone = "You're in your **afternoon recovery** 🌤️ — great for admin tasks and routine work.";
        } else if (currentHour >= 17 && currentHour < 20) {
            energyZone = "It's **evening** 🌆 — good for planning tomorrow and lighter creative tasks.";
        } else {
            energyZone = "It's late 🌙 — wind down and prep for tomorrow. Your best work happens when you're rested.";
        }

        return energyZone + "\n\n"
                + "📅 Your next free slot is at **" + timeStr + "**.\n\n"
                + "**Science of peak performance:**\n"
                + "• 9–11 AM: Analytical thinking (your sharpest)\n"
                + "• 11 AM–1 PM: Communication and collaboration\n"
                + "• 2–4 PM: Physical tasks, routine work\n"
                + "• 5–7 PM: Creative, big-picture thinking";
    }

    private static String getNextActionSuggestion(Context context,
                                                  List<ActionItem> items) {
        // Find next uncompleted task
        ActionItem next = null;
        long now = System.currentTimeMillis();
        for (ActionItem item : items) {
            if (!item.isCompleted && !item.isPending
                    && !item.type.equals("history_routine")) {
                if (next == null || item.hour < next.hour
                        || (item.hour == next.hour && item.minute < next.minute)) {
                    next = item;
                }
            }
        }

        int xp = XPManager.getTotalXP(context);
        int level = XPManager.getLevel(context);

        if (next != null) {
            String type = next.type.equals("routines") ? "routine" : "task";
            return "Your next " + type + " is **\"" + next.title + "\"** "
                    + "at " + formatHour(next.hour) + " 🎯\n\n"
                    + "Complete it to earn +" + (next.type.equals("routines") ? "15" : "10")
                    + " XP and stay on track for Level " + level + "!\n\n"
                    + "Current XP: **" + xp + "** · "
                    + "Next level in: **" + Math.max(0, XPManager.getXPForNextLevel(context) - xp)
                    + " XP**";
        }

        // All done
        int completed = 0;
        for (ActionItem i : items) {
            if (!i.type.equals("history_routine") && i.isCompleted) completed++;
        }
        if (completed > 0) {
            return "🎉 You've completed **" + completed + " task"
                    + (completed > 1 ? "s" : "") + "** today — amazing work!\n\n"
                    + "You're at Level " + level + " with " + xp + " XP total. "
                    + "Take a well-deserved break, or add a new challenge with the **+** button!";
        }

        return "Your schedule looks clear for now! 🌟\n\n"
                + "This is a great time to:\n"
                + "• Add tasks for tomorrow\n"
                + "• Review your goals\n"
                + "• Do a 10-minute focus session\n\n"
                + "Tap **+** to add something productive!";
    }

    private static String getStressAdvice(List<ActionItem> items) {
        int pending = 0;
        for (ActionItem i : items) { if (i.isPending) pending++; }

        String base = "It's okay to feel overwhelmed sometimes. Here's how to reset 🧘\n\n"
                + "**Right now:**\n"
                + "1. Take 3 deep breaths (4 seconds in, 7 hold, 8 out)\n"
                + "2. Write down your top 3 priorities ONLY\n"
                + "3. Do just ONE thing for 25 minutes\n\n"
                + "**Remember:** Done is better than perfect. Progress beats perfection.";

        if (pending > 0) {
            base += "\n\nYou have **" + pending + " pending task"
                    + (pending > 1 ? "s" : "") + "**. "
                    + "Auto-rollover will move them to tomorrow if enabled in Settings.";
        }
        return base;
    }

    private static String getWeeklySummary(Context context, List<ActionItem> items) {
        int completed = 0, total = 0, routines = 0, tasks = 0;
        for (ActionItem i : items) {
            if (i.type.equals("history_routine")) continue;
            total++;
            if (i.isCompleted) {
                completed++;
                if (i.type.equals("routines")) routines++;
                else tasks++;
            }
        }

        int percent = total == 0 ? 0 : (completed * 100) / total;
        int level   = XPManager.getLevel(context);
        int xp      = XPManager.getTotalXP(context);
        int streak  = XPManager.getStreak(context);

        String grade = percent >= 90 ? "🏆 Outstanding"
                : percent >= 70 ? "⚡ Great"
                : percent >= 50 ? "💪 Good"
                : percent >= 30 ? "📈 Getting there"
                : "🌱 Just starting";

        StringBuilder sb = new StringBuilder();
        sb.append("**Your Performance Summary** ").append(grade).append("\n\n");
        sb.append("✅ Completed: ").append(completed).append(" / ").append(total)
                .append(" (").append(percent).append("%)\n");
        sb.append("🔁 Routines done: ").append(routines).append("\n");
        sb.append("📋 Tasks done: ").append(tasks).append("\n");
        sb.append("🔥 Current streak: ").append(streak).append(" day")
                .append(streak != 1 ? "s" : "").append("\n");
        sb.append("⚡ Level: ").append(level).append(" — ")
                .append(XPManager.getTitle(context)).append(" (").append(xp).append(" XP)\n\n");

        // Personalized tip based on score
        if (percent == 100) {
            sb.append("🎯 **Perfect score!** You're in elite focus territory. "
                    + "Keep this streak going!");
        } else if (percent >= 70) {
            sb.append("💡 **Tip:** You're doing great! Try completing routines "
                    + "before 10 AM to get the day's momentum going.");
        } else if (percent >= 40) {
            sb.append("💡 **Tip:** Focus on your top 3 tasks each day instead of "
                    + "trying to do everything. Quality over quantity.");
        } else {
            sb.append("💡 **Tip:** Start with just 1 small task today. "
                    + "Building momentum is the hardest part — after that, it flows.");
        }

        return sb.toString();
    }

    private static String getMotivation(Context context) {
        int streak = XPManager.getStreak(context);
        int level  = XPManager.getLevel(context);

        String[] quotes = {
                "Every expert was once a beginner. Every pro was once an amateur. 🌱",
                "You don't have to be great to start, but you have to start to be great. 🚀",
                "The secret of getting ahead is getting started. ✨",
                "Small daily improvements over time lead to stunning results. ⚡",
                "Focus on progress, not perfection. Each step forward counts. 💫",
                "Your future self is watching you right now through memories. Make them proud. 🔥",
                "Discipline is choosing between what you want now and what you want most. 🎯",
                "The pain of discipline is far less than the pain of regret. 💪"
        };

        int idx = (int)(System.currentTimeMillis() / 3_600_000L) % quotes.length;
        String quote = quotes[idx];

        String streak_msg = streak > 1
                ? "\n\nYou're on a **" + streak + "-day streak** 🔥 — don't break the chain!"
                : "\n\nYou're at **Level " + level + "** — every task brings you closer to Focus God 🔥";

        return quote + streak_msg;
    }

    private static String getStats(List<ActionItem> items) {
        int tasks = 0, routines = 0, completed = 0, pending = 0;
        for (ActionItem i : items) {
            if (i.type.equals("history_routine")) continue;
            if (i.type.equals("tasks")) tasks++;
            if (i.type.equals("routines")) routines++;
            if (i.isCompleted) completed++;
            if (i.isPending) pending++;
        }
        int total = tasks + routines;
        return "📊 **Your Current Stats**\n\n"
                + "📋 Total items: " + total + "\n"
                + "🔁 Routines: " + routines + "\n"
                + "✅ Tasks: " + tasks + "\n"
                + "🎯 Completed: " + completed + "\n"
                + "⏳ Pending: " + pending + "\n"
                + "📈 Completion rate: "
                + (total == 0 ? "0" : (completed * 100 / total)) + "%";
    }

    private static String getContextualGreeting(Context context,
                                                List<ActionItem> items) {
        int hour   = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int level  = XPManager.getLevel(context);
        String title = XPManager.getTitle(context);

        String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";

        int pending = 0, total = 0;
        for (ActionItem i : items) {
            if (!i.type.equals("history_routine")) {
                total++;
                if (i.isPending) pending++;
            }
        }

        String base = greeting + "! I'm your **FocusFlow AI** 🤖\n\n"
                + "You're **Level " + level + " — " + title + "**. ";

        if (total == 0) {
            base += "Your schedule is empty — tap **+** to add your first task!";
        } else if (pending > 0) {
            base += "You have **" + pending + " pending task"
                    + (pending > 1 ? "s" : "") + "**. Want me to help prioritize?";
        } else {
            base += "You have **" + total + " item"
                    + (total > 1 ? "s" : "") + "** scheduled. What can I help you with?";
        }

        return base;
    }

    // ─────────────────────────────────────────────────────
    //   HELPERS
    // ─────────────────────────────────────────────────────

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static int countTodayItems(List<ActionItem> items) {
        Calendar now = Calendar.getInstance();
        int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH),
                d = now.get(Calendar.DAY_OF_MONTH);
        int count = 0;
        for (ActionItem i : items) {
            if (i.type.equals("tasks")
                    && i.year == y && i.month == m && i.day == d) count++;
        }
        return count;
    }

    private static String formatHour(int hour) {
        String amPm = hour >= 12 ? "PM" : "AM";
        int h = hour % 12;
        if (h == 0) h = 12;
        return h + ":00 " + amPm;
    }

    private static List<String> list(String... items) {
        List<String> l = new ArrayList<>();
        Collections.addAll(l, items);
        return l;
    }

    private static List<String> getDefaultChips() {
        return list("What should I do now?", "Give me a tip",
                "How am I doing?", "Suggest tasks");
    }
}