package com.example.thiru;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils; // <-- THIS FIXES THE ERROR!
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.ActionViewHolder> {

    private List<ActionItem> actionItems = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onCheckClicked(ActionItem item);
        void onDeleteClicked(ActionItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ActionItem> items) {
        this.actionItems = items;
        notifyDataSetChanged();
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

        // --- NEW: PREMIUM FADE & SLIDE UP ANIMATION ---
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(50f);
        holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(position * 50L) // Creates a beautiful "cascading" waterfall effect
                .start();
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