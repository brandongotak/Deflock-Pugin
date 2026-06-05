package com.atakmap.android.skeleton;

import android.os.Handler;

import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeflockService {

    private static final String TAG = "DeflockService";
    private static final String INDEX_URL = "https://cdn.deflock.me/regions/index.json";
    private static final int TIMEOUT_MS = 15000;

    // Cached tile index
    private static String cachedTileUrlTemplate = null;
    private static int cachedTileSizeDegrees = 20;
    private static Set<String> cachedRegions = null;
    private static long cacheExpirationMs = 0;

    public interface Callback {
        void onSuccess(List<Alpr> alprs);
        void onError(String message);
    }

    public static void fetchAlprs(final double minLat, final double maxLat,
                                   final double minLng, final double maxLng,
                                   final Handler mainHandler,
                                   final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Alpr> alprs = doFetch(minLat, maxLat, minLng, maxLng);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() { callback.onSuccess(alprs); }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "Fetch failed", e);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() { callback.onError(e.getMessage()); }
                    });
                }
            }
        }, "deflock-fetch").start();
    }

    private static List<Alpr> doFetch(double minLat, double maxLat,
                                       double minLng, double maxLng)
            throws IOException, JSONException {

        ensureIndex();

        // Compute which tiles overlap the bounding box
        List<String> tilesToFetch = computeTiles(minLat, maxLat, minLng, maxLng,
                cachedTileSizeDegrees, cachedRegions);
        Log.d(TAG, "Tiles needed: " + tilesToFetch);

        if (tilesToFetch.isEmpty()) {
            Log.d(TAG, "No tiles cover this area");
            return new ArrayList<>();
        }

        // Fetch each tile and collect results
        Map<String, Alpr> seen = new HashMap<>();
        for (String tileKey : tilesToFetch) {
            String[] parts = tileKey.split("/");
            String tileUrl = cachedTileUrlTemplate
                    .replace("{lat}", parts[0])
                    .replace("{lon}", parts[1]);
            List<Alpr> tileAlprs = fetchTile(tileUrl, minLat, maxLat, minLng, maxLng);
            for (Alpr a : tileAlprs) {
                seen.put(a.id, a); // deduplicate by OSM id
            }
        }

        List<Alpr> result = new ArrayList<>(seen.values());
        Log.d(TAG, "Total ALPRs in area: " + result.size());
        return result;
    }

    private static void ensureIndex() throws IOException, JSONException {
        long now = System.currentTimeMillis();
        if (cachedTileUrlTemplate != null && now < cacheExpirationMs) return;

        Log.d(TAG, "Fetching tile index: " + INDEX_URL);
        String body = httpGet(INDEX_URL);
        JSONObject obj = new JSONObject(body);

        cachedTileUrlTemplate = obj.getString("tile_url");
        cachedTileSizeDegrees = obj.optInt("tile_size_degrees", 20);
        long expirationUtc = obj.optLong("expiration_utc", 0);
        cacheExpirationMs = expirationUtc > 0 ? expirationUtc * 1000L : now + 3600_000L;

        cachedRegions = new HashSet<>();
        JSONArray regions = obj.optJSONArray("regions");
        if (regions != null) {
            for (int i = 0; i < regions.length(); i++) {
                cachedRegions.add(regions.getString(i));
            }
        }
        Log.d(TAG, "Index loaded: " + cachedRegions.size() + " regions, tile=" + cachedTileSizeDegrees + "°");
    }

    /** Compute tile keys (e.g. "20/-80") that overlap [minLat,maxLat] x [minLng,maxLng]. */
    private static List<String> computeTiles(double minLat, double maxLat,
                                              double minLng, double maxLng,
                                              int tileSize, Set<String> regions) {
        List<String> tiles = new ArrayList<>();
        int latStart = floorToGrid(minLat, tileSize);
        int latEnd   = floorToGrid(maxLat, tileSize);
        int lonStart = floorToGrid(minLng, tileSize);
        int lonEnd   = floorToGrid(maxLng, tileSize);

        for (int lat = latStart; lat <= latEnd; lat += tileSize) {
            for (int lon = lonStart; lon <= lonEnd; lon += tileSize) {
                String key = lat + "/" + lon;
                if (regions == null || regions.contains(key)) {
                    tiles.add(key);
                }
            }
        }
        return tiles;
    }

    private static int floorToGrid(double value, int tileSize) {
        return (int) Math.floor(value / tileSize) * tileSize;
    }

    private static List<Alpr> fetchTile(String tileUrl,
                                         double minLat, double maxLat,
                                         double minLng, double maxLng)
            throws IOException, JSONException {
        Log.d(TAG, "Fetching tile: " + tileUrl);
        String body = httpGet(tileUrl);
        return parseTile(body, minLat, maxLat, minLng, maxLng);
    }

    private static List<Alpr> parseTile(String json,
                                         double minLat, double maxLat,
                                         double minLng, double maxLng)
            throws JSONException {
        List<Alpr> result = new ArrayList<>();
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            double lat = obj.getDouble("lat");
            double lon = obj.getDouble("lon");

            // Filter to bounding box
            if (lat < minLat || lat > maxLat || lon < minLng || lon > maxLng) continue;

            // id may be integer or string in OSM data
            String id = String.valueOf(obj.get("id"));
            Map<String, String> tags = new HashMap<>();
            JSONObject tagsObj = obj.optJSONObject("tags");
            if (tagsObj != null) {
                Iterator<String> keys = tagsObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    tags.put(key, tagsObj.optString(key));
                }
            }
            result.add(new Alpr(id, lat, lon, tags, "node"));
        }
        return result;
    }

    private static String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + urlStr);

        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        try {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        } finally {
            reader.close();
            conn.disconnect();
        }
        return sb.toString();
    }
}
