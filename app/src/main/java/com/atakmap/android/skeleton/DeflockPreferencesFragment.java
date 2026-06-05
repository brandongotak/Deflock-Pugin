package com.atakmap.android.skeleton;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.preference.PluginPreferenceFragment;

public class DeflockPreferencesFragment extends PluginPreferenceFragment {

    public DeflockPreferencesFragment(Context pluginContext) {
        super(pluginContext, R.xml.preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public String getSubTitle() {
        return "Deflock Settings";
    }
}
