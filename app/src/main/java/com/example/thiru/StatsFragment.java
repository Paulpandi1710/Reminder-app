package com.example.thiru;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import java.util.ArrayList;

public class StatsFragment extends Fragment {

    private BarChart barChart;
    private TextView tvStatsDone, tvStatsPending, tvStatsRate;

    public StatsFragment() {
        super(R.layout.fragment_stats);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        barChart = view.findViewById(R.id.barChart);
        tvStatsDone = view.findViewById(R.id.tvStatsDone);
        tvStatsPending = view.findViewById(R.id.tvStatsPending);
        tvStatsRate = view.findViewById(R.id.tvStatsRate);

        setupChartAppearance();
        loadChartData();

        // ── PREMIUM ENTRANCE ANIMATION ──
        View scrollView = view.findViewById(R.id.statsScrollView);
        if (scrollView != null) {
            scrollView.setTranslationY(100f);
            scrollView.setAlpha(0f);
            scrollView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(600)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();
        }
    }

    private void setupChartAppearance() {
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBorders(false);
        barChart.animateY(1200);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Routines", "Tasks"}));
        xAxis.setTextColor(Color.parseColor("#8899BB")); // Adjusted for glassmorphism
        xAxis.setTextSize(12f);

        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisLeft().setGridColor(Color.parseColor("#1A2244"));
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setTextColor(Color.parseColor("#8899BB"));
        barChart.getAxisRight().setEnabled(false);
    }

    private void loadChartData() {
        FocusDatabase.getInstance(getContext()).actionDao().getAllItems()
                .observe(getViewLifecycleOwner(), items -> {
                    int routinesCompleted = 0;
                    int tasksCompleted = 0;
                    int totalCompleted = 0;
                    int totalPending = 0;
                    int totalItems = 0;

                    if (items != null) {
                        for (ActionItem item : items) {
                            if (!item.type.equals("history_routine")) {
                                totalItems++;
                            }

                            if (item.isCompleted) {
                                totalCompleted++;
                                if ("routines".equals(item.type) || "history_routine".equals(item.type)) routinesCompleted++;
                                if ("tasks".equals(item.type)) tasksCompleted++;
                            }
                            if (item.isPending) {
                                totalPending++;
                            }
                        }
                    }

                    tvStatsDone.setText(String.valueOf(totalCompleted));
                    tvStatsPending.setText(String.valueOf(totalPending));
                    int successRate = (totalItems == 0) ? 0 : (totalCompleted * 100) / totalItems;
                    tvStatsRate.setText(successRate + "%");

                    ArrayList<BarEntry> entries = new ArrayList<>();
                    entries.add(new BarEntry(0f, routinesCompleted));
                    entries.add(new BarEntry(1f, tasksCompleted));

                    BarDataSet dataSet = new BarDataSet(entries, "Completed Items");
                    dataSet.setColors(Color.parseColor("#4263EB"), Color.parseColor("#8B5CF6"));
                    dataSet.setValueTextColor(Color.parseColor("#FFFFFF"));
                    dataSet.setValueTextSize(14f);

                    BarData barData = new BarData(dataSet);
                    barData.setBarWidth(0.5f);

                    barChart.setData(barData);
                    barChart.invalidate();
                });
    }
}