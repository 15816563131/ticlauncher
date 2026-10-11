package com.trae.ticlauncher;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

/** 列表条目：普通应用 / 特殊入口 */
public class AppEntry {
    public static final int TYPE_APP = 0;
    public static final int TYPE_WATCHFACE = 1;
    public static final int TYPE_SETTINGS = 2;

    public int type = TYPE_APP;
    public String label;
    public Drawable icon;
    public ComponentName component;

    public static AppEntry special(int type, String label) {
        AppEntry e = new AppEntry();
        e.type = type;
        e.label = label;
        return e;
    }
}
