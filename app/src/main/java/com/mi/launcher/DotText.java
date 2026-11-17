package com.mi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

public class DotText extends TextView {
    float cellPx = 6f;
    float used = 6f;
    final Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DotText(Context c) {
        super(c);
    }

    public void setCellPx(float c) {
        cellPx = c;
        requestLayout();
        invalidate();
    }

    @Override
    public void setText(CharSequence t, BufferType type) {
        super.setText(t, type);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        String s = String.valueOf(getText());
        int cols = Math.max(1, DotFont.cols(s));
        int padW = getPaddingLeft() + getPaddingRight();
        int mode = View.MeasureSpec.getMode(wSpec);
        int avail = View.MeasureSpec.getSize(wSpec) - padW;
        float cell = cellPx;
        if (mode != View.MeasureSpec.UNSPECIFIED && avail > 0 && cols * cell > avail) cell = avail / (float) cols;
        used = cell;
        int rows = DotFont.hasAccent(s) ? 8 : 7;
        int w = (int) Math.ceil(cols * cell) + padW;
        if (mode == View.MeasureSpec.EXACTLY) w = View.MeasureSpec.getSize(wSpec);
        int h = (int) Math.ceil(rows * cell) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas cv) {
        String s = String.valueOf(getText());
        float tw = DotFont.cols(s) * used;
        float x = getPaddingLeft();
        float inner = getWidth() - getPaddingLeft() - getPaddingRight();
        int g = getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
        if (g == Gravity.CENTER_HORIZONTAL) x += (inner - tw) / 2f;
        else if (g == Gravity.RIGHT) x += inner - tw;
        float y = getPaddingTop() + (DotFont.hasAccent(s) ? used : 0f);
        DotFont.draw(cv, s, x, y, used, dp, getCurrentTextColor(), 0);
    }
}
