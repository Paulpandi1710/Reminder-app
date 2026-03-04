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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DateAdapter extends RecyclerView.Adapter<DateAdapter.DateViewHolder> {

    private List<Calendar> dates = new ArrayList<>();
    // Since we start from -30 days ago, index 30 is exactly "Today"
    private int selectedPosition = 30;
    private OnDateClickListener listener;

    public interface OnDateClickListener {
        void onDateClick(Calendar date);
    }

    public DateAdapter(OnDateClickListener listener) {
        this.listener = listener;
        generateDates();
    }

    // Generates a massive list of 365 days seamlessly
    private void generateDates() {
        for (int i = -30; i <= 335; i++) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, i);
            dates.add(calendar);
        }
    }

    @NonNull
    @Override
    public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_date_selector, parent, false);
        return new DateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
        Calendar date = dates.get(position);

        String dayOfWeek = new SimpleDateFormat("EEE", Locale.getDefault()).format(date.getTime()).toUpperCase();
        String dayOfMonth = String.valueOf(date.get(Calendar.DAY_OF_MONTH));

        holder.tvDay.setText(dayOfWeek);
        holder.tvDate.setText(dayOfMonth);

        if (selectedPosition == position) {
            holder.cardDate.setCardBackgroundColor(Color.parseColor("#4263EB"));
            holder.tvDay.setTextColor(Color.parseColor("#D0EBFF"));
            holder.tvDate.setTextColor(Color.WHITE);
            holder.cardDate.setCardElevation(8f);
        } else {
            holder.cardDate.setCardBackgroundColor(Color.WHITE);
            holder.tvDay.setTextColor(Color.parseColor("#868E96"));
            holder.tvDate.setTextColor(Color.parseColor("#1A1B1E"));
            holder.cardDate.setCardElevation(2f);
        }

        holder.cardDate.setOnClickListener(v -> {
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);

            if (listener != null) {
                listener.onDateClick(dates.get(selectedPosition));
            }
        });
    }

    @Override
    public int getItemCount() {
        return dates.size();
    }

    class DateViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardDate;
        TextView tvDay, tvDate;

        public DateViewHolder(@NonNull View itemView) {
            super(itemView);
            cardDate = itemView.findViewById(R.id.cardDate);
            tvDay = itemView.findViewById(R.id.tvDay);
            tvDate = itemView.findViewById(R.id.tvDate);
        }
    }
}