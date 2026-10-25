package dev.awaken.core.audio.client.world;

import dev.awaken.core.audio.world.EventClock;
import dev.awaken.core.audio.world.SoundscapeDef.Anchor;
import net.minecraft.util.Mth;

/**
 * Where a world sound comes from, picked from the {@link Listener}'s ring of real columns: a bird in a leaves column,
 * a frog on a water column, the canopy overhead, the open side of a ridge, the horizon. No allocation: results go
 * into the caller's {@code out[3]}.
 */
final class Spots {
    private Spots() {}

    /** A texture's spot. False when the ring has no such place (no canopy overhead, no water near). */
    static boolean texture(Listener at, Anchor anchor, double[] out) {
        switch (anchor) {
            case CANOPY -> {
                double sx = 0, sy = 0, sz = 0;
                int n = 0;
                for (int i = 0; i < Listener.INNER; i++) {
                    if (!at.canopyCol[i]) continue;
                    sx += at.colX[i] + 0.5;
                    sy += at.colTop[i];
                    sz += at.colZ[i] + 0.5;
                    n++;
                }
                if (n == 0) return false;
                // the canopy's centre, pulled halfway to the listener so it surrounds rather than points
                set(out, 0.5 * (sx / n + at.x), Math.max(at.y + 2, sy / n), 0.5 * (sz / n + at.z));
                return true;
            }
            case SKY, FAR -> {
                int open = lowest(at);
                if (open < 0) return false;
                double dx = at.colX[open] + 0.5 - at.x, dz = at.colZ[open] + 0.5 - at.z;
                double len = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz)), r = anchor == Anchor.SKY ? 8 : 30;
                set(out, at.x + dx / len * r, at.y + (anchor == Anchor.SKY ? 6 : 10), at.z + dz / len * r);
                return true;
            }
            default -> {
                int best = -1;
                double bestD = Double.MAX_VALUE;
                int want = kind(anchor);
                for (int i = 0; i < Listener.COLUMNS; i++) {
                    if (at.kind[i] != want) continue;
                    double dx = at.colX[i] + 0.5 - at.x, dz = at.colZ[i] + 0.5 - at.z, d = dx * dx + dz * dz;
                    if (d < bestD) {
                        bestD = d;
                        best = i;
                    }
                }
                if (best < 0) return false;
                set(out, at.colX[best] + 0.5, at.colTop[best] + 0.5, at.colZ[best] + 0.5);
                return true;
            }
        }
    }

    /**
     * An event's spot about {@code dist} blocks away: a random column of the right kind (the nearest ring to that
     * distance), its top a little jittered; FAR and SKY go out along a random bearing. False when there is no such place.
     */
    static boolean event(Listener at, Anchor anchor, float dist, EventClock rnd, double[] out) {
        if (anchor == Anchor.FAR || anchor == Anchor.SKY || anchor == Anchor.CANOPY) {
            if (anchor == Anchor.CANOPY && at.canopy < 0.15F) return false;
            float a = rnd.uniform(0F, Mth.TWO_PI);
            float up = anchor == Anchor.FAR ? rnd.uniform(4F, 14F) : rnd.uniform(6F, 16F);
            set(out, at.x + Mth.cos(a) * dist, at.y + up, at.z + Mth.sin(a) * dist);
            return true;
        }
        int want = kind(anchor);
        int start = (int) rnd.uniform(0F, Listener.COLUMNS - 0.001F);
        int pick = -1;
        float bestFit = Float.MAX_VALUE;
        for (int k = 0; k < Listener.COLUMNS; k++) {
            int i = (start + k) % Listener.COLUMNS;
            if (at.kind[i] != want) continue;
            double dx = at.colX[i] + 0.5 - at.x, dz = at.colZ[i] + 0.5 - at.z;
            float fit = Math.abs((float) Math.sqrt(dx * dx + dz * dz) - dist);
            if (fit < bestFit) {
                bestFit = fit;
                pick = i;
            }
        }
        if (pick < 0) return false;
        double up = anchor == Anchor.LEAVES ? -rnd.uniform(0.5F, 3F) : 0.3;
        set(out, at.colX[pick] + rnd.uniform(0.1F, 0.9F), at.colTop[pick] + up, at.colZ[pick] + rnd.uniform(0.1F, 0.9F));
        return true;
    }

    private static int kind(Anchor a) {
        return switch (a) {
            case LEAVES, CANOPY -> Listener.KIND_LEAVES;
            case WATER -> Listener.KIND_WATER;
            default -> Listener.KIND_GROUND;
        };
    }

    /** The ring column with the lowest top: the open side. */
    private static int lowest(Listener at) {
        int best = -1;
        for (int i = 0; i < Listener.COLUMNS; i++) {
            if (at.kind[i] == Listener.KIND_NONE) continue;
            if (best < 0 || at.colTop[i] < at.colTop[best]) best = i;
        }
        return best;
    }

    private static void set(double[] out, double x, double y, double z) {
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }
}
