package dev.awaken.gates.realms;

import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.progression.Rank;

import java.util.ArrayList;
import java.util.List;

/**
 * One land's drawing, made once per Realm by its program and never changed after, so any worldgen thread can read it.
 * Coordinates are the Realm's own: (0,0) is its middle, y is the world's.
 */
public abstract class Land {
    public record Ctx(Blueprint bp, String id, Rank rank, long seed, int version) {}

    /** A place the live game cares about. */
    public enum SiteKind { MOUTH, PORTAL, WAYSTONE, VILLAGE, DEN, WARDEN, FIND, DELVE, LORD, UPDRAFT }

    public record Site(SiteKind kind, int index, int x, int y, int z, int radius, String what) {}

    /** One block a moment in the place changes, in the Realm's own coordinates. */
    public record Edit(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {}

    /** What you do on one leg of the run through a land (SPEC §50.9). */
    public enum LegKind { ARRIVE, FIGHT, AMBUSH, MOVEMENT, TRAP, WARDEN, BREATHER, SHORTCUT, LORD, HIDDEN }

    /**
     * One leg of the run. {@code site}: the DEN (a fight, an ambush) or WARDEN site whose monsters are this leg's, or -1.
     * {@code pack}: who stands there, each "id" or "id*n"; empty rolls from the theme. {@code when}: for a shortcut, what
     * opens it ("warden2", "den1"). {@code exits}: the legs you can go on to from here.
     */
    public record Leg(String id, LegKind kind, String name, int x, int y, int z, int radius, int site, List<String> pack, String when, List<String> exits) {
        /** How many monsters the pack names, or 0 when it rolls from the theme. */
        public int packSize() {
            int n = 0;
            for (String e : pack) {
                int star = e.indexOf('*');
                n += star < 0 ? 1 : Integer.parseInt(e.substring(star + 1));
            }
            return n;
        }
    }

    /**
     * A trap, in the land's own blocks (SPEC §50.9). A PRESS is a box of cells that fills with {@code on} for part of
     * every {@code period} and empties again. A SPIT is a block at the first corner that lights ({@code on}) and then
     * strikes along the line to the second. A CRUMBLE is floor of {@code on} that gives way a moment after it's stood on.
     */
    public enum TrapKind { PRESS, SPIT, CRUMBLE }

    public record Trap(String leg, TrapKind kind, int x0, int y0, int z0, int x1, int y1, int z1, int period, int phase,
                       net.minecraft.world.level.block.state.BlockState on, net.minecraft.world.level.block.state.BlockState off) {}

    /**
     * A stretch you can fall from: inside this box a long fall puts you back on the last of its ledges you stood on. Each
     * ledge is x, y, z of your feet. {@code free}: the fall costs nothing and nothing is said (a hall that teaches a move,
     * SPEC §17.10: the Leaper's Gauntlet's halls before its Long Run, and the Long Run's own pit).
     */
    public record Course(String leg, int x0, int y0, int z0, int x1, int y1, int z1, List<int[]> ledges, boolean free) {
        public Course(String leg, int x0, int y0, int z0, int x1, int y1, int z1, List<int[]> ledges) {
            this(leg, x0, y0, z0, x1, y1, z1, ledges, false);
        }

        public boolean holds(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    public final Ctx ctx;
    private final List<Site> sites = new ArrayList<>();
    private final List<Leg> legs = new ArrayList<>();
    private final List<Trap> traps = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private volatile List<Leg> route;
    private final java.util.Map<String, Boolean> sealing = new java.util.concurrent.ConcurrentHashMap<>();

    protected Land(Ctx ctx) {
        this.ctx = ctx;
    }

    protected final void site(SiteKind kind, int x, int y, int z, int radius, String what) {
        int n = 0;
        for (Site s : sites) if (s.kind() == kind) n++;
        sites.add(new Site(kind, n, x, y, z, radius, what));
    }

    /** The height of the Realm's water, from its blueprint. */
    public final int water() {
        return ctx.bp().land().water();
    }

    public final List<Site> sites() {
        return List.copyOf(sites);
    }

    public final List<Site> sites(SiteKind kind) {
        return sites.stream().filter(s -> s.kind() == kind).toList();
    }

    public final Site site(SiteKind kind) {
        return sites.stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
    }

    // ---- the run (SPEC §50.9) -------------------------------------------------------------------------------------------

    /** One leg of this land's run, in the order a sheet lists them. */
    protected final void leg(String id, LegKind kind, String name, int x, int y, int z, int radius, int site, List<String> pack, String when, String... exits) {
        legs.add(new Leg(id, kind, name, x, y, z, radius, site, List.copyOf(pack), when, List.of(exits)));
    }

    /**
     * Puts a new leg between two legs of the run already laid (SPEC §50.9, version 2's second stretch of movement): the
     * exit from {@code from} to {@code to} now goes through it. False when {@code from} doesn't lead on to {@code to}.
     */
    protected final boolean between(String from, String to, String id, LegKind kind, String name, int x, int y, int z, int radius) {
        return between(from, to, id, kind, name, x, y, z, radius, -1);
    }

    /** As above, for a leg with a site of its own (a fight's den). */
    protected final boolean between(String from, String to, String id, LegKind kind, String name, int x, int y, int z, int radius, int site) {
        for (int i = 0; i < legs.size(); i++) {
            Leg l = legs.get(i);
            if (!l.id().equals(from) || !l.exits().contains(to)) continue;
            List<String> ex = new ArrayList<>(l.exits());
            ex.set(ex.indexOf(to), id);
            legs.set(i, new Leg(l.id(), l.kind(), l.name(), l.x(), l.y(), l.z(), l.radius(), l.site(), l.pack(), l.when(), List.copyOf(ex)));
            legs.add(new Leg(id, kind, name, x, y, z, radius, site, List.of(), "", List.of(to)));
            route = null;
            return true;
        }
        return false;
    }

    protected final void trap(Trap t) {
        traps.add(t);
    }

    /**
     * A crossing that needs a move of the leap (SPEC §17.10): from the cell feet stand in on one side to the cell they
     * land in on the other, and which move ("double", "dash", "slam"). The walk check takes these as crossings a player
     * with the move can make, and separately proves that none can be made without it.
     */
    public record Leap(String move, int x0, int y0, int z0, int x1, int y1, int z1) {}

    /** The crossings of this land that need a move of the leap. None, for every land but a trial of it. */
    public List<Leap> leaps() {
        return List.of();
    }

    /**
     * A stand of a trial (SPEC §17.10): the cell its plate lies in, the move it holds ("double", "dash", "slam"), and the
     * press head that hangs over it: its lowest cell at rest. The stand is the Ascent's block, set live by the course.
     */
    public record Stand(String move, int x, int y, int z, int head) {}

    /** The stands of this land, in the order they are met. None, for every land but a trial. */
    public List<Stand> stands() {
        return List.of();
    }

    /**
     * A press over a way that never hurts (SPEC §17.10): a head of {@code block} over the footprint, {@code tall} cells
     * deep, whose lowest cell is at {@code feet} when it is down and {@code travel} higher when it is up, on a rod. With a
     * period it runs on the Realm's clock (down, a rise, up, a warning, a fall); with none it is down until
     * {@code freedBy} (a {@link Slam}'s id) lifts it. Whoever it would come down on is set clear of it, unhurt.
     */
    public record Ram(int x0, int z0, int x1, int z1, int feet, int tall, int travel, int period, int phase, String freedBy,
                      net.minecraft.world.level.block.state.BlockState block, net.minecraft.world.level.block.state.BlockState rod) {}

    public List<Ram> rams() {
        return List.of();
    }

    /**
     * Something only a slam moves (SPEC §17.10): a slam landing inside the box applies the change {@code slam_<id>}
     * and lifts the ram it frees, for everyone in the gate and for good, as a stand's bars open. {@code again}: it is
     * part of the timed run, and is put back ({@code rearm_<id>}) each time a runner starts the clock.
     */
    public record Slam(String id, int x0, int y0, int z0, int x1, int y1, int z1, boolean again) {
        public Slam(String id, int x0, int y0, int z0, int x1, int y1, int z1) {
            this(id, x0, y0, z0, x1, y1, z1, false);
        }
    }

    public List<Slam> slams() {
        return List.of();
    }

    /**
     * A place of a trial's course the Ascent shows something at (SPEC §17.10, {@code TrialHooks.at}): the mark's name
     * ("reach", "use/double/1", "use/dash/2", ...) and the two ends of the thing marked, each the cell feet stand in.
     * Told once to each runner who comes near its first end; a first use of a move only once they hold it.
     */
    public record Spot(String mark, int x0, int y0, int z0, int x1, int y1, int z1) {}

    public List<Spot> spots() {
        return List.of();
    }

    /**
     * The timed run of a trial (SPEC §17.10): the box whose first step starts the clock and the box that stops it (each
     * x0 y0 z0 x1 y1 z1), its rings in the order they are passed (each x, y, z of the lane under it and the way the
     * course runs there along z, -1 or 1), par in ticks, and where someone who has run it before is set down (x, y, z,
     * yaw).
     */
    public record Timed(int[] start, int[] finish, List<int[]> rings, int par, int[] door) {}

    /** This land's timed run, or null. */
    public Timed timed() {
        return null;
    }

    /** Whether nothing in this land hurts for falling (a trial's halls: a fall there costs nothing, SPEC §17.10). */
    public boolean soft() {
        return false;
    }

    protected final void course(Course c) {
        courses.add(c);
    }

    /** Whether this land's run was designed, leg by leg, or is only read off where its sites happen to be. */
    public final boolean designed() {
        return !legs.isEmpty();
    }

    /**
     * The run: every leg, in order. A land that hasn't been given one still has one, read off its sites: you arrive, every
     * den is a fight, every Warden a Warden, then the Lord.
     */
    public final List<Leg> route() {
        List<Leg> r = route;
        if (r == null) route = r = legs.isEmpty() ? derived() : List.copyOf(legs);
        return r;
    }

    private List<Leg> derived() {
        List<Leg> out = new ArrayList<>();
        List<String> fights = new ArrayList<>(), wardens = new ArrayList<>();
        for (Site s : sites) {
            if (s.kind() == SiteKind.DEN) fights.add("den" + s.index());
            if (s.kind() == SiteKind.WARDEN) wardens.add("warden" + s.index());
        }
        List<String> first = new ArrayList<>(fights);
        first.addAll(wardens);
        for (Site s : sites) {
            switch (s.kind()) {
                case MOUTH -> out.add(new Leg("arrive", LegKind.ARRIVE, "", s.x(), s.y(), s.z(), s.radius(), -1, List.of(), "", first));
                case DEN -> out.add(new Leg("den" + s.index(), LegKind.FIGHT, "", s.x(), s.y(), s.z(), Math.max(10, s.radius()), s.index(), List.of(), "", wardens));
                case WARDEN -> out.add(new Leg("warden" + s.index(), LegKind.WARDEN, "", s.x(), s.y(), s.z(), Math.max(12, s.radius()), s.index(), List.of(), "", List.of("lord")));
                case LORD -> out.add(new Leg("lord", LegKind.LORD, "", s.x(), s.y(), s.z(), s.radius(), -1, List.of(), "", List.of()));
                default -> {}
            }
        }
        return List.copyOf(out);
    }

    public final Leg leg(String id) {
        for (Leg l : route()) if (l.id().equals(id)) return l;
        return null;
    }

    /**
     * Where a tour stands to look at the n-th leg of a kind, and the way it faces (x, y, z, yaw). A rolled run puts its
     * legs somewhere else on every seed and cuts every room differently, so a tour can't name a place or a camera; it
     * names "the first crossing". With {@code back} 0 it stands at the leg, turned toward the leg it leads on to (the way
     * you would cross a gulf). With {@code back} over 0 it stands that far toward the leg you came from, turned to the leg
     * (the way you would walk into a room); under 0, that far beyond it, looking back. After the yaw come the leg's own x and z.
     * Null when the run has no such leg.
     */
    public final double[] legView(LegKind kind, int index, int back) {
        int n = 0;
        for (Leg l : route()) {
            if (l.kind() != kind || n++ != index) continue;
            Leg on = null, before = null;
            for (String to : l.exits()) if (on == null && leg(to) != null && (leg(to).x() != l.x() || leg(to).z() != l.z())) on = leg(to);
            for (Leg p : route()) if (before == null && p.exits().contains(l.id()) && (p.x() != l.x() || p.z() != l.z())) before = p;
            if (back == 0 || before == null) {
                Leg toward = on != null ? on : before;
                double yaw = toward == null ? 0 : Math.toDegrees(Math.atan2(-(toward.x() - l.x()), toward.z() - l.z()));
                return new double[]{l.x(), l.y(), l.z(), on != null || before == null ? yaw : yaw + 180, l.x(), l.z()};
            }
            double dx = before.x() - l.x(), dz = before.z() - l.z(), far = Math.max(1, Math.hypot(dx, dz));
            double x = l.x() + dx / far * back, z = l.z() + dz / far * back;
            // facing the leg from wherever that put us
            double yaw = Math.toDegrees(Math.atan2(-(l.x() - x), l.z() - z));
            return new double[]{x, l.y(), z, yaw, l.x(), l.z()};
        }
        return null;
    }

    /** The leg a den's monsters belong to (a fight or an ambush), or null. */
    public final Leg legOfDen(int index) {
        for (Leg l : route()) if ((l.kind() == LegKind.FIGHT || l.kind() == LegKind.AMBUSH) && l.site() == index) return l;
        return null;
    }

    public final Leg legOfWarden(int index) {
        for (Leg l : route()) if (l.kind() == LegKind.WARDEN && l.site() == index) return l;
        return null;
    }

    public final List<Trap> traps() {
        return traps;
    }

    public final List<Course> courses() {
        return courses;
    }

    /** Whether a leg has doors of its own to shut while its fight is on ({@code change("seal_" + id)} names them). */
    public final boolean seals(String legId) {
        return sealing.computeIfAbsent(legId, k -> !change("seal_" + k).isEmpty());
    }

    /** The top of the ground in a column (the block you'd stand on, never a piece built over it). */
    public abstract int ground(int x, int z);

    /** How far past the land's edge a point is (positive means the wind turns you back there). */
    public double edge(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) - (halfSize() - 48);
    }

    /**
     * The blocks a moment changes: the way to the Lord opening, the hall changing mid-fight. Empty for a land without them.
     * The run's own moments, each named for a leg: {@code seal_}/{@code unseal_} (a fight's doors), {@code ambush_} (what
     * breaks open as it springs), {@code shortcut_} (the way that opens), {@code found_} (a hidden place, once found), and
     * {@code walk_open}: everything that is open at some time, so the walk check can go through it.
     */
    public List<Edit> change(String moment) {
        return List.of();
    }

    /** Whether a point is inside the place the Lord is fought in. */
    public boolean inHeart(int x, int y, int z) {
        return false;
    }

    /** Draws the chunk this volume covers, every pass, in order. */
    public abstract void paint(V v);

    /** Mist layers, shafts of light, glowing lamps and still water for the client's air and Lens, in land coordinates. Fixed for the land. */
    public dev.awaken.gates.world.FloorAir air() {
        return dev.awaken.gates.world.FloorAir.NONE;
    }

    /** Whether this land is a place under a roof: no sun reaches it, what light there is comes from its candles and a few cracks. */
    public boolean indoors() {
        return false;
    }

    /** Which way the arch home runs: along Z (facing east and west) or along X. */
    public net.minecraft.core.Direction.Axis portalAxis() {
        return net.minecraft.core.Direction.Axis.Z;
    }

    /** What the System says the first time you stand at the Lord's closed door, or null to say nothing. */
    public String heartClosedLine() {
        return null;
    }

    /** What the System says when a shortcut opens somewhere you can't see, or null to say nothing. */
    public String shortcutLine(String legId) {
        return null;
    }

    /** Which of the blueprint's biomes this point is. */
    public abstract int biome(int x, int y, int z);

    /** How far from the middle the land has anything to walk on. */
    public abstract int halfSize();

    /** Every set piece, for the checks. */
    public abstract List<? extends Piece<?>> pieces();

    /** Lines of ground nothing may be built on: roads, stairs, ways into doors. Each is x0,z0,x1,z1,halfWidth. */
    public abstract List<double[]> ways();

    public abstract float mouthYaw();

    public final Site mouth() {
        return site(SiteKind.MOUTH);
    }
}
