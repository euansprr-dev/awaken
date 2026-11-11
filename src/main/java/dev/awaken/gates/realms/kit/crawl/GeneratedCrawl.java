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
 * ledge, a gulf to cross, an ambush. It turns as it goes and it goes down. There is always a side cell that makes a way
 * round the first fight, and usually a hidden cell off the ambush.
 *
 * <p>No two legs are cut alike either: every room rolls a form, every Warden's room and Lord's hall a plan, every gulf a
 * way across (SPEC §50.9, "No two alike"). Which of them a run may roll is its kind's {@link Style}.
 *
 * <p>The land that extends this is a <b>skin</b>: what the rock, floors and doors are made of, what grows on the walls,
 * who lives there, what its traps are made of and what it is cut like. A new skin is a new kind of dungeon; the layouts
 * come free.
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

    /**
     * What a kind of dungeon is cut like (SPEC §50.9): the forms its rooms may take, the plans of its Wardens' rooms and
     * its Lord's hall, the ways across its gulfs, whether its shafts are square, and how wide and high its ways are.
     * A form listed twice is rolled twice as often.
     */
    public record Style(List<Cave.Form> rooms, List<Arena.Plan> wardens, List<Arena.Plan> lords, List<Chasm.Kind> crossings, boolean squareShafts, double wayHalf, int wayHigh) {}

    /**
     * The small pieces a kind's surfaces are finished with (SPEC §50.9 "Depth"), any of them null or empty for none: the
     * stairs that stand at a wall's foot and hang where a roof steps, a drift that lies one layer deep at the foot of
     * things, bits scattered underfoot, what grows on the floor, what hangs from the roof, and how thickly all of it
     * comes (1 the usual). Nothing here has a body but the stairs.
     */
    public record Trim(BlockState footStairs, BlockState roofStairs, BlockState drift, BlockState bits, List<BlockState> plants, List<BlockState> hangs, double growth) {}

    /** This kind's small pieces, or null for none. */
    protected Trim trim(M m) {
        return null;
    }

    /** A block by its id: one of ours ("ember_cinders"), or anyone's with its namespace ("minecraft:cobweb"). */
    protected static BlockState named(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(id.indexOf(':') < 0 ? dev.awaken.Awaken.id(id) : net.minecraft.resources.Identifier.parse(id)).defaultBlockState();
    }

    protected static List<BlockState> namedAll(String... ids) {
        List<BlockState> out = new ArrayList<>();
        for (String id : ids) out.add(named(id));
        return out;
    }

    /** The colour of this kind's light in the air: what its mist takes on and its shafts carry. */
    protected int airTint() {
        return 0xffd9a0;
    }

    /**
     * The air every rolled dungeon has, on top of what its skin asked for (SPEC §50.9 "Air"), within what Gate Air takes:
     * a soft light over the middle of every room something is fought in, so its mist is lit and coloured; still water and
     * a low mist in every gulf and at the foot of every shaft; a shaft of light down every shaft. Call it last in a skin's
     * {@code air()}.
     */
    protected final dev.awaken.gates.world.FloorAir airOf(dev.awaken.gates.realms.kit.AirPlan air) {
        int tint = airTint();
        for (Part p : parts) {
            if (p instanceof Arena a) {
                if (air.lights() < 90) air.light(a.cx, a.feet + 5, a.cz, tint, (float) Math.min(26, a.rx * 1.2), 0.55F);
            } else if (p instanceof Cave c && (p.name.startsWith("fight") || p.name.startsWith("ambush") || p.name.equals("vault") || p.name.equals("hidden"))) {
                if (air.lights() < 90) air.light(c.cx, c.feet + 4, c.cz, tint, (float) Math.min(20, Math.max(c.rx, c.rz) * 1.2), 0.5F);
            } else if (p instanceof Chasm g) {
                int pool = g.top - g.depth + g.water, mx = g.ax + g.dx * g.length / 2, mz = g.az + g.dz * g.length / 2;
                if (air.waters() < 12) air.water(pool, g.x0, g.z0, g.x1, g.z1);
                if (air.mists() < 3) air.mist(pool, 4, tint, 0.55F);
                if (air.lights() < 90) air.light(mx, g.top + 3, mz, tint, 18, 0.45F);
            } else if (p instanceof Shaft sh) {
                if (air.waters() < 12) air.water(sh.bottom + sh.water, sh.x0, sh.z0, sh.x1, sh.z1);
                if (air.shafts() < 10) air.shaft(sh.cx, sh.top + 10, sh.cz, sh.cx, sh.bottom + sh.water, sh.cz, 4, tint, 0.5F);
                if (air.mists() < 3) air.mist(sh.bottom + sh.water, 5, tint, 0.5F);
            }
        }
        // light down every well: the day's colour if the way in has one, the kind's own otherwise; it leans a little, as light through a slot does
        int day = air.firstShaft(tint);
        for (Well w : wells) {
            if (air.shafts() >= 23) break;
            air.shaft(w.x, w.top, w.z, w.x + w.lz * 2, w.floor, w.z + w.lx * 2, 2.4F, day, 0.6F);
            if (air.lights() < 94) air.light(w.x + w.lz * 2, w.floor + 2, w.z + w.lx * 2, day, 7, 0.45F);
        }
        // mist on the floors of the biggest rooms: the Lord's (a skin lays its own), then the Wardens', then the dens, widest first
        int fog = air.firstMist(tint >> 1 & 0x7f7f7f);
        List<Cave> rooms = new ArrayList<>();
        for (Part p : parts) if (p instanceof Cave c && !p.name.equals("root") && !p.name.equals("side")) rooms.add(c);
        rooms.sort((a, b) -> Double.compare((b instanceof Arena ? 4 : 1) * b.rx * b.rz, (a instanceof Arena ? 4 : 1) * a.rx * a.rz));
        for (Cave c : rooms) {
            if (air.mists() >= 6) break;
            if (!air.mistNear(c.feet + 1, 3)) air.mist(c.feet + 1, 3, fog, 0.4F);
        }
        return air.build();
    }

    /** How bold a kind's ground is, 1 the usual, 0 for level floors everywhere. */
    protected double groundDepth() {
        return 1;
    }

    /** What this kind is cut like. As it stands: something dug has caves, pits and rifts; something built has halls, naves and bridges. */
    protected Style style() {
        return wild() >= 0.5
                ? new Style(List.of(Cave.Form.OVAL, Cave.Form.OVAL, Cave.Form.RING, Cave.Form.PIT, Cave.Form.RIFT, Cave.Form.TERRACE), List.of(Arena.Plan.RING, Arena.Plan.TIERS),
                        List.of(Arena.Plan.RING, Arena.Plan.ROTUNDA, Arena.Plan.TIERS),
                        List.of(Chasm.Kind.PILLARS, Chasm.Kind.LEDGES, Chasm.Kind.HANGERS, Chasm.Kind.STEPS, Chasm.Kind.FORD, Chasm.Kind.BRIDGE), false, 2.3, 4)
                : new Style(List.of(Cave.Form.HALL, Cave.Form.PIERS, Cave.Form.CROSS, Cave.Form.TERRACE, Cave.Form.PIT, Cave.Form.RING), List.of(Arena.Plan.SQUARE, Arena.Plan.NAVE, Arena.Plan.RING),
                        List.of(Arena.Plan.NAVE, Arena.Plan.CROSS, Arena.Plan.SQUARE, Arena.Plan.ROTUNDA),
                        List.of(Chasm.Kind.BRIDGE, Chasm.Kind.BEAMS, Chasm.Kind.FALLING, Chasm.Kind.HANGERS, Chasm.Kind.LEDGES, Chasm.Kind.STEPS, Chasm.Kind.FORD), true, 2.6, 5);
    }

    private final List<int[]> taken = new ArrayList<>(), thin = new ArrayList<>(), spitters = new ArrayList<>(), finds = new ArrayList<>();
    private int[] heartDoor;
    private Cave root;
    private Arena lordHall;
    /** Where light comes down (SPEC §50.9 "Air"): a slot in a room's roof, three cells by one, going up to a light nobody sees the source of. */
    private record Well(Cave room, int x, int z, int lx, int lz, int top, int floor) {}

    private final List<Well> wells = new ArrayList<>();
    private static final BlockState WELL_LIGHT = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15);
    /** Every form, plan and crossing this run rolled: for whoever checks that all of them turn up. */
    private final java.util.Set<String> used = new java.util.TreeSet<>();

    protected GeneratedCrawl(Ctx ctx, long salt) {
        super(ctx, salt);
    }

    public final List<int[]> finds() {
        return finds;
    }

    public final java.util.Set<String> used() {
        return used;
    }

    private static <T> T pick(Random r, List<T> from) {
        return from.get(r.nextInt(from.size()));
    }

    /** The rooms whose floor is all one level: for the first fight and the ambush, which other ways lead off. */
    private static List<Cave.Form> level(List<Cave.Form> rooms) {
        List<Cave.Form> out = new ArrayList<>();
        for (Cave.Form f : rooms) if (f != Cave.Form.TERRACE && f != Cave.Form.PIT && f != Cave.Form.RIFT) out.add(f);
        return out.isEmpty() ? List.of(Cave.Form.OVAL) : out;
    }

    /** The crossings that don't go down: nobody jumps upward, so on a climb the way across stays level. */
    private static List<Chasm.Kind> flat(List<Chasm.Kind> crossings) {
        List<Chasm.Kind> out = new ArrayList<>();
        for (Chasm.Kind k : crossings) if (k != Chasm.Kind.STEPS) out.add(k);
        return out.isEmpty() ? List.of(Chasm.Kind.PILLARS) : out;
    }

    private static String called(Chasm.Kind kind) {
        return switch (kind) {
            case BRIDGE -> "A broken bridge";
            case BEAMS -> "A walk along the rafters";
            case LEDGES -> "A ledge over a drop";
            case HANGERS -> "Hanging platforms";
            case STEPS -> "Great steps down";
            case FORD -> "A ford";
            case FALLING -> "A bridge that gives way";
            default -> "A chasm";
        };
    }

    /** What goes with a gulf: where a long fall puts you back, and the trap of whatever in it gives way. */
    private void rig(String id, Chasm pit, M m) {
        pit.walls = wild() < 0.5 ? 1 : 2;
        int bx = pit.ax + pit.dx * pit.length, bz = pit.az + pit.dz * pit.length;
        List<int[]> ledges = new ArrayList<>();
        for (int i = -1; i <= pit.pillars(); i++) {
            // what gave way under someone is no place to put them back on: off a falling deck it is one shelf or the other
            if (pit.kind == Chasm.Kind.FALLING && i >= 0 && i < pit.pillars()) continue;
            int[] at = pit.stand(i);
            ledges.add(new int[]{at[0], at[1], at[2]});
        }
        course(new Course(id, Math.min(pit.ax, bx) - pit.half - 2, pit.top - pit.depth - 2, Math.min(pit.az, bz) - pit.half - 2, Math.max(pit.ax, bx) + pit.half + 2, pit.top + 9, Math.max(pit.az, bz) + pit.half + 2, ledges));
        for (int[] t : pit.thin()) {
            thin.add(t);
            trap(new Trap(id, TrapKind.CRUMBLE, t[0], t[1], t[2], t[3], t[4], t[5], 0, 0, thinFloor(m), AIR));
        }
    }

    /** The crossings a branch may be: the ones that are as easy to come back over as to go out on. */
    private static List<Chasm.Kind> thereAndBack(List<Chasm.Kind> crossings) {
        List<Chasm.Kind> out = new ArrayList<>();
        for (Chasm.Kind k : crossings) if (k != Chasm.Kind.STEPS && k != Chasm.Kind.PILLARS) out.add(k);
        return out.isEmpty() ? List.of(Chasm.Kind.HANGERS) : out;
    }

    /** The nearest of north, east, south and west to a heading: what was built stands square, however the way came to it. */
    private static double snap(double h) {
        return Math.round(h / (Math.PI / 2)) * (Math.PI / 2);
    }

    /** Your feet on a room's floor at a column, or beside it where that column isn't floor. */
    private static int near(Cave room, int x, int z) {
        if (room.feet2(x, z) != NONE) return room.floorAt(x, z);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) if (room.feet2(x + d[0], z + d[1]) != NONE) return room.floorAt(x + d[0], z + d[1]);
        return room.feet;
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
    /** A stand: one great room of waves in place of a run, and how many waves (SPEC §50.9). */
    private boolean stand;
    private int waves;
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
        Style style = style();
        boolean built = wild < 0.5;
        // depth: ground that rises and falls everywhere, and walls that lump and lean where the place was dug
        double deepGround = groundDepth(), rough = built ? 0 : wild;
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
        // from a standard run up there is always a gulf to cross: how it is crossed is what no two runs share. It takes the
        // place of a second fight if there is one to spare, else of whatever came last out of the bag
        if (size != Size.SHORT && !mid.contains('C')) mid.set(mid.lastIndexOf('F') >= 0 ? mid.lastIndexOf('F') : mid.size() - 1, 'C');
        Collections.shuffle(mid, r);
        mid.add(r.nextInt(mid.size() + 1), 'A');
        plan.addAll(mid);
        plan.add('W');
        if (size == Size.GREAT) {
            List<Character> more = new ArrayList<>(draw.subList(middle, Math.min(draw.size(), middle + 3)));
            // and a great run's second stretch has a climb or a crossing of its own
            if (!more.contains('C') && !more.contains('M')) more.set(more.lastIndexOf('F') >= 0 ? more.lastIndexOf('F') : more.size() - 1, up ? 'M' : 'C');
            more.add(r.nextInt(more.size() + 1), 'A');
            plan.addAll(more);
            plan.add('W');
        }
        plan.add('L');
        // a stand (a blueprint's land.params "stand", SPEC §50.9): no run to speak of. One great room where they come at you
        // wave after wave, six at E rank and one more for every rank above, then its Warden and the Lord
        stand = ctx.bp().land().params().getOrDefault("stand", 0.0) > 0;
        if (stand) {
            size = Size.SHORT;
            waves = 6 + ctx.rank().ordinal();
            plan.clear();
            plan.add('A');
            plan.add('W');
            plan.add('L');
        }

        int feet = up ? 92 : 196; // a climb starts low and ends high; everything else starts high
        mouthFeet = feet;
        root = part(new Cave("root", 0, feet, 0, 7, 8, 6, 0.16 * wild, s++));
        root.depth(0, rough);
        taken.add(box(-9, feet - 4, -10, 9, feet + 10, 10, 2));
        site(SiteKind.MOUTH, 0, feet, 0, 6, "");
        site(SiteKind.PORTAL, 0, feet, -5, 2, "");
        List<String> none = List.of();

        double cx = 0, cz = 0, cr = 7, heading = 0;
        // the room the next way leaves, when it leaves a room: its wall and its floor are wherever its form puts them
        Cave from = null;
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
        // the dens after the first and the Wardens' rooms: the room, the way you came into it, its leg, and the room it takes up
        List<Object[]> later = new ArrayList<>();
        int[] ambushBox = null;
        double ambushHeading = 0;
        double sideX = 0, sideZ = 0, sideHeading = 0;
        int sideFeetB = 0;
        String afterFirst = null;

        for (int step = 0; step < plan.size(); step++) {
            char kind = plan.get(step);
            boolean axis = kind == 'S' || kind == 'P' || kind == 'M' || kind == 'C', arena = kind == 'W' || kind == 'L';
            // the way to this leg: how long, and how far it falls (or, on a climb, rises)
            int len = step == 1 ? 22 : shape == Shape.PLUNGE ? 20 + r.nextInt(6) : 17 + r.nextInt(8);
            int fall = shape == Shape.PLUNGE ? 6 + r.nextInt(3) : shape == Shape.SPRAWL ? 4 + r.nextInt(2) : 4 + r.nextInt(Math.min(4, len / 3 - 3));
            if (kind == 'L') {
                len = 24;
                fall = 8;
            }
            int drop = up ? -fall : fall;
            int wide = kind == 'L' ? 22 : kind == 'W' ? (wardenPillars() == 0 ? 15 : 13) : kind == 'M' ? 11 : kind == 'A' ? 13 + r.nextInt(2) + (stand ? 5 : 0) : 10 + r.nextInt(4) + (shape == Shape.SPRAWL ? 2 : 0);
            int high = kind == 'L' ? 16 : kind == 'A' ? (stand ? 9 : 6) : 7 + r.nextInt(3);

            // what this leg is cut like comes before the way to it: it decides how much room the leg needs
            Cave.Form form = Cave.Form.OVAL;
            Arena.Plan hall = Arena.Plan.RING;
            Chasm.Kind cross = Chasm.Kind.PILLARS;
            double rl = wide, rw = wide;
            if (kind == 'F' || kind == 'A') {
                form = pick(r, kind == 'A' || fights == 0 ? level(style.rooms()) : style.rooms());
                switch (form) {
                    case HALL, PIERS -> {
                        rl = wide * 1.35;
                        rw = wide * 0.74;
                    }
                    case TERRACE -> {
                        rl = wide * 1.25;
                        rw = wide * 0.85;
                        high += 2;
                    }
                    case RIFT -> {
                        rl = wide * 1.5;
                        rw = wide * 0.7;
                        high = 12 + r.nextInt(4);
                    }
                    case CROSS -> {
                        rl = wide * 1.2;
                        rw = wide * 0.5;
                    }
                    case RING, PIT -> rl = rw = wide + 2;
                    default -> {}
                }
            } else if (arena) {
                hall = pick(r, kind == 'L' ? style.lords() : style.wardens());
                // a Warden that needs the floor gets it: nothing is raised and nothing stands in the middle
                if (kind == 'W' && wardenPillars() == 0 && (hall == Arena.Plan.TIERS || hall == Arena.Plan.ROTUNDA)) hall = Arena.Plan.RING;
                switch (hall) {
                    case NAVE -> {
                        rl = wide * 1.3;
                        rw = wide * 0.7;
                    }
                    case CROSS -> {
                        rl = wide * 1.15;
                        rw = wide * 0.5;
                    }
                    case SQUARE -> rl = rw = wide * 0.92;
                    case ROTUNDA -> high = high * 13 / 10 + 2;
                    default -> {}
                }
            } else if (kind == 'C') cross = pick(r, up ? flat(style.crossings()) : style.crossings());
            boolean round = arena ? hall == Arena.Plan.RING || hall == Arena.Plan.ROTUNDA || hall == Arena.Plan.TIERS : Cave.round(form);
            Cave.Form cut = arena ? (hall == Arena.Plan.CROSS ? Cave.Form.CROSS : Cave.Form.HALL) : form;
            double rimOf = (arena ? 0.06 : 0.16) * wild;
            int run = kind == 'S' ? 26 + 5 * r.nextInt(2) : kind == 'P' ? 16 : kind != 'C' ? 0 : switch (cross) {
                case BRIDGE -> 36;
                case BEAMS, LEDGES -> 36 + r.nextInt(5);
                case FALLING -> 24 + r.nextInt(7);
                case FORD -> 28 + r.nextInt(9);
                default -> 30 + r.nextInt(9);
            };
            int slope = kind == 'S' ? (up ? -6 : 6) : 0;
            int crossFall = up ? 0 : cross == Chasm.Kind.STEPS ? 6 + r.nextInt(4) : 3;
            // (under great steps the pool lies well below the last of them: a pool within four of a step is somewhere to step down to,
            // and the walk that proves a run would wade in and never try the jump)
            int half = cross == Chasm.Kind.FORD ? 7 : 5, deep = cross == Chasm.Kind.FORD ? 2 : cross == Chasm.Kind.STEPS ? crossFall + 9 : 10;

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
            double best = heading;
            int fewest = Integer.MAX_VALUE;
            for (double h : ways) {
                double lead = from != null ? from.reach(h) * 0.9 : cr * 0.9;
                double ax = cx + sx(h) * lead, az = cz + sz(h) * lead, ex = ax + sx(h) * len, ez = az + sz(h) * len;
                int to = feet - drop, lo = Math.min(feet, to), hi = Math.max(feet, to);
                int n = hits(box(ax, lo - 4, az, ex, hi + 9, ez, 5), 1);
                if (run > 0) n += hits(box(ex, Math.min(to, to - slope) - (kind == 'C' ? deep + 4 : 4), ez, ex + sx(h) * run, Math.max(to, to - slope) + 10, ez + sz(h) * run, kind == 'C' ? half + 2 : 7), 0);
                else if (kind == 'M') {
                    double mx = ex + sx(h) * (wide - 1.5), mz = ez + sz(h) * (wide - 1.5);
                    n += hits(box(mx - wide, to - 34, mz - wide, mx + wide, to + 42, mz + wide, 5), 0);
                } else {
                    double rax = built ? snap(h) : h, back = round ? rl : Cave.edge(cut, rimOf, rl, rw, h - rax);
                    double mx = ex + sx(h) * (back - 1.5), mz = ez + sz(h) * (back - 1.5);
                    double far = cut == Cave.Form.CROSS ? rl : 0;
                    double bx = round ? rl : Math.max(far, Math.abs(sx(rax)) * rl + Math.abs(sz(rax)) * rw), bz = round ? rl : Math.max(far, Math.abs(sz(rax)) * rl + Math.abs(sx(rax)) * rw);
                    n += hits(box(mx - bx, to - 7, mz - bz, mx + bx, to + high + 6, mz + bz, 5), 0);
                }
                if (n < fewest) {
                    fewest = n;
                    best = h;
                }
                if (n == 0) break;
            }
            double h = best;
            double lead = from != null ? from.reach(h) * 0.9 : cr * 0.9;
            double ax = cx + sx(h) * lead, az = cz + sz(h) * lead, ex = ax + sx(h) * len, ez = az + sz(h) * len;
            // out of a room the way starts from the floor it meets there, which a terrace, a pit's walk or a rift may have raised
            // (the room is told first that a way leaves here, so its ground stays down along the line to it)
            if (from != null) {
                from.door((int) Math.round(ax), (int) Math.round(az));
                feet = near(from, (int) Math.round(ax), (int) Math.round(az));
            }
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
            double way = style.wayHalf() + (kind == 'L' ? 0.3 : 0);
            part(new Tunnel("way" + step, (int) Math.round(ax), feet, (int) Math.round(az), (int) Math.round(ex), to, (int) Math.round(ez), way, style.wayHigh() + (kind == 'L' ? 1 : 0), 0.4 * wild, s++)).ground = deepGround;
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
            from = null;
            prevSeals = kind == 'F' || kind == 'A' || kind == 'W';
            int ddx = (int) Math.round(sx(h)), ddz = (int) Math.round(sz(h)), x0 = (int) Math.round(ex), z0 = (int) Math.round(ez);
            switch (kind) {
                case 'F', 'A', 'W', 'L' -> {
                    double rax = built ? snap(h) : h, back = round ? rl : Cave.edge(cut, rimOf, rl, rw, h - rax);
                    int ix = (int) Math.round(ex + sx(h) * (back - 1.5)), iz = (int) Math.round(ez + sz(h) * (back - 1.5));
                    long roomSeed = s++;
                    int narrow = !arena && form == Cave.Form.OVAL ? Math.max(9, wide - r.nextInt(3)) : 0;
                    double inner = Cave.round(form) ? rl : form == Cave.Form.CROSS ? rw * 1.3 : rw;
                    Cave room = null;
                    // where the way comes in the floor may be raised (a pit's walk, the top of a rift, a hall's tiers): the room is set so that it meets the way
                    for (int base = feet, pass = 0; pass < 2; pass++) {
                        room = arena ? new Arena(id, ix, base, iz, wide, high, kind == 'W' ? wardenPillars() : 8, 0.06 * wild, roomSeed).planned(hall, rax)
                                : form == Cave.Form.OVAL ? new Cave(id, ix, base, iz, wide, narrow, high, 0.16 * wild, roomSeed)
                                : new Cave(id, ix, base, iz, inner, inner, high, 0.16 * wild, roomSeed).shaped(form, rax, rl, rw, !up);
                        room.depth(deepGround, rough).door(x0, z0);
                        int raised = near(room, x0, z0) - base;
                        if (raised == 0) break;
                        base = feet - raised;
                    }
                    part(room);
                    taken.add(new int[]{room.x0, room.feet - 4, room.z0, room.x1, room.feet + high + 7, room.z1});
                    if (kind == 'A' && ambush == null) ambushBox = taken.getLast();
                    if (kind == 'W') {
                        used.add("warden " + hall);
                        later.add(new Object[]{room, h, id, taken.getLast()});
                        site(SiteKind.WARDEN, ix, room.feet, iz, wide, "");
                        made.add(new Object[]{id, LegKind.WARDEN, "A Warden's room: " + hall.name().toLowerCase(java.util.Locale.ROOT), ix, room.feet, iz, wide, wardens++, wardenPack()});
                    } else if (kind == 'L') {
                        used.add("lord " + hall);
                        site(SiteKind.LORD, ix, room.feet, iz, wide, ctx.bp().heart().lord());
                        made.add(new Object[]{id, LegKind.LORD, "The Lord's hall: " + hall.name().toLowerCase(java.util.Locale.ROOT), ix, room.feet, iz, wide, -1, none});
                        lordHall = (Arena) room;
                    } else {
                        used.add("room " + form);
                        // where the fight is: the middle of the room, or, round a ring's core, the far side of it
                        int px = form == Cave.Form.RING ? (int) Math.round(ix + sx(h) * rl * 0.62) : ix, pz = form == Cave.Form.RING ? (int) Math.round(iz + sz(h) * rl * 0.62) : iz, py = near(room, px, pz);
                        site(SiteKind.DEN, px, py, pz, 9, "");
                        int radius = (int) Math.min(room.rx, room.rz) - 1;
                        String cutName = form.name().toLowerCase(java.util.Locale.ROOT);
                        if (kind == 'F') {
                            made.add(new Object[]{id, LegKind.FIGHT, "A den: " + cutName, px, py, pz, radius, dens, herd(fights)});
                            if (fights == 0) firstFight = room;
                            else {
                                later.add(new Object[]{room, h, id, taken.getLast()});
                                if (r.nextBoolean()) find(room, h + 2.2);
                            }
                            fights++;
                        } else {
                            made.add(new Object[]{id, LegKind.AMBUSH, "A quiet room: " + cutName, px, py, pz, Math.min(7, radius), dens, ambushPack()});
                            if (ambush == null) {
                                ambush = room;
                                ambushHeading = h;
                            }
                        }
                        dens++;
                    }
                    cx = ix;
                    cz = iz;
                    cr = wide;
                    from = room;
                }
                case 'S', 'P' -> {
                    int bx = x0 + ddx * run, bz = z0 + ddz * run, top = feet;
                    feet -= slope;
                    Tunnel hall2 = part(new Tunnel(id, x0, top, z0, bx, feet, bz, kind == 'P' ? 3.0 : 2.5, kind == 'P' ? 5 : 4, 0, s++));
                    taken.add(box(x0, Math.min(top, feet) - 4, z0, bx, Math.max(top, feet) + 9, bz, 5));
                    int px = -ddz, pz = ddx;
                    if (kind == 'S') {
                        // a cell every five along it, on one wall and then the other, a second apart
                        for (int i = 0, k = 5; k <= run - 4; i++, k += 5) {
                            int x = x0 + ddx * k, z = z0 + ddz * k, side = i % 2 == 0 ? 1 : -1, y = Part.airFrom(hall2.feet2(x, z)) + 1;
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
                    // a gulf, and whatever this run rolled to cross it on: on a climb it stays level, otherwise it steps down as it goes
                    used.add("crossing " + cross);
                    Chasm pit = part(new Chasm(id, cross, x0, feet, z0, ddx, ddz, run, half, cross == Chasm.Kind.FORD ? 6 : 8, deep, crossFall, s++));
                    int bx = x0 + ddx * pit.length, bz = z0 + ddz * pit.length;
                    taken.add(box(x0, feet - deep - 4, z0, bx, feet + 10, bz, half + 3));
                    rig(id, pit, m);
                    made.add(new Object[]{id, LegKind.MOVEMENT, called(cross), x0 + ddx, feet, z0 + ddz, pit.length / 2 + 2, -1, none});
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
                    if (style.squareShafts()) sh.squared();
                    sh.walls = style.squareShafts() ? 1 : 2;
                    used.add(style.squareShafts() ? "shaft SQUARE" : "shaft ROUND");
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
                    made.add(new Object[]{id, LegKind.MOVEMENT, style.squareShafts() ? "A stairwell" : "A shaft", start[0], feet, start[2], 12, -1, none});
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
            int tx = (int) Math.round(sideX + sx(out) * 13), tz = (int) Math.round(sideZ + sz(out) * 13);
            double a = Math.atan2(tx - firstFight.cx, tz - firstFight.cz), edge = firstFight.reach(a) * 0.9;
            int fx = (int) Math.round(firstFight.cx + sx(a) * edge), fz = (int) Math.round(firstFight.cz + sz(a) * edge);
            firstFight.door(fx, fz);
            int ff = near(firstFight, fx, fz), tf = (ff + sideFeetB) / 2;
            part(new Cave("side", tx, tf, tz, 5, 5, 5, 0.16 * wild, s++)).depth(0, rough);
            part(new Tunnel("side_in", fx, ff, fz, tx, tf, tz, 1.6, 3, 0.4 * wild, s++));
            part(new Tunnel("side_out", tx, tf, tz, (int) Math.round(sideX), sideFeetB, (int) Math.round(sideZ), 1.6, 3, 0.4 * wild, s++));
            doorAcross("fight1", fx + sx(a) * 5, ff, tf, fz + sz(a) * 5, a, 1.6);
            finds.add(new int[]{tx + 2, tf, tz + 1});
            made.add(new Object[]{"side", LegKind.BREATHER, "A side cell", tx, tf, tz, 5, -1, none});
            joins.add(new String[]{"fight1", "side"});
            joins.add(new String[]{"side", afterFirst});
        }
        // the hidden cell: a thin way off the ambush, to one side, if there is room for it
        if (ambush != null) {
            for (int side : r.nextBoolean() ? new int[]{1, -1} : new int[]{-1, 1}) {
                double out = ambushHeading + side * Math.PI / 2, rim = ambush.reach(out) * 0.9;
                double ax = ambush.cx + sx(out) * rim, az = ambush.cz + sz(out) * rim, ex = ax + sx(out) * 12, ez = az + sz(out) * 12, mx = ex + sx(out) * 4, mz = ez + sz(out) * 4;
                int[] own = ambushBox;
                taken.remove(own);
                boolean blocked = hits(box(ex - 6, ambush.feet - 5, ez - 6, mx + 6, ambush.feet + 9, mz + 6, 3), 0) > 0;
                taken.add(own);
                if (blocked) continue;
                int hx = (int) Math.round(mx), hz = (int) Math.round(mz), hf = ambush.feet - 1;
                ambush.door((int) Math.round(ax), (int) Math.round(az));
                part(new Tunnel("slip", (int) Math.round(ax), ambush.feet, (int) Math.round(az), (int) Math.round(ex), hf, (int) Math.round(ez), 1.4, 3, 0.4 * wild, s++));
                part(new Cave("hidden", hx, hf, hz, 5.5, 5.5, 5, 0.16 * wild, s++)).depth(0, rough);
                finds.add(new int[]{hx - 1, hf, hz - 1});
                finds.add(new int[]{hx + 2, hf, hz + 1});
                made.add(new Object[]{"hidden", LegKind.HIDDEN, "A place kept back", hx, hf, hz, 5, -1, none});
                joins.add(new String[]{"ambush", "hidden"});
                break;
            }
        }
        // a branch: off a later den or a Warden's room, to one side, a gulf with no fall in it and a vault beyond with two caches in it. Nobody has
        // to cross it; whoever does is paid for it. One to a run from standard up, where there is room for it
        branch:
        if (size != Size.SHORT) for (Object[] den : later) {
            Cave room = (Cave) den[0];
            for (int side : r.nextBoolean() ? new int[]{1, -1} : new int[]{-1, 1}) {
                double out = snap((Double) den[1] + side * Math.PI / 2), edge = room.reach(out) * 0.9;
                int ddx = (int) Math.round(sx(out)), ddz = (int) Math.round(sz(out));
                int ax = (int) Math.round(room.cx + sx(out) * edge), az = (int) Math.round(room.cz + sz(out) * edge);
                Chasm.Kind kind = pick(r, thereAndBack(style.crossings()));
                int run = kind == Chasm.Kind.FALLING ? 22 : kind == Chasm.Kind.FORD ? 26 : kind == Chasm.Kind.HANGERS ? 28 : 36;
                int half = kind == Chasm.Kind.FORD ? 7 : 5, deep = kind == Chasm.Kind.FORD ? 2 : 10, x0 = ax + ddx * 9, z0 = az + ddz * 9;
                int[] own = (int[]) den[3];
                taken.remove(own);
                boolean blocked = hits(box(ax + ddx * 4, room.feet - deep - 4, az + ddz * 4, x0 + ddx * (run + 18), room.feet + 16, z0 + ddz * (run + 18), half + 4), 0) > 0;
                taken.add(own);
                if (blocked) continue;
                room.door(ax, az);
                int f = near(room, ax, az);
                part(new Tunnel("branch_way", ax, f, az, x0, f, z0, 2.0, 4, 0.4 * wild, s++));
                Chasm pit = part(new Chasm("branch", kind, x0, f, z0, ddx, ddz, run, half, kind == Chasm.Kind.FORD ? 6 : 8, deep, 0, s++));
                int vx = x0 + ddx * (pit.length + 5), vz = z0 + ddz * (pit.length + 5);
                part(new Cave("vault", vx, pit.exitFeet, vz, 6, 6, 6, 0.16 * wild, s++)).depth(0, rough);
                taken.add(box(ax, f - deep - 4, az, vx + ddx * 7, f + 12, vz + ddz * 7, half + 3));
                doorAcross((String) den[2], ax + ddx * 4, f, f, az + ddz * 4, out, 2.0);
                rig("branch", pit, m);
                finds.add(new int[]{vx + ddx * 2 + ddz * 3, pit.exitFeet, vz + ddz * 2 + ddx * 3});
                finds.add(new int[]{vx + ddx * 2 - ddz * 3, pit.exitFeet, vz + ddz * 2 - ddx * 3});
                made.add(new Object[]{"branch", LegKind.MOVEMENT, called(kind) + ", off the way", x0 + ddx, f, z0 + ddz, pit.length / 2 + 2, -1, none});
                made.add(new Object[]{"vault", LegKind.BREATHER, "A vault", vx, pit.exitFeet, vz, 5, -1, none});
                joins.add(new String[]{(String) den[2], "branch"});
                joins.add(new String[]{"branch", "vault"});
                used.add("crossing " + kind);
                used.add("branch");
                break branch;
            }
        }
        wells(new Random(seed * 31 + 0x77E11L));
        done();
        for (int[] f : finds) site(SiteKind.FIND, f[0], f[1], f[2], 2, "");
        clearSites();
        for (Object[] l : made) {
            List<String> exits = new ArrayList<>();
            for (String[] j : joins) if (j[0].equals(l[0])) exits.add(j[1]);
            @SuppressWarnings("unchecked") List<String> pack = (List<String>) l[8];
            leg((String) l[0], (LegKind) l[1], (String) l[2], (Integer) l[3], (Integer) l[4], (Integer) l[5], (Integer) l[6], (Integer) l[7], pack,
                    stand && l[1] == LegKind.AMBUSH ? "waves:" + waves : "", exits.toArray(new String[0]));
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

    /** A cache near a room's wall, on level floor: three in from the wall along a heading, or as much further in as it takes to find some. */
    private void find(Cave room, double heading) {
        for (double d = room.reach(heading) - 3; d > 2; d -= 1) {
            int x = (int) Math.round(room.cx + sx(heading) * d), z = (int) Math.round(room.cz + sz(heading) * d), f = room.feet2(x, z);
            if (f == NONE || (f & 1) != 0) continue;
            finds.add(new int[]{x, Math.floorDiv(f, 2), z});
            return;
        }
    }

    private static boolean in(int[] box, int x, int y, int z) {
        return x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
    }

    @Override
    protected final boolean thin(Part part, int x, int y, int z) {
        if (!(part instanceof Shaft) && !(part instanceof Chasm)) return false;
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
        for (Well w : wells) for (int i = -1; i <= 1; i++) {
            int x = w.x + i * w.lx, z = w.z + i * w.lz;
            if (!v.in(x, 1, z)) continue;
            for (int y = w.room.roof(x, z); y <= w.top; y++) v.set(x, y, z, null);
        }
        skinSpecial(v, m);
    }

    /**
     * A gulf's crossing is kept clear whatever a skin grows: nothing with a body stands in the air over the pool down the
     * middle of it, or on anything there is to cross on, however a skin's rule for "well over your head" reads a floor
     * that is ten blocks down. (The strip by each wall is otherwise left alone: a wall may have what it likes on it.)
     * And a climb is never left dark, whatever a skin does or forgets: an unseen light over the ledge every eighth of a
     * turn down a shaft, and over each shelf and stand of a crossing. A jump nobody can see is not a jump.
     */
    @Override
    protected final void finish(V v, M m) {
        for (Part p : parts) {
            if (!p.near(v)) continue;
            if (p instanceof Shaft sh) {
                for (double t = 0; t <= 1.5; t += 0.125) {
                    int[] at = sh.stand(t);
                    v.add(at[0], at[1] + 3, at[2], CLIMB_LIGHT);
                }
            } else if (p instanceof Chasm c) {
                for (int x = Math.max(c.x0, v.x0 - 1); x <= Math.min(c.x1, v.x0 + 16); x++) for (int z = Math.max(c.z0, v.z0 - 1); z <= Math.min(c.z1, v.z0 + 16); z++) {
                    int f = c.feet2(x, z);
                    if (f == NONE) continue;
                    boolean middle = Math.abs(-(x - c.ax) * c.dz + (z - c.az) * c.dx) <= c.half - 2;
                    for (int y = Part.airFrom(f); y <= c.top + 3; y++) if ((middle || c.over(x, y, z)) && c.open(x, y, z) && V.holds(v.get(x, y, z))) v.set(x, y, z, null);
                }
                for (int[] l : c.lights()) v.add(l[0], l[1], l[2], CLIMB_LIGHT);
            }
        }
        for (Well w : wells) for (int i = -1; i <= 1; i++) {
            int x = w.x + i * w.lx, z = w.z + i * w.lz;
            if (!v.in(x, 1, z)) continue;
            v.set(x, w.top, z, WELL_LIGHT);
            v.set(x, w.top - 2, z, WELL_LIGHT);
            if (i == 0) {
                // and down the middle of it, so the pool reaches the floor however high the roof is
                int roof = w.room.roof(x, z);
                v.set(x, roof, z, WELL_LIGHT);
                for (int y = roof - 3; y >= w.floor + 3; y -= 3) v.add(x, y, z, WELL_LIGHT);
            }
        }
        Trim trim = trim(m);
        if (trim != null) for (Part p : parts) if (p.near(v) && (p instanceof Cave || p instanceof Tunnel)) detail(v, trim, p);
    }

    /**
     * A well of light for every room that has the roof for it, two for the Lord's: a slot three cells by one over open
     * floor, off the middle, six cells up into the rock, with nothing else of the run within two cells of it.
     */
    private void wells(Random r) {
        for (Part p : new ArrayList<>(parts)) {
            if (!(p instanceof Cave room) || p.name.equals("root") || p.name.equals("side")) continue;
            int want = room == lordHall ? 2 : 1;
            double turn = r.nextDouble() * Math.PI * 2;
            for (int go = 0; go < 10 && want > 0; go++) {
                double a = turn + go * 2.4, far = (0.22 + 0.06 * (go % 4)) * Math.min(room.rx, room.rz);
                int x = (int) Math.round(room.cx + Math.sin(a) * far), z = (int) Math.round(room.cz + Math.cos(a) * far), lx = go % 2, lz = 1 - lx;
                int top = Integer.MIN_VALUE, low = Integer.MAX_VALUE, floor = 0;
                boolean ok = true;
                for (int i = -1; i <= 1 && ok; i++) {
                    int f = room.feet2(x + i * lx, z + i * lz);
                    if (f == NONE) {
                        ok = false;
                        break;
                    }
                    int y = Part.airFrom(f), roof = room.roof(x + i * lx, z + i * lz);
                    ok = roof - y >= 5 && room.open(x + i * lx, y, z + i * lz) && room.open(x + i * lx, roof - 1, z + i * lz);
                    top = Math.max(top, roof);
                    low = Math.min(low, roof);
                    if (i == 0) floor = y;
                }
                if (!ok) continue;
                top += 6;
                for (Well w : wells) if (w.room == room && Math.abs(w.x - x) + Math.abs(w.z - z) < 8) ok = false;
                clear:
                for (Part q : parts) {
                    if (q == room || !ok) continue;
                    if (x + 3 < q.x0 || x - 3 > q.x1 || z + 3 < q.z0 || z - 3 > q.z1) continue;
                    for (int ox = -3; ox <= 3; ox++) for (int oz = -3; oz <= 3; oz++) for (int y = low - 2; y <= top + 3; y++) if (q.open(x + ox, y, z + oz)) {
                        ok = false;
                        break clear;
                    }
                }
                if (!ok) continue;
                wells.add(new Well(room, x, z, lx, lz, top, floor));
                want--;
            }
        }
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final Direction[] FACES = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

    /** The dungeon's own rock at a cell: solid by the parts' shapes, and still there. */
    private boolean stone(V v, int x, int y, int z) {
        return !open(x, y, z) && V.holds(v.get(x, y, z));
    }

    /**
     * The small things (SPEC §50.9 "Depth"), set last, on the dungeon's own rock and only in air nothing else took: a stair
     * at about half the wall feet off the way, its back to the wall; a stair hung where the roof steps down; a drift one
     * layer deep along the rest of the wall feet; plants in clumps that thicken away from the way; bits; things hanging
     * from the roof. Everything is decided by where a cell is, so two chunks agree about it. Nothing is set where a door
     * shuts, in the narrow ways, or in a hall of traps.
     */
    private void detail(V v, Trim t, Part p) {
        if (p.name.equals("slip") || p.name.startsWith("side_") || p.name.startsWith("gallery") || p.name.startsWith("teeth") || p.name.equals("branch_way")) return;
        Cave room = p instanceof Cave c ? c : null;
        Tunnel way = p instanceof Tunnel w ? w : null;
        boolean den = room != null && !(room instanceof Arena) && !p.name.equals("root");
        for (int x = Math.max(p.x0, v.x0 - 1); x <= Math.min(p.x1, v.x0 + 16); x++) for (int z = Math.max(p.z0, v.z0 - 1); z <= Math.min(p.z1, v.z0 + 16); z++) {
            int f = p.feet2(x, z);
            if (f == NONE) continue;
            int y = Part.airFrom(f), roof = p.roof(x, z);
            if (!p.open(x, y, z) || inDoor(x, y, z)) continue;
            double off = room != null ? room.offWay(x, z) : way.offWay(x, z), wild = Math.max(0, Math.min(1, (off - (room != null ? 2.5 : 1.2)) / 4));
            double own = dev.awaken.gates.realms.kit.N.rnd(x, z, 2001), clump = dev.awaken.gates.realms.kit.N.rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 2002);
            // the floor
            if (v.get(x, y, z) == null && stone(v, x, y - 1, z) && wild > 0) {
                int side = -1, walls = 0;
                for (int i = 0; i < 4; i++) if (stone(v, x + SIDES[i][0], y, z + SIDES[i][1]) && stone(v, x + SIDES[i][0], y + 1, z + SIDES[i][1])) {
                    side = i;
                    walls++;
                }
                boolean clear = v.get(x, y + 1, z) == null && p.open(x, y + 1, z);
                if (walls == 1 && clear && (f & 1) == 0 && t.footStairs() != null && t.footStairs().hasProperty(net.minecraft.world.level.block.StairBlock.FACING) && clump < 0.55 && own < 0.8) {
                    v.set(x, y, z, t.footStairs().setValue(net.minecraft.world.level.block.StairBlock.FACING, FACES[side]));
                } else if (walls > 0 && t.drift() != null && own < 0.45) v.set(x, y, z, t.drift());
                else if (den && !t.plants().isEmpty() && clump < t.growth() * (0.12 + 0.3 * wild) && own < 0.62) v.set(x, y, z, t.plants().get((int) (dev.awaken.gates.realms.kit.N.rnd(x, z, 2003) * t.plants().size())));
                else if (t.bits() != null && own > 1 - 0.05 * t.growth() * (1 + wild)) v.set(x, y, z, t.bits());
            }
            // the roof
            int top = roof - 1;
            if (top - y < 3 || !p.open(x, top, z) || v.get(x, top, z) != null || !stone(v, x, top + 1, z) || inDoor(x, top, z)) continue;
            int lower = -1, steps = 0;
            for (int i = 0; i < 4; i++) if (stone(v, x + SIDES[i][0], top, z + SIDES[i][1]) && p.open(x + SIDES[i][0], top - 1, z + SIDES[i][1])) {
                lower = i;
                steps++;
            }
            if (steps == 1 && t.roofStairs() != null && t.roofStairs().hasProperty(net.minecraft.world.level.block.StairBlock.FACING) && own < 0.55) {
                v.set(x, top, z, t.roofStairs().setValue(net.minecraft.world.level.block.StairBlock.FACING, FACES[lower]).setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
            } else if (steps == 0 && den && top - y >= 4 && !t.hangs().isEmpty() && dev.awaken.gates.realms.kit.N.rnd(Math.floorDiv(x, 4), Math.floorDiv(z, 4), 2004) < 0.4 * t.growth() && own < 0.16) {
                v.set(x, top, z, t.hangs().get((int) (dev.awaken.gates.realms.kit.N.rnd(x, z, 2005) * t.hangs().size())));
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
        return lordHall != null && y < lordHall.feet + 26 && y > lordHall.feet - 4 && x >= lordHall.x0 && x <= lordHall.x1 && z >= lordHall.z0 && z <= lordHall.z1 && lordHall.out(x, z) < 0.96;
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
