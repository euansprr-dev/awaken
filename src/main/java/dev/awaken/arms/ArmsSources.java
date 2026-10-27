package dev.awaken.arms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the ingredients of Hunter gear and Arms weapons come from (docs/arms-progression/PLAN.md §3.5), read once from
 * {@code data/awaken/arms/sources.json}, which tools/arms/sources.py writes from the recipes and the creature loot
 * tables so it can't drift. A hand-written line for what a creature table can't say lives in the lang files under
 * {@code awaken.source.<namespace>.<path>}; the GameTest {@code arms_sources} fails when an ingredient has neither.
 */
public final class ArmsSources {
    private ArmsSources() {}

    public static final int SCHEMA_VERSION = 1;
    static final String PATH = "/data/awaken/arms/sources.json";

    private static final Set<Identifier> INGREDIENTS = new HashSet<>();
    private static final Map<Identifier, List<Identifier>> DROPS = new HashMap<>();
    private static final Map<Identifier, Map<String, Integer>> BLADES = new LinkedHashMap<>();

    static {
        load();
    }

    private static void load() {
        try (InputStream in = ArmsSources.class.getResourceAsStream(PATH)) {
            if (in == null) throw new IllegalStateException("missing " + PATH);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != SCHEMA_VERSION) throw new IllegalStateException(PATH + ": schemaVersion");
            for (JsonElement e : root.getAsJsonArray("ingredients")) INGREDIENTS.add(Identifier.parse(e.getAsString()));
            for (var e : root.getAsJsonObject("drops").entrySet()) {
                List<Identifier> who = new ArrayList<>();
                for (JsonElement c : (JsonArray) e.getValue()) who.add(Identifier.parse(c.getAsString()));
                DROPS.put(Identifier.parse(e.getKey()), List.copyOf(who));
            }
            for (var e : root.getAsJsonObject("blades").entrySet()) {
                Map<String, Integer> parts = new LinkedHashMap<>();
                for (var p : e.getValue().getAsJsonObject().entrySet()) parts.put(p.getKey(), p.getValue().getAsInt());
                BLADES.put(Identifier.parse(e.getKey()), Map.copyOf(parts));
            }
        } catch (IOException e) {
            throw new IllegalStateException(PATH, e);
        }
    }

    /** Whether the item is an ingredient of some Hunter gear piece or Arms weapon. */
    public static boolean isIngredient(Identifier item) {
        return INGREDIENTS.contains(item);
    }

    /** Every ingredient, for the test. */
    public static Set<Identifier> ingredients() {
        return Set.copyOf(INGREDIENTS);
    }

    /** The creatures whose loot tables carry this item (at most five), by entity id. */
    public static List<Identifier> creatures(Identifier item) {
        return DROPS.getOrDefault(item, List.of());
    }

    /** What a Hunter blade is made from: counts of "part", "binding", "hide", "metal" and "essence", or null for any other item. */
    public static @Nullable Map<String, Integer> blade(Identifier item) {
        return BLADES.get(item);
    }

    /** The lang key of an item's hand-written source line. */
    public static String langKey(Identifier item) {
        return "awaken.source." + item.getNamespace() + "." + item.getPath();
    }
}
