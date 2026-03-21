package com.example.thiru;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private FloatingActionButton fab;
    private MaterialCardView     cardBellIcon, cardSettingsIcon;
    private TextView             tvNotifBadge;
    private LinearLayout         layoutTopRightIcons;

    private int activeNavId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav           = findViewById(R.id.bottomNavigationView);
        fab                 = findViewById(R.id.fab);
        cardBellIcon        = findViewById(R.id.cardBellIcon);
        cardSettingsIcon    = findViewById(R.id.cardSettingsIcon);
        tvNotifBadge        = findViewById(R.id.tvNotifBadge);
        layoutTopRightIcons = findViewById(R.id.layoutTopRightIcons);

        View fabAura            = findViewById(R.id.fabAura);
        View bottomNavContainer = findViewById(R.id.bottomNavContainer);

        setupWindowInsets();

        WeeklySummaryScheduler.schedule(this);
        GeofenceHelper.reRegisterAll(this);
        FestivalScheduler.schedule(this);

        // ══════════════════════════════════════════════════════
        //   PREMIUM FLOATING ENTRANCE & AURA ANIMATION
        // ══════════════════════════════════════════════════════
        if (bottomNavContainer != null) {
            bottomNavContainer.setTranslationY(180f);
            bottomNavContainer.setAlpha(0f);
            bottomNavContainer.animate().translationY(0f).alpha(1f)
                    .setDuration(1200).setStartDelay(200)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }

        if (fabAura != null) {
            ObjectAnimator auraScaleX = ObjectAnimator.ofFloat(fabAura, "scaleX", 0.9f, 1.4f, 0.9f);
            ObjectAnimator auraScaleY = ObjectAnimator.ofFloat(fabAura, "scaleY", 0.9f, 1.4f, 0.9f);
            ObjectAnimator auraAlpha = ObjectAnimator.ofFloat(fabAura, "alpha", 0.6f, 0.0f, 0.6f);

            auraScaleX.setDuration(3500);
            auraScaleY.setDuration(3500);
            auraAlpha.setDuration(3500);

            auraScaleX.setRepeatCount(ValueAnimator.INFINITE);
            auraScaleY.setRepeatCount(ValueAnimator.INFINITE);
            auraAlpha.setRepeatCount(ValueAnimator.INFINITE);

            auraScaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            auraScaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            auraAlpha.setInterpolator(new AccelerateDecelerateInterpolator());

            auraScaleX.start();
            auraScaleY.start();
            auraAlpha.start();
        }

        if (fab != null) {
            ObjectAnimator fabFloat = ObjectAnimator.ofFloat(fab, "translationY", 0f, -8f, 0f);
            fabFloat.setDuration(4500);
            fabFloat.setRepeatCount(ValueAnimator.INFINITE);
            fabFloat.setInterpolator(new AccelerateDecelerateInterpolator());
            fabFloat.start();
        }

        if (savedInstanceState == null) {
            loadMainFragment(new HomeFragment(), R.id.nav_home);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == activeNavId) return false;
            Fragment fragment;
            if      (id == R.id.nav_home)    fragment = new HomeFragment();
            else if (id == R.id.nav_planner) fragment = new PlannerFragment();
            else if (id == R.id.nav_stats)   fragment = new StatsFragment();
            else if (id == R.id.nav_ai)      fragment = new AIFragment();
            else return false;
            loadMainFragment(fragment, id);
            return true;
        });

        fab.setOnClickListener(v -> {
            fab.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        fab.animate().scaleX(1f).scaleY(1f).setDuration(400)
                                .setInterpolator(new OvershootInterpolator(3f)).start();
                        AddTaskBottomSheet sheet = new AddTaskBottomSheet();
                        sheet.show(getSupportFragmentManager(), "AddTask");
                    }).start();
        });

        if (cardBellIcon != null) {
            cardBellIcon.setOnClickListener(v ->
                    openOverlayFragment(new NotificationsFragment()));
        }

        if (cardSettingsIcon != null) {
            cardSettingsIcon.setOnClickListener(v ->
                    openOverlayFragment(new SettingsFragment()));
        }

        updateNotificationBadge();

        String navTab = getIntent().getStringExtra("nav_tab");
        if ("home".equals(navTab)) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (getSupportFragmentManager()
                                .getBackStackEntryCount() > 0) {
                            getSupportFragmentManager().popBackStack();
                            setTopIconsVisible(activeNavId == R.id.nav_home);
                            updateNotificationBadge();
                        } else if (activeNavId != R.id.nav_home) {
                            bottomNav.setSelectedItemId(R.id.nav_home);
                        } else {
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        // ══════════════════════════════════════════════════════
        //   DAILY LOGIN REWARD TRIGGER
        // ══════════════════════════════════════════════════════
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            XPManager.XPResult loginResult = XPManager.handleDailyLogin(this);
            if (loginResult != null) {
                showDailyLoginReward(loginResult.xpEarned);

                // If they level up from just opening the app, celebrate!
                if (loginResult.leveledUp) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        LevelUpDialog.show(this, loginResult.newLevel, loginResult.newTitle, loginResult.newBadge, loginResult.xpEarned);
                    }, 3500); // Wait for the banner to finish before showing the dialog
                }
            }
        }, 1500); // 1.5s delay allows the home screen to finish its entrance animation first
    }

    // ── DYNAMIC GLASSMORPHISM BANNER BUILDER ──
    private void showDailyLoginReward(int xpEarned) {
        final android.view.ViewGroup root = findViewById(android.R.id.content);
        if (root == null) return;

        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dpToPx(16f), dpToPx(56f), dpToPx(16f), 0); // Just below status bar
        card.setLayoutParams(params);
        card.setRadius(dpToPx(20f));
        card.setCardElevation(dpToPx(8f));
        card.setCardBackgroundColor(android.graphics.Color.parseColor("#E60A1232")); // Deep dark blue glass
        card.setStrokeWidth(dpToPx(1.5f));
        card.setStrokeColor(android.graphics.Color.parseColor("#4263EB")); // Glowing blue border

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        layout.setPadding(dpToPx(18f), dpToPx(16f), dpToPx(18f), dpToPx(16f));

        TextView icon = new TextView(this);
        icon.setText("✨");
        icon.setTextSize(22f);
        icon.setPadding(0, 0, dpToPx(12f), 0);

        TextView text = new TextView(this);
        text.setText("Welcome back! +" + xpEarned + " Daily Login XP");
        text.setTextColor(android.graphics.Color.WHITE);
        text.setTextSize(14f);
        text.setTypeface(null, android.graphics.Typeface.BOLD);

        layout.addView(icon);
        layout.addView(text);
        card.addView(layout);
        root.addView(card);

        // ── Drop down, pause, and slide back up ──
        card.setTranslationY(-300f);
        card.setAlpha(0f);
        card.animate().translationY(0f).alpha(1f).setDuration(600)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                .withEndAction(() -> {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        card.animate().translationY(-300f).alpha(0f).setDuration(500)
                                .setInterpolator(new android.view.animation.AnticipateInterpolator(1.5f))
                                .withEndAction(() -> root.removeView(card)).start();
                    }, 3000);
                }).start();
    }

    private void loadMainFragment(Fragment fragment, int navId) {
        activeNavId = navId;
        getSupportFragmentManager().popBackStack(
                "overlay",
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.fragment_container, fragment)
                .commit();
        setTopIconsVisible(navId == R.id.nav_home);
    }

    private void openOverlayFragment(Fragment fragment) {
        setTopIconsVisible(false);
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack("overlay")
                .commit();
    }

    public void setTopIconsVisible(boolean visible) {
        if (layoutTopRightIcons == null) return;
        layoutTopRightIcons.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void updateNotificationBadge() {
        if (tvNotifBadge == null) return;
        int count = NotificationHelper.getUnreadCount(this);

        if (count > 0) {
            if (tvNotifBadge.getVisibility() != View.VISIBLE) {
                tvNotifBadge.setVisibility(View.VISIBLE);
                tvNotifBadge.setScaleX(0f);
                tvNotifBadge.setScaleY(0f);
                tvNotifBadge.animate().scaleX(1f).scaleY(1f)
                        .setDuration(400)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                        .start();
            }
            tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
        } else {
            tvNotifBadge.setVisibility(View.GONE);
        }
    }

    private void setupWindowInsets() {
        View root          = findViewById(R.id.main);
        View fragContainer = findViewById(R.id.fragment_container);
        View topIcons      = findViewById(R.id.layoutTopRightIcons);
        View bottomNav     = findViewById(R.id.bottomNavContainer);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int sysTop    = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int sysBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;

            if (topIcons != null) {
                android.view.ViewGroup.MarginLayoutParams lp =
                        (android.view.ViewGroup.MarginLayoutParams) topIcons.getLayoutParams();
                lp.topMargin = sysTop + Math.round(16 * getResources().getDisplayMetrics().density);
                topIcons.setLayoutParams(lp);
            }

            if (bottomNav != null) {
                android.view.ViewGroup.MarginLayoutParams lp =
                        (android.view.ViewGroup.MarginLayoutParams) bottomNav.getLayoutParams();
                lp.bottomMargin = sysBottom + Math.round(8 * getResources().getDisplayMetrics().density);
                bottomNav.setLayoutParams(lp);
            }

            if (fragContainer != null) {
                if (imeBottom > 0) {
                    fragContainer.setPadding(0, 0, 0, imeBottom);
                } else {
                    int bottomBarTotalH = Math.round(110 * getResources().getDisplayMetrics().density) + sysBottom;
                    fragContainer.setPadding(0, 0, 0, bottomBarTotalH);
                }
            }

            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationBadge();
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}