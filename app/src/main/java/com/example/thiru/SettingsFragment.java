package com.example.thiru;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
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

    // ── Separate channel IDs — each toggle owns its own ───
    private static final String CH_GENERAL = "focusflow_general_v1";
    private static final String CH_ALARMS  = "focusflow_alarms_v5";
    private static final String CH_MASTER  = "focusflow_master_alarm";

    // ── Views ─────────────────────────────────────────────
    private TextView tvRingtoneName, tvProfileName;
    private ImageView imgProfile;
    private SharedPreferences prefs;

    // ── XP card views ─────────────────────────────────────
    private TextView tvSettingsXPBadge, tvSettingsXPTitle, tvSettingsXPLevel;
    private TextView tvSettingsXPNumbers, tvSettingsStreak, tvSettingsTotalXP;
    private TextView tvSettingsNextLevel;
    private View viewSettingsXPFill;

    // ── Photo picker ──────────────────────────────────────
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(), uri -> {
                        if (uri != null) {
                            requireContext().getContentResolver()
                                    .takePersistableUriPermission(
                                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            prefs.edit().putString("profile_image",
                                    uri.toString()).apply();
                            imgProfile.setImageURI(uri);
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_settings, container, false);
        prefs = requireContext().getSharedPreferences(
                "AppPrefs", Context.MODE_PRIVATE);

        // ── Bind views ────────────────────────────────────
        tvRingtoneName       = view.findViewById(R.id.tvRingtoneName);
        tvProfileName        = view.findViewById(R.id.tvProfileName);
        imgProfile           = view.findViewById(R.id.imgProfile);
        tvSettingsXPBadge    = view.findViewById(R.id.tvSettingsXPBadge);
        tvSettingsXPTitle    = view.findViewById(R.id.tvSettingsXPTitle);
        tvSettingsXPLevel    = view.findViewById(R.id.tvSettingsXPLevel);
        tvSettingsXPNumbers  = view.findViewById(R.id.tvSettingsXPNumbers);
        tvSettingsStreak     = view.findViewById(R.id.tvSettingsStreak);
        tvSettingsTotalXP    = view.findViewById(R.id.tvSettingsTotalXP);
        tvSettingsNextLevel  = view.findViewById(R.id.tvSettingsNextLevel);
        viewSettingsXPFill   = view.findViewById(R.id.viewSettingsXPFill);

        SwitchMaterial switchNotifications =
                view.findViewById(R.id.switchNotifications);
        SwitchMaterial switchAlarm =
                view.findViewById(R.id.switchAlarm);
        SwitchMaterial switchVibrate =
                view.findViewById(R.id.switchVibrate);
        SwitchMaterial switchAutoRollover =
                view.findViewById(R.id.switchAutoRollover);

        // ── Load profile data ─────────────────────────────
        tvProfileName.setText(prefs.getString("user_name", "Focus Champion"));
        String savedImage = prefs.getString("profile_image", null);
        if (savedImage != null) {
            try { imgProfile.setImageURI(Uri.parse(savedImage)); }
            catch (Exception ignored) {}
        }

        // ── Load switch states from prefs ─────────────────
        loadCurrentRingtoneName();
        switchNotifications.setChecked(prefs.getBoolean("pushNotifs", true));
        switchAlarm.setChecked(prefs.getBoolean("enable_alarm_screen", true));
        switchVibrate.setChecked(prefs.getBoolean("enable_vibration", true));
        switchAutoRollover.setChecked(prefs.getBoolean("auto_rollover", true));

        // ── Load XP card ──────────────────────────────────
        refreshXPCard();

        // ── Profile photo picker ──────────────────────────
        view.findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(
                                ActivityResultContracts.PickVisualMedia
                                        .ImageOnly.INSTANCE)
                        .build()));

        // ── Profile name edit (tap) ───────────────────────
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

        // ══════════════════════════════════════════════════
        //   TOGGLE LISTENERS — EACH INDEPENDENT
        //
        //   switchNotifications:
        //     Saves pushNotifs pref.
        //     Updates general notification channel importance.
        //     Does NOT affect alarm screen or vibration.
        //
        //   switchAlarm:
        //     Saves enable_alarm_screen pref ONLY.
        //     Controls whether AlarmScreenActivity launches.
        //     Does NOT touch any notification channel.
        //
        //   switchVibrate:
        //     Saves enable_vibration pref.
        //     Recreates alarm channels with new vibration setting.
        //     Does NOT affect notifications toggle.
        //
        //   switchAutoRollover:
        //     Saves auto_rollover pref ONLY.
        //     No side effects.
        // ══════════════════════════════════════════════════

        switchNotifications.setOnCheckedChangeListener((b, isOn) -> {
            prefs.edit().putBoolean("pushNotifs", isOn).apply();
            updateChannelImportance(CH_GENERAL, isOn);
        });

        switchAlarm.setOnCheckedChangeListener((b, isOn) ->
                prefs.edit().putBoolean("enable_alarm_screen", isOn).apply());

        switchVibrate.setOnCheckedChangeListener((b, isOn) -> {
            prefs.edit().putBoolean("enable_vibration", isOn).apply();
            updateChannelVibration(CH_ALARMS, isOn);
            updateChannelVibration(CH_MASTER, isOn);
        });

        switchAutoRollover.setOnCheckedChangeListener((b, isOn) ->
                prefs.edit().putBoolean("auto_rollover", isOn).apply());

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
                                    NotificationHelper.clearAll(requireContext());
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

    // ══════════════════════════════════════════════════════
    //   CHANNEL IMPORTANCE — for notifications toggle
    //   Deletes and recreates channel with correct importance.
    //   Android 8+ does not allow updating importance in-place.
    // ══════════════════════════════════════════════════════

    private void updateChannelImportance(String channelId, boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager)
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.deleteNotificationChannel(channelId);
            NotificationChannel ch = new NotificationChannel(
                    channelId,
                    "Focus Notifications",
                    enabled
                            ? NotificationManager.IMPORTANCE_DEFAULT
                            : NotificationManager.IMPORTANCE_NONE);
            ch.setDescription("Task reminders and alerts");
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════
    //   CHANNEL VIBRATION — for vibrate toggle
    //   Only recreates alarm channels, leaves others alone.
    //   Preserves existing channel importance level.
    // ══════════════════════════════════════════════════════

    private void updateChannelVibration(String channelId, boolean vibrate) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager)
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            NotificationChannel existing =
                    nm.getNotificationChannel(channelId);
            int importance = existing != null
                    ? existing.getImportance()
                    : NotificationManager.IMPORTANCE_HIGH;
            CharSequence name = existing != null
                    ? existing.getName() : "Active Alarms";

            nm.deleteNotificationChannel(channelId);

            NotificationChannel ch = new NotificationChannel(
                    channelId, name, importance);
            ch.setBypassDnd(true);
            ch.enableVibration(vibrate);
            if (vibrate) {
                ch.setVibrationPattern(new long[]{0, 1000, 1000});
            }
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════
    //   XP CARD REFRESH
    // ══════════════════════════════════════════════════════

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

        if (tvSettingsNextLevel != null) {
            if (level < 12) {
                tvSettingsNextLevel.setText(
                        "Next rank: " + nextTitle + " " + nextBadge);
                tvSettingsNextLevel.setVisibility(View.VISIBLE);
            } else {
                tvSettingsNextLevel.setText("🔥 Maximum rank achieved!");
                tvSettingsNextLevel.setTextColor(
                        android.graphics.Color.parseColor("#4263EB"));
            }
        }

        animateXPBar(progress);
    }

    private void animateXPBar(float targetProgress) {
        if (viewSettingsXPFill == null) return;
        View parent = (View) viewSettingsXPFill.getParent();
        if (parent == null) return;

        Runnable doAnimate = () -> {
            int parentWidth = parent.getWidth();
            if (parentWidth == 0) return;
            int targetWidth = (int)(parentWidth * Math.min(targetProgress, 1f));
            ValueAnimator anim = ValueAnimator.ofInt(0, targetWidth);
            anim.setDuration(1000);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addUpdateListener(animation -> {
                int val = (int) animation.getAnimatedValue();
                android.view.ViewGroup.LayoutParams lp =
                        viewSettingsXPFill.getLayoutParams();
                lp.width = val;
                viewSettingsXPFill.setLayoutParams(lp);
            });
            anim.start();
        };

        if (parent.getWidth() > 0) {
            doAnimate.run();
        } else {
            parent.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            parent.getViewTreeObserver()
                                    .removeOnGlobalLayoutListener(this);
                            doAnimate.run();
                        }
                    });
        }
    }

    // ══════════════════════════════════════════════════════
    //   RINGTONE RESULT
    // ══════════════════════════════════════════════════════

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_CODE
                && resultCode == Activity.RESULT_OK
                && data != null) {
            Uri uri = data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                prefs.edit().putString("custom_ringtone",
                        uri.toString()).apply();
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
            try {
                Ringtone r = RingtoneManager.getRingtone(
                        getContext(), Uri.parse(saved));
                if (r != null) {
                    tvRingtoneName.setText(r.getTitle(getContext()) + " ›");
                    return;
                }
            } catch (Exception ignored) {}
        }
        tvRingtoneName.setText("Default ›");
    }
}