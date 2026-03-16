package com.example.thiru;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
        // ── MUST be before setContentView ─────────────────
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav           = findViewById(R.id.bottomNavigationView);
        fab                 = findViewById(R.id.fab);
        cardBellIcon        = findViewById(R.id.cardBellIcon);
        cardSettingsIcon    = findViewById(R.id.cardSettingsIcon);
        tvNotifBadge        = findViewById(R.id.tvNotifBadge);
        layoutTopRightIcons = findViewById(R.id.layoutTopRightIcons);

        setupWindowInsets();

        WeeklySummaryScheduler.schedule(this);
        GeofenceHelper.reRegisterAll(this);
        FestivalScheduler.schedule(this);

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
            AddTaskBottomSheet sheet = new AddTaskBottomSheet();
            sheet.show(getSupportFragmentManager(), "AddTask");
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

        // ── Back press — non-deprecated ───────────────────
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
        tvNotifBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
    }

    // ══════════════════════════════════════════════════════
    //   WINDOW INSETS — ROOT CAUSE FIX
    //
    //   THE BUG: Two separate listeners on root + fragContainer.
    //   Root listener returned CONSUMED → Android stops all
    //   inset propagation to children → fragContainer listener
    //   NEVER fired → input bar always hidden behind nav bar.
    //
    //   THE FIX: ONE listener on root only.
    //   Inside it we manually call setPadding on BOTH:
    //     - root:          system bar bottom padding
    //     - fragContainer: keyboard-aware bottom padding
    //
    //   This guarantees both always update together on every
    //   single inset change (keyboard open/close/rotate).
    //
    //   Manifest stateHidden|adjustResize is required —
    //   it tells Android to report ime() insets to our
    //   listener. Without it imeBottom is always 0.
    // ══════════════════════════════════════════════════════
    private void setupWindowInsets() {
        View root          = findViewById(R.id.main);
        View fragContainer = findViewById(R.id.fragment_container);
        View topIcons      = findViewById(R.id.layoutTopRightIcons);

        final int bottomBarH = Math.round(
                80 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int sysTop    = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()).top;
            int sysBottom = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()).bottom;
            int imeBottom = insets.getInsets(
                    WindowInsetsCompat.Type.ime()).bottom;

            // ── 1. Top-right icons below status bar ───────────
            if (topIcons != null) {
                android.view.ViewGroup.MarginLayoutParams lp =
                        (android.view.ViewGroup.MarginLayoutParams)
                                topIcons.getLayoutParams();
                lp.topMargin = sysTop + Math.round(
                        8 * getResources().getDisplayMetrics().density);
                topIcons.setLayoutParams(lp);
            }

            // ── 2. ROOT gets ONLY sysBottom — never imeBottom ─
            // fragContainer handles the keyboard offset below.
            // Adding imeBottom here AND on fragContainer was
            // causing DOUBLE padding — input bar pushed off screen.
            v.setPadding(0, 0, 0, sysBottom);

            // ── 3. fragContainer — keyboard-aware padding ─────
            // This is the ONLY place imeBottom is applied.
            // No double-stacking with root.
            if (fragContainer != null) {
                if (imeBottom > 0) {
                    // Keyboard open → lift content above keyboard
                    fragContainer.setPadding(0, 0, 0, imeBottom);
                } else {
                    // Keyboard closed → space for bottom nav bar
                    fragContainer.setPadding(0, 0, 0,
                            bottomBarH + sysBottom);
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
}