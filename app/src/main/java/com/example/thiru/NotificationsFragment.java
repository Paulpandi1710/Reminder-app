package com.example.thiru;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

        // Update subtitle
        tvSubtitle.setText(unread > 0
                ? unread + " unread notification" + (unread > 1 ? "s" : "")
                : "All caught up ✓");

        // Mark read + update badge
        NotificationHelper.markAllRead(requireContext());
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).updateNotificationBadge();

        // Back
        btnBack.setOnClickListener(v -> {
            if (isAdded() && getParentFragmentManager().getBackStackEntryCount() > 0)
                getParentFragmentManager().popBackStack();
        });

        // Mark all read button
        btnMarkRead.setOnClickListener(v -> {
            NotificationHelper.markAllRead(requireContext());
            tvSubtitle.setText("All caught up ✓");
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).updateNotificationBadge();
        });

        // Show empty or list
        if (items.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            rv.setVisibility(View.GONE);
        } else {
            emptyLayout.setVisibility(View.GONE);
            rv.setVisibility(View.VISIBLE);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            rv.setAdapter(new NotifAdapter(items));
        }
    }

    // ── Adapter ───────────────────────────────────────────
    private static class NotifAdapter
            extends RecyclerView.Adapter<NotifAdapter.VH> {

        private final List<NotificationItem> items;

        NotifAdapter(List<NotificationItem> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            // Build card view programmatically
            MaterialCardView card = new MaterialCardView(parent.getContext());
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(parent.getContext(), 8));
            card.setLayoutParams(lp);
            card.setRadius(dp(parent.getContext(), 18));
            card.setCardElevation(0f);
            card.setStrokeWidth(dp(parent.getContext(), 1));

            LinearLayout inner = new LinearLayout(parent.getContext());
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(android.view.Gravity.TOP);
            inner.setPadding(
                    dp(parent.getContext(), 14), dp(parent.getContext(), 14),
                    dp(parent.getContext(), 14), dp(parent.getContext(), 14));

            // Icon
            TextView tvIcon = new TextView(parent.getContext());
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    dp(parent.getContext(), 36), dp(parent.getContext(), 36));
            iconLp.setMarginEnd(dp(parent.getContext(), 12));
            tvIcon.setLayoutParams(iconLp);
            tvIcon.setGravity(android.view.Gravity.CENTER);
            tvIcon.setTextSize(18f);
            tvIcon.setTag("icon");
            tvIcon.setBackgroundColor(Color.parseColor("#0D1A33"));

            // Content
            LinearLayout content = new LinearLayout(parent.getContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(parent.getContext());
            tvTitle.setTextColor(Color.parseColor("#EEEEFF"));
            tvTitle.setTextSize(13f);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setMaxLines(2);
            tvTitle.setTag("title");

            TextView tvBody = new TextView(parent.getContext());
            tvBody.setTextColor(Color.parseColor("#445588"));
            tvBody.setTextSize(11f);
            tvBody.setMaxLines(2);
            tvBody.setTag("body");
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            bodyLp.topMargin = dp(parent.getContext(), 2);
            tvBody.setLayoutParams(bodyLp);

            TextView tvTime = new TextView(parent.getContext());
            tvTime.setTextColor(Color.parseColor("#223355"));
            tvTime.setTextSize(10f);
            tvTime.setTag("time");
            LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            timeLp.topMargin = dp(parent.getContext(), 4);
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

            // Colors based on read status
            int bgColor  = item.isRead ? Color.parseColor("#0C0C1E") : Color.parseColor("#0E0E28");
            int stroke   = item.isRead ? Color.parseColor("#0D1A33") : Color.parseColor("#1A2A5E");
            card.setCardBackgroundColor(bgColor);
            card.setStrokeColor(stroke);

            // Find views by tag
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

            if (!item.isRead) tvTitle.setTextColor(Color.WHITE);
            else tvTitle.setTextColor(Color.parseColor("#AABBCC"));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }

        private static int dp(android.content.Context ctx, int val) {
            return Math.round(val * ctx.getResources().getDisplayMetrics().density);
        }
    }
}