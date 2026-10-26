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
    static final java.util.Set<String> ALONG = java.util.Set.of("market", "bellwatch", "works", "wallring", "garden", "market_street");

    /** Views taken exactly where they say (indoors, underground): never moved onto open ground. */
    /** Views whose heights are over the ground at the eye (past the basin, where the land lies as it lies). */
    static final java.util.Set<String> GROUND = java.util.Set.of("commons_road_out", "commons_poles", "commons_farm", "commons_trail",
            "commons_camp_end", "commons_night");

    static final java.util.Set<String> EXACT = java.util.Set.of("cellar", "quay_ladder", "walls_out_w", "walls_out_nw", "walls_out_e",
            "walls_out_s", "walls_inner", "walls_inner_high", "walls_walk", "walls_walk_roofed", "walls_climb", "walls_tower",
            "walls_passage", "walls_watergate", "walls_scaffold", "walls_approach", "walls_works_far");

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
        // the 3D ladder: one up the river's lower quay wall, from the lower quay
        add("quay_ladder", -60.5, -3.0, 11.5, -57.5, -1.5, 15.5);
        // M4 B3a: the street life. A Market street of displays under the awnings, signs on brackets and a lantern line;
        // a Bellwatch lane of laundry looking up its lines; the bowl's stalls from the -1 ring; a quay's lantern strings
        add("market_street", 44, 1.0, -62, 0, 6, 0);
        // (B3b) down the middle of the Bellwatch lane west of the canal that carries twelve lines, looking up them
        add("laundry_lane", -168.5, 1.0, -10.5, -205, 11, -10.5);
        add("bowl_stalls", -4, 2.0, 27, 8, -1, 12);
        add("canal_lights", -124, 2.0, 23, -84, 6, 25);
        // the Walls step: the curtain from the fields (Bellwatch's roofed walk, the Roost's stretch, the Garden's, the
        // Works'), the inner face from the ring, from over the roofs and up a climb line, the walk (open and roofed), a
        // Bellwatch drum close up, the High Gate's passage looking out, and the north water gate from the river bank
        add("walls_out_w", -285.9, 7, 34.0, -242.9, 26, -4.0);
        add("walls_out_nw", -293.3, 10, -64.1, -232.1, 30, -72.5);
        add("walls_out_e", 236.4, 8, 166.5, 213.4, 26, 116.3);
        add("walls_out_s", 97.6, 6, 263.7, 57.1, 30, 236.2);
        // the Commons step: the walk out of the High Gate (the road at eye height, a pennant line, the Gatefield farm),
        // the fields from above, the road past the basin towards the Wayfarers' smoke, the road's end at the fingerpost,
        // and a spot in the fields for the night's count of monsters
        add("commons_road_out", 0.5, 1.6, -261.5, 0.5, 3.0, -330);
        add("commons_poles", -2.5, 1.6, -268.5, 0.5, 6.0, -277.5);
        add("commons_farm", -3.5, 5.0, -296.5, 22, 2.0, -272);
        add("commons_fields", -60, 30, -330, 30, 0, -262);
        add("commons_trail", 0.5, 1.6, -345.5, 0.5, 2.5, -440);
        add("commons_camp_end", -2.5, 1.8, -412.5, 4.5, 1.5, -446);
        add("commons_night", 30.5, 5.0, -305.5, 0, 2, -290);
        add("walls_works_far", 165.0, 14, 300.0, 78.0, 22, 222.0); // the Works' slanted sides from the fields
        add("walls_inner", 225.8, 1.0, 43.9, 223.9, 14, 68.3);
        add("walls_inner_high", -50.1, 20, 210.1, -66.6, 20, 225.4);
        add("walls_walk", 181.7, 40.0, 154.8, 161.2, 41, 175.3);
        add("walls_walk_roofed", -225.9, 40.0, 80.2, -233.4, 41, 52.2);
        add("walls_climb", 219.0, 2.0, 55.6, 224.6, 22, 63.8);
        add("walls_tower", -262.7, 10, 34.6, -237.9, 48, 31.3);
        add("walls_passage", 0.5, 1.0, -235.5, 0.5, 5, -262);
        add("walls_watergate", -60, 14, -262, -88, 8, -228);
        add("walls_scaffold", -224.6, 7, 138.9, -212.9, 18, 117.1);
        add("walls_approach", 9, 4, -318, 0, 22, -244);
    }
}
