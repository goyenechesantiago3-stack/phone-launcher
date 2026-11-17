package com.mi.launcher;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LockActivity extends Activity {

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    SharedPreferences sp;
    float dens;
    Typeface font;
    int accent;
    TextView clockTv, dateTv;
    DotClockView dotClock;
    LinearLayout notifBox;
    GestureDetector gd;
    BroadcastReceiver rx;
    boolean torchOn;
    final Handler h = new Handler(Looper.getMainLooper());

    final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            h.postDelayed(this, 10000);
        }
    };
    final Runnable hubCb = new Runnable() {
        @Override public void run() { fillNotifs(); }
    };

    int dp(float v) { return (int) (v * dens + 0.5f); }

    TextView tv(float size, int color) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(font);
        t.setShadowLayer(dp(4), 0, dp(1), 0x66000000);
        return t;
    }

    GradientDrawable glass(int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        g.setStroke(dp(1), stroke);
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        sp = getSharedPreferences("launcher", MODE_PRIVATE);
        dens = getResources().getDisplayMetrics().density;
        font = Hub.font(this);
        accent = sp.getInt("accent", 0xFFD71921);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x66000000);
        setContentView(root);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(20), dp(60), dp(20), dp(120));

        TextView lockIcon = tv(18, 0xCCFFFFFF);
        lockIcon.setText("\uD83D\uDD12");
        col.addView(lockIcon);

        int style = sp.getInt("clock", 0);
        if (style == 1) {
            dotClock = new DotClockView(this);
            dotClock.setCell(dp(9));
            dotClock.setColors(Color.WHITE, 0x22FFFFFF);
            col.addView(dotClock, new LinearLayout.LayoutParams(WRAP, WRAP));
        } else {
            clockTv = tv(style == 2 ? 92 : 84, Color.WHITE);
            if (style == 2) clockTv.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
            else clockTv.setTypeface(font, Typeface.BOLD);
            col.addView(clockTv);
        }
        dateTv = tv(16, 0xCCFFFFFF);
        col.addView(dateTv);

        IslandView card = new IslandView(this);
        card.setEmbedded(true, true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
        clp.topMargin = dp(28);
        col.addView(card, clp);

        notifBox = new LinearLayout(this);
        notifBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        nlp.topMargin = dp(16);
        col.addView(notifBox, nlp);

        root.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Barra inferior: linterna, texto y cámara
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(28), 0, dp(28), dp(36));
        TextView torch = round("\uD83D\uDD26");
        torch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTorch(); }
        });
        TextView hint = tv(13, 0xCCFFFFFF);
        hint.setText("Desliza hacia arriba para desbloquear");
        hint.setGravity(Gravity.CENTER);
        TextView cam = round("\uD83D\uDCF7");
        cam.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCamera(); }
        });
        bottom.addView(torch, new LinearLayout.LayoutParams(dp(52), dp(52)));
        bottom.addView(hint, new LinearLayout.LayoutParams(0, WRAP, 1f));
        bottom.addView(cam, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(bottom, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY();
                if (dy < -dp(90) && vy < -700) {
                    unlock();
                    return true;
                }
                return false;
            }
        });
        gd.setIsLongpressEnabled(false);

        rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { finish(); }
        };
        registerReceiver(rx, new IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_EXPORTED);

        Hub.listeners.add(hubCb);
        updateClock();
        fillNotifs();
    }

    TextView round(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setBackground(glass(0x44000000, 0x55FFFFFF, 26));
        return t;
    }

    void updateClock() {
        Date now = new Date();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        String t = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now);
        if (clockTv != null) clockTv.setText(t);
        if (dotClock != null) dotClock.setText(t);
        String ds = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "MX")).format(now);
        dateTv.setText(ds.substring(0, 1).toUpperCase(new Locale("es")) + ds.substring(1));
    }

    void fillNotifs() {
        notifBox.removeAllViews();
        StatusBarNotification[] arr = null;
        try {
            if (Hub.svc != null) arr = Hub.svc.getActiveNotifications();
        } catch (Exception e) {
            arr = null;
        }
        if (arr == null) return;
        int n = 0;
        for (StatusBarNotification sbn : arr) {
            if (n >= 4) break;
            if (sbn.getPackageName().equals(getPackageName())) continue;
            Notification nt = sbn.getNotification();
            if ((nt.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            CharSequence t1 = nt.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence t2 = nt.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (t1 == null && t2 == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackground(glass(0x33FFFFFF, 0x44FFFFFF, 18));
            TextView a = tv(14, Color.WHITE);
            a.setTypeface(font, Typeface.BOLD);
            a.setSingleLine(true);
            a.setEllipsize(TextUtils.TruncateAt.END);
            a.setText(t1 == null ? "" : t1);
            TextView b = tv(13, 0xCCFFFFFF);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setText(t2 == null ? "" : t2);
            row.addView(a);
            row.addView(b);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.bottomMargin = dp(8);
            notifBox.addView(row, lp);
            n++;
        }
    }

    void unlock() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { finish(); }
            });
        } catch (Exception e) {
            finish();
        }
    }

    void toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String id = cm.getCameraIdList()[0];
            torchOn = !torchOn;
            cm.setTorchMode(id, torchOn);
        } catch (Exception e) {
            torchOn = false;
            Toast.makeText(this, "No se pudo usar la linterna", Toast.LENGTH_SHORT).show();
        }
    }

    void openCamera() {
        try {
            Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            try {
                Intent i2 = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i2);
            } catch (Exception e2) {
                Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gd != null) gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() { }

    @Override
    protected void onResume() {
        super.onResume();
        h.removeCallbacks(tick);
        h.post(tick);
        fillNotifs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Hub.listeners.remove(hubCb);
        try {
            unregisterReceiver(rx);
        } catch (Exception e) {
            // nada
        }
    }
}
