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
 * The per-dimension looks in {@code assets/awaken/lens/looks.json}: exact dimension ids, then prefixes ending in
 * {@code *} (the Descent's strata), then {@code default}. A resource pack can retune any of them.
 */
final class Looks {
    private Looks() {}

    static final int SCHEMA_VERSION = 1;
    private static final Map<String, float[]> EXACT = new HashMap<>();
    private static final List<Map.Entry<String, float[]>> PREFIX = new ArrayList<>();
    private static float[] fallback = Look.neutral();

    static void reload(ResourceManager manager) {
        EXACT.clear();
        PREFIX.clear();
        fallback = Look.neutral();
        var resource = manager.getResource(Awaken.id("lens/looks.json"));
        if (resource.isEmpty()) {
            Awaken.LOG.warn("[lens] no lens/looks.json: every dimension keeps the plain frame");
            return;
        }
        try (Reader r = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            if (version != SCHEMA_VERSION) Awaken.LOG.warn("[lens] looks.json schema {} (expected {}): reading it anyway", version, SCHEMA_VERSION);
            JsonObject looks = root.getAsJsonObject("looks");
            if (looks.has("default")) fallback = Look.parse(looks.getAsJsonObject("default"), Look.neutral());
            for (Map.Entry<String, JsonElement> e : looks.entrySet()) {
                if (e.getKey().equals("default")) continue;
                float[] look = Look.parse(e.getValue().getAsJsonObject(), fallback);
                if (e.getKey().endsWith("*")) PREFIX.add(Map.entry(e.getKey().substring(0, e.getKey().length() - 1), look));
                else EXACT.put(e.getKey(), look);
            }
            Awaken.LOG.info("[lens] {} dimension looks and {} prefixes loaded", EXACT.size(), PREFIX.size());
        } catch (RuntimeException | java.io.IOException e) {
            Awaken.LOG.warn("[lens] lens/looks.json unreadable, keeping the plain frame: {}", e.toString());
            EXACT.clear();
            PREFIX.clear();
            fallback = Look.neutral();
        }
    }

    /** Copies the look for this dimension into {@code out}. */
    static void resolve(String dimension, float[] out) {
        float[] look = EXACT.get(dimension);
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
