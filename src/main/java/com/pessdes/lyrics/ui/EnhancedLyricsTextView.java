package com.pessdes.lyrics.ui;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Enhanced LyricsTextView с визуальными эффектами:
 * - Blur при скролле
 * - Glow для активной строки
 * - Scale анимация
 * - Fade in/out
 * - Градиентный текст
 */
public class EnhancedLyricsTextView extends TextView {

    private boolean blurEnabled = true;
    private boolean glowEnabled = true;
    private boolean fadeEnabled = true;
    private boolean gradientEnabled = true;

    private float blurRadius = 8f;
    private float glowRadius = 12f;
    private int glowColor = 0xFF6C5CE7; // Фиолетовый

    private float targetScale = 1.15f;
    private float currentScale = 1.0f;
    private float targetAlpha = 1.0f;
    private float currentAlpha = 1.0f;

    private int activeLineColor = 0xFFFFFFFF;
    private int inactiveLineColor = 0x80FFFFFF;

    private Paint glowPaint;
    private Paint blurPaint;
    private RenderScript renderScript;
    private ScriptIntrinsicBlur blurScript;
    private Allocation inputAllocation;
    private Allocation outputAllocation;

    private Bitmap blurBuffer;
    private Canvas blurCanvas;

    private boolean isActiveLine = false;
    private float scrollVelocity = 0f;
    private long lastScrollTime = 0;

    private AccelerateDecelerateInterpolator interpolator = new AccelerateDecelerateInterpolator();

    public EnhancedLyricsTextView(Context context) {
        super(context);
        init();
    }

    public EnhancedLyricsTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EnhancedLyricsTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);

        blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blurPaint.setFilterBitmap(true);

        if (blurEnabled) {
            initRenderScript();
        }
    }

    private void initRenderScript() {
        try {
            renderScript = RenderScript.create(getContext());
            blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        } catch (Exception e) {
            blurEnabled = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Применяем scale анимацию
        if (isActiveLine) {
            currentScale = lerp(currentScale, targetScale, 0.15f);
            currentAlpha = lerp(currentAlpha, 1.0f, 0.1f);
        } else {
            currentScale = lerp(currentScale, 1.0f, 0.15f);
            currentAlpha = lerp(currentAlpha, 0.6f, 0.1f);
        }

        // Применяем blur на основе скорости скролла
        float velocityBlur = Math.min(scrollVelocity / 50f, 1.0f) * blurRadius;

        canvas.save();

        // Scale от центра
        float pivotX = getWidth() / 2f;
        float pivotY = getHeight() / 2f;
        canvas.scale(currentScale, currentScale, pivotX, pivotY);

        // Устанавливаем цвет
        if (isActiveLine) {
            setTextColor(activeLineColor);

            // Glow эффект
            if (glowEnabled) {
                drawGlow(canvas);
            }

            // Градиентный текст
            if (gradientEnabled) {
                applyGradientText();
            }
        } else {
            setTextColor(inactiveLineColor);
            setAlpha((int)(currentAlpha * 255));
        }

        // Blur при быстром скролле
        if (blurEnabled && velocityBlur > 0.5f) {
            drawWithBlur(canvas, velocityBlur);
        } else {
            super.onDraw(canvas);
        }

        canvas.restore();

        // Затухание скорости скролла
        scrollVelocity *= 0.9f;

        // Инвалидируем для анимации
        if (Math.abs(currentScale - (isActiveLine ? targetScale : 1.0f)) > 0.001f ||
            Math.abs(currentAlpha - (isActiveLine ? 1.0f : 0.6f)) > 0.001f) {
            postInvalidateOnAnimation();
        }
    }

    private void drawGlow(Canvas canvas) {
        // Создаём glow вокруг текста
        String text = getText().toString();
        if (text.isEmpty()) return;

        Paint glow = new Paint(glowPaint);
        glow.setColor(glowColor);
        glow.setMaskFilter(new BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.OUTER));
        glow.setAlpha(128);

        // Рисуем glow под текстом
        canvas.saveLayer(0, 0, getWidth(), getHeight(), null, Canvas.ALL_SAVE_FLAG);

        // Получаем bounds текста
        Rect textBounds = new Rect();
        getPaint().getTextBounds(text, 0, text.length(), textBounds);

        float x = getPaddingLeft();
        float y = getBaseline();

        canvas.drawText(text, x, y, glow);
        canvas.restore();
    }

    private void applyGradientText() {
        // Градиент от фиолетового к розовому для активной строки
        Shader textShader = new LinearGradient(
            0, 0, getWidth(), 0,
            new int[]{0xFF6C5CE7, 0xFFA29BFE, 0xFFFD79A8},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );
        getPaint().setShader(textShader);
    }

    private void drawWithBlur(Canvas canvas, float radius) {
        // Создаём bitmap для blur
        int width = getWidth();
        int height = getHeight();

        if (width <= 0 || height <= 0) return;

        if (blurBuffer == null || blurBuffer.getWidth() != width || blurBuffer.getHeight() != height) {
            blurBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            blurCanvas = new Canvas(blurBuffer);
        }

        // Рисуем текст в buffer
        blurBuffer.eraseColor(Color.TRANSPARENT);
        super.onDraw(blurCanvas);

        // Применяем blur через RenderScript
        if (renderScript != null && blurScript != null) {
            inputAllocation = Allocation.createFromBitmap(renderScript, blurBuffer);
            outputAllocation = Allocation.createTyped(renderScript, inputAllocation.getType());

            blurScript.setRadius(radius);
            blurScript.setInput(inputAllocation);
            blurScript.forEach(outputAllocation);

            outputAllocation.copyTo(blurBuffer);

            inputAllocation.destroy();
            outputAllocation.destroy();
        }

        // Рисуем размытый bitmap
        canvas.drawBitmap(blurBuffer, 0, 0, blurPaint);
    }

    private float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    // Public API для настройки эффектов

    public void setActiveLine(boolean active) {
        if (isActiveLine != active) {
            isActiveLine = active;
            invalidate();
        }
    }

    public void onScroll(float velocity) {
        scrollVelocity = Math.abs(velocity);
        lastScrollTime = System.currentTimeMillis();
    }

    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        if (enabled && renderScript == null) {
            initRenderScript();
        }
    }

    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
        invalidate();
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

    public void setTargetScale(float scale) {
        this.targetScale = scale;
    }

    public void setActiveLineColor(int color) {
        this.activeLineColor = color;
    }

    public void setInactiveLineColor(int color) {
        this.inactiveLineColor = color;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (renderScript != null) {
            renderScript.destroy();
        }
        if (blurBuffer != null) {
            blurBuffer.recycle();
        }
    }
}
