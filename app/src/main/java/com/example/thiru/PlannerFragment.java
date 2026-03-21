package com.example.thiru;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public class PlannerFragment extends Fragment {

    private static final int MAX_CALENDAR_EVENTS_SHOWN = 3;

    private TimelineAdapter timelineAdapter;
    private TextView tvCurrentMonthYear, tvCurrentDateHeader;
    private LinearLayout layoutEmptyState;
    private RecyclerView rvTimeline;

    private LinearLayout layoutCalendarSection, layoutCalendarEvents;
    private TextView tvCalendarSectionTitle;
    private MaterialCardView layoutCalendarPermission;
    private TextView btnGrantCalendarPermission;

    private TextView tvSeeMoreEvents;
    private boolean calendarExpanded = false;
    private List<CalendarHelper.CalendarEvent> lastEvents = new ArrayList<>();

    private Calendar currentSelectedDate = Calendar.getInstance();
    private List<ActionItem> allDatabaseItems  = new ArrayList<>();

    private final ActivityResultLauncher<String> calendarPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!isAdded()) return;
                        if (granted) {
                            layoutCalendarPermission.setVisibility(View.GONE);
                            layoutCalendarSection.setVisibility(View.VISIBLE);
                            loadCalendarEvents();
                        }
                    });

    public PlannerFragment() {
        super(R.layout.fragment_planner);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvCurrentMonthYear       = view.findViewById(R.id.tvCurrentMonthYear);
        tvCurrentDateHeader      = view.findViewById(R.id.tvCurrentDateHeader);
        layoutEmptyState         = view.findViewById(R.id.layoutEmptyState);
        rvTimeline               = view.findViewById(R.id.rvTimeline);
        layoutCalendarSection    = view.findViewById(R.id.layoutCalendarSection);
        layoutCalendarEvents     = view.findViewById(R.id.layoutCalendarEvents);
        tvCalendarSectionTitle   = view.findViewById(R.id.tvCalendarSectionTitle);
        layoutCalendarPermission = view.findViewById(R.id.layoutCalendarPermission);
        btnGrantCalendarPermission = view.findViewById(R.id.btnGrantCalendarPermission);
        tvSeeMoreEvents          = view.findViewById(R.id.tvSeeMoreEvents);

        RecyclerView rvDates = view.findViewById(R.id.rvDates);
        rvDates.setLayoutManager(new LinearLayoutManager(
                getContext(), LinearLayoutManager.HORIZONTAL, false));
        DateAdapter dateAdapter = new DateAdapter(selectedDate -> {
            currentSelectedDate = selectedDate;
            calendarExpanded = false;
            updateDateHeader(selectedDate);
            filterAndDisplayTasks();
            loadCalendarEvents();
        });
        rvDates.setAdapter(dateAdapter);
        rvDates.scrollToPosition(30);

        rvTimeline.setLayoutManager(new LinearLayoutManager(getContext()));
        timelineAdapter = new TimelineAdapter();
        rvTimeline.setAdapter(timelineAdapter);

        btnGrantCalendarPermission.setOnClickListener(v ->
                calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR));

        if (tvSeeMoreEvents != null) {
            tvSeeMoreEvents.setOnClickListener(v -> {
                calendarExpanded = !calendarExpanded;
                displayCalendarEvents(lastEvents);
            });
        }

        loadTimelineData();
        updateDateHeader(currentSelectedDate);
        checkCalendarAndLoad();

        View scrollView = view.findViewById(R.id.plannerScrollView);
        if (scrollView != null) {
            scrollView.setTranslationY(120f);
            scrollView.setAlpha(0f);
            scrollView.animate().translationY(0f).alpha(1f)
                    .setDuration(600).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private void checkCalendarAndLoad() {
        if (!isAdded()) return;
        if (CalendarHelper.hasPermission(requireContext())) {
            layoutCalendarPermission.setVisibility(View.GONE);
            layoutCalendarSection.setVisibility(View.VISIBLE);
            loadCalendarEvents();
        } else {
            layoutCalendarSection.setVisibility(View.VISIBLE);
            layoutCalendarPermission.setVisibility(View.VISIBLE);
            layoutCalendarEvents.setVisibility(View.GONE);
        }
    }

    private void loadCalendarEvents() {
        if (!isAdded() || !CalendarHelper.hasPermission(requireContext())) return;

        int year  = currentSelectedDate.get(Calendar.YEAR);
        int month = currentSelectedDate.get(Calendar.MONTH);
        int day   = currentSelectedDate.get(Calendar.DAY_OF_MONTH);

        Executors.newSingleThreadExecutor().execute(() -> {
            // Live dynamic query to the device calendar
            List<CalendarHelper.CalendarEvent> rawEvents =
                    CalendarHelper.getEventsForDay(requireContext(), year, month, day);

            // ── THE FIX: Smart Deduplication ──
            // Removes duplicate festivals caused by syncing multiple email accounts
            List<CalendarHelper.CalendarEvent> uniqueEvents = new ArrayList<>();
            Set<String> seenTitles = new HashSet<>();

            for (CalendarHelper.CalendarEvent event : rawEvents) {
                // Normalize the title (lowercase, no extra spaces) to catch exact duplicates
                String normalizedTitle = event.title.trim().toLowerCase();

                // Set.add() returns false if the title already exists in the Set
                if (seenTitles.add(normalizedTitle)) {
                    uniqueEvents.add(event);
                }
            }

            requireActivity().runOnUiThread(() -> {
                if (isAdded()) {
                    lastEvents = uniqueEvents;
                    displayCalendarEvents(uniqueEvents);
                }
            });
        });
    }

    private void displayCalendarEvents(List<CalendarHelper.CalendarEvent> events) {
        if (!isAdded()) return;
        layoutCalendarEvents.removeAllViews();
        layoutCalendarEvents.setVisibility(View.VISIBLE);

        if (events.isEmpty()) {
            tvCalendarSectionTitle.setText("📅  Calendar Events");
            if (tvSeeMoreEvents != null) tvSeeMoreEvents.setVisibility(View.GONE);

            TextView tvEmpty = new TextView(requireContext());
            tvEmpty.setText("No device events today");
            tvEmpty.setTextColor(0xFF445588);
            tvEmpty.setTextSize(12f);
            tvEmpty.setPadding(dpToPx(4f), dpToPx(8f), dpToPx(4f), dpToPx(12f));
            layoutCalendarEvents.addView(tvEmpty);
            return;
        }

        int total   = events.size();
        int showing = calendarExpanded
                ? total
                : Math.min(MAX_CALENDAR_EVENTS_SHOWN, total);

        tvCalendarSectionTitle.setText("📅  "
                + total + " Event" + (total > 1 ? "s" : ""));

        for (int i = 0; i < showing; i++) {
            layoutCalendarEvents.addView(buildEventCard(events.get(i)));
        }

        if (tvSeeMoreEvents != null) {
            if (total > MAX_CALENDAR_EVENTS_SHOWN) {
                tvSeeMoreEvents.setVisibility(View.VISIBLE);
                if (calendarExpanded) {
                    tvSeeMoreEvents.setText("▲  Show less");
                } else {
                    int hidden = total - MAX_CALENDAR_EVENTS_SHOWN;
                    tvSeeMoreEvents.setText("▼  " + hidden + " more event"
                            + (hidden > 1 ? "s" : ""));
                }
            } else {
                tvSeeMoreEvents.setVisibility(View.GONE);
            }
        }
    }

    private View buildEventCard(CalendarHelper.CalendarEvent event) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, dpToPx(8f));
        card.setLayoutParams(cp);

        card.setCardBackgroundColor(0x0AFFFFFF);
        card.setRadius(dpToPx(16f));
        card.setCardElevation(0f);
        card.setStrokeWidth(dpToPx(1.5f));

        int calendarBaseColor = event.calendarColor != 0 ? (0xFF000000 | (event.calendarColor & 0xFFFFFF)) : 0xFF4263EB;
        int glassStrokeColor = (calendarBaseColor & 0x00FFFFFF) | 0x44000000;
        card.setStrokeColor(glassStrokeColor);

        LinearLayout inner = new LinearLayout(getContext());
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inner.setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(12f));

        View bar = new View(getContext());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dpToPx(4f), dpToPx(40f));
        bp.setMarginEnd(dpToPx(12f));
        bar.setLayoutParams(bp);
        bar.setBackgroundColor(calendarBaseColor);

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(event.title);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tvTime = new TextView(getContext());
        tvTime.setText(event.timeLabel
                + (event.durationLabel.isEmpty() ? "" : "  ·  " + event.durationLabel));
        tvTime.setTextColor(calendarBaseColor);
        tvTime.setTextSize(12f);
        tvTime.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dpToPx(2f);
        tvTime.setLayoutParams(tp);

        content.addView(tvTitle);
        content.addView(tvTime);

        if (event.location != null && !event.location.isEmpty()) {
            TextView tvLoc = new TextView(getContext());
            tvLoc.setText("📍 " + event.location);
            tvLoc.setTextColor(0xFF8899BB);
            tvLoc.setTextSize(11f);
            tvLoc.setMaxLines(1);
            tvLoc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dpToPx(2f);
            tvLoc.setLayoutParams(lp);
            content.addView(tvLoc);
        }

        inner.addView(bar);
        inner.addView(content);
        card.addView(inner);
        return card;
    }

    private void updateDateHeader(Calendar calendar) {
        tvCurrentMonthYear.setText(new SimpleDateFormat(
                "MMMM yyyy", Locale.getDefault()).format(calendar.getTime()));
        tvCurrentDateHeader.setText(new SimpleDateFormat(
                "EEEE, d", Locale.getDefault()).format(calendar.getTime()));
    }

    private void loadTimelineData() {
        FocusDatabase.getInstance(getContext()).actionDao()
                .getAllItems()
                .observe(getViewLifecycleOwner(), items -> {
                    allDatabaseItems = items != null ? items : new ArrayList<>();
                    filterAndDisplayTasks();
                });
    }

    private void filterAndDisplayTasks() {
        int ty = currentSelectedDate.get(Calendar.YEAR);
        int tm = currentSelectedDate.get(Calendar.MONTH);
        int td = currentSelectedDate.get(Calendar.DAY_OF_MONTH);

        List<ActionItem> filtered = new ArrayList<>();

        for (ActionItem item : allDatabaseItems) {
            if ("history_routine".equals(item.type) || "geofence".equals(item.type))
                continue;
            if ("routines".equals(item.type)) {
                filtered.add(item);
            } else if (item.year == ty && item.month == tm && item.day == td) {
                filtered.add(item);
            }
        }

        Collections.sort(filtered, (a, b) -> {
            if (a.hour != b.hour) return Integer.compare(a.hour, b.hour);
            return Integer.compare(a.minute, b.minute);
        });

        timelineAdapter.setItems(filtered);

        if (filtered.isEmpty()) {
            rvTimeline.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvTimeline.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkCalendarAndLoad();
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}