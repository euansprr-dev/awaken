package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.SheetLand;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * A dungeon crawl (SPEC §50.9): solid rock with a run hollowed out of it, part by part. A sheet places the parts by
 * name ({@link Cave}, {@link Tunnel}, {@link Arena}, {@link Shaft}) and joins them at the same height; the land that
 * extends this says only what the rock is made of, what the floors are, and what grows on the walls ({@link #adorn}),
 * which is where a crawl gets a look of its own. Floors are always walkable: a way that climbs does it in half steps.
 *
 * <p>Nothing here can float: what isn't hollow is rock from the world's floor up, and a part only ever takes rock away
 * or lays a floor on rock.
 */
public abstract class CrawlLand<M extends Mats> extends SheetLand<M> {
    /** What a part answers for a column it doesn't cover. */
    public static final int NONE = Integer.MIN_VALUE;

    /**
     * One hollow. For each column it covers: twice the height your feet are at ({@code feet2}; odd means a half step, a
     * slab under you), and the first solid cell over your head ({@code roof}). A part with more than one floor in a
     * column ({@link Shaft}) draws itself.
     */
    public abstract static class Part {
        public int x0, z0, x1, z1;
        public String name = "";

        public abstract int feet2(int x, int z);

        public abstract int roof(int x, int z);

        final boolean near(V v) {
            return x1 >= v.x0 - 1 && x0 <= v.x0 + 16 && z1 >= v.z0 - 1 && z0 <= v.z0 + 16;
        }

        /** The first cell of air over the floor: the cell your feet are in, or the one above a half step. */
        public static int airFrom(int feet2) {
            return Math.floorDiv(feet2, 2) + (feet2 & 1);
        }

        /** Whether a cell is this part's air. */
        public boolean open(int x, int y, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return false;
            int f = feet2(x, z);
            return f != NONE && y >= airFrom(f) && y < roof(x, z);
        }

        /** How many cells of water stand on this column's floor (a pool at the bottom of a pit). */
        public int wet(int x, int z) {
            return 0;
        }

        /** Takes the rock out, and stands water in what it says is a pool. */
        void carve(V v) {
            for (int x = Math.max(x0, v.x0 - 1); x <= Math.min(x1, v.x0 + 16); x++) for (int z = Math.max(z0, v.z0 - 1); z <= Math.min(z1, v.z0 + 16); z++) {
                int f = feet2(x, z);
                if (f == NONE) continue;
                int from = Math.floorDiv(f, 2), wet = wet(x, z);
                for (int y = from, top = roof(x, z); y < top; y++) v.set(x, y, z, y < from + wet ? WATER : null);
            }
        }

        /** Lays the floor, only ever on rock: where two parts meet at different heights the lower one's air wins and no shelf is left sticking out. */
        <M extends Mats> void lay(V v, CrawlLand<M> land, M m) {
            for (int x = Math.max(x0, v.x0 - 1); x <= Math.min(x1, v.x0 + 16); x++) for (int z = Math.max(z0, v.z0 - 1); z <= Math.min(z1, v.z0 + 16); z++) {
                int f = feet2(x, z);
                if (f == NONE) continue;
                int y = Math.floorDiv(f, 2);
                if (v.get(x, y - 1, z) == null) continue;
                v.set(x, y - 1, z, land.floor(m, this, x, y - 1, z));
                if ((f & 1) != 0 && v.get(x, y, z) == null) v.set(x, y, z, land.step(m));
            }
        }
    }

    /** A room with no straight edge: an oval whose rim wanders, a level floor, a low dome that isn't smooth. */
    public static class Cave extends Part {
        public final int cx, cz, feet, height;
        public final double rx, rz;
        /** How far the rim wanders in and out, as a share of the radius: about 0.16 for something dug, 0 for something built. */
        public final double rim;
        final SimplexNoise n;

        public Cave(String name, int cx, int feet, int cz, double rx, double rz, int height, long seed) {
            this(name, cx, feet, cz, rx, rz, height, 0.16, seed);
        }

        public Cave(String name, int cx, int feet, int cz, double rx, double rz, int height, double rim, long seed) {
            this.rim = rim;
            this.name = name;
            this.cx = cx;
            this.cz = cz;
            this.feet = feet;
            this.rx = rx;
            this.rz = rz;
            this.height = height;
            n = dev.awaken.gates.realms.kit.N.noise(seed);
            x0 = (int) Math.floor(cx - rx * 1.2) - 1;
            x1 = (int) Math.ceil(cx + rx * 1.2) + 1;
            z0 = (int) Math.floor(cz - rz * 1.2) - 1;
            z1 = (int) Math.ceil(cz + rz * 1.2) + 1;
        }

        /** How far out a column is: 0 at the middle, 1 at the rim. */
        public double out(int x, int z) {
            double dx = (x - cx) / rx, dz = (z - cz) / rz;
            return Math.sqrt(dx * dx + dz * dz) / (1 + rim * n.get(x / 7.0, z / 7.0));
        }

        @Override
        public int feet2(int x, int z) {
            return out(x, z) < 1 ? 2 * feet : NONE;
        }

        @Override
        public int roof(int x, int z) {
            double d = Math.min(1, out(x, z));
            return feet + Math.max(3, 2 + (int) Math.round((height - 2) * Math.pow(1 - d * d, 0.55) + 1.1 * Math.min(1, rim * 8) * n.get(x / 4.0 + 40, z / 4.0)));
        }
    }

    /** A round room to fight in: a cave with a cleaner rim and a ring of pillars left standing a little over half way out, for cover. */
    public static final class Arena extends Cave {
        final int pillars;
        final double turn;

        public Arena(String name, int cx, int feet, int cz, double r, int height, int pillars, long seed) {
            this(name, cx, feet, cz, r, height, pillars, 0.06, seed);
        }

        public Arena(String name, int cx, int feet, int cz, double r, int height, int pillars, double rim, long seed) {
            super(name, cx, feet, cz, r, r, height, rim, seed);
            this.pillars = pillars;
            turn = (seed & 63) / 64.0 * Math.PI;
        }

        /** Whether a column is one of the pillars. */
        public boolean pillar(int x, int z) {
            for (int i = 0; i < pillars; i++) {
                double a = turn + i * Math.PI * 2 / pillars, px = cx + Math.cos(a) * rx * 0.56, pz = cz + Math.sin(a) * rz * 0.56;
                if ((x - px) * (x - px) + (z - pz) * (z - pz) < 2.6) return true;
            }
            return false;
        }

        @Override
        public int feet2(int x, int z) {
            return out(x, z) < 1 && !pillar(x, z) ? 2 * feet : NONE;
        }
    }

    /**
     * A way from one point to another, level or climbing, straight but never square: round ends, an arched roof, sides
     * that wander a little. It climbs in half steps, so it can rise no faster than one in two.
     */
    public static final class Tunnel extends Part {
        final double ax, az, bx, bz, len, half;
        final int ay, by, height;
        /** How far its sides wander: about 0.4 for something dug, 0 for something built (then it is exactly as wide as it says). */
        final double wander;
        final SimplexNoise n;

        public Tunnel(String name, int ax, int ay, int az, int bx, int by, int bz, double half, int height, long seed) {
            this(name, ax, ay, az, bx, by, bz, half, height, 0.4, seed);
        }

        public Tunnel(String name, int ax, int ay, int az, int bx, int by, int bz, double half, int height, double wander, long seed) {
            this.wander = wander;
            this.name = name;
            this.ax = ax;
            this.az = az;
            this.bx = bx;
            this.bz = bz;
            this.ay = ay;
            this.by = by;
            this.half = half;
            this.height = height;
            len = Math.max(1, Math.hypot(bx - ax, bz - az));
            if (Math.abs(by - ay) > len / 2) throw new IllegalArgumentException("tunnel " + name + " climbs " + Math.abs(by - ay) + " in " + (int) len + ": steeper than one in two, nobody could walk it");
            n = dev.awaken.gates.realms.kit.N.noise(seed);
            int pad = (int) Math.ceil(half) + 2;
            x0 = Math.min(ax, bx) - pad;
            x1 = Math.max(ax, bx) + pad;
            z0 = Math.min(az, bz) - pad;
            z1 = Math.max(az, bz) + pad;
        }

        private double along(int x, int z) {
            return Math.max(0, Math.min(1, ((x - ax) * (bx - ax) + (z - az) * (bz - az)) / (len * len)));
        }

        private double off(int x, int z, double t) {
            return Math.hypot(x - (ax + (bx - ax) * t), z - (az + (bz - az) * t));
        }

        @Override
        public int feet2(int x, int z) {
            double t = along(x, z);
            return off(x, z, t) <= half + wander * n.get(x / 5.0, z / 5.0) ? (int) Math.round(2 * (ay + (by - ay) * t)) : NONE;
        }

        @Override
        public int roof(int x, int z) {
            double t = along(x, z), d = Math.min(1, off(x, z, t) / half);
            int f = (int) Math.round(2 * (ay + (by - ay) * t));
            return airFrom(f) + Math.max(2, height - (int) Math.round(d * d * 1.6));
        }
    }

    /**
     * A round shaft with a ledge winding down its wall, a pool at the bottom to fall into, and the ledge running on down
     * into the pool so there is always a way out of it. The ledge starts at the north side at {@code top} and turns
     * through east, south and west, dropping {@code perTurn} each time round; it can be broken at any quarter point
     * (a gap three cells long, straight along x or z, which a sprint clears going down).
     */
    public static final class Shaft extends Part {
        public final int cx, cz, top, bottom, perTurn, drop, water;
        /** The side the ledge starts on: 0 north, 1 east, 2 south, 3 west. It always turns north, east, south, west. */
        public final int start;
        public final double r, ledge;
        final double[] gaps;
        final SimplexNoise n;

        /**
         * @param top    your feet where the ledge starts
         * @param drop   how far the ledge falls in all (it ends under the pool's surface)
         * @param bottom your feet on the pool's bed
         * @param water  how deep the pool is
         * @param gaps   where the ledge is broken, in turns from its start (0.25 is the east side of the first turn)
         */
        public Shaft(String name, int cx, int top, int cz, double r, double ledge, int perTurn, int drop, int bottom, int water, long seed, double... gaps) {
            this(name, 0, cx, top, cz, r, ledge, perTurn, drop, bottom, water, seed, gaps);
        }

        public Shaft(String name, int start, int cx, int top, int cz, double r, double ledge, int perTurn, int drop, int bottom, int water, long seed, double... gaps) {
            this.start = start;
            this.name = name;
            this.cx = cx;
            this.cz = cz;
            this.top = top;
            this.r = r;
            this.ledge = ledge;
            this.perTurn = perTurn;
            this.drop = drop;
            this.bottom = bottom;
            this.water = water;
            this.gaps = gaps;
            if (perTurn > Math.PI * (r - ledge)) throw new IllegalArgumentException("shaft " + name + " drops " + perTurn + " a turn on a ledge " + (int) (r - ledge) + " out: steeper than one in two");
            n = dev.awaken.gates.realms.kit.N.noise(seed);
            x0 = cx - (int) Math.ceil(r) - 2;
            x1 = cx + (int) Math.ceil(r) + 2;
            z0 = cz - (int) Math.ceil(r) - 2;
            z1 = cz + (int) Math.ceil(r) + 2;
        }

        private double far(int x, int z) {
            return Math.hypot(x - cx, z - cz);
        }

        private boolean in(int x, int z) {
            return far(x, z) < r + 0.5 * n.get(x / 5.0, z / 5.0);
        }

        /** How far round from the north side a column is, going through east: 0 to 1. */
        private double round(int x, int z) {
            double a = Math.atan2(x - cx, -(z - cz)) / (Math.PI * 2) - start * 0.25;
            return a - Math.floor(a);
        }

        /** Twice your feet's height on the ledge's k-th time past this column, or NONE where it has ended, is broken, or this column isn't on the ledge. */
        public int ledge2(int x, int z, int k) {
            double d = far(x, z);
            if (d < r - ledge || !in(x, z)) return NONE;
            // the ledge starts with a short level landing on the far side of its first cell, so the way in opens onto floor
            double turns = round(x, z) + k;
            if (turns < -0.09) return NONE;
            double fall = Math.max(0, turns) * perTurn;
            if (fall > drop) return NONE;
            for (double g : gaps) {
                if (Math.abs(turns - g) > 0.1) continue;
                int quarter = Math.floorMod((int) Math.round(g * 4) + start, 4);
                int across = quarter % 2 == 0 ? x - cx : z - cz;
                if (Math.abs(across) <= 1) return NONE;
            }
            return 2 * top - (int) Math.round(2 * fall);
        }

        public int dome(int x, int z) {
            double d = Math.min(1, far(x, z) / r);
            return top + 7 + (int) Math.round(6 * Math.sqrt(1 - d * d));
        }

        @Override
        public int feet2(int x, int z) {
            return in(x, z) ? 2 * bottom : NONE;
        }

        @Override
        public int roof(int x, int z) {
            return dome(x, z);
        }

        /** The shaft is air but for its ledge: two cells thick under your feet, and the half step where it has one. */
        @Override
        public boolean open(int x, int y, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1 || !in(x, z) || y < bottom || y >= dome(x, z)) return false;
            for (int k = -1; k * perTurn <= drop; k++) {
                int f = ledge2(x, z, k);
                if (f == NONE) continue;
                int base = Math.floorDiv(f, 2);
                if (y == base - 1 || y == base - 2 || y == base && (f & 1) != 0) return false;
            }
            return true;
        }

        @Override
        void carve(V v) {
            for (int x = Math.max(x0, v.x0 - 1); x <= Math.min(x1, v.x0 + 16); x++) for (int z = Math.max(z0, v.z0 - 1); z <= Math.min(z1, v.z0 + 16); z++) {
                if (!in(x, z)) continue;
                for (int y = bottom, roof = dome(x, z); y < roof; y++) v.set(x, y, z, y < bottom + water ? WATER : null);
            }
        }

        @Override
        <M extends Mats> void lay(V v, CrawlLand<M> land, M m) {
            for (int x = Math.max(x0, v.x0 - 1); x <= Math.min(x1, v.x0 + 16); x++) for (int z = Math.max(z0, v.z0 - 1); z <= Math.min(z1, v.z0 + 16); z++) {
                if (!in(x, z)) continue;
                v.set(x, bottom - 1, z, land.floor(m, this, x, bottom - 1, z));
                for (int k = -1; ; k++) {
                    if (k * perTurn > drop) break;
                    int f = ledge2(x, z, k);
                    if (f == NONE) continue;
                    int y = Math.floorDiv(f, 2);
                    // two thick, so it reads as a ledge and not a plank; where the land says the floor is thin it is one, with no step on it
                    boolean thin = land.thin(this, x, y - 1, z);
                    v.set(x, y - 1, z, land.floor(m, this, x, y - 1, z));
                    if (!thin) v.set(x, y - 2, z, land.rock(m, x, y - 2, z));
                    if ((f & 1) != 0 && !thin) v.set(x, y, z, land.step(m));
                }
            }
        }

        /** Where to stand on the ledge a given part of a turn from its start, midway across it: x, feet, z. */
        public int[] stand(double turns) {
            double a = (turns + start * 0.25) * Math.PI * 2, d = r - ledge / 2;
            int x = cx + (int) Math.round(Math.sin(a) * d), z = cz - (int) Math.round(Math.cos(a) * d);
            return new int[]{x, top - (int) Math.floor(turns * perTurn), z};
        }
    }

    /**
     * A chasm crossed on pillars (SPEC §50.9): a long room with no floor but a pool far below, a shelf at each end, and a row
     * of flat-topped pillars between them, two or three cells apart, each a sprint from the last and never higher than it.
     * It runs straight along x or z. A ramp down one wall goes from the shelf you start on into the pool, so whoever falls
     * (and isn't caught) can walk back up and try again; there is no way up at the far end but across.
     */
    public static final class Chasm extends Part {
        public final int ax, az, dx, dz, half, top, depth, height, length, exitFeet, exitFrom;
        final int[] start, len, off, down;

        /**
         * @param ax,az  where the first shelf begins, {@code top} your feet on it
         * @param dx,dz  the way it runs: one of them 1 or -1, the other 0
         * @param length about how long to make it (it ends where its last pillar leaves room for the far shelf)
         * @param fall   how far the pillars step down in all, at most
         */
        public Chasm(String name, int ax, int top, int az, int dx, int dz, int length, int half, int height, int depth, int fall, long seed) {
            this.name = name;
            this.ax = ax;
            this.az = az;
            this.dx = dx;
            this.dz = dz;
            this.half = half;
            this.top = top;
            this.depth = depth;
            this.height = height;
            java.util.Random r = new java.util.Random(seed);
            List<int[]> made = new ArrayList<>();
            int pos = 4, gap = 2, lateral = r.nextInt(3) - 1, low = 0;
            while (true) {
                // a gap of three wants a run-up, so the pillar before one is a cell longer
                int next = r.nextInt(3) == 0 ? 3 : 2, size = next == 3 ? 4 : 3;
                if (pos + gap + size + next + 4 > length) break;
                if (low < fall && r.nextInt(3) == 0) low++;
                made.add(new int[]{pos + gap, size, lateral, low});
                pos += gap + size;
                gap = next;
                lateral = Math.max(-(half - 4), Math.min(half - 4, lateral + r.nextInt(3) - 1));
            }
            exitFrom = pos + gap;
            this.length = exitFrom + 4;
            exitFeet = top - low;
            start = new int[made.size()];
            len = new int[made.size()];
            off = new int[made.size()];
            down = new int[made.size()];
            for (int i = 0; i < made.size(); i++) {
                start[i] = made.get(i)[0];
                len[i] = made.get(i)[1];
                off[i] = made.get(i)[2];
                down[i] = made.get(i)[3];
            }
            int ex = ax + dx * this.length, ez = az + dz * this.length, px = Math.abs(dz) * (half + 2), pz = Math.abs(dx) * (half + 2);
            x0 = Math.min(ax, ex) - px - 1;
            x1 = Math.max(ax, ex) + px + 1;
            z0 = Math.min(az, ez) - pz - 1;
            z1 = Math.max(az, ez) + pz + 1;
        }

        public int pillars() {
            return start.length;
        }

        /** Where to stand on a pillar (or, for -1, the first shelf; for the count, the far one): x, feet, z. */
        public int[] stand(int pillar) {
            int along = pillar < 0 ? 1 : pillar >= start.length ? exitFrom + 2 : start[pillar] + len[pillar] / 2, side = pillar < 0 || pillar >= start.length ? 0 : off[pillar];
            int feet = pillar < 0 ? top : pillar >= start.length ? exitFeet : top - down[pillar];
            return new int[]{ax + dx * along - dz * side, feet, az + dz * along + dx * side};
        }

        @Override
        public int feet2(int x, int z) {
            int a = (x - ax) * dx + (z - az) * dz, c = -(x - ax) * dz + (z - az) * dx;
            if (a < 0 || a > length || Math.abs(c) > half) return NONE;
            if (a < 4) return 2 * top;
            if (a >= exitFrom) return 2 * exitFeet;
            for (int i = 0; i < start.length; i++) if (a >= start[i] && a < start[i] + len[i] && Math.abs(c - off[i]) <= 1) return 2 * (top - down[i]);
            int bed = 2 * (top - depth);
            // the way back out: down the wall on one side from the first shelf, half a block a cell, into the pool
            return c >= half - 1 ? Math.max(bed, 2 * top - (a - 3)) : bed;
        }

        @Override
        public int wet(int x, int z) {
            int f = feet2(x, z);
            return f == NONE ? 0 : Math.max(0, top - depth + 2 - Math.floorDiv(f + 1, 2));
        }

        @Override
        public int roof(int x, int z) {
            int c = -(x - ax) * dz + (z - az) * dx;
            return top + Math.max(4, height - (int) Math.round((double) c * c / (half * half) * 2.5));
        }
    }

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    public final List<Part> parts = new ArrayList<>();
    private final List<int[]> doors = new ArrayList<>();
    private final List<String> doorLegs = new ArrayList<>();
    private int bx0, bz0, bx1, bz1, low, top, half;

    protected CrawlLand(Ctx ctx, long salt) {
        super(ctx, salt);
    }

    protected final <P extends Part> P part(P p) {
        parts.add(p);
        return p;
    }

    /** A door of a leg's fight: every cell of this box that is air shuts while the fight is on. */
    protected final void door(String leg, int x0, int y0, int z0, int x1, int y1, int z1) {
        doors.add(new int[]{Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)});
        doorLegs.add(leg);
    }

    /** Call when every part is placed: works out how far the rock has to reach. */
    protected final void done() {
        bx0 = bz0 = low = Integer.MAX_VALUE;
        bx1 = bz1 = top = Integer.MIN_VALUE;
        for (Part p : parts) {
            bx0 = Math.min(bx0, p.x0);
            bx1 = Math.max(bx1, p.x1);
            bz0 = Math.min(bz0, p.z0);
            bz1 = Math.max(bz1, p.z1);
            for (int x = p.x0; x <= p.x1; x += 2) for (int z = p.z0; z <= p.z1; z += 2) {
                int f = p.feet2(x, z);
                if (f == NONE) continue;
                low = Math.min(low, Math.floorDiv(f, 2));
                top = Math.max(top, p.roof(x, z));
            }
        }
        bx0 -= 14;
        bz0 -= 14;
        bx1 += 14;
        bz1 += 14;
        low -= 10;
        top += 12;
        half = Math.max(Math.max(-bx0, bx1), Math.max(-bz0, bz1)) + 10;
    }

    /** The rock itself, anywhere near the hollows. */
    protected abstract BlockState rock(M m, int x, int y, int z);

    /** What the rock is far under the lowest floor, where nobody sees it. */
    protected abstract BlockState deep(M m);

    /** The block under your feet in a part. */
    protected abstract BlockState floor(M m, Part part, int x, int y, int z);

    /** Whether a ledge's floor here is one block thick with nothing under it (floor that can give way). */
    protected boolean thin(Part part, int x, int y, int z) {
        return false;
    }

    /** The half step a climbing way is made of. */
    protected abstract BlockState step(M m);

    /** What shuts a fight's doors. */
    protected abstract BlockState doorBlock(M m);

    /**
     * One cell of air that touches the dungeon's own rock (never something another call of this put there, so nothing can
     * be hung on a thing that isn't in the next chunk): {@code onFloor} (rock under it), {@code underRoof} (rock over it), and
     * {@code wx}, {@code wz} the way to a wall beside it (both 0 for none). Put here what grows on this land's walls,
     * hangs from its roofs and lies on its floors; you may also change the rock you were pointed at. Decide by position,
     * never by chance that differs between calls: the same cell is asked about from more than one chunk.
     */
    protected abstract void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz);

    /** Anything too big for {@link #adorn}: drawn after the floors are laid and before the walls are dressed. */
    protected void special(V v, M m) {}

    /** The top of the rock. */
    protected final int rockTop() {
        return top;
    }

    /** Whatever must be put right after the walls are dressed. */
    protected void finish(V v, M m) {}

    /** Whether a cell is air in the land as it is drawn. */
    public final boolean open(int x, int y, int z) {
        for (Part p : parts) if (p.open(x, y, z)) return true;
        return false;
    }

    @Override
    public int ground(int x, int z) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            Part p = parts.get(i);
            if (x < p.x0 || x > p.x1 || z < p.z0 || z > p.z1) continue;
            int f = p.feet2(x, z);
            if (f != NONE) return Math.floorDiv(f, 2) - 1;
        }
        return 0;
    }

    @Override
    public int halfSize() {
        return half;
    }

    /** No wind at the edge of a crawl: the rock is the edge. */
    @Override
    public double edge(int x, int z) {
        return -1;
    }

    @Override
    public boolean indoors() {
        return true;
    }

    @Override
    public List<Edit> change(String moment) {
        boolean seal = moment.startsWith("seal_"), unseal = moment.startsWith("unseal_");
        if (!seal && !unseal) return List.of();
        String leg = moment.substring(moment.indexOf('_') + 1);
        List<Edit> out = new ArrayList<>();
        BlockState to = seal ? doorBlock(mats()) : Blocks.AIR.defaultBlockState();
        for (int i = 0; i < doors.size(); i++) {
            if (!doorLegs.get(i).equals(leg)) continue;
            int[] d = doors.get(i);
            for (int x = d[0]; x <= d[3]; x++) for (int y = d[1]; y <= d[4]; y++) for (int z = d[2]; z <= d[5]; z++) if (open(x, y, z)) out.add(new Edit(x, y, z, to));
        }
        return out;
    }

    @Override
    protected final void draw(V v) {
        if (v.x0 + 17 < bx0 || v.x0 - 1 > bx1 || v.z0 + 17 < bz0 || v.z0 - 1 > bz1) return;
        M m = mats();
        BlockState filler = deep(m);
        for (int x = Math.max(bx0, v.x0 - 1); x <= Math.min(bx1, v.x0 + 16); x++) for (int z = Math.max(bz0, v.z0 - 1); z <= Math.min(bz1, v.z0 + 16); z++) {
            v.set(x, 0, z, m.bedrock);
            for (int y = 1; y < low; y++) v.set(x, y, z, filler);
            for (int y = Math.max(1, low); y <= top; y++) v.set(x, y, z, rock(m, x, y, z));
        }
        List<Part> near = new ArrayList<>();
        for (Part p : parts) if (p.near(v)) near.add(p);
        for (Part p : near) p.carve(v);
        for (Part p : near) p.lay(v, this, m);
        special(v, m);
        for (Part p : near) dress(v, m, p, near);
        finish(v, m);
        drawPieces(v, m);
    }

    /**
     * The dungeon's own rock (or what a land's {@link #special} made of it): solid by the parts' shapes, and still there.
     * Never something {@link #adorn} put into the air. A shelf, a root or a hanging thing may only hold on to this: what
     * one rule hangs in a chunk's margin isn't always hung in the chunk that owns the cell, and whatever held on to it
     * there would be left in the air.
     */
    private static boolean rock(V v, List<Part> near, int x, int y, int z) {
        for (Part q : near) if (q.open(x, y, z)) return false;
        return V.holds(v.get(x, y, z));
    }

    private void dress(V v, M m, Part p, List<Part> near) {
        for (int x = Math.max(p.x0, v.x0 - 1); x <= Math.min(p.x1, v.x0 + 16); x++) for (int z = Math.max(p.z0, v.z0 - 1); z <= Math.min(p.z1, v.z0 + 16); z++) {
            int f = p.feet2(x, z);
            if (f == NONE) continue;
            for (int y = Math.floorDiv(f, 2), roof = p.roof(x, z); y < roof; y++) {
                if (v.get(x, y, z) != null) continue;
                boolean onFloor = rock(v, near, x, y - 1, z), underRoof = rock(v, near, x, y + 1, z);
                int wx = 0, wz = 0;
                if (rock(v, near, x + 1, y, z)) wx = 1;
                else if (rock(v, near, x - 1, y, z)) wx = -1;
                else if (rock(v, near, x, y, z + 1)) wz = 1;
                else if (rock(v, near, x, y, z - 1)) wz = -1;
                if (onFloor || underRoof || wx != 0 || wz != 0) adorn(v, m, p, x, y, z, onFloor, underRoof, wx, wz);
            }
        }
    }
}
