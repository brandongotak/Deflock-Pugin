package com.atakmap.android.skeleton;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.atakmap.coremap.log.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DeflockDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DeflockDB";
    private static final String DB_DIR = "/sdcard/atak/tools/deflock/";
    private static final String DB_NAME = "deflock.db";
    private static final int DB_VERSION = 1;

    public DeflockDatabaseHelper(Context context) {
        super(context, DB_DIR + DB_NAME, null, DB_VERSION);
        new File(DB_DIR).mkdirs();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE alprs (" +
                "osm_id TEXT PRIMARY KEY, " +
                "lat REAL NOT NULL, " +
                "lon REAL NOT NULL, " +
                "tags TEXT NOT NULL DEFAULT '{}', " +
                "type TEXT NOT NULL DEFAULT 'node'" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS alprs");
        onCreate(db);
    }

    public void upsertAlprs(List<Alpr> alprs) {
        SQLiteDatabase db = getWritableDatabase();
        try {
            db.beginTransaction();
            for (Alpr alpr : alprs) {
                ContentValues cv = new ContentValues();
                cv.put("osm_id", alpr.id);
                cv.put("lat", alpr.lat);
                cv.put("lon", alpr.lon);
                cv.put("tags", tagsToJson(alpr.tags));
                cv.put("type", alpr.type);
                db.insertWithOnConflict("alprs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Alpr> getAllAlprs() {
        List<Alpr> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("alprs", null, null, null, null, null, null);
        try {
            while (c.moveToNext()) {
                result.add(cursorToAlpr(c));
            }
        } finally {
            c.close();
        }
        return result;
    }

    public int getCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM alprs", null);
        try {
            if (c.moveToFirst()) return c.getInt(0);
        } finally {
            c.close();
        }
        return 0;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("alprs", null, null);
    }

    private Alpr cursorToAlpr(Cursor c) {
        String id = c.getString(c.getColumnIndexOrThrow("osm_id"));
        double lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
        double lon = c.getDouble(c.getColumnIndexOrThrow("lon"));
        String tagsJson = c.getString(c.getColumnIndexOrThrow("tags"));
        String type = c.getString(c.getColumnIndexOrThrow("type"));
        return new Alpr(id, lat, lon, jsonToTags(tagsJson), type);
    }

    private String tagsToJson(Map<String, String> tags) {
        JSONObject obj = new JSONObject();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            try { obj.put(entry.getKey(), entry.getValue()); } catch (JSONException ignored) {}
        }
        return obj.toString();
    }

    private Map<String, String> jsonToTags(String json) {
        Map<String, String> tags = new HashMap<>();
        try {
            JSONObject obj = new JSONObject(json);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                tags.put(key, obj.optString(key));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse tags JSON: " + json);
        }
        return tags;
    }
}
