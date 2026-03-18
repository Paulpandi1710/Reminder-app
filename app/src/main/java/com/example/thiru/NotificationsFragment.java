package com.example.thiru;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class NotificationsFragment extends Fragment {

    public NotificationsFragment() {
        super(R.layout.fragment_notifications);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv       = view.findViewById(R.id.rvNotifications);
        View emptyLayout      = view.findViewById(R.id.layoutNotifEmpty);
        TextView tvSubtitle   = view.findViewById(R.id.tvNotifSubtitle);
        MaterialCardView btnBack      = view.findViewById(R.id.btnNotifBack);
        MaterialCardView btnMarkRead  = view.findViewById(R.id.btnMarkAllRead);

        List<NotificationItem> items  = NotificationHelper.getAll(requireContext());
        int unread = NotificationHelper.getUnreadCount(requireContext());

        tvSubtitle.setText(unread > 0
                ? unread + " unread notification" + (unread > 1 ? "s" : "")
                : "All caught up ✓");

        NotificationHelper.markAllRead(requireContext());
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).updateNotificationBadge();

        btnBack.setOnClickListener(v -> {
            if (isAdded() && getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        btnMarkRead.setOnClickListener(v -> {
            NotificationHelper.markAllRead(requireContext());
            tvSubtitle.setText("All caught up ✓");
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).updateNotificationBadge();
            if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
        });

        if (items.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            emptyLayout.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            rv.setAdapter(new NotifAdapter(items));
        }

        view.setTranslationY(100f);
        view.setAlpha(0f);
        view.animate().translationY(0f).alpha(1f)
                .setDuration(600).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private static class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {

        private final List<NotificationItem> items;

        NotifAdapter(List<NotificationItem> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(parent.getContext(), 12));
            card.setLayoutParams(lp);
            card.setRadius(dp(parent.getContext(), 20));
            card.setCardElevation(0f);

            LinearLayout inner = new LinearLayout(parent.getContext());
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(android.view.Gravity.TOP);
            inner.setPadding(
                    dp(parent.getContext(), 16), dp(parent.getContext(), 16),
                    dp(parent.getContext(), 16), dp(parent.getContext(), 16));

            TextView tvIcon = new TextView(parent.getContext());
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    dp(parent.getContext(), 40), dp(parent.getContext(), 40));
            iconLp.setMarginEnd(dp(parent.getContext(), 16));
            tvIcon.setLayoutParams(iconLp);
            tvIcon.setGravity(android.view.Gravity.CENTER);
            tvIcon.setTextSize(18f);
            tvIcon.setTag("icon");

            LinearLayout content = new LinearLayout(parent.getContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(parent.getContext());
            tvTitle.setTextSize(14f);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setMaxLines(2);
            tvTitle.setTag("title");

            TextView tvBody = new TextView(parent.getContext());
            tvBody.setTextColor(Color.parseColor("#8899BB"));
            tvBody.setTextSize(12f);
            tvBody.setMaxLines(2);
            tvBody.setTag("body");
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bodyLp.topMargin = dp(parent.getContext(), 4);
            tvBody.setLayoutParams(bodyLp);

            TextView tvTime = new TextView(parent.getContext());
            tvTime.setTextColor(Color.parseColor("#556688"));
            tvTime.setTextSize(11f);
            tvTime.setTag("time");
            LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            timeLp.topMargin = dp(parent.getContext(), 6);
            tvTime.setLayoutParams(timeLp);

            content.addView(tvTitle);
            content.addView(tvBody);
            content.addView(tvTime);
            inner.addView(tvIcon);
            inner.addView(content);
            card.addView(inner);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            NotificationItem item = items.get(pos);
            MaterialCardView card = (MaterialCardView) holder.itemView;

            if (!item.isRead) {
                card.setCardBackgroundColor(Color.parseColor("#1A4263EB"));
                card.setStrokeWidth(dp(card.getContext(), 1.5f));
                card.setStrokeColor(Color.parseColor("#4263EB"));
            } else {
                card.setCardBackgroundColor(Color.parseColor("#0AFFFFFF"));
                card.setStrokeWidth(dp(card.getContext(), 1.5f));
                card.setStrokeColor(Color.parseColor("#1A2244"));
            }

            LinearLayout inner = (LinearLayout) card.getChildAt(0);
            TextView tvIcon  = inner.findViewWithTag("icon");
            LinearLayout content = (LinearLayout) inner.getChildAt(1);
            TextView tvTitle = content.findViewWithTag("title");
            TextView tvBody  = content.findViewWithTag("body");
            TextView tvTime  = content.findViewWithTag("time");

            tvIcon.setText(NotificationHelper.typeIcon(item.type));
            tvTitle.setText(item.title);
            tvBody.setText(item.body);
            tvTime.setText(NotificationHelper.relativeTime(item.timestamp));

            tvTitle.setTextColor(!item.isRead ? Color.WHITE : Color.parseColor("#AABBCC"));

            holder.itemView.setTranslationY(80f);
            holder.itemView.setAlpha(0f);
            holder.itemView.animate()
                    .translationY(0f).alpha(1f)
                    .setDuration(500)
                    .setStartDelay(pos * 80L)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }

        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder { VH(@NonNull View v) { super(v); } }
        private static int dp(android.content.Context ctx, float val) {
            return Math.round(val * ctx.getResources().getDisplayMetrics().density);
        }
    }
}