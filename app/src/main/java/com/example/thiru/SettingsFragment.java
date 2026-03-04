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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {

    private static final int RINGTONE_PICKER_CODE = 999;
    private TextView tvRingtoneName, tvProfileName;
    private ImageView imgProfile;
    private SharedPreferences prefs;

    // --- NEW: NATIVE ANDROID IMAGE PICKER ---
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prefs.edit().putString("profile_image", uri.toString()).apply();
                    imgProfile.setImageURI(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        tvRingtoneName = view.findViewById(R.id.tvRingtoneName);
        tvProfileName = view.findViewById(R.id.tvProfileName);
        imgProfile = view.findViewById(R.id.imgProfile);

        SwitchMaterial switchNotifications = view.findViewById(R.id.switchNotifications);
        SwitchMaterial switchAlarm = view.findViewById(R.id.switchAlarm);
        SwitchMaterial switchVibrate = view.findViewById(R.id.switchVibrate);
        SwitchMaterial switchDarkMode = view.findViewById(R.id.switchDarkMode);

        // Load Saved Profile Data
        String savedName = prefs.getString("user_name", "Focus Champion");
        tvProfileName.setText(savedName);

        String savedImage = prefs.getString("profile_image", null);
        if (savedImage != null) {
            imgProfile.setImageURI(Uri.parse(savedImage));
        }

        loadCurrentRingtoneName();
        switchNotifications.setChecked(prefs.getBoolean("pushNotifs", true));
        switchAlarm.setChecked(prefs.getBoolean("enable_alarm_screen", true));
        switchVibrate.setChecked(prefs.getBoolean("enable_vibration", true));
        switchDarkMode.setChecked(prefs.getBoolean("dark_mode", false));

        // --- NEW: EDIT NAME AND PHOTO LOGIC ---
        view.findViewById(R.id.btnEditProfile).setOnClickListener(v -> {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        tvProfileName.setOnClickListener(v -> {
            EditText input = new EditText(getContext());
            input.setText(tvProfileName.getText().toString());
            input.setPadding(40, 40, 40, 40);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Edit Name")
                    .setView(input)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String newName = input.getText().toString().trim();
                        prefs.edit().putString("user_name", newName).apply();
                        tvProfileName.setText(newName);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            if (isChecked) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        });

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("pushNotifs", isChecked).apply());
        switchAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("enable_alarm_screen", isChecked).apply());
        switchVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("enable_vibration", isChecked).apply());

        view.findViewById(R.id.btnRingtonePicker).setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            startActivityForResult(intent, RINGTONE_PICKER_CODE);
        });

        view.findViewById(R.id.btnClearData).setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Factory Reset")
                    .setMessage("This will permanently delete all your routines, tasks, and settings.")
                    .setPositiveButton("Wipe Data", (dialog, which) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            FocusDatabase.getInstance(requireContext()).clearAllTables();
                            requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().apply();
                            requireActivity().runOnUiThread(() -> {
                                Intent intent = new Intent(requireContext(), MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                Runtime.getRuntime().exit(0);
                            });
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri != null) {
                prefs.edit().putString("custom_ringtone", uri.toString()).apply();
                Ringtone ringtone = RingtoneManager.getRingtone(getContext(), uri);
                if (ringtone != null) tvRingtoneName.setText(ringtone.getTitle(getContext()));
            } else {
                prefs.edit().remove("custom_ringtone").apply();
                tvRingtoneName.setText("Default");
            }
        }
    }

    private void loadCurrentRingtoneName() {
        String savedUriString = prefs.getString("custom_ringtone", null);
        if (savedUriString != null) {
            Uri uri = Uri.parse(savedUriString);
            Ringtone ringtone = RingtoneManager.getRingtone(getContext(), uri);
            if (ringtone != null) tvRingtoneName.setText(ringtone.getTitle(getContext()));
        } else {
            tvRingtoneName.setText("Default");
        }
    }
}