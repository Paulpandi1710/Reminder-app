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

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            Log.e(TAG, "GeofencingEvent error");
            return;
        }

        if (event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) return;

        List<Geofence> triggered = event.getTriggeringGeofences();
        if (triggered == null || triggered.isEmpty()) return;

        createChannel(context);

        for (Geofence geofence : triggered) {
            String requestId = geofence.getRequestId();
            // Look up the ActionItem to get the title
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    int itemId = Integer.parseInt(requestId);
                    // Get all items, find matching geofence
                    List<ActionItem> all = FocusDatabase.getInstance(context)
                            .actionDao().getAllItemsSync();
                    for (ActionItem item : all) {
                        if (item.id == itemId && "geofence".equals(item.type)) {
                            postNotification(context, item);
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to find geofence item: " + e.getMessage());
                    // Post generic notification
                    postGenericNotification(context, requestId);
                }
            });
        }
    }

    private void postNotification(Context context, ActionItem item) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, item.id,
                openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String desc = (item.description != null && !item.description.isEmpty())
                ? item.description : "You've arrived at this location!";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("📍 " + item.title)
                .setContentText(desc)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(desc))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        nm.notify(item.id + 5000, builder.build());
    }

    private void postGenericNotification(Context context, String id) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("📍 Location Reminder")
                .setContentText("You've arrived at a saved location!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        nm.notify(id.hashCode(), builder.build());
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Location Reminders",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifications when you arrive at saved locations");
        nm.createNotificationChannel(channel);
    }
}