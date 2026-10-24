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

    static {
        // the sky, at dawn in the tour: over the north-west half, across the spire
        add("sky_nw", -178, 188, -168, 30, 20, 40);
        add("sky_nw_low", -205, 120, -130, 10, 40, -10);
        add("sky_n", -50, 175, -236, 20, 10, 40);
        add("sky_w", -236, 160, -35, 40, 10, 10);
        add("sky_top", 0, 199, 18, 0, 0, 17);
        // the start of the run
        add("roost_start", -169.5, 40.0, -129.5, -40, 30, -10);
        // street level, one per district
        add("square", 0, 1.0, 30, 0, 50, -46);
        add("market", 44, 1.0, 30, 0, 10, 0);
        add("bellwatch", -150, 1.0, -36, -150, 20, -62);
        add("quay", -58, 1.0, -44, -64, 14, 4);
        add("terrace", 0, 9.0, -104, 0, 30, -150);
        add("works", -36, 1.0, 102, -60, 6, 118);
        add("garden", 128, 1.0, 2, 200, 8, 0);
        add("wallring", -150, 1.0, 168, -180, 30, 140);
        add("wall_walk", -222, 40.0, -92, -170, 50, -170);
        // studio: the landmarks, framed the same at every milestone
        add("studio_spire", 18, 1.0, -18, 0, 100, -46);
        add("studio_association", 0, 9.0, -96, 0, 26, -148);
        add("studio_roost", -150, 1.0, -112, -190, 52, -146);
        add("studio_high_gate", 0, 6, -292, 0, 30, -238);
        add("studio_dawn_gate", 290, 6, 0, 237, 30, 0);
        add("studio_bell_bridge", -58, 1.0, -34, -72, 0, -10);
        add("studio_cranes", -72, 2, 30, -60, 16, -10);
        add("studio_mills", -40, 1.0, 104, -56, 0, 118);
        add("studio_bellwatch_tower", -134, 1.0, -46, -150, 30, -62);
        add("studio_garden_tower", 168, 1.0, 56, 152, 30, 40);
        add("studio_terrace_tower", 88, 9.0, -112, 72, 34, -128);
    }
}
