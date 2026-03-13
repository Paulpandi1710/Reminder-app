package com.example.thiru;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class AIFragment extends Fragment {

    private static final String TAG        = "AIFragment";
    private static final String PREFS_CHAT = "AIChatHistory";
    private static final String KEY_MSGS   = "chat_messages";
    private static final int    MAX_SAVED  = 40;

    // ── Views ─────────────────────────────────────────────
    private RecyclerView     rvChat;
    private EditText         etAIInput;
    private ImageView        btnSendAI;
    private ImageView        btnMicAI;
    private MaterialCardView cardSendBtn;
    private MaterialCardView cardMicBtn;
    private LinearLayout     layoutTypingIndicator;
    private LinearLayout     layoutSuggestionChips;
    private android.widget.HorizontalScrollView scrollSuggestionChips;
    private LinearLayout     layoutQuickActions;
    private TextView         tvOnlineStatus;
    private TextView         tvAIHeader;
    private TextView         tvAISubtitle;

    // ── State ─────────────────────────────────────────────
    private AIChatAdapter chatAdapter;
    private final List<AIEngine.AIMessage> messages     = new ArrayList<>();
    private List<ActionItem>               currentItems = new ArrayList<>();
    private String  userName      = "";
    private boolean sendBtnActive = false;
    private boolean isListening   = false;

    // ── Speech ────────────────────────────────────────────
    private SpeechRecognizer speechRecognizer;
    private ObjectAnimator   micPulseAnimator;

    // ══════════════════════════════════════════════════════
    //   RECORD_AUDIO permission launcher
    //   MUST be a field — registered before onStart
    // ══════════════════════════════════════════════════════
    private final ActivityResultLauncher<String> micPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startVoiceListening();
                        } else {
                            showMicPermissionDialog();
                        }
                    });

    public AIFragment() {
        super(R.layout.fragment_ai);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvChat                = view.findViewById(R.id.rvChat);
        etAIInput             = view.findViewById(R.id.etAIInput);
        btnSendAI             = view.findViewById(R.id.btnSendAI);
        btnMicAI              = view.findViewById(R.id.btnMicAI);
        cardSendBtn           = view.findViewById(R.id.cardSendBtn);
        cardMicBtn            = view.findViewById(R.id.cardMicBtn);
        layoutTypingIndicator = view.findViewById(R.id.layoutTypingIndicator);
        layoutSuggestionChips = view.findViewById(R.id.layoutSuggestionChips);
        scrollSuggestionChips = view.findViewById(R.id.scrollSuggestionChips);
        layoutQuickActions    = view.findViewById(R.id.layoutQuickActions);
        tvOnlineStatus        = view.findViewById(R.id.tvOnlineStatus);
        tvAIHeader            = view.findViewById(R.id.tvAIHeader);
        tvAISubtitle          = view.findViewById(R.id.tvAISubtitle);

        loadUserName();
        updateHeader();
        if (tvAISubtitle != null) tvAISubtitle.setText(getSubtitle());

        // ── RecyclerView ──────────────────────────────────
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setStackFromEnd(true);
        rvChat.setLayoutManager(llm);
        chatAdapter = new AIChatAdapter(messages);
        rvChat.setAdapter(chatAdapter);

        buildQuickChips();

        // ── Observe DB ────────────────────────────────────
        FocusDatabase.getInstance(getContext()).actionDao().getAllItems()
                .observe(getViewLifecycleOwner(), items ->
                        currentItems = items != null ? items : new ArrayList<>());

        // ── Send button ───────────────────────────────────
        btnSendAI.setOnClickListener(v -> {
            animateSendButtonPress();
            sendMessage();
        });

        // ── Mic button ────────────────────────────────────
        btnMicAI.setOnClickListener(v -> {
            if (isListening) {
                stopVoiceListening();
            } else {
                requestMicAndListen();
            }
        });

        // ── Enter key ─────────────────────────────────────
        etAIInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                animateSendButtonPress();
                sendMessage();
                return true;
            }
            return false;
        });

        // ── Keyboard — untouched from working version ─────
        etAIInput.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.requestFocus();
                showKeyboard(v);
            }
            return false;
        });

        etAIInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showKeyboard(v);
        });

        // ── IME inset padding — untouched ─────────────────
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(0, 0, 0, imeBottom > 0 ? imeBottom : 0);
            return insets;
        });

        // ── Send button state animation on typing ─────────
        etAIInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                boolean hasText = s.length() > 0;
                if (hasText != sendBtnActive) {
                    sendBtnActive = hasText;
                    animateSendButtonState(hasText);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        updateStatusBadge();
        initSpeechRecognizer();

        // ── Chat persistence ──────────────────────────────
        boolean restored = loadChatHistory();
        if (!restored) {
            new Handler(Looper.getMainLooper()).postDelayed(
                    this::showPersonalWelcome, 500);
        } else {
            rvChat.post(() -> {
                if (isAdded() && !messages.isEmpty())
                    rvChat.scrollToPosition(messages.size() - 1);
            });
        }
    }

    // ══════════════════════════════════════════════════════
    //   SPEECH RECOGNIZER — init once, reuse
    //   Created on main thread (required by Android)
    //   Destroyed in onDestroyView to prevent leaks
    // ══════════════════════════════════════════════════════

    private void initSpeechRecognizer() {
        if (!isAdded()) return;
        try {
            if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
                speechRecognizer.setRecognitionListener(new RecognitionListener() {

                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        // Mic is ready — animate to red pulsing
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (isAdded()) setMicListeningState(true);
                        });
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        // User started speaking — stronger pulse
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (isAdded() && cardMicBtn != null)
                                cardMicBtn.animate().scaleX(1.1f).scaleY(1.1f)
                                        .setDuration(100).start();
                        });
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                        // Pulse scale based on voice volume
                        if (!isAdded() || cardMicBtn == null) return;
                        float scale = 1f + Math.min(rmsdB / 100f, 0.15f);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (isAdded())
                                cardMicBtn.animate().scaleX(scale).scaleY(scale)
                                        .setDuration(50).start();
                        });
                    }

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (isAdded()) setMicListeningState(false);
                        });
                    }

                    @Override
                    public void onError(int error) {
                        Log.w(TAG, "SpeechRecognizer error: " + error);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            setMicListeningState(false);
                            // Only show error for real failures, not silence
                            if (error != SpeechRecognizer.ERROR_NO_MATCH
                                    && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                                addMessage(new AIEngine.AIMessage(
                                        "🎤 Couldn't hear that — please try again.", false));
                            }
                        });
                    }

                    @Override
                    public void onResults(Bundle results) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded()) return;
                            setMicListeningState(false);
                            ArrayList<String> matches = results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                handleVoiceResult(matches.get(0));
                            }
                        });
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        // Show partial text in EditText as user speaks
                        ArrayList<String> partial = partialResults.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION);
                        if (partial != null && !partial.isEmpty()) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (isAdded() && etAIInput != null)
                                    etAIInput.setText(partial.get(0));
                            });
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });
            } else {
                Log.w(TAG, "Speech recognition not available on this device");
            }
        } catch (Exception e) {
            Log.e(TAG, "initSpeechRecognizer: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    //   VOICE — REQUEST PERMISSION THEN LISTEN
    // ══════════════════════════════════════════════════════

    private void requestMicAndListen() {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceListening();
        } else {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startVoiceListening() {
        if (!isAdded() || speechRecognizer == null) return;
        try {
            hideKeyboard();
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a task or question…");
            isListening = true;
            speechRecognizer.startListening(intent);
            Log.d(TAG, "Voice listening started");
        } catch (Exception e) {
            Log.e(TAG, "startVoiceListening: " + e.getMessage());
            isListening = false;
            setMicListeningState(false);
        }
    }

    private void stopVoiceListening() {
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        isListening = false;
        setMicListeningState(false);
    }

    // ══════════════════════════════════════════════════════
    //   HANDLE VOICE RESULT
    //   If it sounds like an action → auto-send to Groq
    //   Otherwise → fill EditText, user taps send
    // ══════════════════════════════════════════════════════

    private void handleVoiceResult(String text) {
        if (!isAdded() || text == null || text.trim().isEmpty()) return;
        String clean = text.trim();

        // Capitalise first letter
        clean = Character.toUpperCase(clean.charAt(0)) + clean.substring(1);

        etAIInput.setText(clean);
        etAIInput.setSelection(clean.length());

        // Auto-send if it's clearly an action command
        String lower = clean.toLowerCase();
        boolean isAction = lower.contains("add task")
                || lower.contains("create task")
                || lower.contains("add routine")
                || lower.contains("create routine")
                || lower.contains("schedule task")
                || lower.contains("remind me")
                || lower.contains("set alarm");

        if (isAction) {
            // Small delay so user sees the text before it sends
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded()) {
                    animateSendButtonPress();
                    sendMessage();
                }
            }, 600);
        }
        // Otherwise user sees the text and taps send manually
    }

    // ══════════════════════════════════════════════════════
    //   MIC BUTTON ANIMATION
    //   Idle:      dark bg, grey icon
    //   Listening: red bg, white icon, pulse scale loop
    // ══════════════════════════════════════════════════════

    private void setMicListeningState(boolean listening) {
        if (!isAdded() || cardMicBtn == null || btnMicAI == null) return;
        isListening = listening;

        // Stop previous pulse
        if (micPulseAnimator != null) {
            micPulseAnimator.cancel();
            micPulseAnimator = null;
        }

        if (listening) {
            // Red background
            animateCardColor(cardMicBtn,
                    Color.parseColor("#1A1A3A"),
                    Color.parseColor("#C0392B"), 200);
            btnMicAI.setColorFilter(Color.WHITE);
            cardMicBtn.setScaleX(1f);
            cardMicBtn.setScaleY(1f);

            // Repeating pulse
            micPulseAnimator = ObjectAnimator.ofFloat(cardMicBtn, "scaleX", 1f, 1.12f);
            micPulseAnimator.setDuration(500);
            micPulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            micPulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            micPulseAnimator.start();

            ObjectAnimator pulseY = ObjectAnimator.ofFloat(cardMicBtn, "scaleY", 1f, 1.12f);
            pulseY.setDuration(500);
            pulseY.setRepeatCount(ObjectAnimator.INFINITE);
            pulseY.setRepeatMode(ObjectAnimator.REVERSE);
            pulseY.start();

        } else {
            // Restore idle state
            animateCardColor(cardMicBtn,
                    Color.parseColor("#C0392B"),
                    Color.parseColor("#1A1A3A"), 200);
            btnMicAI.setColorFilter(Color.parseColor("#445577"));
            cardMicBtn.animate().scaleX(1f).scaleY(1f).setDuration(150)
                    .setInterpolator(new OvershootInterpolator(2f)).start();
        }
    }

    private void animateCardColor(MaterialCardView card, int from, int to, int duration) {
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(duration);
        anim.addUpdateListener(a -> {
            if (isAdded()) card.setCardBackgroundColor((int) a.getAnimatedValue());
        });
        anim.start();
    }

    private void showMicPermissionDialog() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Microphone Permission")
                .setMessage("Allow FocusFlow to use the microphone so you can add tasks by voice.")
                .setPositiveButton("Allow", (d, w) ->
                        micPermLauncher.launch(Manifest.permission.RECORD_AUDIO))
                .setNegativeButton("Not now", null)
                .show();
    }

    // ══════════════════════════════════════════════════════
    //   SEND BUTTON ANIMATIONS — untouched
    // ══════════════════════════════════════════════════════

    private void animateSendButtonState(boolean active) {
        if (cardSendBtn == null || btnSendAI == null) return;
        int fromCard   = active ? Color.parseColor("#1A1A3A") : Color.parseColor("#4263EB");
        int toCard     = active ? Color.parseColor("#4263EB") : Color.parseColor("#1A1A3A");
        int fromIcon   = active ? Color.parseColor("#445577") : Color.parseColor("#FFFFFF");
        int toIcon     = active ? Color.parseColor("#FFFFFF") : Color.parseColor("#445577");
        int fromStroke = active ? Color.parseColor("#2A3A7E") : Color.parseColor("#4263EB");
        int toStroke   = active ? Color.parseColor("#4263EB") : Color.parseColor("#2A3A7E");

        ValueAnimator cardAnim = ValueAnimator.ofObject(new ArgbEvaluator(), fromCard, toCard);
        cardAnim.setDuration(250);
        cardAnim.addUpdateListener(a -> {
            if (isAdded()) cardSendBtn.setCardBackgroundColor((int) a.getAnimatedValue());
        });
        cardAnim.start();

        ValueAnimator iconAnim = ValueAnimator.ofObject(new ArgbEvaluator(), fromIcon, toIcon);
        iconAnim.setDuration(250);
        iconAnim.addUpdateListener(a -> {
            if (isAdded()) btnSendAI.setColorFilter((int) a.getAnimatedValue());
        });
        iconAnim.start();

        ValueAnimator strokeAnim = ValueAnimator.ofObject(new ArgbEvaluator(), fromStroke, toStroke);
        strokeAnim.setDuration(250);
        strokeAnim.addUpdateListener(a -> {
            if (isAdded()) cardSendBtn.setStrokeColor((int) a.getAnimatedValue());
        });
        strokeAnim.start();

        if (active) {
            cardSendBtn.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120)
                    .withEndAction(() -> {
                        if (isAdded())
                            cardSendBtn.animate().scaleX(1f).scaleY(1f).setDuration(150)
                                    .setInterpolator(new OvershootInterpolator(3f)).start();
                    }).start();
        }
    }

    private void animateSendButtonPress() {
        if (cardSendBtn == null) return;
        cardSendBtn.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                .withEndAction(() -> {
                    if (isAdded())
                        cardSendBtn.animate().scaleX(1f).scaleY(1f).setDuration(200)
                                .setInterpolator(new OvershootInterpolator(4f)).start();
                }).start();
    }

    // ══════════════════════════════════════════════════════
    //   CHAT PERSISTENCE — save & load
    // ══════════════════════════════════════════════════════

    private void saveChatHistory() {
        if (!isAdded()) return;
        try {
            JSONArray arr = new JSONArray();
            int start = Math.max(0, messages.size() - MAX_SAVED);
            for (int i = start; i < messages.size(); i++) {
                AIEngine.AIMessage msg = messages.get(i);
                JSONObject obj = new JSONObject();
                obj.put("text",   msg.text);
                obj.put("isUser", msg.isUser);
                if (msg.suggestions != null && !msg.suggestions.isEmpty()) {
                    JSONArray chips = new JSONArray();
                    for (String s : msg.suggestions) chips.put(s);
                    obj.put("suggestions", chips);
                }
                arr.put(obj);
            }
            requireContext()
                    .getSharedPreferences(PREFS_CHAT, Context.MODE_PRIVATE)
                    .edit().putString(KEY_MSGS, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveChatHistory: " + e.getMessage());
        }
    }

    private boolean loadChatHistory() {
        if (!isAdded()) return false;
        try {
            String json = requireContext()
                    .getSharedPreferences(PREFS_CHAT, Context.MODE_PRIVATE)
                    .getString(KEY_MSGS, null);
            if (json == null || json.isEmpty()) return false;
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) return false;
            messages.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String text    = obj.getString("text");
                boolean isUser = obj.getBoolean("isUser");
                List<String> chips = new ArrayList<>();
                if (obj.has("suggestions")) {
                    JSONArray ca = obj.getJSONArray("suggestions");
                    for (int j = 0; j < ca.length(); j++) chips.add(ca.getString(j));
                }
                messages.add(new AIEngine.AIMessage(text, isUser, chips));
            }
            chatAdapter.notifyDataSetChanged();
            Log.d(TAG, "Restored " + messages.size() + " messages");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "loadChatHistory: " + e.getMessage());
            return false;
        }
    }

    // ══════════════════════════════════════════════════════
    //   KEYBOARD — untouched from working version
    // ══════════════════════════════════════════════════════

    private void showKeyboard(View target) {
        if (!isAdded() || target == null) return;
        target.post(() -> {
            if (!isAdded()) return;
            try {
                WindowInsetsControllerCompat controller =
                        ViewCompat.getWindowInsetsController(target);
                if (controller != null) {
                    controller.show(WindowInsetsCompat.Type.ime());
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "WindowInsetsController failed: " + e.getMessage());
            }
            target.postDelayed(() -> {
                if (!isAdded()) return;
                try {
                    InputMethodManager imm = (InputMethodManager)
                            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null)
                        imm.showSoftInput(target, InputMethodManager.SHOW_FORCED);
                } catch (Exception e) {
                    Log.e(TAG, "IMM fallback: " + e.getMessage());
                }
            }, 150);
        });
    }

    private void hideKeyboard() {
        if (!isAdded() || etAIInput == null) return;
        try {
            WindowInsetsControllerCompat c =
                    ViewCompat.getWindowInsetsController(etAIInput);
            if (c != null) { c.hide(WindowInsetsCompat.Type.ime()); return; }
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etAIInput.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════
    //   LIFECYCLE
    // ══════════════════════════════════════════════════════

    @Override
    public void onResume() {
        super.onResume();
        updateStatusBadge();
        loadUserName();
        updateHeader();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveChatHistory();
        if (isListening) stopVoiceListening();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // ── Destroy SpeechRecognizer to prevent memory leak ──
        if (micPulseAnimator != null) {
            micPulseAnimator.cancel();
            micPulseAnimator = null;
        }
        if (speechRecognizer != null) {
            try {
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        isListening = false;
    }

    // ══════════════════════════════════════════════════════
    //   HEADER
    // ══════════════════════════════════════════════════════

    private void loadUserName() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String raw = prefs.getString("user_name", "").trim();
        userName = raw.isEmpty() ? "" : raw.split("\\s+")[0];
    }

    private void updateHeader() {
        if (tvAIHeader == null) return;
        tvAIHeader.setText(userName.isEmpty()
                ? getGreeting() + " 👋"
                : getGreeting() + ", " + userName + " 👋");
    }

    // ══════════════════════════════════════════════════════
    //   WELCOME MESSAGE
    // ══════════════════════════════════════════════════════

    private void showPersonalWelcome() {
        if (!isAdded()) return;
        int level   = XPManager.getLevel(requireContext());
        String rank = XPManager.getTitle(requireContext());
        String badge= XPManager.getBadge(requireContext());
        int streak  = XPManager.getStreak(requireContext());
        int xp      = XPManager.getTotalXP(requireContext());
        int nextXP  = XPManager.getXPForNextLevel(requireContext());

        int total = 0, completed = 0, pending = 0;
        for (ActionItem item : currentItems) {
            if (item.type == null || item.type.equals("history_routine")) continue;
            total++;
            if (item.isCompleted) completed++;
            if (item.isPending)   pending++;
        }

        StringBuilder msg = new StringBuilder();
        if (userName.isEmpty()) {
            msg.append(getGreeting()).append("! ").append(badge).append("\n\n");
        } else {
            msg.append(getGreeting()).append(", **").append(userName)
                    .append("**! ").append(badge).append("\n\n");
        }
        msg.append("You're **Level ").append(level).append(" — ").append(rank).append("**");
        if (streak > 1) msg.append(" with a **").append(streak).append("-day streak** 🔥");
        msg.append(".\n");

        if (total == 0) {
            msg.append("Your schedule is clear — tell me to add a task!");
        } else if (completed == total) {
            msg.append("🎉 All **").append(total).append(" tasks** done — incredible!");
        } else {
            int rem = total - completed;
            msg.append("**").append(rem).append(" task")
                    .append(rem > 1 ? "s" : "").append("** remaining today.");
            if (pending > 0) msg.append(" ").append(pending).append(" pending.");
        }

        int needed = Math.max(0, nextXP - xp);
        if (needed > 0 && needed < 80)
            msg.append("\n\nOnly **").append(needed).append(" XP** to your next rank!");

        msg.append("\n\nTry typing or tap 🎤 and say: **\"Add task meeting at 5pm\"**");

        List<String> chips = buildContextChips(total, completed, pending);
        addMessage(new AIEngine.AIMessage(msg.toString(), false, chips));
        showSuggestionChips(chips);
        saveChatHistory();
    }

    private List<String> buildContextChips(int total, int completed, int pending) {
        List<String> chips = new ArrayList<>();
        if (total == 0)             chips.add("Help me plan my day");
        else if (pending > 0)       chips.add("Help with pending tasks");
        else if (completed < total) chips.add("What should I do next?");
        else                        chips.add("Suggest something productive");
        chips.add("Add task for me");
        chips.add("Give me a productivity tip");
        return chips;
    }

    // ══════════════════════════════════════════════════════
    //   SEND MESSAGE
    // ══════════════════════════════════════════════════════

    private void sendMessage() {
        if (etAIInput == null) return;
        String text = etAIInput.getText().toString().trim();
        if (text.isEmpty()) return;

        etAIInput.setText("");
        hideSuggestionChips();
        hideKeyboard();

        addMessage(new AIEngine.AIMessage(text, true));
        saveChatHistory();
        showTyping(true);

        final List<AIEngine.AIMessage> historySnap = new ArrayList<>(messages);
        final List<ActionItem> itemsSnap           = new ArrayList<>(currentItems);
        final String query                         = text;

        GroqService.ask(requireContext(), historySnap, query, itemsSnap,
                result -> {
                    if (!isAdded()) return;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        showTyping(false);
                        updateStatusBadge();
                        if (result.wasOnline) {
                            if (result.actionType.equals(GroqService.ACTION_CREATE_TASK)
                                    || result.actionType.equals(GroqService.ACTION_CREATE_ROUTINE)) {
                                executeAIAction(result);
                            } else {
                                addMessage(new AIEngine.AIMessage(result.text, false));
                                showSuggestionChips(getDynamicChips(query));
                                saveChatHistory();
                            }
                        } else {
                            AIEngine.AIMessage offline =
                                    AIEngine.chat(requireContext(), query, itemsSnap);
                            addMessage(new AIEngine.AIMessage(
                                    offline.text, false, offline.suggestions));
                            if (!offline.suggestions.isEmpty())
                                showSuggestionChips(offline.suggestions);
                            saveChatHistory();
                        }
                    });
                });
    }

    // ══════════════════════════════════════════════════════
    //   AI ACTION ENGINE
    // ══════════════════════════════════════════════════════

    private void executeAIAction(GroqService.GroqResult result) {
        if (result.actionData == null || !isAdded()) return;
        try {
            JSONObject data = result.actionData;
            String action   = data.optString("action", "");
            String title    = data.optString("title", "New Task").trim();
            int hour        = data.optInt("hour", 9);
            int minute      = data.optInt("minute", 0);
            String timeStr  = String.format("%02d:%02d", hour, minute);
            String desc     = data.optString("description", "");

            if (!title.isEmpty())
                title = Character.toUpperCase(title.charAt(0)) + title.substring(1);

            ActionItem item  = new ActionItem();
            item.title       = title;
            item.timeString  = timeStr;
            item.hour        = hour;
            item.minute      = minute;
            item.description = desc;
            item.isCompleted = false;
            item.isPending   = false;
            item.category    = "none";

            if (action.equals(GroqService.ACTION_CREATE_ROUTINE)) {
                item.type       = "routines";
                item.repeatMode = "daily";
                item.year       = 0; item.month = 0; item.day = 0;
            } else {
                item.type       = "tasks";
                item.repeatMode = "none";
                Calendar cal    = Calendar.getInstance();
                item.year       = data.optInt("year",  cal.get(Calendar.YEAR));
                item.month      = data.optInt("month", cal.get(Calendar.MONTH) + 1) - 1;
                item.day        = data.optInt("day",   cal.get(Calendar.DAY_OF_MONTH));
            }

            final ActionItem finalItem = item;
            final String confirmMsg    = result.text;

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    FocusDatabase db = FocusDatabase.getInstance(requireContext());
                    db.actionDao().insert(finalItem);
                    ActionItem inserted = db.actionDao().getTaskByTitle(finalItem.title);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        scheduleAlarmForItem(inserted != null ? inserted : finalItem);
                        addMessage(new AIEngine.AIMessage(confirmMsg, false));
                        showSuggestionChips(list("Add another task",
                                "Show today's tasks", "Give me a tip"));
                        saveChatHistory();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "DB insert: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!isAdded()) return;
                        addMessage(new AIEngine.AIMessage(
                                "⚠️ Couldn't save that — please try again.", false));
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "executeAIAction: " + e.getMessage());
            addMessage(new AIEngine.AIMessage(result.text, false));
        }
    }

    // ══════════════════════════════════════════════════════
    //   SCHEDULE ALARM
    // ══════════════════════════════════════════════════════

    private void scheduleAlarmForItem(ActionItem item) {
        if (!isAdded()) return;
        try {
            Context ctx = requireContext();
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, item.hour);
            cal.set(Calendar.MINUTE,      item.minute);
            cal.set(Calendar.SECOND,      0);
            cal.set(Calendar.MILLISECOND, 0);

            if (item.type.equals("tasks") && item.year > 0) {
                cal.set(Calendar.YEAR,         item.year);
                cal.set(Calendar.MONTH,        item.month);
                cal.set(Calendar.DAY_OF_MONTH, item.day);
            } else {
                if (cal.getTimeInMillis() <= System.currentTimeMillis())
                    cal.add(Calendar.DAY_OF_MONTH, 1);
            }

            if (cal.getTimeInMillis() <= System.currentTimeMillis()) return;

            Intent intent = new Intent(ctx, AlarmReceiver.class);
            intent.setAction("com.example.thiru.ALARM_TRIGGER");
            intent.putExtra("item_id",    item.id);
            intent.putExtra("item_title", item.title);
            intent.putExtra("item_type",  item.type);

            PendingIntent pi = PendingIntent.getBroadcast(ctx, item.id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms())
                    am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
                else
                    am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } catch (Exception e) {
            Log.e(TAG, "scheduleAlarm: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    //   STATUS BADGE
    // ══════════════════════════════════════════════════════

    private void updateStatusBadge() {
        if (tvOnlineStatus == null || !isAdded()) return;
        boolean online = GroqService.isOnline(requireContext());
        tvOnlineStatus.setText(online ? "● Enhanced" : "● Offline");
        tvOnlineStatus.setTextColor(Color.parseColor(online ? "#20C997" : "#556688"));
        if (tvAISubtitle != null)
            tvAISubtitle.setText(online
                    ? "AI-powered · personalised to your schedule"
                    : "Offline mode · connect for smarter responses");
    }

    // ══════════════════════════════════════════════════════
    //   QUICK CHIPS
    // ══════════════════════════════════════════════════════

    private void buildQuickChips() {
        String[][] actions = {
                {"🎯", "What should I focus on right now?"},
                {"🎤", "Add task for me by voice"},
                {"📊", "How am I doing this week?"},
                {"💡", "Give me a productivity tip"},
                {"⏰", "Best time to do deep work?"},
                {"🔥", "Motivate me"}
        };
        for (int i = 0; i < actions.length; i++) {
            String query   = actions[i][1];
            String display = actions[i][0] + "  " + actions[i][1];
            MaterialCardView chip = buildChip(display, "#0D0D22", "#2A3A7E");
            final int delay = i * 60;
            chip.setAlpha(0f);
            chip.setTranslationY(20f);
            chip.animate().alpha(1f).translationY(0f)
                    .setStartDelay(delay).setDuration(300)
                    .setInterpolator(new OvershootInterpolator(1.5f)).start();
            chip.setOnClickListener(v -> {
                if (etAIInput != null) etAIInput.setText(query);
                sendMessage();
            });
            layoutQuickActions.addView(chip);
        }
    }

    // ══════════════════════════════════════════════════════
    //   SUGGESTION CHIPS
    // ══════════════════════════════════════════════════════

    private void showSuggestionChips(List<String> chips) {
        if (!isAdded() || chips == null || chips.isEmpty()) return;
        layoutSuggestionChips.removeAllViews();
        for (int i = 0; i < chips.size(); i++) {
            String s = chips.get(i);
            MaterialCardView chip = buildChip(s, "#0A1232", "#4263EB");
            final int delay = i * 50;
            chip.setAlpha(0f);
            chip.setTranslationX(30f);
            chip.animate().alpha(1f).translationX(0f)
                    .setStartDelay(delay).setDuration(250)
                    .setInterpolator(new OvershootInterpolator(1.2f)).start();
            chip.setOnClickListener(v -> {
                if (etAIInput != null) etAIInput.setText(s);
                sendMessage();
            });
            layoutSuggestionChips.addView(chip);
        }
        scrollSuggestionChips.setVisibility(View.VISIBLE);
        scrollSuggestionChips.setAlpha(0f);
        scrollSuggestionChips.animate().alpha(1f).setDuration(200).start();
    }

    private void hideSuggestionChips() {
        if (scrollSuggestionChips != null)
            scrollSuggestionChips.setVisibility(View.GONE);
        if (layoutSuggestionChips != null)
            layoutSuggestionChips.removeAllViews();
    }

    private List<String> getDynamicChips(String q) {
        q = q.toLowerCase();
        if (q.contains("tip") || q.contains("produc"))
            return list("Best time to work?", "Build a habit?", "How am I doing?");
        if (q.contains("habit") || q.contains("routine"))
            return list("Morning routine ideas", "How to stay consistent?", "Motivate me");
        if (q.contains("motiv") || q.contains("inspir"))
            return list("What to do next?", "My progress this week", "Tip please");
        if (q.contains("focus") || q.contains("study") || q.contains("work"))
            return list("Best focus technique?", "Remove distractions?", "Plan my day");
        if (q.contains("add") || q.contains("create") || q.contains("schedule"))
            return list("Add another task", "Show today's tasks", "Create a routine");
        return list("Tell me more", "Another tip", "How am I doing?");
    }

    // ══════════════════════════════════════════════════════
    //   HELPERS
    // ══════════════════════════════════════════════════════

    private void addMessage(AIEngine.AIMessage msg) {
        if (!isAdded()) return;
        messages.add(msg);
        int pos = messages.size() - 1;
        chatAdapter.notifyItemInserted(pos);
        rvChat.post(() -> {
            if (isAdded()) rvChat.smoothScrollToPosition(pos);
        });
    }

    private void showTyping(boolean show) {
        if (layoutTypingIndicator == null || !isAdded()) return;
        if (show) {
            layoutTypingIndicator.setAlpha(0f);
            layoutTypingIndicator.setVisibility(View.VISIBLE);
            layoutTypingIndicator.animate().alpha(1f).setDuration(200).start();
        } else {
            layoutTypingIndicator.animate().alpha(0f).setDuration(150)
                    .withEndAction(() -> {
                        if (isAdded()) layoutTypingIndicator.setVisibility(View.GONE);
                    }).start();
        }
        if (show) rvChat.post(() -> {
            if (isAdded()) rvChat.smoothScrollToPosition(messages.size() - 1);
        });
    }

    private String getGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 12) return "Good morning";
        if (h < 17) return "Good afternoon";
        if (h < 21) return "Good evening";
        return "Good night";
    }

    private String getSubtitle() {
        if (!isAdded()) return "AI-powered · personalised to your schedule";
        return GroqService.isOnline(requireContext())
                ? "AI-powered · personalised to your schedule"
                : "Offline mode · connect for smarter responses";
    }

    private MaterialCardView buildChip(String text, String bg, String stroke) {
        MaterialCardView chip = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dpToPx(8), 0);
        chip.setLayoutParams(p);
        chip.setCardBackgroundColor(Color.parseColor(bg));
        chip.setRadius(dpToPx(20));
        chip.setCardElevation(0f);
        chip.setStrokeWidth(dpToPx(1));
        chip.setStrokeColor(Color.parseColor(stroke));
        chip.setClickable(true);
        chip.setFocusable(true);

        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#8899CC"));
        tv.setTextSize(12f);
        tv.setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8));
        chip.addView(tv);

        chip.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN)
                v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start();
            else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                        .setInterpolator(new OvershootInterpolator(2.5f)).start();
            return false;
        });
        return chip;
    }

    private List<String> list(String... items) {
        List<String> l = new ArrayList<>();
        for (String s : items) l.add(s);
        return l;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}