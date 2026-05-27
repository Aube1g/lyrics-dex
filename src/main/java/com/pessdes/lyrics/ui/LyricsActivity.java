package com.pessdes.lyrics.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.RenderScript;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced Lyrics Activity с визуальными эффектами:
 * - Blur при скролле
 * - Glow для активной строки
 * - Scale анимация
 * - Fade in/out
 * - Градиентный текст
 * - Авто-скролл для несинхронизированных текстов
 */
public class LyricsActivity extends Activity {

    private static final int ANIMATION_DURATION = 300;
    private static final float ACTIVE_SCALE = 1.15f;
    private static final float BLUR_THRESHOLD = 50f; // pixels/sec

    private FrameLayout container;
    private ScrollView scrollView;
    private LinearLayout lyricsContainer;
    private ActionBar actionBar;

    private List<LyricsLine> lyricsLines = new ArrayList<>();
    private List<EnhancedLyricsTextView> textViews = new ArrayList<>();

    private int currentLine = -1;
    private boolean isSynced = true;
    private boolean autoScrollEnabled = false;
    private long trackDuration = 0;
    private long startTime = 0;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    private VisualEffectsManager effectsManager;
    private Typeface customTypeface;

    // Цвета
    private int activeColor = 0xFFFFFFFF;
    private int inactiveColor = 0x80FFFFFF;
    private int glowColor = 0xFF6C5CE7;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Настройка UI
        setupUI();

        // Инициализация эффектов
        effectsManager = new VisualEffectsManager(this);

        // Обработка скролла
        setupScrollListener();
    }

    private void setupUI() {
        container = new FrameLayout(this);

        // ScrollView для текста
        scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        lyricsContainer = new LinearLayout(this);
        lyricsContainer.setOrientation(LinearLayout.VERTICAL);
        lyricsContainer.setPadding(
            AndroidUtilities.dp(16),
            AndroidUtilities.dp(80),
            AndroidUtilities.dp(16),
            AndroidUtilities.dp(200)
        );

        scrollView.addView(lyricsContainer);
        container.addView(scrollView);

        // ActionBar
        actionBar = new ActionBar(this);
        actionBar.setBackgroundColor(0xCC000000);
        actionBar.setTitle("Lyrics");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finish();
                }
            }
        });
        container.addView(actionBar);

        setContentView(container);
    }

    private void setupScrollListener() {
        scrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            private int lastScrollY = 0;
            private long lastTime = System.currentTimeMillis();

            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                long now = System.currentTimeMillis();
                float dt = (now - lastTime) / 1000f;
                float velocity = Math.abs(scrollY - lastScrollY) / dt;

                // Применяем blur на основе скорости
                if (velocity > BLUR_THRESHOLD) {
                    applyBlurToVisibleItems(velocity);
                } else {
                    clearBlur();
                }

                lastScrollY = scrollY;
                lastTime = now;
            }
        });
    }

    /**
     * Устанавливает текст песни
     * @param plainLyrics Обычный текст
     * @param syncedLyrics Синхронизированный текст (LRC формат) или null для авто-скролла
     * @param duration Длительность трека в секундах
     */
    public void setLyrics(String plainLyrics, String syncedLyrics, long duration) {
        this.trackDuration = duration * 1000; // в ms
        this.isSynced = syncedLyrics != null && !syncedLyrics.isEmpty();

        lyricsContainer.removeAllViews();
        lyricsLines.clear();
        textViews.clear();

        if (isSynced) {
            parseSyncedLyrics(syncedLyrics);
        } else {
            parsePlainLyrics(plainLyrics, duration);
            autoScrollEnabled = true;
        }

        // Создаём TextView для каждой строки
        for (int i = 0; i < lyricsLines.size(); i++) {
            LyricsLine line = lyricsLines.get(i);
            EnhancedLyricsTextView textView = createLyricsTextView(line.text, i);
            lyricsContainer.addView(textView);
            textViews.add(textView);
        }

        // Запускаем обновление
        startUpdateLoop();
    }

    private EnhancedLyricsTextView createLyricsTextView(String text, int index) {
        EnhancedLyricsTextView textView = new EnhancedLyricsTextView(this);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        textView.setLayoutParams(params);

        textView.setText(text);
        textView.setTextSize(18);
        textView.setTextColor(inactiveColor);
        textView.setGravity(android.view.Gravity.CENTER);

        if (customTypeface != null) {
            textView.setTypeface(customTypeface);
        }

        // Настройка эффектов
        textView.setBlurEnabled(true);
        textView.setBlurRadius(8f);
        textView.setGlowEnabled(true);
        textView.setGlowColor(glowColor);
        textView.setGlowRadius(12f);
        textView.setTargetScale(ACTIVE_SCALE);
        textView.setActiveLineColor(activeColor);
        textView.setInactiveLineColor(inactiveColor);

        return textView;
    }

    private void parseSyncedLyrics(String lrc) {
        Pattern pattern = Pattern.compile("\[(\d{2}):\d{2}\.(\d{2,3})\](.*)");
        Matcher matcher = pattern.matcher(lrc);

        while (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            String text = matcher.group(3).trim();

            long timeMs = (minutes * 60 + seconds) * 1000L;
            lyricsLines.add(new LyricsLine(text, timeMs));
        }
    }

    private void parsePlainLyrics(String plain, long duration) {
        String[] lines = plain.split("\n");
        long interval = (duration * 1000) / Math.max(lines.length, 1);

        for (int i = 0; i < lines.length; i++) {
            String text = lines[i].trim();
            if (!text.isEmpty()) {
                long timeMs = i * interval;
                lyricsLines.add(new LyricsLine(text, timeMs));
            }
        }
    }

    private void startUpdateLoop() {
        startTime = System.currentTimeMillis();

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentLine();
                handler.postDelayed(this, 50); // 20fps
            }
        };

        handler.post(updateRunnable);
    }

    private void updateCurrentLine() {
        long currentTime = System.currentTimeMillis() - startTime;

        // Находим текущую строку
        int newLine = -1;
        for (int i = lyricsLines.size() - 1; i >= 0; i--) {
            if (lyricsLines.get(i).time <= currentTime) {
                newLine = i;
                break;
            }
        }

        if (newLine != currentLine && newLine >= 0) {
            // Сбрасываем предыдущую активную строку
            if (currentLine >= 0 && currentLine < textViews.size()) {
                animateLineInactive(textViews.get(currentLine));
            }

            // Активируем новую строку
            currentLine = newLine;
            EnhancedLyricsTextView activeView = textViews.get(currentLine);
            animateLineActive(activeView);

            // Авто-скролл к активной строке
            scrollToLine(currentLine);
        }
    }

    private void animateLineActive(EnhancedLyricsTextView textView) {
        textView.setActiveLine(true);
        textView.animate()
            .scaleX(ACTIVE_SCALE)
            .scaleY(ACTIVE_SCALE)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();

        // Glow эффект
        textView.setGlowEnabled(true);

        // Градиентный текст
        textView.setTextColor(activeColor);
    }

    private void animateLineInactive(EnhancedLyricsTextView textView) {
        textView.setActiveLine(false);
        textView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();

        textView.setGlowEnabled(false);
        textView.setTextColor(inactiveColor);
    }

    private void scrollToLine(int lineIndex) {
        if (lineIndex < 0 || lineIndex >= textViews.size()) return;

        View targetView = textViews.get(lineIndex);
        int targetY = targetView.getTop() - scrollView.getHeight() / 2 + targetView.getHeight() / 2;

        ValueAnimator animator = ValueAnimator.ofInt(scrollView.getScrollY(), targetY);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            scrollView.scrollTo(0, (int) animation.getAnimatedValue());
        });
        animator.start();
    }

    private void applyBlurToVisibleItems(float velocity) {
        float blurAmount = Math.min(velocity / 200f, 1.0f) * 8f;

        for (EnhancedLyricsTextView textView : textViews) {
            if (isViewVisible(textView)) {
                textView.setBlurRadius(blurAmount);
            }
        }
    }

    private void clearBlur() {
        for (EnhancedLyricsTextView textView : textViews) {
            textView.setBlurRadius(0f);
        }
    }

    private boolean isViewVisible(View view) {
        int scrollY = scrollView.getScrollY();
        int viewTop = view.getTop();
        int viewBottom = view.getBottom();
        int scrollBottom = scrollY + scrollView.getHeight();

        return viewBottom >= scrollY && viewTop <= scrollBottom;
    }

    public void setTypeface(Typeface typeface) {
        this.customTypeface = typeface;
        for (EnhancedLyricsTextView textView : textViews) {
            textView.setTypeface(typeface);
        }
    }

    public void setGlowColor(int color) {
        this.glowColor = color;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
        if (effectsManager != null) {
            effectsManager.release();
        }
    }

    private static class LyricsLine {
        String text;
        long time;

        LyricsLine(String text, long time) {
            this.text = text;
            this.time = time;
        }
    }
}
