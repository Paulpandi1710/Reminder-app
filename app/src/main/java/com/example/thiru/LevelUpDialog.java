package com.example.thiru;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.xml.KonfettiView;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * LevelUpDialog — Full-screen animated celebration shown when user levels up.
 * Uses the existing KonfettiView approach but in a Dialog window.
 */
public class LevelUpDialog {

    public static void show(Context context, int newLevel,
                            String newTitle, String newBadge, int xpEarned) {

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_level_up);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.setCancelable(true);

        // ── Bind views ─────────────────────────────────
        TextView tvBadge      = dialog.findViewById(R.id.tvLevelUpBadge);
        TextView tvLevelLabel = dialog.findViewById(R.id.tvLevelUpLabel);
        TextView tvTitle      = dialog.findViewById(R.id.tvLevelUpTitle);
        TextView tvSubtitle   = dialog.findViewById(R.id.tvLevelUpSubtitle);
        TextView tvXPEarned   = dialog.findViewById(R.id.tvLevelUpXPEarned);
        TextView btnDismiss   = dialog.findViewById(R.id.btnLevelUpDismiss);
        KonfettiView konfetti = dialog.findViewById(R.id.levelUpKonfetti);

        // ── Set content ────────────────────────────────
        tvBadge.setText(newBadge);
        tvLevelLabel.setText("LEVEL " + newLevel);
        tvTitle.setText(newTitle);
        tvSubtitle.setText("You've reached a new rank!");
        tvXPEarned.setText("+" + xpEarned + " XP");

        // ── Entrance animations ────────────────────────
        // Badge bounces in
        tvBadge.setScaleX(0f);
        tvBadge.setScaleY(0f);
        tvBadge.setAlpha(0f);
        tvBadge.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(700)
                .setStartDelay(200)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .start();

        // Level label slides up
        tvLevelLabel.setTranslationY(40f);
        tvLevelLabel.setAlpha(0f);
        tvLevelLabel.animate()
                .translationY(0f).alpha(1f)
                .setDuration(500).setStartDelay(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Title slides up
        tvTitle.setTranslationY(40f);
        tvTitle.setAlpha(0f);
        tvTitle.animate()
                .translationY(0f).alpha(1f)
                .setDuration(500).setStartDelay(650)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Subtitle fades in
        tvSubtitle.setAlpha(0f);
        tvSubtitle.animate().alpha(1f)
                .setDuration(400).setStartDelay(800).start();

        // XP earned count-up
        tvXPEarned.setAlpha(0f);
        tvXPEarned.animate().alpha(1f)
                .setDuration(400).setStartDelay(900)
                .withEndAction(() -> pulseView(tvXPEarned))
                .start();

        // Dismiss button fades in last
        btnDismiss.setAlpha(0f);
        btnDismiss.animate().alpha(1f)
                .setDuration(300).setStartDelay(1100).start();

        // ── Confetti burst ─────────────────────────────
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (konfetti != null) {
                Party party = new PartyFactory(
                        new Emitter(2000L, TimeUnit.MILLISECONDS).max(200))
                        .spread(180)
                        .colors(Arrays.asList(
                                0xFF4263EB, 0xFF20C997, 0xFFFFD43B,
                                0xFFFA5252, 0xFFCC5DE8, 0xFF7B5FFF))
                        .setSpeedBetween(1f, 8f)
                        .position(new Position.Relative(0.5, 0.0))
                        .build();
                konfetti.start(party);
            }
        }, 300);

        // ── Dismiss ─────────────────────────────────────
        btnDismiss.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> { /* optional cleanup */ });

        dialog.show();
    }

    /** Gentle scale pulse to draw attention to XP earned text. */
    private static void pulseView(View view) {
        view.animate()
                .scaleX(1.2f).scaleY(1.2f).setDuration(200)
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f).setDuration(200)
                        .setInterpolator(new OvershootInterpolator())
                        .start())
                .start();
    }
}