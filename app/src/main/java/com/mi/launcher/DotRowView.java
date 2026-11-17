package com.mi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class DotRowView extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    float f;
    int color = 0xFFFFFFFF;
    int n = 10;

    public DotRowView(Context c) { super(c); }

    public void set(float fr, int col) {
        fr = Math.max(0f, Math.min(1f, fr));
        if (Math.abs(fr - f) > 0.002f || col != color) {
            f = fr;
            color = col;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cell = w / n;
        float r = Math.min(cell * 0.32f, h / 2f);
        int on = Math.round(f * n);
        if (f > 0f && on == 0) on = 1;
        for (int i = 0; i < n; i++) {
            p.setColor(i < on ? color : 0x33FFFFFF);
            c.drawCircle((i + 0.5f) * cell, h / 2f, r, p);
        }
    }
}
