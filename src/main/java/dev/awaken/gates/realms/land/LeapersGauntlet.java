package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.crawl.CrawlLand;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.gates.realms.kit.N.rnd;

/**
 * The Leaper's Gauntlet (SPEC §17.10, the plan of 11 Nov 2026 in docs/leapers-gauntlet/PLAN.md): the trial that gives the
 * leap, laid by hand in the Stopped Works' blocks. A works that stopped mid-stroke; what it made is still on its stands.
 * No fighting. The way in is crossed with the moves anyone has and ends at a gulf nobody can cross; a stand gives one
 * move of the leap; the hall past it cannot be crossed without that move and asks for it four times; then the next
 * stand. No hall before the Long Run has a clock or costs anything to fall in.
 *
 * <p>Built so far: the way in, the first stand, the hall "Twice" (the double jump), the second stand, the hall "Through"
 * (the dash: a gap of nine, a gap of eight round a corner, a gulf of twelve; its line of presses is still to come) and
 * the third stand. Still to lay: "Down" (the slam), the Long Run, the tally floor. Sizes are the plan's targets until
 * the walk check runs on the leap's real reach.
 *
 * <p>A hall here is a {@link Hall}: a machine hall seventeen wide cut out of the works, with a way of decks five wide
 * down its middle and nothing else to stand on. Under the decks is the casting pit, eighteen down, its floor a crust of
 * slag that glows; nobody reaches it, because the fall catch has them first (and it is deep enough that the catch
 * cannot miss). The first walk of this land (run 43021300881) was a brick passage seven wide with a light of 15 in a
 * hundred: the kind of hallway the new gates exist not to be. The walls are the Stopped Works' own now: courses of
 * brass, furnace mouths over the slag, a pipe run, gauge lights, a roof of plate on brass ribs with lamps let into it.
 *
 * <p>The way on from the second and the third stand is barred until that stand is taken ({@code trial_open_dash},
 * {@code trial_open_slam}); it opens for everyone in the gate, for good.
 */
public final class LeapersGauntlet extends CrawlLand<StoppedWorks.M> {
    /** The way in's floor; the level "Through" is on; how far under a hall's first deck its pit lies; how far over its highest its roof. */
    static final int Y = 100, Y2 = Y + 8, DEEP = 18, HEAD = 10;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** The casting pit's floor: the Ember Mines' crust, which glows and burns nobody. And the glow over it. */
    private static final BlockState SLAG = BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("magma_crust")).defaultBlockState(),
            GLOW = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13);
    /** The bars that wait for a stand: a box each, x0 y0 z0 x1 y1 z1. */
    private static final int[] DOOR_DASH = {-4, Y2, -34, 4, Y2 + 5, -34}, DOOR_SLAM = {56, Y2, 7, 56, Y2 + 5, 15};

    /** A box of air with decks in it and, here and there, a wall standing in it. A deck is x0, z0, x1, z1 and the height feet stand at. */
    static final class Hall extends Part {
        /** The height of its first deck (what its walls are dressed by), its pit's floor and its roof. */
        final int base, pit, top;
        final List<int[]> decks = new ArrayList<>(), walls = new ArrayList<>();

        Hall(String name, int x0, int z0, int x1, int z1, int base, int rise) {
            this.name = name;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.base = base;
            pit = base - DEEP;
            top = base + rise + HEAD;
        }

        Hall deck(int dx0, int dz0, int dx1, int dz1, int feet) {
            decks.add(new int[]{dx0, dz0, dx1, dz1, feet});
            return this;
        }

        /** A wall from the pit to the roof: the works' own rock left standing (something to run along). */
        Hall wall(int wx0, int wz0, int wx1, int wz1) {
            walls.add(new int[]{wx0, wz0, wx1, wz1});
            return this;
        }

        /** The deck over a column, or null over the pit. */
        int[] deckAt(int x, int z) {
            int[] best = null;
            for (int[] d : decks) if (x >= d[0] && x <= d[2] && z >= d[1] && z <= d[3] && (best == null || d[4] > best[4])) best = d;
            return best;
        }

        @Override
        public int feet2(int x, int z) {
            if (x < x0 || x > x1 || z < z0 || z > z1) return NONE;
            for (int[] w : walls) if (x >= w[0] && x <= w[2] && z >= w[1] && z <= w[3]) return NONE;
            int[] d = deckAt(x, z);
            return (d == null ? pit : d[4]) * 2;
        }

        @Override
        public int roof(int x, int z) {
            return top;
        }

        /** Where the fall catch sets someone down: the middle of every deck, and both ends of a long one. */
        List<int[]> ledges() {
            List<int[]> out = new ArrayList<>();
            for (int[] d : decks) {
                int x = (d[0] + d[2]) / 2, z = (d[1] + d[3]) / 2;
                if (d[3] - d[1] > 6) {
                    out.add(new int[]{x, d[4], d[1] + 2});
                    out.add(new int[]{x, d[4], d[3] - 2});
                } else if (d[2] - d[0] > 6) {
                    out.add(new int[]{d[0] + 2, d[4], z});
                    out.add(new int[]{d[2] - 2, d[4], z});
                } else out.add(new int[]{x, d[4], z});
            }
            return out;
        }
    }

    private final Hall wayIn, twice, through, east;
    private final List<Leap> leaps = new ArrayList<>();

    public LeapersGauntlet(Ctx ctx) {
        super(ctx, 0x4C454150L);
        part(new Cave("root", 0, Y, -140, 6, 6, 6, seed + 1));
        // the way in: a gap of two, a step, a gap of three, all with what anyone has; then the lip of the first gulf, and a
        // deck off it to the east wall, where the first stand's room is
        wayIn = part(new Hall("way_in", -8, -136, 8, -103, Y, 1)
                .deck(-2, -136, 2, -128, Y).deck(-2, -125, 2, -119, Y).deck(-2, -118, 2, -113, Y + 1).deck(-2, -109, 2, -103, Y + 1).deck(3, -108, 8, -104, Y + 1));
        part(new Tunnel("to_stand1", 8, Y + 1, -106, 14, Y + 1, -106, 2.4, 5, seed + 2));
        part(new Cave("stand1", 19, Y + 1, -106, 6, 6, 7, seed + 3));
        // "Twice": the gulf (six wide, the far side two higher), a step three up, a gap onto a ledge two higher, and a
        // long gap with only a wall beside it
        twice = part(new Hall("twice", -8, -102, 8, -51, Y + 1, 7)
                .deck(-2, -96, 2, -90, Y + 3).deck(-2, -89, 2, -84, Y + 6).deck(-2, -79, 2, -74, Y + 8).deck(-2, -62, 2, -51, Y + 8)
                .wall(-3, -74, -3, -62));
        part(new Cave("stand2", 0, Y2, -44, 7, 7, 7, seed + 4));
        // "Through": a gap of nine; then the way turns east, with a gap of eight just past the turn and a gulf of twelve
        part(new Tunnel("to_through", 0, Y2, -38, 0, Y2, -30, 2.6, 5, seed + 5));
        through = part(new Hall("through", -8, -30, 8, 19, Y2, 0).deck(-2, -30, 2, -25, Y2).deck(-2, -15, 2, 13, Y2).deck(3, 9, 8, 13, Y2));
        east = part(new Hall("through_east", 9, 3, 41, 19, Y2, 0).deck(9, 9, 10, 13, Y2).deck(19, 9, 25, 13, Y2).deck(38, 9, 41, 13, Y2));
        part(new Cave("stand3", 48, Y2, 11, 7, 7, 7, seed + 6));
        part(new Tunnel("to_down", 54, Y2, 11, 62, Y2, 11, 2.6, 5, seed + 7));
        done();

        site(SiteKind.MOUTH, 0, Y, -140, 6, "");
        site(SiteKind.PORTAL, 0, Y, -145, 2, "");
        clearSites();

        List<String> none = List.of();
        leg("root", LegKind.ARRIVE, "The stopped works", 0, Y, -140, 6, -1, none, "", "way_in");
        leg("way_in", LegKind.MOVEMENT, "The way in", 0, Y + 1, -116, 16, -1, none, "", "stand1");
        leg("stand1", LegKind.BREATHER, "The first stand", 19, Y + 1, -106, 6, -1, none, "", "twice");
        leg("twice", LegKind.MOVEMENT, "Twice", 0, Y + 6, -78, 26, -1, none, "", "stand2");
        leg("stand2", LegKind.BREATHER, "The second stand", 0, Y2, -44, 7, -1, none, "", "through");
        leg("through", LegKind.MOVEMENT, "Through", 0, Y2, -8, 24, -1, none, "", "stand3");
        leg("stand3", LegKind.BREATHER, "The third stand", 48, Y2, 11, 7, -1, none, "");

        // the first gulf is the way in's to catch: whoever falls there has not stood on anything of "Twice" yet
        course(new Course("way_in", -9, Y - DEEP - 2, -137, 9, Y + 14, -97, wayIn.ledges(), true));
        course(new Course("twice", -9, Y - DEEP - 2, -96, 9, Y + 22, -50, twice.ledges(), true));
        course(new Course("through", -9, Y2 - DEEP - 2, -31, 8, Y2 + 12, 20, through.ledges(), true));
        course(new Course("through", 9, Y2 - DEEP - 2, 2, 42, Y2 + 12, 20, east.ledges(), true));

        leaps.add(new Leap("double", 0, Y + 1, -103, 0, Y + 3, -96));
        leaps.add(new Leap("double", 0, Y + 3, -90, 0, Y + 6, -89));
        leaps.add(new Leap("double", 0, Y + 6, -84, 0, Y + 8, -79));
        leaps.add(new Leap("double", 0, Y + 8, -74, 0, Y + 8, -62));
        leaps.add(new Leap("dash", 0, Y2, -25, 0, Y2, -15));
        leaps.add(new Leap("dash", 10, Y2, 11, 19, Y2, 11));
        leaps.add(new Leap("dash", 25, Y2, 11, 38, Y2, 11));
    }

    @Override
    public List<Leap> leaps() {
        return leaps;
    }

    @Override
    protected StoppedWorks.M mats() {
        return StoppedWorks.m();
    }

    // ---- what the works is made of: the Stopped Works' own walls ----------------------------------------------------------

    /** Courses: a band of brass every fourth, a cog let into it now and then; between them plate in runs of three, iron, panels of copper sheet, a little dark brick. */
    @Override
    protected BlockState rock(StoppedWorks.M m, int x, int y, int z) {
        if (Math.floorMod(y, 4) == 0) return rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 4101) < 0.12 ? m.gear : m.brass;
        int off = (int) (rnd(y, 0, 4102) * 3);
        double n = rnd(Math.floorDiv(x + off, 3) * 31 + Math.floorDiv(z + off, 3), y, 4103);
        return n < 0.5 ? m.plate : n < 0.68 ? m.iron : n < 0.84 ? m.sheet : n < 0.95 ? m.sheetOld : m.brick;
    }

    @Override
    protected BlockState deep(StoppedWorks.M m) {
        return m.plate;
    }

    /** A deck is plate with a strip of brass across each end, so where it stops can be read from the one before; a pit's floor is slag; rooms are plate and old sheet. */
    @Override
    protected BlockState floor(StoppedWorks.M m, Part part, int x, int y, int z) {
        if (part instanceof Hall h) {
            int[] d = h.deckAt(x, z);
            if (d == null) return SLAG;
            boolean alongZ = d[3] - d[1] >= d[2] - d[0];
            boolean end = alongZ ? z == d[1] || z == d[3] : x == d[0] || x == d[2];
            return end ? m.brass : Math.floorMod(x + z, 5) == 0 ? m.sheetOld : m.plate;
        }
        if (part instanceof Cave && Math.floorMod(x, 4) == 0) return m.brass;
        return rnd(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 4104) < 0.3 ? m.sheetOld : m.plate;
    }

    @Override
    protected BlockState step(StoppedWorks.M m) {
        return m.ironSlab;
    }

    @Override
    protected BlockState doorBlock(StoppedWorks.M m) {
        return m.bars;
    }

    /**
     * What is set into the walls and the roof. A hall's walls are dressed by its first deck's height, not by the pit under
     * them: furnace mouths three and four under the decks, glowing over the slag; the pipe run two over them; a gauge
     * light every fifth cell at three, with its pool; a second pipe at seven. A room's and a passage's are dressed from
     * their own floor. Every roof is plate on ribs of brass with a lamp let in every fourth cell each way.
     */
    @Override
    protected void adorn(V v, StoppedWorks.M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean hall = part instanceof Hall, free = v.get(x, y, z) == null;
        int up = y - (part instanceof Hall h ? h.base : Part.airFrom(part.feet2(x, z)));
        if (wx != 0 || wz != 0) {
            int ax = x + wx, az = z + wz, along = wx != 0 ? az : ax;
            if (hall && (up == -3 || up == -4) && Math.floorMod(along, 6) <= 1) v.set(ax, y, az, m.furnace);
            else if (!hall && part instanceof Cave && up == 0 && Math.floorMod(along, 6) <= 1) {
                v.set(ax, y, az, m.furnace);
                if (free && Math.floorMod(along, 6) == 0) v.set(x, y, z, m.pool);
            } else if (up == 2 || hall && up == 7) v.set(ax, y, az, wx != 0 ? m.pipeZ : m.pipeX);
            else if (up == 3 && Math.floorMod(along, 5) == 0) {
                v.set(ax, y, az, m.gauge);
                if (free) v.set(x, y, z, m.pool);
            } else if (up == 1 && rnd(ax * 7L + y, az, 4105) < 0.06) v.set(ax, y, az, rnd(ax, az, 4106) < 0.5 ? m.gear : m.cog);
        }
        if (underRoof && !onFloor) {
            boolean lamp = Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2;
            v.set(x, y + 1, z, lamp ? m.gauge : Math.floorMod(z, 3) == 0 ? m.brass : rnd(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 4107) < 0.7 ? m.plate : m.iron);
            if (lamp && free) v.set(x, y, z, m.pool);
        }
    }

    /** Light over every deck and floor every fifth cell, the slag's glow every fourth cell over the pits, and the bars that wait for a stand shut. */
    @Override
    protected void finish(V v, StoppedWorks.M m) {
        for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) {
            for (Part p : parts) {
                int f = p.feet2(x, z);
                if (f == NONE) continue;
                int feet = Part.airFrom(f);
                boolean pit = p instanceof Hall h && h.deckAt(x, z) == null;
                if (pit && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) air(v, x, feet + 1, z, GLOW);
                if (!pit && Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0) air(v, x, feet + 3, z, m.pool);
            }
        }
        for (int[] d : new int[][]{DOOR_DASH, DOOR_SLAM}) {
            for (int x = Math.max(d[0], v.x0); x <= Math.min(d[3], v.x0 + 15); x++) for (int z = Math.max(d[2], v.z0); z <= Math.min(d[5], v.z0 + 15); z++) {
                for (int y = d[1]; y <= d[4]; y++) if (open(x, y, z)) v.set(x, y, z, m.bars);
            }
        }
    }

    private void air(V v, int x, int y, int z, BlockState s) {
        if (open(x, y, z) && v.get(x, y, z) == null) v.set(x, y, z, s);
    }

    private List<Edit> unbar(int[] d) {
        List<Edit> out = new ArrayList<>();
        for (int x = d[0]; x <= d[3]; x++) for (int y = d[1]; y <= d[4]; y++) for (int z = d[2]; z <= d[5]; z++) if (open(x, y, z)) out.add(new Edit(x, y, z, AIR));
        return out;
    }

    /** A stand taken opens the bars past it; the walk check goes through both, as a player who has taken them does. */
    @Override
    public List<Edit> change(String moment) {
        if (moment.equals("trial_open_dash")) return unbar(DOOR_DASH);
        if (moment.equals("trial_open_slam")) return unbar(DOOR_SLAM);
        if (moment.equals("walk_open")) {
            List<Edit> out = unbar(DOOR_DASH);
            out.addAll(unbar(DOOR_SLAM));
            return out;
        }
        return super.change(moment);
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
