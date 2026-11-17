package com.mi.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import java.text.Normalizer;
import java.util.Locale;

public final class DotFont {
    private DotFont() {}

    static String r(String a, String b, String c, String d, String e, String f, String g) {
        return a + b + c + d + e + f + g;
    }

    static final String[] AZ = {
        r("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        r("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        r("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
        r("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        r("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        r("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        r("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
        r("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        r("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
        r("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
        r("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        r("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        r("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        r("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
        r("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        r("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        r("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        r("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        r("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        r("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        r("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        r("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        r("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
        r("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        r("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        r("11111", "00001", "00010", "00100", "01000", "10000", "11111")
    };

    static final String[] DIG = {
        r("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        r("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        r("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        r("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        r("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        r("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        r("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        r("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        r("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        r("01110", "10001", "10001", "01111", "00001", "00010", "01100")
    };

    // devuelve filas x columnas concatenadas (7 filas); ancho = length / 7
    static String glyph(char c) {
        if (c >= 'A' && c <= 'Z') return AZ[c - 'A'];
        if (c >= '0' && c <= '9') return DIG[c - '0'];
        switch (c) {
            case ' ': return "000000000000000000000";
            case '.': return r("0", "0", "0", "0", "0", "1", "1");
            case ',': return r("00", "00", "00", "00", "00", "01", "10");
            case ':': return r("0", "1", "1", "0", "1", "1", "0");
            case '\u00B7': return r("0", "0", "0", "1", "0", "0", "0");
            case '-': return r("000", "000", "000", "111", "000", "000", "000");
            case '/': return r("00001", "00010", "00010", "00100", "01000", "01000", "10000");
            case '%': return r("11001", "11010", "00010", "00100", "01000", "01011", "10011");
            case '+': return r("00000", "00100", "00100", "11111", "00100", "00100", "00000");
            case '!': return r("1", "1", "1", "1", "1", "0", "1");
            case '?': return r("01110", "10001", "00001", "00010", "00100", "00000", "00100");
            case '\'': return r("1", "1", "0", "0", "0", "0", "0");
            default: return null;
        }
    }

    static int wOf(String g) { return g == null ? 3 : g.length() / 7; }

    static class Parsed {
        char[] c;
        boolean[] a;
        int n;
        boolean any;
    }

    static Parsed parse(String s) {
        String d = Normalizer.normalize(s.toUpperCase(Locale.ROOT), Normalizer.Form.NFD);
        Parsed p = new Parsed();
        p.c = new char[d.length()];
        p.a = new boolean[d.length()];
        for (int i = 0; i < d.length(); i++) {
            char x = d.charAt(i);
            if (x >= 0x0300 && x <= 0x036F) {
                if (p.n > 0) {
                    p.a[p.n - 1] = true;
                    p.any = true;
                }
                continue;
            }
            p.c[p.n++] = x;
        }
        return p;
    }

    public static int cols(String s) {
        Parsed p = parse(s);
        int t = 0;
        for (int i = 0; i < p.n; i++) {
            t += wOf(glyph(p.c[i]));
            if (i < p.n - 1) t += 1;
        }
        return t;
    }

    public static boolean hasAccent(String s) {
        return parse(s).any;
    }

    // y = borde superior de las 7 filas. Si hay acentos, se dibujan en la fila de arriba (y - celda).
    public static void draw(Canvas cv, String s, float x, float y, float cell, Paint p, int on, int off) {
        Parsed ps = parse(s);
        p.setStyle(Paint.Style.FILL);
        float r = cell * 0.36f;
        float cx = x;
        for (int i = 0; i < ps.n; i++) {
            String g = glyph(ps.c[i]);
            int w = wOf(g);
            if (g != null) {
                for (int row = 0; row < 7; row++) {
                    for (int col = 0; col < w; col++) {
                        boolean lit = g.charAt(row * w + col) == '1';
                        if (!lit && off == 0) continue;
                        p.setColor(lit ? on : off);
                        cv.drawCircle(cx + (col + 0.5f) * cell, y + (row + 0.5f) * cell, r, p);
                    }
                }
                if (ps.a[i]) {
                    p.setColor(on);
                    cv.drawCircle(cx + (w / 2f) * cell, y - 0.5f * cell, r, p);
                }
            }
            cx += (w + 1) * cell;
        }
    }
}
