package com.mi.launcher;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

public class IslandService extends NotificationListenerService {

    MediaSessionManager msm;
    ComponentName me;
    final List<MediaController> ctrls = new ArrayList<>();
    WindowManager wm;
    IslandView overlay;
    SharedPreferences sp;
    BroadcastReceiver rx;

    final Runnable upd = new Runnable() {
        @Override public void run() { updateOverlay(); }
    };

    final MediaSessionManager.OnActiveSessionsChangedListener sessListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> list) {
                    setControllers(list);
                }
            };

    final MediaController.Callback cb = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { pickMedia(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { pickMedia(); }
        @Override public void onSessionDestroyed() { pickMedia(); }
    };

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Hub.svc = this;
        sp = getSharedPreferences("launcher", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        me = new ComponentName(this, IslandService.class);
        try {
            msm = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            msm.addOnActiveSessionsChangedListener(sessListener, me);
            setControllers(msm.getActiveSessions(me));
        } catch (Exception e) {
            // sin acceso a sesiones
        }
        refreshNotifs();

        rx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                    if (sp.getBoolean("lock_on", false) && Settings.canDrawOverlays(IslandService.this)) {
                        try {
                            Intent li = new Intent(IslandService.this, LockActivity.class);
                            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(li);
                        } catch (Exception e) {
                            // no se pudo abrir el bloqueo
                        }
                    }
                } else if (Intent.ACTION_POWER_CONNECTED.equals(a)) {
                    Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    int lvl = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    int sc = b == null ? 100 : b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    Hub.battPct = sc > 0 ? lvl * 100 / sc : lvl;
                    Hub.chargeUntil = SystemClock.uptimeMillis() + 3500;
                    Hub.changed();
                    Hub.main.postDelayed(new Runnable() {
                        @Override public void run() { Hub.changed(); }
                    }, 3600);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_POWER_CONNECTED);
        registerReceiver(rx, f, Context.RECEIVER_EXPORTED);

        Hub.listeners.add(upd);
        updateOverlay();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Hub.listeners.remove(upd);
        try {
            if (rx != null) unregisterReceiver(rx);
        } catch (Exception e) {
            // nada
        }
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sessListener);
        } catch (Exception e) {
            // nada
        }
        removeOverlay();
        if (Hub.svc == this) Hub.svc = null;
    }

    void setControllers(List<MediaController> l) {
        for (MediaController c : ctrls) {
            try {
                c.unregisterCallback(cb);
            } catch (Exception e) {
                // nada
            }
        }
        ctrls.clear();
        if (l != null) {
            for (MediaController c : l) {
                ctrls.add(c);
                try {
                    c.registerCallback(cb);
                } catch (Exception e) {
                    // nada
                }
            }
        }
        pickMedia();
    }

    void pickMedia() {
        MediaController best = null;
        for (MediaController c : ctrls) {
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING && c.getMetadata() != null) {
                best = c;
                break;
            }
            if (best == null && c.getMetadata() != null) best = c;
        }
        if (best == null) {
            Hub.mc = null;
            Hub.title = null;
            Hub.artist = null;
            Hub.cover = null;
            Hub.playing = false;
            Hub.mediaPkg = null;
        } else {
            MediaMetadata m = best.getMetadata();
            PlaybackState s = best.getPlaybackState();
            Hub.mc = best;
            Hub.mediaPkg = best.getPackageName();
            String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            Hub.title = t == null ? "Reproduciendo" : t;
            Hub.artist = a;
            Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            Hub.cover = b;
            Hub.dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (s != null) {
                Hub.playing = s.getState() == PlaybackState.STATE_PLAYING;
                Hub.pos = s.getPosition();
                Hub.posTime = s.getLastPositionUpdateTime();
                Hub.speed = s.getPlaybackSpeed();
            } else {
                Hub.playing = false;
            }
        }
        Hub.changed();
    }

    void refreshNotifs() {
        Hub.notifPkgs.clear();
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                if (!sbn.getPackageName().equals(getPackageName())) Hub.notifPkgs.add(sbn.getPackageName());
            }
        } catch (Exception e) {
            // nada
        }
        Hub.changed();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refreshNotifs();
        if (sbn.getPackageName().equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_GROUP_SUMMARY)) != 0) return;
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return;
        Bundle ex = n.extras;
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (t == null && x == null) return;
        Hub.peekPkg = sbn.getPackageName();
        Hub.peekTitle = t == null ? null : t.toString();
        Hub.peekText = x == null ? null : x.toString();
        Hub.peekUntil = SystemClock.uptimeMillis() + 4000;
        Hub.changed();
        Hub.main.postDelayed(new Runnable() {
            @Override public void run() { Hub.changed(); }
        }, 4100);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refreshNotifs();
    }

    void updateOverlay() {
        boolean want = sp != null
                && sp.getBoolean("island_overlay", true)
                && Settings.canDrawOverlays(this)
                && !Hub.launcherVisible
                && (Hub.hasMedia() || Hub.hasPeek() || Hub.hasCharge());
        if (want && overlay == null) {
            IslandView v = new IslandView(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) (8 * getResources().getDisplayMetrics().density);
            try {
                wm.addView(v, lp);
                overlay = v;
            } catch (Exception e) {
                overlay = null;
            }
        } else if (!want && overlay != null) {
            removeOverlay();
        }
    }

    void removeOverlay() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception e) {
                // nada
            }
            overlay = null;
        }
    }
}
