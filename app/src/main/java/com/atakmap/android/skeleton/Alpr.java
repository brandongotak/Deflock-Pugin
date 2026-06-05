package com.atakmap.android.skeleton;

import java.util.Collections;
import java.util.Map;

public class Alpr {
    public final String id;
    public final double lat;
    public final double lon;
    public final Map<String, String> tags;
    public final String type; // "node", "way", etc.

    public Alpr(String id, double lat, double lon, Map<String, String> tags, String type) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.tags = tags != null ? tags : Collections.<String, String>emptyMap();
        this.type = type != null ? type : "node";
    }

    public String getManufacturer() {
        String m = tags.get("manufacturer");
        if (m != null && !m.isEmpty()) return m;
        m = tags.get("surveillance:manufacturer");
        if (m != null && !m.isEmpty()) return m;
        m = tags.get("brand");
        if (m != null && !m.isEmpty()) return m;
        m = tags.get("surveillance:brand");
        if (m != null && !m.isEmpty()) return m;
        return "Unknown";
    }

    public String getOperator() {
        String op = tags.get("operator");
        if (op != null && !op.isEmpty()) return op;
        op = tags.get("surveillance:operator");
        if (op != null && !op.isEmpty()) return op;
        return "";
    }

    public String getDisplayName() {
        String mfr = getManufacturer();
        String op = getOperator();
        if (!op.isEmpty()) return mfr + " • " + op;
        return mfr;
    }

    public String getSurveillanceType() {
        String t = tags.get("surveillance:type");
        if (t != null && !t.isEmpty()) return t;
        return "ALPR";
    }

    public String getDirection() {
        return tags.getOrDefault("direction", "");
    }

    /** Unique ATAK map item UID */
    public String getMapUid() {
        return "deflock-" + id;
    }
}
