package com.example.thiru;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

public class FocusTimerDialog extends DialogFragment {

    private TextView tvTimer;
    private MaterialButton btnStartPause, btnReset;

    private CountDownTimer countDownTimer;
    private boolean timerRunning = false;
    private long START_TIME_IN_MILLIS; // Now dynamic!
    private long timeLeftInMillis;
    private long endTime;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_focus_timer, container, false);

        tvTimer = view.findViewById(R.id.tvTimer);
        btnStartPause = view.findViewById(R.id.btnStartPause);
        btnReset = view.findViewById(R.id.btnReset);
        TextView btnClose = view.findViewById(R.id.btnClose);

        // --- 1. GRAB THE USER'S PREFERRED TIME ---
        SharedPreferences appPrefs = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        int focusMins = appPrefs.getInt("focusMins", 25); // Default to 25 if not set
        START_TIME_IN_MILLIS = focusMins * 60 * 1000L;

        // --- 2. RESTORE TIMER STATE ---
        SharedPreferences timerPrefs = requireActivity().getSharedPreferences("FocusTimerPrefs", Context.MODE_PRIVATE);
        timerRunning = timerPrefs.getBoolean("timerRunning", false);

        if (timerRunning) {
            endTime = timerPrefs.getLong("endTime", 0);
            timeLeftInMillis = endTime - System.currentTimeMillis();

            if (timeLeftInMillis < 0) {
                timeLeftInMillis = 0;
                timerRunning = false;
                updateCountDownText();
                updateButtons();
                playAlarmSound();
            } else {
                startTimer();
            }
        } else {
            // If it's not running, grab the left-over time (or start fresh with user setting)
            timeLeftInMillis = timerPrefs.getLong("millisLeft", START_TIME_IN_MILLIS);

            // Safety check: if they changed the setting in the menu, reset to the new full time
            if (timeLeftInMillis > START_TIME_IN_MILLIS) {
                timeLeftInMillis = START_TIME_IN_MILLIS;
            }

            updateCountDownText();
            updateButtons();
        }

        btnStartPause.setOnClickListener(v -> {
            if (timerRunning) pauseTimer();
            else startTimer();
        });

        btnReset.setOnClickListener(v -> resetTimer());
        btnClose.setOnClickListener(v -> dismiss());

        return view;
    }

    private void startTimer() {
        endTime = System.currentTimeMillis() + timeLeftInMillis;

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountDownText();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                timeLeftInMillis = START_TIME_IN_MILLIS;
                updateCountDownText();
                updateButtons();
                playAlarmSound();
            }
        }.start();

        timerRunning = true;
        updateButtons();
    }

    private void pauseTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        updateButtons();
    }

    private void resetTimer() {
        if (countDownTimer != null) countDownTimer.cancel();
        timerRunning = false;
        timeLeftInMillis = START_TIME_IN_MILLIS;
        updateCountDownText();
        updateButtons();
    }

    private void updateCountDownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvTimer.setText(timeLeftFormatted);
    }

    private void updateButtons() {
        if (timerRunning) {
            btnStartPause.setText("PAUSE");
            btnStartPause.setBackgroundColor(Color.parseColor("#F59E0B"));
        } else {
            btnStartPause.setText(timeLeftInMillis < START_TIME_IN_MILLIS ? "RESUME" : "START");
            btnStartPause.setBackgroundColor(Color.parseColor("#2563EB"));
        }
    }

    private void playAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            Ringtone ringtone = RingtoneManager.getRingtone(getContext(), alarmUri);
            ringtone.play();
            Toast.makeText(getContext(), "Focus Session Complete!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        SharedPreferences prefs = requireActivity().getSharedPreferences("FocusTimerPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong("millisLeft", timeLeftInMillis);
        editor.putBoolean("timerRunning", timerRunning);
        editor.putLong("endTime", endTime);
        editor.apply();

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}