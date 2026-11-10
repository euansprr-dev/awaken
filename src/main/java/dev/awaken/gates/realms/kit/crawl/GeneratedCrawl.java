package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A crawl nobody laid out by hand (SPEC §50.9): the run is rolled from the gate's seed, so one blueprint is a different
 * dungeon every time it forms. It always starts with a fight in sight of the Mouth, ends with a Warden and then the
 * Lord, and between them draws from a bag: more fights, a gallery of spitters, a hall of teeth, a shaft with a broken
 * ledge, an ambush. It turns as it goes and it goes down. There is always a side cell that makes a way round the first
 * fight, and usually a hidden cell off the ambush.
 *
 * <p>The land that extends this is a <b>skin</b>: what the rock, floors and doors are made of, what grows on the walls,
 * who lives there and what its traps are made of. A new skin is a new kind of dungeon; the layouts come free.
 */
public abstract class GeneratedCrawl<M extends Mats> extends CrawlLand<M> {
    private static final BlockState CLIMB_LIGHT = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 14);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final double[] AXES = {0, Math.PI / 2, Math.PI, Math.PI * 1.5};

    /** Who stands in the n-th fight (a skin has a few herds and goes round them). */
    protected abstract List<String> herd(int index);

    protected abstract List<String> ambushPack();

    /** The Warden's company; start it with "!name" to name the Warden itself. */
    protected abstract List<String> wardenPack();

    /** A spitter cell lit and unlit, a press's tooth, and the floor of a ledge that gives way. */
    protected abstract BlockState spitOn(M m);

    protected abstract BlockState spitOff(M m);

    protected abstract BlockState tooth(M m);

    protected abstract BlockState thinFloor(M m);

    /** How much rims and sides wander: 1 for something dug or grown, 0 for something built. */
    protected abstract double wild();

    /** The floor of a part that isn't thin. */
    protected abstract BlockState skinFloor(M m, Part part, int x, int y, int z);

    /** How many pillars stand in the Warden's room (none for a Warden that needs the floor). */
    protected int wardenPillars() {
        return 5;
    }

    /** Whatever the skin draws that is bigger than a cell of wall. */
    protected void skinSpecial(V v, M m) {}

    private final List<int[]> taken = new ArrayList<>(), thin = new ArrayList<>(), spitters = new ArrayList<>(), finds = new ArrayList<>();
    private int[] heartDoor;
    private Cave root;
    private int lordX, lordY, lordZ, lordR;

    protected GeneratedCrawl(Ctx ctx, long salt) {
        super(ctx, salt);
    }

    public final List<int[]> finds() {
        return finds;
    }

    private static double sx(double h) {
        return Math.sin(h);
    }

    private static double sz(double h) {
        return Math.cos(h);
    }

    private static int[] box(double x0, int y0, double z0, double x1, int y1, double z1, int pad) {
        return new int[]{(int) Math.floor(Math.min(x0, x1)) - pad, y0, (int) Math.floor(Math.min(z0, z1)) - pad, (int) Math.ceil(Math.max(x0, x1)) + pad, y1, (int) Math.ceil(Math.max(z0, z1)) + pad};
    }

    private int hits(int[] b, int spare) {
        int n = 0;
        for (int i = 0; i < taken.size() - spare; i++) {
            int[] t = taken.get(i);
            if (b[0] <= t[3] && b[3] >= t[0] && b[1] <= t[4] && b[4] >= t[1] && b[2] <= t[5] && b[5] >= t[2]) n++;
        }
        return n;
    }

    /** A door across a way at a point on it. */
    private void doorAcross(String leg, double x, int feetA, int feetB, double z, double heading, double half) {
        int w = (int) Math.ceil(half) + 3, px = (int) Math.round(x), pz = (int) Math.round(z), y0 = Math.min(feetA, feetB) - 2, y1 = Math.max(feetA, feetB) + 8;
        if (Math.abs(sx(heading)) >= Math.abs(sz(heading))) door(leg, px - 1, y0, pz - w, px + 1, y1, pz + w);
        else door(leg, px - w, y0, pz - 1, px + w, y1, pz + 1);
    }

    /** How long a run is: short about five legs, standard eight to ten, great fourteen or so with a second Warden. Rolled three in ten, about half, one in six. */
    public enum Size { SHORT, STANDARD, GREAT }

    /** The way a run goes: steadily down, down hard (two shafts and steep ways), up from the Mouth to a Lord at the top, or wide and nearly level. */
    public enum Shape { DELVE, PLUNGE, CLIMB, SPRAWL }

    private Size size;
    private Shape shape;
    private int mouthFeet;

    /** Your feet where you arrive (the way in is at 0, 0). */
    public final int mouthFeet() {
        return mouthFeet;
    }

    public final Size size() {
        return size;
    }

    public final Shape shape() {
        return shape;
    }

    /** Lays the whole run out. Call once, last thing in the skin's constructor. */
    protected final void generate() {
        Random r = new Random(seed * 0x9E3779B97F4A7C15L ^ 0xC2A7L);
        M m = mats();
        double wild = wild();
        long s = seed * 31 + 7;

        // what kind of run this seed is
        int roll = r.nextInt(100), turn = r.nextInt(100);
        size = roll < 30 ? Size.SHORT : roll < 83 ? Size.STANDARD : Size.GREAT;
        shape = turn < 40 ? Shape.DELVE : turn < 65 ? Shape.PLUNGE : turn < 82 ? Shape.CLIMB : Shape.SPRAWL;
        boolean up = shape == Shape.CLIMB;

        // what the run is: a fight first, then things out of the shape's bag with one ambush among them, then the Warden and the Lord;
        // a great run has a second stretch and a second Warden before the Lord
        List<Character> bag = new ArrayList<>(switch (shape) {
            case PLUNGE -> List.of('M', 'M', 'C', 'S', 'F', 'P', 'F');
            case CLIMB -> List.of('M', 'P', 'F', 'M', 'S', 'F', 'C');
            case SPRAWL -> List.of('F', 'P', 'C', 'F', 'S', 'F', 'C');
            default -> List.of('S', 'M', 'F', 'C', 'P', 'F', 'F');
        });
        // the first two of the bag are what makes the shape what it is; the rest are shuffled
        List<Character> rest = new ArrayList<>(bag.subList(2, bag.size()));
        Collections.shuffle(rest, r);
        List<Character> draw = new ArrayList<>(bag.subList(0, 2));
        draw.addAll(rest);
        int middle = size == Size.SHORT ? 1 + r.nextInt(2) : size == Size.STANDARD ? 3 + r.nextInt(2) : 3;
        List<Character> plan = new ArrayList<>();
        plan.add('F');
        List<Character> mid = new ArrayList<>(draw.subList(0, middle));
        Collections.shuffle(mid, r);
        mid.add(r.nextInt(mid.size() + 1), 'A');
        plan.addAll(mid);
        plan.add('W');
        if (size == Size.GREAT) {
            List<Character> more = new ArrayList<>(draw.subList(middle, Math.min(draw.size(), middle + 3)));
            more.add(r.nextInt(more.size() + 1), 'A');
            plan.addAll(more);
            plan.add('W');
        }
        plan.add('L');

        int feet = up ? 92 : 196; // a climb starts low and ends high; everything else starts high
        mouthFeet = feet;
        root = part(new Cave("root", 0, feet, 0, 7, 8, 6, 0.16 * wild, s++));
        taken.add(box(-9, feet - 4, -10, 9, feet + 10, 10, 2));
        site(SiteKind.MOUTH, 0, feet, 0, 6, "");
        site(SiteKind.PORTAL, 0, feet, -5, 2, "");
        List<String> none = List.of();

        double cx = 0, cz = 0, cr = 7, heading = 0;
        boolean straight = true; // the first way leads straight on from where you arrive, so the first thing you see is where to go
        int dens = 0, fights = 0, wardens = 0;
        java.util.Map<Character, Integer> count = new java.util.HashMap<>();
        String prevLeg = "root";
        boolean prevSeals = false;
        // each leg as it is made: id, kind, name, x, y, z, radius, site, pack; exits are filled in after
        List<Object[]> made = new ArrayList<>();
        made.add(new Object[]{"root", LegKind.ARRIVE, "The way in", 0, feet, 0, 6, -1, none});
        List<String[]> joins = new ArrayList<>();
        Cave firstFight = null, ambush = null;
        int[] ambushBox = null;
        double ambushHeading = 0;
        double sideX = 0, sideZ = 0, sideHeading = 0;
        int sideFeetB = 0;
        String afterFirst = null;

        for (int step = 0; step < plan.size(); step++) {
            char kind = plan.get(step);
            boolean axis = kind == 'S' || kind == 'P' || kind == 'M' || kind == 'C';
            // the way to this leg: how long, and how far it falls (or, on a climb, rises)
            int len = step == 1 ? 22 : shape == Shape.PLUNGE ? 20 + r.nextInt(6) : 17 + r.nextInt(8);
            int fall = shape == Shape.PLUNGE ? 6 + r.nextInt(3) : shape == Shape.SPRAWL ? 4 + r.nextInt(2) : 4 + r.nextInt(Math.min(4, len / 3 - 3));
            if (kind == 'L') {
                len = 24;
                fall = 8;
            }
            int drop = up ? -fall : fall;
            // which way: straight on out of a shaft; else try a few turns and keep the first that runs into nothing
            List<Double> ways = new ArrayList<>();
            if (straight) ways.add(heading);
            else if (axis) {
                for (double a : AXES) if (Math.cos(a - heading) > -0.3) ways.add(a);
                Collections.shuffle(ways, r);
            } else {
                for (double t : new double[]{0, 0.6, -0.6, 1.15, -1.15, 1.7, -1.7}) ways.add(heading + t);
                Collections.shuffle(ways, r);
            }
            int wide = kind == 'L' ? 22 : kind == 'W' ? (wardenPillars() == 0 ? 15 : 13) : kind == 'M' ? 11 : kind == 'A' ? 13 + r.nextInt(2) : 10 + r.nextInt(4) + (shape == Shape.SPRAWL ? 2 : 0);
            int high = kind == 'L' ? 16 : kind == 'A' ? 6 : 7 + r.nextInt(3);
            int run = kind == 'S' ? 26 + 5 * r.nextInt(2) : kind == 'P' ? 16 : kind == 'C' ? 30 + r.nextInt(9) : 0, slope = kind == 'S' ? (up ? -6 : 6) : 0;
            double best = heading;
            int fewest = Integer.MAX_VALUE;
            for (double h : ways) {
                double ax = cx + sx(h) * cr * 0.9, az = cz + sz(h) * cr * 0.9, ex = ax + sx(h) * len, ez = az + sz(h) * len;
                int to = feet - drop, lo = Math.min(feet, to), hi = Math.max(feet, to);
                int n = hits(box(ax, lo - 4, az, ex, hi + 9, ez, 5), 1);
                if (run > 0) n += hits(box(ex, Math.min(to, to - slope) - (kind == 'C' ? 14 : 4), ez, ex + sx(h) * run, Math.max(to, to - slope) + 10, ez + sz(h) * run, 7), 0);
                else {
                    double mx = ex + sx(h) * (wide - 1.5), mz = ez + sz(h) * (wide - 1.5);
                    n += hits(box(mx - wide, to - (kind == 'M' ? 34 : 4), mz - wide, mx + wide, to + (kind == 'M' ? 42 : high + 4), mz + wide, 5), 0);
                }
                if (n < fewest) {
                    fewest = n;
                    best = h;
                }
                if (n == 0) break;
            }
            double h = best;
            double ax = cx + sx(h) * cr * 0.9, az = cz + sz(h) * cr * 0.9, ex = ax + sx(h) * len, ez = az + sz(h) * len;
            int to = feet - drop;
            int nth = count.merge(kind, 1, Integer::sum);
            String id = switch (kind) {
                case 'F' -> "fight" + (fights + 1);
                case 'A' -> "ambush";
                case 'S' -> "gallery";
                case 'P' -> "teeth";
                case 'M' -> "shaft";
                case 'C' -> "chasm";
                case 'W' -> "warden";
                default -> "lord";
            };
            if (kind != 'F' && nth > 1) id += nth;
            double way = kind == 'L' ? 2.6 : 2.3;
            part(new Tunnel("way" + step, (int) Math.round(ax), feet, (int) Math.round(az), (int) Math.round(ex), to, (int) Math.round(ez), way, kind == 'L' ? 5 : 4, 0.4 * wild, s++));
            taken.add(box(ax, Math.min(feet, to) - 4, az, ex, Math.max(feet, to) + 9, ez, 5));
            // the doors on this way: the last fight's, six along it, and this fight's, six short of its end; the way to the Lord has the heart's door instead
            if (kind == 'L') {
                int mx = (int) Math.round((ax + ex) / 2), mz = (int) Math.round((az + ez) / 2), w = 6;
                int lo = Math.min(feet, to) - 2, hi = Math.max(feet, to) + 8;
                heartDoor = Math.abs(sx(h)) >= Math.abs(sz(h)) ? new int[]{mx - 1, lo, mz - w, mx + 1, hi, mz + w} : new int[]{mx - w, lo, mz - 1, mx + w, hi, mz + 1};
            } else {
                if (prevSeals) doorAcross(prevLeg, ax + sx(h) * 6, feet, to, az + sz(h) * 6, h, way);
                if (kind == 'F' || kind == 'A' || kind == 'W') doorAcross(id, ex - sx(h) * 6, feet, to, ez - sz(h) * 6, h, way);
            }
            if (step == 1) {
                // where the side cell's second way joins this one: past the first fight's door, short of the next one
                sideX = ax + sx(h) * 11;
                sideZ = az + sz(h) * 11;
                sideHeading = h;
                sideFeetB = feet - (int) Math.round(drop * 11.0 / len);
                afterFirst = id;
            }
            joins.add(new String[]{prevLeg, id});
            feet = to;
            straight = false;
            prevSeals = kind == 'F' || kind == 'A' || kind == 'W';
            int ddx = (int) Math.round(sx(h)), ddz = (int) Math.round(sz(h)), x0 = (int) Math.round(ex), z0 = (int) Math.round(ez);
            switch (kind) {
                case 'F', 'A', 'W', 'L' -> {
                    double mx = ex + sx(h) * (wide - 1.5), mz = ez + sz(h) * (wide - 1.5);
                    int ix = (int) Math.round(mx), iz = (int) Math.round(mz);
                    Cave room;
                    if (kind == 'W') room = part(new Arena(id, ix, feet, iz, wide, 10, wardenPillars(), 0.06 * wild, s++));
                    else if (kind == 'L') room = part(new Arena(id, ix, feet, iz, wide, high, 8, 0.06 * wild, s++));
                    else room = part(new Cave(id, ix, feet, iz, wide, Math.max(9, wide - r.nextInt(3)), high, 0.16 * wild, s++));
                    taken.add(box(mx - wide, feet - 4, mz - wide, mx + wide, feet + high + 4, mz + wide, 4));
                    if (kind == 'A' && ambush == null) ambushBox = taken.getLast();
                    if (kind == 'W') {
                        site(SiteKind.WARDEN, ix, feet, iz, wide, "");
                        made.add(new Object[]{id, LegKind.WARDEN, "A Warden's room", ix, feet, iz, wide, wardens++, wardenPack()});
                    } else if (kind == 'L') {
                        site(SiteKind.LORD, ix, feet, iz, wide, ctx.bp().heart().lord());
                        made.add(new Object[]{id, LegKind.LORD, "The Lord's hall", ix, feet, iz, wide, -1, none});
                        lordX = ix;
                        lordY = feet;
                        lordZ = iz;
                        lordR = wide;
                    } else {
                        site(SiteKind.DEN, ix, feet, iz, 9, "");
                        int radius = (int) Math.min(room.rx, room.rz) - 1;
                        if (kind == 'F') {
                            made.add(new Object[]{id, LegKind.FIGHT, "A den", ix, feet, iz, radius, dens, herd(fights)});
                            if (fights == 0) firstFight = room;
                            else if (r.nextBoolean()) find(room, h + 2.2);
                            fights++;
                        } else {
                            made.add(new Object[]{id, LegKind.AMBUSH, "A quiet room", ix, feet, iz, Math.min(7, radius), dens, ambushPack()});
                            if (ambush == null) {
                                ambush = room;
                                ambushHeading = h;
                            }
                        }
                        dens++;
                    }
                    cx = mx;
                    cz = mz;
                    cr = kind == 'F' || kind == 'A' ? Math.min(room.rx, room.rz) : wide;
                }
                case 'S', 'P' -> {
                    int bx = x0 + ddx * run, bz = z0 + ddz * run, top = feet;
                    feet -= slope;
                    Tunnel hall = part(new Tunnel(id, x0, top, z0, bx, feet, bz, kind == 'P' ? 3.0 : 2.5, kind == 'P' ? 5 : 4, 0, s++));
                    taken.add(box(x0, Math.min(top, feet) - 4, z0, bx, Math.max(top, feet) + 9, bz, 5));
                    int px = -ddz, pz = ddx;
                    if (kind == 'S') {
                        // a cell every five along it, on one wall and then the other, a second apart
                        for (int i = 0, k = 5; k <= run - 4; i++, k += 5) {
                            int x = x0 + ddx * k, z = z0 + ddz * k, side = i % 2 == 0 ? 1 : -1, y = Part.airFrom(hall.feet2(x, z)) + 1;
                            spitters.add(new int[]{x + px * 3 * side, y, z + pz * 3 * side});
                            trap(new Trap(id, TrapKind.SPIT, x + px * 3 * side, y, z + pz * 3 * side, x - px * 3 * side, y, z - pz * 3 * side, 60, i * 20 % 60, spitOn(m), spitOff(m)));
                        }
                    } else {
                        int[] phase = {0, 70, 40};
                        for (int i = 0; i < 3; i++) {
                            int x = x0 + ddx * (4 + i * 4), z = z0 + ddz * (4 + i * 4);
                            trap(new Trap(id, TrapKind.PRESS, Math.min(x - px * 3, x + px * 3), feet, Math.min(z - pz * 3, z + pz * 3), Math.max(x - px * 3, x + px * 3), feet + 2, Math.max(z - pz * 3, z + pz * 3), 100, phase[i], tooth(m), AIR));
                        }
                    }
                    made.add(new Object[]{id, LegKind.TRAP, kind == 'S' ? "A gallery" : "A hall of teeth", (x0 + bx) / 2, (top + feet) / 2, (z0 + bz) / 2, run / 2 + 2, -1, none});
                    cx = bx;
                    cz = bz;
                    cr = 0;
                }
                case 'C' -> {
                    // pillars over a pool: on a climb they stay level, otherwise they step down a little as they go
                    Chasm pit = part(new Chasm(id, x0, feet, z0, ddx, ddz, run, 5, 8, 10, up ? 0 : 3, s++));
                    int bx = x0 + ddx * pit.length, bz = z0 + ddz * pit.length;
                    taken.add(box(x0, feet - 14, z0, bx, feet + 10, bz, 8));
                    List<int[]> ledges = new ArrayList<>();
                    for (int i = -1; i <= pit.pillars(); i++) {
                        int[] at = pit.stand(i);
                        ledges.add(new int[]{at[0], at[1], at[2]});
                    }
                    course(new Course(id, Math.min(x0, bx) - 7, feet - 12, Math.min(z0, bz) - 7, Math.max(x0, bx) + 7, feet + 9, Math.max(z0, bz) + 7, ledges));
                    made.add(new Object[]{id, LegKind.MOVEMENT, "A chasm", x0 + ddx, feet, z0 + ddz, pit.length / 2 + 2, -1, none});
                    feet = pit.exitFeet;
                    cx = bx;
                    cz = bz;
                    cr = 0;
                }
                default -> {
                    // the shaft. Going down: in at the top on the near side, out a turn and a half and twenty-four blocks lower on the far side,
                    // over three breaks in the ledge. Going up it is the same shaft walked the other way, with no breaks (nobody jumps a gap upward).
                    int near = (int) Math.floorMod(4 - Math.round(h / (Math.PI / 2)), 4), quarter = up ? (near + 2) % 4 : near;
                    double mx = ex + sx(h) * 9.4, mz = ez + sz(h) * 9.4;
                    int ix = (int) Math.round(mx), iz = (int) Math.round(mz), top = up ? feet + 24 : feet;
                    Shaft sh = up ? part(new Shaft(id, quarter, ix, top, iz, 11, 3.2, 16, 26, top - 28, 3, s++))
                            : part(new Shaft(id, quarter, ix, top, iz, 11, 3.2, 16, 26, top - 28, 3, s++, 0.25, 0.75, 1.25));
                    taken.add(box(mx - 12, top - 32, mz - 12, mx + 12, top + 16, mz + 12, 4));
                    // thin floor on the far side of the first turn and the near side of the second
                    thin.add(arc(ix, iz, (quarter + 2) % 4, top - 8));
                    thin.add(arc(ix, iz, quarter, top - 16));
                    for (int[] t : thin.subList(thin.size() - 2, thin.size())) trap(new Trap(id, TrapKind.CRUMBLE, t[0], t[1], t[2], t[3], t[4], t[5], 0, 0, thinFloor(m), AIR));
                    List<int[]> ledges = new ArrayList<>();
                    for (double turns : up ? new double[]{1.48, 1.2, 0.7, 0.2} : new double[]{0.02, 0.31, 0.81, 1.31}) {
                        int[] at = sh.stand(turns);
                        ledges.add(new int[]{at[0], at[1] + 1, at[2]});
                    }
                    course(new Course(id, ix - 13, top - 28, iz - 13, ix + 13, top + 14, iz + 13, ledges));
                    int[] start = sh.stand(up ? 1.48 : 0.02);
                    made.add(new Object[]{id, LegKind.MOVEMENT, "A shaft", start[0], feet, start[2], 12, -1, none});
                    feet = up ? top : top - 24;
                    cx = mx;
                    cz = mz;
                    cr = 9.4 / 0.9;
                    straight = true;
                }
            }
            heading = h;
            prevLeg = id;
        }

        // the side cell: off the first fight, and back into the way beyond it, so there is always a way round
        if (firstFight != null) {
            double out = sideHeading + (r.nextBoolean() ? 1 : -1) * Math.PI / 2;
            int tx = (int) Math.round(sideX + sx(out) * 13), tz = (int) Math.round(sideZ + sz(out) * 13), tf = (firstFight.feet + sideFeetB) / 2;
            part(new Cave("side", tx, tf, tz, 5, 5, 5, 0.16 * wild, s++));
            double a = Math.atan2(tx - firstFight.cx, tz - firstFight.cz);
            int fx = (int) Math.round(firstFight.cx + sx(a) * Math.min(firstFight.rx, firstFight.rz) * 0.9), fz = (int) Math.round(firstFight.cz + sz(a) * Math.min(firstFight.rx, firstFight.rz) * 0.9);
            part(new Tunnel("side_in", fx, firstFight.feet, fz, tx, tf, tz, 1.6, 3, 0.4 * wild, s++));
            part(new Tunnel("side_out", tx, tf, tz, (int) Math.round(sideX), sideFeetB, (int) Math.round(sideZ), 1.6, 3, 0.4 * wild, s++));
            doorAcross("fight1", fx + sx(a) * 5, firstFight.feet, tf, fz + sz(a) * 5, a, 1.6);
            finds.add(new int[]{tx + 2, tf, tz + 1});
            made.add(new Object[]{"side", LegKind.BREATHER, "A side cell", tx, tf, tz, 5, -1, none});
            joins.add(new String[]{"fight1", "side"});
            joins.add(new String[]{"side", afterFirst});
        }
        // the hidden cell: a thin way off the ambush, to one side, if there is room for it
        if (ambush != null) {
            for (int side : r.nextBoolean() ? new int[]{1, -1} : new int[]{-1, 1}) {
                double out = ambushHeading + side * Math.PI / 2, rim = Math.min(ambush.rx, ambush.rz) * 0.9;
                double ax = ambush.cx + sx(out) * rim, az = ambush.cz + sz(out) * rim, ex = ax + sx(out) * 12, ez = az + sz(out) * 12, mx = ex + sx(out) * 4, mz = ez + sz(out) * 4;
                int[] own = ambushBox;
                taken.remove(own);
                boolean blocked = hits(box(ex - 6, ambush.feet - 5, ez - 6, mx + 6, ambush.feet + 9, mz + 6, 3), 0) > 0;
                taken.add(own);
                if (blocked) continue;
                int hx = (int) Math.round(mx), hz = (int) Math.round(mz), hf = ambush.feet - 1;
                part(new Tunnel("slip", (int) Math.round(ax), ambush.feet, (int) Math.round(az), (int) Math.round(ex), hf, (int) Math.round(ez), 1.4, 3, 0.4 * wild, s++));
                part(new Cave("hidden", hx, hf, hz, 5.5, 5.5, 5, 0.16 * wild, s++));
                finds.add(new int[]{hx - 1, hf, hz - 1});
                finds.add(new int[]{hx + 2, hf, hz + 1});
                made.add(new Object[]{"hidden", LegKind.HIDDEN, "A place kept back", hx, hf, hz, 5, -1, none});
                joins.add(new String[]{"ambush", "hidden"});
                break;
            }
        }
        done();
        for (int[] f : finds) site(SiteKind.FIND, f[0], f[1], f[2], 2, "");
        clearSites();
        for (Object[] l : made) {
            List<String> exits = new ArrayList<>();
            for (String[] j : joins) if (j[0].equals(l[0])) exits.add(j[1]);
            @SuppressWarnings("unchecked") List<String> pack = (List<String>) l[8];
            leg((String) l[0], (LegKind) l[1], (String) l[2], (Integer) l[3], (Integer) l[4], (Integer) l[5], (Integer) l[6], (Integer) l[7], pack, "", exits.toArray(new String[0]));
        }
    }

    /** The stretch of a shaft's ledge on one side of it (0 north, 1 east, 2 south, 3 west), seven cells long, at about a height. */
    private static int[] arc(int cx, int cz, int side, int feet) {
        return switch (side) {
            case 0 -> new int[]{cx - 3, feet - 3, cz - 12, cx + 3, feet + 1, cz - 8};
            case 1 -> new int[]{cx + 8, feet - 3, cz - 3, cx + 12, feet + 1, cz + 3};
            case 2 -> new int[]{cx - 3, feet - 3, cz + 8, cx + 3, feet + 1, cz + 12};
            default -> new int[]{cx - 12, feet - 3, cz - 3, cx - 8, feet + 1, cz + 3};
        };
    }

    private void find(Cave room, double heading) {
        double d = Math.min(room.rx, room.rz) - 3;
        finds.add(new int[]{(int) Math.round(room.cx + sx(heading) * d), room.feet, (int) Math.round(room.cz + sz(heading) * d)});
    }

    private static boolean in(int[] box, int x, int y, int z) {
        return x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
    }

    @Override
    protected final boolean thin(Part part, int x, int y, int z) {
        if (!(part instanceof Shaft)) return false;
        for (int[] t : thin) if (in(t, x, y, z)) return true;
        return false;
    }

    @Override
    protected final BlockState floor(M m, Part part, int x, int y, int z) {
        return thin(part, x, y, z) ? thinFloor(m) : skinFloor(m, part, x, y, z);
    }

    @Override
    protected final void special(V v, M m) {
        if (heartDoor != null) {
            BlockState door = doorBlock(m);
            for (int x = Math.max(heartDoor[0], v.x0 - 1); x <= Math.min(heartDoor[3], v.x0 + 16); x++) for (int z = Math.max(heartDoor[2], v.z0 - 1); z <= Math.min(heartDoor[5], v.z0 + 16); z++) {
                for (int y = heartDoor[1]; y <= heartDoor[4]; y++) if (open(x, y, z) && v.get(x, y, z) == null) v.set(x, y, z, door);
            }
        }
        for (int[] sp : spitters) v.set(sp[0], sp[1], sp[2], spitOff(m));
        for (int[] f : finds) v.set(f[0], f[1], f[2], m.cache);
        // a crack in the roof over the way in lets the day down just ahead of where you stand
        for (int x = -3; x <= 3; x++) for (int y = root.roof(x, 4); y <= rockTop(); y++) v.set(x, y, 4, null);
        skinSpecial(v, m);
    }

    /**
     * A chasm's crossing is kept clear whatever a skin grows: nothing with a body stands in the air over the pool between
     * the pillars, or on them, however a skin's rule for "well over your head" reads a floor that is ten blocks down.
     * (The strip by each wall is left alone: the way back up runs along one, and a wall may have what it likes on it.)
     */
    @Override
    protected final void finish(V v, M m) {
        for (Part p : parts) {
            if (!(p instanceof Chasm c) || !p.near(v)) continue;
            for (int x = Math.max(c.x0, v.x0 - 1); x <= Math.min(c.x1, v.x0 + 16); x++) for (int z = Math.max(c.z0, v.z0 - 1); z <= Math.min(c.z1, v.z0 + 16); z++) {
                int f = c.feet2(x, z);
                if (f == NONE || Math.abs(-(x - c.ax) * c.dz + (z - c.az) * c.dx) > c.half - 2) continue;
                for (int y = Part.airFrom(f); y <= c.top + 3; y++) if (V.holds(v.get(x, y, z))) v.set(x, y, z, null);
            }
        }
        // A climb is never left dark, whatever a skin does or forgets: an unseen light over the ledge every eighth of a turn
        // down a shaft, and over each shelf and pillar of a crossing. (Skins light rooms; two of them lit no shaft at all, and
        // a jump nobody can see is not a jump.)
        for (Part p : parts) {
            if (!p.near(v)) continue;
            if (p instanceof Shaft sh) {
                for (double t = 0; t <= 1.5; t += 0.125) {
                    int[] at = sh.stand(t);
                    v.add(at[0], at[1] + 3, at[2], CLIMB_LIGHT);
                }
            } else if (p instanceof Chasm c) {
                for (int i = -1; i <= c.pillars(); i++) {
                    int[] at = c.stand(i);
                    v.add(at[0], at[1] + 3, at[2], CLIMB_LIGHT);
                }
            }
        }
    }

    @Override
    public List<Edit> change(String moment) {
        if (heartDoor != null && (moment.equals("heart_open") || moment.equals("heart_shut"))) {
            List<Edit> out = new ArrayList<>();
            BlockState to = moment.equals("heart_open") ? AIR : doorBlock(mats());
            for (int x = heartDoor[0]; x <= heartDoor[3]; x++) for (int y = heartDoor[1]; y <= heartDoor[4]; y++) for (int z = heartDoor[2]; z <= heartDoor[5]; z++) if (open(x, y, z)) out.add(new Edit(x, y, z, to));
            return out;
        }
        return super.change(moment);
    }

    @Override
    public boolean inHeart(int x, int y, int z) {
        return (x - lordX) * (x - lordX) + (z - lordZ) * (z - lordZ) < (lordR - 1) * (lordR - 1) && y < lordY + 18 && y > lordY - 4;
    }

    @Override
    public String heartClosedLine() {
        return "The way on from here is shut. Whatever is keeping it shut is in this room with you.";
    }

    @Override
    public Direction.Axis portalAxis() {
        return Direction.Axis.X;
    }

    @Override
    public int biome(int x, int y, int z) {
        return 0;
    }

    @Override
    public float mouthYaw() {
        return 0;
    }
}
