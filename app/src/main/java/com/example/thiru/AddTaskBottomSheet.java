package com.example.thiru;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

public class AddTaskBottomSheet extends BottomSheetDialogFragment {

    // ── Existing fields (all unchanged) ─────────────────
    private TextInputEditText etTaskTitle, etDescription, etDuration;
    private TextView tvSelectedTime, tvSelectedDate, tvSelectedRepeat;
    private MaterialButton btnTypeRoutine, btnTypeTask, btnSave, btnCancel;
    private LinearLayout btnPickTime, btnPickDate, btnPickRepeat;

    private String selectedType = "routines";
    private int selectedHour, selectedMinute;
    private int selectedYear, selectedMonth, selectedDay;
    private boolean isSaving = false;

    // ── AI fields ────────────────────────────────────────
    private LinearLayout layoutCategoryRow;
    private MaterialCardView cardCategoryChip, cardDurationSuggestion;
    private TextView tvCategoryEmoji, tvCategoryLabel, tvDurationSuggestion;

    private SmartCategoryEngine.Category currentCategory =
            SmartCategoryEngine.CAT_NONE;
    private List<ActionItem> historyItems = new ArrayList<>();

    // Debounce handler — avoids calling AI on every single keystroke
    private final Handler aiDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable aiDebounceRunnable;
    private static final int AI_DEBOUNCE_MS = 350;

    // ────────────────────────────────────────────────────
    public AddTaskBottomSheet() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Bind existing views ──────────────────────────
        etTaskTitle        = view.findViewById(R.id.etTaskTitle);
        etDescription      = view.findViewById(R.id.etDescription);
        etDuration         = view.findViewById(R.id.etDuration);
        tvSelectedTime     = view.findViewById(R.id.tvSelectedTime);
        tvSelectedDate     = view.findViewById(R.id.tvSelectedDate);
        tvSelectedRepeat   = view.findViewById(R.id.tvSelectedRepeat);
        btnTypeRoutine     = view.findViewById(R.id.btnTypeRoutine);
        btnTypeTask        = view.findViewById(R.id.btnTypeTask);
        btnSave            = view.findViewById(R.id.btnSave);
        btnCancel          = view.findViewById(R.id.btnCancel);
        btnPickTime        = view.findViewById(R.id.btnPickTime);
        btnPickDate        = view.findViewById(R.id.btnPickDate);
        btnPickRepeat      = view.findViewById(R.id.btnPickRepeat);

        // ── Bind AI views ────────────────────────────────
        layoutCategoryRow      = view.findViewById(R.id.layoutCategoryRow);
        cardCategoryChip       = view.findViewById(R.id.cardCategoryChip);
        tvCategoryEmoji        = view.findViewById(R.id.tvCategoryEmoji);
        tvCategoryLabel        = view.findViewById(R.id.tvCategoryLabel);
        cardDurationSuggestion = view.findViewById(R.id.cardDurationSuggestion);
        tvDurationSuggestion   = view.findViewById(R.id.tvDurationSuggestion);

        // ── Set defaults ─────────────────────────────────
        Calendar now = Calendar.getInstance();
        selectedHour   = now.get(Calendar.HOUR_OF_DAY);
        selectedMinute = now.get(Calendar.MINUTE);
        selectedYear   = now.get(Calendar.YEAR);
        selectedMonth  = now.get(Calendar.MONTH);
        selectedDay    = now.get(Calendar.DAY_OF_MONTH);
        updateTimeDisplay();
        tvSelectedDate.setText("Today");
        tvSelectedRepeat.setText("None");
        updateTypeSelection();

        // ── Load history for AI predictions ─────────────
        loadHistoryAsync();

        // ── AI: TextWatcher on title ─────────────────────
        etTaskTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                // Cancel previous debounce
                if (aiDebounceRunnable != null)
                    aiDebounceHandler.removeCallbacks(aiDebounceRunnable);

                String text = s.toString().trim();

                if (text.length() < 2) {
                    hideCategoryChip();
                    hideDurationSuggestion();
                    return;
                }

                // Immediate category update (instant, local keyword match)
                SmartCategoryEngine.Category detected =
                        SmartCategoryEngine.classify(text);
                if (!detected.id.equals(currentCategory.id)) {
                    currentCategory = detected;
                    showCategoryChip(currentCategory);
                }

                // Debounced duration suggestion (slightly heavier)
                aiDebounceRunnable = () -> updateDurationSuggestion(text);
                aiDebounceHandler.postDelayed(aiDebounceRunnable, AI_DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ── AI: Category chip tap → picker dialog ────────
        cardCategoryChip.setOnClickListener(v -> showCategoryPicker());

        // ── AI: Duration suggestion tap → auto-fill ──────
        cardDurationSuggestion.setOnClickListener(v -> {
            if (etDuration.getText() == null || etDuration.getText().toString().isEmpty()) {
                String label = tvDurationSuggestion.getText().toString();
                // Extract the number from "⏱  ~45 min  ·  ..."
                try {
                    String num = label.replaceAll("[^0-9]", "").trim();
                    // If hours format, convert
                    if (label.contains("h") && !label.contains("min")) {
                        // e.g. "~1h" — already in hours, convert to minutes
                        int hours = Integer.parseInt(num);
                        etDuration.setText(String.valueOf(hours * 60));
                    } else {
                        // Extract first number (the minutes value)
                        int predicted = DurationEstimator.predict(
                                requireContext(),
                                etTaskTitle.getText().toString(),
                                currentCategory.id,
                                historyItems);
                        if (predicted > 0)
                            etDuration.setText(String.valueOf(predicted));
                    }
                } catch (Exception ignored) {}
            }
            hideDurationSuggestion();
        });

        // ── Existing click handlers (all unchanged) ──────
        btnTypeRoutine.setOnClickListener(v -> { selectedType = "routines"; updateTypeSelection(); });
        btnTypeTask.setOnClickListener(v -> { selectedType = "tasks"; updateTypeSelection(); });

        btnPickTime.setOnClickListener(v -> {
            TimePickerDialog tpd = new TimePickerDialog(getContext(),
                    (tp, h, m) -> {
                        selectedHour = h; selectedMinute = m; updateTimeDisplay();
                    }, selectedHour, selectedMinute, false);
            tpd.show();
        });

        btnPickDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(getContext(),
                    (dp, y, m, d) -> {
                        selectedYear = y; selectedMonth = m; selectedDay = d;
                        tvSelectedDate.setText(d + "/" + (m + 1) + "/" + y);
                    }, selectedYear, selectedMonth, selectedDay);
            dpd.show();
        });

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

    // ════════════════════════════════════════════════════
    //   AI — Category chip
    // ════════════════════════════════════════════════════

    private void showCategoryChip(SmartCategoryEngine.Category cat) {
        tvCategoryEmoji.setText(cat.emoji);
        tvCategoryLabel.setText(cat.label);
        tvCategoryLabel.setTextColor(cat.color);
        cardCategoryChip.setCardBackgroundColor(cat.bgColor);
        cardCategoryChip.setStrokeColor(cat.color & 0x33FFFFFF | (cat.color & 0xFF000000));

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

    // ════════════════════════════════════════════════════
    //   AI — Duration suggestion
    // ════════════════════════════════════════════════════

    private void updateDurationSuggestion(String title) {
        // Don't show if user already typed a duration
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

    // ════════════════════════════════════════════════════
    //   AI — Category picker bottom dialog
    // ════════════════════════════════════════════════════

    private void showCategoryPicker() {
        if (getContext() == null) return;
        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Choose Category");

        SmartCategoryEngine.Category[] cats = SmartCategoryEngine.ALL_CATEGORIES;
        String[] labels = new String[cats.length];
        for (int i = 0; i < cats.length; i++) {
            labels[i] = cats[i].emoji + "  " + cats[i].label;
        }

        builder.setItems(labels, (dialog, which) -> {
            currentCategory = cats[which];
            showCategoryChip(currentCategory);
            // Refresh duration suggestion with new category
            String title = etTaskTitle.getText() != null
                    ? etTaskTitle.getText().toString() : "";
            updateDurationSuggestion(title);
        });
        builder.show();
    }

    // ════════════════════════════════════════════════════
    //   LOAD HISTORY (background thread)
    // ════════════════════════════════════════════════════

    private void loadHistoryAsync() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<ActionItem> items = FocusDatabase
                        .getInstance(requireContext())
                        .actionDao()
                        .getAllItemsSync();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded()) historyItems = items != null ? items : new ArrayList<>();
                });
            } catch (Exception ignored) {}
        });
    }

    // ════════════════════════════════════════════════════
    //   SAVE TASK (original logic + category saved)
    // ════════════════════════════════════════════════════

    private void saveTask(String title) {
        String description = etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";
        int duration = 0;
        try {
            String durStr = etDuration.getText() != null
                    ? etDuration.getText().toString().trim() : "";
            if (!durStr.isEmpty()) duration = Integer.parseInt(durStr);
        } catch (NumberFormatException ignored) {}

        String timeString = buildTimeString(selectedHour, selectedMinute);
        String repeatMode = tvSelectedRepeat.getText().toString();

        ActionItem item = new ActionItem(
                selectedType, title, timeString,
                selectedHour, selectedMinute,
                selectedYear, selectedMonth, selectedDay,
                duration, description, repeatMode);

        // ── Save category from AI detection ──
        item.category = currentCategory.id;

        // ── Record for future duration predictions ──
        if (duration > 0) {
            DurationEstimator.record(requireContext(), title, duration);
        }

        final ActionItem finalItem = item;
        final int finalDuration = duration;

        Executors.newSingleThreadExecutor().execute(() -> {
            FocusDatabase.getInstance(requireContext())
                    .actionDao().insert(finalItem);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isAdded()) dismiss();
            });
        });
    }

    // ════════════════════════════════════════════════════
    //   EXISTING HELPERS (all unchanged)
    // ════════════════════════════════════════════════════

    private void updateTimeDisplay() {
        String amPm = selectedHour >= 12 ? "PM" : "AM";
        int hour12 = selectedHour % 12;
        if (hour12 == 0) hour12 = 12;
        tvSelectedTime.setText(String.format(java.util.Locale.getDefault(),
                "%d:%02d %s", hour12, selectedMinute, amPm));
    }

    private String buildTimeString(int hour, int minute) {
        String amPm = hour >= 12 ? "PM" : "AM";
        int h = hour % 12;
        if (h == 0) h = 12;
        return String.format(java.util.Locale.getDefault(),
                "%d:%02d %s", h, minute, amPm);
    }

    private void updateTypeSelection() {
        boolean isRoutine = selectedType.equals("routines");
        btnTypeRoutine.setBackgroundColor(isRoutine
                ? getResources().getColor(android.R.color.holo_blue_dark, null)
                : Color.TRANSPARENT);
        btnTypeRoutine.setTextColor(isRoutine ? Color.WHITE
                : getResources().getColor(android.R.color.darker_gray, null));
        btnTypeTask.setBackgroundColor(!isRoutine
                ? getResources().getColor(android.R.color.holo_blue_dark, null)
                : Color.TRANSPARENT);
        btnTypeTask.setTextColor(!isRoutine ? Color.WHITE
                : getResources().getColor(android.R.color.darker_gray, null));
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