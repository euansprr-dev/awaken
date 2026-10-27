package dev.awaken.village.road;

import java.util.ArrayList;
import java.util.List;

/**
 * One road as laid (SPEC §28.9): its line every {@link RoadPlanner#STEP} blocks with the surface height, flags and the
 * land's family at each point (VillageStyles: its stone, earth and timber), its
 * class (a highroad between regions, a lane to a settlement off it), what stands at each end, its waymarkers and how far
 * its lanterns reach from either end. Immutable; cut into {@link Leg legs} short enough that each one's structure start
 * reaches all of it.
 */
public record Road(String key, int cls, float[] x, float[] z, int[] y, byte[] flags, byte[] fam, End from, End to, List<Marker> markers,
                   int litFrom, int litTo) {
    /** A highroad between regions (five wide), a lane to a settlement (three). */
    public static final int HIGHROAD = 0, LANE = 1;
    /** What an end meets: a settlement's street, another road, Carillon's road out, a bare crossroads. */
    public static final int ENTRY = 0, JUNCTION = 1, PORT = 2, CROSSROADS = 3;

    /** One end: the place's name, what it meets (ENTRY..), the settlement's key ("" for none), where, the settlement's kind or -1. */
    public record End(String name, int kind, String settlement, int x, int z, int place) {
        public boolean town() {
            return place == dev.awaken.village.plan.VillagePlan.Kind.TOWN.ordinal();
        }
    }

    /** One arm of a waymarker: the side of the post it points out of (0 N, 1 E, 2 S, 3 W), the place and how far. */
    public record Arm(int side, String name, int distance) {}

    /**
     * A waymarker beside the road at point {@code point}: its post's foot (world), its arms, and a cairn of stones by it
     * (the pass where the first town comes into view).
     */
    public record Marker(int point, int x, int y, int z, List<Arm> arms, boolean cairn) {}

    /** A leg: points i0..i1 (inclusive) and the lattice chunk its structure starts in. */
    public record Leg(Road road, int index, int i0, int i1, int startX, int startZ) {}

    public int points() {
        return x.length;
    }

    public float halfWidth() {
        return cls == HIGHROAD ? 2.5f : 1.5f;
    }

    /** The path length in blocks from point a to point b. */
    public double between(int a, int b) {
        double d = 0;
        for (int i = Math.min(a, b); i < Math.max(a, b); i++) d += Math.hypot(x[i + 1] - x[i], z[i + 1] - z[i]);
        return d;
    }

    /** Lanterns stand by point i (near a settlement at either end). */
    public boolean lit(int i) {
        return i <= litFrom || i >= x.length - 1 - litTo;
    }

    /** Chunks on this lattice are where legs start: every eighth chunk each way. */
    public static final int LATTICE = 8;
    /** A leg's points keep within this many blocks of its middle each way (its start reaches 128 less a margin and the beard). */
    static final int HALF = 36;

    /** The lattice chunk nearest a block coordinate (on one axis). */
    public static int lattice(double block) {
        return LATTICE * (int) Math.floor((block - 8 + 64) / 128.0);
    }

    /** The legs, in order. Each overlaps the next by one point (the segment between them is painted by both). */
    public List<Leg> legs() {
        List<Leg> out = new ArrayList<>();
        int n = x.length, i0 = 0, idx = 0;
        while (i0 < n - 1) {
            float minX = x[i0], maxX = x[i0], minZ = z[i0], maxZ = z[i0];
            int j = i0 + 1;
            while (j < n) {
                float nx0 = Math.min(minX, x[j]), nx1 = Math.max(maxX, x[j]), nz0 = Math.min(minZ, z[j]), nz1 = Math.max(maxZ, z[j]);
                if (nx1 - nx0 > 2 * HALF || nz1 - nz0 > 2 * HALF) break;
                minX = nx0;
                maxX = nx1;
                minZ = nz0;
                maxZ = nz1;
                j++;
            }
            int i1 = Math.max(i0 + 1, j - 1);
            double mx = (minX + maxX) / 2.0, mz = (minZ + maxZ) / 2.0;
            out.add(new Leg(this, idx++, i0, i1, lattice(mx), lattice(mz)));
            i0 = i1;
        }
        return out;
    }
}
