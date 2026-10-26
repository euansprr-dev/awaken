package dev.awaken.delve.cave;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A grotto (PLAN 8.2): behind a waterfall. A passage from the caves ends at a curtain of falling water across a doorway
 * (walk through it, wade the trench under it); beyond, a round room lit by lichen and glow berries, a pool glowing from
 * below (sea pickles in its bed), a geode half out of the wall like a cracked egg that you climb into, and under the
 * floor by the pool a buried cache (a Veil secret: a loot chest under suspicious gravel; opening it counts a region
 * secret, {@code awaken:secrets_found}).
 *
 * <p>Spec: (x, y, z) the room's centre on its floor (feet), {@code rot} forward = away from the curtain.
 */
final class Grotto {
    private Grotto() {}

    static final String CACHE_LOOT = "awaken:chests/delve/grotto_cache";
    static final int ROOM = 7;

    static BoundingBox box(Spec s) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        int ax = f.x(-15, -17), az = f.z(-15, -17), bx = f.x(10, 10), bz = f.z(10, 10);
        return new BoundingBox(Math.min(ax, bx), s.y() - 6, Math.min(az, bz), Math.max(ax, bx), s.y() + 12, Math.max(az, bz));
    }

    /** Some grottos' pools are a legendary fishing spot (a third of them, fixed by the seed). */
    static boolean fishingSpot(Spec s) {
        return Carve.rand(s.seed() + 77, 0, 0, 0) < 0.34;
    }

    /** The buried cache's chest (world position), for the secret's count. */
    static int[] cache(Spec s) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        return new int[]{f.x(-2, 4), f.y(-2), f.z(-2, 4)};
    }

    static void build(Spec s, Sink k) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        long seed = s.seed();
        // ---- the room ---------------------------------------------------------------------------------------------------
        Carve.blob(k, f.x(0.5, 0.5), f.y(0) + 3.2, f.z(0.5, 0.5), ROOM, 4.4, ROOM, seed + 1, 0.16, 3.5, f.y(0));
        // ---- the way in, the curtain ------------------------------------------------------------------------------------
        int door = -ROOM;
        f.carve(k, -1, 0, door - 7, 1, 2, door);
        for (int lz = door - 7; lz <= door + 1; lz++) for (int lx = -1; lx <= 1; lx++) f.put(k, lx, -1, lz, Carve.rock(f.y(-1)));
        // the doorway opens into the room whatever the room's rough wall does there
        f.carve(k, -1, 0, door + 1, 1, 2, door + 3);
        // the slot in the roof the spring falls from, closed round its sides so it only falls
        for (int lx = -2; lx <= 2; lx++) {
            f.put(k, lx, 4, door, Carve.rock(f.y(4)));
            f.put(k, lx, 3, door - 1, Carve.rock(f.y(3)));
            f.put(k, lx, 3, door + 1, Carve.rock(f.y(3)));
            f.put(k, lx, -1, door - 1, Carve.rock(f.y(-1)));
            f.put(k, lx, -1, door + 1, Carve.rock(f.y(-1)));
            f.put(k, lx, -2, door, Carve.rock(f.y(-2)));
        }
        f.put(k, -2, 3, door, Carve.rock(f.y(3)));
        f.put(k, 2, 3, door, Carve.rock(f.y(3)));
        f.put(k, -2, -1, door, Carve.rock(f.y(-1)));
        f.put(k, 2, -1, door, Carve.rock(f.y(-1)));
        for (int lx = -1; lx <= 1; lx++) {
            f.put(k, lx, 3, door, Carve.WATER);
            for (int ly = 0; ly <= 2; ly++) f.put(k, lx, ly, door, Carve.FALLING);
            f.put(k, lx, -1, door, Carve.WATER);
            k.flow(f.x(lx, door), f.y(3), f.z(lx, door));
        }
        if (s.joined()) {
            Carve.tunnel(k, f.x(0.5, door - 6.5), f.y(0), f.z(0.5, door - 6.5), s.jx() + 0.5, s.jy(), s.jz() + 0.5, 1.5, 2.0, seed + 2);
            k.mark("start", s.jx(), s.jy(), s.jz());
        } else {
            f.mark(k, "start", 0, 0, door - 6);
        }
        f.mark(k, "curtain", 0, 0, door - 2);
        // ---- the glowing pool -------------------------------------------------------------------------------------------
        int px = 3, pz = 2;
        for (int lx = px - 3; lx <= px + 3; lx++) for (int lz = pz - 3; lz <= pz + 3; lz++) {
            double d = Math.sqrt((lx - px) * (lx - px) + (lz - pz) * (lz - pz));
            if (d > 2.9) continue;
            int depth = d < 1.6 ? 3 : 2;
            for (int ly = -depth; ly <= -1; ly++) f.put(k, lx, ly, lz, Carve.WATER);
            f.put(k, lx, -depth - 1, lz, Blocks.CLAY.defaultBlockState());
            if (d < 2.2 && Carve.rand(seed + 3, lx, 0, lz) < (fishingSpot(s) ? 0.7 : 0.35)) {
                f.put(k, lx, -depth, lz, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + (int) (Carve.rand(seed + 4, lx, 1, lz) * 4))
                        .setValue(SeaPickleBlock.WATERLOGGED, true));
            }
        }
        f.mark(k, "pool", px, 0, pz);
        f.mark(k, "room", 3, 0, -4);
        // ---- the geode: an egg of basalt, calcite and amethyst half out of the west wall, cracked open ----------------
        geode(s, k, f);
        // ---- the cache: a chest under the floor by the pool, suspicious gravel over it ----------------------------------
        int[] c = cache(s);
        k.lootChest(c[0], c[1], c[2], Carve.turn(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), f.rot), CACHE_LOOT, seed + 5);
        Carve.put(k, c[0], c[1] + 1, c[2], Blocks.SUSPICIOUS_GRAVEL.defaultBlockState());
        Carve.put(k, c[0], c[1] - 1, c[2], Carve.rock(c[1] - 1));
        k.mark("cache", c[0], c[1], c[2]);
        // ---- the light: lichen on the walls, glow berries from the roof, moss underfoot ---------------------------------
        int x0 = f.x(-9, -9), x1 = f.x(9, 9), z0 = f.z(-9, -9), z1 = f.z(9, 9);
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
        if (!k.touches(ax, az, bx, bz)) return;
        for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) {
            for (int y = f.y(0); y <= f.y(9); y++) {
                if (!Carve.open(k, x, y, z)) continue;
                double r = Carve.rand(seed + 6, x, y, z);
                if (r < 0.1) Carve.lichen(k, x, y, z);
                else if (r < 0.125 && Carve.solidAt(k, x, y + 1, z)) Sinkhole.vines(s, k, x, y, z);
            }
            int fy = f.y(0);
            if (Carve.open(k, x, fy, z) && Carve.solidAt(k, x, fy - 1, z) && !k.get(x, fy - 1, z).is(Blocks.SUSPICIOUS_GRAVEL)
                    && Carve.noise(seed + 7, x, 0, z, 3) > 0.55) Carve.put(k, x, fy - 1, z, Blocks.MOSS_BLOCK.defaultBlockState());
        }
    }

    private static void geode(Spec s, Sink k, Frame f) {
        long seed = s.seed();
        int gx = -7, gy = 4, gz = 1;
        double cx = f.x(gx + 0.5, gz + 0.5), cy = f.y(gy) + 0.5, cz = f.z(gx + 0.5, gz + 0.5);
        int r = 6;
        int ix = Mth.floor(cx), iz = Mth.floor(cz);
        if (!k.touches(ix - r, iz - r, ix + r, iz + r)) return;
        for (int x = ix - r; x <= ix + r; x++) for (int z = iz - r; z <= iz + r; z++) for (int y = Mth.floor(cy) - r; y <= Mth.floor(cy) + r; y++) {
            if (!k.inside(x, y, z)) continue;
            double d = Math.sqrt((x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy) + (z + 0.5 - cz) * (z + 0.5 - cz));
            d += (Carve.noise(seed + 8, x, y, z, 2.5) - 0.5) * 0.5;
            BlockState b;
            if (d < 2.6) b = y <= f.y(1) ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Carve.AIR;
            else if (d < 3.4) b = Carve.rand(seed + 9, x, y, z) < 0.12 ? Blocks.BUDDING_AMETHYST.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState();
            else if (d < 4.2) b = Blocks.CALCITE.defaultBlockState();
            else if (d < 5.0) b = Blocks.SMOOTH_BASALT.defaultBlockState();
            else continue;
            k.set(x, y, z, b);
        }
        // the crack toward the room, two wide and two high, and a fallen stone under it to climb from
        for (int lx = gx; lx <= gx + 6; lx++) for (int lz = gz; lz <= gz + 1; lz++) for (int ly = 2; ly <= 3; ly++) f.air(k, lx, ly, lz);
        for (int lz = gz; lz <= gz + 1; lz++) for (int lx = gx - 1; lx <= gx + 5; lx++) {
            if (f.inside(k, lx, 1, lz) && !f.get(k, lx, 1, lz).isSolidRender()) f.put(k, lx, 1, lz, Blocks.AMETHYST_BLOCK.defaultBlockState());
        }
        f.put(k, gx + 6, 0, gz, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        f.air(k, gx + 6, 1, gz);
        // clusters growing in from the inner walls
        for (int x = ix - 3; x <= ix + 3; x++) for (int z = iz - 3; z <= iz + 3; z++) for (int y = Mth.floor(cy) - 2; y <= Mth.floor(cy) + 3; y++) {
            if (!Carve.open(k, x, y, z) || Carve.rand(seed + 10, x, y, z) > 0.35) continue;
            for (Direction d : Direction.values()) {
                int nx = x - d.getStepX(), ny = y - d.getStepY(), nz = z - d.getStepZ();
                if (!k.inside(nx, ny, nz) || !k.get(nx, ny, nz).is(Blocks.AMETHYST_BLOCK) && !k.get(nx, ny, nz).is(Blocks.BUDDING_AMETHYST)) continue;
                if (d == Direction.UP && y <= f.y(2)) break; // the floor keeps clear to stand on
                BlockState c = (Carve.rand(seed + 12, x, y, z) < 0.5 ? Blocks.AMETHYST_CLUSTER : Blocks.MEDIUM_AMETHYST_BUD).defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, d);
                k.set(x, y, z, c);
                break;
            }
        }
        f.mark(k, "goal", gx, 2, gz);
    }
}
