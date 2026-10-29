package dev.awaken.world.stilled;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the Stilled sit (SPEC §43.3), pure from the seed and the world's centre. Three on the north road in the first
 * 400 blocks past the ridge (Aldo on the milestone just past it, carrying his note to Ilse; a woman further on with a
 * keepsake; a man with a key), one at the first hamlet's near edge (when the hamlet is as close as the third, the third
 * is that one: he nearly made it, with the hamlet's key in his hand), then one a cell: 1,000-block cells within 3,000
 * of the centre (two cells in three), 2,000-block cells to 6,000, 3,000-block cells past that. A spot is only a point to
 * start from: the placer moves it beside the road, out to a settlement's edge, or drops it.
 */
final class StilledSpots {
    private StilledSpots() {}

    /** Past the ridge along the north road: Aldo, then two more (all within the first 400). */
    static final int[] NORTH = {12, 170, 330};
    /** The first hamlet's anchor this near the third's road spot: the third sits at the hamlet's edge instead. */
    static final int JOIN_HAMLET = 300;
    /** Never two within this of each other. */
    static final int APART = 150;
    static final int NEAR_BAND = 3000, MID_BAND = 6000;

    /** How a spot finds its ground: beside the road nearest it, at a settlement's edge on the city's side, or a cell's way. */
    enum Where { ROAD, HOME_EDGE, CELL }

    /** A spot: its id, where to start looking and how, and who sits there (with what; {@code n} -1: rolled at placement). */
    record Spot(String id, int x, int z, Where where, StilledText.Pose pose, StilledText.Look look, String carry, int n, String name, long seed) {}

    /** The north road's three and the first hamlet's one ({@code hamlet}: its anchor, null while unknown). */
    static List<Spot> fixed(int cx, int cz, int ridge, int @org.jspecify.annotations.Nullable [] hamlet, long seed) {
        List<Spot> out = new ArrayList<>(4);
        int x = cx + 6;
        out.add(new Spot("aldo", x, cz - ridge - NORTH[0], Where.ROAD, StilledText.Pose.MILESTONE, StilledText.Look.OLD_MAN, "letter",
                StilledText.ALDO, "Aldo", seed ^ 0xA1D0L));
        out.add(new Spot("north1", x, cz - ridge - NORTH[1], Where.ROAD, StilledText.Pose.WALKING, StilledText.Look.WOMAN, "keepsake", -1, "",
                seed ^ 0x7031L));
        int z3 = cz - ridge - NORTH[2];
        if (hamlet != null && Math.hypot(hamlet[0] - x, hamlet[1] - z3) < JOIN_HAMLET) {
            out.add(new Spot("north2", hamlet[0], hamlet[1], Where.HOME_EDGE, StilledText.Pose.REACHING, StilledText.Look.MAN, "key", -1, "",
                    seed ^ 0x7032L));
            return out;
        }
        out.add(new Spot("north2", x, z3, Where.ROAD, StilledText.Pose.LOOKING_BACK, StilledText.Look.MAN, "key", -1, "", seed ^ 0x7032L));
        if (hamlet != null)
            out.add(new Spot("hamlet", hamlet[0], hamlet[1], Where.HOME_EDGE, StilledText.Pose.REACHING, StilledText.Look.YOUNG, "letter", -1, "",
                    seed ^ 0x4A31L));
        return out;
    }

    /** A cell's size at a distance from the centre (by its middle). */
    static int cellSize(double distance) {
        return distance < NEAR_BAND ? 1000 : distance < MID_BAND ? 2000 : 3000;
    }

    /**
     * The cells' spots whose start lies within {@code reach} of (x, z): for each size, the cells of that size whose middle
     * falls in its band; a cell within 3,000 holds one two times in three, the others always.
     */
    static List<Spot> cells(int cx, int cz, int ridge, long seed, int x, int z, int reach) {
        List<Spot> out = new ArrayList<>();
        for (int size : new int[]{1000, 2000, 3000}) {
            for (int i = Math.floorDiv(x - reach - cx, size); i <= Math.floorDiv(x + reach - cx, size); i++)
                for (int j = Math.floorDiv(z - reach - cz, size); j <= Math.floorDiv(z + reach - cz, size); j++) {
                    double mx = cx + (i + 0.5) * size, mz = cz + (j + 0.5) * size;
                    if (cellSize(Math.hypot(mx - cx, mz - cz)) != size) continue;
                    long h = mix(seed ^ (long) size * 0x9E3779B97F4A7C15L ^ (long) i * 0xC2B2AE3D27D4EB4FL ^ (long) j * 0x165667B19E3779F9L);
                    if (size == 1000 && Math.floorMod(h, 3L) == 0) continue;
                    int sx = (int) (cx + (i + 0.15 + 0.7 * unit(h >>> 3)) * size), sz = (int) (cz + (j + 0.15 + 0.7 * unit(h >>> 23)) * size);
                    if (Math.hypot(sx - cx, sz - cz) < ridge + 40 || Math.hypot(sx - x, sz - z) > reach) continue;
                    out.add(roll("g" + size / 1000 + "_" + i + "_" + j, sx, sz, h));
                }
        }
        return out;
    }

    /** A cell's figure, rolled: a pose (pairs and a parent with a child are rarer), a look to suit it, what they carry. */
    static Spot roll(String id, int x, int z, long h) {
        StilledText.Pose[] common = {StilledText.Pose.WALKING, StilledText.Pose.GRAVE, StilledText.Pose.LEAD, StilledText.Pose.REACHING,
                StilledText.Pose.LOOKING_BACK, StilledText.Pose.WAITING, StilledText.Pose.LANTERN, StilledText.Pose.CURLED, StilledText.Pose.BUNDLE,
                StilledText.Pose.MILESTONE};
        long r = mix(h + 1);
        StilledText.Pose pose = Math.floorMod(r, 10L) == 0 ? StilledText.Pose.PAIR : Math.floorMod(r, 10L) == 1 ? StilledText.Pose.CHILD
                : common[(int) Math.floorMod(r >>> 8, (long) common.length)];
        StilledText.Look[] adults = {StilledText.Look.MAN, StilledText.Look.WOMAN, StilledText.Look.OLD_MAN, StilledText.Look.OLD_WOMAN};
        StilledText.Look look = switch (pose) {
            case PAIR -> StilledText.Look.MAN;
            case CHILD -> Math.floorMod(r >>> 16, 2L) == 0 ? StilledText.Look.WOMAN : StilledText.Look.MAN;
            case WAITING -> Math.floorMod(r >>> 16, 2L) == 0 ? StilledText.Look.YOUNG : adults[(int) Math.floorMod(r >>> 20, 4L)];
            default -> adults[(int) Math.floorMod(r >>> 20, 4L)];
        };
        int c = (int) Math.floorMod(r >>> 28, 20L);
        String carry = c < 8 ? "letter" : c < 15 ? "key" : "keepsake";
        return new Spot(id, x, z, Where.CELL, pose, look, carry, -1, "", r);
    }

    static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }

    static double unit(long h) {
        return (h & ((1L << 24) - 1)) / (double) (1L << 24);
    }
}
