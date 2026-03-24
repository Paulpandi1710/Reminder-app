package com.example.thiru;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import java.util.List;
import java.util.concurrent.Executors;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG        = "GeofenceReceiver";
    private static final String CHANNEL_ID = "geofence_channel";
    private static final int    NOTIF_BASE = 6000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Geofence broadcast received");

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) {
            Log.e(TAG, "GeofencingEvent is null");
            return;
        }
        if (event.hasError()) {
            Log.e(TAG, "GeofencingEvent error code: " + event.getErrorCode());
            return;
        }

        int transition = event.getGeofenceTransition();
        Log.d(TAG, "Transition type: " + transition);

        if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.d(TAG, "Exited geofence. Ready to trigger again on next entry.");
            return;
        }

        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER) return;

        List<Geofence> triggered = event.getTriggeringGeofences();
        if (triggered == null || triggered.isEmpty()) {
            Log.w(TAG, "No triggering geofences");
            return;
        }

        createChannel(context);
        final BroadcastReceiver.PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<ActionItem> all = FocusDatabase.getInstance(context)
                        .actionDao().getAllItemsSync();

                for (Geofence geofence : triggered) {
                    String requestId = geofence.getRequestId();
                    Log.d(TAG, "Triggered geofence ID: " + requestId);

                    ActionItem match = null;
                    try {
                        int itemId = Integer.parseInt(requestId);
                        for (ActionItem item : all) {
                            if (item.id == itemId && "geofence".equals(item.type)) {
                                match = item;
                                break;
                            }
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Non-integer geofence ID: " + requestId);
                    }

                    if (match != null) {
                        postNotification(context, match);
                    } else {
                        postFallbackNotification(context, requestId);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing geofence: " + e.getMessage());
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void postNotification(Context context, ActionItem item) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.putExtra("nav_tab", "home");
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context,
                NOTIF_BASE + item.id, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // ── THE FIX: Extract exact Name and Note cleanly ──
        String cleanTitle = item.title.replace("📍 ", "").trim();
        String customNote = "";

        if (item.description != null && !item.description.trim().isEmpty()) {
            if (!item.description.startsWith("Arrive at")) {
                customNote = item.description.trim();
            }
        }

        String notifTitle = "📍 " + cleanTitle;
        String notifBody = customNote.isEmpty()
                ? "You have arrived at your destination!"
                : "Reminder: " + customNote;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle(notifTitle)
                .setContentText(notifBody)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(notifBody)
                        .setBigContentTitle(notifTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 200, 100, 200})
                .setContentIntent(pi);

        nm.notify(NOTIF_BASE + item.id, builder.build());

        // Save cleanly to in-app notification center
        NotificationHelper.add(context, notifTitle, notifBody, "geofence");
        Log.d(TAG, "Notification posted for: " + cleanTitle);
    }

    private void postFallbackNotification(Context context, String geofenceId) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context,
                geofenceId.hashCode(), openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("📍 Location Reminder")
                .setContentText("You've arrived at a saved location!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(geofenceId.hashCode(), builder.build());
        NotificationHelper.add(context, "📍 Location Reminder", "You've arrived at a saved location!", "geofence");
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Location Reminders",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifies when you arrive at saved locations");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 200, 100, 200});
        nm.createNotificationChannel(channel);
    }
}