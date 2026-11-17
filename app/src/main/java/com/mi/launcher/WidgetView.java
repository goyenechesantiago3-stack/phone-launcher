package com.mi.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class WidgetView extends View {
    public static final int COUNT = 9;
    public static final String[] NAMES = {"D\u00EDa", "Bater\u00EDa", "Mes", "Semana", "Hora y fecha",
            "Almacenamiento", "Memoria", "Nota", "A\u00F1o"};
    public static final String[] STYLE_NAMES = {"iPhone cristal", "Nothing", "iPhone oscuro", "Nothing rojo"};

    public static boolean wide(int k) {
        return k == 2 || k == 3 || k == 4 || k == 7;
    }

    public static int heightDp(int k) {
        switch (k) {
            case 2: return 210;
            case 3: return 96;
            case 4: return 112;
            case 7: return 120;
            default: return 110;
        }
    }

    final int kind;
    final int style;
    final float d;
    final Locale es = new Locale("es", "MX");
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF rf = new RectF();
    final Typeface tfN = Typeface.create("sans-serif", Typeface.NORMAL);
    final Typeface tfL = Typeface.create("sans-serif-light", Typeface.NORMAL);
    final Typeface tfM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    final Typeface mono = Typeface.MONOSPACE;

    int bg, stroke, fg, dim, acc;
    float strokeW, radius;
    boolean nothing;

    int pct = 50;
    boolean chg;
    long total, avail;

    final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            invalidate();
            postDelayed(this, 30000);
        }
    };

    public WidgetView(Context c, int kind, int style) {
        super(c);
        this.kind = kind;
        this.style = style;
        this.d = c.getResources().getDisplayMetrics().density;
        fg = 0xFFFFFFFF;
        switch (style) {
            case 1:
                bg = 0x66000000; stroke = 0xCCFFFFFF; strokeW = dp(1.3f); dim = 0x99FFFFFF; acc = 0xFFFFFFFF;
                radius = dp(20); nothing = true;
                break;
            case 2:
                bg = 0xE61C1C1E; stroke = 0x22FFFFFF; strokeW = dp(1); dim = 0x99EBEBF5; acc = 0xFF0A84FF;
                radius = dp(26); nothing = false;
                break;
            case 3:
                bg = 0xF2000000; stroke = 0x33FFFFFF; strokeW = dp(1); dim = 0x99FFFFFF; acc = 0xFFD71921;
                radius = dp(20); nothing = true;
                break;
            default:
                bg = 0x40FFFFFF; stroke = 0x44FFFFFF; strokeW = dp(1); dim = 0xCCFFFFFF; acc = 0xFFFF453A;
                radius = dp(26); nothing = false;
                break;
        }
        refresh();
    }

    float dp(float v) {
        return v * d;
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
        if (w <= 0) w = (int) dp(150);
        int h = View.MeasureSpec.getMode(hSpec) == View.MeasureSpec.EXACTLY
                ? View.MeasureSpec.getSize(hSpec) : (int) dp(heightDp(kind));
        setMeasuredDimension(w, h);
    }

    void refresh() {
        try {
            if (kind == 1) {
                Intent b = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    int sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    pct = sc > 0 ? lvl * 100 / sc : lvl;
                    int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    chg = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
                }
            } else if (kind == 5) {
                StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
                total = sf.getTotalBytes();
                avail = sf.getAvailableBytes();
            } else if (kind == 6) {
                ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                total = mi.totalMem;
                avail = mi.availMem;
            }
        } catch (Exception e) {
            // nada
        }
    }

    // ---------------------------------------------------------------- ayudas de dibujo

    void txt(Canvas c, String s, float x, float y, float size, int color, Typeface tf, Paint.Align al) {
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(al);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(tf);
        c.drawText(s, x, y, p);
    }

    void dots(Canvas c, String s, float x, float y, float cell, int color) {
        p.reset();
        p.setAntiAlias(true);
        DotFont.draw(c, s, x, y, cell, p, color, 0);
    }

    float dotCell(String s, float want, float maxW) {
        int cols = Math.max(1, DotFont.cols(s));
        return Math.min(want, maxW / cols);
    }

    void bar(Canvas c, float x, float y, float w, float h, float frac, int color) {
        frac = Math.max(0f, Math.min(1f, frac));
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        if (nothing) {
            int n = 10;
            float cell = w / n;
            float r = Math.min(cell * 0.32f, h / 2f);
            int on = Math.round(frac * n);
            if (frac > 0f && on == 0) on = 1;
            for (int i = 0; i < n; i++) {
                p.setColor(i < on ? color : 0x33FFFFFF);
                c.drawCircle(x + (i + 0.5f) * cell, y + h / 2f, r, p);
            }
        } else {
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(x, y, x + w, y + h, h / 2f, h / 2f, p);
            p.setColor(color);
            c.drawRoundRect(x, y, x + Math.max(h, w * frac), y + h, h / 2f, h / 2f, p);
        }
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg);
        rf.set(0, 0, w, h);
        c.drawRoundRect(rf, radius, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokeW);
        p.setColor(stroke);
        rf.set(strokeW / 2f, strokeW / 2f, w - strokeW / 2f, h - strokeW / 2f);
        c.drawRoundRect(rf, radius, radius, p);
        float pad = dp(14);
        switch (kind) {
            case 0: drawDay(c, w, h, pad); break;
            case 1: drawBattery(c, w, h, pad); break;
            case 2: drawMonth(c, w, h, pad); break;
            case 3: drawWeek(c, w, h, pad); break;
            case 4: drawTime(c, w, h, pad); break;
            case 5: drawStat(c, w, h, pad, nothing ? "DISCO" : "Almacenamiento",
                    gb(total - avail), "de " + gb(total), total > 0 ? (total - avail) / (float) total : 0f); break;
            case 6: drawStat(c, w, h, pad, nothing ? "RAM" : "Memoria",
                    (total > 0 ? Math.round((total - avail) * 100f / total) : 0) + "%",
                    gb(total - avail) + " de " + gb(total), total > 0 ? (total - avail) / (float) total : 0f); break;
            case 7: drawNote(c, w, h, pad); break;
            default: drawYear(c, w, h, pad); break;
        }
    }

    String gb(long bytes) {
        float g = bytes / 1073741824f;
        return g >= 10 ? Math.round(g) + " GB" : String.format(Locale.US, "%.1f GB", g);
    }

    void drawDay(Canvas c, float w, float h, float pad) {
        Date now = new Date();
        String dn = new SimpleDateFormat("d", es).format(now);
        if (nothing) {
            String top = new SimpleDateFormat("EEE \u00B7 MMM", es).format(now).toUpperCase(es);
            dots(c, top, pad, pad, dotCell(top, dp(2f), w - 2 * pad), acc);
            float cell = dotCell(dn, dp(7), w - 2 * pad);
            dots(c, dn, pad, h - pad - 7 * cell, cell, fg);
        } else {
            txt(c, new SimpleDateFormat("EEEE", es).format(now).toUpperCase(es), pad, pad + dp(13), dp(12.5f), acc, tfM, Paint.Align.LEFT);
            txt(c, new SimpleDateFormat("MMMM", es).format(now), pad, pad + dp(31), dp(12), dim, tfN, Paint.Align.LEFT);
            txt(c, dn, pad, h - pad - dp(2), dp(54), fg, tfL, Paint.Align.LEFT);
        }
    }

    void drawBattery(Canvas c, float w, float h, float pad) {
        int col = (pct <= 20 && !chg) ? 0xFFFF3B30 : (style == 3 ? acc : (style == 2 ? acc : 0xFFFFFFFF));
        String ps = pct + "%";
        if (nothing) {
            dots(c, chg ? "CARGANDO" : "BATERIA", pad, pad, dotCell("CARGANDO", dp(2f), w - 2 * pad), dim);
            float cell = dotCell(ps, dp(5.5f), w - 2 * pad);
            dots(c, ps, pad, h - pad - dp(22) - 7 * cell, cell, fg);
        } else {
            txt(c, chg ? "Cargando" : "Bater\u00EDa", pad, pad + dp(13), dp(13), dim, tfM, Paint.Align.LEFT);
            txt(c, ps, pad, h - pad - dp(24), dp(40), fg, tfL, Paint.Align.LEFT);
        }
        bar(c, pad, h - pad - dp(8), w - 2 * pad, dp(8), pct / 100f, col);
    }

    void drawStat(Canvas c, float w, float h, float pad, String label, String big, String sub, float frac) {
        if (nothing) {
            dots(c, label, pad, pad, dotCell(label, dp(2f), w - 2 * pad), dim);
            float cell = dotCell(big, dp(4.6f), w - 2 * pad);
            dots(c, big, pad, h - pad - dp(22) - 7 * cell, cell, fg);
        } else {
            txt(c, label, pad, pad + dp(13), dp(12.5f), dim, tfM, Paint.Align.LEFT);
            txt(c, big, pad, h - pad - dp(34), dp(32), fg, tfL, Paint.Align.LEFT);
            txt(c, sub, pad, h - pad - dp(18), dp(11), dim, tfN, Paint.Align.LEFT);
        }
        bar(c, pad, h - pad - dp(8), w - 2 * pad, dp(8), frac, style == 3 || style == 2 ? acc : 0xFFFFFFFF);
    }

    void drawMonth(Canvas c, float w, float h, float pad) {
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DAY_OF_MONTH);
        Calendar f = (Calendar) cal.clone();
        f.set(Calendar.DAY_OF_MONTH, 1);
        int off = (f.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int dim2 = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rows = (off + dim2 + 6) / 7;
        Date now = new Date();
        String mon = new SimpleDateFormat("MMMM", es).format(now);
        String yr = new SimpleDateFormat("yyyy", es).format(now);
        if (nothing) {
            dots(c, mon.toUpperCase(es), pad, pad, dp(2f), acc);
            float yc = dotCell(yr, dp(2f), w);
            dots(c, yr, w - pad - DotFont.cols(yr) * yc, pad, yc, dim);
        } else {
            txt(c, mon.substring(0, 1).toUpperCase(es) + mon.substring(1), pad, pad + dp(15), dp(17), acc, tfM, Paint.Align.LEFT);
            txt(c, yr, w - pad, pad + dp(15), dp(14), dim, tfN, Paint.Align.RIGHT);
        }
        float colW = (w - 2 * pad) / 7f;
        String ini = "LMMJVSD";
        float yi = pad + dp(36);
        for (int i = 0; i < 7; i++) {
            txt(c, String.valueOf(ini.charAt(i)), pad + colW * (i + 0.5f), yi, dp(10), dim, nothing ? mono : tfM, Paint.Align.CENTER);
        }
        float y0 = pad + dp(44);
        float rowH = (h - pad - y0) / rows;
        for (int day = 1; day <= dim2; day++) {
            int idx = off + day - 1;
            float cx = pad + colW * ((idx % 7) + 0.5f);
            float cy = y0 + rowH * ((idx / 7) + 0.5f);
            if (style == 3) {
                p.reset();
                p.setAntiAlias(true);
                p.setStyle(Paint.Style.FILL);
                p.setColor(day == today ? acc : (day < today ? 0xFFFFFFFF : 0x44FFFFFF));
                c.drawCircle(cx, cy, day == today ? dp(5) : dp(2.2f), p);
                continue;
            }
            int tc = fg;
            if (day == today) {
                p.reset();
                p.setAntiAlias(true);
                p.setStyle(Paint.Style.FILL);
                if (nothing) {
                    p.setColor(0xFFFFFFFF);
                    float rr = Math.min(colW, rowH) * 0.46f;
                    rf.set(cx - rr, cy - rr, cx + rr, cy + rr);
                    c.drawRoundRect(rf, dp(6), dp(6), p);
                    tc = 0xFF000000;
                } else {
                    p.setColor(acc);
                    c.drawCircle(cx, cy, Math.min(colW, rowH) * 0.46f, p);
                }
            }
            txt(c, String.valueOf(day), cx, cy + dp(4.5f), dp(13), tc, nothing ? mono : tfN, Paint.Align.CENTER);
        }
    }

    void drawWeek(Canvas c, float w, float h, float pad) {
        Calendar cal = Calendar.getInstance();
        int todayIdx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        cal.add(Calendar.DAY_OF_MONTH, -todayIdx);
        String ini = "LMMJVSD";
        float colW = (w - 2 * pad) / 7f;
        for (int i = 0; i < 7; i++) {
            float cx = pad + colW * (i + 0.5f);
            boolean isToday = i == todayIdx;
            String dn = String.valueOf(cal.get(Calendar.DAY_OF_MONTH));
            txt(c, String.valueOf(ini.charAt(i)), cx, pad + dp(8), dp(10.5f), isToday ? acc : dim, nothing ? mono : tfM, Paint.Align.CENTER);
            float cy = h - pad - dp(14);
            if (isToday) {
                p.reset();
                p.setAntiAlias(true);
                p.setStyle(Paint.Style.FILL);
                if (nothing && style == 1) {
                    p.setColor(0xFFFFFFFF);
                    rf.set(cx - dp(15), cy - dp(15), cx + dp(15), cy + dp(15));
                    c.drawRoundRect(rf, dp(8), dp(8), p);
                } else {
                    p.setColor(acc);
                    c.drawCircle(cx, cy, dp(16), p);
                }
            }
            int tc = (isToday && nothing && style == 1) ? 0xFF000000 : fg;
            if (nothing) {
                float cell = dotCell(dn, dp(2.4f), colW - dp(4));
                dots(c, dn, cx - DotFont.cols(dn) * cell / 2f, cy - 3.5f * cell, cell, tc);
            } else {
                txt(c, dn, cx, cy + dp(6), dp(17), tc, tfM, Paint.Align.CENTER);
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    void drawTime(Canvas c, float w, float h, float pad) {
        Date now = new Date();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(getContext());
        String t = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", es).format(now);
        String dt = new SimpleDateFormat("EEEE d 'de' MMMM", es).format(now);
        if (nothing) {
            float cell = dotCell(t, dp(5), w - 2 * pad);
            float dc = dotCell(dt, dp(2f), w - 2 * pad);
            float total2 = 7 * cell + dp(12) + 7 * dc;
            float top = (h - total2) / 2f;
            dots(c, t, pad, top, cell, fg);
            dots(c, dt, pad, top + 7 * cell + dp(12), dc, style == 3 ? acc : dim);
        } else {
            txt(c, t, pad, h / 2f + dp(12), dp(52), fg, tfL, Paint.Align.LEFT);
            String cap = dt.substring(0, 1).toUpperCase(es) + dt.substring(1);
            txt(c, cap, pad, h - pad - dp(6), dp(14), dim, tfN, Paint.Align.LEFT);
        }
    }

    void drawNote(Canvas c, float w, float h, float pad) {
        String note = getContext().getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("note", "");
        boolean empty = note == null || note.trim().isEmpty();
        if (empty) note = "Toca para escribir una nota";
        if (nothing) dots(c, "NOTA", pad, pad, dp(2f), acc);
        else txt(c, "Nota", pad, pad + dp(13), dp(13), acc, tfM, Paint.Align.LEFT);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(dp(14));
        tp.setColor(empty ? dim : fg);
        tp.setTypeface(nothing ? mono : tfN);
        int width = (int) (w - 2 * pad);
        if (width < 10) return;
        StaticLayout sl = StaticLayout.Builder.obtain(note, 0, note.length(), tp, width)
                .setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).build();
        c.save();
        c.translate(pad, pad + dp(24));
        sl.draw(c);
        c.restore();
    }

    void drawYear(Canvas c, float w, float h, float pad) {
        Calendar cal = Calendar.getInstance();
        int doy = cal.get(Calendar.DAY_OF_YEAR);
        int max = cal.getActualMaximum(Calendar.DAY_OF_YEAR);
        float frac = doy / (float) max;
        float cx = w / 2f;
        float cy = h / 2f;
        float R = Math.min(w, h) / 2f - pad - dp(2);
        if (nothing) {
            p.reset();
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.FILL);
            int n = 36;
            int on = Math.round(frac * n);
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(i * (360f / n) - 90);
                p.setColor(i < on ? (style == 3 ? acc : 0xFFFFFFFF) : 0x33FFFFFF);
                c.drawCircle(cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, dp(2.2f), p);
            }
            String s = String.valueOf(doy);
            float cell = dotCell(s, dp(3.2f), R * 1.2f);
            dots(c, s, cx - DotFont.cols(s) * cell / 2f, cy - 3.5f * cell, cell, fg);
        } else {
            p.reset();
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(7));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x33FFFFFF);
            c.drawCircle(cx, cy, R, p);
            p.setColor(style == 2 ? acc : 0xFFFFFFFF);
            rf.set(cx - R, cy - R, cx + R, cy + R);
            c.drawArc(rf, -90, 360f * frac, false, p);
            txt(c, String.valueOf(doy), cx, cy + dp(5), dp(22), fg, tfM, Paint.Align.CENTER);
            txt(c, "de " + max, cx, cy + dp(19), dp(10), dim, tfN, Paint.Align.CENTER);
        }
    }
}
