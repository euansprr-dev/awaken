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

    /**
     * A room. As it is made it has no straight edge: an oval whose rim wanders, a level floor, a low dome that isn't smooth.
     * {@link #shaped} gives it one of the other forms (SPEC §50.9), so that one room is not the next: a long hall, a cross,
     * a ring round a core of rock, a hall of piers, a room with a terrace along one side, a pit with a walk round it, a
     * rift whose floor comes up in steps. Whatever the form, {@code feet} is its lowest floor and every other level is
     * raised from that, so what a skin draws at {@code feet} is on the floor or under it and never in the air.
     */
    public static class Cave extends Part {
        public enum Form { OVAL, HALL, CROSS, RING, PIERS, TERRACE, PIT, RIFT }

        public final int cx, cz, feet, height;
        public final double rx, rz;
        /** How far the rim wanders in and out, as a share of the radius: about 0.16 for something dug, 0 for something built. */
        public final double rim;
        final SimplexNoise n;
        public Form form = Form.OVAL;
        /** The way a formed room runs (a heading: its sine along x, its cosine along z), its half-length that way and its half-width across. */
        public double axis, rl, rw;
        /** A rift's floor steps up as the room runs on, or (falls) down. */
        public boolean falls;
        private double sin, cos = 1, power = 6;
        /**
         * Depth (SPEC §50.9): how bold the ground is (0 a level floor, 1 the usual: the way through stays near level, and
         * off it the ground stands a slab higher, rolls, and banks up toward the walls), and how much the roof swells, dips and
         * comes down in lumps at the walls (0 for what was built plumb; then walls get pilasters and the roof gets beams).
         */
        public double ground, rough;
        /** Where ways come into the room: x, z. The ground is worn level along the line from each to the middle. */
        final List<int[]> doors = new ArrayList<>();

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
            rl = rx;
            rw = rz;
            n = dev.awaken.gates.realms.kit.N.noise(seed);
            x0 = (int) Math.floor(cx - rx * 1.2) - 1;
            x1 = (int) Math.ceil(cx + rx * 1.2) + 1;
            z0 = (int) Math.floor(cz - rz * 1.2) - 1;
            z1 = (int) Math.ceil(cz + rz * 1.2) + 1;
        }

        /**
         * Gives the room a form. A hall, a hall of piers, a terraced room and a rift are {@code rl} long each way along
         * {@code axis} and {@code rw} wide each way across it; a cross has arms {@code rl} long and {@code rw} wide each
         * way; a ring and a pit stay as round as they were made and only take the axis (a pit's ramps lie along and across it).
         */
        public Cave shaped(Form form, double axis, double rl, double rw, boolean falls) {
            this.form = form;
            this.axis = axis;
            this.rl = rl;
            this.rw = rw;
            this.falls = falls;
            sin = Math.sin(axis);
            cos = Math.cos(axis);
            power = power(rim);
            if (!round(form)) {
                double far = form == Form.CROSS ? rl : 0, bx = Math.max(far, Math.abs(sin) * rl + Math.abs(cos) * rw), bz = Math.max(far, Math.abs(cos) * rl + Math.abs(sin) * rw);
                x0 = (int) Math.floor(cx - bx * 1.2) - 2;
                x1 = (int) Math.ceil(cx + bx * 1.2) + 2;
                z0 = (int) Math.floor(cz - bz * 1.2) - 2;
                z1 = (int) Math.ceil(cz + bz * 1.2) + 2;
            }
            return this;
        }

        public Cave depth(double ground, double rough) {
            this.ground = ground;
            this.rough = rough;
            return this;
        }

        /** A way comes in here: nothing rises on the line from it to the middle. */
        public void door(int x, int z) {
            doors.add(new int[]{x, z});
        }

        /** How far a column is off the way through the room: the lines from each doorway to the middle (round a ring's core, the middle of the walk). */
        double offWay(int x, int z) {
            if (form == Form.RING) return Math.abs(oval(x, z) - 0.65) * rx * 1.6;
            double best = Math.hypot(x - cx, z - cz);
            for (int[] d : doors) {
                double vx = cx - d[0], vz = cz - d[1], t = Math.max(0, Math.min(1, ((x - d[0]) * vx + (z - d[1]) * vz) / Math.max(1, vx * vx + vz * vz)));
                best = Math.min(best, Math.hypot(x - (d[0] + vx * t), z - (d[1] + vz * t)));
            }
            return best;
        }

        /**
         * The ground over a level floor, in half blocks: nothing on the way, and off it a slab or two that rolls, banking
         * up to two or three blocks at the walls. Smooth enough that a step is half a block nearly everywhere and never
         * more than one.
         */
        int relief2(int x, int z) {
            if (ground <= 0) return 0;
            double wild = Math.max(0, Math.min(1, (offWay(x, z) - 2.5) / 4.5));
            if (wild == 0) return 0;
            double o = Math.min(1, out(x, z)), bank = Math.max(0, Math.min(1, (o - 0.5) / 0.45));
            double roll = 0.5 + 0.5 * n.get(x / 8.0 + 70, z / 8.0 - 20), lump = 0.5 + 0.5 * n.get(x / 5.0 - 33, z / 5.0 + 9);
            return (int) Math.round(Math.min(7, wild * ground * (0.7 + 1.5 * roll + 3.4 * bank * bank * (0.45 + 0.55 * lump))));
        }

        /** Whether a form's floor is all one level before any ground is laid on it. */
        public static boolean level(Form form) {
            return form != Form.TERRACE && form != Form.PIT && form != Form.RIFT;
        }

        /** Whether a form keeps the oval's plan. */
        public static boolean round(Form form) {
            return form == Form.OVAL || form == Form.RING || form == Form.PIT;
        }

        /** Something built has corners; something dug has a long cave's soft ends. */
        private static double power(double rim) {
            return rim > 0.08 ? 2.6 : 6;
        }

        private static double bar(double a, double b, double p) {
            return Math.pow(Math.pow(Math.abs(a), p) + Math.pow(Math.abs(b), p), 1 / p);
        }

        /** How far the wall of a formed room is from its middle, along a heading turned {@code rel} from its axis. */
        public static double edge(Form form, double rim, double rl, double rw, double rel) {
            double p = power(rim), du = Math.cos(rel), dw = Math.sin(rel), a = 1 / bar(du / rl, dw / rw, p);
            return form == Form.CROSS ? Math.max(a, 1 / bar(du / rw, dw / rl, p)) : a;
        }

        /** Along the room's axis from its middle, and across it. */
        final double u(int x, int z) {
            return (x - cx) * sin + (z - cz) * cos;
        }

        final double w(int x, int z) {
            return (x - cx) * cos - (z - cz) * sin;
        }

        /** Where a place along and across the axis is: x, z. */
        final double[] place(double u, double w) {
            return new double[]{cx + u * sin + w * cos, cz + u * cos - w * sin};
        }

        /** How far out a column is: 0 at the middle, 1 at the rim. */
        public double out(int x, int z) {
            double wander = 1 + rim * n.get(x / 7.0, z / 7.0);
            switch (form) {
                case HALL, PIERS, TERRACE, RIFT -> {
                    return bar(u(x, z) / rl, w(x, z) / rw, power) / wander;
                }
                case CROSS -> {
                    double u = u(x, z), w = w(x, z);
                    return Math.min(bar(u / rl, w / rw, power), bar(u / rw, w / rl, power)) / wander;
                }
                default -> {
                    return oval(x, z) / wander;
                }
            }
        }

        final double oval(int x, int z) {
            double dx = (x - cx) / rx, dz = (z - cz) / rz;
            return Math.sqrt(dx * dx + dz * dz);
        }

        /** Rock left standing inside the room: a ring's core, a hall's piers (and, in an arena, its pillars). */
        public boolean solid(int x, int z) {
            if (form == Form.RING) return oval(x, z) < 0.3;
            if (form == Form.PIERS) {
                // two rows of piers down the hall, one every six cells, each about three cells square
                double u = u(x, z), w = Math.abs(w(x, z)) - rw * 0.5;
                if (Math.abs(w) <= 1 && Math.abs(u) <= rl - 3.5 && Math.abs(u - 6 * Math.round(u / 6)) <= 1) return true;
            }
            return pilaster(x, z);
        }

        /**
         * A wall that was built is not one flat face: a pilaster stands a block out of it every six cells along it. Only
         * for forms with straight walls (the round ones keep their curve), and only where depth was asked for.
         */
        private boolean pilaster(int x, int z) {
            if (ground <= 0 || rough > 0 || round(form)) return false;
            double u = u(x, z), w = w(x, z), near = form == Form.CROSS ? Math.min(bar(u / rl, w / rw, power), bar(u / rw, w / rl, power)) : bar(u / rl, w / rw, power);
            if (near < 1 - 1.6 / Math.min(rl, rw)) return false;
            // along whichever wall is nearer: a long side runs along the axis, an end across it
            double side = Math.abs(w) / rw, end = Math.abs(u) / rl;
            if (form == Form.CROSS) {
                side = Math.abs(u) <= rw ? Math.abs(w) / rl : Math.abs(w) / rw;
                end = Math.abs(w) <= rw ? Math.abs(u) / rl : Math.abs(u) / rw;
            }
            long along = Math.round(side >= end ? u : w);
            return Math.floorMod(along, 6) == 3;
        }

        private static int ramp(double t, int steps) {
            return (int) Math.max(0, Math.min(steps, Math.round(t + steps / 2.0)));
        }

        /** How far a column's floor stands over {@code feet}, in half blocks: the form's own levels, or the ground laid over a level form. */
        public int lift2(int x, int z) {
            return level(form) ? relief2(x, z) : formLift(x, z);
        }

        private int formLift(int x, int z) {
            switch (form) {
                case TERRACE -> {
                    // a terrace three up along one side, and two ramps of half steps onto it
                    double w = w(x, z), brink = rw * 0.3;
                    if (w >= brink) return 6;
                    return Math.abs(Math.abs(u(x, z)) - rl * 0.45) <= 1.6 ? (int) Math.max(0, Math.min(6, Math.round(6 - (brink - w)))) : 0;
                }
                case PIT -> {
                    // a walk two up all the way round, and a ramp down from it along the axis and across it, four in all
                    if (oval(x, z) >= 0.62) return 4;
                    double u = Math.abs(u(x, z)), w = Math.abs(w(x, z)), brink = 0.62 * rx;
                    double from = w <= 1.6 ? brink - u : u <= 1.6 ? brink - w : 99;
                    return (int) Math.max(0, Math.min(4, Math.round(4.6 - from)));
                }
                case RIFT -> {
                    // the floor comes up a block, and then two more, as the room runs on (or goes down, if it falls)
                    double u = falls ? -u(x, z) : u(x, z);
                    return ramp(u + rl / 3, 2) + ramp(u - rl / 3, 4);
                }
                default -> {
                    return 0;
                }
            }
        }

        @Override
        public int feet2(int x, int z) {
            return out(x, z) < 1 && !solid(x, z) ? 2 * feet + lift2(x, z) : NONE;
        }

        @Override
        public int roof(int x, int z) {
            double d = Math.min(1, out(x, z));
            int dome = feet + Math.max(3, 2 + (int) Math.round((height - 2) * Math.pow(1 - d * d, 0.55) + 1.1 * Math.min(1, rim * 8) * n.get(x / 4.0 + 40, z / 4.0)));
            int lift = lift2(x, z);
            if (ground > 0 && height >= 6) {
                if (rough > 0) {
                    // a dug roof swells and dips, and here and there comes down in a lump; never within four of the floor
                    double swell = n.get(x / 6.0 + 200, z / 6.0 - 90), knot = n.get(x / 2.6 - 400, z / 2.6 + 150);
                    dome += (int) Math.round(rough * (1.6 * swell * Math.pow(1 - d * d, 0.5) - (knot > 0.62 ? 2 * (1 - d) : 0)));
                    // and near a wall it comes down in lumps, so the wall leans in over you and is not plumb from feet to head.
                    // (Lumps cut into the wall itself could stand a cell clear of it and hang in the air; what comes down from
                    // the roof is always joined to it.)
                    if (d > 0.78) dome -= (int) Math.round(rough * 3 * Math.min(1, (d - 0.78) / 0.16) * Math.max(0, n.get(x / 4.0 + 300, z / 4.0 - 77)));
                } else if (!round(form) && d < 0.97 && Math.floorMod(Math.round(u(x, z)), 5) == 0) dome -= 1; // a beam across a built hall every five cells
                dome = Math.max(dome, feet + (d > 0.78 ? 3 : 4));
            }
            // over a raised floor there is always room to stand, however low the dome has come by then
            return lift == 0 ? dome : Math.max(dome, feet + (lift + 1) / 2 + 4);
        }

        /** How far it is from the middle to the wall along a heading. */
        public double reach(double heading) {
            double sx = Math.sin(heading), sz = Math.cos(heading), t = 0;
            while (t < 90 && out((int) Math.round(cx + sx * (t + 0.5)), (int) Math.round(cz + sz * (t + 0.5))) < 1) t += 0.5;
            return t;
        }

        /** Your feet on this room's floor at a column, or its lowest floor where the column isn't floor. */
        public int floorAt(int x, int z) {
            int f = feet2(x, z);
            return f == NONE ? feet : Math.floorDiv(f, 2);
        }
    }

    /**
     * A room to fight something big in: a cave with a cleaner rim and pillars left standing for cover. As it is made it
     * is round with a ring of pillars a little over half way out; {@link #planned} lays it out another way (SPEC §50.9).
     * Its middle is always open and level.
     */
    public static final class Arena extends Cave {
        public enum Plan { RING, NAVE, CROSS, ROTUNDA, TIERS, SQUARE }

        final int pillars;
        final double turn;
        public Plan plan = Plan.RING;
        /** Where the pillars stand: x, z, and how thick (a radius, squared). */
        private final List<double[]> posts = new ArrayList<>();

        public Arena(String name, int cx, int feet, int cz, double r, int height, int pillars, long seed) {
            this(name, cx, feet, cz, r, height, pillars, 0.06, seed);
        }

        public Arena(String name, int cx, int feet, int cz, double r, int height, int pillars, double rim, long seed) {
            super(name, cx, feet, cz, r, r, height, rim, seed);
            this.pillars = pillars;
            turn = (seed & 63) / 64.0 * Math.PI;
            ring(pillars, 0.56, turn);
        }

        private void ring(int count, double out, double from) {
            for (int i = 0; i < count; i++) {
                double a = from + i * Math.PI * 2 / count;
                posts.add(new double[]{cx + Math.cos(a) * rx * out, cz + Math.sin(a) * rz * out, 2.6});
            }
        }

        private void post(double u, double w, double thick) {
            double[] at = place(u, w);
            posts.add(new double[]{at[0], at[1], thick});
        }

        /**
         * Lays the room out: a ring of pillars (as made), a nave with two rows of them, a cross with four at the crossing,
         * a rotunda with two rings, a round floor with two tiers of steps round it, or a square hall on four great piers.
         * With no pillars only the plan changes.
         */
        public Arena planned(Plan plan, double axis) {
            this.plan = plan;
            posts.clear();
            double r = rx;
            switch (plan) {
                case NAVE -> {
                    shaped(Form.HALL, axis, r * 1.3, r * 0.7, false);
                    int each = pillars == 0 ? 0 : Math.max(2, pillars / 2);
                    for (int i = 0; i < each; i++) for (int side = -1; side <= 1; side += 2) post(rl * 0.62 * (2.0 * i / (each - 1) - 1), side * rw * 0.52, 2.6);
                }
                case CROSS -> {
                    shaped(Form.CROSS, axis, r * 1.15, r * 0.5, false);
                    if (pillars > 0) for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2) post(i * rw * 0.72, j * rw * 0.72, 2.6);
                }
                case SQUARE -> {
                    shaped(Form.HALL, axis, r * 0.92, r * 0.92, false);
                    if (pillars > 0) for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2) post(i * rl * 0.55, j * rl * 0.55, 5.5);
                }
                case ROTUNDA -> {
                    ring(pillars == 0 ? 0 : Math.max(3, pillars / 2), 0.3, turn);
                    ring(pillars, 0.64, turn + 0.4);
                }
                case TIERS -> ring(pillars, 0.5, turn);
                default -> ring(pillars, 0.56, turn);
            }
            return this;
        }

        /** Whether a column is one of the pillars. */
        public boolean pillar(int x, int z) {
            for (double[] p : posts) if ((x - p[0]) * (x - p[0]) + (z - p[1]) * (z - p[1]) < p[2]) return true;
            return false;
        }

        @Override
        public boolean solid(int x, int z) {
            return pillar(x, z);
        }

        /** Tiers: the outer fifth of the floor is a step up, and the last tenth another. Any other plan keeps its floor level but for the ground banked up against the wall. */
        @Override
        public int lift2(int x, int z) {
            if (plan != Plan.TIERS) return relief2(x, z);
            double o = oval(x, z);
            return o > 0.9 ? 4 : o > 0.78 ? 2 : 0;
        }

        /** Something big is fought here: the floor stays level to five sixths of the way out, and only the last of it banks up. */
        @Override
        int relief2(int x, int z) {
            if (ground <= 0) return 0;
            double o = Math.min(1, out(x, z)), bank = Math.max(0, (o - 0.84) / 0.16);
            return (int) Math.round(Math.min(5, ground * 5 * bank * (0.4 + 0.6 * (0.5 + 0.5 * n.get(x / 5.0 - 33, z / 5.0 + 9)))));
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
        /** Depth (SPEC §50.9): over 0, the way is worn down its middle, with half a step up along its sides (ragged where it was dug, a kerb where it was built). */
        public double ground;

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
            double t = along(x, z), d = off(x, z, t);
            if (d > half + wander * n.get(x / 5.0, z / 5.0)) return NONE;
            int f = (int) Math.round(2 * (ay + (by - ay) * t));
            // the worn middle stays as it was; the sides stand half a step higher, but not where the way meets what it joins
            if (ground > 0 && half >= 2 && d > half * 0.5 && t * len > 3 && (1 - t) * len > 3 && (wander <= 0 || n.get(x / 4.0 + 9, z / 4.0 - 5) > -0.3)) f += 1;
            return f;
        }

        /** How far a column is from the middle of the way. */
        double offWay(int x, int z) {
            return off(x, z, along(x, z));
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
        /** Cut square, with flights along its four walls: for places that were built. */
        public boolean square;

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

        public Shaft squared() {
            square = true;
            return this;
        }

        /**
         * What the wall is cut like (SPEC §50.9 "Depth"): 0 leaves it plain; 1 cuts bays one cell deep between piers two wide
         * (a built stairwell); 2 scoops it out in patches (a dug shaft). Either way the wall is whole from four under the
         * ledge to four over the head of whoever is on it, so there is never a hole beside the climb, only above and below it.
         */
        public int walls;

        /** Twice the ledge's height where it passes this bearing the k-th time, whether or not it is broken there. */
        private int pass(int x, int z, int k) {
            double turns = round(x, z) + k;
            if (turns < -0.09) return NONE;
            double fall = Math.max(0, turns) * perTurn;
            return fall > drop ? NONE : 2 * top - (int) Math.round(2 * fall);
        }

        /** Whether a cell of the wall's face is cut back. */
        private boolean recess(int x, int y, int z) {
            if (walls == 0 || y < bottom + water + 3 || y > top + 5 || in(x, z)) return false;
            int ox = x - cx, oz = z - cz;
            if (Math.abs(ox) == Math.abs(oz)) return false;
            boolean alongZ = Math.abs(ox) > Math.abs(oz);
            int ix = alongZ ? x - Integer.signum(ox) : x, iz = alongZ ? z : z - Integer.signum(oz);
            if (!in(ix, iz)) return false;
            int t = alongZ ? oz : ox;
            if (walls == 1) {
                if (Math.abs(t) > r - 3 || Math.floorMod(t + 2, 6) >= 4) return false;
            } else if (n.get((x + z * 0.7) / 4.0 + 31, y / 4.0 + 7) < 0.12) return false;
            for (int k = -1; k * perTurn <= drop + perTurn; k++) {
                int f = pass(ix, iz, k);
                if (f == NONE) continue;
                int base = Math.floorDiv(f, 2);
                if (y >= base - 5 && y <= base + 5) return false;
            }
            return true;
        }

        private double far(int x, int z) {
            return square ? Math.max(Math.abs(x - cx), Math.abs(z - cz)) : Math.hypot(x - cx, z - cz);
        }

        private boolean in(int x, int z) {
            return far(x, z) < (square ? r : r + 0.5 * n.get(x / 5.0, z / 5.0));
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
            if (x < x0 || x > x1 || z < z0 || z > z1) return false;
            if (!in(x, z)) return recess(x, y, z);
            if (y < bottom || y >= dome(x, z)) return false;
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
                if (!in(x, z)) {
                    if (walls != 0) for (int y = bottom + water + 3; y <= top + 5; y++) if (recess(x, y, z)) v.set(x, y, z, null);
                    continue;
                }
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
            // round a square shaft the ledge keeps its distance from the walls, not from the middle
            if (square) d /= Math.max(Math.abs(Math.sin(a)), Math.abs(Math.cos(a)));
            int x = cx + (int) Math.round(Math.sin(a) * d), z = cz - (int) Math.round(Math.cos(a) * d);
            return new int[]{x, top - (int) Math.floor(turns * perTurn), z};
        }
    }

    /**
     * A gulf to get across (SPEC §50.9): a long room with no floor but a pool far below and a shelf at each end. What
     * there is to cross on is its {@link Kind}: flat-topped pillars, a bridge with spans gone, rafters one cell wide, a
     * ledge along one wall and then the other, platforms hung from the roof, great steps going down, stones in shallow
     * water, or a deck that gives way behind you. Every jump is a sprint over one to three cells, straight along the
     * gulf or straight across it, level or down; nothing is ever higher than what you jump from. It runs straight along
     * x or z. A ramp down one wall goes from the shelf you start on into the pool, so whoever falls (and isn't caught)
     * can walk back up and try again.
     */
    public static final class Chasm extends Part {
        public enum Kind { PILLARS, BRIDGE, BEAMS, LEDGES, HANGERS, STEPS, FORD, FALLING }

        /** A stand that gives way under you; one hung from the roof by its middle; one on a pier down to the bed; one that is beside the way, not on it. */
        static final int THIN = 1, HANG = 2, PIER = 4, ASIDE = 8;

        public final Kind kind;
        public final int ax, az, dx, dz, half, top, depth, height, water, length, exitFeet, exitFrom;
        /** Everything there is to stand on between the shelves: from and to along the gulf, from and to across it, your feet on it, how thick it is (0: solid to the bed), flags. */
        private final List<int[]> stands = new ArrayList<>();
        /** The ones you cross on, in order. */
        private final List<int[]> way = new ArrayList<>();

        /** Pillars over a pool, as the first chasms all were. */
        public Chasm(String name, int ax, int top, int az, int dx, int dz, int length, int half, int height, int depth, int fall, long seed) {
            this(name, Kind.PILLARS, ax, top, az, dx, dz, length, half, height, depth, fall, seed);
        }

        /**
         * @param ax,az  where the first shelf begins, {@code top} your feet on it
         * @param dx,dz  the way it runs: one of them 1 or -1, the other 0
         * @param length the most it may be long (it ends where its last stand leaves room for the far shelf)
         * @param depth  how far under the first shelf the bed is
         * @param fall   how far the way across steps down in all, at most
         */
        public Chasm(String name, Kind kind, int ax, int top, int az, int dx, int dz, int length, int half, int height, int depth, int fall, long seed) {
            this.name = name;
            this.kind = kind;
            this.ax = ax;
            this.az = az;
            this.dx = dx;
            this.dz = dz;
            this.half = half;
            this.top = top;
            this.depth = depth;
            this.height = height;
            water = kind == Kind.FORD ? 1 : 2;
            java.util.Random r = new java.util.Random(seed);
            int low = 0;
            switch (kind) {
                case BRIDGE -> {
                    // a deck three wide on piers, with two spans gone; what fell lies in the pool under each break
                    int pos = 4;
                    for (int i = 0; i < 3; i++) {
                        int size = 5 + r.nextInt(3), gap = 2 + r.nextInt(2);
                        if (i > 0 && low < fall && r.nextBoolean()) low++; // a span that has sagged
                        put(pos, pos + size - 1, -1, 1, top - low, 2, PIER);
                        pos += size;
                        if (i == 2) break;
                        int side = r.nextBoolean() ? 1 : -4;
                        put(pos, pos + gap - 1, side, side + 2, top - depth + 3, 0, ASIDE);
                        pos += gap;
                    }
                    exitFrom = pos;
                }
                case BEAMS -> {
                    // rafters: beams one wide, end to end; each ends on a cross beam from wall to wall a block lower. The next
                    // one starts beside it (step down onto the cross beam and up again) or straight on over a gap of two
                    int lane = r.nextInt(3) - 1, end = 3, count = 0;
                    while (true) {
                        int size = 5 + r.nextInt(3);
                        boolean hop = count > 0 && r.nextInt(5) < 2;
                        int start = count == 0 ? 4 : hop ? end + 3 : end;
                        if (start + size + 4 > length && count >= 2) break;
                        if (count > 0) put(end, end, -half, half, top - 1, 1, ASIDE);
                        if (hop) put(start, start, -half, half, top - 1, 1, ASIDE);
                        else if (count > 0) lane += lane >= 2 ? -2 : lane <= -2 ? 2 : r.nextBoolean() ? 2 : -2;
                        put(start, start + size - 1, lane, lane, top, 1, 0);
                        end = start + size - 1;
                        count++;
                    }
                    exitFrom = end + 1;
                }
                case LEDGES -> {
                    // a ledge two wide along the left wall with a break in it, across by a pillar in the middle, and on along the right wall with another
                    int mid = Math.max(16, length / 2 - 2), brk = 8 + r.nextInt(3), gap = 2 + r.nextInt(2);
                    put(4, brk - 1, -half, -half + 1, top, 2, 0);
                    if (fall > 0) low++;
                    put(brk + gap, mid + 2, -half, -half + 1, top - low, 2, 0);
                    put(mid, mid + 2, -1, 1, top - low, 0, 0);
                    int brk2 = mid + 6 + r.nextInt(3), gap2 = 2 + r.nextInt(2), end = Math.max(brk2 + gap2 + 3, length - 5);
                    put(mid, brk2 - 1, half - 1, half, top - low, 2, 0);
                    if (fall > 1) low++;
                    put(brk2 + gap2, end, half - 1, half, top - low, 2, 0);
                    exitFrom = end + 1;
                }
                case HANGERS -> {
                    // platforms three cells square hung from the roof by their middles, two cells apart
                    int pos = 4, lateral = r.nextInt(3) - 1;
                    while (pos + 2 + 3 + 2 + 4 <= length || way.size() < 3) {
                        if (low < fall && r.nextBoolean()) low++;
                        put(pos + 2, pos + 4, lateral - 1, lateral + 1, top - low, 1, HANG);
                        pos += 5;
                        lateral = Math.max(-(half - 3), Math.min(half - 3, lateral + r.nextInt(3) - 1));
                    }
                    exitFrom = pos + 2;
                }
                case STEPS -> {
                    // great steps going down: each a jump of one or two cells and two or three lower than the last
                    int pos = 4, gap = 2, lateral = r.nextInt(3) - 1;
                    while (low < fall) {
                        int size = 3 + r.nextInt(2);
                        if (pos + gap + size + 2 + 4 > length && !way.isEmpty()) break;
                        low += Math.min(fall - low, 2 + r.nextInt(2));
                        put(pos + gap, pos + gap + size - 1, lateral - 1, lateral + 1, top - low, 0, 0);
                        pos += gap + size;
                        gap = 1 + r.nextInt(2);
                        lateral = Math.max(-(half - 4), Math.min(half - 4, lateral + r.nextInt(3) - 1));
                    }
                    exitFrom = pos + gap;
                }
                case FORD -> {
                    // shallow water from wall to wall: a bank at the foot of each shelf, stones at the water's top between them,
                    // and more stones that lead nowhere. Whoever misses wades, slowly
                    int stone = top - 1, pos = 6, lateral = r.nextInt(5) - 2;
                    put(4, 4, -half, half, stone, 0, ASIDE);
                    while (true) {
                        put(pos, pos + 1, lateral, lateral + 1, stone, 0, 0);
                        int next = pos + 3 + r.nextInt(2);
                        if (next + 1 + 2 + 1 + 4 > length) break;
                        pos = next;
                        lateral = Math.max(-(half - 2), Math.min(half - 3, lateral + r.nextInt(3) - 1));
                    }
                    int bank = pos + 3;
                    put(bank, bank, -half, half, stone, 0, ASIDE);
                    for (int i = 0, more = 5 + r.nextInt(5); i < more; i++) {
                        int a = 6 + r.nextInt(Math.max(1, bank - 8)), c = r.nextInt(2 * half - 2) - (half - 1), big = r.nextInt(2);
                        put(a, a + big, c, c + big, stone, 0, ASIDE);
                    }
                    exitFrom = bank + 1;
                }
                case FALLING -> {
                    // one deck three wide, shelf to shelf, a block thick, that gives way under whoever stands on it
                    int end = Math.max(12, length - 5);
                    put(4, end, -1, 1, top, 1, THIN);
                    exitFrom = end + 1;
                }
                default -> {
                    // flat-topped pillars two or three cells apart; a gap of three wants a run-up, so the pillar before one is a cell longer
                    int pos = 4, gap = 2, lateral = r.nextInt(3) - 1;
                    while (true) {
                        int next = r.nextInt(3) == 0 ? 3 : 2, size = next == 3 ? 4 : 3;
                        if (pos + gap + size + next + 4 > length) break;
                        if (low < fall && r.nextInt(3) == 0) low++;
                        put(pos + gap, pos + gap + size - 1, lateral - 1, lateral + 1, top - low, 0, 0);
                        pos += gap + size;
                        gap = next;
                        lateral = Math.max(-(half - 4), Math.min(half - 4, lateral + r.nextInt(3) - 1));
                    }
                    exitFrom = pos + gap;
                }
            }
            this.length = exitFrom + 4;
            exitFeet = top - low;
            int ex = ax + dx * this.length, ez = az + dz * this.length, px = Math.abs(dz) * (half + 2), pz = Math.abs(dx) * (half + 2);
            x0 = Math.min(ax, ex) - px - 1;
            x1 = Math.max(ax, ex) + px + 1;
            z0 = Math.min(az, ez) - pz - 1;
            z1 = Math.max(az, ez) + pz + 1;
        }

        private void put(int a0, int a1, int c0, int c1, int feet, int thick, int flags) {
            int[] s = {a0, a1, c0, c1, feet, thick, flags};
            stands.add(s);
            if ((flags & ASIDE) == 0) way.add(s);
        }

        private int along(int x, int z) {
            return (x - ax) * dx + (z - az) * dz;
        }

        private int across(int x, int z) {
            return -(x - ax) * dz + (z - az) * dx;
        }

        private int[] at(int a, int c, int feet) {
            return new int[]{ax + dx * a - dz * c, feet, az + dz * a + dx * c};
        }

        /**
         * What the two long walls are cut like under the level of the shelves (SPEC §50.9 "Depth"): 0 leaves them plain; 1 cuts
         * bays one cell deep and four wide between piers two wide; 2 scoops them out in patches. Never beside anything
         * there is to stand on, the ramp back up included, and never low enough to climb into out of the water.
         */
        public int walls;

        /** Whether a cell of a long wall's face is cut back. */
        private boolean recess(int x, int y, int z) {
            if (walls == 0 || y < top - depth + water + 2 || y > top - 2) return false;
            int a = along(x, z), c = across(x, z);
            if (Math.abs(c) != half + 1 || a < 7 || a > exitFrom - 4) return false;
            if (walls == 1) {
                if (Math.floorMod(a, 6) >= 4) return false;
            } else if (dev.awaken.gates.realms.kit.N.rnd(Math.floorDiv(a + (y >> 2), 3), Math.floorDiv(y, 3), 4107 + ax * 31L + az) > 0.5) return false;
            int side = Integer.signum(c), bed = 2 * (top - depth);
            for (int i = 0; i < 2; i++) {
                int cc = side * (half - i);
                int[] at = at(a, cc, 0);
                if (feet2(at[0], at[2]) != bed) return false;
                for (int[] s : stands) if (s[5] != 0 && a >= s[0] - 1 && a <= s[1] + 1 && cc >= s[2] && cc <= s[3]) return false;
            }
            return true;
        }

        @Override
        void carve(V v) {
            super.carve(v);
            if (walls == 0) return;
            for (int a = 7; a <= exitFrom - 4; a++) for (int side = -1; side <= 1; side += 2) {
                int[] at = at(a, side * (half + 1), 0);
                if (!v.in(at[0], 1, at[2])) continue;
                for (int y = top - depth + water + 2; y <= top - 2; y++) if (recess(at[0], y, at[2])) v.set(at[0], y, at[2], null);
            }
        }

        /** How many stands the way across has. */
        public int pillars() {
            return way.size();
        }

        /** Where to stand on one of them (or, for -1, the first shelf; for the count, the far one): x, feet, z. */
        public int[] stand(int i) {
            if (i < 0) return at(1, 0, top);
            if (i >= way.size()) return at(exitFrom + 2, 0, exitFeet);
            int[] s = way.get(i);
            // what hangs by its middle has a chain there: stand at its edge
            return at((s[0] + s[1]) / 2, (s[6] & HANG) != 0 ? s[2] : (s[2] + s[3]) / 2, s[4]);
        }

        /** Where a light may hang so that the whole way across can be seen: over each shelf and every few cells along each stand. */
        public List<int[]> lights() {
            List<int[]> out = new ArrayList<>();
            out.add(at(1, 0, top + 3));
            out.add(at(exitFrom + 2, 0, exitFeet + 3));
            for (int[] s : way) for (int a = s[0] + Math.min(1, (s[1] - s[0]) / 2); a <= s[1]; a += 4) out.add(at(a, (s[6] & HANG) != 0 ? s[2] : (s[2] + s[3]) / 2, s[4] + 3));
            return out;
        }

        /** The stands that give way: each as a box of blocks, x0 y0 z0 x1 y1 z1. */
        public List<int[]> thin() {
            List<int[]> out = new ArrayList<>();
            for (int[] s : stands) {
                if ((s[6] & THIN) == 0) continue;
                int[] p = at(s[0], s[2], 0), q = at(s[1], s[3], 0);
                out.add(new int[]{Math.min(p[0], q[0]), s[4] - 2, Math.min(p[2], q[2]), Math.max(p[0], q[0]), s[4], Math.max(p[2], q[2])});
            }
            return out;
        }

        /** Whether a cell is over one of the stands of the way across, within reach of whoever stands on it. */
        boolean over(int x, int y, int z) {
            int a = along(x, z), c = across(x, z);
            for (int[] s : way) if (a >= s[0] && a <= s[1] && c >= s[2] && c <= s[3] && y >= s[4] && y <= s[4] + 2) return true;
            return false;
        }

        @Override
        public int feet2(int x, int z) {
            int a = along(x, z), c = across(x, z);
            if (a < 0 || a > length || Math.abs(c) > half) return NONE;
            if (a < 4) return 2 * top;
            if (a >= exitFrom) return 2 * exitFeet;
            for (int[] s : stands) if (s[5] == 0 && a >= s[0] && a <= s[1] && c >= s[2] && c <= s[3]) return 2 * s[4];
            int bed = 2 * (top - depth);
            // the way back out: down the wall on one side from the first shelf, half a block a cell, into the pool
            return c >= half - 1 ? Math.max(bed, 2 * top - (a - 3)) : bed;
        }

        @Override
        public int wet(int x, int z) {
            int f = feet2(x, z);
            return f == NONE ? 0 : Math.max(0, top - depth + water - Math.floorDiv(f + 1, 2));
        }

        @Override
        public int roof(int x, int z) {
            int c = across(x, z);
            return top + Math.max(4, height - (int) Math.round((double) c * c / (half * half) * 2.5));
        }

        /** Whether a cell is one of the two under the middle of a stand that a pier fills. */
        private static boolean pier(int[] s, int a, int c) {
            int mid = (s[0] + s[1]) / 2;
            return (s[6] & PIER) != 0 && (a == mid || a == mid + 1) && c >= s[2] && c <= s[3];
        }

        private static boolean hanger(int[] s, int a, int c) {
            return (s[6] & HANG) != 0 && a == (s[0] + s[1]) / 2 && c == (s[2] + s[3]) / 2;
        }

        /** The gulf is air from the pool up, but for the stands that have air under them, what they hang from and what they stand on. */
        @Override
        public boolean open(int x, int y, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return false;
            int f = feet2(x, z);
            if (f == NONE) return recess(x, y, z);
            if (y < airFrom(f) || y >= roof(x, z)) return false;
            int a = along(x, z), c = across(x, z);
            for (int[] s : stands) {
                if (s[5] == 0 || a < s[0] || a > s[1] || c < s[2] || c > s[3]) continue;
                if (y < s[4] && y >= s[4] - s[5]) return false;
                if (y >= s[4] && hanger(s, a, c)) return false;
                if (y < s[4] - s[5] && pier(s, a, c)) return false;
            }
            return true;
        }

        @Override
        <M extends Mats> void lay(V v, CrawlLand<M> land, M m) {
            super.lay(v, land, m);
            for (int[] s : stands) {
                if (s[5] == 0) continue;
                for (int a = s[0]; a <= s[1]; a++) for (int c = s[2]; c <= s[3]; c++) {
                    int[] p = at(a, c, s[4]);
                    int x = p[0], z = p[2];
                    if (!v.in(x, 1, z)) continue;
                    for (int k = 1; k <= s[5]; k++) v.set(x, s[4] - k, z, k == 1 ? land.floor(m, this, x, s[4] - 1, z) : land.rock(m, x, s[4] - k, z));
                    if (hanger(s, a, c)) for (int y = s[4], roof = roof(x, z); y < roof; y++) v.set(x, y, z, land.hanger(m));
                    if (pier(s, a, c)) for (int y = s[4] - s[5] - 1; y >= top - depth; y--) v.set(x, y, z, land.rock(m, x, y, z));
                }
            }
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

    /** Whether a cell is where one of the doors shuts. What stands there is swept away the first time it does. */
    protected final boolean inDoor(int x, int y, int z) {
        for (int[] d : doors) if (x >= d[0] && x <= d[3] && y >= d[1] && y <= d[4] && z >= d[2] && z <= d[5]) return true;
        return false;
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

    /** What a hung platform hangs by. */
    protected BlockState hanger(M m) {
        return Blocks.IRON_CHAIN.defaultBlockState();
    }

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

    // ---- beats (SPEC §72.2) ------------------------------------------------------------------------------------------

    private volatile @org.jspecify.annotations.Nullable BeatSkin beats;

    /** This land's blocks for the parts of its beats; what it leaves out is its rock, floor and half step. */
    protected BeatSkin beatSkin(M m) {
        return new BeatSkin();
    }

    final BeatSkin beats(M m) {
        BeatSkin k = beats;
        if (k == null) beats = k = beatSkin(m);
        return k;
    }

    /** The block of one cell of a beat's solid box ({@code top}: the box's top cell). */
    final BlockState beatBlock(M m, Part part, Sculpt.Role role, int arg, int x, int y, int z, boolean top) {
        BeatSkin k = beats(m);
        return switch (role) {
            case ROCK -> rock(m, x, y, z);
            case FLOOR -> top ? floor(m, part, x, y, z) : rock(m, x, y, z);
            case DUST -> top ? BeatSkin.or(k.dust, floor(m, part, x, y, z)) : rock(m, x, y, z);
            case CRUMBLE -> top ? BeatSkin.or(k.crumble, floor(m, part, x, y, z)) : rock(m, x, y, z);
            case DECK -> top ? BeatSkin.or(k.deck, floor(m, part, x, y, z)) : rock(m, x, y, z);
            case STEP -> BeatSkin.or(k.step, step(m));
            case PLINTH_TOP -> BeatSkin.or(k.plinthTop, BeatSkin.or(k.step, step(m)));
            case CAP -> BeatSkin.or(k.cap, BeatSkin.high(BeatSkin.or(k.step, step(m))));
            case BEAM -> BeatSkin.axis(BeatSkin.or(k.beam, rock(m, x, y, z)), arg);
            case LIP -> BeatSkin.or(k.lip, rock(m, x, y, z));
            case SCUFF -> BeatSkin.or(k.scuff, rock(m, x, y, z));
            case RAIL -> BeatSkin.or(k.rail, rock(m, x, y, z));
            case PLINTH -> BeatSkin.or(k.plinth, rock(m, x, y, z));
            case LINE -> arg < 0 ? BeatSkin.or(k.rope, Sculpt.rope()) : BeatSkin.facing(BeatSkin.or(k.line, BeatSkin.LADDER), arg);
            case DOOR -> BeatSkin.or(k.door, doorBlock(m));
            case GLOW -> BeatSkin.or(k.glow, Blocks.SHROOMLIGHT.defaultBlockState());
            case TOOTH -> BeatSkin.or(k.tooth, rock(m, x, y, z));
            case ANCHOR -> Sculpt.anchorRing();
        };
    }

    /**
     * A beat placed by hand in a designed crawl (SPEC §72.7): beat {@code b} at size {@code t} (0 its smallest, 1 its
     * largest) from (x, feet, z) along (dx, dz), dressed in this land's own skin, proved where it stands with the body's
     * rules, and put in the land as a rolled line is: its piece, its moves, its holds, the first lesson of a family the
     * land hasn't taught yet, its traps, the Course that puts a fall back on its ledges (free at E and D) and its line.
     * Returns the cell past it (x, feet, z) for the way on, or null when it won't hold there, and then nothing of it is
     * kept and the way is the land's own. Call before {@link #done}.
     */
    protected final int @org.jspecify.annotations.Nullable [] handBeat(String leg, Beats.Beat b, double t, int x, int feet, int z, int dx, int dz) {
        BeatLine L = new BeatLine(leg, x, feet, z, dx, dz);
        if (!Beats.build(b, L, t, 1, ctx.rank(), new java.util.Random(seed ^ leg.hashCode()))) return null;
        L.s.done();
        M m = mats();
        if (!dev.awaken.gates.realms.check.MoveProof.failures(L.s.view(paint(m, L.s)), L.moves, ctx.rank()).isEmpty()) return null;
        part(L.s);
        for (Move mv : L.moves) move(mv);
        for (Hold hd : L.holds) hold(hd.leg(), hd.kind(), hd.x(), hd.y(), hd.z(), hd.radius(), hd.count());
        for (Lesson ls : L.lessons) if (lessons().stream().noneMatch(o -> o.family() == ls.family())) lesson(ls);
        BeatSkin k = beats(m);
        for (int[] tp : L.traps) {
            boolean press = tp[6] == 0;
            trap(new Trap(leg, press ? TrapKind.PRESS : TrapKind.CRUMBLE, tp[0], tp[1], tp[2], tp[3], tp[4], tp[5], tp[7], tp[8],
                    press ? BeatSkin.or(k.tooth, rock(m, tp[0], tp[1], tp[2])) : BeatSkin.or(k.crumble, floor(m, L.s, tp[0], tp[1] - 1, tp[2])),
                    Blocks.AIR.defaultBlockState()));
        }
        boolean free = ctx.rank().ordinal() <= dev.awaken.progression.Rank.D.ordinal();
        course(new Course(leg, L.s.x0 - 1, L.yLo - 10, L.s.z0 - 1, L.s.x1 + 1, L.yHi + 6, L.s.z1 + 1, List.copyOf(L.catches), free,
                L.lowest == Integer.MAX_VALUE ? Integer.MIN_VALUE : L.lowest));
        line(new Line(leg, List.copyOf(L.beats), java.util.Set.copyOf(L.families), false, L.startX, L.startY, L.startZ, L.endX(), L.feet, L.endZ()));
        return new int[]{L.x(L.a, 0), L.feet, L.z(L.a, 0)};
    }

    /** A beat's piece in this land's own blocks, for the beat hall (SPEC §72.8): what the proof at generation reads. */
    public final Sculpt.Paint beatPaint(Part part) {
        return paint(mats(), part);
    }

    /** A beat's piece in this land's own blocks, for the proof at generation (nothing painted, nothing adorned). */
    final Sculpt.Paint paint(M m, Part part) {
        return new Sculpt.Paint() {
            @Override
            public BlockState solid(Sculpt.Role role, int arg, int x, int y, int z, boolean top) {
                return beatBlock(m, part, role, arg, x, y, z, top);
            }

            @Override
            public BlockState floor(int x, int y, int z) {
                return CrawlLand.this.floor(m, part, x, y, z);
            }

            @Override
            public BlockState rock(int x, int y, int z) {
                return CrawlLand.this.rock(m, x, y, z);
            }

            @Override
            public BlockState step() {
                return CrawlLand.this.step(m);
            }
        };
    }

    /** Where this land's blocks for the roles a move depends on differ in shape from plain stone's (BeatSkin.check). */
    public final List<String> beatShapes() {
        M m = mats();
        Sculpt probe = new Sculpt("probe");
        return BeatSkin.check((role, top) -> beatBlock(m, probe, role, 0, 0, 64, 0, top));
    }

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
        // last of all: what the beats' moves need is so, whatever a skin, a trim or a piece did (SPEC §72.2)
        for (Part p : near) if (p instanceof Sculpt s) s.enforce(v, this, m);
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
