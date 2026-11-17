package com.mi.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.role.RoleManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {

    static final int REQ_ICON = 1, REQ_FONT = 2, REQ_ROLE = 3, REQ_BIND = 4,
            REQ_CONF = 5, REQ_EXPORT = 6, REQ_IMPORT = 7;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    static final int[] GLASS_A = {0x1F, 0x33, 0x55};
    static final int[] ISIZE = {48, 56, 64};

    static class AppItem {
        String pkg;
        String cls;
        String label;
        ResolveInfo ri;
    }

    PackageManager pm;
    SharedPreferences sp;
    float dens;
    int icoPx, statusH, navH, topPad, sw, sh, accent, lastDot = -1;
    FrameLayout root, content, drawer, panel;
    LinearLayout pagesLL, dock, dots, notifBox;
    SnapScroll pager;
    TextView clockTv, greetTv, dateTv, tvTime, tvNet, tvBatt, calDay, calMon, battTv;
    DotClockView dotClock;
    BarView battBar;
    IslandView island;
    GridView grid;
    EditText search;
    AppAdapter adapter;
    AppWidgetManager awm;
    AppWidgetHost awHost;
    AlertDialog openDlg;
    final List<AppItem> apps = new ArrayList<>();
    final Map<String, Bitmap> cache = new HashMap<>();
    Typeface font = Typeface.DEFAULT;
    boolean drawerOpen, panelOpen, showHidden, torchOn;
    String pendingPkg;
    int pendingWidget = -1;
    AppWidgetProviderInfo pendingInfo;
    final Handler h = new Handler(Looper.getMainLooper());
    GestureDetector gd, dtap;
    BroadcastReceiver pkgReceiver;

    final Runnable refreshRun = new Runnable() {
        @Override public void run() { refreshAll(); }
    };
    final Runnable hubCb = new Runnable() {
        @Override public void run() {
            h.removeCallbacks(refreshRun);
            h.postDelayed(refreshRun, 300);
        }
    };
    final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            updateStatus();
            h.postDelayed(this, 10000);
        }
    };

    // ------------------------------------------------------------ utilidades

    int dp(float v) { return (int) (v * dens + 0.5f); }

    int sysDimen(String n) {
        int id = getResources().getIdentifier(n, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    int glassFill() { return (GLASS_A[sp.getInt("glass", 1)] << 24) | 0xFFFFFF; }

    GradientDrawable glass(int fill, int stroke, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(rDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    TextView tv(float size, int color) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setShadowLayer(dp(4), 0, dp(1), 0x66000000);
        t.setTypeface(font);
        return t;
    }

    void computeSizes() {
        icoPx = dp(ISIZE[sp.getInt("isize", 1)]);
        accent = sp.getInt("accent", 0xFFD71921);
        topPad = sp.getBoolean("custom_bar", true) ? dp(14) : statusH;
    }

    void menu(String title, String[] o, final IntConsumer cb) {
        dlg().setTitle(title).setItems(o, (dg, w) -> cb.accept(w)).show();
    }

    String chk(boolean on, String s) { return (on ? "\u2713 " : "    ") + s; }

    String upper(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(new Locale("es")) + s.substring(1);
    }

    String appLabel(String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    // ------------------------------------------------------------ datos

    List<String> csv(String s) {
        List<String> l = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            for (String p : s.split(",")) {
                if (!p.isEmpty()) l.add(p);
            }
        }
        return l;
    }

    void putCsv(String k, List<String> l) {
        sp.edit().putString(k, TextUtils.join(",", l)).apply();
    }

    List<String> dockList() { return csv(sp.getString("dock", "")); }
    void saveDock(List<String> l) { putCsv("dock", l); }
    List<String> pageItems(int i) { return csv(sp.getString("hp_" + i, "")); }
    void savePage(int i, List<String> l) { putCsv("hp_" + i, l); }

    Set<String> sset(String k) {
        return new HashSet<>(sp.getStringSet(k, new HashSet<String>()));
    }

    void putSet(String k, Set<String> s) { sp.edit().putStringSet(k, s).apply(); }

    String labelOf(AppItem a) { return sp.getString("lbl_" + a.pkg, a.label); }

    AppItem find(String pkg) {
        for (AppItem a : apps) {
            if (a.pkg.equals(pkg)) return a;
        }
        return null;
    }

    int nPages() { return sp.getInt("npages", 2); }

    int pageCap(int i) { return sp.getInt("cols", 4) * (i == 0 ? 3 : 5); }

    void loadApps() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
        apps.clear();
        for (ResolveInfo r : l) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            AppItem a = new AppItem();
            a.pkg = r.activityInfo.packageName;
            a.cls = r.activityInfo.name;
            a.label = r.loadLabel(pm).toString();
            a.ri = r;
            apps.add(a);
        }
    }

    void initDefaults() {
        if (sp.getBoolean("init2", false)) return;
        List<String> dk = new ArrayList<>();
        addDefault(dk, new Intent(Intent.ACTION_DIAL));
        Intent sms = new Intent(Intent.ACTION_MAIN);
        sms.addCategory(Intent.CATEGORY_APP_MESSAGING);
        addDefault(dk, sms);
        Intent br = new Intent(Intent.ACTION_MAIN);
        br.addCategory(Intent.CATEGORY_APP_BROWSER);
        addDefault(dk, br);
        addDefault(dk, new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE));
        saveDock(dk);

        List<String> hp = new ArrayList<>();
        String[] cats = {Intent.CATEGORY_APP_GALLERY, Intent.CATEGORY_APP_MUSIC, Intent.CATEGORY_APP_MAPS,
                Intent.CATEGORY_APP_CALENDAR, Intent.CATEGORY_APP_CONTACTS, Intent.CATEGORY_APP_EMAIL,
                Intent.CATEGORY_APP_CALCULATOR};
        for (String c : cats) {
            Intent it = new Intent(Intent.ACTION_MAIN);
            it.addCategory(c);
            addDefault(hp, it);
        }
        addDefault(hp, new Intent(Settings.ACTION_SETTINGS));
        hp.removeAll(dk);
        savePage(0, hp);
        sp.edit().putBoolean("init2", true).apply();
    }

    void addDefault(List<String> l, Intent i) {
        ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri != null && ri.activityInfo != null) {
            String p = ri.activityInfo.packageName;
            if (!p.equals("android") && find(p) != null && !l.contains(p)) l.add(p);
        }
    }

    // ------------------------------------------------------------ íconos

    float shapeRadius() {
        int s = sp.getInt("shape", 1);
        if (s == 0) return 0.5f;
        if (s == 1) return 0.30f;
        return 0.14f;
    }

    Bitmap shaped(Bitmap src) {
        int s = icoPx;
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float r = shapeRadius() * s;
        c.drawRoundRect(new RectF(0, 0, s, s), r, r, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        if (sp.getBoolean("mono", false)) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0f);
            cm.postConcat(new ColorMatrix(new float[]{
                    1.25f, 0, 0, 0, -15,
                    0, 1.25f, 0, 0, -15,
                    0, 0, 1.25f, 0, -15,
                    0, 0, 0, 1, 0}));
            p.setColorFilter(new ColorMatrixColorFilter(cm));
        }
        Bitmap scaled = (src.getWidth() == s && src.getHeight() == s)
                ? src : Bitmap.createScaledBitmap(src, s, s, true);
        c.drawBitmap(scaled, 0, 0, p);
        return out;
    }

    Bitmap icon(AppItem a) {
        Bitmap b = cache.get(a.pkg);
        if (b != null) return b;
        File f = new File(new File(getFilesDir(), "icons"), a.pkg + ".png");
        Bitmap src = null;
        if (f.exists()) src = BitmapFactory.decodeFile(f.getPath());
        if (src == null) {
            Drawable dr = a.ri.loadIcon(pm);
            src = Bitmap.createBitmap(icoPx, icoPx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(src);
            dr.setBounds(0, 0, icoPx, icoPx);
            dr.draw(c);
        }
        b = shaped(src);
        cache.put(a.pkg, b);
        return b;
    }

    Bitmap decodeUri(Uri u, int target) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int s = 1;
        while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        in = getContentResolver().openInputStream(u);
        Bitmap b = BitmapFactory.decodeStream(in, null, o2);
        in.close();
        return b;
    }

    // ------------------------------------------------------------ abrir apps

    void auth(final Runnable ok) {
        try {
            BiometricPrompt bp = new BiometricPrompt.Builder(this)
                    .setTitle("Confirma tu identidad")
                    .setNegativeButton("Cancelar", getMainExecutor(), (dg, w) -> { })
                    .build();
            bp.authenticate(new CancellationSignal(), getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                            ok.run();
                        }
                    });
        } catch (Exception e) {
            toast("La huella no está disponible");
        }
    }

    void launch(AppItem a) { launch(a, null); }

    void launch(final AppItem a, final View src) {
        if (sset("locked").contains(a.pkg)) {
            auth(() -> doLaunch(a, src));
            return;
        }
        doLaunch(a, src);
    }

    void doLaunch(AppItem a, View src) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName(a.pkg, a.cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Bundle opt = null;
            int an = sp.getInt("anim", 0);
            if (an == 0 && src != null) {
                opt = ActivityOptions.makeScaleUpAnimation(src, 0, 0, src.getWidth(), src.getHeight()).toBundle();
            } else if (an == 1) {
                opt = ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle();
            }
            if (opt != null) startActivity(i, opt);
            else startActivity(i);
            if (openDlg != null && openDlg.isShowing()) openDlg.dismiss();
            if (drawerOpen) closeDrawer();
            if (panelOpen) closePanel();
        } catch (Exception e) {
            toast("No se pudo abrir la app");
        }
    }

    void doubleTapLock() {
        if (!sp.getBoolean("dtap", false)) return;
        if (!LockAccessibility.lock()) toast("Activa Mi Launcher en Accesibilidad (Ajustes > Gestos)");
    }

    // ------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        sp = getSharedPreferences("launcher", MODE_PRIVATE);
        dens = getResources().getDisplayMetrics().density;
        sw = getResources().getDisplayMetrics().widthPixels;
        sh = getResources().getDisplayMetrics().heightPixels;
        statusH = sysDimen("status_bar_height");
        navH = sysDimen("navigation_bar_height");

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        awm = AppWidgetManager.getInstance(this);
        awHost = new AppWidgetHost(this, 1024);
        font = Hub.font(this);
        loadApps();
        initDefaults();

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) < Math.abs(dx) * 1.5f || Math.abs(dy) < dp(80)) return false;
                if (panelOpen) {
                    if (dy < 0 && vy < -800 && e1.getY() > sh * 0.7f) {
                        closePanel();
                        return true;
                    }
                    return false;
                }
                if (drawerOpen) {
                    if (dy > 0 && vy > 800 && gridAtTop()) {
                        closeDrawer();
                        return true;
                    }
                    return false;
                }
                if (dy < 0 && vy < -800) {
                    openDrawer(true, false);
                    return true;
                }
                if (dy > 0 && vy > 800) {
                    if (e1.getX() > sw * 0.5f) openPanel();
                    else openDrawer(true, true);
                    return true;
                }
                return false;
            }
        });
        gd.setIsLongpressEnabled(false);

        dtap = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                doubleTapLock();
                return true;
            }
        });

        buildUi();
        registerPkgReceiver();
        Hub.listeners.add(hubCb);

        if (!sp.getBoolean("role_asked", false)) {
            sp.edit().putBoolean("role_asked", true).apply();
            h.postDelayed(new Runnable() {
                @Override public void run() { requestHome(false); }
            }, 800);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            awHost.startListening();
        } catch (Exception e) {
            // nada
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            awHost.stopListening();
        } catch (Exception e) {
            // nada
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Hub.launcherVisible = true;
        Hub.changed();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Hub.launcherVisible = false;
        Hub.changed();
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Hub.listeners.remove(hubCb);
        h.removeCallbacks(tick);
        h.removeCallbacks(refreshRun);
        try {
            if (pkgReceiver != null) unregisterReceiver(pkgReceiver);
        } catch (Exception e) {
            // nada
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (drawerOpen) closeDrawer();
        if (panelOpen) closePanel();
        if (pager != null) pager.goTo(1, true);
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) closePanel();
        else if (drawerOpen) closeDrawer();
        else if (pager != null) pager.goTo(1, true);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gd != null) gd.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    void registerPkgReceiver() {
        pkgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                loadApps();
                cache.clear();
                refreshAll();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
    }

    boolean gridAtTop() {
        if (grid == null || grid.getChildCount() == 0) return true;
        return grid.getFirstVisiblePosition() == 0
                && grid.getChildAt(0).getTop() >= grid.getPaddingTop();
    }

    void requestHome(boolean verbose) {
        try {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE);
            } else if (verbose) {
                toast("Ya es tu launcher predeterminado");
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e2) {
                toast("Ve a Ajustes > Apps > Apps predeterminadas > Aplicación de inicio");
            }
        }
    }

    // ------------------------------------------------------------ interfaz

    class SnapScroll extends HorizontalScrollView {
        int pageCount = 1;
        int startPage = 0;

        SnapScroll(Context c) { super(c); }

        int curPage() { return sw == 0 ? 0 : Math.round(getScrollX() / (float) sw); }

        void goTo(int p, boolean anim) {
            p = Math.max(0, Math.min(pageCount - 1, p));
            if (anim) smoothScrollTo(p * sw, 0);
            else scrollTo(p * sw, 0);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) startPage = curPage();
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            boolean r = super.onTouchEvent(ev);
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                float dx = getScrollX() - startPage * sw;
                int t = startPage;
                if (dx > sw * 0.15f) t++;
                else if (dx < -sw * 0.15f) t--;
                goTo(t, true);
                return true;
            }
            return r;
        }

        @Override
        public void fling(int velocityX) { }

        @Override
        protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            updateDots();
        }
    }

    void buildUi() {
        drawerOpen = false;
        panelOpen = false;
        computeSizes();
        if (sp.getBoolean("custom_bar", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        root = new FrameLayout(this);
        setContentView(root);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));

        pager = new SnapScroll(this);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pagesLL = new LinearLayout(this);
        pagesLL.setOrientation(LinearLayout.HORIZONTAL);
        pager.addView(pagesLL, new FrameLayout.LayoutParams(WRAP, MATCH));
        content.addView(pager, new FrameLayout.LayoutParams(MATCH, MATCH));

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotLp.bottomMargin = navH + dp(128);
        content.addView(dots, dotLp);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setPadding(dp(10), dp(10), dp(10), dp(10));
        dock.setBackground(glass(glassFill(), 0x66FFFFFF, 30));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = navH + dp(30);
        content.addView(dock, dlp);

        View pill = new View(this);
        pill.setBackground(glass(0x88FFFFFF, 0x00000000, 3));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(44), dp(5),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        plp.bottomMargin = navH + dp(12);
        content.addView(pill, plp);

        if (sp.getBoolean("custom_bar", true)) {
            LinearLayout sb = new LinearLayout(this);
            sb.setOrientation(LinearLayout.HORIZONTAL);
            sb.setGravity(Gravity.CENTER_VERTICAL);
            sb.setPadding(dp(24), 0, dp(24), 0);
            tvTime = tv(14, Color.WHITE);
            tvTime.setTypeface(font, Typeface.BOLD);
            View sp1 = new View(this);
            tvNet = tv(12, 0xDDFFFFFF);
            tvBatt = tv(12, Color.WHITE);
            tvBatt.setPadding(dp(10), 0, 0, 0);
            sb.addView(tvTime);
            sb.addView(sp1, new LinearLayout.LayoutParams(0, 1, 1f));
            sb.addView(tvNet);
            sb.addView(tvBatt);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(MATCH, dp(28), Gravity.TOP);
            slp.topMargin = dp(8);
            content.addView(sb, slp);
        } else {
            tvTime = null;
            tvNet = null;
            tvBatt = null;
        }

        buildPages();
        buildDrawer();
        buildPanel();

        island = new IslandView(this);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ilp.topMargin = Math.max(dp(6), topPad - dp(6));
        root.addView(island, ilp);

        fillDock();
        applyOneHand();
        updateClock();
        updateStatus();
        pager.post(new Runnable() {
            @Override public void run() { pager.goTo(Math.min(1, pager.pageCount - 1), false); }
        });
    }

    void rebuildUi() {
        boolean wasDrawer = drawerOpen;
        buildUi();
        if (wasDrawer) openDrawer(false, false);
    }

    void applyOneHand() {
        if (sp.getBoolean("onehand", false)) {
            content.setPivotX(sw);
            content.setPivotY(sh);
            content.setScaleX(0.78f);
            content.setScaleY(0.78f);
        } else {
            content.setScaleX(1f);
            content.setScaleY(1f);
        }
    }

    LinearLayout.LayoutParams pageLp() {
        return new LinearLayout.LayoutParams(sw, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    void buildPages() {
        pagesLL.removeAllViews();
        pagesLL.addView(widgetPage(), pageLp());
        fillHomePages();
    }

    void fillHomePages() {
        while (pagesLL.getChildCount() > 1) pagesLL.removeViewAt(1);
        int n = nPages();
        for (int i = 0; i < n; i++) pagesLL.addView(homePage(i), pageLp());
        pager.pageCount = n + 1;
        lastDot = -1;
        updateDots();
    }

    void rebuildPages() {
        final int cur = pager.curPage();
        buildPages();
        pager.post(new Runnable() {
            @Override public void run() { pager.goTo(cur, false); }
        });
    }

    void updateDots() {
        if (dots == null || pager == null) return;
        int n = pager.pageCount;
        int cur = sw == 0 ? 0 : Math.round(pager.getScrollX() / (float) sw);
        if (cur == lastDot && dots.getChildCount() == n) return;
        lastDot = cur;
        if (dots.getChildCount() != n) {
            dots.removeAllViews();
            for (int k = 0; k < n; k++) {
                View v = new View(this);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(7), dp(7));
                p.setMargins(dp(4), 0, dp(4), 0);
                dots.addView(v, p);
            }
        }
        for (int k = 0; k < n; k++) {
            dots.getChildAt(k).setBackground(glass(k == cur ? 0xFFFFFFFF : 0x55FFFFFF, 0x00000000, 4));
        }
    }

    // ---- páginas de inicio

    View homePage(final int i) {
        FrameLayout pg = new FrameLayout(this);
        pg.setClickable(true);
        pg.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent ev) {
                dtap.onTouchEvent(ev);
                return false;
            }
        });
        pg.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showSettings();
                return true;
            }
        });
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), topPad + dp(70), dp(10), navH + dp(150));

        if (i == 0) {
            clockTv = null;
            dotClock = null;
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.VERTICAL);
            header.setGravity(Gravity.CENTER_HORIZONTAL);
            greetTv = tv(18, Color.WHITE);
            header.addView(greetTv);
            int style = sp.getInt("clock", 0);
            View clockView;
            if (style == 1) {
                dotClock = new DotClockView(this);
                dotClock.setCell(dp(9));
                dotClock.setColors(Color.WHITE, 0x22FFFFFF);
                clockView = dotClock;
                header.addView(dotClock, new LinearLayout.LayoutParams(WRAP, WRAP));
            } else {
                clockTv = tv(style == 2 ? 92 : 84, Color.WHITE);
                if (style == 2) clockTv.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                else clockTv.setTypeface(font, Typeface.BOLD);
                clockView = clockTv;
                header.addView(clockTv);
            }
            clockView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    showSettings();
                    return true;
                }
            });
            dateTv = tv(16, 0xCCFFFFFF);
            header.addView(dateTv);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(MATCH, WRAP);
            hp.bottomMargin = dp(18);
            col.addView(header, hp);
        }

        int cols = sp.getInt("cols", 4);
        List<String> items = pageItems(i);
        LinearLayout row = null;
        int k = 0;
        for (String token : items) {
            View cell = itemCell(token);
            if (cell == null) continue;
            if (k % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            row.addView(cell, new LinearLayout.LayoutParams(0, WRAP, 1f));
            k++;
        }
        pg.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        return pg;
    }

    View itemCell(String token) {
        if (token.startsWith("f:")) return folderCell(token);
        AppItem a = find(token);
        if (a == null) return null;
        return appCell(a, true);
    }

    View appCell(final AppItem a, boolean label) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER_HORIZONTAL);
        ll.setPadding(dp(2), dp(4), dp(2), dp(6));
        FrameLayout fl = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(icon(a));
        fl.addView(iv, new FrameLayout.LayoutParams(icoPx, icoPx, Gravity.CENTER));
        if (Hub.notifPkgs.contains(a.pkg)) {
            View dot = new View(this);
            dot.setBackground(glass(accent, 0xFF000000, 6));
            fl.addView(dot, new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP | Gravity.END));
        }
        ll.addView(fl, new LinearLayout.LayoutParams(icoPx + dp(10), icoPx));
        if (label && sp.getBoolean("labels", true)) {
            TextView t = tv(11, Color.WHITE);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(2), dp(5), dp(2), 0);
            t.setText(labelOf(a));
            ll.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        ll.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                int ac = e.getActionMasked();
                if (ac == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
                else if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                return false;
            }
        });
        ll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(a, v); }
        });
        ll.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                showAppMenu(a);
                return true;
            }
        });
        return ll;
    }

    View folderCell(final String fid) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER_HORIZONTAL);
        ll.setPadding(dp(2), dp(4), dp(2), dp(6));
        FrameLayout box = new FrameLayout(this);
        box.setBackground(glass(glassFill(), 0x66FFFFFF, 14));
        int small = (int) (icoPx * 0.40f);
        int gap = (icoPx - small * 2) / 3;
        List<String> ps = folderApps(fid);
        for (int k = 0; k < 4 && k < ps.size(); k++) {
            AppItem a = find(ps.get(k));
            if (a == null) continue;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(icon(a));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(small, small);
            lp.leftMargin = gap + (k % 2) * (small + gap);
            lp.topMargin = gap + (k / 2) * (small + gap);
            box.addView(iv, lp);
        }
        ll.addView(box, new LinearLayout.LayoutParams(icoPx, icoPx));
        if (sp.getBoolean("labels", true)) {
            TextView t = tv(11, Color.WHITE);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(2), dp(5), dp(2), 0);
            t.setText(folderName(fid));
            ll.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        ll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFolder(fid); }
        });
        ll.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                folderMenu(fid);
                return true;
            }
        });
        return ll;
    }

    void fillDock() {
        if (dock == null) return;
        dock.removeAllViews();
        int count = 0;
        for (String p : dockList()) {
            AppItem a = find(p);
            if (a == null) continue;
            dock.addView(appCell(a, false), new LinearLayout.LayoutParams(WRAP, WRAP));
            count++;
        }
        dock.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    void refreshAll() {
        if (pagesLL == null) return;
        fillDock();
        fillHomePages();
        updateClock();
        if (adapter != null && search != null) adapter.filter(search.getText().toString());
        if (panelOpen) fillNotifs();
    }

    // ---- página de widgets

    View widgetPage() {
        FrameLayout pg = new FrameLayout(this);
        pg.setClickable(true);
        pg.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent ev) {
                dtap.onTouchEvent(ev);
                return false;
            }
        });
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(dp(16), topPad + dp(70), dp(16), navH + dp(150));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView head = tv(26, Color.WHITE);
        head.setTypeface(font, Typeface.BOLD);
        head.setText("Widgets");
        col.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(130), 1f);
        l1.rightMargin = dp(6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(130), 1f);
        l2.leftMargin = dp(6);
        row.addView(calendarCard(), l1);
        row.addView(batteryCard(), l2);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = dp(14);
        col.addView(row, rlp);

        IslandView mc = new IslandView(this);
        mc.setEmbedded(true, false);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        mlp.topMargin = dp(12);
        col.addView(mc, mlp);

        for (String sid : csv(sp.getString("awids", ""))) {
            try {
                int id = Integer.parseInt(sid);
                AppWidgetProviderInfo info = awm.getAppWidgetInfo(id);
                if (info == null) continue;
                AppWidgetHostView hv = awHost.createView(this, id, info);
                LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(MATCH,
                        Math.max(info.minHeight, dp(70)));
                wlp.topMargin = dp(12);
                col.addView(hv, wlp);
            } catch (Exception e) {
                // widget no disponible
            }
        }

        TextView add = tv(15, Color.WHITE);
        add.setText("+  Agregar widget de Android");
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(12), dp(14), dp(12), dp(14));
        add.setBackground(glass(glassFill(), 0x66FFFFFF, 20));
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickWidget(); }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(MATCH, WRAP);
        alp.topMargin = dp(16);
        col.addView(add, alp);

        sv.addView(col);
        pg.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        return pg;
    }

    View calendarCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setBackground(glass(glassFill(), 0x66FFFFFF, 24));
        calMon = tv(13, accent);
        calDay = tv(46, Color.WHITE);
        calDay.setTypeface(font, Typeface.BOLD);
        c.addView(calMon);
        c.addView(calDay);
        return c;
    }

    View batteryCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(16), dp(8), dp(16), dp(8));
        c.setBackground(glass(glassFill(), 0x66FFFFFF, 24));
        TextView t = tv(13, accent);
        t.setText("BATERÍA");
        battTv = tv(38, Color.WHITE);
        battTv.setTypeface(font, Typeface.BOLD);
        battBar = new BarView(this);
        c.addView(t);
        c.addView(battTv);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, dp(8));
        blp.topMargin = dp(8);
        c.addView(battBar, blp);
        return c;
    }

    // ---- reloj y estado

    void updateClock() {
        Date now = new Date();
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        String t = new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now);
        Locale es = new Locale("es", "MX");
        if (clockTv != null) clockTv.setText(t);
        if (dotClock != null) dotClock.setText(t);
        if (tvTime != null) tvTime.setText(t);
        if (greetTv != null) {
            int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            greetTv.setText(hr < 6 ? "Buenas noches" : hr < 12 ? "Buenos días"
                    : hr < 19 ? "Buenas tardes" : "Buenas noches");
        }
        if (dateTv != null) {
            dateTv.setText(upper(new SimpleDateFormat("EEEE d 'de' MMMM", es).format(now)));
        }
        if (calDay != null) {
            calDay.setText(new SimpleDateFormat("d", es).format(now));
            calMon.setText(new SimpleDateFormat("EEE \u00B7 MMM", es).format(now).toUpperCase(es));
        }
    }

    void updateStatus() {
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int lvl = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int sc = b == null ? 100 : b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int st = b == null ? -1 : b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean chg = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            int pct = sc > 0 ? lvl * 100 / sc : lvl;
            if (tvBatt != null) tvBatt.setText((chg ? "\u26A1" : "") + pct + "%");
            if (battTv != null) battTv.setText(pct + "%");
            if (battBar != null) battBar.set(pct / 100f, pct <= 20 && !chg ? 0xFFFF3B30 : accent);
            if (tvNet != null) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                String net = nc == null ? "Sin red"
                        : nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                        : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Datos" : "Red";
                tvNet.setText(net);
            }
        } catch (Exception e) {
            // nada
        }
    }

    // ---- cajón de apps

    void buildDrawer() {
        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(0xB30A0A12);
        drawer.setClickable(true);
        drawer.setVisibility(View.GONE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), topPad + dp(44), dp(16), 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        search = new EditText(this);
        search.setHint("Buscar apps");
        search.setHintTextColor(0x99FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setTypeface(font);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(18), dp(12), dp(18), dp(12));
        search.setBackground(glass(glassFill(), 0x55FFFFFF, 26));
        bar.addView(search, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView gear = tv(26, Color.WHITE);
        gear.setText("\u2699");
        gear.setPadding(dp(14), dp(6), dp(6), dp(6));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        bar.addView(gear);
        col.addView(bar);

        adapter = new AppAdapter();
        grid = new GridView(this);
        grid.setNumColumns(sp.getInt("cols", 4));
        grid.setVerticalSpacing(dp(8));
        grid.setPadding(0, dp(12), 0, navH + dp(16));
        grid.setClipToPadding(false);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                launch(adapter.list.get(pos), v);
            }
        });
        grid.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                showAppMenu(adapter.list.get(pos));
                return true;
            }
        });
        col.addView(grid, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        drawer.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(drawer, new FrameLayout.LayoutParams(MATCH, MATCH));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                if (adapter.list.size() > 0) launch(adapter.list.get(0), null);
                return true;
            }
        });
        adapter.filter("");
    }

    void blur(boolean on) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            getWindow().setBackgroundBlurRadius(on && sp.getBoolean("blur", true) ? dp(30) : 0);
        } catch (Exception e) {
            // el sistema no permite desenfoque
        }
    }

    void openDrawer(boolean anim, boolean focus) {
        drawerOpen = true;
        drawer.setVisibility(View.VISIBLE);
        adapter.filter(search.getText().toString());
        int an = sp.getInt("anim", 0);
        if (anim && an != 2) {
            drawer.setAlpha(0f);
            drawer.setTranslationY(an == 0 ? dp(140) : 0);
            ViewPropertyAnimator pa = drawer.animate().alpha(1f).translationY(0f).setDuration(250);
            if (an == 0) pa.setInterpolator(new OvershootInterpolator(0.8f));
            pa.start();
        } else {
            drawer.setAlpha(1f);
            drawer.setTranslationY(0f);
        }
        blur(true);
        if (focus) {
            search.requestFocus();
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(search, 0);
            } catch (Exception e) {
                // nada
            }
        }
    }

    void closeDrawer() {
        drawerOpen = false;
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        } catch (Exception e) {
            // nada
        }
        search.clearFocus();
        search.setText("");
        blur(false);
        int an = sp.getInt("anim", 0);
        if (an == 2) {
            drawer.setVisibility(View.GONE);
            return;
        }
        drawer.animate().alpha(0f).translationY(an == 0 ? dp(140) : 0).setDuration(180)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (!drawerOpen) drawer.setVisibility(View.GONE);
                    }
                }).start();
    }

    // ---- panel de control y notificaciones

    TextView tile(String icon, String label, final Runnable r) {
        TextView t = tv(13, Color.WHITE);
        t.setText(icon + "\n" + label);
        t.setGravity(Gravity.CENTER);
        t.setBackground(glass(glassFill(), 0x66FFFFFF, 20));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    r.run();
                } catch (Exception e) {
                    toast("No disponible");
                }
            }
        });
        return t;
    }

    LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(78), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    void toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String id = cm.getCameraIdList()[0];
            torchOn = !torchOn;
            cm.setTorchMode(id, torchOn);
        } catch (Exception e) {
            torchOn = false;
            toast("No se pudo usar la linterna");
        }
    }

    void buildPanel() {
        panel = new FrameLayout(this);
        panel.setBackgroundColor(0xB30A0A12);
        panel.setClickable(true);
        panel.setVisibility(View.GONE);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(dp(16), topPad + dp(44), dp(16), navH + dp(24));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(tile("\uD83D\uDCF6", "Internet", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
            }
        }), tileLp());
        tiles.addView(tile("\uD83D\uDD35", "Bluetooth", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
        }), tileLp());
        tiles.addView(tile("\uD83D\uDD26", "Linterna", new Runnable() {
            @Override public void run() { toggleTorch(); }
        }), tileLp());
        tiles.addView(tile("\u2699", "Ajustes", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }), tileLp());
        col.addView(tiles);

        col.addView(sliderLabel("Brillo"));
        final SeekBar br = new SeekBar(this);
        br.setMax(255);
        try {
            br.setProgress(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS));
        } catch (Exception e) {
            br.setProgress(128);
        }
        tint(br);
        br.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user) return;
                if (!Settings.System.canWrite(MainActivity.this)) {
                    toast("Concede el permiso para cambiar el brillo");
                    startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                    return;
                }
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(5, p));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(br);

        col.addView(sliderLabel("Volumen"));
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        SeekBar vol = new SeekBar(this);
        vol.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        vol.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        tint(vol);
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user) am.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        col.addView(vol);

        IslandView mc = new IslandView(this);
        mc.setEmbedded(true, true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        mlp.topMargin = dp(16);
        col.addView(mc, mlp);

        LinearLayout nh = new LinearLayout(this);
        nh.setOrientation(LinearLayout.HORIZONTAL);
        nh.setGravity(Gravity.CENTER_VERTICAL);
        TextView nt = tv(18, Color.WHITE);
        nt.setTypeface(font, Typeface.BOLD);
        nt.setText("Notificaciones");
        TextView clear = tv(13, accent);
        clear.setText("Borrar todo");
        clear.setPadding(dp(10), dp(6), 0, dp(6));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    if (Hub.svc != null) Hub.svc.cancelAllNotifications();
                } catch (Exception e) {
                    // nada
                }
                fillNotifs();
            }
        });
        nh.addView(nt, new LinearLayout.LayoutParams(0, WRAP, 1f));
        nh.addView(clear);
        LinearLayout.LayoutParams nhl = new LinearLayout.LayoutParams(MATCH, WRAP);
        nhl.topMargin = dp(20);
        col.addView(nh, nhl);

        notifBox = new LinearLayout(this);
        notifBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nbl = new LinearLayout.LayoutParams(MATCH, WRAP);
        nbl.topMargin = dp(8);
        col.addView(notifBox, nbl);

        TextView close = tv(14, 0xCCFFFFFF);
        close.setText("\u2303  Cerrar");
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(18), 0, dp(10));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closePanel(); }
        });
        col.addView(close, new LinearLayout.LayoutParams(MATCH, WRAP));

        sv.addView(col);
        panel.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(panel, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    TextView sliderLabel(String s) {
        TextView t = tv(13, 0xCCFFFFFF);
        t.setText(s);
        t.setPadding(dp(4), dp(14), 0, dp(2));
        return t;
    }

    void tint(SeekBar s) {
        s.setProgressTintList(ColorStateList.valueOf(accent));
        s.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
    }

    void openPanel() {
        panelOpen = true;
        fillNotifs();
        panel.setVisibility(View.VISIBLE);
        int an = sp.getInt("anim", 0);
        if (an != 2) {
            panel.setAlpha(0f);
            panel.setTranslationY(an == 0 ? -dp(140) : 0);
            ViewPropertyAnimator pa = panel.animate().alpha(1f).translationY(0f).setDuration(250);
            if (an == 0) pa.setInterpolator(new OvershootInterpolator(0.8f));
            pa.start();
        } else {
            panel.setAlpha(1f);
            panel.setTranslationY(0f);
        }
        blur(true);
    }

    void closePanel() {
        panelOpen = false;
        blur(false);
        int an = sp.getInt("anim", 0);
        if (an == 2) {
            panel.setVisibility(View.GONE);
            return;
        }
        panel.animate().alpha(0f).translationY(an == 0 ? -dp(140) : 0).setDuration(180)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        if (!panelOpen) panel.setVisibility(View.GONE);
                    }
                }).start();
    }

    void fillNotifs() {
        if (notifBox == null) return;
        notifBox.removeAllViews();
        StatusBarNotification[] arr = null;
        try {
            if (Hub.svc != null) arr = Hub.svc.getActiveNotifications();
        } catch (Exception e) {
            arr = null;
        }
        if (arr == null) {
            TextView t = tv(14, 0xCCFFFFFF);
            t.setText("Activa el acceso a notificaciones en Ajustes del launcher > Isla dinámica y notificaciones.");
            notifBox.addView(t);
            return;
        }
        int n = 0;
        for (final StatusBarNotification sbn : arr) {
            if (sbn.getPackageName().equals(getPackageName())) continue;
            final Notification nt = sbn.getNotification();
            if ((nt.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            CharSequence t1 = nt.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence t2 = nt.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (t1 == null && t2 == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackground(glass(glassFill(), 0x55FFFFFF, 18));
            TextView app = tv(11, accent);
            app.setText(appLabel(sbn.getPackageName()));
            TextView a = tv(14, Color.WHITE);
            a.setTypeface(font, Typeface.BOLD);
            a.setSingleLine(true);
            a.setEllipsize(TextUtils.TruncateAt.END);
            a.setText(t1 == null ? "" : t1);
            TextView b = tv(13, 0xCCFFFFFF);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setText(t2 == null ? "" : t2);
            row.addView(app);
            row.addView(a);
            row.addView(b);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        if (nt.contentIntent != null) nt.contentIntent.send();
                    } catch (Exception e) {
                        // nada
                    }
                    closePanel();
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    try {
                        Hub.svc.cancelNotification(sbn.getKey());
                    } catch (Exception e) {
                        // nada
                    }
                    fillNotifs();
                    return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.bottomMargin = dp(8);
            notifBox.addView(row, lp);
            n++;
        }
        if (n == 0) {
            TextView t = tv(14, 0xCCFFFFFF);
            t.setText("Sin notificaciones");
            notifBox.addView(t);
        }
    }

    // ---- adaptador del cajón

    class AppAdapter extends BaseAdapter {
        final List<AppItem> list = new ArrayList<>();
        Set<String> hid = new HashSet<>();

        void filter(String q) {
            list.clear();
            hid = sset("hidden");
            String qq = q.trim().toLowerCase(Locale.ROOT);
            for (AppItem a : apps) {
                boolean isHidden = hid.contains(a.pkg);
                if (isHidden && !showHidden) continue;
                if (!qq.isEmpty() && !labelOf(a).toLowerCase(Locale.ROOT).contains(qq)) continue;
                list.add(a);
            }
            Collections.sort(list, new java.util.Comparator<AppItem>() {
                @Override public int compare(AppItem x, AppItem y) {
                    return labelOf(x).compareToIgnoreCase(labelOf(y));
                }
            });
            notifyDataSetChanged();
        }

        @Override public int getCount() { return list.size(); }
        @Override public Object getItem(int i) { return list.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            AppItem a = list.get(pos);
            LinearLayout ll;
            ImageView iv;
            View dot;
            TextView t;
            if (v == null) {
                ll = new LinearLayout(MainActivity.this);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setGravity(Gravity.CENTER_HORIZONTAL);
                ll.setLayoutParams(new AbsListView.LayoutParams(MATCH, dp(96)));
                FrameLayout fl = new FrameLayout(MainActivity.this);
                iv = new ImageView(MainActivity.this);
                fl.addView(iv, new FrameLayout.LayoutParams(icoPx, icoPx, Gravity.CENTER));
                dot = new View(MainActivity.this);
                dot.setBackground(glass(accent, 0xFF000000, 6));
                fl.addView(dot, new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP | Gravity.END));
                ll.addView(fl, new LinearLayout.LayoutParams(icoPx + dp(10), icoPx));
                t = tv(11, Color.WHITE);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(4), dp(6), dp(4), 0);
                ll.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
                ll.setTag(new View[]{iv, dot, t});
            } else {
                ll = (LinearLayout) v;
                View[] w = (View[]) ll.getTag();
                iv = (ImageView) w[0];
                dot = w[1];
                t = (TextView) w[2];
            }
            iv.setImageBitmap(icon(a));
            dot.setVisibility(Hub.notifPkgs.contains(a.pkg) ? View.VISIBLE : View.GONE);
            t.setText(labelOf(a));
            t.setTypeface(font);
            t.setVisibility(sp.getBoolean("labels", true) ? View.VISIBLE : View.GONE);
            ll.setAlpha(hid.contains(a.pkg) ? 0.5f : 1f);
            return ll;
        }
    }

    // ------------------------------------------------------------ pantalla de inicio y carpetas

    String folderName(String id) { return sp.getString("fn_" + id, "Carpeta"); }

    List<String> folderApps(String id) { return csv(sp.getString("fo_" + id, "")); }

    List<String> allFolders() {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < nPages(); i++) {
            for (String t : pageItems(i)) {
                if (t.startsWith("f:") && !r.contains(t)) r.add(t);
            }
        }
        return r;
    }

    String folderOf(String pkg) {
        for (String f : allFolders()) {
            if (folderApps(f).contains(pkg)) return f;
        }
        return null;
    }

    int[] locate(String token) {
        for (int i = 0; i < nPages(); i++) {
            int ix = pageItems(i).indexOf(token);
            if (ix >= 0) return new int[]{i, ix};
        }
        return null;
    }

    boolean addToHome(String token) {
        for (int i = 0; i < nPages(); i++) {
            List<String> l = pageItems(i);
            if (l.size() < pageCap(i)) {
                l.add(token);
                savePage(i, l);
                return true;
            }
        }
        toast("Las pantallas están llenas. Agrega una página en Ajustes.");
        return false;
    }

    void removeFromHome(String token) {
        for (int i = 0; i < nPages(); i++) {
            List<String> l = pageItems(i);
            if (l.remove(token)) savePage(i, l);
        }
    }

    void moveItem(String token, int dir) {
        int[] loc = locate(token);
        if (loc == null) return;
        int p = loc[0];
        int ix = loc[1];
        List<String> l = pageItems(p);
        int ni = ix + dir;
        if (ni >= 0 && ni < l.size()) {
            Collections.swap(l, ix, ni);
            savePage(p, l);
        } else {
            int np = p + dir;
            if (np < 0 || np >= nPages()) return;
            List<String> o = pageItems(np);
            if (o.size() >= pageCap(np)) {
                toast("La página destino está llena");
                return;
            }
            l.remove(ix);
            savePage(p, l);
            if (dir > 0) o.add(0, token);
            else o.add(token);
            savePage(np, o);
        }
        refreshAll();
    }

    void deleteFolder(String fid) {
        removeFromHome(fid);
        sp.edit().remove("fo_" + fid).remove("fn_" + fid).apply();
    }

    void removeFromFolder(String pkg, String fid) {
        List<String> l = folderApps(fid);
        l.remove(pkg);
        if (l.isEmpty()) deleteFolder(fid);
        else putCsv("fo_" + fid, l);
    }

    void addToFolder(String pkg, String fid) {
        removeFromHome(pkg);
        String old = folderOf(pkg);
        if (old != null && !old.equals(fid)) removeFromFolder(pkg, old);
        List<String> l = folderApps(fid);
        if (!l.contains(pkg)) l.add(pkg);
        putCsv("fo_" + fid, l);
        refreshAll();
    }

    void newFolderWith(String pkg, String name) {
        int n = sp.getInt("fnext", 1);
        String fid = "f:" + n;
        sp.edit().putInt("fnext", n + 1).putString("fn_" + fid, name).apply();
        String old = folderOf(pkg);
        if (old != null) removeFromFolder(pkg, old);
        int[] loc = locate(pkg);
        if (loc != null) {
            List<String> pl = pageItems(loc[0]);
            pl.set(loc[1], fid);
            savePage(loc[0], pl);
        } else if (!addToHome(fid)) {
            sp.edit().remove("fn_" + fid).apply();
            return;
        }
        List<String> first = new ArrayList<>();
        first.add(pkg);
        putCsv("fo_" + fid, first);
        refreshAll();
    }

    void openFolder(final String fid) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout row = null;
        int k = 0;
        for (String p : folderApps(fid)) {
            AppItem a = find(p);
            if (a == null) continue;
            if (k % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            row.addView(appCell(a, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
            k++;
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        openDlg = dlg().setTitle(folderName(fid)).setView(sv).create();
        openDlg.show();
    }

    void folderMenu(final String fid) {
        menu(folderName(fid), new String[]{"Renombrar carpeta", "Eliminar carpeta (las apps vuelven al cajón)"}, i -> {
            if (i == 0) {
                final EditText in = new EditText(this);
                in.setText(folderName(fid));
                in.setSingleLine(true);
                in.setTextColor(Color.WHITE);
                dlg().setTitle("Nombre de la carpeta").setView(in)
                        .setPositiveButton("Guardar", (dg, w) -> {
                            String t = in.getText().toString().trim();
                            sp.edit().putString("fn_" + fid, t.isEmpty() ? "Carpeta" : t).apply();
                            refreshAll();
                        })
                        .setNegativeButton("Cancelar", null).show();
            } else {
                deleteFolder(fid);
                refreshAll();
            }
        });
    }

    void folderPicker(final AppItem a) {
        final List<String> fs = allFolders();
        String[] o = new String[fs.size() + 1];
        for (int k = 0; k < fs.size(); k++) o[k] = folderName(fs.get(k));
        o[fs.size()] = "+ Nueva carpeta";
        menu("Agregar a carpeta", o, ix -> {
            if (ix < fs.size()) {
                addToFolder(a.pkg, fs.get(ix));
            } else {
                final EditText in = new EditText(this);
                in.setText("Carpeta");
                in.setSingleLine(true);
                in.setTextColor(Color.WHITE);
                dlg().setTitle("Nombre de la carpeta").setView(in)
                        .setPositiveButton("Crear", (dg, w) -> {
                            String t = in.getText().toString().trim();
                            newFolderWith(a.pkg, t.isEmpty() ? "Carpeta" : t);
                        })
                        .setNegativeButton("Cancelar", null).show();
            }
        });
    }

    // ------------------------------------------------------------ menú de cada app

    void opt(List<String> l, List<Runnable> acts, String s, Runnable r) {
        l.add(s);
        acts.add(r);
    }

    void showAppMenu(final AppItem a) {
        final List<String> L = new ArrayList<>();
        final List<Runnable> A = new ArrayList<>();
        final boolean inDock = dockList().contains(a.pkg);
        final boolean isHid = sset("hidden").contains(a.pkg);
        final boolean isLocked = sset("locked").contains(a.pkg);
        final int[] loc = locate(a.pkg);
        final String fid = folderOf(a.pkg);

        opt(L, A, "Renombrar", () -> renameDialog(a));
        opt(L, A, "Cambiar ícono", () -> {
            pendingPkg = a.pkg;
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("image/*");
            startActivityForResult(Intent.createChooser(it, "Elegir imagen"), REQ_ICON);
        });
        opt(L, A, "Restablecer ícono y nombre", () -> {
            new File(new File(getFilesDir(), "icons"), a.pkg + ".png").delete();
            sp.edit().remove("lbl_" + a.pkg).apply();
            cache.remove(a.pkg);
            refreshAll();
        });
        opt(L, A, inDock ? "Quitar del dock" : "Agregar al dock", () -> {
            List<String> l = dockList();
            if (inDock) {
                l.remove(a.pkg);
            } else {
                if (l.size() >= 5) {
                    toast("El dock admite 5 apps");
                    return;
                }
                l.add(a.pkg);
            }
            saveDock(l);
            refreshAll();
        });
        if (loc != null) {
            opt(L, A, "Quitar de la pantalla de inicio", () -> {
                removeFromHome(a.pkg);
                refreshAll();
            });
            opt(L, A, "Mover \u25C0", () -> moveItem(a.pkg, -1));
            opt(L, A, "Mover \u25B6", () -> moveItem(a.pkg, 1));
        } else if (fid == null) {
            opt(L, A, "Agregar a la pantalla de inicio", () -> {
                if (addToHome(a.pkg)) refreshAll();
            });
        }
        if (fid != null) {
            opt(L, A, "Sacar de la carpeta", () -> {
                removeFromFolder(a.pkg, fid);
                addToHome(a.pkg);
                refreshAll();
            });
        } else {
            opt(L, A, "Agregar a una carpeta", () -> folderPicker(a));
        }
        opt(L, A, isHid ? "Mostrar app" : "Ocultar app", () -> {
            Set<String> s = sset("hidden");
            if (isHid) s.remove(a.pkg);
            else s.add(a.pkg);
            putSet("hidden", s);
            refreshAll();
        });
        opt(L, A, isLocked ? "Quitar bloqueo con huella" : "Bloquear con huella", () -> {
            if (isLocked) {
                auth(() -> {
                    Set<String> s1 = sset("locked");
                    s1.remove(a.pkg);
                    putSet("locked", s1);
                    toast("Bloqueo quitado");
                });
            } else {
                Set<String> s2 = sset("locked");
                s2.add(a.pkg);
                putSet("locked", s2);
                toast("Ahora pedirá tu huella para abrirse");
            }
        });
        opt(L, A, "Información de la app", () -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))));
        opt(L, A, "Desinstalar", () -> startActivity(new Intent(
                Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))));

        dlg().setTitle(labelOf(a)).setItems(L.toArray(new String[0]), (dg, w) -> {
            try {
                A.get(w).run();
            } catch (Exception e) {
                toast("No se pudo: " + e.getMessage());
            }
        }).show();
    }

    void renameDialog(final AppItem a) {
        final EditText input = new EditText(this);
        input.setText(labelOf(a));
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        dlg().setTitle("Nuevo nombre").setView(input)
                .setPositiveButton("Guardar", (dg, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) sp.edit().remove("lbl_" + a.pkg).apply();
                    else sp.edit().putString("lbl_" + a.pkg, t).apply();
                    refreshAll();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------ ajustes

    void pickInt(String title, String[] o, final String key, final int[] vals) {
        menu(title, o, i -> {
            sp.edit().putInt(key, vals[i]).apply();
            cache.clear();
            rebuildUi();
        });
    }

    void toggle(String key, boolean def) {
        sp.edit().putBoolean(key, !sp.getBoolean(key, def)).apply();
        cache.clear();
        rebuildUi();
        Hub.changed();
    }

    boolean notifAccess() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    boolean overlayOk() { return Settings.canDrawOverlays(this); }

    void askOverlay() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("No se pudo abrir la pantalla de permisos");
        }
    }

    void askNotifAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Exception e) {
            toast("No se pudo abrir la pantalla de permisos");
        }
    }

    void showSettings() {
        menu("Ajustes del launcher", new String[]{
                "Temas r\u00E1pidos (iOS \u00B7 Nothing \u00B7 Nubia)",
                "Apariencia",
                "Fuente",
                "Animaciones y desenfoque",
                "Pantalla de inicio y widgets",
                "Isla din\u00E1mica y notificaciones",
                "Barra de estado propia",
                "Pantalla de bloqueo propia",
                "Gestos y modo una mano",
                "Fondo de pantalla",
                "Respaldo del tema",
                "Hacer predeterminado este launcher",
                "Evitar que el sistema cierre el launcher"}, i -> {
            switch (i) {
                case 0: presetMenu(); break;
                case 1: appearanceMenu(); break;
                case 2: fontMenu(); break;
                case 3: animMenu(); break;
                case 4: homeMenu(); break;
                case 5: islandMenu(); break;
                case 6: toggle("custom_bar", true); break;
                case 7: lockMenu(); break;
                case 8: gestureMenu(); break;
                case 9:
                    try {
                        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"));
                    } catch (Exception e) {
                        toast("No se pudo abrir el selector de fondos");
                    }
                    break;
                case 10: backupMenu(); break;
                case 11: requestHome(true); break;
                case 12:
                    try {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception e) {
                        toast("No se pudo abrir esa pantalla");
                    }
                    break;
                default: break;
            }
        });
    }

    void presetMenu() {
        menu("Temas r\u00E1pidos", new String[]{
                "iOS Glass (cristal, squircle, azul)",
                "Nothing (monocromo, puntos, rojo)",
                "Nubia (cuadrado, magenta, fino)"}, i -> applyPreset(i));
    }

    void applyPreset(int p) {
        SharedPreferences.Editor e = sp.edit();
        if (p == 0) {
            e.putInt("shape", 1).putBoolean("mono", false).putInt("accent", 0xFF0A84FF)
                    .putInt("glass", 2).putInt("clock", 0).putInt("anim", 0).putBoolean("blur", true);
        } else if (p == 1) {
            e.putInt("shape", 0).putBoolean("mono", true).putInt("accent", 0xFFD71921)
                    .putInt("glass", 0).putInt("clock", 1).putInt("anim", 1).putBoolean("blur", false);
        } else {
            e.putInt("shape", 2).putBoolean("mono", false).putInt("accent", 0xFFFF2D95)
                    .putInt("glass", 1).putInt("clock", 2).putInt("anim", 0).putBoolean("blur", true);
        }
        e.apply();
        cache.clear();
        rebuildUi();
    }

    void appearanceMenu() {
        menu("Apariencia", new String[]{
                "Forma de los \u00EDconos",
                chk(sp.getBoolean("mono", false), "\u00CDconos monocromo (Nothing)"),
                "Color de acento",
                "Transparencia del cristal",
                "Tama\u00F1o de \u00EDconos",
                "Columnas (4 o 5)",
                chk(sp.getBoolean("labels", true), "Mostrar nombres de apps"),
                "Estilo del reloj"}, i -> {
            switch (i) {
                case 0:
                    pickInt("Forma de los \u00EDconos", new String[]{"C\u00EDrculo", "Squircle (iPhone)", "Cuadrado redondeado"},
                            "shape", new int[]{0, 1, 2});
                    break;
                case 1: toggle("mono", false); break;
                case 2:
                    pickInt("Color de acento", new String[]{"Rojo Nothing", "Azul iOS", "Verde", "Magenta Nubia", "Blanco"},
                            "accent", new int[]{0xFFD71921, 0xFF0A84FF, 0xFF30D158, 0xFFFF2D95, 0xFFFFFFFF});
                    break;
                case 3:
                    pickInt("Transparencia del cristal", new String[]{"Suave", "Media", "Fuerte"}, "glass", new int[]{0, 1, 2});
                    break;
                case 4:
                    pickInt("Tama\u00F1o de \u00EDconos", new String[]{"Peque\u00F1o", "Mediano", "Grande"}, "isize", new int[]{0, 1, 2});
                    break;
                case 5:
                    pickInt("Columnas", new String[]{"4 columnas", "5 columnas"}, "cols", new int[]{4, 5});
                    break;
                case 6: toggle("labels", true); break;
                case 7:
                    pickInt("Estilo del reloj", new String[]{"Digital grueso", "Puntos (Nothing)", "Fino"}, "clock", new int[]{0, 1, 2});
                    break;
                default: break;
            }
        });
    }

    void fontMenu() {
        menu("Fuente", new String[]{"Cambiar fuente (archivo .ttf)", "Restablecer fuente"}, i -> {
            if (i == 0) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("*/*");
                startActivityForResult(it, REQ_FONT);
            } else {
                new File(getFilesDir(), "font.ttf").delete();
                sp.edit().remove("font").apply();
                font = Hub.font(this);
                rebuildUi();
            }
        });
    }

    void animMenu() {
        menu("Animaciones y desenfoque", new String[]{
                chk(sp.getInt("anim", 0) == 0, "Escala al abrir (iOS)"),
                chk(sp.getInt("anim", 0) == 1, "Desvanecer"),
                chk(sp.getInt("anim", 0) == 2, "Sin animaciones (m\u00E1s r\u00E1pido)"),
                chk(sp.getBoolean("blur", true), "Desenfoque del fondo")}, i -> {
            if (i < 3) {
                sp.edit().putInt("anim", i).apply();
            } else {
                sp.edit().putBoolean("blur", !sp.getBoolean("blur", true)).apply();
            }
        });
    }

    void homeMenu() {
        menu("Pantalla de inicio y widgets", new String[]{
                "Agregar p\u00E1gina",
                "Quitar la \u00FAltima p\u00E1gina (debe estar vac\u00EDa)",
                "Agregar widget de Android",
                "Quitar widget de Android",
                chk(showHidden, "Ver apps ocultas (pide huella)")}, i -> {
            switch (i) {
                case 0:
                    if (nPages() >= 5) {
                        toast("M\u00E1ximo 5 p\u00E1ginas");
                    } else {
                        sp.edit().putInt("npages", nPages() + 1).apply();
                        rebuildPages();
                    }
                    break;
                case 1: {
                    int n = nPages();
                    if (n <= 1) {
                        toast("Debe quedar al menos una p\u00E1gina");
                    } else if (!pageItems(n - 1).isEmpty()) {
                        toast("La \u00FAltima p\u00E1gina no est\u00E1 vac\u00EDa");
                    } else {
                        sp.edit().putInt("npages", n - 1).remove("hp_" + (n - 1)).apply();
                        rebuildPages();
                    }
                    break;
                }
                case 2: pickWidget(); break;
                case 3: removeWidgetMenu(); break;
                case 4:
                    if (showHidden) {
                        showHidden = false;
                        refreshAll();
                    } else {
                        auth(() -> {
                            showHidden = true;
                            refreshAll();
                        });
                    }
                    break;
                default: break;
            }
        });
    }

    void islandMenu() {
        menu("Isla din\u00E1mica y notificaciones", new String[]{
                chk(sp.getBoolean("island_overlay", true), "Mostrar la isla sobre otras apps"),
                (notifAccess() ? "\u2713 " : "\u2717 ") + "Acceso a notificaciones (necesario)",
                (overlayOk() ? "\u2713 " : "\u2717 ") + "Permiso: mostrar sobre otras apps"}, i -> {
            if (i == 0) toggle("island_overlay", true);
            else if (i == 1) askNotifAccess();
            else askOverlay();
        });
    }

    void lockMenu() {
        final boolean on = sp.getBoolean("lock_on", false);
        menu("Pantalla de bloqueo propia", new String[]{
                chk(on, "Usar mi pantalla de bloqueo"),
                "Probarla ahora",
                (overlayOk() ? "\u2713 " : "\u2717 ") + "Permiso: mostrar sobre otras apps",
                (notifAccess() ? "\u2713 " : "\u2717 ") + "Acceso a notificaciones (mantiene activo el servicio)"}, i -> {
            switch (i) {
                case 0:
                    sp.edit().putBoolean("lock_on", !on).apply();
                    if (!on) {
                        toast("Al apagar la pantalla ver\u00E1s tu bloqueo. Se desbloquea con tu PIN o huella del sistema.");
                        if (!overlayOk()) askOverlay();
                        else if (!notifAccess()) askNotifAccess();
                    }
                    break;
                case 1: startActivity(new Intent(this, LockActivity.class)); break;
                case 2: askOverlay(); break;
                case 3: askNotifAccess(); break;
                default: break;
            }
        });
    }

    void gestureMenu() {
        menu("Gestos y modo una mano", new String[]{
                chk(sp.getBoolean("dtap", false), "Doble toque en un espacio vac\u00EDo para bloquear"),
                chk(sp.getBoolean("onehand", false), "Modo una mano (reduce la pantalla)")}, i -> {
            if (i == 0) {
                boolean now = !sp.getBoolean("dtap", false);
                sp.edit().putBoolean("dtap", now).apply();
                if (now && LockAccessibility.inst == null) {
                    toast("Activa Mi Launcher en Accesibilidad para que pueda bloquear la pantalla");
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        // nada
                    }
                }
            } else {
                toggle("onehand", false);
            }
        });
    }

    void backupMenu() {
        menu("Respaldo del tema", new String[]{
                "Exportar tema y orden (.json)",
                "Importar tema y orden"}, i -> {
            if (i == 0) {
                Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("application/json");
                it.putExtra(Intent.EXTRA_TITLE, "launcher_respaldo.json");
                startActivityForResult(it, REQ_EXPORT);
            } else {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("*/*");
                startActivityForResult(it, REQ_IMPORT);
            }
        });
    }

    // ------------------------------------------------------------ widgets de Android

    void pickWidget() {
        final List<AppWidgetProviderInfo> l = awm.getInstalledProviders();
        String[] n = new String[l.size()];
        for (int k = 0; k < n.length; k++) {
            n[k] = l.get(k).loadLabel(pm) + " \u00B7 " + l.get(k).provider.getPackageName();
        }
        menu("Elegir widget", n, ix -> bindWidget(l.get(ix)));
    }

    void bindWidget(AppWidgetProviderInfo info) {
        int id = awHost.allocateAppWidgetId();
        pendingWidget = id;
        pendingInfo = info;
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configOrAdd(id, info);
        } else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            startActivityForResult(i, REQ_BIND);
        }
    }

    void configOrAdd(int id, AppWidgetProviderInfo info) {
        if (info != null && info.configure != null) {
            try {
                awHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONF, null);
                return;
            } catch (Exception e) {
                // se agrega sin configurar
            }
        }
        addWidgetId(id);
    }

    void addWidgetId(int id) {
        List<String> l = csv(sp.getString("awids", ""));
        l.add(String.valueOf(id));
        putCsv("awids", l);
        pendingWidget = -1;
        rebuildPages();
    }

    void removeWidgetMenu() {
        final List<String> ids = csv(sp.getString("awids", ""));
        if (ids.isEmpty()) {
            toast("No hay widgets de Android agregados");
            return;
        }
        String[] n = new String[ids.size()];
        for (int k = 0; k < n.length; k++) {
            String label = "Widget " + ids.get(k);
            try {
                AppWidgetProviderInfo inf = awm.getAppWidgetInfo(Integer.parseInt(ids.get(k)));
                if (inf != null) label = String.valueOf(inf.loadLabel(pm));
            } catch (Exception e) {
                // se usa el nombre genérico
            }
            n[k] = label;
        }
        menu("Quitar widget", n, ix -> {
            try {
                awHost.deleteAppWidgetId(Integer.parseInt(ids.get(ix)));
            } catch (Exception e) {
                // nada
            }
            ids.remove(ix);
            putCsv("awids", ids);
            rebuildPages();
        });
    }

    // ------------------------------------------------------------ resultados y respaldo

    @Override
    protected void onActivityResult(int rq, int rs, Intent data) {
        super.onActivityResult(rq, rs, data);
        try {
            if (rq == REQ_BIND) {
                if (rs == RESULT_OK && pendingWidget != -1) {
                    configOrAdd(pendingWidget, awm.getAppWidgetInfo(pendingWidget));
                } else if (pendingWidget != -1) {
                    awHost.deleteAppWidgetId(pendingWidget);
                    pendingWidget = -1;
                }
                return;
            }
            if (rq == REQ_CONF) {
                if (rs == RESULT_OK && pendingWidget != -1) addWidgetId(pendingWidget);
                else if (pendingWidget != -1) awHost.deleteAppWidgetId(pendingWidget);
                pendingWidget = -1;
                return;
            }
            if (rq == REQ_ROLE) return;
            if (rs != RESULT_OK || data == null || data.getData() == null) return;
            Uri u = data.getData();
            if (rq == REQ_ICON && pendingPkg != null) {
                Bitmap bm = decodeUri(u, 256);
                if (bm == null) throw new IOException("imagen no v\u00E1lida");
                int s = Math.min(bm.getWidth(), bm.getHeight());
                Bitmap sq = Bitmap.createBitmap(bm, (bm.getWidth() - s) / 2, (bm.getHeight() - s) / 2, s, s);
                sq = Bitmap.createScaledBitmap(sq, 256, 256, true);
                File dir = new File(getFilesDir(), "icons");
                dir.mkdirs();
                FileOutputStream fo = new FileOutputStream(new File(dir, pendingPkg + ".png"));
                sq.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                cache.remove(pendingPkg);
                refreshAll();
            } else if (rq == REQ_FONT) {
                File out = new File(getFilesDir(), "font.ttf");
                InputStream in = getContentResolver().openInputStream(u);
                FileOutputStream fo = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                in.close();
                fo.close();
                try {
                    Typeface.createFromFile(out);
                } catch (Exception e) {
                    out.delete();
                    toast("Esa fuente no es v\u00E1lida");
                    return;
                }
                sp.edit().putString("font", out.getPath()).apply();
                font = Hub.font(this);
                rebuildUi();
            } else if (rq == REQ_EXPORT) {
                exportTo(u);
            } else if (rq == REQ_IMPORT) {
                importFrom(u);
            }
        } catch (Exception e) {
            toast("No se pudo: " + e.getMessage());
        }
    }

    void exportTo(Uri u) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v == null || k.equals("font")) continue;
            if (v instanceof Set) {
                JSONArray arr = new JSONArray();
                for (Object x : (Set<?>) v) arr.put(String.valueOf(x));
                o.put(k, arr);
            } else {
                o.put(k, v);
            }
        }
        OutputStream os = getContentResolver().openOutputStream(u);
        os.write(o.toString(2).getBytes("UTF-8"));
        os.close();
        toast("Tema exportado");
    }

    void importFrom(Uri u) throws Exception {
        InputStream in = getContentResolver().openInputStream(u);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        JSONObject o = new JSONObject(bo.toString("UTF-8"));
        SharedPreferences.Editor ed = sp.edit();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v = o.get(k);
            if (v instanceof JSONArray) {
                Set<String> s = new HashSet<>();
                JSONArray arr = (JSONArray) v;
                for (int i = 0; i < arr.length(); i++) s.add(arr.getString(i));
                ed.putStringSet(k, s);
            } else if (v instanceof Boolean) {
                ed.putBoolean(k, (Boolean) v);
            } else if (v instanceof Integer) {
                ed.putInt(k, (Integer) v);
            } else if (v instanceof Long) {
                ed.putLong(k, (Long) v);
            } else if (v instanceof String) {
                ed.putString(k, (String) v);
            }
        }
        ed.apply();
        cache.clear();
        font = Hub.font(this);
        rebuildUi();
        toast("Tema importado");
    }
}
