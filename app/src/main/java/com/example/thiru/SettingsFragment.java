package com.example.thiru;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private static final int RINGTONE_PICKER_CODE = 999;

    // Profile views
    private TextView tvRingtoneName, tvProfileName;
    private ImageView imgProfile;
    private SharedPreferences prefs;

    // XP views in settings
    private TextView tvSettingsXPBadge, tvSettingsXPTitle, tvSettingsXPLevel;
    private TextView tvSettingsXPNumbers, tvSettingsStreak, tvSettingsTotalXP;
    private TextView tvSettingsNextLevel;
    private View viewSettingsXPFill;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    requireContext().getContentResolver()
                            .takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prefs.edit().putString("profile_image", uri.toString()).apply();
                    imgProfile.setImageURI(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        // ── Profile views ─────────────────────────────────
        tvRingtoneName = view.findViewById(R.id.tvRingtoneName);
        tvProfileName  = view.findViewById(R.id.tvProfileName);
        imgProfile     = view.findViewById(R.id.imgProfile);

        // ── XP card views ─────────────────────────────────
        tvSettingsXPBadge   = view.findViewById(R.id.tvSettingsXPBadge);
        tvSettingsXPTitle   = view.findViewById(R.id.tvSettingsXPTitle);
        tvSettingsXPLevel   = view.findViewById(R.id.tvSettingsXPLevel);
        tvSettingsXPNumbers = view.findViewById(R.id.tvSettingsXPNumbers);
        tvSettingsStreak    = view.findViewById(R.id.tvSettingsStreak);
        tvSettingsTotalXP   = view.findViewById(R.id.tvSettingsTotalXP);
        tvSettingsNextLevel = view.findViewById(R.id.tvSettingsNextLevel);
        viewSettingsXPFill  = view.findViewById(R.id.viewSettingsXPFill);

        // ── Switch views ──────────────────────────────────
        // NOTE: switchDarkMode is REMOVED — app is permanently dark
        SwitchMaterial switchNotifications = view.findViewById(R.id.switchNotifications);
        SwitchMaterial switchAlarm         = view.findViewById(R.id.switchAlarm);
        SwitchMaterial switchVibrate       = view.findViewById(R.id.switchVibrate);
        SwitchMaterial switchAutoRollover  = view.findViewById(R.id.switchAutoRollover);

        // ── Load profile ──────────────────────────────────
        tvProfileName.setText(prefs.getString("user_name", "Focus Champion"));
        String savedImage = prefs.getString("profile_image", null);
        if (savedImage != null) imgProfile.setImageURI(Uri.parse(savedImage));

        // ── Load switch states ────────────────────────────
        loadCurrentRingtoneName();
        switchNotifications.setChecked(prefs.getBoolean("pushNotifs", true));
        switchAlarm.setChecked(prefs.getBoolean("enable_alarm_screen", true));
        switchVibrate.setChecked(prefs.getBoolean("enable_vibration", true));
        switchAutoRollover.setChecked(prefs.getBoolean("auto_rollover", true));

        // ── Load XP card data ─────────────────────────────
        refreshXPCard();

        // ── Edit profile photo ────────────────────────────
        view.findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(
                                ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        // ── Edit profile name (tap) ───────────────────────
        tvProfileName.setOnClickListener(v -> {
            EditText input = new EditText(getContext());
            input.setText(tvProfileName.getText().toString());
            input.setPadding(48, 40, 48, 40);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Edit Name")
                    .setView(input)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String name = input.getText().toString().trim();
                        if (!name.isEmpty()) {
                            prefs.edit().putString("user_name", name).apply();
                            tvProfileName.setText(name);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // ── Switch listeners ──────────────────────────────
        switchNotifications.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean("pushNotifs", on).apply());
        switchAlarm.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean("enable_alarm_screen", on).apply());
        switchVibrate.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean("enable_vibration", on).apply());
        switchAutoRollover.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean("auto_rollover", on).apply());

        // ── Ringtone picker ───────────────────────────────
        view.findViewById(R.id.btnRingtonePicker).setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            startActivityForResult(intent, RINGTONE_PICKER_CODE);
        });

        // ── Clear all data ────────────────────────────────
        view.findViewById(R.id.btnClearData).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Factory Reset")
                        .setMessage("This will permanently delete ALL your "
                                + "routines, tasks, and settings. "
                                + "This cannot be undone.")
                        .setPositiveButton("Wipe Everything", (dialog, which) ->
                                Executors.newSingleThreadExecutor().execute(() -> {
                                    FocusDatabase.getInstance(requireContext())
                                            .clearAllTables();
                                    requireContext()
                                            .getSharedPreferences("AppPrefs",
                                                    Context.MODE_PRIVATE)
                                            .edit().clear().apply();
                                    requireActivity().runOnUiThread(() -> {
                                        Intent i = new Intent(requireContext(),
                                                MainActivity.class);
                                        i.addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(i);
                                        Runtime.getRuntime().exit(0);
                                    });
                                }))
                        .setNegativeButton("Cancel", null)
                        .show());

        return view;
    }

    // ═══════════════════════════════════════════════════════
    //                  XP CARD REFRESH
    // ═══════════════════════════════════════════════════════

    private void refreshXPCard() {
        if (!isAdded() || tvSettingsXPTitle == null) return;

        int level      = XPManager.getLevel(requireContext());
        String title   = XPManager.getTitle(requireContext());
        String badge   = XPManager.getBadge(requireContext());
        int inLevel    = XPManager.getXPInCurrentLevel(requireContext());
        int needed     = XPManager.getXPNeededForLevel(requireContext());
        int streak     = XPManager.getStreak(requireContext());
        int totalXP    = XPManager.getTotalXP(requireContext());
        float progress = XPManager.getLevelProgress(requireContext());
        String nextTitle = XPManager.getNextTitle(requireContext());
        String nextBadge = XPManager.getNextBadge(requireContext());

        tvSettingsXPBadge.setText(badge);
        tvSettingsXPTitle.setText(title);
        tvSettingsXPLevel.setText("Level " + level);
        tvSettingsXPNumbers.setText(inLevel + " / " + needed + " XP");
        tvSettingsStreak.setText("🔥 " + streak + " day streak");
        tvSettingsTotalXP.setText(totalXP + " XP total");

        // Show next level only if not max
        if (level < 12) {
            tvSettingsNextLevel.setText("Next rank: " + nextTitle + " " + nextBadge);
            tvSettingsNextLevel.setVisibility(View.VISIBLE);
        } else {
            tvSettingsNextLevel.setText("🔥 Maximum rank achieved!");
            tvSettingsNextLevel.setTextColor(
                    android.graphics.Color.parseColor("#4263EB"));
        }

        // Animate XP bar fill
        animateXPBar(progress);
    }

    private void animateXPBar(float targetProgress) {
        if (viewSettingsXPFill == null) return;
        View parent = (View) viewSettingsXPFill.getParent();
        if (parent == null) return;

        Runnable doAnimate = () -> {
            int parentWidth = parent.getWidth();
            if (parentWidth == 0) return;
            int targetWidth = (int) (parentWidth * Math.min(targetProgress, 1f));

            ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
            anim.setDuration(1000);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addUpdateListener(animation -> {
                int val = (int) animation.getAnimatedValue();
                android.view.ViewGroup.LayoutParams params =
                        viewSettingsXPFill.getLayoutParams();
                params.width = val;
                viewSettingsXPFill.setLayoutParams(params);
            });
            anim.start();
        };

        if (parent.getWidth() > 0) {
            doAnimate.run();
        } else {
            parent.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            parent.getViewTreeObserver()
                                    .removeOnGlobalLayoutListener(this);
                            doAnimate.run();
                        }
                    });
        }
    }

    // ═══════════════════════════════════════════════════════
    //                RINGTONE RESULT
    // ═══════════════════════════════════════════════════════

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_CODE
                && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                prefs.edit().putString("custom_ringtone", uri.toString()).apply();
                Ringtone r = RingtoneManager.getRingtone(getContext(), uri);
                if (r != null)
                    tvRingtoneName.setText(r.getTitle(getContext()) + " ›");
            } else {
                prefs.edit().remove("custom_ringtone").apply();
                tvRingtoneName.setText("Default ›");
            }
        }
    }

    private void loadCurrentRingtoneName() {
        String saved = prefs.getString("custom_ringtone", null);
        if (saved != null) {
            Ringtone r = RingtoneManager.getRingtone(getContext(), Uri.parse(saved));
            if (r != null) {
                tvRingtoneName.setText(r.getTitle(getContext()) + " ›");
                return;
            }
        }
        tvRingtoneName.setText("Default ›");
    }
}