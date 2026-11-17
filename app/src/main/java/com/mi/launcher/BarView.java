package com.mi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class BarView extends View {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    float f;
    int color = 0xFFFFFFFF;
    int track = 0x33FFFFFF;

    public BarView(Context c) { super(c); }

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
        float r = h / 2f;
        p.setColor(track);
        c.drawRoundRect(0, 0, w, h, r, r, p);
        p.setColor(color);
        if (f > 0f) c.drawRoundRect(0, 0, Math.max(h, w * f), h, r, r, p);
    }
}
