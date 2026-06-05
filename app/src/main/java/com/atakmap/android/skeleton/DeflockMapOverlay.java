package com.atakmap.android.skeleton;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class DeflockMapOverlay extends AbstractMapOverlay2 {

    private static final String TAG = "DeflockMapOverlay";
    private static final String GROUP_NAME = "Deflock ALPR";
    private static final String OVERLAY_ID = "deflock_alpr_overlay";

    private final Context pluginContext;
    private final DefaultMapGroup group;
    private Icon markerIcon;

    public DeflockMapOverlay(MapView mapView, Context pluginContext) {
        this.pluginContext = pluginContext;
        this.group = new DefaultMapGroup(GROUP_NAME);
        this.group.setMetaBoolean("ignoreOffscreen", false);
        this.markerIcon = buildIcon(pluginContext);
    }

    @Override
    public String getIdentifier() { return OVERLAY_ID; }

    @Override
    public String getName() { return GROUP_NAME; }

    @Override
    public MapGroup getRootGroup() { return group; }

    @Override
    public DeepMapItemQuery getQueryFunction() { return null; }

    @Override
    public HierarchyListItem getListModel(android.widget.BaseAdapter adapter,
            long capabilities, HierarchyListFilter filter) {
        return null;
    }

    public void addOrUpdateMarkers(List<Alpr> alprs) {
        for (Alpr alpr : alprs) {
            addOrUpdate(alpr);
        }
    }

    private void addOrUpdate(Alpr alpr) {
        String uid = alpr.getMapUid();
        MapItem existing = group.deepFindUID(uid);

        if (existing instanceof Marker) {
            Marker m = (Marker) existing;
            m.setPoint(GeoPointMetaData.wrap(new GeoPoint(alpr.lat, alpr.lon)));
            m.setTitle(alpr.getDisplayName());
            return;
        }

        Marker marker = new Marker(GeoPointMetaData.wrap(new GeoPoint(alpr.lat, alpr.lon)), uid);
        marker.setType("b-m-p");
        marker.setTitle(alpr.getDisplayName());
        marker.setMetaString("callsign", alpr.getDisplayName());
        marker.setMetaString("deflock_id", alpr.id);
        marker.setMetaString("deflock_manufacturer", alpr.getManufacturer());
        marker.setMetaString("deflock_operator", alpr.getOperator());
        marker.setMetaString("deflock_type", alpr.getSurveillanceType());
        marker.setMetaString("deflock_direction", alpr.getDirection());
        marker.setMetaBoolean("addToObjList", false);
        marker.setClickable(true);
        group.addItem(marker);

        // Set icon AFTER addItem — ATAK resets icon on add
        if (markerIcon != null) {
            marker.setIcon(markerIcon);
        }
    }

    public void clearMarkers() {
        group.clearItems();
    }

    public int getMarkerCount() {
        return group.getItemCount();
    }

    static Icon buildIcon(Context atakContext) {
        try {
            int size = 32;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);

            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(Color.rgb(220, 50, 50));
            fill.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, fill);

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setColor(Color.WHITE);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(2.5f);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, border);

            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(Color.WHITE);
            dot.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, 4f, dot);

            File dir = new File(atakContext.getCacheDir(), "deflock_icons");
            dir.mkdirs();
            File iconFile = new File(dir, "deflock_marker.png");
            FileOutputStream fos = new FileOutputStream(iconFile);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            bmp.recycle();

            return new Icon.Builder()
                    .setImageUri(Icon.STATE_DEFAULT, "file://" + iconFile.getAbsolutePath())
                    .build();
        } catch (Exception e) {
            Log.w(TAG, "Failed to build icon: " + e.getMessage());
            return null;
        }
    }
}
