package com.example.thiru;

public class NotificationItem {
    public String id;
    public String title;
    public String body;
    public String type; // "alarm", "festival", "geofence", "weekly", "xp", "system"
    public long   timestamp;
    public boolean isRead;

    public NotificationItem() {}

    public NotificationItem(String title, String body, String type) {
        this.id        = String.valueOf(System.currentTimeMillis())
                + (int)(Math.random() * 1000);
        this.title     = title;
        this.body      = body;
        this.type      = type;
        this.timestamp = System.currentTimeMillis();
        this.isRead    = false;
    }
}