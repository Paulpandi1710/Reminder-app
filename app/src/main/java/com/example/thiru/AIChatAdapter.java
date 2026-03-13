package com.example.thiru;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class AIChatAdapter extends RecyclerView.Adapter<AIChatAdapter.ChatViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_AI   = 1;

    private final List<AIEngine.AIMessage> messages;

    public AIChatAdapter(List<AIEngine.AIMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean isUser = viewType == TYPE_USER;
        int screen     = parent.getResources().getDisplayMetrics().widthPixels;
        int maxBubble  = (int)(screen * 0.76f);
        int margin     = dp(parent, 12);
        int marginWide = (int)(screen * 0.18f); // pushes bubble to correct side

        // ── Outer row — full width, aligns bubble left or right ──
        FrameLayout row = new FrameLayout(parent.getContext());
        row.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── Bubble card ──────────────────────────────────────────
        MaterialCardView bubble = new MaterialCardView(parent.getContext());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        if (isUser) {
            bp.gravity = Gravity.END;
            bp.setMargins(marginWide, dp(parent, 3), margin, dp(parent, 3));
            bubble.setCardBackgroundColor(Color.parseColor("#1C2D6B"));
            bubble.setStrokeColor(Color.parseColor("#2A3E8A"));
        } else {
            bp.gravity = Gravity.START;
            bp.setMargins(margin, dp(parent, 3), marginWide, dp(parent, 3));
            bubble.setCardBackgroundColor(Color.parseColor("#0C0C20"));
            bubble.setStrokeColor(Color.parseColor("#1A2244"));
        }
        bubble.setLayoutParams(bp);
        bubble.setRadius(dp(parent, 20));
        bubble.setCardElevation(0f);
        bubble.setStrokeWidth(dp(parent, 1));

        // ── Text inside bubble ───────────────────────────────────
        TextView tv = new TextView(parent.getContext());
        tv.setMaxWidth(maxBubble);
        tv.setPadding(dp(parent, 14), dp(parent, 10),
                dp(parent, 14), dp(parent, 10));
        tv.setTextSize(14f);
        tv.setLineSpacing(dp(parent, 2), 1.4f);

        if (isUser) {
            tv.setTextColor(Color.parseColor("#E8EEFF"));
        } else {
            tv.setTextColor(Color.parseColor("#BBBBDD"));
        }

        bubble.addView(tv);
        row.addView(bubble);
        return new ChatViewHolder(row, bubble, tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        AIEngine.AIMessage msg = messages.get(position);

        if (msg.isUser) {
            holder.tv.setText(msg.text);
        } else {
            holder.tv.setText(parseBold(msg.text));
        }

        // Smooth entrance animation
        holder.bubble.setAlpha(0f);
        holder.bubble.setTranslationY(dp(holder.bubble, 12));
        holder.bubble.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setStartDelay(20)
                .start();
    }

    @Override
    public int getItemCount() { return messages.size(); }

    // ── Parse **bold** markdown cleanly ─────────────────
    private static SpannableStringBuilder parseBold(String text) {
        if (text == null) return new SpannableStringBuilder();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0;
        while (i < text.length()) {
            int s = text.indexOf("**", i);
            if (s == -1) { sb.append(text.substring(i)); break; }
            sb.append(text, i, s);
            int e = text.indexOf("**", s + 2);
            if (e == -1) { sb.append(text.substring(s)); break; }
            int spanStart = sb.length();
            sb.append(text, s + 2, e);
            sb.setSpan(new StyleSpan(Typeface.BOLD),
                    spanStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i = e + 2;
        }
        return sb;
    }

    private static int dp(ViewGroup parent, int dp) {
        return Math.round(dp * parent.getContext()
                .getResources().getDisplayMetrics().density);
    }

    private static int dp(android.view.View v, int dp) {
        return Math.round(dp * v.getContext()
                .getResources().getDisplayMetrics().density);
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView bubble;
        final TextView tv;
        ChatViewHolder(FrameLayout row, MaterialCardView bubble, TextView tv) {
            super(row);
            this.bubble = bubble;
            this.tv     = tv;
        }
    }
}