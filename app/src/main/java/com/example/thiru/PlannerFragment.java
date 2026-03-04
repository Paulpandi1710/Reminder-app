package com.example.thiru;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlannerFragment extends Fragment {

    private TimelineAdapter timelineAdapter;
    private TextView tvCurrentMonthYear;
    private TextView tvCurrentDateHeader;
    private LinearLayout layoutEmptyState;
    private RecyclerView rvTimeline;

    private Calendar currentSelectedDate = Calendar.getInstance();
    private List<ActionItem> allDatabaseItems = new ArrayList<>();

    public PlannerFragment() {
        super(R.layout.fragment_planner);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvCurrentMonthYear = view.findViewById(R.id.tvCurrentMonthYear);
        tvCurrentDateHeader = view.findViewById(R.id.tvCurrentDateHeader);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        rvTimeline = view.findViewById(R.id.rvTimeline);

        RecyclerView rvDates = view.findViewById(R.id.rvDates);
        rvDates.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        DateAdapter dateAdapter = new DateAdapter(selectedDate -> {
            currentSelectedDate = selectedDate;
            updateDateHeader(selectedDate);
            filterAndDisplayTasks();
        });
        rvDates.setAdapter(dateAdapter);

        rvDates.scrollToPosition(28);

        rvTimeline.setLayoutManager(new LinearLayoutManager(getContext()));
        timelineAdapter = new TimelineAdapter();
        rvTimeline.setAdapter(timelineAdapter);

        loadTimelineData();
        updateDateHeader(currentSelectedDate);
    }

    private void updateDateHeader(Calendar calendar) {
        String monthYear = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.getTime());
        tvCurrentMonthYear.setText(monthYear);

        String dayDate = new SimpleDateFormat("EEEE, d", Locale.getDefault()).format(calendar.getTime());
        tvCurrentDateHeader.setText(dayDate);
    }

    private void loadTimelineData() {
        FocusDatabase.getInstance(getContext()).actionDao().getAllItems()
                .observe(getViewLifecycleOwner(), items -> {
                    allDatabaseItems = items;
                    filterAndDisplayTasks();
                });
    }

    private void filterAndDisplayTasks() {
        List<ActionItem> filteredList = new ArrayList<>();

        int targetYear = currentSelectedDate.get(Calendar.YEAR);
        int targetMonth = currentSelectedDate.get(Calendar.MONTH);
        int targetDay = currentSelectedDate.get(Calendar.DAY_OF_MONTH);

        for (ActionItem item : allDatabaseItems) {
            if (item.type.equals("routines")) {
                filteredList.add(item);
            } else if (item.year == targetYear && item.month == targetMonth && item.day == targetDay) {
                filteredList.add(item);
            }
        }

        // PERFECT CHRONOLOGICAL SORT BY HOUR THEN MINUTE
        Collections.sort(filteredList, (a, b) -> {
            if (a.hour != b.hour) return Integer.compare(a.hour, b.hour);
            return Integer.compare(a.minute, b.minute);
        });

        timelineAdapter.setItems(filteredList);

        if (filteredList.isEmpty()) {
            rvTimeline.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvTimeline.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }
}