package dev.awaken.anims;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First-person view clips: {@code assets/awaken/fp_clips/*.json}, written by {@code tools/art/specs/fp_moves.py} and {@code fp_casts.py} (edit
 * the poses there, never the JSON). Each clip moves the main and the off hand on top of vanilla's own (view space:
 * x right, y up, z toward the viewer, blocks; then pitch, yaw, roll in degrees), keyed in ticks, each key eased in by
 * its curve ({@link Ease}); events fire at their ticks. Authored right-handed: the game mirrors x, yaw and roll for a
 * left main arm. A clip with "fists" keys where each empty hand is instead (x, y, z in view space; vanilla's resting
 * fist is FirstPerson.View.REST_FIST): its arm is drawn rising to it. Pure data: both sides read it (a GameTest holds the
 * names to the file).
 * <pre>
 * {"schemaVersion": 1, "clips": {"fp_pipe_kick": {"length": 10, "loop": false, "both": true, "fists": false,
 *    "main": [[0, x, y, z, pitch, yaw, roll, "linear"], [3, ..., "easeoutcubic"]], "off": [...], "events": [[2, "push"]]}}}
 * </pre>
 */
public final class FpClipSet {
    private FpClipSet() {}

    public static final int SCHEMA = 1;
    /** The files read, in order (a later file's clip replaces an earlier one's of the same name). */
    public static final String[] FILES = {"assets/awaken/fp_clips/fp_moves.json", "assets/awaken/fp_clips/fp_casts.json"};

    /** One key: its tick, the six values, the curve that eases into it. */
    public record Key(float t, float[] v, int ease) {}

    public static final class Clip {
        public final String name;
        public final float length;
        public final boolean loop, both;
        /** The tracks are fists (x, y, z in view space; the rest unused) rather than offsets (FirstPerson.View fists). */
        public final boolean fists;
        /** A clip played over a stance (a hang's ledge jumps): it ends where the stance holds the hands. */
        public boolean toStance;
        /** A cast clip (fp_casts.py): its fists are drawn even when a hand holds something (the weapon is out of view). */
        public boolean stow;
        final Key[] main, off;
        public final float[] eventAt;
        public final String[] events;

        Clip(String name, float length, boolean loop, boolean both, boolean fists, Key[] main, Key[] off, float[] eventAt, String[] events) {
            this.name = name;
            this.length = length;
            this.loop = loop;
            this.both = both;
            this.fists = fists;
            this.main = main;
            this.off = off;
            this.eventAt = eventAt;
            this.events = events;
        }

        public boolean hasMain() {
            return main.length > 0;
        }

        public boolean hasOff() {
            return off.length > 0;
        }

        /** Both hands' moves at tick t (wrapped for a loop, held at the ends otherwise) into two 6-wide arrays. */
        public void sample(float t, float[] mainOut, float[] offOut) {
            if (loop && length > 0) t = ((t % length) + length) % length;
            at(main, t, mainOut);
            at(off, t, offOut);
        }

        private static void at(Key[] keys, float t, float[] out) {
            if (keys.length == 0) {
                java.util.Arrays.fill(out, 0F);
                return;
            }
            if (t <= keys[0].t) {
                System.arraycopy(keys[0].v, 0, out, 0, 6);
                return;
            }
            for (int i = 1; i < keys.length; i++) {
                Key b = keys[i];
                if (t > b.t) continue;
                Key a = keys[i - 1];
                float u = b.t > a.t ? Ease.at(b.ease, (t - a.t) / (b.t - a.t)) : 1;
                for (int j = 0; j < 6; j++) out[j] = a.v[j] + (b.v[j] - a.v[j]) * u;
                return;
            }
            System.arraycopy(keys[keys.length - 1].v, 0, out, 0, 6);
        }
    }

    /** Every clip in the files (classpath); a file that's missing or wrong is skipped with what went wrong in errors. */
    public static Map<String, Clip> load(StringBuilder errors) {
        Map<String, Clip> out = new LinkedHashMap<>();
        for (String f : FILES) {
            try (InputStream in = FpClipSet.class.getClassLoader().getResourceAsStream(f)) {
                if (in == null) {
                    errors.append(f).append(": missing; ");
                    continue;
                }
                out.putAll(parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                errors.append(f).append(": ").append(ex.getMessage()).append("; ");
            }
        }
        return out;
    }

    public static Map<String, Clip> parse(Reader r) {
        JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
        int schema = root.get("schemaVersion").getAsInt();
        if (schema != SCHEMA) throw new IllegalArgumentException("schemaVersion " + schema + ", expected " + SCHEMA);
        Map<String, Clip> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("clips").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            JsonArray ev = c.has("events") ? c.getAsJsonArray("events") : new JsonArray();
            float[] at = new float[ev.size()];
            String[] names = new String[ev.size()];
            for (int i = 0; i < ev.size(); i++) {
                JsonArray a = ev.get(i).getAsJsonArray();
                at[i] = a.get(0).getAsFloat();
                names[i] = a.get(1).getAsString();
            }
            out.put(e.getKey(), new Clip(e.getKey(), c.get("length").getAsFloat(), c.has("loop") && c.get("loop").getAsBoolean(),
                    c.has("both") && c.get("both").getAsBoolean(), c.has("fists") && c.get("fists").getAsBoolean(), keys(c, "main"), keys(c, "off"), at,
                    names));
            out.get(e.getKey()).toStance = c.has("toStance") && c.get("toStance").getAsBoolean();
            out.get(e.getKey()).stow = c.has("stow") && c.get("stow").getAsBoolean();
        }
        return out;
    }

    private static Key[] keys(JsonObject c, String track) {
        if (!c.has(track)) return new Key[0];
        JsonArray a = c.getAsJsonArray(track);
        Key[] out = new Key[a.size()];
        for (int i = 0; i < a.size(); i++) {
            JsonArray k = a.get(i).getAsJsonArray();
            float[] v = new float[6];
            for (int j = 0; j < 6; j++) v[j] = k.get(1 + j).getAsFloat();
            out[i] = new Key(k.get(0).getAsFloat(), v, k.size() > 7 ? Ease.byName(k.get(7).getAsString()) : Ease.LINEAR);
        }
        return out;
    }
}
