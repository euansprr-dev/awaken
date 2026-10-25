package dev.awaken.atlas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Carillon's bells as the Atlas sees them (SPEC §16.8): each district's bell tower clears its district, the Choir's
 * great bell clears the whole city. Read once from the city's plan ({@code data/awaken/city/plan.json}, plan
 * coordinates from Choir Square); empty when the plan isn't there. A bell counts when it is rung, or when a player
 * stands in its belfry.
 */
public final class Bells {
    private Bells() {}

    /**
     * One bell: the district it clears (null for the whole city), the tower's box in plan coordinates, and the belfry
     * floor's height over the street.
     */
    public record Bell(String name, String district, int x0, int z0, int x1, int z1, int belfry) {
        public boolean wholeCity() {
            return district == null;
        }

        public int cx() {
            return (x0 + x1) / 2;
        }

        public int cz() {
            return (z0 + z1) / 2;
        }

        /** Whether (dx, dz), plan coordinates, is in or right beside the tower. */
        public boolean beside(int dx, int dz, int margin) {
            return dx >= x0 - margin && dx <= x1 + margin && dz >= z0 - margin && dz <= z1 + margin;
        }
    }

    /** The spire's open belfry starts 61 over the street (city.CityLandmarks: stage 3, rows 61–78). */
    static final int SPIRE_BELFRY = 61;
    /** A district tower's belfry is 18 under its top (city.CityLandmarks.bellTower). */
    static final int TOWER_BELFRY_FROM_TOP = 18;
    /** High Terrace stands this far over the street (plan "terraceLift"). */
    static int terraceLift = 12;

    /** A district's name and where its label sits (its bell), plan coordinates; for the Atlas's lettering. */
    public record District(String key, String name, int x, int z) {}

    private static List<Bell> bells;
    private static List<District> districts = List.of();

    public static List<Bell> all() {
        if (bells == null) bells = load();
        return bells;
    }

    public static List<District> districts() {
        all();
        return districts;
    }

    private static List<Bell> load() {
        List<Bell> out = new ArrayList<>();
        List<District> ds = new ArrayList<>();
        try (InputStream in = Bells.class.getResourceAsStream("/data/awaken/city/plan.json")) {
            if (in == null) return out;
            JsonObject plan = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (plan.has("terraceLift")) terraceLift = plan.get("terraceLift").getAsInt();
            for (JsonElement el : plan.getAsJsonArray("districts")) {
                JsonObject d = el.getAsJsonObject();
                JsonArray at = d.getAsJsonArray("bell");
                String key = d.get("key").getAsString();
                if (!key.equals("wallring")) ds.add(new District(key, d.get("name").getAsString(), at.get(0).getAsInt(), at.get(1).getAsInt()));
            }
            for (JsonElement el : plan.getAsJsonArray("landmarks")) {
                JsonObject lm = el.getAsJsonObject();
                String id = lm.get("id").getAsString(), name = lm.get("name").getAsString();
                for (JsonElement pe : lm.getAsJsonArray("parts")) {
                    JsonObject part = pe.getAsJsonObject();
                    String kind = part.get("kind").getAsString();
                    JsonArray b = part.getAsJsonArray("box");
                    int x0 = b.get(0).getAsInt(), z0 = b.get(1).getAsInt(), x1 = b.get(2).getAsInt(), z1 = b.get(3).getAsInt();
                    if (kind.equals("spire")) out.add(new Bell(name, null, x0, z0, x1, z1, SPIRE_BELFRY));
                    else if (kind.equals("belltower") && id.startsWith("bell_")) {
                        String district = id.substring("bell_".length());
                        int lift = district.equals("terrace") ? terraceLift : 0;
                        out.add(new Bell(name, district, x0, z0, x1, z1, lift + part.get("h").getAsInt() - TOWER_BELFRY_FROM_TOP));
                    }
                }
            }
        } catch (Exception e) {
            Awaken.LOG.warn("[atlas] couldn't read the city plan's bells: {}", e.toString());
        }
        districts = List.copyOf(ds);
        return List.copyOf(out);
    }

    /** The bell whose tower is at (dx, dz) from the square, plan coordinates, within {@code margin}; else null. */
    public static Bell at(int dx, int dz, int margin) {
        for (Bell b : all()) if (b.beside(dx, dz, margin)) return b;
        return null;
    }
}
