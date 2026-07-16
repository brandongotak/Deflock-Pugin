package com.atakmap.android.skeleton;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.gui.PanPreference;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.PluginPreferenceFragment;

public class DeflockPreferencesFragment extends PluginPreferenceFragment {

    private final Context pluginContext;

    public DeflockPreferencesFragment(Context pluginContext) {
        super(pluginContext, R.xml.preferences);
        this.pluginContext = pluginContext;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PanPreference updatePref = (PanPreference) findPreference("pref_check_update");
        if (updatePref != null) {
            updatePref.setOnPreferenceClickListener(preference -> {
                Context ctx = pluginContext != null
                        ? pluginContext
                        : MapView.getMapView().getContext();
                DeflockPluginUpdater.checkForUpdate(ctx);
                return true;
            });
        }
    }

    @Override
    public String getSubTitle() {
        return "Deflock Settings";
    }
}
