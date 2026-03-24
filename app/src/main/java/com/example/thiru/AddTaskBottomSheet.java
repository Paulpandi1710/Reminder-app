package com.example.thiru;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

public class AddTaskBottomSheet extends BottomSheetDialogFragment {

    // ── Views ─────────────────────────────────────────────
    private TextInputEditText etTaskTitle, etDescription, etDuration;
    private TextView tvSelectedTime, tvSelectedDate, tvSelectedRepeat;
    private MaterialButton btnTypeRoutine, btnTypeTask;
    private MaterialButton btnSave, btnCancel;
    private LinearLayout btnPickTime, btnPickDate, btnPickRepeat;

    // ── State ─────────────────────────────────────────────
    private String selectedType = "routines";
    private int selectedHour, selectedMinute;
    private int selectedYear, selectedMonth, selectedDay;
    private boolean isSaving = false;

    // ── AI views ──────────────────────────────────────────
    private LinearLayout layoutCategoryRow;
    private MaterialCardView cardCategoryChip, cardDurationSuggestion;
    private TextView tvCategoryEmoji, tvCategoryLabel, tvDurationSuggestion;
    private SmartCategoryEngine.Category currentCategory =
            SmartCategoryEngine.CAT_NONE;
    private List<ActionItem> historyItems = new ArrayList<>();

    private final Handler aiDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable aiDebounceRunnable;
    private static final int AI_DEBOUNCE_MS = 350;

    public AddTaskBottomSheet() {}

    @Override
    public void onStart() {
        super.onStart();
        // Make the system bottom sheet transparent so our custom card corners show
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog != null) {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ══════════════════════════════════════════════════════
        //   SPRING ENTRANCE ANIMATION
        // ══════════════════════════════════════════════════════
        View root = view.findViewById(R.id.bottomSheetRoot);
        if (root != null) {
            root.setTranslationY(400f);
            root.animate()
                    .translationY(0f)
                    .setDuration(600)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }

        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }

        // ── Bind views ────────────────────────────────────
        etTaskTitle          = view.findViewById(R.id.etTaskTitle);
        etDescription        = view.findViewById(R.id.etDescription);
        etDuration           = view.findViewById(R.id.etDuration);
        tvSelectedTime       = view.findViewById(R.id.tvSelectedTime);
        tvSelectedDate       = view.findViewById(R.id.tvSelectedDate);
        tvSelectedRepeat     = view.findViewById(R.id.tvSelectedRepeat);
        btnTypeRoutine       = view.findViewById(R.id.btnTypeRoutine);
        btnTypeTask          = view.findViewById(R.id.btnTypeTask);
        btnSave              = view.findViewById(R.id.btnSave);
        btnCancel            = view.findViewById(R.id.btnCancel);
        btnPickTime          = view.findViewById(R.id.btnPickTime);
        btnPickDate          = view.findViewById(R.id.btnPickDate);
        btnPickRepeat        = view.findViewById(R.id.btnPickRepeat);
        layoutCategoryRow    = view.findViewById(R.id.layoutCategoryRow);
        cardCategoryChip     = view.findViewById(R.id.cardCategoryChip);
        tvCategoryEmoji      = view.findViewById(R.id.tvCategoryEmoji);
        tvCategoryLabel      = view.findViewById(R.id.tvCategoryLabel);
        cardDurationSuggestion = view.findViewById(R.id.cardDurationSuggestion);
        tvDurationSuggestion = view.findViewById(R.id.tvDurationSuggestion);

        // ── Defaults ──────────────────────────────────────
        Calendar now  = Calendar.getInstance();
        selectedHour   = now.get(Calendar.HOUR_OF_DAY);
        selectedMinute = now.get(Calendar.MINUTE);
        selectedYear   = now.get(Calendar.YEAR);
        selectedMonth  = now.get(Calendar.MONTH);
        selectedDay    = now.get(Calendar.DAY_OF_MONTH);

        updateTimeDisplay();
        tvSelectedDate.setText("Today");
        tvSelectedRepeat.setText("None");
        updateTypeSelection();

        loadHistoryAsync();

        // ── AI: TextWatcher ───────────────────────────────
        etTaskTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                if (aiDebounceRunnable != null)
                    aiDebounceHandler.removeCallbacks(aiDebounceRunnable);
                String text = s.toString().trim();
                if (text.length() < 2) {
                    hideCategoryChip();
                    hideDurationSuggestion();
                    return;
                }
                SmartCategoryEngine.Category detected =
                        SmartCategoryEngine.classify(text);
                if (!detected.id.equals(currentCategory.id)) {
                    currentCategory = detected;
                    showCategoryChip(currentCategory);
                }
                aiDebounceRunnable = () -> updateDurationSuggestion(text);
                aiDebounceHandler.postDelayed(aiDebounceRunnable, AI_DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        cardCategoryChip.setOnClickListener(v -> showCategoryPicker());
        cardDurationSuggestion.setOnClickListener(v -> {
            if (etDuration.getText() == null
                    || etDuration.getText().toString().isEmpty()) {
                int predicted = DurationEstimator.predict(
                        requireContext(),
                        etTaskTitle.getText() != null
                                ? etTaskTitle.getText().toString() : "",
                        currentCategory.id, historyItems);
                if (predicted > 0) etDuration.setText(String.valueOf(predicted));
            }
            hideDurationSuggestion();
        });

        // ── Type buttons ──────────────────────────────────
        btnTypeRoutine.setOnClickListener(v -> {
            selectedType = "routines";
            updateTypeSelection();
        });
        btnTypeTask.setOnClickListener(v -> {
            selectedType = "tasks";
            updateTypeSelection();
        });

        // ── Pickers ───────────────────────────────────────
        btnPickTime.setOnClickListener(v ->
                new TimePickerDialog(getContext(),
                        (tp, h, m) -> { selectedHour = h; selectedMinute = m; updateTimeDisplay(); },
                        selectedHour, selectedMinute, false).show());

        btnPickDate.setOnClickListener(v ->
                new DatePickerDialog(getContext(),
                        (dp, y, m, d) -> {
                            selectedYear = y; selectedMonth = m; selectedDay = d;
                            tvSelectedDate.setText(d + "/" + (m + 1) + "/" + y);
                        }, selectedYear, selectedMonth, selectedDay).show());

        btnPickRepeat.setOnClickListener(v -> showRepeatPicker());

        btnCancel.setOnClickListener(v -> dismiss());

        btnSave.setOnClickListener(v -> {
            if (isSaving) return;
            String title = etTaskTitle.getText() != null
                    ? etTaskTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                etTaskTitle.setError("Please enter a title");
                return;
            }
            isSaving = true;
            btnSave.setEnabled(false);
            saveTask(title);
        });
    }

    private void updateTypeSelection() {
        boolean isRoutine = "routines".equals(selectedType);

        btnPickDate.setVisibility(isRoutine ? View.GONE : View.VISIBLE);
        btnPickRepeat.setVisibility(isRoutine ? View.VISIBLE : View.GONE);

        if (!isRoutine) {
            tvSelectedRepeat.setText("None");
        }

        // ── GLASSMORPHISM SELECTION STYLING ──
        int activeStroke = Color.parseColor("#4263EB");
        int activeText = Color.parseColor("#FFFFFF");
        int inactiveStroke = Color.parseColor("#1A2244");
        int inactiveText = Color.parseColor("#8899BB");

        if (isRoutine) {
            if (btnTypeRoutine != null) {
                btnTypeRoutine.setStrokeColor(android.content.res.ColorStateList.valueOf(activeStroke));
                btnTypeRoutine.setTextColor(activeText);
            }
            if (btnTypeTask != null) {
                btnTypeTask.setStrokeColor(android.content.res.ColorStateList.valueOf(inactiveStroke));
                btnTypeTask.setTextColor(inactiveText);
            }
        } else {
            if (btnTypeTask != null) {
                btnTypeTask.setStrokeColor(android.content.res.ColorStateList.valueOf(activeStroke));
                btnTypeTask.setTextColor(activeText);
            }
            if (btnTypeRoutine != null) {
                btnTypeRoutine.setStrokeColor(android.content.res.ColorStateList.valueOf(inactiveStroke));
                btnTypeRoutine.setTextColor(inactiveText);
            }
        }
    }

    private void showCategoryChip(SmartCategoryEngine.Category cat) {
        tvCategoryEmoji.setText(cat.emoji);
        tvCategoryLabel.setText(cat.label);
        tvCategoryLabel.setTextColor(cat.color);
        cardCategoryChip.setCardBackgroundColor(cat.bgColor);
        if (layoutCategoryRow.getVisibility() != View.VISIBLE) {
            layoutCategoryRow.setAlpha(0f);
            layoutCategoryRow.setVisibility(View.VISIBLE);
            layoutCategoryRow.animate().alpha(1f).setDuration(220).start();
        }
    }

    private void hideCategoryChip() {
        layoutCategoryRow.setVisibility(View.GONE);
        currentCategory = SmartCategoryEngine.CAT_NONE;
    }

    private void updateDurationSuggestion(String title) {
        if (etDuration.getText() != null
                && !etDuration.getText().toString().isEmpty()) {
            hideDurationSuggestion();
            return;
        }
        String lbl = DurationEstimator.label(
                requireContext(), title, currentCategory.id, historyItems);
        if (lbl != null) {
            tvDurationSuggestion.setText(lbl);
            if (cardDurationSuggestion.getVisibility() != View.VISIBLE) {
                cardDurationSuggestion.setAlpha(0f);
                cardDurationSuggestion.setVisibility(View.VISIBLE);
                cardDurationSuggestion.animate().alpha(1f).setDuration(220).start();
            }
        } else {
            hideDurationSuggestion();
        }
    }

    private void hideDurationSuggestion() {
        cardDurationSuggestion.setVisibility(View.GONE);
    }

    private void showCategoryPicker() {
        if (getContext() == null) return;
        SmartCategoryEngine.Category[] cats = SmartCategoryEngine.ALL_CATEGORIES;
        String[] labels = new String[cats.length];
        for (int i = 0; i < cats.length; i++)
            labels[i] = cats[i].emoji + "  " + cats[i].label;
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Choose Category")
                .setItems(labels, (dialog, which) -> {
                    currentCategory = cats[which];
                    showCategoryChip(currentCategory);
                    updateDurationSuggestion(etTaskTitle.getText() != null
                            ? etTaskTitle.getText().toString() : "");
                }).show();
    }

    private void loadHistoryAsync() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<ActionItem> items = FocusDatabase
                        .getInstance(requireContext()).actionDao().getAllItemsSync();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded())
                        historyItems = items != null ? items : new ArrayList<>();
                });
            } catch (Exception ignored) {}
        });
    }

    private void saveTask(String title) {
        String description = etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";
        int duration = 0;
        try {
            String d = etDuration.getText() != null
                    ? etDuration.getText().toString().trim() : "";
            if (!d.isEmpty()) duration = Integer.parseInt(d);
        } catch (NumberFormatException ignored) {}

        String timeString  = buildTimeString(selectedHour, selectedMinute);
        String repeatMode  = tvSelectedRepeat.getText().toString();

        if ("tasks".equals(selectedType)) repeatMode = "None";

        ActionItem item = new ActionItem(
                selectedType, title, timeString,
                selectedHour, selectedMinute,
                selectedYear, selectedMonth, selectedDay,
                duration, description, repeatMode);

        item.category = currentCategory.id;

        if (duration > 0)
            DurationEstimator.record(requireContext(), title, duration);

        final ActionItem finalItem = item;
        Executors.newSingleThreadExecutor().execute(() -> {
            FocusDatabase db = FocusDatabase.getInstance(requireContext());
            db.actionDao().insert(finalItem);

            // ── THE FIX: Retrieve the item immediately to schedule its alarm ──
            ActionItem insertedItem = db.actionDao().getTaskByTitle(finalItem.title);
            if (insertedItem == null) insertedItem = finalItem;

            final ActionItem itemToSchedule = insertedItem;

            new Handler(Looper.getMainLooper()).post(() -> {
                if (isAdded()) {
                    scheduleAlarmForItem(itemToSchedule);
                    dismiss();
                }
            });
        });
    }

    // ── THE FIX: Standard Alarm Scheduling Logic ──
    private void scheduleAlarmForItem(ActionItem item) {
        if (getContext() == null) return;
        try {
            AlarmManager am = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Calendar exactTime = Calendar.getInstance();
            if ("tasks".equals(item.type)) {
                exactTime.set(item.year, item.month, item.day,
                        item.hour, item.minute, 0);
                exactTime.set(Calendar.MILLISECOND, 0);
            } else {
                exactTime.set(Calendar.HOUR_OF_DAY, item.hour);
                exactTime.set(Calendar.MINUTE,      item.minute);
                exactTime.set(Calendar.SECOND, 0);
                exactTime.set(Calendar.MILLISECOND, 0);
                if (exactTime.getTimeInMillis() <= System.currentTimeMillis())
                    exactTime.add(Calendar.DAY_OF_YEAR, 1);
            }

            if (exactTime.getTimeInMillis() <= System.currentTimeMillis()) return;

            Intent intent = new Intent(getContext(), AlarmReceiver.class);
            intent.putExtra("TASK_TITLE", item.title);
            intent.putExtra("IS_PRE_WARNING", false);

            int reqCode = (int)(exactTime.getTimeInMillis() % Integer.MAX_VALUE);
            PendingIntent pi = PendingIntent.getBroadcast(getContext(), reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms())
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(exactTime.getTimeInMillis(), pi), pi);
                else
                    am.set(AlarmManager.RTC_WAKEUP, exactTime.getTimeInMillis(), pi);
            } else {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(exactTime.getTimeInMillis(), pi), pi);
            }
        } catch (Exception ignored) {}
    }

    private void updateTimeDisplay() {
        String ap = selectedHour >= 12 ? "PM" : "AM";
        int h = selectedHour % 12;
        if (h == 0) h = 12;
        tvSelectedTime.setText(String.format(java.util.Locale.getDefault(),
                "%d:%02d %s", h, selectedMinute, ap));
    }

    private String buildTimeString(int hour, int minute) {
        String ap = hour >= 12 ? "PM" : "AM";
        int h = hour % 12;
        if (h == 0) h = 12;
        return String.format(java.util.Locale.getDefault(),
                "%d:%02d %s", h, minute, ap);
    }

    private void showRepeatPicker() {
        String[] options = {"None", "Daily", "Weekly: Monday", "Weekly: Tuesday",
                "Weekly: Wednesday", "Weekly: Thursday", "Weekly: Friday",
                "Weekly: Saturday", "Weekly: Sunday"};
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Repeat")
                .setItems(options, (d, i) -> tvSelectedRepeat.setText(options[i]))
                .show();
    }
}