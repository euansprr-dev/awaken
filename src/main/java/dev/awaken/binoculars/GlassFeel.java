package dev.awaken.binoculars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The binoculars' feel (SPEC §39.3), from {@code assets/awaken/binoculars/feel.json} (schemaVersion 1): times, the zoom
 * curve, the steps, the sway. A missing field keeps its built-in number; a broken file keeps all of them, with one log line.
 *
 * @param raiseMs      the hands bring them to the eyes (halved with reduced motion, as is {@code lowerMs})
 * @param eyecup       the raise's progress where the eyecups meet the eyes: the zoom starts, the lens edge fades in
 *                     until {@code eyecupEnd}
 * @param pullMs       the first pull to the step after the raise settles in this long
 * @param settleMs     a step settles in this long, overshooting by {@code overshoot} of itself
 * @param focusRaiseMs the focus pull's softness clears in this long after the raise; {@code focusStepMs} after a step to
 *                     {@code focusFromStep} or more
 * @param sway         the breathing sway's size in degrees at 1× (times the square root of the step); {@code swayCrouch}
 *                     of it crouched
 * @param tapMs        a key press shorter than this is a tap (toggle), longer a hold (peek)
 * @param firstStep    the step the first raise ever goes to (later raises remember the last)
 * @param walk         how fast you walk with them up (share of normal; the spyglass's is 0.2)
 * @param plateReach   how far a held look reads a creature's plate, in blocks; after {@code plateDwellMs} on it
 */
public record GlassFeel(float raiseMs, float lowerMs, float eyecup, float eyecupEnd, float pullMs, float settleMs, float overshoot,
                        float focusRaiseMs, float focusStepMs, float focusFromStep, float sway, float swayCrouch, long tapMs,
                        float firstStep, float[] steps, float walk, float plateReach, long plateDwellMs) {
    public static final String FILE = "assets/awaken/binoculars/feel.json";
    public static final int SCHEMA = 1;

    public static final GlassFeel DEFAULTS = new GlassFeel(330F, 220F, 0.82F, 0.85F, 300F, 120F, 0.02F, 250F, 120F, 6F, 0.04F,
            1F / 3F, 250L, 4F, new float[]{2F, 3F, 4F, 6F, 8F, 12F}, 0.2F, 160F, 500L);

    private static volatile GlassFeel current;

    /** The feel in use (read once from the jar). */
    public static GlassFeel get() {
        GlassFeel f = current;
        if (f == null) current = f = load();
        return f;
    }

    private static GlassFeel load() {
        try (InputStream in = GlassFeel.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in == null) {
                Awaken.LOG.warn("Binoculars: {} is missing; using the built-in feel", FILE);
                return DEFAULTS;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Parsed p = parse(text);
            if (!p.ok()) Awaken.LOG.warn("Binoculars: {} could not be read ({}); using the built-in feel", FILE, p.error());
            return p.feel();
        } catch (java.io.IOException | RuntimeException e) {
            Awaken.LOG.warn("Binoculars: {} could not be read; using the built-in feel", FILE, e);
            return DEFAULTS;
        }
    }

    public record Parsed(GlassFeel feel, boolean ok, String error) {}

    /** Reads a feel file's text: any problem gives the defaults and says what it was. */
    public static Parsed parse(String text) {
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            JsonElement schema = o.get("schemaVersion");
            if (schema == null || schema.getAsInt() != SCHEMA) return new Parsed(DEFAULTS, false, "schemaVersion is not " + SCHEMA);
            GlassFeel d = DEFAULTS;
            float[] steps = d.steps();
            if (o.has("steps")) {
                JsonArray a = o.getAsJsonArray("steps");
                steps = new float[a.size()];
                for (int i = 0; i < steps.length; i++) steps[i] = a.get(i).getAsFloat();
                if (!validSteps(steps)) return new Parsed(DEFAULTS, false, "steps must rise from above 1 to at most 16, one to eight of them");
            }
            GlassFeel f = new GlassFeel(num(o, "raiseMs", d.raiseMs(), 60, 2000), num(o, "lowerMs", d.lowerMs(), 40, 2000),
                    num(o, "eyecup", d.eyecup(), 0.3F, 1F), num(o, "eyecupEnd", d.eyecupEnd(), 0.3F, 1F), num(o, "pullMs", d.pullMs(), 20, 3000),
                    num(o, "settleMs", d.settleMs(), 20, 2000), num(o, "overshoot", d.overshoot(), 0F, 0.2F),
                    num(o, "focusRaiseMs", d.focusRaiseMs(), 0, 2000), num(o, "focusStepMs", d.focusStepMs(), 0, 2000),
                    num(o, "focusFromStep", d.focusFromStep(), 1, 16), num(o, "sway", d.sway(), 0F, 0.5F), num(o, "swayCrouch", d.swayCrouch(), 0F, 1F),
                    (long) num(o, "tapMs", d.tapMs(), 80, 1000), num(o, "firstStep", d.firstStep(), 1, 16), steps,
                    num(o, "walk", d.walk(), 0.05F, 1F), num(o, "plateReach", d.plateReach(), 16, 256), (long) num(o, "plateDwellMs", d.plateDwellMs(), 0, 3000));
            if (f.eyecupEnd() < f.eyecup()) return new Parsed(DEFAULTS, false, "eyecupEnd comes before eyecup");
            return new Parsed(f, true, "");
        } catch (RuntimeException e) {
            return new Parsed(DEFAULTS, false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static boolean validSteps(float[] s) {
        if (s.length < 1 || s.length > 8) return false;
        float last = 1F;
        for (float v : s) {
            if (!(v > last) || v > 16F) return false;
            last = v;
        }
        return true;
    }

    /** A number, or the default when absent; out of its range is an error (a typo, not a choice). */
    private static float num(JsonObject o, String key, float def, float min, float max) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return def;
        float v = e.getAsFloat();
        if (!(v >= min && v <= max)) throw new IllegalArgumentException(key + " " + v + " is outside " + min + ".." + max);
        return v;
    }

    /** The index of the step nearest {@code mag} (the remembered step after a feel change). */
    public int stepIndex(float mag) {
        int best = 0;
        for (int i = 1; i < steps.length; i++) if (Math.abs(steps[i] - mag) < Math.abs(steps[best] - mag)) best = i;
        return best;
    }

    /** The step {@code notches} away from index {@code i}, held at the ends. */
    public int stepBy(int i, int notches) {
        return Math.max(0, Math.min(steps.length - 1, i + notches));
    }
}
