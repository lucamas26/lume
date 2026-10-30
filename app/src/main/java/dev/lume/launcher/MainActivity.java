package dev.lume.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Launcher TV leggero, senza pubblicita' e senza dipendenze.
 * Estetica ispirata a Google TV: sfondo scuro, schede 16:9 arrotondate,
 * anello bianco e ingrandimento sul focus, orologio in alto.
 *
 * Telecomando: OK = apri, pressione lunga su OK o tasto Menu = opzioni app.
 */
public class MainActivity extends Activity {

    private static final String KEY_FAV = "fav";
    private static final String KEY_HIDDEN = "hidden";

    private static final int COLOR_TEXT = 0xFFE8EAED;
    private static final int COLOR_MUTED = 0xFFB6BCC9;
    private static final int COLOR_SURFACE = 0xFF1C2230;
    private static final int COLOR_PILL = 0xFF1F2638;

    private PackageManager pm;
    private SharedPreferences prefs;
    private ScrollView scroll;
    private LinearLayout content;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private View firstFocus;
    private boolean resetFocus = true;
    private int loadGen;
    private float density;
    private int screenW;
    private List<AppEntry> lastApps = new ArrayList<>();

    private final Runnable reload = this::loadApps;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ui.removeCallbacks(reload);
            ui.postDelayed(reload, 600);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        prefs = getSharedPreferences("lume", MODE_PRIVATE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;
        screenW = dm.widthPixels;

        FrameLayout root = new FrameLayout(this);
        root.setBackground(makeBackground(dm.widthPixels));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(dp(56), dp(28), dp(56), dp(56));

        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        loadApps();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(pkgReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    /** Tasto Home premuto mentre il launcher e' gia' aperto: torna in cima. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (firstFocus != null) {
            firstFocus.requestFocus();
        }
        scroll.smoothScrollTo(0, 0);
    }

    /** Un launcher non deve mai chiudersi con Indietro. */
    @Override
    public void onBackPressed() {
        if (scroll.getScrollY() > 0) {
            if (firstFocus != null) {
                firstFocus.requestFocus();
            }
            scroll.smoothScrollTo(0, 0);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused != null && focused.getTag() instanceof AppEntry) {
                showOptions((AppEntry) focused.getTag());
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    // ------------------------------------------------------------------
    // Caricamento app
    // ------------------------------------------------------------------

    private void loadApps() {
        final int gen = ++loadGen;
        new Thread(() -> {
            final List<AppEntry> apps = queryApps();
            ui.post(() -> {
                if (gen == loadGen && !isDestroyed()) {
                    lastApps = apps;
                    render(apps);
                }
            });
        }, "lume-load").start();
    }

    private List<AppEntry> queryApps() {
        Map<String, AppEntry> map = new HashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : found) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || map.containsKey(pkg)) {
                    continue;
                }
                CharSequence label = ri.loadLabel(pm);
                Drawable banner = null;
                try {
                    banner = ri.activityInfo.loadBanner(pm);
                } catch (Throwable ignored) {
                }
                Drawable icon = ri.loadIcon(pm);
                boolean system = (ri.activityInfo.applicationInfo.flags
                        & ApplicationInfo.FLAG_SYSTEM) != 0;
                map.put(pkg, new AppEntry(pkg,
                        new ComponentName(pkg, ri.activityInfo.name),
                        label != null ? label.toString() : pkg,
                        banner, icon, system));
            }
        }
        return new ArrayList<>(map.values());
    }

    // ------------------------------------------------------------------
    // Interfaccia
    // ------------------------------------------------------------------

    private void rerender() {
        resetFocus = true;
        render(lastApps);
    }

    private void render(List<AppEntry> apps) {
        content.removeAllViews();
        firstFocus = null;
        content.addView(buildHeader());

        List<String> hidden = getList(KEY_HIDDEN);
        List<String> favPkgs = getList(KEY_FAV);

        Map<String, AppEntry> byPkg = new HashMap<>();
        for (AppEntry e : apps) {
            byPkg.put(e.pkg, e);
        }

        int hiddenCount = 0;
        for (String p : hidden) {
            if (byPkg.containsKey(p)) {
                hiddenCount++;
            }
        }

        List<AppEntry> favs = new ArrayList<>();
        for (String p : favPkgs) {
            AppEntry e = byPkg.get(p);
            if (e != null && !hidden.contains(p)) {
                favs.add(e);
            }
        }
        List<AppEntry> rest = new ArrayList<>();
        for (AppEntry e : apps) {
            if (!hidden.contains(e.pkg) && !favs.contains(e)) {
                rest.add(e);
            }
        }
        Collections.sort(rest, (a, b) -> a.label.compareToIgnoreCase(b.label));

        int gap = dp(20);
        int avail = screenW - 2 * dp(56);
        int cols = Math.max(2, (avail + gap) / (dp(184) + gap));
        int tileW = (avail - gap * (cols - 1)) / cols;

        addSection(getString(R.string.favorites), favs, cols, tileW, gap);
        addSection(getString(favs.isEmpty() ? R.string.apps : R.string.more_apps),
                rest, cols, tileW, gap);

        if (hiddenCount > 0) {
            View restore = makePill(getString(R.string.show_hidden, hiddenCount), v -> {
                saveList(KEY_HIDDEN, new ArrayList<String>());
                rerender();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(12);
            content.addView(restore, lp);
        }

        if (resetFocus && firstFocus != null) {
            firstFocus.requestFocus();
            resetFocus = false;
        }
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 54);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        left.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE d MMM");
        date.setFormat24Hour("EEE d MMM");
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setTextColor(COLOR_MUTED);
        left.addView(date);

        header.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(makePill(getString(R.string.network),
                v -> safeStart(new Intent(Settings.ACTION_WIFI_SETTINGS))));

        View settings = makePill(getString(R.string.settings),
                v -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(12);
        header.addView(settings, sp);

        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = dp(12);
        header.setLayoutParams(hp);
        return header;
    }

    private void addSection(String title, List<AppEntry> list, int cols, int tileW, int gap) {
        if (list.isEmpty()) {
            return;
        }
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTextColor(COLOR_TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(16);
        tp.bottomMargin = dp(12);
        content.addView(t, tp);

        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = dp(18);
                content.addView(row, rp);
            }
            View tile = makeTile(list.get(i), tileW);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % cols != 0) {
                lp.leftMargin = gap;
            }
            row.addView(tile, lp);
            if (firstFocus == null) {
                firstFocus = tile;
            }
        }
    }

    private View makeTile(final AppEntry e, int w) {
        final float radius = dp(14);

        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setFocusable(true);
        col.setClickable(true);
        col.setTag(e);
        if (Build.VERSION.SDK_INT >= 26) {
            col.setDefaultFocusHighlightEnabled(false);
        }

        final FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(COLOR_SURFACE, radius));
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        int h = w * 9 / 16;
        col.addView(card, new LinearLayout.LayoutParams(w, h));

        ImageView img = new ImageView(this);
        if (e.banner != null) {
            img.setImageDrawable(e.banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            img.setImageDrawable(e.icon);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = h * 55 / 100;
            card.addView(img, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }

        final TextView label = new TextView(this);
        label.setText(e.label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTextColor(COLOR_MUTED);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.topMargin = dp(8);
        ll.leftMargin = dp(2);
        col.addView(label, ll);

        final GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(radius);
        ring.setStroke(dp(3), Color.WHITE);

        col.setOnFocusChangeListener((v, focused) -> {
            card.setForeground(focused ? ring : null);
            label.setTextColor(focused ? Color.WHITE : COLOR_MUTED);
            float scale = focused ? 1.08f : 1f;
            v.animate().scaleX(scale).scaleY(scale)
                    .translationZ(focused ? dp(8) : 0)
                    .setDuration(120).start();
        });
        col.setOnClickListener(v -> launch(e));
        col.setOnLongClickListener(v -> {
            showOptions(e);
            return true;
        });
        return col;
    }

    private TextView makePill(String text, View.OnClickListener action) {
        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setPadding(dp(22), dp(12), dp(22), dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        if (Build.VERSION.SDK_INT >= 26) {
            t.setDefaultFocusHighlightEnabled(false);
        }
        setPillState(t, false);
        t.setOnFocusChangeListener((v, focused) -> setPillState((TextView) v, focused));
        t.setOnClickListener(action);
        return t;
    }

    private void setPillState(TextView t, boolean focused) {
        t.setBackground(rounded(focused ? 0xFFE8EAED : COLOR_PILL, dp(100)));
        t.setTextColor(focused ? 0xFF1B1E26 : COLOR_TEXT);
    }

    private GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    private Drawable makeBackground(int widthPx) {
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF111624, 0xFF090B10});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(widthPx * 0.8f);
        glow.setGradientCenter(0.2f, 0f);
        glow.setColors(new int[]{0x66335CB8, 0x00335CB8});
        return new LayerDrawable(new Drawable[]{base, glow});
    }

    // ------------------------------------------------------------------
    // Azioni
    // ------------------------------------------------------------------

    private void launch(AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(e.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        safeStart(i);
    }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void showOptions(final AppEntry e) {
        final List<String> names = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final List<String> favs = getList(KEY_FAV);
        final int idx = favs.indexOf(e.pkg);

        if (idx < 0) {
            names.add(getString(R.string.pin));
            actions.add(() -> {
                favs.add(e.pkg);
                saveList(KEY_FAV, favs);
                rerender();
            });
        } else {
            names.add(getString(R.string.unpin));
            actions.add(() -> {
                favs.remove(e.pkg);
                saveList(KEY_FAV, favs);
                rerender();
            });
            if (idx > 0) {
                names.add(getString(R.string.move_earlier));
                actions.add(() -> {
                    Collections.swap(favs, idx, idx - 1);
                    saveList(KEY_FAV, favs);
                    rerender();
                });
            }
            if (idx < favs.size() - 1) {
                names.add(getString(R.string.move_later));
                actions.add(() -> {
                    Collections.swap(favs, idx, idx + 1);
                    saveList(KEY_FAV, favs);
                    rerender();
                });
            }
        }

        names.add(getString(R.string.hide));
        actions.add(() -> {
            List<String> hidden = getList(KEY_HIDDEN);
            if (!hidden.contains(e.pkg)) {
                hidden.add(e.pkg);
            }
            saveList(KEY_HIDDEN, hidden);
            favs.remove(e.pkg);
            saveList(KEY_FAV, favs);
            rerender();
        });

        names.add(getString(R.string.app_info));
        actions.add(() -> safeStart(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", e.pkg, null))));

        if (!e.system) {
            names.add(getString(R.string.uninstall));
            actions.add(() -> safeStart(new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", e.pkg, null))));
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(e.label)
                .setItems(names.toArray(new String[0]),
                        (dialog, which) -> actions.get(which).run())
                .show();
    }

    // ------------------------------------------------------------------
    // Preferenze (liste di pacchetti separate da virgola)
    // ------------------------------------------------------------------

    private List<String> getList(String key) {
        List<String> out = new ArrayList<>();
        String s = prefs.getString(key, "");
        if (s != null && !s.isEmpty()) {
            for (String p : s.split(",")) {
                if (!p.isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private void saveList(String key, List<String> list) {
        prefs.edit().putString(key, TextUtils.join(",", list)).apply();
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
