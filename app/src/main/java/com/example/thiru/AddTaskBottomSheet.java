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

public class AddTaskBottomSheet extends BottomSheetDialogFragment {

    private String selectedType = "routines";
    private MaterialButton btnTypeRoutine, btnTypeTask;
    private TextView tvSelectedTime, tvSelectedDate;
    private LinearLayout btnPickDate;
    private EditText etDuration; // NEW

    private int selectedHour, selectedMinute, selectedYear, selectedMonth, selectedDay;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_add_task, container, false);

        EditText etTaskTitle = view.findViewById(R.id.etTaskTitle);
        etDuration = view.findViewById(R.id.etDuration); // NEW
        btnTypeRoutine = view.findViewById(R.id.btnTypeRoutine);
        btnTypeTask = view.findViewById(R.id.btnTypeTask);
        tvSelectedTime = view.findViewById(R.id.tvSelectedTime);
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate);
        LinearLayout btnPickTime = view.findViewById(R.id.btnPickTime);
        btnPickDate = view.findViewById(R.id.btnPickDate);
        MaterialButton btnSave = view.findViewById(R.id.btnSave);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        Calendar now = Calendar.getInstance();
        selectedYear = now.get(Calendar.YEAR);
        selectedMonth = now.get(Calendar.MONTH);
        selectedDay = now.get(Calendar.DAY_OF_MONTH);
        selectedHour = now.get(Calendar.HOUR_OF_DAY);
        selectedMinute = now.get(Calendar.MINUTE);

        btnTypeRoutine.setOnClickListener(v -> updateTypeSelection("routines"));
        btnTypeTask.setOnClickListener(v -> updateTypeSelection("tasks"));

        btnPickDate.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(getContext(), (view1, year, month, dayOfMonth) -> {
                selectedYear = year;
                selectedMonth = month;
                selectedDay = dayOfMonth;

                Calendar cal = Calendar.getInstance();
                cal.set(year, month, dayOfMonth);
                tvSelectedDate.setText(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.getTime()));
            }, selectedYear, selectedMonth, selectedDay);
            datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePicker.show();
        });

        btnPickTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(selectedHour)
                    .setMinute(selectedMinute)
                    .setTitleText("Select Schedule Time")
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

        btnSave.setOnClickListener(v -> {
            String title = etTaskTitle.getText().toString().trim();
            String finalTimeString = tvSelectedTime.getText().toString();
            String durationStr = etDuration.getText().toString().trim();

            if (title.isEmpty()) {
                etTaskTitle.setError("Please enter a title");
                return;
            }

            // Default duration to 0 if they don't type anything
            int durationValue = 0;
            if (!durationStr.isEmpty()) {
                try {
                    durationValue = Integer.parseInt(durationStr);
                } catch (NumberFormatException e) {
                    durationValue = 0;
                }
            }

            // NOW CALLING THE NEW 9-PARAMETER CONSTRUCTOR
            ActionItem newItem = new ActionItem(selectedType, title, finalTimeString, selectedHour, selectedMinute, selectedYear, selectedMonth, selectedDay, durationValue);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                FocusDatabase.getInstance(getContext()).actionDao().insert(newItem);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String timeMsg = calculateTimeLeft(selectedType, selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute);
                        Toast.makeText(getContext(), "Added! Starts in " + timeMsg, Toast.LENGTH_LONG).show();
                        dismiss();
                    });
                }
            });

            scheduleAlarm(title, selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute);
        });

        btnCancel.setOnClickListener(v -> dismiss());
        updateTypeSelection("routines");
        return view;
    }

    private String calculateTimeLeft(String type, int year, int month, int day, int hour, int minute) {
        Calendar target = Calendar.getInstance();
        if (type.equals("routines")) {
            target.set(Calendar.HOUR_OF_DAY, hour);
            target.set(Calendar.MINUTE, minute);
            target.set(Calendar.SECOND, 0);
            if (target.getTimeInMillis() <= System.currentTimeMillis()) target.add(Calendar.DAY_OF_YEAR, 1);
        } else {
            target.set(year, month, day, hour, minute, 0);
        }
        long diffMillis = target.getTimeInMillis() - System.currentTimeMillis();
        long days = diffMillis / (1000 * 60 * 60 * 24);
        long hours = (diffMillis / (1000 * 60 * 60)) % 24;
        long mins = (diffMillis / (1000 * 60)) % 60;
        if (days > 0) return days + "d " + hours + "h";
        else if (hours > 0) return hours + "h " + mins + "m";
        else return mins + " mins";
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

            PendingIntent exactPendingIntent = PendingIntent.getBroadcast(
                    getContext(),
                    (int) System.currentTimeMillis(),
                    exactIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(exactTime.getTimeInMillis(), exactPendingIntent);
            alarmManager.setAlarmClock(alarmClockInfo, exactPendingIntent);

            Calendar preWarningTime = (Calendar) exactTime.clone();
            preWarningTime.add(Calendar.HOUR_OF_DAY, -1);

            if (preWarningTime.getTimeInMillis() > System.currentTimeMillis()) {
                Intent preIntent = new Intent(getContext(), AlarmReceiver.class);
                preIntent.putExtra("TASK_TITLE", taskTitle);
                preIntent.putExtra("IS_PRE_WARNING", true);

                PendingIntent prePendingIntent = PendingIntent.getBroadcast(
                        getContext(),
                        (int) System.currentTimeMillis() + 1,
                        preIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preWarningTime.getTimeInMillis(), prePendingIntent);
            }
        }
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
}