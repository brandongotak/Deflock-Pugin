package com.atakmap.android.skeleton;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

import java.util.List;

public class DeflockPlugin implements IPlugin {

    private static final String TAG = "DeflockPlugin";
    private static final String PREFS_KEY = "Deflock Plugin Preferences";

    private static final int[] RADIUS_KM = {5, 10, 25, 50, 100};
    private static final String[] RADIUS_LABELS = {"5 km", "10 km", "25 km", "50 km", "100 km"};
    private static final int DEFAULT_RADIUS_IDX = 2; // 25 km

    private IServiceController serviceController;
    private Context pluginContext;
    private IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane mainPane;

    private View mainView;
    private TextView tvStatus;
    private DeflockAdapter adapter;
    private DeflockMapOverlay overlay;
    private DeflockDatabaseHelper db;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean fetching = false;
    private int selectedRadiusKm = RADIUS_KM[DEFAULT_RADIUS_IDX];

    public DeflockPlugin() {}

    public DeflockPlugin(IServiceController serviceController) {
        try {
            this.serviceController = serviceController;
            PluginContextProvider ctxProvider = serviceController.getService(PluginContextProvider.class);
            if (ctxProvider != null) {
                pluginContext = ctxProvider.getPluginContext();
                pluginContext.setTheme(R.style.AppTheme);
            }
            uiService = serviceController.getService(IHostUIService.class);

            toolbarItem = new ToolbarItem.Builder(
                    pluginContext.getString(R.string.app_name),
                    MarshalManager.marshal(
                            pluginContext.getResources().getDrawable(R.drawable.ic_deflock),
                            android.graphics.drawable.Drawable.class,
                            gov.tak.api.commons.graphics.Bitmap.class))
                    .setListener(new ToolbarItemAdapter() {
                        @Override
                        public void onClick(ToolbarItem item) { showPane(); }
                    }).build();

            db = new DeflockDatabaseHelper(pluginContext);
            registerPreferences();
            Log.d(TAG, "DeflockPlugin constructed");
        } catch (Exception e) {
            Log.e(TAG, "Construction failed", e);
        }
    }

    @Override
    public void onStart() {
        if (uiService == null) return;
        uiService.addToolbarItem(toolbarItem);

        MapView mv = MapView.getMapView();
        if (mv != null) {
            overlay = new DeflockMapOverlay(mv, pluginContext);
            mv.getMapOverlayManager().addOverlay(overlay);
        }
        Log.d(TAG, "DeflockPlugin started");
    }

    @Override
    public void onStop() {
        if (uiService != null) uiService.removeToolbarItem(toolbarItem);

        MapView mv = MapView.getMapView();
        if (mv != null && overlay != null) {
            mv.getMapOverlayManager().removeOverlay(overlay);
        }
        try { ToolsPreferenceFragment.unregister(PREFS_KEY); } catch (Exception ignored) {}
        if (db != null) db.close();
        Log.d(TAG, "DeflockPlugin stopped");
    }

    private void showPane() {
        if (mainPane == null) {
            mainView = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
            mainPane = new PaneBuilder(mainView)
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.4D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 1.0D)
                    .build();
            setupUI(mainView);
            loadCached();
        }
        if (!uiService.isPaneVisible(mainPane)) {
            uiService.showPane(mainPane, null);
        }
    }

    private void setupUI(View root) {
        tvStatus = root.findViewById(R.id.tv_status);

        TextView tvVersion = root.findViewById(R.id.tv_version);
        if (tvVersion != null) tvVersion.setText("v" + BuildConfig.PLUGIN_VERSION);

        View btnUpdate = root.findViewById(R.id.btn_check_update);
        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DeflockPluginUpdater.checkForUpdate(pluginContext);
                }
            });
        }

        RecyclerView rv = root.findViewById(R.id.recycler_alprs);
        rv.setLayoutManager(new LinearLayoutManager(MapView.getMapView().getContext()));
        adapter = new DeflockAdapter();
        adapter.setOnAlprClickListener(new DeflockAdapter.OnAlprClickListener() {
            @Override
            public void onAlprClick(Alpr alpr) { showAlprDetail(alpr); }
        });
        rv.setAdapter(adapter);

        // Radius spinner
        Spinner spinner = root.findViewById(R.id.spinner_radius);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                MapView.getMapView().getContext(),
                android.R.layout.simple_spinner_item,
                RADIUS_LABELS);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(DEFAULT_RADIUS_IDX);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedRadiusKm = RADIUS_KM[position];
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        root.findViewById(R.id.btn_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { searchCurrentArea(); }
        });

        root.findViewById(R.id.btn_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { clearAll(); }
        });
    }

    private void loadCached() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Alpr> cached = db.getAllAlprs();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!cached.isEmpty()) {
                            adapter.setAlprs(cached);
                            if (overlay != null) overlay.addOrUpdateMarkers(cached);
                            setStatus(cached.size() + " cameras loaded from cache");
                        } else {
                            setStatus("Select a radius and tap Search");
                        }
                    }
                });
            }
        }, "deflock-load-cache").start();
    }

    private void searchCurrentArea() {
        if (fetching) return;
        MapView mv = MapView.getMapView();
        if (mv == null) return;

        double lat = mv.getLatitude();
        double lon = mv.getLongitude();

        if (lat == 0 && lon == 0) {
            Toast.makeText(mv.getContext(), "Map center not available", Toast.LENGTH_SHORT).show();
            return;
        }

        double latDelta = selectedRadiusKm / 111.0;
        double lonDelta = selectedRadiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        double minLat = lat - latDelta, maxLat = lat + latDelta;
        double minLng = lon - lonDelta, maxLng = lon + lonDelta;

        fetching = true;
        setStatus("Searching " + selectedRadiusKm + "km around "
                + String.format("%.4f, %.4f", lat, lon) + "…");

        DeflockService.fetchAlprs(minLat, maxLat, minLng, maxLng, mainHandler,
                new DeflockService.Callback() {
                    @Override
                    public void onSuccess(List<Alpr> alprs) {
                        fetching = false;
                        adapter.setAlprs(alprs);
                        if (overlay != null) overlay.addOrUpdateMarkers(alprs);
                        setStatus(alprs.size() + " cameras found within "
                                + selectedRadiusKm + "km");
                        if (alprs.isEmpty()) {
                            Toast.makeText(MapView.getMapView().getContext(),
                                    "No ALPRs found. Try a larger radius or pan to a US city.",
                                    Toast.LENGTH_LONG).show();
                        }
                        saveToDb(alprs);
                    }

                    @Override
                    public void onError(String message) {
                        fetching = false;
                        setStatus("Error: " + message);
                        Toast.makeText(MapView.getMapView().getContext(),
                                "Deflock error: " + message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void clearAll() {
        if (adapter != null) adapter.setAlprs(new java.util.ArrayList<>());
        if (overlay != null) overlay.clearMarkers();
        setStatus("Cleared — select a radius and tap Search");
        new Thread(new Runnable() {
            @Override
            public void run() { db.clearAll(); }
        }, "deflock-clear").start();
    }

    private void showAlprDetail(Alpr alpr) {
        MapView mv = MapView.getMapView();
        if (mv == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Manufacturer: ").append(alpr.getManufacturer()).append("\n");
        String op = alpr.getOperator();
        if (!op.isEmpty()) sb.append("Operator: ").append(op).append("\n");
        sb.append("Type: ").append(alpr.getSurveillanceType()).append("\n");
        String dir = alpr.getDirection();
        if (!dir.isEmpty()) sb.append("Direction: ").append(dir).append("°\n");
        sb.append(String.format("Coordinates: %.5f, %.5f\n", alpr.lat, alpr.lon));
        sb.append("OSM ID: ").append(alpr.id);

        new AlertDialog.Builder(mv.getContext())
                .setTitle(alpr.getDisplayName())
                .setMessage(sb.toString())
                .setPositiveButton("Pan to", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        mv.getMapController().panTo(new GeoPoint(alpr.lat, alpr.lon), false);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void saveToDb(final List<Alpr> alprs) {
        new Thread(new Runnable() {
            @Override
            public void run() { db.upsertAlprs(alprs); }
        }, "deflock-save").start();
    }

    private void setStatus(String msg) {
        if (tvStatus != null) tvStatus.setText(msg);
    }

    private void registerPreferences() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() { registerPreferencesInternal(); }
            });
        } else {
            registerPreferencesInternal();
        }
    }

    private void registerPreferencesInternal() {
        try {
            try { ToolsPreferenceFragment.unregister(PREFS_KEY); } catch (Exception ignored) {}
            ToolsPreferenceFragment.register(
                    new ToolsPreferenceFragment.ToolPreference(
                            PREFS_KEY,
                            pluginContext.getString(R.string.preferences_summary),
                            PREFS_KEY,
                            pluginContext.getResources().getDrawable(R.drawable.ic_deflock),
                            new DeflockPreferencesFragment(pluginContext)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to register preferences", e);
        }
    }
}
