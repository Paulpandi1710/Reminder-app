package com.example.thiru;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ActionDao {

    @Insert
    void insert(ActionItem item);

    @Update
    void update(ActionItem item);

    @Delete
    void delete(ActionItem item);

    @Query("SELECT * FROM action_table ORDER BY id DESC")
    LiveData<List<ActionItem>> getAllItems();

    @Query("SELECT * FROM action_table WHERE type = :type ORDER BY id DESC")
    LiveData<List<ActionItem>> getItemsByType(String type);

    @Query("SELECT * FROM action_table WHERE title = :taskTitle LIMIT 1")
    ActionItem getTaskByTitle(String taskTitle);

    @Query("SELECT * FROM action_table WHERE isPending = 1 ORDER BY id DESC")
    LiveData<List<ActionItem>> getPendingItems();

    // --- NEW: FOR THE BOOT RECEIVER ---
    // Grabs only the tasks that haven't been completed or missed yet.
    @Query("SELECT * FROM action_table WHERE isCompleted = 0 AND isPending = 0")
    List<ActionItem> getActiveTasksSync();
}