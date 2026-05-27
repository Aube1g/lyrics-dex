package com.pessdes.lyrics.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

/**
 * Менеджер визуальных эффектов для lyrics
 * - Blur эффект
 * - Glow/Shadow
 * - Fade animations
 * - Scale animations
 */
public class VisualEffectsManager {

    private Context context;
    private RenderScript renderScript;
    private ScriptIntrinsicBlur blurScript;

    private boolean blurEnabled = true;
    private boolean glowEnabled = true;
    private boolean animationsEnabled = true;

    private float defaultBlurRadius = 8f;
    private int defaultGlowColor = 0xFF6C5CE7;
    private float defaultGlowRadius = 12f;

    public VisualEffectsManager(Context context) {
        this.context = context;
        initRenderScript();
    }

    private void initRenderScript() {
        try {
            renderScript = RenderScript.create(context);
            blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        } catch (Exception e) {
            blurEnabled = false;
        }
    }

    /**
     * Применяет blur к Bitmap
     */
    public Bitmap applyBlur(Bitmap input, float radius) {
        if (!blurEnabled || renderScript == null || input == null) {
            return input;
        }

        try {
            Bitmap output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);

            Allocation inputAlloc = Allocation.createFromBitmap(renderScript, input);
            Allocation outputAlloc = Allocation.createFromBitmap(renderScript, output);

            blurScript.setRadius(Math.min(radius, 25f)); // Максимум 25
            blurScript.setInput(inputAlloc);
            blurScript.forEach(outputAlloc);

            outputAlloc.copyTo(output);

            inputAlloc.destroy();
            outputAlloc.destroy();

            return output;
        } catch (Exception e) {
            return input;
        }
    }

    /**
     * Создаёт glow эффект для текста
     */
    public Bitmap createGlowBitmap(String text, int textColor, int glowColor, float glowRadius, 
                                    int width, int height, float textSize) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Рисуем glow
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(glowColor);
        glowPaint.setTextSize(textSize);
        glowPaint.setTextAlign(Paint.Align.CENTER);
        glowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(glowRadius, 
            android.graphics.BlurMaskFilter.Blur.OUTER));

        float x = width / 2f;
        float y = height / 2f - ((glowPaint.descent() + glowPaint.ascent()) / 2f);

        canvas.drawText(text, x, y, glowPaint);

        // Рисуем основной текст
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.CENTER);

        canvas.drawText(text, x, y, textPaint);

        return bitmap;
    }

    /**
     * Анимация появления с fade + scale
     */
    public void animateFadeScaleIn(View view, long duration) {
        if (!animationsEnabled) {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }

        view.setAlpha(0f);
        view.setScaleX(0.8f);
        view.setScaleY(0.8f);

        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .start();
    }

    /**
     * Анимация исчезновения
     */
    public void animateFadeOut(View view, long duration, Runnable onEnd) {
        if (!animationsEnabled) {
            view.setAlpha(0f);
            if (onEnd != null) onEnd.run();
            return;
        }

        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction(onEnd)
            .start();
    }

    /**
     * Пульсация (для активной строки)
     */
    public void animatePulse(View view, long duration) {
        if (!animationsEnabled) return;

        ScaleAnimation pulse = new ScaleAnimation(
            1.0f, 1.05f, 1.0f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        );
        pulse.setDuration(duration / 2);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(1);
        pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        view.startAnimation(pulse);
    }

    /**
     * Создаёт bitmap с градиентным текстом
     */
    public Bitmap createGradientTextBitmap(String text, int[] colors, float[] positions,
                                            int width, int height, float textSize) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);

        // Градиент
        android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
            0, 0, width, 0,
            colors,
            positions,
            android.graphics.Shader.TileMode.CLAMP
        );
        paint.setShader(gradient);

        float x = width / 2f;
        float y = height / 2f - ((paint.descent() + paint.ascent()) / 2f);

        canvas.drawText(text, x, y, paint);

        return bitmap;
    }

    /**
     * Применяет blur к View (создаёт snapshot)
     */
    public void applyBlurToView(View view, float radius) {
        if (!blurEnabled) return;

        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache();
        Bitmap bitmap = view.getDrawingCache();

        if (bitmap != null) {
            Bitmap blurred = applyBlur(bitmap, radius);
            // Применяем размытый bitmap как background
            view.setBackground(new android.graphics.drawable.BitmapDrawable(view.getResources(), blurred));
        }

        view.setDrawingCacheEnabled(false);
    }

    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
    }

    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
    }

    public void setAnimationsEnabled(boolean enabled) {
        this.animationsEnabled = enabled;
    }

    public void release() {
        if (renderScript != null) {
            renderScript.destroy();
            renderScript = null;
        }
    }

    public boolean isBlurEnabled() {
        return blurEnabled;
    }

    public boolean isGlowEnabled() {
        return glowEnabled;
    }

    public boolean isAnimationsEnabled() {
        return animationsEnabled;
    }
}
