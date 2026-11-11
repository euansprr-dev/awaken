package dev.awaken.delve.cave;

import org.jspecify.annotations.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A mouth (SPEC §34.1): a way into the caves you can see from far off, about one every 200 m of new land. Four forms,
 * picked by the ground:
 * <ul>
 * <li><b>Hill mouth</b>: a dark arch {@code W} wide and {@code H} high cut into a slope, roots hanging over the lip and
 * vines down its face, a throat falling about one in two (a drop of two now and then) narrowing to a passage that meets
 * natural cave air.</li>
 * <li><b>Delver adit</b>: a timbered square mouth in a hillside with old rails, a short drift that runs straight to a
 * lode's heart (iron or copper, the seam showing in the end face) and on into the cave the seam runs along. The first
 * trip's place: the Delver's Pick is stuck in the stone at the drift's end (mark "pick").</li>
 * <li><b>Cleft</b> (a cliff): a slot gorge {@code W} wide cut into the cliff, open to the sky and its walls hung with
 * vines, a stone or two wedged high between them, its floor falling one in three until the rock closes over it and it
 * narrows into a passage that meets cave air.</li>
 * <li><b>Doline</b> (flat ground): a round hole {@code W} across and {@code DROP} deep where the ground fell into a cave,
 * a lip of turf over its edge with roots under it and vines down its walls, the fallen rock lying in a ledge that spirals
 * down to the bottom, and a low way out of its side toward the cave it fell into. Its spec sits at the hole's middle,
 * level with the ground; forward is the way out at the bottom.</li>
 * </ul>
 * Beside both, on open ground in front, a Delver cairn (stacked stones and an iron spike; at an adit a lantern on a
 * post). The mouth breathes mist at dawn and after rain and a draught comes out of it (client, from the place's site).
 * A mouth laid since the plume (p {@link #LOOK}) also wears its ground: rock and scree in place of grass, so it reads as
 * a grey scar on any hillside and no tree roots in front of it (SPEC §34.1). A mouth saved before it keeps what it built.
 *
 * <p>Spec: (x, y, z) the mouth's floor (feet) at the lip, {@code rot} forward = into the hill.
 */
final class Mouth {
    private Mouth() {}

    /** p: form, throat length, its drop, arch width, arch height, the lode's heart (x, y, z), its ore + 1, the cairn's side, its look. */
    static final int FORM = 0, LEN = 1, DROP = 2, W = 3, H = 4, HX = 5, HY = 6, HZ = 7, ORE = 8, CAIRN = 9, LOOK = 10;
    /** p, a joint that winds down to a cave too deep to walk straight to (0 on a straight one and a saved mouth): its circle's radius, signed by the way it turns; how many legs. */
    static final int SWING = 11, LEGS = 12;
    static final int HILL = 0, ADIT = 1, DOLINE = 2, CLEFT = 3;
    /** The portal's depth: the arch (or the timbered frame) before the throat begins. */
    static final int PORTAL = 4;
    /** How far the ground round a doline may rise or fall from its middle (past that it's a hillside, a hill mouth's). */
    static final int TILT = 4;

    static BoundingBox box(Spec s) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        boolean surround = s.p(LOOK) > 0;
        int half = s.p(W) / 2 + (surround ? 14 : 9), back = surround ? FAN + 1 : 10;
        int ax = f.x(-half, -back), az = f.z(-half, -back), bx = f.x(half, s.p(LEN) + 4), bz = f.z(half, s.p(LEN) + 4);
        int y0 = s.y() - s.p(DROP) - 4, y1 = s.y() + Math.max(s.p(H), 5) + 4;
        if (surround) {
            y0 = Math.min(y0, s.y() - FAN - 4);
            y1 += 6;
        }
        if (s.p(FORM) == ADIT && s.p(ORE) != 0) y0 = Math.min(y0, s.p(HY) - 3);
        BoundingBox b = new BoundingBox(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz));
        if (s.p(LEGS) > 1) {
            for (double[] q : way(s)) {
                int x = (int) Math.floor(q[0]), y = (int) Math.floor(q[1]), z = (int) Math.floor(q[2]);
                b = b.encapsulate(new BoundingBox(x - 6, y - 2, z - 6, x + 6, y + 6, z + 6));
            }
        }
        return b;
    }

    /** The most legs a winding joint takes and the widest circle its bends stand on. */
    static final int MOST_LEGS = 6, MOST_SWING = 18;
    /** The sharpest a bend turns: a leg doubling back over the last cuts away its floor, and it can't be climbed. */
    private static final double MOST_TURN = Math.toRadians(110);

    /**
     * How a fall of d over a run of h winds down and stays no steeper than the slope, {swing, legs}: a spiral ramp, its bends
     * on a circle through both ends (radius |swing|, sign the way it turns), the fewest legs and the smallest circle that give
     * the length. Null when none does.
     */
    static int @Nullable [] winding(double h, double d, double slope, int dir) {
        double want = d / slope;
        for (int legs = 3; legs <= MOST_LEGS; legs++) {
            for (int r = Math.max(4, (int) Math.ceil(h / 2 + 0.01)); r <= MOST_SWING; r++) {
                double turn = turned(h, r, dir, legs) / legs;
                if (turn <= MOST_TURN && legs * 2 * r * Math.sin(turn / 2) >= want) return new int[]{dir * r, legs};
            }
        }
        return null;
    }

    /** The angle the way turns through on its circle: from one end round to the other, plus whole turns while no bend passes the limit. */
    private static double turned(double h, double r, int dir, int legs) {
        double off = Math.sqrt(Math.max(0, r * r - h * h / 4)) * dir;
        double a = Math.atan2(-off, -h / 2), b = Math.atan2(-off, h / 2);
        double arc = Math.floorMod(Math.round((b - a) * dir * 1e6), Math.round(Math.PI * 2e6)) / 1e6;
        if (arc < 1e-3) arc = Math.PI * 2;
        return arc + Math.PI * 2 * Math.max(0, Math.floor((legs * MOST_TURN - arc) / (Math.PI * 2)));
    }

    /** The joint's way, from the throat's end to the cave: its corners (x, feet, z), the two ends included. */
    static double[][] way(Spec s) {
        int[] e = end(s);
        return way(e[0] + 0.5, e[1], e[2] + 0.5, s.jx() + 0.5, s.jy(), s.jz() + 0.5, s.p(SWING), Math.max(1, s.p(LEGS)));
    }

    /** As above: the bends on {@link #winding}'s circle, the fall shared by length so every leg is as steep; straight when {@code swing} is 0. */
    static double[][] way(double ax, double ay, double az, double bx, double by, double bz, int swing, int legs) {
        double dx = bx - ax, dz = bz - az, h = Math.hypot(dx, dz);
        double[][] w = new double[legs + 1][];
        double[] along = new double[legs + 1];
        double run = 0;
        double cx = 0, cz = 0, r = Math.max(Math.abs(swing), h / 2 + 0.01), a0 = 0, step = 0;
        int dir = swing < 0 ? -1 : 1;
        if (swing != 0) {
            double off = Math.sqrt(Math.max(0, r * r - h * h / 4)) * dir, fx = h < 1e-6 ? 0 : dx / h, fz = h < 1e-6 ? 1 : dz / h;
            cx = (ax + bx) / 2 - fz * off;
            cz = (az + bz) / 2 + fx * off;
            a0 = Math.atan2(az - cz, ax - cx);
            step = dir * turned(h, r, dir, legs) / legs;
        }
        for (int i = 0; i <= legs; i++) {
            double t = i / (double) legs, a = a0 + step * i;
            boolean end = i == 0 || i == legs || swing == 0;
            w[i] = new double[]{end ? ax + dx * t : cx + Math.cos(a) * r, 0, end ? az + dz * t : cz + Math.sin(a) * r};
            if (i > 0) run += Math.hypot(w[i][0] - w[i - 1][0], w[i][2] - w[i - 1][2]);
            along[i] = run;
        }
        for (int i = 0; i <= legs; i++) w[i][1] = ay + (by - ay) * (run < 1e-6 ? i / (double) legs : along[i] / run);
        return w;
    }

    /** The throat's floor (local, feet) at local z: level through the portal, then down one in two (adit: its own grade). */
    static int floorAt(Spec s, int lz) {
        int L = s.p(LEN), drop = s.p(DROP);
        if (lz <= PORTAL) return 0;
        if (lz >= L) return -drop;
        if (s.p(FORM) == DOLINE) return -drop;
        if (s.p(FORM) == CLEFT) return -(int) Math.floor((lz - PORTAL) * (double) drop / Math.max(1, L - PORTAL));
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
        switch (s.p(FORM)) {
            case ADIT -> adit(s, f, k);
            case DOLINE -> doline(s, f, k);
            case CLEFT -> cleft(s, f, k);
            default -> hill(s, f, k);
        }
        if (s.p(LOOK) > 0) surround(s, f, k);
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
                double[][] w = way(s);
                // the highest leg first (Carve.tunnel), and a saved mouth as it was
                boolean floored = s.p(LEGS) > 0, down = w[0][1] >= w[w.length - 1][1];
                for (int n = 0; n + 1 < w.length; n++) {
                    int i = down ? n : w.length - 2 - n;
                    Carve.tunnel(k, w[i][0], w[i][1], w[i][2], w[i + 1][0], w[i + 1][1], w[i + 1][2], 1.5, 1.5, s.seed() + 5 + i, floored);
                    // a bend widens into a landing, so turning back on yourself is a step and not a squeeze
                    if (i > 0) Carve.blob(k, w[i][0], w[i][1] + 1.2, w[i][2], 2.8, 2.0, 2.8, s.seed() + 40 + i, 0.2, 3.0, (int) Math.floor(w[i][1]));
                }
            }
            k.mark("throat", e[0], e[1], e[2]);
            k.mark("goal", s.jx(), s.jy(), s.jz());
        } else {
            k.mark("goal", e[0], e[1], e[2]);
        }
        f.mark(k, "start", 0, 0, s.p(FORM) == DOLINE ? -(s.p(W) / 2 + 5) : -6);
        f.mark(k, "mouth", 0, 0, 0);
    }

    // ---- the hill mouth -----------------------------------------------------------------------------------------------

    private static void hill(Spec s, Frame f, Sink k) {
        int L = s.p(LEN), w = s.p(W), h = s.p(H);
        long seed = s.seed();
        // the ground in front: open to the sky, a floor to stand on (a lip of rock where the slope fell away)
        approach(s, f, k, w / 2 + 2, h);
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
            // the vines hang from the brow (the new look) or the hill over the lip: only where the hill stands that high
            // in front of the arch, never in the air of a mouth cut into a gentle slope (SPEC §34.1, "Nothing hangs")
            if (Carve.rand(seed, lx, 0, 9) < 0.55 && overLip(f, k, lx, h, -1)) {
                int len = 2 + (int) (Carve.rand(seed, lx, 1, 9) * 4), top = s.p(LOOK) > 0 ? h : h + 1;
                for (int ly = top; ly > top - len; ly--)
                    Carve.vine(k, f.x(lx, -1), f.y(ly), f.z(lx, -1), f.dir(Direction.SOUTH), ly == top);
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
        approach(s, f, k, 3, 5);
        // spoil heaps beside the cutting
        for (int side = -1; side <= 1; side += 2) {
            // each stone on ground (or the shelf) right under it, never hung over a fall in the slope
            for (int i = 0; i < 3; i++)
                if (firm(f, k, side * (4 + i), -1, -3 - i)) f.put(k, side * (4 + i), 0, -3 - i, i == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
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

    // ---- the cleft ----------------------------------------------------------------------------------------------------

    /** Where the cleft's rock closes over it: open to the sky before this, a passage after. */
    static int roofAt(Spec s) {
        return (int) (s.p(LEN) * 0.65);
    }

    private static void cleft(Spec s, Frame f, Sink k) {
        int L = s.p(LEN), w = s.p(W), h = s.p(H), shut = roofAt(s);
        long seed = s.seed();
        approach(s, f, k, w / 2 + 2, 4);
        if (!f.touches(k, -w - 4, -1, w + 4, L + 1)) return;
        for (int lz = 0; lz <= L; lz++) {
            double half = Carve.lerp(lz / (double) L, w / 2.0 + 0.3, 1.0), mid = mid(s, lz);
            int fl = floorAt(s, lz);
            boolean open = lz < shut;
            // open, to the sky; then the roof comes down a step at a time to a passage four high
            int top = open ? h + 3 : fl + 3 + Math.max(0, shut + 3 - lz);
            for (int lx = (int) Math.floor(mid - half - 2); lx <= (int) Math.ceil(mid + half + 2); lx++) {
                double d = Math.abs(lx - mid);
                for (int ly = fl; ly <= top; ly++) {
                    // the walls lean apart a little toward the sky, and are rough
                    double lean = open ? Math.max(0, (ly - fl - 3) * 0.05) : 0;
                    if (d <= half + lean + Carve.rand(seed, lx, ly, lz) * 0.5 - 0.25) f.air(k, lx, ly, lz);
                }
                if (d <= half + 0.5) floor(f, k, lx, fl, lz);
            }
        }
        // vines down both walls of the open slot, hanging roots where the rock closes over
        for (int lz = 1; lz < shut; lz++) {
            for (int side = -1; side <= 1; side += 2) {
                if (Carve.rand(seed, side, lz, 21) > 0.55) continue;
                int fl = floorAt(s, lz), from = fl + 3 + (int) (Carve.rand(seed, side, lz, 22) * Math.max(1, h - 6));
                Direction toWall = f.dir(side > 0 ? Direction.EAST : Direction.WEST);
                for (int ly = from; ly > fl; ly--) {
                    int lx = wallCell(f, k, mid(s, lz), side, ly, lz);
                    if (lx == Integer.MIN_VALUE) break;
                    Carve.vine(k, f.x(lx, lz), f.y(ly), f.z(lx, lz), toWall);
                }
            }
        }
        for (int lz = shut; lz <= shut + 3 && lz <= L; lz++) {
            int fl = floorAt(s, lz), top = fl + 3 + Math.max(0, shut + 3 - lz);
            for (int lx = -2; lx <= 2; lx++) Carve.roots(k, f.x(lx, lz), f.y(top), f.z(lx, lz));
        }
        // a stone or two wedged across the slot high over the floor
        for (int i = 1; i <= 2; i++) {
            int lz = shut * i / 3, fl = floorAt(s, lz), ly = fl + 6 + (int) (Carve.rand(seed, i, 0, 23) * 3);
            if (ly > h || lz < 2) continue;
            // only where rock holds it on both sides within a few blocks (never a bridge out into the open)
            int m = (int) Math.round(mid(s, lz)), a = wallCell(f, k, m, -1, ly, lz), b = wallCell(f, k, m, 1, ly, lz);
            if (a == Integer.MIN_VALUE || b == Integer.MIN_VALUE || b - a > 4) continue;
            for (int lx = a; lx <= b; lx++) f.put(k, lx, ly, lz, (lx & 1) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
        }
        // moss on the floor
        for (int lz = 0; lz <= L; lz++) {
            int fl = floorAt(s, lz), m = (int) Math.round(mid(s, lz));
            for (int lx = m - 1; lx <= m + 1; lx++) {
                if (Carve.rand(seed, lx, lz, 24) < 0.3 && Carve.open(k, f.x(lx, lz), f.y(fl), f.z(lx, lz)) && Carve.solidAt(k, f.x(lx, lz), f.y(fl - 1), f.z(lx, lz)))
                    f.put(k, lx, fl, lz, Blocks.MOSS_CARPET.defaultBlockState());
            }
        }
    }

    /** The slot's middle at lz: it wanders a little once inside, never at the lip. */
    private static double mid(Spec s, int lz) {
        return (Carve.noise(s.seed(), lz, 0, 0, 6) - 0.5) * 2.4 * Math.min(1, lz / 6.0);
    }

    /** The last air cell going out from the middle on one side at (ly, lz), with rock beyond it; or MIN_VALUE. */
    private static int wallCell(Frame f, Sink k, double mid, int side, int ly, int lz) {
        int lx = (int) Math.round(mid);
        if (!f.inside(k, lx, ly, lz) || !f.get(k, lx, ly, lz).isAir()) return Integer.MIN_VALUE;
        for (int i = 0; i < 6; i++) {
            int nx = lx + side;
            if (!f.inside(k, nx, ly, lz)) return Integer.MIN_VALUE;
            BlockState n = f.get(k, nx, ly, lz);
            if (n.isSolidRender()) return lx;
            if (!n.isAir()) return Integer.MIN_VALUE;
            lx = nx;
        }
        return Integer.MIN_VALUE;
    }

    // ---- the doline ---------------------------------------------------------------------------------------------------

    /** The ledge's turns: the fewest half-turns-and-more that keep it no steeper than one down in 1.6 along it. */
    static int turns(int r, int drop) {
        double R = r - 0.7;
        int n = 0;
        while (Math.PI * (2 * n + 1) * R < drop * 1.6 && n < 6) n++;
        return n;
    }

    private static void doline(Spec s, Frame f, Sink k) {
        int r = s.p(W) / 2, drop = s.p(DROP), L = s.p(LEN);
        long seed = s.seed();
        if (!f.touches(k, -r - 3, -r - 3, r + 3, L + 1)) return;
        double R = r - 0.7, a0 = -Math.PI / 2;
        int n = turns(r, drop);
        double sweep = Math.PI * (2 * n + 1);
        for (int lx = -r - 2; lx <= r + 2; lx++) {
            for (int lz = -r - 2; lz <= r + 2; lz++) {
                double d = Math.sqrt(lx * lx + lz * lz), a = Math.atan2(lz, lx);
                // rough round the edge, the same all the way down; the turf at the top overhangs by one
                double rr = r + 0.5 + (Carve.noise(seed, Math.cos(a) * 3, Math.sin(a) * 3, 0, 2) - 0.5) * 1.2;
                if (d > rr) continue;
                for (int ly = -drop; ly <= (s.p(LOOK) > 0 ? TILT + 2 : 3); ly++) {
                    if (ly == -1 && d > rr - 0.9) continue;
                    f.air(k, lx, ly, lz);
                }
                // the floor: the fallen rock, earth and moss
                double q = Carve.rand(seed, lx, lz, 31);
                BlockState bed = q < 0.35 ? Carve.rubble(f.y(-drop - 1)) : q < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState()
                        : q < 0.8 ? Blocks.MOSS_BLOCK.defaultBlockState() : Carve.rock(f.y(-drop - 1));
                f.put(k, lx, -drop - 1, lz, bed);
                if (bed.is(Blocks.MOSS_BLOCK) && Carve.rand(seed, lx, lz, 32) < 0.5) f.put(k, lx, -drop, lz, Blocks.MOSS_CARPET.defaultBlockState());
                // the ledge: one or more turns of fallen rock down the wall from the near side to the way out
                if (d < R - 0.8) continue;
                double u = ((a - a0) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                for (int t = 0; t <= n; t++) {
                    double th = u + 2 * Math.PI * t;
                    if (th > sweep) break;
                    // near the top it reaches right out to the lip, so the first step down is off solid ground
                    if (d > R + 0.8 && th > 1.0) continue;
                    int fy = -(int) Math.round(th / sweep * drop);
                    double g = Carve.rand(seed, lx, fy, lz);
                    f.put(k, lx, fy - 1, lz, g < 0.4 ? Carve.rubble(f.y(fy - 1)) : g < 0.7 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : g < 0.85 ? Blocks.COARSE_DIRT.defaultBlockState() : Carve.rock(f.y(fy - 1)));
                }
            }
        }
        // roots under the turf's lip, vines down the walls
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            double cx = Math.cos(a), cz = Math.sin(a);
            int wx = Integer.MIN_VALUE, wz = 0;
            for (double rad = r - 1; rad <= r + 2; rad += 0.5) {
                int lx = (int) Math.round(cx * rad), lz = (int) Math.round(cz * rad);
                if (f.inside(k, lx, -2, lz) && f.get(k, lx, -2, lz).isAir()) {
                    wx = lx;
                    wz = lz;
                }
            }
            if (wx == Integer.MIN_VALUE) continue;
            Carve.roots(k, f.x(wx, wz), f.y(-2), f.z(wx, wz));
            if (Carve.rand(seed, i, 0, 33) > 0.5) continue;
            Direction local = Math.abs(cx) > Math.abs(cz) ? (cx > 0 ? Direction.EAST : Direction.WEST) : (cz > 0 ? Direction.SOUTH : Direction.NORTH);
            int sx = wx + local.getStepX(), sz = wz + local.getStepZ();
            if (!f.inside(k, sx, -3, sz) || !f.get(k, sx, -3, sz).isSolidRender()) continue;
            int len = 3 + (int) (Carve.rand(seed, i, 1, 33) * (drop / 2.0));
            for (int ly = -3; ly > -3 - len && ly > -drop; ly--) Carve.vine(k, f.x(wx, wz), f.y(ly), f.z(wx, wz), f.dir(local));
        }
        // the way out at the bottom, toward the cave it fell into
        f.carve(k, -1, -drop, 0, 1, -drop + 2, L);
        for (int lz = 0; lz <= L; lz++) for (int lx = -1; lx <= 1; lx++) floor(f, k, lx, -drop, lz);
    }

    // ---- the worn ground (SPEC §34.1): what you notice from far ----------------------------------------------------------

    /** How far out the bare ground runs in front of a mouth. */
    static final int FAN = 16;

    private static void surround(Spec s, Frame f, Sink k) {
        if (s.p(FORM) == DOLINE) {
            rim(s, f, k);
            return;
        }
        fan(s, f, k);
        if (s.p(FORM) == HILL) collar(s, f, k);
    }

    /** The ground worn bare in front of a mouth: rock and scree in place of grass, widening and ragged, no tree roots on it. */
    private static void fan(Spec s, Frame f, Sink k) {
        int w = s.p(W);
        long seed = s.seed() + 40;
        for (int lz = -FAN; lz < 0; lz++) {
            double hw = w / 2.0 + 2.5 - lz * 0.45;
            int lim = (int) Math.ceil(hw) + 1;
            for (int lx = -lim; lx <= lim; lx++) {
                int x = f.x(lx, lz), z = f.z(lx, lz);
                if (!k.here(x, z)) continue;
                double rag = (Carve.noise(seed, x, 0, z, 5) - 0.5) * 3.5;
                if (Math.abs(lx) > hw + rag) continue;
                // a ragged end, thinner the farther out
                if (lz < -FAN + 5 && Carve.rand(seed, lx, lz, 3) > (FAN + lz) / 5.0) continue;
                worn(k, x, s.y() + 10, s.y() - FAN - 2, z, seed);
            }
        }
    }

    /**
     * A hill mouth's collar: the soil round the arch (within three blocks of it, the first three blocks into the hill)
     * turned to bare rock, with a rough brow leaning out over the top of the arch. A dark hole in a grey ring.
     */
    private static void collar(Spec s, Frame f, Sink k) {
        int w = s.p(W), h = s.p(H);
        long seed = s.seed() + 41;
        double half = w / 2.0 + 0.5;
        for (int lz = 0; lz <= 2; lz++) {
            for (int lx = -(w / 2 + 4); lx <= w / 2 + 4; lx++) {
                for (int ly = -1; ly <= h + 4; ly++) {
                    if (!f.inside(k, lx, ly, lz)) continue;
                    double u = lx / (half + 3.2), v = ly / (double) (h + 3);
                    if (u * u + v * v > 1) continue;
                    double in = lx / half, up = ly / (double) h;
                    if (in * in + up * up <= 1) continue;
                    BlockState b = f.get(k, lx, ly, lz);
                    if (soil(b)) f.put(k, lx, ly, lz, rockFace(seed, f.x(lx, lz), f.y(ly), f.z(lx, lz)));
                }
            }
        }
        // the brow: two courses of rock out over the arch's top, roots under it. Only where the hill itself stands at the
        // brow's top course in that column: on a gentle slope the arch's top is in the open and a brow there hung a dozen
        // blocks over the grass (SPEC §34.1, "Nothing hangs"); the outer course only in front of an inner one.
        for (int lx = -(w / 2 + 1); lx <= w / 2 + 1; lx++) {
            for (int lz = -1; lz >= -2; lz--) {
                if (lz == -2 && Carve.rand(seed, lx, lz, 5) < 0.45) continue;
                if (!overLip(f, k, lx, h, lz)) break;
                for (int ly = h + 1; ly <= h + 2; ly++) f.put(k, lx, ly, lz, rockFace(seed, f.x(lx, lz), f.y(ly), f.z(lx, lz)));
                Carve.roots(k, f.x(lx, lz), f.y(h), f.z(lx, lz));
            }
        }
    }

    /** A doline's rim: the ground fallen away round the hole worn to rock and scree, and a few boulders that didn't fall in. */
    private static void rim(Spec s, Frame f, Sink k) {
        int r = s.p(W) / 2, out = r + 8;
        long seed = s.seed() + 42;
        for (int lx = -out; lx <= out; lx++) {
            for (int lz = -out; lz <= out; lz++) {
                double d = Math.sqrt(lx * lx + lz * lz), a = Math.atan2(lz, lx);
                if (d <= r + 1) continue;
                double edge = r + 3.5 + 3.5 * Carve.noise(seed, Math.cos(a) * 4, Math.sin(a) * 4, 0, 2);
                if (d > edge) continue;
                int x = f.x(lx, lz), z = f.z(lx, lz);
                if (k.here(x, z)) worn(k, x, s.y() + TILT + 2, s.y() - TILT - 2, z, seed);
            }
        }
        // the boulders: leaning stones of the rock round the hole, a block or two off its edge
        for (int i = 0; i < 5; i++) {
            double a = Carve.rand(seed, i, 0, 7) * Math.PI * 2, d = r + 2.5 + Carve.rand(seed, i, 1, 7) * 4;
            int lx = (int) Math.round(Math.cos(a) * d), lz = (int) Math.round(Math.sin(a) * d);
            int x = f.x(lx, lz), z = f.z(lx, lz);
            if (!k.here(x, z)) continue;
            int g = ground(k, x, s.y() + TILT + 2, s.y() - TILT - 2, z);
            if (g == Integer.MIN_VALUE) continue;
            int tall = 1 + (int) (Carve.rand(seed, i, 2, 7) * 2.5);
            for (int y = 0; y < tall; y++) if (k.inside(x, g + y, z)) k.set(x, g + y, z, rockFace(seed, x, g + y, z));
        }
    }

    /** The column's top ground block over y0 to y1 turned to rock or scree (two deep), its small plants cleared; a log or a build stops it. */
    private static void worn(Sink k, int x, int y1, int y0, int z, long seed) {
        for (int y = Math.min(y1, k.maxY()); y >= Math.max(y0, k.minY()); y--) {
            BlockState b = k.get(x, y, z);
            if (b.isAir()) continue;
            if (b.canBeReplaced()) {   // grass and the like, nothing else (a replaceable with a fluid in it stays)
                if (b.getFluidState().isEmpty()) k.set(x, y, z, Carve.AIR);
                continue;
            }
            if (!soil(b)) return;
            // sand stays a sand country's rock; everything else wears down to the grey under it
            boolean sand = b.is(BlockTags.SAND);
            boolean red = b.is(Blocks.RED_SAND);
            for (int i = 0; i < 2 && y - i >= k.minY(); i++) {
                if (i > 0 && !soil(k.get(x, y - i, z))) break;
                k.set(x, y - i, z, sand ? (red ? Blocks.RED_SANDSTONE : Carve.rand(seed, x, y, z) < 0.4 ? Blocks.SMOOTH_SANDSTONE : Blocks.SANDSTONE).defaultBlockState() : scree(seed, x, y - i, z));
            }
            return;
        }
    }

    /** The feet's height on a column's top ground within y1..y0, or MIN_VALUE. */
    private static int ground(Sink k, int x, int y1, int y0, int z) {
        for (int y = Math.min(y1, k.maxY()); y >= Math.max(y0, k.minY()); y--) {
            BlockState b = k.get(x, y, z);
            if (b.isAir() || b.canBeReplaced()) continue;
            return b.isSolidRender() ? y + 1 : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    /** Soil a mouth's wear strips away, not rock or anything built. */
    private static boolean soil(BlockState b) {
        return b.is(BlockTags.DIRT) || b.is(BlockTags.SAND) || b.is(Blocks.GRASS_BLOCK) || b.is(Blocks.SNOW_BLOCK) || b.is(Blocks.POWDER_SNOW) || b.is(Blocks.CLAY);
    }

    /** A worn ground block: scree where the ground has been spilling out, bare rock else, in soft patches. */
    private static BlockState scree(long seed, int x, int y, int z) {
        double n = Carve.noise(seed, x, 0, z, 4.5), q = Carve.rand(seed, x, y, z);
        if (n < 0.34) return q < 0.5 ? Blocks.GRAVEL.defaultBlockState() : Carve.rubble(y);
        if (n < 0.5) return q < 0.5 ? Blocks.TUFF.defaultBlockState() : Carve.rock(y);
        return n < 0.7 ? Carve.rock(y) : q < 0.5 ? Blocks.ANDESITE.defaultBlockState() : Carve.rock(y);
    }

    /** A cell of the rock face (the collar, the brow, a boulder), a little mossy now and then. */
    private static BlockState rockFace(long seed, int x, int y, int z) {
        double q = Carve.rand(seed, x, y, z);
        return q < 0.08 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : q < 0.28 ? Blocks.ANDESITE.defaultBlockState() : q < 0.42 ? Blocks.TUFF.defaultBlockState() : Carve.rock(y);
    }

    // ---- shared -------------------------------------------------------------------------------------------------------

    /** The ground in front of the mouth: open air over it, a floor where the slope fell away (half-width hw). */
    private static void approach(Spec s, Frame f, Sink k, int hw, int h) {
        for (int lz = -8; lz < 0; lz++) {
            int w = hw + (-lz) / 3;
            f.carve(k, -w, 0, lz, w, h + 1, lz);
            for (int lx = -w; lx <= w; lx++) {
                if (!f.inside(k, lx, -1, lz)) continue;
                // the shelf's outer edge breaks up: spoil that spilled out, not a slab laid down
                if (s.p(LOOK) > 0 && Math.abs(lx) >= w - 1 && Carve.rand(s.seed(), lx, lz, 44) < 0.45) continue;
                // a dip of one is filled; where the slope falls away further the land's own slope is the way up, not a
                // shelf over the drop (SPEC §34.1, "Nothing hangs")
                if (!f.get(k, lx, -1, lz).isSolidRender() && firm(f, k, lx, -2, lz)) f.put(k, lx, -1, lz, Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
    }

    /**
     * Whether the hill itself stands at a brow's top course (local height {@code h + 2}, over an arch {@code h} high) in
     * the column (lx, lz) in front of the lip. Read from that column alone, which the mouth leaves alone until the brow:
     * the chunk that writes the column decides, so every chunk agrees.
     */
    private static boolean overLip(Frame f, Sink k, int lx, int h, int lz) {
        return firm(f, k, lx, h + 2, lz);
    }

    /** A whole block at (lx, ly, lz) in this chunk: something laid there rests on it. */
    private static boolean firm(Frame f, Sink k, int lx, int ly, int lz) {
        return f.inside(k, lx, ly, lz) && f.get(k, lx, ly, lz).isSolidRender();
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
     * solid block within four of the mouth's floor). The new look heaps a base of stones and stacks it taller: you see the
     * spike over the grass from well off.
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
        if (s.p(LOOK) > 0) {
            // the heaped base, then a tall stack on it
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) == 2 && Carve.rand(s.seed(), dx, 11, dz) < 0.5) continue;
                    if (k.inside(x + dx, ground - 1, z + dz) && k.inside(x + dx, ground, z + dz) && k.get(x + dx, ground - 1, z + dz).isSolidRender() && k.get(x + dx, ground, z + dz).isAir())
                        Carve.put(k, x + dx, ground, z + dz, (dx == 0 && dz == 0 || Carve.rand(s.seed(), dx, 12, dz) < 0.3 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
                }
            }
            int tall = 4 + (int) Math.floorMod(Carve.hash(s.seed(), 0, 13, 0), 3L);
            for (int i = 1; i <= tall; i++) Carve.put(k, x, ground + i, z, (i % 2 == 1 ? Blocks.COBBLESTONE_WALL : Blocks.MOSSY_COBBLESTONE_WALL).defaultBlockState().setValue(WallBlock.UP, true));
            Carve.put(k, x, ground + tall + 1, z, Blocks.IRON_BARS.defaultBlockState());
        } else {
            Carve.put(k, x, ground, z, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.UP, true));
            Carve.put(k, x, ground + 1, z, Blocks.COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.UP, true));
            Carve.put(k, x, ground + 2, z, Blocks.IRON_BARS.defaultBlockState());
        }
        if (s.p(FORM) == ADIT) {
            int px = f.x(lx + side, lz + 1), pz = f.z(lx + side, lz + 1);
            if (!k.here(px, pz) || !k.inside(px, ground - 1, pz) || !k.get(px, ground - 1, pz).isSolidRender()) return;
            Carve.put(k, px, ground, pz, Blocks.SPRUCE_FENCE.defaultBlockState());
            Carve.put(k, px, ground + 1, pz, Blocks.SPRUCE_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, false));
            Carve.put(k, px, ground + 2, pz, Blocks.LANTERN.defaultBlockState());
        }
    }
}
