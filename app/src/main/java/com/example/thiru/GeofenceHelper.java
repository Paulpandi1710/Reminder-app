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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class GeofenceHelper {

    private static final String TAG = "GeofenceHelper";

    // ── Register a single geofence from an ActionItem ─────
    public static void addGeofence(Context context, ActionItem item) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted");
            return;
        }
        if (item.latitude == 0.0 && item.longitude == 0.0) {
            Log.w(TAG, "Invalid coordinates for geofence: " + item.title);
            return;
        }

        GeofencingClient client = LocationServices.getGeofencingClient(context);

        Geofence geofence = new Geofence.Builder()
                .setRequestId(String.valueOf(item.id))
                .setCircularRegion(item.latitude, item.longitude, item.radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setLoiteringDelay(30_000) // 30s dwell before triggering
                .build();

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();

        try {
            client.addGeofences(request, getGeofencePendingIntent(context))
                    .addOnSuccessListener(v ->
                            Log.d(TAG, "Geofence added: " + item.title))
                    .addOnFailureListener(e ->
                            Log.e(TAG, "Geofence add failed: " + e.getMessage()));
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
        }
    }

    // ── Remove a single geofence by ID ────────────────────
    public static void removeGeofence(Context context, String geofenceId) {
        GeofencingClient client = LocationServices.getGeofencingClient(context);
        client.removeGeofences(Collections.singletonList(geofenceId))
                .addOnSuccessListener(v ->
                        Log.d(TAG, "Geofence removed: " + geofenceId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Geofence remove failed: " + e.getMessage()));
    }

    // ── Re-register ALL saved geofences (called on boot) ──
    public static void reRegisterAll(Context context) {
        if (!hasLocationPermission(context)) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ActionItem> all = FocusDatabase.getInstance(context)
                    .actionDao().getAllItemsSync();
            for (ActionItem item : all) {
                if ("geofence".equals(item.type)) {
                    addGeofence(context, item);
                }
            }
            Log.d(TAG, "Re-registered all geofences");
        });
    }

    // ── PendingIntent for GeofencingClient ────────────────
    public static PendingIntent getGeofencePendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        return PendingIntent.getBroadcast(context, 0, intent, flags);
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