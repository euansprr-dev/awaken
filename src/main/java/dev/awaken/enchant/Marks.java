package dev.awaken.enchant;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Each enchantment's mark (SPEC §45.4): three to five strokes drawn in its family's style (wards closed shapes and rings,
 * banes points and spears, spoils open cups, carrying waves, keeping knots), varied by its number so no two match.
 * Common, so the server knows how many strokes a trace has; the client draws them on the piece and in the book. A stroke
 * is a polyline in the mark's own square (x along the piece, y across it, both -1..1), in the order the hand draws it.
 */
public final class Marks {
    private Marks() {}

    /** Points along each stroke: enough for a smooth line and a fair trace. */
    static final int SAMPLES = 28;

    private static final float[][][] CACHE = new float[Inscriptions.ALL.size() + 1][][];

    /** The mark of the enchantment numbered {@code no} (1-30); a plain ring for anything else. */
    public static float[][] of(int no) {
        if (no < 1 || no >= CACHE.length) return build(0, Family.KEEPING);
        float[][] m = CACHE[no];
        if (m == null) CACHE[no] = m = build(no, Inscriptions.ALL.get(no - 1).family());
        return m;
    }

    public static int strokes(int no) {
        return of(no).length;
    }

    private static float[][] build(int no, Family f) {
        // a small fixed hash: each mark varies its parts by its number, the same on every machine
        long h = (no * 0x9E3779B97F4A7C15L) ^ 0x5DEECE66DL;
        float a = frac(h), b = frac(h >>> 11), c = frac(h >>> 23), d = frac(h >>> 37);
        List<float[]> s = new ArrayList<>();
        switch (f) {
            case WARD -> {
                float r = 0.58F + 0.08F * a, start = Mth.TWO_PI * b;
                s.add(arc(0, 0, r, start, start + Mth.TWO_PI * 0.93F));
                int sides = 3 + (int) (c * 4);
                s.add(polygon(0, 0, r * 0.52F, sides, Mth.PI * d));
                float y = (a - 0.5F) * 0.3F;
                s.add(line(-0.92F, y, 0.92F, -y));
                if (no >= 1 && no <= 5) s.add(arc(0, 0, 0.11F, 0, Mth.TWO_PI)); // a Force's own seed at the heart
            }
            case BANE -> {
                s.add(line(-0.9F, 0, 0.72F, 0));
                float w = 0.26F + 0.1F * a;
                s.add(poly(0.42F, w, 0.96F, 0, 0.42F, -w));
                float x = -0.45F - 0.15F * b;
                s.add(poly(x - 0.22F, 0.34F, x + 0.1F, 0, x - 0.22F, -0.34F));
                if (c > 0.5F) s.add(line(-0.05F, 0.38F, -0.05F, -0.38F));
                else s.add(poly(-0.92F, 0.2F, -0.78F, 0, -0.92F, -0.2F));
            }
            case SPOILS -> {
                float r = 0.5F + 0.1F * a;
                s.add(arc(0, 0.2F, r, Mth.PI * 1.08F, Mth.PI * 1.92F));
                s.add(arc(0, 0.66F, 0.11F + 0.04F * b, Mth.HALF_PI, Mth.HALF_PI + Mth.TWO_PI));
                s.add(line(-r - 0.12F, 0.12F, -r - 0.3F, 0.42F));
                s.add(line(r + 0.12F, 0.12F, r + 0.3F, 0.42F));
                if (c > 0.45F) s.add(arc(0, 0.2F, r * 0.55F, Mth.PI * 1.15F, Mth.PI * 1.85F));
            }
            case CARRYING -> {
                float amp = 0.15F + 0.07F * a, per = 1.2F + 0.8F * b;
                s.add(wave(-0.92F, 0.92F, 0.28F, amp, per, 0));
                s.add(wave(-0.92F, 0.92F, -0.22F, amp, per, Mth.PI * (0.5F + c)));
                s.add(poly(0.5F, 0.62F, 0.86F, 0.62F, 0.7F, 0.86F));
                if (d > 0.5F) s.add(wave(-0.6F, 0.6F, -0.66F, amp * 0.6F, per * 0.8F, 0));
            }
            case KEEPING -> {
                float wx = 0.72F + 0.12F * a, wy = 0.4F + 0.12F * b;
                s.add(eight(wx, wy));
                s.add(arc(0, 0, 0.18F + 0.06F * c, Mth.PI * d, Mth.PI * d + Mth.TWO_PI));
                s.add(line(-0.95F, -0.56F, -0.62F, -0.22F));
                s.add(line(0.95F, 0.56F, 0.62F, 0.22F));
            }
        }
        return s.toArray(new float[0][]);
    }

    private static float frac(long h) {
        return ((h ^ (h >>> 29)) & 0xFFFF) / 65535F;
    }

    private static float[] arc(float cx, float cy, float r, float a0, float a1) {
        float[] p = new float[SAMPLES * 2];
        for (int i = 0; i < SAMPLES; i++) {
            float t = a0 + (a1 - a0) * i / (SAMPLES - 1);
            p[i * 2] = cx + Mth.cos(t) * r;
            p[i * 2 + 1] = cy + Mth.sin(t) * r;
        }
        return p;
    }

    private static float[] line(float x0, float y0, float x1, float y1) {
        float[] p = new float[SAMPLES * 2];
        for (int i = 0; i < SAMPLES; i++) {
            float t = i / (float) (SAMPLES - 1);
            p[i * 2] = Mth.lerp(t, x0, x1);
            p[i * 2 + 1] = Mth.lerp(t, y0, y1);
        }
        return p;
    }

    /** A polyline through the given corners, resampled evenly along its length. */
    private static float[] poly(float... c) {
        int n = c.length / 2;
        float[] len = new float[n];
        for (int i = 1; i < n; i++) len[i] = len[i - 1] + (float) Math.hypot(c[i * 2] - c[i * 2 - 2], c[i * 2 + 1] - c[i * 2 - 1]);
        float total = len[n - 1];
        float[] p = new float[SAMPLES * 2];
        int seg = 1;
        for (int i = 0; i < SAMPLES; i++) {
            float at = total * i / (SAMPLES - 1);
            while (seg < n - 1 && len[seg] < at) seg++;
            float span = Math.max(1e-5F, len[seg] - len[seg - 1]);
            float t = Mth.clamp((at - len[seg - 1]) / span, 0, 1);
            p[i * 2] = Mth.lerp(t, c[seg * 2 - 2], c[seg * 2]);
            p[i * 2 + 1] = Mth.lerp(t, c[seg * 2 - 1], c[seg * 2 + 1]);
        }
        return p;
    }

    private static float[] polygon(float cx, float cy, float r, int sides, float rot) {
        float[] c = new float[(sides + 1) * 2];
        for (int i = 0; i <= sides; i++) {
            float t = rot + Mth.TWO_PI * i / sides;
            c[i * 2] = cx + Mth.cos(t) * r;
            c[i * 2 + 1] = cy + Mth.sin(t) * r;
        }
        return poly(c);
    }

    private static float[] wave(float x0, float x1, float y, float amp, float periods, float phase) {
        float[] p = new float[SAMPLES * 2];
        for (int i = 0; i < SAMPLES; i++) {
            float t = i / (float) (SAMPLES - 1);
            p[i * 2] = Mth.lerp(t, x0, x1);
            p[i * 2 + 1] = y + amp * Mth.sin(phase + t * periods * Mth.TWO_PI);
        }
        return p;
    }

    /** A figure of eight on its side: the keeping knot. */
    private static float[] eight(float wx, float wy) {
        float[] p = new float[SAMPLES * 2];
        for (int i = 0; i < SAMPLES; i++) {
            float t = Mth.TWO_PI * i / (SAMPLES - 1);
            p[i * 2] = wx * Mth.sin(t);
            p[i * 2 + 1] = wy * Mth.sin(t) * Mth.cos(t) * 2F;
        }
        return p;
    }
}
