package com.example.thiru;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.xml.KonfettiView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

    private ActionAdapter adapter;
    private String currentTab = "routines";

    // ── Views ─────────────────────────────────────────────
    private TextView tvTabRoutines, tvTabTasks, tvTabPending, tvTabGeo;
    private View lineTabRoutines, lineTabTasks, lineTabPending;
    private ProgressBar progressBar;
    private TextView tvProgressPercent, tvProgressSubtitle, tvMotivation;
    private TextView tvNextTaskTimer;
    private TextView tvGreetingTime, tvGreetingName, tvDailyQuote;
    private ImageView ivProfilePhoto;
    private KonfettiView konfettiView;
    private EditText etSearch;
    private ImageView ivClearSearch;
    private MaterialCardView cardRolloverBanner;
    private TextView tvRolloverTitle, tvRolloverSubtitle;
    private ImageView ivDismissRollover;
    private MaterialCardView cardGreeting, cardProgress;
    private MaterialCardView cardXP;
    private TextView tvXPBadge, tvXPTitle, tvXPLevelLabel;
    private TextView tvXPNumbers, tvStreakValue, tvTotalXP;
    private View viewXPFill;

    // ── AI Priority sort ──────────────────────────────────
    private MaterialCardView cardAIPriority;
    private TextView tvAIPriorityLabel;
    private boolean aiSortActive = false;
    private List<ActionItem> lastTabItems = new ArrayList<>();

    // ── Geofence add button ───────────────────────────────
    private MaterialCardView cardAddGeofence;

    // ── State ─────────────────────────────────────────────
    private List<ActionItem> allAppItems = new ArrayList<>();
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private int currentAnimatedProgress = 0;
    private int lastCompletedCount = -1;
    private int lastTotalCount = -1;
    private Observer<List<ActionItem>> tabObserver = null;

    private static final String[] QUOTES = {
            "The secret of getting ahead is getting started.",
            "Focus on being productive instead of busy.",
            "Small steps every day lead to big results.",
            "Your future is created by what you do today.",
            "Discipline is the bridge between goals and accomplishment.",
            "One task at a time. Do it well.",
            "Progress, not perfection.",
            "Great things never come from comfort zones."
    };

    public HomeFragment() { super(R.layout.fragment_home); }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Bind views ────────────────────────────────────
        tvTabRoutines      = view.findViewById(R.id.tvTabRoutines);
        tvTabTasks         = view.findViewById(R.id.tvTabTasks);
        tvTabPending       = view.findViewById(R.id.tvTabPending);
        tvTabGeo           = view.findViewById(R.id.tvTabGeo);
        lineTabRoutines    = view.findViewById(R.id.lineTabRoutines);
        lineTabTasks       = view.findViewById(R.id.lineTabTasks);
        lineTabPending     = view.findViewById(R.id.lineTabPending);
        progressBar        = view.findViewById(R.id.progressBar);
        tvProgressPercent  = view.findViewById(R.id.tvProgressPercent);
        tvProgressSubtitle = view.findViewById(R.id.tvProgressSubtitle);
        tvMotivation       = view.findViewById(R.id.tvMotivation);
        tvNextTaskTimer    = view.findViewById(R.id.tvNextTaskTimer);
        tvGreetingTime     = view.findViewById(R.id.tvGreetingTime);
        tvGreetingName     = view.findViewById(R.id.tvGreetingName);
        tvDailyQuote       = view.findViewById(R.id.tvDailyQuote);
        ivProfilePhoto     = view.findViewById(R.id.ivProfilePhoto);
        konfettiView       = view.findViewById(R.id.konfettiView);
        etSearch           = view.findViewById(R.id.etSearch);
        ivClearSearch      = view.findViewById(R.id.ivClearSearch);
        cardRolloverBanner = view.findViewById(R.id.cardRolloverBanner);
        tvRolloverTitle    = view.findViewById(R.id.tvRolloverTitle);
        tvRolloverSubtitle = view.findViewById(R.id.tvRolloverSubtitle);
        ivDismissRollover  = view.findViewById(R.id.ivDismissRollover);
        cardGreeting       = view.findViewById(R.id.cardGreeting);
        cardProgress       = view.findViewById(R.id.cardProgress);
        cardXP             = view.findViewById(R.id.cardXP);
        tvXPBadge          = view.findViewById(R.id.tvXPBadge);
        tvXPTitle          = view.findViewById(R.id.tvXPTitle);
        tvXPLevelLabel     = view.findViewById(R.id.tvXPLevelLabel);
        tvXPNumbers        = view.findViewById(R.id.tvXPNumbers);
        tvStreakValue      = view.findViewById(R.id.tvStreakValue);
        tvTotalXP          = view.findViewById(R.id.tvTotalXP);
        viewXPFill         = view.findViewById(R.id.viewXPFill);
        cardAIPriority     = view.findViewById(R.id.cardAIPriority);
        tvAIPriorityLabel  = view.findViewById(R.id.tvAIPriorityLabel);
        cardAddGeofence    = view.findViewById(R.id.cardAddGeofence);

        setupAIPriorityButton();
        setupGeofenceButton();
        setupGreetingCard();
        setupSearchBar();
        runAutoRolloverIfEnabled();
        refreshXPCard();

        RecyclerView rvTasks = view.findViewById(R.id.rvTasks);
        rvTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ActionAdapter();
        rvTasks.setAdapter(adapter);
        setupSwipeGestures(rvTasks);

        adapter.setOnItemClickListener(new ActionAdapter.OnItemClickListener() {
            @Override
            public void onCheckClicked(ActionItem item) {
                // Geofence items cannot be "completed" like tasks — skip XP
                if ("geofence".equals(item.type)) return;
                item.isCompleted = !item.isCompleted;
                if (item.isCompleted) {
                    item.isPending = false;
                    launchConfetti();
                    XPManager.XPResult result = item.type.equals("routines")
                            ? XPManager.onRoutineCompleted(requireContext(), item.id)
                            : XPManager.onTaskCompleted(requireContext(), item.id);
                    if (result != null) handleXPResult(result);
                }
                Executors.newSingleThreadExecutor().execute(() ->
                        FocusDatabase.getInstance(getContext()).actionDao().update(item));
            }

            @Override
            public void onDeleteClicked(ActionItem item) {
                if ("geofence".equals(item.type)) {
                    // Remove geofence from Android system then delete from DB
                    GeofenceHelper.removeGeofence(requireContext(), String.valueOf(item.id));
                    Executors.newSingleThreadExecutor().execute(() ->
                            FocusDatabase.getInstance(getContext()).actionDao().delete(item));
                } else {
                    EditTaskBottomSheet editSheet = new EditTaskBottomSheet();
                    editSheet.setActionItem(item);
                    editSheet.show(getParentFragmentManager(), "EditTaskBottomSheet");
                }
            }
        });

        tvTabRoutines.setOnClickListener(v -> switchTab("routines"));
        tvTabTasks.setOnClickListener(v    -> switchTab("tasks"));
        tvTabPending.setOnClickListener(v  -> switchTab("pending"));
        tvTabGeo.setOnClickListener(v      -> switchTab("geofence"));

        loadDataForCurrentTab();
        observeProgressAndTimer();
        view.post(this::playEntranceAnimations);
    }

    // ══════════════════════════════════════════════════════
    //   GEOFENCE BUTTON SETUP
    // ══════════════════════════════════════════════════════

    private void setupGeofenceButton() {
        if (cardAddGeofence == null) return;
        cardAddGeofence.setOnClickListener(v -> {
            AddGeofenceBottomSheet sheet = new AddGeofenceBottomSheet();
            sheet.show(getParentFragmentManager(), "AddGeofenceBottomSheet");
        });
    }

    private void updateGeofenceButtonVisibility() {
        if (cardAddGeofence == null) return;
        boolean show = "geofence".equals(currentTab);
        cardAddGeofence.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ══════════════════════════════════════════════════════
    //   AI PRIORITY SORT
    // ══════════════════════════════════════════════════════

    private void setupAIPriorityButton() {
        if (cardAIPriority == null) return;
        cardAIPriority.setOnClickListener(v -> toggleAIPrioritySort());
    }

    private void toggleAIPrioritySort() {
        if (!isAdded() || adapter == null) return;
        aiSortActive = !aiSortActive;
        updateAIPriorityButtonUI();
        if (aiSortActive) {
            List<ActionItem> sorted = new ArrayList<>(lastTabItems);
            sorted.sort((a, b) ->
                    Integer.compare(calcPriorityScore(b), calcPriorityScore(a)));
            adapter.setItems(sorted);
            if (cardAIPriority != null) {
                cardAIPriority.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100)
                        .withEndAction(() -> {
                            if (isAdded())
                                cardAIPriority.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(150)
                                        .setInterpolator(new OvershootInterpolator(2f))
                                        .start();
                        }).start();
            }
            try {
                Snackbar.make(requireView(),
                        "🧠 AI sorted by urgency — 🔴 urgent first",
                        Snackbar.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        } else {
            adapter.setItems(lastTabItems);
        }
    }

    private void updateAIPriorityButtonUI() {
        if (cardAIPriority == null || tvAIPriorityLabel == null) return;
        if (aiSortActive) {
            cardAIPriority.setCardBackgroundColor(Color.parseColor("#4263EB"));
            cardAIPriority.setStrokeColor(Color.parseColor("#7B9BFF"));
            tvAIPriorityLabel.setTextColor(Color.WHITE);
            tvAIPriorityLabel.setText("🧠 AI Sorted");
        } else {
            cardAIPriority.setCardBackgroundColor(Color.parseColor("#0D0D22"));
            cardAIPriority.setStrokeColor(Color.parseColor("#2A3A7E"));
            tvAIPriorityLabel.setTextColor(Color.parseColor("#8899CC"));
            tvAIPriorityLabel.setText("🧠 AI Priority");
        }
    }

    private int calcPriorityScore(ActionItem item) {
        if (item.isCompleted) return -100;
        if ("geofence".equals(item.type)) return 5; // geofence items always mid-priority
        int score = 0;
        long now      = System.currentTimeMillis();
        long taskTime = getTaskTimeMillis(item);
        long diffMins = (taskTime - now) / 60000L;
        if (item.isPending)       score += 50;
        else if (diffMins < 0)    score += 50;
        else if (diffMins < 60)   score += 40;
        else if (diffMins < 180)  score += 30;
        else if (diffMins < 360)  score += 20;
        else if (diffMins < 1440) score += 10;
        if ("routines".equals(item.type)) score -= 5;
        return score;
    }

    public static String getPriorityLabel(ActionItem item) {
        if (item.isCompleted || "geofence".equals(item.type)) return "";
        long now      = System.currentTimeMillis();
        long diffMins = calcItemDiffMins(item, now);
        if (item.isPending || diffMins < 0) return "🔴";
        if (diffMins < 60)  return "🔴";
        if (diffMins < 180) return "🟡";
        if (diffMins < 720) return "🟢";
        return "";
    }

    private static long calcItemDiffMins(ActionItem item, long now) {
        Calendar cal = Calendar.getInstance();
        if ("routines".equals(item.type)) {
            cal.set(Calendar.HOUR_OF_DAY, item.hour);
            cal.set(Calendar.MINUTE, item.minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        } else {
            cal.set(item.year, item.month, item.day, item.hour, item.minute, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
        return (cal.getTimeInMillis() - now) / 60000L;
    }

    // ─────────────────────────────────────────────────────
    //   LOAD DATA FOR CURRENT TAB
    // ─────────────────────────────────────────────────────

    private void loadDataForCurrentTab() {
        if (!isAdded()) return;

        FocusDatabase.getInstance(getContext()).actionDao()
                .getItemsByType("routines").removeObservers(getViewLifecycleOwner());
        FocusDatabase.getInstance(getContext()).actionDao()
                .getItemsByType("tasks").removeObservers(getViewLifecycleOwner());
        FocusDatabase.getInstance(getContext()).actionDao()
                .getPendingItems().removeObservers(getViewLifecycleOwner());
        FocusDatabase.getInstance(getContext()).actionDao()
                .getItemsByType("geofence").removeObservers(getViewLifecycleOwner());

        tabObserver = items -> {
            lastTabItems = items != null ? items : new ArrayList<>();
            if (adapter != null) {
                if (aiSortActive) {
                    List<ActionItem> sorted = new ArrayList<>(lastTabItems);
                    sorted.sort((a, b) ->
                            Integer.compare(calcPriorityScore(b), calcPriorityScore(a)));
                    adapter.setItems(sorted);
                } else {
                    adapter.setItems(lastTabItems);
                }
            }
        };

        switch (currentTab) {
            case "pending":
                FocusDatabase.getInstance(getContext()).actionDao()
                        .getPendingItems()
                        .observe(getViewLifecycleOwner(), tabObserver);
                break;
            case "geofence":
                FocusDatabase.getInstance(getContext()).actionDao()
                        .getItemsByType("geofence")
                        .observe(getViewLifecycleOwner(), tabObserver);
                break;
            default:
                FocusDatabase.getInstance(getContext()).actionDao()
                        .getItemsByType(currentTab)
                        .observe(getViewLifecycleOwner(), tabObserver);
                break;
        }
    }

    // ─────────────────────────────────────────────────────
    //   OBSERVE PROGRESS AND TIMER
    // ─────────────────────────────────────────────────────

    private void observeProgressAndTimer() {
        FocusDatabase.getInstance(getContext()).actionDao()
                .getAllItems()
                .observe(getViewLifecycleOwner(), allItems -> {
                    this.allAppItems = allItems != null ? allItems : new ArrayList<>();
                    if (allItems == null || allItems.isEmpty()) {
                        updateProgressUI(0, 0);
                        return;
                    }

                    int total = 0, completed = 0;
                    Calendar now     = Calendar.getInstance();
                    int curYear      = now.get(Calendar.YEAR);
                    int curMonth     = now.get(Calendar.MONTH);
                    int curDay       = now.get(Calendar.DAY_OF_MONTH);
                    int curDayOfWeek = now.get(Calendar.DAY_OF_WEEK);
                    ExecutorService ex = Executors.newSingleThreadExecutor();
                    boolean needsRefresh = false;

                    for (ActionItem item : allItems) {
                        if (item.type.equals("history_routine")
                                || item.type.equals("geofence")) continue;
                        total++;
                        if (item.isCompleted) {
                            completed++;
                            if (item.type.equals("routines")
                                    && item.repeatMode != null
                                    && !item.repeatMode.equals("None")) {
                                boolean dateChanged =
                                        item.year != curYear
                                                || item.month != curMonth
                                                || item.day != curDay;
                                boolean shouldReset = false;
                                if (item.repeatMode.equals("Daily") && dateChanged) {
                                    shouldReset = true;
                                } else if (item.repeatMode.startsWith("Weekly") && dateChanged) {
                                    String dayPart = item.repeatMode.contains(":")
                                            ? item.repeatMode.split(": ")[1].trim() : "";
                                    if (curDayOfWeek == getDayOfWeekInt(dayPart))
                                        shouldReset = true;
                                }
                                if (shouldReset) {
                                    needsRefresh = true;
                                    ActionItem hist = new ActionItem(
                                            "history_routine", item.title, item.timeString,
                                            item.hour, item.minute, item.year, item.month,
                                            item.day, item.duration, item.description,
                                            item.repeatMode);
                                    hist.isCompleted = true;
                                    ex.execute(() -> FocusDatabase.getInstance(getContext())
                                            .actionDao().insert(hist));
                                    item.isCompleted = false;
                                    item.year  = curYear;
                                    item.month = curMonth;
                                    item.day   = curDay;
                                    ex.execute(() -> FocusDatabase.getInstance(getContext())
                                            .actionDao().update(item));
                                }
                            }
                        } else if (!item.isPending) {
                            long taskTime = getTaskTimeMillis(item);
                            if (taskTime < System.currentTimeMillis() - 60_000L) {
                                item.isPending = true;
                                ex.execute(() -> FocusDatabase.getInstance(getContext())
                                        .actionDao().update(item));
                                needsRefresh = true;
                            }
                        }
                    }

                    updateProgressUI(completed, total);
                    startLiveTimer();
                    if (needsRefresh) loadDataForCurrentTab();
                });
    }

    // ═══════════════════════════════════════════════════════
    //   XP SYSTEM — unchanged
    // ═══════════════════════════════════════════════════════

    private void handleXPResult(XPManager.XPResult result) {
        if (result == null || !isAdded()) return;
        showXPSnackbar(result.xpEarned);
        if (result.leveledUp) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded())
                    LevelUpDialog.show(requireContext(), result.newLevel,
                            result.newTitle, result.newBadge, result.xpEarned);
            }, 600);
        }
        refreshXPCard();
    }

    private void refreshXPCard() {
        if (!isAdded() || tvXPTitle == null) return;
        int level    = XPManager.getLevel(requireContext());
        String title = XPManager.getTitle(requireContext());
        String badge = XPManager.getBadge(requireContext());
        int inLevel  = XPManager.getXPInCurrentLevel(requireContext());
        int needed   = XPManager.getXPNeededForLevel(requireContext());
        int streak   = XPManager.getStreak(requireContext());
        int totalXP  = XPManager.getTotalXP(requireContext());
        float progress = XPManager.getLevelProgress(requireContext());
        tvXPBadge.setText(badge);
        tvXPTitle.setText(title);
        tvXPLevelLabel.setText("Level " + level);
        tvXPNumbers.setText(inLevel + " / " + needed + " XP");
        tvStreakValue.setText("🔥 " + streak + " day streak");
        tvTotalXP.setText(totalXP + " XP total");
        animateXPBar(progress);
        if (cardXP != null) {
            cardXP.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100)
                    .withEndAction(() -> cardXP.animate()
                            .scaleX(1f).scaleY(1f).setDuration(180)
                            .setInterpolator(new OvershootInterpolator(2f)).start())
                    .start();
        }
    }

    private void animateXPBar(float targetProgress) {
        if (viewXPFill == null) return;
        View parent = (View) viewXPFill.getParent();
        if (parent == null) return;
        Runnable doAnimate = () -> {
            int parentWidth = parent.getWidth();
            if (parentWidth == 0) return;
            int targetWidth = (int) (parentWidth * Math.min(targetProgress, 1f));
            ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
            anim.setDuration(900);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addUpdateListener(animation -> {
                int val = (int) animation.getAnimatedValue();
                android.view.ViewGroup.LayoutParams params = viewXPFill.getLayoutParams();
                params.width = val;
                viewXPFill.setLayoutParams(params);
            });
            anim.start();
        };
        if (parent.getWidth() > 0) {
            doAnimate.run();
        } else {
            parent.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            parent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            doAnimate.run();
                        }
                    });
        }
    }

    private void showXPSnackbar(int xpEarned) {
        if (!isAdded()) return;
        try {
            Snackbar sb = Snackbar.make(requireView(),
                    "⚡ +" + xpEarned + " XP earned!", Snackbar.LENGTH_SHORT);
            sb.setBackgroundTint(Color.parseColor("#1A4263EB"));
            sb.setTextColor(Color.parseColor("#7B9BFF"));
            sb.show();
        } catch (Exception ignored) {}
    }

    private void triggerDailyCompleteBonus() {
        if (!isAdded()) return;
        XPManager.XPResult result = XPManager.onDailyComplete(requireContext());
        if (result != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isAdded()) return;
                try {
                    int streak = XPManager.getStreak(requireContext());
                    Snackbar sb = Snackbar.make(requireView(),
                            "🎯 Daily complete! +" + result.xpEarned
                                    + " XP  🔥 " + streak + " day streak",
                            Snackbar.LENGTH_LONG);
                    sb.setBackgroundTint(Color.parseColor("#1A20C997"));
                    sb.setTextColor(Color.parseColor("#20C997"));
                    sb.show();
                } catch (Exception ignored) {}
                handleXPResult(result);
            }, 800);
        }
    }

    // ═══════════════════════════════════════════════════════
    //   ENTRANCE ANIMATIONS
    // ═══════════════════════════════════════════════════════

    private void playEntranceAnimations() {
        if (!isAdded()) return;
        animateCardIn(cardGreeting,  60,  80f, 480);
        animateCardIn(cardXP,       160,  60f, 450);
        animateCardIn(cardProgress, 240,  60f, 450);
        if (ivProfilePhoto != null) {
            ivProfilePhoto.setScaleX(0f);
            ivProfilePhoto.setScaleY(0f);
            ivProfilePhoto.animate().scaleX(1f).scaleY(1f)
                    .setDuration(550).setStartDelay(250)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
        }
    }

    private void animateCardIn(View v, int delay, float fromY, int duration) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(fromY);
        v.animate().alpha(1f).translationY(0f).setDuration(duration)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();
    }

    // ═══════════════════════════════════════════════════════
    //   PROGRESS UI
    // ═══════════════════════════════════════════════════════

    private void updateProgressUI(int completed, int total) {
        int target = (total == 0) ? 0 : (completed * 100) / total;
        ValueAnimator bar = ValueAnimator.ofInt(currentAnimatedProgress, target);
        bar.setDuration(900);
        bar.setInterpolator(new DecelerateInterpolator(1.5f));
        bar.addUpdateListener(a -> {
            int val = (int) a.getAnimatedValue();
            if (progressBar != null) progressBar.setProgress(val);
            if (tvProgressPercent != null) tvProgressPercent.setText(val + "%");
        });
        bar.start();
        currentAnimatedProgress = target;
        ValueAnimator count = ValueAnimator.ofInt(0, completed);
        count.setDuration(800);
        count.setInterpolator(new DecelerateInterpolator());
        count.addUpdateListener(a -> {
            if (tvProgressSubtitle != null)
                tvProgressSubtitle.setText("You have completed "
                        + a.getAnimatedValue() + " out of " + total + " objectives today.");
        });
        count.start();
        String motivation = target == 100 ? "All Done! 🎉"
                : target >= 50 ? "Almost there! 🔥"
                : target > 0   ? "Keep going! 💪"
                : "Ready to Flow?";
        if (tvMotivation != null) {
            final String m = motivation;
            tvMotivation.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                tvMotivation.setText(m);
                tvMotivation.animate().alpha(1f).setDuration(280).start();
            }).start();
        }
        if (target == 100 && total > 0
                && (completed != lastCompletedCount || total != lastTotalCount)) {
            triggerDailyCompleteBonus();
        }
        lastCompletedCount = completed;
        lastTotalCount = total;
    }

    // ═══════════════════════════════════════════════════════
    //   TAB SWITCHING
    // ═══════════════════════════════════════════════════════

    private void switchTab(String tabType) {
        currentTab = tabType;
        aiSortActive = false;
        updateAIPriorityButtonUI();
        updateGeofenceButtonVisibility();
        if (etSearch != null) etSearch.setText("");

        int active   = Color.parseColor("#FFFFFF");
        int inactive = Color.parseColor("#445588");

        tvTabRoutines.setTextColor(tabType.equals("routines") ? active : inactive);
        tvTabTasks.setTextColor(tabType.equals("tasks")       ? active : inactive);
        tvTabPending.setTextColor(tabType.equals("pending")   ? active : inactive);
        if (tvTabGeo != null)
            tvTabGeo.setTextColor(tabType.equals("geofence")  ? active : inactive);

        tvTabRoutines.setBackground(tabType.equals("routines")
                ? ContextCompat.getDrawable(requireContext(), R.drawable.tab_selected_bg) : null);
        tvTabTasks.setBackground(tabType.equals("tasks")
                ? ContextCompat.getDrawable(requireContext(), R.drawable.tab_selected_bg) : null);
        tvTabPending.setBackground(tabType.equals("pending")
                ? ContextCompat.getDrawable(requireContext(), R.drawable.tab_selected_bg) : null);
        if (tvTabGeo != null)
            tvTabGeo.setBackground(tabType.equals("geofence")
                    ? ContextCompat.getDrawable(requireContext(), R.drawable.tab_selected_bg) : null);

        TextView sel = tabType.equals("routines") ? tvTabRoutines
                : tabType.equals("tasks")    ? tvTabTasks
                : tabType.equals("pending")  ? tvTabPending
                : tvTabGeo;
        if (sel != null)
            sel.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100)
                    .withEndAction(() -> sel.animate()
                            .scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(new OvershootInterpolator(2.5f)).start())
                    .start();

        loadDataForCurrentTab();
    }

    // ═══════════════════════════════════════════════════════
    //   SWIPE GESTURES
    // ═══════════════════════════════════════════════════════

    private void setupSwipeGestures(RecyclerView rv) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView r,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder t) {
                adapter.moveItem(vh.getAdapterPosition(), t.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                ActionItem item = adapter.getItemAt(vh.getAdapterPosition());
                ExecutorService ex = Executors.newSingleThreadExecutor();
                if ("geofence".equals(item.type)) {
                    // Swipe left to delete geofence
                    GeofenceHelper.removeGeofence(requireContext(), String.valueOf(item.id));
                    ex.execute(() -> FocusDatabase.getInstance(getContext())
                            .actionDao().delete(item));
                    Snackbar.make(requireView(), "📍 Geofence removed", Snackbar.LENGTH_SHORT).show();
                    return;
                }
                if (dir == ItemTouchHelper.RIGHT) {
                    item.isCompleted = !item.isCompleted;
                    if (item.isCompleted) {
                        item.isPending = false;
                        launchConfetti();
                        XPManager.XPResult result = item.type.equals("routines")
                                ? XPManager.onRoutineCompleted(requireContext(), item.id)
                                : XPManager.onTaskCompleted(requireContext(), item.id);
                        if (result != null) handleXPResult(result);
                    }
                    ex.execute(() -> FocusDatabase.getInstance(getContext())
                            .actionDao().update(item));
                } else {
                    ex.execute(() -> {
                        FocusDatabase.getInstance(getContext()).actionDao().delete(item);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Snackbar sb = Snackbar.make(requireView(),
                                        "Flow deleted", Snackbar.LENGTH_LONG);
                                sb.setAction("UNDO", v ->
                                        Executors.newSingleThreadExecutor().execute(() ->
                                                FocusDatabase.getInstance(getContext())
                                                        .actionDao().insert(item)));
                                sb.setActionTextColor(Color.parseColor("#4263EB"));
                                sb.show();
                            });
                        }
                    });
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv2,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX, float dY, int state, boolean active) {
                View iv = vh.itemView;
                Paint p = new Paint();
                if (state == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    if (dX > 0) {
                        p.setColor(Color.parseColor("#1520C997"));
                        c.drawRoundRect(iv.getLeft(), iv.getTop(),
                                iv.getLeft() + dX, iv.getBottom(), 24f, 24f, p);
                    } else if (dX < 0) {
                        p.setColor(Color.parseColor("#15FA5252"));
                        c.drawRoundRect(iv.getRight() + dX, iv.getTop(),
                                iv.getRight(), iv.getBottom(), 24f, 24f, p);
                    }
                }
                super.onChildDraw(c, rv2, vh, dX, dY, state, active);
            }
        }).attachToRecyclerView(rv);
    }

    // ═══════════════════════════════════════════════════════
    //   AUTO-ROLLOVER
    // ═══════════════════════════════════════════════════════

    private void runAutoRolloverIfEnabled() {
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_rollover", true)) return;
        String today = getTodayDateString();
        if (today.equals(prefs.getString("last_rollover_date", ""))) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            Calendar y = Calendar.getInstance();
            y.add(Calendar.DAY_OF_YEAR, -1);
            Calendar t = Calendar.getInstance();
            List<ActionItem> all = FocusDatabase.getInstance(getContext())
                    .actionDao().getAllItemsSync();
            int rolled = 0;
            for (ActionItem item : all) {
                if (item.type.equals("tasks") && item.isPending
                        && item.year  == y.get(Calendar.YEAR)
                        && item.month == y.get(Calendar.MONTH)
                        && item.day   == y.get(Calendar.DAY_OF_MONTH)) {
                    item.year  = t.get(Calendar.YEAR);
                    item.month = t.get(Calendar.MONTH);
                    item.day   = t.get(Calendar.DAY_OF_MONTH);
                    item.isPending  = false;
                    item.timeString = formatTimeString(item.year, item.month,
                            item.day, item.hour, item.minute);
                    FocusDatabase.getInstance(getContext()).actionDao().update(item);
                    rolled++;
                }
            }
            prefs.edit().putString("last_rollover_date", today).apply();
            if (rolled > 0) {
                final int cnt = rolled;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded() && cardRolloverBanner != null) showRolloverBanner(cnt);
                });
            }
        });
    }

    private void showRolloverBanner(int count) {
        tvRolloverTitle.setText(count + " Task" + (count > 1 ? "s" : "") + " Rolled Over");
        tvRolloverSubtitle.setText("Yesterday's pending task"
                + (count > 1 ? "s" : "") + " moved to today ✨");
        cardRolloverBanner.setVisibility(View.VISIBLE);
        cardRolloverBanner.setAlpha(0f);
        cardRolloverBanner.setTranslationY(-60f);
        cardRolloverBanner.animate().alpha(1f).translationY(0f).setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
        ivDismissRollover.setOnClickListener(v -> dismissRolloverBanner());
        new Handler(Looper.getMainLooper()).postDelayed(this::dismissRolloverBanner, 6000);
    }

    private void dismissRolloverBanner() {
        if (cardRolloverBanner == null) return;
        cardRolloverBanner.animate().alpha(0f).translationY(-40f).setDuration(350)
                .withEndAction(() -> {
                    if (cardRolloverBanner != null) {
                        cardRolloverBanner.setVisibility(View.GONE);
                        cardRolloverBanner.setTranslationY(0f);
                    }
                }).start();
    }

    // ═══════════════════════════════════════════════════════
    //   SEARCH BAR
    // ═══════════════════════════════════════════════════════

    private void setupSearchBar() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                ivClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        ivClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            etSearch.clearFocus();
        });
    }

    // ═══════════════════════════════════════════════════════
    //   GREETING CARD
    // ═══════════════════════════════════════════════════════

    private void setupGreetingCard() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = h >= 5 && h < 12 ? "Good Morning ☀️"
                : h < 17 ? "Good Afternoon 🌤️"
                : h < 21 ? "Good Evening 🌆" : "Good Night 🌙";
        tvGreetingTime.setText(greeting);
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE);
        tvGreetingName.setText(prefs.getString("user_name", "Focus Master"));
        String photoUri = prefs.getString("profile_image", null);
        if (photoUri != null) {
            try { ivProfilePhoto.setImageURI(Uri.parse(photoUri)); }
            catch (Exception ignored) {}
        }
        String savedQuote = prefs.getString("saved_daily_quote", null);
        if (savedQuote != null && !savedQuote.isEmpty()) {
            tvDailyQuote.setText("\"" + savedQuote + "\"");
        } else {
            int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            tvDailyQuote.setText("\"" + QUOTES[day % QUOTES.length] + "\"");
        }
        if (isInternetAvailable()
                && !getTodayDateString().equals(prefs.getString("quote_fetch_date", ""))) {
            fetchQuoteFromInternet();
        }
    }

    // ═══════════════════════════════════════════════════════
    //   CONFETTI
    // ═══════════════════════════════════════════════════════

    private void launchConfetti() {
        if (konfettiView == null) return;
        Party party = new PartyFactory(new Emitter(300L, TimeUnit.MILLISECONDS).max(80))
                .spread(60)
                .colors(Arrays.asList(0xFF4263EB, 0xFF20C997, 0xFFFFD43B,
                        0xFFFA5252, 0xFFCC5DE8, 0xFF7B5FFF))
                .setSpeedBetween(2f, 6f)
                .position(new Position.Relative(0.5, 0.4))
                .build();
        konfettiView.start(party);
    }

    // ═══════════════════════════════════════════════════════
    //   TIMER
    // ═══════════════════════════════════════════════════════

    private void startLiveTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        timerRunnable = new Runnable() {
            @Override public void run() {
                updateNextTaskTimer();
                timerHandler.postDelayed(this, 60_000L);
            }
        };
        timerRunnable.run();
    }

    private void updateNextTaskTimer() {
        if (allAppItems.isEmpty()) {
            if (tvNextTaskTimer != null)
                tvNextTaskTimer.setText("Next: No upcoming tasks");
            return;
        }
        long now = System.currentTimeMillis();
        List<ActionItem> upcoming = new ArrayList<>();
        for (ActionItem item : allAppItems) {
            if (!item.isCompleted && !item.isPending
                    && !item.type.equals("history_routine")
                    && !item.type.equals("geofence")) {
                long t = getTaskTimeMillis(item);
                if (t > now) upcoming.add(item);
            }
        }
        if (upcoming.isEmpty()) {
            if (tvNextTaskTimer != null)
                tvNextTaskTimer.setText("Next: No upcoming tasks");
            return;
        }
        Collections.sort(upcoming, (a, b) ->
                Long.compare(getTaskTimeMillis(a), getTaskTimeMillis(b)));
        ActionItem next = upcoming.get(0);
        long diff  = getTaskTimeMillis(next) - now;
        long hours = (diff / 3_600_000L) % 24;
        long mins  = (diff / 60_000L) % 60;
        if (tvNextTaskTimer != null)
            tvNextTaskTimer.setText("Next: " + next.title + " in "
                    + (hours > 0 ? hours + "h " + mins + "m" : mins + "m"));
    }

    private long getTaskTimeMillis(ActionItem item) {
        Calendar cal = Calendar.getInstance();
        if (item.type.equals("routines")) {
            cal.set(Calendar.HOUR_OF_DAY, item.hour);
            cal.set(Calendar.MINUTE, item.minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() < System.currentTimeMillis())
                cal.add(Calendar.DAY_OF_YEAR, 1);
        } else {
            cal.set(item.year, item.month, item.day, item.hour, item.minute, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
        return cal.getTimeInMillis();
    }

    // ═══════════════════════════════════════════════════════
    //   HELPERS
    // ═══════════════════════════════════════════════════════

    private int getDayOfWeekInt(String day) {
        if (day == null) return Calendar.MONDAY;
        switch (day.trim()) {
            case "Monday":    return Calendar.MONDAY;
            case "Tuesday":   return Calendar.TUESDAY;
            case "Wednesday": return Calendar.WEDNESDAY;
            case "Thursday":  return Calendar.THURSDAY;
            case "Friday":    return Calendar.FRIDAY;
            case "Saturday":  return Calendar.SATURDAY;
            default:          return Calendar.SUNDAY;
        }
    }

    private boolean isInternetAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) requireContext()
                    .getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return false; }
    }

    private String getTodayDateString() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH)
                + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    private void fetchQuoteFromInternet() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL("https://zenquotes.io/api/today");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();
                    String json = sb.toString().trim();
                    if (json.startsWith("[")) json = json.substring(1);
                    if (json.endsWith("]"))   json = json.substring(0, json.length() - 1);
                    JSONObject obj = new JSONObject(json);
                    String q = obj.getString("q") + " — " + obj.getString("a");
                    android.content.SharedPreferences p = requireContext()
                            .getSharedPreferences("AppPrefs",
                                    android.content.Context.MODE_PRIVATE);
                    p.edit().putString("saved_daily_quote", q)
                            .putString("quote_fetch_date", getTodayDateString()).apply();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (tvDailyQuote != null && isAdded())
                            tvDailyQuote.setText("\"" + q + "\"");
                    });
                } else {
                    conn.disconnect();
                }
            } catch (Exception ignored) {}
        });
    }

    private String formatTimeString(int year, int month, int day, int hour, int minute) {
        String amPm = hour >= 12 ? "PM" : "AM";
        int h = hour % 12;
        if (h == 0) h = 12;
        return String.format("%02d/%02d/%04d %02d:%02d %s",
                day, month + 1, year, h, minute, amPm);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
    }
}