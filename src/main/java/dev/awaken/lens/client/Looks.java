package dev.awaken.lens.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The looks, from every JSON file under {@code assets/<namespace>/lens/} (looks.json, plus files other modules add, such
 * as looks_lands.json), merged by key in path order. Keys: {@code biome:<id>} (wins where the camera's biome matches),
 * exact dimension ids, prefixes ending in {@code *} (the Descent's strata), then {@code default}. A resource pack can
 * retune any of them.
 */
final class Looks {
    private Looks() {}

    static final int SCHEMA_VERSION = 1;
    private static final Map<String, float[]> EXACT = new HashMap<>();
    private static final List<Map.Entry<String, float[]>> PREFIX = new ArrayList<>();
    private static final Map<String, float[]> BIOME = new HashMap<>();
    private static float[] fallback = Look.neutral();

    static void reload(ResourceManager manager) {
        EXACT.clear();
        PREFIX.clear();
        BIOME.clear();
        fallback = Look.neutral();
        // every lens/*.json in every namespace, in path order: a later file's key replaces an earlier one's
        var files = new java.util.TreeMap<>(manager.listResources("lens", id -> id.getPath().endsWith(".json")));
        if (files.isEmpty()) {
            Awaken.LOG.warn("[lens] no lens/*.json: every place keeps the plain frame");
            return;
        }
        Map<String, JsonObject> raw = new java.util.LinkedHashMap<>();
        for (var file : files.entrySet()) {
            try (Reader r = file.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
                if (version != SCHEMA_VERSION) Awaken.LOG.warn("[lens] {} schema {} (expected {}): reading it anyway", file.getKey(), version, SCHEMA_VERSION);
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("looks").entrySet()) raw.put(e.getKey(), e.getValue().getAsJsonObject());
            } catch (RuntimeException | java.io.IOException e) {
                Awaken.LOG.warn("[lens] {} unreadable, skipped: {}", file.getKey(), e.toString());
            }
        }
        try {
            if (raw.containsKey("default")) fallback = Look.parse(raw.get("default"), Look.neutral());
            for (Map.Entry<String, JsonObject> e : raw.entrySet()) {
                String key = e.getKey();
                if (key.equals("default")) continue;
                float[] look = Look.parse(e.getValue(), fallback);
                if (key.startsWith("biome:")) BIOME.put(key.substring("biome:".length()), look);
                else if (key.endsWith("*")) PREFIX.add(Map.entry(key.substring(0, key.length() - 1), look));
                else EXACT.put(key, look);
            }
            Awaken.LOG.info("[lens] {} files: {} dimension looks, {} prefixes, {} biome looks", files.size(), EXACT.size(), PREFIX.size(), BIOME.size());
        } catch (RuntimeException e) {
            Awaken.LOG.warn("[lens] looks unreadable, keeping the plain frame: {}", e.toString());
            EXACT.clear();
            PREFIX.clear();
            BIOME.clear();
            fallback = Look.neutral();
        }
    }

    /** True when a look has this exact key (a dimension's, a story moment's or a place look's id). */
    static boolean has(String key) {
        return key != null && EXACT.containsKey(key);
    }

    /** True when some look is keyed on this biome. */
    static boolean hasBiome(String biome) {
        return biome != null && BIOME.containsKey(biome);
    }

    /** Copies the look for this place into {@code out}: the biome's look if there is one, else the dimension's. */
    static void resolve(String dimension, String biome, float[] out) {
        float[] look = biome == null ? null : BIOME.get(biome);
        if (look != null) {
            System.arraycopy(look, 0, out, 0, Look.SIZE);
            return;
        }
        look = EXACT.get(dimension);
        if (look == null) {
            for (Map.Entry<String, float[]> e : PREFIX) {
                if (dimension.startsWith(e.getKey())) {
                    look = e.getValue();
                    break;
                }
            }
        }
        System.arraycopy(look != null ? look : fallback, 0, out, 0, Look.SIZE);
    }
}
