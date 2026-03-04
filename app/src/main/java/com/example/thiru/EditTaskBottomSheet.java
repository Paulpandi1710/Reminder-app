package com.example.thiru;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditTaskBottomSheet extends BottomSheetDialogFragment {

    private ActionItem currentItem;
    private String selectedType;
    private int selectedHour, selectedMinute, selectedYear, selectedMonth, selectedDay;

    private MaterialButton btnTypeRoutine, btnTypeTask;
    private TextView tvSelectedTime, tvSelectedDate;
    private LinearLayout btnPickDate;

    // We pass the item we want to edit via a setter before showing the fragment
    public void setActionItem(ActionItem item) {
        this.currentItem = item;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_edit_task, container, false);

        if (currentItem == null) {
            dismiss();
            return view;
        }

        EditText etTaskTitle = view.findViewById(R.id.etEditTaskTitle);
        EditText etDuration = view.findViewById(R.id.etEditDuration);
        btnTypeRoutine = view.findViewById(R.id.btnEditTypeRoutine);
        btnTypeTask = view.findViewById(R.id.btnEditTypeTask);
        tvSelectedTime = view.findViewById(R.id.tvEditSelectedTime);
        tvSelectedDate = view.findViewById(R.id.tvEditSelectedDate);
        LinearLayout btnPickTime = view.findViewById(R.id.btnEditPickTime);
        btnPickDate = view.findViewById(R.id.btnEditPickDate);

        MaterialButton btnUpdate = view.findViewById(R.id.btnUpdate);
        MaterialButton btnDelete = view.findViewById(R.id.btnDelete);
        MaterialButton btnCancel = view.findViewById(R.id.btnEditCancel);

        // Pre-fill the data
        etTaskTitle.setText(currentItem.title);
        etDuration.setText(String.valueOf(currentItem.duration));
        selectedType = currentItem.type;
        selectedHour = currentItem.hour;
        selectedMinute = currentItem.minute;
        selectedYear = currentItem.year;
        selectedMonth = currentItem.month;
        selectedDay = currentItem.day;

        tvSelectedTime.setText(currentItem.timeString);

        Calendar cal = Calendar.getInstance();
        cal.set(selectedYear, selectedMonth, selectedDay);
        tvSelectedDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.getTime()));

        updateTypeSelection(selectedType);

        btnTypeRoutine.setOnClickListener(v -> updateTypeSelection("routines"));
        btnTypeTask.setOnClickListener(v -> updateTypeSelection("tasks"));

        btnPickDate.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(getContext(), (view1, year, month, dayOfMonth) -> {
                selectedYear = year;
                selectedMonth = month;
                selectedDay = dayOfMonth;
                Calendar tempCal = Calendar.getInstance();
                tempCal.set(year, month, dayOfMonth);
                tvSelectedDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(tempCal.getTime()));
            }, selectedYear, selectedMonth, selectedDay);
            datePicker.show();
        });

        btnPickTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(selectedHour)
                    .setMinute(selectedMinute)
                    .setTitleText("Select New Time")
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                    .build();
            picker.show(getParentFragmentManager(), "TIME_PICKER");
            picker.addOnPositiveButtonClickListener(dialog -> {
                selectedHour = picker.getHour();
                selectedMinute = picker.getMinute();
                String amPm = selectedHour >= 12 ? "PM" : "AM";
                int displayHour = selectedHour == 0 ? 12 : (selectedHour > 12 ? selectedHour - 12 : selectedHour);
                tvSelectedTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, selectedMinute, amPm));
            });
        });

        btnUpdate.setOnClickListener(v -> {
            String title = etTaskTitle.getText().toString().trim();
            String durationStr = etDuration.getText().toString().trim();

            if (title.isEmpty()) {
                etTaskTitle.setError("Required");
                return;
            }

            int dur = 0;
            if (!durationStr.isEmpty()) {
                try { dur = Integer.parseInt(durationStr); } catch (Exception e) { }
            }

            // Cancel old alarm
            cancelAlarm(currentItem.title);

            // Update item
            currentItem.title = title;
            currentItem.type = selectedType;
            currentItem.timeString = tvSelectedTime.getText().toString();
            currentItem.hour = selectedHour;
            currentItem.minute = selectedMinute;
            currentItem.year = selectedYear;
            currentItem.month = selectedMonth;
            currentItem.day = selectedDay;
            currentItem.duration = dur;

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                FocusDatabase.getInstance(getContext()).actionDao().update(currentItem);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Flow Updated!", Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            });

            // Reschedule new alarm
            scheduleAlarm(currentItem.title, selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute);
        });

        btnDelete.setOnClickListener(v -> {
            cancelAlarm(currentItem.title);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                FocusDatabase.getInstance(getContext()).actionDao().delete(currentItem);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Flow Deleted", Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            });
        });

        btnCancel.setOnClickListener(v -> dismiss());
        return view;
    }

    private void updateTypeSelection(String type) {
        selectedType = type;
        if (type.equals("routines")) {
            btnPickDate.setVisibility(View.GONE);
            btnTypeRoutine.setStrokeColorResource(R.color.primary_blue);
            btnTypeRoutine.setTextColor(getResources().getColor(R.color.primary_blue));
            btnTypeTask.setStrokeColorResource(android.R.color.darker_gray);
            btnTypeTask.setTextColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            btnPickDate.setVisibility(View.VISIBLE);
            btnTypeTask.setStrokeColorResource(R.color.primary_blue);
            btnTypeTask.setTextColor(getResources().getColor(R.color.primary_blue));
            btnTypeRoutine.setStrokeColorResource(android.R.color.darker_gray);
            btnTypeRoutine.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void cancelAlarm(String oldTitle) {
        if (getContext() == null) return;
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(getContext(), AlarmReceiver.class);
        intent.putExtra("TASK_TITLE", oldTitle);
        PendingIntent pi = PendingIntent.getBroadcast(getContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pi);
    }

    private void scheduleAlarm(String taskTitle, int year, int month, int day, int hour, int minute) {
        if (getContext() == null) return;
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Calendar exactTime = Calendar.getInstance();
        exactTime.set(year, month, day, hour, minute, 0);

        if (exactTime.getTimeInMillis() > System.currentTimeMillis()) {
            Intent exactIntent = new Intent(getContext(), AlarmReceiver.class);
            exactIntent.putExtra("TASK_TITLE", taskTitle);
            exactIntent.putExtra("IS_PRE_WARNING", false);
            PendingIntent exactPendingIntent = PendingIntent.getBroadcast(getContext(), (int) System.currentTimeMillis(), exactIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(exactTime.getTimeInMillis(), exactPendingIntent);
            alarmManager.setAlarmClock(alarmClockInfo, exactPendingIntent);
        }
    }
}