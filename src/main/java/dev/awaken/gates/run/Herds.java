package dev.awaken.gates.run;

import java.util.List;
import java.util.Map;

/**
 * Who stands in a den nobody designed (SPEC §50.9). A land whose run is only read off its sites still shouldn't field
 * the same fight at every camp, so each theme has a few herds and a land goes round them den by den, starting somewhere
 * different for every seed. Under a roof a herd of the cave hunters joins the round: they need the dark, or a roof to
 * hang from. A theme with no herds here rolls from its roster as before.
 */
public final class Herds {
    private Herds() {}

    private static final Map<String, List<List<String>>> OPEN = Map.of(
            "hive", List.of(List.of("drone_bee*3", "hive_guard"), List.of("honey_slime*3", "hive_larva*3"), List.of("hive_larva*6", "drone_bee"), List.of("drone_bee*2", "honey_slime*3")),
            "bell_crypt", List.of(List.of("toll_ghoul*3", "crypt_archer*2"), List.of("crypt_archer*3", "bell_wisp*2"), List.of("bell_wisp*4", "crypt_keeper"), List.of("toll_ghoul*4", "bell_wisp")),
            "overgrown_temple", List.of(List.of("thorn_hound*3", "thorn_archer*2"), List.of("vine_creaker*2", "thorn_archer*2"), List.of("thorn_archer*3", "incense_priest"), List.of("thorn_hound*4", "vine_creaker")));
    private static final Map<String, List<String>> UNDER_A_ROOF = Map.of(
            "hive", List.of("~drip_lurker*3", "honey_slime*2"),
            "bell_crypt", List.of("~hushling*3", "toll_ghoul"),
            "overgrown_temple", List.of("~drip_lurker*2", "thorn_hound*2"));

    /** The herd for a den: empty when the theme has none, and the pack then rolls from the theme's roster. */
    public static List<String> of(String theme, int den, long seed, boolean indoors) {
        List<List<String>> herds = OPEN.get(theme);
        if (herds == null) return List.of();
        int n = herds.size() + (indoors ? 1 : 0), i = Math.floorMod(den + (int) (seed % 97), n);
        return i < herds.size() ? herds.get(i) : UNDER_A_ROOF.get(theme);
    }
}
