package dev.awaken.city;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named viewpoints for tours and the fixed "studio" set of landmark framings (reused at every milestone so the shots
 * compare). Plan coordinates: eye (x, ry, z) and a target the camera looks at; the sky views keep the S2 camera limits
 * (≤ 215 blocks from the player, who stands at the eye, and ≤ 200 above the ground) and fly over the north-west half
 * looking south-east across the spire.
 */
final class CityViews {
    private CityViews() {}

    record View(double x, double ry, double z, double tx, double tr, double tz) {
        float yaw() {
            return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
        }

        float pitch() {
            double h = Math.hypot(tx - x, tz - z);
            return (float) -Math.toDegrees(Math.atan2(tr - ry, Math.max(1e-3, h)));
        }
    }

    static final Map<String, View> VIEWS = new LinkedHashMap<>();

    static void add(String name, double x, double ry, double z, double tx, double tr, double tz) {
        VIEWS.put(name, new View(x, ry, z, tx, tr, tz));
    }

    /** Street views that look along the street nearest the spot (the longest clear run), not at a fixed target. */
    static final java.util.Set<String> ALONG = java.util.Set.of("market", "bellwatch", "works", "wallring", "garden");

    /** Views taken exactly where they say (indoors, underground): never moved onto open ground. */
    static final java.util.Set<String> EXACT = java.util.Set.of("cellar");

    static {
        // the sky, at dawn in the tour: over the north-west half, across the spire
        add("sky_nw", -178, 188, -168, 30, 20, 40);
        add("sky_nw_low", -205, 120, -130, 10, 40, -10);
        add("sky_n", -50, 175, -236, 20, 10, 40);
        add("sky_w", -236, 160, -35, 40, 10, 10);
        add("sky_top", 0, 199, 18, 0, 0, 17);
        // the start of the run
        add("roost_start", -169.5, 40.0, -129.5, -40, 30, -10);
        // street level, one per district (ALONG views pick the clearest street near the spot and look down it)
        add("square", 0, 1.0, 30, 0, 50, -46);
        add("market", 50, 1.0, 20, 0, 6, 0);
        add("bellwatch", -150, 1.0, -40, -150, 6, -62);
        add("quay", -58, 1.0, -44, -64, 14, 4);
        add("terrace", 0, 9.0, -104, 0, 30, -150);
        add("works", 30, 1.0, 140, 0, 6, 120);
        add("garden", 128, 1.0, 2, 200, 8, 0);
        add("wallring", -150, 1.0, 172, -180, 6, 140);
        add("wall_walk", -229.5, 40.0, -61.5, -190, 50, -146);
        // studio: the landmarks framed from above the roofs (clear of the houses), the same at every milestone
        add("studio_spire", 0, 18, 64, 0, 70, -46);
        add("studio_association", 0, 9.0, -96, 0, 26, -148);
        add("studio_roost", -138, 42, -96, -190, 52, -146);
        add("studio_high_gate", 0, 6, -292, 0, 30, -238);
        add("studio_dawn_gate", 290, 6, 0, 237, 30, 0);
        add("studio_bell_bridge", -72, 10, -50, -72, 0, -10);
        add("studio_cranes", -72, 2, 30, -60, 16, -10);
        add("studio_mills", -30, 12, 118, -64, 2, 116);
        add("studio_bellwatch_tower", -118, 36, -30, -150, 40, -62);
        add("studio_garden_tower", 182, 32, 72, 152, 34, 40);
        add("studio_terrace_tower", 102, 38, -98, 72, 40, -128);
        // M4: one facade close-up per district, from across the street, looking up a row of fronts (B1 onwards)
        add("facade_bellwatch", -138.5, 1.0, -59.5, -145, 9, -49);
        add("facade_quay", -104.5, 1.0, 50, -95, 7, 57.8);
        add("facade_market", 80.5, 1.0, -2.5, 74, 9, 5);
        add("facade_terrace", 86, 9.0, -130.5, 79.5, 17, -141);
        add("facade_garden", 148, 1.0, -2.5, 140.2, 7, 5);
        add("facade_works", 50, 1.0, 127.5, 55.2, 6, 118);
        add("facade_wallring", 225.5, 1.0, 2.5, 217.7, 6, -5);
        // and the roofscape from just over the roofs: ridge caps, dormers, crow steps, chimneys
        add("roofs_bellwatch", -118, 42, -18, -165, 14, -70);
        add("roofs_quay", -60, 40, -60, -90, 15, -120);
        // M4 B2: the relief. Bellwatch's Knoll from the canal side (the old town's roofs climbing to its tower), Choir
        // Square's bowl from the rim, the High Terrace's west retaining wall from the end of a quay lane, the gatehouse yard
        add("knoll", -95, 40, -110, -150, 14, -62);
        add("bowl", 22, 12, 26, 0, -3, 0);
        add("terrace_wall", -60, 2.0, -132.5, -44, 10, -134);
        add("gate_yard", 0, 14, -268, 0, 8, -244);
        add("cellar", -148.5, 1.0, -58.5, -155, 3, -66);
    }
}
