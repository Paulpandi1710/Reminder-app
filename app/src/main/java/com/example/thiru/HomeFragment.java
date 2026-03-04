package com.example.thiru;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private ActionAdapter adapter;
    private String currentTab = "routines";

    private TextView tvTabRoutines, tvTabTasks, tvTabPending;
    private View lineTabRoutines, lineTabTasks, lineTabPending;
    private ProgressBar progressBar;
    private TextView tvProgressPercent, tvProgressSubtitle, tvMotivation;
    private TextView tvNextTaskTimer;

    private List<ActionItem> allAppItems = new ArrayList<>();
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvTabRoutines = view.findViewById(R.id.tvTabRoutines);
        tvTabTasks = view.findViewById(R.id.tvTabTasks);
        tvTabPending = view.findViewById(R.id.tvTabPending);
        lineTabRoutines = view.findViewById(R.id.lineTabRoutines);
        lineTabTasks = view.findViewById(R.id.lineTabTasks);
        lineTabPending = view.findViewById(R.id.lineTabPending);
        progressBar = view.findViewById(R.id.progressBar);
        tvProgressPercent = view.findViewById(R.id.tvProgressPercent);
        tvProgressSubtitle = view.findViewById(R.id.tvProgressSubtitle);
        tvMotivation = view.findViewById(R.id.tvMotivation);
        tvNextTaskTimer = view.findViewById(R.id.tvNextTaskTimer);

        RecyclerView rvTasks = view.findViewById(R.id.rvTasks);
        rvTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ActionAdapter();
        rvTasks.setAdapter(adapter);

        // TRIGGERING THE EDIT SHEET INSTEAD OF DELETE
        adapter.setOnItemClickListener(new ActionAdapter.OnItemClickListener() {
            @Override
            public void onCheckClicked(ActionItem item) {
                item.isCompleted = !item.isCompleted;
                if (item.isCompleted) item.isPending = false; // Mark pending as done!
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.execute(() -> FocusDatabase.getInstance(getContext()).actionDao().update(item));
            }

            @Override
            public void onDeleteClicked(ActionItem item) {
                EditTaskBottomSheet editSheet = new EditTaskBottomSheet();
                editSheet.setActionItem(item);
                editSheet.show(getParentFragmentManager(), "EditTaskBottomSheet");
            }
        });

        tvTabRoutines.setOnClickListener(v -> switchTab("routines"));
        tvTabTasks.setOnClickListener(v -> switchTab("tasks"));
        tvTabPending.setOnClickListener(v -> switchTab("pending"));

        loadDataForCurrentTab();
        observeProgressAndTimer();
    }

    private void switchTab(String tabType) {
        currentTab = tabType;

        tvTabRoutines.setTextColor(Color.parseColor(tabType.equals("routines") ? "#4263EB" : "#868E96"));
        lineTabRoutines.setBackgroundColor(Color.parseColor(tabType.equals("routines") ? "#4263EB" : "#E0E0E0"));

        tvTabTasks.setTextColor(Color.parseColor(tabType.equals("tasks") ? "#4263EB" : "#868E96"));
        lineTabTasks.setBackgroundColor(Color.parseColor(tabType.equals("tasks") ? "#4263EB" : "#E0E0E0"));

        tvTabPending.setTextColor(Color.parseColor(tabType.equals("pending") ? "#4263EB" : "#868E96"));
        lineTabPending.setBackgroundColor(Color.parseColor(tabType.equals("pending") ? "#4263EB" : "#E0E0E0"));

        loadDataForCurrentTab();
    }

    private void loadDataForCurrentTab() {
        if (currentTab.equals("pending")) {
            FocusDatabase.getInstance(getContext()).actionDao().getPendingItems()
                    .observe(getViewLifecycleOwner(), items -> adapter.setItems(items));
        } else {
            FocusDatabase.getInstance(getContext()).actionDao().getItemsByType(currentTab)
                    .observe(getViewLifecycleOwner(), items -> adapter.setItems(items));
        }
    }

    private void observeProgressAndTimer() {
        FocusDatabase.getInstance(getContext()).actionDao().getAllItems()
                .observe(getViewLifecycleOwner(), allItems -> {
                    this.allAppItems = allItems;

                    if (allItems == null || allItems.isEmpty()) {
                        updateProgressUI(0, 0);
                        return;
                    }

                    int total = allItems.size();
                    int completed = 0;
                    long now = System.currentTimeMillis();
                    boolean foundMissedTasks = false;

                    ExecutorService executor = Executors.newSingleThreadExecutor();

                    for (ActionItem item : allItems) {
                        if (item.isCompleted) {
                            completed++;
                        } else if (!item.isPending) {
                            // --- NEW: THE AUTO-SWEEP LOGIC ---
                            // If it's not completed and not pending, check if the time has passed!
                            long taskTime = getTaskTimeMillis(item);

                            // Give a 1-minute grace period. If it's in the past, force it to PENDING.
                            if (taskTime < (now - 60000)) {
                                item.isPending = true;
                                executor.execute(() -> FocusDatabase.getInstance(getContext()).actionDao().update(item));
                                foundMissedTasks = true;
                            }
                        }
                    }

                    updateProgressUI(completed, total);
                    startLiveTimer();

                    // If we automatically moved things to pending, refresh the list if we are on the pending tab!
                    if (foundMissedTasks && currentTab.equals("pending")) {
                        loadDataForCurrentTab();
                    }
                });
    }

    private void startLiveTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateNextTaskTimer();
                timerHandler.postDelayed(this, 60000); // 1 minute
            }
        };
        timerRunnable.run();
    }

    private void updateNextTaskTimer() {
        if (allAppItems == null || allAppItems.isEmpty()) {
            tvNextTaskTimer.setText("Next: No upcoming tasks");
            return;
        }

        List<ActionItem> upcoming = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (ActionItem item : allAppItems) {
            if (!item.isCompleted && !item.isPending) {
                Calendar taskTime = Calendar.getInstance();
                if (item.type.equals("routines")) {
                    taskTime.set(Calendar.HOUR_OF_DAY, item.hour);
                    taskTime.set(Calendar.MINUTE, item.minute);
                    taskTime.set(Calendar.SECOND, 0);
                    if (taskTime.getTimeInMillis() < now) taskTime.add(Calendar.DAY_OF_YEAR, 1);
                } else {
                    taskTime.set(item.year, item.month, item.day, item.hour, item.minute, 0);
                }

                if (taskTime.getTimeInMillis() > now) upcoming.add(item);
            }
        }

        if (upcoming.isEmpty()) {
            tvNextTaskTimer.setText("Next: No upcoming tasks");
            return;
        }

        Collections.sort(upcoming, (a, b) -> Long.compare(getTaskTimeMillis(a), getTaskTimeMillis(b)));

        ActionItem nextTask = upcoming.get(0);
        long diffMillis = getTaskTimeMillis(nextTask) - now;
        long hours = (diffMillis / (1000 * 60 * 60)) % 24;
        long mins = (diffMillis / (1000 * 60)) % 60;

        String timeLeft = hours > 0 ? hours + "h " + mins + "m" : mins + "m";
        tvNextTaskTimer.setText("Next: " + nextTask.title + " in " + timeLeft);
    }

    private long getTaskTimeMillis(ActionItem item) {
        Calendar cal = Calendar.getInstance();
        if (item.type.equals("routines")) {
            cal.set(Calendar.HOUR_OF_DAY, item.hour);
            cal.set(Calendar.MINUTE, item.minute);
            cal.set(Calendar.SECOND, 0);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        } else {
            cal.set(item.year, item.month, item.day, item.hour, item.minute, 0);
        }
        return cal.getTimeInMillis();
    }

    private void updateProgressUI(int completed, int total) {
        int percent = (total == 0) ? 0 : (completed * 100) / total;
        progressBar.setProgress(percent);
        tvProgressPercent.setText(percent + "%");
        tvProgressSubtitle.setText("You have completed " + completed + " out of " + total + " objectives today.");

        if (percent == 100) tvMotivation.setText("All Done!");
        else if (percent >= 50) tvMotivation.setText("Almost there!");
        else if (percent > 0) tvMotivation.setText("Keep going!");
        else tvMotivation.setText("Ready to Flow?");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
    }
}