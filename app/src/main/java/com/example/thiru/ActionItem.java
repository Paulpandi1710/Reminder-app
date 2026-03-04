package com.example.thiru;

import androidx.room.Entity;
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

    // NEW: Duration of the task in minutes
    public int duration;

    public boolean isCompleted;
    public boolean isPending;

    public ActionItem() {
    }

    public ActionItem(String type, String title, String timeString, int hour, int minute, int year, int month, int day, int duration) {
        this.type = type;
        this.title = title;
        this.timeString = timeString;
        this.hour = hour;
        this.minute = minute;
        this.year = year;
        this.month = month;
        this.day = day;
        this.duration = duration;
        this.isCompleted = false;
        this.isPending = false;
    }
}