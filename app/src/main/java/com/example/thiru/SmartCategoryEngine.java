package com.example.thiru;

/**
 * SmartCategoryEngine — 100% offline local NLP.
 * Tags a task title with a category + emoji + color instantly.
 * Zero latency, zero internet. Runs on TextWatcher as user types.
 */
public class SmartCategoryEngine {

    public static class Category {
        public final String id;
        public final String label;
        public final String emoji;
        public final int color;
        public final int bgColor;

        public Category(String id, String label, String emoji, int color, int bgColor) {
            this.id      = id;
            this.label   = label;
            this.emoji   = emoji;
            this.color   = color;
            this.bgColor = bgColor;
        }

        public String display() { return emoji + "  " + label; }
    }

    public static final Category CAT_CODING   = new Category("coding",   "Coding",   "💻", 0xFF4263EB, 0xFF0D1A3A);
    public static final Category CAT_STUDY    = new Category("study",    "Study",    "📚", 0xFF7B5FFF, 0xFF160D3A);
    public static final Category CAT_HEALTH   = new Category("health",   "Health",   "🏃", 0xFF20C997, 0xFF0D2A20);
    public static final Category CAT_ERRANDS  = new Category("errands",  "Errands",  "🛒", 0xFFF59F00, 0xFF2A1E00);
    public static final Category CAT_WORK     = new Category("work",     "Work",     "💼", 0xFF339AF0, 0xFF0D1E2A);
    public static final Category CAT_HOME     = new Category("home",     "Home",     "🏠", 0xFFFF6B6B, 0xFF2A0D0D);
    public static final Category CAT_FINANCE  = new Category("finance",  "Finance",  "💰", 0xFF51CF66, 0xFF0D2A12);
    public static final Category CAT_PERSONAL = new Category("personal", "Personal", "🧘", 0xFFCC5DE8, 0xFF200D2A);
    public static final Category CAT_NONE     = new Category("none",     "General",  "📌", 0xFF445588, 0xFF0D1020);

    public static final Category[] ALL_CATEGORIES = {
            CAT_CODING, CAT_STUDY, CAT_HEALTH, CAT_ERRANDS,
            CAT_WORK, CAT_HOME, CAT_FINANCE, CAT_PERSONAL, CAT_NONE
    };

    private static final Object[][] KEYWORD_MAP = {
            // CODING
            {"fix bug", CAT_CODING}, {"fix the", CAT_CODING},
            {"code", CAT_CODING}, {"debug", CAT_CODING}, {"deploy", CAT_CODING},
            {"git", CAT_CODING}, {"api", CAT_CODING}, {"android", CAT_CODING},
            {"java", CAT_CODING}, {"kotlin", CAT_CODING}, {"python", CAT_CODING},
            {"backend", CAT_CODING}, {"frontend", CAT_CODING}, {"algorithm", CAT_CODING},
            {"program", CAT_CODING}, {"script", CAT_CODING}, {"compile", CAT_CODING},
            {"develop", CAT_CODING}, {"database", CAT_CODING}, {"server", CAT_CODING},
            // STUDY
            {"study", CAT_STUDY}, {"read chapter", CAT_STUDY},
            {"exam", CAT_STUDY}, {"notes", CAT_STUDY}, {"revision", CAT_STUDY},
            {"learn", CAT_STUDY}, {"lecture", CAT_STUDY}, {"homework", CAT_STUDY},
            {"assignment", CAT_STUDY}, {"course", CAT_STUDY}, {"quiz", CAT_STUDY},
            {"practice", CAT_STUDY}, {"research", CAT_STUDY}, {"review", CAT_STUDY},
            {"dbms", CAT_STUDY}, {"math", CAT_STUDY}, {"physics", CAT_STUDY},
            // HEALTH
            {"gym", CAT_HEALTH}, {"workout", CAT_HEALTH}, {"exercise", CAT_HEALTH},
            {"yoga", CAT_HEALTH}, {"meditat", CAT_HEALTH}, {"doctor", CAT_HEALTH},
            {"medicine", CAT_HEALTH}, {"diet", CAT_HEALTH}, {"health", CAT_HEALTH},
            {"stretch", CAT_HEALTH}, {"swim", CAT_HEALTH}, {"run", CAT_HEALTH},
            {"walk", CAT_HEALTH}, {"protein", CAT_HEALTH},
            // ERRANDS
            {"buy", CAT_ERRANDS}, {"pick up", CAT_ERRANDS}, {"shop", CAT_ERRANDS},
            {"groceries", CAT_ERRANDS}, {"pharmacy", CAT_ERRANDS}, {"store", CAT_ERRANDS},
            {"purchase", CAT_ERRANDS}, {"order", CAT_ERRANDS}, {"collect", CAT_ERRANDS},
            {"market", CAT_ERRANDS}, {"milk", CAT_ERRANDS},
            // WORK
            {"meeting", CAT_WORK}, {"email", CAT_WORK}, {"report", CAT_WORK},
            {"client", CAT_WORK}, {"present", CAT_WORK}, {"deadline", CAT_WORK},
            {"office", CAT_WORK}, {"interview", CAT_WORK}, {"resume", CAT_WORK},
            {"proposal", CAT_WORK}, {"follow up", CAT_WORK}, {"zoom", CAT_WORK},
            // HOME
            {"clean", CAT_HOME}, {"cook", CAT_HOME}, {"wash", CAT_HOME},
            {"repair", CAT_HOME}, {"organis", CAT_HOME}, {"organiz", CAT_HOME},
            {"laundry", CAT_HOME}, {"dishes", CAT_HOME}, {"vacuum", CAT_HOME},
            {"garden", CAT_HOME},
            // FINANCE
            {"pay", CAT_FINANCE}, {"bill", CAT_FINANCE}, {"bank", CAT_FINANCE},
            {"transfer", CAT_FINANCE}, {"invoice", CAT_FINANCE}, {"salary", CAT_FINANCE},
            {"budget", CAT_FINANCE}, {"tax", CAT_FINANCE}, {"invest", CAT_FINANCE},
            {"loan", CAT_FINANCE}, {"rent", CAT_FINANCE}, {"recharge", CAT_FINANCE},
            // PERSONAL
            {"journal", CAT_PERSONAL}, {"gratitude", CAT_PERSONAL}, {"hobby", CAT_PERSONAL},
            {"friend", CAT_PERSONAL}, {"family", CAT_PERSONAL}, {"birthday", CAT_PERSONAL},
            {"relax", CAT_PERSONAL}, {"movie", CAT_PERSONAL}, {"music", CAT_PERSONAL},
            {"travel", CAT_PERSONAL}, {"trip", CAT_PERSONAL}, {"photo", CAT_PERSONAL},
    };

    public static Category classify(String title) {
        if (title == null || title.trim().isEmpty()) return CAT_NONE;
        String lower = title.toLowerCase().trim();
        // Multi-word first (more specific)
        for (Object[] pair : KEYWORD_MAP) {
            String kw = (String) pair[0];
            if (kw.contains(" ") && lower.contains(kw)) return (Category) pair[1];
        }
        // Single word
        for (Object[] pair : KEYWORD_MAP) {
            String kw = (String) pair[0];
            if (!kw.contains(" ") && lower.contains(kw)) return (Category) pair[1];
        }
        return CAT_NONE;
    }

    public static Category fromId(String id) {
        if (id == null) return CAT_NONE;
        for (Category c : ALL_CATEGORIES) {
            if (c.id.equals(id)) return c;
        }
        return CAT_NONE;
    }
}