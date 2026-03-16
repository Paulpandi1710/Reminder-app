package com.example.thiru;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class ActionAdapter extends RecyclerView.Adapter<ActionAdapter.ActionViewHolder> {

    private List<ActionItem> masterList  = new ArrayList<>();
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
        this.masterList  = new ArrayList<>(items);
        this.actionItems = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        if (query == null || query.trim().isEmpty()) {
            actionItems = new ArrayList<>(masterList);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            List<ActionItem> filtered = new ArrayList<>();
            for (ActionItem item : masterList) {
                if (item.title != null
                        && item.title.toLowerCase().contains(lowerQuery)) {
                    filtered.add(item);
                } else if (item.description != null
                        && item.description.toLowerCase().contains(lowerQuery)) {
                    filtered.add(item);
                } else if (item.timeString != null
                        && item.timeString.toLowerCase().contains(lowerQuery)) {
                    filtered.add(item);
                }
            }
            actionItems = filtered;
        }
        notifyDataSetChanged();
    }

    public ActionItem getItemAt(int position) {
        return actionItems.get(position);
    }

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
        ActionItem item = actionItems.get(position);

        // Reset default animations and visibility to prevent recycling bugs
        holder.viewCompleteRipple.setAlpha(0f);
        holder.viewCompleteRipple.setScaleX(1f);
        holder.viewCompleteRipple.setScaleY(1f);
        holder.ivCheck.setScaleX(1f);
        holder.ivCheck.setScaleY(1f);
        holder.ivCheck.setRotation(0f);
        holder.layoutCompletedStatus.setTranslationY(0f);

        holder.tvTitle.setText(item.title);
        holder.tvTime.setText(item.timeString);

        if ("routines".equals(item.type)) {
            holder.tvBonusXP.setText("+15 XP 🎉");
        } else {
            holder.tvBonusXP.setText("+25 XP 🎉");
        }

        // ══════════════════════════════════════════════════
        //   GEOFENCE ITEM
        // ══════════════════════════════════════════════════
        if ("geofence".equals(item.type)) {
            holder.cardCheck.setCardBackgroundColor(Color.parseColor("#0A1530"));
            holder.cardCheck.setStrokeColor(Color.parseColor("#1A2244"));
            holder.ivCheck.setImageResource(android.R.drawable.ic_dialog_map);
            holder.ivCheck.setColorFilter(Color.parseColor("#4263EB"));

            holder.cardCheck.setClickable(false);
            holder.cardCheck.setFocusable(false);

            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvTitle.setAlpha(1.0f);
            holder.tvTitle.setTextColor(Color.parseColor("#EEEEFF"));
            holder.tvTime.setTextColor(Color.parseColor("#20C997"));

            holder.ivDelete.setImageResource(android.R.drawable.ic_menu_delete);
            holder.ivDelete.setColorFilter(Color.parseColor("#FA5252"));

            holder.cardMain.setStrokeColor(Color.parseColor("#0D3A2A"));
            holder.cardMain.setAlpha(1.0f);

            holder.viewUrgentDot.setVisibility(View.GONE);
            holder.tvStatusLabel.setVisibility(View.GONE);
            holder.layoutCompletedStatus.setVisibility(View.GONE);

            cancelPulseAnimation(holder);

            holder.ivDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClicked(item);
            });
        } else {
            // ══════════════════════════════════════════════════
            //   NORMAL ITEMS (Tasks / Routines)
            // ══════════════════════════════════════════════════
            holder.cardCheck.setClickable(true);
            holder.cardCheck.setFocusable(true);
            holder.tvTime.setTextColor(Color.parseColor("#556699"));

            holder.ivDelete.setImageResource(R.drawable.ic_settings);
            holder.ivDelete.setColorFilter(Color.parseColor("#6677AA"));

            // Evaluate Urgency
            if (!item.isCompleted) {
                Calendar cal = Calendar.getInstance();
                if ("routines".equals(item.type)) {
                    cal.set(Calendar.HOUR_OF_DAY, item.hour);
                    cal.set(Calendar.MINUTE, item.minute);
                    cal.set(Calendar.SECOND, 0);
                } else {
                    cal.set(item.year, item.month, item.day, item.hour, item.minute, 0);
                }
                long diffMins = (cal.getTimeInMillis() - System.currentTimeMillis()) / 60000L;

                if (item.isPending || diffMins < 60) {
                    holder.viewUrgentDot.setVisibility(View.VISIBLE);
                    holder.tvStatusLabel.setVisibility(View.VISIBLE);
                    if (item.isPending) {
                        holder.tvStatusLabel.setText("· Pending");
                        holder.tvStatusLabel.setTextColor(Color.parseColor("#F59F00"));
                        holder.viewUrgentDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F59F00")));
                    } else {
                        holder.tvStatusLabel.setText("· Urgent");
                        holder.tvStatusLabel.setTextColor(Color.parseColor("#FA5252"));
                        holder.viewUrgentDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FA5252")));
                    }
                    startPulseAnimation(holder); // Start breathing effect on the dot
                } else {
                    holder.viewUrgentDot.setVisibility(View.GONE);
                    holder.tvStatusLabel.setVisibility(View.GONE);
                    cancelPulseAnimation(holder);
                }
            } else {
                holder.viewUrgentDot.setVisibility(View.GONE);
                holder.tvStatusLabel.setVisibility(View.GONE);
                cancelPulseAnimation(holder);
            }

            // Apply visual states
            if (item.isCompleted) {
                holder.cardMain.setStrokeColor(Color.parseColor("#1A20C997"));
                holder.cardMain.setAlpha(0.5f);
                holder.cardCheck.setCardBackgroundColor(Color.parseColor("#1120C997"));
                holder.cardCheck.setStrokeColor(Color.parseColor("#2A4D40"));
                holder.ivCheck.setColorFilter(Color.parseColor("#20C997"));
                holder.ivCheck.setImageResource(R.drawable.ic_check);

                holder.layoutCompletedStatus.setVisibility(View.VISIBLE);
                holder.layoutCompletedStatus.setAlpha(1f);

                holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.tvTitle.setTextColor(Color.parseColor("#8899AA"));
            } else {
                holder.cardMain.setStrokeColor(Color.parseColor("#1A2244"));
                holder.cardMain.setAlpha(1.0f);
                holder.cardCheck.setCardBackgroundColor(Color.parseColor("#0D1540"));
                holder.cardCheck.setStrokeColor(Color.parseColor("#223366"));
                holder.ivCheck.setColorFilter(Color.TRANSPARENT);
                holder.ivCheck.setImageResource(0);

                holder.layoutCompletedStatus.setVisibility(View.GONE);

                holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                holder.tvTitle.setTextColor(Color.parseColor("#EEEEFF"));
            }

            // ══════════════════════════════════════════════════
            //   EXAGGERATED CHAINED ANIMATION CLICK LISTENER
            // ══════════════════════════════════════════════════
            holder.cardCheck.setOnClickListener(v -> {
                if (!item.isCompleted) {

                    // 1. The whole card does a springy bounce!
                    holder.cardMain.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120)
                            .withEndAction(() -> {
                                holder.cardMain.animate().scaleX(1f).scaleY(1f).setDuration(250)
                                        .setInterpolator(new OvershootInterpolator(2.5f)).start();
                            }).start();

                    // 2. Check icon spins -180 degrees while popping in
                    holder.ivCheck.setImageResource(R.drawable.ic_check);
                    holder.ivCheck.setColorFilter(Color.parseColor("#20C997"));
                    holder.ivCheck.setScaleX(0f);
                    holder.ivCheck.setScaleY(0f);
                    holder.ivCheck.setRotation(-180f);
                    holder.ivCheck.animate().scaleX(1f).scaleY(1f).rotation(0f)
                            .setDuration(450).setInterpolator(new OvershootInterpolator(3.5f)).start();

                    // 3. Massive Green Ripple blast
                    holder.viewCompleteRipple.setAlpha(0.6f);
                    holder.viewCompleteRipple.setScaleX(0.2f);
                    holder.viewCompleteRipple.setScaleY(0.2f);
                    holder.viewCompleteRipple.animate().scaleX(5.5f).scaleY(5.5f).alpha(0f)
                            .setDuration(600).start();

                    // 4. XP badge pops up aggressively
                    holder.layoutCompletedStatus.setVisibility(View.VISIBLE);
                    holder.layoutCompletedStatus.setTranslationY(40f);
                    holder.layoutCompletedStatus.setAlpha(0f);
                    holder.layoutCompletedStatus.animate().translationY(0f).alpha(1f)
                            .setDuration(400).setStartDelay(100)
                            .setInterpolator(new OvershootInterpolator(2f)).start();

                    // 5. Card dimming and Strikethrough
                    holder.cardMain.animate().alpha(0.5f).setDuration(400).setStartDelay(150).start();
                    holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.tvTitle.setTextColor(Color.parseColor("#8899AA"));

                    holder.viewUrgentDot.setVisibility(View.GONE);
                    holder.tvStatusLabel.setVisibility(View.GONE);
                    cancelPulseAnimation(holder);

                    // 6. Fire DB update AFTER the beautiful animation plays out
                    v.postDelayed(() -> {
                        if (listener != null) listener.onCheckClicked(item);
                    }, 400);
                } else {
                    if (listener != null) listener.onCheckClicked(item);
                }
            });

            holder.ivDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClicked(item);
            });
        }

        // ══════════════════════════════════════════════════
        //   CASCADING LIST ENTRANCE ANIMATION (Waterfall effect)
        // ══════════════════════════════════════════════════
        holder.itemView.setTranslationY(80f);
        holder.itemView.setAlpha(0f);
        holder.itemView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(Math.min(position * 60, 600)) // Staggered delay based on list position
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }

    private void startPulseAnimation(ActionViewHolder holder) {
        if (holder.viewUrgentDot.getTag() == null) {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(holder.viewUrgentDot, "alpha", 1f, 0.2f, 1f);
            pulse.setDuration(1200);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
            holder.viewUrgentDot.setTag(pulse);
        }
    }

    private void cancelPulseAnimation(ActionViewHolder holder) {
        if (holder.viewUrgentDot.getTag() != null) {
            ((ObjectAnimator) holder.viewUrgentDot.getTag()).cancel();
            holder.viewUrgentDot.setTag(null);
            holder.viewUrgentDot.setAlpha(1f);
        }
    }

    @Override
    public int getItemCount() {
        return actionItems.size();
    }

    static class ActionViewHolder extends RecyclerView.ViewHolder {
        TextView  tvTitle, tvTime, tvStatusLabel, tvBonusXP;
        ImageView ivCheck, ivDelete;
        MaterialCardView cardMain, cardCheck;
        View viewCompleteRipple, viewUrgentDot, layoutCompletedStatus;

        ActionViewHolder(View itemView) {
            super(itemView);
            tvTitle  = itemView.findViewById(R.id.tvTitle);
            tvTime   = itemView.findViewById(R.id.tvTime);
            tvStatusLabel = itemView.findViewById(R.id.tvStatusLabel);
            tvBonusXP = itemView.findViewById(R.id.tvBonusXP);
            ivCheck  = itemView.findViewById(R.id.ivCheck);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            cardMain = itemView.findViewById(R.id.cardMain);
            cardCheck = itemView.findViewById(R.id.cardCheck);
            viewCompleteRipple = itemView.findViewById(R.id.viewCompleteRipple);
            viewUrgentDot = itemView.findViewById(R.id.viewUrgentDot);
            layoutCompletedStatus = itemView.findViewById(R.id.layoutCompletedStatus);
        }
    }
}