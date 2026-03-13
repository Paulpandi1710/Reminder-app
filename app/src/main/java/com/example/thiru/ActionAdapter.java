package com.example.thiru;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.ActionViewHolder> {

    // masterList holds ALL items from DB untouched
    // actionItems holds the currently DISPLAYED (filtered) items
    private List<ActionItem> masterList = new ArrayList<>();
    private List<ActionItem> actionItems = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onCheckClicked(ActionItem item);
        void onDeleteClicked(ActionItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    // Called by LiveData observer — always updates master + displayed list
    public void setItems(List<ActionItem> items) {
        this.masterList = new ArrayList<>(items);
        this.actionItems = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    // Called by search bar text watcher — filters displayed list only
    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            // No search active — show full list
            actionItems = new ArrayList<>(masterList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            List<ActionItem> filtered = new ArrayList<>();
            for (ActionItem item : masterList) {
                if (item.title != null && item.title.toLowerCase().contains(lowerQuery)) {
                    filtered.add(item);
                } else if (item.description != null && item.description.toLowerCase().contains(lowerQuery)) {
                    filtered.add(item);
                } else if (item.timeString != null && item.timeString.toLowerCase().contains(lowerQuery)) {
                    filtered.add(item);
                }
            }
            actionItems = filtered;
        }
        notifyDataSetChanged();
    }

    // Helper method for Swiping
    public ActionItem getItemAt(int position) {
        return actionItems.get(position);
    }

    // Helper method for Drag and Drop
    public void moveItem(int fromPosition, int toPosition) {
        Collections.swap(actionItems, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public ActionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_action_card, parent, false);
        return new ActionViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ActionViewHolder holder, int position) {
        ActionItem currentItem = actionItems.get(position);

        holder.tvTitle.setText(currentItem.title);
        holder.tvTime.setText(currentItem.timeString);

        if (currentItem.isCompleted) {
            holder.ivCheck.setColorFilter(Color.parseColor("#20C997"));
            holder.ivCheck.setBackgroundResource(R.drawable.rounded_square_green);
            holder.ivCheck.setImageResource(R.drawable.ic_check);
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvTitle.setAlpha(0.5f);
        } else {
            holder.ivCheck.setColorFilter(Color.TRANSPARENT);
            holder.ivCheck.setBackgroundResource(R.drawable.circle_bg_light);
            holder.ivCheck.setImageResource(0);
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvTitle.setAlpha(1.0f);
        }

        holder.ivCheck.setOnClickListener(v -> {
            if (listener != null) listener.onCheckClicked(currentItem);
        });

        holder.ivDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClicked(currentItem);
        });

        // Premium Slide-in Animation
        holder.itemView.setAnimation(AnimationUtils.loadAnimation(
                holder.itemView.getContext(), android.R.anim.slide_in_left));
    }

    @Override
    public int getItemCount() {
        return actionItems.size();
    }

    class ActionViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTitle, tvTime;
        private ImageView ivCheck, ivDelete;

        public ActionViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvTime = itemView.findViewById(R.id.tvTime);
            ivCheck = itemView.findViewById(R.id.ivCheck);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }
    }
}