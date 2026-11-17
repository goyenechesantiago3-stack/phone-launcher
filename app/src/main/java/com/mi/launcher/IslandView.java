package com.mi.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

public class IslandView extends FrameLayout {

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    float d;
    SharedPreferences sp;
    int accent;
    final Handler h = new Handler(Looper.getMainLooper());
    boolean expanded, embedded, hideEmpty;
    LinearLayout pill, top, textCol, controls, bars;
    ImageView cover;
    TextView title, artist, note;
    Glyph prev, play, next;
    View[] bar = new View[4];
    View spacer;
    BarView prog;
    final Random rnd = new Random();

    final Runnable refresher = new Runnable() {
        @Override public void run() { refresh(); }
    };
    final Runnable ticker = new Runnable() {
        @Override public void run() {
            tickBars();
            h.postDelayed(this, 140);
        }
    };
    final Runnable collapser = new Runnable() {
        @Override public void run() { setExpanded(false); }
    };

    int dp(float v) { return (int) (v * d + 0.5f); }

    public IslandView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        sp = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        accent = sp.getInt("accent", 0xFFD71921);
        Typeface tf = Hub.font(c);

        pill = new LinearLayout(c);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER_HORIZONTAL);
        pill.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF000000);
        g.setCornerRadius(dp(26));
        g.setStroke(dp(1), 0x33FFFFFF);
        pill.setBackground(g);

        top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackgroundColor(0xFF222222);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(8));
            }
        });
        cover.setClipToOutline(true);
        top.addView(cover, new LinearLayout.LayoutParams(dp(30), dp(30)));

        textCol = new LinearLayout(c);
        textCol.setOrientation(LinearLayout.VERTICAL);
        title = new TextView(c);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(15);
        title.setTypeface(tf, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        artist = new TextView(c);
        artist.setTextColor(0xB3FFFFFF);
        artist.setTextSize(12);
        artist.setTypeface(tf);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(title);
        textCol.addView(artist);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tlp.leftMargin = dp(10);
        tlp.rightMargin = dp(10);
        top.addView(textCol, tlp);

        spacer = new View(c);
        top.addView(spacer, new LinearLayout.LayoutParams(dp(70), 1));

        bars = new LinearLayout(c);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 4; i++) {
            View b = new View(c);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(accent);
            bg.setCornerRadius(dp(2));
            b.setBackground(bg);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(3), dp(18));
            bl.leftMargin = dp(2);
            bars.addView(b, bl);
            b.setPivotY(dp(18));
            b.setScaleY(0.25f);
            bar[i] = b;
        }
        top.addView(bars, new LinearLayout.LayoutParams(WRAP, dp(18)));
        pill.addView(top, new LinearLayout.LayoutParams(WRAP, WRAP));

        prog = new BarView(c);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, dp(4));
        plp.topMargin = dp(12);
        pill.addView(prog, plp);

        controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        prev = gbtn(c, Glyph.PREV);
        play = gbtn(c, Glyph.PLAY);
        next = gbtn(c, Glyph.NEXT);
        controls.addView(prev, gl());
        controls.addView(play, gl());
        controls.addView(next, gl());
        pill.addView(controls, new LinearLayout.LayoutParams(WRAP, WRAP));

        note = new TextView(c);
        note.setTextColor(0xFFFFFFFF);
        note.setTextSize(14);
        note.setTypeface(tf);
        note.setSingleLine(true);
        note.setEllipsize(TextUtils.TruncateAt.END);
        note.setMaxWidth(dp(300));
        note.setVisibility(View.GONE);
        pill.addView(note, new LinearLayout.LayoutParams(WRAP, WRAP));

        addView(pill, new FrameLayout.LayoutParams(WRAP, WRAP));
        setVisibility(View.GONE);

        pill.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (embedded) return;
                if (Hub.hasPeek() || Hub.hasCharge()) {
                    if (Hub.hasPeek()) openPkg(Hub.peekPkg);
                    return;
                }
                setExpanded(!expanded);
            }
        });
        pill.setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                openPkg(Hub.mediaPkg);
                return true;
            }
        });
        cover.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (expanded || embedded) openPkg(Hub.mediaPkg);
                else setExpanded(true);
            }
        });
        play.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    if (Hub.playing) Hub.mc.getTransportControls().pause();
                    else Hub.mc.getTransportControls().play();
                } catch (Exception e) {
                    // nada
                }
            }
        });
        prev.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Hub.mc.getTransportControls().skipToPrevious();
                } catch (Exception e) {
                    // nada
                }
            }
        });
        next.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Hub.mc.getTransportControls().skipToNext();
                } catch (Exception e) {
                    // nada
                }
            }
        });
    }

    Glyph gbtn(Context c, int type) {
        Glyph g = new Glyph(c, type);
        g.setPadding(dp(13), dp(13), dp(13), dp(13));
        return g;
    }

    LinearLayout.LayoutParams gl() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(52), dp(52));
        l.setMargins(dp(4), dp(2), dp(4), dp(2));
        return l;
    }

    TextView btn(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(26);
        t.setTextColor(0xFFFFFFFF);
        t.setPadding(dp(16), dp(4), dp(16), dp(2));
        return t;
    }

    public void setEmbedded(boolean e, boolean hide) {
        embedded = e;
        hideEmpty = hide;
    }

    void openPkg(String pkg) {
        if (pkg == null) return;
        try {
            Intent i = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
        } catch (Exception e) {
            // nada
        }
    }

    void setExpanded(boolean e) {
        expanded = e;
        h.removeCallbacks(collapser);
        if (e && !embedded) h.postDelayed(collapser, 7000);
        refresh();
        pill.setScaleX(0.92f);
        pill.setScaleY(0.92f);
        pill.animate().scaleX(1f).scaleY(1f).setDuration(220)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();
    }

    String join(String a, String b) {
        return (a == null ? "" : a) + (b == null ? "" : "  " + b);
    }

    void setPillWidth(boolean wide) {
        int w = WRAP;
        if (wide) w = embedded ? MATCH : getResources().getDisplayMetrics().widthPixels - dp(24);
        ViewGroup.LayoutParams lp = pill.getLayoutParams();
        if (lp.width != w) {
            lp.width = w;
            pill.setLayoutParams(lp);
        }
        LinearLayout.LayoutParams tl = (LinearLayout.LayoutParams) top.getLayoutParams();
        int tw = wide ? MATCH : WRAP;
        if (tl.width != tw) {
            tl.width = tw;
            top.setLayoutParams(tl);
        }
    }

    void refresh() {
        boolean peek = Hub.hasPeek();
        boolean chg = Hub.hasCharge();
        boolean med = Hub.hasMedia();
        if (!embedded && !peek && !chg && !med) {
            setVisibility(View.GONE);
            return;
        }
        if (embedded && !med && hideEmpty) {
            setVisibility(View.GONE);
            return;
        }
        setVisibility(View.VISIBLE);
        boolean noteMode = !embedded && (peek || chg);
        if (noteMode || !med) {
            top.setVisibility(View.GONE);
            prog.setVisibility(View.GONE);
            controls.setVisibility(View.GONE);
            note.setVisibility(View.VISIBLE);
            if (noteMode) {
                note.setText(chg ? "Cargando " + Hub.battPct + "%" : join(Hub.peekTitle, Hub.peekText));
                long rem = Math.max(Hub.peekUntil, Hub.chargeUntil) - SystemClock.uptimeMillis();
                if (rem > 0) {
                    h.removeCallbacks(refresher);
                    h.postDelayed(refresher, rem + 50);
                }
            } else {
                note.setText("Nada se está reproduciendo");
            }
            setPillWidth(embedded);
            return;
        }
        note.setVisibility(View.GONE);
        top.setVisibility(View.VISIBLE);
        boolean ex = expanded || embedded;
        title.setText(Hub.title);
        artist.setText(Hub.artist == null ? "" : Hub.artist);
        if (Hub.cover != null) cover.setImageBitmap(Hub.cover);
        else cover.setImageDrawable(null);
        int cs = dp(ex ? 56 : 30);
        LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) cover.getLayoutParams();
        if (cp.width != cs) {
            cp.width = cs;
            cp.height = cs;
            cover.setLayoutParams(cp);
        }
        textCol.setVisibility(ex ? View.VISIBLE : View.GONE);
        spacer.setVisibility(ex ? View.GONE : View.VISIBLE);
        prog.setVisibility(ex ? View.VISIBLE : View.GONE);
        controls.setVisibility(ex ? View.VISIBLE : View.GONE);
        play.setType(Hub.playing ? Glyph.PAUSE : Glyph.PLAY);
        setPillWidth(ex);
        updateProg();
    }

    void updateProg() {
        prog.set(Hub.dur > 0 ? (float) Hub.position() / Hub.dur : 0f, accent);
    }

    void tickBars() {
        if (getVisibility() != View.VISIBLE) return;
        boolean show = top.getVisibility() == View.VISIBLE;
        for (int i = 0; i < 4; i++) {
            float t = (show && Hub.playing) ? 0.2f + rnd.nextFloat() * 0.8f : 0.2f;
            bar[i].animate().scaleY(t).setDuration(130).start();
        }
        if ((expanded || embedded) && show) updateProg();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Hub.listeners.add(refresher);
        refresh();
        h.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Hub.listeners.remove(refresher);
        h.removeCallbacks(ticker);
        h.removeCallbacks(refresher);
        h.removeCallbacks(collapser);
    }
}
