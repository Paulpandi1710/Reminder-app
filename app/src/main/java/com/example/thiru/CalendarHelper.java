package com.example.thiru;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class CalendarHelper {

    public static class CalendarEvent {
        public final long id;
        public final String title;
        public final String description;
        public final String location;
        public final long startMillis;
        public final long endMillis;
        public final boolean allDay;
        public final String calendarName;
        public final int calendarColor;

        public final int startHour;
        public final int startMinute;
        public final int endHour;
        public final int endMinute;
        public final String timeLabel;
        public final String durationLabel;

        public CalendarEvent(long id, String title, String description,
                             String location, long startMillis, long endMillis,
                             boolean allDay, String calendarName, int calendarColor) {
            this.id            = id;
            this.title         = title != null ? title : "Untitled Event";
            this.description   = description != null ? description : "";
            this.location      = location != null ? location : "";
            this.startMillis   = startMillis;
            this.endMillis     = endMillis;
            this.allDay        = allDay;
            this.calendarName  = calendarName != null ? calendarName : "Calendar";
            this.calendarColor = calendarColor;

            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(startMillis);
            this.startHour   = start.get(Calendar.HOUR_OF_DAY);
            this.startMinute = start.get(Calendar.MINUTE);

            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(endMillis);
            this.endHour   = end.get(Calendar.HOUR_OF_DAY);
            this.endMinute = end.get(Calendar.MINUTE);

            this.timeLabel     = allDay ? "All Day" : buildTimeLabel(start, end);
            this.durationLabel = allDay ? "" : buildDuration(startMillis, endMillis);
        }

        private static String buildTimeLabel(Calendar start, Calendar end) {
            return formatTime(start) + " – " + formatTime(end);
        }

        private static String formatTime(Calendar cal) {
            int hour   = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            String amPm = hour >= 12 ? "PM" : "AM";
            int h = hour % 12;
            if (h == 0) h = 12;
            return String.format(java.util.Locale.getDefault(),
                    "%d:%02d %s", h, minute, amPm);
        }

        private static String buildDuration(long start, long end) {
            long diffMs  = end - start;
            long hours   = diffMs / 3_600_000L;
            long minutes = (diffMs % 3_600_000L) / 60_000L;
            if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
            if (hours > 0)                return hours + "h";
            return minutes + "m";
        }

        // REQUIRED BY AI ENGINE!
        public boolean overlapsWith(int taskHour, int taskMinute, int durationMinutes) {
            if (allDay) return false;
            long taskStart = toMillisToday(taskHour, taskMinute);
            long taskEnd   = taskStart + durationMinutes * 60_000L;
            return startMillis < taskEnd && endMillis > taskStart;
        }

        private static long toMillisToday(int hour, int minute) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        }
    }

    public static List<CalendarEvent> getEventsForDay(Context context,
                                                      int year, int month, int day) {
        if (!hasPermission(context)) return new ArrayList<>();

        Calendar localStart = Calendar.getInstance();
        localStart.set(year, month, day, 0, 0, 0);
        localStart.set(Calendar.MILLISECOND, 0);

        Calendar localEnd = Calendar.getInstance();
        localEnd.set(year, month, day, 23, 59, 59);
        localEnd.set(Calendar.MILLISECOND, 999);

        List<CalendarEvent> events = queryEvents(context, localStart.getTimeInMillis(), localEnd.getTimeInMillis());

        Collections.sort(events, (a, b) -> {
            if (a.allDay && !b.allDay) return -1;
            if (!a.allDay && b.allDay) return 1;
            return Long.compare(a.startMillis, b.startMillis);
        });

        return events;
    }

    public static List<CalendarEvent> getTodayEvents(Context context) {
        Calendar now = Calendar.getInstance();
        return getEventsForDay(context,
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));
    }

    public static List<CalendarEvent> getUpcomingEvents(Context context, int days) {
        if (!hasPermission(context)) return new ArrayList<>();
        long now    = System.currentTimeMillis();
        long future = now + (long) days * 24 * 3_600_000L;
        List<CalendarEvent> events = queryEvents(context, now, future);
        Collections.sort(events, (a, b) -> Long.compare(a.startMillis, b.startMillis));
        return events;
    }

    public static boolean hasPermission(Context context) {
        return android.content.pm.PackageManager.PERMISSION_GRANTED
                == context.checkSelfPermission(
                android.Manifest.permission.READ_CALENDAR);
    }

    private static List<CalendarEvent> queryEvents(Context context,
                                                   long startMs, long endMs) {
        List<CalendarEvent> events = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();

        // FIX: Removed CALENDAR_COLOR completely. This was crashing the query on modern Android.
        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startMs);
        ContentUris.appendId(builder, endMs);

        try {
            Cursor cursor = cr.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long   id       = cursor.getLong(0);
                    String title    = cursor.getString(1);
                    String desc     = cursor.getString(2);
                    String loc      = cursor.getString(3);
                    long   dtStart  = cursor.getLong(4);
                    long   dtEnd    = cursor.getLong(5);
                    int    allDay   = cursor.getInt(6);

                    int calColor = 0xFF4263EB; // Safe default blue

                    if (dtEnd == 0) dtEnd = dtStart + 3_600_000L;

                    events.add(new CalendarEvent(id, title, desc, loc,
                            dtStart, dtEnd, allDay == 1, "Calendar", calColor));
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("CalendarHelper", "Error reading calendar", e);
        }

        return events;
    }
}