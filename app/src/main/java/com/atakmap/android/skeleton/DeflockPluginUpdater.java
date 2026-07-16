package com.atakmap.android.skeleton;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Plugin self-update: query the GitHub Releases API for the latest published
 * release and, if it is newer than what's installed, offer to download &amp;
 * install its APK asset.
 *
 * <p>Versions are compared as dotted numeric strings: the release's
 * {@code tag_name} (a leading "v" is tolerated) against
 * {@link BuildConfig#PLUGIN_VERSION}. The generated {@code versionCode} is
 * deliberately not used — it is derived from build metadata and does not
 * correspond to anything a release tag can express.
 */
public final class DeflockPluginUpdater {

    private static final String TAG = "DeflockPluginUpdater";

    private static final String GITHUB_OWNER = "brandongotak";
    private static final String GITHUB_REPO = "Deflock-Pugin";

    private static final String RELEASES_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String RELEASES_PAGE_URL =
            "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private DeflockPluginUpdater() {
    }

    /** Query GitHub for the latest release and, if newer, offer to install it. */
    public static void checkForUpdate(final Context pluginContext) {
        Toast.makeText(pluginContext, "Checking for updates…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String latestVersion = null;
            String downloadUrl = null;
            String error = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                conn.setRequestProperty("User-Agent", "Deflock-ATAK-Plugin/" + BuildConfig.PLUGIN_VERSION);
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject o = new JSONObject(sb.toString());
                    latestVersion = normalizeVersion(o.optString("tag_name", ""));
                    downloadUrl = findApkAsset(o.optJSONArray("assets"));
                    if (downloadUrl == null) error = "release has no .apk asset";
                } else if (code == 404) {
                    error = "no published releases yet";
                } else {
                    error = "GitHub returned HTTP " + code;
                }
            } catch (Exception e) {
                error = e.getMessage();
                Log.w(TAG, "Update check failed", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
            final String fLatestVersion = latestVersion;
            final String fDownloadUrl = downloadUrl;
            final String fError = error;
            new Handler(Looper.getMainLooper()).post(
                    () -> onUpdateCheckResult(pluginContext, fLatestVersion, fDownloadUrl, fError));
        }, "Deflock-UpdateCheck").start();
    }

    /** Pick the first asset whose name ends in .apk. */
    private static String findApkAsset(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            if (a.optString("name", "").toLowerCase().endsWith(".apk")) {
                String url = a.optString("browser_download_url", "");
                if (!url.isEmpty()) return url;
            }
        }
        return null;
    }

    /** Strip a leading "v" and any surrounding whitespace from a release tag. */
    private static String normalizeVersion(String tag) {
        String v = tag.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v;
    }

    /**
     * Compare dotted numeric versions. Returns &gt;0 if a is newer than b, 0 if
     * equal, &lt;0 if older. Missing components count as 0, so "1.1" equals
     * "1.1.0". Non-numeric components compare as 0 rather than throwing.
     */
    static int compareVersions(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length ? parseOrZero(as[i]) : 0;
            int bi = i < bs.length ? parseOrZero(bs[i]) : 0;
            if (ai != bi) return ai < bi ? -1 : 1;
        }
        return 0;
    }

    private static int parseOrZero(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void onUpdateCheckResult(Context pluginContext, String latestVersion,
            String downloadUrl, String error) {
        String installed = BuildConfig.PLUGIN_VERSION;
        if (error != null || latestVersion == null || latestVersion.isEmpty()) {
            Toast.makeText(pluginContext,
                    "Update check failed" + (error != null ? " (" + error + ")" : ""),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (compareVersions(latestVersion, installed) > 0) {
            Context ctx = MapView.getMapView() != null ? MapView.getMapView().getContext() : pluginContext;
            final String url = downloadUrl;
            try {
                new AlertDialog.Builder(ctx)
                        .setTitle("Update available")
                        .setMessage("A newer Deflock plugin is available.\n\nLatest: " + latestVersion
                                + "\nInstalled: " + installed)
                        .setPositiveButton("Download & install", (d, w) -> downloadAndInstallUpdate(pluginContext, url))
                        .setNegativeButton("Later", null)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to show update dialog", e);
            }
        } else {
            Toast.makeText(pluginContext, "You're on the latest version (" + installed + ")",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Download the APK via DownloadManager; tapping the completion notification
     * hands it to the system installer.
     */
    private static void downloadAndInstallUpdate(Context pluginContext, String apkUrl) {
        try {
            Context ctx = MapView.getMapView() != null ? MapView.getMapView().getContext() : pluginContext;
            android.app.DownloadManager dm =
                    (android.app.DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            android.app.DownloadManager.Request req =
                    new android.app.DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("Deflock plugin update");
            req.setDescription("Downloading the latest Deflock plugin");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, "Deflock-plugin-update.apk");
            dm.enqueue(req);
            Toast.makeText(pluginContext,
                    "Downloading update… tap the download notification to install when it finishes.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "DownloadManager failed, opening the releases page instead", e);
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                pluginContext.startActivity(i);
            } catch (Exception e2) {
                Toast.makeText(pluginContext, "Could not start the update download", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
