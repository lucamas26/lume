package dev.lume.launcher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** Una app avviabile mostrata nel launcher. */
final class AppEntry {
    final String pkg;
    final ComponentName component;
    final String label;
    final Drawable banner; // puo' essere null (le app TV ne forniscono uno 16:9)
    final Drawable icon;
    final boolean system;

    AppEntry(String pkg, ComponentName component, String label,
             Drawable banner, Drawable icon, boolean system) {
        this.pkg = pkg;
        this.component = component;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.system = system;
    }
}
