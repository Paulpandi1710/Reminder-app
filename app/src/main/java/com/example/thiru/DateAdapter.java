package com.example.thiru;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DateAdapter extends RecyclerView.Adapter<DateAdapter.DateVH> {

    public interface OnDateSelectedListener {
        void onDateSelected(Calendar date);
    }

    // ── Configuration ─────────────────────────────────────
    private static final int TOTAL_DAYS  = 61; // 30 past + today + 30 future
    private static final int TODAY_POS   = 30; // index of "today"

    private final OnDateSelectedListener listener;
    private final Calendar baseDate;
    private int selectedPos = TODAY_POS;

    // Colors
    private static final int COLOR_SELECTED_BG     = 0xFF4263EB;
    private static final int COLOR_TODAY_BG        = 0xFF1A2A55;
    private static final int COLOR_DEFAULT_BG      = 0xFF0E0E24;
    private static final int COLOR_SELECTED_TEXT   = 0xFFFFFFFF;
    private static final int COLOR_TODAY_TEXT      = 0xFF7B9BFF;
    private static final int COLOR_DEFAULT_TEXT    = 0xFFCCDDEE; // BRIGHT — was too dark before
    private static final int COLOR_DAY_LABEL_SEL   = 0xFFFFFFFF;
    private static final int COLOR_DAY_LABEL_TODAY = 0xFF4263EB;
    private static final int COLOR_DAY_LABEL_DEF   = 0xFF8899CC; // VISIBLE — was too dark

    public DateAdapter(OnDateSelectedListener listener) {
        this.listener = listener;
        baseDate = Calendar.getInstance();
        baseDate.add(Calendar.DAY_OF_YEAR, -TODAY_POS);
        baseDate.set(Calendar.HOUR_OF_DAY, 0);
        baseDate.set(Calendar.MINUTE, 0);
        baseDate.set(Calendar.SECOND, 0);
        baseDate.set(Calendar.MILLISECOND, 0);
    }

    @NonNull @Override
    public DateVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_date_selector, parent, false);
        return new DateVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DateVH holder, int position) {
        Calendar date = getDateForPos(position);
        boolean isToday    = position == TODAY_POS;
        boolean isSelected = position == selectedPos;

        // Day of week label (SUN, MON, TUE…)
        String dayLabel = new SimpleDateFormat("EEE", Locale.getDefault())
                .format(date.getTime()).toUpperCase();
        holder.tvDayOfWeek.setText(dayLabel);

        // Day number
        holder.tvDayNumber.setText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)));

        // ── Apply color scheme ─────────────────────────
        if (isSelected) {
            holder.cardCircle.setCardBackgroundColor(COLOR_SELECTED_BG);
            holder.tvDayNumber.setTextColor(COLOR_SELECTED_TEXT);
            holder.tvDayOfWeek.setTextColor(COLOR_DAY_LABEL_SEL);
        } else if (isToday) {
            holder.cardCircle.setCardBackgroundColor(COLOR_TODAY_BG);
            holder.tvDayNumber.setTextColor(COLOR_TODAY_TEXT);
            holder.tvDayOfWeek.setTextColor(COLOR_DAY_LABEL_TODAY);
        } else {
            holder.cardCircle.setCardBackgroundColor(COLOR_DEFAULT_BG);
            holder.tvDayNumber.setTextColor(COLOR_DEFAULT_TEXT);
            holder.tvDayOfWeek.setTextColor(COLOR_DAY_LABEL_DEF);
        }

        // Click
        holder.itemView.setOnClickListener(v -> {
            int prev = selectedPos;
            selectedPos = holder.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPos);
            if (listener != null) listener.onDateSelected(getDateForPos(selectedPos));
        });
    }

    @Override public int getItemCount() { return TOTAL_DAYS; }

    private Calendar getDateForPos(int position) {
        Calendar cal = (Calendar) baseDate.clone();
        cal.add(Calendar.DAY_OF_YEAR, position);
        return cal;
    }

    // ── ViewHolder ────────────────────────────────────────
    static class DateVH extends RecyclerView.ViewHolder {
        TextView tvDayOfWeek, tvDayNumber;
        MaterialCardView cardCircle;

        DateVH(@NonNull View v) {
            super(v);
            tvDayOfWeek = v.findViewById(R.id.tvDayOfWeek);
            tvDayNumber = v.findViewById(R.id.tvDayNumber);
            cardCircle  = v.findViewById(R.id.cardDateCircle);
        }
    }
}