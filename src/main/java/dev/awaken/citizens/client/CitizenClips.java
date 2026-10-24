package dev.awaken.citizens.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.citizens.Act;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Citizens' job clips ({@code assets/awaken/citizen_clips.json}, written by tools/art/specs/citizen_clips.py in the
 * power_casts figure terms and baked like the Player Animation Library's bones), played here on the citizen's own model
 * (the library only animates players). Each key holds the channels below in degrees and pixels; NaN means "keep the
 * vanilla pose" (so arm-only clips play over the walk). Read once from the jar; sampled without allocating.
 */
public final class CitizenClips {
    private CitizenClips() {}

    public static final String RESOURCE = "/assets/awaken/citizen_clips.json";
    // channels
    public static final int ROOT_RX = 0, ROOT_RZ = 1, ROOT_PX = 2, ROOT_PY = 3, ROOT_PZ = 4, TORSO_Y = 5, HEAD_X = 6, HEAD_Z = 7,
            RA_X = 8, RA_Y = 9, RA_Z = 10, RA_PX = 11, RA_PZ = 12, LA_X = 13, LA_Y = 14, LA_Z = 15, LA_PX = 16, LA_PZ = 17,
            RL_X = 18, RL_Y = 19, RL_Z = 20, LL_X = 21, LL_Y = 22, LL_Z = 23, CH = 24;

    static final class Clip {
        final float length;
        final boolean loop;
        final float[] t;
        final int[] ease;
        final float[][] v;

        Clip(float length, boolean loop, float[] t, int[] ease, float[][] v) {
            this.length = length;
            this.loop = loop;
            this.t = t;
            this.ease = ease;
            this.v = v;
        }
    }

    private static final Clip[] CLIPS = new Clip[Act.values().length];
    private static boolean loaded;

    static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = CitizenClips.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                Awaken.LOG.warn("[citizens] no {}: citizens keep the vanilla poses", RESOURCE);
                return;
            }
            JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject clips = o.getAsJsonObject("clips");
            int n = 0;
            for (Act a : Act.values()) {
                if (!clips.has(a.clip())) continue;
                CLIPS[a.ordinal()] = parse(clips.getAsJsonObject(a.clip()));
                n++;
            }
            Awaken.LOG.info("[citizens] {} citizen clips read", n);
        } catch (Exception e) {
            Awaken.LOG.error("[citizens] cannot read {}", RESOURCE, e);
        }
    }

    private static Clip parse(JsonObject c) {
        float length = c.get("length").getAsFloat();
        boolean loop = c.has("loop") && c.get("loop").getAsBoolean();
        JsonArray keys = c.getAsJsonArray("keys");
        float[] t = new float[keys.size()];
        int[] ease = new int[keys.size()];
        float[][] v = new float[keys.size()][CH];
        for (int k = 0; k < keys.size(); k++) {
            JsonObject key = keys.get(k).getAsJsonObject();
            t[k] = key.get("t").getAsFloat();
            ease[k] = Ease.of(key.has("e") ? key.get("e").getAsString() : "linear");
            Arrays.fill(v[k], Float.NaN);
            JsonArray vals = key.getAsJsonArray("v");
            for (int ch = 0; ch < Math.min(CH, vals.size()); ch++) {
                JsonElement e = vals.get(ch);
                if (e != null && !e.isJsonNull()) v[k][ch] = e.getAsFloat();
            }
        }
        return new Clip(length, loop, t, ease, v);
    }

    public static boolean has(Act a) {
        load();
        return CLIPS[a.ordinal()] != null;
    }

    public static boolean loops(Act a) {
        load();
        Clip c = CLIPS[a.ordinal()];
        return c != null && c.loop;
    }

    public static float length(Act a) {
        load();
        Clip c = CLIPS[a.ordinal()];
        return c == null ? 0 : c.length;
    }

    /**
     * The clip's channels at {@code time} ticks from its start into {@code out} (NaN where it leaves the vanilla pose);
     * false when the clip has none. One-shot clips hold their last key.
     */
    public static boolean sample(Act a, float time, float[] out) {
        load();
        Clip c = CLIPS[a.ordinal()];
        if (c == null) return false;
        float tt = c.loop && c.length > 0 ? time % c.length : Math.min(time, c.t[c.t.length - 1]);
        int n = c.t.length;
        if (n == 1 || tt <= c.t[0]) {
            System.arraycopy(c.v[0], 0, out, 0, CH);
            return true;
        }
        int k = 1;
        while (k < n && c.t[k] < tt) k++;
        if (k >= n) {
            if (!c.loop) {
                System.arraycopy(c.v[n - 1], 0, out, 0, CH);
                return true;
            }
            // between the last key and the loop's end: ease back to the first key
            float span = c.length - c.t[n - 1];
            float f = span <= 0 ? 1 : (tt - c.t[n - 1]) / span;
            mix(c.v[n - 1], c.v[0], Ease.apply(c.ease[0], f), out);
            return true;
        }
        float span = c.t[k] - c.t[k - 1];
        float f = span <= 0 ? 1 : (tt - c.t[k - 1]) / span;
        mix(c.v[k - 1], c.v[k], Ease.apply(c.ease[k], f), out);
        return true;
    }

    private static void mix(float[] a, float[] b, float f, float[] out) {
        for (int ch = 0; ch < CH; ch++) {
            float x = a[ch], y = b[ch];
            if (Float.isNaN(x)) out[ch] = y;
            else if (Float.isNaN(y)) out[ch] = x;
            else out[ch] = x + (y - x) * f;
        }
    }

    /** The library's easings, by name (power_casts.EASES). */
    static final class Ease {
        static final String[] NAMES = {"linear", "constant", "step", "easeinsine", "easeoutsine", "easeinoutsine", "easeinquad",
                "easeoutquad", "easeinoutquad", "easeincubic", "easeoutcubic", "easeinoutcubic", "easeinquart", "easeoutquart",
                "easeinoutquart", "easeinexpo", "easeoutexpo", "easeinoutexpo", "easeoutback", "easeinback", "easeinoutback", "easeoutcirc"};

        static int of(String s) {
            for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(s)) return i;
            return 0;
        }

        static float apply(int e, float x) {
            x = Math.clamp(x, 0f, 1f);
            double pi = Math.PI;
            return (float) switch (e) {
                case 1, 2 -> x < 1 ? 0 : 1;
                case 3 -> 1 - Math.cos(x * pi / 2);
                case 4 -> Math.sin(x * pi / 2);
                case 5 -> -(Math.cos(pi * x) - 1) / 2;
                case 6 -> x * x;
                case 7 -> 1 - (1 - x) * (1 - x);
                case 8 -> x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
                case 9 -> x * x * x;
                case 10 -> 1 - Math.pow(1 - x, 3);
                case 11 -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
                case 12 -> x * x * x * x;
                case 13 -> 1 - Math.pow(1 - x, 4);
                case 14 -> x < 0.5 ? 8 * x * x * x * x : 1 - Math.pow(-2 * x + 2, 4) / 2;
                case 15 -> x == 0 ? 0 : Math.pow(2, 10 * x - 10);
                case 16 -> x == 1 ? 1 : 1 - Math.pow(2, -10 * x);
                case 17 -> x == 0 ? 0 : x == 1 ? 1 : x < 0.5 ? Math.pow(2, 20 * x - 10) / 2 : (2 - Math.pow(2, -20 * x + 10)) / 2;
                case 18 -> 1 + 2.70158 * Math.pow(x - 1, 3) + 1.70158 * Math.pow(x - 1, 2);
                case 19 -> 2.70158 * x * x * x - 1.70158 * x * x;
                case 20 -> x < 0.5 ? (Math.pow(2 * x, 2) * ((2.5949 + 1) * 2 * x - 2.5949)) / 2
                        : (Math.pow(2 * x - 2, 2) * ((2.5949 + 1) * (x * 2 - 2) + 2.5949) + 2) / 2;
                case 21 -> Math.sqrt(1 - Math.pow(x - 1, 2));
                default -> x;
            };
        }
    }
}
