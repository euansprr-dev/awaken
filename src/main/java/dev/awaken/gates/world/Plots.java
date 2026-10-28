package dev.awaken.gates.world;

/**
 * Content version 6+ (SPEC §38, "Room for height"): every floor gets its own plot in the cell instead of a 32-block band.
 * A cell is four 256 x 256 quadrants, each split into an upper tier (y 128..255) and a lower tier (y 0..127): eight
 * plots for the six main and two annex floors a gate can have. A tall world (drops and climbs) takes a whole quadrant
 * column (y 0..255) when the gate has a quadrant to spare. Pure geometry; the assignment is a function of the plan only,
 * so every thread rebuilds the same plots.
 */
public final class Plots {
    private Plots() {}

    public static final int FIRST_VERSION = 6;
    public static final int SIDE = 256;
    public static final int TIER_H = 128;
    /** Rooms floors (today's rooms inside a plot) stand this far above their plot's bottom. */
    public static final int ROOMS_BASE = 48;

    /** One plot: a quadrant of the cell and a y range (both inclusive). */
    public record Plot(int quadrant, int minY, int maxY) {
        public int qx() {
            return quadrant & 1;
        }

        public int qz() {
            return quadrant >> 1;
        }

        public int x0() {
            return qx() * SIDE;
        }

        public int z0() {
            return qz() * SIDE;
        }

        public boolean tall() {
            return maxY - minY + 1 > TIER_H;
        }

        public int height() {
            return maxY - minY + 1;
        }

        public boolean contains(int cellLocalX, int cellLocalZ, int y) {
            return y >= minY && y <= maxY && cellLocalX >= x0() && cellLocalX < x0() + SIDE && cellLocalZ >= z0() && cellLocalZ < z0() + SIDE;
        }

        /** Slot (chunk) origin of the quadrant, in cell slots. */
        public int slotX() {
            return qx() * (SIDE / 16);
        }

        public int slotZ() {
            return qz() * (SIDE / 16);
        }
    }

    public static Plot upper(int quadrant) {
        return new Plot(quadrant, TIER_H, 2 * TIER_H - 1);
    }

    public static Plot lower(int quadrant) {
        return new Plot(quadrant, 0, TIER_H - 1);
    }

    public static Plot column(int quadrant) {
        return new Plot(quadrant, 0, 2 * TIER_H - 1);
    }

    /**
     * Plots for a gate's floors in order (main floors 1..n, then annex floors). {@code tall[i]} asks floor i for a whole
     * quadrant column; it gets one only while every other floor still fits, earliest floors first, else a tier.
     */
    public static Plot[] assign(boolean[] tall) {
        int n = tall.length;
        if (n > 8) throw new IllegalArgumentException("a cell holds at most 8 floors, asked for " + n);
        Plot[] out = new Plot[n];
        boolean[] usedUpper = new boolean[4], usedLower = new boolean[4];
        int wantTall = 0;
        for (boolean t : tall) if (t) wantTall++;
        // each tall floor costs a second tier: n floors with k tall use n + k of the eight tiers
        int granted = Math.min(wantTall, Math.min(4, 8 - n));
        int q = 0;
        for (int i = 0; i < n && granted > 0; i++) {
            if (!tall[i]) continue;
            out[i] = column(q);
            usedUpper[q] = usedLower[q] = true;
            q++;
            granted--;
        }
        // the rest fill the upper tiers first, then the lower ones, in floor order
        for (int i = 0; i < n; i++) {
            if (out[i] != null) continue;
            Plot p = null;
            for (int k = 0; k < 4 && p == null; k++) if (!usedUpper[k]) {
                usedUpper[k] = true;
                p = upper(k);
            }
            for (int k = 0; k < 4 && p == null; k++) if (!usedLower[k]) {
                usedLower[k] = true;
                p = lower(k);
            }
            out[i] = p;
        }
        return out;
    }
}
