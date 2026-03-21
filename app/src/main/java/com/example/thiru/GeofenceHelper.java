package com.example.thiru;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class GeofenceHelper {

    private static final String TAG = "GeofenceHelper";

    // ── Register a single geofence ────────────────────────
    public static void addGeofence(Context context, ActionItem item) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Missing ACCESS_FINE_LOCATION permission");
            return;
        }
        if (item.latitude == 0.0 && item.longitude == 0.0) {
            Log.w(TAG, "Zero coordinates — skipping: " + item.title);
            return;
        }

        GeofencingClient client = LocationServices.getGeofencingClient(context);

        // ── THE FIX: Added EXIT transition and Responsiveness ──
        Geofence geofence = new Geofence.Builder()
                .setRequestId(String.valueOf(item.id))
                .setCircularRegion(item.latitude, item.longitude,
                        Math.max(item.radius, 100f)) // Minimum 100m for pocket GPS reliability
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                // We MUST track EXIT so Android knows to reset the geofence for the next ENTER!
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                // Forces the hardware to wake up and check every 60 seconds for accuracy
                .setNotificationResponsiveness(60000)
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        try {
            client.addGeofences(request, getGeofencePendingIntent(context))
                    .addOnSuccessListener(v ->
                            Log.d(TAG, "✅ Geofence registered: "
                                    + item.title + " id=" + item.id
                                    + " r=" + Math.max(item.radius, 100f) + "m"
                                    + " lat=" + item.latitude
                                    + " lng=" + item.longitude))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "❌ Geofence registration failed: "
                                    + e.getMessage()));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException registering geofence: " + e.getMessage());
        }
    }

    // ── Remove a single geofence by ActionItem ID ─────────
    public static void removeGeofence(Context context, String geofenceId) {
        GeofencingClient client = LocationServices.getGeofencingClient(context);
        client.removeGeofences(Collections.singletonList(geofenceId))
                .addOnSuccessListener(v ->
                        Log.d(TAG, "Geofence removed: " + geofenceId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Geofence remove failed: " + e.getMessage()));
    }

    // ── Re-register ALL geofences (called on app start + boot) ──
    public static void reRegisterAll(Context context) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "No location permission — skipping re-registration");
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<ActionItem> all = FocusDatabase.getInstance(context)
                        .actionDao().getAllItemsSync();
                int count = 0;
                for (ActionItem item : all) {
                    if ("geofence".equals(item.type)
                            && (item.latitude != 0.0 || item.longitude != 0.0)) {
                        addGeofence(context, item);
                        count++;
                    }
                }
                Log.d(TAG, "Re-registered " + count + " geofences");
            } catch (Exception e) {
                Log.e(TAG, "reRegisterAll failed: " + e.getMessage());
            }
        });
    }

    // ── PendingIntent sent to GeofenceBroadcastReceiver ───
    public static PendingIntent getGeofencePendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        return PendingIntent.getBroadcast(context, 777, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasBackgroundLocationPermission(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
            return true;
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}