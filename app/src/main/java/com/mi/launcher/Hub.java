package com.mi.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Hub {
    static IslandService svc;
    static MediaController mc;
    static String title, artist, mediaPkg;
    static Bitmap cover;
    static boolean playing;
    static long pos, dur, posTime;
    static float speed = 1f;
    static final Set<String> notifPkgs = new HashSet<>();
    static String peekTitle, peekText, peekPkg;
    static long peekUntil, chargeUntil;
    static int battPct;
    static boolean launcherVisible;
    static final List<Runnable> listeners = new ArrayList<>();
    static final Handler main = new Handler(Looper.getMainLooper());

    static boolean hasMedia() { return title != null; }
    static boolean hasPeek() { return SystemClock.uptimeMillis() < peekUntil; }
    static boolean hasCharge() { return SystemClock.uptimeMillis() < chargeUntil; }

    static long position() {
        if (!playing) return pos;
        long p = pos + (long) ((SystemClock.elapsedRealtime() - posTime) * speed);
        return dur > 0 ? Math.min(p, dur) : p;
    }

    static void changed() {
        main.post(new Runnable() {
            @Override public void run() {
                for (Runnable r : new ArrayList<>(listeners)) r.run();
            }
        });
    }

    static Typeface font(Context c) {
        String p = c.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString("font", null);
        if (p != null) {
            File f = new File(p);
            if (f.exists()) {
                try {
                    return Typeface.createFromFile(f);
                } catch (Exception e) {
                    // se usa la fuente por defecto
                }
            }
        }
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }
}
