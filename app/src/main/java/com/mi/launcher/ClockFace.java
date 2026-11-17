package com.mi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import java.util.Calendar;
import java.util.Locale;

public class ClockFace extends View {
    int kind;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            postDelayed(this, (kind == 5 || kind == 6) ? 1000 : 15000);
        }
    };

    static final String[] U = {"CERO", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS",
            "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    static final String[] T = {"TREINTA", "CUARENTA", "CINCUENTA"};

    public ClockFace(Context c, int kind) {
        super(c);
        this.kind = kind;
    }

    float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = View.MeasureSpec.getSize(wSpec);
        if (w <= 0) w = (int) dp(300);
        float h;
        switch (kind) {
            case 3: h = 176; break;
            case 4: h = 210; break;
            case 5: h = 230; break;
            case 6: h = 250; break;
            case 7: h = 170; break;
            default: h = 160; break;
        }
        setMeasuredDimension(w, (int) dp(h));
    }

    static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }

    static String word(int n) {
        if (n < 30) return U[n];
        String s = T[n / 10 - 3];
        if (n % 10 != 0) s += " Y " + U[n % 10];
        return s;
    }

    void hand(Canvas cv, float cx, float cy, float deg, float len, float sw, int color) {
        double a = Math.toRadians(deg - 90);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        p.setColor(color);
        cv.drawLine(cx, cy, cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len, p);
    }

    @Override
    protected void onDraw(Canvas cv) {
        Calendar cal = Calendar.getInstance();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(getContext());
        int hr = cal.get(Calendar.HOUR_OF_DAY);
        int mn = cal.get(Calendar.MINUTE);
        int sc = cal.get(Calendar.SECOND);
        int hd = is24 ? hr : (hr % 12 == 0 ? 12 : hr % 12);
        float w = getWidth();
        float h = getHeight();
        p.reset();
        p.setAntiAlias(true);
        switch (kind) {
            case 3: {
                String a = two(hd);
                String b = two(mn);
                float cell = Math.min(dp(11), (w - dp(8)) / 11f);
                float x = (w - 11 * cell) / 2f;
                float gap = dp(18);
                float top = (h - (14 * cell + gap)) / 2f;
                DotFont.draw(cv, a, x, top, cell, p, 0xFFFFFFFF, 0x22FFFFFF);
                DotFont.draw(cv, b, x, top + 7 * cell + gap, cell, p, 0xFFFFFFFF, 0x22FFFFFF);
                break;
            }
            case 4: {
                String a = two(hd);
                String b = two(mn);
                float size = dp(112);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                p.setTextSize(size);
                p.setColor(0xFFFFFFFF);
                float b1 = size * 0.80f + dp(6);
                cv.drawText(a, w / 2f, b1, p);
                p.setColor(0xCCFFFFFF);
                cv.drawText(b, w / 2f, b1 + size * 0.78f, p);
                break;
            }
            case 5: {
                float cx = w / 2f;
                float cy = h / 2f;
                float R = Math.min(w, h) / 2f - dp(10);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.5f));
                p.setColor(0x66FFFFFF);
                cv.drawCircle(cx, cy, R, p);
                p.setStrokeWidth(dp(2));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 12; i++) {
                    double a = Math.toRadians(i * 30);
                    float c = (float) Math.cos(a);
                    float s = (float) Math.sin(a);
                    float r1 = R - dp(4);
                    float r2 = R - dp(i % 3 == 0 ? 16 : 10);
                    cv.drawLine(cx + c * r1, cy + s * r1, cx + c * r2, cy + s * r2, p);
                }
                hand(cv, cx, cy, ((hr % 12) + mn / 60f) * 30f, R * 0.5f, dp(5), 0xFFFFFFFF);
                hand(cv, cx, cy, (mn + sc / 60f) * 6f, R * 0.78f, dp(3.2f), 0xFFFFFFFF);
                hand(cv, cx, cy, sc * 6f, R * 0.86f, dp(1.4f), 0xFFD71921);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFD71921);
                cv.drawCircle(cx, cy, dp(4), p);
                break;
            }
            case 6: {
                float cx = w / 2f;
                float cy = h / 2f;
                float R = Math.min(w, h) / 2f - dp(12);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 60; i++) {
                    double a = Math.toRadians(i * 6 - 90);
                    boolean major = i % 5 == 0;
                    int col = i <= sc ? 0xFFFFFFFF : 0x33FFFFFF;
                    if (i == sc) col = 0xFFD71921;
                    p.setColor(col);
                    float rr = dp(major ? 2.6f : 1.7f);
                    cv.drawCircle(cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, rr, p);
                }
                String t = two(hd) + ":" + two(mn);
                int cols = DotFont.cols(t);
                float cell = Math.min(dp(5), (R * 1.6f) / cols);
                DotFont.draw(cv, t, cx - cols * cell / 2f, cy - 3.5f * cell, cell, p, 0xFFFFFFFF, 0x22FFFFFF);
                break;
            }
            case 7: {
                String t = hd + ":" + two(mn);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                p.setTextSize(dp(88));
                p.setColor(0xFFFFFFFF);
                cv.drawText(t, w / 2f, dp(80), p);
                float frac = (hr * 3600 + mn * 60 + sc) / 86400f;
                float bw = w * 0.56f;
                float bx = (w - bw) / 2f;
                float by = dp(106);
                float bh = dp(6);
                p.setColor(0x33FFFFFF);
                cv.drawRoundRect(bx, by, bx + bw, by + bh, bh / 2f, bh / 2f, p);
                p.setColor(0xFFFFFFFF);
                cv.drawRoundRect(bx, by, bx + Math.max(bh, bw * frac), by + bh, bh / 2f, bh / 2f, p);
                p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                p.setTextSize(dp(13));
                p.setColor(0xCCFFFFFF);
                cv.drawText(Math.round(frac * 100) + "% del d\u00EDa", w / 2f, dp(140), p);
                break;
            }
            default: {
                String hw = hd == 1 ? "UNA" : word(hd);
                String mw = mn == 0 ? "EN PUNTO" : word(mn);
                float c1 = Math.min(dp(6), (w - dp(8)) / (float) DotFont.cols(hw));
                float c2 = Math.min(dp(3.2f), (w - dp(8)) / (float) DotFont.cols(mw));
                float gap = dp(18);
                float t1 = (h - (7 * c1 + gap + 7 * c2)) / 2f;
                DotFont.draw(cv, hw, (w - DotFont.cols(hw) * c1) / 2f, t1, c1, p, 0xFFFFFFFF, 0);
                DotFont.draw(cv, mw, (w - DotFont.cols(mw) * c2) / 2f, t1 + 7 * c1 + gap, c2, p, 0xCCFFFFFF, 0);
                break;
            }
        }
    }
}
