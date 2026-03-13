package com.example.thiru;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class PlannerFragment extends Fragment {

    // ── Existing views ─────────────────────────────────
    private TimelineAdapter timelineAdapter;
    private TextView tvCurrentMonthYear;
    private TextView tvCurrentDateHeader;
    private LinearLayout layoutEmptyState;
    private RecyclerView rvTimeline;

    // ── Calendar views ──────────────────────────────────
    private LinearLayout layoutCalendarSection;
    private LinearLayout layoutCalendarEvents;
    private TextView tvCalendarSectionTitle;
    private MaterialCardView layoutCalendarPermission;
    private TextView btnGrantCalendarPermission;

    // ── State ───────────────────────────────────────────
    private Calendar currentSelectedDate = Calendar.getInstance();
    private List<ActionItem> allDatabaseItems = new ArrayList<>();

    // ── Calendar permission launcher ────────────────────
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

        // ── Bind views ─────────────────────────────────
        tvCurrentMonthYear       = view.findViewById(R.id.tvCurrentMonthYear);
        tvCurrentDateHeader      = view.findViewById(R.id.tvCurrentDateHeader);
        layoutEmptyState         = view.findViewById(R.id.layoutEmptyState);
        rvTimeline               = view.findViewById(R.id.rvTimeline);
        layoutCalendarSection    = view.findViewById(R.id.layoutCalendarSection);
        layoutCalendarEvents     = view.findViewById(R.id.layoutCalendarEvents);
        tvCalendarSectionTitle   = view.findViewById(R.id.tvCalendarSectionTitle);
        layoutCalendarPermission = view.findViewById(R.id.layoutCalendarPermission);
        btnGrantCalendarPermission = view.findViewById(R.id.btnGrantCalendarPermission);

        // ── Date strip ─────────────────────────────────
        RecyclerView rvDates = view.findViewById(R.id.rvDates);
        rvDates.setLayoutManager(new LinearLayoutManager(
                getContext(), LinearLayoutManager.HORIZONTAL, false));
        DateAdapter dateAdapter = new DateAdapter(selectedDate -> {
            currentSelectedDate = selectedDate;
            updateDateHeader(selectedDate);
            filterAndDisplayTasks();
            loadCalendarEvents();
        });
        rvDates.setAdapter(dateAdapter);
        rvDates.scrollToPosition(30);

        // ── Timeline ───────────────────────────────────
        rvTimeline.setLayoutManager(new LinearLayoutManager(getContext()));
        timelineAdapter = new TimelineAdapter();
        rvTimeline.setAdapter(timelineAdapter);

        // ── Calendar permission button ──────────────────
        btnGrantCalendarPermission.setOnClickListener(v ->
                calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR));

        // ── Load data ──────────────────────────────────
        loadTimelineData();
        updateDateHeader(currentSelectedDate);
        checkCalendarAndLoad();
    }

    // ═══════════════════════════════════════════════════
    //   CALENDAR
    // ═══════════════════════════════════════════════════

    private void checkCalendarAndLoad() {
        if (!isAdded()) return;
        if (CalendarHelper.hasPermission(requireContext())) {
            layoutCalendarPermission.setVisibility(View.GONE);
            layoutCalendarSection.setVisibility(View.VISIBLE);
            loadCalendarEvents();
        } else {
            // Show section with permission banner only
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
            List<CalendarHelper.CalendarEvent> events =
                    CalendarHelper.getEventsForDay(requireContext(), year, month, day);
            requireActivity().runOnUiThread(() -> {
                if (isAdded()) displayCalendarEvents(events);
            });
        });
    }

    private void displayCalendarEvents(List<CalendarHelper.CalendarEvent> events) {
        layoutCalendarEvents.removeAllViews();
        layoutCalendarEvents.setVisibility(View.VISIBLE);

        if (events.isEmpty()) {
            TextView noEvents = new TextView(getContext());
            noEvents.setText("No meetings scheduled");
            noEvents.setTextColor(0xFF445588);
            noEvents.setTextSize(12f);
            noEvents.setPadding(0, 2, 0, 6);
            layoutCalendarEvents.addView(noEvents);
            tvCalendarSectionTitle.setText("📅  Calendar Events");
            return;
        }

        tvCalendarSectionTitle.setText("📅  " + events.size()
                + " Calendar Event" + (events.size() > 1 ? "s" : ""));

        for (CalendarHelper.CalendarEvent event : events) {
            layoutCalendarEvents.addView(buildEventCard(event));
        }
    }

    private View buildEventCard(CalendarHelper.CalendarEvent event) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, dpToPx(6));
        card.setLayoutParams(cp);
        card.setCardBackgroundColor(0xFF0E0E24);
        card.setRadius(dpToPx(14));
        card.setCardElevation(0f);
        card.setStrokeWidth(dpToPx(1));
        int strokeColor = event.calendarColor != 0
                ? (0xFF000000 | (event.calendarColor & 0xFFFFFF)) : 0xFF1A2244;
        card.setStrokeColor(strokeColor);

        LinearLayout inner = new LinearLayout(getContext());
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inner.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        // Left color bar
        View bar = new View(getContext());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dpToPx(3), dpToPx(36));
        bp.setMarginEnd(dpToPx(10));
        bar.setLayoutParams(bp);
        bar.setBackgroundColor(event.calendarColor != 0
                ? (0xFF000000 | (event.calendarColor & 0xFFFFFF)) : 0xFF4263EB);

        // Content
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(event.title);
        tvTitle.setTextColor(0xFFEEEEFF);
        tvTitle.setTextSize(13f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tvTime = new TextView(getContext());
        tvTime.setText(event.timeLabel
                + (event.durationLabel.isEmpty() ? "" : "  ·  " + event.durationLabel));
        tvTime.setTextColor(0xFF4263EB);
        tvTime.setTextSize(11f);
        LinearLayout.LayoutParams timep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        timep.topMargin = dpToPx(2);
        tvTime.setLayoutParams(timep);

        content.addView(tvTitle);
        content.addView(tvTime);

        if (!event.location.isEmpty()) {
            TextView tvLoc = new TextView(getContext());
            tvLoc.setText("📍 " + event.location);
            tvLoc.setTextColor(0xFF334466);
            tvLoc.setTextSize(10f);
            tvLoc.setMaxLines(1);
            tvLoc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dpToPx(2);
            tvLoc.setLayoutParams(lp);
            content.addView(tvLoc);
        }

        inner.addView(bar);
        inner.addView(content);
        card.addView(inner);
        return card;
    }

    // ═══════════════════════════════════════════════════
    //   TIMELINE (all original logic — unchanged)
    // ═══════════════════════════════════════════════════

    private void updateDateHeader(Calendar calendar) {
        tvCurrentMonthYear.setText(new SimpleDateFormat(
                "MMMM yyyy", Locale.getDefault()).format(calendar.getTime()));
        tvCurrentDateHeader.setText(new SimpleDateFormat(
                "EEEE, d", Locale.getDefault()).format(calendar.getTime()));
    }

    private void loadTimelineData() {
        FocusDatabase.getInstance(getContext()).actionDao().getAllItems()
                .observe(getViewLifecycleOwner(), items -> {
                    allDatabaseItems = items != null ? items : new ArrayList<>();
                    filterAndDisplayTasks();
                });
    }

    private void filterAndDisplayTasks() {
        List<ActionItem> filtered = new ArrayList<>();
        int ty = currentSelectedDate.get(Calendar.YEAR);
        int tm = currentSelectedDate.get(Calendar.MONTH);
        int td = currentSelectedDate.get(Calendar.DAY_OF_MONTH);

        for (ActionItem item : allDatabaseItems) {
            if (item.type.equals("routines")) {
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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}