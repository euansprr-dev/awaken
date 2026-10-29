package dev.awaken.arms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each style comes from (docs/arms-progression/PLAN.md §3.4), read once from the mod's own
 * {@code data/awaken/arms/style_homes.json} on both sides: the gate that teaches it, the rank Hale's rack stocks it at
 * (the rank of its master Lord), the person who teaches it early or the left chest it is found in early (SPEC §43.5),
 * and the prices and level bands by rank.
 */
public final class ArmsHomes {
    private ArmsHomes() {}

    public static final int SCHEMA_VERSION = 1;
    static final String PATH = "/data/awaken/arms/style_homes.json";

    /** A person who teaches a style as the last step of a job. */
    public record Teacher(String person, String chain, String line) {}

    /** One style's homes: its gate theme (an id and a name), its rank, its master Lord, an early teacher if any, and whether a left chest holds it early. */
    public record Home(String style, String theme, String gate, Rank rank, String lord, @Nullable Teacher teacher, boolean leftChest) {}

    private static final Map<String, Home> BY_STYLE = new LinkedHashMap<>();
    private static final Map<String, Home> BY_THEME = new LinkedHashMap<>();
    private static final Map<Rank, Integer> RACK = new EnumMap<>(Rank.class);
    private static final Map<Rank, Integer> VILLAGE = new EnumMap<>(Rank.class);
    private static final Map<Rank, int[]> BANDS = new EnumMap<>(Rank.class);
    private static final Map<Rank, Float> SPOILS = new EnumMap<>(Rank.class);
    private static int spoilsPity = 6, markedPity = 25;
    private static float guardian = 0.30F, sovereign = 0.40F, beast = 0.15F, elite = 0.04F, warden = 0.10F, campHeart = 0.10F;

    static {
        load();
    }

    private static void load() {
        try (InputStream in = ArmsHomes.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("missing " + PATH);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) throw new IllegalStateException(PATH + ": schemaVersion");
            for (var e : root.getAsJsonObject("rackPrice").entrySet()) RACK.put(rank(e.getKey()), e.getValue().getAsInt());
            for (var e : root.getAsJsonObject("villagePrice").entrySet()) VILLAGE.put(rank(e.getKey()), e.getValue().getAsInt());
            for (var e : root.getAsJsonObject("bands").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                BANDS.put(rank(e.getKey()), new int[] {a.get(0).getAsInt(), a.get(1).getAsInt()});
            }
            JsonObject sp = root.getAsJsonObject("spoils");
            for (var e : sp.getAsJsonObject("chance").entrySet()) SPOILS.put(rank(e.getKey()), e.getValue().getAsFloat());
            spoilsPity = sp.get("pity").getAsInt();
            guardian = sp.get("guardian").getAsFloat();
            sovereign = sp.get("sovereign").getAsFloat();
            beast = sp.get("beast").getAsFloat();
            JsonObject mk = root.getAsJsonObject("marked");
            elite = mk.get("elite").getAsFloat();
            warden = mk.get("warden").getAsFloat();
            campHeart = mk.get("campHeart").getAsFloat();
            markedPity = mk.get("pity").getAsInt();
            for (JsonElement el : root.getAsJsonArray("styles")) {
                JsonObject o = el.getAsJsonObject();
                Teacher t = null;
                if (o.has("teacher")) {
                    JsonObject to = o.getAsJsonObject("teacher");
                    t = new Teacher(to.get("person").getAsString(), to.get("chain").getAsString(), to.get("line").getAsString());
                }
                Home h = new Home(o.get("id").getAsString(), o.get("theme").getAsString(), o.get("gate").getAsString(),
                        rank(o.get("rank").getAsString()), o.get("lord").getAsString(), t,
                        o.has("found") && o.get("found").getAsString().equals("left_chest"));
                BY_STYLE.put(h.style(), h);
                BY_THEME.put(h.theme(), h);
            }
        } catch (IOException e) {
            throw new IllegalStateException(PATH, e);
        }
    }

    private static Rank rank(String label) {
        for (Rank r : Rank.values()) if (r.label.equals(label)) return r;
        throw new IllegalStateException(PATH + ": unknown rank " + label);
    }

    /** The home of a style (null for the Longsword, which everyone has). */
    public static @Nullable Home of(String style) {
        return BY_STYLE.get(style);
    }

    /** The style a gate theme (its id) teaches, if it teaches one. */
    public static @Nullable Home ofTheme(String themeId) {
        return BY_THEME.get(themeId);
    }

    public static List<Home> all() {
        return List.copyOf(BY_STYLE.values());
    }

    /** The styles whose gates and masters sit at this rank, in file order. */
    public static List<Home> atRank(Rank rank) {
        List<Home> out = new ArrayList<>();
        for (Home h : BY_STYLE.values()) if (h.rank() == rank) out.add(h);
        return out;
    }

    /** What Hale's rack asks for a pattern of this rank, in essence. */
    public static int rackPrice(Rank rank) {
        return RACK.getOrDefault(rank, RACK.get(Rank.S));
    }

    /** The chance a Gate Lord of this rank drops Spoils (a Marked weapon of its style), per fighter. */
    public static float spoilChance(Rank rank) {
        return SPOILS.getOrDefault(rank, SPOILS.get(Rank.S));
    }

    /** Certain on this Lord kill without any weapon (the sixth). */
    public static int spoilsPity() {
        return spoilsPity;
    }

    public static float guardianChance() {
        return guardian;
    }

    public static float sovereignChance() {
        return sovereign;
    }

    public static float beastChance() {
        return beast;
    }

    public static float markedElite() {
        return elite;
    }

    public static float markedWarden() {
        return warden;
    }

    public static float markedCampHeart() {
        return campHeart;
    }

    /** Certain on this elite, Warden or camp-heart kill without a Marked weapon (the 25th). */
    public static int markedPity() {
        return markedPity;
    }

    /** What a village smith asks for a pattern of this rank, in emeralds. */
    public static int villagePrice(Rank rank) {
        return VILLAGE.getOrDefault(rank, VILLAGE.get(Rank.S));
    }

    /** The level band of a rank's structures and villages, "1-9" or "100+" (the plate bands, docs/regions/PLAN.md). */
    public static String band(Rank rank) {
        int[] b = BANDS.get(rank);
        if (b == null) return "";
        return b[1] >= 999 ? b[0] + "+" : b[0] + "-" + b[1];
    }

    /** The rank a level sits in (E for anything below D's band). */
    public static Rank rankOfLevel(int level) {
        Rank best = Rank.E;
        for (var e : BANDS.entrySet()) if (level >= e.getValue()[0] && e.getKey().ordinal() > best.ordinal()) best = e.getKey();
        return best;
    }
}
