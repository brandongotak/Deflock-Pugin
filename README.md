# Deflock ATAK Plugin

An ATAK plugin that displays crowdsourced **Automatic License Plate Reader (ALPR)** camera locations on the map, sourced from the [Deflock](https://deflock.org) database.

**ATAK SDK:** 5.4.0 &nbsp;|&nbsp; **Min SDK:** 21 &nbsp;|&nbsp; **Version:** 1.0.0

---

## Features

- **Live ALPR data** — fetches camera locations from the Deflock CDN tile system covering 100,000+ crowdsourced cameras worldwide
- **Adjustable search radius** — 5 / 10 / 25 / 50 / 100 km around the current map center
- **Map markers** — red dot markers placed directly on the ATAK map for every camera in range
- **Scrollable list** — manufacturer and operator shown per row with tap-to-detail
- **Detail dialog** — manufacturer, operator, surveillance type, direction, coordinates, and OSM ID with a **Pan to** button
- **SQLite cache** — previously fetched cameras load instantly on reopen without a network call
- **Offline persistence** — cached data survives app restarts

---

## Data Source

Camera data is served from the [Deflock](https://deflock.org) project — a community-driven effort to map ALPR deployments using OpenStreetMap.

| CDN endpoint | Purpose |
|---|---|
| `cdn.deflock.me/regions/index.json` | Tile index — URL template, 20° tile grid |
| `cdn.deflock.me/regions/{lat}/{lon}.json` | Camera data per tile |

The plugin fetches only the tiles that overlap the current search area and filters results client-side to the exact radius.

---

## Usage

1. Open ATAK and tap the **camera icon** in the toolbar
2. Select a search radius from the spinner (default: **25 km**)
3. Tap **Search** — cameras appear on the map and in the list below
4. Tap any list row to see full details and pan to the camera
5. Tap **Clear** to remove all markers and reset the cache

---

## Project Structure

```
app/src/main/java/com/atakmap/android/skeleton/
├── DeflockPlugin.java          # IPlugin lifecycle, toolbar, pane, search, detail dialog
├── Alpr.java                   # Data model — id, lat, lon, tags, manufacturer/operator helpers
├── DeflockService.java         # CDN tile fetch — index, tile grid computation, JSON parsing
├── DeflockMapOverlay.java      # AbstractMapOverlay2 — red dot markers, icon drawn programmatically
├── DeflockAdapter.java         # RecyclerView adapter — manufacturer, operator, direction per row
├── DeflockDatabaseHelper.java  # SQLite cache at /sdcard/atak/tools/deflock/deflock.db
├── DeflockPreferences.java     # SharedPreferences wrapper
└── DeflockPreferencesFragment.java  # Settings UI in ATAK Tools menu
```

---

## Building

### Prerequisites

- ATAK CIV 5.4.0+ installed on your device or emulator
- This plugin must live inside the ATAK SDK's `plugins/` directory
- JDK 17 (Gradle 7.4.2 requires Java 17 — incompatible with Java 21)
- `local.properties` configured (see `template.local.properties`)

### Build & Install

```bash
# From Git Bash or WSL — handles Java 17 detection, build, uninstall, and install automatically
./install-plugin.sh
```

Or manually:

```bash
./gradlew assembleCivDebug
adb install -r app/build/outputs/apk/civ/debug/*.apk
```

### Bumping the Version

Edit `ext.PLUGIN_VERSION` in `app/build.gradle` — the version string in the plugin UI is injected automatically at build time via `BuildConfig.PLUGIN_VERSION`.

---

## Settings

Open **ATAK → Settings → Tools → Deflock Settings**:

| Setting | Default | Description |
|---|---|---|
| Max Search Radius | 20 km | Upper bound for the radius spinner |
| Show Labels | Off | Display operator names on map markers |

---

## License

MIT
