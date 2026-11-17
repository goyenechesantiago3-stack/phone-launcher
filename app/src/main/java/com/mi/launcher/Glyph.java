package com.mi.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class Glyph extends View {
    public static final int WIFI = 0, BT = 1, TORCH = 2, GEAR = 3, LOCK = 4, CAMERA = 5,
            UP = 6, PLUS = 7, PREV = 8, PLAY = 9, PAUSE = 10, NEXT = 11;

    int type;
    int color = 0xFFFFFFFF;
    float S;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final RectF rf = new RectF();

    public Glyph(Context c, int t) {
        super(c);
        type = t;
    }

    public void setType(int t) {
        if (t != type) {
            type = t;
            invalidate();
        }
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    void stroke() { p.setStyle(Paint.Style.STROKE); }
    void fill() { p.setStyle(Paint.Style.FILL); }
    float u(float v) { return v * S; }

    void line(Canvas cv, float x1, float y1, float x2, float y2) {
        cv.drawLine(u(x1), u(y1), u(x2), u(y2), p);
    }

    void rect(Canvas cv, float l, float t, float r, float b, float rad) {
        rf.set(u(l), u(t), u(r), u(b));
        cv.drawRoundRect(rf, u(rad), u(rad), p);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float aw = getWidth() - getPaddingLeft() - getPaddingRight();
        float ah = getHeight() - getPaddingTop() - getPaddingBottom();
        S = Math.min(aw, ah);
        if (S <= 0) return;
        cv.save();
        cv.translate(getPaddingLeft() + (aw - S) / 2f, getPaddingTop() + (ah - S) / 2f);
        p.setColor(color);
        p.setStrokeWidth(S * 0.075f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        stroke();
        path.reset();
        switch (type) {
            case WIFI: {
                float[] rs = {0.18f, 0.34f, 0.50f};
                for (float r : rs) {
                    rf.set(u(0.5f - r), u(0.78f - r), u(0.5f + r), u(0.78f + r));
                    cv.drawArc(rf, 225, 90, false, p);
                }
                fill();
                cv.drawCircle(u(0.5f), u(0.78f), u(0.06f), p);
                break;
            }
            case BT:
                path.moveTo(u(0.28f), u(0.32f));
                path.lineTo(u(0.72f), u(0.68f));
                path.lineTo(u(0.5f), u(0.88f));
                path.lineTo(u(0.5f), u(0.12f));
                path.lineTo(u(0.72f), u(0.32f));
                path.lineTo(u(0.28f), u(0.68f));
                cv.drawPath(path, p);
                break;
            case TORCH:
                path.moveTo(u(0.30f), u(0.08f));
                path.lineTo(u(0.70f), u(0.08f));
                path.lineTo(u(0.62f), u(0.30f));
                path.lineTo(u(0.38f), u(0.30f));
                path.close();
                cv.drawPath(path, p);
                rect(cv, 0.38f, 0.30f, 0.62f, 0.92f, 0.06f);
                break;
            case GEAR:
                cv.drawCircle(u(0.5f), u(0.5f), u(0.15f), p);
                cv.drawCircle(u(0.5f), u(0.5f), u(0.30f), p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45);
                    float cx = (float) Math.cos(a), cy = (float) Math.sin(a);
                    cv.drawLine(u(0.5f + cx * 0.33f), u(0.5f + cy * 0.33f),
                            u(0.5f + cx * 0.45f), u(0.5f + cy * 0.45f), p);
                }
                break;
            case LOCK:
                rect(cv, 0.22f, 0.44f, 0.78f, 0.90f, 0.08f);
                rf.set(u(0.32f), u(0.12f), u(0.68f), u(0.52f));
                cv.drawArc(rf, 180, 180, false, p);
                line(cv, 0.32f, 0.32f, 0.32f, 0.44f);
                line(cv, 0.68f, 0.32f, 0.68f, 0.44f);
                fill();
                cv.drawCircle(u(0.5f), u(0.67f), u(0.05f), p);
                break;
            case CAMERA:
                rect(cv, 0.08f, 0.28f, 0.92f, 0.82f, 0.10f);
                cv.drawCircle(u(0.5f), u(0.55f), u(0.16f), p);
                path.moveTo(u(0.34f), u(0.28f));
                path.lineTo(u(0.40f), u(0.16f));
                path.lineTo(u(0.60f), u(0.16f));
                path.lineTo(u(0.66f), u(0.28f));
                cv.drawPath(path, p);
                break;
            case UP:
                path.moveTo(u(0.22f), u(0.62f));
                path.lineTo(u(0.50f), u(0.34f));
                path.lineTo(u(0.78f), u(0.62f));
                cv.drawPath(path, p);
                break;
            case PLUS:
                line(cv, 0.5f, 0.2f, 0.5f, 0.8f);
                line(cv, 0.2f, 0.5f, 0.8f, 0.5f);
                break;
            case PREV:
                fill();
                path.moveTo(u(0.80f), u(0.22f));
                path.lineTo(u(0.36f), u(0.50f));
                path.lineTo(u(0.80f), u(0.78f));
                path.close();
                cv.drawPath(path, p);
                rect(cv, 0.18f, 0.22f, 0.28f, 0.78f, 0.03f);
                break;
            case NEXT:
                fill();
                path.moveTo(u(0.20f), u(0.22f));
                path.lineTo(u(0.64f), u(0.50f));
                path.lineTo(u(0.20f), u(0.78f));
                path.close();
                cv.drawPath(path, p);
                rect(cv, 0.72f, 0.22f, 0.82f, 0.78f, 0.03f);
                break;
            case PLAY:
                fill();
                path.moveTo(u(0.30f), u(0.16f));
                path.lineTo(u(0.84f), u(0.50f));
                path.lineTo(u(0.30f), u(0.84f));
                path.close();
                cv.drawPath(path, p);
                break;
            case PAUSE:
                fill();
                rect(cv, 0.24f, 0.18f, 0.42f, 0.82f, 0.04f);
                rect(cv, 0.58f, 0.18f, 0.76f, 0.82f, 0.04f);
                break;
            default:
                break;
        }
        cv.restore();
    }
}
