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
 * <p>
 * Schema 2 (SPEC §32, "The arm"; a file of either schema loads): a fists clip marked {@code "arm"} uses a fist key's three
 * spare values as the forearm's pitch and yaw (degrees in view space: 0, 0 points straight ahead, yaw + to the right, pitch +
 * up) and the wrist's roll, so the forearm can lie across the view, stand up or drive forward; without it the arm rises to the
 * fist from under the frame's corner as before. {@code "mainMoves"} / {@code "offMoves"} make that track offsets (it moves
 * what the hand holds) while the other stays fists: a cast from the off hand with the weapon lowered in the main one.
 * {@code "ease"} beside {@code "stow"} puts the held thing away over a few ticks instead of at once. {@code "light"} keys the
 * arms' light: {@code [tick, chargeMain, heatMain, chargeOff, heatOff, ease]} (FirstPerson.View lightMain/lightOff).
 * <pre>
 * {"schemaVersion": 1, "clips": {"fp_pipe_kick": {"length": 10, "loop": false, "both": true, "fists": false,
 *    "main": [[0, x, y, z, pitch, yaw, roll, "linear"], [3, ..., "easeoutcubic"]], "off": [...], "events": [[2, "push"]]}}}
 * </pre>
 */
public final class FpClipSet {
    private FpClipSet() {}

    public static final int SCHEMA = 2;
    /** The files read, in order (a later file's clip replaces an earlier one's of the same name). */
    public static final String[] FILES = {"assets/awaken/fp_clips/fp_moves.json", "assets/awaken/fp_clips/fp_casts.json",
            "assets/awaken/fp_clips/fp_casts2.json"};

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
        /** A climb's clip (fp_moves.py "climb": a catch, a mantle): the hands grip the world, what they hold is put away. */
        public boolean climb;
        /** Schema 2: the fist keys carry the forearm's pitch, yaw and the wrist's roll (see the class note). */
        public boolean arm;
        /** Schema 2: that track is offsets even in a fists clip (the hand that keeps the weapon while the other casts). */
        public boolean mainMoves, offMoves;
        /** Schema 2: a stow that eases what the hands hold away and back instead of hiding it at once. */
        public boolean ease;
        /** Schema 2: the arms' light, or empty (each key: chargeMain, heatMain, chargeOff, heatOff). */
        Key[] light = new Key[0];
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

        public boolean hasLight() {
            return light.length > 0;
        }

        /** The light at tick t into a 4-wide array: chargeMain, heatMain, chargeOff, heatOff. */
        public void light(float t, float[] out) {
            if (loop && length > 0) t = ((t % length) + length) % length;
            at(light, t, SCRATCH);
            System.arraycopy(SCRATCH, 0, out, 0, 4);
        }

        private static final float[] SCRATCH = new float[6];

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
        if (schema < 1 || schema > SCHEMA) throw new IllegalArgumentException("schemaVersion " + schema + ", expected 1 to " + SCHEMA);
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
            out.get(e.getKey()).climb = c.has("climb") && c.get("climb").getAsBoolean();
            Clip made = out.get(e.getKey());
            made.arm = flag(c, "arm");
            made.mainMoves = flag(c, "mainMoves");
            made.offMoves = flag(c, "offMoves");
            made.ease = flag(c, "ease");
            if (c.has("light")) made.light = lightKeys(c.getAsJsonArray("light"));
        }
        return out;
    }

    private static boolean flag(JsonObject c, String name) {
        return c.has(name) && c.get(name).getAsBoolean();
    }

    /** Light keys: [tick, chargeMain, heatMain, chargeOff, heatOff, ease]. */
    private static Key[] lightKeys(JsonArray a) {
        Key[] out = new Key[a.size()];
        for (int i = 0; i < a.size(); i++) {
            JsonArray k = a.get(i).getAsJsonArray();
            float[] v = new float[6];
            for (int j = 0; j < 4; j++) v[j] = k.get(1 + j).getAsFloat();
            out[i] = new Key(k.get(0).getAsFloat(), v, k.size() > 5 ? Ease.byName(k.get(5).getAsString()) : Ease.LINEAR);
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
