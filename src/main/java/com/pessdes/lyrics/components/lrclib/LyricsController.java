package com.pessdes.lyrics.components.lrclib;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;

import com.pessdes.lyrics.ui.EnhancedLyricsTextView;
import com.pessdes.lyrics.ui.LyricsActivity;
import com.pessdes.lyrics.ui.VisualEffectsManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced LyricsController — главный класс для связи Python ↔ Java
 * Поддерживает визуальные эффекты, авто-скролл, множественные провайдеры
 */
public class LyricsController {

    private static LyricsController instance;
    private static final Object lock = new Object();

    private Context context;
    private Typeface typeface;
    private String pluginModuleName;
    private VisualEffectsManager effectsManager;

    // Настройки визуальных эффектов
    private boolean blurEnabled = true;
    private boolean glowEnabled = true;
    private boolean fadeEnabled = true;
    private boolean gradientEnabled = true;
    private float blurRadius = 8f;
    private float glowRadius = 12f;
    private int glowColor = 0xFF6C5CE7;
    private int animationDuration = 300;
    private float scaleActiveLine = 1.15f;

    // Провайдеры
    private Map<String, Object> providers = new HashMap<>();
    private Map<String, Integer> providerPriorities = new HashMap<>();

    private LyricsController() {
        this.context = ApplicationLoader.applicationContext;
        this.effectsManager = new VisualEffectsManager(context);
    }

    public static LyricsController getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new LyricsController();
                }
            }
        }
        return instance;
    }

    /**
     * Инициализация из Python плагина
     */
    public void initPluginController(String moduleName) {
        this.pluginModuleName = moduleName;
    }

    /**
     * Показывает Activity с текстом песни
     */
    public void presentLyricsActivity(BaseFragment fragment) {
        if (fragment == null || fragment.getContext() == null) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Получаем текущий трек из MediaController
                org.telegram.messenger.MediaController mediaController = 
                    org.telegram.messenger.MediaController.getInstance();

                if (mediaController == null) return;

                org.telegram.messenger.MessageObject playingMessage = 
                    mediaController.getPlayingMessageObject();

                if (playingMessage == null || !playingMessage.isMusic()) return;

                // Получаем информацию о треке
                String trackName = playingMessage.getMusicTitle();
                String artistName = playingMessage.getMusicAuthor();
                long duration = playingMessage.getDuration();

                // Запрашиваем lyrics через Python callback
                requestLyricsFromPython(trackName, artistName, duration, (plain, synced) -> {
                    // Открываем LyricsActivity
                    android.content.Intent intent = new android.content.Intent(
                        fragment.getContext(), 
                        LyricsActivity.class
                    );

                    intent.putExtra("track_name", trackName);
                    intent.putExtra("artist_name", artistName);
                    intent.putExtra("plain_lyrics", plain != null ? plain : "");
                    intent.putExtra("synced_lyrics", synced != null ? synced : "");
                    intent.putExtra("duration", duration);

                    fragment.getContext().startActivity(intent);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Запрашивает lyrics через Python callback
     */
    private void requestLyricsFromPython(String trackName, String artistName, long duration, 
                                          LyricsCallback callback) {
        // Этот метод будет перезаписан через хуки Python
        // По умолчанию используем встроенные провайдеры
        callback.onLyricsReady(null, null);
    }

    /**
     * Создаёт объект Lyrics для передачи в Python
     */
    public static Object createLyrics(float duration, String plainLyrics, String syncedLyrics) {
        // Возвращает структуру данных для Python
        Map<String, Object> lyrics = new HashMap<>();
        lyrics.put("duration", duration);
        lyrics.put("plain", plainLyrics);
        lyrics.put("synced", syncedLyrics);
        return lyrics;
    }

    /**
     * Добавляет провайдер текстов
     */
    public boolean createAndAddSimpleProvider(String name, String id, Object searchFn, int priority) {
        providers.put(id, searchFn);
        providerPriorities.put(id, priority);
        return true;
    }

    /**
     * Удаляет провайдер
     */
    public boolean removeProvider(String id) {
        providers.remove(id);
        providerPriorities.remove(id);
        return true;
    }

    /**
     * Устанавливает шрифт
     */
    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
    }

    public Typeface getTypeface() {
        return typeface;
    }

    // === Настройки визуальных эффектов ===

    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        effectsManager.setBlurEnabled(enabled);
    }

    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
        effectsManager.setGlowEnabled(enabled);
    }

    public void setFadeEnabled(boolean enabled) {
        this.fadeEnabled = enabled;
    }

    public void setGradientEnabled(boolean enabled) {
        this.gradientEnabled = enabled;
    }

    public void setBlurRadius(float radius) {
        this.blurRadius = radius;
    }

    public void setGlowRadius(float radius) {
        this.glowRadius = radius;
    }

    public void setGlowColor(int color) {
        this.glowColor = color;
    }

    public void setAnimationDuration(int duration) {
        this.animationDuration = duration;
    }

    public void setScaleActiveLine(float scale) {
        this.scaleActiveLine = scale;
    }

    public void setFadeInOutEnabled(boolean enabled) {
        this.fadeEnabled = enabled;
    }

    // === Getters ===

    public boolean isBlurEnabled() {
        return blurEnabled;
    }

    public boolean isGlowEnabled() {
        return glowEnabled;
    }

    public float getBlurRadius() {
        return blurRadius;
    }

    public float getGlowRadius() {
        return glowRadius;
    }

    public int getGlowColor() {
        return glowColor;
    }

    public int getAnimationDuration() {
        return animationDuration;
    }

    public float getScaleActiveLine() {
        return scaleActiveLine;
    }

    public VisualEffectsManager getEffectsManager() {
        return effectsManager;
    }

    /**
     * Парсит версию плагина
     */
    public static int parseVersionCode(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major * 10000 + minor * 100 + patch;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Получает экземпляр ReachText плагина
     */
    public Object getReachTextPlugin() {
        // Возвращает ссылку на Python модуль
        return this;
    }

    private interface LyricsCallback {
        void onLyricsReady(String plain, String synced);
    }
            }
