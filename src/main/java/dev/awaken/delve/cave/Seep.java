package dev.awaken.delve.cave;

import dev.awaken.body.ascent.AscentContent;
import dev.awaken.delve.DelveContent;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A seep (PLAN 8.2): below y 0, where the Deep presses up through the rock round a Deepglass lode. A long hall stained
 * violet-black, Deepglass in veins along its walls and in spires you climb (a spiral of steps round each), a magma vent
 * whose updraft lifts a glider to the roof, a Hollows nest in an alcove, and through a curving throat the heart chamber:
 * a Deepglass dais where the seep heart is rooted, the lode's own heart glinting in the wall behind it. Its rule: the
 * creatures in it are five levels over the region (living's Regions), the sound muffles and hums, the light goes violet;
 * kill its heart and it falls quiet.
 *
 * <p>Spec: (x, y, z) the hall's centre on its floor (feet), p = [lode heart x, y, z].
 */
final class Seep {
    private Seep() {}

    static final int HX = 0, HY = 1, HZ = 2;
    static final double HALL_LONG = 13, HALL_HIGH = 7, HALL_WIDE = 8.5;

    /** The heart chamber's centre on its floor: six back from the lode's heart toward the hall. */
    static int[] chamber(Spec s) {
        double ux = s.p(HX) - s.x(), uz = s.p(HZ) - s.z(), d = Math.max(1e-3, Math.sqrt(ux * ux + uz * uz));
        return new int[]{Mth.floor(s.p(HX) + 0.5 - ux / d * 6), s.p(HY) - 2, Mth.floor(s.p(HZ) + 0.5 - uz / d * 6)};
    }

    static BoundingBox box(Spec s) {
        BoundingBox b = new BoundingBox(s.x() - 20, s.y() - 4, s.z() - 20, s.x() + 20, s.y() + 18, s.z() + 20);
        b.encapsulate(new BoundingBox(s.p(HX) - 12, s.p(HY) - 8, s.p(HZ) - 12, s.p(HX) + 12, s.p(HY) + 10, s.p(HZ) + 12));
        return b;
    }

    static void build(Spec s, Sink k) {
        long seed = s.seed();
        int[] c = chamber(s);
        double ux = c[0] - s.x(), uz = c[2] - s.z(), ul = Math.max(1e-3, Math.sqrt(ux * ux + uz * uz));
        ux /= ul;
        uz /= ul;
        int floor = s.y();
        // ---- the hall: an ellipsoid laid along the way to the chamber, cut flat underfoot ----------------------------
        hall(s, k, ux, uz, floor);
        // ---- the throat to the chamber, the chamber -------------------------------------------------------------------
        double ex = s.x() + 0.5 + ux * (HALL_LONG - 2), ez = s.z() + 0.5 + uz * (HALL_LONG - 2);
        Carve.tunnel(k, ex, floor, ez, c[0] + 0.5 - ux * 4, c[1], c[2] + 0.5 - uz * 4, 1.6, 3.0, seed + 3);
        Carve.blob(k, c[0] + 0.5, c[1] + 3.5, c[2] + 0.5, 5.6, 4.6, 5.6, seed + 4, 0.18, 3.0, c[1]);
        // the lode's heart stays in the back wall, a hand's breadth of air before it
        int hx = s.p(HX), hy = s.p(HY), hz = s.p(HZ);
        Carve.air(k, Mth.floor(hx + 0.5 - ux * 1.2), hy, Mth.floor(hz + 0.5 - uz * 1.2));
        Carve.solid(k, hx, hy, hz);
        // the dais: Deepglass underfoot round the socket, veins running out from it across the floor
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) Carve.put(k, c[0] + dx, c[1] - 1, c[2] + dz, glass());
        for (int v = 0; v < 6; v++) {
            double a = v * Math.PI / 3 + Carve.rand(seed + 5, v, 0, 0) * 0.6;
            for (double t = 2; t < 6.5; t += 0.7) {
                int x = Mth.floor(c[0] + 0.5 + Math.cos(a) * t), z = Mth.floor(c[2] + 0.5 + Math.sin(a) * t);
                if (Carve.solidAt(k, x, c[1] - 1, z)) Carve.put(k, x, c[1] - 1, z, glass());
            }
        }
        k.mark("socket", c[0], c[1], c[2]);
        k.mark("hall", Mth.floor(s.x() + 0.5 - ux * 6), floor, Mth.floor(s.z() + 0.5 - uz * 6));
        k.mark("goal", c[0] + Mth.floor(-ux * 3 + 0.5), c[1], c[2] + Mth.floor(-uz * 3 + 0.5));
        // ---- the nest: an alcove off the hall's side, bone and shed chitin round a hollow ------------------------------
        nest(s, k, ux, uz, floor);
        // ---- spires and the vent in the hall ---------------------------------------------------------------------------
        for (int i = 0; i < 4; i++) {
            double along = -HALL_LONG * 0.6 + i * HALL_LONG * 0.4 + Carve.rand(seed + 10, i, 0, 0) * 2;
            double side = (i % 2 == 0 ? 1 : -1) * (HALL_WIDE * 0.45 + Carve.rand(seed + 11, i, 0, 0) * 1.5);
            int x = Mth.floor(s.x() + 0.5 + ux * along - uz * side), z = Mth.floor(s.z() + 0.5 + uz * along + ux * side);
            spire(s, k, x, floor, z, 5 + (int) (Carve.rand(seed + 12, i, 0, 0) * 5), i);
        }
        vent(k, Mth.floor(s.x() + 0.5 - ux * 4 + uz * 2), floor, Mth.floor(s.z() + 0.5 - uz * 4 - ux * 2));
        // ---- the stain: the rock round every opening goes violet-black, Deepglass breaks through in veins -------------
        stain(s, k, c);
        veins(s, k, c, floor);
        // ---- the way in: a ruined Delver gallery from the natural caves -------------------------------------------------
        double sx = s.x() + 0.5 - ux * (HALL_LONG - 1.5), sz = s.z() + 0.5 - uz * (HALL_LONG - 1.5);
        if (s.joined()) {
            Carve.tunnel(k, sx, floor, sz, s.jx() + 0.5, s.jy(), s.jz() + 0.5, 1.6, 2.0, seed + 20);
            k.mark("start", s.jx(), s.jy(), s.jz());
        } else {
            k.mark("start", Mth.floor(sx), floor, Mth.floor(sz));
        }
        // a broken timber set where the Delvers' drift came in
        int bx = Mth.floor(sx - ux * 1.5), bz = Mth.floor(sz - uz * 1.5);
        Direction across = Math.abs(ux) > Math.abs(uz) ? Direction.SOUTH : Direction.EAST;
        for (int side = -2; side <= 2; side += 4) {
            int px = bx + across.getStepX() * side, pz = bz + across.getStepZ() * side;
            for (int y = floor; y <= floor + (side < 0 ? 3 : 1); y++) if (Carve.open(k, px, y, pz)) Carve.put(k, px, y, pz, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        Carve.put(k, bx, floor, bz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                across.getAxis()));
        // a dead Delver lamp over the hall (SPEC §34.6)
        Carve.lamp(k, s.x(), floor, s.z(), 16);
    }

    private static BlockState glass() {
        return DelveContent.DEEPGLASS.get().defaultBlockState();
    }

    private static void hall(Spec s, Sink k, double ux, double uz, int floor) {
        long seed = s.seed();
        double cx = s.x() + 0.5, cy = floor + HALL_HIGH * 0.72, cz = s.z() + 0.5;
        int r = (int) Math.ceil(HALL_LONG * 1.3) + 1;
        if (!k.touches(s.x() - r, s.z() - r, s.x() + r, s.z() + r)) return;
        for (int x = Math.max(s.x() - r, k.minX()); x <= Math.min(s.x() + r, k.maxX()); x++) {
            for (int z = Math.max(s.z() - r, k.minZ()); z <= Math.min(s.z() + r, k.maxZ()); z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                double a = (dx * ux + dz * uz) / HALL_LONG, b = (-dx * uz + dz * ux) / HALL_WIDE;
                for (int y = floor; y <= floor + (int) (HALL_HIGH * 2); y++) {
                    double h = (y + 0.5 - cy) / HALL_HIGH;
                    double d = a * a + b * b + h * h;
                    double kk = 1 + 0.26 * (2 * Carve.noise(seed + 1, x, y, z, 4.5) - 1);
                    if (d <= kk * kk) Carve.air(k, x, y, z);
                }
            }
        }
    }

    private static void nest(Spec s, Sink k, double ux, double uz, int floor) {
        long seed = s.seed();
        double side = Carve.rand(seed + 30, 0, 0, 0) < 0.5 ? 1 : -1;
        double nx = s.x() + 0.5 + ux * 2 - uz * side * (HALL_WIDE + 3.5), nz = s.z() + 0.5 + uz * 2 + ux * side * (HALL_WIDE + 3.5);
        Carve.blob(k, nx, floor + 1.6, nz, 4.2, 2.4, 4.2, seed + 31, 0.2, 3, floor);
        Carve.tunnel(k, s.x() + 0.5 + ux * 2 - uz * side * (HALL_WIDE - 2), floor, s.z() + 0.5 + uz * 2 + ux * side * (HALL_WIDE - 2), nx, floor, nz, 1.6, 0.5, seed + 32);
        int bx = Mth.floor(nx), bz = Mth.floor(nz);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            int x = bx + dx, z = bz + dz;
            if (!Carve.open(k, x, floor, z) || !Carve.solidAt(k, x, floor - 1, z)) continue;
            if (d < 1.8) Carve.put(k, x, floor - 1, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
            else if (d < 3.4 && Carve.rand(seed + 33, x, 0, z) < 0.5) Carve.put(k, x, floor, z, Blocks.BONE_BLOCK.defaultBlockState());
            else if (Carve.rand(seed + 34, x, 0, z) < 0.12) Carve.put(k, x, floor, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP).setValue(PointedDripstoneBlock.THICKNESS, SpeleothemThickness.TIP));
        }
        Carve.put(k, bx + 2, floor, bz - 1, Blocks.SKELETON_SKULL.defaultBlockState());
        k.mark("nest", bx, floor, bz);
    }

    /** A Deepglass spire you can climb: a core column and a ring of steps round it, a block up each, edge to edge. */
    private static void spire(Spec s, Sink k, int x, int floor, int z, int height, int i) {
        if (!Carve.solidAt(k, x, floor - 1, z) && k.inside(x, floor - 1, z)) return;
        for (int y = floor; y < floor + height; y++) Carve.put(k, x, y, z, glass());
        int[][] ring = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        int start = (i * 3) & 7;
        for (int step = 0; step < height - 1; step++) {
            int[] o = ring[(start + step) & 7];
            int sx = x + o[0], sz = z + o[1], y = floor + step;
            if (!Carve.open(k, sx, y, sz)) continue;
            Carve.put(k, sx, y, sz, step % 3 == 2 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : glass());
        }
        int top = floor + height;
        if (Carve.open(k, x, top, z)) Carve.put(k, x, top, z, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
        k.mark("spire" + i, x, top, z);
    }

    /** A magma vent in the hall's floor: the Ascent's updraft over it lifts a glider to the roof (PLAN 8.2, Vents). */
    private static void vent(Sink k, int x, int floor, int z) {
        if (!Carve.solidAt(k, x, floor - 1, z) && k.inside(x, floor - 1, z)) return;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (dx * dx + dz * dz > 5) continue;
            if (Carve.solidAt(k, x + dx, floor - 1, z + dz)) Carve.put(k, x + dx, floor - 1, z + dz, Blocks.MAGMA_BLOCK.defaultBlockState());
        }
        Carve.put(k, x, floor - 2, z, Blocks.MAGMA_BLOCK.defaultBlockState());
        Carve.put(k, x, floor - 1, z, AscentContent.UPDRAFT_VENT.get().defaultBlockState());
        k.mark("vent", x, floor, z);
    }

    /** The rock that touches the seep's air: black, violet and glassy (blackstone, obsidian, crying obsidian, tuff). */
    private static void stain(Spec s, Sink k, int[] c) {
        long seed = s.seed();
        BoundingBox b = box(s);
        int x0 = Math.max(b.minX(), k.minX()), x1 = Math.min(b.maxX(), k.maxX());
        int z0 = Math.max(b.minZ(), k.minZ()), z1 = Math.min(b.maxZ(), k.maxZ());
        int y0 = Math.max(b.minY(), k.minY()), y1 = Math.min(b.maxY(), k.maxY());
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            BlockState here = k.get(x, y, z);
            if (!here.isSolidRender() || here.is(DelveContent.DEEPGLASS.get()) || !stainable(here) || !Mine.touchesAir(k, x, y, z)) continue;
            double n = Carve.noise(seed + 40, x, y, z, 3.0), r = Carve.rand(seed + 41, x, y, z);
            BlockState to = n > 0.62 ? (r < 0.15 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState())
                    : n > 0.42 ? Blocks.BLACKSTONE.defaultBlockState() : n > 0.3 ? Blocks.TUFF.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            k.set(x, y, z, to);
        }
    }

    private static boolean stainable(BlockState s) {
        return s.is(Blocks.DEEPSLATE) || s.is(Blocks.STONE) || s.is(Blocks.TUFF) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.CALCITE);
    }

    /** Veins of Deepglass along the walls, wandering toward the chamber: where the Deep runs through the rock. */
    private static void veins(Spec s, Sink k, int[] c, int floor) {
        long seed = s.seed();
        for (int v = 0; v < 9; v++) {
            double a = Carve.rand(seed + 50, v, 0, 0) * Math.PI * 2;
            double x = s.x() + 0.5 + Math.cos(a) * (HALL_WIDE + 0.5), z = s.z() + 0.5 + Math.sin(a) * (HALL_WIDE + 0.5);
            double y = floor + 1 + Carve.rand(seed + 51, v, 0, 0) * HALL_HIGH;
            int len = 10 + (int) (Carve.rand(seed + 52, v, 0, 0) * 10);
            for (int i = 0; i < len; i++) {
                double tx = c[0] - x, tz = c[2] - z, td = Math.max(1e-3, Math.sqrt(tx * tx + tz * tz));
                double w = Carve.noise(seed + 53, v * 17, i, 0, 4) * 2 - 1;
                x += tx / td * 0.7 + (-tz / td) * w * 0.9;
                z += tz / td * 0.7 + (tx / td) * w * 0.9;
                y += (Carve.rand(seed + 54, v, i, 0) - 0.5) * 0.8;
                int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
                // a vein shows only where it runs in the rock face, never across the air
                for (int oy = 0; oy <= 1; oy++) {
                    if (Carve.solidAt(k, ix, iy + oy, iz) && Mine.touchesAir(k, ix, iy + oy, iz)) Carve.put(k, ix, iy + oy, iz, glass());
                }
            }
        }
    }
}
