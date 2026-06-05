package com.atakmap.android.skeleton;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class DeflockPreferences {
    private static final String KEY_MAX_RADIUS_KM = "pref_max_radius_km";
    private static final String KEY_SHOW_LABELS = "pref_show_labels";

    private final SharedPreferences prefs;

    public DeflockPreferences(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public int getMaxRadiusKm() {
        try {
            return Integer.parseInt(prefs.getString(KEY_MAX_RADIUS_KM, "20"));
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    public boolean isShowLabels() {
        return prefs.getBoolean(KEY_SHOW_LABELS, false);
    }
}
