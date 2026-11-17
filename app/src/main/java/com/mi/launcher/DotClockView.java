package com.mi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

public class DotClockView extends View {
    static final String[] G = {
        "01110" + "10001" + "10011" + "10101" + "11001" + "10001" + "01110",
        "00100" + "01100" + "00100" + "00100" + "00100" + "00100" + "01110",
        "01110" + "10001" + "00001" + "00010" + "00100" + "01000" + "11111",
        "11110" + "00001" + "00001" + "01110" + "00001" + "00001" + "11110",
        "00010" + "00110" + "01010" + "10010" + "11111" + "00010" + "00010",
        "11111" + "10000" + "11110" + "00001" + "00001" + "10001" + "01110",
        "00110" + "01000" + "10000" + "11110" + "10001" + "10001" + "01110",
        "11111" + "00001" + "00010" + "00100" + "01000" + "01000" + "01000",
        "01110" + "10001" + "10001" + "01110" + "10001" + "10001" + "01110",
        "01110" + "10001" + "10001" + "01111" + "00001" + "00010" + "01100"
    };
    static final String COLON = "0010100";

    String text = "00:00";
    int cell = 24;
    int onColor = 0xFFFFFFFF;
    int offColor = 0x22FFFFFF;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DotClockView(Context c) { super(c); }

    public void setText(String t) {
        if (!t.equals(text)) {
            text = t;
            requestLayout();
            invalidate();
        }
    }

    public void setCell(int px) {
        cell = px;
        requestLayout();
        invalidate();
    }

    public void setColors(int on, int off) {
        onColor = on;
        offColor = off;
        invalidate();
    }

    int cols() {
        int c = 0;
        for (int i = 0; i < text.length(); i++) {
            c += text.charAt(i) == ':' ? 1 : 5;
            if (i < text.length() - 1) c += 1;
        }
        return c;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(cols() * cell, 7 * cell);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float r = cell * 0.36f;
        int x = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String g;
            int gw;
            if (ch == ':') {
                g = COLON;
                gw = 1;
            } else if (ch >= '0' && ch <= '9') {
                g = G[ch - '0'];
                gw = 5;
            } else {
                x += 6;
                continue;
            }
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < gw; col++) {
                    boolean lit = g.charAt(row * gw + col) == '1';
                    p.setColor(lit ? onColor : offColor);
                    cv.drawCircle((x + col + 0.5f) * cell, (row + 0.5f) * cell, r, p);
                }
            }
            x += gw + 1;
        }
    }
}
