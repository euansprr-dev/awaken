package dev.awaken.hearth.room;

import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Set;

/**
 * What the scanner saw in one closed room while it walked every cell and wall face (one pass, no extra fills): the
 * floor and how much of it is lit, the walls' materials, windows, fire, detail, seats and tables, the furniture, how
 * high the floor stands over the ground outside, and a sign's words. {@link Reading#of} turns it into the six readings.
 * Mutable while the room is built, then frozen into {@link #facts()} for what other code needs after the scan.
 */
public final class Tally {
    /** Detail kinds: the best of each counts (twenty carpets are one kind of soft thing). Trim first, then soft. */
    public static final int STAIRS = 0, SLAB = 1, TRAPDOOR = 2, POST = 3, CHAIN = 4, LANTERN = 5,
            CARPET = 6, BANNER = 7, PAINTING = 8, POT = 9, CANDLE = 10, BOOKS = 11;
    static final int TRIM_MASK = 0b111111, SOFT_MASK = 0b111111 << 6;

    /** Warmth points (the best fire counts): a campfire, an open fire or a Hearthfire; a lit stove; an unlit stove or candles. */
    public static final int WARM_FIRE = 3, WARM_STOVE = 2, WARM_EMBER = 1;

    public int cells, floor, litFloor;
    long darkX, darkZ;
    int dark;
    public int height, width, depth;
    public int faces, seeThrough, worked;
    final Set<Block> workedKinds = new HashSet<>();
    final int[] family = new int[Materials.FAMILIES];
    public int windows, windowDirs, ceiling, glassCeiling;
    public int warmth, detail, seats, tables;
    public final int[] furniture = new int[Furniture.COUNT];
    /** The lowest floor cell's y, and the ground's height a few blocks outside the room (Integer.MIN_VALUE: unknown). */
    public int floorY = Integer.MAX_VALUE, ground = Integer.MIN_VALUE;
    public String sign = "";
    public final Set<String> trophies = new HashSet<>();
    int minX, minZ, maxX, maxZ;

    void bounds(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    void floorCell(int x, int z, boolean lit) {
        floor++;
        if (lit) {
            litFloor++;
        } else {
            dark++;
            darkX += x;
            darkZ += z;
        }
    }

    void face(int packedMaterial, Block block, boolean seeThrough) {
        faces++;
        family[packedMaterial & 0xFF]++;
        if (Materials.worked(packedMaterial)) {
            worked++;
            if (workedKinds.size() < 8) workedKinds.add(block);
        }
        if (seeThrough) this.seeThrough++;
    }

    void warm(int points) {
        warmth = Math.max(warmth, points);
    }

    void detail(int kind) {
        detail |= 1 << kind;
    }

    public int trimKinds() {
        return Integer.bitCount(detail & TRIM_MASK);
    }

    public int softKinds() {
        return Integer.bitCount(detail & SOFT_MASK);
    }

    public int workedKinds() {
        return workedKinds.size();
    }

    /** How far the floor stands over the ground outside (0 when unknown). */
    public int elevation() {
        return ground == Integer.MIN_VALUE || floorY == Integer.MAX_VALUE ? 0 : Math.max(0, floorY - ground);
    }

    public int count(Furniture f) {
        return furniture[f.ordinal()];
    }

    /** The compass word for where the unlit floor is, from the room's middle ("north", or "middle"). */
    String darkEnd() {
        if (dark == 0) return "middle";
        double cx = (minX + maxX) / 2.0, cz = (minZ + maxZ) / 2.0;
        double dx = (double) darkX / dark - cx, dz = (double) darkZ / dark - cz;
        double halfW = Math.max(1, (maxX - minX) / 2.0), halfD = Math.max(1, (maxZ - minZ) / 2.0);
        if (Math.abs(dx) / halfW < 0.25 && Math.abs(dz) / halfD < 0.25) return "middle";
        return Math.abs(dx) / halfW > Math.abs(dz) / halfD ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    /** The main material: the family with the most faces (unknown blocks never name a room). */
    Materials.Family mainFamily() {
        int best = -1, bestN = 0;
        for (int i = 0; i < family.length; i++) {
            if (i == Materials.Family.OTHER.ordinal()) continue;
            if (family[i] > bestN) {
                bestN = family[i];
                best = i;
            }
        }
        return best < 0 ? Materials.Family.OTHER : Materials.Family.values()[best];
    }

    /** Faces per family (the home's Style adds them up across rooms). */
    public int[] families() {
        return family.clone();
    }

    // ---- what is kept after the scan ----------------------------------------------------------------------------------

    public static final int F_FLOOR = 0, F_HEIGHT = 1, F_SEATS = 2, F_TABLES = 3, F_CEILING = 4, F_GLASS_CEILING = 5,
            F_ELEVATION = 6, F_WINDOW_DIRS = 7, F_SOFT = 8, F_TRIM = 9, F_WARMTH = 10, F_MAIN = 11,
            // (W6) the Mine head: the deepest climbable shaft under a hatch in the floor, and where its hatch is
            F_SHAFT = 12, F_SHAFT_X = 13, F_SHAFT_Y = 14, F_SHAFT_Z = 15, F_COUNT = 16;

    /** Trapdoors in the floor (a shaft's hatch, at most four looked at), and the deepest shaft found under one. */
    final long[] hatches = new long[4];
    int hatchCount;
    public int shaft;
    public long shaftAt;

    void hatch(long pos) {
        if (hatchCount < hatches.length) hatches[hatchCount++] = pos;
    }

    /** The numbers other code reads after the scan (the room kinds' conditions, Comfort), saved with the room. */
    public int[] facts() {
        int[] f = new int[F_COUNT];
        f[F_FLOOR] = floor;
        f[F_HEIGHT] = height;
        f[F_SEATS] = seats;
        f[F_TABLES] = tables;
        f[F_CEILING] = ceiling;
        f[F_GLASS_CEILING] = glassCeiling;
        f[F_ELEVATION] = elevation();
        f[F_WINDOW_DIRS] = windowDirs;
        f[F_SOFT] = softKinds();
        f[F_TRIM] = trimKinds();
        f[F_WARMTH] = warmth;
        f[F_MAIN] = mainFamily().ordinal();
        if (shaft > 0) {
            f[F_SHAFT] = shaft;
            f[F_SHAFT_X] = net.minecraft.core.BlockPos.getX(shaftAt);
            f[F_SHAFT_Y] = net.minecraft.core.BlockPos.getY(shaftAt);
            f[F_SHAFT_Z] = net.minecraft.core.BlockPos.getZ(shaftAt);
        }
        return f;
    }
}
