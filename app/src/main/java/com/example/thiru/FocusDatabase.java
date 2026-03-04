package com.example.thiru;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// BUMPED TO VERSION 5 to support the new Duration field
@Database(entities = {ActionItem.class}, version = 5, exportSchema = false)
public abstract class FocusDatabase extends RoomDatabase {

    public abstract ActionDao actionDao();

    private static volatile FocusDatabase INSTANCE;

    public static FocusDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (FocusDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    FocusDatabase.class, "focus_database")
                            .fallbackToDestructiveMigration() // Safely clears old format to prevent crashes
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}