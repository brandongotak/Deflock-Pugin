# ATAK SDK 5.4.0 Plugin Development Knowledge Base

Comprehensive reference for building ATAK plugins. Covers architecture, lifecycle, APIs, UI patterns, CoT, map integration, and best practices.

---

## 1. SDK Structure & Key Paths

```
ATAK-CIV-5.4.0.24-SDK/
├── atak.apk                    # Base ATAK application (354 MB)
├── main.jar                    # Core ATAK library (31 MB)
├── atak-javadoc.jar            # API documentation (13 MB)
├── atak-gradle-takdev.jar      # Gradle build plugin (151 KB)
├── android_keystore            # Default debug/release signing key
├── ATAK_Plugin_Development_Guide.pdf  # Official dev guide
├── CHANGELOG.txt               # Version history
├── proguard-release-keep.txt   # ProGuard rules
├── docs/
│   ├── BROADCAST.txt           # All broadcast intent actions (2240 lines)
│   ├── Build_Environment_Changes.pdf
│   ├── TakChatSpec.odt         # Chat protocol spec
│   ├── takcot.zip              # CoT protocol specs
│   └── takproto.zip            # Protocol buffer definitions
├── plugins/                    # 24+ community/reference plugins
│   ├── Skeletion-ATAK-Plugin/  # Your skeleton template
│   ├── Address-ATAK-Plugin/    # Geocoding reference plugin
│   └── ...
└── samples/                    # 27 official sample plugins
    ├── helloworld/             # Comprehensive feature showcase
    ├── plugintemplate/          # Modern IPlugin template
    ├── plugintemplate-compose/  # Jetpack Compose variant
    ├── cotinjector/            # CoT event generation
    ├── radialmenudemo/         # Radial menu patterns
    ├── action-bar-demo/        # NavView/action bar control
    ├── customtiles/            # Map tile integration
    ├── hello3d/                # 3D graphics
    ├── hellojni/               # Native JNI integration
    ├── sensortester/           # Sensor data access
    └── ...
```

---

## 2. Plugin Architecture — Two Patterns

### Pattern A: IPlugin (Modern Pane API) — RECOMMENDED

Used by: `plugintemplate`, `action-bar-demo`, skeleton plugin

```java
public class MyPlugin implements IPlugin {
    private IServiceController serviceController;
    private Context pluginContext;
    private IHostUIService uiService;
    private ToolbarItem toolbarItem;
    private Pane myPane;

    public MyPlugin() { /* Required no-arg constructor */ }

    public MyPlugin(IServiceController serviceController) {
        this.serviceController = serviceController;
        this.pluginContext = serviceController
            .getService(PluginContextProvider.class).getPluginContext();
        this.uiService = serviceController.getService(IHostUIService.class);

        // Create toolbar icon
        Drawable icon = pluginContext.getDrawable(R.drawable.ic_plugin);
        toolbarItem = new ToolbarItem.Builder(
            pluginContext.getString(R.string.app_name),
            MarshalManager.marshal(icon, Drawable.class,
                gov.tak.api.commons.graphics.Bitmap.class))
            .setListener(new ToolbarItemAdapter() {
                @Override
                public void onClick(ToolbarItem item) { showPane(); }
            })
            .build();

        // Register preferences
        registerPreferences();
    }

    @Override
    public void onStart() {
        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        uiService.removeToolbarItem(toolbarItem);
    }

    private void showPane() {
        if (myPane == null) {
            View view = PluginLayoutInflater.inflate(
                pluginContext, R.layout.main_layout, null);
            setupUI(view);
            myPane = new PaneBuilder(view)
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                .build();
        }
        uiService.showPane(myPane, null);
    }
}
```

### Pattern B: AbstractPlugin + DropDownMapComponent (Legacy but Feature-Rich)

Used by: `helloworld`, `radialmenudemo`, Address plugin

```java
// Lifecycle entry point
public class MyLifecycle extends AbstractPlugin {
    public MyLifecycle(IServiceController serviceController) {
        super(serviceController,
            new MyTool(serviceController.getService(
                PluginContextProvider.class).getPluginContext()),
            new MyMapComponent());
    }
}

// Tool (toolbar button)
public class MyTool extends AbstractPluginTool {
    public MyTool(Context context) {
        super(context,
            context.getString(R.string.app_name),
            context.getString(R.string.app_name),
            context.getResources().getDrawable(R.drawable.ic_launcher),
            "com.example.myplugin.SHOW_PLUGIN");
    }
}

// Map component (core logic)
public class MyMapComponent extends DropDownMapComponent {
    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        context.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(context, intent, view);

        // Register dropdown receiver
        MyDropDown dd = new MyDropDown(view, context);
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.example.myplugin.SHOW_PLUGIN", "Show plugin panel");
        registerDropDownReceiver(dd, filter);

        // Register preferences
        ToolsPreferenceFragment.register(
            new ToolsPreferenceFragment.ToolPreference(
                "My Plugin", "Plugin settings", "myPluginPrefs",
                context.getDrawable(R.drawable.ic_launcher),
                new MyPreferenceFragment(context)));

        // Register map overlay
        MyOverlay overlay = new MyOverlay(view, context);
        view.getMapOverlayManager().addOverlay(overlay);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        // Cleanup in reverse order
        ToolsPreferenceFragment.unregister("myPluginPrefs");
        super.onDestroyImpl(context, view);
    }
}
```

---

## 3. Plugin Lifecycle

```
ATAK loads plugin APK
    ↓
Reads assets/plugin.xml → finds IPlugin impl class
    ↓
Calls Constructor(IServiceController)
    ├── Get pluginContext via PluginContextProvider
    ├── Get IHostUIService for UI
    ├── Create ToolbarItem with icon
    └── Register preferences
    ↓
onStart() called when ATAK is ready
    ├── Add toolbar item (icon becomes visible)
    ├── Register CoT handlers
    ├── Register broadcast receivers
    └── Start background services
    ↓
User interaction (toolbar tap, broadcasts, CoT events)
    ↓
onStop() called on shutdown
    ├── Unregister handlers & receivers
    ├── Remove toolbar item
    ├── Dispose widgets & overlays
    └── Close databases
```

**Critical cleanup rules:**
- Always unregister receivers in onDestroy/onStop
- Dispose long-running threads
- Clear bitmap caches
- Close database connections
- Remove map overlays

---

## 4. Build Configuration (build.gradle)

### Root build.gradle
```groovy
buildscript {
    ext.PLUGIN_VERSION = "1.0.0"
    ext.ATAK_VERSION = "5.2.0"

    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
        maven {
            url = "https://artifacts.tak.gov/artifactory/maven"
            credentials {
                username = project.findProperty("takrepo.user") ?: ""
                password = project.findProperty("takrepo.password") ?: ""
            }
        }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:7.4.2'
        def takdevVersion = '3.+'
        classpath "com.atakmap.gradle:atak-gradle-takdev:${takdevVersion}"
    }
}
```

### App build.gradle — Critical Settings
```groovy
apply plugin: 'com.android.application'
apply plugin: 'atak-takdev-plugin'

android {
    compileSdkVersion 34
    buildToolsVersion "30.0.3"

    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 34
        ndk { abiFilters "armeabi-v7a", "arm64-v8a", "x86" }
    }

    // CRITICAL: Required for plugin loading
    bundle { storeArchive { enable = false } }
    packagingOptions { jniLibs { useLegacyPackaging = true } }

    // Signing with ATAK default keystore
    signingConfigs {
        release {
            storeFile file("${rootDir}/android_keystore")
            storePassword "tnttnt"
            keyAlias "wintec_mapping"
            keyPassword "tnttnt"
        }
    }

    buildTypes {
        debug {
            debuggable true
            matchingFallbacks = ['sdk']
        }
        release {
            minifyEnabled true
            proguardFile 'proguard-rules.pro'
            signingConfig signingConfigs.release
            matchingFallbacks = ['odk']
        }
    }

    // Flavor configuration
    flavorDimensions "application"
    productFlavors {
        civ { dimension "application" }
        // Add: mil, gov, etc. as needed
    }
}

dependencies {
    implementation fileTree(dir: 'libs', include: '*.jar')
    implementation 'androidx.recyclerview:recyclerview:1.2.1'
}
```

### local.properties (REQUIRED)
```properties
sdk.dir=C:/Users/matth/AppData/Local/Android/Sdk
takrepo.url=https://artifacts.tak.gov/artifactory/maven
takrepo.user=YOUR_USERNAME
takrepo.password=YOUR_PASSWORD
takdev.plugin=.
```

### Build Output
APK location: `app/build/outputs/atak-apks/sdk/`
Naming: `ATAK-Plugin-{name}-{version}-{variant}-{ATAK_VERSION}.apk`

---

## 5. AndroidManifest.xml — Required Elements

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.myplugin">

    <!-- Android 11+ package visibility -->
    <queries>
        <package android:name="com.atakmap.app.civ" />
        <package android:name="com.atakmap.app" />
        <package android:name="com.atakmap.app.mil" />
        <package android:name="com.atakmap.app.gov" />
    </queries>

    <!-- Permissions as needed -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <application
        android:icon="@drawable/ic_plugin"
        android:label="@string/app_name"
        android:extractNativeLibs="true"
        android:allowBackup="false">

        <!-- REQUIRED: Plugin API version -->
        <meta-data android:name="plugin-api" android:value="${atakApiVersion}"/>

        <!-- REQUIRED: Discovery activity (ATAK 4.6.0.2+) -->
        <activity android:name="com.atakmap.app.component"
            android:exported="true">
            <intent-filter>
                <action android:name="com.atakmap.app.component" />
            </intent-filter>
        </activity>

        <!-- Optional: Permission activity (Android 13+) -->
        <activity android:name=".PluginPermissionActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />
    </application>
</manifest>
```

---

## 6. plugin.xml — Extension Declaration

Located at `app/src/main/assets/plugin.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<plugin>
    <extension
        type="gov.tak.api.plugin.IPlugin"
        impl="com.example.myplugin.MyPlugin"
        singleton="true" />
</plugin>
```

- `type`: Always `gov.tak.api.plugin.IPlugin`
- `impl`: Fully qualified class name of your IPlugin implementation
- `singleton`: Always `"true"`

---

## 7. UI: Toolbar & Pane API

### Creating a Toolbar Item
```java
Drawable icon = pluginContext.getDrawable(R.drawable.ic_plugin);
ToolbarItem item = new ToolbarItem.Builder(
    pluginContext.getString(R.string.app_name),
    MarshalManager.marshal(icon,
        android.graphics.drawable.Drawable.class,
        gov.tak.api.commons.graphics.Bitmap.class))
    .setListener(new ToolbarItemAdapter() {
        @Override
        public void onClick(ToolbarItem item) { showPane(); }
    })
    .build();
uiService.addToolbarItem(item);    // in onStart()
uiService.removeToolbarItem(item); // in onStop()
```

### Creating a Pane (Drop-Down Panel)
```java
View view = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
Pane pane = new PaneBuilder(view)
    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)  // 50% screen width
    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)  // 50% screen height
    .build();
uiService.showPane(pane, null);

// Check visibility
boolean visible = uiService.isPaneVisible(pane);
```

### Pane Sizing Options
- `0.5D` = half screen (side panel)
- `1.0D` = full screen
- Adjustable per orientation

---

## 8. UI: DropDownReceiver (Legacy Pattern)

```java
public class MyDropDown extends DropDownReceiver
    implements DropDown.OnStateListener {

    public static final String SHOW_PLUGIN = "com.example.myplugin.SHOW";

    public MyDropDown(MapView mapView, Context pluginContext) {
        super(mapView);
        // Initialize UI
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (SHOW_PLUGIN.equals(action)) {
            showDropDown(rootView,
                HALF_WIDTH, FULL_HEIGHT,   // landscape
                FULL_WIDTH, HALF_HEIGHT,   // portrait
                false, this);
        }
    }

    @Override
    public void disposeImpl() { /* cleanup */ }

    // DropDown.OnStateListener methods
    @Override
    public void onDropDownVisible(boolean visible) { }
    @Override
    public void onDropDownSelectionRemoved() { }
    @Override
    public void onDropDownSizeChanged(double width, double height) { }
    @Override
    public void onDropDownClose() { }
}
```

**Registration in MapComponent:**
```java
DocumentedIntentFilter filter = new DocumentedIntentFilter();
filter.addAction(MyDropDown.SHOW_PLUGIN, "Show plugin panel");
registerDropDownReceiver(myDropDown, filter);
```

---

## 9. Layout Inflation

**CRITICAL**: Always use `PluginLayoutInflater` for plugin views, NOT the standard `LayoutInflater`. This ensures resources resolve from the plugin APK, not from ATAK core.

```java
View view = PluginLayoutInflater.inflate(pluginContext, R.layout.main_layout, null);
```

**Theme**: Always set theme before inflating:
```java
context.setTheme(R.style.ATAKPluginTheme);
```

---

## 10. CoT (Cursor on Target)

### CoT Event XML Structure
```xml
<?xml version="1.0" encoding="UTF-8"?>
<event version="2.0"
    uid="unique-identifier-uuid"
    type="a-f-G-U-C"
    time="2025-02-24T12:34:56.789Z"
    start="2025-02-24T12:34:56.789Z"
    stale="2025-02-24T12:39:56.789Z"
    how="m-g">
    <point lat="38.8977" lon="-77.0365" hae="0" ce="9999999" le="9999999"/>
    <detail>
        <contact callsign="Alpha-1"/>
        <__mydetail customField="value"/>
    </detail>
</event>
```

### Creating & Dispatching CoT Events
```java
CotEvent event = new CotEvent();
event.setUID(UUID.randomUUID().toString());
event.setType("a-f-G-U-C");  // friendly ground unit combat
event.setHow("m-g");          // machine-generated

CoordinatedTime now = new CoordinatedTime();
event.setTime(now);
event.setStart(now);
event.setStale(new CoordinatedTime(now.getMilliseconds() + 300000)); // 5 min

CotPoint point = new CotPoint(lat, lon, hae, ce, le);
event.setPoint(point);

CotDetail detail = new CotDetail("detail");
CotDetail contact = new CotDetail("contact");
contact.setAttribute("callsign", "Alpha-1");
detail.addChild(contact);
event.setDetail(detail);

// Dispatch to ATAK
CotMapComponent.getInternalDispatcher().dispatch(event);
```

### CotDetailHandler — Custom CoT Data
```java
public class MyCotHandler extends CotDetailHandler {
    public static final String DETAIL_NAME = "__myplugin";

    public MyCotHandler() { super(DETAIL_NAME); }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        // Serialize map item data INTO CoT detail
        CotDetail myDetail = new CotDetail(DETAIL_NAME);
        myDetail.setAttribute("customVal", item.getMetaString("myVal", ""));
        detail.addChild(myDetail);
        return true;
    }

    @Override
    public CommsMapComponent.ImportResult toItemMetadata(
            MapItem item, CotEvent event, CotDetail detail) {
        // Parse incoming CoT detail ONTO map item
        String val = detail.getAttribute("customVal");
        if (val != null) {
            item.setMetaString("myVal", val);
            return CommsMapComponent.ImportResult.SUCCESS;
        }
        return CommsMapComponent.ImportResult.FAILURE;
    }
}

// Register/unregister
CotDetailManager.getInstance().registerHandler(DETAIL_NAME, handler);
CotDetailManager.getInstance().unregisterHandler(handler);
```

---

## 11. CoT Type Code Reference

Format: `a-{affiliation}-{dimension}-{function}`

### Affiliation
| Code | Meaning |
|------|---------|
| f | Friendly |
| h | Hostile |
| u | Unknown |
| n | Neutral |
| a | Assumed Friend |
| s | Suspect |
| j | Joker |
| k | Faker |

### Dimension
| Code | Meaning |
|------|---------|
| A | Air |
| G | Ground |
| S | Sea Surface |
| U | Subsurface |
| P | Point/Position |
| I | Installation |
| E | Equipment |

### Common Types
| Type | Description |
|------|-------------|
| a-f-G-U-C | Friendly ground unit, combat |
| a-f-G-U-C-I | Friendly ground infantry |
| a-h-G | Hostile ground |
| a-f-A | Friendly air |
| a-n-G | Neutral ground |
| b-m-p-s-p-i | Bits - mission point (SPI) |
| b-m-p-w-GOTO | Mission waypoint (GOTO) |
| u-d-p | User-defined point |

---

## 12. Map Integration

### MapView — Primary Map Reference
```java
MapView mapView = MapView.getMapView();
```

### MapOverlay — Custom Overlay in Overlay Manager
```java
public class MyOverlay extends AbstractMapOverlay2 {
    private DefaultMapGroup group = new DefaultMapGroup("My Plugin Items");

    @Override
    public String getIdentifier() { return "MyPluginOverlay"; }

    @Override
    public String getName() { return "My Plugin"; }

    @Override
    public MapGroup getRootGroup() { return group; }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter filter) {
        return new MyHierarchyListItem(adapter, filter, group);
    }
}

// Register
mapView.getMapOverlayManager().addOverlay(overlay);
// Unregister
mapView.getMapOverlayManager().removeOverlay(overlay);
```

### Creating Map Markers
```java
// Create a marker
Marker marker = new Marker(GeoPointMetaData.wrap(
    new GeoPoint(lat, lon)), UUID.randomUUID().toString());
marker.setType("a-f-G-U-C");
marker.setTitle("My Marker");
marker.setMetaString("callsign", "Alpha");
marker.setMetaBoolean("addToObjList", false); // hide from "User Objects"

// Add to a map group
mapGroup.addItem(marker);

// Remove
mapGroup.removeItem(marker);
```

---

## 13. Map Widgets

```java
public class MyWidget extends AbstractWidgetMapComponent {
    private MarkerIconWidget iconWidget;

    @Override
    protected void onCreateWidgets(Context context, Intent intent, MapView view) {
        iconWidget = new MarkerIconWidget();
        iconWidget.setName("MyWidget");
        // Position and configure widget
        RootLayoutWidget root = (RootLayoutWidget) view.getComponentExtra("rootLayoutWidget");
        LinearLayoutWidget topRight = root.getLayout(RootLayoutWidget.TOP_RIGHT);
        topRight.addWidgetAt(0, iconWidget);
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        iconWidget.setVisible(false);
    }
}
```

---

## 14. Preferences & Settings

### Registering Preferences in ATAK Settings Menu
```java
ToolsPreferenceFragment.register(
    new ToolsPreferenceFragment.ToolPreference(
        "My Plugin Settings",             // title
        "Configure my plugin",            // summary
        "myPluginPreferences",            // unique key
        context.getDrawable(R.drawable.ic_plugin),  // icon
        new MyPreferenceFragment(pluginContext)));   // fragment

// Cleanup
ToolsPreferenceFragment.unregister("myPluginPreferences");
```

### Preference Fragment
```java
public class MyPreferenceFragment extends PluginPreferenceFragment {
    public MyPreferenceFragment(Context pluginContext) {
        super(pluginContext, R.xml.preferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Access preferences by key
        PanCheckBoxPreference toggle = (PanCheckBoxPreference)
            findPreference("pref_my_toggle");
    }

    @Override
    public String getSubTitle() {
        return "My Plugin Settings";
    }
}
```

### res/xml/preferences.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
    <PanCheckBoxPreference
        android:key="pref_my_toggle"
        android:title="Enable Feature"
        android:summary="Turn this feature on/off"
        android:defaultValue="false" />

    <PanEditTextPreference
        android:key="pref_my_text"
        android:title="Server URL"
        android:summary="Enter the server address"
        android:defaultValue="" />

    <PanListPreference
        android:key="pref_my_list"
        android:title="Update Interval"
        android:entries="@array/interval_labels"
        android:entryValues="@array/interval_values"
        android:defaultValue="30" />
</PreferenceScreen>
```

### SharedPreferences Wrapper
```java
public class MyPreferences {
    private final SharedPreferences prefs;

    public MyPreferences(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean isFeatureEnabled() {
        return prefs.getBoolean("pref_my_toggle", false);
    }

    public void setFeatureEnabled(boolean enabled) {
        prefs.edit().putBoolean("pref_my_toggle", enabled).apply();
    }

    // Encrypted credentials (ATAK API)
    // AtakAuthenticationDatabase.saveCredentials(key, host, user, pass, false);
    // AtakAuthenticationDatabase.getCredentials(key, host);
}
```

---

## 15. Broadcasting & IPC

### Sending Broadcasts
```java
// Send a local broadcast
Intent intent = new Intent("com.example.myplugin.SHOW");
intent.putExtra("uid", markerUid);
AtakBroadcast.getInstance().sendBroadcast(intent);
```

### Receiving Broadcasts
```java
BroadcastReceiver receiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        String uid = intent.getStringExtra("uid");
        // Handle the broadcast
    }
};
AtakBroadcast.getInstance().registerReceiver(receiver,
    new DocumentedIntentFilter("com.example.myplugin.ACTION"));

// Cleanup
AtakBroadcast.getInstance().unregisterReceiver(receiver);
```

### Common ATAK Broadcast Actions

| Action | Description |
|--------|-------------|
| `com.atakmap.app.COMPONENTS_CREATED` | All ATAK components initialized |
| `com.atakmap.app.QUITAPP` | ATAK is shutting down |
| `com.atakmap.android.maps.FOCUS` | Focus/select a map item |
| `com.atakmap.android.maps.UNFOCUS` | Deselect map item |
| `com.atakmap.android.maps.SNAP_TO_SELF` | Center map on self |
| `com.atakmap.android.maps.TRACK_UP` | Set track-up orientation |
| `com.atakmap.android.maps.NORTH_UP` | Set north-up orientation |
| `com.atakmap.android.map.action.LOCK_CAM` | Lock camera on item |
| `com.atakmap.android.map.action.UNLOCK_CAM` | Unlock camera |
| `com.atakmap.android.maps.SHOW_MENU` | Show radial menu |
| `com.atakmap.android.maps.HIDE_MENU` | Hide radial menu |
| `com.atakmap.android.user.ENTER_LOCATION_DROP_DOWN` | Open point dropper |
| `com.atakmap.android.contact.CONTACT_LIST` | Show contacts |
| `com.atakmap.android.cotdetails.COTINFO` | Show CoT details |
| `com.atakmap.android.tools.RELOAD_ACTION_BAR` | Reload action bar |
| `com.atakmap.android.tools.ADD_NEW_TOOL` | Add toolbar tool |
| `com.atakmap.android.maps.toolbar.BEGIN_TOOL` | Begin tool mode |
| `com.atakmap.android.maps.toolbar.END_TOOL` | End tool mode |
| `com.atakmap.android.maps.route.EDIT_ROUTE` | Edit a route |
| `com.atakmap.android.maps.route.START_NAV` | Start navigation |
| `com.atakmap.android.maps.route.END_NAV` | End navigation |
| `com.atakmap.android.missionpackage.MISSIONPACKAGE` | Mission package actions |
| `com.atakmap.android.geofence.EDIT` | Edit geofence |

---

## 16. Database & Storage

### SQLiteOpenHelper Pattern
```java
public class MyDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "myplugin.db";
    private static final String DB_DIR = "/sdcard/atak/tools/myplugin/";
    private static final int DB_VERSION = 1;

    public MyDatabaseHelper(Context context) {
        super(context, DB_DIR + DB_NAME, null, DB_VERSION);
        // Ensure directory exists
        new File(DB_DIR).mkdirs();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "data TEXT NOT NULL, " +
            "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
        // Handle migrations
    }
}
```

### ATAK File Paths
| Path | Purpose |
|------|---------|
| `/sdcard/atak/` | ATAK root directory |
| `/sdcard/atak/tools/{plugin}/` | Plugin-specific data |
| `/sdcard/atak/Databases/` | Shared databases |
| `/sdcard/atak/export/` | Exported files |
| `/sdcard/atak/support/logs/` | Log files |

---

## 17. Radial Menu

### Showing the Radial Menu
```java
Intent intent = new Intent(MapMenuReceiver.SHOW_MENU);
intent.putExtra("point", geoPoint.toString());
AtakBroadcast.getInstance().sendBroadcast(intent);
```

### Custom Menu Items
Radial menus are defined in XML or programmatically using `MapMenuWidget`.

---

## 18. Action Bar / NavView Control

```java
NavView actionBar = NavView.getInstance();

// Check state
boolean visible = actionBar.buttonsVisible();
boolean locked = actionBar.buttonsLocked();

// Toggle visibility
Intent toggle = new Intent(NavView.TOGGLE_BUTTONS);
AtakBroadcast.getInstance().sendBroadcast(toggle);

// Lock/unlock buttons
Intent lock = new Intent(NavView.LOCK_BUTTONS);
lock.putExtra("lock", true);
AtakBroadcast.getInstance().sendBroadcast(lock);

// Listen for visibility changes
actionBar.addButtonVisibilityListener(visible -> {
    // Handle change
});
```

---

## 19. Native Library Loading

```java
public class PluginNativeLoader {
    private static String ndl = null;

    public static void init(Context context) {
        try {
            PackageInfo pi = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            ndl = pi.applicationInfo.nativeLibraryDir;
        } catch (Exception ignored) {}
    }

    public static void loadLibrary(String name) {
        if (ndl != null) {
            File lib = new File(ndl, "lib" + name + ".so");
            if (lib.exists()) {
                System.load(lib.getAbsolutePath());
                return;
            }
        }
        System.loadLibrary(name);
    }
}
```

---

## 20. Import/Export

### Register Custom Exporter
```java
ExporterManager.registerExporter(
    "My Format",
    context.getDrawable(R.drawable.ic_export),
    MyExportMarshal.class);
```

### State Saver Integration
```java
// Wait for state saver to finish loading
AtakBroadcast.getInstance().registerReceiver(new BroadcastReceiver() {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        reprocessMapItems();
    }
}, new DocumentedIntentFilter(StateSaverPublisher.STATESAVER_COMPLETE_LOAD));

if (StateSaverPublisher.isFinished()) {
    reprocessMapItems();
}
```

---

## 21. New Plugin Checklist

1. **Copy skeleton template** — Duplicate `Skeletion-ATAK-Plugin/` and rename
2. **Rename package** — Change `com.atakmap.android.skeleton` to your package in:
   - All Java files (package declaration + imports)
   - `AndroidManifest.xml` (package attribute)
   - `plugin.xml` (impl attribute)
   - `build.gradle` (namespace/applicationId)
3. **Update strings** — Edit `res/values/strings.xml` (app_name, app_desc)
4. **Update icon** — Replace `res/drawable/ic_skeleton.xml` with your icon
5. **Configure build** — Update `PLUGIN_VERSION` and `ATAK_VERSION` in `build.gradle`
6. **Set up local.properties** — Copy from `template.local.properties`, fill in SDK path and TAK repo credentials
7. **Customize main layout** — Edit `res/layout/main_layout.xml`
8. **Add plugin logic** — Modify the main plugin class (toolbar click handler, UI setup)
9. **Add CoT handling** — Customize `CotHandler` if needed for custom data
10. **Add preferences** — Update `res/xml/preferences.xml` and preference fragment
11. **Build** — `./gradlew assembleCivDebug`
12. **Deploy** — Install APK on device/emulator with ATAK already running
13. **Test** — ATAK will detect and prompt to load the plugin

---

## 22. Common Pitfalls

### Lambda + ProGuard
Lambdas work in debug but **break in release builds** due to ProGuard obfuscation. Use anonymous inner classes for release-critical code, or configure ProGuard to keep lambda-generated classes.

### Context Usage
- Use `pluginContext` for resource resolution (layouts, drawables, strings)
- Use `mapView.getContext()` (ATAK context) for AlertDialogs, toasts, and system services
- **Never** use plugin context for AlertDialog — it will crash

### Build Variant Selection
Android Studio defaults to alphabetical first flavor (e.g., `ausDebug`). Always manually select `civDebug` in Build > Select Build Variants.

### Theme Application
Always call `context.setTheme(R.style.ATAKPluginTheme)` before `super.onCreate()` in DropDownMapComponent.

### Discovery Activity
The `com.atakmap.app.component` activity in AndroidManifest.xml is **required** for ATAK 4.6.0.2+. Without it, ATAK will not discover the plugin.

### Bundle Store Archive
`bundle { storeArchive { enable = false } }` is **required** in build.gradle. Without it, the plugin APK won't load correctly.

### JNI Legacy Packaging
`packagingOptions { jniLibs { useLegacyPackaging = true } }` is **required** for native library support.

### Cleanup Requirements
Every `registerReceiver()` must have a matching `unregisterReceiver()`. Every `addOverlay()` must have `removeOverlay()`. Every `register()` must have `unregister()`. Failing to clean up causes memory leaks and crashes on plugin reload.

### ProGuard Keep Rules
Plugin classes implementing `IPlugin`, `MapComponent`, activities, services, and broadcast receivers must be kept. The skeleton's `proguard-rules.pro` handles this, but custom additions may need extra keep rules.

---

## 23. Key API Imports Reference

```java
// Plugin framework
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.platform.marshal.MarshalManager;
import gov.tak.platform.ui.PluginLayoutInflater;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.widgets.IToolbarExtension;
import gov.tak.api.widgets.ToolbarItem;
import gov.tak.api.widgets.ToolbarItemAdapter;
import gov.tak.api.plugin.PluginContextProvider;

// Map and CoT
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.detail.CotDetailManager;

// UI components
import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.DocumentedIntentFilter;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.overlay.AbstractMapOverlay2;

// Preferences
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.gui.PanCheckBoxPreference;
import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.gui.PanListPreference;

// Legacy plugin framework
import transapps.mapi.MapView;
import transapps.maps.plugin.tool.AbstractPluginTool;
import com.atakmap.android.maps.AbstractPlugin;
```

---

## 24. Version Compatibility Notes

| SDK Version | Key Changes |
|-------------|-------------|
| 5.4.0 | Pane API black screen fix, stability improvements |
| 5.3.0 | 3D streaming tiles, MIL-STD 2525 search, geofence alerts |
| 5.2.0 | Stars rendering, streaming elevation, metrics API, QR code support |
| 5.1.0 | tak:// URI scheme, enhanced depth perception |
| 5.0.0 | Globe mode default, GeoPDF support, unified build |
| 4.8.0 | takkernel migration, CoT 2525C palette |
| 4.7.0 | takengine@2.0, URL links in CoT, Android 11 support |
| 4.6.0 | Plugin discovery activity requirement, encrypted preferences |
| 4.5.0 | AndroidX migration, inner plugin signing for Play Store |
| 4.1.0 | Android 29+ target, AndroidX migration, 3D model support |
| 4.0.0 | Hashtags, BLE API, Gradle 6.2 |

---

## 25. Supported Build Flavors

| Flavor | Distribution |
|--------|-------------|
| civ | Civilian (default) |
| mil | US Military |
| gov | US Government |
| aus | Australia |
| nzl | New Zealand |
| gbr | United Kingdom |
| can | Canada |
| nor | Norway |
| swe | Sweden |
| prt | Portugal |
| hun | Hungary |
| bel | Belgium |
| est | Estonia |
