package dev.awaken.delve.cave;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A mouth (SPEC §34.1): a way into the caves you can see from far off, about one every 200 m of new land. Two forms for
 * now, picked by the ground:
 * <ul>
 * <li><b>Hill mouth</b>: a dark arch {@code W} wide and {@code H} high cut into a slope, roots hanging over the lip and
 * vines down its face, a throat falling about one in two (a drop of two now and then) narrowing to a passage that meets
 * natural cave air.</li>
 * <li><b>Delver adit</b>: a timbered square mouth in a hillside with old rails, a short drift that runs straight to a
 * lode's heart (iron or copper, the seam showing in the end face) and on into the cave the seam runs along. The first
 * trip's place: the Delver's Pick is stuck in the stone at the drift's end (mark "pick").</li>
 * </ul>
 * Beside both, on open ground in front, a Delver cairn (stacked stones and an iron spike; at an adit a lantern on a
 * post). The mouth breathes mist at dawn and after rain and a draught comes out of it (client, from the place's site).
 *
 * <p>Spec: (x, y, z) the mouth's floor (feet) at the lip, {@code rot} forward = into the hill.
 */
final class Mouth {
    private Mouth() {}

    /** p: form, throat length, its drop, arch width, arch height, the lode's heart (x, y, z), its ore + 1, the cairn's side. */
    static final int FORM = 0, LEN = 1, DROP = 2, W = 3, H = 4, HX = 5, HY = 6, HZ = 7, ORE = 8, CAIRN = 9;
    static final int HILL = 0, ADIT = 1;
    /** The portal's depth: the arch (or the timbered frame) before the throat begins. */
    static final int PORTAL = 4;

    static BoundingBox box(Spec s) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        int half = s.p(W) / 2 + 9;
        int ax = f.x(-half, -10), az = f.z(-half, -10), bx = f.x(half, s.p(LEN) + 4), bz = f.z(half, s.p(LEN) + 4);
        int y0 = s.y() - s.p(DROP) - 4, y1 = s.y() + Math.max(s.p(H), 5) + 4;
        if (s.p(FORM) == ADIT && s.p(ORE) != 0) y0 = Math.min(y0, s.p(HY) - 3);
        return new BoundingBox(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz));
    }

    /** The throat's floor (local, feet) at local z: level through the portal, then down one in two (adit: its own grade). */
    static int floorAt(Spec s, int lz) {
        int L = s.p(LEN), drop = s.p(DROP);
        if (lz <= PORTAL) return 0;
        if (lz >= L) return -drop;
        if (s.p(FORM) == ADIT) return -(int) Math.floor((lz - PORTAL) * (double) drop / Math.max(1, L - PORTAL));
        // the hill's throat: one in two, with a drop of two where the hash says, never past the end's floor
        int f = -(lz - PORTAL) / 2;
        if (Math.floorMod(Carve.hash(s.seed(), lz, 0, 3), 7L) == 0) f -= 1;
        return Math.max(f, -drop);
    }

    /** Where the throat ends (local z L, its floor), in the world: what the joint tunnel starts from. */
    static int[] end(Spec s) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        int L = s.p(LEN);
        return new int[]{f.x(0, L), f.y(-s.p(DROP)), f.z(0, L)};
    }

    static void build(Spec s, Sink k) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        if (s.p(FORM) == ADIT) adit(s, f, k);
        else hill(s, f, k);
        cairn(s, f, k);
        // on into the natural cave the place found
        int[] e = end(s);
        if (s.joined()) {
            if (s.p(FORM) == ADIT) {
                // out of the drift's side, so the heart stays in the end face
                int L = s.p(LEN), fe = floorAt(s, L);
                double sx = f.x(1.5, L - 1.5), sz = f.z(1.5, L - 1.5);
                Carve.tunnel(k, sx, f.y(fe), sz, s.jx() + 0.5, s.jy(), s.jz() + 0.5, 1.5, 1.5, s.seed() + 5);
                if (s.p(ORE) != 0) Carve.solid(k, s.p(HX), s.p(HY), s.p(HZ));
            } else {
                Carve.tunnel(k, e[0] + 0.5, e[1], e[2] + 0.5, s.jx() + 0.5, s.jy(), s.jz() + 0.5, 1.5, 1.5, s.seed() + 5);
            }
            k.mark("goal", s.jx(), s.jy(), s.jz());
        } else {
            k.mark("goal", e[0], e[1], e[2]);
        }
        f.mark(k, "start", 0, 0, -6);
        f.mark(k, "mouth", 0, 0, 0);
    }

    // ---- the hill mouth -----------------------------------------------------------------------------------------------

    private static void hill(Spec s, Frame f, Sink k) {
        int L = s.p(LEN), w = s.p(W), h = s.p(H);
        long seed = s.seed();
        // the ground in front: open to the sky, a floor to stand on (a lip of rock where the slope fell away)
        approach(f, k, w / 2 + 2, h);
        // the arch: an ellipse, full width at the floor, its top rough
        for (int lz = 0; lz <= PORTAL; lz++) {
            double half = w / 2.0 + 0.5 - lz * 0.15;
            for (int lx = -(w / 2 + 1); lx <= w / 2 + 1; lx++) {
                double u = lx / half;
                if (Math.abs(u) > 1) continue;
                int top = (int) Math.floor((h - lz * 0.3) * Math.sqrt(1 - u * u) + Carve.rand(seed, lx, lz, 1) * 0.9);
                for (int ly = 0; ly <= Math.max(2, top); ly++) f.air(k, lx, ly, lz);
                floor(f, k, lx, 0, lz);
            }
        }
        // the throat: narrowing from the arch to a passage four wide and four high, falling one in two
        if (f.touches(k, -w / 2 - 2, PORTAL, w / 2 + 2, L + 1)) {
            for (int lz = PORTAL + 1; lz <= L; lz++) {
                double t = (lz - PORTAL) / (double) Math.max(1, L - PORTAL);
                double half = Carve.lerp(Math.min(1, t * 1.6), w / 2.0, 1.8);
                int top = (int) Math.round(Carve.lerp(Math.min(1, t * 1.4), h - 1.5, 3.5));
                int fl = floorAt(s, lz);
                for (int lx = (int) -Math.ceil(half); lx <= (int) Math.ceil(half); lx++) {
                    double u = Math.abs(lx) / (half + 0.5);
                    if (u > 1) continue;
                    int ceil = fl + Math.max(3, (int) Math.floor(top * Math.sqrt(1 - u * u * 0.7)));
                    for (int ly = fl; ly <= ceil; ly++) f.air(k, lx, ly, lz);
                    floor(f, k, lx, fl, lz);
                }
            }
        }
        // dressing: roots hanging under the lip, vines down the face above the arch, moss and fallen stones inside
        for (int lx = -w / 2; lx <= w / 2; lx++) {
            for (int ly = h - 2; ly <= h + 1; ly++) {
                Carve.roots(k, f.x(lx, 0), f.y(ly), f.z(lx, 0));
                Carve.roots(k, f.x(lx, 1), f.y(ly), f.z(lx, 1));
            }
            if (Carve.rand(seed, lx, 0, 9) < 0.55) {
                int len = 2 + (int) (Carve.rand(seed, lx, 1, 9) * 4);
                for (int ly = h + 2; ly > h + 2 - len; ly--) Carve.vine(k, f.x(lx, -1), f.y(ly), f.z(lx, -1), f.dir(Direction.SOUTH));
            }
        }
        for (int lz = 0; lz <= PORTAL + 3; lz++) {
            for (int lx = -w / 2; lx <= w / 2; lx++) {
                double r = Carve.rand(seed, lx, lz, 11);
                int fl = floorAt(s, lz);
                if (r < 0.28 && Carve.open(k, f.x(lx, lz), f.y(fl), f.z(lx, lz))) f.put(k, lx, fl, lz, Blocks.MOSS_CARPET.defaultBlockState());
                else if (r > 0.95 && Math.abs(lx) >= w / 2 - 1 && Carve.open(k, f.x(lx, lz), f.y(fl), f.z(lx, lz)))
                    f.put(k, lx, fl, lz, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            }
        }
    }

    // ---- the Delver adit ----------------------------------------------------------------------------------------------

    private static void adit(Spec s, Frame f, Sink k) {
        int L = s.p(LEN);
        approach(f, k, 3, 5);
        // spoil heaps beside the cutting
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) f.put(k, side * (4 + i), 0, -3 - i, i == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
        }
        if (f.touches(k, -3, -1, 3, L + 1)) {
            for (int lz = -1; lz < L; lz++) {
                int fl = floorAt(s, lz), next = floorAt(s, lz + 1);
                f.carve(k, -1, fl, lz, 1, fl + 3, lz);
                for (int lx = -1; lx <= 1; lx++) floor(f, k, lx, fl, lz);
                for (int lx = -1; lx <= 1; lx++) f.put(k, lx, fl - 1, lz, lx == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState());
                // old rails where the floor runs level, gaps where it steps and here and there where they were taken up
                boolean level = floorAt(s, lz - 1) == fl && next == fl;
                if (level && lz < L - 2 && Math.floorMod(Carve.hash(s.seed(), lz, 2, 2), 6L) != 0)
                    f.put(k, 0, fl, lz, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.NORTH_SOUTH));
                if (lz >= 4 && lz % 5 == 4) set(f, k, lz, fl);
            }
        }
        // the portal: heavy posts and a lintel, a lantern hook
        BlockState post = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState beam = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int ly = -1; ly <= 4; ly++) {
            f.put(k, -2, ly, 0, post);
            f.put(k, 2, ly, 0, post);
        }
        for (int lx = -3; lx <= 3; lx++) f.put(k, lx, 4, 0, beam);
        f.put(k, -3, 5, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        f.put(k, 3, 5, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        f.put(k, 1, 3, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // the drift's end: the lode's heart in the face (the lode itself is the world's, written after), the pick before it
        int fe = floorAt(s, L);
        f.carve(k, -1, fe, L, 1, fe + 3, L);
        for (int lx = -1; lx <= 1; lx++) floor(f, k, lx, fe, L);
        // the Delver's Pick in the stone before the face (facing out of it: the model's north)
        f.mark(k, "pick", 0, fe, L);
        f.put(k, 0, fe, L, dev.awaken.delve.DelveContent.WAKING_PICK_STONE.get().defaultBlockState());
        f.put(k, -1, fe, L - 1, Blocks.BARREL.defaultBlockState());
    }

    /** A timber set across the drift at lz: posts in the walls, a cap in the roof. */
    private static void set(Frame f, Sink k, int lz, int fl) {
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState cap = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int ly = fl; ly <= fl + 3; ly++) {
            f.put(k, -2, ly, lz, post);
            f.put(k, 2, ly, lz, post);
        }
        for (int lx = -2; lx <= 2; lx++) f.put(k, lx, fl + 4, lz, cap);
    }

    // ---- shared -------------------------------------------------------------------------------------------------------

    /** The ground in front of the mouth: open air over it, a floor where the slope fell away (half-width hw). */
    private static void approach(Frame f, Sink k, int hw, int h) {
        for (int lz = -8; lz < 0; lz++) {
            int w = hw + (-lz) / 3;
            f.carve(k, -w, 0, lz, w, h + 1, lz);
            for (int lx = -w; lx <= w; lx++) {
                if (!f.inside(k, lx, -1, lz)) continue;
                if (!f.get(k, lx, -1, lz).isSolidRender()) f.put(k, lx, -1, lz, Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
    }

    /** Rock under the feet at (lx, fl, lz) where the carving or a natural hole left none. */
    private static void floor(Frame f, Sink k, int lx, int fl, int lz) {
        if (!f.inside(k, lx, fl - 1, lz)) return;
        BlockState under = f.get(k, lx, fl - 1, lz);
        if (!under.isSolidRender() && under.getFluidState().isEmpty()) f.put(k, lx, fl - 1, lz, Carve.rock(f.y(fl - 1)));
    }

    /**
     * The Delver cairn: three stones stacked on the ground to one side in front of the mouth, an iron spike on top; at an
     * adit a lantern on a post beside it (seen from far at night). Sits on whatever ground is there (the column's top
     * solid block within four of the mouth's floor).
     */
    private static void cairn(Spec s, Frame f, Sink k) {
        int side = s.p(CAIRN) == 0 ? 1 : -1;
        int lx = side * (s.p(W) / 2 + 4), lz = -5;
        int x = f.x(lx, lz), z = f.z(lx, lz);
        if (!k.here(x, z)) return;
        int ground = Integer.MIN_VALUE;
        for (int y = f.y(4); y >= f.y(-4); y--) {
            if (!k.inside(x, y, z)) continue;
            BlockState b = k.get(x, y, z);
            if (b.isSolidRender()) {
                ground = y + 1;
                break;
            }
            if (!b.isAir() && !b.canBeReplaced()) return;   // a tree, a build: no cairn
        }
        if (ground == Integer.MIN_VALUE) return;
        Carve.put(k, x, ground, z, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.UP, true));
        Carve.put(k, x, ground + 1, z, Blocks.COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.UP, true));
        Carve.put(k, x, ground + 2, z, Blocks.IRON_BARS.defaultBlockState());
        if (s.p(FORM) == ADIT) {
            int px = f.x(lx + side, lz + 1), pz = f.z(lx + side, lz + 1);
            if (!k.here(px, pz) || !k.inside(px, ground - 1, pz) || !k.get(px, ground - 1, pz).isSolidRender()) return;
            Carve.put(k, px, ground, pz, Blocks.SPRUCE_FENCE.defaultBlockState());
            Carve.put(k, px, ground + 1, pz, Blocks.SPRUCE_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, false));
            Carve.put(k, px, ground + 2, pz, Blocks.LANTERN.defaultBlockState());
        }
    }
}
