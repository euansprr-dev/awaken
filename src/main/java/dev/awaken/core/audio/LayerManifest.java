package dev.awaken.core.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * A layered music context as the game reads it: {@code assets/<ns>/music_layers/<path>.json} for the context id
 * {@code <ns>:<path>} (gate themes are {@code awaken:gate/<theme>}, written by tools/art/specs/audio_layers.py). Any
 * module or resource pack can add one (the city's districts, the Descent): a context is data, the player is generic.
 *
 * <pre>
 * { "schemaVersion": 1, "source": "music" | "ambient", "title": "...", "sampleRate": 44100,
 *   "frames": 2352000,                                   every stem's exact length (they loop together)
 *   "stems":  [ {"name": "bed", "file": "awaken:layers/layer_bell_crypt_bed", "attack": 6, "duck": 2, "release": 10}, ...],
 *   "levels": [ {"name": "calm", "gains": [1, 0, 0, 0]}, ... ]   one gain per stem, for each intensity level
 * }
 * </pre>
 * attack / duck / release: seconds for a stem's gain to travel 0 to 1 when it rises, when it falls because the music
 * got more intense, and when it falls because things calmed down. Missing levels repeat the last one.
 */
public record LayerManifest(Identifier id, boolean music, String title, int sampleRate, long frames, List<Stem> stems,
                            float[][] gains) {
    public static final int SCHEMA = 1, MAX_STEMS = 8;

    public record Stem(String name, Identifier file, float attack, float duck, float release) {}

    /** Where the manifest of a context lives. */
    public static Identifier path(Identifier context) {
        return Identifier.fromNamespaceAndPath(context.getNamespace(), "music_layers/" + context.getPath() + ".json");
    }

    /** The gain of every stem at an intensity level (levels past the last defined repeat it). */
    public float[] gainsAt(int level) {
        return gains[Math.max(0, Math.min(gains.length - 1, level))];
    }

    /** Parses a manifest; throws IllegalArgumentException with the reason when it is malformed. */
    public static LayerManifest parse(Identifier id, JsonObject o) {
        int schema = o.has("schemaVersion") ? o.get("schemaVersion").getAsInt() : 0;
        if (schema != SCHEMA) throw new IllegalArgumentException(id + ": schemaVersion " + schema + " (expected " + SCHEMA + ")");
        String source = o.has("source") ? o.get("source").getAsString() : "music";
        if (!source.equals("music") && !source.equals("ambient")) throw new IllegalArgumentException(id + ": source " + source);
        int rate = o.has("sampleRate") ? o.get("sampleRate").getAsInt() : 44100;
        long frames = o.get("frames").getAsLong();
        if (frames <= 0) throw new IllegalArgumentException(id + ": frames " + frames);
        List<Stem> stems = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("stems")) {
            JsonObject s = e.getAsJsonObject();
            stems.add(new Stem(s.get("name").getAsString(), Identifier.parse(s.get("file").getAsString()),
                    f(s, "attack", 2F), f(s, "duck", 2F), f(s, "release", 6F)));
        }
        if (stems.isEmpty() || stems.size() > MAX_STEMS) throw new IllegalArgumentException(id + ": " + stems.size() + " stems");
        JsonArray lv = o.getAsJsonArray("levels");
        float[][] gains = new float[Intensity.NAMES.length][];
        for (int l = 0; l < gains.length; l++) {
            JsonArray g = lv.get(Math.min(l, lv.size() - 1)).getAsJsonObject().getAsJsonArray("gains");
            if (g.size() != stems.size()) throw new IllegalArgumentException(id + ": level " + l + " has " + g.size() + " gains");
            gains[l] = new float[stems.size()];
            for (int i = 0; i < stems.size(); i++) gains[l][i] = Math.max(0F, Math.min(1F, g.get(i).getAsFloat()));
        }
        String title = o.has("title") ? o.get("title").getAsString() : id.toString();
        return new LayerManifest(id, source.equals("music"), title, rate, frames, List.copyOf(stems), gains);
    }

    private static float f(JsonObject o, String key, float def) {
        return o.has(key) ? Math.max(0.05F, o.get(key).getAsFloat()) : def;
    }
}
