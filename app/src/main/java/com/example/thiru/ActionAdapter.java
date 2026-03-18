package com.example.thiru;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

        // Deep Spring Touch Physics
        holder.cardMain.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(250)
                            .setInterpolator(new DecelerateInterpolator(1.5f)).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(500)
                            .setInterpolator(new OvershootInterpolator(2.0f)).start();
                    break;
            }
            return false;
        });

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
            holder.cardMain.setCardElevation(0f);
            holder.cardMain.setAlpha(1.0f);

            holder.layoutCompletedStatus.setVisibility(View.GONE);

            holder.ivDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClicked(item);
            });
        } else {
            holder.cardCheck.setClickable(true);
            holder.cardCheck.setFocusable(true);
            holder.tvTime.setTextColor(Color.parseColor("#556699"));

            holder.ivDelete.setImageResource(R.drawable.ic_settings);
            holder.ivDelete.setColorFilter(Color.parseColor("#6677AA"));

            // ══════════════════════════════════════════════════
            //   HARDWARE NEON GLOWS (API 28+)
            // ══════════════════════════════════════════════════
            if (item.isCompleted) {
                // Green Glow
                holder.cardMain.setStrokeColor(Color.parseColor("#20C997"));
                holder.cardMain.setCardElevation(12f);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    holder.cardMain.setOutlineAmbientShadowColor(Color.parseColor("#20C997"));
                    holder.cardMain.setOutlineSpotShadowColor(Color.parseColor("#20C997"));
                }

                holder.cardMain.setAlpha(0.6f);
                holder.cardCheck.setCardBackgroundColor(Color.parseColor("#1120C997"));
                holder.cardCheck.setStrokeColor(Color.parseColor("#2A4D40"));
                holder.ivCheck.setColorFilter(Color.parseColor("#20C997"));
                holder.ivCheck.setImageResource(R.drawable.ic_check);

                holder.layoutCompletedStatus.setVisibility(View.VISIBLE);
                holder.layoutCompletedStatus.setAlpha(1f);

                holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.tvTitle.setTextColor(Color.parseColor("#8899AA"));
            } else {
                // Blue Glow
                holder.cardMain.setStrokeColor(Color.parseColor("#4263EB"));
                holder.cardMain.setCardElevation(12f);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    holder.cardMain.setOutlineAmbientShadowColor(Color.parseColor("#4263EB"));
                    holder.cardMain.setOutlineSpotShadowColor(Color.parseColor("#4263EB"));
                }

                holder.cardMain.setAlpha(1.0f);
                holder.cardCheck.setCardBackgroundColor(Color.parseColor("#0D1540"));
                holder.cardCheck.setStrokeColor(Color.parseColor("#223366"));
                holder.ivCheck.setColorFilter(Color.TRANSPARENT);
                holder.ivCheck.setImageResource(0);

                holder.layoutCompletedStatus.setVisibility(View.GONE);

                holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                holder.tvTitle.setTextColor(Color.parseColor("#EEEEFF"));
            }

            // Cinematic Check Animation
            holder.cardCheck.setOnClickListener(v -> {
                if (!item.isCompleted) {
                    holder.cardMain.animate().scaleX(0.93f).scaleY(0.93f).setDuration(250)
                            .withEndAction(() -> {
                                holder.cardMain.animate().scaleX(1f).scaleY(1f).setDuration(600)
                                        .setInterpolator(new OvershootInterpolator(2.0f)).start();
                            }).start();

                    holder.ivCheck.setImageResource(R.drawable.ic_check);
                    holder.ivCheck.setColorFilter(Color.parseColor("#20C997"));
                    holder.ivCheck.setScaleX(0f);
                    holder.ivCheck.setScaleY(0f);
                    holder.ivCheck.setRotation(-180f);
                    holder.ivCheck.animate().scaleX(1f).scaleY(1f).rotation(0f)
                            .setDuration(800)
                            .setInterpolator(new OvershootInterpolator(2.0f)).start();

                    holder.viewCompleteRipple.setAlpha(0.6f);
                    holder.viewCompleteRipple.setScaleX(0.2f);
                    holder.viewCompleteRipple.setScaleY(0.2f);
                    holder.viewCompleteRipple.animate().scaleX(6.0f).scaleY(6.0f).alpha(0f)
                            .setDuration(1200)
                            .start();

                    holder.layoutCompletedStatus.setVisibility(View.VISIBLE);
                    holder.layoutCompletedStatus.setTranslationY(40f);
                    holder.layoutCompletedStatus.setAlpha(0f);
                    holder.layoutCompletedStatus.animate().translationY(0f).alpha(1f)
                            .setDuration(600).setStartDelay(200)
                            .setInterpolator(new OvershootInterpolator(1.8f)).start();

                    holder.cardMain.setStrokeColor(Color.parseColor("#20C997"));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        holder.cardMain.setOutlineAmbientShadowColor(Color.parseColor("#20C997"));
                        holder.cardMain.setOutlineSpotShadowColor(Color.parseColor("#20C997"));
                    }

                    holder.cardMain.animate().alpha(0.6f).setDuration(600).setStartDelay(250).start();
                    holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    holder.tvTitle.setTextColor(Color.parseColor("#8899AA"));

                    v.postDelayed(() -> {
                        if (listener != null) listener.onCheckClicked(item);
                    }, 1000);
                } else {
                    if (listener != null) listener.onCheckClicked(item);
                }
            });

            holder.ivDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClicked(item);
            });
        }

        // Staggered Waterfall Entrance
        holder.itemView.setTranslationY(100f);
        holder.itemView.setAlpha(0f);
        holder.itemView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(Math.min(position * 100, 800))
                .setInterpolator(new DecelerateInterpolator(2.5f))
                .start();
    }

    @Override
    public int getItemCount() {
        return actionItems.size();
    }

    static class ActionViewHolder extends RecyclerView.ViewHolder {
        TextView  tvTitle, tvTime, tvBonusXP;
        ImageView ivCheck, ivDelete;
        MaterialCardView cardMain, cardCheck;
        View viewCompleteRipple, layoutCompletedStatus;

        ActionViewHolder(View itemView) {
            super(itemView);
            tvTitle  = itemView.findViewById(R.id.tvTitle);
            tvTime   = itemView.findViewById(R.id.tvTime);
            tvBonusXP = itemView.findViewById(R.id.tvBonusXP);
            ivCheck  = itemView.findViewById(R.id.ivCheck);
            ivDelete = itemView.findViewById(R.id.ivDelete);
            cardMain = itemView.findViewById(R.id.cardMain);
            cardCheck = itemView.findViewById(R.id.cardCheck);
            viewCompleteRipple = itemView.findViewById(R.id.viewCompleteRipple);
            layoutCompletedStatus = itemView.findViewById(R.id.layoutCompletedStatus);
        }
    }
}