package dev.awaken.delve.cave;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A chute (PLAN 8.2): a Delver ore chute, {@value #LENGTH} blocks of cut steps falling half a block each (slab, block,
 * slab: the stair slide's own rhythm, SPEC §17.22), three wide between dressed walls, from a timbered mouth where the
 * cold air comes up, ending at a lip over an underground lake: slide it and it throws you out over the water. The way
 * back is the steps themselves (a half block a block walks up) and a stair cut down the cavern's side to a landing at
 * the water.
 *
 * <p>Spec: (x, y, z) the mouth's floor (feet), {@code rot} forward = downhill.
 */
final class Chute {
    private Chute() {}

    static final int LENGTH = 48;
    /** The lake's surface (feet) below the mouth, and its depth. */
    static final int LAKE = -29, LAKE_DEPTH = 4;

    static BoundingBox box(Spec s) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        int ax = f.x(-13, -6), az = f.z(-13, -6), bx = f.x(13, LENGTH + 26), bz = f.z(13, LENGTH + 26);
        return new BoundingBox(Math.min(ax, bx), s.y() + LAKE - LAKE_DEPTH - 4, Math.min(az, bz), Math.max(ax, bx), s.y() + 7, Math.max(az, bz));
    }

    /** The floor's top (relative to the mouth) at step {@code i}: down half a block each. */
    static double floorTop(int i) {
        return -i / 2.0;
    }

    static void build(Spec s, Sink k) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        long seed = s.seed();
        // ---- the mouth: a landing in the rock, a timber portal, the way in from the caves ----------------------------
        f.carve(k, -2, 0, -4, 2, 3, 1);
        for (int lx = -2; lx <= 2; lx++) for (int lz = -4; lz <= 1; lz++) f.put(k, lx, -1, lz, Carve.rock(f.y(-1)));
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int ly = 0; ly <= 3; ly++) {
            f.put(k, -2, ly, 1, post);
            f.put(k, 2, ly, 1, post);
        }
        for (int lx = -2; lx <= 2; lx++) f.put(k, lx, 4, 1, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        f.put(k, -1, 3, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        f.put(k, 2, 0, -3, Blocks.BARREL.defaultBlockState());
        if (s.joined()) {
            Carve.tunnel(k, f.x(0.5, -3.5), f.y(0), f.z(0.5, -3.5), s.jx() + 0.5, s.jy(), s.jz() + 0.5, 1.5, 2.0, seed + 1);
            k.mark("start", s.jx(), s.jy(), s.jz());
        } else {
            f.mark(k, "start", 0, 0, -3);
        }
        f.mark(k, "mouth", 0, 0, 0);
        // ---- the chute: slab and block, half a block down a block, dressed walls and a timber set every eight --------
        if (f.touches(k, -3, 1, 3, LENGTH + 3)) {
            for (int i = 0; i < LENGTH; i++) {
                int lz = 2 + i;
                double top = floorTop(i);
                int full = (int) Math.floor(top); // the highest full block's top, or the slab's base
                boolean half = top != full;
                int airFrom = half ? full + 1 : full;
                f.carve(k, -1, airFrom, lz, 1, full + 4, lz);
                for (int lx = -1; lx <= 1; lx++) {
                    if (half) {
                        f.put(k, lx, full, lz, Carve.slab(f.y(full)).setValue(SlabBlock.TYPE, SlabType.BOTTOM));
                        f.put(k, lx, full - 1, lz, Carve.bricks(f.y(full - 1)));
                    } else {
                        f.put(k, lx, full - 1, lz, Carve.bricks(f.y(full - 1)));
                    }
                }
                for (int ly = full - 1; ly <= full + 4; ly++) {
                    BlockState d = Carve.dressed(f.y(ly));
                    f.put(k, -2, ly, lz, d);
                    f.put(k, 2, ly, lz, d);
                }
                if (i % 8 == 4) {
                    for (int ly = full; ly <= full + 3; ly++) {
                        f.put(k, -2, ly, lz, post);
                        f.put(k, 2, ly, lz, post);
                    }
                    for (int lx = -2; lx <= 2; lx++) f.put(k, lx, full + 4, lz, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                }
                if (i % 16 == 9) f.put(k, 1, full + 3, lz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        // ---- the lake cavern: its lip, the water, a landing and a stair back up ---------------------------------------
        int lip = (int) Math.floor(floorTop(LENGTH - 1));
        double cz = 2 + LENGTH + 9.5;
        Carve.blob(k, f.x(0.5, cz), f.y(LAKE + 3), f.z(0.5, cz), 9.5, 8.0, 11.0, seed + 2, 0.2, 4.0, f.y(LAKE - LAKE_DEPTH));
        // past the last step nothing but air down to the water: the slide's run-out throws you out over it
        f.carve(k, -2, LAKE - LAKE_DEPTH, 2 + LENGTH, 2, lip + 4, 2 + LENGTH + 3);
        BoundingBox lake = new BoundingBox(f.x(-12, 2 + LENGTH), f.y(LAKE - LAKE_DEPTH), f.z(-12, 2 + LENGTH), f.x(12, 2 + LENGTH + 22), f.y(LAKE - 1), f.z(12, 2 + LENGTH + 22));
        int lx0 = Math.max(Math.min(lake.minX(), lake.maxX()), k.minX()), lx1 = Math.min(Math.max(lake.minX(), lake.maxX()), k.maxX());
        int lz0 = Math.max(Math.min(lake.minZ(), lake.maxZ()), k.minZ()), lz1 = Math.min(Math.max(lake.minZ(), lake.maxZ()), k.maxZ());
        for (int x = lx0; x <= lx1; x++) for (int z = lz0; z <= lz1; z++) for (int y = lake.minY(); y <= lake.maxY(); y++) {
            if (k.inside(x, y, z) && k.get(x, y, z).isAir()) k.set(x, y, z, Carve.WATER);
        }
        // the stair down the cavern's east side from beside the lip to a landing at the water
        int sx = 3;
        f.carve(k, 2, lip, LENGTH - 2, 4, lip + 4, LENGTH + 1);
        for (int lz = LENGTH - 2; lz <= LENGTH + 1; lz++) for (int lx = 2; lx <= 4; lx++) f.put(k, lx, lip - 1, lz, Carve.rock(f.y(lip - 1)));
        int steps = lip - LAKE;
        for (int i = 0; i < steps; i++) {
            int lz = LENGTH + 2 + i, ly = lip - 1 - i;
            f.carve(k, sx - 1, ly, lz, sx + 1, ly + 4, lz);
            for (int lx = sx - 1; lx <= sx + 1; lx++) {
                f.put(k, lx, ly, lz, Carve.stairs(f.y(ly)).setValue(StairBlock.FACING, Direction.NORTH));
                f.put(k, lx, ly - 1, lz, Carve.bricks(f.y(ly - 1)));
            }
        }
        int landing = LENGTH + 2 + steps;
        for (int lz = landing; lz <= landing + 1; lz++) for (int lx = sx - 1; lx <= sx + 1; lx++) {
            f.carve(k, lx, LAKE, lz, lx, LAKE + 3, lz);
            f.put(k, lx, LAKE - 1, lz, Carve.bricks(f.y(LAKE - 1)));
        }
        f.put(k, sx + 1, LAKE, landing + 1, Blocks.LANTERN.defaultBlockState());
        f.mark(k, "goal", sx, LAKE, landing);
        f.mark(k, "lip", 0, lip + 1, 2 + LENGTH - 1);
    }
}
