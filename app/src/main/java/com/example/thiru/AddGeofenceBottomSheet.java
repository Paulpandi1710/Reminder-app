package com.example.thiru;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import java.util.concurrent.Executors;

public class AddGeofenceBottomSheet extends BottomSheetDialogFragment {

    private EditText etTitle, etNote, etLat, etLng;
    private TextView tvUseLocationStatus;
    private TextView chipRadius50, chipRadius100, chipRadius200, chipRadius500;
    private float selectedRadius = 50f;
    private FusedLocationProviderClient fusedClient;

    // ── Permission launcher ───────────────────────────────
    private final ActivityResultLauncher<String[]> locationPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean fine = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        if (fine) {
                            fetchCurrentLocation();
                        } else {
                            Toast.makeText(getContext(),
                                    "Location permission needed to use GPS",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_geofence, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etTitle             = view.findViewById(R.id.etGeoTitle);
        etNote              = view.findViewById(R.id.etGeoNote);
        etLat               = view.findViewById(R.id.etGeoLat);
        etLng               = view.findViewById(R.id.etGeoLng);
        tvUseLocationStatus = view.findViewById(R.id.tvUseLocationStatus);
        chipRadius50        = view.findViewById(R.id.chipRadius50);
        chipRadius100       = view.findViewById(R.id.chipRadius100);
        chipRadius200       = view.findViewById(R.id.chipRadius200);
        chipRadius500       = view.findViewById(R.id.chipRadius500);

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());

        setupRadiusChips(view);

        // Use current location
        view.findViewById(R.id.cardUseCurrentLocation).setOnClickListener(v ->
                requestLocationAndFetch());

        // Save
        view.findViewById(R.id.cardSaveGeofence).setOnClickListener(v -> saveGeofence());
    }

    private void setupRadiusChips(View view) {
        selectRadiusChip(chipRadius50, 50f);

        chipRadius50.setOnClickListener(v  -> selectRadiusChip(chipRadius50, 50f));
        chipRadius100.setOnClickListener(v -> selectRadiusChip(chipRadius100, 100f));
        chipRadius200.setOnClickListener(v -> selectRadiusChip(chipRadius200, 200f));
        chipRadius500.setOnClickListener(v -> selectRadiusChip(chipRadius500, 500f));
    }

    private void selectRadiusChip(TextView selected, float radius) {
        selectedRadius = radius;
        int activeColor   = android.graphics.Color.parseColor("#FFFFFF");
        int inactiveColor = android.graphics.Color.parseColor("#445588");

        chipRadius50.setTextColor(chipRadius50  == selected ? activeColor : inactiveColor);
        chipRadius100.setTextColor(chipRadius100 == selected ? activeColor : inactiveColor);
        chipRadius200.setTextColor(chipRadius200 == selected ? activeColor : inactiveColor);
        chipRadius500.setTextColor(chipRadius500 == selected ? activeColor : inactiveColor);

        chipRadius50.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius50 == selected ? R.drawable.tab_selected_bg : R.drawable.rounded_input_bg));
        chipRadius100.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius100 == selected ? R.drawable.tab_selected_bg : R.drawable.rounded_input_bg));
        chipRadius200.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius200 == selected ? R.drawable.tab_selected_bg : R.drawable.rounded_input_bg));
        chipRadius500.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius500 == selected ? R.drawable.tab_selected_bg : R.drawable.rounded_input_bg));
    }

    private void requestLocationAndFetch() {
        boolean hasFine = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (hasFine) {
            fetchCurrentLocation();
        } else {
            locationPermLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchCurrentLocation() {
        if (tvUseLocationStatus != null)
            tvUseLocationStatus.setText("📡 Getting location...");

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        etLat.setText(String.valueOf(location.getLatitude()));
                        etLng.setText(String.valueOf(location.getLongitude()));
                        if (tvUseLocationStatus != null)
                            tvUseLocationStatus.setText("✅ Location captured!");
                    } else {
                        // Fallback to last known location
                        fusedClient.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (last != null) {
                                        etLat.setText(String.valueOf(last.getLatitude()));
                                        etLng.setText(String.valueOf(last.getLongitude()));
                                        if (tvUseLocationStatus != null)
                                            tvUseLocationStatus.setText("✅ Location captured!");
                                    } else {
                                        if (tvUseLocationStatus != null)
                                            tvUseLocationStatus.setText("❌ Could not get location");
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (tvUseLocationStatus != null)
                        tvUseLocationStatus.setText("❌ Location failed");
                });
    }

    private void saveGeofence() {
        String title = etTitle.getText().toString().trim();
        String note  = etNote.getText().toString().trim();
        String latStr = etLat.getText().toString().trim();
        String lngStr = etLng.getText().toString().trim();

        if (title.isEmpty()) {
            etTitle.setError("Enter a location name");
            etTitle.requestFocus();
            return;
        }
        if (latStr.isEmpty() || lngStr.isEmpty()) {
            Toast.makeText(getContext(),
                    "Please enter coordinates or use current location",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        double lat, lng;
        try {
            lat = Double.parseDouble(latStr);
            lng = Double.parseDouble(lngStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid coordinates", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check background location for Android 10+
        if (!GeofenceHelper.hasBackgroundLocationPermission(requireContext())) {
            Toast.makeText(getContext(),
                    "For background geofencing, allow 'All the time' location access in Settings",
                    Toast.LENGTH_LONG).show();
            // Open settings but still save
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null));
            startActivity(intent);
        }

        // Build ActionItem of type "geofence"
        ActionItem item = new ActionItem();
        item.type        = "geofence";
        item.title       = "📍 " + title;
        item.description = note.isEmpty() ? "Arrive at " + title : note;
        item.latitude    = lat;
        item.longitude   = lng;
        item.radius      = selectedRadius;
        item.timeString  = lat + ", " + lng + " (r=" + (int)selectedRadius + "m)";
        // hour/minute not used for geofence
        item.hour = 0; item.minute = 0;
        item.year = 0; item.month = 0; item.day = 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            FocusDatabase.getInstance(requireContext()).actionDao().insert(item);

            // Get the inserted item's id to register geofence with correct ID
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Re-register all geofences (simple approach — ensures new item is registered)
                GeofenceHelper.reRegisterAll(requireContext());
            }, 500);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (isAdded()) {
                    Toast.makeText(getContext(),
                            "📍 " + title + " saved! You'll be notified on arrival.",
                            Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            });
        });
    }
}