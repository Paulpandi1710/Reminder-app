package com.example.thiru;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder> {

    private List<ActionItem> timelineItems = new ArrayList<>();

    public void setItems(List<ActionItem> items) {
        this.timelineItems = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_hour, parent, false);
        return new TimelineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder holder, int position) {
        ActionItem item = timelineItems.get(position);

        String[] timeParts = item.timeString.split(" ");
        if (timeParts.length == 2) {
            holder.tvTimelineTime.setText(timeParts[0] + "\n" + timeParts[1]);
        } else {
            holder.tvTimelineTime.setText(item.timeString);
        }

        holder.tvTimelineTitle.setText(item.title);

        if (item.duration > 0) {
            holder.tvDurationBadge.setVisibility(View.VISIBLE);
            holder.tvDurationBadge.setText(item.duration + "m");
        } else {
            holder.tvDurationBadge.setVisibility(View.GONE);
        }

        String hexColor = item.type.equals("routines") ? "#4263EB" : "#F59F00";
        holder.tvTimelineType.setText(item.type.equals("routines") ? "ROUTINE" : "ONE-TIME TASK");
        holder.tvTimelineType.setTextColor(Color.parseColor(hexColor));
        holder.viewCategoryStripe.setBackgroundColor(Color.parseColor(hexColor));
        holder.ivTimelineDot.setColorFilter(Color.parseColor(hexColor));

        if (item.isCompleted) {
            holder.cardTimelineItem.setAlpha(0.4f);
            holder.ivTimelineDot.setAlpha(0.4f);
        } else {
            holder.cardTimelineItem.setAlpha(1.0f);
            holder.ivTimelineDot.setAlpha(1.0f);
        }

        holder.itemView.setAlpha(0f);
        holder.itemView.setScaleX(0.85f);
        holder.itemView.setScaleY(0.85f);
        holder.itemView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setStartDelay(position * 40L)
                .start();
    }

    @Override
    public int getItemCount() {
        return timelineItems.size();
    }

    class TimelineViewHolder extends RecyclerView.ViewHolder {
        TextView tvTimelineTime, tvTimelineTitle, tvTimelineType, tvDurationBadge;
        View viewCategoryStripe;
        ImageView ivTimelineDot;
        MaterialCardView cardTimelineItem;

        public TimelineViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimelineTime = itemView.findViewById(R.id.tvTimelineTime);
            tvTimelineTitle = itemView.findViewById(R.id.tvTimelineTitle);
            tvTimelineType = itemView.findViewById(R.id.tvTimelineType);
            tvDurationBadge = itemView.findViewById(R.id.tvDurationBadge);
            viewCategoryStripe = itemView.findViewById(R.id.viewCategoryStripe);
            ivTimelineDot = itemView.findViewById(R.id.ivTimelineDot);
            cardTimelineItem = itemView.findViewById(R.id.cardTimelineItem);
        }
    }
}