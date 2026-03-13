package com.example.thiru;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "action_table")
public class ActionItem {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String type;
    public String title;
    public String timeString;
    public int hour;
    public int minute;
    public int year;
    public int month;
    public int day;
    public int duration;

    public String description;
    public String repeatMode;

    public boolean isCompleted;
    public boolean isPending;

    @ColumnInfo(defaultValue = "none")
    public String category;

    // --- Added location fields below ---
    @ColumnInfo(name = "latitude", defaultValue = "0.0")
    public double latitude = 0.0;

    @ColumnInfo(name = "longitude", defaultValue = "0.0")
    public double longitude = 0.0;

    @ColumnInfo(name = "radius", defaultValue = "100.0")
    public float radius = 100f;
    // -----------------------------------

    public ActionItem() {}

    @Ignore  // ← tells Room to ignore this constructor, use the no-arg one above
    public ActionItem(String type, String title, String timeString,
                      int hour, int minute,
                      int year, int month, int day,
                      int duration, String description, String repeatMode) {
        this.type        = type;
        this.title       = title;
        this.timeString  = timeString;
        this.hour        = hour;
        this.minute      = minute;
        this.year        = year;
        this.month       = month;
        this.day         = day;
        this.duration    = duration;
        this.description = description;
        this.repeatMode  = repeatMode;
        this.isCompleted = false;
        this.isPending   = false;
        this.category    = "none";
    }
}