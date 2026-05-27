package com.pessdes.lyrics.ui;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;

import java.util.List;

/**
 * RecyclerView Adapter для lyrics с визуальными эффектами
 * Более производительный чем ScrollView для больших текстов
 */
public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.LyricsViewHolder> {

    private List<String> lyricsLines;
    private int activePosition = -1;
    private Typeface typeface;

    private boolean blurEnabled = true;
    private boolean glowEnabled = true;
    private float blurRadius = 8f;
    private float glowRadius = 12f;
    private int glowColor = 0xFF6C5CE7;
    private float activeScale = 1.15f;

    public LyricsAdapter(List<String> lyricsLines) {
        this.lyricsLines = lyricsLines;
    }

    @NonNull
    @Override
    public LyricsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        EnhancedLyricsTextView textView = new EnhancedLyricsTextView(parent.getContext());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        textView.setLayoutParams(params);

        textView.setTextSize(18);
        textView.setGravity(android.view.Gravity.CENTER);

        if (typeface != null) {
            textView.setTypeface(typeface);
        }

        // Настройка эффектов
        textView.setBlurEnabled(blurEnabled);
        textView.setBlurRadius(blurRadius);
        textView.setGlowEnabled(glowEnabled);
        textView.setGlowColor(glowColor);
        textView.setGlowRadius(glowRadius);
        textView.setTargetScale(activeScale);

        return new LyricsViewHolder(textView);
    }

    @Override
    public void onBindViewHolder(@NonNull LyricsViewHolder holder, int position) {
        String text = lyricsLines.get(position);
        holder.textView.setText(text);

        boolean isActive = position == activePosition;
        holder.textView.setActiveLine(isActive);

        if (isActive) {
            holder.textView.setTextColor(0xFFFFFFFF);
            holder.textView.setGlowEnabled(glowEnabled);
        } else {
            holder.textView.setTextColor(0x80FFFFFF);
            holder.textView.setGlowEnabled(false);
        }
    }

    @Override
    public int getItemCount() {
        return lyricsLines != null ? lyricsLines.size() : 0;
    }

    public void setActivePosition(int position) {
        if (activePosition == position) return;

        int oldPosition = activePosition;
        activePosition = position;

        if (oldPosition >= 0 && oldPosition < getItemCount()) {
            notifyItemChanged(oldPosition);
        }
        if (position >= 0 && position < getItemCount()) {
            notifyItemChanged(position);
        }
    }

    public void setTypeface(Typeface typeface) {
        this.typeface = typeface;
        notifyDataSetChanged();
    }

    public void setBlurEnabled(boolean enabled) {
        this.blurEnabled = enabled;
        notifyDataSetChanged();
    }

    public void setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
        notifyDataSetChanged();
    }

    public void setBlurRadius(float radius) {
        this.blurRadius = radius;
        notifyDataSetChanged();
    }

    public void setGlowRadius(float radius) {
        this.glowRadius = radius;
        notifyDataSetChanged();
    }

    public void setGlowColor(int color) {
        this.glowColor = color;
        notifyDataSetChanged();
    }

    public void setActiveScale(float scale) {
        this.activeScale = scale;
        notifyDataSetChanged();
    }

    static class LyricsViewHolder extends RecyclerView.ViewHolder {
        EnhancedLyricsTextView textView;

        LyricsViewHolder(View itemView) {
            super(itemView);
            this.textView = (EnhancedLyricsTextView) itemView;
        }
    }
}
