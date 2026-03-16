package com.example.thiru;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class AddGeofenceBottomSheet extends BottomSheetDialogFragment {

    // ── Form fields ───────────────────────────────────────
    private EditText etAddressSearch, etGeoTitle, etGeoNote;
    private WebView webViewMap;
    private TextView tvCoordsDisplay;
    private TextView chipRadius50, chipRadius100, chipRadius200, chipRadius500;
    private MaterialCardView cardSearchResults, cardCoordsDisplay, cardGPSOnly;
    private LinearLayout layoutSearchResults;
    private View layoutMapLoading;

    // ── State ─────────────────────────────────────────────
    private double selectedLat = 0.0, selectedLng = 0.0;
    private float  selectedRadius = 50f;
    private boolean mapReady = false;

    private FusedLocationProviderClient fusedClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Permission launcher ───────────────────────────────
    private final ActivityResultLauncher<String[]> locationPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean fine = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        if (fine) fetchCurrentLocation();
                        else Toast.makeText(getContext(),
                                "Location permission required", Toast.LENGTH_SHORT).show();
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_geofence, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Expand to full screen
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View bottomSheet = dialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Bind views ────────────────────────────────────
        etAddressSearch  = view.findViewById(R.id.etAddressSearch);
        etGeoTitle       = view.findViewById(R.id.etGeoTitle);
        etGeoNote        = view.findViewById(R.id.etGeoNote);
        webViewMap       = view.findViewById(R.id.webViewMap);
        tvCoordsDisplay  = view.findViewById(R.id.tvCoordsDisplay);
        chipRadius50     = view.findViewById(R.id.chipRadius50);
        chipRadius100    = view.findViewById(R.id.chipRadius100);
        chipRadius200    = view.findViewById(R.id.chipRadius200);
        chipRadius500    = view.findViewById(R.id.chipRadius500);
        cardSearchResults   = view.findViewById(R.id.cardSearchResults);
        layoutSearchResults = view.findViewById(R.id.layoutSearchResults);
        cardCoordsDisplay   = view.findViewById(R.id.cardCoordsDisplay);
        cardGPSOnly         = view.findViewById(R.id.cardGPSOnly);
        layoutMapLoading    = view.findViewById(R.id.layoutMapLoading);

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());

        setupMap();
        setupRadiusChips();
        setupSearchBar(view);
        setupLocationButtons(view);
        view.findViewById(R.id.cardSaveGeofence).setOnClickListener(v -> saveGeofence());
    }

    // ══════════════════════════════════════════════════════
    //   MAP SETUP — Leaflet.js via WebView
    // ══════════════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private void setupMap() {
        WebSettings settings = webViewMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // ── JavaScript → Java bridge ──────────────────────
        webViewMap.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onLocationSelected(double lat, double lng) {
                selectedLat = lat;
                selectedLng = lng;
                mainHandler.post(() -> {
                    updateCoordsDisplay(lat, lng);
                    // update radius circle on map
                    webViewMap.evaluateJavascript(
                            "updateRadius(" + selectedRadius + ");", null);
                });
            }
        }, "Android");

        webViewMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                mapReady = true;
                if (layoutMapLoading != null)
                    layoutMapLoading.setVisibility(View.GONE);
            }
        });
        webViewMap.setWebChromeClient(new WebChromeClient());

        // Load map from assets
        webViewMap.loadUrl("file:///android_asset/geofence_map.html");
    }

    // ══════════════════════════════════════════════════════
    //   ADDRESS SEARCH — Android Geocoder (no API key)
    // ══════════════════════════════════════════════════════

    private void setupSearchBar(View view) {
        view.findViewById(R.id.cardSearch).setOnClickListener(v -> runSearch());

        etAddressSearch.setOnEditorActionListener((v, actionId, event) -> {
            runSearch();
            return true;
        });
    }

    private void runSearch() {
        String query = etAddressSearch.getText().toString().trim();
        if (query.isEmpty()) return;

        // Show loading state
        if (cardSearchResults != null) {
            cardSearchResults.setVisibility(View.VISIBLE);
            if (layoutSearchResults != null) {
                layoutSearchResults.removeAllViews();
                TextView loading = new TextView(getContext());
                loading.setText("   🔍 Searching...");
                loading.setTextColor(0xFF5566AA);
                loading.setTextSize(13f);
                loading.setPadding(16, 12, 16, 12);
                layoutSearchResults.addView(loading);
            }
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                List<Address> results;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+ async method
                    geocoder.getFromLocationName(query, 5, addresses ->
                            mainHandler.post(() -> showSearchResults(addresses)));
                    return;
                } else {
                    //noinspection deprecation
                    results = geocoder.getFromLocationName(query, 5);
                    final List<Address> finalResults = results;
                    mainHandler.post(() -> showSearchResults(finalResults));
                }
            } catch (Exception e) {
                mainHandler.post(() -> showSearchError());
            }
        });
    }

    private void showSearchResults(List<Address> results) {
        if (!isAdded() || layoutSearchResults == null) return;
        layoutSearchResults.removeAllViews();

        if (results == null || results.isEmpty()) {
            showSearchError();
            return;
        }

        for (Address addr : results) {
            // Build display string
            String line1 = addr.getMaxAddressLineIndex() >= 0
                    ? addr.getAddressLine(0) : addr.getLocality();
            if (line1 == null || line1.isEmpty()) continue;

            View row = LayoutInflater.from(getContext())
                    .inflate(android.R.layout.simple_list_item_2,
                            layoutSearchResults, false);

            TextView tv1 = row.findViewById(android.R.id.text1);
            TextView tv2 = row.findViewById(android.R.id.text2);

            // Format: first part bold, rest smaller
            String[] parts = line1.split(",", 2);
            tv1.setText(parts[0].trim());
            tv1.setTextColor(0xFFFFFFFF);
            tv1.setTextSize(13f);
            tv2.setText(parts.length > 1 ? parts[1].trim() : "");
            tv2.setTextColor(0xFF445588);
            tv2.setTextSize(11f);
            row.setBackgroundColor(0x00000000);
            row.setPadding(16, 10, 16, 10);

            final double lat = addr.getLatitude();
            final double lng = addr.getLongitude();
            final String placeName = parts[0].trim();

            row.setOnClickListener(v -> {
                // Pin on map
                selectLocation(lat, lng);
                // Auto-fill title if empty
                if (etGeoTitle.getText().toString().trim().isEmpty()) {
                    etGeoTitle.setText(placeName);
                }
                // Hide results
                cardSearchResults.setVisibility(View.GONE);
            });

            layoutSearchResults.addView(row);

            // Divider
            View divider = new View(getContext());
            divider.setBackgroundColor(0x111A2244);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            layoutSearchResults.addView(divider);
        }

        cardSearchResults.setVisibility(View.VISIBLE);
    }

    private void showSearchError() {
        if (!isAdded() || layoutSearchResults == null) return;
        layoutSearchResults.removeAllViews();
        TextView err = new TextView(getContext());
        err.setText("   ❌ No results found. Try a different search.");
        err.setTextColor(0xFF554466);
        err.setTextSize(12f);
        err.setPadding(16, 14, 16, 14);
        layoutSearchResults.addView(err);
        if (cardSearchResults != null)
            cardSearchResults.setVisibility(View.VISIBLE);

        // Auto-hide after 2s
        mainHandler.postDelayed(() -> {
            if (isAdded() && cardSearchResults != null)
                cardSearchResults.setVisibility(View.GONE);
        }, 2000);
    }

    // ══════════════════════════════════════════════════════
    //   LOCATION BUTTONS
    // ══════════════════════════════════════════════════════

    private void setupLocationButtons(View view) {
        // Both GPS buttons do the same thing
        View.OnClickListener gpsClick = v -> requestLocationAndFetch();
        if (cardGPSOnly != null) cardGPSOnly.setOnClickListener(gpsClick);
        if (cardCoordsDisplay != null)
            cardCoordsDisplay.findViewById(R.id.cardUseCurrentLocation)
                    .setOnClickListener(gpsClick);
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
        if (cardGPSOnly != null) {
            TextView tv = cardGPSOnly.findViewById(android.R.id.text1);
        }
        Toast.makeText(getContext(), "📡 Getting your location...",
                Toast.LENGTH_SHORT).show();

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        selectLocation(location.getLatitude(), location.getLongitude());
                        // Reverse geocode to suggest title
                        reverseGeocode(location.getLatitude(), location.getLongitude());
                    } else {
                        // fallback to last known
                        fusedClient.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) {
                                selectLocation(last.getLatitude(), last.getLongitude());
                                reverseGeocode(last.getLatitude(), last.getLongitude());
                            } else {
                                Toast.makeText(getContext(),
                                        "Could not get location. Try outdoors.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Location failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void reverseGeocode(double lat, double lng) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1, addresses -> {
                        if (addresses != null && !addresses.isEmpty()) {
                            Address addr = addresses.get(0);
                            String name = addr.getFeatureName() != null
                                    ? addr.getFeatureName()
                                    : addr.getLocality() != null
                                    ? addr.getLocality() : "My Location";
                            mainHandler.post(() -> {
                                if (isAdded() && etGeoTitle.getText().toString().trim().isEmpty())
                                    etGeoTitle.setText(name);
                            });
                        }
                    });
                } else {
                    //noinspection deprecation
                    List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        String name = addr.getFeatureName() != null
                                ? addr.getFeatureName()
                                : addr.getLocality() != null
                                ? addr.getLocality() : "My Location";
                        mainHandler.post(() -> {
                            if (isAdded() && etGeoTitle.getText().toString().trim().isEmpty())
                                etGeoTitle.setText(name);
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    // ══════════════════════════════════════════════════════
    //   SELECT LOCATION — updates map + coords display
    // ══════════════════════════════════════════════════════

    private void selectLocation(double lat, double lng) {
        selectedLat = lat;
        selectedLng = lng;
        updateCoordsDisplay(lat, lng);

        // Move map to location + place marker + draw radius circle
        if (mapReady) {
            String js = String.format(Locale.US,
                    "setLocationFromAndroid(%f, %f, %f);", lat, lng, selectedRadius);
            webViewMap.evaluateJavascript(js, null);
        }
    }

    private void updateCoordsDisplay(double lat, double lng) {
        if (!isAdded()) return;
        String coords = String.format(Locale.US, "%.5f, %.5f", lat, lng);
        if (tvCoordsDisplay != null)
            tvCoordsDisplay.setText("📌 " + coords);
        if (cardCoordsDisplay != null)
            cardCoordsDisplay.setVisibility(View.VISIBLE);
        if (cardGPSOnly != null)
            cardGPSOnly.setVisibility(View.GONE);
    }

    // ══════════════════════════════════════════════════════
    //   RADIUS CHIPS
    // ══════════════════════════════════════════════════════

    private void setupRadiusChips() {
        selectRadius(chipRadius50, 50f);
        chipRadius50.setOnClickListener(v  -> selectRadius(chipRadius50, 50f));
        chipRadius100.setOnClickListener(v -> selectRadius(chipRadius100, 100f));
        chipRadius200.setOnClickListener(v -> selectRadius(chipRadius200, 200f));
        chipRadius500.setOnClickListener(v -> selectRadius(chipRadius500, 500f));
    }

    private void selectRadius(TextView selected, float radius) {
        selectedRadius = radius;
        int active   = 0xFFFFFFFF;
        int inactive = 0xFF445588;

        chipRadius50.setTextColor(chipRadius50   == selected ? active : inactive);
        chipRadius100.setTextColor(chipRadius100 == selected ? active : inactive);
        chipRadius200.setTextColor(chipRadius200 == selected ? active : inactive);
        chipRadius500.setTextColor(chipRadius500 == selected ? active : inactive);

        int selBg  = R.drawable.tab_selected_bg;
        int normBg = R.drawable.rounded_input_bg;
        chipRadius50.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius50  == selected ? selBg : normBg));
        chipRadius100.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius100 == selected ? selBg : normBg));
        chipRadius200.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius200 == selected ? selBg : normBg));
        chipRadius500.setBackground(ContextCompat.getDrawable(requireContext(),
                chipRadius500 == selected ? selBg : normBg));

        // Update radius circle on map
        if (mapReady && selectedLat != 0.0) {
            webViewMap.evaluateJavascript(
                    "updateRadius(" + radius + ");", null);
        }
    }

    // ══════════════════════════════════════════════════════
    //   SAVE GEOFENCE
    // ══════════════════════════════════════════════════════

    private void saveGeofence() {
        String title = etGeoTitle.getText().toString().trim();
        String note  = etGeoNote.getText().toString().trim();

        if (title.isEmpty()) {
            etGeoTitle.setError("Enter a location name");
            etGeoTitle.requestFocus();
            return;
        }
        if (selectedLat == 0.0 && selectedLng == 0.0) {
            Toast.makeText(getContext(),
                    "Please select a location on the map first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final double lat = selectedLat;
        final double lng = selectedLng;
        final float  rad = selectedRadius;
        final String coords = String.format(Locale.US, "%.5f, %.5f (r=%dm)",
                lat, lng, (int) rad);

        // Build ActionItem
        ActionItem item = new ActionItem();
        item.type        = "geofence";
        item.title       = "📍 " + title;
        item.description = note.isEmpty() ? "Arrive at " + title : note;
        item.latitude    = lat;
        item.longitude   = lng;
        item.radius      = rad;
        item.timeString  = coords;
        item.hour = 0; item.minute = 0;
        item.year = 0; item.month  = 0; item.day = 0;

        TextView saveLabel = getView() != null
                ? getView().findViewById(R.id.tvSaveLabel) : null;
        if (saveLabel != null) saveLabel.setText("Saving...");

        Executors.newSingleThreadExecutor().execute(() -> {
            // Insert to DB
            FocusDatabase.getInstance(requireContext()).actionDao().insert(item);

            // Wait a moment then query back the inserted item to get its real ID
            try { Thread.sleep(300); } catch (Exception ignored) {}

            List<ActionItem> all = FocusDatabase.getInstance(requireContext())
                    .actionDao().getAllItemsSync();

            // Find the newest geofence with this title and coordinates
            ActionItem saved = null;
            int maxId = -1;
            for (ActionItem a : all) {
                if ("geofence".equals(a.type)
                        && Math.abs(a.latitude  - lat) < 0.00001
                        && Math.abs(a.longitude - lng) < 0.00001
                        && a.id > maxId) {
                    maxId = a.id;
                    saved = a;
                }
            }

            if (saved != null) {
                final ActionItem toRegister = saved;
                GeofenceHelper.addGeofence(requireContext(), toRegister);
            }

            mainHandler.post(() -> {
                if (isAdded()) {
                    Toast.makeText(getContext(),
                            "📍 Saved! You'll be notified when you arrive at "
                                    + title + " (within " + (int)rad + "m)",
                            Toast.LENGTH_LONG).show();
                    dismiss();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (webViewMap != null) {
            webViewMap.destroy();
        }
    }
}